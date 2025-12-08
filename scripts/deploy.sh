#!/bin/bash

# TaeMoi - Deployment Script
# Automates the deployment process

# Capture start time
START_TIME=$(date +%s)

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}"
echo "============================================"
echo "    TaeMoi Deployment Script"
echo "============================================"
echo -e "${NC}"

# Function to print step
step() {
    echo -e "${BLUE}==>${NC} $1"
}

# Function to print success
success() {
    echo -e "${GREEN}✓${NC} $1"
}

# Function to print error and exit
fail() {
    echo -e "${RED}✗ ERROR:${NC} $1"
    exit 1
}

# Function to print warning
warn() {
    echo -e "${YELLOW}⚠ WARNING:${NC} $1"
}

# Determine environment and compose file
ENVIRONMENT=${1:-production}
if [ "$ENVIRONMENT" = "production" ]; then
    COMPOSE_FILE="docker-compose.production.yml"
    PORT_HTTP=80
    PORT_HTTPS=443
    PORT_BACKEND=8080
elif [ "$ENVIRONMENT" = "local" ]; then
    COMPOSE_FILE="docker-compose.yml"
    PORT_HTTP=80
    PORT_BACKEND=8080
else
    fail "Invalid environment. Use: ./deploy.sh [production|local]"
fi

echo -e "${BLUE}Environment: ${ENVIRONMENT}${NC}"
echo -e "${BLUE}Compose file: ${COMPOSE_FILE}${NC}"
echo ""

# Check if running from project root
if [ ! -f "$COMPOSE_FILE" ]; then
    fail "${COMPOSE_FILE} not found. Run this script from the project root directory."
fi

# Check for .env file
if [ ! -f .env ]; then
    fail ".env file not found. Copy .env.production.template to .env and configure it."
fi

# Load environment variables to check database name
source .env

# Verify database name matches migration script
if [ "$ENVIRONMENT" = "production" ]; then
    if [ "$MYSQL_DATABASE" != "taemoi_db" ]; then
        warn "MYSQL_DATABASE should be 'taemoi_db' for production (migration_server.sql expects this)"
        warn "Current value: $MYSQL_DATABASE"
        echo -e "${YELLOW}Continue anyway? (y/n)${NC}"
        read -r response
        if [ "$response" != "y" ]; then
            fail "Deployment cancelled. Please update .env file."
        fi
    fi
fi

# Run verification script first
step "Running pre-deployment verification..."
if [ -f scripts/verify-deployment.sh ]; then
    bash scripts/verify-deployment.sh || fail "Pre-deployment verification failed. Fix errors and try again."
else
    warn "Verification script not found. Skipping verification."
fi

# Stop and remove existing containers
step "Stopping existing containers..."
docker-compose -f "$COMPOSE_FILE" down 2>/dev/null || true
success "Existing containers stopped"

echo ""
step "Building Docker images..."
docker-compose -f "$COMPOSE_FILE" build || fail "Docker build failed"
success "Docker images built successfully"

echo ""
step "Starting services..."
docker-compose -f "$COMPOSE_FILE" up -d || fail "Failed to start services"
success "Services started"

echo ""
step "Waiting for services to be ready..."

# Wait for database
echo "Waiting for database..."
for i in {1..60}; do
    if docker-compose -f "$COMPOSE_FILE" exec -T database mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} 2>/dev/null | grep -q "mysqld is alive"; then
        success "Database is ready"
        break
    fi
    echo -n "."
    sleep 2
    if [ $i -eq 60 ]; then
        echo ""
        warn "Database health check timeout. Continuing anyway..."
        break
    fi
done

# Check if schema and migration were executed
if [ "$ENVIRONMENT" = "production" ]; then
    step "Verifying database initialization..."

    # Check if tables exist (DDL executed)
    TABLE_CHECK=$(docker-compose -f "$COMPOSE_FILE" exec -T database mysql -u root -p${MYSQL_ROOT_PASSWORD} -D ${MYSQL_DATABASE} -e "SHOW TABLES;" 2>/dev/null | wc -l)
    if [ "$TABLE_CHECK" -gt 1 ]; then
        success "Schema created (01_ddl_schema_server.sql executed)"
    else
        warn "No tables found. DDL script may not have executed."
        warn "Check: docker-compose -f $COMPOSE_FILE logs database"
        warn "Init files should be in mysql/init/: 01_ddl_schema_server.sql, 02_migration_server.sql"
    fi

    # Check if data was migrated (check alumno table row count)
    if [ "$TABLE_CHECK" -gt 1 ]; then
        ALUMNO_COUNT=$(docker-compose -f "$COMPOSE_FILE" exec -T database mysql -u root -p${MYSQL_ROOT_PASSWORD} -D ${MYSQL_DATABASE} -e "SELECT COUNT(*) FROM alumno;" 2>/dev/null | tail -n 1)
        if [ "$ALUMNO_COUNT" -gt 0 ]; then
            success "Data migrated (02_migration_server.sql executed - ${ALUMNO_COUNT} students found)"
        else
            warn "No student data found. Migration script may not have executed."
            warn "Check: docker-compose -f $COMPOSE_FILE logs database"
        fi
    fi
fi

# Wait for backend
echo "Waiting for backend..."
for i in {1..90}; do
    if curl -s http://localhost:${PORT_BACKEND}/api/eventos > /dev/null 2>&1; then
        success "Backend is ready"
        break
    fi
    echo -n "."
    sleep 2
    if [ $i -eq 90 ]; then
        echo ""
        warn "Backend health check timeout. Check logs: docker-compose -f $COMPOSE_FILE logs backend"
        break
    fi
done

# Check frontend
if [ "$ENVIRONMENT" = "production" ] || [ "$PORT_HTTP" = "80" ]; then
    echo "Waiting for frontend..."
    for i in {1..30}; do
        if curl -s http://localhost:${PORT_HTTP} > /dev/null 2>&1; then
            success "Frontend is ready"
            break
        fi
        echo -n "."
        sleep 2
        if [ $i -eq 30 ]; then
            echo ""
            warn "Frontend health check timeout. Check logs: docker-compose -f $COMPOSE_FILE logs frontend"
            break
        fi
    done
fi

echo ""
step "Checking container status..."
docker-compose -f "$COMPOSE_FILE" ps

echo ""
echo -e "${GREEN}"
echo "============================================"
echo "    Deployment Completed!"
echo "============================================"
echo -e "${NC}"

# Calculate and display deployment time
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo ""
echo -e "${BLUE}⏱  Deployment time: ${MINUTES}m ${SECONDS}s${NC}"

echo ""
echo "Environment: ${ENVIRONMENT}"
echo ""
echo "Services running:"
if [ "$ENVIRONMENT" = "production" ]; then
    echo "  - Frontend:  http://localhost (port 80/443)"
    echo "  - Backend:   Internal (accessed via Nginx proxy)"
    echo "  - Database:  Internal (taemoi-network)"
else
    echo "  - Frontend:  http://localhost:${PORT_HTTP}"
    echo "  - Backend:   http://localhost:${PORT_BACKEND}"
    echo "  - Database:  localhost:3307"
fi
echo ""
echo "Useful commands:"
echo "  - View logs:        docker-compose -f $COMPOSE_FILE logs -f"
echo "  - View backend:     docker-compose -f $COMPOSE_FILE logs -f backend"
echo "  - View database:    docker-compose -f $COMPOSE_FILE logs -f database"
echo "  - Stop services:    docker-compose -f $COMPOSE_FILE down"
echo "  - Restart service:  docker-compose -f $COMPOSE_FILE restart <service>"
echo "  - Enter container:  docker-compose -f $COMPOSE_FILE exec <service> bash"
echo ""
if [ "$ENVIRONMENT" = "production" ]; then
    echo "Production-specific commands:"
    echo "  - Check DB migration: docker-compose -f $COMPOSE_FILE exec database mysql -u root -p${MYSQL_ROOT_PASSWORD} -D ${MYSQL_DATABASE} -e 'SHOW TABLES;'"
    echo "  - Manual migration:   docker-compose -f $COMPOSE_FILE exec -T database mysql -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} < mysql/init/migration_server.sql"
    echo ""
fi
echo "Next steps:"
echo "  1. Test the application in your browser"
echo "  2. Check logs if any services show issues: docker-compose -f $COMPOSE_FILE logs -f backend"
if [ "$ENVIRONMENT" = "production" ]; then
    echo "  3. Verify database migration completed successfully"
    echo "  4. Configure SSL/HTTPS certificates (if not done)"
    echo "  5. Setup automated backups"
    echo "  6. Configure firewall rules"
    echo "  7. Test all application features"
fi
echo ""
