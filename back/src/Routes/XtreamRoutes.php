<?php

declare(strict_types=1);

namespace App\Routes;

use Slim\App;
use App\Controllers\Xtream\XtreamController;
use App\Middleware\ClientAuthMiddleware;

/**
 * Xtream Codes API Routes
 *
 * Handles all /player_api.php endpoints for IPTV client access
 */
class XtreamRoutes
{
    public static function register(App $app): void
    {
        $xtreamController = new XtreamController();

        $app->map(['GET', 'POST'], '/player_api.php[/]', function ($request, $response) use ($xtreamController) {
            $queryParams = $request->getQueryParams();
            $action = $queryParams['action'] ?? null;

            switch ($action) {
                // Live Streams
                case 'get_live_categories':
                    return $xtreamController->getLiveCategories($request, $response);

                case 'get_live_streams':
                    return $xtreamController->getLiveStreams($request, $response);

                // VOD (Movies)
                case 'get_vod_categories':
                    return $xtreamController->getVodCategories($request, $response);

                case 'get_vod_streams':
                    return $xtreamController->getVodStreams($request, $response);

                case 'get_vod_info':
                    return $xtreamController->getVodInfo($request, $response);

                // Series
                case 'get_series_categories':
                    return $xtreamController->getSeriesCategories($request, $response);

                case 'get_series':
                    return $xtreamController->getSeries($request, $response);

                case 'get_series_info':
                    return $xtreamController->getSeriesInfo($request, $response);

                // EPG Data
                case 'get_short_epg':
                    return $xtreamController->getShortEpg($request, $response);

                case 'get_simple_data_table':
                    return $xtreamController->getSimpleDataTable($request, $response);

                default:
                    // No action = authenticate / server info
                    return $xtreamController->authenticate($request, $response);
            }
        })->add(new ClientAuthMiddleware());
    }
}
