import type {
  Dispatch,
  HistoryRow,
  Order,
  OrderHistory,
  Product,
  ProductInput,
  QueueOrder,
  Reservation,
} from './types'

const ORDER_API: string =
  import.meta.env.VITE_ORDER_API_URL || 'http://localhost:8000'
const SCHEDULER_API: string =
  import.meta.env.VITE_SCHEDULER_API_URL || 'http://localhost:8001'

export const POLL_INTERVAL_MS: number = Number(
  import.meta.env.VITE_POLL_INTERVAL_MS || 3000,
)

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`
    try {
      const body = await response.json()
      if (body?.detail) {
        detail = typeof body.detail === 'string' ? body.detail : JSON.stringify(body.detail)
      }
    } catch {
      /* respuesta sin cuerpo JSON */
    }
    throw new Error(detail)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  // Catálogo -------------------------------------------------------------
  menu: () => request<Product[]>(`${ORDER_API}/menu`),
  listProducts: () => request<Product[]>(`${ORDER_API}/products`),
  createProduct: (product: ProductInput) =>
    request<Product>(`${ORDER_API}/products`, {
      method: 'POST',
      body: JSON.stringify(product),
    }),
  updateProduct: (id: number, product: Partial<ProductInput>) =>
    request<Product>(`${ORDER_API}/products/${id}`, {
      method: 'PUT',
      body: JSON.stringify(product),
    }),
  deleteProduct: (id: number) =>
    request<void>(`${ORDER_API}/products/${id}`, { method: 'DELETE' }),

  // Pedidos --------------------------------------------------------------
  listOrders: () => request<Order[]>(`${ORDER_API}/orders`),
  createOrder: (payload: {
    type: 'para_llevar' | 'en_mesa'
    table_number: number | null
    items: { product_id: number; quantity: number }[]
  }) =>
    request<Order>(`${ORDER_API}/orders`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // Reservas -------------------------------------------------------------
  listReservations: () => request<Reservation[]>(`${ORDER_API}/reservations`),
  createReservation: (payload: {
    customer_name: string
    party_size: number
    reserved_at: string
    notes: string | null
  }) =>
    request<Reservation>(`${ORDER_API}/reservations`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  updateReservation: (
    id: number,
    payload: Partial<{
      customer_name: string
      party_size: number
      reserved_at: string
      notes: string | null
    }>,
  ) =>
    request<Reservation>(`${ORDER_API}/reservations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  deleteReservation: (id: number) =>
    request<void>(`${ORDER_API}/reservations/${id}`, { method: 'DELETE' }),

  // Cola de cocina y despacho (Queue Scheduler) --------------------------
  queue: () => request<QueueOrder[]>(`${SCHEDULER_API}/queue`),
  dispatch: () => request<Dispatch>(`${SCHEDULER_API}/dispatch`),
  setStatus: (orderId: number, status: 'en_preparacion' | 'listo') =>
    request<QueueOrder>(`${SCHEDULER_API}/queue/orders/${orderId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
  deliver: (orderId: number) =>
    request<QueueOrder>(`${SCHEDULER_API}/queue/orders/${orderId}/deliver`, {
      method: 'POST',
    }),

  // Historial de comandas (Requerimiento 9) ------------------------------
  orderHistory: (orderId: number) =>
    request<OrderHistory>(`${SCHEDULER_API}/history/orders/${orderId}`),
  historyList: () => request<HistoryRow[]>(`${SCHEDULER_API}/history`),
}
