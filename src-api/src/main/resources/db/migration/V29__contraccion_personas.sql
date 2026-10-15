-- Multi-escuela, fase 2 (contraer): personas. El responsable legal vive solo en `tutor`.
--
-- REQUISITO DE DESPLIEGUE: V29 va en el MISMO despliegue que V28 y que el código que ya no lee ni
-- escribe alumno.responsable_legal_nombre / responsable_legal_nif (el responsable legal es el primer
-- tutor con responsable_legal = TRUE). Antes de V29 se ejecuta docs/multi-escuela/precheck-v29.sql.
-- Después de V29, volver atrás exige restaurar la copia de seguridad (el código anterior lee las
-- columnas borradas).
--
-- Hasta este despliegue el código mantenía las columnas heredadas iguales al primer tutor responsable
-- legal; sin ninguno, guardaban lo último escrito (puede ser solo un NIF: tutor.nombre era obligatorio).
-- Ahora un responsable legal del que solo se conoce el NIF es un tutor con nombre NULL.
--
-- Pasos (cada uno repetible: se vuelve a consultar information_schema y los datos antes de cada
-- cambio; nada se decide antes de una DDL para saltarlo al repetir tras un fallo y `flyway repair`):
--   0. Comprobación previa (antes de cualquier cambio). Se detiene sin cambiar nada si:
--      - un alumno con tutor responsable legal tiene en responsable_legal_nombre (no vacío) otro
--        nombre que el del primero de ellos (por orden, id), comparando sin espacios sobrantes:
--          Table '<esquema>.v29_responsable_legal_nombre_no_coincide_con_el_tutor' doesn't exist
--      - ídem con responsable_legal_nif (no vacío, normalizado como NifUtils):
--          Table '<esquema>.v29_responsable_legal_nif_no_coincide_con_el_tutor' doesn't exist
--      - algún tutor no tiene ni nombre ni NIF (incumpliría la restricción del paso 4):
--          Table '<esquema>.v29_tutor_sin_nombre_ni_nif' doesn't exist
--      Se corrige la fila, se ejecuta `flyway repair` y se vuelve a desplegar. Las comprobaciones de
--      las columnas heredadas se omiten si ya no existen (repetición tras borrarlas).
--   1. tutor.nombre admite NULL.
--   2. Relleno: cada alumno sin tutor responsable legal con nombre o NIF heredado recibe uno, como lo
--      crea hoy el código: TUTOR_LEGAL, responsable_legal = TRUE, orden = máximo + 1, organización del
--      alumno, nombre sin espacios sobrantes (NULL si vacío) y NIF en mayúsculas sin espacios
--      exteriores (NULL si vacío o 00000000Z). Repetible: solo alumnos que siguen sin responsable.
--   3. Nombres vacíos de tutor a NULL.
--   4. Restricción ck_tutor_nombre_o_nif: nombre o NIF no vacíos.
--   5. Borrado de alumno.responsable_legal_nombre y responsable_legal_nif (lo último).

SET @schema_name = DATABASE();

-- ============================================================================
-- 0. Comprobación previa (antes de cualquier cambio)
-- ============================================================================
SET @v29_fallo = NULL;

-- Nombre heredado distinto del primer tutor responsable legal (nombre + apellidos como
-- Tutor.getNombreCompleto, recortado a 180 como lo copiaba el código; espacios normalizados).
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'responsable_legal_nombre'),
        'SELECT COUNT(*) INTO @v29_n
         FROM alumno a
         JOIN (SELECT t.alumno_id, t.nombre, t.apellidos,
                      ROW_NUMBER() OVER (PARTITION BY t.alumno_id ORDER BY t.orden, t.id) AS posicion
               FROM tutor t WHERE t.responsable_legal = TRUE) p
           ON p.alumno_id = a.id AND p.posicion = 1
         WHERE TRIM(REGEXP_REPLACE(COALESCE(a.responsable_legal_nombre, ''''), ''[[:space:]]+'', '' '')) <> ''''
           AND NOT (CONVERT(TRIM(REGEXP_REPLACE(a.responsable_legal_nombre, ''[[:space:]]+'', '' '')) USING utf8mb4) COLLATE utf8mb4_bin
                    <=> CONVERT(NULLIF(TRIM(REGEXP_REPLACE(LEFT(TRIM(CONCAT(TRIM(COALESCE(p.nombre, '''')), '' '',
                                TRIM(COALESCE(p.apellidos, '''')))), 180), ''[[:space:]]+'', '' '')), '''') USING utf8mb4) COLLATE utf8mb4_bin)',
        'SELECT 0 INTO @v29_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v29_fallo = IF(@v29_fallo IS NULL AND @v29_n > 0, 'responsable_legal_nombre_no_coincide_con_el_tutor', @v29_fallo);

-- NIF heredado distinto del NIF del primer tutor responsable legal.
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'responsable_legal_nif'),
        'SELECT COUNT(*) INTO @v29_n
         FROM alumno a
         JOIN (SELECT t.alumno_id, t.nif,
                      ROW_NUMBER() OVER (PARTITION BY t.alumno_id ORDER BY t.orden, t.id) AS posicion
               FROM tutor t WHERE t.responsable_legal = TRUE) p
           ON p.alumno_id = a.id AND p.posicion = 1
         WHERE NULLIF(NULLIF(UPPER(TRIM(a.responsable_legal_nif)), ''''), ''00000000Z'') IS NOT NULL
           AND NOT (CONVERT(NULLIF(NULLIF(UPPER(TRIM(a.responsable_legal_nif)), ''''), ''00000000Z'') USING utf8mb4) COLLATE utf8mb4_bin
                    <=> CONVERT(NULLIF(NULLIF(UPPER(TRIM(p.nif)), ''''), ''00000000Z'') USING utf8mb4) COLLATE utf8mb4_bin)',
        'SELECT 0 INTO @v29_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v29_fallo = IF(@v29_fallo IS NULL AND @v29_n > 0, 'responsable_legal_nif_no_coincide_con_el_tutor', @v29_fallo);

-- Tutores sin nombre ni NIF (la restricción del paso 4 no se podría crear).
SELECT COUNT(*) INTO @v29_n FROM tutor
WHERE TRIM(COALESCE(nombre, '')) = '' AND TRIM(COALESCE(nif, '')) = '';
SET @v29_fallo = IF(@v29_fallo IS NULL AND @v29_n > 0, 'tutor_sin_nombre_ni_nif', @v29_fallo);

-- Detenerse si algo no coincide (nada ha cambiado todavía).
SET @sql = IF(
    @v29_fallo IS NULL,
    'SELECT ''v29: las columnas heredadas coinciden con los tutores''',
    CONCAT('SELECT 1 FROM v29_', @v29_fallo)
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 1. tutor.nombre admite NULL (conserva su juego de caracteres y colación)
-- ============================================================================
SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE tutor MODIFY COLUMN nombre VARCHAR(180) CHARACTER SET ', CHARACTER_SET_NAME,
                       ' COLLATE ', COLLATION_NAME, ' NULL')
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'tutor' AND COLUMN_NAME = 'nombre' AND IS_NULLABLE = 'NO'),
        'SELECT ''tutor.nombre already NULL'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. Relleno de tutores responsables legales desde las columnas heredadas
-- ============================================================================
-- Las expresiones se construyen con las columnas que existan en este momento (si una ya no existe,
-- vale NULL), así que el relleno nunca se salta por una decisión tomada antes de otra DDL.
SET @v29_nombre = (
    SELECT IF(COUNT(*) > 0,
              'NULLIF(TRIM(REGEXP_REPLACE(COALESCE(a.responsable_legal_nombre, ''''), ''[[:space:]]+'', '' '')), '''')',
              'NULL')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'responsable_legal_nombre'
);
SET @v29_nif = (
    SELECT IF(COUNT(*) > 0,
              'NULLIF(NULLIF(UPPER(TRIM(a.responsable_legal_nif)), ''''), ''00000000Z'')',
              'NULL')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'responsable_legal_nif'
);
SET @sql = IF(
    @v29_nombre = 'NULL' AND @v29_nif = 'NULL',
    'SELECT ''alumno.responsable_legal_* already dropped''',
    CONCAT(
        'INSERT INTO tutor (organizacion_id, alumno_id, nombre, apellidos, nif, parentesco, telefono, email,
                            responsable_legal, orden)
         SELECT x.organizacion_id, x.alumno_id, x.nombre, NULL, x.nif, ''TUTOR_LEGAL'', NULL, NULL, TRUE,
                (SELECT COALESCE(MAX(t.orden), 0) + 1 FROM tutor t WHERE t.alumno_id = x.alumno_id)
         FROM (SELECT a.organizacion_id, a.id AS alumno_id, ', @v29_nombre, ' AS nombre, ', @v29_nif, ' AS nif
               FROM alumno a
               WHERE NOT EXISTS (SELECT 1 FROM tutor t WHERE t.alumno_id = a.id AND t.responsable_legal = TRUE)) x
         WHERE x.nombre IS NOT NULL OR x.nif IS NOT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 3. Nombres vacíos de tutor a NULL (la columna ya admite NULL: paso 1)
-- ============================================================================
UPDATE tutor SET nombre = NULL WHERE nombre IS NOT NULL AND TRIM(nombre) = '';

-- ============================================================================
-- 4. Restricción: un tutor tiene nombre o NIF
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'tutor'
                  AND CONSTRAINT_NAME = 'ck_tutor_nombre_o_nif' AND CONSTRAINT_TYPE = 'CHECK'),
        'SELECT ''ck_tutor_nombre_o_nif already exists''',
        'ALTER TABLE tutor ADD CONSTRAINT ck_tutor_nombre_o_nif
           CHECK (TRIM(COALESCE(nombre, '''')) <> '''' OR TRIM(COALESCE(nif, '''')) <> '''')'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 5. Borrado de las columnas heredadas (en una sola sentencia si siguen las dos)
-- ============================================================================
SET @v29_columnas = (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN `', COLUMN_NAME, '`') ORDER BY COLUMN_NAME SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno'
      AND COLUMN_NAME IN ('responsable_legal_nombre', 'responsable_legal_nif')
);
SET @sql = IF(
    @v29_columnas IS NULL,
    'SELECT ''alumno.responsable_legal_* already dropped''',
    CONCAT('ALTER TABLE alumno ', @v29_columnas)
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
