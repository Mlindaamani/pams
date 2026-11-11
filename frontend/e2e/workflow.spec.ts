import { expect, test, type Page } from '@playwright/test';

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

test.describe('workflow configuration', () => {
  test.skip(
    !username || !password,
    'Set E2E_USERNAME and E2E_PASSWORD to run authenticated E2E tests',
  );

  test('workflow actions use plain language and preview role visibility', async ({ page }) => {
    await login(page);
    await page.goto('/admin/workflow?tab=actions');
    await expect(page.getByText('When request is Draft')).toBeVisible();
    await expect(page.getByText('purchase_request:update')).toHaveCount(0);

    await page.getByLabel('Role', { exact: true }).click();
    await page.getByRole('option', { name: 'Finance' }).click();

    await expect(page.getByText('Preview actions by role')).toBeVisible();
    await expect(page.getByText('Draft', { exact: true }).last()).toBeVisible();
    await expect(page.getByText('Approve', { exact: true }).last()).toBeVisible();
  });

  test('workflow policy builder supports quorum and stage reordering', async ({ page }) => {
    await login(page);
    await page.goto('/admin/workflow/policies/new');
    await expect(page.getByText('Workflow builder')).toBeVisible();

    await page.getByRole('button', { name: 'Add approver' }).click();
    await page.getByRole('button', { name: 'Add stage' }).click();

    await expect(page.getByTestId('workflow-stage-1')).toContainText('Parallel');
    await expect(page.getByLabel('Quorum').first()).toBeVisible();
    await page.getByLabel('Quorum').first().fill('2');
    await expect(page.getByLabel('Quorum').first()).toHaveValue('2');

    const source = await page.getByRole('button', { name: 'Move stage 2' }).boundingBox();
    const target = await page.getByRole('button', { name: 'Move stage 1' }).boundingBox();
    expect(source).not.toBeNull();
    expect(target).not.toBeNull();
    await page.mouse.move(source!.x + source!.width / 2, source!.y + source!.height / 2);
    await page.mouse.down();
    await page.mouse.move(target!.x + target!.width / 2, target!.y + target!.height / 2, {
      steps: 12,
    });
    await page.mouse.up();

    await expect(page.getByTestId('workflow-stage-1').getByTestId('workflow-approver')).toHaveCount(
      1,
    );
    await expect(page.getByTestId('workflow-stage-2').getByTestId('workflow-approver')).toHaveCount(
      2,
    );
  });
});
