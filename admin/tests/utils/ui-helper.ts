/**
 * UI Helper Utilities
 * Common UI operations for testing the React admin panel
 */

import { Page } from '@playwright/test';
import { ADMIN_PANEL_URL, TEST_ADMIN_USER, DELAYS } from '../fixtures/test-data';

export class UIHelper {
  constructor(private page: Page) {}

  /**
   * Login via UI
   */
  async login(
    username: string = TEST_ADMIN_USER.username,
    password: string = TEST_ADMIN_USER.password
  ) {
    await this.page.goto(`${ADMIN_PANEL_URL}/login`);

    // Fill login form
    await this.page.fill('input[name="username"]', username);
    await this.page.fill('input[name="password"]', password);

    // Submit form
    await this.page.click('button[type="submit"]');

    // Wait for redirect to dashboard
    await this.page.waitForURL(`${ADMIN_PANEL_URL}/dashboard`, {
      timeout: 5000,
    });
  }

  /**
   * Logout via UI
   */
  async logout() {
    // Click user menu or logout button
    await this.page.click('[data-testid="logout-button"]');

    // Wait for redirect to login
    await this.page.waitForURL(`${ADMIN_PANEL_URL}/login`);
  }

  /**
   * Navigate to page
   */
  async navigateTo(path: string) {
    await this.page.goto(`${ADMIN_PANEL_URL}${path}`);
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Click navigation link
   */
  async clickNavLink(label: string) {
    await this.page.click(`a:has-text("${label}")`);
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Wait for element and click
   */
  async waitAndClick(selector: string, timeout = 5000) {
    const element = await this.page.waitForSelector(selector, { timeout });
    await element?.click();
  }

  /**
   * Wait for table to load
   */
  async waitForTable(timeout = 5000) {
    await this.page.waitForSelector('[role="grid"]', { timeout });
  }

  /**
   * Find table row by text
   */
  async getTableRowByText(text: string) {
    return this.page.locator(`[role="row"]:has-text("${text}")`).first();
  }

  /**
   * Click row action button
   */
  async clickRowAction(rowText: string, actionText: string) {
    const row = await this.getTableRowByText(rowText);
    const button = row.locator(`button:has-text("${actionText}")`);
    await button.click();
  }

  /**
   * Fill form field
   */
  async fillField(label: string, value: string) {
    // Try label for attribute first
    const input = this.page.locator(`input[aria-label="${label}"]`);
    const count = await input.count();

    if (count > 0) {
      await input.fill(value);
      return;
    }

    // Try label with text
    const labelElement = this.page.locator(`label:has-text("${label}")`);
    const inputId = await labelElement.getAttribute('for');

    if (inputId) {
      await this.page.fill(`#${inputId}`, value);
    } else {
      // Try finding input following label
      await this.page.fill(`input[placeholder="${label}"]`, value);
    }
  }

  /**
   * Fill textarea
   */
  async fillTextarea(label: string, value: string) {
    const textarea = this.page.locator(
      `textarea[aria-label="${label}"], textarea[placeholder="${label}"]`
    );
    await textarea.fill(value);
  }

  /**
   * Select dropdown option
   */
  async selectDropdown(label: string, optionText: string) {
    // Open dropdown
    const dropdown = this.page.locator(`div[aria-label="${label}"]`);
    await dropdown.click();

    // Select option
    await this.page.click(`li[data-value="${optionText}"]`);
  }

  /**
   * Submit form
   */
  async submitForm() {
    await this.page.click('button[type="submit"]');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Check for success message
   */
  async expectSuccessMessage(message: string) {
    await this.page.waitForSelector(`text=${message}`, { timeout: 5000 });
  }

  /**
   * Check for error message
   */
  async expectErrorMessage(message: string) {
    await this.page.waitForSelector(`text=${message}`, { timeout: 5000 });
  }

  /**
   * Wait for element to be visible
   */
  async waitForText(text: string, timeout = 5000) {
    await this.page.waitForSelector(`text=${text}`, { timeout });
  }

  /**
   * Check if element is visible
   */
  async isVisible(selector: string): Promise<boolean> {
    try {
      await this.page.waitForSelector(selector, { timeout: 1000 });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Wait for loading to complete
   */
  async waitForLoadingComplete() {
    // Wait for loading spinners to disappear
    await this.page.waitForSelector('[data-testid="loading-spinner"]', {
      state: 'hidden',
      timeout: 10000,
    });
  }

  /**
   * Open and fill create/edit modal
   */
  async openAndFillModal(buttonText: string, formData: Record<string, string>) {
    // Click button to open modal
    await this.page.click(`button:has-text("${buttonText}")`);

    // Wait for modal
    await this.page.waitForSelector('[role="dialog"]', { timeout: 5000 });

    // Fill form fields
    for (const [label, value] of Object.entries(formData)) {
      await this.fillField(label, value);
    }
  }

  /**
   * Close modal
   */
  async closeModal() {
    // Try close button
    const closeBtn = this.page.locator('[aria-label="close"], button[aria-label="Close"]');
    if (await closeBtn.isVisible()) {
      await closeBtn.click();
    }

    // Wait for modal to disappear
    await this.page.waitForSelector('[role="dialog"]', { state: 'hidden' });
  }

  /**
   * Handle alert dialog
   */
  async handleAlert(action: 'confirm' | 'dismiss' = 'confirm') {
    // Set up alert handler before action
    this.page.once('dialog', async (dialog) => {
      if (action === 'confirm') {
        await dialog.accept();
      } else {
        await dialog.dismiss();
      }
    });
  }

  /**
   * Wait for sync to complete
   */
  async waitForSyncComplete(timeout = 30000) {
    await this.page.waitForSelector('text=Sync Completed', {
      timeout,
    });
  }

  /**
   * Take screenshot for debugging
   */
  async takeScreenshot(name: string) {
    await this.page.screenshot({ path: `tests/screenshots/${name}.png` });
  }

  /**
   * Wait for network idle
   */
  async waitForNetworkIdle() {
    await this.page.waitForLoadState('networkidle');
  }
}
