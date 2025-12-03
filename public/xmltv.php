<?php

declare(strict_types=1);

use Dotenv\Dotenv;
use App\Models\Client;
use App\Models\Source;

require __DIR__ . '/../vendor/autoload.php';

// Load environment variables
$dotenv = Dotenv::createImmutable(__DIR__ . '/..');
$dotenv->load();

// Get credentials from query parameters
$username = $_GET['username'] ?? null;
$password = $_GET['password'] ?? null;

if (!$username || !$password) {
    http_response_code(401);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

// Authenticate client
$clients = Client::findAll(['username' => $username, 'password' => $password]);
if (empty($clients)) {
    http_response_code(401);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

$client = $clients[0];

// Check if client has source
if (!$client->source_id) {
    http_response_code(400);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

// Load source
$source = Source::find($client->source_id);
if (!$source) {
    http_response_code(404);
    header('Content-Type: application/xml');
    echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
    echo '<tv></tv>';
    exit;
}

// Generate XMLTV EPG data
header('Content-Type: application/xml; charset=utf-8');
header('Content-Disposition: attachment; filename="epg.xml"');

echo '<?xml version="1.0" encoding="UTF-8"?>' . "\n";
echo '<!DOCTYPE tv SYSTEM "xmltv.dtd">' . "\n";
echo '<tv>' . "\n";

// Generate sample EPG data
// In a real implementation, this would fetch actual EPG data from the database
echo '  <channel id="1">' . "\n";
echo '    <display-name>Sample Channel 1</display-name>' . "\n";
echo '  </channel>' . "\n";

echo '  <programme start="' . date('YmdHis O') . '" stop="' . date('YmdHis O', time() + 3600) . '" channel="1">' . "\n";
echo '    <title lang="en">Sample Program 1</title>' . "\n";
echo '    <desc lang="en">This is a sample program description</desc>' . "\n";
echo '  </programme>' . "\n";

echo '  <programme start="' . date('YmdHis O', time() + 3600) . '" stop="' . date('YmdHis O', time() + 7200) . '" channel="1">' . "\n";
echo '    <title lang="en">Sample Program 2</title>' . "\n";
echo '    <desc lang="en">This is another sample program description</desc>' . "\n";
echo '  </programme>' . "\n";

echo '</tv>' . "\n";
