import { expect, test, type Page } from '@playwright/test';

const CLIENT_EMAIL = process.env.E2E_CLIENT_EMAIL ?? 'cliente@demo.com';
const CLIENT_PASSWORD = process.env.E2E_CLIENT_PASSWORD ?? 'Demo1234!';
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@demo.com';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Demo1234!';

async function login(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.fill('#login-email', email);
  await page.fill('#login-password', password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

test.describe('autenticación', () => {
  test('un cliente inicia sesión y recibe cookies httpOnly', async ({ page }) => {
    await login(page, CLIENT_EMAIL, CLIENT_PASSWORD);

    await expect(page).toHaveURL('/');
    await expect(page.locator('.session-chip')).toContainText(CLIENT_EMAIL);

    const cookies = await page.context().cookies();
    const access = cookies.find((cookie) => cookie.name === 'panaderia_access');
    const refresh = cookies.find((cookie) => cookie.name === 'panaderia_refresh');

    expect(access, 'debe existir la cookie de acceso').toBeTruthy();
    expect(refresh, 'debe existir la cookie de refresco').toBeTruthy();
    expect(access?.httpOnly).toBe(true);
    expect(refresh?.httpOnly).toBe(true);
    expect(access?.sameSite).toBe('Lax');
    expect(access?.path).toBe('/');

    const localValues = await page.evaluate(() => Object.values(localStorage));
    expect(localValues, 'no se guardan tokens en localStorage').toHaveLength(0);
  });

  test('un administrador es redirigido al backoffice', async ({ page }) => {
    await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);

    await expect(page).toHaveURL('/staff');
    await expect(page.getByRole('heading', { name: /Hola,/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Permisos efectivos' })).toBeVisible();
    await expect(page.getByText('Nivel 100')).toBeVisible();
  });

  test('credenciales inválidas muestran un error', async ({ page }) => {
    await login(page, CLIENT_EMAIL, 'ClaveIncorrecta123');

    await expect(page).toHaveURL(/\/login/);
    await expect(page.locator('.form-error')).toContainText(/correo|contraseña|inválid|no se pudo/i);
    const cookies = await page.context().cookies();
    expect(cookies.some((cookie) => cookie.name === 'panaderia_access')).toBe(false);
  });

  test('el registro público crea una cuenta cliente', async ({ page }) => {
    const email = `e2e.cliente.${Date.now()}@demo.com`;

    await page.goto('/register');
    await page.fill('#register-name', 'Cliente E2E');
    await page.fill('#register-email', email);
    await page.fill('#register-password', 'Demo1234!');
    await page.fill('#register-confirm', 'Demo1234!');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    await expect(page).toHaveURL('/');
    await expect(page.locator('.session-chip')).toContainText(email);
    await expect(page.locator('.session-chip')).toContainText('cliente');
  });

  test('cerrar sesión limpia las cookies', async ({ page }) => {
    await login(page, CLIENT_EMAIL, CLIENT_PASSWORD);
    await expect(page).toHaveURL('/');

    await page.getByRole('button', { name: 'Salir' }).click();

    await expect(page).toHaveURL('/');
    await expect(page.locator('.main-nav').getByRole('link', { name: 'Ingresar' })).toBeVisible();

    const cookies = await page.context().cookies();
    expect(cookies.some((cookie) => cookie.name === 'panaderia_access')).toBe(false);
    expect(cookies.some((cookie) => cookie.name === 'panaderia_refresh')).toBe(false);
  });
});
