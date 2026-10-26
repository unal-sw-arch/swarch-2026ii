import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../context/AuthContext';
import { RoleName } from '../types/auth';
import { LoadingSpinner } from '../shared/components/ui/LoadingSpinner';

export interface RoleGuardProps {
  requiredRoles?: RoleName[];
  forbiddenRoles?: RoleName[];
  children: React.ReactNode;
}

export function RoleGuard({
  requiredRoles,
  forbiddenRoles,
  children,
}: RoleGuardProps): React.JSX.Element {
  const { isAuthenticated, isLoading, profile } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <LoadingSpinner label="Verificando permisos…" fullScreen />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  const userRoles = profile?.roles || ['Miembro de la Comunidad'];

  // Si hay roles prohibidos y el usuario tiene alguno de ellos
  if (forbiddenRoles && forbiddenRoles.some((role) => userRoles.includes(role))) {
    return <Navigate to="/unauthorized" replace />;
  }

  // Si hay roles requeridos y el usuario no tiene ninguno de ellos
  if (requiredRoles && requiredRoles.length > 0) {
    const hasRequired = requiredRoles.some((role) => userRoles.includes(role));
    if (!hasRequired) {
      return <Navigate to="/unauthorized" replace />;
    }
  }

  return <>{children}</>;
}
