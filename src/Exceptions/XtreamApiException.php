<?php

declare(strict_types=1);

namespace App\Exceptions;

use Exception;

/**
 * Exception thrown when Xtream Codes API operations fail
 */
class XtreamApiException extends Exception
{
    /**
     * Create exception for authentication failure
     */
    public static function authenticationFailed(string $message = 'Authentication failed'): self
    {
        return new self($message);
    }

    /**
     * Create exception for network failure
     */
    public static function networkError(string $message, ?\Throwable $previous = null): self
    {
        return new self("Network error: {$message}", 0, $previous);
    }

    /**
     * Create exception for invalid response
     */
    public static function invalidResponse(string $message = 'Invalid or malformed response from API'): self
    {
        return new self($message);
    }

    /**
     * Create exception for API error
     */
    public static function apiError(string $message, int $code = 0): self
    {
        return new self("API error: {$message}", $code);
    }

    /**
     * Create exception for missing required parameter
     */
    public static function missingParameter(string $parameter): self
    {
        return new self("Missing required parameter: {$parameter}");
    }
}
