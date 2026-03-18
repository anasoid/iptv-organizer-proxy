---
description: Ensure GitHub CI workflows include the correct npm build steps whenever admin/frontend code is added or modified.
applyTo: "admin/**/*,. github/workflows/**/*"
---

# Frontend CI — Workflow Verification Rules

Whenever you create or modify files under `admin/` (the React + Vite frontend), you **must** verify that the GitHub Actions workflow for the frontend is consistent and complete.

---

## Required npm Steps in `.github/workflows/frontend-ci.yml`

The workflow **must** contain all of the following steps (in this order) inside the `test-frontend` job:

```yaml
- name: Install dependencies
  run: npm ci

- name: Run ESLint
  run: npm run lint

- name: Run TypeScript type check
  run: npm run type-check

- name: Run tests
  run: npm test

- name: Build
  run: npm run build
```

- The `working-directory` for the job **must** be set to `admin`.
- The `cache-dependency-path` **must** point to `admin/package-lock.json`.

---

## When Modifying `admin/package.json`

- If you **add, rename, or remove** a script in `admin/package.json`, check whether any step in `.github/workflows/frontend-ci.yml` references that script name.
- If the script name changes (e.g., `test` → `test:unit`), update the corresponding `run:` line in the workflow **in the same PR/change**.
- If a new mandatory build step is added (e.g., `generate`, `codegen`), add a corresponding step to the workflow.

---

## `build:java` vs `build` — What Goes in CI

| Script | Purpose | Used in CI? |
|---|---|---|
| `npm run build` | Standalone Vite build (no file copy) | ✅ Yes — use this in CI |
| `npm run build:java` | Builds **and** copies dist into `back/src/main/resources/…` | ❌ No — not for CI; only for local dev |
| `npm run build:prod` | Production build with specific API URL | ❌ No — not for CI |

Always use `npm run build` (not `build:java` or `build:prod`) in the GitHub Actions workflow.

---

## Checklist Before Finishing Any Frontend Change

- [ ] All new/changed source files are under `admin/src/`.
- [ ] `npm run lint` would pass (no new ESLint errors introduced).
- [ ] `npm run type-check` would pass (no new TypeScript errors introduced).
- [ ] `npm run build` would pass (Vite build succeeds).
- [ ] `.github/workflows/frontend-ci.yml` still contains all five required steps listed above.
- [ ] No step in the workflow references a script that no longer exists in `admin/package.json`.

---

## References

- Workflow file: [frontend-ci.yml](../../.github/workflows/frontend-ci.yml)
- Frontend package: [package.json](../../admin/package.json)
- Vite config: [vite.config.ts](../../admin/vite.config.ts)

