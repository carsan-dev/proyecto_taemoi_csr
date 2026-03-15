#!/bin/bash

###############################################################################
# SSL Certificate Setup Script for TaeMoi
# Obtains Let's Encrypt SSL certificates for the domain
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if .env exists
if [ ! -f ".env" ]; then
    log_error ".env file not found!"
    exit 1
fi

# Load environment variables
source .env

if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
    log_error "DOMAIN and EMAIL must be set in .env"
    exit 1
fi

log_info "Setting up SSL certificates for: $DOMAIN"

# Create directories
mkdir -p certbot/conf
mkdir -p certbot/www

# Check if certificates already exist
if [ -d "certbot/conf/live/$DOMAIN" ]; then
    log_info "Certificates already exist for $DOMAIN"
    read -p "Do you want to renew them? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 0
    fi
    RENEW_FLAG="--force-renewal"
else
    RENEW_FLAG=""
fi

# Start a temporary nginx container for ACME challenge
log_info "Starting temporary web server for ACME challenge..."
docker run --rm -d \
    --name nginx-ssl-setup \
    -p 80:80 \
    -v $(pwd)/certbot/www:/var/www/certbot \
    nginx:alpine

# Wait for nginx to start
sleep 3

# Obtain certificate
log_info "Obtaining SSL certificate from Let's Encrypt..."
docker run --rm \
    -v $(pwd)/certbot/conf:/etc/letsencrypt \
    -v $(pwd)/certbot/www:/var/www/certbot \
    certbot/certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email $EMAIL \
    --agree-tos \
    --no-eff-email \
    $RENEW_FLAG \
    -d $DOMAIN \
    -d www.$DOMAIN

# Stop temporary nginx
log_info "Stopping temporary web server..."
docker stop nginx-ssl-setup

log_info "SSL certificates obtained successfully!"
log_info "Certificates are stored in: certbot/conf/live/$DOMAIN/"
log_info ""
log_info "You can now deploy the application with: sudo bash scripts/deploy.sh"

exit 0
