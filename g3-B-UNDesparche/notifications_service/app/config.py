import os
from dataclasses import dataclass


def _bool(name: str, default: bool = False) -> bool:
    return os.getenv(name, str(default)).strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    rabbitmq_url: str
    prefetch: int
    max_retries: int
    retry_delay_ms: int
    # --- correo ---
    provider: str                 # "brevo" (principal) | "smtp" (respaldo: Gmail, Mailpit, etc.)
    brevo_api_key: str | None
    mail_from_email: str
    mail_from_name: str
    mail_reply_to: str | None
    smtp_host: str | None
    smtp_port: int
    smtp_user: str | None
    smtp_password: str | None
    smtp_starttls: bool
    sandbox_to: str | None
    dry_run: bool
    # --- otros ---
    frontend_url: str
    dev_endpoints: bool


def load_settings() -> Settings:
    provider = os.getenv("MAIL_PROVIDER", "brevo").strip().lower()
    if provider not in {"brevo", "smtp"}:
        raise ValueError("MAIL_PROVIDER debe ser 'brevo' o 'smtp'")
    brevo_key = os.getenv("BREVO_API_KEY") or None
    smtp_host = os.getenv("SMTP_HOST") or None
    not_configured = (provider == "brevo" and brevo_key is None) or (provider == "smtp" and smtp_host is None)
    return Settings(
        rabbitmq_url=os.getenv("RABBITMQ_URL", "amqp://undesparche:undesparche@rabbitmq:5672/"),
        prefetch=int(os.getenv("PREFETCH", "10")),
        max_retries=int(os.getenv("MAX_RETRIES", "5")),
        retry_delay_ms=int(os.getenv("RETRY_DELAY_MS", "30000")),
        provider=provider,
        brevo_api_key=brevo_key,
        mail_from_email=os.getenv("MAIL_FROM_EMAIL", "no-reply@example.com"),
        mail_from_name=os.getenv("MAIL_FROM_NAME", "UNdesparche"),
        mail_reply_to=os.getenv("MAIL_REPLY_TO") or None,
        smtp_host=smtp_host,
        smtp_port=int(os.getenv("SMTP_PORT", "587")),
        smtp_user=os.getenv("SMTP_USER") or None,
        smtp_password=os.getenv("SMTP_PASSWORD") or None,
        smtp_starttls=_bool("SMTP_STARTTLS", True),
        sandbox_to=os.getenv("SANDBOX_TO") or None,
        dry_run=_bool("EMAIL_DRY_RUN", False) or not_configured,
        frontend_url=os.getenv("FRONTEND_URL", "http://localhost:5172").rstrip("/"),
        dev_endpoints=_bool("DEV_ENDPOINTS", False),
    )
