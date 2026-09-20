#!/bin/sh
set -e

# Cria um database por servico no mesmo cluster Postgres.
# Decisao documentada em docs/decisoes-tecnicas.md (ADR-001: 1 container, N databases).
# POSTGRES_MULTIPLE_DATABASES vem do docker-compose (ex: "solicitacao_db,notificacao_db,relatorio_db").
# O database padrao $POSTGRES_USER ja existe; os demais sao criados aqui.

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
  echo "Creating additional databases: $POSTGRES_MULTIPLE_DATABASES"
  # Converte virgula em espaco para iterar
  DBS=$(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' ')
  for db in $DBS; do
    # Remove espacos em branco
    db=$(echo "$db" | tr -d '[:space:]')
    if [ -n "$db" ]; then
      echo "Creating database '$db'..."
      psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
SELECT 'CREATE DATABASE "$db"' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
      echo "Database '$db' ready."
    fi
  done
fi
