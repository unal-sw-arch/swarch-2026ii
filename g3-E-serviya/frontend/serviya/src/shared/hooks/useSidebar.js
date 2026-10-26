import { useSyncExternalStore } from 'react';

/**
 * Store mínimo (module-level) del estado abierto/cerrado del sidebar en móvil. Se usa desde el
 * navbar (botón hamburguesa) y el sidebar (drawer) sin necesidad de un wrapper común — así funciona
 * igual para el layout de cliente/oferente (DashboardLayout) y el de admin (AdminNavbar/AdminSidebar).
 */
let isOpen = false;
const listeners = new Set();
const emit = () => listeners.forEach((l) => l());

export const sidebarStore = {
    toggle() { isOpen = !isOpen; emit(); },
    close() { isOpen = false; emit(); },
    getSnapshot: () => isOpen,
    subscribe(listener) {
        listeners.add(listener);
        return () => listeners.delete(listener);
    },
};

export function useSidebarOpen() {
    return useSyncExternalStore(sidebarStore.subscribe, sidebarStore.getSnapshot);
}
