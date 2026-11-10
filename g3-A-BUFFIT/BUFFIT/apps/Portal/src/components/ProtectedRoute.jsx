import React, { useContext } from 'react';
import { Navigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { getRole, homeFor } from '../utils/user';

/**
 * roles:        si se indica, solo esos roles pueden entrar.
 * excludeRoles: esos roles no pueden entrar (se les redirige a su página de inicio).
 **/
export default function ProtectedRoute({ children, roles, excludeRoles }) {
  const { user, loading } = useContext(AuthContext);
  if (loading) return <div>Cargando...</div>;
  if (!user) return <Navigate to="/login" replace />;

  const role = getRole(user);
  if ((roles && !roles.includes(role)) || (excludeRoles && excludeRoles.includes(role))) {
    return <Navigate to={homeFor(user)} replace />;
  }

  return children;
}
