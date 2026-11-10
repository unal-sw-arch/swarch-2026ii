ARG PYTHON_BASE_IMAGE=python:3.13-slim
FROM ${PYTHON_BASE_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY shared/requirements.txt shared/requirements.txt
COPY interaction_worker/requirements.txt interaction_worker/requirements.txt
RUN pip install --no-cache-dir -r interaction_worker/requirements.txt \
    && useradd --create-home --uid 10001 app
COPY --chown=app:app shared shared
COPY --chown=app:app interaction_worker interaction_worker
USER app
EXPOSE 8092
CMD ["python", "-m", "interaction_worker.main"]
