from PyQt5.QtWidgets import QFormLayout, QGroupBox, QHBoxLayout, QLineEdit, QPushButton, QVBoxLayout, QWidget

from app.api import ApiError
from app.common import show_error, show_info


class AccountTab(QWidget):
    """The admin's own account: personal data and password."""

    def __init__(self, gateway):
        super().__init__()
        self.gateway = gateway

        self.email = QLineEdit()
        self.email.setEnabled(False)
        self.name = QLineEdit()
        self.phone = QLineEdit()
        self.address = QLineEdit()
        save = QPushButton("Guardar mis datos")
        save.clicked.connect(self.save_profile)

        profile_form = QFormLayout()
        profile_form.addRow("Correo", self.email)
        profile_form.addRow("Nombre", self.name)
        profile_form.addRow("Teléfono", self.phone)
        profile_form.addRow("Dirección", self.address)
        profile_box = QGroupBox("Mis datos")
        profile_layout = QVBoxLayout(profile_box)
        profile_layout.addLayout(profile_form)
        profile_layout.addWidget(save)

        self.current = QLineEdit()
        self.new = QLineEdit()
        self.repeat = QLineEdit()
        for field in (self.current, self.new, self.repeat):
            field.setEchoMode(QLineEdit.Password)
        change = QPushButton("Cambiar contraseña")
        change.clicked.connect(self.change_password)

        password_form = QFormLayout()
        password_form.addRow("Contraseña actual", self.current)
        password_form.addRow("Contraseña nueva", self.new)
        password_form.addRow("Repite la nueva", self.repeat)
        password_box = QGroupBox("Cambiar contraseña")
        password_layout = QVBoxLayout(password_box)
        password_layout.addLayout(password_form)
        password_layout.addWidget(change)

        row = QHBoxLayout()
        row.addWidget(profile_box)
        row.addWidget(password_box)
        layout = QVBoxLayout(self)
        layout.addLayout(row)
        layout.addStretch()
        self.load()

    def load(self):
        try:
            user = self.gateway.me()
        except ApiError as error:
            return show_error(self, error)
        self.email.setText(user["email"])
        self.name.setText(user["name"])
        self.phone.setText(user["phone"])
        self.address.setText(user["address"])

    def save_profile(self):
        data = {"name": self.name.text().strip(), "phone": self.phone.text().strip(), "address": self.address.text().strip()}
        try:
            self.gateway.update_me(data)
        except ApiError as error:
            return show_error(self, error)
        show_info(self, "Tus datos quedaron guardados")

    def change_password(self):
        if self.new.text() != self.repeat.text():
            return show_error(self, "Las contraseñas nuevas no coinciden")
        try:
            self.gateway.change_password(self.current.text(), self.new.text())
        except ApiError as error:
            return show_error(self, error)
        for field in (self.current, self.new, self.repeat):
            field.clear()
        show_info(self, "Cambiaste tu contraseña. Te enviamos un correo de aviso.")
