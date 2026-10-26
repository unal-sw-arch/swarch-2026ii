# Build web del cliente administrativo (Flutter) servido por nginx.
#
# IMPORTANTE: admin_client depende de operational_client por path
# (pubspec: restaurante_operational -> ../operational_client). Por eso el
# CONTEXTO de build de esta imagen debe ser la carpeta `apps/`, no
# `apps/admin_client`. En docker-compose esto ya está configurado.
#
# La URL del backend se fija en tiempo de compilación con --dart-define y se
# pasa como build arg (una imagen = un entorno de API).

# ---------- Stage 1: build ----------
FROM debian:bookworm-slim AS build

ARG FLUTTER_VERSION=3.47.7
# URL del backend que el NAVEGADOR del usuario debe alcanzar (no la red interna
# de Docker). Para el stack local contenerizado es el Worker en localhost:8787.
ARG RESTAURANT_API_URL=http://localhost:8787

ENV DEBIAN_FRONTEND=noninteractive \
    FLUTTER_HOME=/opt/flutter \
    PATH=/opt/flutter/bin:/opt/flutter/bin/cache/dart-sdk/bin:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        git curl unzip xz-utils ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "${FLUTTER_VERSION}" \
        https://github.com/flutter/flutter.git "${FLUTTER_HOME}" \
    && git config --global --add safe.directory "${FLUTTER_HOME}" \
    && flutter --version \
    && flutter config --no-analytics \
    && flutter precache --web

WORKDIR /workspace
# Se copian ambos clientes porque admin depende de operational por path.
COPY operational_client ./operational_client
COPY admin_client ./admin_client

WORKDIR /workspace/admin_client
RUN flutter pub get \
    && flutter build web --release \
        --dart-define=RESTAURANT_API_URL="${RESTAURANT_API_URL}"

# ---------- Stage 2: serve ----------
FROM nginx:1.27-alpine AS serve

COPY admin_client/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/admin_client/build/web /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
