FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

# Primero solo requirements.txt: así la capa de dependencias se cachea
# y no se reinstala cada vez que cambias codigo.
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

# settings.py espera la BD en /app/db.sqlite3. Se enlaza a /data (un volumen
# con nombre) para que los datos sobrevivan a "docker compose down".
RUN mkdir -p /data && ln -sf /data/db.sqlite3 /app/db.sqlite3
VOLUME ["/data"]

EXPOSE 8000

# Aplica migraciones y arranca el servidor de desarrollo de Django.
CMD ["sh", "-c", "python manage.py migrate --noinput && python manage.py seed_events && python manage.py runserver 0.0.0.0:8000 --noreload"]
