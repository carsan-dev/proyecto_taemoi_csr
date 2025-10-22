#!/bin/bash

###############################################################################
# TaeMoi Production Deployment Script for Ubuntu Server
# This script handles the deployment of the TaeMoi application with HTTPS
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Log functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   log_error "This script must be run as root (use sudo)"
   exit 1
fi

# Check if .env.production exists
if [ ! -f ".env.production" ]; then
    log_error ".env.production file not found!"
    log_info "Please copy .env.production.example to .env.production and configure it"
    exit 1
fi

# Load environment variables
source .env.production

# Validate required environment variables
required_vars=("DOMAIN" "EMAIL" "MYSQL_ROOT_PASSWORD" "MYSQL_DATABASE" "MYSQL_USER" "MYSQL_PASSWORD" "JWT_SECRET")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        log_error "Required environment variable $var is not set in .env.production"
        exit 1
    fi
done

log_info "Starting TaeMoi deployment..."

# Update system packages
log_info "Updating system packages..."
apt-get update -qq
apt-get upgrade -y -qq

# Install required dependencies
log_info "Installing required dependencies..."
apt-get install -y -qq \
    docker.io \
    docker-compose \
    curl \
    git \
    ufw \
    certbot \
    python3-certbot-nginx

# Enable and start Docker
log_info "Enabling Docker service..."
systemctl enable docker
systemctl start docker

# Configure firewall
log_info "Configuring firewall..."
ufw --force enable
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow 80/tcp
ufw allow 443/tcp
ufw status

# Create necessary directories
log_info "Creating necessary directories..."
mkdir -p certbot/conf
mkdir -p certbot/www
mkdir -p nginx
mkdir -p mysql/init

# Update nginx configuration with actual domain
log_info "Updating Nginx configuration with domain: $DOMAIN..."
sed -i "s/your-domain.com/$DOMAIN/g" nginx/nginx-production.conf

# Stop any existing containers
log_info "Stopping existing containers..."
docker-compose -f docker-compose.production.yml down 2>/dev/null || true

# Check if SSL certificates exist
if [ ! -d "certbot/conf/live/$DOMAIN" ]; then
    log_warn "SSL certificates not found. Obtaining Let's Encrypt certificates..."

    # Start nginx temporarily for certificate challenge
    docker run --rm -d \
        --name nginx-certbot \
        -p 80:80 \
        -v $(pwd)/certbot/www:/var/www/certbot \
        nginx:alpine

    # Obtain certificate
    docker run --rm \
        -v $(pwd)/certbot/conf:/etc/letsencrypt \
        -v $(pwd)/certbot/www:/var/www/certbot \
        certbot/certbot certonly \
        --webroot \
        --webroot-path=/var/www/certbot \
        --email $EMAIL \
        --agree-tos \
        --no-eff-email \
        -d $DOMAIN \
        -d www.$DOMAIN

    # Stop temporary nginx
    docker stop nginx-certbot

    log_info "SSL certificates obtained successfully!"
else
    log_info "SSL certificates already exist"
fi

# Build and start containers
log_info "Building and starting Docker containers..."
docker-compose -f docker-compose.production.yml build --no-cache
docker-compose -f docker-compose.production.yml up -d

# Wait for services to be healthy
log_info "Waiting for services to be ready..."
sleep 10

# Check service health
log_info "Checking service health..."
docker-compose -f docker-compose.production.yml ps

# Display status
log_info ""
log_info "=========================================="
log_info "Deployment completed successfully!"
log_info "=========================================="
log_info ""
log_info "Your application should be available at:"
log_info "  - https://$DOMAIN"
log_info "  - https://www.$DOMAIN"
log_info ""
log_info "To view logs:"
log_info "  docker-compose -f docker-compose.production.yml logs -f"
log_info ""
log_info "To stop the application:"
log_info "  docker-compose -f docker-compose.production.yml down"
log_info ""
log_info "SSL certificates will auto-renew via certbot"
log_info ""

exit 0
