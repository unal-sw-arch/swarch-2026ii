import Link from 'next/link';
import { getSession } from '@/lib/auth';
import { readCart } from '@/lib/cart';
import { can, canAny, isStaff } from '@/lib/permissions';
import LogoutButton from './LogoutButton';

export default function NavBar() {
  const session = getSession();
  const cartCount = Object.values(readCart()).reduce((total, quantity) => total + quantity, 0);

  return (
    <header className="site-header">
      <Link className="brand" href="/">
        <span aria-hidden="true">🥐</span>
        <span>Panadería Express</span>
      </Link>

      <nav className="main-nav" aria-label="Navegación principal">
        <Link href="/">Inicio</Link>
        {session ? <Link href="/menu">Menú</Link> : null}

        {session ? (
          <>
            {can(session, 'orders:create_own') ? (
              <Link href="/cart">Carrito{cartCount > 0 ? ` (${cartCount})` : ''}</Link>
            ) : null}
            {canAny(session, ['orders:read_own', 'orders:read_all']) ? (
              <Link href="/orders">
                {can(session, 'orders:read_all') ? 'Pedidos' : 'Mis pedidos'}
              </Link>
            ) : null}
            {canAny(session, ['reservations:manage_own', 'reservations:manage_all']) ? (
              <Link href="/reservations">Reservas</Link>
            ) : null}
            {isStaff(session) ? <Link href="/staff">Backoffice</Link> : null}
            <span className="session-chip" title={session.email}>
              {session.email}
              {session.roles.length > 0 ? ` · ${session.roles.join(' / ')}` : ''}
            </span>
            <LogoutButton />
          </>
        ) : (
          <>
            <Link href="/login">Ingresar</Link>
            <Link className="button-link" href="/register">
              Crear cuenta
            </Link>
          </>
        )}
      </nav>
    </header>
  );
}
