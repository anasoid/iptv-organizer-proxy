<?php

// Router for PHP built-in web server
// Usage: php -S localhost:8080 -t public router.php

// Static file extensions
if (preg_match('/\.(?:js|css|gif|jpg|jpeg|png|ico|svg|woff|woff2|ttf|eot)$/', $_SERVER["REQUEST_URI"])) {
    return false;
}

// Extract the requested file from REQUEST_URI
$requestUri = $_SERVER["REQUEST_URI"];

// Handle /admin/ routes - serve from admin directory
if (strpos($requestUri, '/admin') === 0) {
    // Redirect /admin to /admin/
    if ($requestUri === '/admin') {
        header('Location: /admin/');
        return true;
    }

    // Get the requested path relative to /admin/
    $adminRequestPath = substr($requestUri, 7); // Remove '/admin/' prefix
    if ($adminRequestPath === '') {
        $adminRequestPath = 'index.html';
    }

    $adminFilePath = 'public/admin/' . $adminRequestPath;

    // If file exists, serve it
    if (file_exists($adminFilePath) && is_file($adminFilePath)) {
        return false;
    }

    // For SPA routing, serve index.html for any non-existent route
    if (file_exists('public/admin/index.html')) {
        require_once 'public/admin/index.html';
        return true;
    }
}

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
