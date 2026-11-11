FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod .
COPY main.go .
RUN go build -o /api-gateway .

FROM alpine:3.20
COPY --from=build /api-gateway /api-gateway
EXPOSE 8000
CMD ["/api-gateway"]
