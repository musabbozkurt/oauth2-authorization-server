#!/usr/bin/env bash
set -e

# Clean up existing resources
docker rm -f oauth2-authorization-server || true

docker run --cap-add CHECKPOINT_RESTORE --cap-add SYS_ADMIN --rm -p 9000:9000 --name oauth2-authorization-server oauth2-authorization-server:checkpoint