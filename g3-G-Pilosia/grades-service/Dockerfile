FROM node:22-alpine

WORKDIR /service

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY src ./src

EXPOSE 4000
CMD ["node", "src/index.js"]
