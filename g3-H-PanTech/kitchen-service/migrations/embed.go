// Package migrations embebe los scripts SQL del esquema kitchen.
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS
