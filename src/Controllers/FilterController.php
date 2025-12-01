<?php

declare(strict_types=1);

namespace App\Controllers;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Filter;

class FilterController
{
    /**
     * List all filters
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function list(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $page = (int) ($queryParams['page'] ?? 1);
            $limit = (int) ($queryParams['limit'] ?? 10);
            $offset = ($page - 1) * $limit;

            $filters = Filter::findAll();
            $total = count($filters);
            $paginatedFilters = array_slice($filters, $offset, $limit);

            // Convert models to arrays
            $filtersData = array_map(fn($filter) => $filter->toArray(), $paginatedFilters);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $filtersData,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list filters: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get filter by ID
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function get(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid filter ID', 400);
            }

            $filter = Filter::find($id);
            if (!$filter) {
                return $this->jsonError($response, 'Filter not found', 404);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $filter->toArray(),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get filter: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Create new filter
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function create(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $body = json_decode($request->getBody()->getContents(), true);

            // Validate required fields
            if (empty($body['name'])) {
                return $this->jsonError($response, 'Filter name is required', 400);
            }
            if (empty($body['filter_config'])) {
                return $this->jsonError($response, 'Filter configuration is required', 400);
            }

            // Validate YAML format (basic validation)
            if (!is_string($body['filter_config'])) {
                return $this->jsonError($response, 'Filter configuration must be a valid YAML string', 400);
            }

            // Create filter
            $filter = new Filter();
            $filter->name = $body['name'];
            $filter->filter_config = $body['filter_config'];
            $filter->description = $body['description'] ?? null;

            if (!$filter->save()) {
                return $this->jsonError($response, 'Failed to create filter', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Filter created successfully',
                'data' => $filter->toArray(),
            ], 201);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to create filter: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Update filter
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function update(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid filter ID', 400);
            }

            $filter = Filter::find($id);
            if (!$filter) {
                return $this->jsonError($response, 'Filter not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            // Update fields
            if (isset($body['name'])) {
                $filter->name = $body['name'];
            }
            if (isset($body['filter_config'])) {
                if (!is_string($body['filter_config'])) {
                    return $this->jsonError($response, 'Filter configuration must be a valid YAML string', 400);
                }
                $filter->filter_config = $body['filter_config'];
            }
            if (isset($body['description'])) {
                $filter->description = $body['description'];
            }

            if (!$filter->save()) {
                return $this->jsonError($response, 'Failed to update filter', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Filter updated successfully',
                'data' => $filter->toArray(),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to update filter: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Delete filter
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function delete(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid filter ID', 400);
            }

            $filter = Filter::find($id);
            if (!$filter) {
                return $this->jsonError($response, 'Filter not found', 404);
            }

            if (!$filter->delete()) {
                return $this->jsonError($response, 'Failed to delete filter', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Filter deleted successfully',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to delete filter: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Preview filter results
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function preview(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid filter ID', 400);
            }

            $filter = Filter::find($id);
            if (!$filter) {
                return $this->jsonError($response, 'Filter not found', 404);
            }

            // Parse filter rules and apply to streams
            // This is a simplified preview - full implementation would need actual filtering logic
            $preview = [
                'filter_id' => $filter->id,
                'filter_name' => $filter->name,
                'preview_count' => 0,
                'preview_samples' => [],
            ];

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $preview,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to preview filter: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Return JSON response
     *
     * @param ResponseInterface $response
     * @param mixed $data
     * @param int $statusCode
     * @return ResponseInterface
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
     *
     * @param ResponseInterface $response
     * @param string $message
     * @param int $statusCode
     * @return ResponseInterface
     */
    protected function jsonError(ResponseInterface $response, string $message, int $statusCode = 400): ResponseInterface
    {
        return $this->jsonResponse($response, [
            'success' => false,
            'message' => $message,
        ], $statusCode);
    }
}
