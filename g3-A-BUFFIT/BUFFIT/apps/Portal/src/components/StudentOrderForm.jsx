import React, { useState, useContext } from 'react';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';

export const StudentOrderForm = () => {
  const { user } = useContext(AuthContext);
  const [diningHall, setDiningHall] = useState('Comedor Central UNAL');
  const [pickupTimeSlot, setPickupTimeSlot] = useState('12:15 - 12:30');
  const [items, setItems] = useState([
    { product_name: 'Pechuga a la plancha', category: 'Proteína', quantity: 1 },
    { product_name: 'Arroz con verduras', category: 'Carbohidrato', quantity: 1 }
  ]);
  const [statusMessage, setStatusMessage] = useState(null);

  const handleSubmitOrder = async (e) => {
    e.preventDefault();
    setStatusMessage(null);

    const payload = {
      user_id: user.id,
      user_type: user.user_type || 'ESTUDIANTE', // APOYO_ALIMENTARIO, ESTUDIANTE, GENERAL
      dining_hall: diningHall,
      pickup_time_slot: pickupTimeSlot,
      items: items
    };

    try {
      const res = await api.post('/orders', payload);
      setStatusMessage({ type: 'success', text: `¡Pedido #${res.data.id} confirmado con exito!` });
    } catch (err) {
      const errorDetail = err.response?.data?.detail || err.response?.data?.error || 'Error al procesar el pedido';
      setStatusMessage({ type: 'error', text: errorDetail });
    }
  };

  return (
    <div className="order-form-container">
      <h2>Armar Menú del Día</h2>
      {statusMessage && (
        <div className={`alert ${statusMessage.type}`}>{statusMessage.text}</div>
      )}
      <form onSubmit={handleSubmitOrder}>
        <label>Comedor de Recogida:</label>
        <select value={diningHall} onChange={(e) => setDiningHall(e.target.value)}>
          <option value="Comedor Central UNAL">Comedor Central UNAL</option>
          <option value="Comedor Hemeroteca">Comedor Hemeroteca</option>
        </select>

        <label>Rango de Hora:</label>
        <select value={pickupTimeSlot} onChange={(e) => setPickupTimeSlot(e.target.value)}>
          <option value="12:00 - 12:15">12:00 - 12:15</option>
          <option value="12:15 - 12:30">12:15 - 12:30</option>
          <option value="12:30 - 12:45">12:30 - 12:45</option>
        </select>

        <button type="submit">Confirmar y Guardar Pedido</button>
      </form>
    </div>
  );
};