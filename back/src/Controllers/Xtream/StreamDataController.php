<?php

declare(strict_types=1);

namespace App\Controllers\Xtream;

use Psr\Http\Message\ResponseInterface;
use App\Models\Client;
use App\Models\Source;

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

            // Build proxy URL using source credentials
            $sourceUsername = $source->username;
            $sourcePassword = $source->password;
            $proxyUrl = rtrim($source->url, '/') . '/' . $type . '/' . $sourceUsername . '/' . $sourcePassword . '/' . $streamId . '.' . $ext;

            // Proxy the stream
            return $this->proxyStreamRequest($response, $proxyUrl, $ext);

        } catch (\Exception $e) {
            $response->getBody()->write('Error: ' . htmlspecialchars($e->getMessage()));
            return $response->withStatus(500);
        }
    }

    /**
     * Proxy stream request to source
     */
    private function proxyStreamRequest(
        ResponseInterface $response,
        string $proxyUrl,
        string $ext
    ): ResponseInterface {
        try {
            $ch = curl_init();
            curl_setopt_array($ch, [
                CURLOPT_URL => $proxyUrl,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_BINARYTRANSFER => true,
                CURLOPT_SSL_VERIFYPEER => false,
                CURLOPT_SSL_VERIFYHOST => false,
                CURLOPT_FOLLOWLOCATION => false,
                CURLOPT_TIMEOUT => 300,
                CURLOPT_CONNECTTIMEOUT => 10,
                CURLOPT_HEADER => true,
            ]);

            $response_data = curl_exec($ch);
            $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
            $header_size = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
            $headers = substr($response_data, 0, $header_size);
            $body = substr($response_data, $header_size);
            curl_close($ch);

            // Parse headers from response
            $headerLines = explode("\r\n", $headers);
            foreach ($headerLines as $line) {
                if (empty(trim($line))) continue;
                if (stripos($line, 'HTTP/') === 0) continue;
                
                if (strpos($line, ':') !== false) {
                    [$name, $value] = explode(':', $line, 2);
                    $response = $response->withHeader(trim($name), trim($value));
                }
            }

            $response->getBody()->write($body);
            return $response->withStatus($httpCode);

        } catch (\Exception $e) {
            $response->getBody()->write('Stream error: ' . htmlspecialchars($e->getMessage()));
            return $response->withStatus(500);
        }
    }
}
