# Carga scripts/pruebas/datos-sinteticos.sql en la base de datos de pruebas (servicio "database"
# de docker-compose.pruebas.yml). Requiere haber migrado antes a V17 (migrar-hasta-v17.ps1).
# Se puede repetir: el SQL vacía antes las tablas que rellena.
$ErrorActionPreference = 'Stop'

$raiz = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$sql = Join-Path $PSScriptRoot 'datos-sinteticos.sql'
if (-not (Test-Path $sql)) {
    throw "No existe $sql; generalo con: python scripts/pruebas/generar_datos_sinteticos.py"
}

# cmd /c con redirección "<" evita que PowerShell recodifique el fichero UTF-8 al canalizarlo.
$compose = Join-Path $raiz 'docker-compose.pruebas.yml'
cmd /c "docker compose -f `"$compose`" exec -T database mysql --default-character-set=utf8mb4 -utaemoi -ptaemoi-pruebas-local taemoi_pruebas < `"$sql`""
if ($LASTEXITCODE -ne 0) { throw "La carga termino con código $LASTEXITCODE" }
Write-Host 'Datos sinteticos cargados en taemoi_pruebas (localhost:3308).'
