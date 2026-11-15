from PyQt5.QtWidgets import QFormLayout, QGroupBox, QLabel, QLineEdit, QPushButton, QVBoxLayout, QWidget

from app.api import ApiError
from app.common import show_error, show_info

FIELDS = [
    ("name", "Nombre de la tienda"),
    ("slogan", "Frase corta"),
    ("address", "Dirección"),
    ("phone", "Teléfono"),
    ("email", "Correo de contacto"),
    ("opening_hours", "Horario de atención"),
    ("delivery_area", "Zona de entregas"),
]


class StoreTab(QWidget):
    """Data of the store that the customers see in the web and in the emails."""

    def __init__(self, gateway, on_saved):
        super().__init__()
        self.gateway = gateway
        self.on_saved = on_saved
        self.inputs = {key: QLineEdit() for key, _ in FIELDS}

        form = QFormLayout()
        for key, label in FIELDS:
            form.addRow(label, self.inputs[key])
        save = QPushButton("Guardar datos de la tienda")
        save.clicked.connect(self.save)

        box = QGroupBox("Datos de la tienda")
        box_layout = QVBoxLayout(box)
        hint = QLabel("Estos datos aparecen en la página de la tienda y en los correos que reciben los clientes.")
        hint.setObjectName("hint")
        box_layout.addWidget(hint)
        box_layout.addLayout(form)
        box_layout.addWidget(save)

        layout = QVBoxLayout(self)
        layout.addWidget(box)
        layout.addStretch()
        self.load()

    def load(self):
        try:
            store = self.gateway.store()
        except ApiError as error:
            return show_error(self, error)
        for key, _ in FIELDS:
            self.inputs[key].setText(store.get(key, ""))

    def save(self):
        data = {key: self.inputs[key].text().strip() for key, _ in FIELDS}
        try:
            store = self.gateway.save_store(data)
        except ApiError as error:
            return show_error(self, error)
        self.on_saved(store)
        show_info(self, "Los datos de la tienda quedaron guardados")
