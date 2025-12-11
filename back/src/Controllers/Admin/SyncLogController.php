<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\SyncLog;
use App\Models\Source;

class SyncLogController
{
    /**
     * List all sync logs (paginated)
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

            // Optional filters
            $sourceId = isset($queryParams['source_id']) ? (int) $queryParams['source_id'] : null;
            $syncType = $queryParams['sync_type'] ?? null;
            $status = $queryParams['status'] ?? null;

            // Build conditions
            $conditions = [];
            if ($sourceId !== null) {
                $conditions['source_id'] = $sourceId;
            }
            if ($syncType !== null) {
                $conditions['sync_type'] = $syncType;
            }
            if ($status !== null) {
                $conditions['status'] = $status;
            }

            // Get all sync logs with filters
            $allLogs = SyncLog::findAll($conditions, ['started_at' => 'DESC']);
            $total = count($allLogs);

            // Get paginated results
            $paginatedLogs = array_slice($allLogs, $offset, $limit);

            // Convert models to arrays and enrich with source name
            $logsData = array_map(function($log) {
                $logArray = $log->toArray();

                // Add source name if available
                if (isset($logArray['source_id'])) {
                    $source = Source::find((int) $logArray['source_id']);
                    $logArray['source_name'] = $source ? $source->name : null;
                }

                return $logArray;
            }, $paginatedLogs);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $logsData,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list sync logs: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get sync log by ID
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
                return $this->jsonError($response, 'Invalid sync log ID', 400);
            }

            $syncLog = SyncLog::find($id);
            if (!$syncLog) {
                return $this->jsonError($response, 'Sync log not found', 404);
            }

            $logData = $syncLog->toArray();

            // Add source information
            if (isset($logData['source_id'])) {
                $source = Source::find((int) $logData['source_id']);
                if ($source) {
                    $logData['source'] = [
                        'id' => $source->id,
                        'name' => $source->name,
                        'url' => $source->url,
                    ];
                }
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $logData,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get sync log: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Delete sync log
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
                return $this->jsonError($response, 'Invalid sync log ID', 400);
            }

            $syncLog = SyncLog::find($id);
            if (!$syncLog) {
                return $this->jsonError($response, 'Sync log not found', 404);
            }

            if (!$syncLog->delete()) {
                return $this->jsonError($response, 'Failed to delete sync log', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Sync log deleted successfully',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to delete sync log: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get sync log statistics
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function stats(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $sourceId = isset($queryParams['source_id']) ? (int) $queryParams['source_id'] : null;
            $syncType = $queryParams['sync_type'] ?? null;

            // Get statistics
            if ($sourceId) {
                $stats = SyncLog::getStats($sourceId, $syncType);
            } else {
                // Global statistics across all sources
                $allLogs = SyncLog::findAll();
                $stats = [
                    'total_syncs' => count($allLogs),
                    'completed_syncs' => count(array_filter($allLogs, fn($log) => $log->status === SyncLog::STATUS_COMPLETED)),
                    'failed_syncs' => count(array_filter($allLogs, fn($log) => $log->status === SyncLog::STATUS_FAILED)),
                    'running_syncs' => count(array_filter($allLogs, fn($log) => $log->status === SyncLog::STATUS_RUNNING)),
                    'total_added' => array_sum(array_map(fn($log) => $log->items_added, $allLogs)),
                    'total_updated' => array_sum(array_map(fn($log) => $log->items_updated, $allLogs)),
                    'total_deleted' => array_sum(array_map(fn($log) => $log->items_deleted, $allLogs)),
                ];
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $stats,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get sync log stats: ' . $e->getMessage(), 500);
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
