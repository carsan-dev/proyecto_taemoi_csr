# MySQL initialization notes

The database schema is no longer bootstrapped from `docker-entrypoint-initdb.d`.

## Current strategy

- MySQL creates the database and user from environment variables (`MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`).
- Flyway applies the schema from `src-api/src/main/resources/db/migration`.
- Backend startup seeds only essential catalog/admin bootstrap data through `InicializadorDatos`.

## First deployment against an existing database

If the database already exists and does not yet have `flyway_schema_history`:

```text
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
```

After the first successful startup, change it back to `false`.

## Empty database deployments

For a fresh database, Flyway will create the schema automatically. Optional bootstrap admin credentials can still be provided through:

```text
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
APP_BOOTSTRAP_ADMIN_PASSWORD=<long-random-password>
APP_BOOTSTRAP_ADMIN_NOMBRE=Admin
APP_BOOTSTRAP_ADMIN_APELLIDOS=Bootstrap
```

## Manual restore from backup

```bash
docker-compose -f docker-compose.production.yml exec -T database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} < /secure/path/private_backup.sql
```

## Notes

- Production/student data must still be restored from secure backups outside Git.
- A fresh deployment from this public repository is expected to start without student data.
- The application can still start because essential catalog data and an optional bootstrap admin are handled by the backend initializer.
