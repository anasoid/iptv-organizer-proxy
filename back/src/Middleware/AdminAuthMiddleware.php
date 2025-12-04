<?php

declare(strict_types=1);

namespace App\Middleware;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface;
use Firebase\JWT\JWT;
use Firebase\JWT\Key;
use Firebase\JWT\ExpiredException;

class AdminAuthMiddleware implements MiddlewareInterface
{
    public function process(ServerRequestInterface $request, RequestHandlerInterface $handler): ResponseInterface
    {
        try {
            // Get authorization header
            $authHeader = $request->getHeaderLine('Authorization');
            if (empty($authHeader)) {
                return $this->unauthorizedResponse(
                    $handler->handle($request),
                    'Authorization header missing'
                );
            }

            // Extract token from "Bearer <token>"
            if (!preg_match('/Bearer\s+(.+)/', $authHeader, $matches)) {
                return $this->unauthorizedResponse(
                    $handler->handle($request),
                    'Invalid authorization header format'
                );
            }

            $token = $matches[1];
            $secret = $_ENV['JWT_SECRET'] ?? 'your-secret-key-change-this-in-production';

            // Verify token
            $decoded = JWT::decode($token, new Key($secret, 'HS256'));

            // Add decoded token to request attributes
            $request = $request->withAttribute('token', $token);
            $request = $request->withAttribute('userId', $decoded->userId ?? null);
            $request = $request->withAttribute('user', $decoded);

            return $handler->handle($request);
        } catch (ExpiredException $e) {
            return $this->unauthorizedResponse(
                $handler->handle($request),
                'Token has expired'
            );
        } catch (\Throwable $e) {
            return $this->unauthorizedResponse(
                $handler->handle($request),
                'Invalid token: ' . $e->getMessage()
            );
        }
    }

    /**
     * Return unauthorized JSON response
     *
     * @param ResponseInterface $response
     * @param string $message
     * @return ResponseInterface
     */
    private function unauthorizedResponse(ResponseInterface $response, string $message): ResponseInterface
    {
        $response->getBody()->write(json_encode([
            'success' => false,
            'message' => $message,
        ]));

        return $response
            ->withHeader('Content-Type', 'application/json')
            ->withStatus(401);
    }
}
