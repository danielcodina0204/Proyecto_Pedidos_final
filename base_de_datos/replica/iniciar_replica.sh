set -eu

if [ ! -s "$PGDATA/PG_VERSION" ]; then
    until pg_basebackup -h db-primary -U replicador -D "$PGDATA" -R -X stream; do
        echo "Esperando a la base primaria..."
        rm -rf "${PGDATA:?}"/*
        sleep 2
    done
    chmod 700 "$PGDATA"
fi

exec postgres
