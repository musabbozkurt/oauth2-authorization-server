#!/bin/bash

# Define your targets. You can use two formats:
# 1. Single Server: "IP:PORT:PASSWORD:PATTERN"
# 2. IP Range:     "START_IP - END_IP:PORT:PASSWORD:PATTERN"
SERVERS=(
  "192.168.1.50:6379:mypassword1:cache:*"
  "192.168.1.60 - 192.168.1.65:6379:mypassword2:temp_*"
)

# Helper function to convert IP to integer
ip2long() {
  local ip=$1
  local a b c d
  IFS=. read -r a b c d <<< "$ip"
  echo "$(( (a << 24) + (b << 16) + (c << 8) + d ))"
}

# Helper function to convert integer back to IP
long2ip() {
  local long=$1
  echo "$(( (long >> 24) & 255 )).$(( (long >> 16) & 255 )).$(( (long >> 8) & 255 )).$(( long & 255 ))"
}

# Process each target entry
for entry in "${SERVERS[@]}"; do
  # Split the main entry into Host/Range part and the rest (Port:Pass:Pattern)
  TARGET_PART=$(echo "$entry" | cut -d':' -f1)
  REST_PART=$(echo "$entry" | cut -d':' -f2-)

  IFS=':' read -r PORT PASS PATTERN <<< "$REST_PART"

  # Array to hold IPs to process for the current entry
  CURRENT_IPS=()

  # Check if the target part contains an IP range (contains " - ")
  if [[ "$TARGET_PART" == *" - "* ]]; then
    START_IP=$(echo "$TARGET_PART" | awk -F ' - ' '{print $1}' | xargs)
    END_IP=$(echo "$TARGET_PART" | awk -F ' - ' '{print $2}' | xargs)

    start_long=$(ip2long "$START_IP")
    end_long=$(ip2long "$END_IP")

    # Generate all IPs in the range
    for ((i=start_long; i<=end_long; i++)); do
      CURRENT_IPS+=($(long2ip "$i"))
    done
  else
    # Single IP
    CURRENT_IPS+=($(echo "$TARGET_PART" | xargs))
  fi

  # Loop through all resolved IPs for this entry
  for HOST in "${CURRENT_IPS[@]}"; do
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
done
