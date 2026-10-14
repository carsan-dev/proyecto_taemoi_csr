#!/usr/bin/env bash
# Aplica las migraciones de producción (V1..V17) a la base de datos de pruebas con la imagen
# oficial de Flyway. Las V18+ quedan pendientes a propósito.
# Uso: scripts/pruebas/migrar-hasta-v17.sh   (con el servicio "database" de docker-compose.pruebas.yml arrancado)
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIGRACIONES="$RAIZ/src-api/src/main/resources/db/migration"
# En Git Bash (Windows) Docker necesita la ruta en formato Windows y sin conversión de rutas.
if pwd -W >/dev/null 2>&1; then
  MIGRACIONES="$(cd "$MIGRACIONES" && pwd -W)"
  export MSYS_NO_PATHCONV=1
fi

RED="${RED_PRUEBAS:-taemoi-pruebas_pruebas}"
FLYWAY_IMAGEN="${FLYWAY_IMAGEN:-flyway/flyway:11.7.2}"
URL="jdbc:mysql://database:3306/taemoi_pruebas?allowPublicKeyRetrieval=true&useSSL=false"

flyway() {
  docker run --rm --network "$RED" \
    -v "$MIGRACIONES:/flyway/sql:ro" \
    "$FLYWAY_IMAGEN" \
    -url="$URL" -user=taemoi -password=taemoi-pruebas-local \
    -locations=filesystem:/flyway/sql -connectRetries=10 \
    "$@"
}

flyway -target=17 migrate
flyway info
