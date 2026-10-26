FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1
# socat: el código de Pedidos llama a gRPC en "localhost:50051" (fijo en grpc_client.py).
# Dentro de Docker el inventario es otro contenedor, así que se reenvía ese puerto local hacia él.
RUN apt-get update && apt-get install -y --no-install-recommends socat \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
ENV INVENTORY_GRPC_TARGET=gestor-inventario:50051
EXPOSE 8004
CMD ["sh", "-c", "socat TCP-LISTEN:50051,fork,reuseaddr TCP:${INVENTORY_GRPC_TARGET} & exec python app.py"]
