import Link from 'next/link';
import { getSession } from '@/lib/auth';
import { readCart } from '@/lib/cart';
import { isStaff } from '@/lib/permissions';
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
        <Link href="/menu">Menú</Link>

        {session ? (
          <>
            <Link href="/cart">Carrito{cartCount > 0 ? ` (${cartCount})` : ''}</Link>
            <Link href="/orders">Mis pedidos</Link>
            <Link href="/reservations">Reservas</Link>
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
