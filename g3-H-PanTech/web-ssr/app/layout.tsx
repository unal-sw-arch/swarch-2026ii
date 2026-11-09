import type { Metadata } from 'next';
import NavBar from '@/components/NavBar';
import './globals.css';

const appName = process.env.NEXT_PUBLIC_APP_NAME ?? 'Panadería Express';

export const metadata: Metadata = {
  title: {
    default: `${appName} · Pedidos y reservas`,
    template: `%s · ${appName}`,
  },
  description:
    'Panadería Express: menú, pedidos para llevar, reservas y backoffice del personal con autenticación y permisos.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>
        <NavBar />
        <div className="page-shell">{children}</div>
        <footer className="site-footer">
          <span>{appName}</span>
          <span>Prototipo 2 · Identidad y Web SSR</span>
        </footer>
      </body>
    </html>
  );
}
