<?php

declare(strict_types=1);

namespace App\Controllers;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Client;

class ClientController
{
    /**
     * List all clients (paginated, searchable)
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function list(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $page = (int) ($queryParams['page'] ?? 1);
            $limit = (int) ($queryParams['limit'] ?? 10);
            $search = $queryParams['search'] ?? null;
            $offset = ($page - 1) * $limit;

            // Get all clients (filter by search if provided)
            $allClients = Client::findAll();

            if ($search) {
                $allClients = array_filter($allClients, function ($client) use ($search) {
                    return stripos($client->username, $search) !== false ||
                           stripos($client->password, $search) !== false ||
                           stripos($client->email ?? '', $search) !== false;
                });
            }

            $total = count($allClients);
            $paginatedClients = array_slice($allClients, $offset, $limit);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $paginatedClients,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list clients: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get client by ID
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function get(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $client,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get client: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Create new client
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function create(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $body = json_decode($request->getBody()->getContents(), true);

            // Validate required fields
            if (empty($body['username'])) {
                return $this->jsonError($response, 'Username is required', 400);
            }
            if (empty($body['password'])) {
                return $this->jsonError($response, 'Password is required', 400);
            }
            if (empty($body['source_id'])) {
                return $this->jsonError($response, 'Source ID is required', 400);
            }

            // Create client
            $client = new Client();
            $client->username = $body['username'];
            $client->password = $body['password'];
            $client->source_id = (int) $body['source_id'];
            $client->filter_id = isset($body['filter_id']) ? (int) $body['filter_id'] : null;
            $client->email = $body['email'] ?? null;
            $client->is_active = $body['is_active'] ?? 1;

            if (!$client->save()) {
                return $this->jsonError($response, 'Failed to create client', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Client created successfully',
                'data' => $client,
            ], 201);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to create client: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Update client
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function update(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            // Update fields
            if (isset($body['username'])) {
                $client->username = $body['username'];
            }
            if (isset($body['password'])) {
                $client->password = $body['password'];
            }
            if (isset($body['source_id'])) {
                $client->source_id = (int) $body['source_id'];
            }
            if (isset($body['filter_id'])) {
                $client->filter_id = $body['filter_id'] ? (int) $body['filter_id'] : null;
            }
            if (isset($body['email'])) {
                $client->email = $body['email'];
            }
            if (isset($body['is_active'])) {
                $client->is_active = (int) $body['is_active'];
            }

            if (!$client->save()) {
                return $this->jsonError($response, 'Failed to update client', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Client updated successfully',
                'data' => $client,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to update client: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Delete client
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function delete(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            if (!$client->delete()) {
                return $this->jsonError($response, 'Failed to delete client', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Client deleted successfully',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to delete client: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get connection logs for client
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function logs(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid client ID', 400);
            }

            $client = Client::find($id);
            if (!$client) {
                return $this->jsonError($response, 'Client not found', 404);
            }

            $queryParams = $request->getQueryParams();
            $limit = (int) ($queryParams['limit'] ?? 50);

            // Get connection logs (this will need a connectionLogs method on Client model)
            // For now, return empty logs
            $logs = [];

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $logs,
                'count' => count($logs),
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get client logs: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Return JSON response
     *
     * @param ResponseInterface $response
     * @param mixed $data
     * @param int $statusCode
     * @return ResponseInterface
     */
    protected function jsonResponse(ResponseInterface $response, mixed $data, int $statusCode = 200): ResponseInterface
    {
        $response->getBody()->write(json_encode($data));
        return $response
            ->withHeader('Content-Type', 'application/json')
            ->withStatus($statusCode);
    }

    /**
     * Return JSON error response
     *
     * @param ResponseInterface $response
     * @param string $message
     * @param int $statusCode
     * @return ResponseInterface
     */
    protected function jsonError(ResponseInterface $response, string $message, int $statusCode = 400): ResponseInterface
    {
        return $this->jsonResponse($response, [
            'success' => false,
            'message' => $message,
        ], $statusCode);
    }
}
