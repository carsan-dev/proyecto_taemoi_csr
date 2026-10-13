-- Multi-escuela, fase 1: índices para servir ficheros por URL (AccesoArchivos).
-- Los ficheros nuevos se buscan por ruta exacta (org-<id>/…) dentro de la escuela activa:
-- Hibernate añade organizacion_id = ? (@TenantId), por eso el índice es (organizacion_id, ruta).
-- ruta es VARCHAR(500) utf8mb4: se indexa un prefijo de 255 caracteres (basta para la igualdad).
-- Cada paso está protegido para poder repetirse (mismo patrón que V19).

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'imagen'
              AND INDEX_NAME = 'idx_imagen_organizacion_ruta'
        ),
        'SELECT ''idx_imagen_organizacion_ruta already exists''',
        'ALTER TABLE imagen ADD INDEX idx_imagen_organizacion_ruta (organizacion_id, ruta(255))'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'documento'
              AND INDEX_NAME = 'idx_documento_organizacion_ruta'
        ),
        'SELECT ''idx_documento_organizacion_ruta already exists''',
        'ALTER TABLE documento ADD INDEX idx_documento_organizacion_ruta (organizacion_id, ruta(255))'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
