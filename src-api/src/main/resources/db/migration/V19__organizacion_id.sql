-- Multi-escuela, fase 1: organizacion_id en todas las tablas de negocio.
-- Patrón por tabla, pensado para no reconstruir tablas grandes (auditoria_evento):
--   1. ADD COLUMN ... NOT NULL DEFAULT 1: en MySQL 8.0 es instantáneo y deja todas las
--      filas existentes en la organización 1 (Taemoi) sin reescribirlas.
--   2. DROP DEFAULT (solo metadatos): la base NO tiene organización por defecto; toda
--      inserción debe indicarla (si no, falla por NOT NULL / clave ajena).
--   3. Índice (en línea) y clave ajena a organizacion(id) añadida con foreign_key_checks = 0
--      para evitar la copia de la tabla: todos los valores son 1, que existe (V18).
-- Después, los únicos pasan a ser por organización.
-- No llevan organizacion_id: usuario, usuario_rol, tablas de unión (alumno_grupo,
-- alumno_turno, preinscripcion_turno), configuracion_sistema ni flyway_schema_history.
-- Cada paso está protegido para poder repetirse sobre una base que ya lo tenga
-- (las bases de producción anteriores a Flyway pueden tener nombres de índices distintos).

SET @schema_name = DATABASE();

-- ----------------------------------------------------------------------------
-- alumno
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''alumno.organizacion_id already exists''',
        'ALTER TABLE alumno ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE alumno ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND INDEX_NAME = 'idx_alumno_organizacion'
        ),
        'SELECT ''idx_alumno_organizacion already exists''',
        'ALTER TABLE alumno ADD INDEX idx_alumno_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND CONSTRAINT_NAME = 'fk_alumno_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_alumno_organizacion already exists''',
        'ALTER TABLE alumno ADD CONSTRAINT fk_alumno_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- alumno_deporte
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_deporte'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''alumno_deporte.organizacion_id already exists''',
        'ALTER TABLE alumno_deporte ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE alumno_deporte ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_deporte'
              AND INDEX_NAME = 'idx_alumno_deporte_organizacion'
        ),
        'SELECT ''idx_alumno_deporte_organizacion already exists''',
        'ALTER TABLE alumno_deporte ADD INDEX idx_alumno_deporte_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_deporte'
              AND CONSTRAINT_NAME = 'fk_alumno_deporte_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_alumno_deporte_organizacion already exists''',
        'ALTER TABLE alumno_deporte ADD CONSTRAINT fk_alumno_deporte_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- alumno_convocatoria
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_convocatoria'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''alumno_convocatoria.organizacion_id already exists''',
        'ALTER TABLE alumno_convocatoria ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE alumno_convocatoria ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_convocatoria'
              AND INDEX_NAME = 'idx_alumno_convocatoria_organizacion'
        ),
        'SELECT ''idx_alumno_convocatoria_organizacion already exists''',
        'ALTER TABLE alumno_convocatoria ADD INDEX idx_alumno_convocatoria_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_convocatoria'
              AND CONSTRAINT_NAME = 'fk_alumno_convocatoria_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_alumno_convocatoria_organizacion already exists''',
        'ALTER TABLE alumno_convocatoria ADD CONSTRAINT fk_alumno_convocatoria_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- alumno_reto_diario_log
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_reto_diario_log'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''alumno_reto_diario_log.organizacion_id already exists''',
        'ALTER TABLE alumno_reto_diario_log ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE alumno_reto_diario_log ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_reto_diario_log'
              AND INDEX_NAME = 'idx_alumno_reto_diario_log_organizacion'
        ),
        'SELECT ''idx_alumno_reto_diario_log_organizacion already exists''',
        'ALTER TABLE alumno_reto_diario_log ADD INDEX idx_alumno_reto_diario_log_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno_reto_diario_log'
              AND CONSTRAINT_NAME = 'fk_alumno_reto_diario_log_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_alumno_reto_diario_log_organizacion already exists''',
        'ALTER TABLE alumno_reto_diario_log ADD CONSTRAINT fk_alumno_reto_diario_log_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- auditoria_evento
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'auditoria_evento'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''auditoria_evento.organizacion_id already exists''',
        'ALTER TABLE auditoria_evento ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE auditoria_evento ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'auditoria_evento'
              AND INDEX_NAME = 'idx_auditoria_evento_organizacion'
        ),
        'SELECT ''idx_auditoria_evento_organizacion already exists''',
        'ALTER TABLE auditoria_evento ADD INDEX idx_auditoria_evento_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'auditoria_evento'
              AND CONSTRAINT_NAME = 'fk_auditoria_evento_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_auditoria_evento_organizacion already exists''',
        'ALTER TABLE auditoria_evento ADD CONSTRAINT fk_auditoria_evento_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- categoria
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'categoria'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''categoria.organizacion_id already exists''',
        'ALTER TABLE categoria ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE categoria ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'categoria'
              AND INDEX_NAME = 'idx_categoria_organizacion'
        ),
        'SELECT ''idx_categoria_organizacion already exists''',
        'ALTER TABLE categoria ADD INDEX idx_categoria_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'categoria'
              AND CONSTRAINT_NAME = 'fk_categoria_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_categoria_organizacion already exists''',
        'ALTER TABLE categoria ADD CONSTRAINT fk_categoria_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- convocatoria
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'convocatoria'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''convocatoria.organizacion_id already exists''',
        'ALTER TABLE convocatoria ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE convocatoria ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'convocatoria'
              AND INDEX_NAME = 'idx_convocatoria_organizacion'
        ),
        'SELECT ''idx_convocatoria_organizacion already exists''',
        'ALTER TABLE convocatoria ADD INDEX idx_convocatoria_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'convocatoria'
              AND CONSTRAINT_NAME = 'fk_convocatoria_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_convocatoria_organizacion already exists''',
        'ALTER TABLE convocatoria ADD CONSTRAINT fk_convocatoria_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- documento
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'documento'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''documento.organizacion_id already exists''',
        'ALTER TABLE documento ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE documento ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'documento'
              AND INDEX_NAME = 'idx_documento_organizacion'
        ),
        'SELECT ''idx_documento_organizacion already exists''',
        'ALTER TABLE documento ADD INDEX idx_documento_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'documento'
              AND CONSTRAINT_NAME = 'fk_documento_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_documento_organizacion already exists''',
        'ALTER TABLE documento ADD CONSTRAINT fk_documento_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- evento
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'evento'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''evento.organizacion_id already exists''',
        'ALTER TABLE evento ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE evento ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'evento'
              AND INDEX_NAME = 'idx_evento_organizacion'
        ),
        'SELECT ''idx_evento_organizacion already exists''',
        'ALTER TABLE evento ADD INDEX idx_evento_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'evento'
              AND CONSTRAINT_NAME = 'fk_evento_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_evento_organizacion already exists''',
        'ALTER TABLE evento ADD CONSTRAINT fk_evento_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- grado
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grado'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''grado.organizacion_id already exists''',
        'ALTER TABLE grado ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE grado ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grado'
              AND INDEX_NAME = 'idx_grado_organizacion'
        ),
        'SELECT ''idx_grado_organizacion already exists''',
        'ALTER TABLE grado ADD INDEX idx_grado_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'grado'
              AND CONSTRAINT_NAME = 'fk_grado_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_grado_organizacion already exists''',
        'ALTER TABLE grado ADD CONSTRAINT fk_grado_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- grupo
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grupo'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''grupo.organizacion_id already exists''',
        'ALTER TABLE grupo ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE grupo ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grupo'
              AND INDEX_NAME = 'idx_grupo_organizacion'
        ),
        'SELECT ''idx_grupo_organizacion already exists''',
        'ALTER TABLE grupo ADD INDEX idx_grupo_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'grupo'
              AND CONSTRAINT_NAME = 'fk_grupo_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_grupo_organizacion already exists''',
        'ALTER TABLE grupo ADD CONSTRAINT fk_grupo_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- imagen
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'imagen'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''imagen.organizacion_id already exists''',
        'ALTER TABLE imagen ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE imagen ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'imagen'
              AND INDEX_NAME = 'idx_imagen_organizacion'
        ),
        'SELECT ''idx_imagen_organizacion already exists''',
        'ALTER TABLE imagen ADD INDEX idx_imagen_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'imagen'
              AND CONSTRAINT_NAME = 'fk_imagen_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_imagen_organizacion already exists''',
        'ALTER TABLE imagen ADD CONSTRAINT fk_imagen_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- plantilla_preinscripcion
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'plantilla_preinscripcion'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''plantilla_preinscripcion.organizacion_id already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE plantilla_preinscripcion ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'plantilla_preinscripcion'
              AND INDEX_NAME = 'idx_plantilla_preinscripcion_organizacion'
        ),
        'SELECT ''idx_plantilla_preinscripcion_organizacion already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD INDEX idx_plantilla_preinscripcion_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'plantilla_preinscripcion'
              AND CONSTRAINT_NAME = 'fk_plantilla_preinscripcion_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_plantilla_preinscripcion_organizacion already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD CONSTRAINT fk_plantilla_preinscripcion_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- preinscripcion
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'preinscripcion'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''preinscripcion.organizacion_id already exists''',
        'ALTER TABLE preinscripcion ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE preinscripcion ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'preinscripcion'
              AND INDEX_NAME = 'idx_preinscripcion_organizacion'
        ),
        'SELECT ''idx_preinscripcion_organizacion already exists''',
        'ALTER TABLE preinscripcion ADD INDEX idx_preinscripcion_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'preinscripcion'
              AND CONSTRAINT_NAME = 'fk_preinscripcion_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_preinscripcion_organizacion already exists''',
        'ALTER TABLE preinscripcion ADD CONSTRAINT fk_preinscripcion_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- producto
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'producto'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''producto.organizacion_id already exists''',
        'ALTER TABLE producto ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE producto ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'producto'
              AND INDEX_NAME = 'idx_producto_organizacion'
        ),
        'SELECT ''idx_producto_organizacion already exists''',
        'ALTER TABLE producto ADD INDEX idx_producto_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'producto'
              AND CONSTRAINT_NAME = 'fk_producto_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_producto_organizacion already exists''',
        'ALTER TABLE producto ADD CONSTRAINT fk_producto_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- producto_alumno
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'producto_alumno'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''producto_alumno.organizacion_id already exists''',
        'ALTER TABLE producto_alumno ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE producto_alumno ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'producto_alumno'
              AND INDEX_NAME = 'idx_producto_alumno_organizacion'
        ),
        'SELECT ''idx_producto_alumno_organizacion already exists''',
        'ALTER TABLE producto_alumno ADD INDEX idx_producto_alumno_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'producto_alumno'
              AND CONSTRAINT_NAME = 'fk_producto_alumno_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_producto_alumno_organizacion already exists''',
        'ALTER TABLE producto_alumno ADD CONSTRAINT fk_producto_alumno_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- registro_pendiente
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'registro_pendiente'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''registro_pendiente.organizacion_id already exists''',
        'ALTER TABLE registro_pendiente ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE registro_pendiente ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'registro_pendiente'
              AND INDEX_NAME = 'idx_registro_pendiente_organizacion'
        ),
        'SELECT ''idx_registro_pendiente_organizacion already exists''',
        'ALTER TABLE registro_pendiente ADD INDEX idx_registro_pendiente_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'registro_pendiente'
              AND CONSTRAINT_NAME = 'fk_registro_pendiente_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_registro_pendiente_organizacion already exists''',
        'ALTER TABLE registro_pendiente ADD CONSTRAINT fk_registro_pendiente_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- turno
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'turno'
              AND COLUMN_NAME = 'organizacion_id'
        ),
        'SELECT ''turno.organizacion_id already exists''',
        'ALTER TABLE turno ADD COLUMN organizacion_id BIGINT NOT NULL DEFAULT 1'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE turno ALTER COLUMN organizacion_id DROP DEFAULT;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'turno'
              AND INDEX_NAME = 'idx_turno_organizacion'
        ),
        'SELECT ''idx_turno_organizacion already exists''',
        'ALTER TABLE turno ADD INDEX idx_turno_organizacion (organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'turno'
              AND CONSTRAINT_NAME = 'fk_turno_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_turno_organizacion already exists''',
        'ALTER TABLE turno ADD CONSTRAINT fk_turno_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ============================================================================
-- Únicos por organización
-- ============================================================================

-- alumno: UNIQUE (numero_expediente) -> UNIQUE (organizacion_id, numero_expediente).
-- Primero se crea el compuesto y después se borran todos los únicos de una sola columna
-- sobre numero_expediente, sea cual sea su nombre (baseline: UKi2otd4eccxkj7jwqeygr3wgoj).
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND INDEX_NAME = 'uk_alumno_organizacion_expediente'
        ),
        'SELECT ''uk_alumno_organizacion_expediente already exists''',
        'ALTER TABLE alumno ADD CONSTRAINT uk_alumno_organizacion_expediente UNIQUE (organizacion_id, numero_expediente)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drops = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', unicos.index_name, '`') SEPARATOR ', ')
    FROM (
        SELECT s.INDEX_NAME AS index_name
        FROM information_schema.STATISTICS s
        WHERE s.TABLE_SCHEMA = @schema_name
          AND s.TABLE_NAME = 'alumno'
          AND s.NON_UNIQUE = 0
          AND s.INDEX_NAME <> 'PRIMARY'
        GROUP BY s.INDEX_NAME
        HAVING COUNT(*) = 1
           AND MAX(s.COLUMN_NAME) = 'numero_expediente'
    ) unicos
);
SET @sql = IF(
    @drops IS NULL,
    'SELECT ''alumno has no single-column unique on numero_expediente''',
    CONCAT('ALTER TABLE alumno ', @drops)
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- plantilla_preinscripcion: UNIQUE (deporte, version) -> UNIQUE (organizacion_id, deporte, version).
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'plantilla_preinscripcion'
              AND INDEX_NAME = 'uk_plantilla_organizacion_deporte_version'
        ),
        'SELECT ''uk_plantilla_organizacion_deporte_version already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD CONSTRAINT uk_plantilla_organizacion_deporte_version UNIQUE (organizacion_id, deporte, version)'
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
              AND TABLE_NAME = 'plantilla_preinscripcion'
              AND INDEX_NAME = 'uk_plantilla_deporte_version'
        ),
        'ALTER TABLE plantilla_preinscripcion DROP INDEX uk_plantilla_deporte_version',
        'SELECT ''uk_plantilla_deporte_version already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- preinscripcion: la clave generada de preinscripción activa incluye la organización
-- (antes: identidad_hash|deporte|temporada, V8). Se reconstruye columna e índice único
-- en una sola sentencia; se omite si la expresión ya contiene organizacion_id.
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'preinscripcion'
              AND COLUMN_NAME = 'clave_preinscripcion_activa'
              AND GENERATION_EXPRESSION LIKE '%organizacion_id%'
        ),
        'SELECT ''preinscripcion.clave_preinscripcion_activa already includes organizacion_id''',
        'ALTER TABLE preinscripcion
           DROP INDEX uk_preinscripcion_activa,
           DROP COLUMN clave_preinscripcion_activa,
           ADD COLUMN clave_preinscripcion_activa VARCHAR(180)
             GENERATED ALWAYS AS (
               CASE
                 WHEN estado IN (''PENDIENTE'', ''EN_LISTA_ESPERA'', ''FINALIZADA'')
                 THEN CONCAT(organizacion_id, ''|'', identidad_hash, ''|'', deporte, ''|'', temporada)
                 ELSE NULL
               END
             ) STORED,
           ADD UNIQUE KEY uk_preinscripcion_activa (clave_preinscripcion_activa)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
