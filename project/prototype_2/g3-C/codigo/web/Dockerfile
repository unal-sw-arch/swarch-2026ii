FROM node:20-alpine
WORKDIR /app
COPY package.json .
RUN npm install --omit=dev
COPY src ./src
COPY views ./views
COPY public ./public
EXPOSE 8080
CMD ["npm", "start"]
