<?php

declare(strict_types=1);

namespace App\Services\Xtream;

use App\Exceptions\XtreamApiException;
use App\Models\Source;
use GuzzleHttp\Client;
use Psr\Http\Message\ResponseInterface;
use Generator;

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
     * @throws XtreamApiException
     */
    public function __construct($config, ?Client $httpClient = null)
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
            $httpClient
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
     * Get XMLTV data (streamed to avoid memory overhead)
     *
     * @param int|null $streamId Optional stream ID filter
     * @return ResponseInterface PSR-7 response with streamed XMLTV data
     * @throws XtreamApiException
     */
    public function getXmltv(?int $streamId = null): ResponseInterface
    {
        return $this->epgClient->getXmltv($streamId);
    }

    /**
     * Stream live categories as generator (memory optimized)
     *
     * @return Generator<int, mixed>
     * @throws XtreamApiException
     */
    public function streamLiveCategories(): Generator
    {
        return $this->categoryClient->streamLiveCategories();
    }

    /**
     * Stream VOD categories as generator (memory optimized)
     *
     * @return Generator<int, mixed>
     * @throws XtreamApiException
     */
    public function streamVodCategories(): Generator
    {
        return $this->categoryClient->streamVodCategories();
    }

    /**
     * Stream series categories as generator (memory optimized)
     *
     * @return Generator<int, mixed>
     * @throws XtreamApiException
     */
    public function streamSeriesCategories(): Generator
    {
        return $this->categoryClient->streamSeriesCategories();
    }

    /**
     * Stream live streams as generator (memory optimized)
     *
     * @param int|null $categoryId Optional category filter
     * @return Generator<int, mixed>
     * @throws XtreamApiException
     */
    public function streamLiveStreams(?int $categoryId = null): Generator
    {
        return $this->streamClient->streamLiveStreams($categoryId);
    }

    /**
     * Stream VOD streams as generator (memory optimized)
     *
     * @param int|null $categoryId Optional category filter
     * @return Generator<int, mixed>
     * @throws XtreamApiException
     */
    public function streamVodStreams(?int $categoryId = null): Generator
    {
        return $this->streamClient->streamVodStreams($categoryId);
    }

    /**
     * Stream series as generator (memory optimized)
     *
     * @param int|null $categoryId Optional category filter
     * @return Generator<int, mixed>
     * @throws XtreamApiException
     */
    public function streamSeries(?int $categoryId = null): Generator
    {
        return $this->streamClient->streamSeries($categoryId);
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
