<?php

declare(strict_types=1);

namespace App\Controllers\Admin;

use Psr\Http\Message\ServerRequestInterface;
use Psr\Http\Message\ResponseInterface;
use App\Models\Category;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;
use App\Database\Connection;
use Symfony\Component\Yaml\Yaml;
use PDO;

class AccessControlController
{
    /**
     * Export access control settings for all streams and categories
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function export(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $queryParams = $request->getQueryParams();
            $sourceId = isset($queryParams['source_id']) ? (int) $queryParams['source_id'] : null;
            $format = $queryParams['format'] ?? 'json';

            if (!in_array($format, ['json', 'yaml'])) {
                return $this->jsonError($response, 'Invalid format. Use json or yaml', 400);
            }

            // Build data structure
            $data = [
                'exported_at' => date('Y-m-d H:i:s'),
                'version' => '1.0',
                'source_id' => $sourceId,
                'streams' => [],
                'categories' => []
            ];

            // Export streams
            $db = Connection::getConnection();
            if ($sourceId) {
                $sql = 'SELECT * FROM (
                    SELECT *, "live" as type FROM live_streams WHERE source_id = ? AND allow_deny IS NOT NULL
                    UNION ALL
                    SELECT *, "vod" as type FROM vod_streams WHERE source_id = ? AND allow_deny IS NOT NULL
                    UNION ALL
                    SELECT *, "series" as type FROM series WHERE source_id = ? AND allow_deny IS NOT NULL
                ) AS all_streams ORDER BY type, num';
                $stmt = $db->prepare($sql);
                $stmt->execute([$sourceId, $sourceId, $sourceId]);
            } else {
                $sql = 'SELECT * FROM (
                    SELECT *, "live" as type FROM live_streams WHERE allow_deny IS NOT NULL
                    UNION ALL
                    SELECT *, "vod" as type FROM vod_streams WHERE allow_deny IS NOT NULL
                    UNION ALL
                    SELECT *, "series" as type FROM series WHERE allow_deny IS NOT NULL
                ) AS all_streams ORDER BY source_id, type, num';
                $stmt = $db->prepare($sql);
                $stmt->execute();
            }
            $streams = $stmt->fetchAll(PDO::FETCH_ASSOC);

            foreach ($streams as $streamData) {
                $data['streams'][] = [
                    'stream_id' => $streamData['stream_id'],
                    'name' => $streamData['name'],
                    'type' => $streamData['type'],
                    'allow_deny' => $streamData['allow_deny'],
                ];
            }

            // Export categories
            $db = Connection::getConnection();
            if ($sourceId) {
                $sql = 'SELECT * FROM categories WHERE source_id = ? AND allow_deny IS NOT NULL ORDER BY category_type, num';
                $stmt = $db->prepare($sql);
                $stmt->execute([$sourceId]);
            } else {
                $sql = 'SELECT * FROM categories WHERE allow_deny IS NOT NULL ORDER BY source_id, category_type, num';
                $stmt = $db->prepare($sql);
                $stmt->execute();
            }
            $categories = $stmt->fetchAll(PDO::FETCH_ASSOC);

            foreach ($categories as $categoryData) {
                $data['categories'][] = [
                    'category_id' => $categoryData['category_id'],
                    'category_name' => $categoryData['category_name'],
                    'category_type' => $categoryData['category_type'],
                    'allow_deny' => $categoryData['allow_deny'],
                ];
            }

            // Convert to requested format
            if ($format === 'yaml') {
                $content = Yaml::dump($data, 10);
                $filename = 'access-control-' . ($sourceId ? "source-$sourceId-" : '') . date('Y-m-d-His') . '.yaml';
            } else {
                $content = json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
                $filename = 'access-control-' . ($sourceId ? "source-$sourceId-" : '') . date('Y-m-d-His') . '.json';
            }

            // Return file download
            $response->getBody()->write($content);
            return $response
                ->withHeader('Content-Type', $format === 'yaml' ? 'application/yaml' : 'application/json')
                ->withHeader('Content-Disposition', "attachment; filename=\"$filename\"");
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to export: ' . $e->getMessage(), 500);
        }
    }

    /**
     * Import access control settings for streams and categories
     *
     * @param ServerRequestInterface $request
     * @param ResponseInterface $response
     * @param array $args
     * @return ResponseInterface
     */
    public function import(ServerRequestInterface $request, ResponseInterface $response, array $args): ResponseInterface
    {
        try {
            $body = json_decode($request->getBody()->getContents(), true);

            if (!isset($body['streams']) || !isset($body['categories'])) {
                return $this->jsonError($response, 'Invalid import format. Missing streams or categories', 400);
            }

            $stats = [
                'streams_updated' => 0,
                'streams_failed' => 0,
                'categories_updated' => 0,
                'categories_failed' => 0,
                'errors' => []
            ];

            // Import streams
            $db = Connection::getConnection();
            foreach ($body['streams'] as $streamData) {
                try {
                    if (!isset($streamData['stream_id']) || !isset($streamData['type']) || !isset($streamData['allow_deny'])) {
                        $stats['streams_failed']++;
                        continue;
                    }

                    $streamId = $streamData['stream_id'];
                    $type = $streamData['type'];
                    $allowDeny = $streamData['allow_deny'];

                    // Determine table based on type
                    $table = match($type) {
                        'live' => 'live_streams',
                        'vod' => 'vod_streams',
                        'series' => 'series',
                        default => null
                    };

                    if (!$table) {
                        $stats['streams_failed']++;
                        $stats['errors'][] = "Unknown stream type: {$type}";
                        continue;
                    }

                    $sql = "UPDATE {$table} SET allow_deny = ? WHERE stream_id = ?";
                    $stmt = $db->prepare($sql);
                    $result = $stmt->execute([$allowDeny, $streamId]);

                    if ($stmt->rowCount() > 0) {
                        $stats['streams_updated']++;
                    } else {
                        $stats['streams_failed']++;
                        $stats['errors'][] = "Stream {$streamId} (type: {$type}) not found";
                    }
                } catch (\Throwable $e) {
                    $stats['streams_failed']++;
                    $stats['errors'][] = "Error updating stream {$streamData['stream_id']}: " . $e->getMessage();
                }
            }

            // Import categories
            foreach ($body['categories'] as $categoryData) {
                try {
                    if (!isset($categoryData['category_id']) || !isset($categoryData['allow_deny'])) {
                        $stats['categories_failed']++;
                        continue;
                    }

                    $categoryId = $categoryData['category_id'];
                    $allowDeny = $categoryData['allow_deny'];

                    $sql = "UPDATE categories SET allow_deny = ? WHERE category_id = ?";
                    $stmt = $db->prepare($sql);
                    $stmt->execute([$allowDeny, $categoryId]);

                    if ($stmt->rowCount() > 0) {
                        $stats['categories_updated']++;
                    } else {
                        $stats['categories_failed']++;
                        $stats['errors'][] = "Category {$categoryId} not found";
                    }
                } catch (\Throwable $e) {
                    $stats['categories_failed']++;
                    $stats['errors'][] = "Error updating category {$categoryData['category_id']}: " . $e->getMessage();
                }
            }

            return $this->jsonResponse($response, [
                'success' => true,
                'message' => 'Access control import completed',
                'data' => $stats
            ]);
        } catch (\Throwable $e) {
            return $this->jsonError($response, 'Failed to import: ' . $e->getMessage(), 400);
        }
    }

    /**
     * Return JSON response
     */
    private function jsonResponse(ResponseInterface $response, array $data, int $statusCode = 200): ResponseInterface
    {
        $response->getBody()->write(json_encode($data, JSON_UNESCAPED_SLASHES));
        return $response
            ->withStatus($statusCode)
            ->withHeader('Content-Type', 'application/json');
    }

    /**
     * Return JSON error response
     */
    private function jsonError(ResponseInterface $response, string $message, int $statusCode = 500): ResponseInterface
    {
        return $this->jsonResponse($response, [
            'success' => false,
            'message' => $message,
        ], $statusCode);
    }
}
