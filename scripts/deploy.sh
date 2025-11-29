#!/bin/bash

# TaeMoi - Deployment Script
# Automates the deployment process

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

# Check if running from project root
if [ ! -f docker-compose.yml ]; then
    fail "docker-compose.yml not found. Run this script from the project root directory."
fi

# Run verification script first
step "Running pre-deployment verification..."
if [ -f scripts/verify-deployment.sh ]; then
    bash scripts/verify-deployment.sh || fail "Pre-deployment verification failed. Fix errors and try again."
else
    echo -e "${YELLOW}Warning: Verification script not found. Skipping verification.${NC}"
fi

echo ""
step "Building Docker images..."
docker-compose build || fail "Docker build failed"
success "Docker images built successfully"

echo ""
step "Starting services..."
docker-compose up -d || fail "Failed to start services"
success "Services started"

echo ""
step "Waiting for services to be ready..."

# Wait for database
echo "Waiting for database..."
for i in {1..30}; do
    if docker-compose exec -T database mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} 2>/dev/null | grep -q "mysqld is alive"; then
        success "Database is ready"
        break
    fi
    echo -n "."
    sleep 2
    if [ $i -eq 30 ]; then
        echo ""
        echo -e "${YELLOW}Warning: Database health check timeout. Continuing anyway...${NC}"
        break
    fi
done

# Wait for backend
echo "Waiting for backend..."
for i in {1..60}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        success "Backend is ready"
        break
    fi
    echo -n "."
    sleep 2
    if [ $i -eq 60 ]; then
        echo ""
        echo -e "${YELLOW}Warning: Backend health check timeout. Check logs: docker-compose logs backend${NC}"
        break
    fi
done

# Check frontend
echo "Waiting for frontend..."
for i in {1..30}; do
    if curl -s http://localhost > /dev/null 2>&1; then
        success "Frontend is ready"
        break
    fi
    echo -n "."
    sleep 2
    if [ $i -eq 30 ]; then
        echo ""
        echo -e "${YELLOW}Warning: Frontend health check timeout. Check logs: docker-compose logs frontend${NC}"
        break
    fi
done

echo ""
step "Checking container status..."
docker-compose ps

echo ""
echo -e "${GREEN}"
echo "============================================"
echo "    Deployment Completed!"
echo "============================================"
echo -e "${NC}"

echo ""
echo "Services running:"
echo "  - Frontend: http://localhost"
echo "  - Backend:  http://localhost:8080"
echo "  - Database: localhost:3307"
echo ""
echo "Useful commands:"
echo "  - View logs:       docker-compose logs -f"
echo "  - View specific:   docker-compose logs -f backend"
echo "  - Stop services:   docker-compose down"
echo "  - Restart service: docker-compose restart <service>"
echo ""
echo "Next steps:"
echo "  1. Test the application in your browser"
echo "  2. Check logs if any services show issues"
echo "  3. Configure SSL/HTTPS (see DEPLOYMENT_CHECKLIST.md Step 8)"
echo "  4. Setup backups (see DEPLOYMENT_CHECKLIST.md Step 11)"
echo "  5. Configure firewall (see DEPLOYMENT_CHECKLIST.md Step 9)"
echo ""
