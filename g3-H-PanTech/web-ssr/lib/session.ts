export type Session = {
  userId: string;
  email: string;
  roles: string[];
  permissions: string[];
  level: number;
  expiresAt: number;
};

export type AccessRule = {
  anyOf?: string[];
  staffOnly?: boolean;
};

const STAFF_ROLES = new Set(['admin', 'supervisor', 'mesero', 'cocinero', 'despachador']);

/**
 * Decodifica el payload del JWT solo para pintar la interfaz y decidir
 * redirecciones. La firma la validan el Gateway y cada servicio.
 */
export function decodeSession(token?: string | null): Session | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;

  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as {
      sub?: string;
      email?: string;
      roles?: string[];
      permissions?: string[];
      level?: number;
      exp?: number;
    };

    if (!payload.sub || !payload.exp) return null;

    return {
      userId: payload.sub,
      email: payload.email ?? '',
      roles: Array.isArray(payload.roles) ? payload.roles : [],
      permissions: Array.isArray(payload.permissions) ? payload.permissions : [],
      level: typeof payload.level === 'number' ? payload.level : 0,
      expiresAt: payload.exp * 1000,
    };
  } catch {
    return null;
  }
}

export function isExpired(session: Session, now = Date.now()): boolean {
  return session.expiresAt <= now;
}

export function isStaff(session: Session | null): boolean {
  return !!session && session.roles.some((role) => STAFF_ROLES.has(role));
}

export function homeFor(session: Session | null): string {
  return isStaff(session) ? '/staff' : '/';
}

export function can(session: Session | null, permission: string): boolean {
  return !!session?.permissions.includes(permission);
}

export function canAny(session: Session | null, permissions: string[]): boolean {
  return !!session && permissions.some((permission) => session.permissions.includes(permission));
}

/**
 * Reglas de acceso por prefijo de ruta. El middleware las aplica antes de
 * renderizar y cada página vuelve a comprobar la sesión en el servidor.
 */
const ACCESS_RULES: Array<{ prefix: string; rule: AccessRule }> = [
  { prefix: '/staff/orders', rule: { anyOf: ['orders:create', 'orders:charge'] } },
  { prefix: '/staff/reservations', rule: { anyOf: ['reservations:manage_all'] } },
  { prefix: '/staff/history', rule: { anyOf: ['history:read_all'] } },
  { prefix: '/staff/products', rule: { anyOf: ['products:manage'] } },
  { prefix: '/staff/users', rule: { anyOf: ['users:manage', 'users:manage_operational'] } },
  { prefix: '/staff/roles', rule: { anyOf: ['roles:manage'] } },
  { prefix: '/staff/reports', rule: { anyOf: ['reports:read'] } },
  { prefix: '/staff/kitchen', rule: { anyOf: ['kitchen:read'] } },
  { prefix: '/staff', rule: { staffOnly: true } },
  { prefix: '/cart', rule: { anyOf: ['orders:create_own'] } },
  { prefix: '/checkout', rule: { anyOf: ['orders:create_own'] } },
  { prefix: '/orders', rule: { anyOf: ['orders:read_own', 'orders:read_all'] } },
  { prefix: '/reservations', rule: { anyOf: ['reservations:manage_own', 'reservations:manage_all'] } },
];

export function accessRuleFor(pathname: string): AccessRule | null {
  const match = ACCESS_RULES.find(
    (entry) => pathname === entry.prefix || pathname.startsWith(`${entry.prefix}/`),
  );
  return match?.rule ?? null;
}

export function canAccess(session: Session | null, pathname: string): boolean {
  const rule = accessRuleFor(pathname);
  if (!rule) return true;
  if (!session) return false;
  if (rule.staffOnly && !isStaff(session)) return false;
  if (rule.anyOf && !canAny(session, rule.anyOf)) return false;
  return true;
}
