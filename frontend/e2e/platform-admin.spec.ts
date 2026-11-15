import { expect, test, type Page } from '@playwright/test';

const username = process.env.PLATFORM_ADMIN_USERNAME;
const password = process.env.PLATFORM_ADMIN_PASSWORD;

async function login(page: Page) {
  await page.goto('/login');
  await page.waitForURL(/\/realms\/pams\/protocol\/openid-connect\/auth/, { timeout: 30_000 });
  await page.locator('#username').fill(username!);
  await page.locator('#password').fill(password!);
  await Promise.all([
    page.waitForURL(/localhost:3000|127\.0\.0\.1:3000/, { timeout: 30_000 }),
    page.locator('#kc-login').click(),
  ]);
}

test.describe('SaaS Creator account', () => {
  test.skip(
    !username || !password,
    'Set PLATFORM_ADMIN_USERNAME and PLATFORM_ADMIN_PASSWORD to run platform admin E2E tests',
  );

  test('lands on platform, uses Keycloak account and cannot open tenant user administration', async ({
    page,
  }) => {
    await login(page);
    await expect(page).toHaveURL(/\/platform\/dashboard$/);

    await page.getByRole('button', { name: 'Account menu' }).click();
    await expect(page.getByRole('menuitem', { name: 'My profile' })).toHaveCount(0);
    await expect(page.getByRole('menuitem', { name: 'Keycloak account' })).toBeVisible();
    await page.keyboard.press('Escape');

    await page.goto('/profile');
    await expect(page.getByRole('heading', { name: 'SaaS Creator account' })).toBeVisible();
    await expect(page.getByText('admin@pams.com')).toBeVisible();
    await expect(page.getByText('Department', { exact: true })).toHaveCount(0);

    await page.goto('/admin/users');
    await expect(page.getByText('You do not have permission to view this page')).toBeVisible();
  });
});
