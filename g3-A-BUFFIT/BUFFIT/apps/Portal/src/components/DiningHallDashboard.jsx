import React, { useCallback, useEffect, useState } from 'react';
import { getDiningHallOrders, deliverOrder } from '../services/api';
import DiningHallGate from './DiningHallGate';

const REFRESH_MS = 30000;

const STATUS_COLORS = {
  CONFIRMED: { bg: '#fff3cd', fg: '#856404' },
  DELIVERED: { bg: '#e8f5e9', fg: '#1b5e20' },
  CANCELLED: { bg: '#fde8e8', fg: '#9b1c1c' },
};

function OrdersTable({ hall }) {
  const [orders, setOrders] = useState([]);
  const [filter, setFilter] = useState('CONFIRMED');
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState({ text: '', type: '' });
  const [working, setWorking] = useState(null);

  const fetchOrders = useCallback(async () => {
    try {
      const data = await getDiningHallOrders(hall);
      setOrders(data);
    } catch (err) {
      console.error('Error al cargar pedidos:', err);
      setMessage({ text: err.response?.data?.error || 'No se pudieron cargar los pedidos.', type: 'error' });
    } finally {
      setLoading(false);
    }
  }, [hall]);

  useEffect(() => {
    fetchOrders();
    const timer = setInterval(fetchOrders, REFRESH_MS);
    return () => clearInterval(timer);
  }, [fetchOrders]);

  const handleDeliver = async (orderId) => {
    try {
      setWorking(orderId);
      setMessage({ text: '', type: '' });
      await deliverOrder(orderId, hall);
      setMessage({ text: `Orden #${orderId} marcada como entregada.`, type: 'success' });
      await fetchOrders();
    } catch (err) {
      setMessage({ text: err.response?.data?.error || 'Error al actualizar el estado del pedido.', type: 'error' });
    } finally {
      setWorking(null);
    }
  };

  const visible = orders.filter((o) => filter === 'ALL' || o.status === filter);
  const count = (s) => orders.filter((o) => o.status === s).length;

  return (
    <div className="card">
      <h2>Entregas de almuerzos de hoy</h2>

      <div style={{ display: 'flex', gap: '8px', margin: '12px 0', flexWrap: 'wrap' }}>
        {[
          ['CONFIRMED', `Sin entregar (${count('CONFIRMED')})`],
          ['DELIVERED', `Entregadas (${count('DELIVERED')})`],
          ['CANCELLED', `Canceladas (${count('CANCELLED')})`],
          ['ALL', `Todas (${orders.length})`],
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            className="btn"
            onClick={() => setFilter(value)}
            style={{ opacity: filter === value ? 1 : 0.55 }}
          >
            {label}
          </button>
        ))}
        <button type="button" className="btn" onClick={fetchOrders} style={{ marginLeft: 'auto' }}>
          Actualizar
        </button>
      </div>

      {message.text && (
        <div style={{
          padding: '10px 14px', marginBottom: '12px', borderRadius: '6px', fontWeight: 'bold',
          backgroundColor: message.type === 'error' ? '#fde8e8' : '#e8f5e9',
          color: message.type === 'error' ? '#9b1c1c' : '#1b5e20',
        }}>
          {message.text}
        </div>
      )}

      {loading ? (
        <p>Cargando pedidos...</p>
      ) : visible.length === 0 ? (
        <p>No hay pedidos en esta vista.</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '2px solid #ddd' }}>
              <th># Orden</th>
              <th>Franja de recogida</th>
              <th>Usuario</th>
              <th>Platos</th>
              <th>Estado</th>
              <th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((order) => {
              const colors = STATUS_COLORS[order.status] || {};
              return (
                <tr key={order.id} style={{ borderBottom: '1px solid #eee', verticalAlign: 'top' }}>
                  <td>{order.id}</td>
                  <td>{order.pickup_time_slot}</td>
                  <td>{order.user_id}</td>
                  <td style={{ fontSize: '0.9rem' }}>
                    {order.items.map((i) => i.product_name).join(', ')}
                  </td>
                  <td>
                    <span style={{
                      padding: '2px 8px', borderRadius: '10px', fontWeight: 'bold',
                      background: colors.bg, color: colors.fg,
                    }}>
                      {order.status_label || order.status}
                    </span>
                  </td>
                  <td>
                    {order.status === 'CONFIRMED' && (
                      <button className="btn" disabled={working === order.id} onClick={() => handleDeliver(order.id)}>
                        {working === order.id ? 'Guardando...' : 'Marcar entregada'}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default function DiningHallDashboard() {
  return <DiningHallGate>{(hall) => <OrdersTable hall={hall} />}</DiningHallGate>;
}

export { DiningHallDashboard };
