<?php

declare(strict_types=1);

namespace App\Controllers;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Source;
use App\Models\Client;
use App\Models\Filter;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;

class DashboardController
{
    /**
     * Get system statistics
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function stats(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            // Get counts
            $sources = Source::findAll();
            $clients = Client::findAll();
            $filters = Filter::findAll();
            $liveStreams = LiveStream::findAll();
            $vodStreams = VodStream::findAll();
            $series = Series::findAll();

            // Count active items
            $activeSources = array_filter($sources, function ($source) {
                return $source->is_active == 1;
            });
            $activeClients = array_filter($clients, function ($client) {
                return $client->is_active == 1;
            });

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => [
                    'sources' => [
                        'total' => count($sources),
                        'active' => count($activeSources),
                    ],
                    'clients' => [
                        'total' => count($clients),
                        'active' => count($activeClients),
                    ],
                    'filters' => [
                        'total' => count($filters),
                    ],
                    'streams' => [
                        'live' => count($liveStreams),
                        'vod' => count($vodStreams),
                        'series' => count($series),
                        'total' => count($liveStreams) + count($vodStreams) + count($series),
                    ],
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get stats: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get recent activity logs
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function activity(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $limit = (int) ($queryParams['limit'] ?? 50);

            // For now, return empty activity logs
            // This would be populated from connection_logs and sync_logs tables
            $activities = [];

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $activities,
                'count' => count($activities),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get activity: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get sync status for all sources
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function syncStatus(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $sources = Source::findAll();

            $syncStatus = array_map(function ($source) {
                return [
                    'id' => $source->id,
                    'name' => $source->name,
                    'sync_status' => $source->sync_status,
                    'last_sync' => $source->last_sync,
                    'next_sync' => $source->next_sync,
                    'sync_interval' => $source->sync_interval,
                ];
            }, $sources);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $syncStatus,
                'count' => count($syncStatus),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get sync status: ' . $e->getMessage(), 500);
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
