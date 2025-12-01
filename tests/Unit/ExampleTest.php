<?php

declare(strict_types=1);

namespace App\Tests\Unit;

use PHPUnit\Framework\TestCase;

/**
 * Example test to ensure PHPUnit is working
 * This can be removed once real tests are added
 */
class ExampleTest extends TestCase
{
    public function testExample(): void
    {
        $this->assertTrue(true);
    }

    public function testPhpVersion(): void
    {
        $this->assertGreaterThanOrEqual('8.1', PHP_VERSION);
    }
}
