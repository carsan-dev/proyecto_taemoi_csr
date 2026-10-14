-- Multi-escuela, fase 2 (contraer): borrado de las columnas heredadas del catálogo.
--
-- REQUISITO DE DESPLIEGUE: V28 va en un despliegue POSTERIOR al de V26/V27, cuando ese ya ha
-- funcionado en producción y sus comprobaciones (postcheck-v26.sql, postcheck-v27.sql) están
-- limpias. Antes de V28 se ejecuta docs/multi-escuela/precheck-v28.sql. Después de V28, volver
-- atrás exige restaurar la copia de seguridad (el código anterior lee las columnas borradas).
--
-- Desde V26/V27 el código lee el catálogo (disciplina_id, grado.codigo, tarifa_id) y escribía las
-- columnas heredadas en paralelo. Este despliegue deja de escribirlas y las borra:
--
--   alumno_deporte.deporte, grupo.deporte, convocatoria.deporte,
--   plantilla_preinscripcion.deporte, preinscripcion.deporte   -> disciplina_id
--   grado.tipo_grado                                           -> grado.codigo (pasa a NOT NULL)
--   alumno_deporte.tipo_tarifa                                 -> tarifa_id
--   categoria.tipo_categoria                                   -> categoria.codigo
--
-- Pasos (cada uno repetible: se consulta information_schema antes de cada cambio):
--   0. Guarda de despliegue: V28 se niega a ejecutarse en la misma ejecución de Flyway que V27. V27
--      crea la tabla TEMPORAL v27_aplicada_en_esta_conexion, que solo existe en la conexión de esa
--      ejecución y desaparece al cerrarse. Si V28 la encuentra, se detiene ANTES de cambiar nada:
--        Table '<esquema>.v28_no_puede_aplicarse_en_el_mismo_despliegue_que_v27' doesn't exist
--      En producción: se ejecuta `flyway repair` y V28 se despliega más tarde, cuando las
--      comprobaciones de V26/V27 estén limpias. Los entornos de prueba que migran de cero en una sola
--      ejecución (tests, docker-compose.pruebas.yml, docker-compose.e2e.yml) la desactivan con el
--      placeholder de Flyway v28_permitir_mismo_despliegue_que_v27 = true; su valor por defecto
--      (application.properties) es false.
--   1. Comprobación previa: cada columna heredada se compara con su equivalente del catálogo en las
--      filas que todavía tienen las dos. Si alguna fila no coincide, la migración se detiene ANTES de
--      cambiar nada, con un error que nombra la tabla y la columna:
--        Table '<esquema>.v28_<tabla>_<columna>_no_coincide_con_el_catalogo' doesn't exist
--      Se corrige la fila, se ejecuta `flyway repair` y se vuelve a desplegar.
--   2. organizacion.comportamiento_legado_taemoi (NOT NULL, FALSE por defecto; TRUE para la
--      organización 1). El comportamiento heredado de Taemoi (conceptos literales, competidor a 20 €,
--      tesorería por concepto, variantes sin clasificar) solo se aplica a las organizaciones que lo
--      tienen; el resto usa siempre el del catálogo.
--   3. Catálogo de categorías: categoria gana codigo (= tipo_categoria), disciplina_id, edad_min,
--      edad_max y orden. Las categorías de competición de taekwondo (por nombre, las que asignaba el
--      código: Infantil <= 9, Precadete 10-11, Cadete 12-13, Junior 14-16, Senior >= 17) pasan a la
--      disciplina TAEKWONDO de su organización con esos rangos de edad.
--   4. alumno_convocatoria.grado_actual / grado_siguiente: de ENUM de TipoGrado a VARCHAR(40) (mismos
--      valores) para admitir los códigos de grado de cualquier escuela.
--   5. Índices: se crean los equivalentes por disciplina_id de los que incluían deporte (único
--      (alumno_id, disciplina_id), único de plantilla por (organizacion_id, disciplina_id, version),
--      apto/filtros/dni de preinscripción, clave generada de preinscripción activa con
--      disciplina_id) antes de borrar los antiguos. Si alguno falla (p. ej. un alumno con dos
--      inscripciones de la misma disciplina), no se ha borrado nada todavía.
--   6. Se borran los índices que incluyen una columna heredada y después las columnas.
--   7. grado.codigo NOT NULL.

SET @schema_name = DATABASE();

-- ============================================================================
-- 0. Guarda de despliegue: V27 no puede haberse aplicado en esta misma ejecución
-- ============================================================================
-- Si V27 se aplicó en esta conexión, la tabla ya existe (con su fila) y no se crea; si no, se crea
-- vacía. No es una tabla del esquema: es temporal y solo la ve esta conexión.
CREATE TEMPORARY TABLE IF NOT EXISTS v27_aplicada_en_esta_conexion (
    marca TINYINT NOT NULL PRIMARY KEY
);
SELECT COUNT(*) INTO @v28_v27_en_esta_ejecucion FROM v27_aplicada_en_esta_conexion;
SET @v28_permitir_con_v27 = '${v28_permitir_mismo_despliegue_que_v27}';

SET @sql = IF(
    @v28_v27_en_esta_ejecucion > 0 AND @v28_permitir_con_v27 <> 'true',
    'SELECT 1 FROM v28_no_puede_aplicarse_en_el_mismo_despliegue_que_v27',
    'SELECT ''v28: V27 se aplicó en un despliegue anterior (o guarda desactivada en pruebas)'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TEMPORARY TABLE IF EXISTS v27_aplicada_en_esta_conexion;

-- ============================================================================
-- 1. Comprobación previa (antes de cualquier cambio)
-- ============================================================================
SET @v28_fallo = NULL;

-- alumno_deporte.deporte = disciplina.codigo ----------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'deporte'),
        'SELECT COUNT(*) INTO @v28_n FROM alumno_deporte x LEFT JOIN disciplina d ON d.id = x.disciplina_id
         WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
            OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'alumno_deporte_deporte', @v28_fallo);

-- alumno_deporte.tipo_tarifa = tarifa.codigo (solo filas con tipo_tarifa: una tarifa con código
-- propio de otra escuela no tiene tipo_tarifa) ---------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'tipo_tarifa'),
        'SELECT COUNT(*) INTO @v28_n FROM alumno_deporte x LEFT JOIN tarifa t ON t.id = x.tarifa_id
         WHERE x.tipo_tarifa IS NOT NULL
           AND (t.id IS NULL OR t.organizacion_id <> x.organizacion_id
                OR CONVERT(t.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.tipo_tarifa USING utf8mb4) COLLATE utf8mb4_bin)',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'alumno_deporte_tipo_tarifa', @v28_fallo);

-- grupo.deporte ----------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'deporte'),
        'SELECT COUNT(*) INTO @v28_n FROM grupo x LEFT JOIN disciplina d ON d.id = x.disciplina_id
         WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
            OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'grupo_deporte', @v28_fallo);

-- convocatoria.deporte ---------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'convocatoria' AND COLUMN_NAME = 'deporte'),
        'SELECT COUNT(*) INTO @v28_n FROM convocatoria x LEFT JOIN disciplina d ON d.id = x.disciplina_id
         WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
            OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'convocatoria_deporte', @v28_fallo);

-- plantilla_preinscripcion.deporte --------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion' AND COLUMN_NAME = 'deporte'),
        'SELECT COUNT(*) INTO @v28_n FROM plantilla_preinscripcion x LEFT JOIN disciplina d ON d.id = x.disciplina_id
         WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
            OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'plantilla_preinscripcion_deporte', @v28_fallo);

-- preinscripcion.deporte -------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion' AND COLUMN_NAME = 'deporte'),
        'SELECT COUNT(*) INTO @v28_n FROM preinscripcion x LEFT JOIN disciplina d ON d.id = x.disciplina_id
         WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
            OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'preinscripcion_deporte', @v28_fallo);

-- grado.tipo_grado = grado.codigo (y ningún grado sin codigo: pasa a NOT NULL) -
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grado' AND COLUMN_NAME = 'tipo_grado'),
        'SELECT COUNT(*) INTO @v28_n FROM grado
         WHERE codigo IS NULL
            OR (tipo_grado IS NOT NULL
                AND CONVERT(codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(tipo_grado USING utf8mb4) COLLATE utf8mb4_bin)',
        'SELECT COUNT(*) INTO @v28_n FROM grado WHERE codigo IS NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'grado_tipo_grado', @v28_fallo);

-- categoria.tipo_categoria = categoria.codigo (si una ejecución anterior ya creó codigo) ----------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria' AND COLUMN_NAME = 'tipo_categoria')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria' AND COLUMN_NAME = 'codigo'),
        'SELECT COUNT(*) INTO @v28_n FROM categoria
         WHERE tipo_categoria IS NOT NULL AND codigo IS NOT NULL
           AND CONVERT(codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(tipo_categoria USING utf8mb4) COLLATE utf8mb4_bin',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @v28_fallo = IF(@v28_fallo IS NULL AND @v28_n > 0, 'categoria_tipo_categoria', @v28_fallo);

-- Detenerse si algo no coincide (nada ha cambiado todavía).
SET @sql = IF(
    @v28_fallo IS NULL,
    'SELECT ''v28: las columnas heredadas coinciden con el catálogo''',
    CONCAT('SELECT 1 FROM v28_', @v28_fallo, '_no_coincide_con_el_catalogo')
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. organizacion.comportamiento_legado_taemoi
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'organizacion' AND COLUMN_NAME = 'comportamiento_legado_taemoi'),
        'SELECT ''organizacion.comportamiento_legado_taemoi already exists''',
        'ALTER TABLE organizacion ADD COLUMN comportamiento_legado_taemoi BOOLEAN NOT NULL DEFAULT FALSE'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Siempre, también al repetir V28 tras un fallo parcial (la columna ya existe): la organización 1
-- conserva el comportamiento heredado. Las demás no se tocan (FALSE por defecto). V28 solo se
-- repite tras un fallo, antes de que el código nuevo arranque, así que no pisa ningún cambio de uso.
UPDATE organizacion SET comportamiento_legado_taemoi = TRUE WHERE id = 1;

-- ============================================================================
-- 3. Catálogo de categorías
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria' AND COLUMN_NAME = 'codigo'),
        'SELECT ''categoria catalog columns already exist''',
        'ALTER TABLE categoria
           ADD COLUMN codigo VARCHAR(40) NULL,
           ADD COLUMN disciplina_id BIGINT NULL,
           ADD COLUMN edad_min INT NULL,
           ADD COLUMN edad_max INT NULL,
           ADD COLUMN orden INT NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria' AND INDEX_NAME = 'idx_categoria_disciplina'),
        'SELECT ''idx_categoria_disciplina already exists''',
        'ALTER TABLE categoria ADD INDEX idx_categoria_disciplina (disciplina_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'categoria'
                  AND CONSTRAINT_NAME = 'fk_categoria_disciplina' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_categoria_disciplina already exists''',
        'ALTER TABLE categoria ADD CONSTRAINT fk_categoria_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- codigo = tipo_categoria (el valor que se exponía en el JSON; NULL si no lo tenía).
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria' AND COLUMN_NAME = 'tipo_categoria'),
        'UPDATE categoria SET codigo = tipo_categoria WHERE codigo IS NULL AND tipo_categoria IS NOT NULL',
        'SELECT ''categoria.tipo_categoria already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Categorías de competición de taekwondo: las que el código buscaba por nombre según la edad
-- (AlumnoDeporteServiceImpl / AlumnoServiceImpl.asignarCategoriaSegunEdad). Solo las que aún no
-- tienen disciplina ni rango.
UPDATE categoria c
JOIN (
  SELECT 'Infantil' AS nombre, NULL AS edad_min, 9 AS edad_max, 1 AS orden
  UNION ALL SELECT 'Precadete', 10, 11, 2
  UNION ALL SELECT 'Cadete', 12, 13, 3
  UNION ALL SELECT 'Junior', 14, 16, 4
  UNION ALL SELECT 'Senior', 17, NULL, 5
) v ON CONVERT(c.nombre USING utf8mb4) COLLATE utf8mb4_unicode_ci = CONVERT(v.nombre USING utf8mb4) COLLATE utf8mb4_unicode_ci
JOIN disciplina d
  ON d.organizacion_id = c.organizacion_id
 AND d.codigo = 'TAEKWONDO'
SET c.disciplina_id = d.id,
    c.edad_min = v.edad_min,
    c.edad_max = v.edad_max,
    c.orden = v.orden
WHERE c.disciplina_id IS NULL
  AND c.edad_min IS NULL
  AND c.edad_max IS NULL;

-- Comprobación de categorías tras copiar el código (antes de borrar nada).
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria' AND COLUMN_NAME = 'tipo_categoria'),
        'SELECT COUNT(*) INTO @v28_n FROM categoria
         WHERE tipo_categoria IS NOT NULL
           AND (codigo IS NULL
                OR CONVERT(codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(tipo_categoria USING utf8mb4) COLLATE utf8mb4_bin)',
        'SELECT 0 INTO @v28_n'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(
    @v28_n = 0,
    'SELECT ''v28: categoria.codigo = tipo_categoria''',
    'SELECT 1 FROM v28_categoria_tipo_categoria_no_coincide_con_el_catalogo'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 4. alumno_convocatoria: grados como texto (VARCHAR) en lugar del ENUM de TipoGrado
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_convocatoria'
                  AND COLUMN_NAME IN ('grado_actual', 'grado_siguiente') AND DATA_TYPE = 'enum'),
        'ALTER TABLE alumno_convocatoria
           MODIFY COLUMN grado_actual VARCHAR(40) NULL,
           MODIFY COLUMN grado_siguiente VARCHAR(40) NULL',
        'SELECT ''alumno_convocatoria grades already VARCHAR'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 5. Índices equivalentes por disciplina_id (antes de borrar los de deporte)
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte'
                  AND INDEX_NAME = 'uk_alumno_deporte_alumno_disciplina'),
        'SELECT ''uk_alumno_deporte_alumno_disciplina already exists''',
        'ALTER TABLE alumno_deporte ADD CONSTRAINT uk_alumno_deporte_alumno_disciplina UNIQUE (alumno_id, disciplina_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte'
                  AND INDEX_NAME = 'idx_alumno_deporte_disciplina_apto'),
        'SELECT ''idx_alumno_deporte_disciplina_apto already exists''',
        'ALTER TABLE alumno_deporte ADD INDEX idx_alumno_deporte_disciplina_apto (disciplina_id, apto_para_examen)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion'
                  AND INDEX_NAME = 'uk_plantilla_organizacion_disciplina_version'),
        'SELECT ''uk_plantilla_organizacion_disciplina_version already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD CONSTRAINT uk_plantilla_organizacion_disciplina_version UNIQUE (organizacion_id, disciplina_id, version)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion'
                  AND INDEX_NAME = 'idx_plantilla_disciplina_activa'),
        'SELECT ''idx_plantilla_disciplina_activa already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD INDEX idx_plantilla_disciplina_activa (disciplina_id, activa)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion'
                  AND INDEX_NAME = 'idx_preinscripcion_filtros_disciplina'),
        'SELECT ''idx_preinscripcion_filtros_disciplina already exists''',
        'ALTER TABLE preinscripcion ADD INDEX idx_preinscripcion_filtros_disciplina (temporada, disciplina_id, estado)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion'
                  AND INDEX_NAME = 'idx_preinscripcion_dni_disciplina_temporada'),
        'SELECT ''idx_preinscripcion_dni_disciplina_temporada already exists''',
        'ALTER TABLE preinscripcion ADD INDEX idx_preinscripcion_dni_disciplina_temporada (dni, disciplina_id, temporada)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Clave generada de preinscripción activa: organizacion|identidad|disciplina_id|temporada (antes
-- con deporte). Misma unicidad: en cada organización deporte y disciplina_id se corresponden 1 a 1
-- (comprobado arriba). Columna e índice se reconstruyen en una sentencia.
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion'
              AND COLUMN_NAME = 'clave_preinscripcion_activa'
              AND GENERATION_EXPRESSION LIKE '%deporte%'
        ),
        'ALTER TABLE preinscripcion
           DROP INDEX uk_preinscripcion_activa,
           DROP COLUMN clave_preinscripcion_activa,
           ADD COLUMN clave_preinscripcion_activa VARCHAR(180)
             GENERATED ALWAYS AS (
               CASE
                 WHEN estado IN (''PENDIENTE'', ''EN_LISTA_ESPERA'', ''FINALIZADA'')
                 THEN CONCAT(organizacion_id, ''|'', identidad_hash, ''|'', disciplina_id, ''|'', temporada)
                 ELSE NULL
               END
             ) STORED,
           ADD UNIQUE KEY uk_preinscripcion_activa (clave_preinscripcion_activa)',
        'SELECT ''preinscripcion.clave_preinscripcion_activa already uses disciplina_id'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 6. Índices que incluyen una columna heredada y las columnas
-- ============================================================================
SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE alumno_deporte ',
                       GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte'
           AND COLUMN_NAME IN ('deporte', 'tipo_tarifa') AND INDEX_NAME <> 'PRIMARY'),
        'SELECT ''alumno_deporte: no legacy indexes'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE grupo ',
                       GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo'
           AND COLUMN_NAME = 'deporte' AND INDEX_NAME <> 'PRIMARY'),
        'SELECT ''grupo: no legacy indexes'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE convocatoria ',
                       GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'convocatoria'
           AND COLUMN_NAME = 'deporte' AND INDEX_NAME <> 'PRIMARY'),
        'SELECT ''convocatoria: no legacy indexes'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE plantilla_preinscripcion ',
                       GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion'
           AND COLUMN_NAME = 'deporte' AND INDEX_NAME <> 'PRIMARY'),
        'SELECT ''plantilla_preinscripcion: no legacy indexes'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE preinscripcion ',
                       GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion'
           AND COLUMN_NAME = 'deporte' AND INDEX_NAME <> 'PRIMARY'),
        'SELECT ''preinscripcion: no legacy indexes'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE grado ',
                       GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grado'
           AND COLUMN_NAME = 'tipo_grado' AND INDEX_NAME <> 'PRIMARY'),
        'SELECT ''grado: no legacy indexes'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IFNULL(
        (SELECT CONCAT('ALTER TABLE categoria ',
                       GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', '))
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria'
           AND COLUMN_NAME = 'tipo_categoria' AND INDEX_NAME <> 'PRIMARY'),
        'SELECT ''categoria: no legacy indexes'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Columnas --------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'deporte'),
        'ALTER TABLE alumno_deporte DROP COLUMN `deporte`',
        'SELECT ''alumno_deporte.deporte already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'tipo_tarifa'),
        'ALTER TABLE alumno_deporte DROP COLUMN `tipo_tarifa`',
        'SELECT ''alumno_deporte.tipo_tarifa already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'deporte'),
        'ALTER TABLE grupo DROP COLUMN `deporte`',
        'SELECT ''grupo.deporte already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'convocatoria' AND COLUMN_NAME = 'deporte'),
        'ALTER TABLE convocatoria DROP COLUMN `deporte`',
        'SELECT ''convocatoria.deporte already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion' AND COLUMN_NAME = 'deporte'),
        'ALTER TABLE plantilla_preinscripcion DROP COLUMN `deporte`',
        'SELECT ''plantilla_preinscripcion.deporte already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion' AND COLUMN_NAME = 'deporte'),
        'ALTER TABLE preinscripcion DROP COLUMN `deporte`',
        'SELECT ''preinscripcion.deporte already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grado' AND COLUMN_NAME = 'tipo_grado'),
        'ALTER TABLE grado DROP COLUMN `tipo_grado`',
        'SELECT ''grado.tipo_grado already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'categoria' AND COLUMN_NAME = 'tipo_categoria'),
        'ALTER TABLE categoria DROP COLUMN `tipo_categoria`',
        'SELECT ''categoria.tipo_categoria already dropped'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 7. grado.codigo NOT NULL (comprobado en el paso 1: ningún grado sin codigo)
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grado'
                  AND COLUMN_NAME = 'codigo' AND IS_NULLABLE = 'YES'),
        'ALTER TABLE grado MODIFY COLUMN codigo VARCHAR(40) NOT NULL',
        'SELECT ''grado.codigo already NOT NULL'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
