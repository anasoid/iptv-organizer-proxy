<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\AdminUser;

class AdminUserController
{
    /**
     * List all admin users
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
            $offset = ($page - 1) * $limit;

            $users = AdminUser::findAll();
            $total = count($users);
            $paginatedUsers = array_slice($users, $offset, $limit);

            // Convert models to arrays and remove password hashes
            $usersData = array_map(function ($user) {
                $data = $user->toArray();
                unset($data['password_hash']);
                return $data;
            }, $paginatedUsers);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $usersData,
                'pagination' => [
                    'page' => $page,
                    'limit' => $limit,
                    'total' => $total,
                    'pages' => (int) ceil($total / $limit),
                ],
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to list admin users: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Get admin user by ID
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
                return $this->jsonError($response, 'Invalid user ID', 400);
            }

            $user = AdminUser::find($id);
            if (!$user) {
                return $this->jsonError($response, 'User not found', 404);
            }

            // Convert to array and remove password hash
            $userData = $user->toArray();
            unset($userData['password_hash']);

            return $this->jsonResponse($response, [
                'success' => true,
                'data' => $userData,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to get admin user: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Create new admin user
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

            // Create admin user
            $user = AdminUser::create(
                $body['username'],
                $body['password'],
                $body['email'] ?? null
            );

            // Convert to array and remove password hash
            $userData = $user->toArray();
            unset($userData['password_hash']);

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Admin user created successfully',
                'data' => $userData,
            ], 201);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to create admin user: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Update admin user
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
                return $this->jsonError($response, 'Invalid user ID', 400);
            }

            $user = AdminUser::find($id);
            if (!$user) {
                return $this->jsonError($response, 'User not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            // Update fields
            if (isset($body['email'])) {
                $user->email = $body['email'];
            }
            if (isset($body['is_active'])) {
                $user->is_active = (int) $body['is_active'];
            }

            if (!$user->save()) {
                return $this->jsonError($response, 'Failed to update admin user', 500);
            }

            // Convert to array and remove password hash
            $userData = $user->toArray();
            unset($userData['password_hash']);

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Admin user updated successfully',
                'data' => $userData,
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to update admin user: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Delete admin user
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
                return $this->jsonError($response, 'Invalid user ID', 400);
            }

            $user = AdminUser::find($id);
            if (!$user) {
                return $this->jsonError($response, 'User not found', 404);
            }

            if (!$user->delete()) {
                return $this->jsonError($response, 'Failed to delete admin user', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Admin user deleted successfully',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to delete admin user: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Change password for admin user
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function changePassword(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $id = (int) ($args['id'] ?? 0);
            if (!$id) {
                return $this->jsonError($response, 'Invalid user ID', 400);
            }

            $user = AdminUser::find($id);
            if (!$user) {
                return $this->jsonError($response, 'User not found', 404);
            }

            $body = json_decode($request->getBody()->getContents(), true);

            // Validate old password
            if (empty($body['old_password'])) {
                return $this->jsonError($response, 'Old password is required', 400);
            }
            if (empty($body['new_password'])) {
                return $this->jsonError($response, 'New password is required', 400);
            }

            if (!password_verify($body['old_password'], $user->password_hash)) {
                return $this->jsonError($response, 'Old password is incorrect', 401);
            }

            // Update password
            if (!$user->updatePassword($body['new_password'])) {
                return $this->jsonError($response, 'Failed to update password', 500);
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Password updated successfully',
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to change password: ' . $e->getMessage(), 500);
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
