<?php

declare(strict_types=1);

namespace App\Controllers\Xtream;

use Psr\Http\Message\ResponseInterface;
use App\Models\Client;
use App\Models\Source;
use App\Services\HttpClient;

class StreamDataController
{
    /**
     * Handle stream proxy requests
     */
    public function handleStreamRequest(
        ResponseInterface $response,
        string $type,
        string $username,
        string $password,
        int $streamId,
        string $ext
    ): ResponseInterface {
        error_log("StreamDataController: handleStreamRequest called with $type/$username/$password/$streamId.$ext");
        $ext = strtolower($ext);

        if (!$streamId) {
            error_log("StreamDataController: Invalid stream_id");
            $response->getBody()->write('Invalid stream_id');
            return $response->withStatus(400);
        }

        try {
            error_log("StreamDataController: Looking up client credentials");
            $clients = Client::findAll(['username' => $username, 'password' => $password]);
            error_log("StreamDataController: Found " . count($clients) . " clients");
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
            error_log("StreamDataController: Calling proxyStreamRequest with URL: $streamUrl");
            return $this->proxyStreamRequest($response, $streamUrl, $ext);

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
        ResponseInterface $response,
        string $proxyUrl,
        string $ext
    ): ResponseInterface {
        try {
            $httpClient = HttpClient::getInstance();
            $httpCode = 200;
            $responseStarted = false;

            // Check if we should follow redirects when proxying
            $followLocation = filter_var($_ENV['STREAM_FOLLOW_LOCATION'] ?? false, FILTER_VALIDATE_BOOLEAN);

            // Stream directly from upstream to client without buffering
            // NOTE: Headers forwarding will be added in next iteration
            $result = $httpClient->streamDirectToClient(
                $proxyUrl,
                // Callback for data chunks (optional - can be used for logging/monitoring)
                onData: function(string $chunk) {
                    // Data is already sent to client via echo in streamDirectToClient
                    // This callback is optional for monitoring/logging
                },
                // Callback for headers (optional - can be used for response processing)
                onHeader: function(string $headerLine) use (&$response, &$httpCode, &$responseStarted) {
                    // Parse and forward headers
                    if (stripos($headerLine, 'HTTP/') === 0) {
                        // Extract HTTP status code
                        if (preg_match('/HTTP\/\d\.\d\s+(\d+)/', $headerLine, $matches)) {
                            $httpCode = (int) $matches[1];
                        }
                    } else if (strpos($headerLine, ':') !== false) {
                        // Forward response headers to client
                        [$name, $value] = explode(':', $headerLine, 2);
                        $name = trim($name);
                        $value = trim($value);

                        // Skip certain headers that shouldn't be proxied
                        $skipHeaders = ['transfer-encoding', 'content-encoding'];
                        if (!in_array(strtolower($name), $skipHeaders)) {
                            header("$name: $value");
                        }
                    }
                },
                curlOptions: [],
                followLocation: $followLocation
            );

            // Check for cURL errors
            if ($result['errno'] !== 0) {
                $errorMsg = $result['error'] ?? 'Unknown cURL error';
                error_log("Stream proxy error: $errorMsg (errno: {$result['errno']})");
                http_response_code(500);
                echo 'Stream error: ' . htmlspecialchars($errorMsg);
                return $response->withStatus(500);
            }

            // Set HTTP status code from upstream response
            http_response_code($result['http_code']);

            return $response->withStatus($result['http_code']);

        } catch (\Exception $e) {
            error_log("Stream proxy exception: " . $e->getMessage());
            http_response_code(500);
            echo 'Stream error: ' . htmlspecialchars($e->getMessage());
            return $response->withStatus(500);
        }
    }

    /**
     * Extract forwardable headers from client request to pass to upstream server
     *
     * @param ServerRequestInterface $request Client request
     * @return array Array of headers in format ['Header: value', 'Another-Header: value']
     */
    private function extractForwardableHeaders(ServerRequestInterface $request): array
    {
        $headers = [];

        // Headers to skip when forwarding to upstream server
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

        // Get all headers from request
        foreach ($request->getHeaders() as $name => $values) {
            $lowerName = strtolower($name);

            // Skip headers that shouldn't be forwarded
            if (in_array($lowerName, $skipHeaders)) {
                continue;
            }

            // Join multiple header values with commas (standard HTTP behavior)
            $value = implode(',', $values);

            // Add to curl headers array in format required by CURLOPT_HTTPHEADER
            $headers[] = "$name: $value";
        }

        return $headers;
    }
}
