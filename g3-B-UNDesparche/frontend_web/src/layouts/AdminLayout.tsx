import React, { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate, useLocation } from 'react-router';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { SkipLink } from '../shared/components/ui/SkipLink';
import { drawerBackdropVariants, drawerPanelVariants } from '../shared/motion/motionTokens';

export interface AdminSidebarItem {
  label: string;
  path: string;
  icon: string;
}

export interface AdminLayoutProps {
  sidebarItems?: AdminSidebarItem[];
  userName?: string;
  userRole?: string;
  photoURL?: string;
  onLogout?: () => void;
  children?: React.ReactNode;
}

export function AdminLayout({
  sidebarItems,
  userName,
  userRole,
  onLogout,
  children,
}: AdminLayoutProps): React.JSX.Element {
  const { profile, firebaseUser, logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const name = userName || profile?.name || firebaseUser?.displayName || 'Administrador';
  const role = userRole || profile?.roles?.[0] || 'Administrador';
  const initial = name.charAt(0).toUpperCase() || 'A';

  const defaultItems: AdminSidebarItem[] = sidebarItems || [
    { label: 'Eventos', path: '/admin/events', icon: 'calendar_month' },
    { label: 'Inventario', path: '/admin/equipment', icon: 'sports_soccer' },
    { label: 'Sistema', path: '/admin/system/events', icon: 'settings' },
  ];

  const handleLogout = async () => {
    if (onLogout) {
      onLogout();
    } else {
      await logout();
      navigate('/login');
    }
  };

  const sidebarContent = (
    <div className="flex h-full flex-col justify-between bg-white text-neutral-900 border-r border-neutral-200/80 dark:bg-[#080E14] dark:text-white dark:border-[#00D592]/15 font-sans transition-colors duration-200 overflow-hidden">
      <div className="flex flex-col min-h-0 flex-1">
        {/* Brand header */}
        <div className="flex h-16 items-center justify-between border-b border-neutral-200/80 px-4 dark:border-[#00D592]/15 gap-2 shrink-0">
          <Link
            to="/"
            onClick={() => setMobileSidebarOpen(false)}
            className="flex items-center gap-2 font-display text-base font-bold tracking-tight text-neutral-900 dark:text-white focus-visible:outline-2 focus-visible:outline-[#00D592] min-w-0"
          >
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-gradient-to-tr from-[#00D592] to-[#00F5A0] text-[#032B1D] shadow-sm">
              <span className="material-symbols-outlined text-lg font-bold" aria-hidden="true">
                admin_panel_settings
              </span>
            </span>
            <span className="truncate">UNDesparche</span>
          </Link>
          <div className="flex items-center gap-1.5 shrink-0">
            <span className="rounded-full bg-[#00D592]/15 px-2 py-0.5 font-bold text-[10px] text-[#00875A] dark:bg-[#00D592]/20 dark:text-[#00F5A0] border border-[#00D592]/30">
              ADMIN
            </span>
            {/* Theme Switcher Button in Sidebar Header */}
            <button
              type="button"
              onClick={toggleTheme}
              aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              className="inline-flex h-7 w-7 items-center justify-center rounded-full border border-neutral-200 bg-neutral-100 text-neutral-700 hover:border-[#00D592]/40 hover:text-[#00875A] dark:border-neutral-800 dark:bg-[#111A24] dark:text-neutral-300 dark:hover:text-[#00F5A0] transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
            >
              <span className="material-symbols-outlined text-sm">
                {isDark ? 'light_mode' : 'dark_mode'}
              </span>
            </button>
            {/* Botón cerrar para móvil */}
            <button
              type="button"
              onClick={() => setMobileSidebarOpen(false)}
              className="flex md:hidden rounded-lg p-1 text-neutral-500 hover:text-neutral-900 dark:text-gray-400 dark:hover:text-white hover:bg-neutral-100 dark:hover:bg-white/10"
              aria-label="Cerrar menú"
            >
              <span className="material-symbols-outlined text-xl">close</span>
            </button>
          </div>
        </div>

        {/* User profile card in sidebar */}
        <div className="border-b border-neutral-200/80 p-4 dark:border-gray-800/80 shrink-0">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-tr from-[#00D592] to-[#00BA7C] font-bold text-base text-[#032B1D] shadow-sm">
              {initial}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate font-bold text-sm text-neutral-900 dark:text-white">{name}</p>
              <p className="truncate text-xs text-neutral-500 dark:text-gray-400">{role}</p>
            </div>
          </div>
        </div>

        {/* Navigation list */}
        <nav className="p-3 overflow-y-auto flex-1" aria-label="Navegación de administración">
          <ul className="flex flex-col gap-1.5">
            {defaultItems.map((item) => (
              <li key={item.path}>
                <NavLink
                  to={item.path}
                  onClick={() => setMobileSidebarOpen(false)}
                  className={({ isActive }) =>
                    `flex items-center gap-3 rounded-2xl px-4 py-2.5 text-sm font-semibold transition-all duration-200 focus-visible:outline-2 focus-visible:outline-[#00D592] ${
                      isActive
                        ? 'bg-[#00D592]/15 text-[#00875A] dark:text-[#00F5A0] shadow-xs border border-[#00D592]/30 font-bold'
                        : 'text-neutral-700 hover:bg-neutral-100 hover:text-neutral-900 dark:text-gray-300 dark:hover:bg-white/5 dark:hover:text-white'
                    }`
                  }
                >
                  <span className="material-symbols-outlined text-xl" aria-hidden="true">
                    {item.icon}
                  </span>
                  <span>{item.label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </div>

      {/* Sidebar bottom links */}
      <div className="border-t border-neutral-200/80 p-4 dark:border-gray-800/80 shrink-0">
        <ul className="flex flex-col gap-1.5">
          <li>
            <Link
              to="/"
              onClick={() => setMobileSidebarOpen(false)}
              className="flex items-center gap-2.5 rounded-xl px-4 py-2 text-xs font-semibold text-neutral-700 hover:bg-neutral-100 hover:text-neutral-900 dark:text-gray-300 dark:hover:bg-white/5 dark:hover:text-white transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
            >
              <span className="material-symbols-outlined text-base" aria-hidden="true">
                arrow_back
              </span>
              <span>Volver a la App</span>
            </Link>
          </li>
          <li>
            <button
              type="button"
              onClick={handleLogout}
              className="flex w-full items-center gap-2.5 rounded-xl px-4 py-2 text-left text-xs font-semibold text-red-600 hover:bg-red-50 dark:text-red-300 dark:hover:bg-red-500/15 dark:hover:text-red-200 transition-colors focus-visible:outline-2 focus-visible:outline-[#00D592]"
            >
              <span className="material-symbols-outlined text-base" aria-hidden="true">
                logout
              </span>
              <span>Cerrar sesión</span>
            </button>
          </li>
        </ul>
      </div>
    </div>
  );

  return (
    <div className="flex h-screen w-full overflow-hidden bg-neutral-50/70 text-neutral-900 dark:bg-[#0A1016] dark:text-white font-sans transition-colors duration-200">
      <SkipLink />

      {/* Desktop Sidebar: stays pinned, never scrolls away */}
      <aside className="hidden w-72 shrink-0 md:flex md:flex-col h-full" aria-label="Barra lateral de administración">
        {sidebarContent}
      </aside>

      {/* Mobile Drawer */}
      <AnimatePresence>
        {mobileSidebarOpen && (
          <div className="fixed inset-0 z-50 flex md:hidden" role="dialog" aria-modal="true">
            <motion.div
              variants={drawerBackdropVariants}
              initial="initial"
              animate="animate"
              exit="exit"
              className="fixed inset-0 bg-black/60 backdrop-blur-sm"
              onClick={() => setMobileSidebarOpen(false)}
              aria-hidden="true"
            />
            <motion.div
              variants={drawerPanelVariants}
              initial="initial"
              animate="animate"
              exit="exit"
              className="relative z-10 w-72 max-w-[85vw] h-full shadow-2xl"
            >
              {sidebarContent}
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Main Content Area: independent vertical scroll container */}
      <div className="flex min-w-0 flex-1 flex-col h-full overflow-y-auto overflow-x-hidden">
        {/* Mobile top navbar with toggle */}
        <header className="flex h-16 items-center justify-between border-b border-neutral-200/80 bg-white/90 px-4 backdrop-blur-md md:hidden dark:border-[#00D592]/15 dark:bg-[#080E14]/90 sticky top-0 z-30 shrink-0">
          <button
            type="button"
            onClick={() => setMobileSidebarOpen(true)}
            aria-label="Abrir menú lateral de administración"
            className="rounded-full p-2 text-neutral-700 hover:bg-neutral-100 dark:text-neutral-300 dark:hover:bg-neutral-800 focus-visible:outline-2 focus-visible:outline-[#00D592]"
          >
            <span className="material-symbols-outlined text-2xl" aria-hidden="true">
              menu
            </span>
          </button>
          <span className="font-display font-bold text-sm text-[#00875A] dark:text-[#00F5A0] truncate max-w-[200px]">
            Panel de Administración
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={toggleTheme}
              aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-neutral-200 text-neutral-700 hover:bg-neutral-100 dark:border-neutral-800 dark:text-neutral-300 dark:hover:bg-neutral-800"
            >
              <span className="material-symbols-outlined text-base">
                {isDark ? 'light_mode' : 'dark_mode'}
              </span>
            </button>
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-tr from-[#00D592] to-[#00BA7C] text-xs font-bold text-[#032B1D]">
              {initial}
            </div>
          </div>
        </header>

        <main id="main-content" className="min-w-0 w-full flex-1 p-4 sm:p-6 lg:p-8">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
              className="min-h-full"
            >
              {children || <Outlet />}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  );
}
