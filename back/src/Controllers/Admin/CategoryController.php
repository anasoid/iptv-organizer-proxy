<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Category;

class CategoryController
{
    /**
     * List all categories by source (paginated, read-only)
     */
    public function list(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $sourceId = (int) ($queryParams['source_id'] ?? 0);
            $page = (int) ($queryParams['page'] ?? 1);
            $limit = (int) ($queryParams['limit'] ?? 20);

            if (!$sourceId) {
                return $this->jsonError($response, 'source_id is required', 400);
            }

            $offset = ($page - 1) * $limit;

            // Get all categories for source
            $allCategories = Category::findAll(['source_id' => $sourceId]);

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
