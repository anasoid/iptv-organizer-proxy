<?php

declare(strict_types=1);

namespace App\Controllers\Xtream;

use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use App\Models\Client;
use App\Models\Source;
use App\Services\HttpClient;

class StreamDataController
{
    /**
     * Handle stream proxy requests
     */
    public function handleStreamRequest(
        ServerRequestInterface $request,
        ResponseInterface $response,
        string $type,
        string $username,
        string $password,
        int $streamId,
        string $ext
    ): ResponseInterface {
        $ext = strtolower($ext);

        // Log initial stream request
        error_log("========== STREAM REQUEST ==========");
        error_log("StreamDataController: Client request URL - " . $request->getRequestTarget());
        error_log("StreamDataController: Stream request - Type: {$type}, StreamID: {$streamId}, Ext: {$ext}");

        // Log client request headers
        $clientHeaders = [];
        foreach ($request->getHeaders() as $name => $values) {
            $clientHeaders[] = "{$name}: " . implode(",", $values);
        }
        error_log("StreamDataController: Client request headers - " . implode(" | ", $clientHeaders));

        if (!$streamId) {
            error_log("StreamDataController: Final status code - 400");
            $response->getBody()->write('Invalid stream_id');
            return $response->withStatus(400);
        }

        try {
            $clients = Client::findAll(['username' => $username, 'password' => $password]);
            if (empty($clients)) {
                error_log("StreamDataController: Final status code - 401");
                $response->getBody()->write('Invalid credentials');
                return $response->withStatus(401);
            }

            $client = $clients[0];

            if (!$client->source_id) {
                error_log("StreamDataController: Final status code - 400");
                $response->getBody()->write('No source configured');
                return $response->withStatus(400);
            }

            $source = Source::find($client->source_id);
            if (!$source) {
                error_log("StreamDataController: Final status code - 404");
                $response->getBody()->write('Source not found');
                return $response->withStatus(404);
            }

            // Build stream URL using source credentials
            $sourceUsername = $source->username;
            $sourcePassword = $source->password;
            $streamUrl = rtrim($source->url, '/') . '/' . $type . '/' . $sourceUsername . '/' . $sourcePassword . '/' . $streamId . '.' . $ext;

            // Log the upstream URL
            error_log("StreamDataController: Upstream stream URL - {$streamUrl}");

            // Check if we should redirect instead of proxy
            $useRedirect = filter_var($_ENV['STREAM_USE_REDIRECT'] ?? false, FILTER_VALIDATE_BOOLEAN);
            if ($useRedirect) {
                return $response->withStatus(302)->withHeader('Location', $streamUrl);
            }

            // Extract request headers for status check
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
                    'expect',               // Not needed for stream
                    'proxy-connection',     // Not applicable
                    'proxy-authenticate',   // Not applicable
                    'te',                   // Transfer encoding
                ];

                if (!in_array(strtolower($name), $skipHeaders)) {
                    $curlHeaders[] = "{$name}: {$headerValue}";
                }
            }

            // Check upstream status for 302 redirects
            $upstreamStatus = $this->checkUpstreamStatus($streamUrl, $curlHeaders, (bool) $source->enableproxy);

            // Log upstream response status
            error_log("StreamDataController: Upstream response status - {$upstreamStatus['status']}");
            if (!empty($upstreamStatus['location'])) {
                error_log("StreamDataController: Upstream Location header - {$upstreamStatus['location']}");
            }

            // If 302 redirect, check if we should proxy or redirect directly
            if ($upstreamStatus['status'] === 302 && !empty($upstreamStatus['location'])) {
                // Check if source disables stream proxy endpoint
                if ($source->disablestreamproxy) {
                    // Return direct redirect to client - they will connect directly to discovered URL
                    $redirectUrl = $upstreamStatus['location'];
                    error_log("StreamDataController: 302 redirect detected, returning direct redirect to client");
                    error_log("StreamDataController: Client redirect URL - {$redirectUrl}");
                    error_log("StreamDataController: Final status code - 302");
                    error_log("=================================");

                    return $response
                        ->withStatus(302)
                        ->withHeader('Location', $redirectUrl);
                } else {
                    // Current behavior: encode and route through /proxy endpoint
                    $encodedUrl = base64_encode($upstreamStatus['location']);
                    $proxyUrl = "/proxy/{$username}/{$password}?url={$encodedUrl}";

                    error_log("StreamDataController: 302 redirect detected, returning client redirect to proxy endpoint");
                    error_log("StreamDataController: Client redirect URL - {$proxyUrl}");
                    error_log("StreamDataController: Final status code - 302");
                    error_log("=================================");

                    return $response
                        ->withStatus(302)
                        ->withHeader('Location', $proxyUrl);
                }
            }

            // Log that we're streaming normally
            error_log("StreamDataController: No redirect, streaming data normally");

            // Proxy the stream normally
            return $this->proxyStreamRequest($request, $response, $streamUrl, $ext, (bool) $source->enableproxy);

        } catch (\Exception $e) {
            error_log("StreamDataController: Exception - " . $e->getMessage());
            error_log("StreamDataController: Final status code - 500");
            $response->getBody()->write('Error: ' . htmlspecialchars($e->getMessage()));
            return $response->withStatus(500);
        }
    }

    /**
     * Proxy stream request to source (true streaming without buffering)
     */
    private function proxyStreamRequest(
        ServerRequestInterface $request,
        ResponseInterface $response,
        string $proxyUrl,
        string $ext,
        ?bool $sourceEnableProxy = null
    ): ResponseInterface {
        try {
            // Log streaming start
            error_log("========== STREAMING DATA ==========");
            error_log("StreamDataController: Starting stream from - {$proxyUrl}");

            $httpClient = HttpClient::getInstance();
            $httpCode = 200;
            $responseStarted = false;
            $responseHeaders = [];

            // Check if we should follow redirects when proxying
            $followLocation = filter_var($_ENV['STREAM_FOLLOW_LOCATION'] ?? false, FILTER_VALIDATE_BOOLEAN);

            // Extract and format request headers for logging and forwarding
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
                    'expect',               // Not needed for stream
                    'proxy-connection',     // Not applicable
                    'proxy-authenticate',   // Not applicable
                    'te',                   // Transfer encoding
                ];

                if (!in_array(strtolower($name), $skipHeaders)) {
                    $curlHeaders[] = "{$name}: {$headerValue}";
                }
            }
            $requestHeadersStr = !empty($requestHeaders) ? implode(" | ", $requestHeaders) : "No headers";

            // Log request headers that will be forwarded upstream
            $upstreamHeadersStr = !empty($curlHeaders) ? implode(" | ", $curlHeaders) : "No headers";
            error_log("StreamDataController: Request headers to forward - {$upstreamHeadersStr}");

            // Disable output buffering and compression for streaming
            if (function_exists('ini_set')) {
                ini_set('output_buffering', '0');
                ini_set('zlib.output_compression', '0');
            }

            // Clear any existing output buffers
            while (ob_get_level()) {
                ob_end_clean();
            }

            // Stream directly from upstream to client without buffering
            $result = $httpClient->streamDirectToClient(
                $proxyUrl,
                // Callback for data chunks (optional - can be used for logging/monitoring)
                onData: function(string $chunk) {
                    // Data is already sent to client via echo in streamDirectToClient
                },
                // Callback for headers (optional - can be used for response processing)
                onHeader: function(string $headerLine) use (&$response, &$httpCode, &$responseStarted, &$responseHeaders) {
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
                                'transfer-encoding',      // Proxy will set this as needed
                                'proxy-connection',       // Non-standard, causes issues
                                'connection',             // Proxy manages connection
                                // NOTE: DO NOT skip content-encoding - client needs it to decompress body
                            ];
                            if (!in_array(strtolower($name), $skipHeaders)) {
                                if (!headers_sent()) {
                                    header("$name: $value");
                                }
                            }
                        }
                    } catch (\Throwable $e) {
                        error_log("StreamDataController: Error in header callback - " . $e->getMessage());
                    }
                },
                curlOptions: [CURLOPT_HTTPHEADER => $curlHeaders],
                followLocation: $followLocation,
                sourceEnableProxy: $sourceEnableProxy
            );

            if ($result['errno'] !== 0) {
                $errorMsg = $result['error'] ?? 'Unknown cURL error';
                error_log("StreamDataController: cURL error - $errorMsg (errno: {$result['errno']})");
                error_log("StreamDataController: Final status code - 500");
                if (!headers_sent()) {
                    http_response_code(500);
                }
                echo 'Stream error: ' . htmlspecialchars($errorMsg);
                return $response->withStatus(500);
            }

            // Log response status and headers
            error_log("StreamDataController: Response status - {$httpCode}");
            if (!empty($responseHeaders)) {
                error_log("StreamDataController: Response headers - " . implode(" | ", $responseHeaders));
            }
            error_log("StreamDataController: Final status code - {$httpCode}");
            error_log("StreamDataController: Stream completed successfully");
            error_log("=================================");

            return $response->withStatus($httpCode);

        } catch (\Exception $e) {
            error_log("StreamDataController: Exception - " . $e->getMessage());
            error_log("StreamDataController: Final status code - 500");
            http_response_code(500);
            echo 'Stream error: ' . htmlspecialchars($e->getMessage());
            return $response->withStatus(500);
        }
    }

    /**
     * Check upstream status without streaming body
     *
     * Makes a HEAD or GET request to check the upstream response status and location header.
     * Used to detect 302 redirects before attempting to stream.
     *
     * @param string $url The URL to check
     * @param array $curlHeaders Headers to forward to upstream
     * @param ?bool $sourceEnableProxy Source-level proxy setting (null = not specified)
     * @return array Array with keys: ['status' => int, 'location' => string|null]
     */
    private function checkUpstreamStatus(string $url, array $curlHeaders = [], ?bool $sourceEnableProxy = null): array
    {
        try {
            $httpClient = HttpClient::getInstance();

            // Use GET request with RETURNTRANSFER to check status and headers without buffering
            // (Some servers don't handle HEAD requests correctly and won't return redirects)
            $curlOptions = [
                CURLOPT_HTTPHEADER => $curlHeaders,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_HEADER => true,
                CURLOPT_TIMEOUT => 10,
                CURLOPT_CONNECTTIMEOUT => 5,
            ];

            // Make request to get headers
            $result = $httpClient->curlRequest($url, $curlOptions, followLocation: false, sourceEnableProxy: $sourceEnableProxy);

            $httpCode = $result['status'];
            $location = null;

            // Parse headers to find Location header
            if (!empty($result['headers'])) {
                $headerLines = explode("\r\n", $result['headers']);
                foreach ($headerLines as $headerLine) {
                    $headerLine = trim($headerLine);
                    if (stripos($headerLine, 'location:') === 0) {
                        $location = trim(substr($headerLine, 9));  // strlen('location:') = 9
                        break;
                    }
                }
            }

            return [
                'status' => $httpCode,
                'location' => $location,
                'errno' => $result['errno'],
            ];

        } catch (\Exception $e) {
            error_log("StreamDataController: Error checking upstream status - " . $e->getMessage());
            return [
                'status' => 0,
                'location' => null,
                'errno' => 1,
            ];
        }
    }

}
