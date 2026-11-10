ARG PYTHON_BASE_IMAGE=python:3.13-slim
FROM ${PYTHON_BASE_IMAGE}
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY shared/requirements.txt shared/requirements.txt
COPY interactions_service/requirements.txt interactions_service/requirements.txt
RUN pip install --no-cache-dir -r interactions_service/requirements.txt \
    && useradd --create-home --uid 10001 app
COPY --chown=app:app shared shared
COPY --chown=app:app interactions_service interactions_service
USER app
EXPOSE 8087
CMD ["python", "-m", "uvicorn", "interactions_service.main:app", "--host", "0.0.0.0", "--port", "8087"]
