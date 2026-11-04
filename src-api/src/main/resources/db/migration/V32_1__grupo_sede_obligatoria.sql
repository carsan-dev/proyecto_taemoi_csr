-- Multi-escuela, fase 3, E10-5 (contraer): la sede del grupo pasa a ser obligatoria y el nombre del grupo, único en
-- su sede. Plan: docs/multi-escuela/fase3-e10-plan.md §3, §4 y §5.
--
-- REQUISITO DE DESPLIEGUE: después de la V32 (E10-3) y con el código de la E10-4, que ya no crea grupos sin sede ni
-- con un nombre repetido en la sede (409). Antes: consulta 9 de docs/multi-escuela/precheck-e10.sql (deben ser 0 sus
-- dos primeras columnas); después: docs/multi-escuela/postcheck-e10.sql (consulta «Solo tras V32_1»). Volver atrás
-- exige restaurar la copia de seguridad de desplegar_migrar.
--
-- Pasos (cada uno repetible tras un fallo a medias y `flyway repair`: en MySQL el DDL no se deshace, así que se
-- vuelve a consultar information_schema antes de cada cambio):
--   1. Comprobación previa. Si algún grupo no tiene sede o dos grupos de la misma sede tienen el mismo nombre según
--      la colación de la columna (utf8mb4_0900_ai_ci: «Infantil», «infantil» e «Infantíl» chocan), la migración se
--      detiene ANTES de cambiar nada, con un error que lo nombra:
--        Table '<esquema>.v32_1_hay_grupos_sin_sede' doesn't exist
--        Table '<esquema>.v32_1_hay_nombres_de_grupo_repetidos_en_una_sede' doesn't exist
--      Se corrigen los grupos, se ejecuta `flyway repair` y se vuelve a desplegar.
--   2. grupo.sede_id pasa de BIGINT NULL (V31) a BIGINT NOT NULL. La clave ajena fk_grupo_sede (V31) no tiene
--      ON DELETE SET NULL, así que se conserva tal cual.
--   3. Índice único uk_grupo_sede_nombre (sede_id, nombre). idx_grupo_sede (V31) se conserva.
SET @schema_name = DATABASE();

-- ============================================================================
-- 1. Comprobación previa (antes de cualquier cambio)
-- ============================================================================
SET @v32_1_fallo = NULL;

SELECT COUNT(*) INTO @v32_1_n FROM grupo g WHERE g.sede_id IS NULL;
SET @v32_1_fallo = IF(@v32_1_fallo IS NULL AND @v32_1_n > 0, 'hay_grupos_sin_sede', @v32_1_fallo);

-- Agrupa con la colación de la columna, igual que la consulta 9 del precheck.
SELECT COUNT(*) INTO @v32_1_n
FROM (SELECT g.sede_id, g.nombre FROM grupo g WHERE g.sede_id IS NOT NULL
      GROUP BY g.sede_id, g.nombre HAVING COUNT(*) > 1) r;
SET @v32_1_fallo = IF(@v32_1_fallo IS NULL AND @v32_1_n > 0, 'hay_nombres_de_grupo_repetidos_en_una_sede', @v32_1_fallo);

-- Detenerse si algo falla (nada ha cambiado todavía).
SET @sql = IF(
    @v32_1_fallo IS NULL,
    'SELECT ''v32_1: todos los grupos tienen sede y nombre único en ella''',
    CONCAT('SELECT 1 FROM v32_1_', @v32_1_fallo)
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. grupo.sede_id NOT NULL (la clave ajena fk_grupo_sede se conserva)
-- ============================================================================
SET @sql = IF((SELECT IS_NULLABLE FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'sede_id') = 'NO',
    'SELECT ''v32_1: grupo.sede_id ya es NOT NULL''',
    'ALTER TABLE grupo MODIFY sede_id BIGINT NOT NULL');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 3. Nombre único en la sede
-- ============================================================================
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS
                      WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo'
                        AND INDEX_NAME = 'uk_grupo_sede_nombre'),
    'SELECT ''v32_1: uk_grupo_sede_nombre ya existe''',
    'ALTER TABLE grupo ADD UNIQUE KEY uk_grupo_sede_nombre (sede_id, nombre)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
