from PyQt5.QtCore import Qt
from PyQt5.QtGui import QPixmap
from PyQt5.QtWidgets import (QComboBox, QDialog, QDialogButtonBox, QDoubleSpinBox, QFileDialog, QFormLayout,
                             QHBoxLayout, QLabel, QLineEdit, QMessageBox, QPlainTextEdit, QPushButton, QSpinBox,
                             QVBoxLayout, QWidget)

from app.api import ApiError
from app.common import UNIT_LABELS, fill_table, format_price, format_quantity, make_table, secondary, show_error


class ProductDialog(QDialog):
    """Form to create or edit one product."""

    def __init__(self, parent, gateway, categories, product=None):
        super().__init__(parent)
        self.gateway = gateway
        self.setWindowTitle("Editar producto" if product else "Nuevo producto")
        self.setMinimumWidth(520)
        product = product or {}

        self.name = QLineEdit(product.get("name", ""))
        self.description = QPlainTextEdit(product.get("description", ""))
        self.description.setMaximumHeight(80)
        self.category = QComboBox()
        for c in categories:
            self.category.addItem(c["name"], c["id"])
        if product:
            self.category.setCurrentIndex(self.category.findData(product["category_id"]))
        self.unit = QComboBox()
        for code, label in UNIT_LABELS.items():
            self.unit.addItem(label, code)
        self.unit.setCurrentIndex(self.unit.findData(product.get("unit", "unidad")))
        self.price = QSpinBox()
        self.price.setRange(0, 100_000_000)
        self.price.setSingleStep(100)
        self.price.setPrefix("$ ")
        self.price.setValue(product.get("price", 0))
        self.stock = QDoubleSpinBox()
        self.stock.setRange(0, 100_000)
        self.stock.setDecimals(2)
        self.stock.setValue(product.get("stock", 0))
        self.image = QLineEdit(product.get("image", ""))
        upload = secondary(QPushButton("Subir imagen..."))
        upload.clicked.connect(self.upload_image)
        self.preview = QLabel()
        self.preview.setFixedSize(200, 150)
        self.preview.setAlignment(Qt.AlignCenter)
        self.image.editingFinished.connect(self.show_preview)

        image_row = QHBoxLayout()
        image_row.addWidget(self.image)
        image_row.addWidget(upload)

        form = QFormLayout()
        form.addRow("Nombre", self.name)
        form.addRow("Descripción", self.description)
        form.addRow("Categoría", self.category)
        form.addRow("Se vende por", self.unit)
        form.addRow("Precio (por unidad, kg o litro)", self.price)
        form.addRow("Existencias", self.stock)
        form.addRow("Imagen", image_row)
        form.addRow("", self.preview)

        buttons = QDialogButtonBox(QDialogButtonBox.Save | QDialogButtonBox.Cancel)
        buttons.button(QDialogButtonBox.Save).setText("Guardar")
        buttons.button(QDialogButtonBox.Cancel).setText("Cancelar")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)

        layout = QVBoxLayout(self)
        layout.addLayout(form)
        layout.addWidget(buttons)
        self.show_preview()

    def upload_image(self):
        path, _ = QFileDialog.getOpenFileName(self, "Elegir imagen", "", "Imágenes (*.jpg *.jpeg *.png *.webp)")
        if not path:
            return
        try:
            self.image.setText(self.gateway.upload_image(path))
        except ApiError as error:
            return show_error(self, error)
        self.show_preview()

    def show_preview(self):
        pixmap = QPixmap()
        if self.image.text() and pixmap.loadFromData(self.gateway.image(self.image.text())):
            self.preview.setPixmap(pixmap.scaled(200, 150, Qt.KeepAspectRatio, Qt.SmoothTransformation))
        else:
            self.preview.setText("Sin imagen")

    def data(self):
        return {
            "name": self.name.text().strip(),
            "description": self.description.toPlainText().strip(),
            "category_id": self.category.currentData(),
            "unit": self.unit.currentData(),
            "price": self.price.value(),
            "stock": self.stock.value(),
            "image": self.image.text().strip(),
        }


class CategoriesDialog(QDialog):
    """List of categories, with buttons to add and delete."""

    def __init__(self, parent, gateway):
        super().__init__(parent)
        self.gateway = gateway
        self.categories = []
        self.setWindowTitle("Categorías")
        self.setMinimumSize(460, 420)

        self.table = make_table(["Nombre", "Identificador", "Orden"])
        self.name = QLineEdit()
        self.name.setPlaceholderText("Nombre de la nueva categoría")
        self.position = QSpinBox()
        self.position.setRange(1, 999)
        self.position.setValue(50)
        add = QPushButton("Agregar")
        add.clicked.connect(self.add)
        delete = secondary(QPushButton("Eliminar seleccionada"))
        delete.clicked.connect(self.delete)

        row = QHBoxLayout()
        row.addWidget(self.name)
        row.addWidget(QLabel("Orden"))
        row.addWidget(self.position)
        row.addWidget(add)

        layout = QVBoxLayout(self)
        layout.addWidget(self.table)
        layout.addWidget(delete)
        layout.addLayout(row)
        self.load()

    def load(self):
        try:
            self.categories = self.gateway.categories()
        except ApiError as error:
            return show_error(self, error)
        fill_table(self.table, [[c["name"], c["slug"], c["position"]] for c in self.categories])

    def add(self):
        name = self.name.text().strip()
        if not name:
            return
        slug = "".join(ch if ch.isalnum() else "-" for ch in name.lower())
        for a, b in zip("áéíóúñü", "aeiounu"):
            slug = slug.replace(a, b)
        try:
            self.gateway.create_category(name, slug, self.position.value())
        except ApiError as error:
            return show_error(self, error)
        self.name.clear()
        self.load()

    def delete(self):
        row = self.table.currentRow()
        if row < 0:
            return
        try:
            self.gateway.delete_category(self.categories[row]["id"])
        except ApiError as error:
            return show_error(self, error)
        self.load()


class ProductsTab(QWidget):
    def __init__(self, gateway):
        super().__init__()
        self.gateway = gateway
        self.products = []

        self.search = QLineEdit()
        self.search.setPlaceholderText("Buscar producto")
        self.search.returnPressed.connect(self.load)
        new = QPushButton("Nuevo producto")
        new.clicked.connect(self.new_product)
        edit = secondary(QPushButton("Editar"))
        edit.clicked.connect(self.edit_product)
        delete = secondary(QPushButton("Eliminar"))
        delete.clicked.connect(self.delete_product)
        categories = secondary(QPushButton("Categorías..."))
        categories.clicked.connect(lambda: CategoriesDialog(self, self.gateway).exec_())

        top = QHBoxLayout()
        top.addWidget(self.search)
        top.addWidget(new)
        top.addWidget(edit)
        top.addWidget(delete)
        top.addWidget(categories)

        self.table = make_table(["ID", "Nombre", "Categoría", "Precio", "Se vende por", "Existencias"])
        self.table.doubleClicked.connect(self.edit_product)

        layout = QVBoxLayout(self)
        layout.addLayout(top)
        layout.addWidget(self.table)
        self.load()

    def load(self):
        try:
            self.products = self.gateway.products(self.search.text().strip())
        except ApiError as error:
            return show_error(self, error)
        fill_table(self.table, [
            [p["id"], p["name"], p["category_name"], format_price(p["price"]), p["unit"],
             format_quantity(p["stock"], p["unit"])]
            for p in self.products
        ])

    def selected(self):
        row = self.table.currentRow()
        return self.products[row] if 0 <= row < len(self.products) else None

    def open_dialog(self, product=None):
        try:
            categories = self.gateway.categories()
        except ApiError as error:
            return show_error(self, error)
        dialog = ProductDialog(self, self.gateway, categories, product)
        while dialog.exec_():
            try:
                self.gateway.save_product(dialog.data(), product["id"] if product else None)
                break
            except ApiError as error:
                show_error(self, error)
        self.load()

    def new_product(self):
        self.open_dialog()

    def edit_product(self):
        product = self.selected()
        if product:
            self.open_dialog(product)

    def delete_product(self):
        product = self.selected()
        if not product:
            return
        answer = QMessageBox.question(self, "Eliminar producto", f'¿Eliminar "{product["name"]}" del catálogo?')
        if answer != QMessageBox.Yes:
            return
        try:
            self.gateway.delete_product(product["id"])
        except ApiError as error:
            return show_error(self, error)
        self.load()
