#!/bin/bash
# Script de inicialización de PostgreSQL.
# Crea bd_publica si no existe todavía.
# Se ejecuta automáticamente solo la PRIMERA VEZ que arranca el contenedor
# (cuando el volumen postgres_data está vacío).

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE bd_publica OWNER $POSTGRES_USER'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bd_publica')\gexec
EOSQL

echo ">> bd_publica: lista."
