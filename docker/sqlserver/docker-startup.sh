#!/usr/bin/env bash
set -e

sleep 2
/opt/mssql/bin/sqlservr &

sleep 2

echo "Waiting for SQL Server to start..."
for i in {1..60}; do
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "${MSSQL_SA_PASSWORD}" -C -Q "SELECT 1" && break
    echo "Still waiting..."
    sleep 2
done

sqlcmd -S localhost -U sa -P "${MSSQL_SA_PASSWORD}" -e -C -i /data/create_metadata_simple.sql

echo
echo "Data loaded in"
echo "Starting T-SQL prompt"

sqlcmd -S localhost -U sa -P "${MSSQL_SA_PASSWORD}" -e -C

