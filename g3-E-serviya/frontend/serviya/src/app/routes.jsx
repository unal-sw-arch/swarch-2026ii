import { createBrowserRouter } from "react-router-dom";
import App from "./App";
import { ErrorPage } from "./ErrorPage";
import { ProtectedRoute } from "../shared/components/ProtectedRoute";

import { HomePage, SearchPage, ServiceDetailPage, OffererServicesPage, OffererServiceDetailPage, AvailabilityPage } from "../modules/services";
import { LoginPage, RegisterPage, RecoverPasswordPage, ResetPasswordPage } from "../modules/users";
import { ProfilePage, OffererProfilePage } from "../modules/profiles";
import {
    ClientDashboardPage,
    OffererDashboardPage,
    ClientRequestsPage,
    ReschedulesPage,
    ClientSchedulePage,
    OffererSchedulePage,
    ClientHistoryPage,
    OffererHistoryPage,
    OffererRequestsPage,
    OffererReschedulesPage,
    RequestServicePage,
    RequestDetailPage,
} from "../modules/requests";
import { NotificationsPage } from "../modules/notifications";
import { MyReportsPage } from "../modules/reports";
import { AdminDashboardPage, AdminUsersPage, AdminReportsPage, AdminFeedbackPage, AdminServicesPage } from "../modules/admin";

const page = (element) => ({ element: <App>{element}</App> });
const protectedPage = (element, allowedRoles) => ({ element: <App><ProtectedRoute allowedRoles={allowedRoles}>{element}</ProtectedRoute></App> });

export const router = createBrowserRouter([
    {
        path: "/",
        errorElement: <ErrorPage />,
        children: [
            // Public
            { index: true, ...page(<HomePage />) },
            { path: "login", ...page(<LoginPage />) },
            { path: "register", ...page(<RegisterPage />) },
            { path: "recover", ...page(<RecoverPasswordPage />) },
            { path: "reset-password", ...page(<ResetPasswordPage />) },
            { path: "services", ...page(<SearchPage />) },
            { path: "services/:id", ...page(<ServiceDetailPage />) },
            { path: "offerers/:id", ...page(<OffererProfilePage />) },

            // Client (requires CLIENT role) - Request service wizard
            { path: "request-service/:serviceId", ...protectedPage(<RequestServicePage />, ['CLIENT']) },

            // Account (shared by roles - requires authentication)
            { path: "profile", ...protectedPage(<ProfilePage />) },
            { path: "notifications", ...protectedPage(<NotificationsPage />) },
            { path: "my-reports", ...protectedPage(<MyReportsPage />) },

            // Client (requires CLIENT role)
            { path: "dashboard", ...protectedPage(<ClientDashboardPage />, ['CLIENT']) },
            { path: "requests", ...protectedPage(<ClientRequestsPage />, ['CLIENT']) },
            { path: "requests/:id", ...protectedPage(<RequestDetailPage />, ['CLIENT']) },
            { path: "reschedules", ...protectedPage(<ReschedulesPage />, ['CLIENT']) },
            { path: "schedule", ...protectedPage(<ClientSchedulePage />, ['CLIENT']) },
            { path: "history", ...protectedPage(<ClientHistoryPage />, ['CLIENT']) },

            // Offerer (requires OFFERER role)
            { path: "offerer/dashboard", ...protectedPage(<OffererDashboardPage />, ['OFFERER']) },
            { path: "offerer/requests", ...protectedPage(<OffererRequestsPage />, ['OFFERER']) },
            { path: "offerer/services", ...protectedPage(<OffererServicesPage />, ['OFFERER']) },
            { path: "offerer/services/:id", ...protectedPage(<OffererServiceDetailPage />, ['OFFERER']) },
            { path: "offerer/availability", ...protectedPage(<AvailabilityPage />, ['OFFERER']) },
            { path: "offerer/schedule", ...protectedPage(<OffererSchedulePage />, ['OFFERER']) },
            { path: "offerer/reschedules", ...protectedPage(<OffererReschedulesPage />, ['OFFERER']) },
            { path: "offerer/history", ...protectedPage(<OffererHistoryPage />, ['OFFERER']) },

            // Admin (requires ADMIN role)
            { path: "admin/dashboard", ...protectedPage(<AdminDashboardPage />, ['ADMIN']) },
            { path: "admin/users", ...protectedPage(<AdminUsersPage />, ['ADMIN']) },
            { path: "admin/reports", ...protectedPage(<AdminReportsPage />, ['ADMIN']) },
            { path: "admin/feedback", ...protectedPage(<AdminFeedbackPage />, ['ADMIN']) },
            { path: "admin/services", ...protectedPage(<AdminServicesPage />, ['ADMIN']) },
        ],
    },
]);