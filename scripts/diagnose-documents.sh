#!/bin/bash
###############################################################################
# Document Access Diagnostic Script
# Run this on your production server to diagnose document access issues
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
echo "  Document Access Diagnostic"
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
# STEP 1: Check if containers are running
###############################################################################
log_step "1. Checking container status..."
if docker ps | grep -q "taemoi-backend-prod"; then
    log_info "✓ Backend container is running"
else
    log_error "✗ Backend container is NOT running!"
    exit 1
fi

if docker ps | grep -q "taemoi-frontend-prod"; then
    log_info "✓ Frontend container is running"
else
    log_error "✗ Frontend container is NOT running!"
    exit 1
fi

###############################################################################
# STEP 2: Check static files exist on host
###############################################################################
log_step "2. Checking static files on host..."

if [ -d "static_resources/documentos/Documentos_Alumnos_Moiskimdo" ]; then
    DOC_COUNT=$(find static_resources/documentos/Documentos_Alumnos_Moiskimdo -type f | wc -l)
    log_info "✓ Found $DOC_COUNT document files on host"

    # Show a sample file
    SAMPLE_FILE=$(find static_resources/documentos/Documentos_Alumnos_Moiskimdo -type f | head -1)
    log_info "  Sample: $SAMPLE_FILE"
else
    log_error "✗ static_resources/documentos/Documentos_Alumnos_Moiskimdo not found!"
fi

###############################################################################
# STEP 3: Check files are accessible inside backend container
###############################################################################
log_step "3. Checking files inside backend container..."

CONTAINER_DOC_COUNT=$(docker exec taemoi-backend-prod find /var/www/app/documentos/Documentos_Alumnos_Moiskimdo -type f 2>/dev/null | wc -l)
if [ "$CONTAINER_DOC_COUNT" -gt 0 ]; then
    log_info "✓ Found $CONTAINER_DOC_COUNT documents in container"
else
    log_error "✗ No documents found in container at /var/www/app/documentos/"
    log_warn "  Bind mount might not be working!"
fi

# Check bind mount
log_info "Checking bind mount configuration..."
docker inspect taemoi-backend-prod | grep -A 3 "documentos" || log_warn "Could not find mount info"

###############################################################################
# STEP 4: Check database document URLs
###############################################################################
log_step "4. Checking document URLs in database..."

echo "Getting sample document URLs from database..."
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "SELECT id, nombre, url FROM documento LIMIT 5;" 2>/dev/null || log_error "Could not query database"

###############################################################################
# STEP 5: Test direct access to backend
###############################################################################
log_step "5. Testing direct access to backend (port 8080)..."

# Get a sample document path
SAMPLE_DOC_PATH=$(docker exec taemoi-backend-prod find /var/www/app/documentos/Documentos_Alumnos_Moiskimdo -type f -name "*.pdf" 2>/dev/null | head -1)

if [ -n "$SAMPLE_DOC_PATH" ]; then
    # Extract the relative path
    RELATIVE_PATH=$(echo $SAMPLE_DOC_PATH | sed 's|/var/www/app/||')
    log_info "Testing: http://localhost:8080/$RELATIVE_PATH"

    # Test from inside backend container
    RESPONSE=$(docker exec taemoi-backend-prod curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/$RELATIVE_PATH" 2>/dev/null)

    if [ "$RESPONSE" = "200" ]; then
        log_info "✓ Backend returns 200 OK"
    elif [ "$RESPONSE" = "302" ]; then
        log_error "✗ Backend returns 302 REDIRECT (OAuth2 issue!)"
        log_warn "  Document is being redirected - Security configuration problem!"
    elif [ "$RESPONSE" = "404" ]; then
        log_error "✗ Backend returns 404 NOT FOUND"
        log_warn "  File exists but backend can't serve it - ResourceHandler issue!"
    else
        log_error "✗ Backend returns $RESPONSE"
    fi
else
    log_error "Could not find sample document to test"
fi

###############################################################################
# STEP 6: Test access through nginx
###############################################################################
log_step "6. Testing access through nginx (port 80/443)..."

if [ -n "$SAMPLE_DOC_PATH" ]; then
    RELATIVE_PATH=$(echo $SAMPLE_DOC_PATH | sed 's|/var/www/app/||')

    # Test HTTP
    HTTP_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost/$RELATIVE_PATH" 2>/dev/null)
    log_info "HTTP Response: $HTTP_RESPONSE"

    if [ "$HTTP_RESPONSE" = "200" ]; then
        log_info "✓ Nginx proxy works correctly (HTTP)"
    elif [ "$HTTP_RESPONSE" = "302" ] || [ "$HTTP_RESPONSE" = "301" ]; then
        log_warn "Nginx returns redirect - checking where it redirects to..."
        REDIRECT_URL=$(curl -s -I "http://localhost/$RELATIVE_PATH" 2>/dev/null | grep -i "location:" | awk '{print $2}' | tr -d '\r')
        log_info "  Redirects to: $REDIRECT_URL"

        if echo "$REDIRECT_URL" | grep -q "oauth2\|google"; then
            log_error "✗ REDIRECTING TO OAUTH2! This is the problem!"
        fi
    else
        log_error "✗ Nginx returns $HTTP_RESPONSE"
    fi
fi

###############################################################################
# STEP 7: Check backend logs for errors
###############################################################################
log_step "7. Checking backend logs for errors..."

echo "Last 20 lines of backend log:"
docker logs taemoi-backend-prod --tail 20 2>&1 | grep -E "ERROR|WARN|documentos|OAuth2" || log_info "No relevant errors found"

###############################################################################
# STEP 8: Test with actual URL from database
###############################################################################
log_step "8. Testing with actual URL from database..."

# Get one URL from database
DB_URL=$(docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "SELECT url FROM documento LIMIT 1;" 2>/dev/null | tr -d '\r')

if [ -n "$DB_URL" ]; then
    log_info "Database URL: $DB_URL"

    # Test this exact URL
    URL_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" "$DB_URL" 2>/dev/null)
    log_info "Response for database URL: $URL_RESPONSE"

    if [ "$URL_RESPONSE" = "302" ]; then
        log_error "✗ Database URL redirects to OAuth2!"
        REDIRECT_LOC=$(curl -s -I "$DB_URL" 2>/dev/null | grep -i "location:" | awk '{print $2}' | tr -d '\r')
        log_info "  Redirects to: $REDIRECT_LOC"
    fi
else
    log_warn "No document URL found in database"
fi

###############################################################################
# SUMMARY
###############################################################################
echo ""
echo "=========================================="
echo "  Diagnostic Summary"
echo "=========================================="
echo ""
log_info "Container Status: OK"
log_info "Files on host: $DOC_COUNT files"
log_info "Files in container: $CONTAINER_DOC_COUNT files"
log_info "Backend response: $RESPONSE"
log_info "Nginx response: $HTTP_RESPONSE"
echo ""
log_info "If backend returns 302, the issue is in SecurityConfiguration.java"
log_info "If nginx returns 302, check nginx-production.conf proxy configuration"
log_info "If files in container = 0, check docker-compose.production.yml bind mounts"
echo ""
