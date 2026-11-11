# ---- build ----
FROM python:3.12-slim AS builder
WORKDIR /build
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ---- runtime ----
FROM python:3.12-slim AS runtime
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1
RUN useradd --create-home --uid 1000 app
WORKDIR /app
COPY --from=builder /opt/venv /opt/venv
COPY --chown=app:app . .
USER app
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=30s \
  CMD celery -A celery_app inspect ping --timeout 5 > /dev/null || exit 1
CMD ["sh", "-c", "celery -A celery_app worker --loglevel=INFO --concurrency=${WORKER_CONCURRENCY:-2}"]
