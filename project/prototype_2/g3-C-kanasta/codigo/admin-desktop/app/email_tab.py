from PyQt5.QtWidgets import (QCheckBox, QFormLayout, QGroupBox, QHBoxLayout, QLabel, QLineEdit, QPushButton,
                             QVBoxLayout, QWidget)

from app.api import ApiError
from app.common import fill_table, format_date, make_table, secondary, show_error, show_info

TYPES = {
    "ACCOUNT_CREATED": "Cuenta creada",
    "PASSWORD_CHANGED": "Cambio de contraseña",
    "ORDER_PLACED": "Recibo de pedido",
    "TEST": "Prueba",
}
STATUSES = {"SENT": "Enviado", "FAILED": "Falló", "SKIPPED": "No enviado"}


class EmailTab(QWidget):
    """Resend account used to send the emails of the store."""

    def __init__(self, gateway):
        super().__init__()
        self.gateway = gateway

        hint = QLabel(
            "1. Crea una cuenta gratis en resend.com y copia tu API key (empieza por re_).\n"
            "2. Mientras no verifiques un dominio propio en Resend, usa el remitente onboarding@resend.dev; "
            "en ese modo Resend solo entrega correos a la dirección con la que creaste la cuenta.\n"
            "3. Activa el envío, guarda y envía un correo de prueba."
        )
        hint.setObjectName("hint")
        hint.setWordWrap(True)

        self.enabled = QCheckBox("Enviar correos a los clientes")
        self.api_key = QLineEdit()
        self.api_key.setEchoMode(QLineEdit.Password)
        self.from_email = QLineEdit()
        self.from_name = QLineEdit()
        save = QPushButton("Guardar configuración")
        save.clicked.connect(self.save)

        form = QFormLayout()
        form.addRow("", self.enabled)
        form.addRow("API key de Resend", self.api_key)
        form.addRow("Correo remitente", self.from_email)
        form.addRow("Nombre del remitente", self.from_name)

        settings_box = QGroupBox("Cuenta de correo (Resend)")
        settings_layout = QVBoxLayout(settings_box)
        settings_layout.addWidget(hint)
        settings_layout.addLayout(form)
        settings_layout.addWidget(save)

        self.test_to = QLineEdit()
        self.test_to.setPlaceholderText("Correo que recibirá la prueba")
        test = secondary(QPushButton("Enviar correo de prueba"))
        test.clicked.connect(self.send_test)
        test_row = QHBoxLayout()
        test_row.addWidget(self.test_to)
        test_row.addWidget(test)
        settings_layout.addLayout(test_row)

        refresh = secondary(QPushButton("Actualizar historial"))
        refresh.clicked.connect(self.load_history)
        self.table = make_table(["Fecha", "Tipo", "Para", "Asunto", "Estado", "Detalle"])
        history_box = QGroupBox("Últimos correos")
        history_layout = QVBoxLayout(history_box)
        history_layout.addWidget(refresh)
        history_layout.addWidget(self.table)

        layout = QVBoxLayout(self)
        layout.addWidget(settings_box)
        layout.addWidget(history_box)
        self.load()

    def load(self):
        try:
            settings = self.gateway.email_settings()
        except ApiError as error:
            return show_error(self, error)
        self.enabled.setChecked(settings["enabled"])
        self.from_email.setText(settings["fromEmail"])
        self.from_name.setText(settings["fromName"])
        self.api_key.clear()
        self.api_key.setPlaceholderText(
            f"Guardada: {settings['apiKeyHint']} (déjalo vacío para no cambiarla)" if settings["apiKeySet"]
            else "Pega aquí tu API key"
        )
        self.load_history()

    def load_history(self):
        try:
            emails = self.gateway.emails()
        except ApiError as error:
            return show_error(self, error)
        fill_table(self.table, [
            [format_date(e["createdAt"]), TYPES.get(e["type"], e["type"]), e["to"], e["subject"],
             STATUSES.get(e["status"], e["status"]), e["detail"]]
            for e in emails
        ])

    def save(self):
        data = {
            "apiKey": self.api_key.text().strip(),
            "fromEmail": self.from_email.text().strip(),
            "fromName": self.from_name.text().strip(),
            "enabled": self.enabled.isChecked(),
        }
        try:
            self.gateway.save_email_settings(data)
        except ApiError as error:
            return show_error(self, error)
        show_info(self, "La configuración del correo quedó guardada")
        self.load()

    def send_test(self):
        to = self.test_to.text().strip()
        if not to:
            return show_error(self, "Escribe el correo que recibirá la prueba")
        try:
            result = self.gateway.send_test_email(to)
        except ApiError as error:
            return show_error(self, error)
        if result["status"] == "SENT":
            show_info(self, f"Correo de prueba enviado a {to}")
        else:
            show_error(self, f"El correo no se envió: {result['detail']}")
        self.load_history()
