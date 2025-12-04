<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\AdminUser;
use Firebase\JWT\JWT;
use Firebase\JWT\Key;

class AuthController
{
    /**
     * Login endpoint - authenticates admin user and returns JWT token
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function login(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            $body = json_decode($request->getBody()->getContents(), true);

            // Validate request
            if (empty($body['username']) || empty($body['password'])) {
                return $this->jsonError($response, 'Username and password are required', 400);
            }

            // Authenticate user
            $user = AdminUser::authenticate($body['username'], $body['password']);
            if (!$user) {
                return $this->jsonError($response, 'Invalid credentials', 401);
            }

            // Generate JWT token
            $token = $this->generateToken($user);

            return $this->jsonResponse($response, [
                'success' => true,
                'token' => $token,
                'user' => [
                    'id' => $user->id,
                    'username' => $user->username,
                    'email' => $user->email ?? null,
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Authentication failed: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get current authenticated user
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function getCurrentUser(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        try {
            // Get token from request (set by middleware)
            $token = $request->getAttribute('token');
            $userId = $request->getAttribute('userId');

            if (!$userId) {
                return $this->jsonError($response, 'Unauthorized', 401);
            }

            // Get user from database
            $user = AdminUser::find($userId);
            if (!$user) {
                return $this->jsonError($response, 'User not found', 404);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'user' => [
                    'id' => $user->id,
                    'username' => $user->username,
                    'email' => $user->email ?? null,
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get user: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Logout endpoint (client-side token removal)
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @return ResponseInterface
     */
    public function logout(ServerRequestInterface $request, ResponseInterface $response): ResponseInterface
    {
        return $this->jsonResponse($response, [
            'success' => true,
            'message' => 'Logged out successfully',
        ]);
    }

    /**
     * Generate JWT token for authenticated user
     *
     * @param AdminUser $user
     * @return string
     */
    private function generateToken(AdminUser $user): string
    {
        $secret = $_ENV['JWT_SECRET'] ?? null;
        if (!$secret) {
            throw new \RuntimeException('JWT_SECRET environment variable is not set');
        }
        $issuedAt = time();
        $expire = $issuedAt + (24 * 60 * 60); // 24 hours

        $payload = [
            'iat' => $issuedAt,
            'exp' => $expire,
            'userId' => $user->id,
            'username' => $user->username,
        ];

        return JWT::encode($payload, $secret, 'HS256');
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
