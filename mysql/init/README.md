# MySQL Initialization Scripts

This directory contains SQL scripts that are automatically executed when the MySQL container is created for the first time.

## How It Works

Docker MySQL automatically executes all `.sql` and `.sh` files in `/docker-entrypoint-initdb.d/` during container initialization, **in alphabetical order**.

**IMPORTANT**: Scripts only execute the **first time** the container is created (when the volume is empty).

## Current File Structure

```
mysql/init/
├── 01_ddl_schema_server.sql     # DDL - Creates database schema and tables (EXECUTES FIRST)
├── 02_migration_server.sql      # DML - Migrates data from Access database (EXECUTES SECOND)
├── migration_local.sql.bak      # Local development data (NOT executed - no numeric prefix)
└── README.md                    # This file
```

## Execution Order

The numeric prefixes ensure correct execution order:

1. **01_ddl_schema_server.sql** (FIRST)
   - Creates the database schema: `taemoi_db`
   - Creates all tables: `alumno`, `grupo`, `turno`, `evento`, `producto`, `convocatoria`, etc.
   - Defines indexes, foreign keys, and constraints
   - **Must execute BEFORE data migration**

2. **02_migration_server.sql** (SECOND)
   - Migrates production data from the Access database
   - Clears existing data (DELETE statements)
   - Inserts students, groups, schedules, products, exam calls, etc.
   - **Requires tables to exist (from step 1)**

## Production Deployment

For production deployment using `docker-compose.production.yml`:

### Automatic Initialization (First Deployment)

When you run `./scripts/deploy.sh production` for the first time:

1. ✅ MySQL container starts with empty volume
2. ✅ `01_ddl_schema_server.sql` executes → Creates schema and tables
3. ✅ `02_migration_server.sql` executes → Populates data
4. ✅ Backend starts after database health check passes
5. ✅ Application is ready with full production data

### Verification

The deploy script automatically verifies:
```bash
# Check schema created
docker-compose -f docker-compose.production.yml exec database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "SHOW TABLES;"

# Check data migrated
docker-compose -f docker-compose.production.yml exec database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "SELECT COUNT(*) FROM alumno;"
```

## Re-initialization

To re-run the initialization scripts (⚠️ **DESTROYS ALL DATA**):

```bash
# Stop containers and remove volumes
docker-compose -f docker-compose.production.yml down -v

# Verify volumes are removed
docker volume ls | grep taemoi

# Redeploy (will re-execute init scripts)
./scripts/deploy.sh production
```

## Local Development

For local development, you can use `migration_local.sql.bak`:

```bash
# Rename to enable execution
cd mysql/init
mv migration_local.sql.bak 02_migration_local.sql
mv 02_migration_server.sql migration_server.sql.bak

# Deploy locally
cd ../..
./scripts/deploy.sh local
```

**Note**: Only ONE migration file should have the `02_` prefix at a time.

## Manual Data Loading

If automatic initialization fails or you need manual control:

### Option 1: Execute from host

```bash
# Create schema
docker-compose -f docker-compose.production.yml exec -T database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_db < mysql/init/01_ddl_schema_server.sql

# Migrate data
docker-compose -f docker-compose.production.yml exec -T database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_db < mysql/init/02_migration_server.sql
```

### Option 2: Copy into container and execute

```bash
# Copy files
docker cp mysql/init/01_ddl_schema_server.sql taemoi-mysql-prod:/tmp/
docker cp mysql/init/02_migration_server.sql taemoi-mysql-prod:/tmp/

# Execute inside container
docker-compose -f docker-compose.production.yml exec database bash
mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_db < /tmp/01_ddl_schema_server.sql
mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_db < /tmp/02_migration_server.sql
exit
```

## Expected Data Counts (Production)

After successful migration, you should see:

```sql
USE taemoi_db;
SELECT COUNT(*) FROM alumno;              -- Students
SELECT COUNT(*) FROM grupo;               -- Groups
SELECT COUNT(*) FROM turno;               -- Schedules
SELECT COUNT(*) FROM evento;              -- Events
SELECT COUNT(*) FROM producto;            -- Products
SELECT COUNT(*) FROM convocatoria;        -- Exam calls
SELECT COUNT(*) FROM alumno_convocatoria; -- Student exam registrations
SELECT COUNT(*) FROM producto_alumno;     -- Product assignments
SELECT COUNT(*) FROM usuario;             -- User accounts
```

## Troubleshooting

### Scripts Not Executing

**Symptom**: No tables in database after deployment

**Causes**:
1. Volume already exists (scripts only run on first initialization)
2. Files not in `/docker-entrypoint-initdb.d/` inside container
3. Incorrect file permissions
4. SQL syntax errors

**Solutions**:

```bash
# Check if files are mounted in container
docker-compose -f docker-compose.production.yml exec database \
  ls -la /docker-entrypoint-initdb.d/

# Should show:
# 01_ddl_schema_server.sql
# 02_migration_server.sql

# View initialization logs
docker-compose -f docker-compose.production.yml logs database | grep -i "entrypoint"

# Remove volumes and redeploy
docker-compose -f docker-compose.production.yml down -v
./scripts/deploy.sh production
```

### Schema Created But No Data

**Symptom**: Tables exist but are empty

**Cause**: `02_migration_server.sql` failed to execute

**Solution**:

```bash
# Check migration script logs
docker-compose -f docker-compose.production.yml logs database | grep -A 20 "migration"

# Manually execute migration
docker-compose -f docker-compose.production.yml exec -T database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} taemoi_db < mysql/init/02_migration_server.sql
```

### Wrong Database Name

**Symptom**: `ERROR 1049 (42000): Unknown database 'taemoi_db'`

**Cause**: `.env` file has wrong `MYSQL_DATABASE` value

**Solution**:

```bash
# Edit .env file
nano .env

# Ensure it has:
MYSQL_DATABASE=taemoi_db

# Redeploy
docker-compose -f docker-compose.production.yml down -v
./scripts/deploy.sh production
```

### Backend Can't Connect

**Symptom**: Spring Boot fails with connection errors

**Causes**:
1. Database name mismatch in `.env`
2. Backend starting before database is ready

**Solutions**:

```bash
# Verify database is accessible
docker-compose -f docker-compose.production.yml exec database \
  mysql -u root -p${MYSQL_ROOT_PASSWORD} -e "SHOW DATABASES;"

# Check backend logs
docker-compose -f docker-compose.production.yml logs backend | grep -i "database\|connection"

# Restart backend after database is ready
docker-compose -f docker-compose.production.yml restart backend
```

## File Details

### 01_ddl_schema_server.sql

- **Size**: ~14 KB
- **Purpose**: Creates database structure
- **Contents**:
  - `CREATE SCHEMA taemoi_db`
  - Table definitions
  - Indexes and constraints
  - Foreign key relationships
- **Execution time**: < 1 second

### 02_migration_server.sql

- **Size**: ~7 MB
- **Purpose**: Populates production data
- **Contents**:
  - DELETE statements (clear existing data)
  - INSERT statements (students, products, exams, etc.)
  - Data from migrated Access database
- **Execution time**: 10-30 seconds

## Important Notes

1. **One-time execution**: Init scripts only run when the database volume is created for the first time
2. **Alphabetical order**: Files execute in alphabetical order (hence the numeric prefixes)
3. **Database name**: Must be `taemoi_db` to match both DDL and migration scripts
4. **Dependencies**: Migration script requires tables to exist (DDL must run first)
5. **Volume persistence**: Data persists in Docker volumes even after `docker-compose down`
6. **Complete reset**: Use `docker-compose down -v` to remove volumes and trigger re-initialization

## Related Files

- `../ddl_schema_server.sql` - Source DDL file (copied to `01_ddl_schema_server.sql`)
- `../Dockerfile` - MySQL container configuration
- `../../docker-compose.production.yml` - Production deployment configuration
- `../../scripts/deploy.sh` - Automated deployment script
- `../../.env` - Environment variables (must have `MYSQL_DATABASE=taemoi_db`)

---

**Last Updated**: 2025-11-30
