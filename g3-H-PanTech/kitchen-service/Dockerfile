# Build multistage: compila un binario estático y lo copia a una imagen mínima.
FROM golang:1.26-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/kitchen ./cmd/kitchen

FROM alpine:3.20
# wget viene en busybox y lo usa el healthcheck del compose.
RUN apk add --no-cache ca-certificates && adduser -D -u 10001 app
USER app
COPY --from=build /out/kitchen /usr/local/bin/kitchen
EXPOSE 8001
ENTRYPOINT ["kitchen"]
