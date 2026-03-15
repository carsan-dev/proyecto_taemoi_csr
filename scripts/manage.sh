#!/bin/bash

###############################################################################
# TaeMoi Management Script
# Quick commands for managing the production deployment
###############################################################################

COMPOSE_FILE="docker-compose.production.yml"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_usage() {
    cat << EOF
TaeMoi Management Script

Usage: sudo bash scripts/manage.sh [command]

Commands:
    start           Start all services
    stop            Stop all services
    restart         Restart all services
    status          Show status of all services
    logs            Show logs (follow mode)
    logs-backend    Show backend logs only
    logs-frontend   Show frontend logs only
    logs-db         Show database logs only
    build           Rebuild all containers
    update          Pull latest code and rebuild
    backup-db       Backup database
    backup-files    Backup uploaded files
    restore-db      Restore database from backup
    ssl-renew       Manually renew SSL certificates
    clean           Remove stopped containers and unused images
    health          Check health of all services
    shell-backend   Open shell in backend container
    shell-db        Open MySQL shell in database container
    stats           Show resource usage statistics

Examples:
    sudo bash scripts/manage.sh start
    sudo bash scripts/manage.sh logs
    sudo bash scripts/manage.sh backup-db

EOF
}

check_env() {
    if [ ! -f ".env.production" ]; then
        log_error ".env.production file not found!"
        exit 1
    fi
    source .env.production
}

cmd_start() {
    log_info "Starting TaeMoi services..."
    docker-compose -f $COMPOSE_FILE up -d
    log_info "Services started!"
}

cmd_stop() {
    log_info "Stopping TaeMoi services..."
    docker-compose -f $COMPOSE_FILE down
    log_info "Services stopped!"
}

cmd_restart() {
    log_info "Restarting TaeMoi services..."
    docker-compose -f $COMPOSE_FILE restart
    log_info "Services restarted!"
}

cmd_status() {
    log_info "Service status:"
    docker-compose -f $COMPOSE_FILE ps
}

cmd_logs() {
    log_info "Showing logs (Ctrl+C to exit)..."
    docker-compose -f $COMPOSE_FILE logs -f
}

cmd_logs_backend() {
    log_info "Showing backend logs (Ctrl+C to exit)..."
    docker-compose -f $COMPOSE_FILE logs -f backend
}

cmd_logs_frontend() {
    log_info "Showing frontend logs (Ctrl+C to exit)..."
    docker-compose -f $COMPOSE_FILE logs -f frontend
}

cmd_logs_db() {
    log_info "Showing database logs (Ctrl+C to exit)..."
    docker-compose -f $COMPOSE_FILE logs -f database
}

cmd_build() {
    log_info "Rebuilding containers..."
    docker-compose -f $COMPOSE_FILE build --no-cache
    log_info "Build complete!"
}

cmd_update() {
    log_info "Pulling latest code..."
    git pull origin main

    log_info "Stopping services..."
    docker-compose -f $COMPOSE_FILE down

    log_info "Rebuilding containers..."
    docker-compose -f $COMPOSE_FILE build --no-cache

    log_info "Starting services..."
    docker-compose -f $COMPOSE_FILE up -d

    log_info "Update complete!"
}

cmd_backup_db() {
    check_env
    BACKUP_FILE="backup_db_$(date +%Y%m%d_%H%M%S).sql"
    log_info "Creating database backup: $BACKUP_FILE"

    mkdir -p backups
    docker-compose -f $COMPOSE_FILE exec -T database \
        mysqldump -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} > backups/$BACKUP_FILE

    log_info "Database backup created: backups/$BACKUP_FILE"
}

cmd_backup_files() {
    log_info "Backing up uploaded files..."
    mkdir -p backups

    BACKUP_DATE=$(date +%Y%m%d_%H%M%S)

    # Backup images
    log_info "Backing up images..."
    docker run --rm \
        -v taemoi_app_imagenes:/source \
        -v $(pwd)/backups:/backup \
        alpine tar czf /backup/imagenes_${BACKUP_DATE}.tar.gz -C /source .

    # Backup documents
    log_info "Backing up documents..."
    docker run --rm \
        -v taemoi_app_documentos:/source \
        -v $(pwd)/backups:/backup \
        alpine tar czf /backup/documentos_${BACKUP_DATE}.tar.gz -C /source .

    log_info "File backups created in backups/ directory"
}

cmd_restore_db() {
    check_env

    if [ -z "$1" ]; then
        log_error "Please specify backup file"
        log_info "Usage: sudo bash scripts/manage.sh restore-db backups/backup_db_20240101.sql"
        exit 1
    fi

    if [ ! -f "$1" ]; then
        log_error "Backup file not found: $1"
        exit 1
    fi

    log_warn "This will restore the database from: $1"
    read -p "Are you sure? (yes/no): " -r
    if [[ ! $REPLY == "yes" ]]; then
        log_info "Restore cancelled"
        exit 0
    fi

    log_info "Restoring database..."
    docker-compose -f $COMPOSE_FILE exec -T database \
        mysql -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE} < $1

    log_info "Database restored successfully!"
}

cmd_ssl_renew() {
    log_info "Renewing SSL certificates..."
    docker-compose -f $COMPOSE_FILE exec certbot certbot renew
    docker-compose -f $COMPOSE_FILE restart frontend
    log_info "SSL certificates renewed!"
}

cmd_clean() {
    log_info "Cleaning up Docker resources..."
    docker system prune -f
    log_info "Cleanup complete!"
}

cmd_health() {
    log_info "Checking service health..."

    # Check backend
    if curl -f -s http://localhost:8080/api/eventos > /dev/null; then
        log_info "✓ Backend is healthy"
    else
        log_error "✗ Backend is not responding"
    fi

    # Check frontend
    if curl -f -s http://localhost/health > /dev/null; then
        log_info "✓ Frontend is healthy"
    else
        log_error "✗ Frontend is not responding"
    fi

    # Check database
    if docker-compose -f $COMPOSE_FILE exec -T database mysqladmin ping -h localhost -u root -p${MYSQL_ROOT_PASSWORD} > /dev/null 2>&1; then
        log_info "✓ Database is healthy"
    else
        log_error "✗ Database is not responding"
    fi
}

cmd_shell_backend() {
    log_info "Opening shell in backend container..."
    docker-compose -f $COMPOSE_FILE exec backend /bin/bash
}

cmd_shell_db() {
    check_env
    log_info "Opening MySQL shell..."
    docker-compose -f $COMPOSE_FILE exec database mysql -u root -p${MYSQL_ROOT_PASSWORD} ${MYSQL_DATABASE}
}

cmd_stats() {
    log_info "Resource usage statistics:"
    docker stats --no-stream
}

# Main script
if [ $# -eq 0 ]; then
    show_usage
    exit 0
fi

case "$1" in
    start)
        cmd_start
        ;;
    stop)
        cmd_stop
        ;;
    restart)
        cmd_restart
        ;;
    status)
        cmd_status
        ;;
    logs)
        cmd_logs
        ;;
    logs-backend)
        cmd_logs_backend
        ;;
    logs-frontend)
        cmd_logs_frontend
        ;;
    logs-db)
        cmd_logs_db
        ;;
    build)
        cmd_build
        ;;
    update)
        cmd_update
        ;;
    backup-db)
        cmd_backup_db
        ;;
    backup-files)
        cmd_backup_files
        ;;
    restore-db)
        cmd_restore_db "$2"
        ;;
    ssl-renew)
        cmd_ssl_renew
        ;;
    clean)
        cmd_clean
        ;;
    health)
        check_env
        cmd_health
        ;;
    shell-backend)
        cmd_shell_backend
        ;;
    shell-db)
        cmd_shell_db
        ;;
    stats)
        cmd_stats
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        log_error "Unknown command: $1"
        show_usage
        exit 1
        ;;
esac

exit 0
