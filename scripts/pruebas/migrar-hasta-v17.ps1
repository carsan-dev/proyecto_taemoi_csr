# Aplica las migraciones de producción (V1..V17) a la base de datos de pruebas con la imagen
# oficial de Flyway. Las V18+ quedan pendientes a propósito.
# Uso: .\scripts\pruebas\migrar-hasta-v17.ps1   (con el servicio "database" de docker-compose.pruebas.yml arrancado)
$ErrorActionPreference = 'Stop'

$raiz = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$migraciones = Join-Path $raiz 'src-api\src\main\resources\db\migration'
$red = if ($env:RED_PRUEBAS) { $env:RED_PRUEBAS } else { 'taemoi-pruebas_pruebas' }
$imagen = if ($env:FLYWAY_IMAGEN) { $env:FLYWAY_IMAGEN } else { 'flyway/flyway:11.7.2' }
$url = 'jdbc:mysql://database:3306/taemoi_pruebas?allowPublicKeyRetrieval=true&useSSL=false'

function Invoke-Flyway([string[]]$argumentos) {
    docker run --rm --network $red `
        -v "${migraciones}:/flyway/sql:ro" `
        $imagen `
        "-url=$url" -user=taemoi -password=taemoi-pruebas-local `
        -locations=filesystem:/flyway/sql -connectRetries=10 @argumentos
    if ($LASTEXITCODE -ne 0) { throw "Flyway termino con código $LASTEXITCODE" }
}

Invoke-Flyway @('-target=17', 'migrate')
Invoke-Flyway @('info')
