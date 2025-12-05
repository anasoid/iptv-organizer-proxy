<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * Filter Model
 *
 * Handles YAML-based stream filtering configuration
 * Separates rules (filtering logic) from favoris (virtual categories)
 *
 * @property int $id
 * @property string $name
 * @property string|null $description
 * @property string $filter_config YAML rules section (include/exclude rules)
 * @property string|null $favoris YAML favoris section (virtual category definitions)
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
        'favoris',
    ];

    private ?array $parsedConfig = null;

    /**
     * Parse YAML configuration (rules and favoris)
     *
     * Returns array with both rules (from filter_config) and favoris (from separate field)
     *
     * @return array ['rules' => [...], 'favoris' => [...]]
     */
    public function parseYaml(): array
    {
        if ($this->parsedConfig !== null) {
            return $this->parsedConfig;
        }

        $rules = [];
        $favoris = [];

        // Parse rules from filter_config
        if (!empty($this->filter_config)) {
            try {
                $rulesConfig = $this->parseYamlBasic($this->filter_config);
                $rules = $rulesConfig['rules'] ?? [];
            } catch (\Exception $e) {
                throw new RuntimeException("Failed to parse rules YAML: " . $e->getMessage());
            }
        }

        // Parse favoris from separate favoris field
        if (!empty($this->favoris)) {
            try {
                $favorisConfig = $this->parseYamlBasic($this->favoris);
                $favoris = $favorisConfig['favoris'] ?? $favorisConfig;
            } catch (\Exception $e) {
                throw new RuntimeException("Failed to parse favoris YAML: " . $e->getMessage());
            }
        }

        $this->parsedConfig = ['rules' => $rules, 'favoris' => $favoris];
        return $this->parsedConfig;
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
     * Validate YAML rules configuration
     *
     * @param string $yaml Rules YAML
     * @return bool
     */
    public static function validateYaml(string $yaml): bool
    {
        if (empty($yaml)) {
            return false;
        }

        // Validate rules YAML - must contain 'rules:' section
        if (strpos($yaml, 'rules:') === false) {
            throw new RuntimeException("Rules YAML must contain 'rules:' section");
        }

        // Try to parse it and validate structure
        try {
            // Use Symfony YAML parser for proper validation
            $parser = new \Symfony\Component\Yaml\Parser();
            $config = $parser->parse($yaml);

            if (!isset($config['rules'])) {
                throw new RuntimeException("Rules YAML must contain 'rules:' section");
            }

            if (!is_array($config['rules'])) {
                throw new RuntimeException("'rules' section must be an array");
            }

            // Validate each rule structure
            foreach ($config['rules'] as $index => $rule) {
                if (!is_array($rule)) {
                    throw new RuntimeException("Rule " . ($index + 1) . " must be an object");
                }

                // Check required fields
                if (empty($rule['name'])) {
                    throw new RuntimeException("Rule " . ($index + 1) . " is missing 'name' field");
                }

                if (!isset($rule['type']) || !in_array($rule['type'], ['include', 'exclude'])) {
                    throw new RuntimeException("Rule '{$rule['name']}' has invalid 'type' (must be 'include' or 'exclude')");
                }

                if (empty($rule['match'])) {
                    throw new RuntimeException("Rule '{$rule['name']}' is missing 'match' section");
                }

                if (!is_array($rule['match'])) {
                    throw new RuntimeException("Rule '{$rule['name']}' match section must be an object");
                }

                // Validate match criteria keys
                $validCriteriaKeys = ['categories', 'channels', 'stream_type'];
                foreach ($rule['match'] as $key => $value) {
                    if (!in_array($key, $validCriteriaKeys)) {
                        throw new RuntimeException("Rule '{$rule['name']}' has invalid match criteria key '{$key}' (valid keys: " . implode(', ', $validCriteriaKeys) . ")");
                    }

                    // Validate categories/channels structure
                    if ($key === 'categories' || $key === 'channels') {
                        if (!is_array($value)) {
                            throw new RuntimeException("Rule '{$rule['name']}' {$key} must be an object");
                        }

                        // Check for valid keys
                        $validKeys = ['by_name', 'by_labels'];
                        foreach ($value as $subKey => $subValue) {
                            if (!in_array($subKey, $validKeys)) {
                                throw new RuntimeException("Rule '{$rule['name']}' has unexpected {$key} key '{$subKey}'. Valid keys: " . implode(', ', $validKeys));
                            }

                            if (!is_array($subValue)) {
                                throw new RuntimeException("Rule '{$rule['name']}' {$key}.{$subKey} must be an array");
                            }
                        }
                    }
                }
            }

            return true;
        } catch (\Exception $e) {
            throw new RuntimeException("Invalid rules YAML: " . $e->getMessage());
        }
    }

    /**
     * Validate YAML favoris configuration
     *
     * @param string $yaml Favoris YAML
     * @return bool
     */
    public static function validateFavorisYaml(string $yaml): bool
    {
        if (empty($yaml)) {
            return true; // Favoris is optional
        }

        // Try to parse it
        try {
            $instance = new static();
            $instance->favoris = $yaml;
            $favorisConfig = $instance->parseYamlBasic($yaml);
            // Favoris can be a list directly or under 'favoris:' key
            return true;
        } catch (\Exception $e) {
            throw new RuntimeException("Invalid favoris YAML: " . $e->getMessage());
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

        // Filter config (rules) is required
        if (empty($this->attributes['filter_config'])) {
            throw new RuntimeException("Filter rules configuration is required");
        }

        // Validate rules YAML syntax
        static::validateYaml($this->attributes['filter_config']);

        // Validate favoris YAML if provided
        if (!empty($this->attributes['favoris'])) {
            static::validateFavorisYaml($this->attributes['favoris']);
        }

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
