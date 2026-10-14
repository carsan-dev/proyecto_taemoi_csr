#!/usr/bin/env bash
# Vuelca la BD de pruebas (estado V17 + datos sintéticos) en scripts/pruebas/backup-v17.sql.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$DIR/../.." && pwd)"
docker compose -f "$RAIZ/docker-compose.pruebas.yml" exec -T database \
  mysqldump --default-character-set=utf8mb4 --single-transaction --routines --triggers --hex-blob \
  -uroot -proot-pruebas-local --databases taemoi_pruebas > "$DIR/backup-v17.sql"
echo "Copia en $DIR/backup-v17.sql"
