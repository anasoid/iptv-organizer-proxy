<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Source;

class SourceController
{
    /**
     * List all sources (paginated)
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

            // Get total count
            $sources = Source::findAll();
            $total = count($sources);

            // Get paginated results
            $paginatedSources = array_slice($sources, $offset, $limit);

            // Convert models to arrays
            $sourcesData = array_map(fn($source) => $source->toArray(), $paginatedSources);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $sourcesData,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list sources: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get source by ID
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
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $source->toArray(),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get source: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Create new source
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
                return $this->jsonError($response, 'Source name is required', 400);
            }
            if (empty($body['url'])) {
                return $this->jsonError($response, 'Source URL is required', 400);
            }
            if (empty($body['username'])) {
                return $this->jsonError($response, 'Username is required', 400);
            }
            if (empty($body['password'])) {
                return $this->jsonError($response, 'Password is required', 400);
            }

            // Create source
            $source = new Source();
            $source->name = $body['name'];
            $source->url = $body['url'];
            $source->username = $body['username'];
            $source->password = $body['password'];
            $source->sync_interval = $body['sync_interval'] ?? 1;
            $source->sync_status = 'idle';
            $source->is_active = $body['is_active'] ?? 1;

            if (!$source->save()) {
                return $this->jsonError($response, 'Failed to create source', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Source created successfully',
                'data' => $source->toArray(),
            ], 201);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to create source: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Update source
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
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            // Update fields
            if (isset($body['name'])) {
                $source->name = $body['name'];
            }
            if (isset($body['url'])) {
                $source->url = $body['url'];
            }
            if (isset($body['username'])) {
                $source->username = $body['username'];
            }
            if (isset($body['password'])) {
                $source->password = $body['password'];
            }
            if (isset($body['sync_interval'])) {
                $source->sync_interval = (int) $body['sync_interval'];
            }
            if (isset($body['is_active'])) {
                $source->is_active = (int) $body['is_active'];
            }

            if (!$source->save()) {
                return $this->jsonError($response, 'Failed to update source', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Source updated successfully',
                'data' => $source->toArray(),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to update source: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Delete source
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
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            if (!$source->delete()) {
                return $this->jsonError($response, 'Failed to delete source', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Source deleted successfully',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to delete source: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Test source connection
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function testConnection(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            // Test connection
            $isConnected = $source->testConnection();

            return $this->jsonResponse($response, [
                'success' => true,
                'connected' => $isConnected,
                'message' => $isConnected ? 'Connection successful' : 'Connection failed',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Connection test failed: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Trigger manual sync for source
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function sync(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            // Update sync status
            if (!$source->updateSyncStatus('syncing')) {
                return $this->jsonError($response, 'Failed to update sync status', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Sync started for source',
                'data' => [
                    'source_id' => $source->id,
                    'sync_status' => 'syncing',
                    'timestamp' => time(),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to trigger sync: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get sync logs for source
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function syncLogs(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            $queryParams = $request->getQueryParams();
            $limit = (int) ($queryParams['limit'] ?? 50);

            $logs = $source->syncLogs($limit);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $logs,
                'count' => count($logs),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get sync logs: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Trigger sync for specific task type
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function syncTaskType(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $taskType = $args['taskType'] ?? '';

            if (!$id) {
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $validTaskTypes = ['live_categories', 'live_streams', 'vod_categories', 'vod_streams', 'series_categories', 'series'];
            if (!in_array($taskType, $validTaskTypes)) {
                return $this->jsonError($response, 'Invalid task type', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            if (!$source->is_active) {
                return $this->jsonError($response, 'Source is not active', 400);
            }

            // Create XtreamClient and SyncService
            $client = new \App\Services\Xtream\XtreamClient($source);
            $syncService = new \App\Services\SyncService($source, $client);

            // Map task type to method
            $methodMap = [
                'live_categories' => 'syncLiveCategories',
                'live_streams' => 'syncLiveStreams',
                'vod_categories' => 'syncVodCategories',
                'vod_streams' => 'syncVodStreams',
                'series_categories' => 'syncSeriesCategories',
                'series' => 'syncSeries',
            ];

            $method = $methodMap[$taskType];
            $result = $syncService->$method();

            // Check if sync returned an error (e.g., already running)
            if (isset($result['error'])) {
                return $this->jsonResponse($response, [
                    'success' => false,
                    'message' => $result['error'],
                    'data' => $result,
                ], 409); // 409 Conflict status code
            }

            // Sync completed successfully - update status to idle
            $source->updateSyncStatus('idle');

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => "Sync completed for {$taskType}",
                'data' => $result,
            ]);

        } catch (\Throwable $e) {
            // Sync failed - update status to error
            $source->updateSyncStatus('error');
            return $this->jsonError($response, 'Sync failed: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Trigger sync for all task types in correct order
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function syncAll(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);

            if (!$id) {
                return $this->jsonError($response, 'Invalid source ID', 400);
            }

            $source = Source::find($id);
            if (!$source) {
                return $this->jsonError($response, 'Source not found', 404);
            }

            if (!$source->is_active) {
                return $this->jsonError($response, 'Source is not active', 400);
            }

            // Create XtreamClient and SyncService
            $client = new \App\Services\Xtream\XtreamClient($source);
            $syncService = new \App\Services\SyncService($source, $client);

            // Execute in correct dependency order: categories before streams
            $tasks = [
                'live_categories' => 'syncLiveCategories',
                'live_streams' => 'syncLiveStreams',
                'vod_categories' => 'syncVodCategories',
                'vod_streams' => 'syncVodStreams',
                'series_categories' => 'syncSeriesCategories',
                'series' => 'syncSeries',
            ];

            $results = [];
            $totalStats = ['added' => 0, 'updated' => 0, 'deleted' => 0];

            foreach ($tasks as $taskType => $method) {
                try {
                    $stats = $syncService->$method();
                    $results[$taskType] = $stats;

                    if (isset($stats['error'])) {
                        continue;
                    }

                    $totalStats['added'] += $stats['added'] ?? 0;
                    $totalStats['updated'] += $stats['updated'] ?? 0;
                    $totalStats['deleted'] += $stats['deleted'] ?? 0;
                } catch (\Exception $e) {
                    $results[$taskType] = ['error' => $e->getMessage()];
                }
            }

            // Check if any task had an error
            $hasErrors = false;
            $errorMessage = '';
            foreach ($results as $taskType => $result) {
                if (isset($result['error'])) {
                    $hasErrors = true;
                    if (empty($errorMessage)) {
                        $errorMessage = "{$taskType}: {$result['error']}";
                    }
                }
            }

            // If there are errors, update status to error and return
            if ($hasErrors) {
                $source->updateSyncStatus('error');
                return $this->jsonResponse($response, [
                    'success' => false,
                    'message' => $errorMessage,
                    'data' => [
                        'results' => $results,
                        'summary' => $totalStats,
                    ],
                ], 409); // 409 Conflict status code
            }

            // All syncs completed successfully - update status to idle
            $source->updateSyncStatus('idle');

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Sync all completed',
                'data' => [
                    'results' => $results,
                    'summary' => $totalStats,
                ],
            ]);

        } catch (\Throwable $e) {
            // Sync failed - update status to error
            $source->updateSyncStatus('error');
            return $this->jsonError($response, 'Sync all failed: ' . $e->getMessage(), 500);
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
