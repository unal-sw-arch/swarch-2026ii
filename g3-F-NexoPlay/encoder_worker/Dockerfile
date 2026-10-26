ARG PYTHON_BASE_IMAGE=python:3.13-slim
FROM ${PYTHON_BASE_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 app
WORKDIR /app
COPY shared/requirements.txt shared/requirements.txt
COPY encoder_worker/requirements.txt encoder_worker/requirements.txt
RUN pip install --no-cache-dir -r encoder_worker/requirements.txt
COPY --chown=app:app shared shared
COPY --chown=app:app encoder_worker encoder_worker
USER app
EXPOSE 8091
CMD ["python", "-m", "encoder_worker.main"]
