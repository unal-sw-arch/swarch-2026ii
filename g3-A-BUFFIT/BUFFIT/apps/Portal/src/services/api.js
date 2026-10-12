import axios from 'axios';

const AUTH_SERVICE_URL = import.meta.env.VITE_AUTH_SERVICE_URL || 'http://localhost:5000';

const api = axios.create({
  baseURL: AUTH_SERVICE_URL,
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

export const registerUser = async (userData) => {
  const response = await api.post('/register', userData);
  return response.data;
};

export const loginUser = async (credentials) => {
  const response = await api.post('/login', credentials);
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
export default api;