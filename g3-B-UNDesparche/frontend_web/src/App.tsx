import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router';
import { AuthProvider } from './context/AuthContext';
import { AppLayout } from './layouts/AppLayout';
import { AdminLayout } from './layouts/AdminLayout';
import { RoleGuard } from './guards/RoleGuard';

// Vistas públicas
import { LandingPage } from './modules/landing/LandingPage';
import { EventsListPage } from './modules/events/EventsListPage';
import { EventDetailPage } from './modules/events/EventDetailPage';
import { UnsubscribePage } from './modules/events/UnsubscribePage';
import { CampusMapPage } from './modules/map/CampusMapPage';

// Vistas de Implementos
import { EquipmentListPage } from './modules/equipment/EquipmentListPage';
import { EquipmentDetailPage } from './modules/equipment/EquipmentDetailPage';
import { ReservationPage } from './modules/equipment/ReservationPage';

// Vistas de Autenticación
import { LoginPage } from './modules/auth/LoginPage';
import { UnauthorizedPage } from './modules/auth/UnauthorizedPage';

// Vistas de Administración
import { EventsAdminPage } from './modules/admin/events/EventsAdminPage';
import { EquipmentAdminPage } from './modules/admin/equipment/EquipmentAdminPage';
import { SystemEventsPage } from './modules/admin/system/SystemEventsPage';
import { SystemUsersPage } from './modules/admin/system/SystemUsersPage';
import { SystemInventoryPage } from './modules/admin/system/SystemInventoryPage';

import { ToastProvider } from './shared/context/ToastContext';
import { ThemeProvider } from './context/ThemeContext';

export default function App(): React.JSX.Element {
  return (
    <ThemeProvider>
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
          {/* Rutas de Cara al Usuario (AppLayout) */}
          <Route element={<AppLayout />}>
            <Route path="/" element={<LandingPage />} />
            <Route path="/events" element={<EventsListPage />} />
            <Route path="/events/:id" element={<EventDetailPage />} />
            <Route path="/events/unsubscribe" element={<UnsubscribePage />} />
            <Route path="/map" element={<CampusMapPage />} />
            <Route path="/unauthorized" element={<UnauthorizedPage />} />

            {/* Implementos (Cualquier rol autenticado) */}
            <Route
              path="/equipment"
              element={
                <RoleGuard>
                  <EquipmentListPage />
                </RoleGuard>
              }
            />
            <Route
              path="/equipment/:id"
              element={
                <RoleGuard>
                  <EquipmentDetailPage />
                </RoleGuard>
              }
            />
            <Route
              path="/equipment/:id/reserve"
              element={
                <RoleGuard>
                  <ReservationPage />
                </RoleGuard>
              }
            />
          </Route>

          {/* Rutas de Administración: Administrador de Eventos */}
          <Route
            path="/admin/events"
            element={
              <RoleGuard requiredRoles={['Administrador de Eventos']}>
                <AdminLayout
                  sidebarItems={[
                    { label: 'Mis Eventos', path: '/admin/events', icon: 'calendar_month' },
                  ]}
                >
                  <EventsAdminPage />
                </AdminLayout>
              </RoleGuard>
            }
          />

          {/* Rutas de Administración: Administrador de Implementos */}
          <Route
            path="/admin/equipment"
            element={
              <RoleGuard requiredRoles={['Administrador de Implementos']}>
                <AdminLayout
                  sidebarItems={[
                    { label: 'Inventario Facultad', path: '/admin/equipment', icon: 'sports_soccer' },
                  ]}
                >
                  <EquipmentAdminPage />
                </AdminLayout>
              </RoleGuard>
            }
          />

          {/* Rutas de Administración: Administrador del Sistema */}
          <Route
            path="/admin/system"
            element={
              <RoleGuard requiredRoles={['Administrador del Sistema']}>
                <AdminLayout
                  sidebarItems={[
                    { label: 'Eventos Globales', path: '/admin/system/events', icon: 'event' },
                    { label: 'Usuarios y Roles', path: '/admin/system/users', icon: 'group' },
                    { label: 'Inventario General', path: '/admin/system/inventory', icon: 'inventory_2' },
                  ]}
                />
              </RoleGuard>
            }
          >
            <Route index element={<Navigate to="events" replace />} />
            <Route path="events" element={<SystemEventsPage />} />
            <Route path="users" element={<SystemUsersPage />} />
            <Route path="inventory" element={<SystemInventoryPage />} />
          </Route>

          {/* Login independiente */}
          <Route path="/login" element={<LoginPage />} />

          {/* Redirección por defecto */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
      </ToastProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}
