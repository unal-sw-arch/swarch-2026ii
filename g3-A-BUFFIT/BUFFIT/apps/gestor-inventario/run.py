import uvicorn
from app.config import Config

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="0.0.0.0", port=Config.PORT)
