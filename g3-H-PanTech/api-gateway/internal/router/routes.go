package router

import (
	"net/http"
	"strings"
)

type Upstream string

const (
	Identity  Upstream = "identity"
	Order     Upstream = "order"
	Kitchen   Upstream = "kitchen"
	Reporting Upstream = "reporting"
)

type Access int

const (
	Public Access = iota
	Authenticated
	AnyPermission
	// Blocked no es alcanzable desde afuera (403).
	Blocked
)

// Rule: gana la primera regla que coincide.
type Rule struct {
	Methods []string // vacío = cualquiera
	Path    string   // vacío = cualquiera; si no, exacta
	Access  Access
	AnyOf   []string
}

type Route struct {
	Prefix   string
	Upstream Upstream
	Rules    []Rule
}

var Routes = []Route{
	{Prefix: "/auth", Upstream: Identity, Rules: []Rule{
		{Methods: []string{http.MethodPost}, Path: "/auth/login", Access: Public},
		{Methods: []string{http.MethodPost}, Path: "/auth/register", Access: Public},
		{Methods: []string{http.MethodPost}, Path: "/auth/refresh", Access: Public},
		{Access: Authenticated},
	}},
	{Prefix: "/users", Upstream: Identity, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{"users:manage", "users:manage_operational", "roles:manage"}},
	}},
	{Prefix: "/roles", Upstream: Identity, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{"users:manage", "users:manage_operational", "roles:manage"}},
	}},
	{Prefix: "/permissions", Upstream: Identity, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{"roles:manage"}},
	}},
	{Prefix: "/audit", Upstream: Identity, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{"users:manage"}},
	}},
	{Prefix: "/menu", Upstream: Order, Rules: []Rule{
		{Access: Authenticated},
	}},
	{Prefix: "/orders", Upstream: Order, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{
			"orders:create_own", "orders:read_own", "orders:create", "orders:charge", "orders:read_all",
		}},
	}},
	{Prefix: "/reservations", Upstream: Order, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{"reservations:manage_own", "reservations:manage_all"}},
	}},
	{Prefix: "/products", Upstream: Order, Rules: []Rule{
		{Methods: []string{http.MethodGet, http.MethodHead}, Access: Authenticated},
		{Access: AnyPermission, AnyOf: []string{"products:manage"}},
	}},
	{Prefix: "/queue", Upstream: Kitchen, Rules: []Rule{
		// Solo el Worker encola, directo a Kitchen.
		{Methods: []string{http.MethodPost}, Path: "/queue/orders", Access: Blocked},
		{Access: AnyPermission, AnyOf: []string{"kitchen:read", "kitchen:update", "kitchen:deliver"}},
	}},
	{Prefix: "/history", Upstream: Reporting, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{"history:read_own", "history:read_all"}},
	}},
	{Prefix: "/reports", Upstream: Reporting, Rules: []Rule{
		{Access: AnyPermission, AnyOf: []string{"reports:read"}},
	}},
}

func Match(path string) (Route, bool) {
	for _, r := range Routes {
		if path == r.Prefix || strings.HasPrefix(path, r.Prefix+"/") {
			return r, true
		}
	}
	return Route{}, false
}

// Decide deniega por defecto si ninguna regla coincide.
func (r Route) Decide(method, path string) Rule {
	for _, rule := range r.Rules {
		if rule.Path != "" && strings.TrimSuffix(path, "/") != rule.Path {
			continue
		}
		if len(rule.Methods) > 0 && !contains(rule.Methods, method) {
			continue
		}
		return rule
	}
	return Rule{Access: Blocked}
}

func contains(list []string, v string) bool {
	for _, item := range list {
		if item == v {
			return true
		}
	}
	return false
}
