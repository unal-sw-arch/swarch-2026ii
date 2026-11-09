from PyQt5.QtCore import Qt, pyqtSignal
from PyQt5.QtGui import QPixmap
from PyQt5.QtWidgets import (QApplication, QHBoxLayout, QLabel, QMainWindow, QPushButton, QTabWidget, QVBoxLayout,
                             QWidget)

from app.account_tab import AccountTab
from app.api import ApiError
from app.common import secondary
from app.customers_tab import CustomersTab
from app.email_tab import EmailTab
from app.orders_tab import OrdersTab
from app.products_tab import ProductsTab
from app.store_tab import StoreTab


class MainWindow(QMainWindow):
    logged_out = pyqtSignal()

    def __init__(self, gateway, logo_path):
        super().__init__()
        self.gateway = gateway
        self.logging_out = False
        self.setWindowTitle("Kanasta Admin")
        self.resize(1200, 760)

        logo = QLabel()
        logo.setPixmap(QPixmap(logo_path).scaledToHeight(44, Qt.SmoothTransformation))
        self.store_name = QLabel()
        self.store_name.setObjectName("title")
        session = QLabel(f"Sesión: {gateway.user['name']}")
        logout = secondary(QPushButton("Cerrar sesión"))
        logout.clicked.connect(self.logout)

        header = QHBoxLayout()
        header.addWidget(logo)
        header.addWidget(self.store_name)
        header.addStretch()
        header.addWidget(session)
        header.addWidget(logout)

        tabs = QTabWidget()
        tabs.addTab(OrdersTab(gateway), "Pedidos")
        tabs.addTab(ProductsTab(gateway), "Productos")
        tabs.addTab(CustomersTab(gateway), "Clientes")
        tabs.addTab(StoreTab(gateway, self.show_store), "Tienda")
        tabs.addTab(EmailTab(gateway), "Correo")
        tabs.addTab(AccountTab(gateway), "Mi cuenta")
        self.tabs = tabs

        central = QWidget()
        layout = QVBoxLayout(central)
        layout.addLayout(header)
        layout.addWidget(tabs)
        self.setCentralWidget(central)

        try:
            self.show_store(gateway.store())
        except ApiError:
            pass

    def show_store(self, store):
        self.store_name.setText(store["name"])

    def logout(self):
        self.logging_out = True
        self.gateway.logout()
        self.close()
        self.logged_out.emit()

    # Closing the window (without "Cerrar sesión") closes the app
    def closeEvent(self, event):
        if not self.logging_out:
            QApplication.quit()
        event.accept()
