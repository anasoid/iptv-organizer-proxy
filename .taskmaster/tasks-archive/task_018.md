# Task ID: 18

**Title:** CI/CD Pipeline with GitHub Actions

**Status:** pending

**Dependencies:** 17

**Priority:** medium

**Description:** Implement automated testing, multi-platform Docker builds, and publishing to Docker Hub/GHCR using GitHub Actions

**Details:**

1. Create .github/workflows/ci.yml:
   - Trigger: on pull_request
   - Jobs:
     - test-backend:
       - Checkout code
       - Setup PHP 8.2
       - Install Composer dependencies
       - Run PHPUnit tests
       - Run PHPStan static analysis
       - Upload coverage report
     - test-frontend:
       - Checkout code
       - Setup Node.js 18
       - Install npm dependencies
       - Run Jest tests
       - Run ESLint
       - Run TypeScript type check
       - Build production bundle
2. Create .github/workflows/docker-build.yml:
   - Trigger: on push to main, on tag (v*)
   - Jobs:
     - build-backend:
       - Set up QEMU (for multi-platform)
       - Set up Docker Buildx
       - Login to Docker Hub
       - Login to GitHub Container Registry
       - Extract metadata (tags, labels)
       - Build and push:
         - Platforms: linux/amd64, linux/arm64
         - Tags: latest, git SHA, semantic version (if tag)
         - Push to Docker Hub and GHCR
     - build-frontend:
       - Same as backend but for frontend image
     - build-nginx:
       - Same for nginx image
     - build-sync-worker:
       - Same for sync worker image (or use backend image with different entrypoint)
3. Create .github/workflows/release.yml:
   - Trigger: on tag push (v*)
   - Jobs:
     - create-release:
       - Extract version from tag
       - Generate changelog from commits
       - Create GitHub release
       - Upload deployment artifacts (docker-compose.yml, .env.example, README)
4. Semantic versioning:
   - Parse git tags for version numbers
   - Tag Docker images with version: v1.0.0, v1.0, v1, latest
5. Secrets configuration:
   - GitHub repository secrets:
     - DOCKERHUB_USERNAME
     - DOCKERHUB_TOKEN
     - GHCR_TOKEN (GitHub PAT)
6. Build optimization:
   - Use Docker layer caching
   - Cache Composer dependencies
   - Cache npm dependencies
7. Matrix builds (optional):
   - Test on multiple PHP versions (8.1, 8.2)
   - Test on multiple Node versions (18, 20)
8. Deployment workflow (optional):
   - Auto-deploy to staging on main branch push
   - Manual approval for production deployment
9. Status badges:
   - Add CI status badge to README
   - Docker image version badge

**Test Strategy:**

1. Test CI workflow runs on pull request
2. Test backend tests execute successfully
3. Test frontend tests execute successfully
4. Test PHPStan static analysis passes
5. Test ESLint linting passes
6. Test Docker build workflow triggers on main push
7. Test multi-platform images build successfully
8. Test images pushed to Docker Hub
9. Test images pushed to GHCR
10. Test release workflow creates GitHub release on tag
11. Test semantic versioning applied correctly
12. Test changelog generated from commits
13. Manual test: create release tag and verify full pipeline
14. Test status badges display correctly in README
