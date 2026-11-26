import { expect, test } from '@playwright/test';

test('Keycloak login uses the PAMS MUI theme without a logo', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/login');
  await page.waitForURL(/\/realms\/pams\/protocol\/openid-connect\/auth/, { timeout: 30_000 });
  await page.waitForTimeout(400);

  await expect(page.getByRole('heading', { name: 'Sign in to your account' })).toBeVisible();
  await expect(page.getByLabel('Username or email')).toBeVisible();
  await expect(page.locator('#password')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign In' })).toBeEnabled();
  await expect(page.locator('img')).toHaveCount(0);

  await expect(page).toHaveScreenshot('keycloak-login.png', {
    animations: 'disabled',
    maxDiffPixelRatio: 0.01,
  });
});

const tenantUsername = process.env.TENANT_ADMIN_USERNAME;
const tenantPassword = process.env.TENANT_ADMIN_PASSWORD;

test('MUI password update page matches the PAMS theme', async ({ page }) => {
  test.skip(
    !tenantUsername || !tenantPassword,
    'Set TENANT_ADMIN_USERNAME and TENANT_ADMIN_PASSWORD to run the account-security snapshot',
  );

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/login');
  await page.waitForURL(/\/realms\/pams\/protocol\/openid-connect\/auth/, { timeout: 30_000 });
  await page.locator('#username').fill(tenantUsername!);
  await page.locator('#password').fill(tenantPassword!);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL(/localhost:3000/, { timeout: 30_000 });
  await page.getByRole('button', { name: 'Account menu' }).waitFor({ timeout: 30_000 });

  await page.goto('/profile/security', { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Change password' }).click();
  await page.waitForURL(/required-action.*UPDATE_PASSWORD/, { timeout: 30_000 });
  await page.waitForTimeout(400);

  await expect(page.getByRole('heading', { name: 'Update password' })).toBeVisible();
  await expect(page.locator('#password-new')).toBeVisible();
  await expect(page.locator('#password-confirm')).toBeVisible();
  await expect(page.locator('img')).toHaveCount(0);

  await expect(page).toHaveScreenshot('keycloak-update-password.png', {
    animations: 'disabled',
    maxDiffPixelRatio: 0.01,
  });

  await page.getByRole('button', { name: 'Cancel' }).click();
});
