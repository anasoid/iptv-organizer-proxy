<?php

declare(strict_types=1);

namespace App\Middleware;

use App\Models\Client;
use App\Models\Source;
use App\Models\Filter;
use App\Services\AuthService;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface;
use Slim\Psr7\Response;

/**
 * Client Authentication Middleware
 *
 * Validates client credentials for Xtream API access
 */
class ClientAuthMiddleware implements MiddlewareInterface
{
    private AuthService $authService;

    /**
     * Constructor
     *
     * @param AuthService|null $authService
     */
    public function __construct(?AuthService $authService = null)
    {
        $this->authService = $authService ?? new AuthService();
    }

    /**
     * Process the request
     *
     * @param ServerRequestInterface $request
     * @param RequestHandlerInterface $handler
     * @return ResponseInterface
     */
    public function process(ServerRequestInterface $request, RequestHandlerInterface $handler): ResponseInterface
    {
        // Extract credentials from query parameters
        $queryParams = $request->getQueryParams();
        $username = $queryParams['username'] ?? null;
        $password = $queryParams['password'] ?? null;

        // Check if credentials are provided
        if (empty($username) || empty($password)) {
            return $this->unauthorizedResponse('Missing username or password');
        }

        // Authenticate client
        $client = $this->authService->authenticateClient($username, $password);

        if ($client === null) {
            return $this->unauthorizedResponse('Invalid credentials');
        }

        // Check if client has source assignment
        if (!$this->authService->hasSourceAssignment($client)) {
            return $this->unauthorizedResponse('No source assigned to client');
        }

        // Load associated Source model
        $source = Source::find($client->source_id);

        if ($source === null) {
            return $this->unauthorizedResponse('Source not found');
        }

        // Load associated Filter model (if any)
        $filter = null;
        if ($client->filter_id !== null) {
            $filter = Filter::find($client->filter_id);
        }

        // Store client, source, and filter in request attributes
        $request = $request
            ->withAttribute('client', $client)
            ->withAttribute('source', $source)
            ->withAttribute('filter', $filter)
            ->withAttribute('hide_adult_content', (bool) $client->hide_adult_content);

        // Log the connection
        $action = $request->getUri()->getPath();
        $this->authService->logConnection($client, $action, $request);

        // Continue to next middleware/handler
        return $handler->handle($request);
    }

    /**
     * Create unauthorized response
     *
     * @param string $message Error message
     * @return ResponseInterface
     */
    private function unauthorizedResponse(string $message): ResponseInterface
    {
        $response = new Response();
        $response->getBody()->write(json_encode([
            'error' => $message,
            'status' => 'unauthorized',
        ]));

        return $response
            ->withStatus(401)
            ->withHeader('Content-Type', 'application/json');
    }
}
