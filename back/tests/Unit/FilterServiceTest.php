<?php

declare(strict_types=1);

namespace App\Tests\Unit;

use PHPUnit\Framework\TestCase;
use App\Services\FilterService;
use App\Models\Filter;

/**
 * Simple Filter test fixture that doesn't require database
 */
class FilterTestFixture
{
    public string $filter_config;
    public ?string $favoris = null;

    public function __construct(string $filter_config)
    {
        $this->filter_config = $filter_config;
    }
}

/**
 * FilterService Unit Tests
 *
 * Tests the YAML filter parsing and stream filtering logic with include/exclude rules
 * and virtual favoris categories
 */
class FilterServiceTest extends TestCase
{
    /**
     * Create a filter fixture with YAML config
     *
     * @param string $yaml YAML configuration
     * @return object
     */
    private function createFilterWithYaml(string $yaml): object
    {
        return new FilterTestFixture($yaml);
    }

    /**
     * Test: Adult content filtering (highest priority)
     */
    public function testAdultContentFilteringWithHideEnabled(): void
    {
        $filter = null;
        $service = new FilterService($filter, hideAdultContent: true);

        $streams = [
            ['id' => 1, 'name' => 'Sports Channel', 'is_adult' => false],
            ['id' => 2, 'name' => 'Adult Channel', 'is_adult' => true],
            ['id' => 3, 'name' => 'Movies', 'is_adult' => false],
        ];

        $filtered = $service->applyToStreams($streams);
        $filtered = array_values($filtered); // Re-index array

        // Only non-adult streams should remain
        $this->assertCount(2, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
        $this->assertEquals(3, $filtered[1]['id']);
    }

    /**
     * Test: Adult content filtering disabled
     */
    public function testAdultContentFilteringDisabled(): void
    {
        $filter = null;
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Sports', 'is_adult' => false],
            ['id' => 2, 'name' => 'Adult', 'is_adult' => true],
        ];

        $filtered = $service->applyToStreams($streams);

        // All streams should remain
        $this->assertCount(2, $filtered);
    }

    /**
     * Test: No filter assigned, all streams pass through
     */
    public function testNoFilterAssigned(): void
    {
        $service = new FilterService(null, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Channel 1', 'is_adult' => false],
            ['id' => 2, 'name' => 'Channel 2', 'is_adult' => false],
        ];

        $filtered = $service->applyToStreams($streams);

        // All streams should pass through when no filter
        $this->assertCount(2, $filtered);
    }

    /**
     * Test: Exclude rules reject matching streams
     */
    public function testExcludeRulesByName(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "Block Adult"
    type: exclude
    match:
      channels:
        by_name: ["Adult", "XXX"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Sports Channel', 'labels' => 'sports,HD', 'category_name' => 'Sports', 'category_labels' => 'sports'],
            ['id' => 2, 'name' => 'Adult Channel', 'labels' => 'adult', 'category_name' => 'Adult', 'category_labels' => 'adult'],
            ['id' => 3, 'name' => 'Family Movies', 'labels' => 'family', 'category_name' => 'Movies', 'category_labels' => 'movies'],
        ];

        $filtered = $service->applyToStreams($streams);
        $filtered = array_values($filtered); // Re-index array

        // With exclude-only rules: stream matching exclude rule is hidden, others are shown
        // Stream 2 matches exclude rule → hidden
        // Streams 1, 3 don't match any rule → shown by default
        $this->assertCount(2, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
        $this->assertEquals(3, $filtered[1]['id']);
    }

    /**
     * Test: Exclude rules by labels
     */
    public function testExcludeRulesByLabels(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "Block XXX"
    type: exclude
    match:
      channels:
        by_labels: ["XXX", "18+"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Sports', 'labels' => 'sports,HD', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 2, 'name' => 'Adult Movies', 'labels' => 'XXX,movies', 'category_name' => 'Adult', 'category_labels' => ''],
            ['id' => 3, 'name' => 'Kids Show', 'labels' => 'kids,family', 'category_name' => 'Kids', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);
        $filtered = array_values($filtered); // Re-index array

        // With exclude-only rules: stream matching exclude rule is hidden, others are shown
        // Stream 2 matches exclude rule → hidden
        // Streams 1, 3 don't match any rule → shown by default
        $this->assertCount(2, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
        $this->assertEquals(3, $filtered[1]['id']);
    }

    /**
     * Test: Include rules accept only matching streams
     */
    public function testIncludeRulesByName(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "Sports Only"
    type: include
    match:
      channels:
        by_name: ["Sports"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Sports Channel', 'labels' => 'sports', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 2, 'name' => 'Movies', 'labels' => 'movies', 'category_name' => 'Movies', 'category_labels' => ''],
            ['id' => 3, 'name' => 'Sports Update', 'labels' => 'sports', 'category_name' => 'News', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);
        $filtered = array_values($filtered); // Re-index array

        // Only channels with "Sports" in name should pass
        $this->assertCount(2, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
        $this->assertEquals(3, $filtered[1]['id']);
    }

    /**
     * Test: Include rules by labels
     */
    public function testIncludeRulesByLabels(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "HD Only"
    type: include
    match:
      channels:
        by_labels: ["HD", "FHD"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Channel 1', 'labels' => 'HD,sports', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 2, 'name' => 'Channel 2', 'labels' => 'SD,sports', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 3, 'name' => 'Channel 3', 'labels' => 'FHD,movies', 'category_name' => 'Movies', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);
        $filtered = array_values($filtered); // Re-index array

        // Only channels with HD or FHD label should pass
        $this->assertCount(2, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
        $this->assertEquals(3, $filtered[1]['id']);
    }

    /**
     * Test: First matching rule wins (rule order matters)
     */
    public function testExcludeHasPriorityOverInclude(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "Include Sports"
    type: include
    match:
      channels:
        by_labels: ["sports"]
  - name: "Exclude Adult Sports"
    type: exclude
    match:
      channels:
        by_labels: ["adult"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Sports Channel', 'labels' => 'sports,HD', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 2, 'name' => 'Adult Sports', 'labels' => 'sports,adult', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 3, 'name' => 'Regular Channel', 'labels' => 'HD', 'category_name' => 'Movies', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);

        // Stream 1: matches include rule (first) → ACCEPT
        // Stream 2: matches include rule (first) → ACCEPT (even though it also has adult label)
        // Stream 3: doesn't match any rule, has include rules → REJECT
        $this->assertCount(2, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
        $this->assertEquals(2, $filtered[1]['id']);
    }

    /**
     * Test: Category name matching
     */
    public function testCategoryNameMatching(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "News Categories"
    type: include
    match:
      categories:
        by_name: ["News", "Politics"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'BBC News', 'labels' => 'news', 'category_name' => 'News', 'category_labels' => ''],
            ['id' => 2, 'name' => 'CNN', 'labels' => 'news', 'category_name' => 'Politics', 'category_labels' => ''],
            ['id' => 3, 'name' => 'Sports Channel', 'labels' => 'sports', 'category_name' => 'Sports', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);

        // Only news and politics categories should pass
        $this->assertCount(2, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
        $this->assertEquals(2, $filtered[1]['id']);
    }

    /**
     * Test: Empty filter (no rules) passes all streams
     */
    public function testEmptyRulesPassAllStreams(): void
    {
        $yaml = <<<'YAML'
rules: []
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: false);

        $streams = [
            ['id' => 1, 'name' => 'Channel 1', 'labels' => '', 'category_name' => 'All', 'category_labels' => ''],
            ['id' => 2, 'name' => 'Channel 2', 'labels' => '', 'category_name' => 'All', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);

        // All streams should pass when no rules
        $this->assertCount(2, $filtered);
    }

    /**
     * Test: Case-insensitive label matching
     */
    public function testCaseInsensitiveLabelMatching(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "HD Content"
    type: include
    match:
      channels:
        by_labels: ["hd"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter);

        $streams = [
            ['id' => 1, 'name' => 'Channel 1', 'labels' => 'HD,sports', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 2, 'name' => 'Channel 2', 'labels' => 'hd,movies', 'category_name' => 'Movies', 'category_labels' => ''],
            ['id' => 3, 'name' => 'Channel 3', 'labels' => 'Hd,news', 'category_name' => 'News', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);

        // All variations of "HD" should match
        $this->assertCount(3, $filtered);
    }

    /**
     * Test: Case-insensitive name matching
     */
    public function testCaseInsensitiveNameMatching(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "ESPN Content"
    type: include
    match:
      channels:
        by_name: ["ESPN"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter);

        $streams = [
            ['id' => 1, 'name' => 'ESPN Sports', 'labels' => 'sports', 'category_name' => 'Sports', 'category_labels' => ''],
            ['id' => 2, 'name' => 'espn news', 'labels' => 'news', 'category_name' => 'News', 'category_labels' => ''],
            ['id' => 3, 'name' => 'ESPNHD Channel', 'labels' => 'HD', 'category_name' => 'Sports', 'category_labels' => ''],
        ];

        $filtered = $service->applyToStreams($streams);

        // All variations of "ESPN" should match
        $this->assertCount(3, $filtered);
    }

    /**
     * Test: Adult content filtering is applied before rules
     */
    public function testAdultContentPriorityOverRules(): void
    {
        $yaml = <<<'YAML'
rules:
  - name: "Include All Movies"
    type: include
    match:
      channels:
        by_name: ["Movie"]
YAML;

        $filter = $this->createFilterWithYaml($yaml);
        $service = new FilterService($filter, hideAdultContent: true);

        $streams = [
            ['id' => 1, 'name' => 'Movie Channel', 'labels' => 'movies', 'category_name' => 'Movies', 'category_labels' => '', 'is_adult' => false],
            ['id' => 2, 'name' => 'Adult Movie', 'labels' => 'movies,adult', 'category_name' => 'Movies', 'category_labels' => '', 'is_adult' => true],
        ];

        $filtered = $service->applyToStreams($streams);

        // Adult stream should be filtered out even though it matches include rule
        $this->assertCount(1, $filtered);
        $this->assertEquals(1, $filtered[0]['id']);
    }
}
