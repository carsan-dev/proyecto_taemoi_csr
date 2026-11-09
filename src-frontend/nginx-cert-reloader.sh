#!/bin/sh

set -eu

# Entrypoint del nginx de producción:
# 1. Activa los hosts de escuela (nginx/escuelas/<dominio>.conf, montado en $ESCUELAS_DIR) que tienen certificado.
# 2. Arranca nginx.
# 3. Vigila TODOS los certificados (live/*/fullchain.pem): si aparece, cambia o desaparece uno, vuelve a activar los
#    hosts y recarga nginx solo si nginx -t es correcto.
# Un host de escuela sin certificado, o cuya configuración no pasa nginx -t, se queda fuera y se avisa en el log:
# nunca impide que arranque o se recargue moiskimdo.es.

LIVE_DIR="${NGINX_LIVE_DIR:-/etc/letsencrypt/live}"
ESCUELAS_DIR="${NGINX_ESCUELAS_DIR:-/etc/nginx/escuelas}"
# Copia de $ESCUELAS_DIR tomada al arrancar el contenedor: el resto de la configuración son ficheros montados que no
# cambian hasta recrearlo, y así un git pull sin recrear no mezcla ficheros nuevos con los cargados.
DISPONIBLES_DIR="${NGINX_ESCUELAS_DISPONIBLES_DIR:-/etc/nginx/escuelas-disponibles}"
# Lo que incluye nginx-production.conf (include /etc/nginx/escuelas-activas/*.conf).
ACTIVAS_DIR="${NGINX_ESCUELAS_ACTIVAS_DIR:-/etc/nginx/escuelas-activas}"
RELOAD_INTERVAL_SECONDS="${NGINX_CERT_RELOAD_INTERVAL_SECONDS:-300}"

case "$RELOAD_INTERVAL_SECONDS" in
  ''|*[!0-9]*)
    echo "NGINX_CERT_RELOAD_INTERVAL_SECONDS must be a positive integer" >&2
    exit 1
    ;;
esac

if [ "$RELOAD_INTERVAL_SECONDS" -eq 0 ]; then
  echo "NGINX_CERT_RELOAD_INTERVAL_SECONDS must be greater than zero" >&2
  exit 1
fi

# Huella de todos los certificados: ruta y sha256 de cada live/*/fullchain.pem legible.
certificates_checksum() {
  for certificate in "$LIVE_DIR"/*/fullchain.pem; do
    if [ -r "$certificate" ]; then
      sha256sum "$certificate" || true
    fi
  done
}

nginx_config_ok() {
  nginx -t >/dev/null 2>&1
}

snapshot_schools() {
  rm -rf "$DISPONIBLES_DIR"
  mkdir -p "$DISPONIBLES_DIR" "$ACTIVAS_DIR"
  if [ -d "$ESCUELAS_DIR" ]; then
    for conf in "$ESCUELAS_DIR"/*.conf; do
      if [ -f "$conf" ]; then
        cp "$conf" "$DISPONIBLES_DIR/"
      fi
    done
  fi
}

# Deja en $ACTIVAS_DIR solo los hosts con certificado completo que pasan nginx -t, de uno en uno: un host roto no se
# lleva por delante a los demás ni a moiskimdo.es. Si la configuración base (sin escuelas) ya falla, no activa ninguno.
activate_schools() {
  mkdir -p "$ACTIVAS_DIR"
  rm -f "$ACTIVAS_DIR"/*.conf

  if ! nginx_config_ok; then
    echo "nginx -t fails without school hosts; no school host activated" >&2
    return 0
  fi

  for conf in "$DISPONIBLES_DIR"/*.conf; do
    if [ ! -f "$conf" ]; then
      continue
    fi

    domain="$(basename "$conf" .conf)"
    case "$domain" in
      ''|.*|-*|*[!a-z0-9.-]*)
        echo "School host ignored: invalid file name $(basename "$conf") (expected <domain>.conf in lowercase)" >&2
        continue
        ;;
    esac

    certificate_dir="$LIVE_DIR/$domain"
    if [ ! -r "$certificate_dir/fullchain.pem" ] || [ ! -r "$certificate_dir/privkey.pem" ] \
      || [ ! -r "$certificate_dir/chain.pem" ]; then
      echo "School host $domain not active: no certificate in $certificate_dir" >&2
      continue
    fi

    cp "$conf" "$ACTIVAS_DIR/$domain.conf"
    if test_output="$(nginx -t 2>&1)"; then
      echo "School host $domain active"
    else
      rm -f "$ACTIVAS_DIR/$domain.conf"
      echo "School host $domain not active: nginx -t failed with it:" >&2
      echo "$test_output" >&2
    fi
  done
}

watch_certificates() {
  previous_checksum="$(certificates_checksum)"
  # 1 tras un fallo: se reintenta en cada vuelta aunque los certificados vuelvan a ser los de antes, porque
  # activate_schools ya ha cambiado escuelas-activas y debe quedar igual que lo cargado.
  retry_pending=0

  while sleep "$RELOAD_INTERVAL_SECONDS"; do
    current_checksum="$(certificates_checksum)"

    if [ -z "$current_checksum" ]; then
      continue
    fi
    if [ "$current_checksum" = "$previous_checksum" ] && [ "$retry_pending" -eq 0 ]; then
      continue
    fi

    activate_schools || echo "Could not activate school hosts" >&2
    if nginx -t && nginx -s reload; then
      previous_checksum="$current_checksum"
      retry_pending=0
      echo "Reloaded nginx after TLS certificate update"
    else
      retry_pending=1
      echo "TLS certificate changed, but nginx -t or reload failed; nginx keeps the previous configuration" >&2
    fi
  done
}

# /var/cache/nginx es un volumen que sobrevive a las imágenes: si una imagen nueva cambia el uid del usuario nginx,
# los temporales antiguos quedan de otro dueño y nginx corta las respuestas grandes del proxy (Permission denied).
if [ "$(id -u)" -eq 0 ] && [ -d /var/cache/nginx ]; then
  chown -R nginx:nginx /var/cache/nginx || echo "Could not fix ownership of /var/cache/nginx" >&2
fi

# Un fallo aquí no debe impedir que arranque moiskimdo.es: sin escuelas activas, el include del glob queda vacío.
snapshot_schools || echo "Could not copy school hosts from $ESCUELAS_DIR" >&2
activate_schools || echo "Could not activate school hosts" >&2

watch_certificates &
exec nginx "$@"
