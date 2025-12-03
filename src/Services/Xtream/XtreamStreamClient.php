<?php

declare(strict_types=1);

namespace App\Services\Xtream;

use App\Exceptions\XtreamApiException;
use GuzzleHttp\Exception\GuzzleException;

/**
 * Xtream Codes Stream Client
 *
 * Handles fetching streams and series from Xtream Codes API
 */
class XtreamStreamClient
{
    private XtreamAuthenticator $authenticator;
    private int $maxRetries = 3;

    /**
     * Constructor
     *
     * @param XtreamAuthenticator $authenticator
     */
    public function __construct(XtreamAuthenticator $authenticator)
    {
        $this->authenticator = $authenticator;
    }

    /**
     * Get live streams
     *
     * @param int|null $categoryId Optional category filter
     * @return array
     * @throws XtreamApiException
     */
    public function getLiveStreams(?int $categoryId = null): array
    {
        $params = ['action' => 'get_live_streams'];
        if ($categoryId !== null) {
            $params['category_id'] = $categoryId;
        }

        return $this->fetchStreams($params);
    }

    /**
     * Get VOD streams
     *
     * @param int|null $categoryId Optional category filter
     * @return array
     * @throws XtreamApiException
     */
    public function getVodStreams(?int $categoryId = null): array
    {
        $params = ['action' => 'get_vod_streams'];
        if ($categoryId !== null) {
            $params['category_id'] = $categoryId;
        }

        return $this->fetchStreams($params);
    }

    /**
     * Get series
     *
     * @param int|null $categoryId Optional category filter
     * @return array
     * @throws XtreamApiException
     */
    public function getSeries(?int $categoryId = null): array
    {
        $params = ['action' => 'get_series'];
        if ($categoryId !== null) {
            $params['category_id'] = $categoryId;
        }

        return $this->fetchStreams($params);
    }

    /**
     * Get series info
     *
     * @param int $seriesId Series ID
     * @return array
     * @throws XtreamApiException
     */
    public function getSeriesInfo(int $seriesId): array
    {
        $params = [
            'action' => 'get_series_info',
            'series_id' => $seriesId,
        ];

        return $this->fetchStreams($params);
    }

    /**
     * Fetch streams from API
     *
     * @param array $params Query parameters
     * @return array
     * @throws XtreamApiException
     */
    private function fetchStreams(array $params): array
    {
        $credentials = $this->authenticator->getCredentials();
        $httpClient = $this->authenticator->getHttpClient();
        $logger = $this->authenticator->getLogger();

        $params['username'] = $credentials['username'];
        $params['password'] = $credentials['password'];

        $attempt = 0;
        $lastException = null;

        while ($attempt < $this->maxRetries) {
            try {
                $response = $httpClient->get($this->authenticator->getBaseUrl(), [
                    'query' => $params,
                ]);

                $body = (string) $response->getBody();
                $data = json_decode($body, true);

                if (json_last_error() !== JSON_ERROR_NONE) {
                    $logger->error('Invalid JSON response', [
                        'action' => $params['action'] ?? 'unknown',
                        'error' => json_last_error_msg(),
                    ]);
                    throw XtreamApiException::invalidResponse('Failed to parse stream response');
                }

                if (!is_array($data)) {
                    $logger->warning('Empty or invalid stream response', [
                        'action' => $params['action'] ?? 'unknown',
                    ]);
                    return [];
                }

                $logger->info('Streams fetched successfully', [
                    'action' => $params['action'] ?? 'unknown',
                    'count' => count($data),
                ]);

                return $data;
            } catch (GuzzleException $e) {
                $attempt++;
                $lastException = $e;

                $logger->warning('Network error fetching streams', [
                    'action' => $params['action'] ?? 'unknown',
                    'attempt' => $attempt,
                    'error' => $e->getMessage(),
                ]);

                if ($attempt < $this->maxRetries) {
                    $delay = $this->calculateBackoff($attempt);
                    usleep($delay * 1000);
                }
            }
        }

        $action = $params['action'] ?? 'unknown';
        $logger->error('Failed to fetch streams after retries', [
            'action' => $action,
            'attempts' => $this->maxRetries,
        ]);

        throw XtreamApiException::networkError(
            "Failed to fetch {$action} after {$this->maxRetries} attempts",
            $lastException
        );
    }

    /**
     * Calculate exponential backoff delay in milliseconds
     *
     * @param int $attempt Attempt number (1-based)
     * @return int Delay in milliseconds
     */
    private function calculateBackoff(int $attempt): int
    {
        return (int) (100 * pow(2, $attempt - 1));
    }
}
