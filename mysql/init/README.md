# MySQL Init Scripts

Este directorio contiene scripts SQL que se ejecutan automáticamente cuando se crea el contenedor MySQL por primera vez.

## Cómo Funciona

Docker MySQL ejecuta automáticamente todos los archivos `.sql` y `.sh` en `/docker-entrypoint-initdb.d/` durante la inicialización del contenedor.

**IMPORTANTE**: Los scripts solo se ejecutan la **primera vez** que se crea el contenedor (cuando el volumen está vacío).

## Orden de Ejecución

Los archivos se ejecutan en orden alfabético:

1. `00-init-db.sh` - Crea base de datos y usuario (ya incluido en mysql/init-db.sh)
2. `01-migration.sql` - Datos migrados desde Access (DEBES COPIARLO AQUÍ)

## Cómo Usar

### Opción 1: Carga Automática (Recomendado para primer despliegue)

1. **Copiar archivo de migración**:

```bash
# Windows
copy migration_output_v2.sql mysql\init\01-migration.sql

# Linux/Mac
cp migration_output_v2.sql mysql/init/01-migration.sql
```

2. **Asegurarse de que no hay volúmenes previos**:

```bash
# Eliminar volúmenes existentes (CUIDADO: borra todos los datos)
docker-compose down -v

# Verificar que no quedan volúmenes
docker volume ls | grep taemoi
```

3. **Iniciar contenedores**:

```bash
# Los datos se cargarán automáticamente
docker-compose up -d

# Ver logs para confirmar carga
docker-compose logs database | grep -i "migration"
```

4. **Verificar datos cargados**:

```bash
docker exec proyecto_taemoi_csr-database-1 mysql -u root -p"root" taemoidb \
  -e "SELECT COUNT(*) FROM alumno;"
```

### Opción 2: Carga Manual (Más control)

Si prefieres controlar manualmente cuándo se cargan los datos:

```bash
# NO copies migration_output_v2.sql a mysql/init/

# Inicia contenedores con base de datos vacía
docker-compose up -d

# Carga datos cuando estés listo
docker cp migration_output_v2.sql proyecto_taemoi_csr-database-1:/tmp/migration.sql
docker exec -i proyecto_taemoi_csr-database-1 mysql -u root -p"root" taemoidb < migration_output_v2.sql
```

## Estructura de Archivos

```
mysql/
├── Dockerfile                    # Imagen personalizada de MySQL
├── init-db.sh                    # Script de inicialización base
└── init/                         # Scripts de carga de datos
    ├── README.md                 # Este archivo
    └── 01-migration.sql          # DEBES COPIARLO AQUÍ (datos migrados)
```

## Notas Importantes

1. **Solo primera vez**: Los scripts init solo se ejecutan cuando el contenedor se crea por primera vez
2. **Recrear volumen**: Para volver a ejecutar los scripts, debes eliminar el volumen: `docker-compose down -v`
3. **Tamaño de archivo**: `migration_output_v2.sql` es ~2.5 MB (685 alumnos, 1132 exámenes, 2751 pagos)
4. **Tiempo de carga**: La carga inicial puede tardar 30-60 segundos
5. **Base de datos**: Asegúrate de que `MYSQL_DATABASE` en `.env` coincide con el nombre usado en el SQL

## Verificación

Después de iniciar los contenedores, verifica:

```bash
# Ver logs de inicialización
docker-compose logs database | grep -A 5 -B 5 "migration"

# Conectarse a MySQL
docker exec -it proyecto_taemoi_csr-database-1 mysql -u root -p

# Verificar tablas
mysql> USE taemoidb;
mysql> SHOW TABLES;
mysql> SELECT COUNT(*) FROM alumno;
mysql> SELECT COUNT(*) FROM grupo;
mysql> SELECT COUNT(*) FROM turno;
```

Conteos esperados:
- alumno: 685 (146 activos, 539 inactivos)
- grado: 19
- grupo: 2
- turno: 21
- producto: 435
- convocatoria: 65
- alumno_convocatoria: 1132
- producto_alumno: 2751

## Solución de Problemas

### Script no se ejecuta

```bash
# Verificar que el archivo está en el contenedor
docker exec proyecto_taemoi_csr-database-1 ls -la /docker-entrypoint-initdb.d/

# Ver logs completos de inicialización
docker-compose logs database | less
```

### Error de sintaxis SQL

```bash
# Verificar que el archivo SQL es válido
head -100 mysql/init/01-migration.sql

# Verificar encoding (debe ser UTF-8)
file mysql/init/01-migration.sql
```

### Volumen ya existe

```bash
# Eliminar volumen existente (CUIDADO: borra datos)
docker-compose down
docker volume rm proyecto_taemoi_csr_db_data

# O eliminar todos los volúmenes
docker-compose down -v
```
