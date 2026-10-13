-- Multi-escuela, fase 2 (ampliar): catálogo de disciplinas y sistemas de grados.
--
--   1. sistema_grados y disciplina (por organización). Taemoi (organización 1) recibe las 4
--      disciplinas del enum Deporte (codigo = nombre del enum) y dos sistemas de grados:
--      taekwondo y kickboxing. Pilates y defensa personal femenina no tienen grados.
--   2. grado gana las columnas del catálogo. Las filas existentes de la organización 1 conservan
--      su id y pasan a ser el sistema de taekwondo; se crea una copia para kickboxing con sus
--      nombres visibles (ROJO -> 'MARRÓN', como GradoUtils) y su propio orden.
--   3. Las inscripciones (alumno_deporte) y la columna heredada alumno.grado_id que apuntan a un
--      grado de otro sistema se reasignan al grado del mismo código del sistema de su disciplina
--      (en la práctica: kickboxing pasa a la copia de kickboxing).
--   4. disciplina_id (NULL) junto a cada columna deporte: alumno_deporte, grupo, convocatoria,
--      plantilla_preinscripcion y preinscripcion; rellenada por codigo.
--
-- Valores de progresión (orden, aplica_a), meses mínimos y bloques de temario: copiados de
-- GradeProgressionConfig, ExamEligibilityConfig y ExamMaterialBlockConfig (ver
-- docs/multi-escuela/valores-actuales.md). Los tests de paridad comprueban que los servicios
-- nuevos devuelven exactamente lo mismo que esas clases.
--
-- Regla de progresión que reproducen los datos (ProgresionGradosService):
--   siguiente = menor orden > actual, en el mismo sistema, con aplica_a = grupo de edad o TODOS;
--   si el grado actual no es de ese grupo de edad (ni TODOS), solo puede seguir un grado TODOS.
--   NINGUNO = el grado existe pero nunca es destino de progresión.
--
-- No se rellenan: color_1/color_2 (decisión visual pendiente) ni equivalente_adulto_id (sin uso
-- todavía). tipo_grado se mantiene hasta la contracción.
-- Todas las sentencias son repetibles sin efectos duplicados.

SET @schema_name = DATABASE();

-- ----------------------------------------------------------------------------
-- sistema_grados
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sistema_grados (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  plantilla_origen VARCHAR(40) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sistema_grados_organizacion_nombre (organizacion_id, nombre),
  CONSTRAINT fk_sistema_grados_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- disciplina
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS disciplina (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  codigo VARCHAR(40) NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  sistema_grados_id BIGINT NULL,
  permite_competicion BOOLEAN NOT NULL DEFAULT FALSE,
  requiere_licencia BOOLEAN NOT NULL DEFAULT FALSE,
  edad_adulto TINYINT NOT NULL,
  orden INT NOT NULL DEFAULT 0,
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_disciplina_organizacion_codigo (organizacion_id, codigo),
  KEY idx_disciplina_sistema_grados (sistema_grados_id),
  CONSTRAINT fk_disciplina_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_disciplina_sistema_grados FOREIGN KEY (sistema_grados_id) REFERENCES sistema_grados(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Semillas de Taemoi (organización 1).
INSERT INTO sistema_grados (organizacion_id, nombre, plantilla_origen)
SELECT 1, s.nombre, s.plantilla_origen
FROM (
  SELECT 'Taekwondo' AS nombre, 'TAEKWONDO' AS plantilla_origen
  UNION ALL SELECT 'Kickboxing', 'KICKBOXING'
) s
WHERE EXISTS (SELECT 1 FROM organizacion WHERE id = 1)
  AND NOT EXISTS (
    SELECT 1 FROM sistema_grados sg
    WHERE sg.organizacion_id = 1 AND sg.plantilla_origen = s.plantilla_origen
  );

SET @sistema_tkd = (SELECT id FROM sistema_grados
                    WHERE organizacion_id = 1 AND plantilla_origen = 'TAEKWONDO' ORDER BY id LIMIT 1);
SET @sistema_kb = (SELECT id FROM sistema_grados
                   WHERE organizacion_id = 1 AND plantilla_origen = 'KICKBOXING' ORDER BY id LIMIT 1);

-- Edad adulta: FechaUtils (14 por defecto, 15 en kickboxing).
-- Competición: tarifa de competidor solo en taekwondo y kickboxing (ProductoAlumnoServiceImpl).
-- Licencia: deporteRequiereLicencia solo en taekwondo y kickboxing (ProductoAlumnoServiceImpl).
-- Nombre: nombreDeportePublico (PDFServiceImpl). Orden: el del enum Deporte.
INSERT INTO disciplina (organizacion_id, codigo, nombre, sistema_grados_id, permite_competicion,
                        requiere_licencia, edad_adulto, orden, activa)
SELECT 1, d.codigo, d.nombre, d.sistema_grados_id, d.permite_competicion, d.requiere_licencia,
       d.edad_adulto, d.orden, TRUE
FROM (
  SELECT 'TAEKWONDO' AS codigo, 'Taekwondo' AS nombre, @sistema_tkd AS sistema_grados_id,
         TRUE AS permite_competicion, TRUE AS requiere_licencia, 14 AS edad_adulto, 1 AS orden
  UNION ALL SELECT 'KICKBOXING', 'Kickboxing', @sistema_kb, TRUE, TRUE, 15, 2
  UNION ALL SELECT 'PILATES', 'Pilates', NULL, FALSE, FALSE, 14, 3
  UNION ALL SELECT 'DEFENSA_PERSONAL_FEMENINA', 'Defensa personal femenina', NULL, FALSE, FALSE, 14, 4
) d
WHERE EXISTS (SELECT 1 FROM organizacion WHERE id = 1)
  AND NOT EXISTS (
    SELECT 1 FROM disciplina x WHERE x.organizacion_id = 1 AND x.codigo = d.codigo
  );

-- ----------------------------------------------------------------------------
-- grado: columnas del catálogo (todas añadidas a la vez; ALTER atómico)
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grado'
              AND COLUMN_NAME = 'sistema_grados_id'
        ),
        'SELECT ''grado catalog columns already exist''',
        'ALTER TABLE grado
           ADD COLUMN sistema_grados_id BIGINT NULL,
           ADD COLUMN codigo VARCHAR(40) NULL,
           ADD COLUMN nombre VARCHAR(100) NULL,
           ADD COLUMN orden INT NULL,
           ADD COLUMN color_1 VARCHAR(7) NULL,
           ADD COLUMN color_2 VARCHAR(7) NULL,
           ADD COLUMN aplica_a ENUM(''TODOS'', ''MENORES'', ''ADULTOS'', ''NINGUNO'') NOT NULL DEFAULT ''TODOS'',
           ADD COLUMN equivalente_adulto_id BIGINT NULL,
           ADD COLUMN requiere_examen BOOLEAN NOT NULL DEFAULT TRUE,
           ADD COLUMN meses_minimos_menor INT NULL,
           ADD COLUMN meses_minimos_adulto INT NULL,
           ADD COLUMN bloque_temario VARCHAR(60) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Antes de rellenar: un código repetido dentro de un sistema hace fallar la migración.
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grado'
              AND INDEX_NAME = 'uk_grado_sistema_codigo'
        ),
        'SELECT ''uk_grado_sistema_codigo already exists''',
        'ALTER TABLE grado ADD CONSTRAINT uk_grado_sistema_codigo UNIQUE (sistema_grados_id, codigo)'
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
              AND TABLE_NAME = 'grado'
              AND INDEX_NAME = 'idx_grado_equivalente_adulto'
        ),
        'SELECT ''idx_grado_equivalente_adulto already exists''',
        'ALTER TABLE grado ADD INDEX idx_grado_equivalente_adulto (equivalente_adulto_id)'
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
              AND CONSTRAINT_NAME = 'fk_grado_sistema_grados'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_grado_sistema_grados already exists''',
        'ALTER TABLE grado ADD CONSTRAINT fk_grado_sistema_grados FOREIGN KEY (sistema_grados_id) REFERENCES sistema_grados(id)'
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
              AND TABLE_NAME = 'grado'
              AND CONSTRAINT_NAME = 'fk_grado_equivalente_adulto'
              AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_grado_equivalente_adulto already exists''',
        'ALTER TABLE grado ADD CONSTRAINT fk_grado_equivalente_adulto FOREIGN KEY (equivalente_adulto_id) REFERENCES grado(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- grado: sistema de taekwondo (las filas existentes de la organización 1)
-- ----------------------------------------------------------------------------
-- Base nueva (sin las 19 filas que hoy siembra InicializadorDatos): se crean las que falten,
-- en el orden del enum. En producción ya existen y conservan su id.
INSERT INTO grado (organizacion_id, tipo_grado)
SELECT 1, c.codigo
FROM (
  SELECT 'BLANCO' AS codigo, 1 AS orden
  UNION ALL SELECT 'BLANCO_AMARILLO', 2
  UNION ALL SELECT 'AMARILLO', 3
  UNION ALL SELECT 'AMARILLO_NARANJA', 4
  UNION ALL SELECT 'NARANJA', 5
  UNION ALL SELECT 'NARANJA_VERDE', 6
  UNION ALL SELECT 'VERDE', 7
  UNION ALL SELECT 'VERDE_AZUL', 8
  UNION ALL SELECT 'AZUL', 9
  UNION ALL SELECT 'AZUL_ROJO', 10
  UNION ALL SELECT 'ROJO', 11
  UNION ALL SELECT 'ROJO_NEGRO_1_PUM', 12
  UNION ALL SELECT 'ROJO_NEGRO_2_PUM', 13
  UNION ALL SELECT 'ROJO_NEGRO_3_PUM', 14
  UNION ALL SELECT 'NEGRO_1_DAN', 15
  UNION ALL SELECT 'NEGRO_2_DAN', 16
  UNION ALL SELECT 'NEGRO_3_DAN', 17
  UNION ALL SELECT 'NEGRO_4_DAN', 18
  UNION ALL SELECT 'NEGRO_5_DAN', 19
) c
WHERE EXISTS (SELECT 1 FROM organizacion WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM grado g WHERE g.organizacion_id = 1 AND g.tipo_grado = c.codigo)
ORDER BY c.orden;

-- Taekwondo: intermedios y PUM solo para menores; DAN solo para adultos; colores principales
-- para todos. Meses (menor/adulto) de ExamEligibilityConfig (NULL = no apto); bloques de
-- ExamMaterialBlockConfig. requiere_examen: todos salvo BLANCO (grado de entrada).
UPDATE grado g
JOIN (
  SELECT 'BLANCO' AS codigo, 'Blanco' AS nombre, 1 AS orden, 'TODOS' AS aplica_a, FALSE AS requiere_examen,
         2 AS meses_menor, 3 AS meses_adulto, 'b01_inicio_a_amarillo' AS bloque
  UNION ALL SELECT 'BLANCO_AMARILLO', 'Blanco-Amarillo', 2, 'MENORES', TRUE, 2, NULL, 'b01_inicio_a_amarillo'
  UNION ALL SELECT 'AMARILLO', 'Amarillo', 3, 'TODOS', TRUE, 3, 5, 'b02_amarillo_a_naranja'
  UNION ALL SELECT 'AMARILLO_NARANJA', 'Amarillo-Naranja', 4, 'MENORES', TRUE, 3, NULL, 'b02_amarillo_a_naranja'
  UNION ALL SELECT 'NARANJA', 'Naranja', 5, 'TODOS', TRUE, 4, 6, 'b03_naranja_a_verde'
  UNION ALL SELECT 'NARANJA_VERDE', 'Naranja-Verde', 6, 'MENORES', TRUE, 4, NULL, 'b03_naranja_a_verde'
  UNION ALL SELECT 'VERDE', 'Verde', 7, 'TODOS', TRUE, 6, 8, 'b04_verde_a_azul'
  UNION ALL SELECT 'VERDE_AZUL', 'Verde-Azul', 8, 'MENORES', TRUE, 6, NULL, 'b04_verde_a_azul'
  UNION ALL SELECT 'AZUL', 'Azul', 9, 'TODOS', TRUE, 8, 10, 'b05_azul_a_rojo'
  UNION ALL SELECT 'AZUL_ROJO', 'Azul-Rojo', 10, 'MENORES', TRUE, 10, NULL, 'b05_azul_a_rojo'
  UNION ALL SELECT 'ROJO', 'Rojo', 11, 'TODOS', TRUE, 12, 12, 'b06_rojo_a_negro_1'
  UNION ALL SELECT 'ROJO_NEGRO_1_PUM', 'Rojo-Negro-1-Pum', 12, 'MENORES', TRUE, 24, NULL, 'b07_negro_1_a_negro_2'
  UNION ALL SELECT 'ROJO_NEGRO_2_PUM', 'Rojo-Negro-2-Pum', 13, 'MENORES', TRUE, 36, NULL, 'b08_negro_2_a_negro_3'
  UNION ALL SELECT 'ROJO_NEGRO_3_PUM', 'Rojo-Negro-3-Pum', 14, 'MENORES', TRUE, NULL, NULL, 'b09_negro_3_a_negro_4'
  UNION ALL SELECT 'NEGRO_1_DAN', 'Negro-1-Dan', 15, 'ADULTOS', TRUE, NULL, 24, 'b07_negro_1_a_negro_2'
  UNION ALL SELECT 'NEGRO_2_DAN', 'Negro-2-Dan', 16, 'ADULTOS', TRUE, NULL, 36, 'b08_negro_2_a_negro_3'
  UNION ALL SELECT 'NEGRO_3_DAN', 'Negro-3-Dan', 17, 'ADULTOS', TRUE, NULL, 48, 'b09_negro_3_a_negro_4'
  UNION ALL SELECT 'NEGRO_4_DAN', 'Negro-4-Dan', 18, 'ADULTOS', TRUE, NULL, 60, 'b10_negro_4_a_negro_5'
  UNION ALL SELECT 'NEGRO_5_DAN', 'Negro-5-Dan', 19, 'ADULTOS', TRUE, NULL, NULL, 'b11_negro_5_a_maximo'
) c ON c.codigo = g.tipo_grado
SET g.sistema_grados_id = @sistema_tkd,
    g.codigo = c.codigo,
    g.nombre = c.nombre,
    g.orden = c.orden,
    g.aplica_a = c.aplica_a,
    g.requiere_examen = c.requiere_examen,
    g.meses_minimos_menor = c.meses_menor,
    g.meses_minimos_adulto = c.meses_adulto,
    g.bloque_temario = c.bloque
WHERE g.organizacion_id = 1
  AND g.sistema_grados_id IS NULL
  AND @sistema_tkd IS NOT NULL;

-- ----------------------------------------------------------------------------
-- grado: sistema de kickboxing (copia de las 19 filas de taekwondo)
-- ----------------------------------------------------------------------------
-- Mismo mapa para menores y adultos: intermedios y PUM nunca son destino (NINGUNO), el resto
-- para todos. Los PUM se ordenan tras NEGRO_5_DAN para que desde un PUM no se progrese
-- (como hoy). Meses y bloques iguales que taekwondo (las clases actuales no distinguen).
INSERT INTO grado (organizacion_id, tipo_grado, sistema_grados_id, codigo, nombre, orden, aplica_a,
                   requiere_examen, meses_minimos_menor, meses_minimos_adulto, bloque_temario)
SELECT t.organizacion_id,
       t.tipo_grado,
       @sistema_kb,
       t.codigo,
       CASE WHEN t.codigo = 'ROJO' THEN 'MARRÓN' ELSE t.nombre END,
       CASE
         WHEN t.codigo IN ('NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN')
           THEN t.orden - 3
         WHEN t.codigo IN ('ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM')
           THEN t.orden + 5
         ELSE t.orden
       END,
       CASE WHEN t.aplica_a = 'MENORES' THEN 'NINGUNO' ELSE 'TODOS' END,
       t.requiere_examen,
       t.meses_minimos_menor,
       t.meses_minimos_adulto,
       t.bloque_temario
FROM grado t
WHERE t.organizacion_id = 1
  AND t.sistema_grados_id = @sistema_tkd
  AND @sistema_kb IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT sistema_grados_id, codigo FROM grado) k
    WHERE k.sistema_grados_id = @sistema_kb AND k.codigo = t.codigo
  )
ORDER BY t.orden;

-- ----------------------------------------------------------------------------
-- Reasignar grado_id al sistema de la disciplina (kickboxing -> copia de kickboxing)
-- ----------------------------------------------------------------------------
UPDATE alumno_deporte ad
JOIN grado actual ON actual.id = ad.grado_id
JOIN disciplina d
  ON d.organizacion_id = ad.organizacion_id
 AND d.codigo = CONVERT(ad.deporte USING utf8mb4) COLLATE utf8mb4_bin
JOIN grado destino
  ON destino.organizacion_id = ad.organizacion_id
 AND destino.sistema_grados_id = d.sistema_grados_id
 AND destino.codigo = actual.codigo
SET ad.grado_id = destino.id
WHERE d.sistema_grados_id IS NOT NULL
  AND NOT (actual.sistema_grados_id <=> d.sistema_grados_id);

-- Columna heredada (contracción en V25+): mismo criterio con alumno.deporte.
UPDATE alumno a
JOIN grado actual ON actual.id = a.grado_id
JOIN disciplina d
  ON d.organizacion_id = a.organizacion_id
 AND d.codigo = CONVERT(a.deporte USING utf8mb4) COLLATE utf8mb4_bin
JOIN grado destino
  ON destino.organizacion_id = a.organizacion_id
 AND destino.sistema_grados_id = d.sistema_grados_id
 AND destino.codigo = actual.codigo
SET a.grado_id = destino.id
WHERE d.sistema_grados_id IS NOT NULL
  AND NOT (actual.sistema_grados_id <=> d.sistema_grados_id);

-- ----------------------------------------------------------------------------
-- disciplina_id junto a deporte. Patrón por tabla: columna NULL, índice, relleno por codigo,
-- clave ajena (con foreign_key_checks = 0 para no copiar la tabla; los valores ya son válidos).
-- ----------------------------------------------------------------------------

-- alumno_deporte -------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'disciplina_id'
        ),
        'SELECT ''alumno_deporte.disciplina_id already exists''',
        'ALTER TABLE alumno_deporte ADD COLUMN disciplina_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND INDEX_NAME = 'idx_alumno_deporte_disciplina'
        ),
        'SELECT ''idx_alumno_deporte_disciplina already exists''',
        'ALTER TABLE alumno_deporte ADD INDEX idx_alumno_deporte_disciplina (disciplina_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE alumno_deporte t
JOIN disciplina d
  ON d.organizacion_id = t.organizacion_id
 AND d.codigo = CONVERT(t.deporte USING utf8mb4) COLLATE utf8mb4_bin
SET t.disciplina_id = d.id
WHERE t.disciplina_id IS NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte'
              AND CONSTRAINT_NAME = 'fk_alumno_deporte_disciplina' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_alumno_deporte_disciplina already exists''',
        'ALTER TABLE alumno_deporte ADD CONSTRAINT fk_alumno_deporte_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- grupo ----------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'disciplina_id'
        ),
        'SELECT ''grupo.disciplina_id already exists''',
        'ALTER TABLE grupo ADD COLUMN disciplina_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo' AND INDEX_NAME = 'idx_grupo_disciplina'
        ),
        'SELECT ''idx_grupo_disciplina already exists''',
        'ALTER TABLE grupo ADD INDEX idx_grupo_disciplina (disciplina_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE grupo t
JOIN disciplina d
  ON d.organizacion_id = t.organizacion_id
 AND d.codigo = CONVERT(t.deporte USING utf8mb4) COLLATE utf8mb4_bin
SET t.disciplina_id = d.id
WHERE t.disciplina_id IS NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'grupo'
              AND CONSTRAINT_NAME = 'fk_grupo_disciplina' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_grupo_disciplina already exists''',
        'ALTER TABLE grupo ADD CONSTRAINT fk_grupo_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- convocatoria ---------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'convocatoria' AND COLUMN_NAME = 'disciplina_id'
        ),
        'SELECT ''convocatoria.disciplina_id already exists''',
        'ALTER TABLE convocatoria ADD COLUMN disciplina_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'convocatoria' AND INDEX_NAME = 'idx_convocatoria_disciplina'
        ),
        'SELECT ''idx_convocatoria_disciplina already exists''',
        'ALTER TABLE convocatoria ADD INDEX idx_convocatoria_disciplina (disciplina_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE convocatoria t
JOIN disciplina d
  ON d.organizacion_id = t.organizacion_id
 AND d.codigo = CONVERT(t.deporte USING utf8mb4) COLLATE utf8mb4_bin
SET t.disciplina_id = d.id
WHERE t.disciplina_id IS NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'convocatoria'
              AND CONSTRAINT_NAME = 'fk_convocatoria_disciplina' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_convocatoria_disciplina already exists''',
        'ALTER TABLE convocatoria ADD CONSTRAINT fk_convocatoria_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- plantilla_preinscripcion ---------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion' AND COLUMN_NAME = 'disciplina_id'
        ),
        'SELECT ''plantilla_preinscripcion.disciplina_id already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD COLUMN disciplina_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion'
              AND INDEX_NAME = 'idx_plantilla_preinscripcion_disciplina'
        ),
        'SELECT ''idx_plantilla_preinscripcion_disciplina already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD INDEX idx_plantilla_preinscripcion_disciplina (disciplina_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE plantilla_preinscripcion t
JOIN disciplina d
  ON d.organizacion_id = t.organizacion_id
 AND d.codigo = CONVERT(t.deporte USING utf8mb4) COLLATE utf8mb4_bin
SET t.disciplina_id = d.id
WHERE t.disciplina_id IS NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'plantilla_preinscripcion'
              AND CONSTRAINT_NAME = 'fk_plantilla_preinscripcion_disciplina' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_plantilla_preinscripcion_disciplina already exists''',
        'ALTER TABLE plantilla_preinscripcion ADD CONSTRAINT fk_plantilla_preinscripcion_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- preinscripcion -------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion' AND COLUMN_NAME = 'disciplina_id'
        ),
        'SELECT ''preinscripcion.disciplina_id already exists''',
        'ALTER TABLE preinscripcion ADD COLUMN disciplina_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion' AND INDEX_NAME = 'idx_preinscripcion_disciplina'
        ),
        'SELECT ''idx_preinscripcion_disciplina already exists''',
        'ALTER TABLE preinscripcion ADD INDEX idx_preinscripcion_disciplina (disciplina_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE preinscripcion t
JOIN disciplina d
  ON d.organizacion_id = t.organizacion_id
 AND d.codigo = CONVERT(t.deporte USING utf8mb4) COLLATE utf8mb4_bin
SET t.disciplina_id = d.id
WHERE t.disciplina_id IS NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'preinscripcion'
              AND CONSTRAINT_NAME = 'fk_preinscripcion_disciplina' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_preinscripcion_disciplina already exists''',
        'ALTER TABLE preinscripcion ADD CONSTRAINT fk_preinscripcion_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;
