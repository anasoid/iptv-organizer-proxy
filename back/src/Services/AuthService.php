<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\Client;
use App\Models\ConnectionLog;
use DateTime;
use Psr\Http\Message\ServerRequestInterface;

/**
 * Authentication Service
 *
 * Handles client authentication and connection logging
 */
class AuthService
{
    /**
     * Authenticate client with username and password
     *
     * @param string $username
     * @param string $password
     * @return Client|null Returns Client model if authenticated, null otherwise
     */
    public function authenticateClient(string $username, string $password): ?Client
    {
        $clients = Client::findAll(['username' => $username]);

        if (empty($clients)) {
            return null;
        }

        $client = $clients[0];

        // Compare password (plain text for now, can be enhanced to support hashing)
        if ($client->password !== $password) {
            return null;
        }

        // Check if client is active and not expired
        if (!$this->isClientActive($client)) {
            return null;
        }

        return $client;
    }

    /**
     * Check if client is active and not expired
     *
     * @param Client $client
     * @return bool
     */
    public function isClientActive(Client $client): bool
    {
        // Check is_active flag
        if (!$client->is_active) {
            return false;
        }

        // Check expiry date if set
        if ($client->expiry_date !== null) {
            $expiryDate = new DateTime($client->expiry_date);
            $now = new DateTime();

            if ($now > $expiryDate) {
                return false;
            }
        }

        return true;
    }

    /**
     * Log client connection/action
     *
     * @param Client $client
     * @param string $action API action or endpoint
     * @param ServerRequestInterface $request PSR-7 request
     * @return void
     */
    public function logConnection(Client $client, string $action, ServerRequestInterface $request): void
    {
        $serverParams = $request->getServerParams();

        ConnectionLog::logConnection(
            $client->id,
            $action,
            [
                'ip' => $serverParams['REMOTE_ADDR'] ?? 'unknown',
                'user_agent' => $serverParams['HTTP_USER_AGENT'] ?? null,
            ]
        );
    }

    /**
     * Validate client has required source assignment
     *
     * @param Client $client
     * @return bool
     */
    public function hasSourceAssignment(Client $client): bool
    {
        return !empty($client->source_id);
    }
}
