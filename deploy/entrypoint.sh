#!/bin/sh
set -eu
if [ "$(id -u)" = 0 ]; then
    mkdir -p /var/data/menu-images
    chown app:app /var/data /var/data/menu-images
    exec gosu app "$@"
fi
exec "$@"
