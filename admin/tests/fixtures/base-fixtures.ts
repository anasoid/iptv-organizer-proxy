/**
 * Base Test Fixtures
 * Extends Playwright fixtures with custom helpers
 */
/* eslint-disable react-hooks/rules-of-hooks */

import { test as base } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';
import { UIHelper } from '../utils/ui-helper';

type TestFixtures = {
  apiHelper: APIHelper;
  uiHelper: UIHelper;
};

export const test = base.extend<TestFixtures>({
  apiHelper: async ({ request }, use) => {
    const apiHelper = new APIHelper(request);
    await use(apiHelper);
  },

  uiHelper: async ({ page }, use) => {
    const uiHelper = new UIHelper(page);
    await use(uiHelper);
  },
});

export { expect } from '@playwright/test';
