
FROM node:22-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci --omit=dev

COPY env.js ./
COPY src ./src

ENV NODE_ENV=production

EXPOSE 8082

USER node

CMD ["npm", "start"]
