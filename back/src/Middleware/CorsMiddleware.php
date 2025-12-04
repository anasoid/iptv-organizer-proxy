<?php

declare(strict_types=1);

namespace App\Middleware;

use Psr\Http\Message\ResponseInterface;
use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface;

/**
 * CORS Middleware
 *
 * Handles Cross-Origin Resource Sharing (CORS) headers
 */
class CorsMiddleware implements MiddlewareInterface
{
    private array $allowedOrigins;

    /**
     * Constructor
     *
     * @param array|null $allowedOrigins List of allowed origins (default: all)
     */
    public function __construct(?array $allowedOrigins = null)
    {
        // Get allowed origins from environment or use default
        if ($allowedOrigins === null) {
            $originsEnv = $_ENV['CORS_ALLOWED_ORIGINS'] ?? '*';
            $this->allowedOrigins = $originsEnv === '*' ? ['*'] : explode(',', $originsEnv);
        } else {
            $this->allowedOrigins = $allowedOrigins;
        }
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
        // Handle preflight OPTIONS request
        if ($request->getMethod() === 'OPTIONS') {
            $response = new \Slim\Psr7\Response();
            return $this->addCorsHeaders($response, $request);
        }

        // Process the request normally
        $response = $handler->handle($request);

        // Add CORS headers to response
        return $this->addCorsHeaders($response, $request);
    }

    /**
     * Add CORS headers to response
     *
     * @param ResponseInterface $response
     * @param ServerRequestInterface $request
     * @return ResponseInterface
     */
    private function addCorsHeaders(ResponseInterface $response, ServerRequestInterface $request): ResponseInterface
    {
        $origin = $request->getHeaderLine('Origin');

        // Determine if origin is allowed
        $allowedOrigin = '*';
        if (!in_array('*', $this->allowedOrigins)) {
            if (in_array($origin, $this->allowedOrigins)) {
                $allowedOrigin = $origin;
            } else {
                // Origin not allowed, use first allowed origin as fallback
                $allowedOrigin = $this->allowedOrigins[0] ?? '*';
            }
        }

        return $response
            ->withHeader('Access-Control-Allow-Origin', $allowedOrigin)
            ->withHeader('Access-Control-Allow-Headers', 'X-Requested-With, Content-Type, Accept, Origin, Authorization')
            ->withHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, PATCH, OPTIONS')
            ->withHeader('Access-Control-Allow-Credentials', 'true');
    }
}
