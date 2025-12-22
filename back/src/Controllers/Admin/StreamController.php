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
     * Remove accents from string for case-insensitive and accent-insensitive comparison
     */
    private function normalizeString(string $string): string
    {
        // Convert to lowercase
        $string = strtolower($string);

        // Try to remove accents using iconv if available
        if (extension_loaded('iconv')) {
            $iconvResult = @iconv('UTF-8', 'ASCII//TRANSLIT//IGNORE', $string);
            if ($iconvResult !== false) {
                return $iconvResult;
            }
        }

        // Fallback: Use regex to remove common accents
        $replacements = [
            'à' => 'a', 'á' => 'a', 'â' => 'a', 'ã' => 'a', 'ä' => 'a', 'å' => 'a',
            'è' => 'e', 'é' => 'e', 'ê' => 'e', 'ë' => 'e',
            'ì' => 'i', 'í' => 'i', 'î' => 'i', 'ï' => 'i',
            'ò' => 'o', 'ó' => 'o', 'ô' => 'o', 'õ' => 'o', 'ö' => 'o',
            'ù' => 'u', 'ú' => 'u', 'û' => 'u', 'ü' => 'u',
            'ñ' => 'n', 'ç' => 'c',
        ];
        return strtr($string, $replacements);
    }

    /**
     * List streams by source and type (paginated, read-only)
     * Query params:
     *   - source_id (required)
     *   - type (required): live, vod, series
     *   - category_id (optional): filter by category
     *   - search (optional): search by stream name (case-insensitive)
     *   - stream_id (optional): filter by stream_id
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
            $search = $queryParams['search'] ?? null;
            $streamId = $queryParams['stream_id'] ?? null;
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

            // Filter by stream_id if provided
            if ($streamId !== null && $streamId !== '') {
                $allStreams = array_filter($allStreams, function($stream) use ($streamId) {
                    return (string) $stream->stream_id === (string) $streamId;
                });
                // Re-index array after filtering
                $allStreams = array_values($allStreams);
            }

            // Filter by search query (case-insensitive and accent-insensitive)
            if ($search) {
                $searchNormalized = $this->normalizeString($search);
                $allStreams = array_filter($allStreams, function($stream) use ($searchNormalized) {
                    return strpos($this->normalizeString($stream->name), $searchNormalized) !== false;
                });
                // Re-index array after filtering
                $allStreams = array_values($allStreams);
            }

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
     * Get stream by ID and optional type
     * Query params:
     *   - type (optional): live, vod, series (if not provided, searches all types)
     */
    public function get(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $type = $request->getQueryParams()['type'] ?? null;

            if (!$id) {
                return $this->jsonError($response, 'Invalid stream ID', 400);
            }

            // If type is specified, search only that type
            if ($type) {
                if (!in_array($type, ['live', 'vod', 'series'])) {
                    return $this->jsonError($response, 'Invalid type. Must be live, vod, or series', 400);
                }

                $modelClass = $this->getModelClass($type);
                $stream = $modelClass::find($id);
            } else {
                // Try to find in all stream types
                $stream = LiveStream::find($id)
                       ?? VodStream::find($id)
                       ?? Series::find($id);
            }

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
     * Update stream allow_deny field
     * Body params:
     *   - allow_deny: 'allow', 'deny', or null to remove override
     */
    public function updateAllowDeny(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $type = $request->getQueryParams()['type'] ?? null;

            if (!$id) {
                return $this->jsonError($response, 'Invalid stream ID', 400);
            }

            // Find stream
            $stream = null;
            if ($type) {
                if (!in_array($type, ['live', 'vod', 'series'])) {
                    return $this->jsonError($response, 'Invalid type. Must be live, vod, or series', 400);
                }
                $modelClass = $this->getModelClass($type);
                $stream = $modelClass::find($id);
            } else {
                // Try to find in all stream types
                $stream = LiveStream::find($id)
                       ?? VodStream::find($id)
                       ?? Series::find($id);
            }

            if (!$stream) {
                return $this->jsonError($response, 'Stream not found', 404);
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

            // Update stream
            $stream->allow_deny = $allowDeny;
            if (!$stream->save()) {
                return $this->jsonError($response, 'Failed to update stream', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Stream allow_deny updated successfully',
                'data' => $this->formatStreamData($stream),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to update stream: ' . $e->getMessage(), 500);
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
