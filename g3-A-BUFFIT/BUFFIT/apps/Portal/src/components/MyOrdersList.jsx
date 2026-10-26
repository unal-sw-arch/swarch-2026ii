import React, { useState, useEffect, useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import { getUserOrders, cancelOrder } from '../services/api';

export default function MyOrdersList() {
  const { user } = useContext(AuthContext);
  const currentUser = user?.user || user;
  
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(null);

  // Estado para la orden que se está confirmando para cancelar
  const [confirmingCancelId, setConfirmingCancelId] = useState(null);
  
  // Estado para mostrar avisos de éxito o error en la pantalla
  const [message, setMessage] = useState({ text: '', type: '' });

  const fetchOrders = async () => {
    try {
      const userId = currentUser?.id || currentUser?.user_id || currentUser?.email;
      if (userId) {
        const data = await getUserOrders(userId);
        setOrders(data);
      }
    } catch (err) {
      console.error("Error al obtener pedidos:", err);
      setMessage({ text: 'Error al cargar el historial de pedidos.', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, [currentUser]);

  const handleConfirmarCancelacion = async (orderId) => {
    try {
      setActionLoading(orderId);
      setMessage({ text: '', type: '' });

      await cancelOrder(orderId);
      
      setMessage({ text: `Pedido #${orderId} cancelado exitosamente.`, type: 'success' });
      setConfirmingCancelId(null);
      
      // Actualizar la lista de pedidos
      await fetchOrders();
    } catch (err) {
      console.error("Error al cancelar orden:", err);
      setMessage({ 
        text: err.response?.data?.error || 'No se pudo cancelar el pedido.', 
        type: 'error' 
      });
    } finally {
      setActionLoading(null);
    }
  };

  if (loading) return <div className="container" style={{ marginTop: '30px' }}>Cargando tus pedidos...</div>;

  return (
    <div className="container" style={{ marginTop: '30px' }}>
      <h2>Mis Pedidos</h2>

      {/* BANNER DE NOTIFICACIÓN EN PANTALLA */}
      {message.text && (
        <div style={{
          padding: '12px 16px',
          marginBottom: '20px',
          borderRadius: '6px',
          fontWeight: 'bold',
          backgroundColor: message.type === 'error' ? '#fde8e8' : '#e8f5e9',
          color: message.type === 'error' ? '#9b1c1c' : '#1b5e20',
          border: `1px solid ${message.type === 'error' ? '#f8b4b4' : '#a5d6a7'}`
        }}>
          {message.text}
        </div>
      )}

      {orders.length === 0 ? (
        <p>No tienes pedidos registrados aún.</p>
      ) : (
        orders.map((ord) => {
          const orderId = ord.id || ord.order_id;
          const status = ord.status || 'CONFIRMED';
          const canCancel = status === 'CONFIRMED' || status === 'PENDING';
          const isConfirmingThis = confirmingCancelId === orderId;

          return (
            <div className="card" key={orderId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
              <div>
                <h3>Pedido #{orderId}</h3>
                <p><strong>Comedor:</strong> {ord.dining_hall}</p>
                <p><strong>Horario:</strong> {ord.pickup_time_slot}</p>
                <p>
                  <strong>Estado:</strong>{' '}
                  <span style={{ 
                    color: status === 'CANCELLED' ? '#d32f2f' : '#2e7d32',
                    fontWeight: 'bold' 
                  }}>
                    {status}
                  </span>
                </p>
              </div>

              {canCancel && (
                <div>
                  {!isConfirmingThis ? (
                    <button 
                      className="btn" 
                      onClick={() => setConfirmingCancelId(orderId)}
                      style={{ 
                        backgroundColor: '#d32f2f', 
                        color: '#fff', 
                        border: 'none', 
                        padding: '10px 15px', 
                        borderRadius: '4px',
                        cursor: 'pointer' 
                      }}
                    >
                      Cancelar Pedido
                    </button>
                  ) : (
                    /* AVISO DE CONFIRMACIÓN DENTRO DE LA TARJETA */
                    <div style={{
                      backgroundColor: '#fff3cd',
                      border: '1px solid #ffeeba',
                      padding: '10px 14px',
                      borderRadius: '6px',
                      textAlign: 'right'
                    }}>
                      <p style={{ margin: '0 0 8px 0', fontSize: '0.9rem', color: '#856404', fontWeight: 'bold' }}>
                        ¿Confirmar cancelación del Pedido #{orderId}?
                      </p>
                      <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                        <button
                          onClick={() => setConfirmingCancelId(null)}
                          style={{
                            padding: '6px 12px',
                            background: '#ccc',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer'
                          }}
                          disabled={actionLoading === orderId}
                        >
                          No
                        </button>
                        <button
                          onClick={() => handleConfirmarCancelacion(orderId)}
                          style={{
                            padding: '6px 12px',
                            background: '#d32f2f',
                            color: '#fff',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer'
                          }}
                          disabled={actionLoading === orderId}
                        >
                          {actionLoading === orderId ? 'Cancelando...' : 'Sí, Cancelar'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })
      )}
    </div>
  );
}