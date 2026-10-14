#!/usr/bin/env bash
# Carga scripts/pruebas/datos-sinteticos.sql en la base de datos de pruebas (servicio "database"
# de docker-compose.pruebas.yml). Requiere haber migrado antes a V17 (migrar-hasta-v17.sh).
# Se puede repetir: el SQL vacía antes las tablas que rellena.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$DIR/../.." && pwd)"
SQL="$DIR/datos-sinteticos.sql"

if [ ! -f "$SQL" ]; then
  echo "No existe $SQL; genéralo con: python scripts/pruebas/generar_datos_sinteticos.py" >&2
  exit 1
fi

docker compose -f "$RAIZ/docker-compose.pruebas.yml" exec -T database \
  mysql --default-character-set=utf8mb4 -utaemoi -ptaemoi-pruebas-local taemoi_pruebas < "$SQL"
echo "Datos sintéticos cargados en taemoi_pruebas (localhost:3308)."
