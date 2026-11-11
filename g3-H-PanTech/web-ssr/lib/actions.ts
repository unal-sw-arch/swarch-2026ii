'use server';

import { revalidatePath } from 'next/cache';
import { redirect } from 'next/navigation';
import { ApiError, apiFetch } from './api';
import { requireSession } from './auth';
import { clearCart, readCart, writeCart, type Cart } from './cart';
import type { Order, Reservation } from './types';

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.details && typeof error.details === 'object') {
      const first = Object.values(error.details as Record<string, unknown>)[0];
      if (typeof first === 'string') return first;
    }
    return error.message;
  }
  return 'No se pudo completar la operación. Intenta de nuevo.';
}

function fail(path: string, error: unknown): never {
  redirect(`${path}${path.includes('?') ? '&' : '?'}error=${encodeURIComponent(errorMessage(error))}`);
}

function text(data: FormData, name: string): string {
  return String(data.get(name) ?? '').trim();
}

function number(data: FormData, name: string): number {
  return Number.parseInt(text(data, name), 10);
}

function cartFromForm(data: FormData, base: Cart): Cart {
  const next: Cart = { ...base };
  const productId = text(data, 'product_id');
  const quantity = number(data, 'quantity');
  if (!productId) return next;
  if (!Number.isFinite(quantity) || quantity <= 0) {
    delete next[productId];
  } else {
    next[productId] = Math.min(quantity, 50);
  }
  return next;
}

// ------------------------------------------------------------------ Carrito --
export async function addToCart(data: FormData): Promise<void> {
  requireSession();
  const cart = cartFromForm(data, readCart());
  writeCart(cart);
  revalidatePath('/cart');
  revalidatePath('/menu');
  redirect('/cart');
}

export async function updateCartLine(data: FormData): Promise<void> {
  requireSession();
  writeCart(cartFromForm(data, readCart()));
  revalidatePath('/cart');
  redirect('/cart');
}

export async function removeCartLine(data: FormData): Promise<void> {
  requireSession();
  const cart = readCart();
  delete cart[text(data, 'product_id')];
  writeCart(cart);
  revalidatePath('/cart');
  redirect('/cart');
}

// ------------------------------------------------------------------ Pedidos --
function orderPayload(data: FormData, staff: boolean): Record<string, unknown> {
  const orderType = staff ? text(data, 'order_type') : 'para_llevar';
  const table = number(data, 'table_number');
  const payload: Record<string, unknown> = {
    order_type: orderType || 'para_llevar',
    items: [],
  };
  if (orderType === 'en_mesa' && Number.isFinite(table) && table > 0) {
    payload.table_number = table;
  }
  const notes = text(data, 'notes');
  if (notes) payload.notes = notes;

  if (staff) {
    const items: Array<{ product_id: string; quantity: number }> = [];
    for (const [key, value] of data.entries()) {
      if (!key.startsWith('qty_')) continue;
      const quantity = Number.parseInt(String(value), 10);
      if (Number.isFinite(quantity) && quantity > 0) {
        items.push({ product_id: key.slice(4), quantity: Math.min(quantity, 50) });
      }
    }
    payload.items = items;
  } else {
    payload.items = Object.entries(readCart()).map(([product_id, quantity]) => ({
      product_id,
      quantity,
    }));
  }
  return payload;
}

export async function createOrder(data: FormData): Promise<void> {
  requireSession();
  const items = orderPayload(data, false).items as unknown[];
  if (items.length === 0) fail('/cart', new Error('El carrito está vacío'));
  try {
    const order = await apiFetch<Order>('/orders', {
      method: 'POST',
      body: JSON.stringify(orderPayload(data, false)),
    });
    clearCart();
    revalidatePath('/orders');
    redirect(`/checkout/payment?orderId=${order.id}`);
  } catch (error) {
    if (error instanceof ApiError) fail('/checkout', error);
    throw error;
  }
}

export async function createStaffOrder(data: FormData): Promise<void> {
  requireSession();
  const payload = orderPayload(data, true);
  if ((payload.items as unknown[]).length === 0) {
    fail('/staff/orders', new Error('Selecciona al menos un producto'));
  }
  try {
    await apiFetch<Order>('/orders', { method: 'POST', body: JSON.stringify(payload) });
    revalidatePath('/staff/orders');
    redirect('/staff/orders?ok=Pedido%20creado');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/orders', error);
    throw error;
  }
}

export async function payOrder(data: FormData): Promise<void> {
  requireSession();
  const orderId = text(data, 'order_id');
  const returnTo = text(data, 'return_to') || `/orders/${orderId}`;
  try {
    const order = await apiFetch<Order>(`/orders/${orderId}`);
    await apiFetch(`/orders/${orderId}/payment`, {
      method: 'POST',
      headers: { 'Idempotency-Key': `${orderId}-${Date.now()}` },
      body: JSON.stringify({ amount: order.total, method: 'simulado' }),
    });
    revalidatePath('/orders');
    revalidatePath(`/orders/${orderId}`);
    revalidatePath('/staff/orders');
    redirect(`${returnTo}${returnTo.includes('?') ? '&' : '?'}ok=Pago%20confirmado`);
  } catch (error) {
    if (error instanceof ApiError) fail(returnTo, error);
    throw error;
  }
}

export async function cancelOrder(data: FormData): Promise<void> {
  requireSession();
  const orderId = text(data, 'order_id');
  const returnTo = text(data, 'return_to') || '/staff/cancel';
  try {
    await apiFetch<Order>(`/orders/${orderId}/cancel`, { method: 'POST' });
    revalidatePath('/orders');
    revalidatePath(`/orders/${orderId}`);
    revalidatePath('/staff/cancel');
    revalidatePath('/staff/orders');
    redirect(`${returnTo}${returnTo.includes('?') ? '&' : '?'}ok=Pedido%20cancelado`);
  } catch (error) {
    if (error instanceof ApiError) fail(returnTo, error);
    throw error;
  }
}

// --------------------------------------------------------------- Reservas --
export async function createReservation(data: FormData): Promise<void> {
  requireSession();
  try {
    await apiFetch<Reservation>('/reservations', {
      method: 'POST',
      body: JSON.stringify({
        date: text(data, 'date'),
        time: text(data, 'time'),
        people: number(data, 'people'),
        notes: text(data, 'notes') || null,
      }),
    });
    revalidatePath('/reservations');
    revalidatePath('/staff/reservations');
    redirect('/reservations?ok=Reserva%20registrada');
  } catch (error) {
    if (error instanceof ApiError) fail('/reservations', error);
    throw error;
  }
}

export async function changeReservationStatus(data: FormData): Promise<void> {
  requireSession();
  const reservationId = text(data, 'reservation_id');
  const status = text(data, 'status');
  const returnTo = text(data, 'return_to') || '/reservations';
  try {
    await apiFetch<Reservation>(`/reservations/${reservationId}`, {
      method: 'PUT',
      body: JSON.stringify({ status, reason: text(data, 'reason') || null }),
    });
    revalidatePath('/reservations');
    revalidatePath('/staff/reservations');
    redirect(`${returnTo}${returnTo.includes('?') ? '&' : '?'}ok=Reserva%20actualizada`);
  } catch (error) {
    if (error instanceof ApiError) fail(returnTo, error);
    throw error;
  }
}

// --------------------------------------------------------------- Productos --
export async function createProduct(data: FormData): Promise<void> {
  requireSession();
  try {
    await apiFetch('/products', {
      method: 'POST',
      body: JSON.stringify({
        name: text(data, 'name'),
        category: text(data, 'category'),
        prep_minutes: number(data, 'prep_minutes'),
        price: text(data, 'price'),
        active: true,
      }),
    });
    revalidatePath('/staff/products');
    revalidatePath('/menu');
    redirect('/staff/products?ok=Producto%20creado');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/products', error);
    throw error;
  }
}

export async function updateProduct(data: FormData): Promise<void> {
  requireSession();
  const productId = text(data, 'product_id');
  try {
    await apiFetch(`/products/${encodeURIComponent(productId)}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: text(data, 'name'),
        category: text(data, 'category'),
        prep_minutes: number(data, 'prep_minutes'),
        price: text(data, 'price'),
        active: data.get('active') === 'on' || data.get('active') === 'true',
      }),
    });
    revalidatePath('/staff/products');
    revalidatePath('/menu');
    redirect('/staff/products?ok=Producto%20actualizado');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/products', error);
    throw error;
  }
}

export async function deleteProduct(data: FormData): Promise<void> {
  requireSession();
  try {
    await apiFetch(`/products/${encodeURIComponent(text(data, 'product_id'))}`, { method: 'DELETE' });
    revalidatePath('/staff/products');
    revalidatePath('/menu');
    redirect('/staff/products?ok=Producto%20eliminado');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/products', error);
    throw error;
  }
}

// ----------------------------------------------------------------- Usuarios --
export async function createUser(data: FormData): Promise<void> {
  requireSession();
  const roles = data.getAll('roles').map(String).filter(Boolean);
  try {
    await apiFetch('/users', {
      method: 'POST',
      body: JSON.stringify({
        email: text(data, 'email'),
        full_name: text(data, 'full_name'),
        password: text(data, 'password'),
        roles,
      }),
    });
    revalidatePath('/staff/users');
    redirect('/staff/users?ok=Cuenta%20creada');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/users', error);
    throw error;
  }
}

export async function updateUser(data: FormData): Promise<void> {
  requireSession();
  const userId = text(data, 'user_id');
  const payload: Record<string, unknown> = {};
  const fullName = text(data, 'full_name');
  const email = text(data, 'email');
  const password = text(data, 'password');
  if (fullName) payload.full_name = fullName;
  if (email) payload.email = email;
  if (password) payload.password = password;
  if (data.has('active')) payload.active = data.get('active') === 'on' || data.get('active') === 'true';
  try {
    await apiFetch(`/users/${userId}`, { method: 'PATCH', body: JSON.stringify(payload) });
    revalidatePath('/staff/users');
    redirect('/staff/users?ok=Cuenta%20actualizada');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/users', error);
    throw error;
  }
}

export async function assignRole(data: FormData): Promise<void> {
  requireSession();
  const userId = text(data, 'user_id');
  try {
    await apiFetch(`/users/${userId}/roles`, {
      method: 'POST',
      body: JSON.stringify({ role: text(data, 'role') }),
    });
    revalidatePath('/staff/users');
    redirect('/staff/users?ok=Rol%20asignado');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/users', error);
    throw error;
  }
}

export async function removeRole(data: FormData): Promise<void> {
  requireSession();
  const userId = text(data, 'user_id');
  const role = text(data, 'role');
  try {
    await apiFetch(`/users/${userId}/roles/${encodeURIComponent(role)}`, { method: 'DELETE' });
    revalidatePath('/staff/users');
    redirect('/staff/users?ok=Rol%20retirado');
  } catch (error) {
    if (error instanceof ApiError) fail('/staff/users', error);
    throw error;
  }
}
