#!/usr/bin/env bash
# Devuelve la BD de pruebas al estado V17 guardado en scripts/pruebas/backup-v17.sql.
# Para el backend antes (si no, volvería a migrar a V18+ al reconectar) y lo deja parado.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$DIR/../.." && pwd)"
COMPOSE=(docker compose -f "$RAIZ/docker-compose.pruebas.yml")
[ -f "$DIR/backup-v17.sql" ] || { echo "Falta $DIR/backup-v17.sql (ver respaldar-v17.sh)" >&2; exit 1; }
"${COMPOSE[@]}" stop backend >/dev/null 2>&1 || true
"${COMPOSE[@]}" exec -T database mysql -uroot -proot-pruebas-local \
  -e "DROP DATABASE IF EXISTS taemoi_pruebas; CREATE DATABASE taemoi_pruebas CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON taemoi_pruebas.* TO 'taemoi'@'%';"
"${COMPOSE[@]}" exec -T database mysql --default-character-set=utf8mb4 -uroot -proot-pruebas-local < "$DIR/backup-v17.sql"
echo "BD restaurada a V17. Arranca el backend con: docker compose -f docker-compose.pruebas.yml up -d backend"
