-- Multi-escuela, fase 1: permisos por organización (membresia).
-- Sustituye a usuario_rol como fuente de los permisos. usuario_rol se mantiene (sin escribirse)
-- hasta la fase de contracción: si se vuelve al código anterior, verá los roles de antes de V20.
-- Relleno desde usuario_rol, todo en la organización 1 (Taemoi):
--   ROLE_ADMIN   -> PROPIETARIO
--   ROLE_MANAGER -> ADMINISTRADOR
--   ROLE_USER    -> FAMILIA
-- Todas las sentencias son repetibles sin efectos duplicados.

SET @schema_name = DATABASE();

CREATE TABLE IF NOT EXISTS membresia (
  id BIGINT NOT NULL AUTO_INCREMENT,
  usuario_id BIGINT NOT NULL,
  organizacion_id BIGINT NOT NULL,
  rol ENUM('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR', 'FAMILIA') NOT NULL,
  cargo VARCHAR(100) NULL,
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_membresia_usuario_organizacion_rol (usuario_id, organizacion_id, rol),
  KEY idx_membresia_organizacion_activa (organizacion_id, activa),
  -- Borrar un usuario (p. ej. al eliminar su alumno) borra sus membresías.
  CONSTRAINT fk_membresia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
  CONSTRAINT fk_membresia_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO membresia (usuario_id, organizacion_id, rol)
SELECT origen.usuario_id, 1, origen.rol
FROM (
  SELECT DISTINCT
    ur.usuario_id,
    CASE ur.roles
      WHEN 'ROLE_ADMIN' THEN 'PROPIETARIO'
      WHEN 'ROLE_MANAGER' THEN 'ADMINISTRADOR'
      WHEN 'ROLE_USER' THEN 'FAMILIA'
    END AS rol
  FROM usuario_rol ur
  WHERE ur.roles IN ('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER')
) origen
WHERE NOT EXISTS (
  SELECT 1
  FROM membresia m
  WHERE m.usuario_id = origen.usuario_id
    AND m.organizacion_id = 1
    AND m.rol = origen.rol
);

-- ----------------------------------------------------------------------------
-- usuario.ultima_organizacion_id: escuela elegida en el último inicio de sesión o cambio.
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'usuario'
              AND COLUMN_NAME = 'ultima_organizacion_id'
        ),
        'SELECT ''usuario.ultima_organizacion_id already exists''',
        'ALTER TABLE usuario ADD COLUMN ultima_organizacion_id BIGINT NULL'
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
              AND TABLE_NAME = 'usuario'
              AND INDEX_NAME = 'idx_usuario_ultima_organizacion'
        ),
        'SELECT ''idx_usuario_ultima_organizacion already exists''',
        'ALTER TABLE usuario ADD INDEX idx_usuario_ultima_organizacion (ultima_organizacion_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name
              AND TABLE_NAME = 'usuario'
              AND CONSTRAINT_NAME = 'fk_usuario_ultima_organizacion'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_usuario_ultima_organizacion already exists''',
        'ALTER TABLE usuario ADD CONSTRAINT fk_usuario_ultima_organizacion FOREIGN KEY (ultima_organizacion_id) REFERENCES organizacion(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
