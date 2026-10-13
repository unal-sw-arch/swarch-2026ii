export type Role = 'mesero' | 'cocinero' | 'despachador' | 'productos' | 'historial'

export type ProductCategory = 'vitrina' | 'preparado'

export type OrderStatus = 'pendiente' | 'en_preparacion' | 'listo' | 'entregado'

export interface Product {
  id: number
  name: string
  category: ProductCategory
  prep_minutes: number
  price: string | number
  active: boolean
}

export interface ProductInput {
  name: string
  category: ProductCategory
  prep_minutes: number
  price: number
  active: boolean
}

export interface OrderItem {
  product_id: number
  quantity: number
  name?: string | null
}

export interface Order {
  id: number
  type: 'para_llevar' | 'en_mesa'
  table_number: number | null
  created_at: string
  items: OrderItem[]
}

export interface Reservation {
  id: number
  customer_name: string
  party_size: number
  reserved_at: string
  notes: string | null
  created_at: string
}

export interface QueueItem {
  product_id: number
  name: string
  quantity: number
  prep_minutes: number
}

export interface QueueOrder {
  order_id: number
  type: 'para_llevar' | 'en_mesa'
  table_number: number | null
  status: OrderStatus
  estimated_minutes: number
  total_items: number
  items: QueueItem[]
  created_at: string | null
  arrival_ts: number
}

export interface Dispatch {
  para_llevar: QueueOrder[]
  en_mesa: QueueOrder[]
}

export interface StatusEvent {
  status: OrderStatus
  changed_at: string
}

export interface OrderHistory {
  order_id: number
  events: StatusEvent[]
  started_at: string | null
  ready_at: string | null
  delivered_at: string | null
  duration_seconds: number | null
}

export interface HistoryRow {
  order_id: number
  status: OrderStatus
  started_at: string
  ready_at: string | null
  delivered_at: string | null
  duration_seconds: number | null
}
