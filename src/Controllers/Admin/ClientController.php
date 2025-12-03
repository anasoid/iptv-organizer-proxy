<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Client;
use App\Models\Category;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;
use App\Models\Filter;
use App\Services\FilterService;

class ClientController
{
    /**
     * List all clients (paginated, searchable)
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
            $search = $queryParams['search'] ?? null;
            $offset = ($page - 1) * $limit;

            // Get all clients (filter by search if provided)
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

            // Convert models to arrays
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
            if (empty($body['username'])) {
                return $this->jsonError($response, 'Username is required', 400);
            }
            if (empty($body['password'])) {
                return $this->jsonError($response, 'Password is required', 400);
            }
            if (empty($body['source_id'])) {
                return $this->jsonError($response, 'Source ID is required', 400);
            }

            // Create client
            $client = new Client();
            $client->username = $body['username'];
            $client->password = $body['password'];
            $client->source_id = (int) $body['source_id'];
            $client->filter_id = isset($body['filter_id']) ? (int) $body['filter_id'] : null;
            $client->email = $body['email'] ?? null;
            $client->is_active = $body['is_active'] ?? 1;

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
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            // Update fields
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
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
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

            $queryParams = $request->getQueryParams();
            $limit = (int) ($queryParams['limit'] ?? 50);

            // Get connection logs (this will need a connectionLogs method on Client model)
            // For now, return empty logs
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
     * Get blocked categories and streams by type for client
     * GET /api/clients/{id}/blocked-items
     */
    public function getBlockedItems(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            // If no filter is assigned, return empty
            if (!$client->filter_id) {
                return $this->jsonResponse($response, [
                    'success' => true,
                    'data' => [
                        'has_filter' => false,
                        'blocked_categories' => [],
                        'blocked_streams' => [],
                    ],
                ]);
            }

            // Get the filter
            $filter = Filter::find($client->filter_id);
            if (!$filter) {
                return $this->jsonResponse($response, [
                    'success' => true,
                    'data' => [
                        'has_filter' => false,
                        'blocked_categories' => [],
                        'blocked_streams' => [],
                    ],
                ]);
            }

            $filterService = new FilterService($filter, (bool) $client->hide_adult_content);

            // Get all streams first to determine blocked by rules
            $liveStreams = LiveStream::getBySource($client->source_id, false);
            $vodStreams = VodStream::getBySource($client->source_id, false);
            $seriesList = Series::getBySource($client->source_id, false);

            // Apply filter to streams
            $filteredLive = $filterService->applyToStreams($liveStreams);
            $filteredVod = $filterService->applyToStreams($vodStreams);
            $filteredSeries = $filterService->applyToStreams($seriesList);

            // Get all categories and apply category filters
            $allLiveCategories = Category::getBySourceAndType($client->source_id, 'live');
            $allVodCategories = Category::getBySourceAndType($client->source_id, 'vod');
            $allSeriesCategories = Category::getBySourceAndType($client->source_id, 'series');

            // Apply category filters
            $filteredLiveCategories = $filterService->filterCategories($allLiveCategories);
            $filteredVodCategories = $filterService->filterCategories($allVodCategories);
            $filteredSeriesCategories = $filterService->filterCategories($allSeriesCategories);

            // Get blocked categories: those blocked directly by filter rules OR have no allowed streams
            $blockedCategories = [];

            // Helper function to get blocked categories
            $getBlockedCategories = function($allStreams, $filteredStreams, $allCategories, $filteredCategories) {
                // Get IDs of filtered streams
                $allowedStreamIds = array_map(fn($s) => $s->id, $filteredStreams);

                // Get IDs of filtered categories
                $allowedCategoryIds = array_map(fn($c) => $c->category_id, $filteredCategories);

                // Get all category IDs from streams
                $allCategoryIds = array_unique(array_map(fn($s) => $s->category_id, $allStreams));

                // For each category, check if it's blocked by filter rules OR has no allowed streams
                $blockedCategoryIds = [];
                foreach ($allCategoryIds as $catId) {
                    // If category is not in filtered categories, it's blocked by rule
                    if (!in_array($catId, $allowedCategoryIds)) {
                        $blockedCategoryIds[] = $catId;
                        continue;
                    }

                    // If category is allowed by rule, check if it has any allowed streams
                    $streamsInCategory = array_filter($allStreams, fn($s) => $s->category_id == $catId);
                    $allowedInCategory = array_filter($streamsInCategory, fn($s) => in_array($s->id, $allowedStreamIds));

                    // If no allowed streams in this category, it's blocked
                    if (empty($allowedInCategory)) {
                        $blockedCategoryIds[] = $catId;
                    }
                }

                return array_filter($allCategories, fn($c) => in_array($c->category_id, $blockedCategoryIds));
            };

            // Get blocked categories for each type
            $blockedCategories['live'] = $getBlockedCategories($liveStreams, $filteredLive, $allLiveCategories, $filteredLiveCategories);
            $blockedCategories['vod'] = $getBlockedCategories($vodStreams, $filteredVod, $allVodCategories, $filteredVodCategories);
            $blockedCategories['series'] = $getBlockedCategories($seriesList, $filteredSeries, $allSeriesCategories, $filteredSeriesCategories);

            // Format blocked categories
            $blockedCategories = [
                'live' => array_map(fn($c) => [
                    'id' => $c->id,
                    'category_id' => $c->category_id,
                    'category_name' => $c->category_name,
                    'parent_id' => $c->parent_id ?? 0,
                ], $blockedCategories['live']),
                'vod' => array_map(fn($c) => [
                    'id' => $c->id,
                    'category_id' => $c->category_id,
                    'category_name' => $c->category_name,
                    'parent_id' => $c->parent_id ?? 0,
                ], $blockedCategories['vod']),
                'series' => array_map(fn($c) => [
                    'id' => $c->id,
                    'category_id' => $c->category_id,
                    'category_name' => $c->category_name,
                    'parent_id' => $c->parent_id ?? 0,
                ], $blockedCategories['series']),
            ];

            // Get blocked streams
            $blockedStreams = [];

            // Blocked streams are those not in the filtered list
            $filteredLiveIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filteredLive);
            $filteredVodIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filteredVod);
            $filteredSeriesIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filteredSeries);

            $blockedLive = array_values(array_filter($liveStreams, function ($stream) use ($filteredLiveIds) {
                return !in_array($stream->id, $filteredLiveIds);
            }));

            $blockedVod = array_values(array_filter($vodStreams, function ($stream) use ($filteredVodIds) {
                return !in_array($stream->id, $filteredVodIds);
            }));

            $blockedSeriesItems = array_values(array_filter($seriesList, function ($series) use ($filteredSeriesIds) {
                return !in_array($series->id, $filteredSeriesIds);
            }));

            // Format response
            $blockedStreams['live'] = array_map(fn($s) => [
                'id' => $s->id,
                'name' => $s->name,
                'num' => $s->num ?? null,
                'category_id' => $s->category_id,
            ], $blockedLive);

            $blockedStreams['vod'] = array_map(fn($s) => [
                'id' => $s->id,
                'name' => $s->name,
                'num' => $s->num ?? null,
                'category_id' => $s->category_id,
            ], $blockedVod);

            $blockedStreams['series'] = array_map(fn($s) => [
                'id' => $s->id,
                'name' => $s->name,
                'num' => $s->num ?? null,
                'category_id' => $s->category_id,
            ], $blockedSeriesItems);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => [
                    'has_filter' => true,
                    'filter_name' => $filter->name,
                    'blocked_categories' => $blockedCategories,
                    'blocked_streams' => $blockedStreams,
                    'blocked_counts' => [
                        'categories_live' => count($blockedCategories['live']),
                        'categories_vod' => count($blockedCategories['vod']),
                        'categories_series' => count($blockedCategories['series']),
                        'streams_live' => count($blockedLive),
                        'streams_vod' => count($blockedVod),
                        'streams_series' => count($blockedSeriesItems),
                        'total_categories' => count($blockedCategories['live']) + count($blockedCategories['vod']) + count($blockedCategories['series']),
                        'total_streams' => count($blockedLive) + count($blockedVod) + count($blockedSeriesItems),
                        'total' => count($blockedCategories['live']) + count($blockedCategories['vod']) + count($blockedCategories['series']) + count($blockedLive) + count($blockedVod) + count($blockedSeriesItems),
                    ],
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get blocked items: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Export blocked items for client (JSON export)
     * GET /api/clients/{id}/export/blocked-items
     */
    public function exportBlockedItems(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            // If no filter is assigned, return empty
            if (!$client->filter_id) {
                return $this->jsonResponse($response, [
                    'success' => true,
                    'data' => [
                        'has_filter' => false,
                        'blocked_by_type' => [],
                    ],
                ]);
            }

            // Get the filter
            $filter = Filter::find($client->filter_id);
            if (!$filter) {
                return $this->jsonResponse($response, [
                    'success' => true,
                    'data' => [
                        'has_filter' => false,
                        'blocked_by_type' => [],
                    ],
                ]);
            }

            $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
            $proxyUrl = $_ENV['APP_URL'] ?? $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

            // Get all streams first to determine blocked by rules
            $liveStreams = LiveStream::getBySource($client->source_id, false);
            $vodStreams = VodStream::getBySource($client->source_id, false);
            $seriesList = Series::getBySource($client->source_id, false);

            // Apply filter to streams
            $filteredLive = $filterService->applyToStreams($liveStreams);
            $filteredVod = $filterService->applyToStreams($vodStreams);
            $filteredSeries = $filterService->applyToStreams($seriesList);

            // Get all categories and apply category filters
            $allLiveCategories = Category::getBySourceAndType($client->source_id, 'live');
            $allVodCategories = Category::getBySourceAndType($client->source_id, 'vod');
            $allSeriesCategories = Category::getBySourceAndType($client->source_id, 'series');

            // Apply category filters
            $filteredLiveCategories = $filterService->filterCategories($allLiveCategories);
            $filteredVodCategories = $filterService->filterCategories($allVodCategories);
            $filteredSeriesCategories = $filterService->filterCategories($allSeriesCategories);

            // Get blocked categories: those blocked directly by filter rules OR have no allowed streams
            $blockedCategories = [];

            // Helper function to get blocked categories
            $getBlockedCategories = function($allStreams, $filteredStreams, $allCategories, $filteredCategories) {
                // Get IDs of filtered streams
                $allowedStreamIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filteredStreams);

                // Get IDs of filtered categories
                $allowedCategoryIds = array_map(fn($c) => $c->category_id, $filteredCategories);

                // Get all category IDs from streams
                $allCategoryIds = array_unique(array_map(fn($s) => $s->category_id, $allStreams));

                // For each category, check if it's blocked by filter rules OR has no allowed streams
                $blockedCategoryIds = [];
                foreach ($allCategoryIds as $catId) {
                    // If category is not in filtered categories, it's blocked by rule
                    if (!in_array($catId, $allowedCategoryIds)) {
                        $blockedCategoryIds[] = $catId;
                        continue;
                    }

                    // If category is allowed by rule, check if it has any allowed streams
                    $streamsInCategory = array_filter($allStreams, fn($s) => $s->category_id == $catId);
                    $allowedInCategory = array_filter($streamsInCategory, fn($s) => in_array($s->id, $allowedStreamIds));

                    // If no allowed streams in this category, it's blocked
                    if (empty($allowedInCategory)) {
                        $blockedCategoryIds[] = $catId;
                    }
                }

                return array_filter($allCategories, fn($c) => in_array($c->category_id, $blockedCategoryIds));
            };

            // Get blocked categories for each type
            $blockedLiveCategories = $getBlockedCategories($liveStreams, $filteredLive, $allLiveCategories, $filteredLiveCategories);
            $blockedVodCategories = $getBlockedCategories($vodStreams, $filteredVod, $allVodCategories, $filteredVodCategories);
            $blockedSeriesCategories = $getBlockedCategories($seriesList, $filteredSeries, $allSeriesCategories, $filteredSeriesCategories);

            // Format blocked categories
            $blockedCategories = [
                'live' => array_map(fn($c) => [
                    'category_id' => (string) $c->category_id,
                    'category_name' => $c->category_name,
                    'parent_id' => (int) ($c->parent_id ?? 0),
                ], $blockedLiveCategories),
                'vod' => array_map(fn($c) => [
                    'category_id' => (string) $c->category_id,
                    'category_name' => $c->category_name,
                    'parent_id' => (int) ($c->parent_id ?? 0),
                ], $blockedVodCategories),
                'series' => array_map(fn($c) => [
                    'category_id' => (string) $c->category_id,
                    'category_name' => $c->category_name,
                    'parent_id' => (int) ($c->parent_id ?? 0),
                ], $blockedSeriesCategories),
            ];

            // Get blocked streams
            $blockedStreams = [];

            // Blocked streams are those not in the filtered list
            $filteredLiveIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filteredLive);
            $filteredVodIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filteredVod);
            $filteredSeriesIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filteredSeries);

            $blockedLive = array_values(array_filter($liveStreams, function ($stream) use ($filteredLiveIds) {
                return !in_array($stream->id, $filteredLiveIds);
            }));

            // Check VOD streams
            $blockedVod = array_values(array_filter($vodStreams, function ($stream) use ($filteredVodIds) {
                return !in_array($stream->id, $filteredVodIds);
            }));

            // Check series
            $blockedSeriesItems = array_values(array_filter($seriesList, function ($series) use ($filteredSeriesIds) {
                return !in_array($series->id, $filteredSeriesIds);
            }));

            // Format response in Xtream format
            $blockedStreams['live'] = array_map(fn($s) => $s->toXtreamFormat($proxyUrl, $client->username, $client->password), $blockedLive);
            $blockedStreams['vod'] = array_map(fn($s) => $s->toXtreamFormat($proxyUrl, $client->username, $client->password), $blockedVod);
            $blockedStreams['series'] = array_map(fn($s) => $s->toXtreamFormat($proxyUrl, $client->username, $client->password), $blockedSeriesItems);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => [
                    'has_filter' => true,
                    'filter_name' => $filter->name,
                    'blocked_categories' => $blockedCategories,
                    'blocked_streams' => $blockedStreams,
                    'blocked_counts' => [
                        'categories_live' => count($blockedCategories['live']),
                        'categories_vod' => count($blockedCategories['vod']),
                        'categories_series' => count($blockedCategories['series']),
                        'streams_live' => count($blockedLive),
                        'streams_vod' => count($blockedVod),
                        'streams_series' => count($blockedSeriesItems),
                        'total_categories' => count($blockedCategories['live']) + count($blockedCategories['vod']) + count($blockedCategories['series']),
                        'total_streams' => count($blockedLive) + count($blockedVod) + count($blockedSeriesItems),
                        'total' => count($blockedCategories['live']) + count($blockedCategories['vod']) + count($blockedCategories['series']) + count($blockedLive) + count($blockedVod) + count($blockedSeriesItems),
                    ],
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to export blocked items: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get live categories for client (JSON export)
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

            // Get categories from database
            $regularCategories = Category::getBySourceAndType($client->source_id, 'live');

            // Apply filter if client has one assigned
            if ($client->filter_id) {
                $filter = Filter::find($client->filter_id);
                if ($filter) {
                    $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
                    $regularCategories = $filterService->filterCategories($regularCategories);
                }
            }

            // Format as Xtream API response
            $categories = [];
            foreach ($regularCategories as $category) {
                $categories[] = [
                    'category_id' => (string) $category->category_id,
                    'category_name' => $category->category_name,
                    'parent_id' => (int) ($category->parent_id ?? 0),
                ];
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get live categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get VOD categories for client (JSON export)
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

            // Get categories from database
            $regularCategories = Category::getBySourceAndType($client->source_id, 'vod');

            // Apply filter if client has one assigned
            if ($client->filter_id) {
                $filter = Filter::find($client->filter_id);
                if ($filter) {
                    $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
                    $regularCategories = $filterService->filterCategories($regularCategories);
                }
            }

            // Format as Xtream API response
            $categories = [];
            foreach ($regularCategories as $category) {
                $categories[] = [
                    'category_id' => (string) $category->category_id,
                    'category_name' => $category->category_name,
                    'parent_id' => (int) ($category->parent_id ?? 0),
                ];
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get VOD categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get series categories for client (JSON export)
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

            // Get categories from database
            $regularCategories = Category::getBySourceAndType($client->source_id, 'series');

            // Apply filter if client has one assigned
            if ($client->filter_id) {
                $filter = Filter::find($client->filter_id);
                if ($filter) {
                    $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
                    $regularCategories = $filterService->filterCategories($regularCategories);
                }
            }

            // Format as Xtream API response
            $categories = [];
            foreach ($regularCategories as $category) {
                $categories[] = [
                    'category_id' => (string) $category->category_id,
                    'category_name' => $category->category_name,
                    'parent_id' => (int) ($category->parent_id ?? 0),
                ];
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $categories,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get series categories: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get live streams for client (JSON export)
     * GET /api/clients/{id}/export/live-streams?category_id=X
     */
    public function exportLiveStreams(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $categoryId = isset($request->getQueryParams()['category_id']) ? (int) $request->getQueryParams()['category_id'] : null;

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $proxyUrl = $_ENV['APP_URL'] ?? $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

            // Get streams from database
            if ($categoryId !== null && $categoryId < 100000) {
                // Regular category filter
                $streams = LiveStream::getByCategory($client->source_id, $categoryId);
            } else {
                // All streams
                $streams = LiveStream::getBySource($client->source_id, true);
            }

            // Convert to Xtream format
            $result = [];
            foreach ($streams as $stream) {
                $streamData = $stream->toXtreamFormat($proxyUrl, $client->username, $client->password);
                $result[] = $streamData;
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $result,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get live streams: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get VOD streams for client (JSON export)
     * GET /api/clients/{id}/export/vod-streams?category_id=X
     */
    public function exportVodStreams(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $categoryId = isset($request->getQueryParams()['category_id']) ? (int) $request->getQueryParams()['category_id'] : null;

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $proxyUrl = $_ENV['APP_URL'] ?? $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

            // Get streams from database
            if ($categoryId !== null && $categoryId < 100000) {
                // Regular category filter
                $streams = VodStream::getByCategory($client->source_id, $categoryId);
            } else {
                // All streams
                $streams = VodStream::getBySource($client->source_id, true);
            }

            // Convert to Xtream format
            $result = [];
            foreach ($streams as $stream) {
                $streamData = $stream->toXtreamFormat($proxyUrl, $client->username, $client->password);
                $result[] = $streamData;
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $result,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get VOD streams: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get series for client (JSON export)
     * GET /api/clients/{id}/export/series?category_id=X
     */
    public function exportSeries(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            $categoryId = isset($request->getQueryParams()['category_id']) ? (int) $request->getQueryParams()['category_id'] : null;

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $proxyUrl = $_ENV['APP_URL'] ?? $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

            // Get series from database
            if ($categoryId !== null && $categoryId < 100000) {
                // Regular category filter
                $seriesList = Series::getByCategory($client->source_id, $categoryId);
            } else {
                // All series
                $seriesList = Series::getBySource($client->source_id, true);
            }

            // Convert to Xtream format
            $result = [];
            foreach ($seriesList as $series) {
                $seriesData = $series->toXtreamFormat($proxyUrl, $client->username, $client->password);
                $result[] = $seriesData;
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $result,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get series: ' . $e->getMessage(), 500);
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
