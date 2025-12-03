<?php

declare(strict_types=1);

namespace App\Controllers\Xtream;

use App\Models\Client;
use App\Models\Source;
use App\Models\Filter;
use App\Models\Category;
use App\Models\LiveStream;
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

        // Build server info
        $serverInfo = [
            'url' => $proxyUrl,
            'port' => '80',
            'https_port' => '443',
            'server_protocol' => 'http',
            'rtmp_port' => '1935',
            'timestamp_now' => time(),
            'time_now' => date('Y-m-d H:i:s'),
        ];

        // Build user info
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

        // Apply filtering
        $filterService = new FilterService($filter, (bool) $client->hide_adult_content);
        $streams = $filterService->applyToStreams($streams, $categoryId);

        // Convert to Xtream format
        $result = [];
        foreach ($streams as $stream) {
            $streamData = $stream->toXtreamFormat($proxyUrl, $client->username, $client->password);
            $result[] = $streamData;
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
}
