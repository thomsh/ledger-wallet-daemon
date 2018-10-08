#!/usr/bin/env bash
set -eux
exec ./bin/daemon -http.port=":$HTTP_PORT" -admin.port=":$ADMIN_PORT" $@
