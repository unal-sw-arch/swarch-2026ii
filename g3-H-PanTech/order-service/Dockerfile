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
# Git en Windows puede convertir los saltos de línea a CRLF y quitar el permiso de ejecución: se normaliza aquí.
RUN sed -i 's/\r$//' entrypoint.sh
USER app
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=5s --retries=10 --start-period=20s \
  CMD python -c "import os,urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://localhost:%s/health' % os.environ.get('PORT', '8000')).status == 200 else 1)"
CMD ["sh", "./entrypoint.sh"]
