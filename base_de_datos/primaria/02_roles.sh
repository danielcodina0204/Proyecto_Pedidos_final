
set -e

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v app_pass="$APP_DB_PASSWORD" -v repl_pass="$REPLICATION_PASSWORD" <<'SQL'
CREATE ROLE app LOGIN PASSWORD :'app_pass';
GRANT SELECT, INSERT, UPDATE, DELETE ON pedidos TO app;
CREATE ROLE replicador LOGIN REPLICATION PASSWORD :'repl_pass';
SQL

echo "host replication replicador all scram-sha-256" >> "$PGDATA/pg_hba.conf"
