<?php

declare(strict_types=1);

namespace App\Services;

use Monolog\Logger;
use Monolog\Handler\StreamHandler;

/**
 * Proxy Configuration Service
 *
 * Centralized service for loading, parsing, and validating proxy configuration.
 * Supports both unified PROXY_URL and component-based configuration.
 * Handles HTTP/HTTPS and SOCKS5 proxy types with optional authentication.
 */
class ProxyConfigService
{
    private static ?ProxyConfigService $instance = null;
    private array $config = [];
    private bool $enabled = false;
    private Logger $logger;

    private function __construct()
    {
        $this->logger = new Logger('ProxyConfigService');
        $this->logger->pushHandler(new StreamHandler('php://stderr', Logger::WARNING));
        $this->loadConfiguration();
    }

    public static function getInstance(): ProxyConfigService
    {
        if (self::$instance === null) {
            self::$instance = new self();
        }
        return self::$instance;
    }

    /**
     * Load proxy configuration from environment variables
     */
    private function loadConfiguration(): void
    {
        $this->enabled = filter_var(
            $_ENV['PROXY_ENABLED'] ?? false,
            FILTER_VALIDATE_BOOLEAN
        );

        if (!$this->enabled) {
            $this->config = [];
            return;
        }

        try {
            // Try unified PROXY_URL first
            if (!empty($_ENV['PROXY_URL'])) {
                $this->config = $this->parseProxyUrl($_ENV['PROXY_URL']);
            } else {
                // Use component-based configuration
                $this->config = $this->parseComponentConfig();
            }

            // Validate configuration
            $errors = $this->validate();
            if (!empty($errors)) {
                $this->logger->error('Proxy configuration validation failed', [
                    'errors' => $errors,
                ]);
                $this->enabled = false;
                return;
            }

            $this->logger->info('Proxy configuration loaded successfully', [
                'type' => $this->config['type'],
                'host' => $this->config['host'],
                'port' => $this->config['port'],
                'has_auth' => !empty($this->config['username']),
            ]);
        } catch (\Throwable $e) {
            $this->logger->error('Failed to load proxy configuration', [
                'error' => $e->getMessage(),
            ]);
            $this->enabled = false;
        }
    }

    /**
     * Parse proxy URL (e.g., http://user:pass@proxy:8080)
     *
     * @param string $url Proxy URL
     * @return array Parsed proxy configuration
     * @throws \InvalidArgumentException
     */
    private function parseProxyUrl(string $url): array
    {
        $parsed = parse_url($url);

        if ($parsed === false || !isset($parsed['host'])) {
            throw new \InvalidArgumentException('Invalid proxy URL format');
        }

        $scheme = $parsed['scheme'] ?? 'http';
        $host = $parsed['host'];
        $port = $parsed['port'] ?? $this->getDefaultPort($scheme);
        $username = isset($parsed['user']) ? urldecode($parsed['user']) : null;
        $password = isset($parsed['pass']) ? urldecode($parsed['pass']) : null;

        return [
            'type' => $scheme,
            'host' => $host,
            'port' => $port,
            'username' => $username,
            'password' => $password,
            'url' => $url,
        ];
    }

    /**
     * Parse component-based proxy configuration
     *
     * @return array Proxy configuration
     * @throws \InvalidArgumentException
     */
    private function parseComponentConfig(): array
    {
        $type = $_ENV['PROXY_TYPE'] ?? 'http';
        $host = $_ENV['PROXY_HOST'] ?? '';
        $port = (int)($_ENV['PROXY_PORT'] ?? $this->getDefaultPort($type));
        $username = $_ENV['PROXY_USERNAME'] ?? null;
        $password = $_ENV['PROXY_PASSWORD'] ?? null;

        if (empty($host)) {
            throw new \InvalidArgumentException('Proxy host is required when proxy is enabled');
        }

        // Build URL for unified access
        $url = $type . '://';
        if ($username && $password) {
            $url .= urlencode($username) . ':' . urlencode($password) . '@';
        }
        $url .= $host . ':' . $port;

        return [
            'type' => $type,
            'host' => $host,
            'port' => $port,
            'username' => $username,
            'password' => $password,
            'url' => $url,
        ];
    }

    /**
     * Get default port for proxy type
     *
     * @param string $type Proxy type (http, https, socks5)
     * @return int Default port
     */
    private function getDefaultPort(string $type): int
    {
        return match ($type) {
            'http' => 8080,
            'https' => 8443,
            'socks5' => 1080,
            default => 8080,
        };
    }

    /**
     * Validate proxy configuration
     *
     * @return array List of validation errors (empty if valid)
     */
    public function validate(): array
    {
        $errors = [];

        if (empty($this->config)) {
            return $errors;
        }

        if (empty($this->config['host'])) {
            $errors[] = 'Proxy host is required';
        }

        if (!in_array($this->config['type'], ['http', 'https', 'socks5'])) {
            $errors[] = 'Invalid proxy type. Must be http, https, or socks5';
        }

        if ($this->config['port'] < 1 || $this->config['port'] > 65535) {
            $errors[] = 'Invalid proxy port. Must be between 1 and 65535';
        }

        // Validate that username and password are both set or both empty
        $hasUsername = !empty($this->config['username']);
        $hasPassword = !empty($this->config['password']);

        if ($hasUsername !== $hasPassword) {
            $errors[] = 'Both username and password must be provided for proxy authentication';
        }

        return $errors;
    }

    /**
     * Check if proxy is enabled
     *
     * @return bool
     */
    public function isEnabled(): bool
    {
        return $this->enabled;
    }

    /**
     * Get Guzzle-compatible proxy configuration
     *
     * @return array Configuration array for Guzzle client
     */
    public function getGuzzleConfig(): array
    {
        if (!$this->enabled || empty($this->config)) {
            return [];
        }

        // Guzzle accepts proxy as a string URL
        return ['proxy' => $this->config['url']];
    }

    /**
     * Get cURL-compatible proxy options
     *
     * @return array cURL options array
     */
    public function getCurlOptions(): array
    {
        if (!$this->enabled || empty($this->config)) {
            return [];
        }

        $options = [
            CURLOPT_PROXY => $this->config['host'] . ':' . $this->config['port'],
            CURLOPT_PROXYTYPE => $this->getCurlProxyType($this->config['type']),
        ];

        // Add authentication if present
        if (!empty($this->config['username']) && !empty($this->config['password'])) {
            $options[CURLOPT_PROXYUSERPWD] = $this->config['username'] . ':' . $this->config['password'];
        }

        return $options;
    }

    /**
     * Get cURL proxy type constant
     *
     * @param string $type Proxy type (http, https, socks5)
     * @return int cURL proxy type constant
     */
    private function getCurlProxyType(string $type): int
    {
        return match ($type) {
            'http', 'https' => CURLPROXY_HTTP,
            'socks5' => CURLPROXY_SOCKS5,
            default => CURLPROXY_HTTP,
        };
    }

    /**
     * Get proxy type
     *
     * @return string
     */
    public function getProxyType(): string
    {
        return $this->config['type'] ?? 'http';
    }

    /**
     * Get proxy host
     *
     * @return string
     */
    public function getHost(): string
    {
        return $this->config['host'] ?? '';
    }

    /**
     * Get proxy port
     *
     * @return int
     */
    public function getPort(): int
    {
        return $this->config['port'] ?? 0;
    }

    /**
     * Check if proxy requires authentication
     *
     * @return bool
     */
    public function requiresAuthentication(): bool
    {
        return !empty($this->config['username']) && !empty($this->config['password']);
    }

    /**
     * Get full proxy URL
     *
     * @return string|null
     */
    public function getProxyUrl(): ?string
    {
        return $this->config['url'] ?? null;
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
