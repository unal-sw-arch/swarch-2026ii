import axios from 'axios';

const API_GATEWAY_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8000';

const api = axios.create({
  baseURL: API_GATEWAY_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => {
    return response; 
  },
  async (error) => {
    const originalRequest = error.config;
    const url = originalRequest.url || '';

    if (url.includes('/users/login') || url.includes('/users/register')) {
       return Promise.reject(error);
    }

    // Refresh Token
    if (error.response && error.response.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const response = await axios.post(`${API_GATEWAY_URL}/users/refresh`, {
          refresh_token: refreshToken
        });
        
        const newAccessToken = response.data.token;
        localStorage.setItem('token', newAccessToken);
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);
        
      } catch (refreshError) {
        // Si el refresh también falla
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        
        if (refreshError.response && refreshError.response.status === 403) {
            window.location.href = '/login';
        } else {
            window.location.href = '/login';
        }
        return Promise.reject(refreshError);
      }
    }

    // Falta de permisos de acceso
    if (error.response && error.response.status === 403 && !originalRequest._retry) {
       console.warn("Acceso denegado: No tienes permisos para esta acción.");
    }

    return Promise.reject(error);
  }
);

export const registerUser = async (userData) => {
  const response = await api.post('/users/register', userData);
  return response.data;
};

export const loginUser = async (credentials) => {
  const response = await api.post('/users/login', credentials);
  return response.data;
};

// Petición para obtener las opciones del menú disponibles del día (desde el Inventario)
export const getMenuOpciones = async () => {
  const response = await api.get('/inventario/opciones');
  return response.data;
};

// Petición para crear una nueva orden en el Gestor de Pedidos
export const createOrder = async (orderData) => {
  const response = await api.post('/pedidos/crearOrden', orderData);
  return response.data;
};

// Obtener historial de pedidos del usuario autenticado
export const getUserOrders = async (userId) => {
  const response = await api.get(`/pedidos/user/${userId}`);
  return response.data;
};

export const cancelOrder = async (orderId) => {
  const response = await api.put(`/pedidos/${orderId}/cancel`);
  return response.data;
};

//--------------------------------Peticiones a Users--------------------------------- 
//Obtener lista de usuarios
export const getAllUsers = async () => {
  const response = await api.get(`/users/get`);
  return response.data;
};

//Inhabilitar un usuario
export const toggleUser = async (userId,action, reason=null) => {
  const response = await api.patch(`/users/toggle/${userId}/${action}`,{reason:reason});
  return response.data;
};

//Eliminar un usuario
export const deleteUser = async (userId) => {
  const response = await api.delete(`/users/delete/${userId}`);
  return response.data;
};

// ---------------------------------------------------------------- Rol Comedor
// Pedidos del comedor para hoy (o `fecha`), ordenados por franja de recogida
export const getDiningHallOrders = async (diningHall, { status, fecha } = {}) => {
  const response = await api.get('/pedidos/comedor', {
    params: { dining_hall: diningHall, status, fecha },
  });
  return response.data;
};

export const deliverOrder = async (orderId, diningHall) => {
  const response = await api.put(`/pedidos/${orderId}/deliver`, { dining_hall: diningHall });
  return response.data;
};

export const getInventoryItems = async (diningHall) => {
  const response = await api.get('/inventario/items', { params: { comedor: diningHall } });
  return response.data;
};

export const createInventoryItem = async (item) => {
  const response = await api.post('/inventario/items', item);
  return response.data;
};

export const updateInventoryItem = async (itemId, changes) => {
  const response = await api.put(`/inventario/items/${itemId}`, changes);
  return response.data;
};

export const createLeftoverReport = async (diningHall) => {
  const response = await api.post('/inventario/reportes/sobrantes', { dining_hall: diningHall });
  return response.data;
};

export const getLeftoverReports = async (diningHall) => {
  const response = await api.get('/inventario/reportes/sobrantes', { params: { comedor: diningHall } });
  return response.data;
};

// ---------------------------------------------------------------- Estadisticas (Comedor / Administrador)
const statsParams = ({ desde, hasta, comedor } = {}) => ({
  desde: desde || undefined, hasta: hasta || undefined, comedor: comedor || undefined,
});
const getStats = async (path, filters) => {
  const response = await api.get(`/estadisticas/${path}`, { params: statsParams(filters) });
  return response.data;
};
export const getStatsSummary = (filters) => getStats('resumen', filters);
export const getStatsByDiningHall = (filters) => getStats('por-comedor', filters);
export const getStatsBySlot = (filters) => getStats('por-franja', filters);
export const getStatsByUserType = (filters) => getStats('por-tipo-usuario', filters);
export const getStatsDaily = (filters) => getStats('serie-diaria', filters);

export default api;