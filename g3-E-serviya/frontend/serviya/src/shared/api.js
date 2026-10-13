// Capa ligera de acceso a la API del backend (RF-001/002/004/005).
// Usa fetch nativo (no hay axios en el proyecto) y guarda el JWT en localStorage.

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
const TOKEN_KEY = 'serviya_token';

export function getApiImageUrl(path) {
    if (!path) return null;
    if (/^https?:\/\//i.test(path) || path.startsWith('data:')) return path;
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    const base = API_BASE.replace(/\/$/, '');
    return `${base}${normalizedPath}`;
}

// --- Token (JWT) ---
export function saveToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}
export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}
export function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}
export function isAuthenticated() {
    if (!getToken()) return false;
    try {
        const payload = JSON.parse(atob(getToken().split('.')[1]));
        return Math.floor(Date.now() / 1000) < payload.exp;
    } catch {
        return false;
    }
}

// Lee los roles del payload del JWT (el backend los incluye como claim "roles").
export function rolesFromToken(token = getToken()) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.roles ?? [];
    } catch {
        return [];
    }
}

// Id del usuario autenticado: el backend usa el userId como "sub" del JWT.
export function userIdFromToken(token = getToken()) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.sub ? Number(payload.sub) : null;
    } catch {
        return null;
    }
}

// Llama a la API y devuelve el JSON; si la respuesta es de error, lanza con el mensaje del backend.
// Si recibe 401 en una petición autenticada, limpia el token y redirige a login.
async function request(path, { method = 'GET', body, auth = false, formData = false } = {}) {
    const headers = {};
    if (!formData) {
        headers['Content-Type'] = 'application/json';
    }
    if (auth) {
        const token = getToken();
        if (token) headers.Authorization = `Bearer ${token}`;
    }

    const res = await fetch(`${API_BASE}${path}`, {
        method,
        headers,
        body: body ? (formData ? body : JSON.stringify(body)) : undefined,
    });

    // Interceptor de 401: si la petición era autenticada y el token expira/es inválido
    if (res.status === 401 && auth) {
        clearToken();
        if (typeof window !== 'undefined') {
            window.location.href = '/login';
        }
        throw new Error('Sesión expirada. Redirigiendo al login...');
    }

    const isJson = res.headers.get('content-type')?.includes('application/json');
    const data = isJson ? await res.json() : null;

    if (!res.ok) {
        const message = data?.message || `Error ${res.status}`;
        throw new Error(message);
    }
    return data;
}

// --- Endpoints ---
export const authApi = {
    // RF-001
    login: (email, password) =>
        request('/api/v1/auth/login', { method: 'POST', body: { email, password } }),
    // RF-002 / RF-004
    register: (payload) =>
        request('/api/v1/auth/register', { method: 'POST', body: payload }),

    // RF-003 — recuperación de contraseña por enlace de un solo uso.
    // 1) Pide el enlace (el backend responde 204 exista o no el correo, para no filtrar cuentas).
    requestPasswordReset: (email) =>
        request('/api/v1/auth/password-reset', { method: 'POST', body: { email } }),
    // 2) Valida el token del enlace (lanza si es inválido/expirado).
    validateResetToken: (token) =>
        request('/api/v1/auth/password-reset/validate', { method: 'POST', body: { token } }),
    // 3) Confirma la nueva contraseña (mínimo 8 caracteres).
    confirmPasswordReset: (token, newPassword) =>
        request('/api/v1/auth/password-reset/confirm', { method: 'POST', body: { token, newPassword } }),
};

export const profileApi = {
    // RF-005 — identidad tomada del JWT por el backend
    getMyProfile: () => request('/api/v1/users/me/profile', { auth: true }),

    // RF-006 — PATCH parcial: solo se envian los campos modificados.
    // El documento (tipo/numero) NO es editable y por eso nunca se manda.
    updateMyProfile: (payload) => request('/api/v1/users/me/profile', { method: 'PATCH', body: payload, auth: true }),
    updateMyProfilePhoto: (payload) => request('/api/v1/users/me/profile/photo', { method: 'PATCH', body: payload, auth: true, formData: true }),
    getOffererProfile: () => request('/api/v1/offerers/me', { auth: true }),
    updateOffererProfile: (payload) => request('/api/v1/offerers/me', { method: 'PATCH', body: payload, auth: true }),
    getProfile: (id) => request(`/api/v1/offerers/${id}`, { auth: true }),
    getUserById: (userId) => request(`/api/v1/users/${userId}`, { auth: false }),

    // RF-027 — perfil publico completo del oferente: identidad, especialidad, rating,
    // metricas de desempeño y servicios activos. Es PUBLICO: no requiere token (visitantes).
    getOffererPublicProfile: (id) => request(`/api/v1/offerers/${id}/public-profile`),
    changeMainAddress: (payload) => request(`/api/v1/users/me/main-address`, { method: 'PATCH', body: payload, auth: true }),
 
    // RF-007 — cambia la contraseña del usuario autenticado
    changePassword: (currentPassword, newPassword) =>
        request('/api/v1/users/me/password', {
            method: 'PATCH',
            auth: true,
            body: { currentPassword, newPassword },
        }),
};

// Cuenta del usuario autenticado (/users/me): roles y eliminación.
export const accountApi = {
    // RF-067 (vista propia): roles actuales del usuario.
    getMyRoles: () => request('/api/v1/users/me/roles', { auth: true }),

    // RF-010 / RF-011 — devuelven un JWT NUEVO que ya incluye el rol recién adquirido.
    // Hay que guardarlo (saveToken) para tener acceso inmediato sin volver a iniciar sesión.
    acquireOffererRole: () => request('/api/v1/users/me/roles/offerer', { method: 'POST', auth: true }),
    acquireClientRole: () => request('/api/v1/users/me/roles/client', { method: 'POST', auth: true }),

    // RF-008 — soft delete de la cuenta propia (cancela solicitudes y desactiva servicios).
    deleteMyAccount: () => request('/api/v1/users/me', { method: 'DELETE', auth: true }),
};

// Panel de administracion (requiere rol ADMIN).
export const adminApi = {
    searchUsers: (params = {}) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([k, v]) => {
            if (v !== undefined && v !== null && v !== '') qs.append(k, v);
        });
        const q = qs.toString();
        return request(`/api/v1/admin/users${q ? '?' + q : ''}`, { auth: true });
    },
    getUser: (id) => request(`/api/v1/admin/users/${id}`, { auth: true }),
    createUser: (payload) => request('/api/v1/admin/users', { method: 'POST', body: payload, auth: true }),

    // RF-068 — edicion parcial (el documento NO es editable, por eso nunca se envia).
    updateUser: (id, payload) =>
        request(`/api/v1/admin/users/${id}`, { method: 'PATCH', body: payload, auth: true }),
    deleteUser: (id) => request(`/api/v1/admin/users/${id}`, { method: 'DELETE', auth: true }),
    banUser: (id, payload) => request(`/api/v1/admin/users/${id}/ban`, { method: 'POST', body: payload, auth: true }),
    unbanUser: (id) => request(`/api/v1/admin/users/${id}/unban`, { method: 'POST', auth: true }),

    // RF-067 — roles del usuario CON su fecha de concesion.
    getUserRoles: (id) => request(`/api/v1/admin/users/${id}/roles`, { auth: true }),
    // RF-065 — el admin es la unica via legitima para conceder ADMIN.
    grantRole: (id, role) =>
        request(`/api/v1/admin/users/${id}/roles`, { method: 'POST', body: { role }, auth: true }),
    // RF-066 — retirar rol: dispara la cascada conservadora en el backend.
    revokeRole: (id, role) =>
        request(`/api/v1/admin/users/${id}/roles/${role}`, { method: 'DELETE', auth: true }),
};

export const serviceApi = {
    getMyServices: (offererId) => request(`/api/v1/offerers/${offererId}/services`, { auth: true }),
    createService: (payload, formData = false) => request('/api/v1/services', { method: 'POST', body: payload, auth: true, formData }),
    updateService: (id, payload, formData = false) => request(`/api/v1/services/${id}`, { method: 'PATCH', body: payload, auth: true, formData }),
    deleteService: (id) => request(`/api/v1/services/${id}`, { method: 'DELETE', auth: true }),
    getServiceAvailability: (serviceId) => request(`/api/v1/service-availabilities/service/${serviceId}`, { auth: true }),
    createServiceAvailability: (serviceId, payload) => {
        const body = { ...payload, isActive: payload.active ?? true };
        delete body.active;
        return request(`/api/v1/service-availabilities/service/${serviceId}`, { method: 'POST', body, auth: true });
    },
    updateServiceAvailability: (id, payload) => {
        const body = { ...payload, isActive: payload.active ?? payload.isActive ?? true };
        delete body.active;
        return request(`/api/v1/service-availabilities/${id}`, { method: 'PUT', body, auth: true });
    },
    deleteServiceAvailability: (id) => request(`/api/v1/service-availabilities/${id}`, { method: 'DELETE', auth: true }),
    applyGeneralTemplateToService: (serviceId) => request(`/api/v1/service-availabilities/service/${serviceId}/apply-template`, { method: 'POST', auth: true }),
    searchServices: (params) => {
        const queryParams = new URLSearchParams();
        Object.entries(params || {}).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') {
                queryParams.append(key, val);
            }
        });
        return request(`/api/v1/services/search?${queryParams.toString()}`, { auth: true });
    },
    getServiceDetail: (id) => request(`/api/v1/services/${id}/detail`, { auth: true }),
};

export const addressApi = {
    getMyAddresses: () => request('/api/v1/users/me/addresses', { auth: true }),
    createAddress: (payload) => request('/api/v1/users/me/addresses', { method: 'POST', body: payload, auth: true }),
    updateAddress: (id, payload) => request(`/api/v1/addresses/${id}`, { method: 'PATCH', body: payload, auth: true }),
    deleteAddress: (id) => request(`/api/v1/addresses/${id}`, { method: 'DELETE', auth: true }),
};

export const requestApi = {
    createRequest: (payload) => request('/api/v1/service-requests', { method: 'POST', body: payload, auth: true }),

    // Detalle de una solicitud (solo participante o admin; el backend valida con el JWT).
    getRequestDetail: (id) => request(`/api/v1/service-requests/${id}`, { auth: true }),
    // Historial/línea de tiempo de la solicitud (cadena de reprogramaciones + transiciones de estado).
    getRequestHistory: (id) => request(`/api/v1/service-requests/${id}/history`, { auth: true }),
    getMyClientRequests: (params = {}) => {
        const queryParams = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') {
                queryParams.append(key, val);
            }
        });
        const qs = queryParams.toString();
        return request(`/api/v1/users/me/client-requests${qs ? '?' + qs : ''}`, { auth: true });
    },
    getOffererRequests: (params = {}) => {
        const queryParams = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') {
                if (Array.isArray(val)) {
                    val.forEach(v => queryParams.append(key, v));
                } else {
                    queryParams.append(key, val);
                }
            }
        });
        const qs = queryParams.toString();
        return request(`/api/v1/users/me/offerer-requests${qs ? '?' + qs : ''}`, { auth: true });
    },
    cancelRequest: (id) => request(`/api/v1/service-requests/${id}/cancel`, { method: 'POST', auth: true }),
    acceptRequest: (id) => request(`/api/v1/service-requests/${id}/accept`, { method: 'POST', auth: true }),
    rejectRequest: (id) => request(`/api/v1/service-requests/${id}/reject`, { method: 'POST', auth: true }),
    markCompleted: (id) => request(`/api/v1/service-requests/${id}/mark-completed`, { method: 'POST', auth: true }),
    confirmCompletion: (id) => request(`/api/v1/service-requests/${id}/confirm-completion`, { method: 'POST', auth: true }),
    rescheduleRequest: (id, payload) => request(`/api/v1/service-requests/${id}/reschedule`, { method: 'POST', body: payload, auth: true }),
    getClientAgenda: (params = {}) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') qs.append(key, val);
        });
        return request(`/api/v1/users/me/client-agenda${qs.toString() ? '?' + qs.toString() : ''}`, { auth: true });
    },
    getMyOffererRequests: (params = {}) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') qs.append(key, val);
        });
        return request(`/api/v1/users/me/offerer-requests${qs.toString() ? '?' + qs.toString() : ''}`, { auth: true });
    },
};

// RF-023/RF-034/RF-035/RF-036 — Propuestas de reprogramación
export const proposalApi = {
    createProposal: (payload) => request('/api/v1/reschedule-proposals', { method: 'POST', body: payload, auth: true }),
    acceptProposal: (id) => request(`/api/v1/reschedule-proposals/${id}/accept`, { method: 'POST', auth: true }),
    rejectProposal: (id) => request(`/api/v1/reschedule-proposals/${id}/reject`, { method: 'POST', auth: true }),
    cancelProposal: (id) => request(`/api/v1/reschedule-proposals/${id}/cancel`, { method: 'POST', auth: true }),
    // Detalle enriquecido de una propuesta (incluye requestId, dirección, precio, etc.).
    getProposalById: (id) => request(`/api/v1/reschedule-proposals/${id}`, { auth: true }),
    getReceived: (params = {}) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') qs.append(key, val);
        });
        return request(`/api/v1/users/me/proposals/received${qs.toString() ? '?' + qs.toString() : ''}`, { auth: true });
    },
    getSent: (params = {}) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') qs.append(key, val);
        });
        return request(`/api/v1/users/me/proposals/sent${qs.toString() ? '?' + qs.toString() : ''}`, { auth: true });
    },
};

export const availabilityApi = {
    getMyAvailability: () => request('/api/v1/offerers/me/availability', { auth: true }),
    saveMyAvailability: (slots) => request('/api/v1/offerers/me/availability', { method: 'PUT', body: slots, auth: true }),
};

export const categoryApi = {
    getCategories: () => request('/api/v1/categories', { auth: true }),
}

export const platformApi = {
    getStats: () => request('/api/v1/platform/stats', { auth: false }),
};

export const clientAgendaApi = {
    getClientAgenda: () => request('/api/v1/users/me/client-agenda', { auth: true }),
};

export const offererAgendaApi = {
    getOffererAgenda: () => request('/api/v1/users/me/offerer-agenda', { auth: true }),
};

export const reportApi = {
    createRequestReport: (payload) => request('/api/v1/reports/requests', { method: 'POST', body: payload, auth: true }),
    createServiceFeedbackReport: (payload) => request('/api/v1/reports/service-feedback', { method: 'POST', body: payload, auth: true }),
    createClientFeedbackReport: (payload) => request('/api/v1/reports/client-feedback', { method: 'POST', body: payload, auth: true }),
    getAll: (page = 0, size = 20, status) => {
        const qs = new URLSearchParams({ page, size });
        if (status) qs.set('status', status);
        return request(`/api/v1/reports?${qs.toString()}`, { auth: true });
    },
    // Detalle enriquecido (partes + payload del subtipo request/feedback) e historial de acciones.
    getById: (id) => request(`/api/v1/reports/${id}`, { auth: true }),
    getActions: (id) => request(`/api/v1/reports/${id}/actions`, { auth: true }),
    // RF: reportes enviados por un usuario (para "Mis reportes"). Los recibidos NO se exponen.
    getSentByUser: (userId) => request(`/api/v1/users/${userId}/reports/sent`, { auth: true }),
};

export const userApi = {
    getDisplayName: async (id) => {
        if (!id) return 'Usuario sin asignar';

        try {
            const data = await request(`/api/v1/offerers/${id}/summary`, { auth: true });
            if (data.fullName || data.name) {
                return data.fullName || data.name;
            }
        } catch {
            // Intenta con el perfil del usuario autenticado si no existe resumen de oferente.
        }

        try {
            const data = await request('/api/v1/users/me/profile', { auth: true });
            if (data.fullName || data.name) {
                return data.fullName || data.name;
            }
        } catch {
            // Si no existe un perfil cargado, conserva el valor por defecto.
        }

        return `Usuario ${id}`;
    },
};

export const metricsApi = {
    getMyMetrics: () => request('/api/v1/users/me/metrics', { auth: true }),
    getOffererMetrics: (id) => request(`/api/v1/offerers/${id}/metrics`),
    getClientTagMetrics: (clientId) => request(`/api/v1/clients/${clientId}/tag-metrics`, { auth: true }),
    getOffererTagMetrics: (offererId) => request(`/api/v1/offerers/${offererId}/tag-metrics`, { auth: true }),
};

export const notificationApi = {
    getNotifications: (params = {}) => {
        const qs = new URLSearchParams();
        if (params.read !== undefined) qs.set('read', params.read);
        if (params.channelId) qs.set('channelId', params.channelId);
        if (params.status) qs.set('status', params.status);
        if (params.page !== undefined) qs.set('page', params.page);
        if (params.size) qs.set('size', params.size);
        const query = qs.toString();
        return request(`/api/v1/notifications${query ? '?' + query : ''}`, { auth: true });
    },
    markAsRead: (id) =>
        request(`/api/v1/notifications/${id}/read`, { method: 'POST', auth: true }),
    getChannels: () =>
        request('/api/v1/notification-channels', { auth: true }),
};

// RF-048 — Búsqueda combinada de feedback por parte del admin
export const adminFeedbackApi = {
    search: (params = {}) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') qs.append(key, val);
        });
        return request(`/api/v1/admin/feedback?${qs.toString()}`, { auth: true });
    },
    removeDirect: (payload) =>
        request('/api/v1/admin/feedback/remove', { method: 'POST', body: payload, auth: true }),
};

// RF-049 — Acciones de moderación sobre reportes
export const moderationApi = {
    warnUser: (reportId) =>
        request(`/api/v1/reports/${reportId}/actions/warn`, { method: 'POST', auth: true }),
    banUser: (reportId) =>
        request(`/api/v1/reports/${reportId}/actions/ban`, { method: 'POST', auth: true }),
    revertFeedback: (reportId) =>
        request(`/api/v1/reports/${reportId}/actions/revert-feedback`, { method: 'POST', auth: true }),
    closeReport: (reportId) =>
        request(`/api/v1/reports/${reportId}/actions/close`, { method: 'POST', auth: true }),
    markNotProvided: (reportId) =>
        request(`/api/v1/reports/${reportId}/actions/mark-not-provided`, { method: 'POST', auth: true }),
};

// RF-041/RF-045 — Feedback de servicio (cliente califica servicio)
// RF-043/RF-044 — Feedback de cliente (oferente califica cliente)
export const feedbackApi = {
    submitServiceFeedback: (requestId, payload) =>
        request(`/api/v1/service-requests/${requestId}/feedback`, { method: 'POST', body: payload, auth: true }),
    submitClientFeedback: (requestId, payload) =>
        request(`/api/v1/service-requests/${requestId}/client-feedback`, { method: 'POST', body: payload, auth: true }),
    getServiceFeedback: (requestId) =>
        request(`/api/v1/service-requests/${requestId}/feedback`, { auth: true }),
    getClientFeedback: (requestId) =>
        request(`/api/v1/service-requests/${requestId}/client-feedback`, { auth: true }),
    // Booleano explícito de existencia (evita depender de un 404) para mostrar/ocultar
    // los botones "Calificar servicio"/"Calificar cliente".
    serviceFeedbackExists: (requestId) =>
        request(`/api/v1/service-requests/${requestId}/feedback/exists`, { auth: true }),
    clientFeedbackExists: (requestId) =>
        request(`/api/v1/service-requests/${requestId}/client-feedback/exists`, { auth: true }),
};

// RF-064 — Eliminar servicio desde el panel de administración
export const adminServiceApi = {
    search: (params = {}) => {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([key, val]) => {
            if (val !== undefined && val !== null && val !== '') qs.append(key, val);
        });
        return request(`/api/v1/services/search?${qs.toString()}`, { auth: true });
    },
    deleteService: (id) => request(`/api/v1/admin/services/${id}`, { method: 'DELETE', auth: true }),
};

// Ruta de inicio según el rol devuelto por el backend.
export function homePathForRoles(roles = []) {
    if (roles.includes('ADMIN')) return '/admin/dashboard';
    if (roles.includes('OFFERER')) return '/offerer/dashboard';
    return '/dashboard';
}
