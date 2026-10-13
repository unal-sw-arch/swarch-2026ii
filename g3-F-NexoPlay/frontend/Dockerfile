FROM node:24-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
ARG VITE_API_URL
ARG VITE_STREAMING_URL
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_STREAMING_URL=$VITE_STREAMING_URL
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80