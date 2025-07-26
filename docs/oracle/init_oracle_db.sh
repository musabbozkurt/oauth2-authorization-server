#!/bin/bash

# echo "your-secret-key-from-oracle-container-registry" | docker login container-registry.oracle.com -u your-oracle-username --password-stdin

docker-compose up -d

# Wait for Oracle DB inside the container
docker exec oracle19 bash -c '
  echo "Waiting for ORCLPDB to open..."
  until echo "SELECT open_mode FROM v\$pdbs;" | sqlplus -s sys/${ORACLE_PWD}@ORCLPDB as sysdba | grep -q "READ WRITE"; do
    echo "Still waiting for ORCLPDB to open..."
    sleep 15
  done

  echo "PDB is open. Running schema script..."
  sqlplus sys/${ORACLE_PWD}@ORCLPDB as sysdba @/container-entrypoint-startdb.d/create_myapp_schema.sql
'
