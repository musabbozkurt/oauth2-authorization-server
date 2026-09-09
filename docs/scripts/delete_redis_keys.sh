#!/bin/bash

# Define your servers as "host:port:password:pattern"
SERVERS=(
  "host:6379:password:oauth2-authorization-server:authorizationLock:*"
)

for entry in "${SERVERS[@]}"; do
  # Parse IP, port, password, and pattern information
  IFS=':' read -r HOST PORT PASS PATTERN <<< "$entry"

  echo "Connecting to: IP $HOST:$PORT (Pattern: '$PATTERN')..."

  # Check if redis-cli is installed locally
  if command -v redis-cli &> /dev/null; then
    echo "Using local redis-cli..."

    redis-cli -h "$HOST" -p "$PORT" -a "$PASS" --no-auth-warning \
      --scan --pattern "$PATTERN" | while read -r key; do
        if [ -n "$key" ]; then
          key=$(echo "$key" | tr -d '\r')
          echo "Deleting: $key"
          redis-cli -h "$HOST" -p "$PORT" -a "$PASS" --no-auth-warning UNLINK "$key" > /dev/null
        fi
      done
  else
    echo "Local redis-cli not found. Falling back to Docker (redis:8.10.1)..."

    docker run --rm redis:8.10.1 redis-cli -h "$HOST" -p "$PORT" -a "$PASS" --no-auth-warning \
      --scan --pattern "$PATTERN" | while read -r key; do
        if [ -n "$key" ]; then
          key=$(echo "$key" | tr -d '\r')
          echo "Deleting: $key"
          docker run --rm redis:8.10.1 redis-cli -h "$HOST" -p "$PORT" -a "$PASS" --no-auth-warning UNLINK "$key" > /dev/null
        fi
      done
  fi

  echo "Process completed on IP $HOST:$PORT."
  echo "-----------------------------------"
done
