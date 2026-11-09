"""Kanasta Admin: desktop application for the store administrator."""
import os
import sys

from PyQt5.QtGui import QIcon
from PyQt5.QtWidgets import QApplication

from app.common import STYLE
from app.login_dialog import LoginDialog
from app.main_window import MainWindow

HERE = os.path.dirname(os.path.abspath(__file__))
LOGO = os.path.join(HERE, "assets", "logo.png")
ICON = os.path.join(HERE, "assets", "icono.png")


def start(app):
    login = LoginDialog(LOGO)
    if not login.exec_():
        app.quit()
        return
    window = MainWindow(login.gateway, LOGO)
    window.logged_out.connect(lambda: start(app))
    app.window = window
    if os.getenv("KANASTA_FULLSCREEN") == "1":
        window.setGeometry(app.primaryScreen().availableGeometry())
    window.show()


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Kanasta Admin")
    app.setWindowIcon(QIcon(ICON))
    app.setStyleSheet(STYLE)
    app.setQuitOnLastWindowClosed(False)
    start(app)
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
