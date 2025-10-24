#!/bin/bash
###############################################################################
# Script para inicializar archivos estáticos (imágenes y documentos)
# en contenedores Docker de TaeMoi
###############################################################################

set -e

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Funciones de log
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

###############################################################################
# Configuración
###############################################################################

# Nombre del contenedor backend
BACKEND_CONTAINER="taemoi-backend-prod"

# Rutas en el host
HOST_IMAGENES_DIR="./static_resources/imagenes"
HOST_DOCUMENTOS_DIR="./static_resources/documentos"

# Rutas en el contenedor
CONTAINER_IMAGENES_DIR="/var/www/app/imagenes"
CONTAINER_DOCUMENTOS_DIR="/var/www/app/documentos"

###############################################################################
# Verificaciones
###############################################################################

log_info "Iniciando copia de archivos estáticos..."

# Verificar que el contenedor está corriendo
if ! docker ps | grep -q $BACKEND_CONTAINER; then
    log_error "El contenedor $BACKEND_CONTAINER no está corriendo"
    log_info "Inicia los contenedores primero: docker-compose -f docker-compose.production.yml up -d"
    exit 1
fi

###############################################################################
# Copiar Imágenes
###############################################################################

if [ -d "$HOST_IMAGENES_DIR" ]; then
    log_info "Copiando imágenes al contenedor..."

    # Contar archivos
    IMG_COUNT=$(find "$HOST_IMAGENES_DIR" -type f | wc -l)
    log_info "Encontrados $IMG_COUNT archivos de imagen"

    # Copiar al contenedor
    docker cp "$HOST_IMAGENES_DIR/." "$BACKEND_CONTAINER:$CONTAINER_IMAGENES_DIR/"

    # Establecer permisos
    docker exec $BACKEND_CONTAINER chown -R 1000:1000 $CONTAINER_IMAGENES_DIR
    docker exec $BACKEND_CONTAINER chmod -R 755 $CONTAINER_IMAGENES_DIR

    log_info "✓ Imágenes copiadas correctamente"
else
    log_warn "Directorio de imágenes no encontrado: $HOST_IMAGENES_DIR"
    log_warn "Crea el directorio y coloca las imágenes allí"
fi

###############################################################################
# Copiar Documentos
###############################################################################

if [ -d "$HOST_DOCUMENTOS_DIR" ]; then
    log_info "Copiando documentos al contenedor..."

    # Contar archivos
    DOC_COUNT=$(find "$HOST_DOCUMENTOS_DIR" -type f | wc -l)
    log_info "Encontrados $DOC_COUNT archivos de documentos"

    # Copiar al contenedor
    docker cp "$HOST_DOCUMENTOS_DIR/." "$BACKEND_CONTAINER:$CONTAINER_DOCUMENTOS_DIR/"

    # Establecer permisos
    docker exec $BACKEND_CONTAINER chown -R 1000:1000 $CONTAINER_DOCUMENTOS_DIR
    docker exec $BACKEND_CONTAINER chmod -R 755 $CONTAINER_DOCUMENTOS_DIR

    log_info "✓ Documentos copiados correctamente"
else
    log_warn "Directorio de documentos no encontrado: $HOST_DOCUMENTOS_DIR"
    log_warn "Crea el directorio y coloca los documentos allí si es necesario"
fi

###############################################################################
# Verificación
###############################################################################

log_info "Verificando archivos en el contenedor..."

# Verificar imágenes
IMG_IN_CONTAINER=$(docker exec $BACKEND_CONTAINER find $CONTAINER_IMAGENES_DIR -type f 2>/dev/null | wc -l)
log_info "Archivos de imagen en contenedor: $IMG_IN_CONTAINER"

# Verificar documentos
DOC_IN_CONTAINER=$(docker exec $BACKEND_CONTAINER find $CONTAINER_DOCUMENTOS_DIR -type f 2>/dev/null | wc -l)
log_info "Archivos de documentos en contenedor: $DOC_IN_CONTAINER"

# Listar primeros archivos de imágenes
log_info "Primeras imágenes en contenedor:"
docker exec $BACKEND_CONTAINER ls $CONTAINER_IMAGENES_DIR/alumnos 2>/dev/null | head -5 || log_warn "No se encontraron imágenes de alumnos"

###############################################################################
# Finalización
###############################################################################

echo ""
log_info "=========================================="
log_info "Inicialización completada"
log_info "=========================================="
log_info "Imágenes: $IMG_IN_CONTAINER archivos"
log_info "Documentos: $DOC_IN_CONTAINER archivos"
echo ""
log_info "Prueba el acceso:"
log_info "  curl -I http://localhost:8080/imagenes/alumnos/1.jpg"
echo ""

exit 0
