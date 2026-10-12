import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    PORT = int(os.getenv("PORT", 5000))
    JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "clave_secreta_super_segura_buffit")
    JWT_ALGORITHM = os.getenv("JWT_ALGORITHM", "HS256")
    API_GATEWAY_URL = os.getenv("API_GATEWAY_URL", "http://localhost:8000")