# Database Migrations

This directory contains database migration scripts that are run automatically during deployment.

## Migration Files

### migration_documento_to_relative_paths.sql

**Purpose:** Converts documento absolute file paths to relative paths for environment independence.

**When it runs:** Automatically during deployment via `deploy.sh`

**What it does:**
1. Converts old `/opt/taemoi/static_resources/documentos/` paths to `/var/www/app/documentos/`
2. Converts all absolute paths to relative paths (e.g., `/var/www/app/documentos/Documentos_Alumnos_Moiskimdo/...` → `Documentos_Alumnos_Moiskimdo/...`)
3. Verifies the migration by showing path types

**Why:**
- Old absolute paths break when moving between environments (dev/staging/production)
- Relative paths are resolved at runtime based on environment configuration
- Makes database portable between different server configurations

**Safe to run multiple times:** Yes, the migration is idempotent (won't cause issues if run multiple times)

## Manual Migration

If you need to run this migration manually:

```bash
# Production
docker-compose -f docker-compose.production.yml exec -T database mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_db < mysql/migrations/migration_documento_to_relative_paths.sql

# Local
docker-compose exec -T database mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_bbdd < mysql/migrations/migration_documento_to_relative_paths.sql
```

## Verify Migration

Check that paths are now relative:

```bash
# Production
docker-compose -f docker-compose.production.yml exec database mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "SELECT id, nombre, ruta FROM documento LIMIT 10;"

# Look for paths like: Documentos_Alumnos_Moiskimdo/123_NAME/file.pdf
# NOT like: /var/www/app/documentos/Documentos_Alumnos_Moiskimdo/123_NAME/file.pdf
```

## Code Changes

The migration works in conjunction with code changes in `DocumentoServiceImpl.java`:

- **guardarDocumento()**: Now saves relative paths to database
- **obtenerRecursoDocumento()**: Resolves relative paths to absolute at runtime
- **eliminarDocumento()**: Handles both relative and absolute paths (backward compatible)

This ensures new documents use relative paths, while old documents continue to work during the transition.
