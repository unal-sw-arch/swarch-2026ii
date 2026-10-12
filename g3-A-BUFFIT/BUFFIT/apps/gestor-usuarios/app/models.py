from datetime import datetime
from werkzeug.security import generate_password_hash, check_password_hash
from app.database import db

class User(db.Model):
    __tablename__ = "users"

    id = db.Column(db.Integer, primary_key=True, autoincrement=True)
    name = db.Column(db.String(120), nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password_hash = db.Column(db.String(256), nullable=False)  # Campo para guardar la contraseña encriptada
    role = db.Column(db.String(50), nullable=False, default="Usuario")  # Usuario, Comedor, Administrador
    user_type = db.Column(db.String(50), nullable=False, default="ESTUDIANTE")  # APOYO_ALIMENTARIO, ESTUDIANTE, GENERAL
    is_active = db.Column(db.Boolean, default=True)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

    def set_password(self, password: str):
        """Genera el hash seguro de la contraseña."""
        self.password_hash = generate_password_hash(password)

    def check_password(self, password: str) -> bool:
        """Verifica la contraseña ingresada contra el hash almacenado."""
        return check_password_hash(self.password_hash, password)

    def to_dict(self):
        return {
            "id": str(self.id),
            "name": self.name,
            "email": self.email,
            "role": self.role,
            "user_type": self.user_type,
            "is_active": self.is_active,
            "created_at": self.created_at.isoformat() if self.created_at else None
        }