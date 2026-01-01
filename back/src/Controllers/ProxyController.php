<?php

declare(strict_types=1);

namespace App\Controllers;

use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use App\Models\Client;
use App\Services\HttpClient;

/**
 * ProxyController handles encoded stream proxy requests with redirect following
 *
 * Endpoint: /proxy/{username}/{password}?url={base64_encoded_url}
 *
 * This controller is used when a stream request encounters a 302 redirect.
 * The initial stream request returns a 302 redirect to this endpoint with the
 * upstream redirect URL encoded as base64. This controller then:
 * 1. Authenticates the client
 * 2. Decodes the URL
 * 3. Follows any redirects and streams the final data
 */
class ProxyController
{
    /**
     * Handle proxy request with base64 encoded URL
     */
    public function handleProxyRequest(
        ServerRequestInterface $request,
        ResponseInterface $response,
        string $username,
        string $password
    ): ResponseInterface {
        try {
            // Log proxy request
            error_log("========== PROXY REQUEST ==========");
            error_log("ProxyController: Client request URL - " . $request->getRequestTarget());
            error_log("ProxyController: Proxy request received");

            // Log client request headers
            $clientHeaders = [];
            foreach ($request->getHeaders() as $name => $values) {
                $clientHeaders[] = "{$name}: " . implode(",", $values);
            }
            error_log("ProxyController: Client request headers - " . implode(" | ", $clientHeaders));

            // Get the base64 encoded URL from query parameters
            $queryParams = $request->getQueryParams();
            $encodedUrl = $queryParams['url'] ?? null;

            if (!$encodedUrl) {
                error_log("ProxyController: Missing url parameter");
                error_log("ProxyController: Final status code - 400");
                $response->getBody()->write('Missing url parameter');
                return $response->withStatus(400);
            }

            // Authenticate client using ORIGINAL credentials
            $clients = Client::findAll(['username' => $username, 'password' => $password]);
            if (empty($clients)) {
                error_log("ProxyController: Invalid credentials");
                error_log("ProxyController: Final status code - 401");
                $response->getBody()->write('Invalid credentials');
                return $response->withStatus(401);
            }

            $client = $clients[0];
            error_log("ProxyController: Client authenticated successfully");

            // Decode the base64 encoded URL
            $decodedUrl = base64_decode($encodedUrl, true);
            if ($decodedUrl === false || empty($decodedUrl)) {
                error_log("ProxyController: Invalid encoded URL");
                error_log("ProxyController: Final status code - 400");
                $response->getBody()->write('Invalid encoded URL');
                return $response->withStatus(400);
            }

            // Validate that decoded URL is a valid string
            if (!is_string($decodedUrl)) {
                error_log("ProxyController: Invalid URL format");
                error_log("ProxyController: Final status code - 400");
                $response->getBody()->write('Invalid URL format');
                return $response->withStatus(400);
            }

            error_log("ProxyController: Decoded redirect URL - {$decodedUrl}");

            // Stream the data from the decoded URL with redirects enabled
            return $this->streamWithRedirects($request, $response, $decodedUrl);

        } catch (\Exception $e) {
            error_log("ProxyController: Exception - " . $e->getMessage());
            error_log("ProxyController: Final status code - 500");
            $response->getBody()->write('Error: ' . htmlspecialchars($e->getMessage()));
            return $response->withStatus(500);
        }
    }

    /**
     * Stream data from URL with redirect following enabled
     *
     * This method always follows redirects (302, etc.) internally and streams
     * the final data to the client without the client seeing the intermediate redirects.
     */
    private function streamWithRedirects(
        ServerRequestInterface $request,
        ResponseInterface $response,
        string $url
    ): ResponseInterface {
        try {
            // Log streaming start with redirects enabled
            error_log("========== PROXY STREAMING ==========");
            error_log("ProxyController: Starting stream with redirect following - {$url}");

            $httpClient = HttpClient::getInstance();
            $httpCode = 200;
            $responseHeaders = [];

            // Extract and format request headers for forwarding
            $requestHeaders = [];
            $curlHeaders = [];
            foreach ($request->getHeaders() as $name => $values) {
                $headerValue = implode(",", $values);
                $requestHeaders[] = "{$name}: {$headerValue}";

                // Determine if this header should be forwarded to upstream
                $skipHeaders = [
                    'host',                  // Will be set by upstream server
                    'connection',            // Proxy needs to manage this
                    'content-length',        // cURL will set this
                    'transfer-encoding',     // cURL will handle
                    'upgrade',               // Streaming proxy doesn't support upgrades
                    'expect',                // Not needed for stream
                    'proxy-connection',      // Not applicable
                    'proxy-authenticate',    // Not applicable
                    'te',                    // Transfer encoding
                ];

                if (!in_array(strtolower($name), $skipHeaders)) {
                    $curlHeaders[] = "{$name}: {$headerValue}";
                }
            }

            // Log request headers that will be forwarded upstream
            $upstreamHeadersStr = !empty($curlHeaders) ? implode(" | ", $curlHeaders) : "No headers";
            error_log("ProxyController: Request headers to forward - {$upstreamHeadersStr}");

            // Disable output buffering and compression for streaming
            if (function_exists('ini_set')) {
                ini_set('output_buffering', '0');
                ini_set('zlib.output_compression', '0');
            }

            // Clear any existing output buffers
            while (ob_get_level()) {
                ob_end_clean();
            }

            // Stream directly from upstream to client, ALWAYS following redirects
            $result = $httpClient->streamDirectToClient(
                $url,
                // Callback for data chunks
                onData: function(string $chunk) {
                    // Data is already sent to client via echo in streamDirectToClient
                },
                // Callback for headers
                onHeader: function(string $headerLine) use (&$response, &$httpCode, &$responseHeaders) {
                    try {
                        // Parse and forward headers
                        if (stripos($headerLine, 'HTTP/') === 0) {
                            // Extract HTTP status code
                            if (preg_match('/HTTP\/\d\.\d\s+(\d+)/', $headerLine, $matches)) {
                                $httpCode = (int) $matches[1];
                                $responseHeaders[] = $headerLine;

                                // Send HTTP status code immediately (before any data)
                                if (!headers_sent()) {
                                    http_response_code($httpCode);
                                }
                            }
                        } else if (strpos($headerLine, ':') !== false) {
                            // Forward response headers to client
                            [$name, $value] = explode(':', $headerLine, 2);
                            $name = trim($name);
                            $value = trim($value);

                            // Collect response header
                            $responseHeaders[] = "{$name}: {$value}";

                            // Skip certain headers that shouldn't be proxied
                            $skipHeaders = [
                                'transfer-encoding',      // Don't forward encoding
                                'content-encoding',       // Don't forward encoding
                                'proxy-connection',       // Non-standard, causes issues
                                'connection',             // Proxy manages connection
                            ];
                            if (!in_array(strtolower($name), $skipHeaders)) {
                                if (!headers_sent()) {
                                    header("$name: $value");
                                }
                            }
                        }
                    } catch (\Throwable $e) {
                        error_log("ProxyController: Error in header callback - " . $e->getMessage());
                    }
                },
                curlOptions: [CURLOPT_HTTPHEADER => $curlHeaders],
                followLocation: true  // ALWAYS follow redirects in proxy endpoint
            );

            // Check for cURL errors
            if ($result['errno'] !== 0) {
                $errorMsg = $result['error'] ?? 'Unknown cURL error';

                // CURLE_WRITE_ERROR (errno 23) often means client disconnected
                // This is NORMAL for IPTV streams (clients test, seek, or drop connections)
                if ($result['errno'] === 23) {
                    error_log("ProxyController: Client disconnected (normal for IPTV playback)");
                    error_log("ProxyController: Final status code - " . ($httpCode ?: 200));
                    error_log("=================================");
                    return $response->withStatus($httpCode ?: 200);
                }

                // Real streaming errors - return 500
                error_log("ProxyController: cURL error - $errorMsg (errno: {$result['errno']})");
                error_log("ProxyController: Final status code - 500");
                error_log("=================================");
                if (!headers_sent()) {
                    http_response_code(500);
                }
                echo 'Stream error: ' . htmlspecialchars($errorMsg);
                return $response->withStatus(500);
            }

            // Log response status and headers
            error_log("ProxyController: Final response status - {$httpCode}");
            if (!empty($responseHeaders)) {
                error_log("ProxyController: Final response headers - " . implode(" | ", $responseHeaders));
            }
            error_log("ProxyController: Final status code - {$httpCode}");
            error_log("ProxyController: Proxy stream completed successfully");
            error_log("=================================");

            return $response->withStatus($httpCode);

        } catch (\Exception $e) {
            error_log("ProxyController: Exception - " . $e->getMessage());
            error_log("ProxyController: Final status code - 500");
            http_response_code(500);
            echo 'Stream error: ' . htmlspecialchars($e->getMessage());
            return $response->withStatus(500);
        }
    }
}
