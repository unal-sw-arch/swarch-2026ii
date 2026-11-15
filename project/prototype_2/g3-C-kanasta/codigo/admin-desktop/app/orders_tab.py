from PyQt5.QtWidgets import QComboBox, QHBoxLayout, QLabel, QPlainTextEdit, QPushButton, QVBoxLayout, QWidget

from app.api import ApiError
from app.common import (STATUS_LABELS, fill_table, format_date, format_price, format_quantity, make_table,
                        secondary, show_error, show_info)


class OrdersTab(QWidget):
    def __init__(self, gateway):
        super().__init__()
        self.gateway = gateway
        self.orders = []

        self.filter = QComboBox()
        self.filter.addItem("Todos los pedidos", None)
        for code, label in STATUS_LABELS.items():
            self.filter.addItem(label, code)
        self.filter.currentIndexChanged.connect(self.load)
        refresh = secondary(QPushButton("Actualizar"))
        refresh.clicked.connect(self.load)

        top = QHBoxLayout()
        top.addWidget(QLabel("Mostrar:"))
        top.addWidget(self.filter)
        top.addStretch()
        top.addWidget(refresh)

        self.table = make_table(["Pedido", "Fecha", "Cliente", "Teléfono", "Dirección", "Total", "Estado"])
        self.table.itemSelectionChanged.connect(self.show_detail)

        self.detail = QPlainTextEdit()
        self.detail.setReadOnly(True)
        self.detail.setMaximumHeight(170)

        self.status = QComboBox()
        for code, label in STATUS_LABELS.items():
            self.status.addItem(label, code)
        save = QPushButton("Guardar estado")
        save.clicked.connect(self.save_status)

        bottom = QHBoxLayout()
        bottom.addWidget(QLabel("Cambiar estado del pedido seleccionado:"))
        bottom.addWidget(self.status)
        bottom.addWidget(save)
        bottom.addStretch()

        layout = QVBoxLayout(self)
        layout.addLayout(top)
        layout.addWidget(self.table)
        layout.addWidget(self.detail)
        layout.addLayout(bottom)
        self.load()

    def load(self):
        try:
            self.orders = self.gateway.orders(self.filter.currentData())
        except ApiError as error:
            return show_error(self, error)
        fill_table(self.table, [
            [o["id"][-6:].upper(), format_date(o["createdAt"]), o["customerName"], o["phone"], o["address"],
             format_price(o["total"]) + (" (aprox.)" if o["estimated"] else ""), STATUS_LABELS[o["status"]]]
            for o in self.orders
        ])
        self.detail.clear()

    def selected(self):
        row = self.table.currentRow()
        return self.orders[row] if 0 <= row < len(self.orders) else None

    def show_detail(self):
        order = self.selected()
        if not order:
            return
        lines = [f"Pedido #{order['id'][-6:].upper()} - {order['customerName']} ({order['email']})", ""]
        for item in order["items"]:
            lines.append(f"  {format_quantity(item['quantity'], item['unit']):>10}  {item['name']}  {format_price(item['subtotal'])}")
        lines.append("")
        lines.append(f"Total: {format_price(order['total'])}")
        if order["estimated"]:
            lines.append("Tiene productos por peso o volumen: cobre el valor real de lo que se entrega.")
        if order["notes"]:
            lines.append(f"Notas: {order['notes']}")
        self.detail.setPlainText("\n".join(lines))
        self.status.setCurrentIndex(list(STATUS_LABELS).index(order["status"]))

    def save_status(self):
        order = self.selected()
        if not order:
            return show_error(self, "Primero selecciona un pedido de la lista")
        try:
            self.gateway.update_order_status(order["id"], self.status.currentData())
        except ApiError as error:
            return show_error(self, error)
        show_info(self, "El estado del pedido quedó guardado")
        self.load()
