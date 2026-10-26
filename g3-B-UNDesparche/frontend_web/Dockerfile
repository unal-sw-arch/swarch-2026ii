FROM node:20-alpine

WORKDIR /app

# Copia de manifiestos para cachear la instalación de dependencias
COPY package*.json ./
RUN npm install

# Copia el código fuente
COPY . .
RUN [ -f .env ] || cp .env.example .env

EXPOSE 5172

CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5172"]
