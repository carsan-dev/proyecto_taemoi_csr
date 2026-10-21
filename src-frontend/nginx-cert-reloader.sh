#!/bin/sh

set -eu

CERTIFICATE_PATH="${NGINX_CERTIFICATE_PATH:-/etc/letsencrypt/live/moiskimdo.es/fullchain.pem}"
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

certificate_checksum() {
  if [ -r "$CERTIFICATE_PATH" ]; then
    sha256sum "$CERTIFICATE_PATH" | awk '{print $1}'
  fi
}

watch_certificate() {
  previous_checksum="$(certificate_checksum)"

  while sleep "$RELOAD_INTERVAL_SECONDS"; do
    current_checksum="$(certificate_checksum)"

    if [ -z "$current_checksum" ] || [ "$current_checksum" = "$previous_checksum" ]; then
      continue
    fi

    if nginx -t && nginx -s reload; then
      previous_checksum="$current_checksum"
      echo "Reloaded nginx after TLS certificate update"
    else
      echo "TLS certificate changed, but nginx reload failed" >&2
    fi
  done
}

# /var/cache/nginx es un volumen que sobrevive a las imágenes: si una imagen nueva cambia el uid del usuario nginx,
# los temporales antiguos quedan de otro dueño y nginx corta las respuestas grandes del proxy (Permission denied).
if [ "$(id -u)" -eq 0 ] && [ -d /var/cache/nginx ]; then
  chown -R nginx:nginx /var/cache/nginx || echo "Could not fix ownership of /var/cache/nginx" >&2
fi

watch_certificate &
exec nginx "$@"
