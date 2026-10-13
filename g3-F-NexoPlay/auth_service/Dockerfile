FROM node:24-slim
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev
COPY src ./src
EXPOSE 8081
CMD ["node", "src/index.js"]