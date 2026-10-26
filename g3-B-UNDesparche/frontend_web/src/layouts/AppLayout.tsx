import React, { useState, useEffect } from 'react';
import { Link, NavLink, Outlet, useNavigate, useLocation } from 'react-router';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { SkipLink } from '../shared/components/ui/SkipLink';
import { useTheme } from '../context/ThemeContext';

export function AppLayout({ children }: { children?: React.ReactNode }): React.JSX.Element {
  const { isAuthenticated, profile, firebaseUser, logout, hasRole } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMobileMenuOpen(false);
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const isEventAdmin = hasRole('Administrador de Eventos');
  const isEquipmentAdmin = hasRole('Administrador de Implementos');
  const isSystemAdmin = hasRole('Administrador del Sistema');
  const hasAnyAdminRole = isEventAdmin || isEquipmentAdmin || isSystemAdmin;

  const adminPath = isSystemAdmin
    ? '/admin/system/events'
    : isEventAdmin
      ? '/admin/events'
      : isEquipmentAdmin
        ? '/admin/equipment'
        : '/';

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `inline-flex items-center px-3 py-1.5 text-xs sm:text-sm font-semibold rounded-full transition-all duration-200 focus-visible:outline-2 focus-visible:outline-[#00D592] ${
      isActive
        ? 'bg-[#00D592]/15 text-[#00875A] dark:text-[#00F5A0] font-bold shadow-xs ring-1 ring-[#00D592]/30'
        : 'text-neutral-700 dark:text-neutral-300 hover:text-neutral-950 dark:hover:text-white hover:bg-black/[0.04] dark:hover:bg-white/[0.06]'
    }`;

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  const displayName = profile?.name || firebaseUser?.displayName || 'Estudiante UNAL';
  const initial = displayName.charAt(0).toUpperCase() || 'U';

  return (
    <div className="flex min-h-screen flex-col bg-white dark:bg-[#080E14] text-foreground font-sans transition-colors duration-200">
      <SkipLink />

      {/* Main Navbar: Floating Island (Linear / Raycast / Apple Style) */}
      <header className="fixed top-0 left-0 right-0 z-50 w-full pt-3.5 px-4 sm:px-6 pointer-events-none">
        <div className="pointer-events-auto mx-auto flex h-14 max-w-5xl items-center justify-between rounded-full border border-black/[0.08] dark:border-white/[0.1] bg-white/80 dark:bg-[#080E14]/85 backdrop-blur-2xl shadow-[0_8px_32px_rgba(0,0,0,0.06)] dark:shadow-[0_8px_32px_rgba(0,0,0,0.35)] px-3.5 sm:px-5 transition-all duration-300">
          {/* Brand Logo & Badges */}
          <div className="flex items-center gap-4 sm:gap-6">
            <Link
              to="/"
              className="group flex items-center gap-2.5 font-display text-base sm:text-lg font-bold tracking-tight text-neutral-900 dark:text-white focus-visible:outline-2 focus-visible:outline-[#00D592] rounded-full"
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-tr from-[#00D592] to-[#00F5A0] text-[#032B1D] shadow-xs shadow-[#00D592]/30 transition-transform duration-200 group-hover:scale-105">
                <span className="material-symbols-outlined text-lg font-bold" aria-hidden="true">
                  local_activity
                </span>
              </span>
              <div className="flex flex-col">
                <span className="font-extrabold tracking-tight text-sm sm:text-base leading-none">UNDesparche</span>
                <span className="hidden text-[9px] font-medium text-neutral-500 dark:text-neutral-400 leading-none sm:inline mt-0.5">
                  UNAL · Sede Bogotá
                </span>
              </div>
            </Link>

            {/* Desktop Navigation */}
            <nav className="hidden md:flex md:items-center md:gap-1" aria-label="Navegación principal">
              <NavLink to="/events" className={navLinkClass}>
                Eventos
              </NavLink>
              <NavLink to="/equipment" className={navLinkClass}>
                Implementos
              </NavLink>
              <NavLink to="/map" className={navLinkClass}>
                Mapa Campus
              </NavLink>
            </nav>
          </div>

          {/* User actions & Theme Switcher */}
          <div className="hidden md:flex md:items-center md:gap-2.5">
            {/* Theme Toggle Button */}
            <button
              type="button"
              onClick={toggleTheme}
              aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-black/[0.07] bg-black/[0.03] text-neutral-600 transition-colors hover:border-[#00D592]/40 hover:text-[#00BA7C] dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-neutral-300 dark:hover:text-[#00F5A0] focus-visible:outline-2 focus-visible:outline-[#00D592]"
            >
              <span className="material-symbols-outlined text-base" aria-hidden="true">
                {isDark ? 'light_mode' : 'dark_mode'}
              </span>
            </button>

            {isAuthenticated ? (
              <div className="flex items-center gap-2">
                {hasAnyAdminRole && (
                  <Link
                    to={adminPath}
                    className="inline-flex items-center gap-1.5 rounded-full bg-[#00D592]/12 px-3 py-1 text-xs font-semibold text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/30 hover:bg-[#00D592]/20 transition-all focus-visible:outline-2 focus-visible:outline-[#00D592]"
                  >
                    <span className="material-symbols-outlined text-sm" aria-hidden="true">
                      admin_panel_settings
                    </span>
                    <span className="hidden lg:inline">Panel Admin</span>
                  </Link>
                )}

                <div className="flex items-center gap-2 rounded-full border border-black/[0.07] bg-black/[0.02] py-0.5 pl-1 pr-2.5 dark:border-white/[0.08] dark:bg-white/[0.04]">
                  <div className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-to-tr from-[#00D592] to-[#00BA7C] text-xs font-bold text-[#032B1D] shadow-xs ring-2 ring-[#00D592]/30">
                    {initial}
                  </div>
                  <div className="flex flex-col text-left">
                    <span className="max-w-[100px] truncate text-xs font-bold text-neutral-900 dark:text-neutral-100">
                      {displayName}
                    </span>
                    <span className="text-[9px] text-neutral-500 dark:text-neutral-400 capitalize leading-none">
                      {profile?.roles?.[0] || 'Estudiante'}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={handleLogout}
                  aria-label="Cerrar sesión"
                  title="Cerrar sesión"
                  className="inline-flex h-8 w-8 items-center justify-center rounded-full text-neutral-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/30 transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
                >
                  <span className="material-symbols-outlined text-lg" aria-hidden="true">
                    logout
                  </span>
                </button>
              </div>
            ) : (
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 rounded-full bg-[#00D592] px-3.5 py-1.5 text-xs font-bold text-neutral-950 shadow-xs hover:bg-[#00C082] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
              >
                <span className="material-symbols-outlined text-base" aria-hidden="true">
                  login
                </span>
                <span>Ingresar</span>
              </Link>
            )}
          </div>

          {/* Mobile menu and theme button */}
          <div className="flex items-center gap-1.5 md:hidden">
            <button
              type="button"
              onClick={toggleTheme}
              aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-black/[0.07] text-neutral-600 dark:border-white/[0.08] dark:text-neutral-300"
            >
              <span className="material-symbols-outlined text-base">
                {isDark ? 'light_mode' : 'dark_mode'}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label={mobileMenuOpen ? 'Cerrar menú' : 'Abrir menú'}
              aria-expanded={mobileMenuOpen}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-black/[0.07] text-neutral-700 dark:border-white/[0.08] dark:text-neutral-200 focus-visible:outline-2 focus-visible:outline-[#00D592]"
            >
              <span className="material-symbols-outlined text-xl" aria-hidden="true">
                {mobileMenuOpen ? 'close' : 'menu'}
              </span>
            </button>
          </div>
        </div>

        {/* Mobile dropdown with Floating Island Sheet */}
        <AnimatePresence>
          {mobileMenuOpen && (
            <>
              {/* Backdrop */}
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.2 }}
                onClick={() => setMobileMenuOpen(false)}
                className="pointer-events-auto fixed inset-0 z-40 bg-black/40 backdrop-blur-xs md:hidden"
                aria-hidden="true"
              />

              {/* Floating Mobile Card Sheet */}
              <motion.div
                initial={{ opacity: 0, y: -8, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -8, scale: 0.98 }}
                transition={{ type: 'spring', duration: 0.3, bounce: 0 }}
                className="pointer-events-auto fixed top-20 left-4 right-4 z-50 mx-auto max-w-sm rounded-3xl border border-black/[0.08] dark:border-white/[0.1] bg-white/95 dark:bg-[#080E14]/95 backdrop-blur-2xl shadow-2xl p-4 md:hidden"
              >
                <nav className="flex flex-col gap-1.5" aria-label="Navegación móvil">
                  <NavLink
                    to="/events"
                    onClick={() => setMobileMenuOpen(false)}
                    className={navLinkClass}
                  >
                    Eventos
                  </NavLink>
                  <NavLink
                    to="/equipment"
                    onClick={() => setMobileMenuOpen(false)}
                    className={navLinkClass}
                  >
                    Implementos
                  </NavLink>
                  <NavLink
                    to="/map"
                    onClick={() => setMobileMenuOpen(false)}
                    className={navLinkClass}
                  >
                    Mapa Campus
                  </NavLink>

                  {isAuthenticated ? (
                    <div className="mt-3 border-t border-black/[0.06] dark:border-white/[0.08] pt-3">
                      <div className="flex items-center gap-3 px-3 py-2">
                        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-tr from-[#00D592] to-[#00BA7C] text-sm font-bold text-[#032B1D]">
                          {initial}
                        </div>
                        <div className="flex flex-col">
                          <span className="text-sm font-bold text-neutral-900 dark:text-white">{displayName}</span>
                          <span className="text-xs text-neutral-500">{profile?.roles?.[0] || 'Estudiante'}</span>
                        </div>
                      </div>

                      {hasAnyAdminRole && (
                        <Link
                          to={adminPath}
                          onClick={() => setMobileMenuOpen(false)}
                          className="mt-2 flex items-center gap-2 rounded-xl bg-[#00D592]/10 px-4 py-2.5 text-xs font-semibold text-[#00875A] dark:text-[#00F5A0] border border-[#00D592]/25"
                        >
                          <span className="material-symbols-outlined text-base" aria-hidden="true">
                            admin_panel_settings
                          </span>
                          Panel de Administración
                        </Link>
                      )}

                      <button
                        type="button"
                        onClick={() => {
                          setMobileMenuOpen(false);
                          handleLogout();
                        }}
                        className="mt-2 flex w-full items-center gap-2 rounded-xl px-4 py-2.5 text-left text-xs font-semibold text-red-600 hover:bg-red-50 dark:hover:bg-red-950/30"
                      >
                        <span className="material-symbols-outlined text-base" aria-hidden="true">
                          logout
                        </span>
                        Cerrar sesión
                      </button>
                    </div>
                  ) : (
                    <Link
                      to="/login"
                      onClick={() => setMobileMenuOpen(false)}
                      className="mt-3 flex items-center justify-center gap-2 rounded-full bg-[#00D592] px-4 py-2.5 text-center text-sm font-bold text-neutral-950 shadow-md hover:bg-[#00C082] transition-colors"
                    >
                      <span className="material-symbols-outlined text-lg" aria-hidden="true">
                        login
                      </span>
                      Iniciar sesión
                    </Link>
                  )}
                </nav>
              </motion.div>
            </>
          )}
        </AnimatePresence>
      </header>

      {/* Main Content Area */}
      <main id="main-content" className={`flex-1 ${location.pathname === '/' ? '' : 'pt-20'}`}>
        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            className="h-full"
          >
            {children || <Outlet />}
          </motion.div>
        </AnimatePresence>
      </main>

      {/* Institutional Footer: Clean & Legitimate */}
      <footer className="border-t border-black/5 bg-white/70 py-10 dark:border-white/5 dark:bg-[#080E14]/90 backdrop-blur-md">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex flex-col items-center justify-between gap-6 md:flex-row">
            <div className="flex flex-col items-center text-center md:items-start md:text-left">
              <div className="flex items-center gap-2">
                <span className="flex h-2 w-2 rounded-full bg-[#00D592]" />
                <span className="font-display font-bold text-base tracking-tight text-gray-900 dark:text-white">
                  UNDesparche · Universidad Nacional de Colombia
                </span>
              </div>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Iniciativa comunitaria estudiantil · Ciudad Universitaria de Bogotá
              </p>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-6 text-xs font-medium text-gray-600 dark:text-gray-300">
              <Link to="/events" className="hover:text-[#00BA7C] dark:hover:text-[#00F5A0] transition-colors">
                Eventos
              </Link>
              <Link to="/equipment" className="hover:text-[#00BA7C] dark:hover:text-[#00F5A0] transition-colors">
                Implementos
              </Link>
              <Link to="/map" className="hover:text-[#00BA7C] dark:hover:text-[#00F5A0] transition-colors">
                Mapa interactivo
              </Link>
              <Link to="/login" className="hover:text-[#00BA7C] dark:hover:text-[#00F5A0] transition-colors">
                Acceso Comunidad
              </Link>
            </div>
          </div>
          <div className="mt-8 border-t border-gray-200/60 pt-6 text-center text-xs text-gray-400 dark:border-gray-800">
            © {new Date().getFullYear()} Universidad Nacional de Colombia. Todos los derechos reservados.
          </div>
        </div>
      </footer>
    </div>
  );
}
