#!/bin/bash

# TaeMoi - 502 Error Diagnostic Script
# Helps diagnose 502 Bad Gateway errors in production deployment

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}"
echo "============================================"
echo "    TaeMoi - 502 Error Diagnostics"
echo "============================================"
echo -e "${NC}"

# Function to print section header
section() {
    echo ""
    echo -e "${BLUE}=== $1 ===${NC}"
}

# Function to print success
success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Function to print error
error() {
    echo -e "${RED}✗${NC} $1"
}

# Function to print warning
warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Check if docker-compose is available
if ! command -v docker &> /dev/null; then
    error "Docker is not installed or not in PATH"
    exit 1
fi

# Determine compose file
COMPOSE_FILE="docker-compose.production.yml"
if [ ! -f "$COMPOSE_FILE" ]; then
    COMPOSE_FILE="docker-compose.yml"
fi

section "1. Container Status"
docker ps -a --filter "name=taemoi" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

section "2. Backend Container Logs (last 50 lines)"
echo -e "${YELLOW}Looking for errors in backend logs...${NC}"
docker logs taemoi-backend-prod --tail 50 2>&1 | grep -i "error\|exception\|failed\|refused" || echo "No obvious errors found"

section "3. Backend Health Check"
if docker ps | grep -q taemoi-backend-prod; then
    echo "Testing backend connectivity from inside container..."
    docker exec taemoi-backend-prod wget --spider -q http://localhost:8080/api/eventos 2>&1
    if [ $? -eq 0 ]; then
        success "Backend is responding on port 8080"
    else
        error "Backend is NOT responding on port 8080"
    fi
else
    error "Backend container is not running"
fi

section "4. Database Connection"
if docker ps | grep -q taemoi-mysql-prod; then
    echo "Checking database health..."
    docker exec taemoi-mysql-prod mysqladmin ping -h localhost 2>&1 | grep -q "mysqld is alive"
    if [ $? -eq 0 ]; then
        success "Database is healthy"
    else
        error "Database is not responding"
    fi
else
    error "Database container is not running"
fi

section "5. Network Connectivity"
echo "Testing if frontend can reach backend..."
if docker ps | grep -q taemoi-frontend-prod; then
    docker exec taemoi-frontend-prod wget --spider -q http://backend:8080/api/eventos 2>&1
    if [ $? -eq 0 ]; then
        success "Frontend can reach backend"
    else
        error "Frontend CANNOT reach backend (this is likely your issue!)"
        warn "Check if containers are on the same Docker network"
    fi
else
    error "Frontend container is not running"
fi

section "6. Nginx Configuration Test"
if docker ps | grep -q taemoi-frontend-prod; then
    echo "Testing nginx configuration..."
    docker exec taemoi-frontend-prod nginx -t 2>&1
    if [ $? -eq 0 ]; then
        success "Nginx configuration is valid"
    else
        error "Nginx configuration has errors"
    fi
fi

section "7. SSL Certificate Check"
if docker exec taemoi-frontend-prod test -f /etc/letsencrypt/live/moiskimdo.es/fullchain.pem 2>/dev/null; then
    success "SSL certificates found"
else
    error "SSL certificates NOT found"
    warn "If using HTTPS nginx config, this will cause failures"
    warn "Use nginx-production-http-only.conf or set up SSL certificates first"
fi

section "8. Environment Variables Check"
echo "Checking critical environment variables in backend..."
docker exec taemoi-backend-prod printenv | grep -E "SPRING_PROFILES_ACTIVE|SPRING_DATASOURCE_URL|JWT_SECRET|CORS_ALLOWED_ORIGIN" | sed 's/=.*/=***HIDDEN***/'

section "9. Port Accessibility"
echo "Checking if services are listening on expected ports..."
if docker ps | grep -q taemoi-backend-prod; then
    docker exec taemoi-backend-prod netstat -tuln 2>/dev/null | grep 8080 || warn "Backend not listening on port 8080"
fi

section "10. Recent Container Restarts"
echo "Checking if containers are restarting (indicates crash loops)..."
RESTART_COUNT=$(docker inspect taemoi-backend-prod --format='{{.RestartCount}}' 2>/dev/null || echo "N/A")
echo "Backend restart count: $RESTART_COUNT"
if [ "$RESTART_COUNT" != "N/A" ] && [ "$RESTART_COUNT" -gt 0 ]; then
    warn "Backend has restarted $RESTART_COUNT times - check logs for crash reasons"
fi

echo ""
echo -e "${BLUE}"
echo "============================================"
echo "    Diagnosis Complete"
echo "============================================"
echo -e "${NC}"

echo ""
echo "Common Solutions:"
echo "  1. Backend not starting:"
echo "     - Check: docker logs taemoi-backend-prod"
echo "     - Fix: Verify database schema exists, check environment variables"
echo ""
echo "  2. SSL certificate errors:"
echo "     - Temporarily use HTTP-only config: ./nginx/nginx-production-http-only.conf"
echo "     - Update docker-compose.production.yml volume mount"
echo ""
echo "  3. Network connectivity issues:"
echo "     - Restart all services: docker-compose -f $COMPOSE_FILE restart"
echo "     - Rebuild: docker-compose -f $COMPOSE_FILE up --build -d"
echo ""
echo "  4. Database schema validation errors:"
echo "     - Run migration scripts in mysql/init/"
echo "     - Or temporarily change ddl-auto to 'update' in application-production.properties"
echo ""
