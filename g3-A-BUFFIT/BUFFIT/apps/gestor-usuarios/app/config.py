import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    PORT = int(os.getenv("PORT", 5002))
    SQLALCHEMY_DATABASE_URI = os.getenv(
        "DATABASE_URL",
        "postgresql+psycopg2://user_buffit:password_buffit@localhost:5432/bd_usuarios"
    )
    SQLALCHEMY_TRACK_MODIFICATIONS = False