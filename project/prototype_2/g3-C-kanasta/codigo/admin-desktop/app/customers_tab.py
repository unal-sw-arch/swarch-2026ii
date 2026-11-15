from PyQt5.QtWidgets import QHBoxLayout, QLabel, QPushButton, QVBoxLayout, QWidget

from app.api import ApiError
from app.common import fill_table, format_date, make_table, secondary, show_error


class CustomersTab(QWidget):
    def __init__(self, gateway):
        super().__init__()
        self.gateway = gateway
        self.users = []

        hint = QLabel("Una cuenta desactivada no puede ingresar a la tienda ni hacer pedidos.")
        hint.setObjectName("hint")
        refresh = secondary(QPushButton("Actualizar"))
        refresh.clicked.connect(self.load)
        toggle = QPushButton("Activar / desactivar cuenta")
        toggle.clicked.connect(self.toggle)

        top = QHBoxLayout()
        top.addWidget(hint)
        top.addStretch()
        top.addWidget(refresh)
        top.addWidget(toggle)

        self.table = make_table(["Nombre", "Correo", "Teléfono", "Dirección", "Tipo", "Estado", "Creada"])

        layout = QVBoxLayout(self)
        layout.addLayout(top)
        layout.addWidget(self.table)
        self.load()

    def load(self):
        try:
            self.users = self.gateway.users()
        except ApiError as error:
            return show_error(self, error)
        fill_table(self.table, [
            [u["name"], u["email"], u["phone"], u["address"],
             "Administrador" if u["role"] == "ADMIN" else "Cliente",
             "Activa" if u["active"] else "Desactivada", format_date(u["createdAt"])]
            for u in self.users
        ])

    def toggle(self):
        row = self.table.currentRow()
        if row < 0:
            return show_error(self, "Primero selecciona una cuenta de la lista")
        user = self.users[row]
        try:
            self.gateway.set_user_active(user["id"], not user["active"])
        except ApiError as error:
            return show_error(self, error)
        self.load()
