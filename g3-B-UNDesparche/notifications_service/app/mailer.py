import asyncio
import logging
import re
from dataclasses import dataclass, field
from email.message import EmailMessage
from email.utils import formataddr

import aiosmtplib
import httpx

from .config import Settings

logger = logging.getLogger(__name__)

BREVO_URL = "https://api.brevo.com/v3/smtp/email"
MAX_PARALLEL_SENDS = 5


class EmailError(Exception):
    pass


class TransientEmailError(EmailError):
    """Vale la pena reintentar (429, 5xx, red, SMTP 4xx)."""


class PermanentEmailError(EmailError):
    """Reintentar no sirve (dirección inválida, remitente no verificado, credenciales)."""


@dataclass
class OutgoingEmail:
    to: str
    subject: str
    html: str
    headers: dict[str, str] = field(default_factory=dict)


class Mailer:
    """Envía un correo por destinatario con Brevo (API HTTPS) o SMTP (respaldo)."""

    def __init__(self, settings: Settings):
        self._s = settings
        self._client = httpx.AsyncClient(timeout=15)
        self._sem = asyncio.Semaphore(MAX_PARALLEL_SENDS)

    async def close(self) -> None:
        await self._client.aclose()

    async def send_all(self, emails: list[OutgoingEmail]) -> list[EmailError | None]:
        """Devuelve, por cada correo y en el mismo orden, None si salió o el error si falló.

        Los fallos son por destinatario: el consumer reintenta solo a quienes fallaron."""
        return list(await asyncio.gather(*(self._send_one(e) for e in emails)))

    async def _send_one(self, email: OutgoingEmail) -> EmailError | None:
        async with self._sem:
            try:
                await self._deliver(email)
            except EmailError as exc:
                return exc
            except Exception as exc:  # imprevisto: tratarlo como temporal
                return TransientEmailError(f"Error inesperado: {exc!r}")
        return None

    async def _deliver(self, email: OutgoingEmail) -> None:
        to, subject = email.to, email.subject
        if self._s.sandbox_to:  # modo pruebas: todo a una sola dirección
            to, subject = self._s.sandbox_to, f"[TEST -> {email.to}] {email.subject}"
        if self._s.dry_run:
            logger.info("[DRY-RUN] to=%s subject=%r (html %d bytes)", to, subject, len(email.html))
            return
        if self._s.provider == "brevo":
            await self._send_brevo(to, subject, email)
        else:
            await self._send_smtp(to, subject, email)

    # ---------------- Brevo (API REST) ----------------
    async def _send_brevo(self, to: str, subject: str, email: OutgoingEmail) -> None:
        payload: dict = {
            "sender": {"name": self._s.mail_from_name, "email": self._s.mail_from_email},
            "to": [{"email": to}],
            "subject": subject,
            "htmlContent": email.html,
        }
        if email.headers:
            payload["headers"] = email.headers
        if self._s.mail_reply_to:
            payload["replyTo"] = {"email": self._s.mail_reply_to}
        try:
            resp = await self._client.post(
                BREVO_URL, json=payload,
                headers={"api-key": self._s.brevo_api_key or "", "accept": "application/json"},
            )
        except httpx.HTTPError as exc:
            raise TransientEmailError(f"Error de red hacia Brevo: {exc}") from exc

        if resp.status_code in (200, 201, 202):
            return
        detail = f"Brevo respondió {resp.status_code}: {resp.text[:300]}"
        if resp.status_code == 429 or resp.status_code >= 500:
            raise TransientEmailError(detail)
        raise PermanentEmailError(detail)

    # ---------------- SMTP (respaldo: Gmail, Mailpit, ...) ----------------
    async def _send_smtp(self, to: str, subject: str, email: OutgoingEmail) -> None:
        msg = EmailMessage()
        msg["From"] = formataddr((self._s.mail_from_name, self._s.mail_from_email))
        msg["To"] = to
        msg["Subject"] = subject
        if self._s.mail_reply_to:
            msg["Reply-To"] = self._s.mail_reply_to
        for k, v in email.headers.items():
            msg[k] = v
        text = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", email.html)).strip()
        msg.set_content(text)
        msg.add_alternative(email.html, subtype="html")

        port = self._s.smtp_port
        try:
            await aiosmtplib.send(
                msg,
                hostname=self._s.smtp_host,
                port=port,
                username=self._s.smtp_user,
                password=self._s.smtp_password,
                use_tls=(port == 465),
                start_tls=(self._s.smtp_starttls and port != 465),
                timeout=15,
            )
        except (aiosmtplib.SMTPAuthenticationError, aiosmtplib.SMTPRecipientsRefused,
                aiosmtplib.SMTPSenderRefused) as exc:
            raise PermanentEmailError(f"SMTP rechazó el envío: {exc}") from exc
        except aiosmtplib.SMTPResponseException as exc:
            if 400 <= exc.code < 500:
                raise TransientEmailError(f"SMTP {exc.code}: {exc.message}") from exc
            raise PermanentEmailError(f"SMTP {exc.code}: {exc.message}") from exc
        except (aiosmtplib.SMTPException, OSError, asyncio.TimeoutError) as exc:
            raise TransientEmailError(f"Error SMTP/red: {exc!r}") from exc
