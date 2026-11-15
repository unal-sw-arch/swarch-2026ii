from PyQt5.QtCore import Qt
from PyQt5.QtGui import QPixmap
from PyQt5.QtWidgets import QDialog, QFormLayout, QLabel, QLineEdit, QPushButton, QVBoxLayout

from app.api import ApiError, DEFAULT_URL, Gateway
from app.common import show_error


class LoginDialog(QDialog):
    def __init__(self, logo_path):
        super().__init__()
        self.gateway = None
        self.setWindowTitle("Kanasta Admin - Ingresar")
        self.setFixedWidth(420)

        logo = QLabel()
        logo.setPixmap(QPixmap(logo_path).scaledToWidth(260, Qt.SmoothTransformation))
        logo.setAlignment(Qt.AlignCenter)

        title = QLabel("Administración de la tienda")
        title.setObjectName("title")
        title.setAlignment(Qt.AlignCenter)

        self.server = QLineEdit(DEFAULT_URL)
        self.email = QLineEdit("admin@tienda.com")
        self.password = QLineEdit()
        self.password.setEchoMode(QLineEdit.Password)
        self.password.returnPressed.connect(self.login)

        form = QFormLayout()
        form.addRow("Servidor", self.server)
        form.addRow("Correo", self.email)
        form.addRow("Contraseña", self.password)

        button = QPushButton("Ingresar")
        button.clicked.connect(self.login)

        layout = QVBoxLayout(self)
        layout.addWidget(logo)
        layout.addWidget(title)
        layout.addLayout(form)
        layout.addWidget(button)

    def login(self):
        gateway = Gateway(self.server.text().strip())
        try:
            gateway.login(self.email.text().strip(), self.password.text())
        except ApiError as error:
            show_error(self, error)
            return
        self.gateway = gateway
        self.accept()
