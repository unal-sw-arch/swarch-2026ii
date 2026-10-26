from flask import Flask
from app.config import Config
from app.database import db
from app.routes import orders_bp

def create_app():
    app = Flask(__name__)
    app.config.from_object(Config)

    db.init_app(app)

    with app.app_context():
        db.create_all()  # Crea las tablas automáticamente en la base de datos

    app.register_blueprint(orders_bp, url_prefix="/pedidos")

    @app.route("/health", methods=["GET"])
    def health_check():
        return {"status": "ok", "service": "Gestor de Pedidos (Flask)"}, 200

    return app