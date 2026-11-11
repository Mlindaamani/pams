import { expect, test } from '@playwright/test';

test('serves the SPA shell and runtime config', async ({ page, request }) => {
  const config = await request.get('/config.js');
  expect(config.ok()).toBeTruthy();
  expect(await config.text()).toContain('__APP_CONFIG__');

  const response = await page.goto('/');
  expect(response?.ok() ?? true).toBeTruthy();
  await expect(page).toHaveTitle(/PAMS/);
});
