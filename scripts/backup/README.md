# Copias de seguridad de producción con restic

Sustituye a `/usr/local/bin/mysql_backup_usb.sh`. Cada 4 horas guarda **la base de datos, las
fotos, los documentos, el `.env` y los certificados** en un repositorio restic **cifrado y
deduplicado** del disco USB. Si algo falla, la ejecución termina en error y avisa; nunca guarda
una copia en el disco interno sin decirlo.

| Fichero | Se instala en | Para qué |
|---------|---------------|----------|
| `backup_taemoi.sh` | `/usr/local/bin/` (root, 700) | Copia, retención y comprobación semanal |
| `restaurar_taemoi.sh` | `/usr/local/bin/` (root, 700) | Listar, restaurar ficheros, verificar y restaurar la BD |
| `taemoi-backup.conf.example` | `/etc/taemoi-backup.conf` (root, 600) | Configuración (sin contraseñas) |

## Instalación rápida

> Requisito: `restic` 0.14 o posterior (los scripts fallan con un mensaje claro si es más
> antiguo). **En Ubuntu 24.04 basta con `apt install restic`** (0.16.4, la versión probada).
> En Ubuntu 22.04 apt trae 0.12: instale el binario oficial (ver "restic en Ubuntu 22.04").

```bash
# 0. Copiar esta carpeta al servidor (por ejemplo, desde el repositorio clonado en /opt/taemoi)
cd /opt/taemoi/scripts/backup

# 1. Paquetes (mailutils solo si va a usar ALERT_EMAIL)
sudo apt update && sudo apt install -y restic curl

# 2. Contraseña del repositorio, en un fichero solo legible por root
sudo install -d -o root -g root -m 700 /root/.config/taemoi-backup
sudo sh -c 'umask 077; head -c 32 /dev/urandom | base64 > /root/.config/taemoi-backup/restic-password'
sudo cat /root/.config/taemoi-backup/restic-password   # cópiela AHORA al gestor de contraseñas

# 3. Crear el repositorio en el USB (el script nunca lo crea solo)
mountpoint /mnt/backups_usb                             # debe decir "is a mountpoint"
sudo restic init --repo /mnt/backups_usb/restic-taemoi \
  --password-file /root/.config/taemoi-backup/restic-password

# 4. Scripts y configuración
sudo install -o root -g root -m 700 backup_taemoi.sh restaurar_taemoi.sh /usr/local/bin/
sudo install -o root -g root -m 600 taemoi-backup.conf.example /etc/taemoi-backup.conf
sudo sed -i 's/\r$//' /etc/taemoi-backup.conf           # por si se copió desde Windows (CRLF)
sudo nano /etc/taemoi-backup.conf                       # configure al menos un aviso (ver "Avisos")

# 5. Probar
sudo backup_taemoi.sh --preflight                       # comprobaciones, no escribe nada
sudo backup_taemoi.sh                                   # primera copia (termina con "=== OK")
sudo restaurar_taemoi.sh db-verify latest               # la copia se puede restaurar
```

### 6. Cron (root)

Cree antes el fichero de log, solo legible por root (si no, cron lo crearía con permisos 644):

```bash
sudo install -o root -g root -m 600 /dev/null /var/log/taemoi-backup.log
```

Edite con `sudo crontab -e`. **Mantenga la línea antigua durante 2 semanas** en paralelo y
desplace la nueva 15 minutos para que no vuelquen a la vez:

```cron
# Antigua: se mantiene 2 semanas mientras se comprueba la nueva
0 */4 * * * /usr/local/bin/mysql_backup_usb.sh >> /var/log/mysql_backup_usb.log 2>&1
# Nueva: copia cada 4 horas y comprobación de integridad los domingos
15 */4 * * * /usr/local/bin/backup_taemoi.sh >> /var/log/taemoi-backup.log 2>&1
30 3 * * 0 /usr/local/bin/backup_taemoi.sh --check >> /var/log/taemoi-backup.log 2>&1
```

Pasadas las 2 semanas, con avisos sin fallos y al menos un simulacro de restauración correcto,
borre la línea antigua. Los `.sql.gz` antiguos de `/mnt/backups_usb/mysql_taemoi` puede
conservarlos hasta que caduquen o borrarlos a mano cuando ya no los necesite.

Las dos tareas comparten un bloqueo (`flock` sobre `/run/taemoi-backup.lock`, en una carpeta
solo escribible por root): si la comprobación del domingo sigue en marcha a las 04:15, la copia
espera (hasta `LOCK_WAIT_SECONDS`) en lugar de solaparse. Los scripts fijan su propio `PATH`, así
que funcionan con el `PATH` mínimo de cron.

### 7. Rotación del log

```bash
sudo tee /etc/logrotate.d/taemoi-backup >/dev/null <<'EOF'
/var/log/taemoi-backup.log {
    monthly
    rotate 12
    compress
    missingok
    notifempty
    create 0600 root root
}
EOF
```

## Qué se guarda

| Snapshot (etiquetas) | Contenido | Cómo |
|----------------------|-----------|------|
| `taemoi`, `db` | Volcado completo de MySQL (`/taemoi-db.sql`) | `mysqldump` → `restic backup --stdin`, sin pasar por disco |
| `taemoi`, `files` | `APP_IMAGENES_HOST_PATH`, `APP_DOCUMENTOS_HOST_PATH`, `/opt/taemoi/.env`, `/opt/taemoi/certbot/conf` (+ `EXTRA_PATHS`) | `restic backup` incremental |
| `taemoi`, `db-pre-restore` | La BD tal como estaba antes de una restauración en producción | Lo crea `restaurar_taemoi.sh` |
| `taemoi`, `db-pending` | Volcado aún sin verificar (solo dura unos segundos) | Nunca cuenta como copia buena; se borra si falla algo |

No se guardan: el volumen Docker `db_data` en bruto (el volcado es lo fiable), las imágenes
Docker (se descargan de nuevo) ni `docker-compose.production.yml`/`nginx` (están en git; si
quiere tenerlos también, añádalos a `EXTRA_PATHS`).

### Cómo se verifica el volcado

`mysqldump` se ejecuta con `--single-transaction --routines --triggers --events --hex-blob
--default-character-set=utf8mb4`. La contraseña llega por la variable `MYSQL_PWD`
(`docker exec -e MYSQL_PWD` sin valor la toma del entorno), así que **no aparece en la línea de
órdenes** ni en `ps`. Tras guardarlo, el snapshot solo se da por bueno si:

1. `mysqldump` y `restic` terminan con código 0 (`set -o pipefail`);
2. el volcado ocupa al menos `MIN_DUMP_BYTES`;
3. al leerlo de vuelta del repositorio termina con `-- Dump completed`.

El volcado se guarda primero con la etiqueta `db-pending`; solo cuando pasa las tres
comprobaciones se cambia a `db` (`restic tag --add db --remove db-pending`). Si falla cualquiera,
se borran **todos** los `db-pending` (aunque no se haya podido leer el ID del snapshot), la
ejecución termina en FALLO y se avisa. Los `db-pending` que deje una ejecución interrumpida se
borran al principio de la siguiente y antes de la retención.

Aunque falle la BD, la copia de ficheros se intenta igualmente; en una ejecución con fallos no
se aplica la retención. Tampoco se aplica si el reloj del sistema va por detrás del snapshot más
reciente (por ejemplo, tras un fallo de la pila del reloj): `forget` podría quedarse con los
snapshots "del futuro" y borrar los buenos.

Si un volcado correcto ocupa menos de la mitad que el anterior, se envía un **aviso** (paso
`dump-size-drop`) pero la ejecución no falla: puede ser una limpieza legítima o una pérdida de datos.

## Avisos

Configure al menos uno en `/etc/taemoi-backup.conf`:

| Variable | Qué hace | Recomendación |
|----------|----------|---------------|
| `HEALTHCHECK_URL` | Pings a `URL/start`, `URL` (éxito), `URL/fail` y `URL/log` (avisos) | **La mejor opción**: healthchecks.io gratis, periodo 4 h, margen 1 h. Avisa también si el equipo está apagado o cron no corre |
| `HEALTHCHECK_CHECK_URL` | Lo mismo para `--check` | Otro check con periodo 7 días |
| `NTFY_URL` | Publica un mensaje en un tema de ntfy si hay fallo o aviso | Útil en el móvil. En ntfy.sh **los temas son públicos**: cualquiera que adivine el nombre lee los mensajes. Use un nombre largo y aleatorio o un servidor ntfy propio con acceso restringido |
| `ALERT_EMAIL` | Envía el fallo con `mail` | Requiere `mailutils` y un MTA que funcione |

**Los avisos no incluyen el log.** Solo envían: resultado (FALLO/AVISO), equipo, modo, nombre
del paso, código de salida y fecha. El motivo: la salida de restic y mysqldump puede contener
nombres de ficheros con datos personales (por ejemplo, documentos con el nombre de un alumno
menor), y healthchecks, ntfy y el correo son servicios de terceros. Los detalles se consultan en
`/var/log/taemoi-backup.log` del servidor. Ninguna URL de aviso se escribe en el log.

### Por qué no hay copia alternativa en el disco interno

El script antiguo, si el USB no estaba montado, guardaba en `/var/backups` **sin avisar**. Eso
oculta el problema durante semanas y deja la copia en el mismo disco que los datos: si ese disco
se estropea, se pierde todo a la vez. Ahora, sin USB montado, la ejecución falla y avisa. El
script también comprueba que `RESTIC_REPOSITORY` está dentro de `USB_MOUNTPOINT`, para no
escribir nunca en la carpeta vacía del punto de montaje.

Recomendación: monte el USB en `/etc/fstab` por `UUID=` con la opción `nofail`, para que el
servidor arranque aunque el disco no esté conectado.

## Dónde guardar las claves

| Secreto | Dónde | Por qué |
|---------|-------|---------|
| Contraseña de restic | Gestor de contraseñas (Bitwarden, 1Password, KeePassXC…) **y** `/root/.config/taemoi-backup/restic-password` en el servidor | Sin ella el repositorio es irrecuperable. **Nunca en el USB**: quien tenga el disco tendría las copias |
| `APP_CIFRADO_CLAVE` | Gestor de contraseñas (y en `/opt/taemoi/.env`) | Sin ella los datos de salud cifrados de la BD restaurada son ilegibles |
| `.env` completo | Gestor de contraseñas, como nota segura | Va dentro de las copias, pero solo se puede sacar con la contraseña de restic |

Haga una prueba de que la copia del gestor es correcta en cada simulacro (paso 1 de abajo).

## Simulacro mensual de restauración

Dedique 20 minutos el primer lunes de cada mes:

```bash
# 1. Usar la contraseña GUARDADA EN EL GESTOR, no la del servidor
sudo sh -c 'umask 077; cat > /root/pw-simulacro'        # pegue la contraseña, Enter, Ctrl+D
sudo RESTIC_PASSWORD_FILE=/root/pw-simulacro restic -r /mnt/backups_usb/restic-taemoi snapshots --latest 2

# 2. Ver las copias y verificar la BD en un MySQL temporal (no toca producción)
sudo restaurar_taemoi.sh list
sudo restaurar_taemoi.sh db-verify latest               # debe terminar en "VERIFICACIÓN CORRECTA"

# 3. Restaurar ficheros en una carpeta aparte y compararlos
sudo restaurar_taemoi.sh files latest --target /root/simulacro-$(date +%F)
sudo diff -rq /root/simulacro-$(date +%F)/opt/taemoi/static_resources /opt/taemoi/static_resources
sudo grep -c APP_CIFRADO_CLAVE /root/simulacro-$(date +%F)/opt/taemoi/.env   # debe ser 1

# 4. Limpiar (el .env restaurado tiene secretos)
sudo rm -rf /root/simulacro-$(date +%F) /root/pw-simulacro
```

Compruebe que las filas de `db-verify` (alumnos, pagos…) cuadran con lo que muestra la web y
que la migración de Flyway es la esperada. Anote fecha, snapshot y resultado.

## Restaurar

`restaurar_taemoi.sh` nunca sobrescribe nada por defecto:

```bash
sudo restaurar_taemoi.sh list --type db                 # o files / all
sudo restaurar_taemoi.sh files latest --target /root/restauracion
sudo restaurar_taemoi.sh files 1a2b3c4d --target /root/r --include /opt/taemoi/static_resources/documentos
sudo restaurar_taemoi.sh db-verify 1a2b3c4d             # --keep deja el MySQL temporal para inspeccionarlo
sudo restaurar_taemoi.sh db-production 1a2b3c4d         # solo IMPRIME los pasos exactos
sudo restaurar_taemoi.sh db-production 1a2b3c4d --confirm-production
```

- **`files`** restaura en una carpeta vacía; rechaza `/`, `/opt/taemoi` y las carpetas de
  subidas. Para devolver ficheros a producción, cópielos a mano tras revisarlos (`rsync -a --dry-run`).
- **`db-verify`** crea un contenedor MySQL nuevo, sin red ni puertos, importa el volcado,
  muestra tablas, filas y la última migración de Flyway, y lo elimina al terminar.
- **`db-production`** sin `--confirm-production` solo imprime el plan. Con la opción, pide
  escribir `RESTAURAR <base de datos>` en una terminal y entonces:
  1. `docker stop taemoi-backend-prod` (antes de la copia previa, para que no se pierda nada);
  2. guarda la BD actual en restic (`db-pre-restore`). Si esta copia falla, la borra, vuelve a
     arrancar el backend y aborta sin tocar la BD;
  3. recrea la base de datos con su mismo juego de caracteres e importa el volcado;
  4. `docker start taemoi-backend-prod` (Flyway aplica las migraciones posteriores, si las hay).
  Si la importación falla, deja el backend parado y muestra cómo volver al estado anterior.

### Equipo nuevo (el mini PC se ha perdido)

1. Instale Ubuntu, Docker y `restic`; conecte y monte el USB en `/mnt/backups_usb`.
2. Guarde la contraseña de restic del gestor en `/root/.config/taemoi-backup/restic-password` (600).
3. `sudo RESTIC_REPOSITORY=/mnt/backups_usb/restic-taemoi restaurar_taemoi.sh files latest --target /root/r`
   (sin `/etc/taemoi-backup.conf` usa los valores por defecto).
4. Copie `.env`, `static_resources` y `certbot/conf` a `/opt/taemoi`, levante MySQL con
   `docker compose -f docker-compose.production.yml up -d database` y use `db-production`.

## Espacio en el USB de 128 GB

- restic deduplica y comprime: cada copia solo añade lo nuevo. Los volcados se guardan sin gzip
  a propósito, para que la deduplicación entre volcados funcione.
- Orden de magnitud: tamaño del repositorio ≈ fotos + documentos + unas pocas veces el volcado
  comprimido. La retención por defecto (42 cada 4 h = 7 días, 30 diarias, 12 semanales,
  12 mensuales) apenas suma espacio si la BD cambia poco.
- Durante las 2 semanas en paralelo, el script antiguo sigue ocupando espacio: `du -sh /mnt/backups_usb/*`.
- Si el uso del disco llega a `DISK_USAGE_ALERT_PERCENT` (85 %), la ejecución termina en FALLO
  para avisar. Opciones: bajar `KEEP_HOURLY`/`KEEP_DAILY` (se aplica en la siguiente copia), borrar
  los `.sql.gz` antiguos o cambiar a un disco mayor. **Nunca borre a mano ficheros dentro de
  `restic-taemoi/`.**
- Consultas útiles: `df -h /mnt/backups_usb`, `sudo restic -r /mnt/backups_usb/restic-taemoi --password-file /root/.config/taemoi-backup/restic-password stats --mode raw-data`.

## Leer los logs

```bash
sudo tail -n 50 /var/log/taemoi-backup.log
sudo grep -E 'FALLO|ERROR|WARN' /var/log/taemoi-backup.log | tail
sudo journalctl -t backup_taemoi --since "2 days ago"   # una línea de resumen por ejecución
```

Cada ejecución empieza con `=== Inicio` y termina con `=== OK` o con `FALLO: ... Paso: ...`.
Las líneas `restic |` y `mysqldump |` son la salida de esas órdenes. Mensajes frecuentes:

| Mensaje | Qué hacer |
|---------|-----------|
| `El disco USB NO está montado` | Conectar y montar el disco; revisar `/etc/fstab` |
| `repository is already locked` | Comprobar que no hay restic en marcha (`pgrep -a restic`) y, si no lo hay, `restic unlock` |
| `Otra copia o restauración sigue en curso` | Una ejecución anterior tarda demasiado; revisar el log anterior |
| `está truncado` / `MIN_DUMP_BYTES` | Revisar MySQL (`docker logs taemoi-mysql-prod`); el snapshot defectuoso ya se ha descartado |
| `AVISO dump-size-drop` | Comprobar en la web que no faltan datos; si es una limpieza legítima, no hay que hacer nada |
| `El reloj del sistema ... va por detrás` | Corregir la hora (`timedatectl`, NTP); la copia se hizo pero no se aplicó la retención |
| `restic X es demasiado antiguo` | Instalar restic 0.14 o posterior (ver "restic en Ubuntu 22.04") |
| `restic no pudo leer algunos ficheros` | Permisos o ficheros que cambian durante la copia; el detalle está en las líneas `restic |` |

## Copia fuera de casa (para más adelante)

El USB está junto al servidor: un robo, incendio o subida de tensión se llevaría ambos. Cuando
quiera una segunda copia, cree otro repositorio restic y copie los snapshots con `restic copy`
(no hace falta cambiar `backup_taemoi.sh`):

```bash
# Backblaze B2 (credenciales en variables de entorno, en un fichero root 600)
export B2_ACCOUNT_ID=... B2_ACCOUNT_KEY=...
OFFSITE=b2:nombre-del-bucket:taemoi
# Hetzner Storage Box (clave SSH de root; puerto 23 en /root/.ssh/config)
OFFSITE=sftp:uXXXXX@uXXXXX.your-storagebox.de:/restic-taemoi

# Crear el repositorio remoto con los mismos parámetros de troceado (deduplicación eficiente)
restic -r "$OFFSITE" init --from-repo /mnt/backups_usb/restic-taemoi \
  --from-password-file /root/.config/taemoi-backup/restic-password --copy-chunker-params

# Copiar (por ejemplo, una vez al día desde cron) y aplicar la misma retención allí
restic -r "$OFFSITE" copy --from-repo /mnt/backups_usb/restic-taemoi \
  --from-password-file /root/.config/taemoi-backup/restic-password --tag taemoi
restic -r "$OFFSITE" forget --tag taemoi --group-by host,tags \
  --keep-daily 30 --keep-weekly 12 --keep-monthly 12 --prune
```

El repositorio remoto puede tener su propia contraseña (guárdela también en el gestor).
Esto no está implementado ni probado: queda pendiente decidir proveedor y credenciales.

## restic en Ubuntu 22.04 (binario oficial)

Solo si `restic version` es anterior a 0.14. Instale la versión publicada en GitHub y verifique
la firma antes de usarla (compruebe la huella de la clave en la documentación oficial de restic,
apartado de instalación):

```bash
V=0.17.3
cd "$(mktemp -d)"
for f in "restic_${V}_linux_amd64.bz2" SHA256SUMS SHA256SUMS.asc; do
  wget "https://github.com/restic/restic/releases/download/v${V}/${f}"
done
# HUELLA: cópiela de la documentación oficial de restic (apartado de instalación); no la escriba de memoria.
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys HUELLA_DE_LA_CLAVE_DE_RESTIC
gpg --verify SHA256SUMS.asc SHA256SUMS                  # debe decir "Good signature"
sha256sum --check --ignore-missing SHA256SUMS           # debe decir "OK"
bunzip2 "restic_${V}_linux_amd64.bz2"
sudo install -o root -g root -m 755 "restic_${V}_linux_amd64" /usr/local/bin/restic
/usr/local/bin/restic version
```

Los scripts ponen `/usr/local/bin` antes que `/usr/bin` en su `PATH`, así que usan este binario
aunque el paquete de apt siga instalado.

## Decisiones de diseño

| Tema | Decisión |
|------|----------|
| Herramienta | restic: cifrado, deduplicación, retención y verificación integradas |
| Fallos | Código de salida ≠ 0, línea `FALLO` en el log, aviso. Sin copia silenciosa en el disco interno |
| Concurrencia | `flock` en `/run/taemoi-backup.lock` compartido por copia, comprobación y restauración en producción |
| Privacidad de los avisos | Sin líneas del log: solo paso, código, equipo y fecha |
| Secretos | Contraseña de restic solo en `RESTIC_PASSWORD_FILE` (root 600); el script rechaza `RESTIC_PASSWORD` en la configuración y una configuración con permisos de grupo/otros |
| Retención | `restic forget --prune` solo sobre snapshots `taemoi`, agrupados por equipo y etiquetas. No borra nada fuera del repositorio |
| Integridad | `--check` lee y verifica `CHECK_READ_DATA_SUBSET` (10 %) de los datos cada semana |
| Coherencia | La BD y los ficheros se guardan con unos segundos de diferencia: una subida justo en ese momento puede quedar solo en uno de los dos snapshots |
