<?php

declare(strict_types=1);

namespace App\Models;

use App\Database\Connection;
use PDO;
use PDOException;
use RuntimeException;

/**
 * Base Model Class
 *
 * Provides common CRUD operations and utilities for all models
 */
abstract class BaseModel
{
    protected PDO $db;
    protected string $table = '';
    protected string $primaryKey = 'id';
    protected array $fillable = [];
    protected array $dates = ['created_at', 'updated_at'];
    protected array $attributes = [];
    protected bool $exists = false;

    public function __construct()
    {
        $this->db = Connection::getConnection();
    }

    /**
     * Find record by ID
     *
     * @param int $id
     * @return static|null
     */
    public static function find(int $id): ?static
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table} WHERE {$instance->primaryKey} = ? LIMIT 1"
        );
        $stmt->execute([$id]);
        $data = $stmt->fetch();

        if (!$data) {
            return null;
        }

        $instance->attributes = $data;
        $instance->exists = true;

        return $instance;
    }

    /**
     * Find all records with optional conditions
     *
     * @param array $conditions WHERE conditions as key => value pairs
     * @param array $orderBy ['column' => 'ASC|DESC']
     * @param int|null $limit
     * @param int $offset
     * @return array
     */
    public static function findAll(
        array $conditions = [],
        array $orderBy = [],
        ?int $limit = null,
        int $offset = 0
    ): array {
        $instance = new static();
        $sql = "SELECT * FROM {$instance->table}";

        // Build WHERE clause
        $whereParams = [];
        if (!empty($conditions)) {
            $whereClauses = [];
            foreach ($conditions as $column => $value) {
                $whereClauses[] = "{$column} = ?";
                $whereParams[] = $value;
            }
            $sql .= " WHERE " . implode(' AND ', $whereClauses);
        }

        // Build ORDER BY clause
        if (!empty($orderBy)) {
            $orderClauses = [];
            foreach ($orderBy as $column => $direction) {
                $orderClauses[] = "{$column} {$direction}";
            }
            $sql .= " ORDER BY " . implode(', ', $orderClauses);
        }

        // Add LIMIT and OFFSET
        if ($limit !== null) {
            $sql .= " LIMIT {$limit}";
            if ($offset > 0) {
                $sql .= " OFFSET {$offset}";
            }
        }

        $stmt = $instance->db->prepare($sql);
        $stmt->execute($whereParams);

        $results = [];
        while ($data = $stmt->fetch()) {
            $model = new static();
            $model->attributes = $data;
            $model->exists = true;
            $results[] = $model;
        }

        return $results;
    }

    /**
     * Save model (insert or update)
     *
     * @return bool
     */
    public function save(): bool
    {
        if (!$this->validate()) {
            return false;
        }

        if ($this->exists) {
            return $this->update();
        }

        return $this->insert();
    }

    /**
     * Insert new record
     *
     * @return bool
     */
    protected function insert(): bool
    {
        $data = $this->getFillableData();

        // Add timestamps
        if (in_array('created_at', $this->dates)) {
            $data['created_at'] = $this->getCurrentTimestamp();
        }
        if (in_array('updated_at', $this->dates)) {
            $data['updated_at'] = $this->getCurrentTimestamp();
        }

        $columns = array_keys($data);
        $placeholders = array_fill(0, count($columns), '?');

        $sql = "INSERT INTO {$this->table} (" . implode(', ', $columns) . ")
                VALUES (" . implode(', ', $placeholders) . ")";

        try {
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute(array_values($data));

            if ($result) {
                $this->attributes[$this->primaryKey] = (int) $this->db->lastInsertId();
                $this->attributes = array_merge($this->attributes, $data);
                $this->exists = true;
            }

            return $result;
        } catch (PDOException $e) {
            throw new RuntimeException("Insert failed: " . $e->getMessage(), 0, $e);
        }
    }

    /**
     * Update existing record
     *
     * @return bool
     */
    protected function update(): bool
    {
        $data = $this->getFillableData();

        // Add updated_at timestamp
        if (in_array('updated_at', $this->dates)) {
            $data['updated_at'] = $this->getCurrentTimestamp();
        }

        $setClauses = [];
        foreach (array_keys($data) as $column) {
            $setClauses[] = "{$column} = ?";
        }

        $sql = "UPDATE {$this->table} SET " . implode(', ', $setClauses) .
               " WHERE {$this->primaryKey} = ?";

        try {
            $stmt = $this->db->prepare($sql);
            $params = array_values($data);
            $params[] = $this->attributes[$this->primaryKey];

            $result = $stmt->execute($params);

            if ($result) {
                $this->attributes = array_merge($this->attributes, $data);
            }

            return $result;
        } catch (PDOException $e) {
            throw new RuntimeException("Update failed: " . $e->getMessage(), 0, $e);
        }
    }

    /**
     * Delete record
     *
     * @return bool
     */
    public function delete(): bool
    {
        if (!$this->exists) {
            return false;
        }

        $sql = "DELETE FROM {$this->table} WHERE {$this->primaryKey} = ?";

        try {
            $stmt = $this->db->prepare($sql);
            $result = $stmt->execute([$this->attributes[$this->primaryKey]]);

            if ($result) {
                $this->exists = false;
            }

            return $result;
        } catch (PDOException $e) {
            throw new RuntimeException("Delete failed: " . $e->getMessage(), 0, $e);
        }
    }

    /**
     * Validate model data (to be overridden in child classes)
     *
     * @return bool
     */
    protected function validate(): bool
    {
        return true;
    }

    /**
     * Get fillable data from attributes
     *
     * @return array
     */
    protected function getFillableData(): array
    {
        if (empty($this->fillable)) {
            return $this->attributes;
        }

        return array_intersect_key(
            $this->attributes,
            array_flip($this->fillable)
        );
    }

    /**
     * Get current timestamp based on database type
     *
     * @return string
     */
    protected function getCurrentTimestamp(): string
    {
        $dbType = $_ENV['DB_TYPE'] ?? 'mysql';

        if ($dbType === 'sqlite') {
            return date('Y-m-d H:i:s');
        }

        // MySQL uses CURRENT_TIMESTAMP in the database, but for consistency we return formatted time
        return date('Y-m-d H:i:s');
    }

    /**
     * Magic getter for attributes
     *
     * @param string $name
     * @return mixed
     */
    public function __get(string $name)
    {
        return $this->attributes[$name] ?? null;
    }

    /**
     * Magic setter for attributes
     *
     * @param string $name
     * @param mixed $value
     */
    public function __set(string $name, $value): void
    {
        $this->attributes[$name] = $value;
    }

    /**
     * Magic isset for attributes
     *
     * @param string $name
     * @return bool
     */
    public function __isset(string $name): bool
    {
        return isset($this->attributes[$name]);
    }

    /**
     * Get all attributes
     *
     * @return array
     */
    public function toArray(): array
    {
        return $this->attributes;
    }

    /**
     * Get attribute value
     *
     * @param string $key
     * @param mixed $default
     * @return mixed
     */
    public function getAttribute(string $key, $default = null)
    {
        return $this->attributes[$key] ?? $default;
    }

    /**
     * Set attribute value
     *
     * @param string $key
     * @param mixed $value
     * @return $this
     */
    public function setAttribute(string $key, $value): static
    {
        $this->attributes[$key] = $value;
        return $this;
    }
}
