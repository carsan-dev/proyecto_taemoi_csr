# Despliegue

Estos ficheros son ejemplos sanitizados de propiedades y no contienen secretos reales.

- `applicationAWS.properties.txt`
- `applicationDocker.properties.txt`

Usalos solo como referencia para comparar con `src-api/src/main/resources/application.properties`.

## Importante

- `JWT_SECRET` debe inyectarse por entorno, nunca hardcodeado.
- Las credenciales reales de base de datos, correo y OAuth2 deben vivir fuera del repositorio.
- Si despliegas sobre una base vacia, usa `APP_BOOTSTRAP_ADMIN_*` para crear el primer admin de forma segura.

## Renovacion automatica TLS

En produccion, el servicio `certbot` comprueba la renovacion cada 12 horas y se
reinicia automaticamente con Docker. El frontend comprueba cada 5 minutos si
el certificado montado ha cambiado y, despues de validar la configuracion,
recarga Nginx sin interrumpir el servicio.

Despues de desplegar cambios en la configuracion, comprueba ambos contenedores:

```bash
sudo docker compose -f docker-compose.production.yml ps frontend certbot
sudo docker compose -f docker-compose.production.yml logs --tail=100 certbot
```

Para probar el desafio de renovacion contra el entorno de pruebas de Let's
Encrypt, deteniendo temporalmente solo el proceso automatico:

```bash
sudo docker compose -f docker-compose.production.yml stop certbot
sudo docker compose -f docker-compose.production.yml run --rm --no-deps \
  --entrypoint certbot certbot renew --cert-name moiskimdo.es --dry-run
sudo docker compose -f docker-compose.production.yml up -d certbot
```

La renovacion manual con recarga inmediata de Nginx esta disponible con:

```bash
sudo bash scripts/manage.sh ssl-renew
```
