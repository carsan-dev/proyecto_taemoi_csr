#!/bin/bash
###############################################################################
# Document Access Fix Script for Production Server
# This script fixes document URLs in the database
###############################################################################

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

echo ""
echo "=========================================="
echo "  Document URL Fix Script"
echo "=========================================="
echo ""

# Get database credentials from environment
if [ -f .env ]; then
    source .env
else
    log_error ".env file not found!"
    exit 1
fi

###############################################################################
# STEP 1: Analyze current document URLs
###############################################################################
log_step "1. Analyzing document URLs in database..."

echo "Sample of current URLs:"
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "
SELECT id, nombre, url
FROM documento
ORDER BY id
LIMIT 10;
" 2>/dev/null

# Check for common issues
log_info "Checking for URL issues..."

# Count URLs with percent encoding
PERCENT_COUNT=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "
SELECT COUNT(*) FROM documento WHERE url LIKE '%\%%';
" 2>/dev/null)

log_info "URLs with percent encoding (e.g., %20, %C3%): $PERCENT_COUNT"

# Count URLs with localhost
LOCALHOST_COUNT=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "
SELECT COUNT(*) FROM documento WHERE url LIKE '%localhost%';
" 2>/dev/null)

log_info "URLs with localhost: $LOCALHOST_COUNT"

# Count URLs without HTTPS
HTTP_COUNT=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "
SELECT COUNT(*) FROM documento WHERE url LIKE 'http://moiskimdo.es%';
" 2>/dev/null)

log_info "URLs using HTTP instead of HTTPS: $HTTP_COUNT"

###############################################################################
# STEP 2: Ask for confirmation
###############################################################################
echo ""
log_warn "This script will:"
log_warn "1. Create a backup of the documento table"
log_warn "2. Fix URL encoding (remove %, spaces, accents)"
log_warn "3. Replace localhost with moiskimdo.es"
log_warn "4. Ensure HTTPS is used"
echo ""

read -p "Do you want to proceed? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    log_info "Aborted by user"
    exit 0
fi

###############################################################################
# STEP 3: Create backup
###############################################################################
log_step "2. Creating backup of documento table..."

docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "
DROP TABLE IF EXISTS documento_backup_$(date +%Y%m%d);
CREATE TABLE documento_backup_$(date +%Y%m%d) AS SELECT * FROM documento;
" 2>/dev/null

BACKUP_COUNT=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "
SELECT COUNT(*) FROM documento_backup_$(date +%Y%m%d);
" 2>/dev/null)

log_info "✓ Backup created with $BACKUP_COUNT records"

###############################################################################
# STEP 4: Fix URLs
###############################################################################
log_step "3. Fixing document URLs..."

log_info "Removing URL encoding from URLs..."
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "
UPDATE documento
SET url = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    url,
    '%C3%A1', 'a'),  -- á
    '%C3%A9', 'e'),  -- é
    '%C3%AD', 'i'),  -- í
    '%C3%B3', 'o'),  -- ó
    '%C3%BA', 'u'),  -- ú
    '%C3%B1', 'n'),  -- ñ
    '%20', '_'),     -- space to underscore
    '%C3%81', 'A'),  -- Á
    '%C3%89', 'E'),  -- É
    '%C3%8D', 'I'),  -- Í
    '%C3%93', 'O')   -- Ó
WHERE url LIKE '%\%%';
" 2>/dev/null

log_info "Fixing localhost URLs..."
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "
UPDATE documento
SET url = REPLACE(url, 'http://localhost:8080', 'https://moiskimdo.es')
WHERE url LIKE '%localhost%';
" 2>/dev/null

log_info "Ensuring HTTPS..."
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "
UPDATE documento
SET url = REPLACE(url, 'http://moiskimdo.es', 'https://moiskimdo.es')
WHERE url LIKE 'http://moiskimdo.es%';
" 2>/dev/null

###############################################################################
# STEP 5: Verify fixes
###############################################################################
log_step "4. Verifying fixes..."

echo "Sample of fixed URLs:"
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "
SELECT id, nombre, url
FROM documento
ORDER BY id
LIMIT 10;
" 2>/dev/null

# Re-check issues
NEW_PERCENT_COUNT=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "
SELECT COUNT(*) FROM documento WHERE url LIKE '%\%%';
" 2>/dev/null)

NEW_LOCALHOST_COUNT=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "
SELECT COUNT(*) FROM documento WHERE url LIKE '%localhost%';
" 2>/dev/null)

NEW_HTTP_COUNT=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "
SELECT COUNT(*) FROM documento WHERE url LIKE 'http://moiskimdo.es%';
" 2>/dev/null)

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo "=========================================="
echo "  Fix Summary"
echo "=========================================="
echo ""
log_info "Before:"
log_info "  - URLs with percent encoding: $PERCENT_COUNT"
log_info "  - URLs with localhost: $LOCALHOST_COUNT"
log_info "  - URLs using HTTP: $HTTP_COUNT"
echo ""
log_info "After:"
log_info "  - URLs with percent encoding: $NEW_PERCENT_COUNT"
log_info "  - URLs with localhost: $NEW_LOCALHOST_COUNT"
log_info "  - URLs using HTTP: $NEW_HTTP_COUNT"
echo ""

if [ "$NEW_PERCENT_COUNT" -eq 0 ] && [ "$NEW_LOCALHOST_COUNT" -eq 0 ] && [ "$NEW_HTTP_COUNT" -eq 0 ]; then
    log_info "✓ All URLs fixed successfully!"
else
    log_warn "Some issues may remain. Check the URLs above."
fi

echo ""
log_info "Backup table: documento_backup_$(date +%Y%m%d)"
log_info "To restore backup if needed:"
log_info "  docker exec taemoi-mysql-prod mysql -u root -p\${MYSQL_ROOT_PASSWORD} -D taemoi_db -e \"DELETE FROM documento; INSERT INTO documento SELECT * FROM documento_backup_$(date +%Y%m%d);\""
echo ""
