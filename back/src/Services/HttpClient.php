<?php

declare(strict_types=1);

namespace App\Services;

use GuzzleHttp\Client;
use GuzzleHttp\Exception\GuzzleException;
use Psr\Http\Message\ResponseInterface;
use Monolog\Logger;
use Monolog\Handler\StreamHandler;

/**
 * Centralized HTTP Client
 *
 * Unified HTTP client wrapper that handles both Guzzle and cURL requests.
 * Automatically applies proxy configuration to all HTTP calls.
 * Provides consistent logging, error handling, and request management.
 */
class HttpClient
{
    private static ?HttpClient $instance = null;
    private Client $guzzleClient;
    private ProxyConfigService $proxyConfig;
    private Logger $logger;
    private array $defaultCurlOptions;

    private function __construct()
    {
        $this->logger = new Logger('HttpClient');
        $this->logger->pushHandler(new StreamHandler('php://stderr', Logger::WARNING));
        $this->proxyConfig = ProxyConfigService::getInstance();
        $this->guzzleClient = $this->createGuzzleClient();

        // Initialize default curl options with proper constant keys
        $this->defaultCurlOptions = [
            CURLOPT_SSL_VERIFYPEER => false,
            CURLOPT_SSL_VERIFYHOST => false,
            CURLOPT_TIMEOUT => 300,
            CURLOPT_CONNECTTIMEOUT => 10,
        ];
    }

    /**
     * Get singleton instance
     *
     * @return HttpClient
     */
    public static function getInstance(): HttpClient
    {
        if (self::$instance === null) {
            self::$instance = new self();
        }
        return self::$instance;
    }

    /**
     * Create Guzzle client with proxy configuration
     *
     * @return Client
     */
    private function createGuzzleClient(): Client
    {
        $config = [
            'timeout' => 180,
            'connect_timeout' => 30,
        ];

        // Add proxy configuration if enabled
        if ($this->proxyConfig->isEnabled()) {
            $proxyConfig = $this->proxyConfig->getGuzzleConfig();
            $config = array_merge($config, $proxyConfig);
            $this->logger->info('Guzzle client configured with proxy', [
                'proxy_type' => $this->proxyConfig->getProxyType(),
                'proxy_host' => $this->proxyConfig->getHost(),
            ]);
        }

        return new Client($config);
    }

    // =====================================================================
    // JSON API Methods (Guzzle-based) - For JSON APIs
    // =====================================================================

    /**
     * Perform GET request for JSON APIs
     *
     * @param string $url Request URL
     * @param array $options Request options
     * @return ResponseInterface
     * @throws GuzzleException
     */
    public function getJson(string $url, array $options = []): ResponseInterface
    {
        return $this->requestJson('GET', $url, $options);
    }

    /**
     * Perform POST request for JSON APIs
     *
     * @param string $url Request URL
     * @param array $options Request options
     * @return ResponseInterface
     * @throws GuzzleException
     */
    public function postJson(string $url, array $options = []): ResponseInterface
    {
        return $this->requestJson('POST', $url, $options);
    }

    /**
     * Perform generic HTTP request for JSON APIs (Guzzle-based)
     *
     * @param string $method HTTP method
     * @param string $url Request URL
     * @param array $options Request options
     * @return ResponseInterface
     * @throws GuzzleException
     */
    public function requestJson(string $method, string $url, array $options = []): ResponseInterface
    {
        try {
            return $this->guzzleClient->request($method, $url, $options);
        } catch (GuzzleException $e) {
            $this->logger->error('JSON API request failed', [
                'method' => $method,
                'url' => $url,
                'error' => $e->getMessage(),
                'proxy_enabled' => $this->proxyConfig->isEnabled(),
            ]);
            throw $e;
        }
    }

    /**
     * Backwards compatibility: alias for requestJson()
     *
     * @deprecated Use requestJson() instead
     * @param string $method HTTP method
     * @param string $url Request URL
     * @param array $options Request options
     * @return ResponseInterface
     * @throws GuzzleException
     */
    public function request(string $method, string $url, array $options = []): ResponseInterface
    {
        return $this->requestJson($method, $url, $options);
    }

    /**
     * Backwards compatibility: alias for getJson()
     *
     * @deprecated Use getJson() instead
     * @param string $url Request URL
     * @param array $options Request options
     * @return ResponseInterface
     * @throws GuzzleException
     */
    public function get(string $url, array $options = []): ResponseInterface
    {
        return $this->getJson($url, $options);
    }

    /**
     * Backwards compatibility: alias for postJson()
     *
     * @deprecated Use postJson() instead
     * @param string $url Request URL
     * @param array $options Request options
     * @return ResponseInterface
     * @throws GuzzleException
     */
    public function post(string $url, array $options = []): ResponseInterface
    {
        return $this->postJson($url, $options);
    }

    /**
     * Get underlying Guzzle client (for direct access if needed)
     *
     * @return Client
     */
    public function getGuzzleClient(): Client
    {
        return $this->guzzleClient;
    }

    // =====================================================================
    // Binary/Stream Methods (cURL-based) - For Streaming & Binary Data
    // =====================================================================

    /**
     * Perform cURL request
     *
     * @param string $url Request URL
     * @param array $curlOptions cURL options (merged with defaults)
     * @param bool $followLocation Whether to follow HTTP redirects (default: false)
     * @param ?bool $sourceEnableProxy Source-level proxy setting (null = not specified)
     * @return array Result containing status, headers, body, and errno
     */
    public function curlRequest(string $url, array $curlOptions = [], bool $followLocation = false, ?bool $sourceEnableProxy = null): array
    {
        return $this->executeCurl($url, $curlOptions, $followLocation, $sourceEnableProxy);
    }

    /**
     * Perform cURL stream request (for binary data)
     *
     * @param string $url Request URL
     * @param array $curlOptions cURL options (merged with defaults)
     * @param bool $followLocation Whether to follow HTTP redirects (default: false)
     * @param ?bool $sourceEnableProxy Source-level proxy setting (null = not specified)
     * @return array Result containing status, headers, body, and errno
     */
    public function streamRequest(string $url, array $curlOptions = [], bool $followLocation = false, ?bool $sourceEnableProxy = null): array
    {
        // Ensure stream options are set
        $curlOptions[CURLOPT_RETURNTRANSFER] = true;
        $curlOptions[CURLOPT_HEADER] = true;

        return $this->executeCurl($url, $curlOptions, $followLocation, $sourceEnableProxy);
    }

    /**
     * Stream response directly without buffering (true streaming for large files)
     *
     * @param string $url Request URL
     * @param callable|null $onData Callback for each data chunk: function(string $chunk): void
     * @param callable|null $onHeader Callback for each header: function(string $headerLine): bool
     * @param array $curlOptions Additional cURL options
     * @param bool $followLocation Whether to follow HTTP redirects
     * @param ?bool $sourceEnableProxy Source-level proxy setting (null = not specified)
     * @return array Result with keys: errno, error, http_code
     */
    public function streamDirectToClient(
        string $url,
        ?callable $onData = null,
        ?callable $onHeader = null,
        array $curlOptions = [],
        bool $followLocation = false,
        ?bool $sourceEnableProxy = null
    ): array {
        // Track client connection status
        $clientConnected = true;

        // Stop execution if client disconnects (don't keep running)
        ignore_user_abort(false);

        // Register shutdown function to detect client disconnect
        register_shutdown_function(function() use (&$clientConnected) {
            if (connection_aborted()) {
                $clientConnected = false;
            }
        });

        $ch = curl_init();

        // Merge with default cURL options (user options take precedence)
        // Use + operator instead of array_merge() to preserve numeric (constant) keys
        $curlOptions = $curlOptions + $this->defaultCurlOptions;

        // Enable FOLLOWLOCATION if requested
        // cURL can follow redirects even with RETURNTRANSFER=false when using callbacks
        $curlOptions[CURLOPT_FOLLOWLOCATION] = $followLocation;
        if ($followLocation) {
            $curlOptions[CURLOPT_MAXREDIRS] = 10;
        }

        // Configure for streaming without buffering
        $curlOptions[CURLOPT_URL] = $url;
        $curlOptions[CURLOPT_RETURNTRANSFER] = false;  // Don't buffer response

        // Add low-speed timeout to detect stalled connections quickly
        // If less than 1 byte is transferred in 5 seconds, abort
        $curlOptions[CURLOPT_LOW_SPEED_LIMIT] = 1;
        $curlOptions[CURLOPT_LOW_SPEED_TIME] = 5;

        // Add proxy configuration if enabled (with source-level override)
        if ($this->proxyConfig->shouldUseProxy($sourceEnableProxy)) {
            $proxyOptions = $this->proxyConfig->getCurlOptions($sourceEnableProxy);
            // Use + operator instead of array_merge() to preserve numeric (constant) keys
            $curlOptions = $curlOptions + $proxyOptions;
            $this->logger->info('cURL streaming configured with proxy', [
                'proxy_type' => $this->proxyConfig->getProxyType(),
                'proxy_host' => $this->proxyConfig->getHost(),
                'source_enable_proxy' => $sourceEnableProxy,
            ]);
        }

        // Set all regular options using individual curl_setopt calls
        // (curl_setopt_array sometimes has issues with certain options in PHP 8.3)
        foreach ($curlOptions as $option => $value) {
            try {
                // Validate option is an integer
                if (!is_int($option)) {
                    error_log("Invalid curl option key (not int): " . var_export($option, true));
                    continue;
                }

                curl_setopt($ch, $option, $value);
            } catch (\Throwable $e) {
                // Log and throw the error so we can see what's invalid
                error_log("curl_setopt failed for option $option: " . $e->getMessage());
                throw $e;
            }
        }

        // Set callbacks separately

        // Header callback - called for each header line as it arrives
        curl_setopt($ch, CURLOPT_HEADERFUNCTION, function($curl, $headerLine) use ($onHeader, &$clientConnected) {
            // Check if client connection is still active
            if (connection_aborted() || !$clientConnected) {
                error_log("HttpClient: Client disconnected during header processing, aborting upstream download");
                return 0;  // Return 0 to abort transfer
            }

            $trimmed = trim($headerLine);

            // Skip empty lines
            if (empty($trimmed)) {
                return strlen($headerLine);
            }

            // Call user callback if provided
            if ($onHeader) {
                try {
                    $onHeader($trimmed);
                } catch (\Throwable $e) {
                    $this->logger->error('Error in header callback', [
                        'error' => $e->getMessage(),
                    ]);
                }
            }

            return strlen($headerLine);  // Must return bytes read
        });

        // Data callback - called for each chunk of response body as it arrives
        curl_setopt($ch, CURLOPT_WRITEFUNCTION, function($curl, $chunk) use ($onData, &$clientConnected) {
            // Check if client connection is still active
            // If client disconnected, return 0 to abort cURL transfer immediately
            if (connection_aborted() || !$clientConnected) {
                error_log("HttpClient: Client disconnected during streaming, aborting upstream download");
                return 0;  // Signal cURL to stop downloading
            }

            $chunkSize = strlen($chunk);

            // Send chunk directly to client immediately (no buffering)
            echo $chunk;

            // Call user callback if provided
            if ($onData) {
                try {
                    $onData($chunk);
                } catch (\Throwable $e) {
                    $this->logger->error('Error in data callback', [
                        'error' => $e->getMessage(),
                        'chunk_size' => $chunkSize,
                    ]);
                }
            }

            // Flush output to ensure data is sent immediately
            if (function_exists('flush')) {
                flush();
            }

            return $chunkSize;  // Must return bytes processed
        });

        // Execute request - data is streamed directly to client via callbacks
        $result = curl_exec($ch);
        $errno = curl_errno($ch);
        $error = curl_error($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);

        curl_close($ch);

        return [
            'errno' => $errno,
            'error' => $error,
            'http_code' => $httpCode,
        ];
    }

    /**
     * Execute cURL request with automatic proxy configuration
     *
     * @param string $url Request URL
     * @param array $curlOptions cURL options (merged with defaults)
     * @param bool $followLocation Whether to follow HTTP redirects
     * @param ?bool $sourceEnableProxy Source-level proxy setting (null = not specified)
     * @return array Result with keys: status, headers, body, errno, error
     */
    private function executeCurl(string $url, array $curlOptions = [], bool $followLocation = false, ?bool $sourceEnableProxy = null): array
    {
        $ch = curl_init();

        // Merge with default cURL options (user options take precedence)
        // Use + operator instead of array_merge() to preserve numeric (constant) keys
        $curlOptions = $curlOptions + $this->defaultCurlOptions;

        // Set follow location based on parameter
        $curlOptions[CURLOPT_FOLLOWLOCATION] = $followLocation;

        // Set URL
        $curlOptions[CURLOPT_URL] = $url;

        // Add proxy configuration if enabled (with source-level override)
        if ($this->proxyConfig->shouldUseProxy($sourceEnableProxy)) {
            $proxyOptions = $this->proxyConfig->getCurlOptions($sourceEnableProxy);
            // Use + operator instead of array_merge() to preserve numeric (constant) keys
            $curlOptions = $curlOptions + $proxyOptions;
            $this->logger->info('cURL request configured with proxy', [
                'proxy_type' => $this->proxyConfig->getProxyType(),
                'proxy_host' => $this->proxyConfig->getHost(),
                'source_enable_proxy' => $sourceEnableProxy,
            ]);
        }

        // Set curl options individually (curl_setopt_array has issues in PHP 8.3)
        foreach ($curlOptions as $option => $value) {
            try {
                // Validate option is an integer
                if (!is_int($option)) {
                    error_log("Invalid curl option key (not int): " . var_export($option, true));
                    continue;
                }

                curl_setopt($ch, $option, $value);
            } catch (\Throwable $e) {
                error_log("curl_setopt failed for option $option: " . $e->getMessage());
                throw $e;
            }
        }

        $response_data = curl_exec($ch);
        $errno = curl_errno($ch);
        $error = curl_error($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);

        $result = [
            'status' => $httpCode,
            'errno' => $errno,
            'error' => $error,
            'headers' => '',
            'body' => '',
        ];

        // Handle errors
        if ($errno !== 0) {
            $this->logger->error('cURL request failed', [
                'url' => $url,
                'errno' => $errno,
                'error' => $error,
                'proxy_enabled' => $this->proxyConfig->isEnabled(),
            ]);
            curl_close($ch);
            return $result;
        }

        if ($response_data === false) {
            $this->logger->error('cURL request returned false', [
                'url' => $url,
                'http_code' => $httpCode,
            ]);
            curl_close($ch);
            return $result;
        }

        // Parse headers if CURLOPT_HEADER was set
        if (!empty($curlOptions[CURLOPT_HEADER])) {
            $header_size = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
            $result['headers'] = substr($response_data, 0, $header_size);
            $result['body'] = substr($response_data, $header_size);
        } else {
            $result['body'] = $response_data;
        }

        curl_close($ch);

        return $result;
    }

    /**
     * Check if proxy is enabled
     *
     * @return bool
     */
    public function isProxyEnabled(): bool
    {
        return $this->proxyConfig->isEnabled();
    }

    /**
     * Get proxy information
     *
     * @return array|null Proxy info or null if disabled
     */
    public function getProxyInfo(): ?array
    {
        if (!$this->proxyConfig->isEnabled()) {
            return null;
        }

        return [
            'type' => $this->proxyConfig->getProxyType(),
            'host' => $this->proxyConfig->getHost(),
            'port' => $this->proxyConfig->getPort(),
            'requires_auth' => $this->proxyConfig->requiresAuthentication(),
        ];
    }

    /**
     * Get default cURL options
     *
     * @return array
     */
    public function getDefaultCurlOptions(): array
    {
        return $this->defaultCurlOptions;
    }

    /**
     * Set default cURL options (useful for testing or customization)
     *
     * @param array $options Default cURL options
     * @return void
     */
    public function setDefaultCurlOptions(array $options): void
    {
        $this->defaultCurlOptions = $options;
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
