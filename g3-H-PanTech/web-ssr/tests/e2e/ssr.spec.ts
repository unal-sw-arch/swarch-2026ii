import { expect, test } from '@playwright/test';

const CLIENT_EMAIL = process.env.E2E_CLIENT_EMAIL ?? 'cliente@demo.com';
const CLIENT_PASSWORD = process.env.E2E_CLIENT_PASSWORD ?? 'Demo1234!';
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@demo.com';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Demo1234!';

async function login(page: import('@playwright/test').Page, email: string, password: string) {
  await page.goto('/login');
  await page.fill('#login-email', email);
  await page.fill('#login-password', password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

test.describe('renderizado en servidor y rutas protegidas', () => {
  test('la portada llega renderizada en el HTML inicial', async ({ request }) => {
    const response = await request.get('/');
    expect(response.status()).toBe(200);

    const html = await response.text();
    expect(html).toContain('Panadería Express');
    expect(html).toContain('Bienvenido a la panadería');
    expect(html).toContain('Renderizado en el servidor');
  });

  test('sin sesión, /staff redirige al login', async ({ page }) => {
    await page.goto('/staff');

    await expect(page).toHaveURL(/\/login\?next=%2Fstaff|\/login\?next=\/staff/);
    await expect(page.getByRole('heading', { name: 'Ingresa a tu cuenta' })).toBeVisible();
  });

  test('un cliente autenticado recibe 403 en /staff', async ({ page }) => {
    await login(page, CLIENT_EMAIL, CLIENT_PASSWORD);
    await expect(page).toHaveURL('/');

    const response = await page.goto('/staff');
    expect(response?.status()).toBe(403);
    await expect(page.getByRole('heading', { name: /No tienes permiso/i })).toBeVisible();
  });

  test('un administrador ve el backoffice renderizado', async ({ page }) => {
    await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    await expect(page).toHaveURL('/staff');

    const html = await page.content();
    expect(html).toContain('Permisos efectivos');
    expect(html).toContain('admin');
  });

  test('el endpoint /health responde ok', async ({ request }) => {
    const response = await request.get('/health');
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ status: 'ok' });
  });
});
