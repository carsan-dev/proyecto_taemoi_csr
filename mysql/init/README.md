# MySQL initialization scripts

This directory contains the SQL files executed by Docker MySQL on first startup.

## Current structure

```text
mysql/init/
|- 01_ddl_schema_server.sql
`- README.md
```

## Important security change

- The repository no longer includes production data, student data, or Access exports.
- The old SQL migration artifacts were removed from the repository.
- Real data must be restored from a secure backup outside Git.

## What the scripts do now

1. `01_ddl_schema_server.sql`
   - creates the schema and tables

## First secure deployment on an empty database

You now have two safe options:

1. Provision a bootstrap admin through environment variables:

```text
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
APP_BOOTSTRAP_ADMIN_PASSWORD=<long-random-password>
APP_BOOTSTRAP_ADMIN_NOMBRE=Admin
APP_BOOTSTRAP_ADMIN_APELLIDOS=Bootstrap
```

2. Restore a private database backup from a secure source that is not stored in this repository.

After the first secure login, remove `APP_BOOTSTRAP_ADMIN_PASSWORD` from the environment.

## Manual restore from backup

When using a private SQL backup stored outside Git:

```bash
docker-compose -f docker-compose.production.yml exec -T database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_db < /secure/path/private_backup.sql
```

## Notes

- Init scripts only run the first time the MySQL volume is created.
- Recreating the volume re-runs the schema bootstrap.
- A fresh deployment from this public repository is expected to start without student data.
- The application can still start because essential catalog data and an optional bootstrap admin are handled by the backend initializer.
