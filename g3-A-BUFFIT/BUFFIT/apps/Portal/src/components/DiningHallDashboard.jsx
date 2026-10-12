import React, { useEffect, useState } from 'react';
import api from '../services/api';

export const DiningHallDashboard = () => {
  const [orders, setOrders] = useState([]);

  const fetchOrders = async () => {
    try {
      // Solicita los pedidos del comedor actual
      const response = await api.get('/orders?dining_hall=Comedor Central UNAL');
      setOrders(response.data);
    } catch (err) {
      console.error('Error al cargar pedidos:', err);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  const handleDeliver = async (orderId) => {
    try {
      await api.patch(`/orders/${orderId}/deliver`);
      alert(`Orden #${orderId} marcada como entregada.`);
      fetchOrders(); // Recarga la lista
    } catch (err) {
      alert('Error al actualizar el estado del pedido.');
    }
  };

  return (
    <div className="dashboard-container">
      <h2>Panel del Comedor - Entregas de Almuerzos</h2>
      <table>
        <thead>
          <tr>
            <th># Orden</th>
            <th>Usuario ID</th>
            <th>Franja Horaria</th>
            <th>Estado</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => (
            <tr key={order.id}>
              <td>{order.id}</td>
              <td>{order.user_id}</td>
              <td>{order.pickup_time_slot}</td>
              <td><strong>{order.status}</strong></td>
              <td>
                {order.status === 'CONFIRMED' && (
                  <button onClick={() => handleDeliver(order.id)}>
                    Marcar Entregado
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};