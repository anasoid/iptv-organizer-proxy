<?php

declare(strict_types=1);

namespace App\Controllers\Xtream;

use App\Models\Client;
use App\Models\Source;
use App\Models\Filter;
use App\Models\Category;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;
use App\Services\FilterService;
use App\Services\ContentFilterService;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use Slim\Psr7\Response;

/**
 * Xtream Codes API Controller
 *
 * Handles Xtream API endpoints for IPTV clients
 */
class XtreamController
{
    /**
     * Authenticate and return server/user info
     *
     * Endpoint: /player_api.php (no action parameter)
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function authenticate(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        // Get proxy URL from environment or request
        $proxyUrl = $_ENV['APP_URL'] ??
                    $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

        try {
            // Fetch authentication data from original source
            $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
            $authData = $xtreamClient->authenticate();

            // Replace server URL with proxy URL (domain only, no protocol/port)
            if (isset($authData['server_info'])) {
                // Parse the proxy URL to extract domain and scheme
                $parsedUrl = parse_url($proxyUrl);
                $scheme = $parsedUrl['scheme'] ?? 'http';
                $host = $parsedUrl['host'] ?? 'localhost';
                $port = $parsedUrl['port'] ?? ($scheme === 'https' ? 443 : 80);
                
                // Set domain (without protocol and port)
                $authData['server_info']['url'] = $host;
                
                // Set protocol
                $authData['server_info']['server_protocol'] = $scheme;
                
                // Set ports based on protocol
                if ($scheme === 'https') {
                    // HTTPS: use current port in https_port, port is 80
                    $authData['server_info']['https_port'] = (string) $port;
                    $authData['server_info']['port'] = '80';
                } else {
                    // HTTP: use current port in port, https_port is 443
                    $authData['server_info']['port'] = (string) $port;
                    $authData['server_info']['https_port'] = '443';
                }
                
                // Update timestamp to current time
                $authData['server_info']['timestamp_now'] = time();
                $authData['server_info']['time_now'] = date('Y-m-d H:i:s');
            }

            $data = $authData;
        } catch (\Exception $e) {
            // Fallback to basic info if source fetch fails
            $parsedUrl = parse_url($proxyUrl);
            $scheme = $parsedUrl['scheme'] ?? 'http';
            $host = $parsedUrl['host'] ?? 'localhost';
            $port = $parsedUrl['port'] ?? ($scheme === 'https' ? 443 : 80);
            
            if ($scheme === 'https') {
                // HTTPS: use current port in https_port, port is 80
                $httpPort = '80';
                $httpsPort = (string) $port;
            } else {
                // HTTP: use current port in port, https_port is 443
                $httpPort = (string) $port;
                $httpsPort = '443';
            }
            
            $serverInfo = [
                'url' => $host,
                'port' => $httpPort,
                'https_port' => $httpsPort,
                'server_protocol' => $scheme,
                'rtmp_port' => '1935',
                'timestamp_now' => time(),
                'time_now' => date('Y-m-d H:i:s'),
            ];

            $userInfo = [
                'username' => $client->username,
                'password' => $client->password,
                'message' => '',
                'auth' => 1,
                'status' => $client->is_active ? 'Active' : 'Inactive',
                'exp_date' => $client->expiry_date ? strtotime($client->expiry_date) : null,
                'is_trial' => '0',
                'active_cons' => '0',
                'created_at' => strtotime($client->created_at),
                'max_connections' => (string) $client->max_connections,
                'allowed_output_formats' => ['m3u8', 'ts', 'rtmp'],
            ];

            $data = [
                'user_info' => $userInfo,
                'server_info' => $serverInfo,
            ];
        }

        $response->getBody()->write(json_encode($data));

        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get live categories
     *
     * Endpoint: /player_api.php?action=get_live_categories
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getLiveCategories(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        /** @var Filter|null $filter */
        $filter = $request->getAttribute('filter');

        $categories = [];

        // Use ContentFilterService to get allowed categories
        $filterService = new ContentFilterService($client);

        // Generate favoris virtual categories first (if filter assigned)
        if ($filter !== null) {
            $filterServiceBase = new FilterService($filter, (bool) $client->hide_adult_content);
            $favorisCategories = $filterServiceBase->generateFavorisCategories();
            $categories = array_merge($categories, $favorisCategories);
        }

        // Get allowed regular categories (filtered by rules)
        $allowedCategories = $filterService->getAllowedCategories('live');

        foreach ($allowedCategories as $category) {
            $categories[] = [
                'category_id' => (string) $category->category_id,
                'category_name' => $category->category_name,
                'parent_id' => (int) ($category->parent_id ?? 0),
            ];
        }

        $response->getBody()->write(json_encode($categories));

        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get live streams
     *
     * Endpoint: /player_api.php?action=get_live_streams
     * Optional: &category_id=X
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getLiveStreams(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        /** @var Filter|null $filter */
        $filter = $request->getAttribute('filter');

        $queryParams = $request->getQueryParams();
        $categoryId = isset($queryParams['category_id']) ? (int) $queryParams['category_id'] : null;

        // Get proxy URL from environment or request
        $proxyUrl = $_ENV['APP_URL'] ??
                    $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

        // Query streams from database
        if ($categoryId !== null && $categoryId < 100000) {
            // Regular category filter
            $streams = LiveStream::getByCategory($source->id, $categoryId);
        } else {
            // All streams or favoris category
            $streams = LiveStream::getBySource($source->id, true);
        }

        // Apply filtering (FilterService needs objects to lookup category info, returns arrays)
        $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
        $filteredArrays = $filterService->applyToStreams($streams, $categoryId);

        // Build a map of stream_id to original LiveStream object for data extraction
        $streamMap = [];
        foreach ($streams as $stream) {
            $streamMap[$stream->stream_id] = $stream;
        }

        // Convert filtered result arrays to complete Xtream format
        $result = [];
        $num = 1;
        foreach ($filteredArrays as $streamArray) {
            $streamId = (int) ($streamArray['stream_id'] ?? 0);
            $originalStream = $streamMap[$streamId] ?? null;
            
            // Parse the JSON data field from database
            $dataJson = [];
            if ($originalStream && !empty($originalStream->data)) {
                $dataJson = json_decode($originalStream->data, true) ?? [];
            }
            
            // Build complete Xtream response with all fields
            $xtreamData = [
                'num' => $dataJson['num'] ?? $num,
                'name' => $streamArray['name'] ?? '',
                'stream_type' => $dataJson['stream_type'] ?? 'live',
                'stream_id' => $streamId,
                'stream_icon' => $dataJson['stream_icon'] ?? '',
                'epg_channel_id' => $dataJson['epg_channel_id'] ?? '',
                'added' => $dataJson['added'] ?? null,
                'is_adult' => (int) ($streamArray['is_adult'] ?? 0),
                'category_id' => (int) ($streamArray['category_id'] ?? 0),
                'category_ids' => !empty($streamArray['category_ids']) 
                    ? json_decode($streamArray['category_ids'], true) 
                    : [],
                'custom_sid' => $dataJson['custom_sid'] ?? null,
                'tv_archive' => (int) ($dataJson['tv_archive'] ?? 0),
                'direct_source' => $dataJson['direct_source'] ?? '',
                'tv_archive_duration' => (int) ($dataJson['tv_archive_duration'] ?? 0),
            ];
            $result[] = $xtreamData;
            $num++;
        }

        $response->getBody()->write(json_encode($result));

        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get VOD categories
     *
     * Endpoint: /player_api.php?action=get_vod_categories
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getVodCategories(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        /** @var Filter|null $filter */
        $filter = $request->getAttribute('filter');

        $categories = [];

        // Use ContentFilterService to get allowed categories
        $filterService = new ContentFilterService($client);

        // Generate favoris virtual categories first (if filter assigned)
        if ($filter !== null) {
            $filterServiceBase = new FilterService($filter, (bool) $client->hide_adult_content);
            $favorisCategories = $filterServiceBase->generateFavorisCategories();
            $categories = array_merge($categories, $favorisCategories);
        }

        // Get allowed regular categories (filtered by rules)
        $allowedCategories = $filterService->getAllowedCategories('vod');

        foreach ($allowedCategories as $category) {
            $categories[] = [
                'category_id' => (string) $category->category_id,
                'category_name' => $category->category_name,
                'parent_id' => (int) ($category->parent_id ?? 0),
            ];
        }

        $response->getBody()->write(json_encode($categories));

        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get series categories
     *
     * Endpoint: /player_api.php?action=get_series_categories
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getSeriesCategories(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        /** @var Filter|null $filter */
        $filter = $request->getAttribute('filter');

        $categories = [];

        // Use ContentFilterService to get allowed categories
        $filterService = new ContentFilterService($client);

        // Generate favoris virtual categories first (if filter assigned)
        if ($filter !== null) {
            $filterServiceBase = new FilterService($filter, (bool) $client->hide_adult_content);
            $favorisCategories = $filterServiceBase->generateFavorisCategories();
            $categories = array_merge($categories, $favorisCategories);
        }

        // Get allowed regular categories (filtered by rules)
        $allowedCategories = $filterService->getAllowedCategories('series');

        foreach ($allowedCategories as $category) {
            $categories[] = [
                'category_id' => (string) $category->category_id,
                'category_name' => $category->category_name,
                'parent_id' => (int) ($category->parent_id ?? 0),
            ];
        }

        $response->getBody()->write(json_encode($categories));

        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get VOD streams
     *
     * Endpoint: /player_api.php?action=get_vod_streams
     * Optional: &category_id=X
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getVodStreams(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        /** @var Filter|null $filter */
        $filter = $request->getAttribute('filter');

        $queryParams = $request->getQueryParams();
        $categoryId = isset($queryParams['category_id']) ? (int) $queryParams['category_id'] : null;

        // Get proxy URL from environment or request
        $proxyUrl = $_ENV['APP_URL'] ??
                    $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

        // Query streams from database
        if ($categoryId !== null && $categoryId < 100000) {
            // Regular category filter
            $streams = VodStream::getByCategory($source->id, $categoryId);
        } else {
            // All streams or favoris category
            $streams = VodStream::getBySource($source->id, true);
        }

        // Apply filtering (FilterService needs objects to lookup category info, returns arrays)
        $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
        $filteredArrays = $filterService->applyToStreams($streams, $categoryId);

        // Build a map of stream_id to original VodStream object for data extraction
        $streamMap = [];
        foreach ($streams as $stream) {
            $streamMap[$stream->stream_id] = $stream;
        }

        // Convert filtered result arrays to complete Xtream format
        $result = [];
        $num = 1;
        foreach ($filteredArrays as $streamArray) {
            $streamId = (int) ($streamArray['stream_id'] ?? 0);
            $originalStream = $streamMap[$streamId] ?? null;
            
            // Parse the JSON data field from database
            $dataJson = [];
            if ($originalStream && !empty($originalStream->data)) {
                $dataJson = json_decode($originalStream->data, true) ?? [];
            }
            
            // Build complete Xtream response with all fields
            $xtreamData = [
                'num' => $dataJson['num'] ?? $num,
                'name' => $streamArray['name'] ?? '',
                'stream_type' => $dataJson['stream_type'] ?? 'movie',
                'stream_id' => $streamId,
                'stream_icon' => $dataJson['stream_icon'] ?? '',
                'epg_channel_id' => $dataJson['epg_channel_id'] ?? '',
                'added' => $dataJson['added'] ?? null,
                'is_adult' => (int) ($streamArray['is_adult'] ?? 0),
                'category_id' => (int) ($streamArray['category_id'] ?? 0),
                'category_ids' => !empty($streamArray['category_ids']) 
                    ? json_decode($streamArray['category_ids'], true) 
                    : [],
                'custom_sid' => $dataJson['custom_sid'] ?? null,
                'tv_archive' => (int) ($dataJson['tv_archive'] ?? 0),
                'direct_source' => $dataJson['direct_source'] ?? '',
                'tv_archive_duration' => (int) ($dataJson['tv_archive_duration'] ?? 0),
            ];
            $result[] = $xtreamData;
            $num++;
        }

        $response->getBody()->write(json_encode($result));

        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get series streams
     *
     * Endpoint: /player_api.php?action=get_series
     * Optional: &category_id=X
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getSeries(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        /** @var Filter|null $filter */
        $filter = $request->getAttribute('filter');

        $queryParams = $request->getQueryParams();
        $categoryId = isset($queryParams['category_id']) ? (int) $queryParams['category_id'] : null;

        // Get proxy URL from environment or request
        $proxyUrl = $_ENV['APP_URL'] ??
                    $request->getUri()->getScheme() . '://' . $request->getUri()->getHost();

        // Query series from database
        if ($categoryId !== null && $categoryId < 100000) {
            // Regular category filter
            $series = Series::getByCategory($source->id, $categoryId);
        } else {
            // All series or favoris category
            $series = Series::getBySource($source->id, true);
        }

        // Apply filtering (FilterService needs objects to lookup category info, returns arrays)
        $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
        $filteredArrays = $filterService->applyToStreams($series, $categoryId);

        // Build a map of stream_id to original Series object for data extraction
        $streamMap = [];
        foreach ($series as $item) {
            $streamMap[$item->stream_id] = $item;
        }

        // Convert filtered result arrays to complete Xtream format
        $result = [];
        $num = 1;
        foreach ($filteredArrays as $itemArray) {
            $streamId = (int) ($itemArray['stream_id'] ?? 0);
            $originalSeries = $streamMap[$streamId] ?? null;
            
            // Parse the JSON data field from database
            $dataJson = [];
            if ($originalSeries && !empty($originalSeries->data)) {
                $dataJson = json_decode($originalSeries->data, true) ?? [];
            }
            
            // Build complete Xtream response with all fields
            $xtreamData = [
                'num' => $dataJson['num'] ?? $num,
                'name' => $itemArray['name'] ?? '',
                'stream_type' => $dataJson['stream_type'] ?? 'series',
                'stream_id' => $streamId,
                'stream_icon' => $dataJson['stream_icon'] ?? '',
                'epg_channel_id' => $dataJson['epg_channel_id'] ?? '',
                'added' => $dataJson['added'] ?? null,
                'is_adult' => (int) ($itemArray['is_adult'] ?? 0),
                'category_id' => (int) ($itemArray['category_id'] ?? 0),
                'category_ids' => !empty($itemArray['category_ids']) 
                    ? json_decode($itemArray['category_ids'], true) 
                    : [],
                'custom_sid' => $dataJson['custom_sid'] ?? null,
                'tv_archive' => (int) ($dataJson['tv_archive'] ?? 0),
                'direct_source' => $dataJson['direct_source'] ?? '',
                'tv_archive_duration' => (int) ($dataJson['tv_archive_duration'] ?? 0),
            ];
            $result[] = $xtreamData;
            $num++;
        }

        $response->getBody()->write(json_encode($result));

        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get VOD stream information
     *
     * Endpoint: /player_api.php?action=get_vod_info
     * Required: vod_id
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getVodInfo(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        $queryParams = $request->getQueryParams();
        $vodId = isset($queryParams['vod_id']) ? (int) $queryParams['vod_id'] : null;

        if (!$vodId) {
            return $this->jsonError($response, 'vod_id parameter is required', 400);
        }

        try {
            // Proxy to original source
            $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
            $info = $xtreamClient->getStreamClient()->getVodInfo($vodId);

            $response->getBody()->write(json_encode($info));
            return $response->withHeader('Content-Type', 'application/json');
        } catch (\Exception $e) {
            return $this->jsonError($response, 'Failed to fetch VOD info: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get series information
     *
     * Endpoint: /player_api.php?action=get_series_info
     * Required: series_id
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getSeriesInfo(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Client $client */
        $client = $request->getAttribute('client');

        /** @var Source $source */
        $source = $request->getAttribute('source');

        $queryParams = $request->getQueryParams();
        $seriesId = isset($queryParams['series']) ? (int) $queryParams['series'] : null;

        if (!$seriesId) {
            return $this->jsonError($response, 'series parameter is required', 400);
        }

        try {
            // Proxy to original source
            $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
            $info = $xtreamClient->getSeriesInfo($seriesId);

            $response->getBody()->write(json_encode($info));
            return $response->withHeader('Content-Type', 'application/json');
        } catch (\Exception $e) {
            return $this->jsonError($response, 'Failed to fetch Series info: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get short EPG (upcoming programs)
     *
     * Endpoint: /player_api.php?action=get_short_epg
     * Optional: stream_id, limit
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getShortEpg(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $queryParams = $request->getQueryParams();
        $streamId = isset($queryParams['stream_id']) ? (int) $queryParams['stream_id'] : null;
        $limit = isset($queryParams['limit']) ? (int) $queryParams['limit'] : 10;

        // EPG data structure (simplified - actual implementation would fetch from database)
        $epg = [
            'epg' => [
                [
                    'id' => '1',
                    'title' => 'Program 1',
                    'start' => date('Y-m-d H:i:s', time()),
                    'end' => date('Y-m-d H:i:s', time() + 3600),
                    'description' => 'Program description',
                ],
                [
                    'id' => '2',
                    'title' => 'Program 2',
                    'start' => date('Y-m-d H:i:s', time() + 3600),
                    'end' => date('Y-m-d H:i:s', time() + 7200),
                    'description' => 'Program description',
                ],
            ],
        ];

        $response->getBody()->write(json_encode($epg));
        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Get simple data table (full EPG for a stream)
     *
     * Endpoint: /player_api.php?action=get_simple_data_table
     * Required: stream_id
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getSimpleDataTable(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        $queryParams = $request->getQueryParams();
        $streamId = isset($queryParams['stream_id']) ? (int) $queryParams['stream_id'] : null;

        if (!$streamId) {
            return $this->jsonError($response, 'stream_id parameter is required', 400);
        }

        // EPG data table (simplified - actual implementation would fetch from database)
        $table = [
            'rows' => [
                [
                    'id' => '1',
                    'title' => 'Program 1',
                    'start' => date('Y-m-d H:i:s', time()),
                    'end' => date('Y-m-d H:i:s', time() + 3600),
                    'description' => 'Program description',
                ],
                [
                    'id' => '2',
                    'title' => 'Program 2',
                    'start' => date('Y-m-d H:i:s', time() + 3600),
                    'end' => date('Y-m-d H:i:s', time() + 7200),
                    'description' => 'Program description',
                ],
            ],
        ];

        $response->getBody()->write(json_encode($table));
        return $response->withHeader('Content-Type', 'application/json');
    }

    /**
     * Return JSON error response
     *
     * @param ResponseInterface $response
     * @param string $message
     * @param int $statusCode
     * @return ResponseInterface
     */
    private function jsonError(ResponseInterface $response, string $message, int $statusCode = 400): ResponseInterface
    {
        $response->getBody()->write(json_encode([
            'error' => $message,
        ]));

        return $response
            ->withStatus($statusCode)
            ->withHeader('Content-Type', 'application/json');
    }
}
