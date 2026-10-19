-- Solicitudes al propietario, encargos y devolución enlazada (pieza 6c-2, docs/multi-escuela/fase3-6c-solicitudes.md §6).
--
-- SOLO AMPLÍA: una tabla nueva y VACÍA y cuatro columnas nuevas al final de movimiento_caja. No escribe filas ni
-- toca ninguna otra tabla. El código anterior sigue funcionando sobre este esquema: no conoce la tabla, y al insertar
-- un movimiento sin las columnas nuevas quedan NULL salvo metodo, que queda EFECTIVO por el DEFAULT. Va como V31_8
-- porque V32 está reservada para la contracción multi-escuela.
--
--   solicitud        lo que G, R o el profesor piden al propietario (tipos 1 a 3) y los encargos del profesor (tipo 4).
--                    Una sola pendiente por recurso (D6c-2): cuatro columnas generadas VIRTUAL con clave única, el id del
--                    recurso si la solicitud está PENDIENTE y NULL si no (cobro, entrega, movimiento y, solo en
--                    REVERTIR_COBRO, el cargo: decisión 191). VIRTUAL por lo mismo que cobro.producto_alumno_activo_id
--                    (V31_5): MySQL no admite ON DELETE SET NULL en la columna base de una columna generada STORED.
--                    FK: a usuario, alumno, cargo, producto y sede, ON DELETE SET NULL (borrar una cuenta, un alumno,
--                    un cargo o un producto del catálogo no falla: la fila guarda su instantánea); a cobro, entrega y
--                    movimiento, sin acción (esas filas no se borran).
--   movimiento_caja  cobro_id y producto_alumno_id (devolución enlazada al cobro y al cargo, decisión 153; ERROR de la
--                    193), metodo (D6c-13: VARCHAR(20) NOT NULL DEFAULT 'EFECTIVO'; todas las filas actuales quedan
--                    EFECTIVO y el saldo de hoy no cambia) y cobro_devuelto_activo_id (VIRTUAL, clave única: una
--                    DEVOLUCION ACTIVA por cobro, D6c-11). Las usa la 6c-6; la 6c-2 solo las mapea.
--
-- Coste en producción (mysql:8.0): cada ADD COLUMN va en su PROPIA sentencia y es INSTANT (columna NULL, columna NOT
-- NULL con DEFAULT y columna VIRTUAL: solo metadatos). Los índices son INPLACE sin bloquear escrituras. Las FK se añaden
-- con foreign_key_checks = 0 en la sesión (INPLACE, solo metadatos; con 1 MySQL las añade con COPY y bloquea las
-- escrituras), como V31_6: es seguro porque las columnas nuevas están enteras a NULL. Aun así se cuentan los huérfanos
-- antes y, si hubiera alguno, se añaden con foreign_key_checks = 1 para que MySQL las valide (y falle). Se restaura
-- justo después (es de la SESIÓN de Flyway).
--
-- Repetible: CREATE TABLE IF NOT EXISTS es atómico en MySQL 8 y cada cambio de movimiento_caja vuelve a consultar
-- information_schema. Tras un fallo a medias, `flyway repair` y volver a desplegar crea solo lo que falte.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v31_8.sql.

SET @schema_name = DATABASE();

-- ============================================================================
-- 1. Tabla solicitud
-- ============================================================================
CREATE TABLE IF NOT EXISTS solicitud (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  -- Del cobro, la entrega o el movimiento; del cargo en un cargo sin cobro (191); del encargo, D6c-9.
  sede_id BIGINT NULL,
  -- REVERTIR_COBRO, DESHACER_COBRO_ENTREGADO, ANULAR_ENTREGA, ANULAR_MOVIMIENTO, ENCARGO
  tipo VARCHAR(30) NOT NULL,
  -- Tipos 1-3: PENDIENTE, ACEPTADA, DENEGADA, RETIRADA, NO_APLICABLE.
  -- Encargo: PEDIDO, ENCARGADO, RECIBIDO, ENTREGADO, RECHAZADO, RETIRADA.
  estado VARCHAR(20) NOT NULL,
  solicitado_por_usuario_id BIGINT NULL,
  solicitado_en DATETIME(6) NOT NULL,
  -- D6c-1. En el encargo, la nota libre (talla...).
  motivo VARCHAR(500) NOT NULL,
  resuelto_por_usuario_id BIGINT NULL,
  resuelto_en DATETIME(6) NULL,
  respuesta VARCHAR(500) NULL,
  cobro_id BIGINT NULL,
  entrega_caja_id BIGINT NULL,
  movimiento_caja_id BIGINT NULL,
  -- Cargo del cobro (tipos 1 y 2), cargo sin cobro (tipo 1, 191) o cargo creado por el encargo.
  producto_alumno_id BIGINT NULL,
  producto_id BIGINT NULL,
  -- Instantáneas para la bandeja.
  alumno_id BIGINT NULL,
  concepto VARCHAR(255) NULL,
  importe DECIMAL(10,2) NULL,
  -- Tipo 2: AUN_NO_PAGADO, NO_HUBO_VENTA.
  motivo_deshacer VARCHAR(20) NULL,
  encargado_en DATETIME(6) NULL,
  encargado_por_usuario_id BIGINT NULL,
  recibido_en DATETIME(6) NULL,
  recibido_por_usuario_id BIGINT NULL,
  entregado_en DATETIME(6) NULL,
  -- Encargo caído con el cargo pagado y sin devolución (195).
  devolucion_pendiente BOOLEAN NOT NULL DEFAULT FALSE,
  movimiento_devolucion_id BIGINT NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  cobro_pendiente_id BIGINT
    GENERATED ALWAYS AS (IF(estado = 'PENDIENTE', cobro_id, NULL)) VIRTUAL,
  entrega_pendiente_id BIGINT
    GENERATED ALWAYS AS (IF(estado = 'PENDIENTE', entrega_caja_id, NULL)) VIRTUAL,
  movimiento_pendiente_id BIGINT
    GENERATED ALWAYS AS (IF(estado = 'PENDIENTE', movimiento_caja_id, NULL)) VIRTUAL,
  cargo_pendiente_id BIGINT
    GENERATED ALWAYS AS (IF(estado = 'PENDIENTE' AND tipo = 'REVERTIR_COBRO', producto_alumno_id, NULL)) VIRTUAL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_solicitud_cobro_pendiente (cobro_pendiente_id),
  UNIQUE KEY uk_solicitud_entrega_pendiente (entrega_pendiente_id),
  UNIQUE KEY uk_solicitud_movimiento_pendiente (movimiento_pendiente_id),
  UNIQUE KEY uk_solicitud_cargo_pendiente (cargo_pendiente_id),
  KEY idx_solicitud_estado_tipo (organizacion_id, estado, tipo),
  KEY idx_solicitud_sede_estado (organizacion_id, sede_id, estado),
  KEY idx_solicitud_solicitado_por_estado (solicitado_por_usuario_id, estado),
  KEY idx_solicitud_producto_alumno (producto_alumno_id),
  KEY idx_solicitud_sede (sede_id),
  KEY idx_solicitud_resuelto_por (resuelto_por_usuario_id),
  KEY idx_solicitud_encargado_por (encargado_por_usuario_id),
  KEY idx_solicitud_recibido_por (recibido_por_usuario_id),
  KEY idx_solicitud_cobro (cobro_id),
  KEY idx_solicitud_entrega_caja (entrega_caja_id),
  KEY idx_solicitud_movimiento_caja (movimiento_caja_id),
  KEY idx_solicitud_producto (producto_id),
  KEY idx_solicitud_alumno (alumno_id),
  KEY idx_solicitud_movimiento_devolucion (movimiento_devolucion_id),
  CONSTRAINT fk_solicitud_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_solicitud_sede FOREIGN KEY (sede_id) REFERENCES sede(id) ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_solicitado_por FOREIGN KEY (solicitado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_resuelto_por FOREIGN KEY (resuelto_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_encargado_por FOREIGN KEY (encargado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_recibido_por FOREIGN KEY (recibido_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_cobro FOREIGN KEY (cobro_id) REFERENCES cobro(id),
  CONSTRAINT fk_solicitud_entrega_caja FOREIGN KEY (entrega_caja_id) REFERENCES entrega_caja(id),
  CONSTRAINT fk_solicitud_movimiento_caja FOREIGN KEY (movimiento_caja_id) REFERENCES movimiento_caja(id),
  CONSTRAINT fk_solicitud_producto_alumno FOREIGN KEY (producto_alumno_id) REFERENCES producto_alumno(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_producto FOREIGN KEY (producto_id) REFERENCES producto(id) ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE SET NULL,
  CONSTRAINT fk_solicitud_movimiento_devolucion FOREIGN KEY (movimiento_devolucion_id) REFERENCES movimiento_caja(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 2. Columnas de movimiento_caja (una sentencia cada una: INSTANT)
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja' AND COLUMN_NAME = 'cobro_id'),
        'SELECT ''movimiento_caja.cobro_id already exists''',
        'ALTER TABLE movimiento_caja ADD COLUMN cobro_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja'
                  AND COLUMN_NAME = 'producto_alumno_id'),
        'SELECT ''movimiento_caja.producto_alumno_id already exists''',
        'ALTER TABLE movimiento_caja ADD COLUMN producto_alumno_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja' AND COLUMN_NAME = 'metodo'),
        'SELECT ''movimiento_caja.metodo already exists''',
        'ALTER TABLE movimiento_caja ADD COLUMN metodo VARCHAR(20) NOT NULL DEFAULT ''EFECTIVO'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja'
                  AND COLUMN_NAME = 'cobro_devuelto_activo_id'),
        'SELECT ''movimiento_caja.cobro_devuelto_activo_id already exists''',
        'ALTER TABLE movimiento_caja ADD COLUMN cobro_devuelto_activo_id BIGINT GENERATED ALWAYS AS (IF(estado = ''ACTIVO'' AND tipo = ''DEVOLUCION'', cobro_id, NULL)) VIRTUAL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 3. Índices de movimiento_caja (INPLACE, sin bloquear escrituras)
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja'
                  AND INDEX_NAME = 'idx_movimiento_caja_cobro'),
        'SELECT ''idx_movimiento_caja_cobro already exists''',
        'ALTER TABLE movimiento_caja ADD INDEX idx_movimiento_caja_cobro (cobro_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja'
                  AND INDEX_NAME = 'idx_movimiento_caja_producto_alumno'),
        'SELECT ''idx_movimiento_caja_producto_alumno already exists''',
        'ALTER TABLE movimiento_caja ADD INDEX idx_movimiento_caja_producto_alumno (producto_alumno_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja'
                  AND INDEX_NAME = 'uk_movimiento_caja_cobro_devuelto_activo'),
        'SELECT ''uk_movimiento_caja_cobro_devuelto_activo already exists''',
        'ALTER TABLE movimiento_caja ADD UNIQUE INDEX uk_movimiento_caja_cobro_devuelto_activo (cobro_devuelto_activo_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 4. FK de movimiento_caja, sin reconstruir la tabla
-- ============================================================================
SET @huerfanos = (
    SELECT COUNT(*) FROM movimiento_caja m
    WHERE (m.cobro_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM cobro c WHERE c.id = m.cobro_id))
       OR (m.producto_alumno_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM producto_alumno pa WHERE pa.id = m.producto_alumno_id))
);
SET @fk_checks_antes = @@SESSION.foreign_key_checks;
SET SESSION foreign_key_checks = IF(@huerfanos = 0, 0, 1);

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja'
                  AND CONSTRAINT_NAME = 'fk_movimiento_caja_cobro' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_movimiento_caja_cobro already exists''',
        'ALTER TABLE movimiento_caja ADD CONSTRAINT fk_movimiento_caja_cobro FOREIGN KEY (cobro_id) REFERENCES cobro(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'movimiento_caja'
                  AND CONSTRAINT_NAME = 'fk_movimiento_caja_producto_alumno' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_movimiento_caja_producto_alumno already exists''',
        'ALTER TABLE movimiento_caja ADD CONSTRAINT fk_movimiento_caja_producto_alumno FOREIGN KEY (producto_alumno_id) REFERENCES producto_alumno(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET SESSION foreign_key_checks = IF(@fk_checks_antes IS NULL, 1, @fk_checks_antes);
