#!/usr/bin/env bash
###############################################################################
# purgar-auditoria-healthcheck.sh
#
# Borra de `auditoria_evento` las filas generadas por el healthcheck de Docker
# del backend (wget a GET /api/eventos cada 30 s), que se auditaban antes del
# hotfix. Conserva las visitas anónimas reales al mismo endpoint y todo lo demás.
#
# Criterio EXACTO de borrado (no se amplía nunca):
#   usuario_id IS NULL
#   AND metodo_http = 'GET'
#   AND endpoint = '/api/eventos'
#   AND ip_cliente IN ('127.0.0.1', '::1', '0:0:0:0:0:0:0:1')
#   AND user_agent LIKE 'Wget%'
#
# Requisitos previos (en este orden):
#   1. Hotfix desplegado (el healthcheck ya no se audita).
#   2. Copia de seguridad hecha y verificada (scripts/backup/backup_taemoi.sh).
#
# Uso (en el servidor, desde /opt/taemoi):
#   bash scripts/mantenimiento/purgar-auditoria-healthcheck.sh               # = --dry-run
#   bash scripts/mantenimiento/purgar-auditoria-healthcheck.sh --dry-run     # solo cuenta
#   bash scripts/mantenimiento/purgar-auditoria-healthcheck.sh --ejecutar    # borra (pide confirmación)
#   bash scripts/mantenimiento/purgar-auditoria-healthcheck.sh --ejecutar --optimizar
#
# Comportamiento:
#   --dry-run    (por defecto) Solo lectura: total de la tabla, filas que se
#                borrarían y filas que se conservan del mismo endpoint.
#   --ejecutar   Comprueba que no hay filas del criterio en los últimos 5 minutos
#                (si las hay, aborta: falta desplegar el hotfix), pide escribir el
#                número exacto de filas a borrar y borra por lotes de 10 000 (cada
#                lote es su propia transacción, autocommit). Al final verifica:
#                0 filas restantes del criterio y conservadas intactas.
#   --optimizar  Tras el borrado, OPTIMIZE TABLE auditoria_evento (reconstruye la
#                tabla para liberar espacio; puede tardar varios minutos y necesita
#                espacio libre en disco equivalente al tamaño final de la tabla).
#
# Es idempotente: si se interrumpe (Ctrl+C, caída de SSH...), los lotes ya hechos
# quedan confirmados; basta con relanzarlo y continúa con lo que falte.
# Solo imprime recuentos, nunca datos personales.
#
# Códigos de salida: 0 OK · 1 error · 2 uso incorrecto · 3 healthcheck aún activo
#                    4 confirmación incorrecta · 5 verificación final fallida
#                    6 OPTIMIZE fallido (el borrado ya está hecho y verificado)
# No lo lances en torno a las 03:15: AuditoriaRetentionJob borra auditoría antigua
# y la verificación final podría dar un FALLO falso.
#
# Variable de entorno (solo para pruebas locales):
#   TAEMOI_PURGA_CONTENEDOR  Contenedor MySQL (por defecto taemoi-mysql-prod). Las
#                            credenciales se leen de MYSQL_USER, MYSQL_PASSWORD y
#                            MYSQL_DATABASE dentro de ese contenedor.
###############################################################################
set -euo pipefail

CONTENEDOR="${TAEMOI_PURGA_CONTENEDOR:-taemoi-mysql-prod}"
TABLA="auditoria_evento"
TAM_LOTE=10000
PAUSA_LOTE=0.5
VENTANA_MIN=5

FILTRO_ENDPOINT="usuario_id IS NULL AND metodo_http = 'GET' AND endpoint = '/api/eventos'"
IP_LOCAL="ip_cliente IN ('127.0.0.1', '::1', '0:0:0:0:0:0:0:1')"
UA_WGET="user_agent LIKE 'Wget%'"
CRITERIO="${FILTRO_ENDPOINT} AND ${IP_LOCAL} AND ${UA_WGET}"

MODO="dry-run"
OPTIMIZAR=0

uso() {
    sed -n '3,51p' "$0" | sed 's/^# \{0,1\}//'
}

die() {
    echo "ERROR: $1" >&2
    exit "${2:-1}"
}

for arg in "$@"; do
    case "$arg" in
        --dry-run) MODO="dry-run" ;;
        --ejecutar) MODO="ejecutar" ;;
        --optimizar) OPTIMIZAR=1 ;;
        -h|--help) uso; exit 0 ;;
        *) echo "Argumento desconocido: $arg" >&2; uso >&2; exit 2 ;;
    esac
done

if [[ "$OPTIMIZAR" == 1 && "$MODO" != "ejecutar" ]]; then
    die "--optimizar solo se admite junto a --ejecutar." 2
fi

# Ejecuta SQL (leído de $1) en el contenedor y devuelve la salida sin cabeceras.
# El SQL va por stdin: nunca aparece en la lista de procesos ni en el historial.
consulta() {
    local sql="$1" salida errfile
    errfile="$(mktemp)"
    if ! salida="$(printf '%s\n' "$sql" | docker exec -i "$CONTENEDOR" sh -c \
        'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -N -B' 2>"$errfile")"; then
        grep -v 'Using a password on the command line' "$errfile" >&2 || true
        rm -f "$errfile"
        die "fallo al ejecutar SQL en el contenedor '$CONTENEDOR'."
    fi
    rm -f "$errfile"
    printf '%s' "$salida"
}

es_entero() { [[ "$1" =~ ^[0-9]+$ ]]; }

# --- Comprobaciones previas -------------------------------------------------
command -v docker >/dev/null 2>&1 || die "docker no está disponible."
estado="$(docker inspect -f '{{.State.Running}}' "$CONTENEDOR" 2>/dev/null || true)"
[[ "$estado" == "true" ]] || die "el contenedor '$CONTENEDOR' no existe o no está en marcha."

# Recuentos en una sola lectura consistente (autocommit = una instantánea).
# max_id_tabla delimita las filas existentes al empezar, para verificar al final
# que las conservadas siguen intactas aunque entren visitas nuevas mientras tanto.
SQL_RECUENTOS="SELECT
  COUNT(*),
  COALESCE(MAX(id), 0),
  COALESCE(SUM(${FILTRO_ENDPOINT}), 0),
  COALESCE(SUM((${CRITERIO}) IS TRUE), 0),
  COALESCE(SUM(${FILTRO_ENDPOINT} AND (${IP_LOCAL}) IS NOT TRUE AND (${UA_WGET}) IS NOT TRUE), 0),
  COALESCE(SUM(${FILTRO_ENDPOINT} AND (${CRITERIO}) IS NOT TRUE), 0),
  COALESCE(MAX(CASE WHEN (${CRITERIO}) IS TRUE THEN id END), 0)
FROM ${TABLA}"

leer_recuentos() {
    local fila
    fila="$(consulta "$SQL_RECUENTOS")"
    read -r TOTAL MAX_ID_TABLA FILTRO BORRABLES EXTERNAS CONSERVADAS MAX_ID_CRITERIO <<<"$fila"
    for v in "$TOTAL" "$MAX_ID_TABLA" "$FILTRO" "$BORRABLES" "$EXTERNAS" "$CONSERVADAS" "$MAX_ID_CRITERIO"; do
        es_entero "$v" || die "respuesta inesperada de MySQL al contar."
    done
}

# Filas del criterio en los últimos VENTANA_MIN minutos. LEAST(NOW(), UTC_TIMESTAMP())
# cubre que fecha_evento esté guardada en UTC o en hora local del servidor; las filas
# "del futuro" también cuentan. Usa idx_auditoria_evento_fecha.
contar_recientes() {
    consulta "SELECT COUNT(*) FROM ${TABLA}
WHERE fecha_evento >= LEAST(NOW(), UTC_TIMESTAMP()) - INTERVAL ${VENTANA_MIN} MINUTE
  AND ${CRITERIO}"
}

echo "== Purga de auditoría del healthcheck ($MODO) =="
echo "Contenedor: $CONTENEDOR · tabla: $TABLA"
echo "Contando (recorre la tabla completa; puede tardar unos segundos)..."
leer_recuentos
TOTAL_INICIAL="$TOTAL"
MAX_ID_INICIAL="$MAX_ID_TABLA"
CONSERVADAS_INICIAL="$CONSERVADAS"
EXTERNAS_INICIAL="$EXTERNAS"
BORRAR="$BORRABLES"
MAX_ID_BORRAR="$MAX_ID_CRITERIO"

RECIENTES="$(contar_recientes)"
es_entero "$RECIENTES" || die "respuesta inesperada de MySQL al contar filas recientes."

cat <<EOF

  Total de filas de la tabla .................... $TOTAL
  Anónimas GET /api/eventos (filtro) ............ $FILTRO
    - healthcheck (criterio, SE BORRARÍAN) ...... $BORRAR
    - conservadas del mismo endpoint ............ $CONSERVADAS
        de ellas, sin IP local y sin Wget ....... $EXTERNAS
  Filas del criterio en los últimos ${VENTANA_MIN} min ........ $RECIENTES

EOF

# --- Espacio en disco (informativo: no aborta) ------------------------------------
# Tamaños de information_schema: aproximados (estadísticas en caché de InnoDB).
fila="$(consulta "SELECT @@datadir, @@log_bin, @@binlog_format,
  ROUND(data_length / 1048576, 1), ROUND(index_length / 1048576, 1),
  ROUND(data_free / 1048576, 1), avg_row_length
FROM information_schema.TABLES
WHERE table_schema = DATABASE() AND table_name = '${TABLA}'")"
read -r DATADIR LOG_BIN BINLOG_FMT MB_DATOS MB_INDICES MB_LIBRE_TABLA AVG_FILA <<<"$fila"
DISCO="$(docker exec "$CONTENEDOR" df -hP "$DATADIR" 2>/dev/null | awk 'NR == 2 {print "tamaño " $2 " · usado " $3 " · libre " $4 " (" $5 " ocupado)"}' || true)"
echo "Espacio:"
echo "  Disco del datadir de MySQL ($DATADIR): ${DISCO:-no disponible (revisa con df -h en el host)}"
echo "  Tabla $TABLA (aprox.): datos ${MB_DATOS} MB · índices ${MB_INDICES} MB · libre interno ${MB_LIBRE_TABLA} MB"
if [[ "$LOG_BIN" == 1 ]] && es_entero "${AVG_FILA:-}" && [[ "$AVG_FILA" -gt 0 ]]; then
    echo "  Binlog activo ($BINLOG_FMT): el borrado lo hará crecer unos $(( AVG_FILA * BORRAR / 1048576 )) MB (estimación: fila media ${AVG_FILA} B x $BORRAR filas)."
elif [[ "$LOG_BIN" == 1 ]]; then
    echo "  Binlog activo ($BINLOG_FMT): el borrado lo hará crecer (no estimable: estadísticas de InnoDB sin calcular; cuenta con cientos de MB para ~700 000 filas)."
else
    echo "  Binlog: log_bin=${LOG_BIN:-?} (${BINLOG_FMT:-?})."
fi
echo "  El borrado NO devuelve espacio al disco; --optimizar reconstruye la tabla y necesita"
echo "  temporalmente espacio libre similar a su tamaño final."
echo

if [[ "$MODO" == "dry-run" ]]; then
    if [[ "$RECIENTES" != 0 ]]; then
        echo "AVISO: el healthcheck sigue generando filas; --ejecutar abortaría hasta desplegar el hotfix."
    fi
    echo "Modo --dry-run: no se ha modificado nada. Para borrar: --ejecutar"
    exit 0
fi

# --- Modo --ejecutar --------------------------------------------------------
if [[ "$RECIENTES" != 0 ]]; then
    die "hay $RECIENTES filas del criterio en los últimos ${VENTANA_MIN} minutos: el healthcheck sigue auditándose. Despliega el hotfix, espera ${VENTANA_MIN} minutos y vuelve a lanzar el script. No se ha borrado nada." 3
fi

BORRADAS=0
if [[ "$BORRAR" == 0 ]]; then
    echo "No hay filas del criterio: nada que borrar."
else
    echo "Se van a borrar $BORRAR filas (criterio exacto, id <= $MAX_ID_BORRAR) en lotes de $TAM_LOTE."
    echo "Asegúrate de tener la copia de seguridad hecha y verificada."
    respuesta=""
    read -r -p "Escribe el número exacto de filas a borrar para confirmar: " respuesta || true
    if [[ "$respuesta" != "$BORRAR" ]]; then
        die "confirmación incorrecta ('$respuesta' != '$BORRAR'). No se ha borrado nada." 4
    fi

    trap 'echo; echo "Interrumpido: $BORRADAS filas borradas según el script (el lote en curso puede haberse completado también en el servidor). Los lotes hechos están confirmados; relanza el script para continuar." >&2; exit 130' INT TERM

    # Cursor por id: cada lote recorre solo un rango del índice PRIMARY (id > cursor),
    # sin volver a leer las filas conservadas ni las ya borradas pendientes de purga.
    # Los límites de id solo ESTRECHAN el criterio, nunca lo amplían.
    CURSOR=0
    LOTE=0
    inicio=$SECONDS
    while :; do
        LOTE=$((LOTE + 1))
        fila="$(consulta "SET @hasta := (SELECT MAX(id) FROM (
    SELECT id FROM ${TABLA}
    WHERE id > ${CURSOR} AND id <= ${MAX_ID_BORRAR} AND ${CRITERIO}
    ORDER BY id LIMIT ${TAM_LOTE}) AS lote);
DELETE FROM ${TABLA}
WHERE id > ${CURSOR} AND id <= COALESCE(@hasta, ${CURSOR}) AND ${CRITERIO}
ORDER BY id LIMIT ${TAM_LOTE};
SELECT ROW_COUNT(), COALESCE(@hasta, ${CURSOR});")"
        read -r FILAS NUEVO_CURSOR <<<"$fila"
        es_entero "$FILAS" && es_entero "$NUEVO_CURSOR" || die "respuesta inesperada de MySQL en el lote $LOTE."
        if [[ "$FILAS" == 0 ]]; then
            break
        fi
        BORRADAS=$((BORRADAS + FILAS))
        CURSOR="$NUEVO_CURSOR"
        printf 'Lote %d: %d filas · acumulado %d/%d (%d%%) · %ds\n' \
            "$LOTE" "$FILAS" "$BORRADAS" "$BORRAR" $((BORRADAS * 100 / BORRAR)) $((SECONDS - inicio))
        sleep "$PAUSA_LOTE"
    done
    trap - INT TERM
    echo "Borrado terminado: $BORRADAS filas en $((SECONDS - inicio)) s."
fi

# --- Verificación final ---------------------------------------------------------
echo
echo "Verificando..."
fila="$(consulta "SELECT
  COUNT(*),
  COALESCE(SUM((${CRITERIO}) IS TRUE), 0),
  COALESCE(SUM(id <= ${MAX_ID_INICIAL} AND ${FILTRO_ENDPOINT} AND (${CRITERIO}) IS NOT TRUE), 0),
  COALESCE(SUM(id <= ${MAX_ID_INICIAL} AND ${FILTRO_ENDPOINT} AND (${IP_LOCAL}) IS NOT TRUE AND (${UA_WGET}) IS NOT TRUE), 0),
  COALESCE(SUM(id > ${MAX_ID_INICIAL}), 0)
FROM ${TABLA}")"
read -r TOTAL_FINAL RESTANTES CONSERVADAS_FINAL EXTERNAS_FINAL NUEVAS <<<"$fila"
for v in "$TOTAL_FINAL" "$RESTANTES" "$CONSERVADAS_FINAL" "$EXTERNAS_FINAL" "$NUEVAS"; do
    es_entero "$v" || die "respuesta inesperada de MySQL al verificar."
done

cat <<EOF
  Filas restantes con el criterio ............... $RESTANTES (esperado 0)
  Conservadas del endpoint (previas) ............ $CONSERVADAS_FINAL (antes $CONSERVADAS_INICIAL)
    sin IP local y sin Wget ..................... $EXTERNAS_FINAL (antes $EXTERNAS_INICIAL)
  Total de la tabla ............................. $TOTAL_FINAL (antes $TOTAL_INICIAL, borradas $BORRADAS, nuevas $NUEVAS)
EOF

fallo=0
[[ "$RESTANTES" == 0 ]] || { echo "FALLO: quedan $RESTANTES filas del criterio (¿healthcheck activo?)." >&2; fallo=1; }
[[ "$CONSERVADAS_FINAL" == "$CONSERVADAS_INICIAL" ]] || { echo "FALLO: han cambiado las filas conservadas." >&2; fallo=1; }
[[ "$EXTERNAS_FINAL" == "$EXTERNAS_INICIAL" ]] || { echo "FALLO: han cambiado las visitas anónimas externas." >&2; fallo=1; }
[[ "$TOTAL_FINAL" == $((TOTAL_INICIAL - BORRADAS + NUEVAS)) ]] || { echo "AVISO: el total no cuadra exactamente (otro proceso ha borrado filas)." >&2; }
[[ "$fallo" == 0 ]] || exit 5
echo "Verificación correcta."

# --- OPTIMIZE opcional ------------------------------------------------------------
if [[ "$OPTIMIZAR" == 1 ]]; then
    echo
    echo "OPTIMIZE TABLE $TABLA: reconstruye la tabla para devolver el espacio al disco."
    echo "Puede tardar varios minutos y necesita espacio libre similar al tamaño final de la tabla."
    inicio=$SECONDS
    # Filas: Table, Op, Msg_type, Msg_text. Correcto solo si no hay 'error' y el
    # último 'status' es OK. Un error de sentencia ya sale con código 1 en consulta().
    salida_opt="$(consulta "OPTIMIZE TABLE ${TABLA}")"
    printf '%s\n' "$salida_opt" | awk -F'\t' 'NF >= 4 {print "  " $3 ": " $4}'
    if printf '%s\n' "$salida_opt" | awk -F'\t' '
            tolower($3) == "error" { err = 1 }
            tolower($3) == "status" { st = $4 }
            END { exit (err || st != "OK") ? 1 : 0 }'; then
        echo "OPTIMIZE terminado en $((SECONDS - inicio)) s."
    else
        die "OPTIMIZE TABLE ha fallado (ver mensajes arriba). El borrado ya está hecho y verificado; la tabla sigue íntegra. Reintenta solo el OPTIMIZE más tarde: --ejecutar --optimizar." 6
    fi
fi
echo "Hecho."
