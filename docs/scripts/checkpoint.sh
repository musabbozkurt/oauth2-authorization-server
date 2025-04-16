#!/usr/bin/env bash
set -e

case $(uname -m) in
    arm64)   url="https://cdn.azul.com/zulu/bin/zulu24.28.85-ca-crac-jdk24.0.0-linux_aarch64.tar.gz" ;;
    *)       url="https://cdn.azul.com/zulu/bin/zulu24.28.85-ca-crac-jdk24.0.0-linux_x64.tar.gz" ;;
esac

echo "Using CRaC enabled JDK $url"

./mvnw clean package

# Clean up existing resources
docker rm -f oauth2-authorization-server || true

# Create the network if it doesn't exist
docker network create oauth2-network || true

# Start dependencies using docker-compose
docker-compose up -d

# Wait for services to be ready
echo "Waiting for services to be ready..."
# sleep 10

docker build --no-cache --progress=plain -t oauth2-authorization-server:builder -f DockerfileForProjectCRaC --build-arg CRAC_JDK_URL=$url .

docker run -d --privileged --rm \
    --name=oauth2-authorization-server \
    --network=oauth2-network \
    --ulimit nofile=1024 \
    -p 9000:9000 \
    -v "$(pwd)"/target:/opt/mnt \
    -e FLAG=$1 \
    -e SPRING_DATASOURCE_URL=jdbc:mariadb://host.docker.internal:3306/oauth2_authorization_server \
    oauth2-authorization-server:builder

echo "Please wait during creating the checkpoint..."
sleep 10

# Commit the container changes using double quotes for the ENTRYPOINT change
docker commit --change="ENTRYPOINT [\"/opt/app/entrypoint.sh\"]" "$(docker ps -qf "name=oauth2-authorization-server")" oauth2-authorization-server:checkpoint

docker kill "$(docker ps -qf "name=oauth2-authorization-server")"