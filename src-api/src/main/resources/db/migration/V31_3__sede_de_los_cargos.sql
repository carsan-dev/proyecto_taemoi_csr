-- Sede de los cargos de tesorería (entrega 6-3, docs/multi-escuela/entrega6-plan.md §4.3 y §4.5; decisión D6-9).
--
-- SOLO AÑADE: producto_alumno.sede_id BIGINT NULL, índice y FK a sede (ON DELETE SET NULL). El código anterior
-- sigue funcionando sobre este esquema (no lee la columna e inserta sin ella: queda NULL). Por eso no lleva una guarda
-- que detenga la migración (como la de V32); sí lleva guardas de existencia (information_schema) en cada cambio de
-- esquema. Va como V31_3 porque V32 está reservada para la contracción multi-escuela.
--
-- Desde la 6-3 el código fija la sede al CREAR el cargo (ProductoAlumno @PrePersist: la sede principal del alumno en
-- ese momento) y nunca la cambia. Este script rellena el histórico, SOLO las filas con sede_id IS NULL:
--   1. Escuelas con UNA sola sede: todos sus cargos van a esa sede (en Moiskimdo, organización 1, «Umbrete»),
--      tenga o no el alumno sede principal y aunque el cargo no tenga alumno resoluble.
--   2. Escuelas con varias sedes: la sede principal ACTUAL del alumno del cargo, si la tiene y es de la misma
--      escuela. El alumno se resuelve como en la tesorería: COALESCE(alumno_id, alumno_deporte.alumno_id).
--      Sin sede principal, NULL (solo la gestión ve esos cargos, en la fila «Sin sede»).
--   3. Escuelas sin sede: NULL.
-- Repetible: cada paso vuelve a consultar information_schema y los datos; tras un fallo a medias, `flyway repair`
-- y volver a desplegar. Repetirlo no cambia las filas ya rellenas.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v31_3.sql.

SET @schema_name = DATABASE();

-- ============================================================================
-- 1. producto_alumno.sede_id, índice y FK
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno' AND COLUMN_NAME = 'sede_id'),
        'SELECT ''producto_alumno.sede_id already exists''',
        'ALTER TABLE producto_alumno ADD COLUMN sede_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND INDEX_NAME = 'idx_producto_alumno_sede'),
        'SELECT ''idx_producto_alumno_sede already exists''',
        'ALTER TABLE producto_alumno ADD INDEX idx_producto_alumno_sede (sede_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND CONSTRAINT_NAME = 'fk_producto_alumno_sede' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_producto_alumno_sede already exists''',
        'ALTER TABLE producto_alumno ADD CONSTRAINT fk_producto_alumno_sede FOREIGN KEY (sede_id) REFERENCES sede(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. Relleno: escuelas con una sola sede (todos sus cargos a esa sede)
-- ============================================================================
UPDATE producto_alumno pa
JOIN (SELECT organizacion_id, MIN(id) AS sede_id
      FROM sede
      GROUP BY organizacion_id
      HAVING COUNT(*) = 1) unica
  ON unica.organizacion_id = pa.organizacion_id
SET pa.sede_id = unica.sede_id
WHERE pa.sede_id IS NULL;

-- ============================================================================
-- 3. Relleno: escuelas con varias sedes (sede principal actual del alumno del cargo, de la misma escuela)
-- ============================================================================
UPDATE producto_alumno pa
LEFT JOIN alumno_deporte ad ON ad.id = pa.alumno_deporte_id
JOIN alumno a ON a.id = COALESCE(pa.alumno_id, ad.alumno_id)
JOIN sede s ON s.id = a.sede_principal_id AND s.organizacion_id = pa.organizacion_id
SET pa.sede_id = s.id
WHERE pa.sede_id IS NULL;
