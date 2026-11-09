package proxy

import (
	"context"
	"net/http"
)

type identityKey struct{}

func WithIdentity(ctx context.Context, h http.Header) context.Context {
	return context.WithValue(ctx, identityKey{}, h)
}

func IdentityFrom(ctx context.Context) http.Header {
	h, _ := ctx.Value(identityKey{}).(http.Header)
	return h
}
