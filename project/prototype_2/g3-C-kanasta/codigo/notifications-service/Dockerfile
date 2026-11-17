FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN go build -o /notifications-service .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=build /notifications-service /notifications-service
EXPOSE 8005
CMD ["/notifications-service"]
