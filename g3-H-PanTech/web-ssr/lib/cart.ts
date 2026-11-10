import { cookies } from 'next/headers';

export const CART_COOKIE = 'panaderia_cart';

export type Cart = Record<string, number>;

/** Carrito guardado en cookie httpOnly; el cliente nunca calcula el total. */
export function readCart(): Cart {
  const raw = cookies().get(CART_COOKIE)?.value;
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const cart: Cart = {};
    for (const [productId, quantity] of Object.entries(parsed)) {
      const qty = Number(quantity);
      if (productId && Number.isInteger(qty) && qty > 0 && qty <= 50) {
        cart[productId] = qty;
      }
    }
    return cart;
  } catch {
    return {};
  }
}

export function writeCart(cart: Cart): void {
  const compact = Object.fromEntries(
    Object.entries(cart).filter(([, qty]) => Number.isInteger(qty) && qty > 0),
  );
  cookies().set(CART_COOKIE, JSON.stringify(compact), {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.COOKIE_SECURE === 'true',
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
  });
}

export function clearCart(): void {
  cookies().set(CART_COOKIE, '{}', {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.COOKIE_SECURE === 'true',
    path: '/',
    maxAge: 0,
  });
}

export function cartLines(cart: Cart): Array<{ product_id: string; quantity: number }> {
  return Object.entries(cart).map(([product_id, quantity]) => ({ product_id, quantity }));
}
