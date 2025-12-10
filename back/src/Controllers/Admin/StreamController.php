<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;
use RuntimeException;

class StreamController
{
    /**
     * Map type to model class
     */
    private function getModelClass(string $type): string
    {
        return match($type) {
            'live' => LiveStream::class,
            'vod' => VodStream::class,
            'series' => Series::class,
            default => throw new RuntimeException("Invalid stream type: $type"),
        };
    }

    /**
     * List streams by source and type (paginated, read-only)
     * Query params:
     *   - source_id (required)
     *   - type (required): live, vod, series
     *   - category_id (optional): filter by category
     *   - page (optional): default 1
     *   - limit (optional): default 20
     */
    public function list(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $sourceId = (int) ($queryParams['source_id'] ?? 0);
            $type = $queryParams['type'] ?? null;
            $categoryId = isset($queryParams['category_id']) ? (int) $queryParams['category_id'] : null;
            $page = (int) ($queryParams['page'] ?? 1);
            $limit = (int) ($queryParams['limit'] ?? 20);

            if (!$sourceId) {
                return $this->jsonError($response, 'source_id is required', 400);
            }

            if (!$type) {
                return $this->jsonError($response, 'type is required (live, vod, series)', 400);
            }

            // Validate type
            if (!in_array($type, ['live', 'vod', 'series'])) {
                return $this->jsonError($response, 'Invalid type. Must be live, vod, or series', 400);
            }

            $offset = ($page - 1) * $limit;
            $modelClass = $this->getModelClass($type);

            // Build query conditions
            $conditions = ['source_id' => $sourceId];
            if ($categoryId !== null) {
                $conditions['category_id'] = $categoryId;
            }

            // Get all streams matching conditions
            $allStreams = $modelClass::findAll($conditions);

            $total = count($allStreams);
            $paginatedStreams = array_slice($allStreams, $offset, $limit);
            $streamsData = array_map(fn($stream) => $this->formatStreamData($stream), $paginatedStreams);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $streamsData,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list streams: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get stream by ID
     */
    public function get(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid stream ID', 400);
            }

            // Try to find in all stream types
            $stream = LiveStream::find($id)
                   ?? VodStream::find($id)
                   ?? Series::find($id);

            if (!$stream) {
                return $this->jsonError($response, 'Stream not found', 404);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $this->formatStreamData($stream),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get stream: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Format stream data with parsed JSON fields
     */
    private function formatStreamData(object $stream): array
    {
        $data = $stream->toArray();

        // Parse data field if it's JSON
        if (isset($data['data']) && is_string($data['data'])) {
            $decoded = json_decode($data['data'], true);
            if (is_array($decoded)) {
                $data['data'] = $decoded;
            }
        }

        // Parse category_ids if it's JSON
        if (isset($data['category_ids']) && is_string($data['category_ids'])) {
            $decoded = json_decode($data['category_ids'], true);
            if (is_array($decoded)) {
                $data['category_ids'] = $decoded;
            }
        }

        return $data;
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
