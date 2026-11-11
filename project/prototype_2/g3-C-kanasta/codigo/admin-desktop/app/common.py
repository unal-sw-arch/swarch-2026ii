"""Small helpers shared by all the screens of Kanasta Admin."""
from datetime import datetime

from PyQt5.QtWidgets import QAbstractItemView, QHeaderView, QMessageBox, QTableWidget, QTableWidgetItem

STATUS_LABELS = {"PENDING": "Pendiente", "SHIPPED": "En camino", "DELIVERED": "Entregado", "CANCELLED": "Cancelado"}
UNIT_LABELS = {"unidad": "Unidad", "kg": "Kilogramo (kg)", "litro": "Litro (L)"}

STYLE = """
QWidget { font-size: 14px; color: #1f2a25; }
QMainWindow, QDialog { background: #f6f8f7; }
QPushButton { background: #1f8a5b; color: white; border: 0; border-radius: 6px; padding: 7px 14px; }
QPushButton:hover { background: #166a45; }
QPushButton:disabled { background: #b8c4be; }
QPushButton[secondary="true"] { background: #e5f4ec; color: #166a45; }
QLineEdit, QComboBox, QSpinBox, QDoubleSpinBox, QPlainTextEdit {
    background: white; border: 1px solid #dfe6e2; border-radius: 6px; padding: 5px; }
QTableWidget { background: white; border: 1px solid #dfe6e2; gridline-color: #eef2f0; }
QHeaderView::section { background: #eef4f1; border: 0; padding: 6px; font-weight: bold; }
QTabWidget::pane { border: 1px solid #dfe6e2; background: white; border-radius: 6px; }
QTabBar::tab { background: #e5f4ec; padding: 8px 16px; margin-right: 2px; border-top-left-radius: 6px; border-top-right-radius: 6px; }
QTabBar::tab:selected { background: #1f8a5b; color: white; }
QGroupBox { font-weight: bold; border: 1px solid #dfe6e2; border-radius: 6px; margin-top: 12px; padding: 10px; background: white; }
QGroupBox::title { subcontrol-origin: margin; left: 10px; padding: 0 4px; }
QLabel#title { font-size: 20px; font-weight: bold; color: #166a45; }
QLabel#hint { color: #64746d; font-size: 12px; }
"""


def format_price(value):
    return "$ " + f"{round(value):,}".replace(",", ".")


def format_quantity(quantity, unit):
    text = f"{quantity:g}".replace(".", ",")
    return {"kg": f"{text} kg", "litro": f"{text} L"}.get(unit, text)


def format_date(iso_text):
    try:
        return datetime.fromisoformat(iso_text.replace("Z", "+00:00")).astimezone().strftime("%Y-%m-%d %H:%M")
    except (ValueError, AttributeError):
        return iso_text or ""


def show_error(parent, error):
    QMessageBox.warning(parent, "Kanasta Admin", str(error))


def show_info(parent, text):
    QMessageBox.information(parent, "Kanasta Admin", text)


def make_table(headers):
    table = QTableWidget(0, len(headers))
    table.setHorizontalHeaderLabels(headers)
    table.setSelectionBehavior(QAbstractItemView.SelectRows)
    table.setSelectionMode(QAbstractItemView.SingleSelection)
    table.setEditTriggers(QAbstractItemView.NoEditTriggers)
    table.verticalHeader().setVisible(False)
    table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
    return table


def fill_table(table, rows):
    table.setRowCount(len(rows))
    for r, row in enumerate(rows):
        for c, value in enumerate(row):
            table.setItem(r, c, QTableWidgetItem(str(value)))


def secondary(button):
    button.setProperty("secondary", True)
    return button
