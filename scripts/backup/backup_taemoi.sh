#!/usr/bin/env bash
#
# backup_taemoi.sh - Copia de seguridad de producción de Taemoi con restic.
#
# Cada ejecución crea dos snapshots en el repositorio restic (cifrado y deduplicado):
#   1. "db"    -> volcado completo de MySQL (mysqldump) enviado a restic por stdin,
#                 sin pasar por disco. Después se comprueba: código de salida 0 de
#                 mysqldump y de restic, tamaño mínimo y marca final "-- Dump completed"
#                 leída de vuelta desde el repositorio. Se guarda como "db-pending" y solo pasa a
#                 "db" si todo es correcto; si algo falla se borra y la ejecución termina en FALLO.
#   2. "files" -> imágenes, documentos, /opt/taemoi/.env y certbot/conf (y EXTRA_PATHS).
# Si todo ha ido bien aplica la retención (restic forget --prune) y revisa el espacio libre.
#
# Modos:
#   backup_taemoi.sh                copia + retención (uso normal desde cron)
#   backup_taemoi.sh --check        solo comprobación de integridad (restic check --read-data-subset)
#   backup_taemoi.sh --preflight    solo comprobaciones previas; no escribe nada ni envía avisos
#   Opción común: --config FICHERO  (por defecto /etc/taemoi-backup.conf)
#
# Cualquier fallo termina con código distinto de 0, deja una línea "FALLO" en el log y
# dispara los avisos configurados (HEALTHCHECK_URL, NTFY_URL, ALERT_EMAIL).
# No existe copia alternativa en el disco interno: si el USB no está montado, se falla
# (una copia en el mismo disco que los datos no protege de su avería y oculta el problema).
#
set -Eeuo pipefail
umask 077
# cron arranca con PATH=/usr/bin:/bin; se fija uno completo (restic oficial suele ir en /usr/local/bin).
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

readonly SCRIPT_NAME="backup_taemoi"
readonly SCRIPT_VERSION="1.1.0"
readonly MIN_RESTIC_VERSION="0.14"

CONFIG_FILE="/etc/taemoi-backup.conf"
MODE="backup"
CURRENT_STEP="inicio"
ALERTS_ENABLED=1
TMP_DIR=""
LOCK_FD=""
FAILED_STEPS=()
KEEP_ARGS=()

# ---------------------------------------------------------------------------
# Valores por defecto. Se pueden cambiar en el fichero de configuración.
# ---------------------------------------------------------------------------
set_defaults() {
  USB_MOUNTPOINT="/mnt/backups_usb"
  RESTIC_REPOSITORY="/mnt/backups_usb/restic-taemoi"
  RESTIC_PASSWORD_FILE="/root/.config/taemoi-backup/restic-password"
  TAEMOI_DIR="/opt/taemoi"
  ENV_FILE=""
  IMAGENES_DIR=""
  DOCUMENTOS_DIR=""
  CERTBOT_CONF_DIR=""
  EXTRA_PATHS=()
  MYSQL_CONTAINER="taemoi-mysql-prod"
  DB_DUMP_FILENAME="taemoi-db.sql"
  MIN_DUMP_BYTES=20480
  BACKUP_HOST="$(hostname -s 2>/dev/null || hostname)"
  KEEP_LAST=""
  KEEP_HOURLY=42
  KEEP_DAILY=30
  KEEP_WEEKLY=12
  KEEP_MONTHLY=12
  KEEP_YEARLY=""
  PRUNE_MAX_UNUSED="5%"
  CHECK_READ_DATA_SUBSET="10%"
  # En /run (solo escribible por root), no en /run/lock (escribible por todos).
  LOCK_FILE="/run/taemoi-backup.lock"
  LOCK_WAIT_SECONDS=7200
  DISK_USAGE_ALERT_PERCENT=85
  HEALTHCHECK_URL=""
  HEALTHCHECK_CHECK_URL=""
  NTFY_URL=""
  ALERT_EMAIL=""
  HTTP_TIMEOUT_SECONDS=15
}

# ---------------------------------------------------------------------------
# Registro
# ---------------------------------------------------------------------------
timestamp() { date '+%Y-%m-%d %H:%M:%S%z'; }

log() {
  local level=$1 line
  shift
  line="$(timestamp) [${level}] ${SCRIPT_NAME}: $*"
  if [[ $level == "ERROR" ]]; then
    printf '%s\n' "$line" >&2
  else
    printf '%s\n' "$line"
  fi
}
log_info() { log INFO "$@"; }
log_warn() { log WARN "$@"; }
log_error() { log ERROR "$@"; }

# Vuelca al registro, línea a línea, la salida guardada de una orden.
log_output() {
  local file=$1 prefix=$2 line
  [[ -s $file ]] || return 0
  while IFS= read -r line || [[ -n $line ]]; do
    log INFO "  ${prefix} | ${line}"
  done <"$file"
}

syslog_msg() {
  if command -v logger >/dev/null 2>&1; then
    logger -t "$SCRIPT_NAME" -p "user.$1" -- "$2" || true
  fi
}

fail() {
  log_error "$*"
  exit 1
}

# Ejecuta una orden guardando su salida (stdout+stderr) y la vuelca al registro.
# Devuelve el código de salida de la orden.
run_logged() {
  local label=$1 out rc=0
  shift
  out="$(mktemp -p "$TMP_DIR" cmd.XXXXXX)"
  "$@" >"$out" 2>&1 || rc=$?
  log_output "$out" "$label"
  rm -f -- "$out"
  return "$rc"
}

# ---------------------------------------------------------------------------
# Avisos
# ---------------------------------------------------------------------------
# Los avisos salen hacia servicios de terceros (healthchecks, ntfy, correo). NUNCA incluyen
# líneas del registro: la salida de restic/mysqldump puede contener nombres de ficheros con
# datos personales (p. ej. nombres de alumnos menores). Solo: equipo, modo, paso, código y fecha.
healthcheck_url_for_mode() {
  if [[ $MODE == "check" ]]; then
    printf '%s' "${HEALTHCHECK_CHECK_URL:-}"
  else
    printf '%s' "${HEALTHCHECK_URL:-}"
  fi
}

# Hace un ping HTTP sin mostrar nunca la URL (puede contener un token).
http_ping() {
  local url=$1 body=${2:-}
  if ! command -v curl >/dev/null 2>&1; then
    log_warn "curl no está instalado: no se puede enviar el aviso HTTP."
    return 1
  fi
  if [[ -n $body ]]; then
    curl -fsS -m "${HTTP_TIMEOUT_SECONDS:-15}" --retry 2 -o /dev/null --data-binary "@${body}" "$url"
  else
    curl -fsS -m "${HTTP_TIMEOUT_SECONDS:-15}" --retry 2 -o /dev/null "$url"
  fi
}

notify_start() {
  local url
  url="$(healthcheck_url_for_mode)"
  if [[ -n $url ]]; then
    http_ping "${url%/}/start" || log_warn "No se pudo enviar el aviso de inicio a HEALTHCHECK."
  fi
}

notify_success() {
  local url
  url="$(healthcheck_url_for_mode)"
  if [[ -n $url ]]; then
    http_ping "${url%/}" || log_warn "No se pudo enviar el aviso de éxito a HEALTHCHECK."
  fi
}

# Escribe el cuerpo mínimo de un aviso en un fichero y muestra su ruta.
write_alert_body() {
  local kind=$1 step=$2 rc=$3 body
  body="$(mktemp -p "$TMP_DIR" alert.XXXXXX)"
  printf 'Copia de seguridad de Taemoi: %s
Equipo: %s
Modo: %s
Paso: %s
Código: %s
Fecha: %s
Detalles: /var/log/taemoi-backup.log en el servidor
'     "$kind" "${BACKUP_HOST:-desconocido}" "$MODE" "$step" "$rc" "$(timestamp)" >"$body"
  printf '%s' "$body"
}

# Envía un aviso por ntfy y correo. $1 = FALLO o AVISO, $2 = fichero con el cuerpo.
send_ntfy_and_mail() {
  local kind=$1 body=$2 priority="high"
  [[ $kind == "FALLO" ]] || priority="default"
  if [[ -n ${NTFY_URL:-} ]] && command -v curl >/dev/null 2>&1; then
    curl -fsS -m "${HTTP_TIMEOUT_SECONDS:-15}" --retry 2 -o /dev/null       -H "Title: Taemoi: ${kind} en la copia (${BACKUP_HOST:-?})" -H "Priority: ${priority}" -H "Tags: warning"       --data-binary "@${body}" "$NTFY_URL" || log_warn "No se pudo enviar el aviso a NTFY_URL."
  fi
  if [[ -n ${ALERT_EMAIL:-} ]]; then
    if command -v mail >/dev/null 2>&1; then
      mail -s "[Taemoi] ${kind} en la copia de seguridad (${BACKUP_HOST:-?})" "$ALERT_EMAIL" <"$body" ||
        log_warn "No se pudo enviar el correo de aviso."
    else
      log_warn "ALERT_EMAIL está definido pero la orden 'mail' no está instalada (apt install mailutils)."
    fi
  fi
}

send_failure_alerts() {
  local rc=$1 body url
  [[ -n $TMP_DIR && -d $TMP_DIR ]] || return 0
  body="$(write_alert_body FALLO "$CURRENT_STEP" "$rc")"
  url="$(healthcheck_url_for_mode)"
  if [[ -n $url ]]; then
    http_ping "${url%/}/fail" "$body" || log_warn "No se pudo enviar el aviso de fallo a HEALTHCHECK."
  fi
  send_ntfy_and_mail FALLO "$body"
}

# Aviso que no hace fallar la ejecución (healthchecks: endpoint /log, no cambia el estado).
send_warning_alert() {
  local step=$1 body url
  ((ALERTS_ENABLED)) || return 0
  body="$(write_alert_body AVISO "$step" 0)"
  url="$(healthcheck_url_for_mode)"
  if [[ -n $url ]]; then
    http_ping "${url%/}/log" "$body" || log_warn "No se pudo enviar el aviso a HEALTHCHECK."
  fi
  send_ntfy_and_mail AVISO "$body"
}

# ---------------------------------------------------------------------------
# Trampas
# ---------------------------------------------------------------------------
on_err() {
  log_error "Orden fallida en la línea $1 (código $2): $3"
}

on_exit() {
  local rc=$?
  trap - ERR
  if ((rc != 0)); then
    log_error "FALLO: modo '${MODE}' terminado con código ${rc}. Paso: ${CURRENT_STEP}"
    syslog_msg err "FALLO (${MODE}) en el paso '${CURRENT_STEP}', código ${rc}"
    if ((ALERTS_ENABLED)); then
      send_failure_alerts "$rc" || true
    fi
  fi
  if [[ -n $TMP_DIR && -d $TMP_DIR ]]; then
    rm -rf -- "$TMP_DIR"
  fi
  exit "$rc"
}

set_err_trap() {
  trap 'on_err "$LINENO" "$?" "$BASH_COMMAND"' ERR
}

set_err_trap
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# ---------------------------------------------------------------------------
# Argumentos y configuración
# ---------------------------------------------------------------------------
usage() {
  cat <<'EOF'
Uso: backup_taemoi.sh [--check | --preflight] [--config FICHERO]

  (sin modo)     Copia de seguridad completa (BD + ficheros) y retención.
  --check        Comprobación de integridad del repositorio (restic check --read-data-subset).
  --preflight    Solo comprobaciones previas (montaje, repositorio, contraseña, rutas, MySQL).
  --config F     Fichero de configuración (por defecto /etc/taemoi-backup.conf).
  -h, --help     Muestra esta ayuda.
EOF
}

parse_args() {
  while (($# > 0)); do
    case $1 in
      --check) MODE="check" ;;
      --preflight) MODE="preflight" ;;
      --config)
        [[ $# -ge 2 ]] || { usage >&2; exit 2; }
        CONFIG_FILE=$2
        shift
        ;;
      -h | --help)
        usage
        exit 0
        ;;
      *)
        printf 'Opción desconocida: %s\n' "$1" >&2
        usage >&2
        exit 2
        ;;
    esac
    shift
  done
  if [[ $MODE == "preflight" ]]; then
    ALERTS_ENABLED=0
  fi
}

# Comprueba que un fichero sensible pertenece a root y no tiene permisos para grupo/otros.
check_private_file() {
  local file=$1 label=$2 owner mode
  [[ -f $file ]] || fail "${label}: no existe el fichero ${file}."
  owner="$(stat -c '%u' -- "$file")"
  mode="$(stat -c '%a' -- "$file")"
  [[ $owner == "0" ]] || fail "${label}: ${file} debe pertenecer a root (chown root:root ${file})."
  if (((8#$mode & 8#077) != 0)); then
    fail "${label}: ${file} tiene permisos ${mode}; deben ser 600 o 400 (chmod 600 ${file})."
  fi
}

load_config() {
  CURRENT_STEP="lectura de la configuración"
  set_defaults
  [[ -f $CONFIG_FILE ]] || fail "No existe el fichero de configuración ${CONFIG_FILE} (copie taemoi-backup.conf.example, ver README)."
  check_private_file "$CONFIG_FILE" "Configuración"
  # shellcheck source=/dev/null
  source "$CONFIG_FILE"

  if [[ -n ${RESTIC_PASSWORD:-} || -n ${RESTIC_PASSWORD_COMMAND:-} ]]; then
    fail "La contraseña de restic no debe estar en la configuración ni en el entorno: use RESTIC_PASSWORD_FILE."
  fi
  unset RESTIC_PASSWORD RESTIC_PASSWORD_COMMAND RESTIC_REPOSITORY_FILE
  export RESTIC_REPOSITORY RESTIC_PASSWORD_FILE

  local var
  for var in MIN_DUMP_BYTES LOCK_WAIT_SECONDS DISK_USAGE_ALERT_PERCENT HTTP_TIMEOUT_SECONDS; do
    [[ ${!var} =~ ^[0-9]+$ ]] || fail "Configuración: ${var} debe ser un número entero (valor actual: '${!var}')."
  done
  for var in KEEP_LAST KEEP_HOURLY KEEP_DAILY KEEP_WEEKLY KEEP_MONTHLY KEEP_YEARLY; do
    [[ -z ${!var} || ${!var} =~ ^[0-9]+$ ]] || fail "Configuración: ${var} debe estar vacío o ser un entero (valor actual: '${!var}')."
  done
  [[ $DB_DUMP_FILENAME =~ ^[A-Za-z0-9._-]+$ ]] || fail "Configuración: DB_DUMP_FILENAME solo admite letras, números, '.', '_' y '-'."
  [[ -n $BACKUP_HOST ]] || fail "Configuración: BACKUP_HOST está vacío."
}

# Lee una variable de un fichero .env de docker compose sin ejecutarlo.
env_value() {
  local key=$1 line value
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$ENV_FILE" | tail -n 1)" || return 0
  value=${line#*=}
  value=${value%$'\r'}
  if [[ ${#value} -ge 2 && $value == \"*\" ]]; then
    value=${value:1:${#value}-2}
  elif [[ ${#value} -ge 2 && $value == \'*\' ]]; then
    value=${value:1:${#value}-2}
  else
    # Comentario al final de una línea sin comillas (mismo criterio que docker compose).
    value="$(sed -E 's/[[:space:]]+#.*$//' <<<"$value")"
  fi
  printf '%s' "$value"
}

# Ruta del .env relativa al directorio del proyecto, como hace docker compose.
absolute_path() {
  local path=$1
  if [[ $path == /* ]]; then
    printf '%s' "$path"
  else
    printf '%s/%s' "$TAEMOI_DIR" "${path#./}"
  fi
}

resolve_settings() {
  CURRENT_STEP="resolución de rutas"
  ENV_FILE=${ENV_FILE:-${TAEMOI_DIR}/.env}
  CERTBOT_CONF_DIR=${CERTBOT_CONF_DIR:-${TAEMOI_DIR}/certbot/conf}
  if [[ $MODE == "check" ]]; then
    return 0
  fi
  [[ -r $ENV_FILE ]] || fail "No se puede leer ${ENV_FILE}."
  if [[ -z $IMAGENES_DIR ]]; then
    IMAGENES_DIR="$(env_value APP_IMAGENES_HOST_PATH)"
    IMAGENES_DIR=${IMAGENES_DIR:-${TAEMOI_DIR}/static_resources/imagenes}
  fi
  if [[ -z $DOCUMENTOS_DIR ]]; then
    DOCUMENTOS_DIR="$(env_value APP_DOCUMENTOS_HOST_PATH)"
    DOCUMENTOS_DIR=${DOCUMENTOS_DIR:-${TAEMOI_DIR}/static_resources/documentos}
  fi
  IMAGENES_DIR="$(absolute_path "$IMAGENES_DIR")"
  DOCUMENTOS_DIR="$(absolute_path "$DOCUMENTOS_DIR")"
}

acquire_lock() {
  CURRENT_STEP="bloqueo de ejecución (flock)"
  exec {LOCK_FD}>>"$LOCK_FILE"
  if ! flock -w "$LOCK_WAIT_SECONDS" "$LOCK_FD"; then
    fail "Otra copia o restauración sigue en curso tras esperar ${LOCK_WAIT_SECONDS}s (bloqueo ${LOCK_FILE})."
  fi
}

# ---------------------------------------------------------------------------
# Comprobaciones previas
# ---------------------------------------------------------------------------
require_commands() {
  local cmd missing=()
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
  done
  if ((${#missing[@]} > 0)); then
    fail "Faltan órdenes necesarias: ${missing[*]}"
  fi
}

repository_is_local() {
  [[ $RESTIC_REPOSITORY == /* ]]
}

# Falla si restic es anterior a MIN_RESTIC_VERSION (p. ej. el 0.12 de Ubuntu 22.04).
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

check_repository() {
  CURRENT_STEP="comprobación del repositorio"
  require_commands restic flock stat df
  check_restic_version
  check_private_file "$RESTIC_PASSWORD_FILE" "Contraseña de restic"
  [[ -s $RESTIC_PASSWORD_FILE ]] || fail "El fichero de contraseña ${RESTIC_PASSWORD_FILE} está vacío."

  if repository_is_local; then
    require_commands mountpoint realpath
    if ! mountpoint -q "$USB_MOUNTPOINT"; then
      fail "El disco USB NO está montado en ${USB_MOUNTPOINT}. No se hace copia en el disco interno (a propósito). Monte el disco (mount ${USB_MOUNTPOINT}) y revise /etc/fstab."
    fi
    local repo_real mount_real
    repo_real="$(realpath -m -- "$RESTIC_REPOSITORY")"
    mount_real="$(realpath -m -- "$USB_MOUNTPOINT")"
    [[ $repo_real == "${mount_real}/"* ]] ||
      fail "RESTIC_REPOSITORY (${RESTIC_REPOSITORY}) no está dentro de USB_MOUNTPOINT (${USB_MOUNTPOINT})."
    [[ -f ${RESTIC_REPOSITORY}/config ]] ||
      fail "No hay un repositorio restic en ${RESTIC_REPOSITORY} (falta 'config'). Inicialícelo con 'restic init' (ver README); este script nunca lo crea solo."
  fi

  # La salida de 'cat config' se descarta; solo interesa saber si el repositorio se abre.
  if ! restic cat config --no-lock >/dev/null 2>"${TMP_DIR}/restic-open.err"; then
    log_output "${TMP_DIR}/restic-open.err" "restic"
    fail "No se puede abrir el repositorio restic (¿contraseña incorrecta, repositorio dañado o inaccesible?)."
  fi

  local locks
  locks="$(restic list locks --no-lock 2>/dev/null | grep -c . || true)"
  if [[ $locks != "0" ]]; then
    log_warn "El repositorio tiene ${locks} bloqueo(s) de restic. Si la ejecución falla por 'repository is already locked', compruebe que no hay ningún restic en marcha (pgrep -a restic) y ejecute 'restic unlock'."
  fi
}

check_mysql_container() {
  require_commands docker
  local running
  running="$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null || true)"
  [[ $running == "true" ]] || fail "El contenedor MySQL '${MYSQL_CONTAINER}' no está en marcha."
}

file_paths() {
  printf '%s\n' "$IMAGENES_DIR" "$DOCUMENTOS_DIR" "$ENV_FILE" "$CERTBOT_CONF_DIR"
  if ((${#EXTRA_PATHS[@]} > 0)); then
    printf '%s\n' "${EXTRA_PATHS[@]}"
  fi
}

disk_usage_percent() {
  df -P -- "$USB_MOUNTPOINT" | awk 'NR == 2 { sub(/%/, "", $5); print $5 }'
}

warn_if_no_alerts() {
  if [[ -z $HEALTHCHECK_URL && -z $NTFY_URL && -z $ALERT_EMAIL ]]; then
    log_warn "No hay avisos configurados (HEALTHCHECK_URL, NTFY_URL, ALERT_EMAIL): los fallos solo quedarán en el log."
  fi
}

# ---------------------------------------------------------------------------
# Pasos de la copia (cada uno se ejecuta en un subshell con set -e; ver run_step)
# ---------------------------------------------------------------------------
# Lista los IDs de los snapshots que cumplen un filtro de etiquetas (p. ej. taemoi,db-pending),
# del más antiguo al más reciente.
snapshot_ids_with_tags() {
  local json
  json="$(restic snapshots --json --tag "$1")" || return 1
  { grep -o '"id":"[0-9a-f]\{64\}"' <<<"$json" || true; } | cut -d'"' -f4
}

# Borra todos los volcados sin verificar (etiqueta db-pending): de esta ejecución o de una
# anterior que se interrumpió. Nunca cuentan como copia buena.
purge_pending_dumps() {
  local ids
  local -a id_list=()
  if ! ids="$(snapshot_ids_with_tags "taemoi,db-pending")"; then
    log_error "No se pudieron listar los snapshots db-pending."
    return 1
  fi
  [[ -n $ids ]] || return 0
  mapfile -t id_list <<<"$ids"
  if run_logged "restic" restic forget "${id_list[@]}"; then
    log_warn "Descartados ${#id_list[@]} snapshot(s) db-pending sin verificar: no son copias válidas."
  else
    log_error "No se pudieron descartar los snapshots db-pending. Revíselos con: restic snapshots --tag db-pending"
    return 1
  fi
}

# Tamaño del volcado dentro de un snapshot (vacío si no se puede leer).
dump_size_in_snapshot() {
  restic ls --json "$1" | grep -F "\"path\":\"/${DB_DUMP_FILENAME}\"" |
    grep -o '"size":[0-9]*' | head -n 1 | cut -d: -f2 || true
}

discard_pending_and_fail() {
  purge_pending_dumps || true
  fail "$@"
}

step_database() {
  check_mysql_container
  purge_pending_dumps || fail "No se pudieron limpiar volcados pendientes de una ejecución anterior."

  local db_name db_password out err snapshot_id dump_rc restic_rc
  db_name="$(env_value MYSQL_DATABASE)"
  db_password="$(env_value MYSQL_ROOT_PASSWORD)"
  [[ -n $db_name ]] || fail "MYSQL_DATABASE no está definido en ${ENV_FILE}."
  [[ -n $db_password ]] || fail "MYSQL_ROOT_PASSWORD no está definido en ${ENV_FILE}."

  out="${TMP_DIR}/db-backup.out"
  err="${TMP_DIR}/db-dump.err"
  log_info "Volcando la base de datos '${db_name}' del contenedor ${MYSQL_CONTAINER} hacia restic..."

  # El snapshot se guarda con la etiqueta 'db-pending'. Solo tras verificarlo se cambia a 'db';
  # si algo falla (aunque no se pueda leer su ID), se borran todos los 'db-pending'.
  # La contraseña viaja en la variable de entorno MYSQL_PWD: 'docker exec -e MYSQL_PWD'
  # (sin '=valor') toma el valor del entorno del cliente, así no aparece en la línea de órdenes.
  set +e
  MYSQL_PWD="$db_password" docker exec -e MYSQL_PWD "$MYSQL_CONTAINER" \
    mysqldump --user=root \
    --single-transaction --quick --routines --triggers --events --hex-blob \
    --default-character-set=utf8mb4 --set-gtid-purged=OFF --no-tablespaces \
    "$db_name" 2>"$err" |
    restic backup --stdin --stdin-filename "$DB_DUMP_FILENAME" \
      --host "$BACKUP_HOST" --tag taemoi --tag db-pending >"$out" 2>&1
  local -a statuses=("${PIPESTATUS[@]}")
  set -e
  dump_rc=${statuses[0]}
  restic_rc=${statuses[1]}
  unset db_password

  log_output "$err" "mysqldump"
  log_output "$out" "restic"
  snapshot_id="$(sed -n 's/^snapshot \([0-9a-f]\{8,\}\) saved$/\1/p' "$out" | tail -n 1)"

  if ((dump_rc != 0 || restic_rc != 0)); then
    discard_pending_and_fail "El volcado de la base de datos ha fallado (mysqldump=${dump_rc}, restic=${restic_rc})."
  fi
  [[ -n $snapshot_id ]] ||
    discard_pending_and_fail "No se pudo identificar el snapshot de la base de datos en la salida de restic."

  # Verificación leyendo de vuelta desde el repositorio.
  local size tail_text
  size="$(dump_size_in_snapshot "$snapshot_id")"
  [[ $size =~ ^[0-9]+$ ]] ||
    discard_pending_and_fail "No se pudo leer el tamaño del volcado en el snapshot ${snapshot_id}."
  if ((size < MIN_DUMP_BYTES)); then
    discard_pending_and_fail "El volcado ocupa ${size} bytes, menos que MIN_DUMP_BYTES=${MIN_DUMP_BYTES}: se considera incompleto."
  fi
  if ! tail_text="$(restic dump "$snapshot_id" "/${DB_DUMP_FILENAME}" | tail -c 1024)"; then
    discard_pending_and_fail "No se pudo leer de vuelta el volcado del snapshot ${snapshot_id}."
  fi
  grep -q -- '-- Dump completed' <<<"$tail_text" ||
    discard_pending_and_fail "El volcado no termina con '-- Dump completed': está truncado."

  # Aviso (no fallo) si el volcado es menos de la mitad que el último bueno.
  local previous_id previous_size
  previous_id="$(snapshot_ids_with_tags "taemoi,db" 2>/dev/null | tail -n 1 || true)"
  if [[ -n $previous_id ]]; then
    previous_size="$(dump_size_in_snapshot "$previous_id")"
    if [[ $previous_size =~ ^[0-9]+$ ]] && ((size * 2 < previous_size)); then
      log_warn "AVISO dump-size-drop: el volcado (${size} bytes) es menos de la mitad del anterior (${previous_size} bytes). Compruebe que no se han perdido datos."
      send_warning_alert "dump-size-drop"
    fi
  fi

  run_logged "restic" restic tag --add db --remove db-pending "$snapshot_id" ||
    discard_pending_and_fail "No se pudo marcar como verificado el snapshot ${snapshot_id}."
  log_info "Base de datos guardada y verificada (${size} bytes); snapshot etiquetado como 'db'."
}

step_files() {
  local -a paths=() missing=()
  local path rc=0
  while IFS= read -r path; do
    [[ -n $path ]] || continue
    if [[ -e $path ]]; then
      paths+=("$path")
    else
      missing+=("$path")
    fi
  done < <(file_paths)

  if ((${#paths[@]} == 0)); then
    fail "No existe ninguna de las rutas de ficheros a copiar."
  fi
  log_info "Guardando ficheros: ${paths[*]}"
  run_logged "restic" restic backup --host "$BACKUP_HOST" --tag taemoi --tag files -- "${paths[@]}" || rc=$?

  if ((rc == 3)); then
    fail "restic no pudo leer algunos ficheros (código 3): el snapshot de ficheros está incompleto. Revise el log."
  elif ((rc != 0)); then
    fail "La copia de ficheros ha fallado (restic=${rc})."
  fi
  if ((${#missing[@]} > 0)); then
    fail "Se copiaron las rutas existentes, pero faltan estas: ${missing[*]}"
  fi
  log_info "Ficheros guardados."
}

build_keep_args() {
  KEEP_ARGS=()
  if [[ -n $KEEP_LAST && $KEEP_LAST -gt 0 ]]; then KEEP_ARGS+=(--keep-last "$KEEP_LAST"); fi
  if [[ -n $KEEP_HOURLY && $KEEP_HOURLY -gt 0 ]]; then KEEP_ARGS+=(--keep-hourly "$KEEP_HOURLY"); fi
  if [[ -n $KEEP_DAILY && $KEEP_DAILY -gt 0 ]]; then KEEP_ARGS+=(--keep-daily "$KEEP_DAILY"); fi
  if [[ -n $KEEP_WEEKLY && $KEEP_WEEKLY -gt 0 ]]; then KEEP_ARGS+=(--keep-weekly "$KEEP_WEEKLY"); fi
  if [[ -n $KEEP_MONTHLY && $KEEP_MONTHLY -gt 0 ]]; then KEEP_ARGS+=(--keep-monthly "$KEEP_MONTHLY"); fi
  if [[ -n $KEEP_YEARLY && $KEEP_YEARLY -gt 0 ]]; then KEEP_ARGS+=(--keep-yearly "$KEEP_YEARLY"); fi
  return 0
}

apply_retention() {
  CURRENT_STEP="retención (restic forget --prune)"
  build_keep_args
  ((${#KEEP_ARGS[@]} > 0)) || fail "No hay ninguna regla KEEP_* activa: no se aplica la retención."
  # Si el reloj ha retrocedido, 'forget' conservaría los snapshots "del futuro" y podría borrar
  # los buenos. Se falla antes de borrar nada.
  CURRENT_STEP="reloj del sistema"
  check_clock_not_behind_snapshots
  CURRENT_STEP="retención (restic forget --prune)"
  purge_pending_dumps || fail "No se pudieron descartar los snapshots db-pending antes de la retención."
  log_info "Aplicando retención: ${KEEP_ARGS[*]}"
  # Solo actúa sobre snapshots con la etiqueta 'taemoi', agrupados por equipo y etiquetas
  # (db, files y db-pre-restore se retienen por separado). No toca nada fuera del repositorio.
  run_logged "restic" restic forget --tag taemoi --group-by host,tags --compact \
    "${KEEP_ARGS[@]}" --prune --max-unused "$PRUNE_MAX_UNUSED" ||
    fail "restic forget --prune ha fallado."
}

check_clock_not_behind_snapshots() {
  local json times latest=0 t epoch now
  json="$(restic snapshots --json --tag taemoi)" || fail "No se pudieron leer las fechas de los snapshots."
  times="$({ grep -o '"time":"[^"]*"' <<<"$json" || true; } | cut -d'"' -f4)"
  while IFS= read -r t; do
    [[ -n $t ]] || continue
    epoch="$(date -d "$t" +%s 2>/dev/null)" || fail "Fecha de snapshot ilegible: ${t}"
    if ((epoch > latest)); then
      latest=$epoch
    fi
  done <<<"$times"
  now="$(date +%s)"
  # 120 s de margen por pequeñas diferencias de reloj.
  if ((latest > now + 120)); then
    fail "El reloj del sistema ($(date -d "@${now}" '+%F %T')) va por detrás del snapshot más reciente ($(date -d "@${latest}" '+%F %T')). No se aplica la retención. Revise la hora (timedatectl)."
  fi
}

check_disk_usage() {
  CURRENT_STEP="espacio en disco"
  repository_is_local || return 0
  local usage
  usage="$(disk_usage_percent)"
  [[ $usage =~ ^[0-9]+$ ]] || fail "No se pudo calcular el uso de ${USB_MOUNTPOINT}."
  log_info "Uso del disco de copias ${USB_MOUNTPOINT}: ${usage}% (umbral de aviso ${DISK_USAGE_ALERT_PERCENT}%)."
  if ((usage >= DISK_USAGE_ALERT_PERCENT)); then
    fail "El disco de copias está al ${usage}% (umbral ${DISK_USAGE_ALERT_PERCENT}%). Revise la retención o el tamaño de los datos (ver README)."
  fi
}

# Ejecuta un paso en un subshell con 'set -e' activo. Si falla, se anota y se sigue con el
# siguiente paso (así un fallo de MySQL no impide copiar las fotos); al final la ejecución falla.
run_step() {
  local name=$1 func=$2 rc
  CURRENT_STEP=$name
  # La trampa ERR se quita fuera del subshell (el fallo ya se anota abajo) y se reactiva dentro.
  trap - ERR
  set +e
  (
    set_err_trap
    set -e
    "$func"
  )
  rc=$?
  set -e
  set_err_trap
  if ((rc != 0)); then
    FAILED_STEPS+=("$name")
    log_error "El paso '${name}' ha fallado (código ${rc})."
  fi
  return 0
}

run_check() {
  CURRENT_STEP="restic check --read-data-subset=${CHECK_READ_DATA_SUBSET}"
  log_info "Comprobando la integridad del repositorio (lectura de ${CHECK_READ_DATA_SUBSET} de los datos)..."
  run_logged "restic" restic check --read-data-subset="$CHECK_READ_DATA_SUBSET" ||
    fail "restic check ha detectado problemas o no ha podido completarse."
  log_info "Integridad del repositorio correcta."
}

# ---------------------------------------------------------------------------
# Principal
# ---------------------------------------------------------------------------
main() {
  # Los valores por defecto se cargan antes de nada: la trampa de salida los usa aunque
  # se falle antes de leer la configuración.
  set_defaults
  parse_args "$@"
  TMP_DIR="$(mktemp -d -t taemoi-backup.XXXXXX)"

  ((EUID == 0)) || fail "Este script debe ejecutarse como root."
  load_config
  resolve_settings
  log_info "=== Inicio: modo '${MODE}' (v${SCRIPT_VERSION}, equipo ${BACKUP_HOST}) ==="

  case $MODE in
    preflight)
      check_repository
      run_step "contenedor MySQL" check_mysql_container
      local path
      while IFS= read -r path; do
        if [[ -n $path && ! -e $path ]]; then
          log_error "No existe la ruta a copiar: ${path}"
          FAILED_STEPS+=("ruta ${path}")
        fi
      done < <(file_paths)
      if repository_is_local; then
        log_info "Uso actual de ${USB_MOUNTPOINT}: $(disk_usage_percent)%"
      fi
      warn_if_no_alerts
      ;;
    check)
      acquire_lock
      notify_start
      check_repository
      run_check
      ;;
    backup)
      acquire_lock
      notify_start
      warn_if_no_alerts
      check_repository
      run_step "copia de la base de datos" step_database
      run_step "copia de ficheros" step_files
      if ((${#FAILED_STEPS[@]} == 0)); then
        apply_retention
      else
        log_warn "No se aplica la retención en esta ejecución porque ha habido fallos."
      fi
      run_step "espacio en disco" check_disk_usage
      ;;
  esac

  if ((${#FAILED_STEPS[@]} > 0)); then
    local joined
    joined="$(printf '%s; ' "${FAILED_STEPS[@]}")"
    CURRENT_STEP=${joined%; }
    fail "Pasos fallidos: ${CURRENT_STEP}"
  fi

  CURRENT_STEP="fin"
  log_info "=== OK: modo '${MODE}' completado en ${SECONDS}s ==="
  syslog_msg info "OK (${MODE}) en ${SECONDS}s"
  if [[ $MODE != "preflight" ]]; then
    notify_success
  fi
}

main "$@"
