<?php

declare(strict_types=1);

namespace App\Controllers\Xtream;

use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use App\Models\Client;
use App\Models\Source;

class TimeshiftController
{
    /**
     * Handle timeshift proxy requests
     *
     * URL format: /streaming/timeshift.php?username=X&password=Y&stream=Z&duration=D&start=S
     * Also supports: /streaming/timeshift.php?stream=Z&duration=D&start=S (with client auth from middleware)
     */
    public function handleTimeshiftRequest(
        ServerRequestInterface $request,
        ResponseInterface $response
    ): ResponseInterface {
        try {
            $queryParams = $request->getQueryParams();

            // Extract parameters
            $username = $queryParams['username'] ?? null;
            $password = $queryParams['password'] ?? null;

            // Authenticate client and get source
            if (!$username || !$password) {
                $response->getBody()->write('Missing required parameters: username and password');
                return $response->withStatus(401);
            }

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
            $sourceUrl = $source->url;

            // Build timeshift URL with source credentials
            $timeshiftUrl = rtrim($sourceUrl, '/') . '/streaming/timeshift.php?';
            $timeshiftParams = [
                'username' => $sourceUsername,
                'password' => $sourcePassword,
            ];

            // Add all other query parameters as-is (stream, duration, start, etc.)
            foreach ($queryParams as $key => $value) {
                if (!in_array($key, ['username', 'password'])) {
                    $timeshiftParams[$key] = $value;
                }
            }

            $timeshiftUrl .= http_build_query($timeshiftParams);

            // Redirect to original source with source credentials
            return $response->withStatus(302)->withHeader('Location', $timeshiftUrl);

        } catch (\Exception $e) {
            $response->getBody()->write('Error: ' . htmlspecialchars($e->getMessage()));
            return $response->withStatus(500);
        }
    }
}
