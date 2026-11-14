import { expect, test, type Locator, type Page } from '@playwright/test';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;

async function login(page: Page) {
  await page.goto('/login');
  await page.waitForURL(/\/realms\/pams\/protocol\/openid-connect\/auth/, { timeout: 30_000 });
  await page.locator('#username').fill(username!);
  await page.locator('#password').fill(password!);
  await Promise.all([
    page.waitForURL(/localhost:3000|127\.0\.0\.1:3000/, { timeout: 30_000 }),
    page.locator('#kc-login').click(),
  ]);
  await page.getByRole('link', { name: 'Dashboard' }).first().waitFor({ timeout: 30_000 });
}

function formControl(combobox: Locator) {
  return combobox.locator('xpath=ancestor::div[contains(@class,"MuiFormControl-root")][1]');
}

async function box(locator: Locator) {
  const value = await locator.boundingBox();
  expect(value).not.toBeNull();
  return value!;
}

test.describe('global combobox layout', () => {
  test.skip(
    !username || !password,
    'Set E2E_USERNAME and E2E_PASSWORD to run authenticated E2E tests',
  );

  test('catalog modal vendor combobox fills the dialog and keeps options readable', async ({
    page,
  }) => {
    await login(page);
    await page.goto('/admin/catalog');
    await page.getByRole('button', { name: 'New catalog item' }).click();

    const dialog = page.getByRole('dialog', { name: 'New catalog item' });
    await expect(dialog).toBeVisible();
    const vendor = dialog.getByRole('combobox', { name: 'Default vendor' });
    const dialogBox = await box(dialog);
    const vendorBox = await box(formControl(vendor));
    expect(vendorBox.width).toBeGreaterThan(dialogBox.width * 0.75);

    await vendor.click();
    const option = page.getByRole('option', { name: 'Vendor to be confirmed' });
    await expect(option).toBeVisible();
    const optionBox = await box(option);
    expect(optionBox.x).toBeGreaterThanOrEqual(dialogBox.x);
    expect(optionBox.x + optionBox.width).toBeLessThanOrEqual(dialogBox.x + dialogBox.width + 1);
  });

  test('workflow permission combobox stays inside its card and wraps content', async ({ page }) => {
    await login(page);
    await page.goto('/admin/workflow?tab=actions');
    await expect(page.getByText('When request is Draft')).toBeVisible();

    const card = page.locator('.MuiCard-root').filter({ hasText: 'When request is Draft' }).first();
    const who = page.getByRole('combobox', { name: 'Who can use it' }).first();
    const cardBox = await box(card);
    const whoBox = await box(formControl(who));
    expect(whoBox.width).toBeGreaterThan(200);

    await who.click();
    const listbox = page.getByRole('listbox');
    await expect(listbox).toBeVisible();
    const listboxBox = await box(listbox);
    expect(listboxBox.x).toBeGreaterThanOrEqual(cardBox.x);
    expect(listboxBox.x + listboxBox.width).toBeLessThanOrEqual(cardBox.x + cardBox.width + 1);

    const selected = page.getByRole('option', { name: /Cancel purchase requests/ }).last();
    await expect(selected).toBeVisible();
    const hasOverflow = await selected.evaluate(
      (element) => element.scrollWidth > element.clientWidth + 1,
    );
    expect(hasOverflow).toBe(false);
  });

  test('delivery log filters keep their widths and show full event labels', async ({ page }) => {
    await login(page);
    await page.goto('/admin/webhooks');
    await page.getByRole('tab', { name: 'Delivery Logs' }).click();

    const endpoint = page.getByRole('combobox', { name: 'Endpoint' });
    const status = page.getByRole('combobox', { name: 'Status' });
    const event = page.getByRole('combobox', { name: 'Event' });
    expect((await box(formControl(endpoint))).width).toBeGreaterThanOrEqual(260);
    expect((await box(formControl(status))).width).toBeGreaterThanOrEqual(160);
    expect((await box(formControl(event))).width).toBeGreaterThanOrEqual(220);

    await event.click();
    const option = page.getByRole('option', {
      name: 'Request converted to purchase order',
    });
    await expect(option).toBeVisible();
    const hasOverflow = await option.evaluate(
      (element) => element.scrollWidth > element.clientWidth + 1,
    );
    expect(hasOverflow).toBe(false);
  });
});
