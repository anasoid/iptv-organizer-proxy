<?php

declare(strict_types=1);

namespace App\Services\Xtream;

use App\Exceptions\XtreamApiException;
use GuzzleHttp\Exception\GuzzleException;

/**
 * Xtream Codes EPG Client
 *
 * Handles fetching EPG (Electronic Program Guide) data from Xtream Codes API
 */
class XtreamEpgClient
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
     * Get simple data table for a stream
     *
     * @param int $streamId Stream ID
     * @return array Raw EPG data
     * @throws XtreamApiException
     */
    public function getSimpleDataTable(int $streamId): array
    {
        return $this->fetchEpgData([
            'action' => 'get_simple_data_table',
            'stream_id' => $streamId,
        ]);
    }

    /**
     * Get short EPG for a stream
     *
     * @param int $streamId Stream ID
     * @param int $limit Number of entries to retrieve
     * @return array Raw EPG data
     * @throws XtreamApiException
     */
    public function getShortEpg(int $streamId, int $limit = 100): array
    {
        return $this->fetchEpgData([
            'action' => 'get_short_epg',
            'stream_id' => $streamId,
            'limit' => $limit,
        ]);
    }

    /**
     * Get XMLTV data
     *
     * @param int|null $streamId Optional stream ID filter
     * @return string Raw XMLTV XML data
     * @throws XtreamApiException
     */
    public function getXmltv(?int $streamId = null): string
    {
        $credentials = $this->authenticator->getCredentials();
        $httpClient = $this->authenticator->getHttpClient();
        $logger = $this->authenticator->getLogger();

        $baseUrl = $this->authenticator->getBaseUrl();
        $xmltvUrl = str_replace('/player_api.php', '/xmltv.php', $baseUrl);

        $params = [
            'username' => $credentials['username'],
            'password' => $credentials['password'],
        ];

        if ($streamId !== null) {
            $params['stream_id'] = $streamId;
        }

        $attempt = 0;
        $lastException = null;

        while ($attempt < $this->maxRetries) {
            try {
                $response = $httpClient->get($xmltvUrl, [
                    'query' => $params,
                ]);

                $body = (string) $response->getBody();

                if (empty($body)) {
                    $logger->warning('Empty XMLTV response', [
                        'stream_id' => $streamId,
                    ]);
                    return '';
                }

                $logger->info('XMLTV data fetched successfully', [
                    'stream_id' => $streamId,
                    'size' => strlen($body),
                ]);

                return $body;
            } catch (GuzzleException $e) {
                $attempt++;
                $lastException = $e;

                $logger->warning('Network error fetching XMLTV', [
                    'stream_id' => $streamId,
                    'attempt' => $attempt,
                    'error' => $e->getMessage(),
                ]);

                if ($attempt < $this->maxRetries) {
                    $delay = $this->calculateBackoff($attempt);
                    usleep($delay * 1000);
                }
            }
        }

        $logger->error('Failed to fetch XMLTV after retries', [
            'stream_id' => $streamId,
            'attempts' => $this->maxRetries,
        ]);

        throw XtreamApiException::networkError(
            "Failed to fetch XMLTV after {$this->maxRetries} attempts",
            $lastException
        );
    }

    /**
     * Fetch EPG data from API
     *
     * @param array $params Query parameters
     * @return array Raw EPG data
     * @throws XtreamApiException
     */
    private function fetchEpgData(array $params): array
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
                    throw XtreamApiException::invalidResponse('Failed to parse EPG response');
                }

                if (!is_array($data)) {
                    $logger->warning('Empty or invalid EPG response', [
                        'action' => $params['action'] ?? 'unknown',
                    ]);
                    return [];
                }

                $logger->info('EPG data fetched successfully', [
                    'action' => $params['action'] ?? 'unknown',
                    'stream_id' => $params['stream_id'] ?? null,
                ]);

                return $data;
            } catch (GuzzleException $e) {
                $attempt++;
                $lastException = $e;

                $logger->warning('Network error fetching EPG', [
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
        $logger->error('Failed to fetch EPG data after retries', [
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
