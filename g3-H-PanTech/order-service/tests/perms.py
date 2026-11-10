"""Conjuntos de permisos de prueba (según la matriz de roles del README)."""
CUSTOMER = ("orders:create_own", "orders:read_own", "orders:cancel_own", "reservations:manage_own")
WAITER = ("orders:create", "orders:charge", "reservations:manage_all")
SUPERVISOR = WAITER + ("orders:read_all", "history:read_all", "reports:read")
ADMIN = SUPERVISOR + ("products:manage",)
COOK = ("kitchen:read", "kitchen:update")
