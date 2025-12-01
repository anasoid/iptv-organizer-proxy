<?php

declare(strict_types=1);

namespace App\Services\Xtream;

use App\Exceptions\XtreamApiException;
use App\Models\Source;
use GuzzleHttp\Client;

/**
 * Xtream Codes API Client (Main Facade)
 *
 * Orchestrates all Xtream API operations and provides a unified interface
 */
class XtreamClient
{
    private XtreamAuthenticator $authenticator;
    private XtreamCategoryClient $categoryClient;
    private XtreamStreamClient $streamClient;
    private XtreamEpgClient $epgClient;

    /**
     * Constructor
     *
     * @param Source|array $config Source model or credentials array ['url', 'username', 'password']
     * @param Client|null $httpClient Optional Guzzle client (for testing)
     * @param int $rateLimitDelay Optional rate limit delay in milliseconds
     * @throws XtreamApiException
     */
    public function __construct($config, ?Client $httpClient = null, int $rateLimitDelay = 0)
    {
        if ($config instanceof Source) {
            $url = $config->url;
            $username = $config->username;
            $password = $config->password;
        } elseif (is_array($config)) {
            if (!isset($config['url'], $config['username'], $config['password'])) {
                throw XtreamApiException::missingParameter('url, username, or password');
            }
            $url = $config['url'];
            $username = $config['username'];
            $password = $config['password'];
        } else {
            throw XtreamApiException::apiError('Invalid config type. Expected Source model or array.');
        }

        // Initialize authenticator
        $this->authenticator = new XtreamAuthenticator(
            $url,
            $username,
            $password,
            $httpClient,
            $rateLimitDelay
        );

        // Initialize specialized clients
        $this->categoryClient = new XtreamCategoryClient($this->authenticator);
        $this->streamClient = new XtreamStreamClient($this->authenticator);
        $this->epgClient = new XtreamEpgClient($this->authenticator);
    }

    /**
     * Authenticate with server
     *
     * @return array Server and user info
     * @throws XtreamApiException
     */
    public function authenticate(): array
    {
        return $this->authenticator->authenticate();
    }

    /**
     * Get live stream categories
     *
     * @return array
     * @throws XtreamApiException
     */
    public function getLiveCategories(): array
    {
        return $this->categoryClient->getLiveCategories();
    }

    /**
     * Get VOD categories
     *
     * @return array
     * @throws XtreamApiException
     */
    public function getVodCategories(): array
    {
        return $this->categoryClient->getVodCategories();
    }

    /**
     * Get series categories
     *
     * @return array
     * @throws XtreamApiException
     */
    public function getSeriesCategories(): array
    {
        return $this->categoryClient->getSeriesCategories();
    }

    /**
     * Get live streams
     *
     * @param int|null $categoryId Optional category filter
     * @return array
     * @throws XtreamApiException
     */
    public function getLiveStreams(?int $categoryId = null): array
    {
        return $this->streamClient->getLiveStreams($categoryId);
    }

    /**
     * Get VOD streams
     *
     * @param int|null $categoryId Optional category filter
     * @return array
     * @throws XtreamApiException
     */
    public function getVodStreams(?int $categoryId = null): array
    {
        return $this->streamClient->getVodStreams($categoryId);
    }

    /**
     * Get series
     *
     * @param int|null $categoryId Optional category filter
     * @return array
     * @throws XtreamApiException
     */
    public function getSeries(?int $categoryId = null): array
    {
        return $this->streamClient->getSeries($categoryId);
    }

    /**
     * Get series info
     *
     * @param int $seriesId Series ID
     * @return array
     * @throws XtreamApiException
     */
    public function getSeriesInfo(int $seriesId): array
    {
        return $this->streamClient->getSeriesInfo($seriesId);
    }

    /**
     * Get simple data table for a stream
     *
     * @param int $streamId Stream ID
     * @return array
     * @throws XtreamApiException
     */
    public function getSimpleDataTable(int $streamId): array
    {
        return $this->epgClient->getSimpleDataTable($streamId);
    }

    /**
     * Get short EPG for a stream
     *
     * @param int $streamId Stream ID
     * @param int $limit Number of entries
     * @return array
     * @throws XtreamApiException
     */
    public function getShortEpg(int $streamId, int $limit = 100): array
    {
        return $this->epgClient->getShortEpg($streamId, $limit);
    }

    /**
     * Get XMLTV data
     *
     * @param int|null $streamId Optional stream ID filter
     * @return string
     * @throws XtreamApiException
     */
    public function getXmltv(?int $streamId = null): string
    {
        return $this->epgClient->getXmltv($streamId);
    }

    /**
     * Get authenticator instance
     *
     * @return XtreamAuthenticator
     */
    public function getAuthenticator(): XtreamAuthenticator
    {
        return $this->authenticator;
    }

    /**
     * Get category client instance
     *
     * @return XtreamCategoryClient
     */
    public function getCategoryClient(): XtreamCategoryClient
    {
        return $this->categoryClient;
    }

    /**
     * Get stream client instance
     *
     * @return XtreamStreamClient
     */
    public function getStreamClient(): XtreamStreamClient
    {
        return $this->streamClient;
    }

    /**
     * Get EPG client instance
     *
     * @return XtreamEpgClient
     */
    public function getEpgClient(): XtreamEpgClient
    {
        return $this->epgClient;
    }

    /**
     * Test connection to source server
     *
     * @return bool
     */
    public function testConnection(): bool
    {
        try {
            $this->authenticate();
            return $this->authenticator->isAuthenticated();
        } catch (XtreamApiException $e) {
            return false;
        }
    }
}
