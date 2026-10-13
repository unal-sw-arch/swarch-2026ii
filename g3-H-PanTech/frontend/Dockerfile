# Build stage: compila la SPA con Node
FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci || npm install

COPY . .

# Variables de entorno de Vite (se incrustan en el bundle al compilar).
ARG VITE_ORDER_API_URL=http://localhost:8000
ARG VITE_SCHEDULER_API_URL=http://localhost:8001
ARG VITE_POLL_INTERVAL_MS=3000
ENV VITE_ORDER_API_URL=$VITE_ORDER_API_URL \
    VITE_SCHEDULER_API_URL=$VITE_SCHEDULER_API_URL \
    VITE_POLL_INTERVAL_MS=$VITE_POLL_INTERVAL_MS

RUN npm run build

# Runtime stage: sirve los estáticos con Nginx
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
