#!/usr/bin/env bash
#
# restaurar_taemoi.sh - Restauración de copias de Taemoi hechas con backup_taemoi.sh (restic).
#
# Órdenes:
#   list [--type db|files|all]
#       Lista los snapshots del repositorio.
#   files SNAPSHOT --target DIR [--include RUTA]...
#       Restaura ficheros en DIR (vacío o inexistente). Nunca restaura en su sitio: DIR no
#       puede ser '/' ni estar dentro de /opt/taemoi ni de las carpetas de subidas.
#   db-verify [SNAPSHOT] [--keep]
#       Importa el volcado en un contenedor MySQL NUEVO y temporal (sin red, sin puertos)
#       y muestra tablas, filas y versión de Flyway. No toca producción. Con --keep deja el
#       contenedor en marcha para inspeccionarlo.
#   db-production SNAPSHOT [--confirm-production]
#       Sin --confirm-production solo imprime los pasos exactos (no cambia nada).
#       Con --confirm-production pide confirmación escrita en una terminal interactiva y
#       entonces: para el backend, guarda la BD actual en restic (etiqueta db-pre-restore;
#       si falla, rearranca el backend y aborta), recrea la BD, importa el volcado y arranca el backend.
#
# SNAPSHOT es un ID de restic (corto o largo) o "latest".
# Opción común: --config FICHERO (por defecto /etc/taemoi-backup.conf; es opcional, para poder
# restaurar en un equipo nuevo solo con RESTIC_REPOSITORY y RESTIC_PASSWORD_FILE en el entorno).
#
set -Eeuo pipefail
umask 077
# PATH completo aunque se lance desde cron o con un entorno mínimo.
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

readonly SCRIPT_NAME="restaurar_taemoi"
readonly MIN_RESTIC_VERSION="0.14"
readonly SCRATCH_DB="taemoi_restore_check"

CONFIG_FILE="/etc/taemoi-backup.conf"
SCRATCH_CONTAINER=""
KEEP_SCRATCH=0
LOCK_FD=""
TMP_DIR=""

set_defaults() {
  USB_MOUNTPOINT="${USB_MOUNTPOINT:-/mnt/backups_usb}"
  RESTIC_REPOSITORY="${RESTIC_REPOSITORY:-/mnt/backups_usb/restic-taemoi}"
  RESTIC_PASSWORD_FILE="${RESTIC_PASSWORD_FILE:-/root/.config/taemoi-backup/restic-password}"
  TAEMOI_DIR="/opt/taemoi"
  ENV_FILE=""
  IMAGENES_DIR=""
  DOCUMENTOS_DIR=""
  CERTBOT_CONF_DIR=""
  MYSQL_CONTAINER="taemoi-mysql-prod"
  BACKEND_CONTAINER="taemoi-backend-prod"
  MYSQL_IMAGE=""
  DB_DUMP_FILENAME="taemoi-db.sql"
  BACKUP_HOST="$(hostname -s 2>/dev/null || hostname)"
  LOCK_FILE="/run/taemoi-backup.lock"
  LOCK_WAIT_SECONDS=7200
  SCRATCH_READY_TIMEOUT=240
}

# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------
log() {
  local level=$1
  shift
  printf '%s [%s] %s: %s\n' "$(date '+%Y-%m-%d %H:%M:%S%z')" "$level" "$SCRIPT_NAME" "$*" >&2
}
log_info() { log INFO "$@"; }
log_warn() { log WARN "$@"; }
fail() {
  log ERROR "$*"
  exit 1
}

on_err() {
  log ERROR "Orden fallida en la línea $1 (código $2): $3"
}

cleanup() {
  local rc=$?
  trap - ERR
  if [[ -n $SCRATCH_CONTAINER ]]; then
    if ((KEEP_SCRATCH)); then
      log_info "El contenedor temporal ${SCRATCH_CONTAINER} sigue en marcha (--keep). Elimínelo al terminar: docker rm -f -v ${SCRATCH_CONTAINER}"
    else
      docker rm -f -v "$SCRATCH_CONTAINER" >/dev/null 2>&1 || log_warn "No se pudo eliminar ${SCRATCH_CONTAINER}: docker rm -f -v ${SCRATCH_CONTAINER}"
    fi
  fi
  if [[ -n $TMP_DIR && -d $TMP_DIR ]]; then
    rm -rf -- "$TMP_DIR"
  fi
  exit "$rc"
}

trap 'on_err "$LINENO" "$?" "$BASH_COMMAND"' ERR
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

usage() {
  cat <<'EOF'
Uso: restaurar_taemoi.sh [--config FICHERO] ORDEN [opciones]

  list [--type db|files|all]                     Lista snapshots (por defecto: all).
  files SNAPSHOT --target DIR [--include RUTA]   Restaura ficheros en DIR (nunca en su sitio).
  db-verify [SNAPSHOT] [--keep]                  Importa el volcado en un MySQL temporal y lo resume.
  db-production SNAPSHOT [--confirm-production]  Sin la opción: imprime los pasos. Con ella: los
                                                 ejecuta tras una confirmación escrita.

SNAPSHOT: ID de restic o "latest". Ejemplos:
  restaurar_taemoi.sh list --type db
  restaurar_taemoi.sh files latest --target /root/restauracion-$(date +%F)
  restaurar_taemoi.sh files latest --target /root/r --include /opt/taemoi/static_resources/documentos
  restaurar_taemoi.sh db-verify latest
  restaurar_taemoi.sh db-production 1a2b3c4d
EOF
}

check_private_file() {
  local file=$1 label=$2 owner mode
  [[ -f $file ]] || fail "${label}: no existe el fichero ${file}."
  owner="$(stat -c '%u' -- "$file")"
  mode="$(stat -c '%a' -- "$file")"
  [[ $owner == "0" ]] || fail "${label}: ${file} debe pertenecer a root."
  if (((8#$mode & 8#077) != 0)); then
    fail "${label}: ${file} tiene permisos ${mode}; deben ser 600 o 400."
  fi
}

load_config() {
  set_defaults
  if [[ -f $CONFIG_FILE ]]; then
    check_private_file "$CONFIG_FILE" "Configuración"
    # shellcheck source=/dev/null
    source "$CONFIG_FILE"
  else
    log_warn "No existe ${CONFIG_FILE}; se usan los valores por defecto y las variables de entorno."
  fi
  if [[ -n ${RESTIC_PASSWORD:-} || -n ${RESTIC_PASSWORD_COMMAND:-} ]]; then
    fail "Use RESTIC_PASSWORD_FILE para la contraseña de restic, no RESTIC_PASSWORD."
  fi
  unset RESTIC_PASSWORD RESTIC_PASSWORD_COMMAND RESTIC_REPOSITORY_FILE
  export RESTIC_REPOSITORY RESTIC_PASSWORD_FILE
  ENV_FILE=${ENV_FILE:-${TAEMOI_DIR}/.env}
  CERTBOT_CONF_DIR=${CERTBOT_CONF_DIR:-${TAEMOI_DIR}/certbot/conf}
  [[ $DB_DUMP_FILENAME =~ ^[A-Za-z0-9._-]+$ ]] || fail "DB_DUMP_FILENAME no es válido."
  [[ $LOCK_WAIT_SECONDS =~ ^[0-9]+$ && $SCRATCH_READY_TIMEOUT =~ ^[0-9]+$ ]] ||
    fail "LOCK_WAIT_SECONDS y SCRATCH_READY_TIMEOUT deben ser enteros."
}

require_commands() {
  local cmd missing=()
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
  done
  ((${#missing[@]} == 0)) || fail "Faltan órdenes necesarias: ${missing[*]}"
}

check_restic_version() {
  local version major minor min_major min_minor
  version="$(restic version 2>/dev/null | sed -n 's/^restic \([0-9][0-9]*\.[0-9][0-9]*\).*/\1/p' | head -n 1)"
  [[ $version =~ ^[0-9]+\.[0-9]+$ ]] || fail "No se pudo averiguar la versión de restic ($(command -v restic))."
  major=${version%%.*}
  minor=${version#*.}
  min_major=${MIN_RESTIC_VERSION%%.*}
  min_minor=${MIN_RESTIC_VERSION#*.}
  if ((major < min_major || (major == min_major && minor < min_minor))); then
    fail "restic ${version} ($(command -v restic)) es demasiado antiguo: se necesita ${MIN_RESTIC_VERSION} o posterior (ver README)."
  fi
}

open_repository() {
  require_commands restic
  check_restic_version
  check_private_file "$RESTIC_PASSWORD_FILE" "Contraseña de restic"
  if [[ $RESTIC_REPOSITORY == /* && ! -f ${RESTIC_REPOSITORY}/config ]]; then
    fail "No hay un repositorio restic en ${RESTIC_REPOSITORY}. ¿Está montado el disco USB?"
  fi
  restic cat config --no-lock >/dev/null || fail "No se puede abrir el repositorio restic (¿contraseña incorrecta?)."
}

env_value() {
  local key=$1 line value
  [[ -r $ENV_FILE ]] || fail "No se puede leer ${ENV_FILE}."
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$ENV_FILE" | tail -n 1)" || return 0
  value=${line#*=}
  value=${value%$'\r'}
  if [[ ${#value} -ge 2 && $value == \"*\" ]]; then
    value=${value:1:${#value}-2}
  elif [[ ${#value} -ge 2 && $value == \'*\' ]]; then
    value=${value:1:${#value}-2}
  else
    value="$(sed -E 's/[[:space:]]+#.*$//' <<<"$value")"
  fi
  printf '%s' "$value"
}

absolute_path() {
  local path=$1
  if [[ $path == /* ]]; then
    printf '%s' "$path"
  else
    printf '%s/%s' "$TAEMOI_DIR" "${path#./}"
  fi
}

acquire_lock() {
  exec {LOCK_FD}>>"$LOCK_FILE"
  log_info "Esperando el bloqueo ${LOCK_FILE} (evita que una copia programada se ejecute a la vez)..."
  flock -w "$LOCK_WAIT_SECONDS" "$LOCK_FD" || fail "No se obtuvo el bloqueo en ${LOCK_WAIT_SECONDS}s: hay una copia en curso."
}

# Devuelve el ID completo de un snapshot. Para "latest" filtra por las etiquetas indicadas.
resolve_snapshot() {
  local spec=$1 tags=$2 json id
  [[ $spec =~ ^(latest|[0-9a-f]{8,64})$ ]] || fail "SNAPSHOT no válido: '${spec}' (use un ID de restic o 'latest')."
  if [[ $spec == "latest" ]]; then
    json="$(restic snapshots --no-lock --json --tag "$tags" latest)" || fail "No se pudieron leer los snapshots."
  else
    json="$(restic snapshots --no-lock --json "$spec")" || fail "No se pudieron leer los snapshots."
  fi
  id="$(grep -o '"id":"[0-9a-f]\{64\}"' <<<"$json" | head -n 1 | cut -d'"' -f4 || true)"
  [[ -n $id ]] || fail "No se encontró el snapshot '${spec}' (etiquetas: ${tags})."
  printf '%s' "$id"
}

# Comprueba que el snapshot contiene un volcado completo (marca final de mysqldump).
verify_dump_snapshot() {
  local snapshot_id=$1 tail_text
  log_info "Comprobando que el snapshot ${snapshot_id:0:8} contiene un volcado completo..."
  if ! tail_text="$(restic dump --no-lock "$snapshot_id" "/${DB_DUMP_FILENAME}" | tail -c 1024)"; then
    fail "El snapshot ${snapshot_id:0:8} no contiene /${DB_DUMP_FILENAME} o no se puede leer."
  fi
  grep -q -- '-- Dump completed' <<<"$tail_text" || fail "El volcado del snapshot ${snapshot_id:0:8} está incompleto."
}

# Borra los volcados sin verificar (etiqueta db-pending). Se usa con el bloqueo tomado.
purge_pending_dumps() {
  local ids
  local -a id_list=()
  ids="$(restic snapshots --json --tag taemoi,db-pending | grep -o '"id":"[0-9a-f]\{64\}"' | cut -d'"' -f4 || true)"
  [[ -n $ids ]] || return 0
  mapfile -t id_list <<<"$ids"
  if restic forget "${id_list[@]}" >/dev/null 2>&1; then
    log_warn "Descartados ${#id_list[@]} snapshot(s) db-pending incompletos."
  else
    log_warn "No se pudieron descartar los snapshots db-pending: restic snapshots --tag db-pending"
  fi
}

# ---------------------------------------------------------------------------
# list
# ---------------------------------------------------------------------------
cmd_list() {
  local type="all"
  while (($# > 0)); do
    case $1 in
      --type)
        [[ $# -ge 2 ]] || fail "Falta el valor de --type."
        type=$2
        shift
        ;;
      *) fail "Opción desconocida para list: $1" ;;
    esac
    shift
  done
  open_repository
  case $type in
    all) restic snapshots --no-lock --tag taemoi ;;
    db) restic snapshots --no-lock --tag taemoi,db --tag taemoi,db-pre-restore ;;
    files) restic snapshots --no-lock --tag taemoi,files ;;
    *) fail "--type debe ser db, files o all." ;;
  esac
}

# ---------------------------------------------------------------------------
# files
# ---------------------------------------------------------------------------
path_is_within() {
  local path=$1 base=$2
  [[ -n $base ]] || return 1
  base="$(realpath -m -- "$base")"
  [[ $path == "$base" || $path == "${base}/"* ]]
}

cmd_files() {
  local spec="" target="" snapshot_id
  local -a include_args=()
  while (($# > 0)); do
    case $1 in
      --target)
        [[ $# -ge 2 ]] || fail "Falta el valor de --target."
        target=$2
        shift
        ;;
      --include)
        [[ $# -ge 2 ]] || fail "Falta el valor de --include."
        include_args+=(--include "$2")
        shift
        ;;
      -*) fail "Opción desconocida para files: $1" ;;
      *)
        [[ -z $spec ]] || fail "Solo se admite un SNAPSHOT."
        spec=$1
        ;;
    esac
    shift
  done
  [[ -n $spec ]] || fail "Indique el SNAPSHOT (ID o 'latest')."
  [[ -n $target ]] || fail "Indique --target DIR. Esta orden nunca restaura en su sitio."
  require_commands realpath

  target="$(realpath -m -- "$target")"
  [[ $target != "/" ]] || fail "--target no puede ser '/'."
  local imagenes="" documentos="" protected
  if [[ -r $ENV_FILE ]]; then
    imagenes="${IMAGENES_DIR:-$(env_value APP_IMAGENES_HOST_PATH)}"
    documentos="${DOCUMENTOS_DIR:-$(env_value APP_DOCUMENTOS_HOST_PATH)}"
  fi
  imagenes="$(absolute_path "${imagenes:-${IMAGENES_DIR:-${TAEMOI_DIR}/static_resources/imagenes}}")"
  documentos="$(absolute_path "${documentos:-${DOCUMENTOS_DIR:-${TAEMOI_DIR}/static_resources/documentos}}")"
  local -a protected_paths=("$TAEMOI_DIR" "$imagenes" "$documentos" "$CERTBOT_CONF_DIR" "$USB_MOUNTPOINT")
  if [[ $RESTIC_REPOSITORY == /* ]]; then
    protected_paths+=("$RESTIC_REPOSITORY")
  fi
  for protected in "${protected_paths[@]}"; do
    if path_is_within "$target" "$protected"; then
      fail "--target ${target} está dentro de ${protected}: no se restaura ahí (ni en su sitio ni en el disco de copias). Restaure en otra carpeta y copie a mano lo necesario."
    fi
  done
  if [[ -e $target ]]; then
    [[ -d $target ]] || fail "--target ${target} existe y no es una carpeta."
    [[ -z "$(ls -A -- "$target")" ]] || fail "--target ${target} no está vacía."
  fi

  open_repository
  snapshot_id="$(resolve_snapshot "$spec" "taemoi,files")"
  log_info "Restaurando el snapshot ${snapshot_id:0:8} en ${target}..."
  restic restore --no-lock "$snapshot_id" --target "$target" "${include_args[@]}"

  cat <<EOF

Restauración terminada en: ${target}
Las rutas conservan su ruta original debajo de esa carpeta, por ejemplo:
  ${target}${imagenes}
  ${target}${documentos}
  ${target}${ENV_FILE}
  ${target}${CERTBOT_CONF_DIR}

Para volver a ponerlos en producción, revise primero las diferencias y copie a mano, por ejemplo:
  diff -rq ${target}${documentos} ${documentos}
  rsync -a --dry-run ${target}${documentos}/ ${documentos}/    # quite --dry-run cuando esté seguro

ATENCIÓN: ${target}${ENV_FILE} contiene secretos (contraseñas y APP_CIFRADO_CLAVE).
Bórrelo cuando termine:  rm -rf ${target}
EOF
}

# ---------------------------------------------------------------------------
# db-verify
# ---------------------------------------------------------------------------
scratch_mysql() {
  docker exec -i "$SCRATCH_CONTAINER" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=tcp -h127.0.0.1 -uroot --default-character-set=utf8mb4 "$@"' \
    mysql "$@"
}

default_mysql_image() {
  if [[ -n $MYSQL_IMAGE ]]; then
    printf '%s' "$MYSQL_IMAGE"
    return 0
  fi
  docker inspect -f '{{.Config.Image}}' "$MYSQL_CONTAINER" 2>/dev/null || printf 'mysql:8.0'
}

cmd_db_verify() {
  local spec="latest" snapshot_id image scratch_password waited=0
  while (($# > 0)); do
    case $1 in
      --keep) KEEP_SCRATCH=1 ;;
      -*) fail "Opción desconocida para db-verify: $1" ;;
      *) spec=$1 ;;
    esac
    shift
  done
  require_commands docker
  open_repository
  snapshot_id="$(resolve_snapshot "$spec" "taemoi,db")"
  verify_dump_snapshot "$snapshot_id"

  image="$(default_mysql_image)"
  SCRATCH_CONTAINER="taemoi-restore-check-$(date +%Y%m%d%H%M%S)"
  scratch_password="$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  log_info "Creando el contenedor temporal ${SCRATCH_CONTAINER} (${image}, sin red ni puertos)..."
  MYSQL_ROOT_PASSWORD="$scratch_password" docker run -d --name "$SCRATCH_CONTAINER" \
    --network none --label taemoi.restore-check=1 -e MYSQL_ROOT_PASSWORD \
    "$image" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
  unset scratch_password

  log_info "Esperando a que MySQL esté listo (máximo ${SCRATCH_READY_TIMEOUT}s)..."
  until scratch_mysql -e 'SELECT 1' >/dev/null 2>&1; do
    [[ "$(docker inspect -f '{{.State.Running}}' "$SCRATCH_CONTAINER" 2>/dev/null)" == "true" ]] ||
      fail "El contenedor temporal se ha parado. Revise: docker logs ${SCRATCH_CONTAINER}"
    ((waited < SCRATCH_READY_TIMEOUT)) || fail "MySQL temporal no respondió en ${SCRATCH_READY_TIMEOUT}s."
    sleep 3
    waited=$((waited + 3))
  done

  scratch_mysql -e "CREATE DATABASE ${SCRATCH_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  log_info "Importando el volcado del snapshot ${snapshot_id:0:8} en la base temporal ${SCRATCH_DB}..."
  restic dump --no-lock "$snapshot_id" "/${DB_DUMP_FILENAME}" | scratch_mysql "$SCRATCH_DB" ||
    fail "La importación del volcado en el MySQL temporal ha fallado."

  local tables table count total=0 n=0
  tables="$(scratch_mysql -N -B -e "SELECT table_name FROM information_schema.tables WHERE table_schema='${SCRATCH_DB}' AND table_type='BASE TABLE' ORDER BY table_name")"
  printf '\nTablas restauradas (filas exactas):\n'
  # La lista se lee por el descriptor 3: 'docker exec -i' consumiría la entrada estándar del bucle.
  while IFS= read -r table <&3; do
    [[ -n $table ]] || continue
    count="$(scratch_mysql -N -B -e "SELECT COUNT(*) FROM \`${table//\`/}\`" "$SCRATCH_DB" </dev/null)"
    printf '  %-50s %12s\n' "$table" "$count"
    total=$((total + count))
    n=$((n + 1))
  done 3<<<"$tables"
  printf '  %-50s %12s\n' "TOTAL (${n} tablas)" "$total"
  ((n > 0)) || fail "El volcado no contiene ninguna tabla."

  if grep -qx 'flyway_schema_history' <<<"$tables"; then
    printf '\nÚltima migración de Flyway aplicada: %s\n' \
      "$(scratch_mysql -N -B -e "SELECT CONCAT('V', version, ' - ', description, ' (', installed_on, ')') FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1" "$SCRATCH_DB")"
  fi
  printf '\nVERIFICACIÓN CORRECTA: el snapshot %s se importa sin errores.\n' "${snapshot_id:0:8}"
  if ((KEEP_SCRATCH)); then
    # shellcheck disable=SC2016  # $MYSQL_ROOT_PASSWORD debe expandirse dentro del contenedor.
    printf 'Para inspeccionarlo:\n  docker exec -it %s sh -c '\''MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot %s'\''\n' \
      "$SCRATCH_CONTAINER" "$SCRATCH_DB"
  fi
}

# ---------------------------------------------------------------------------
# db-production
# ---------------------------------------------------------------------------
prod_mysql() {
  MYSQL_PWD="$PROD_DB_PASSWORD" docker exec -i -e MYSQL_PWD "$MYSQL_CONTAINER" \
    mysql -uroot --default-character-set=utf8mb4 "$@"
}

print_production_plan() {
  local snapshot_id=$1 db_name=$2
  cat <<EOF

=====================================================================
 RESTAURACIÓN DE LA BASE DE DATOS DE PRODUCCIÓN
=====================================================================
 Snapshot a restaurar : ${snapshot_id:0:8}
 Base de datos        : ${db_name}   (contenedor ${MYSQL_CONTAINER})
 Backend              : ${BACKEND_CONTAINER}

 Pasos exactos (MYSQL_PWD se carga desde MYSQL_ROOT_PASSWORD de ${ENV_FILE};
 la contraseña nunca va en la línea de órdenes):

 1. Parar el backend (nadie escribe en la BD desde aquí):
      docker stop ${BACKEND_CONTAINER}
      (equivale a: cd ${TAEMOI_DIR} && docker compose -f docker-compose.production.yml stop backend)

 2. Guardar la BD actual en restic, por si hay que deshacer (si falla, se vuelve a
    arrancar el backend y se aborta sin tocar la BD):
      MYSQL_PWD=... docker exec -e MYSQL_PWD ${MYSQL_CONTAINER} mysqldump -uroot \\
        --single-transaction --routines --triggers --events --hex-blob \\
        --default-character-set=utf8mb4 --set-gtid-purged=OFF --no-tablespaces ${db_name} \\
      | restic backup --stdin --stdin-filename ${DB_DUMP_FILENAME} --tag taemoi --tag db-pending
      restic tag --add db-pre-restore --remove db-pending <id>     (solo tras verificarlo)

 3. Recrear la base de datos (mismo juego de caracteres) e importar el volcado:
      MYSQL_PWD=... docker exec -i -e MYSQL_PWD ${MYSQL_CONTAINER} mysql -uroot \\
        -e "DROP DATABASE \`${db_name}\`; CREATE DATABASE \`${db_name}\` CHARACTER SET <actual> COLLATE <actual>;"
      restic dump ${snapshot_id:0:8} /${DB_DUMP_FILENAME} \\
      | MYSQL_PWD=... docker exec -i -e MYSQL_PWD ${MYSQL_CONTAINER} mysql -uroot --default-character-set=utf8mb4 ${db_name}
      (Los permisos del usuario de la aplicación sobre ${db_name}.* se conservan.)

 4. Arrancar el backend (Flyway aplicará las migraciones posteriores a la copia, si las hay):
      docker start ${BACKEND_CONTAINER}
      docker logs -f ${BACKEND_CONTAINER}

 Recomendado antes: restaurar_taemoi.sh db-verify ${snapshot_id:0:8}
=====================================================================

EOF
}

cmd_db_production() {
  local spec="" confirmed=0 snapshot_id db_name answer
  while (($# > 0)); do
    case $1 in
      --confirm-production) confirmed=1 ;;
      -*) fail "Opción desconocida para db-production: $1" ;;
      *)
        [[ -z $spec ]] || fail "Solo se admite un SNAPSHOT."
        spec=$1
        ;;
    esac
    shift
  done
  [[ -n $spec ]] || fail "Indique explícitamente el SNAPSHOT (ID o 'latest')."
  require_commands docker flock
  open_repository
  snapshot_id="$(resolve_snapshot "$spec" "taemoi,db")"
  db_name="$(env_value MYSQL_DATABASE)"
  [[ $db_name =~ ^[A-Za-z0-9_]+$ ]] || fail "MYSQL_DATABASE de ${ENV_FILE} no es un nombre válido."
  verify_dump_snapshot "$snapshot_id"
  print_production_plan "$snapshot_id" "$db_name"

  if ((!confirmed)); then
    log_info "No se ha modificado nada. Para ejecutar estos pasos añada --confirm-production (pedirá confirmación)."
    exit 2
  fi
  [[ -t 0 ]] || fail "La restauración en producción necesita una terminal interactiva para confirmar."
  read -r -p "Esto SUSTITUYE la base de datos '${db_name}' de producción. Escriba exactamente 'RESTAURAR ${db_name}': " answer
  [[ $answer == "RESTAURAR ${db_name}" ]] || fail "Confirmación incorrecta. No se ha modificado nada."

  PROD_DB_PASSWORD="$(env_value MYSQL_ROOT_PASSWORD)"
  [[ -n $PROD_DB_PASSWORD ]] || fail "MYSQL_ROOT_PASSWORD no está definido en ${ENV_FILE}."
  [[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null)" == "true" ]] ||
    fail "El contenedor ${MYSQL_CONTAINER} no está en marcha."
  acquire_lock

  # 1. Parar el backend antes de la copia previa: así la copia "para deshacer" incluye todo.
  local backend_exists=0
  if docker inspect "$BACKEND_CONTAINER" >/dev/null 2>&1; then
    backend_exists=1
    log_info "Paso 1/4: docker stop ${BACKEND_CONTAINER}"
    docker stop "$BACKEND_CONTAINER" >/dev/null
  else
    log_warn "Paso 1/4: no existe el contenedor ${BACKEND_CONTAINER}; se continúa sin pararlo."
  fi

  # 2. Copia previa para deshacer. Se guarda como db-pending y solo tras verificarla pasa a
  #    db-pre-restore. Si falla: se borra, se vuelve a arrancar el backend y se aborta.
  log_info "Paso 2/4: guardando la BD actual en restic (para poder deshacer)..."
  local out pre_id="" pre_ok=1 tail_text
  TMP_DIR="$(mktemp -d -t taemoi-restore.XXXXXX)"
  out="${TMP_DIR}/pre-restore.out"
  if ! MYSQL_PWD="$PROD_DB_PASSWORD" docker exec -e MYSQL_PWD "$MYSQL_CONTAINER" \
    mysqldump --user=root --single-transaction --quick --routines --triggers --events --hex-blob \
    --default-character-set=utf8mb4 --set-gtid-purged=OFF --no-tablespaces "$db_name" |
    restic backup --stdin --stdin-filename "$DB_DUMP_FILENAME" --host "$BACKUP_HOST" \
      --tag taemoi --tag db-pending >"$out" 2>&1; then
    pre_ok=0
  fi
  if ((pre_ok)); then
    pre_id="$(sed -n 's/^snapshot \([0-9a-f]\{8,\}\) saved$/\1/p' "$out" | tail -n 1)"
    [[ -n $pre_id ]] || pre_ok=0
  fi
  if ((pre_ok)); then
    if ! tail_text="$(restic dump "$pre_id" "/${DB_DUMP_FILENAME}" | tail -c 1024)" ||
      ! grep -q -- '-- Dump completed' <<<"$tail_text"; then
      pre_ok=0
    fi
  fi
  if ((pre_ok)); then
    restic tag --add db-pre-restore --remove db-pending "$pre_id" >/dev/null 2>&1 || pre_ok=0
  fi
  if ((!pre_ok)); then
    cat "$out" >&2 || true
    purge_pending_dumps
    if ((backend_exists)); then
      log_info "Volviendo a arrancar el backend: docker start ${BACKEND_CONTAINER}"
      docker start "$BACKEND_CONTAINER" >/dev/null || log_warn "No se pudo arrancar ${BACKEND_CONTAINER}: hágalo a mano."
    fi
    fail "No se pudo guardar la BD actual para poder deshacer. Se aborta: la BD de producción NO se ha modificado."
  fi
  # 'restic tag' crea un snapshot nuevo: se busca su ID definitivo.
  pre_id="$(resolve_snapshot latest "taemoi,db-pre-restore")"
  log_info "Copia previa guardada: snapshot ${pre_id:0:8}."

  # 3. Recrear la BD e importar.
  local charset collation
  read -r charset collation < <(prod_mysql -N -B -e \
    "SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${db_name}'") || true
  charset=${charset:-utf8mb4}
  collation=${collation:-utf8mb4_0900_ai_ci}
  [[ $charset =~ ^[A-Za-z0-9_]+$ && $collation =~ ^[A-Za-z0-9_]+$ ]] || fail "Juego de caracteres inesperado."
  log_info "Paso 3/4: recreando ${db_name} (${charset}/${collation}) e importando ${snapshot_id:0:8}..."
  if ! prod_mysql -e "DROP DATABASE IF EXISTS \`${db_name}\`; CREATE DATABASE \`${db_name}\` CHARACTER SET ${charset} COLLATE ${collation};" ||
    ! restic dump --no-lock "$snapshot_id" "/${DB_DUMP_FILENAME}" | prod_mysql "$db_name"; then
    cat >&2 <<EOF

La importación HA FALLADO. El backend se deja PARADO a propósito.
Para volver al estado anterior a esta restauración:
  restaurar_taemoi.sh db-production ${pre_id:0:8} --confirm-production
EOF
    fail "Importación fallida."
  fi

  # 4. Arrancar el backend.
  if ((backend_exists)); then
    log_info "Paso 4/4: docker start ${BACKEND_CONTAINER}"
    docker start "$BACKEND_CONTAINER" >/dev/null
  fi
  cat <<EOF

Restauración completada.
  - Revise el arranque del backend: docker logs -f ${BACKEND_CONTAINER}
  - Compruebe en la web que los datos son los esperados.
  - Para deshacer: restaurar_taemoi.sh db-production ${pre_id:0:8} --confirm-production
EOF
}

# ---------------------------------------------------------------------------
# Principal
# ---------------------------------------------------------------------------
main() {
  if (($# > 0)) && [[ $1 == "--config" ]]; then
    [[ $# -ge 2 ]] || fail "Falta el valor de --config."
    CONFIG_FILE=$2
    shift 2
  fi
  if (($# == 0)); then
    usage >&2
    exit 2
  fi
  local command=$1
  shift
  case $command in
    -h | --help | help)
      usage
      exit 0
      ;;
  esac
  ((EUID == 0)) || fail "Este script debe ejecutarse como root."
  load_config
  case $command in
    list) cmd_list "$@" ;;
    files) cmd_files "$@" ;;
    db-verify) cmd_db_verify "$@" ;;
    db-production) cmd_db_production "$@" ;;
    *)
      usage >&2
      fail "Orden desconocida: ${command}"
      ;;
  esac
}

main "$@"
