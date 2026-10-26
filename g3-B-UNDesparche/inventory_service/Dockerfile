FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

RUN mkdir -p /data && ln -sf /data/db.sqlite3 /app/db.sqlite3
VOLUME ["/data"]

EXPOSE 8080

CMD ["sh", "-c", "python manage.py migrate --noinput && python manage.py seed_inventory && python manage.py runserver 0.0.0.0:8080 --noreload"]
