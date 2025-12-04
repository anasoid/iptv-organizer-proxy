<?php

declare(strict_types=1);

namespace App\Services\Xtream;

use App\Exceptions\XtreamApiException;
use GuzzleHttp\Exception\GuzzleException;

/**
 * Xtream Codes Category Client
 *
 * Handles fetching categories from Xtream Codes API
 */
class XtreamCategoryClient
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
     * Get live stream categories
     *
     * @return array
     * @throws XtreamApiException
     */
    public function getLiveCategories(): array
    {
        return $this->fetchCategories('get_live_categories');
    }

    /**
     * Get VOD categories
     *
     * @return array
     * @throws XtreamApiException
     */
    public function getVodCategories(): array
    {
        return $this->fetchCategories('get_vod_categories');
    }

    /**
     * Get series categories
     *
     * @return array
     * @throws XtreamApiException
     */
    public function getSeriesCategories(): array
    {
        return $this->fetchCategories('get_series_categories');
    }

    /**
     * Fetch categories from API
     *
     * @param string $action API action
     * @return array
     * @throws XtreamApiException
     */
    private function fetchCategories(string $action): array
    {
        $credentials = $this->authenticator->getCredentials();
        $httpClient = $this->authenticator->getHttpClient();
        $logger = $this->authenticator->getLogger();

        $attempt = 0;
        $lastException = null;

        while ($attempt < $this->maxRetries) {
            try {
                $response = $httpClient->get($this->authenticator->getBaseUrl(), [
                    'query' => [
                        'username' => $credentials['username'],
                        'password' => $credentials['password'],
                        'action' => $action,
                    ],
                ]);

                $body = (string) $response->getBody();
                $data = json_decode($body, true);

                if (json_last_error() !== JSON_ERROR_NONE) {
                    $logger->error('Invalid JSON response', [
                        'action' => $action,
                        'error' => json_last_error_msg(),
                    ]);
                    throw XtreamApiException::invalidResponse('Failed to parse category response');
                }

                if (!is_array($data)) {
                    $logger->warning('Empty or invalid category response', [
                        'action' => $action,
                    ]);
                    return [];
                }

                $logger->info('Categories fetched successfully', [
                    'action' => $action,
                    'count' => count($data),
                ]);

                return $data;
            } catch (GuzzleException $e) {
                $attempt++;
                $lastException = $e;

                $logger->warning('Network error fetching categories', [
                    'action' => $action,
                    'attempt' => $attempt,
                    'error' => $e->getMessage(),
                ]);

                if ($attempt < $this->maxRetries) {
                    $delay = $this->calculateBackoff($attempt);
                    usleep($delay * 1000);
                }
            }
        }

        $logger->error('Failed to fetch categories after retries', [
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
