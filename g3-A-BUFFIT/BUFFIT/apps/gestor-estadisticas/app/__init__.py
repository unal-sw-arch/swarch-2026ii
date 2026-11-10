import logging
import time

from flask import Flask
from sqlalchemy.exc import OperationalError

from app.config import Config
from app.database import db
from app.routes import stats_bp


def create_app():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    app = Flask(__name__)
    app.config.from_object(Config)
    db.init_app(app)

    with app.app_context():
        for intento in range(10):  # la BD puede tardar unos segundos tras arrancar
            try:
                db.create_all()
                break
            except OperationalError:
                if intento == 9:
                    raise
                time.sleep(3)

    app.register_blueprint(stats_bp, url_prefix="/estadisticas")

    @app.route("/health", methods=["GET"])
    def health_check():
        return {"status": "ok", "service": "Gestor de Estadisticas (Flask)"}, 200

    if Config.CONSUMER_ENABLED:
        from app.consumer import start_consumer
        start_consumer(app)

    return app
