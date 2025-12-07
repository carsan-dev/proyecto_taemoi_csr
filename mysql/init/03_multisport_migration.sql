-- =====================================================
-- MULTISPORT MIGRATION SCRIPT
-- =====================================================
-- Schema: `taemoi_db`
-- Purpose: Adds multisport support to TaeMoi
-- Execution: Auto-runs after 01_ddl and 02_migration on first DB startup
-- Generated: 2025-12-07
-- =====================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET character_set_connection = utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET UNIQUE_CHECKS = 0;

USE `taemoi_db`;

-- =====================================================
-- STEP 1: Create alumno_deporte table
-- =====================================================
CREATE TABLE IF NOT EXISTS `alumno_deporte` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `alumno_id` BIGINT NOT NULL,
  `deporte` ENUM('TAEKWONDO','KICKBOXING','PILATES','DEFENSA_PERSONAL_FEMENINA') NOT NULL,
  `grado_id` BIGINT DEFAULT NULL,
  `fecha_grado` DATE DEFAULT NULL,
  `apto_para_examen` BIT(1) NOT NULL DEFAULT 0,
  `activo` BIT(1) NOT NULL DEFAULT 1,
  `fecha_alta` DATE DEFAULT NULL,
  `fecha_baja` DATE DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alumno_deporte` (`alumno_id`, `deporte`),
  INDEX `idx_alumno_deporte_alumno_id` (`alumno_id`),
  INDEX `idx_alumno_deporte_deporte` (`deporte`),
  INDEX `idx_alumno_deporte_apto` (`deporte`, `apto_para_examen`),
  CONSTRAINT `fk_alumno_deporte_alumno`
    FOREIGN KEY (`alumno_id`) REFERENCES `alumno` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_alumno_deporte_grado`
    FOREIGN KEY (`grado_id`) REFERENCES `grado` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================
-- STEP 2: Populate alumno_deporte from existing alumno data
-- =====================================================
-- For each existing student, create an AlumnoDeporte entry
-- using their current deporte, grado, fecha_grado, etc.

INSERT INTO `alumno_deporte` (
  `alumno_id`,
  `deporte`,
  `grado_id`,
  `fecha_grado`,
  `apto_para_examen`,
  `activo`,
  `fecha_alta`,
  `fecha_baja`
)
SELECT
  a.id AS alumno_id,
  COALESCE(a.deporte, 'TAEKWONDO') AS deporte,  -- Default to TAEKWONDO if NULL
  a.grado_id,
  a.fecha_grado,
  COALESCE(a.apto_para_examen, 0) AS apto_para_examen,
  a.activo,
  a.fecha_alta,
  a.fecha_baja
FROM `alumno` a
WHERE NOT EXISTS (
  SELECT 1 FROM `alumno_deporte` ad
  WHERE ad.alumno_id = a.id
  AND ad.deporte = COALESCE(a.deporte, 'TAEKWONDO')
);

-- =====================================================
-- STEP 3: Update alumno_convocatoria table structure
-- =====================================================
-- Add alumno_deporte_id column if it doesn't exist
-- This links exam registrations to specific sports

-- Check if column exists, add if not
SET @column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'taemoi_db'
  AND TABLE_NAME = 'alumno_convocatoria'
  AND COLUMN_NAME = 'alumno_deporte_id'
);

SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE `alumno_convocatoria` ADD COLUMN `alumno_deporte_id` BIGINT DEFAULT NULL AFTER `convocatoria_id`;',
  'SELECT ''Column alumno_deporte_id already exists'' AS message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add foreign key constraint if it doesn't exist
SET @fk_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = 'taemoi_db'
  AND TABLE_NAME = 'alumno_convocatoria'
  AND CONSTRAINT_NAME = 'fk_alumno_convocatoria_alumno_deporte'
);

SET @sql = IF(
  @fk_exists = 0,
  'ALTER TABLE `alumno_convocatoria`
   ADD CONSTRAINT `fk_alumno_convocatoria_alumno_deporte`
   FOREIGN KEY (`alumno_deporte_id`) REFERENCES `alumno_deporte` (`id`) ON DELETE CASCADE;',
  'SELECT ''FK fk_alumno_convocatoria_alumno_deporte already exists'' AS message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================
-- STEP 4: Link existing alumno_convocatoria to alumno_deporte
-- =====================================================
-- Update alumno_convocatoria records to reference the correct alumno_deporte
-- Match based on alumno_id and convocatoria.deporte

UPDATE `alumno_convocatoria` ac
INNER JOIN `convocatoria` c ON ac.convocatoria_id = c.id
INNER JOIN `alumno_deporte` ad ON ac.alumno_id = ad.alumno_id AND ad.deporte = c.deporte
SET ac.alumno_deporte_id = ad.id
WHERE ac.alumno_deporte_id IS NULL;

-- =====================================================
-- STEP 5: Add deporte field to grupo table if not exists
-- =====================================================

SET @column_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'taemoi_db'
  AND TABLE_NAME = 'grupo'
  AND COLUMN_NAME = 'deporte'
);

SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE `grupo`
   ADD COLUMN `deporte` ENUM(''TAEKWONDO'',''KICKBOXING'',''PILATES'',''DEFENSA_PERSONAL_FEMENINA'') DEFAULT NULL AFTER `nombre`;',
  'SELECT ''Column grupo.deporte already exists'' AS message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================
-- STEP 6: Populate grupo.deporte based on grupo names
-- =====================================================
-- Automatically detect sport from group name patterns

UPDATE `grupo` SET deporte = 'TAEKWONDO'
WHERE deporte IS NULL
AND (
  LOWER(nombre) LIKE '%taekwondo%'
  OR LOWER(nombre) LIKE '%tkd%'
  OR LOWER(nombre) LIKE '%lunes%'
  OR LOWER(nombre) LIKE '%martes%'
  OR LOWER(nombre) LIKE '%miércoles%'
  OR LOWER(nombre) LIKE '%miercoles%'
  OR LOWER(nombre) LIKE '%jueves%'
  OR LOWER(nombre) LIKE '%viernes%'
  OR LOWER(nombre) NOT LIKE '%kickboxing%'
  AND LOWER(nombre) NOT LIKE '%pilates%'
  AND LOWER(nombre) NOT LIKE '%defensa%'
);

UPDATE `grupo` SET deporte = 'KICKBOXING'
WHERE deporte IS NULL
AND (
  LOWER(nombre) LIKE '%kickboxing%'
  OR LOWER(nombre) LIKE '%kick%'
  OR LOWER(nombre) LIKE '%boxing%'
);

UPDATE `grupo` SET deporte = 'PILATES'
WHERE deporte IS NULL
AND LOWER(nombre) LIKE '%pilates%';

UPDATE `grupo` SET deporte = 'DEFENSA_PERSONAL_FEMENINA'
WHERE deporte IS NULL
AND (
  LOWER(nombre) LIKE '%defensa%'
  OR LOWER(nombre) LIKE '%personal%'
  OR LOWER(nombre) LIKE '%femenina%'
);

-- Default remaining groups to TAEKWONDO
UPDATE `grupo` SET deporte = 'TAEKWONDO'
WHERE deporte IS NULL;

-- Add index on grupo.deporte for faster queries
SET @index_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = 'taemoi_db'
  AND TABLE_NAME = 'grupo'
  AND INDEX_NAME = 'idx_grupo_deporte'
);

SET @sql = IF(
  @index_exists = 0,
  'CREATE INDEX `idx_grupo_deporte` ON `grupo` (`deporte`);',
  'SELECT ''Index idx_grupo_deporte already exists'' AS message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================
-- STEP 7: Verification queries
-- =====================================================
-- Check how many AlumnoDeporte records were created
SELECT COUNT(*) AS alumno_deporte_count FROM `alumno_deporte`;

-- Check sport distribution
SELECT deporte, COUNT(*) AS count
FROM `alumno_deporte`
GROUP BY deporte
ORDER BY count DESC;

-- Check grupos with deporte
SELECT deporte, COUNT(*) AS count
FROM `grupo`
GROUP BY deporte
ORDER BY count DESC;

-- Check alumno_convocatoria links
SELECT
  COUNT(*) AS total,
  SUM(CASE WHEN alumno_deporte_id IS NOT NULL THEN 1 ELSE 0 END) AS linked,
  SUM(CASE WHEN alumno_deporte_id IS NULL THEN 1 ELSE 0 END) AS unlinked
FROM `alumno_convocatoria`;

-- =====================================================
-- CLEANUP & FINALIZATION
-- =====================================================
SET FOREIGN_KEY_CHECKS = 1;
SET UNIQUE_CHECKS = 1;

-- =====================================================
-- MIGRATION COMPLETE
-- =====================================================
-- Next steps:
-- 1. Verify data with queries above
-- 2. Test backend endpoints: GET /api/alumnos/{id}/deportes
-- 3. Run backend migration endpoint if needed: POST /api/admin/migracion/ejecutar
-- 4. Check frontend displays sports correctly
-- =====================================================
