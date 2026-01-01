<?php

declare(strict_types=1);

namespace App\Services;

use Psr\Http\Message\ServerRequestInterface;

/**
 * RequestLogger provides methods to log input request details
 */
class RequestLogger
{
    /**
     * Log input request details
     *
     * Logs comprehensive request information including URL, method, headers, and query parameters
     *
     * @param ServerRequestInterface $request The incoming request
     * @param string $controller The controller name for logging context
     * @return void
     */
    public static function logInputRequest(ServerRequestInterface $request, string $controller): void
    {
        // Log request URL/target
        $requestUrl = $request->getRequestTarget();
        error_log("{$controller}: Request URL - {$requestUrl}");

        // Log HTTP method
        $method = $request->getMethod();
        error_log("{$controller}: Request method - {$method}");

        // Log query parameters if present
        $queryParams = $request->getQueryParams();
        if (!empty($queryParams)) {
            $queryStr = self::formatArray($queryParams);
            error_log("{$controller}: Query parameters - {$queryStr}");
        }

        // Log request headers
        $headers = $request->getHeaders();
        if (!empty($headers)) {
            $headerLines = [];
            foreach ($headers as $name => $values) {
                $headerLines[] = "{$name}: " . implode(",", $values);
            }
            error_log("{$controller}: Request headers - " . implode(" | ", $headerLines));
        }

        // Log request body if present and not streaming
        $body = $request->getBody();
        if ($body->getSize() > 0 && $body->getSize() < 5000) {
            $body->rewind();
            $content = $body->getContents();
            $body->rewind();
            if (!empty($content)) {
                error_log("{$controller}: Request body - {$content}");
            }
        }
    }

    /**
     * Log request headers only
     *
     * @param ServerRequestInterface $request The incoming request
     * @param string $controller The controller name for logging context
     * @return void
     */
    public static function logRequestHeaders(ServerRequestInterface $request, string $controller): void
    {
        $headerLines = [];
        foreach ($request->getHeaders() as $name => $values) {
            $headerLines[] = "{$name}: " . implode(",", $values);
        }

        if (!empty($headerLines)) {
            error_log("{$controller}: Request headers - " . implode(" | ", $headerLines));
        }
    }

    /**
     * Log request URL and method
     *
     * @param ServerRequestInterface $request The incoming request
     * @param string $controller The controller name for logging context
     * @return void
     */
    public static function logRequestLine(ServerRequestInterface $request, string $controller): void
    {
        $method = $request->getMethod();
        $url = $request->getRequestTarget();
        error_log("{$controller}: {$method} {$url}");
    }

    /**
     * Format array for logging
     *
     * @param array $array The array to format
     * @return string Formatted array string
     */
    private static function formatArray(array $array): string
    {
        $parts = [];
        foreach ($array as $key => $value) {
            if (is_array($value)) {
                $parts[] = "{$key}: [" . self::formatArray($value) . "]";
            } else {
                $parts[] = "{$key}: {$value}";
            }
        }
        return implode(", ", $parts);
    }
}
