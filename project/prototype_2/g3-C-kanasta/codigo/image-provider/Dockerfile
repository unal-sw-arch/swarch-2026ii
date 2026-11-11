FROM node:20-alpine
WORKDIR /app
COPY package.json .
RUN npm install --omit=dev
COPY src ./src
COPY images ./images
EXPOSE 8003
CMD ["npm", "start"]
