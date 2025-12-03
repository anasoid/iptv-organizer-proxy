<?php
// Router for PHP built-in server
$requestUri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$publicPath = __DIR__;

// Remove leading slash and get the file path
$file = ltrim($requestUri, '/');

// If it's a direct file request (has file extension and exists), serve it
if ($file && strpos($file, '.') !== false) {
    $filePath = $publicPath . '/' . $file;
    if (file_exists($filePath) && is_file($filePath)) {
        return false; // Let the server serve the file
    }
}

// Route everything else to index.php (including non-file requests)
$_SERVER['REQUEST_URI'] = '/' . $file . (isset($_SERVER['QUERY_STRING']) && $_SERVER['QUERY_STRING'] ? '?' . $_SERVER['QUERY_STRING'] : '');
require_once $publicPath . '/index.php';
