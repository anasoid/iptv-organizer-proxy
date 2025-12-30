<?php

declare(strict_types=1);

namespace App\Services\Xtream;

use App\Exceptions\XtreamApiException;
use App\Services\HttpClient;
use GuzzleHttp\Exception\GuzzleException;
use Monolog\Logger;
use Monolog\Handler\StreamHandler;

/**
 * Xtream Codes API Authenticator
 *
 * Handles authentication, credential management, and rate limiting
 */
class XtreamAuthenticator
{
    private HttpClient $httpClient;
    private string $baseUrl;
    private string $username;
    private string $password;
    private bool $authenticated = false;
    private ?array $serverInfo = null;
    private ?array $userInfo = null;
    private Logger $logger;

    /**
     * Constructor
     *
     * @param string $url Source server URL
     * @param string $username Xtream username
     * @param string $password Xtream password
     * @param HttpClient|null $httpClient Optional HTTP client (for testing)
     */
    public function __construct(
        string $url,
        string $username,
        string $password,
        ?HttpClient $httpClient = null
    ) {
        $this->baseUrl = rtrim($url, '/') . '/player_api.php';
        $this->username = $username;
        $this->password = $password;

        $this->httpClient = $httpClient ?? HttpClient::getInstance();

        $this->logger = new Logger('XtreamAuthenticator');
        $this->logger->pushHandler(new StreamHandler('php://stderr', Logger::WARNING));
    }

    /**
     * Authenticate with Xtream Codes server
     *
     * @return array Server and user info
     * @throws XtreamApiException
     */
    public function authenticate(): array
    {
        try {
            $response = $this->httpClient->getGuzzleClient()->get($this->baseUrl, [
                'query' => [
                    'username' => $this->username,
                    'password' => $this->password,
                ],
            ]);

            $body = (string) $response->getBody();
            $data = json_decode($body, true);

            if (json_last_error() !== JSON_ERROR_NONE) {
                $this->logger->error('Invalid JSON response during authentication', [
                    'error' => json_last_error_msg(),
                ]);
                throw XtreamApiException::invalidResponse('Failed to parse authentication response');
            }

            if (!isset($data['user_info']) || !isset($data['server_info'])) {
                $this->logger->error('Authentication response missing required fields', [
                    'response' => $data,
                ]);
                throw XtreamApiException::authenticationFailed('Invalid authentication response structure');
            }

            $this->serverInfo = $data['server_info'];
            $this->userInfo = $data['user_info'];
            $this->authenticated = true;

            $this->logger->info('Authentication successful', [
                'username' => $this->username,
            ]);

            return [
                'server_info' => $this->serverInfo,
                'user_info' => $this->userInfo,
            ];
        } catch (GuzzleException $e) {
            $this->logger->error('Network error during authentication', [
                'error' => $e->getMessage(),
            ]);
            throw XtreamApiException::networkError($e->getMessage(), $e);
        }
    }

    /**
     * Get base URL
     *
     * @return string
     */
    public function getBaseUrl(): string
    {
        return $this->baseUrl;
    }

    /**
     * Get credentials
     *
     * @return array
     */
    public function getCredentials(): array
    {
        return [
            'username' => $this->username,
            'password' => $this->password,
        ];
    }

    /**
     * Check if authenticated
     *
     * @return bool
     */
    public function isAuthenticated(): bool
    {
        return $this->authenticated;
    }

    /**
     * Get HTTP client instance
     *
     * @return HttpClient
     */
    public function getHttpClient(): HttpClient
    {
        return $this->httpClient;
    }

    /**
     * Get server info from authentication
     *
     * @return array|null
     */
    public function getServerInfo(): ?array
    {
        return $this->serverInfo;
    }

    /**
     * Get user info from authentication
     *
     * @return array|null
     */
    public function getUserInfo(): ?array
    {
        return $this->userInfo;
    }


    /**
     * Get logger instance
     *
     * @return Logger
     */
    public function getLogger(): Logger
    {
        return $this->logger;
    }
}
