FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY main.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /api-gateway .

FROM alpine:3.20
RUN adduser -D -H app
COPY --from=build /api-gateway /usr/local/bin/api-gateway
USER app
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/api-gateway"]
