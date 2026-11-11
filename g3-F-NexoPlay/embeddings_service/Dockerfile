ARG PYTHON_BASE_IMAGE=python:3.13-slim
FROM ${PYTHON_BASE_IMAGE}

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 \
    HF_HUB_OFFLINE=1 HF_HUB_DISABLE_TELEMETRY=1 HF_HUB_DISABLE_PROGRESS_BARS=1
WORKDIR /app

COPY requirements.txt requirements-inference.txt ./
RUN pip install --no-cache-dir torch==2.14.0 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt -r requirements-inference.txt \
    && useradd --create-home --uid 10001 app

COPY requirements-state.txt ./
RUN pip install --no-cache-dir -r requirements-state.txt \
    && mkdir -p /var/lib/embeddings \
    && chown app:app /var/lib/embeddings

COPY --chown=app:app app ./app
COPY --chown=app:app models.json ./models.json
USER app

EXPOSE 8090
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
