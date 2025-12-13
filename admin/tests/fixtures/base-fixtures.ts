/**
 * Base Test Fixtures
 * Extends Playwright fixtures with custom helpers
 */

import { test as base } from '@playwright/test';
import { APIHelper } from '../utils/api-helper';
import { UIHelper } from '../utils/ui-helper';

type TestFixtures = {
  apiHelper: APIHelper;
  uiHelper: UIHelper;
};

export const test = base.extend<TestFixtures>({
  // eslint-disable-next-line react-hooks/rules-of-hooks
  apiHelper: async ({ request }, use) => {
    const apiHelper = new APIHelper(request);
    await use(apiHelper);
  },

  // eslint-disable-next-line react-hooks/rules-of-hooks
  uiHelper: async ({ page }, use) => {
    const uiHelper = new UIHelper(page);
    await use(uiHelper);
  },
});

export { expect } from '@playwright/test';
