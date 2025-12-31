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

        if (!$streamId) {
            $response->getBody()->write('Invalid stream_id');
            return $response->withStatus(400);
        }

        try {
            $clients = Client::findAll(['username' => $username, 'password' => $password]);
            if (empty($clients)) {
                $response->getBody()->write('Invalid credentials');
                return $response->withStatus(401);
            }

            $client = $clients[0];

            if (!$client->source_id) {
                $response->getBody()->write('No source configured');
                return $response->withStatus(400);
            }

            $source = Source::find($client->source_id);
            if (!$source) {
                $response->getBody()->write('Source not found');
                return $response->withStatus(404);
            }

            // Build stream URL using source credentials
            $sourceUsername = $source->username;
            $sourcePassword = $source->password;
            $streamUrl = rtrim($source->url, '/') . '/' . $type . '/' . $sourceUsername . '/' . $sourcePassword . '/' . $streamId . '.' . $ext;

            // Check if we should redirect instead of proxy
            $useRedirect = filter_var($_ENV['STREAM_USE_REDIRECT'] ?? false, FILTER_VALIDATE_BOOLEAN);
            if ($useRedirect) {
                return $response->withStatus(302)->withHeader('Location', $streamUrl);
            }

            // Proxy the stream
            return $this->proxyStreamRequest($request, $response, $streamUrl, $ext);

        } catch (\Exception $e) {
            error_log("StreamDataController: Exception - " . $e->getMessage());
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
        string $ext
    ): ResponseInterface {
        try {
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
                        error_log("StreamDataController: Error in header callback - " . $e->getMessage());
                    }
                },
                curlOptions: [CURLOPT_HTTPHEADER => $curlHeaders],
                followLocation: $followLocation
            );

            if ($result['errno'] !== 0) {
                $errorMsg = $result['error'] ?? 'Unknown cURL error';
                error_log("StreamDataController: cURL error - $errorMsg (errno: {$result['errno']})");
                if (!headers_sent()) {
                    http_response_code(500);
                }
                echo 'Stream error: ' . htmlspecialchars($errorMsg);
                return $response->withStatus(500);
            }

            return $response->withStatus($httpCode);

        } catch (\Exception $e) {
            error_log("StreamDataController: Exception - " . $e->getMessage());
            http_response_code(500);
            echo 'Stream error: ' . htmlspecialchars($e->getMessage());
            return $response->withStatus(500);
        }
    }

}
