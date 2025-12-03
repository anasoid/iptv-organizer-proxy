<?php

// Router for PHP built-in web server
// Usage: php -S localhost:8080 -t public router.php

if (preg_match('/\.(?:js|css|gif|jpg|jpeg|png|ico|svg|woff|woff2|ttf|eot)$/', $_SERVER["REQUEST_URI"])) {
    return false;
}

require_once 'public/index.php';
