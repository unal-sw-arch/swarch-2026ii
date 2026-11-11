from app import create_app
from app.config import Config

app = create_app()

if __name__ == "__main__":
    # use_reloader=False: el recargador de Flask duplicaria el hilo consumidor del broker
    app.run(host="0.0.0.0", port=Config.PORT, debug=False, use_reloader=False)
