import { Navigate, useLocation } from 'react-router-dom';
import { isAuthenticated, rolesFromToken, homePathForRoles } from '../../../shared/api';

export function ProtectedRoute({ children, allowedRoles = [] }) {
    const location = useLocation();
    const authenticated = isAuthenticated();

    if (!authenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    const userRoles = rolesFromToken();
    const hasRequiredRole = allowedRoles.length === 0 || allowedRoles.some(role => userRoles.includes(role));

    if (!hasRequiredRole) {
        const redirectPath = homePathForRoles(userRoles);
        return <Navigate to={redirectPath} replace />;
    }

    return children;
}

export default ProtectedRoute;