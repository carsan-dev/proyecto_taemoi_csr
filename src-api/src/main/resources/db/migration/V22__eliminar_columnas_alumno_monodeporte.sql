-- Multi-deporte, limpieza final: el alumno deja de tener los campos de deporte único.
-- Todos los datos por deporte viven en alumno_deporte (producción: 0 alumnos sin fila en
-- alumno_deporte). Se eliminan las 14 columnas heredadas de `alumno`:
--   deporte, grado_id, fecha_grado, apto_para_examen, tipo_tarifa, cuantia_tarifa,
--   rol_familiar, grupo_familiar, competidor, peso, fecha_peso, tiene_licencia,
--   numero_licencia, fecha_licencia
-- Todas son NULL DEFAULT NULL (V1), así que su borrado no depende de valores por defecto.
-- Orden:
--   0. Archivar los valores de las 14 columnas en alumno_monodeporte_archivo (una fila por
--      alumno, con los tipos originales leídos de information_schema). Tabla sin entidad JPA:
--      ddl-auto=validate solo comprueba las tablas mapeadas. Solo se insertan alumnos que aún
--      no estén archivados, así que repetir el script no duplica filas.
--   1. Rellenar alumno_deporte.cuantia_tarifa a partir de alumno.cuantia_tarifa donde la del
--      deporte es NULL: es exactamente el valor que usaba el fallback del código al cobrar
--      mensualidades, así que el precio no cambia al desaparecer la columna.
--   2. Quitar las claves ajenas sobre alumno.grado_id (el nombre se busca: las bases
--      anteriores a Flyway pueden tener otro) y los índices que usen esa columna.
--   3. Quitar cada columna.
-- Cada paso está protegido con information_schema para poder repetirse.

SET @schema_name = DATABASE();

-- ----------------------------------------------------------------------------
-- 0. Archivo de los valores heredados
-- ----------------------------------------------------------------------------
SET SESSION group_concat_max_len = 65535;
SET @columnas_legado = 'deporte,grado_id,fecha_grado,apto_para_examen,tipo_tarifa,cuantia_tarifa,rol_familiar,grupo_familiar,competidor,peso,fecha_peso,tiene_licencia,numero_licencia,fecha_licencia';

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT ''alumno has no legacy columns left to archive (create)''',
        CONCAT(
            'CREATE TABLE IF NOT EXISTS alumno_monodeporte_archivo (',
            'id BIGINT NOT NULL, ',
            'organizacion_id BIGINT NOT NULL, ',
            GROUP_CONCAT(CONCAT('`', COLUMN_NAME, '` ', COLUMN_TYPE, ' NULL') ORDER BY ORDINAL_POSITION SEPARATOR ', '),
            ', archivado_en DATETIME DEFAULT CURRENT_TIMESTAMP, ',
            'PRIMARY KEY (id), ',
            'INDEX idx_alumno_monodeporte_archivo_organizacion (organizacion_id)',
            ') ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4'
        )
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'alumno'
      AND FIND_IN_SET(COLUMN_NAME, @columnas_legado) > 0
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT ''alumno has no legacy columns left to archive (insert)''',
        CONCAT(
            'INSERT INTO alumno_monodeporte_archivo (id, organizacion_id, ',
            GROUP_CONCAT(CONCAT('`', COLUMN_NAME, '`') ORDER BY ORDINAL_POSITION SEPARATOR ', '),
            ') SELECT a.id, a.organizacion_id, ',
            GROUP_CONCAT(CONCAT('a.`', COLUMN_NAME, '`') ORDER BY ORDINAL_POSITION SEPARATOR ', '),
            ' FROM alumno a WHERE NOT EXISTS (SELECT 1 FROM alumno_monodeporte_archivo x WHERE x.id = a.id)'
        )
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'alumno'
      AND FIND_IN_SET(COLUMN_NAME, @columnas_legado) > 0
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 1. Conservar la tarifa heredada en el deporte del alumno
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'cuantia_tarifa'
        ),
        'UPDATE alumno_deporte ad JOIN alumno a ON a.id = ad.alumno_id SET ad.cuantia_tarifa = a.cuantia_tarifa WHERE ad.cuantia_tarifa IS NULL AND a.cuantia_tarifa IS NOT NULL',
        'SELECT ''alumno.cuantia_tarifa already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. Claves ajenas e índices sobre alumno.grado_id
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IFNULL(
        (
            SELECT CONCAT('ALTER TABLE alumno ',
                          GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', CONSTRAINT_NAME, '`') SEPARATOR ', '))
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'grado_id'
              AND REFERENCED_TABLE_NAME IS NOT NULL
        ),
        'SELECT ''alumno.grado_id has no foreign key'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IFNULL(
        (
            SELECT CONCAT('ALTER TABLE alumno ',
                          GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'grado_id'
              AND INDEX_NAME <> 'PRIMARY'
        ),
        'SELECT ''alumno.grado_id has no index'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 3. Columnas de deporte único
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'deporte'
        ),
        'ALTER TABLE alumno DROP COLUMN `deporte`',
        'SELECT ''alumno.deporte already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'grado_id'
        ),
        'ALTER TABLE alumno DROP COLUMN `grado_id`',
        'SELECT ''alumno.grado_id already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'fecha_grado'
        ),
        'ALTER TABLE alumno DROP COLUMN `fecha_grado`',
        'SELECT ''alumno.fecha_grado already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'apto_para_examen'
        ),
        'ALTER TABLE alumno DROP COLUMN `apto_para_examen`',
        'SELECT ''alumno.apto_para_examen already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'tipo_tarifa'
        ),
        'ALTER TABLE alumno DROP COLUMN `tipo_tarifa`',
        'SELECT ''alumno.tipo_tarifa already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'cuantia_tarifa'
        ),
        'ALTER TABLE alumno DROP COLUMN `cuantia_tarifa`',
        'SELECT ''alumno.cuantia_tarifa already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'rol_familiar'
        ),
        'ALTER TABLE alumno DROP COLUMN `rol_familiar`',
        'SELECT ''alumno.rol_familiar already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'grupo_familiar'
        ),
        'ALTER TABLE alumno DROP COLUMN `grupo_familiar`',
        'SELECT ''alumno.grupo_familiar already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'competidor'
        ),
        'ALTER TABLE alumno DROP COLUMN `competidor`',
        'SELECT ''alumno.competidor already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'peso'
        ),
        'ALTER TABLE alumno DROP COLUMN `peso`',
        'SELECT ''alumno.peso already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'fecha_peso'
        ),
        'ALTER TABLE alumno DROP COLUMN `fecha_peso`',
        'SELECT ''alumno.fecha_peso already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'tiene_licencia'
        ),
        'ALTER TABLE alumno DROP COLUMN `tiene_licencia`',
        'SELECT ''alumno.tiene_licencia already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'numero_licencia'
        ),
        'ALTER TABLE alumno DROP COLUMN `numero_licencia`',
        'SELECT ''alumno.numero_licencia already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'fecha_licencia'
        ),
        'ALTER TABLE alumno DROP COLUMN `fecha_licencia`',
        'SELECT ''alumno.fecha_licencia already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
