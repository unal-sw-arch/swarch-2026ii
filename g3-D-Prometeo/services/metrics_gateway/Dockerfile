# Gateway de datos HTTP ↔ MongoDB para desarrollo local. Ver README.md.
FROM node:22-bookworm-slim

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev

COPY server.js ./

EXPOSE 8788

CMD ["node", "server.js"]
