export type AuthTokens = {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  refresh_expires_in: number;
};

export type UserSummary = {
  id: string;
  email: string;
  full_name: string;
  active: boolean;
  roles: string[];
  permissions: string[];
  level: number;
  created_at: string;
  last_login_at: string | null;
};

export type AuthResponse = AuthTokens & {
  user: UserSummary;
};

export type ProductCategory = 'vitrina' | 'preparado';

export type Product = {
  product_id: string;
  name: string;
  category: ProductCategory;
  price: string;
  prep_minutes: number;
  active: boolean;
};

export type MenuItem = Omit<Product, 'active'>;

export type OrderStatus =
  | 'pendiente'
  | 'pagado'
  | 'en_preparacion'
  | 'listo'
  | 'entregado'
  | 'cancelado';

export type OrderItem = {
  product_id: string;
  product_name: string;
  unit_price: string;
  prep_minutes: number;
  quantity: number;
  line_total: string;
};

export type Order = {
  id: string;
  customer_id: string | null;
  created_by: string;
  order_type: 'para_llevar' | 'en_mesa';
  table_number: number | null;
  status: OrderStatus;
  total: string;
  notes: string | null;
  created_at: string;
  updated_at: string;
  items: OrderItem[];
};

export type OrderStatusResponse = {
  id: string;
  status: OrderStatus;
  updated_at: string;
};

export type Reservation = {
  id: string;
  customer_id: string;
  date: string;
  time: string;
  people: number;
  notes: string | null;
  status: 'pendiente' | 'confirmada' | 'cancelada';
  cancel_reason: string | null;
  created_at: string;
  updated_at: string;
};

export type QueueItem = {
  order_id: string;
  customer_id: string;
  order_type: 'para_llevar' | 'en_mesa';
  status: 'pendiente' | 'en_preparacion' | 'listo';
  prep_minutes: number;
  priority_score: number;
  wait_minutes: number;
  position: number;
  eta_minutes: number;
  enqueued_at: string;
  started_at: string | null;
  ready_at: string | null;
  items: Array<{
    product_id: string;
    name: string;
    quantity: number;
    prep_minutes: number;
  }>;
};

export type QueueResponse = {
  items: QueueItem[];
  capacity: { max: number; in_preparation: number };
  generated_at: string;
};

export type HistoryItem = {
  product_id: string;
  name: string;
  quantity: number;
  unit_price: string;
  prep_minutes: number;
};

export type HistoryOrder = {
  order_id: string;
  customer_id: string | null;
  status: OrderStatus;
  total: string;
  order_type: string | null;
  table_number: number | null;
  paid_at: string | null;
  ready_at: string | null;
  receipt_object: string | null;
  items: HistoryItem[];
  timeline: Array<{ status: string; at: string }>;
};

export type HistoryPage = {
  items: HistoryOrder[];
  total: number;
  page: number;
  page_size: number;
};

export type SalesReport = {
  from: string;
  to: string;
  orders_count: number;
  revenue: string;
  average_ticket: string;
  by_day: Array<{ day: string; orders: number; revenue: string }>;
  top_products: Array<{
    product_id: string;
    name: string;
    quantity: number;
    revenue: string;
  }>;
};

export type TimesReport = {
  from: string;
  to: string;
  orders_measured: number;
  avg_prep_minutes: number | null;
  avg_delivery_minutes: number | null;
  avg_total_minutes: number | null;
};

export type Role = {
  id: string;
  name: string;
  description: string;
  level: number;
  is_staff: boolean;
  permissions: string[];
};

export type Permission = {
  code: string;
  description: string;
};

export type AuditLog = {
  id: string;
  actor_id: string | null;
  action: string;
  entity: string;
  entity_id: string | null;
  details: Record<string, unknown> | null;
  created_at: string;
};

export type ExportResult = {
  url: string;
  object: string;
  format: string;
  content_type: string;
  bytes: number;
  expires_at: string;
};
