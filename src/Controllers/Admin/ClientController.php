<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Client;
use App\Models\Filter;
use App\Services\FilterService;
use App\Services\ContentFilterService;

class ClientController
{
    /**
     * List all clients (paginated, searchable)
     */
    public function list(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $page = (int) ($queryParams['page'] ?? 1);
            $limit = (int) ($queryParams['limit'] ?? 10);
            $search = $queryParams['search'] ?? null;
            $offset = ($page - 1) * $limit;

            $allClients = Client::findAll();

            if ($search) {
                $allClients = array_filter($allClients, function ($client) use ($search) {
                    return stripos($client->username, $search) !== false ||
                           stripos($client->password, $search) !== false ||
                           stripos($client->email ?? '', $search) !== false;
                });
            }

            $total = count($allClients);
            $paginatedClients = array_slice($allClients, $offset, $limit);
            $clientsData = array_map(fn($client) => $client->toArray(), $paginatedClients);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $clientsData,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list clients: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get client by ID
     */
    public function get(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $client->toArray(),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get client: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Create new client
     */
    public function create(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $body = json_decode($request->getBody()->getContents(), true);

            if (empty($body['username'])) {
                return $this->jsonError($response, 'Username is required', 400);
            }
            if (empty($body['password'])) {
                return $this->jsonError($response, 'Password is required', 400);
            }
            if (empty($body['source_id'])) {
                return $this->jsonError($response, 'Source ID is required', 400);
            }

            $client = new Client();
            $client->username = $body['username'];
            $client->password = $body['password'];
            $client->source_id = (int) $body['source_id'];
            $client->filter_id = isset($body['filter_id']) ? (int) $body['filter_id'] : null;
            $client->email = $body['email'] ?? null;
            $client->is_active = $body['is_active'] ?? 1;
            $client->hide_adult_content = (int) ($body['hide_adult_content'] ?? 0);

            if (!$client->save()) {
                return $this->jsonError($response, 'Failed to create client', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Client created successfully',
                'data' => $client->toArray(),
            ], 201);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to create client: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Update client
     */
    public function update(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            if (isset($body['username'])) {
                $client->username = $body['username'];
            }
            if (isset($body['password'])) {
                $client->password = $body['password'];
            }
            if (isset($body['source_id'])) {
                $client->source_id = (int) $body['source_id'];
            }
            if (isset($body['filter_id'])) {
                $client->filter_id = $body['filter_id'] ? (int) $body['filter_id'] : null;
            }
            if (isset($body['email'])) {
                $client->email = $body['email'];
            }
            if (isset($body['is_active'])) {
                $client->is_active = (int) $body['is_active'];
            }
            if (isset($body['hide_adult_content'])) {
                $client->hide_adult_content = (int) $body['hide_adult_content'];
            }

            if (!$client->save()) {
                return $this->jsonError($response, 'Failed to update client', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Client updated successfully',
                'data' => $client->toArray(),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to update client: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Delete client
     */
    public function delete(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            if (!$client->delete()) {
                return $this->jsonError($response, 'Failed to delete client', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Client deleted successfully',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to delete client: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get connection logs for client
     */
    public function logs(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $logs = [];

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $logs,
                'count' => count($logs),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get client logs: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export live categories for client
     * GET /api/clients/{id}/export/live-categories
     */
    public function exportLiveCategories(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $categories = [];
            $contentFilterService = new ContentFilterService($client);

            // Add favoris virtual categories if filter assigned
            $filter = $client->filter_id ? Filter::find($client->filter_id) : null;
            if ($filter !== null) {
                $filterServiceBase = new FilterService($filter, (bool) $client->hide_adult_content);
                $favorisCategories = $filterServiceBase->generateFavorisCategories();
                $categories = array_merge($categories, $favorisCategories);
            }

            // Add allowed regular categories (filtered by rules)
            $allowedCategories = array_map(
                [ContentFilterService::class, 'formatCategory'],
                $contentFilterService->getAllowedCategories('live')
            );
            $categories = array_merge($categories, $allowedCategories);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get live categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export VOD categories for client
     * GET /api/clients/{id}/export/vod-categories
     */
    public function exportVodCategories(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $categories = [];
            $contentFilterService = new ContentFilterService($client);

            // Add favoris virtual categories if filter assigned
            $filter = $client->filter_id ? Filter::find($client->filter_id) : null;
            if ($filter !== null) {
                $filterServiceBase = new FilterService($filter, (bool) $client->hide_adult_content);
                $favorisCategories = $filterServiceBase->generateFavorisCategories();
                $categories = array_merge($categories, $favorisCategories);
            }

            // Add allowed regular categories (filtered by rules)
            $allowedCategories = array_map(
                [ContentFilterService::class, 'formatCategory'],
                $contentFilterService->getAllowedCategories('vod')
            );
            $categories = array_merge($categories, $allowedCategories);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get VOD categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export series categories for client
     * GET /api/clients/{id}/export/series-categories
     */
    public function exportSeriesCategories(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $categories = [];
            $contentFilterService = new ContentFilterService($client);

            // Add favoris virtual categories if filter assigned
            $filter = $client->filter_id ? Filter::find($client->filter_id) : null;
            if ($filter !== null) {
                $filterServiceBase = new FilterService($filter, (bool) $client->hide_adult_content);
                $favorisCategories = $filterServiceBase->generateFavorisCategories();
                $categories = array_merge($categories, $favorisCategories);
            }

            // Add allowed regular categories (filtered by rules)
            $allowedCategories = array_map(
                [ContentFilterService::class, 'formatCategory'],
                $contentFilterService->getAllowedCategories('series')
            );
            $categories = array_merge($categories, $allowedCategories);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get series categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export live streams for client
     * GET /api/clients/{id}/export/live-streams
     */
    public function exportLiveStreams(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $streams = array_map(
                [ContentFilterService::class, 'formatStream'],
                $filterService->getAllowedStreams('live')
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $streams,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get live streams: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export VOD streams for client
     * GET /api/clients/{id}/export/vod-streams
     */
    public function exportVodStreams(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $streams = array_map(
                [ContentFilterService::class, 'formatStream'],
                $filterService->getAllowedStreams('vod')
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $streams,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get VOD streams: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export series for client
     * GET /api/clients/{id}/export/series
     */
    public function exportSeries(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $series = array_map(
                [ContentFilterService::class, 'formatStream'],
                $filterService->getAllowedSeries()
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $series,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get series: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export blocked live categories
     * GET /api/clients/{id}/export/blocked-live-categories
     */
    public function exportBlockedLiveCategories(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $categories = array_map(
                [ContentFilterService::class, 'formatCategory'],
                $filterService->getBlockedCategories('live')
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get blocked live categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export blocked VOD categories
     * GET /api/clients/{id}/export/blocked-vod-categories
     */
    public function exportBlockedVodCategories(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $categories = array_map(
                [ContentFilterService::class, 'formatCategory'],
                $filterService->getBlockedCategories('vod')
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get blocked VOD categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export blocked series categories
     * GET /api/clients/{id}/export/blocked-series-categories
     */
    public function exportBlockedSeriesCategories(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $categories = array_map(
                [ContentFilterService::class, 'formatCategory'],
                $filterService->getBlockedCategories('series')
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get blocked series categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export blocked live streams
     * GET /api/clients/{id}/export/blocked-live-streams
     */
    public function exportBlockedLiveStreams(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $streams = array_map(
                [ContentFilterService::class, 'formatStream'],
                $filterService->getBlockedStreams('live')
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $streams,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get blocked live streams: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export blocked VOD streams
     * GET /api/clients/{id}/export/blocked-vod-streams
     */
    public function exportBlockedVodStreams(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $streams = array_map(
                [ContentFilterService::class, 'formatStream'],
                $filterService->getBlockedStreams('vod')
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $streams,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get blocked VOD streams: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export blocked series
     * GET /api/clients/{id}/export/blocked-series
     */
    public function exportBlockedSeries(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $filterService = new ContentFilterService($client);
            $series = array_map(
                [ContentFilterService::class, 'formatStream'],
                $filterService->getBlockedSeries()
            );

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $series,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get blocked series: ' . $e->getMessage(), 500);
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
