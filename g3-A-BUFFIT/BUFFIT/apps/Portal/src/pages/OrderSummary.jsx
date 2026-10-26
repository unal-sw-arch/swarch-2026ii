import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { createOrder } from '../services/api';

export default function OrderSummary() {
  const location = useLocation();
  const navigate = useNavigate();

  const draftOrder = location.state?.draftOrder;

  const [submitting, setSubmitting] = useState(false);
  const [completedOrder, setCompletedOrder] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');

  if (!draftOrder && !completedOrder) {
    return (
      <div className="container" style={{ marginTop: '50px', textAlign: 'center' }}>
        <h3>No hay ninguna orden pendiente.</h3>
        <button className="btn" onClick={() => navigate('/')} style={{ marginTop: '20px' }}>
          Volver al Inicio
        </button>
      </div>
    );
  }

  const handleConfirmarPedido = async () => {
    try {
      setSubmitting(true);
      setErrorMsg('');

      // Construcción garantizada del objeto exacto que pide Flask en @orders_bp.route("/crearOrden")
      const payload = {
        user_id: String(draftOrder.user_id || "1"),
        user_type: String(draftOrder.user_type || "ESTUDIANTE").toUpperCase(),
        dining_hall: String(draftOrder.dining_hall),
        pickup_time_slot: String(draftOrder.pickup_time_slot),
        items: draftOrder.items || []
      };

      console.log("Enviando Payload a Gestor de Pedidos:", payload);

      const createdOrder = await createOrder(payload);
      setCompletedOrder(createdOrder);

    } catch (err) {
      console.error("Error al procesar la orden:", err);
      setErrorMsg(err.response?.data?.error || 'No se pudo registrar el pedido en el sistema.');
    } finally {
      setSubmitting(false);
    }
  };

  if (completedOrder) {
    return (
      <div className="container" style={{ marginTop: '40px' }}>
        <div className="card" style={{ textAlign: 'center', borderColor: '#4CAF50' }}>
          <h2 style={{ color: '#4CAF50' }}>¡Pedido Confirmado Exitosamente! 🎉</h2>
          <p>Tu orden ha sido registrada en el Gestor de Pedidos.</p>

          <hr style={{ margin: '20px 0' }} />

          <h3>Código de Ticket: <strong>{completedOrder.order_id || completedOrder.id || 'ORD-OK'}</strong></h3>
          <p><strong>Punto de Recogida:</strong> {completedOrder.dining_hall || draftOrder.dining_hall}</p>
          <p><strong>Horario:</strong> {completedOrder.pickup_time_slot || draftOrder.pickup_time_slot}</p>

          <button className="btn" onClick={() => navigate('/mis-pedidos')} style={{ marginTop: '20px' }}>
            Volver al Menú Principal
          </button>
        </div>
      </div>
    );
  }

  const plato = draftOrder.platoVisual || {};

  return (
    <div className="container" style={{ marginTop: '40px' }}>
      <h2>Resumen de tu Orden</h2>
      <p>Revisa la composición de tu almuerzo antes de confirmar.</p>

      {errorMsg && <p style={{ color: 'red', fontWeight: 'bold' }}>{errorMsg}</p>}

      <div className="card">
        <h3>Detalle del Menú</h3>
        <ul>
          <li><strong>Proteína:</strong> {plato.proteina}</li>
          <li><strong>Carbohidrato 1:</strong> {plato.carb1}</li>
          <li><strong>Carbohidrato 2:</strong> {plato.carb2}</li>
          <li><strong>Ensalada:</strong> {plato.ensalada}</li>
          <li><strong>Bebida:</strong> {plato.bebida}</li>
          <li><strong>Sopa:</strong> {plato.sopa}</li>
          <li><strong>Postre:</strong> {plato.postre}</li>
        </ul>
      </div>

      <div className="card">
        <h3>Logística y Pago</h3>
        <p><strong>Punto de Recogida:</strong> {draftOrder.dining_hall}</p>
        <p><strong>Horario Seleccionado:</strong> {draftOrder.pickup_time_slot}</p>
        <p><strong>Tipo de Usuario:</strong> {draftOrder.user_type}</p>
        <h3 style={{ marginTop: '10px', color: '#14253f' }}>
          Total a Pagar: ${draftOrder.precio?.toLocaleString('es-CO')} COP
        </h3>
      </div>

      <div style={{ display: 'flex', gap: '15px', marginTop: '20px' }}>
        <button
          type="button"
          onClick={() => navigate('/')}
          style={{ padding: '12px 20px', background: '#ccc', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
          disabled={submitting}
        >
          Modificar Selección
        </button>

        <button
          type="button"
          className="btn"
          onClick={handleConfirmarPedido}
          style={{ flex: 1, fontSize: '1.1rem' }}
          disabled={submitting}
        >
          {submitting ? 'Procesando Pedido...' : 'Confirmar Pedido'}
        </button>
      </div>
    </div>
  );
}