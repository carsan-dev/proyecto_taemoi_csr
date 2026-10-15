# Procedimiento de operación en producción

Se ejecuta a mano en el servidor (mini PC Ubuntu 24.04, repositorio en `/opt/taemoi`, rama `develop`).
Cada paso indica qué debe salir y, si no sale, que hay que parar. Detalle de cada migración:
[checklist-despliegue.md](../multi-escuela/checklist-despliegue.md). Copias: [scripts/backup/README.md](../../scripts/backup/README.md).

Las consultas a producción devuelven solo recuentos (nunca nombres, DNI ni emails). Lo que no se puede
verificar desde el repositorio va marcado «(comprobar en el servidor)».

Consulta SQL sin teclear contraseña (usa las variables del propio contenedor; sustituya la consulta):

```bash
echo "SELECT COUNT(*) FROM alumno;" | docker exec -i taemoi-mysql-prod sh -c 'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

Con un fichero: la misma orden con `< fichero.sql` en lugar de `echo ... |`.

## 1. Antes de cualquier despliegue

1. Estado de la CI de `develop`:

```bash
gh run list --branch develop --limit 3
```

Debe salir: la última ejecución de `CI` en `completed success` con el commit que se va a desplegar (o mírelo en la pestaña Actions de GitHub). Si está en rojo o en curso: parar.

2. Las imágenes existen. La CI publica desde `develop` las etiquetas `:develop` y `:sha-<sha7>`; `:latest` solo desde `main` (`.github/workflows/ci.yml:184`).

```bash
docker manifest inspect ghcr.io/carsan-dev/taemoi-backend:develop
```

Debe salir: un JSON. Repetir con `taemoi-frontend` y, si se usa, con `:sha-<sha7>` (comprobar en el servidor: puede pedir `docker login ghcr.io`). Si no existe: parar.

3. El `.env` apunta a `:develop` o a un `:sha-...`, nunca a `:latest`:

```bash
grep -E "TAEMOI_(BACKEND|FRONTEND)_IMAGE" /opt/taemoi/.env
```

Debe salir: dos líneas con `:develop` o `:sha-xxxxxxx`. Si salen vacías o con `:latest`, el compose usa `:latest` (`docker-compose.production.yml`, líneas 24 y 65), que es de `main`: parar y corregir el `.env`.

4. Anotar las imágenes en marcha (para la vuelta atrás):

```bash
docker inspect --format '{{.Config.Image}} {{.Image}}' taemoi-backend-prod taemoi-frontend-prod
```

5. El árbol está limpio y en `develop`:

```bash
cd /opt/taemoi && git status -sb
```

Debe salir: `## develop...origin/develop` sin ficheros modificados. Si no: parar.

## 2. Despliegue normal (sin migraciones nuevas)

Sin migraciones nuevas = ningún fichero nuevo en `src-api/src/main/resources/db/migration`.

1. Esperar a que la CI de `develop` termine en verde, incluido el job `publish-images`:

```bash
gh run list --branch develop --limit 1
```

Debe salir: `CI` en `completed success` con el commit a desplegar (o la pestaña Actions de GitHub). El compose no construye nada: `backend` y `frontend` solo tienen `image:` con `pull_policy: always` (`docker-compose.production.yml`, líneas 23-26 y 64-67) y `up` descarga la imagen de GHCR que indique el `.env`. Si `publish-images` no ha terminado, se levantaría la imagen anterior con el compose nuevo. Si está en curso o en rojo: parar y esperar.

2. Actualizar el repositorio:

```bash
cd /opt/taemoi && git pull --ff-only
```

Debe salir: `Fast-forward` o `Already up to date.`. Si falla: parar, no forzar.

3. Parar todo (`down` también para la base de datos: hay corte total hasta el paso 4):

```bash
docker compose -f docker-compose.production.yml down
```

4. Levantar descargando las imágenes del `.env` (`--build` no hace nada aquí):

```bash
docker compose -f docker-compose.production.yml up -d --build backend frontend
```

Debe salir: `database`, `backend` y `frontend` creados/`Started` (la base de datos arranca como dependencia). Si da error de imagen o de `pull`: parar y revisar el `.env` (sección 1, paso 3).

> `bash scripts/deploy.sh` sigue en el repositorio como alternativa (verifica, hace `down`/`up -d` y espera a los servicios), pero este procedimiento no lo usa.

3. Logs del backend (sin errores de arranque):

```bash
docker logs --tail 50 taemoi-backend-prod
```

4. Comprobaciones funcionales: sección 3, paso 9.

## 3. Despliegue con migraciones Flyway

Con ventana de mantenimiento (corte total: `down` para también la base de datos). Qué versión va en qué despliegue (V28 no con V27, V28 y V29 juntos, V32 no con V31)
está en la checklist; aquí solo el procedimiento.

1. Esperar a que la CI de `develop` termine en verde, incluido `publish-images` (si no, `up` levantaría la imagen anterior; ver sección 2, paso 1):

```bash
gh run list --branch develop --limit 1
```

Debe salir: `CI` en `completed success` con el commit a desplegar. Si no: parar.

Actualizar el repositorio (no afecta a los contenedores en marcha) y parar frontend y backend dejando la BD arriba:

```bash
cd /opt/taemoi && git pull --ff-only
```

```bash
docker compose -f docker-compose.production.yml stop frontend backend
```

Se usa `stop` y no `down` porque `backup_taemoi.sh` necesita el contenedor MySQL en marcha para el volcado (`check_mysql_container`).

Comprobar que la BD sigue en marcha:

```bash
docker ps --filter name=taemoi-mysql-prod --format '{{.Names}} {{.Status}}'
```

Debe salir: `taemoi-mysql-prod Up ... (healthy)`. Si no: parar.

2. Prechecks (solo lectura, solo recuentos; guarde la salida). Ejemplo para V28:

```bash
docker exec -i taemoi-mysql-prod sh -c 'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < /opt/taemoi/docs/multi-escuela/precheck-v28.sql
```

| Migración | Fichero en `docs/multi-escuela/` | Debe salir |
|-----------|----------------------------------|------------|
| V22 | `precheck-v22.sql` | revisar cada recuento distinto de 0 |
| V26 | `postcheck-v26.sql` | primera consulta a 0 en las cinco tablas |
| V27 | `postcheck-v24.sql`, `postcheck-v27.sql` | informativos; no detienen V27 |
| V28 | `precheck-v28.sql` | siete primeras columnas, `alumno_deporte_disciplina_repetida` y `plantilla_version_repetida` a 0 |
| V29 | `precheck-v29.sql` | las tres primeras columnas a 0 |
| V31 | `precheck-v31.sql` | `membresias_responsable_o_profesor` = 0; el resto, revisar (checklist) |

Si un recuento obligatorio no es 0: parar y corregir esas filas.

3. Antes de V27 y de V31, permiso `CREATE TEMPORARY TABLES` del usuario de la aplicación:

```bash
echo "SHOW GRANTS;" | docker exec -i taemoi-mysql-prod sh -c 'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

Debe salir: `CREATE TEMPORARY TABLES` o `ALL PRIVILEGES`. Si no: parar.

4. Copia con la aplicación parada:

```bash
sudo backup_taemoi.sh
```

Debe salir: `=== OK` al final del log. Si acaba con `FALLO: ... Paso: ...`: parar (tabla de errores en el README de backup, «Leer los logs»).

5. Comprobar la copia (BD volcada en un MySQL temporal; no toca producción):

```bash
sudo restaurar_taemoi.sh list --type db
```

```bash
sudo restaurar_taemoi.sh db-verify latest
```

Debe salir: el snapshot nuevo en la lista y `VERIFICACIÓN CORRECTA` con tablas, filas y última versión de Flyway. Anote el ID del snapshot: es la vuelta atrás. Si falla: no migrar.

6. Desplegar (ya con copia hecha y verificada). `down` para también la BD; `up` la rearranca y descarga las imágenes del `.env`:

```bash
docker compose -f docker-compose.production.yml down
```

```bash
docker compose -f docker-compose.production.yml up -d --build backend frontend
```

Debe salir: los tres servicios `Started`. Si la descarga de la imagen falla: parar y revisar el `.env`. (`deploy.sh` queda como alternativa, no se usa.)

**Espera a Flyway.** El arranque puede tardar hasta el `start_period` del healthcheck (180 s) mientras Flyway migra. No relance nada ni reinicie el backend con una migración en curso (MySQL no deshace el DDL a medias). Siga el log:

```bash
docker logs -f taemoi-backend-prod
```

Debe salir: líneas de Flyway (`Migrating schema ... to version ...`) y al final el arranque de Spring. No reinicie el backend con una migración en curso (MySQL no deshace el DDL a medias). Espere a:

```bash
docker inspect --format '{{.State.Health.Status}}' taemoi-backend-prod
```

Debe salir: `healthy`. Si el backend se reinicia en bucle o el log dice que una migración ha fallado: parar, sección 5.

7. Historial de Flyway (recuento por versión y éxito):

```bash
echo "SELECT version, success, COUNT(*) AS n FROM flyway_schema_history GROUP BY version, success ORDER BY MAX(installed_rank);" | docker exec -i taemoi-mysql-prod sh -c 'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

Debe salir: cada versión con `success = 1` y `n = 1`, y como última la esperada. Un `success = 0` o una versión repetida: parar, sección 5.

8. Postchecks (solo lectura, solo recuentos; guarde la salida). Ejemplo para V31:

```bash
docker exec -i taemoi-mysql-prod sh -c 'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < /opt/taemoi/docs/multi-escuela/postcheck-v31.sql
```

| Migración aplicada | Fichero | Debe salir |
|--------------------|---------|------------|
| V24 | `postcheck-v24.sql` | informativo |
| V26 | `postcheck-v26.sql` | primera consulta a 0 en las cinco tablas |
| V27 | `postcheck-v27.sql` | `inscripciones_tarifa_distinta` = 0 |
| V31 | `postcheck-v31.sql` | `familia_alumnos_email_sin_vinculo`, `familia_alumnos_vinculo_sin_email`, `grupos_sin_sede`, `organizaciones_sin_sede` a 0 |

9. Comprobaciones funcionales (apartado «Después» de la checklist):

```bash
docker-compose -f /opt/taemoi/docker-compose.production.yml ps
```

Debe salir: `database`, `backend`, `frontend` y `certbot` en `Up`. Después, en el navegador: entrar como administrador, gestor y familia (cada uno ve lo mismo que antes); dar de alta un alumno (expediente siguiente al más alto); ver las fotos de eventos en la web pública y las fotos y documentos de alumnos en el panel. Si algo falla con migraciones ya aplicadas: sección 5.

## 4. Copia de seguridad y prueba de restauración

Scripts en `scripts/backup/`, instalados en `/usr/local/bin/` (instalación en su README). Repositorio restic:
`/mnt/backups_usb/restic-taemoi`; log: `/var/log/taemoi-backup.log`.

**Manual** (BD, imágenes, documentos, `.env` y certificados):

```bash
sudo backup_taemoi.sh
```

Debe salir: `=== OK` al final del log. Para comprobar el entorno sin escribir nada: `sudo backup_taemoi.sh --preflight`.

**Programada** (cron de root):

```bash
sudo crontab -l
```

Debe salir: `15 */4 * * * /usr/local/bin/backup_taemoi.sh >> /var/log/taemoi-backup.log 2>&1` y `30 3 * * 0 /usr/local/bin/backup_taemoi.sh --check >> /var/log/taemoi-backup.log 2>&1` (según el README; comprobar en el servidor que están instaladas). Fallos recientes:

```bash
sudo grep -E 'FALLO|ERROR|WARN' /var/log/taemoi-backup.log | tail
```

**Listar instantáneas** (admite `--type db|files|all`):

```bash
sudo restaurar_taemoi.sh list
```

**Simulacro mensual** (primer lunes de cada mes, ~20 min; no toca producción):

```bash
sudo restaurar_taemoi.sh db-verify latest
```

Debe salir: `VERIFICACIÓN CORRECTA`, con filas coherentes con la web y la versión de Flyway esperada. Ficheros en una carpeta aparte y comparación:

```bash
sudo restaurar_taemoi.sh files latest --target /root/simulacro-$(date +%F)
```

```bash
sudo diff -rq /root/simulacro-$(date +%F)/opt/taemoi/static_resources /opt/taemoi/static_resources
```

Debe salir: sin diferencias salvo lo subido después de la copia. Limpiar (el `.env` restaurado lleva secretos):

```bash
sudo rm -rf /root/simulacro-$(date +%F)
```

El simulacro completo, que además prueba la contraseña de restic guardada en el gestor, está en el README de backup («Simulacro mensual de restauración»). Anote fecha, snapshot y resultado.

**Restauración real de la BD** (sustituye la de producción). Sin `--confirm-production` solo imprime el plan:

```bash
sudo restaurar_taemoi.sh db-production <ID_SNAPSHOT>
```

Con la opción pide escribir `RESTAURAR <base de datos>` y entonces para el backend, guarda la BD actual en restic (etiqueta `db-pre-restore`; si falla, rearranca el backend y aborta), recrea la BD, importa el volcado y arranca el backend:

```bash
sudo restaurar_taemoi.sh db-production <ID_SNAPSHOT> --confirm-production
```

Si la importación falla, el backend queda parado y el script indica cómo volver al estado anterior: seguirlo.

**Restaurar ficheros** (fotos y documentos): `restaurar_taemoi.sh files SNAPSHOT --target DIR [--include RUTA]` restaura en una carpeta vacía; se copian a mano tras revisarlos con `rsync -a --dry-run`. Nunca restaura sobre producción.

## 5. Vuelta atrás

Según si ya hay migraciones aplicadas (paso 7 de la sección 3).

**A. Sin migraciones nuevas: imagen anterior.**

1. En `/opt/taemoi/.env` poner `TAEMOI_BACKEND_IMAGE` y `TAEMOI_FRONTEND_IMAGE` en el `:sha-<sha7>` anterior (anotado en la sección 1, paso 4) y comprobarlo:

```bash
grep -E "TAEMOI_(BACKEND|FRONTEND)_IMAGE" /opt/taemoi/.env
```

2. Recrear con esas imágenes:

```bash
cd /opt/taemoi && docker-compose -f docker-compose.production.yml up -d backend frontend
```

Debe salir: backend `healthy` y la web operativa. Si el `:sha-` ya no está en GHCR: parar y usar la copia (B).

**B. Con migraciones aplicadas: restaurar la copia.** Tras V20, V26, V28 o V29 el código anterior no funciona sobre el esquema nuevo: la única vuelta atrás es la copia del paso 4 de la sección 3.

1. Parar la aplicación y fijar en el `.env` la imagen anterior (como en A.1):

```bash
cd /opt/taemoi && docker-compose -f docker-compose.production.yml stop frontend backend
```

2. Recrear el backend con la imagen anterior sin arrancarlo. `db-production` arranca el contenedor existente: con la imagen nueva volvería a migrar la BD restaurada (comprobar en el servidor):

```bash
docker-compose -f docker-compose.production.yml up --no-start backend
```

3. Restaurar el snapshot previo a la migración: sección 4, «Restauración real de la BD», con el ID anotado.

4. Arrancar el frontend:

```bash
docker-compose -f docker-compose.production.yml up -d frontend
```

**C. Migración que falla a medias (`flyway repair`).** La checklist dice «`flyway repair` y volver a desplegar» para V26 a V31 (cada paso revisa esquema y datos al repetirse). En producción no hay CLI de Flyway ni script de reparación en el repositorio: **pendiente de definir** (comprobar en el servidor si hay `flyway` instalado). Para ver la migración fallida:

```bash
echo "SELECT installed_rank, version, success FROM flyway_schema_history WHERE success = 0;" | docker exec -i taemoi-mysql-prod sh -c 'mysql -h 127.0.0.1 -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
```

Si hay dudas o la migración ya cambió datos: restaurar la copia (B). No borre filas de `flyway_schema_history` a mano.

## 6. Chuleta

Logs del backend (contenedor `taemoi-backend-prod`; sustituya `backend` por `database` o `frontend` para los otros):

```bash
cd /opt/taemoi && docker-compose -f docker-compose.production.yml logs -f backend
```

Estado de los contenedores:

```bash
docker-compose -f /opt/taemoi/docker-compose.production.yml ps
```

Reiniciar un servicio:

```bash
cd /opt/taemoi && docker-compose -f docker-compose.production.yml restart backend
```

Backend sano (debe salir `healthy`):

```bash
docker inspect --format '{{.State.Health.Status}}' taemoi-backend-prod
```

Imágenes de alumnos: el host `/opt/taemoi/static_resources/imagenes` se monta en `/var/www/app/imagenes`.

```bash
ls /opt/taemoi/static_resources/imagenes | head
```

Espacio en disco:

```bash
df -h / /mnt/backups_usb
```

```bash
docker system df
```
