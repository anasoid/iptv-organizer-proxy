<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Category;

class CategoryController
{
    /**
     * Remove accents from string for case-insensitive and accent-insensitive comparison
     */
    private function normalizeString(string $string): string
    {
        // Convert to lowercase and remove accents
        $string = strtolower($string);

        // Use iconv to remove accents (é -> e, ñ -> n, etc.)
        $string = iconv('UTF-8', 'ASCII//TRANSLIT', $string);

        return $string;
    }

    /**
     * List all categories by source (paginated, read-only)
     * Query params:
     *   - source_id (required)
     *   - search (optional): search by category name (case-insensitive and accent-insensitive)
     *   - category_type (optional): filter by category type ('live', 'vod', 'series')
     *   - page (optional): default 1
     *   - limit (optional): default 20
     */
    public function list(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $sourceId = (int) ($queryParams['source_id'] ?? 0);
            $search = $queryParams['search'] ?? null;
            $categoryType = $queryParams['category_type'] ?? null;
            $page = (int) ($queryParams['page'] ?? 1);
            $limit = (int) ($queryParams['limit'] ?? 20);

            if (!$sourceId) {
                return $this->jsonError($response, 'source_id is required', 400);
            }

            $offset = ($page - 1) * $limit;

            // Get all categories for source
            $allCategories = Category::findAll(['source_id' => $sourceId]);

            // Filter by search query (case-insensitive and accent-insensitive)
            if ($search) {
                $searchNormalized = $this->normalizeString($search);
                $allCategories = array_filter($allCategories, function($category) use ($searchNormalized) {
                    return strpos($this->normalizeString($category->category_name), $searchNormalized) !== false;
                });
                // Re-index array after filtering
                $allCategories = array_values($allCategories);
            }

            // Filter by category type
            if ($categoryType) {
                $validTypes = ['live', 'vod', 'series'];
                if (!in_array($categoryType, $validTypes)) {
                    return $this->jsonError($response, 'Invalid category_type. Must be one of: live, vod, series', 400);
                }
                $allCategories = array_filter($allCategories, function($category) use ($categoryType) {
                    return $category->category_type === $categoryType;
                });
                // Re-index array after filtering
                $allCategories = array_values($allCategories);
            }

            $total = count($allCategories);
            $paginatedCategories = array_slice($allCategories, $offset, $limit);
            $categoriesData = array_map(fn($category) => $category->toArray(), $paginatedCategories);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categoriesData,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get category by ID or by source_id + category_id
     * Query params:
     *   - source_id (optional): if provided, searches by source_id + category_id (functional lookup)
     *                           if not provided, searches by database id
     */
    public function get(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid category ID', 400);
            }

            $queryParams = $request->getQueryParams();
            $sourceId = isset($queryParams['source_id']) ? (int) $queryParams['source_id'] : null;

            error_log("CategoryController::get() - Fetching: ID=$id, SourceID=$sourceId");

            $category = null;

            // If source_id is provided, search by source_id + category_id (functional lookup)
            if ($sourceId) {
                error_log("CategoryController::get() - Searching by source_id ($sourceId) + category_id ($id)");
                $category = Category::findAll([
                    'source_id' => $sourceId,
                    'category_id' => $id
                ]);
                $category = !empty($category) ? $category[0] : null;
            } else {
                // Search by database id
                error_log("CategoryController::get() - Searching by database id ($id)");
                $category = Category::find($id);
            }

            if (!$category) {
                error_log("CategoryController::get() - Category NOT FOUND (ID=$id, SourceID=$sourceId)");
                return $this->jsonError($response, 'Category not found', 404);
            }

            error_log("CategoryController::get() - Category FOUND: " . $category->category_name);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $category->toArray(),
            ]);
        } catch (\Throwable $e) {
            error_log("CategoryController::get() - Exception: " . $e->getMessage());
            return $this->jsonError($response, 'Failed to get category: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Return JSON response
     */
    protected function jsonResponse(ResponseInterface $response, mixed $data, int $statusCode = 200): ResponseInterface
    {
        $response->getBody()->write(json_encode($data));
        return $response
            ->withHeader('Content-Type', 'application/json')
            ->withStatus($statusCode);
    }

    /**
     * Update category allow_deny field
     * Body params:
     *   - allow_deny: 'allow', 'deny', or null to remove override
     */
    public function updateAllowDeny(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid category ID', 400);
            }

            $category = Category::find($id);
            if (!$category) {
                return $this->jsonError($response, 'Category not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            // Validate allow_deny value
            if (!array_key_exists('allow_deny', $body)) {
                return $this->jsonError($response, 'allow_deny field is required', 400);
            }

            $allowDeny = $body['allow_deny'];
            if ($allowDeny !== null && !in_array($allowDeny, ['allow', 'deny'])) {
                return $this->jsonError($response, 'allow_deny must be "allow", "deny", or null', 400);
            }

            // Update category
            $category->allow_deny = $allowDeny;
            if (!$category->save()) {
                return $this->jsonError($response, 'Failed to update category', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Category allow_deny updated successfully',
                'data' => $category->toArray(),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to update category: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Return JSON error response
     */
    protected function jsonError(ResponseInterface $response, string $message, int $statusCode = 400): ResponseInterface
    {
        $response->getBody()->write(json_encode([
            'success' => false,
            'message' => $message,
        ]));
        return $response
            ->withHeader('Content-Type', 'application/json')
            ->withStatus($statusCode);
    }
}
