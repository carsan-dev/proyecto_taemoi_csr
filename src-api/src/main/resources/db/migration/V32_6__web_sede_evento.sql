-- Web por plantilla, pieza W2 (docs/multi-escuela/fase4-web-plan.md §4.2): datos de la web en sede y evento.
--
-- SOLO AMPLÍA: columnas nuevas, todas NULL y sin relleno (ninguna fila cambia; Moiskimdo, la 1, queda igual), un
-- índice único, tres índices y tres claves ajenas nuevas. No cambia ninguna columna, índice ni restricción existente.
-- El código anterior sigue funcionando sobre este esquema: no conoce las columnas (ddl-auto=validate solo mira lo
-- mapeado) y todas admiten NULL.
--
--   sede.slug                 Dirección de la página de la sede en la web (/sedes/<slug>): minúsculas, cifras y
--                             guiones. Único por escuela (uk_sede_organizacion_slug); varias sedes sin slug (NULL)
--                             conviven. Sin relleno: lo pone la gestión.
--   sede.lugar                «Pabellón cubierto municipal».
--   sede.indicaciones_acceso  «Entrada por la calle lateral…».
--   sede.foto_imagen_id       Fotos de la sede (imagen de la misma escuela; lo comprueba la aplicación). Si se borra
--   sede.foto2_imagen_id      la imagen, la sede se queda sin esa foto (ON DELETE SET NULL).
--   evento.hora               Hora del evento (decisión 466: opcional; si falta, no se pinta).
--   evento.lugar              Lugar del evento (466, opcional).
--   evento.sede_id            Sede del evento (466, opcional; de la misma escuela, lo comprueba la aplicación). Las
--                             sedes no se borran; si se borrara, el evento se queda sin sede (ON DELETE SET NULL).
--
-- Coste en producción (mysql:8.0): sede y evento son tablas pequeñas (decenas de filas). Las columnas se añaden al
-- final y son NULL; los índices y las claves ajenas reconstruyen la tabla en milisegundos. `desplegar_migrar` para
-- antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar lo nuevo borraría los datos de la web de las sedes y eventos: ALTER TABLE ... DROP FOREIGN KEY,
-- DROP INDEX y DROP COLUMN de cada elemento de arriba.
--
-- Repetible: en MySQL el DDL no se deshace, así que cada paso consulta antes information_schema y solo hace lo que
-- falte. Tras un fallo a medias, `flyway repair` y volver a desplegar completa lo pendiente.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-web-sede-evento.sql.

SET @schema_name = DATABASE();

-- ============================================================================
-- 1. sede
-- ============================================================================
SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND COLUMN_NAME = 'slug'),
    'SELECT ''sede.slug already exists''',
    'ALTER TABLE sede ADD COLUMN slug VARCHAR(60) NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND COLUMN_NAME = 'lugar'),
    'SELECT ''sede.lugar already exists''',
    'ALTER TABLE sede ADD COLUMN lugar VARCHAR(120) NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND COLUMN_NAME = 'indicaciones_acceso'),
    'SELECT ''sede.indicaciones_acceso already exists''',
    'ALTER TABLE sede ADD COLUMN indicaciones_acceso VARCHAR(300) NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND COLUMN_NAME = 'foto_imagen_id'),
    'SELECT ''sede.foto_imagen_id already exists''',
    'ALTER TABLE sede ADD COLUMN foto_imagen_id BIGINT NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND COLUMN_NAME = 'foto2_imagen_id'),
    'SELECT ''sede.foto2_imagen_id already exists''',
    'ALTER TABLE sede ADD COLUMN foto2_imagen_id BIGINT NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND INDEX_NAME = 'uk_sede_organizacion_slug'),
    'SELECT ''uk_sede_organizacion_slug already exists''',
    'ALTER TABLE sede ADD UNIQUE KEY uk_sede_organizacion_slug (organizacion_id, slug)'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND INDEX_NAME = 'idx_sede_foto_imagen'),
    'SELECT ''idx_sede_foto_imagen already exists''',
    'ALTER TABLE sede ADD INDEX idx_sede_foto_imagen (foto_imagen_id)'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND INDEX_NAME = 'idx_sede_foto2_imagen'),
    'SELECT ''idx_sede_foto2_imagen already exists''',
    'ALTER TABLE sede ADD INDEX idx_sede_foto2_imagen (foto2_imagen_id)'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'sede'
          AND CONSTRAINT_NAME = 'fk_sede_foto_imagen' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
    'SELECT ''fk_sede_foto_imagen already exists''',
    'ALTER TABLE sede ADD CONSTRAINT fk_sede_foto_imagen FOREIGN KEY (foto_imagen_id) REFERENCES imagen(id) ON DELETE SET NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'sede'
          AND CONSTRAINT_NAME = 'fk_sede_foto2_imagen' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
    'SELECT ''fk_sede_foto2_imagen already exists''',
    'ALTER TABLE sede ADD CONSTRAINT fk_sede_foto2_imagen FOREIGN KEY (foto2_imagen_id) REFERENCES imagen(id) ON DELETE SET NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. evento
-- ============================================================================
SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'evento' AND COLUMN_NAME = 'hora'),
    'SELECT ''evento.hora already exists''',
    'ALTER TABLE evento ADD COLUMN hora TIME NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'evento' AND COLUMN_NAME = 'lugar'),
    'SELECT ''evento.lugar already exists''',
    'ALTER TABLE evento ADD COLUMN lugar VARCHAR(160) NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'evento' AND COLUMN_NAME = 'sede_id'),
    'SELECT ''evento.sede_id already exists''',
    'ALTER TABLE evento ADD COLUMN sede_id BIGINT NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'evento' AND INDEX_NAME = 'idx_evento_sede'),
    'SELECT ''idx_evento_sede already exists''',
    'ALTER TABLE evento ADD INDEX idx_evento_sede (sede_id)'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'evento'
          AND CONSTRAINT_NAME = 'fk_evento_sede' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
    'SELECT ''fk_evento_sede already exists''',
    'ALTER TABLE evento ADD CONSTRAINT fk_evento_sede FOREIGN KEY (sede_id) REFERENCES sede(id) ON DELETE SET NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
