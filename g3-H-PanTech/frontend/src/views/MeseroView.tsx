import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Order, Product, Reservation } from '../types'

const EMPTY_RESERVATION = {
  customer_name: '',
  party_size: 2,
  reserved_at: '',
  notes: '',
}

export default function MeseroView() {
  const [menu, setMenu] = useState<Product[]>([])
  const [orders, setOrders] = useState<Order[]>([])
  const [reservations, setReservations] = useState<Reservation[]>([])
  const [quantities, setQuantities] = useState<Record<number, number>>({})
  const [orderType, setOrderType] = useState<'para_llevar' | 'en_mesa'>('para_llevar')
  const [tableNumber, setTableNumber] = useState<string>('')
  const [reservation, setReservation] = useState({ ...EMPTY_RESERVATION })
  const [editingReservationId, setEditingReservationId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadAll = async () => {
    try {
      const [menuData, ordersData, reservationsData] = await Promise.all([
        api.menu(),
        api.listOrders(),
        api.listReservations(),
      ])
      setMenu(menuData)
      setOrders(ordersData)
      setReservations(reservationsData)
      setError(null)
    } catch (err) {
      setError((err as Error).message)
    }
  }

  useEffect(() => {
    loadAll()
  }, [])

  const setQuantity = (productId: number, quantity: number) =>
    setQuantities((prev) => ({ ...prev, [productId]: Math.max(0, quantity) }))

  const submitOrder = async () => {
    const items = Object.entries(quantities)
      .map(([productId, quantity]) => ({ product_id: Number(productId), quantity }))
      .filter((item) => item.quantity > 0)

    if (items.length === 0) {
      setError('Agrega al menos un producto al pedido')
      return
    }
    if (orderType === 'en_mesa' && !tableNumber) {
      setError('Indica el número de mesa para un pedido en mesa')
      return
    }
    try {
      const created = await api.createOrder({
        type: orderType,
        table_number: orderType === 'en_mesa' ? Number(tableNumber) : null,
        items,
      })
      setMessage(`Pedido #${created.id} enviado a cocina`)
      setError(null)
      setQuantities({})
      setTableNumber('')
      await loadAll()
    } catch (err) {
      setError((err as Error).message)
      setMessage(null)
    }
  }

  const submitReservation = async () => {
    if (!reservation.customer_name || !reservation.reserved_at) {
      setError('La reserva requiere nombre y fecha/hora')
      return
    }
    const payload = {
      customer_name: reservation.customer_name,
      party_size: Number(reservation.party_size),
      reserved_at: new Date(reservation.reserved_at).toISOString(),
      notes: reservation.notes || null,
    }
    try {
      if (editingReservationId === null) {
        await api.createReservation(payload)
        setMessage('Reserva creada')
      } else {
        await api.updateReservation(editingReservationId, payload)
        setMessage('Reserva actualizada')
      }
      setReservation({ ...EMPTY_RESERVATION })
      setEditingReservationId(null)
      setError(null)
      await loadAll()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  const editReservation = (item: Reservation) => {
    setEditingReservationId(item.id)
    setReservation({
      customer_name: item.customer_name,
      party_size: item.party_size,
      reserved_at: item.reserved_at.slice(0, 16),
      notes: item.notes ?? '',
    })
  }

  const removeReservation = async (id: number) => {
    try {
      await api.deleteReservation(id)
      await loadAll()
    } catch (err) {
      setError((err as Error).message)
    }
  }

  return (
    <div className="grid two">
      <section className="card">
        <h2>Nuevo pedido</h2>
        {message && <p className="ok">{message}</p>}
        {error && <p className="error">{error}</p>}

        <div className="row">
          <label>
            Tipo
            <select
              value={orderType}
              onChange={(e) => setOrderType(e.target.value as 'para_llevar' | 'en_mesa')}
            >
              <option value="para_llevar">Para llevar</option>
              <option value="en_mesa">En mesa</option>
            </select>
          </label>
          {orderType === 'en_mesa' && (
            <label>
              Mesa
              <input
                type="number"
                min={1}
                value={tableNumber}
                onChange={(e) => setTableNumber(e.target.value)}
              />
            </label>
          )}
        </div>

        <h3>Menú y tiempos estimados</h3>
        <table className="menu-table">
          <thead>
            <tr>
              <th>Producto</th>
              <th>Categoría</th>
              <th>Prep.</th>
              <th>Precio</th>
              <th>Cantidad</th>
            </tr>
          </thead>
          <tbody>
            {menu.map((product) => (
              <tr key={product.id}>
                <td>{product.name}</td>
                <td>
                  <span className={`tag ${product.category}`}>{product.category}</span>
                </td>
                <td>{product.prep_minutes} min</td>
                <td>${Number(product.price).toFixed(2)}</td>
                <td>
                  <input
                    type="number"
                    min={0}
                    value={quantities[product.id] ?? 0}
                    onChange={(e) => setQuantity(product.id, Number(e.target.value))}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <button className="primary" onClick={submitOrder}>
          Crear pedido y enviar a cocina
        </button>

        <h3>Pedidos recientes</h3>
        <ul className="list">
          {orders.length === 0 && <li className="muted">Sin pedidos aún.</li>}
          {orders.map((order) => (
            <li key={order.id}>
              <strong>#{order.id}</strong>{' '}
              {order.type === 'en_mesa' ? `Mesa ${order.table_number}` : 'Para llevar'} ·{' '}
              <span className="muted">
                {order.items.map((i) => `${i.quantity}× ${i.name ?? i.product_id}`).join(', ')}
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <h2>{editingReservationId === null ? 'Nueva reserva' : `Editar reserva #${editingReservationId}`}</h2>
        <label>
          Cliente
          <input
            value={reservation.customer_name}
            onChange={(e) => setReservation({ ...reservation, customer_name: e.target.value })}
          />
        </label>
        <div className="row">
          <label>
            Personas
            <input
              type="number"
              min={1}
              value={reservation.party_size}
              onChange={(e) =>
                setReservation({ ...reservation, party_size: Number(e.target.value) })
              }
            />
          </label>
          <label>
            Fecha y hora
            <input
              type="datetime-local"
              value={reservation.reserved_at}
              onChange={(e) => setReservation({ ...reservation, reserved_at: e.target.value })}
            />
          </label>
        </div>
        <label>
          Notas
          <textarea
            rows={2}
            value={reservation.notes}
            onChange={(e) => setReservation({ ...reservation, notes: e.target.value })}
          />
        </label>
        <div className="row">
          <button className="primary" onClick={submitReservation}>
            {editingReservationId === null ? 'Crear reserva' : 'Guardar cambios'}
          </button>
          {editingReservationId !== null && (
            <button
              onClick={() => {
                setEditingReservationId(null)
                setReservation({ ...EMPTY_RESERVATION })
              }}
            >
              Cancelar
            </button>
          )}
        </div>

        <h3>Reservas</h3>
        <ul className="list">
          {reservations.length === 0 && <li className="muted">Sin reservas.</li>}
          {reservations.map((item) => (
            <li key={item.id} className="list-item">
              <div>
                <strong>{item.customer_name}</strong> · {item.party_size} pers. ·{' '}
                {new Date(item.reserved_at).toLocaleString()}
                {item.notes && <div className="muted">{item.notes}</div>}
              </div>
              <div className="actions">
                <button onClick={() => editReservation(item)}>Editar</button>
                <button className="danger" onClick={() => removeReservation(item.id)}>
                  Eliminar
                </button>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
