# Imagen para ejecutar el backend (Cloudflare Worker) en local mediante
# `wrangler dev`, que emula el runtime de Workers, D1 y el Durable Object
# sobre Miniflare. En producción el Worker NO corre en Docker: se despliega
# a la edge de Cloudflare con `wrangler deploy`.

FROM node:22-bookworm-slim

# Wrangler/Miniflare usan workerd, que necesita estas librerías del sistema.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Instalar dependencias primero para aprovechar la caché de capas.
COPY package.json package-lock.json ./
RUN npm ci

# Copiar el resto del código del backend (el .dockerignore excluye
# node_modules, .wrangler, clientes Flutter, etc.).
COPY . .

# Puerto por defecto de `wrangler dev`.
EXPOSE 8787

# Escuchar en 0.0.0.0 para que el servidor sea accesible fuera del contenedor.
CMD ["npx", "wrangler", "dev", "--ip", "0.0.0.0", "--port", "8787"]
