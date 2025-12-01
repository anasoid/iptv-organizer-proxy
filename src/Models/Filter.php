<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * Filter Model
 *
 * Handles YAML-based stream filtering configuration
 *
 * @property int $id
 * @property string $name
 * @property string|null $description
 * @property string $filter_config
 * @property string $created_at
 * @property string $updated_at
 */
class Filter extends BaseModel
{
    protected string $table = 'filters';
    protected array $fillable = [
        'name',
        'description',
        'filter_config',
    ];

    private ?array $parsedConfig = null;

    /**
     * Parse YAML configuration
     *
     * @return array
     */
    public function parseYaml(): array
    {
        if ($this->parsedConfig !== null) {
            return $this->parsedConfig;
        }

        if (empty($this->filter_config)) {
            return ['rules' => [], 'favoris' => []];
        }

        // Note: This will use symfony/yaml when available (Task 9)
        // For now, we'll use a basic YAML parser
        try {
            $this->parsedConfig = $this->parseYamlBasic($this->filter_config);
            return $this->parsedConfig;
        } catch (\Exception $e) {
            throw new RuntimeException("Failed to parse YAML: " . $e->getMessage());
        }
    }

    /**
     * Basic YAML parser (will be replaced with symfony/yaml in Task 9)
     *
     * @param string $yaml
     * @return array
     */
    private function parseYamlBasic(string $yaml): array
    {
        // This is a placeholder for basic YAML parsing
        // In Task 9, this will use Symfony\Component\Yaml\Yaml::parse()

        $lines = explode("\n", $yaml);
        $result = ['rules' => [], 'favoris' => []];
        $currentSection = null;
        $currentItem = null;

        foreach ($lines as $line) {
            $line = trim($line);

            if (empty($line) || strpos($line, '#') === 0) {
                continue;
            }

            if ($line === 'rules:') {
                $currentSection = 'rules';
                continue;
            }

            if ($line === 'favoris:') {
                $currentSection = 'favoris';
                continue;
            }

            // This is a simplified parser - real implementation in Task 9
            if ($currentSection && strpos($line, '- ') === 0) {
                $result[$currentSection][] = trim(substr($line, 2));
            }
        }

        return $result;
    }

    /**
     * Validate YAML configuration
     *
     * @param string $yaml
     * @return bool
     */
    public static function validateYaml(string $yaml): bool
    {
        if (empty($yaml)) {
            return false;
        }

        // Basic validation - check for required sections
        if (strpos($yaml, 'rules:') === false) {
            throw new RuntimeException("YAML must contain 'rules:' section");
        }

        if (strpos($yaml, 'favoris:') === false) {
            throw new RuntimeException("YAML must contain 'favoris:' section");
        }

        // Try to parse it
        try {
            $instance = new static();
            $instance->filter_config = $yaml;
            $instance->parseYaml();
            return true;
        } catch (\Exception $e) {
            throw new RuntimeException("Invalid YAML: " . $e->getMessage());
        }
    }

    /**
     * Apply filter to streams (placeholder - full implementation in Task 9)
     *
     * @param array $streams
     * @return array Filtered streams
     */
    public function applyToStreams(array $streams): array
    {
        $config = $this->parseYaml();

        // This is a placeholder
        // Full implementation will be in Task 9 (FilterService)
        return $streams;
    }

    /**
     * Validate model data
     *
     * @return bool
     */
    protected function validate(): bool
    {
        // Name required
        if (empty($this->attributes['name'])) {
            throw new RuntimeException("Filter name is required");
        }

        // Filter config required and valid
        if (empty($this->attributes['filter_config'])) {
            throw new RuntimeException("Filter configuration is required");
        }

        // Validate YAML syntax
        static::validateYaml($this->attributes['filter_config']);

        return true;
    }

    /**
     * Get filter rules
     *
     * @return array
     */
    public function getRules(): array
    {
        $config = $this->parseYaml();
        return $config['rules'] ?? [];
    }

    /**
     * Get favoris configuration
     *
     * @return array
     */
    public function getFavoris(): array
    {
        $config = $this->parseYaml();
        return $config['favoris'] ?? [];
    }

    /**
     * Create filter from array
     *
     * @param string $name
     * @param string $description
     * @param array $config
     * @return static
     */
    public static function createFromArray(string $name, string $description, array $config): static
    {
        // Convert array to YAML string (simplified)
        $yaml = "rules:\n";
        foreach ($config['rules'] ?? [] as $rule) {
            $yaml .= "  - {$rule}\n";
        }
        $yaml .= "\nfavoris:\n";
        foreach ($config['favoris'] ?? [] as $favoris) {
            $yaml .= "  - {$favoris}\n";
        }

        $instance = new static();
        $instance->name = $name;
        $instance->description = $description;
        $instance->filter_config = $yaml;

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create filter");
        }

        return $instance;
    }
}
