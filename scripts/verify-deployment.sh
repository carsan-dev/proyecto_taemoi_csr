#!/bin/bash

# TaeMoi - Pre-Deployment Verification Script
# Run this before deploying to catch configuration issues early

set -e

echo "============================================"
echo "TaeMoi Pre-Deployment Verification"
echo "============================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

# Function to print error
error() {
    echo -e "${RED}[ERROR]${NC} $1"
    ((ERRORS++))
}

# Function to print warning
warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
    ((WARNINGS++))
}

# Function to print success
success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

echo "Step 1: Checking .env file..."
echo "----------------------------------------"

# Check if .env exists
if [ ! -f .env ]; then
    error ".env file not found! Copy .env.production.template to .env first."
else
    success ".env file exists"

    # Check for placeholder values
    if grep -q "your-" .env; then
        error ".env file contains placeholder values (your-*). Replace all placeholders!"
        echo "  Found placeholders:"
        grep "your-" .env | sed 's/^/    /'
    else
        success "No placeholder values found in .env"
    fi

    # Check required variables
    REQUIRED_VARS=(
        "MYSQL_ROOT_PASSWORD"
        "MYSQL_PASSWORD"
        "JWT_SECRET"
        "SPRING_MAIL_USERNAME"
        "SPRING_MAIL_PASSWORD"
        "CORS_ALLOWED_ORIGIN"
    )

    for var in "${REQUIRED_VARS[@]}"; do
        if ! grep -q "^${var}=" .env; then
            error "Required variable $var not found in .env"
        else
            # Check if variable is not empty
            value=$(grep "^${var}=" .env | cut -d '=' -f2-)
            if [ -z "$value" ]; then
                error "Variable $var is empty in .env"
            else
                success "Variable $var is set"
            fi
        fi
    done

    # Check JWT_SECRET length (should be at least 32 characters)
    JWT_SECRET=$(grep "^JWT_SECRET=" .env | cut -d '=' -f2- | tr -d '"')
    if [ ${#JWT_SECRET} -lt 32 ]; then
        warning "JWT_SECRET is too short (${#JWT_SECRET} chars). Recommended: 43+ chars from 'openssl rand -base64 32'"
    else
        success "JWT_SECRET length is adequate (${#JWT_SECRET} chars)"
    fi

    # Check CORS uses HTTPS
    CORS_ORIGIN=$(grep "^CORS_ALLOWED_ORIGIN=" .env | cut -d '=' -f2-)
    if [[ $CORS_ORIGIN == http://* ]]; then
        warning "CORS_ALLOWED_ORIGIN uses HTTP. Use HTTPS in production!"
    else
        success "CORS_ALLOWED_ORIGIN uses HTTPS"
    fi
fi

echo ""
echo "Step 2: Checking Docker configuration..."
echo "----------------------------------------"

# Check if Docker is installed
if command -v docker &> /dev/null; then
    success "Docker is installed ($(docker --version))"
else
    error "Docker is not installed"
fi

# Check if Docker Compose is installed
if command -v docker-compose &> /dev/null; then
    success "Docker Compose is installed ($(docker-compose --version))"
else
    error "Docker Compose is not installed"
fi

# Check if user can run docker without sudo
if docker ps &> /dev/null; then
    success "User can run Docker commands"
else
    warning "User may need sudo for Docker. Add user to docker group: sudo usermod -aG docker \$USER"
fi

echo ""
echo "Step 3: Checking Docker Compose file..."
echo "----------------------------------------"

if [ -f docker-compose.yml ]; then
    success "docker-compose.yml exists"

    # Validate docker-compose file
    if docker-compose config > /dev/null 2>&1; then
        success "docker-compose.yml is valid"
    else
        error "docker-compose.yml has syntax errors"
        docker-compose config
    fi
else
    error "docker-compose.yml not found"
fi

echo ""
echo "Step 4: Checking frontend build files..."
echo "----------------------------------------"

# Check if SSR files are disabled
if [ -f src-frontend/server.ts ]; then
    error "SSR file server.ts should be renamed to server.ts.unused"
else
    success "SSR files are disabled"
fi

# Check tsconfig.app.json
if grep -q "main.server.ts" src-frontend/tsconfig.app.json 2>/dev/null; then
    error "tsconfig.app.json still references SSR files"
else
    success "tsconfig.app.json is correct"
fi

# Check if frontend dependencies are installed
if [ -d src-frontend/node_modules ]; then
    success "Frontend node_modules exists"
else
    warning "Frontend dependencies not installed. Run: cd src-frontend && npm install"
fi

echo ""
echo "Step 5: Checking backend configuration..."
echo "----------------------------------------"

# Check if pom.xml exists
if [ -f src-api/pom.xml ]; then
    success "Backend pom.xml exists"
else
    error "Backend pom.xml not found"
fi

# Check if wait-for-it.sh exists
if [ -f src-api/wait-for-it.sh ]; then
    success "wait-for-it.sh exists"
    if [ -x src-api/wait-for-it.sh ]; then
        success "wait-for-it.sh is executable"
    else
        warning "wait-for-it.sh is not executable. Run: chmod +x src-api/wait-for-it.sh"
    fi
else
    error "wait-for-it.sh not found in src-api/"
fi

echo ""
echo "Step 6: Checking ports availability..."
echo "----------------------------------------"

# Check if ports are available
PORTS=(80 8080 3307)
for port in "${PORTS[@]}"; do
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        warning "Port $port is already in use"
    else
        success "Port $port is available"
    fi
done

echo ""
echo "Step 7: Checking disk space..."
echo "----------------------------------------"

# Check available disk space (need at least 5GB)
AVAILABLE=$(df . | tail -1 | awk '{print $4}')
AVAILABLE_GB=$((AVAILABLE / 1024 / 1024))

if [ $AVAILABLE_GB -lt 5 ]; then
    warning "Low disk space: ${AVAILABLE_GB}GB available. Recommended: 10GB+"
else
    success "Sufficient disk space: ${AVAILABLE_GB}GB available"
fi

echo ""
echo "============================================"
echo "Verification Summary"
echo "============================================"

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed! Ready to deploy.${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Build: docker-compose build"
    echo "  2. Deploy: docker-compose up -d"
    echo "  3. Check logs: docker-compose logs -f"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠ ${WARNINGS} warning(s) found${NC}"
    echo "Review warnings above. You can proceed with deployment, but address warnings for production."
    exit 0
else
    echo -e "${RED}✗ ${ERRORS} error(s) and ${WARNINGS} warning(s) found${NC}"
    echo "Fix errors above before deploying."
    exit 1
fi
