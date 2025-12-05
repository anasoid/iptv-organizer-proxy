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
        $baseUrl = $this->authenticator->getBaseUrl();

        $attempt = 0;
        $lastException = null;

        while ($attempt < $this->maxRetries) {
            try {
                $logger->debug('Fetching categories', [
                    'action' => $action,
                    'attempt' => $attempt + 1,
                    'url' => $baseUrl,
                    'username' => $credentials['username'],
                ]);

                $response = $httpClient->get($baseUrl, [
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
                        'body_preview' => substr($body, 0, 200),
                    ]);
                    throw XtreamApiException::invalidResponse('Failed to parse category response');
                }

                if (!is_array($data)) {
                    $logger->warning('Empty or invalid category response', [
                        'action' => $action,
                        'response_type' => gettype($data),
                    ]);
                    return [];
                }

                $logger->info('Categories fetched successfully', [
                    'action' => $action,
                    'count' => count($data),
                    'http_code' => $response->getStatusCode(),
                ]);

                return $data;
            } catch (GuzzleException $e) {
                $attempt++;
                $lastException = $e;

                // Extract detailed error information
                $errorDetails = $this->extractErrorDetails($e, $action, $baseUrl, $attempt);

                $logger->warning('Network error fetching categories', $errorDetails);

                if ($attempt < $this->maxRetries) {
                    $delay = $this->calculateBackoff($attempt);
                    $logger->debug("Retrying after {$delay}ms", [
                        'action' => $action,
                        'attempt' => $attempt,
                        'next_attempt' => $attempt + 1,
                    ]);
                    usleep($delay * 1000);
                }
            }
        }

        $logger->error('Failed to fetch categories after retries', [
            'action' => $action,
            'attempts' => $this->maxRetries,
            'last_error' => $lastException ? $lastException->getMessage() : 'Unknown error',
            'last_error_class' => $lastException ? get_class($lastException) : 'Unknown',
        ]);

        throw XtreamApiException::networkError(
            "Failed to fetch {$action} after {$this->maxRetries} attempts",
            $lastException
        );
    }

    /**
     * Extract detailed error information from exception
     *
     * @param GuzzleException $e
     * @param string $action
     * @param string $url
     * @param int $attempt
     * @return array
     */
    private function extractErrorDetails(GuzzleException $e, string $action, string $url, int $attempt): array
    {
        $details = [
            'action' => $action,
            'attempt' => $attempt,
            'url' => $url,
            'error_message' => $e->getMessage(),
            'exception_class' => get_class($e),
        ];

        // Extract Guzzle request exception details
        if (method_exists($e, 'getRequest')) {
            try {
                $request = $e->getRequest();
                $details['http_method'] = $request->getMethod();
                $details['request_uri'] = (string) $request->getUri();
            } catch (\Exception $ex) {
                // Request not available
            }
        }

        // Extract response if available
        if (method_exists($e, 'getResponse')) {
            try {
                $response = $e->getResponse();
                if ($response) {
                    $details['http_code'] = $response->getStatusCode();
                    $details['http_reason'] = $response->getReasonPhrase();
                }
            } catch (\Exception $ex) {
                // Response not available
            }
        }

        // Extract connection details
        if (method_exists($e, 'getHandlerContext')) {
            try {
                $context = $e->getHandlerContext();
                if (is_array($context)) {
                    // Connection errors
                    if (isset($context['error'])) {
                        $details['connection_error'] = $context['error'];
                    }
                    // Total time
                    if (isset($context['total_time'])) {
                        $details['total_time_ms'] = (int) ($context['total_time'] * 1000);
                    }
                    // Connect time
                    if (isset($context['connect_time'])) {
                        $details['connect_time_ms'] = (int) ($context['connect_time'] * 1000);
                    }
                }
            } catch (\Exception $ex) {
                // Handler context not available
            }
        }

        // Parse cURL error codes if available in message
        if (preg_match('/cURL error (\d+)/', $e->getMessage(), $matches)) {
            $curlErrorCode = (int) $matches[1];
            $details['curl_error_code'] = $curlErrorCode;
            $details['curl_error_description'] = $this->getCurlErrorDescription($curlErrorCode);
        }

        // Add hostname for DNS issues
        if (preg_match('/Failed to connect|name resolution|Could not resolve/i', $e->getMessage())) {
            if (preg_match('#https?://([^/]+)#', $url, $hostMatch)) {
                $details['hostname'] = $hostMatch[1];
                $details['likely_cause'] = 'DNS resolution or network connectivity issue';
            }
        }

        return $details;
    }

    /**
     * Get human-readable cURL error description
     *
     * @param int $curlErrorCode
     * @return string
     */
    private function getCurlErrorDescription(int $curlErrorCode): string
    {
        $curlErrors = [
            1 => 'CURLE_UNSUPPORTED_PROTOCOL - Unsupported protocol',
            3 => 'CURLE_URL_MALFORMAT - URL malformed',
            6 => 'CURLE_COULDNT_RESOLVE_HOST - DNS resolution failed',
            7 => 'CURLE_COULDNT_CONNECT - Connection refused or timeout',
            28 => 'CURLE_OPERATION_TIMEDOUT - Connection timeout',
            35 => 'CURLE_SSL_CONNECT_ERROR - SSL/TLS connection error',
            52 => 'CURLE_GOT_NOTHING - Empty response from server',
            56 => 'CURLE_RECV_ERROR - Network read error',
        ];

        return $curlErrors[$curlErrorCode] ?? "Unknown cURL error {$curlErrorCode}";
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
