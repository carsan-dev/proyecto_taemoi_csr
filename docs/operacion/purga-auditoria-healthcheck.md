### Purga de la auditoría del healthcheck (una sola vez)

Antes del hotfix, el healthcheck de Docker del backend (`wget` a `GET /api/eventos` cada 30 s) se auditaba y llenó `auditoria_evento` (~700 000 filas). Esas filas se borran con `scripts/mantenimiento/purgar-auditoria-healthcheck.sh`. Las visitas anónimas reales al mismo endpoint se conservan.

**Cuándo:** no lo ejecutes en torno a las 03:15. A esa hora corre `AuditoriaRetentionJob`, que borra auditoría antigua, y la verificación final podría dar un FALLO falso (código 5).

Orden:
1. Despliega el hotfix y espera al menos 5 minutos.
2. Haz una copia de seguridad y verifícala (`scripts/backup/backup_taemoi.sh`).
3. Comprueba el espacio libre: `df -h` en el host y `docker exec taemoi-mysql-prod df -h /var/lib/mysql`. El borrado hace crecer el binlog (formato ROW, cientos de MB) y `OPTIMIZE` necesita espacio libre similar al tamaño final de la tabla.
4. Desde `/opt/taemoi`, cuenta sin tocar nada:
   `bash scripts/mantenimiento/purgar-auditoria-healthcheck.sh` (equivale a `--dry-run`).
   Comprueba que "SE BORRARÍAN" ronda las 701 215 filas, que las conservadas sin IP local y sin Wget son unas 294 y que hay 0 filas del criterio en los últimos 5 minutos. Revisa también el bloque "Espacio": disco libre, tamaño de la tabla y binlog estimado.
5. Borra: `bash scripts/mantenimiento/purgar-auditoria-healthcheck.sh --ejecutar` y escribe el número exacto de filas que pide. Borra por lotes de 10 000 (autocommit) y muestra el progreso.
6. Opcional, fuera de horas: `--ejecutar --optimizar` hace además `OPTIMIZE TABLE auditoria_evento`, que reconstruye la tabla para devolver espacio al disco. Puede tardar.
7. Comprueba el final: tiene que aparecer "Verificación correcta." y el código de salida debe ser 0 (`echo $?`), con 0 filas restantes del criterio y las conservadas iguales que antes. Si sale el código 5, no repitas a ciegas:
   - Si hay filas restantes del criterio, el healthcheck sigue auditándose. Revisa el despliegue del hotfix y relanza.
   - Si han cambiado las conservadas, comprueba si coincidió con `AuditoriaRetentionJob` (logs del backend) u otro borrado. Si no tiene explicación, compara con la copia de seguridad antes de tocar nada más.

Códigos de salida:
- 0: correcto.
- 1: error de MySQL o Docker. Relanza: el script es idempotente y continúa solo.
- 2: uso incorrecto.
- 3: el healthcheck sigue activo (falta el hotfix; no se ha borrado nada).
- 4: confirmación incorrecta (no se ha borrado nada).
- 5: la verificación final ha fallado (ver el paso 7).
- 6: `OPTIMIZE` ha fallado. El borrado ya está hecho y verificado; reintenta solo `--ejecutar --optimizar` más tarde.

Si se interrumpe (Ctrl+C o caída de SSH), relánzalo y continuará con lo que falte. El script solo imprime recuentos y tamaños, nunca datos personales.
