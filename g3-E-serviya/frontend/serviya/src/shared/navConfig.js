/**
 * Sidebar navigation definitions per role. Shared across the dashboard pages
 * so the menu stays consistent. Icon names map to <Icon name="..." />.
 */

export const CLIENT_NAV = [
    {
        title: 'General',
        items: [
            { label: 'Mi panel', to: '/dashboard', icon: 'grid', end: true },
            { label: 'Buscar servicios', to: '/services', icon: 'search' },
        ],
    },
    {
        title: 'Mis solicitudes',
        items: [
            { label: 'Solicitudes activas', to: '/requests', icon: 'tasks' },
            { label: 'Agenda', to: '/schedule', icon: 'calendar' },
            { label: 'Historial', to: '/history', icon: 'history' },
            { label: 'Reprogramaciones', to: '/reschedules', icon: 'reschedule' },
        ],
    },
    {
        title: 'Mi cuenta',
        items: [
            { label: 'Mi perfil', to: '/profile', icon: 'user' },
            { label: 'Notificaciones', to: '/notifications', icon: 'bell' },
            { label: 'Mis reportes', to: '/my-reports', icon: 'alertTriangle' },
        ],
    },
];

export const OFFERER_NAV = [
    {
        title: 'Oferente',
        items: [
            { label: 'Mi panel', to: '/offerer/dashboard', icon: 'grid' },
            { label: 'Mis servicios', to: '/offerer/services', icon: 'wrench' },
            { label: 'Disponibilidad', to: '/offerer/availability', icon: 'calendar' },
        ],
    },
    {
        title: 'Solicitudes',
        items: [
            { label: 'Recibidas', to: '/offerer/requests', icon: 'tasks' },
            { label: 'Agenda', to: '/offerer/schedule', icon: 'calendar' },
            { label: 'Reprogramaciones', to: '/offerer/reschedules', icon: 'reschedule' },
            { label: 'Historial', to: '/offerer/history', icon: 'history' },
        ],
    },
    {
        title: 'Mi cuenta',
        items: [
            { label: 'Mi perfil', to: '/profile', icon: 'user' },
            { label: 'Notificaciones', to: '/notifications', icon: 'bell' },
            { label: 'Mis reportes', to: '/my-reports', icon: 'alertTriangle' },
        ],
    },
];
