# Devuelve la BD de pruebas al estado V17 guardado en scripts/pruebas/backup-v17.sql (para el backend antes).
$ErrorActionPreference = 'Stop'
$raiz = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$compose = Join-Path $raiz 'docker-compose.pruebas.yml'
$backup = Join-Path $PSScriptRoot 'backup-v17.sql'
if (-not (Test-Path $backup)) { throw "Falta $backup (ver respaldar-v17.sh)" }
docker compose -f $compose stop backend | Out-Null
docker compose -f $compose exec -T database mysql -uroot -proot-pruebas-local -e "DROP DATABASE IF EXISTS taemoi_pruebas; CREATE DATABASE taemoi_pruebas CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON taemoi_pruebas.* TO 'taemoi'@'%';"
if ($LASTEXITCODE -ne 0) { throw "No se pudo recrear la BD" }
cmd /c "docker compose -f `"$compose`" exec -T database mysql --default-character-set=utf8mb4 -uroot -proot-pruebas-local < `"$backup`""
if ($LASTEXITCODE -ne 0) { throw "La restauracion termino con codigo $LASTEXITCODE" }
Write-Host 'BD restaurada a V17. Arranca el backend con: docker compose -f docker-compose.pruebas.yml up -d backend'
