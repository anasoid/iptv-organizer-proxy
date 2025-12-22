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
        $proxyUrl = $_ENV['APP_URL'] ?? (function() use ($request) {
            $uri = $request->getUri();
            $scheme = $uri->getScheme();
            $host = $uri->getHost();
            $port = $uri->getPort();

            // Include port if it's not a default port for the scheme
            if ($port && !(($scheme === 'http' && $port === 80) || ($scheme === 'https' && $port === 443))) {
                return $scheme . '://' . $host . ':' . $port;
            }
            return $scheme . '://' . $host;
        })();

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

            // Replace user info with client credentials (not source credentials)
            if (isset($authData['user_info'])) {
                $authData['user_info']['username'] = $client->getAttribute('username');
                $authData['user_info']['password'] = $client->getAttribute('password');
            }

            $data = $authData;
        } catch (\Exception $e) {
            // Log the exception
            error_log(sprintf(
                'Failed to fetch authentication from source %d for client %d: %s',
                $source->getAttribute('id'),
                $client->getAttribute('id'),
                $e->getMessage()
            ));

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
                'username' => $client->getAttribute('username'),
                'password' => $client->getAttribute('password'),
                'message' => '',
                'auth' => 1,
                'status' => $client->getAttribute('is_active') ? 'Active' : 'Inactive',
                'exp_date' => ($expDate = $client->getAttribute('expiry_date')) ? strtotime($expDate) : null,
                'is_trial' => '0',
                'active_cons' => '0',
                'created_at' => strtotime($client->getAttribute('created_at')),
                'max_connections' => (string) $client->getAttribute('max_connections'),
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

        // Set Content-Type header BEFORE writing to body
        $response = $response->withHeader('Content-Type', 'application/json');
        $body = $response->getBody();

        // Start array
        $body->write('[');

        $isFirst = true;

        // Use ContentFilterService to get allowed categories
        $filterService = new ContentFilterService($client);

        // Get allowed regular categories with pagination and stream them
        $pageSize = (int) ($_ENV['XTREAM_API_PAGINATION_LIMIT'] ?? 10000);
        $offset = 0;

        while (true) {
            $allowedCategories = $filterService->getAllowedCategories('live', $pageSize, $offset);

            if (empty($allowedCategories)) {
                break;
            }

            foreach ($allowedCategories as $category) {
                if (!$isFirst) {
                    $body->write(',');
                }
                // Include num field for proper ordering (categories returned already ordered by num from database)
                $categoryData = [
                    'num' => (int) ($category->getAttribute('num') ?? 0),
                    'category_id' => (string) $category->getAttribute('category_id'),
                    'category_name' => $category->getAttribute('category_name'),
                    'parent_id' => (int) ($category->getAttribute('parent_id') ?? 0),
                ];
                $body->write(json_encode($categoryData, JSON_UNESCAPED_SLASHES));
                $isFirst = false;
            }

            // Check if this was the last page before releasing memory
            $categoryCount = count($allowedCategories);

            // Memory optimization: Release objects from current batch before loading next batch
            unset($allowedCategories);

            // If we got fewer items than page size, we're done
            if ($categoryCount < $pageSize) {
                break;
            }

            $offset += $pageSize;
        }

        // Close array
        $body->write(']');

        return $response;
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

        // Stream response to avoid memory overhead
        return $this->streamStreamsResponse(
            $response,
            LiveStream::class,
            $source->getAttribute('id'),
            $client,
            $filter,
            $categoryId,
            'live'
        );
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

        // Set Content-Type header BEFORE writing to body
        $response = $response->withHeader('Content-Type', 'application/json');
        $body = $response->getBody();

        // Start array
        $body->write('[');

        $isFirst = true;

        // Use ContentFilterService to get allowed categories
        $filterService = new ContentFilterService($client);

        // Get allowed regular categories with pagination and stream them
        $pageSize = (int) ($_ENV['XTREAM_API_PAGINATION_LIMIT'] ?? 10000);
        $offset = 0;

        while (true) {
            $allowedCategories = $filterService->getAllowedCategories('vod', $pageSize, $offset);

            if (empty($allowedCategories)) {
                break;
            }

            foreach ($allowedCategories as $category) {
                if (!$isFirst) {
                    $body->write(',');
                }
                // Include num field for proper ordering (categories returned already ordered by num from database)
                $categoryData = [
                    'num' => (int) ($category->getAttribute('num') ?? 0),
                    'category_id' => (string) $category->getAttribute('category_id'),
                    'category_name' => $category->getAttribute('category_name'),
                    'parent_id' => (int) ($category->getAttribute('parent_id') ?? 0),
                ];
                $body->write(json_encode($categoryData, JSON_UNESCAPED_SLASHES));
                $isFirst = false;
            }

            // Check if this was the last page before releasing memory
            $categoryCount = count($allowedCategories);

            // Memory optimization: Release objects from current batch before loading next batch
            unset($allowedCategories);

            // If we got fewer items than page size, we're done
            if ($categoryCount < $pageSize) {
                break;
            }

            $offset += $pageSize;
        }

        // Close array
        $body->write(']');

        return $response;
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

        // Set Content-Type header BEFORE writing to body
        $response = $response->withHeader('Content-Type', 'application/json');
        $body = $response->getBody();

        // Start array
        $body->write('[');

        $isFirst = true;

        // Use ContentFilterService to get allowed categories
        $filterService = new ContentFilterService($client);

        // Get allowed regular categories with pagination and stream them
        $pageSize = (int) ($_ENV['XTREAM_API_PAGINATION_LIMIT'] ?? 10000);
        $offset = 0;

        while (true) {
            $allowedCategories = $filterService->getAllowedCategories('series', $pageSize, $offset);

            if (empty($allowedCategories)) {
                break;
            }

            foreach ($allowedCategories as $category) {
                if (!$isFirst) {
                    $body->write(',');
                }
                // Include num field for proper ordering (categories returned already ordered by num from database)
                $categoryData = [
                    'num' => (int) ($category->getAttribute('num') ?? 0),
                    'category_id' => (string) $category->getAttribute('category_id'),
                    'category_name' => $category->getAttribute('category_name'),
                    'parent_id' => (int) ($category->getAttribute('parent_id') ?? 0),
                ];
                $body->write(json_encode($categoryData, JSON_UNESCAPED_SLASHES));
                $isFirst = false;
            }

            // Check if this was the last page before releasing memory
            $categoryCount = count($allowedCategories);

            // Memory optimization: Release objects from current batch before loading next batch
            unset($allowedCategories);

            // If we got fewer items than page size, we're done
            if ($categoryCount < $pageSize) {
                break;
            }

            $offset += $pageSize;
        }

        // Close array
        $body->write(']');

        return $response;
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

        // Stream response to avoid memory overhead
        return $this->streamStreamsResponse(
            $response,
            VodStream::class,
            $source->getAttribute('id'),
            $client,
            $filter,
            $categoryId,
            'movie'
        );
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

        // Stream response to avoid memory overhead
        return $this->streamStreamsResponse(
            $response,
            Series::class,
            $source->getAttribute('id'),
            $client,
            $filter,
            $categoryId,
            'series'
        );
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
        $seriesId = isset($queryParams['series_id']) ? (int) $queryParams['series_id'] : null;

        if (!$seriesId) {
            return $this->jsonError($response, 'series_id parameter is required', 400);
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
        /** @var Source $source */
        $source = $request->getAttribute('source');

        $queryParams = $request->getQueryParams();
        $streamId = isset($queryParams['stream_id']) ? (int) $queryParams['stream_id'] : null;
        $limit = isset($queryParams['limit']) ? (int) $queryParams['limit'] : 10;

        if (!$streamId) {
            return $this->jsonError($response, 'stream_id parameter is required', 400);
        }

        try {
            // Proxy to original source
            $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
            $epg = $xtreamClient->getEpgClient()->getShortEpg($streamId, $limit);

            $response->getBody()->write(json_encode($epg));
            return $response->withHeader('Content-Type', 'application/json');
        } catch (\Exception $e) {
            return $this->jsonError($response, 'Failed to fetch EPG: ' . $e->getMessage(), 500);
        }
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
        /** @var Source $source */
        $source = $request->getAttribute('source');

        $queryParams = $request->getQueryParams();
        $streamId = isset($queryParams['stream_id']) ? (int) $queryParams['stream_id'] : null;

        if (!$streamId) {
            return $this->jsonError($response, 'stream_id parameter is required', 400);
        }

        try {
            // Proxy to original source
            $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
            $table = $xtreamClient->getEpgClient()->getSimpleDataTable($streamId);

            $response->getBody()->write(json_encode($table));
            return $response->withHeader('Content-Type', 'application/json');
        } catch (\Exception $e) {
            return $this->jsonError($response, 'Failed to fetch EPG data: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get XMLTV EPG data
     *
     * Endpoint: /player_api.php?action=get_xmltv
     * Optional: stream_id
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getXmltv(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        /** @var Source $source */
        $source = $request->getAttribute('source');

        $queryParams = $request->getQueryParams();
        $streamId = isset($queryParams['stream_id']) ? (int) $queryParams['stream_id'] : null;

        try {
            // Proxy to original source (streamed to avoid memory overhead for large XMLTV files)
            $xtreamClient = new \App\Services\Xtream\XtreamClient($source);
            $xmltvResponse = $xtreamClient->getEpgClient()->getXmltv($streamId);

            // Pipe source response stream directly to client response
            $sourceBody = $xmltvResponse->getBody();
            $response = $response
                ->withBody($sourceBody)
                ->withHeader('Content-Type', 'application/xml');

            return $response;
        } catch (\Exception $e) {
            return $this->jsonError($response, 'Failed to fetch XMLTV: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Stream formatted streams response for Xtream API
     *
     * Common logic for getLiveStreams, getVodStreams, and getSeries
     * Streams response to avoid memory overhead
     *
     * @param ResponseInterface $response
     * @param string $modelClass Stream model class (LiveStream, VodStream, or Series)
     * @param int $sourceId Source ID
     * @param Client $client Authenticated client
     * @param Filter|null $filter Optional client filter
     * @param int|null $categoryId Optional category filter
     * @param string $defaultStreamType Default stream_type if not in data
     * @return ResponseInterface
     */
    private function streamStreamsResponse(
        ResponseInterface $response,
        string $modelClass,
        int $sourceId,
        Client $client,
        ?Filter $filter,
        ?int $categoryId,
        string $defaultStreamType
    ): ResponseInterface {
        // Set Content-Type header BEFORE writing to body
        $response = $response->withHeader('Content-Type', 'application/json');
        $body = $response->getBody();

        // Start array
        $body->write('[');

        // Set up pagination
        $pageSize = (int) ($_ENV['XTREAM_API_PAGINATION_LIMIT'] ?? 10000);
        $offset = 0;

        // Apply filtering service for use in loop
        $filterService = new FilterService($filter, (bool) $client->getAttribute('hide_adult_content'));

        $isFirst = true;

        // Load streams with pagination and stream them (maintains num ordering from database)
        while (true) {
            // Query streams from database with pagination (already ordered by num ASC)
            if ($categoryId !== null && $categoryId < 100000) {
                // Regular category filter
                $streams = $modelClass::getByCategory($sourceId, $categoryId, $pageSize, $offset);
            } else {
                // All streams
                $streams = $modelClass::getBySource($sourceId, $pageSize, $offset);
            }

            if (empty($streams)) {
                break;
            }

            // Apply filtering to this batch (FilterService returns arrays, maintains num ordering)
            $filteredArrays = $filterService->applyToStreams($streams, $categoryId);

            // Memory optimization: Build minimal map for current batch only (for data field extraction)
            $streamDataMap = [];
            foreach ($streams as $stream) {
                $streamDataMap[$stream->getAttribute('stream_id')] = [
                    'data' => $stream->getAttribute('data') ?? '{}',
                    'source_id' => $stream->getAttribute('source_id'),
                ];
            }

            // Convert filtered result arrays to complete Xtream format and stream
            // Streams maintain their 'num' ordering from database through FilterService
            foreach ($filteredArrays as $streamArray) {
                if (!$isFirst) {
                    $body->write(',');
                }

                $streamId = (int) ($streamArray['stream_id'] ?? 0);
                $streamData = $streamDataMap[$streamId] ?? null;

                // Parse the JSON data field once per stream
                $dataJson = [];
                if ($streamData && !empty($streamData['data'])) {
                    $dataJson = json_decode($streamData['data'], true) ?? [];
                }

                // Pre-decode category_ids if needed (may be JSON string from database)
                $categoryIds = [];
                if (!empty($streamArray['category_ids'])) {
                    $categoryIds = is_array($streamArray['category_ids'])
                        ? $streamArray['category_ids']
                        : (json_decode($streamArray['category_ids'], true) ?? []);
                }

                // Build complete Xtream response with all fields
                // Use 'num' from stream array to maintain database ordering
                $xtreamData = [
                    'num' => (int) $streamArray['num'],
                    'source_id' => $streamData['source_id'] ?? null,
                    'stream_id' => $streamId,
                    'name' => $streamArray['name'] ?? '',
                    'category_id' => (int) ($streamArray['category_id'] ?? 0),
                    'category_ids' => $categoryIds,
                    'is_adult' => (int) ($streamArray['is_adult'] ?? 0),
                    'stream_type' => $defaultStreamType,
                ];

                // Add all JSON data fields to response (allow custom field override if present)
                foreach ($dataJson as $key => $value) {
                    // Use custom field value if present in filtered data, otherwise use JSON value
                    $xtreamData[$key] = $streamArray[$key] ?? $value;
                }

                // Write this stream to response
                $body->write(json_encode($xtreamData, JSON_UNESCAPED_SLASHES));
                $isFirst = false;
            }

            // Check if this was the last page before releasing memory
            // (compare database query result, not filtered result, to handle cases where filtering removes items)
            $isLastPage = count($streams) < $pageSize;

            // Memory optimization: Release objects from current batch before loading next batch
            unset($streams, $filteredArrays, $streamDataMap);

            // If the database returned fewer items than page size, we've reached the end
            if ($isLastPage) {
                break;
            }

            $offset += $pageSize;
        }

        // Close array
        $body->write(']');

        return $response;
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
