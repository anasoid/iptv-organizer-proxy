<?php

// Router for PHP built-in web server
// Usage: php -S localhost:8080 -t public router.php

if (preg_match('/\.(?:js|css|gif|jpg|jpeg|png|ico|svg|woff|woff2|ttf|eot)$/', $_SERVER["REQUEST_URI"])) {
    return false;
}

// Extract the requested file from REQUEST_URI
$requestUri = $_SERVER["REQUEST_URI"];

// List of PHP files in public directory that should be directly accessible
$phpFiles = [
    'player_api.php',
    'xmltv.php',
    'get.php',
    'debug.php',
];

// Check if request matches any of these PHP files
foreach ($phpFiles as $phpFile) {
    if (strpos($requestUri, '/' . $phpFile) === 0 || $requestUri === '/' . $phpFile) {
        require_once 'public/' . $phpFile;
        return true;
    }
}

// Default routing to Slim Framework for everything else
require_once 'public/index.php';
