from flask import Flask
from flask_cors import CORS
from app.config import Config
from app.routes import auth_bp

def create_app():
    app = Flask(__name__)
    app.config.from_object(Config)

    # Permite peticiones HTTP Cross-Origin desde el Portal (React)
    CORS(app)

    app.register_blueprint(auth_bp, url_prefix="/")

    @app.route("/health", methods=["GET"])
    def health_check():
        return {"status": "ok", "service": "AuthService (Flask)"}, 200

    return app