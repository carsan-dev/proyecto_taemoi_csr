-- Multi-escuela, fase 2 (ampliar): tarifas y catálogo de productos.
--
--   1. tarifa (por organización). Taemoi (organización 1) recibe las 10 tarifas de TipoTarifa con
--      las cuantías de TarifaConfig (codigo = nombre del enum; disciplina = la que admite
--      PreinscripcionService.validarDatosDeporte).
--      PADRES_HIJOS: el importe depende del ROL familiar, no de la edad (TarifaConfig):
--        importe = 0.00 (sin rol o NINGUNO), importe_adulto = 28.00 (PADRE/MADRE, el progenitor),
--        importe_menor = 26.00 (HIJO/HIJA). Es la representación que prevé modelo-datos.md («los
--        importes adulto/menor cubren PADRES_HIJOS»); TarifasService elige la columna por rol.
--      HERMANOS: 26.00 fijo por miembro (sin importes por rol). El resto: importe fijo.
--   2. alumno_deporte.tarifa_id (NULL), rellenada desde tipo_tarifa por codigo. tipo_tarifa se
--      mantiene hasta la contracción.
--   3. producto gana codigo (NULL), tipo y disciplina_id (NULL). Solo para la organización 1 y por
--      coincidencia EXACTA del concepto (bytes iguales: sin ignorar mayúsculas, acentos ni
--      espacios) con los nombres que el código usa hoy. tipo = 'OTRO' por defecto: los escritores
--      actuales (alta manual de productos, InicializadorDatos, tarifa competidor creada al vuelo)
--      no conocen el tipo, y OTRO es el único valor que no afirma nada; un producto solo entra en
--      una búsqueda por tipo cuando se ha clasificado expresamente.
--      Conceptos repetidos: se clasifica el de menor id (el que devuelve hoy findFirstByConcepto
--      sin ORDER BY en la práctica); los demás quedan OTRO / codigo NULL. Conceptos sin
--      coincidencia exacta: OTRO / codigo NULL. Ver docs/multi-escuela/postcheck-v24.sql.
--      Examen y recompensa sin disciplina: el mismo producto sirve hoy a taekwondo y kickboxing.
--   4. grado.producto_examen_id / producto_recompensa_id: el producto de TipoGrado de cada grado,
--      en los dos sistemas (la copia de kickboxing usa los mismos productos, como hoy
--      AlumnoServiceImpl.resolverConceptoProducto, que no distingue disciplina).
--      Las variantes «ROJO BORDADO» (AlumnoServiceImpl) se clasifican (EXAMEN/RECOMPENSA, codigo)
--      pero no se enlazan a ningún grado: el modelo tiene un solo producto de examen y uno de
--      recompensa por grado.
--   5. disciplina.producto_competidor_id (TARIFA COMPETIDOR TAEKWONDO/KICKBOXING) y
--      producto_licencia_id. La licencia queda NULL: no hay un producto de licencia por
--      disciplina sino variantes por edad, discapacidad y periodo (ProductoAlumnoServiceImpl),
--      que se identifican por codigo.
--
-- Las claves ajenas hacia producto son ON DELETE SET NULL: hoy se puede borrar cualquier producto
-- (ProductoServiceImpl.eliminarProducto) y ese comportamiento no debe cambiar.
--
-- SOLO AMPLÍA: no cambia ninguna columna existente ni ningún llamador. Las filas creadas o
-- modificadas después de V24 por el código actual (tipo_tarifa, productos nuevos) no se
-- sincronizan: la migración del cambio de llamadores debe repetir estos rellenos.
-- Todas las sentencias son repetibles sin efectos duplicados.

SET @schema_name = DATABASE();

-- ----------------------------------------------------------------------------
-- tarifa
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tarifa (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  disciplina_id BIGINT NULL,
  codigo VARCHAR(40) NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  importe DECIMAL(8,2) NOT NULL,
  importe_adulto DECIMAL(8,2) NULL,
  importe_menor DECIMAL(8,2) NULL,
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tarifa_organizacion_codigo (organizacion_id, codigo),
  KEY idx_tarifa_disciplina (disciplina_id),
  CONSTRAINT fk_tarifa_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_tarifa_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Semillas de Taemoi, en el orden de TarifaConfig.init (el de obtenerTodasLasCuantias).
-- Nombres: los de los desplegables actuales de la aplicación.
INSERT INTO tarifa (organizacion_id, disciplina_id, codigo, nombre, importe, importe_adulto, importe_menor, activa)
SELECT 1, d.id, t.codigo, t.nombre, t.importe, t.importe_adulto, t.importe_menor, TRUE
FROM (
  SELECT 1 AS orden, 'FAMILIAR' AS codigo, 'Familiar' AS nombre, 0.00 AS importe,
         NULL AS importe_adulto, NULL AS importe_menor, 'TAEKWONDO' AS disciplina
  UNION ALL SELECT 2, 'PADRES_HIJOS', 'Padres e Hijos', 0.00, 28.00, 26.00, 'TAEKWONDO'
  UNION ALL SELECT 3, 'ADULTO_GRUPO', 'Adulto Grupo', 20.00, NULL, NULL, 'TAEKWONDO'
  UNION ALL SELECT 4, 'INFANTIL_GRUPO', 'Infantil Grupo', 20.00, NULL, NULL, 'TAEKWONDO'
  UNION ALL SELECT 5, 'HERMANOS', 'Hermanos', 26.00, NULL, NULL, 'TAEKWONDO'
  UNION ALL SELECT 6, 'INFANTIL', 'Infantil', 28.00, NULL, NULL, 'TAEKWONDO'
  UNION ALL SELECT 7, 'ADULTO', 'Adulto', 30.00, NULL, NULL, 'TAEKWONDO'
  UNION ALL SELECT 8, 'KICKBOXING', 'Kickboxing', 30.00, NULL, NULL, 'KICKBOXING'
  UNION ALL SELECT 9, 'PILATES', 'Pilates', 30.00, NULL, NULL, 'PILATES'
  UNION ALL SELECT 10, 'DEFENSA_PERSONAL_FEMENINA', 'Defensa Personal Femenina', 25.00, NULL, NULL,
                   'DEFENSA_PERSONAL_FEMENINA'
) t
LEFT JOIN disciplina d
  ON d.organizacion_id = 1
 AND CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(t.disciplina USING utf8mb4) COLLATE utf8mb4_bin
WHERE EXISTS (SELECT 1 FROM organizacion WHERE id = 1)
  AND NOT EXISTS (
    SELECT 1 FROM tarifa x
    WHERE x.organizacion_id = 1
      AND CONVERT(x.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(t.codigo USING utf8mb4) COLLATE utf8mb4_bin
  )
ORDER BY t.orden;

-- ----------------------------------------------------------------------------
-- alumno_deporte.tarifa_id
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'tarifa_id'
        ),
        'SELECT ''alumno_deporte.tarifa_id already exists''',
        'ALTER TABLE alumno_deporte ADD COLUMN tarifa_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte' AND INDEX_NAME = 'idx_alumno_deporte_tarifa'
        ),
        'SELECT ''idx_alumno_deporte_tarifa already exists''',
        'ALTER TABLE alumno_deporte ADD INDEX idx_alumno_deporte_tarifa (tarifa_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE alumno_deporte ad
JOIN tarifa t
  ON t.organizacion_id = ad.organizacion_id
 AND CONVERT(t.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(ad.tipo_tarifa USING utf8mb4) COLLATE utf8mb4_bin
SET ad.tarifa_id = t.id
WHERE ad.tarifa_id IS NULL
  AND ad.tipo_tarifa IS NOT NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte'
              AND CONSTRAINT_NAME = 'fk_alumno_deporte_tarifa' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_alumno_deporte_tarifa already exists''',
        'ALTER TABLE alumno_deporte ADD CONSTRAINT fk_alumno_deporte_tarifa FOREIGN KEY (tarifa_id) REFERENCES tarifa(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- producto: codigo, tipo, disciplina_id (todas a la vez; ALTER atómico)
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto' AND COLUMN_NAME = 'codigo'
        ),
        'SELECT ''producto catalog columns already exist''',
        'ALTER TABLE producto
           ADD COLUMN codigo VARCHAR(60) NULL,
           ADD COLUMN tipo ENUM(''MENSUALIDAD'', ''RESERVA_PLAZA'', ''LICENCIA'', ''COMPETIDOR'', ''EXAMEN'', ''RECOMPENSA'', ''OTRO'') NOT NULL DEFAULT ''OTRO'',
           ADD COLUMN disciplina_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto' AND INDEX_NAME = 'uk_producto_organizacion_codigo'
        ),
        'SELECT ''uk_producto_organizacion_codigo already exists''',
        'ALTER TABLE producto ADD CONSTRAINT uk_producto_organizacion_codigo UNIQUE (organizacion_id, codigo)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto' AND INDEX_NAME = 'idx_producto_disciplina'
        ),
        'SELECT ''idx_producto_disciplina already exists''',
        'ALTER TABLE producto ADD INDEX idx_producto_disciplina (disciplina_id)'
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
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'producto'
              AND CONSTRAINT_NAME = 'fk_producto_disciplina' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_producto_disciplina already exists''',
        'ALTER TABLE producto ADD CONSTRAINT fk_producto_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplina(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- Conceptos que el código busca hoy por nombre (docs/multi-escuela/valores-actuales.md §1 y §7):
-- InicializadorDatos, ProductoAlumnoServiceImpl, TipoGrado y AlumnoServiceImpl (rojo bordado).
DROP TEMPORARY TABLE IF EXISTS v24_producto_conocido;
CREATE TEMPORARY TABLE v24_producto_conocido (
  concepto VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL,
  codigo VARCHAR(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  tipo VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  disciplina VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
  PRIMARY KEY (codigo)
);
INSERT INTO v24_producto_conocido (concepto, codigo, tipo, disciplina) VALUES
  ('MENSUALIDAD', 'MENSUALIDAD', 'MENSUALIDAD', NULL),
  ('RESERVA DE PLAZA', 'RESERVA_PLAZA', 'RESERVA_PLAZA', NULL),
  ('LICENCIA FEDERATIVA', 'LICENCIA_FEDERATIVA', 'LICENCIA', NULL),
  ('LICENCIA FEDERATIVA TAEKWONDO ADULTO', 'LICENCIA_TAEKWONDO_ADULTO', 'LICENCIA', 'TAEKWONDO'),
  ('LICENCIA FEDERATIVA TAEKWONDO INFANTIL', 'LICENCIA_TAEKWONDO_INFANTIL', 'LICENCIA', 'TAEKWONDO'),
  ('LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD', 'LICENCIA_TAEKWONDO_DISCAPACIDAD', 'LICENCIA', 'TAEKWONDO'),
  ('PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO', 'LICENCIA_TAEKWONDO_ADULTO_PROPORCIONAL', 'LICENCIA', 'TAEKWONDO'),
  ('PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL', 'LICENCIA_TAEKWONDO_INFANTIL_PROPORCIONAL', 'LICENCIA', 'TAEKWONDO'),
  ('PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD', 'LICENCIA_TAEKWONDO_DISCAPACIDAD_PROPORCIONAL', 'LICENCIA', 'TAEKWONDO'),
  ('LICENCIA FEDERATIVA KICKBOXING ADULTO', 'LICENCIA_KICKBOXING_ADULTO', 'LICENCIA', 'KICKBOXING'),
  ('LICENCIA FEDERATIVA KICKBOXING INFANTIL', 'LICENCIA_KICKBOXING_INFANTIL', 'LICENCIA', 'KICKBOXING'),
  ('TARIFA COMPETIDOR TAEKWONDO', 'COMPETIDOR_TAEKWONDO', 'COMPETIDOR', 'TAEKWONDO'),
  ('TARIFA COMPETIDOR KICKBOXING', 'COMPETIDOR_KICKBOXING', 'COMPETIDOR', 'KICKBOXING'),
  ('DERECHO A EXAMEN BLANCO/AMARILLO', 'EXAMEN_BLANCO_AMARILLO', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN AMARILLO', 'EXAMEN_AMARILLO', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN AMARILLO/NARANJA', 'EXAMEN_AMARILLO_NARANJA', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN NARANJA', 'EXAMEN_NARANJA', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN NARANJA/VERDE', 'EXAMEN_NARANJA_VERDE', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN VERDE', 'EXAMEN_VERDE', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN VERDE/AZUL', 'EXAMEN_VERDE_AZUL', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN AZUL', 'EXAMEN_AZUL', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN AZUL/ROJO', 'EXAMEN_AZUL_ROJO', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN ROJO', 'EXAMEN_ROJO', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN ROJO/NEGRO 1º PUM', 'EXAMEN_ROJO_NEGRO_1_PUM', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN ROJO/NEGRO 2º PUM', 'EXAMEN_ROJO_NEGRO_2_PUM', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN NEGRO 1º DAN', 'EXAMEN_NEGRO_1_DAN', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN NEGRO 2º DAN', 'EXAMEN_NEGRO_2_DAN', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN NEGRO 3º DAN', 'EXAMEN_NEGRO_3_DAN', 'EXAMEN', NULL),
  ('DERECHO A EXAMEN ROJO BORDADO', 'EXAMEN_ROJO_BORDADO', 'EXAMEN', NULL),
  ('DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA', 'RECOMPENSA_BLANCO_AMARILLO', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA', 'RECOMPENSA_AMARILLO', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA', 'RECOMPENSA_AMARILLO_NARANJA', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A NARANJA POR RECOMPENSA', 'RECOMPENSA_NARANJA', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA', 'RECOMPENSA_NARANJA_VERDE', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A VERDE RECOMPENSA', 'RECOMPENSA_VERDE', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA', 'RECOMPENSA_VERDE_AZUL', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A AZUL POR RECOMPENSA', 'RECOMPENSA_AZUL', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA', 'RECOMPENSA_AZUL_ROJO', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A ROJO POR RECOMPENSA', 'RECOMPENSA_ROJO', 'RECOMPENSA', NULL),
  ('DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA', 'RECOMPENSA_ROJO_BORDADO', 'RECOMPENSA', NULL);

-- Producto canónico de cada concepto: el de menor id con el concepto exacto (bytes iguales).
DROP TEMPORARY TABLE IF EXISTS v24_producto_canonico;
CREATE TEMPORARY TABLE v24_producto_canonico (
  codigo VARCHAR(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  producto_id BIGINT NOT NULL,
  PRIMARY KEY (codigo)
);
INSERT INTO v24_producto_canonico (codigo, producto_id)
SELECT k.codigo, MIN(p.id)
FROM producto p
JOIN v24_producto_conocido k
  ON CAST(CONVERT(p.concepto USING utf8mb4) AS BINARY) = CAST(k.concepto AS BINARY)
WHERE p.organizacion_id = 1
GROUP BY k.codigo;

-- Repetible: un codigo ya asignado no se vuelve a asignar (ni a otro producto).
DELETE c
FROM v24_producto_canonico c
JOIN producto p
  ON p.organizacion_id = 1
 AND CONVERT(p.codigo USING utf8mb4) COLLATE utf8mb4_bin = c.codigo;

UPDATE producto p
JOIN v24_producto_canonico c ON c.producto_id = p.id
JOIN v24_producto_conocido k ON k.codigo = c.codigo
LEFT JOIN disciplina d
  ON d.organizacion_id = p.organizacion_id
 AND CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin = k.disciplina
SET p.codigo = k.codigo,
    p.tipo = k.tipo,
    p.disciplina_id = d.id
WHERE p.organizacion_id = 1
  AND p.codigo IS NULL;

DROP TEMPORARY TABLE IF EXISTS v24_producto_canonico;
DROP TEMPORARY TABLE IF EXISTS v24_producto_conocido;

-- ----------------------------------------------------------------------------
-- grado: producto_examen_id / producto_recompensa_id
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grado' AND COLUMN_NAME = 'producto_examen_id'
        ),
        'SELECT ''grado product columns already exist''',
        'ALTER TABLE grado
           ADD COLUMN producto_examen_id BIGINT NULL,
           ADD COLUMN producto_recompensa_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grado' AND INDEX_NAME = 'idx_grado_producto_examen'
        ),
        'SELECT ''idx_grado_producto_examen already exists''',
        'ALTER TABLE grado ADD INDEX idx_grado_producto_examen (producto_examen_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grado' AND INDEX_NAME = 'idx_grado_producto_recompensa'
        ),
        'SELECT ''idx_grado_producto_recompensa already exists''',
        'ALTER TABLE grado ADD INDEX idx_grado_producto_recompensa (producto_recompensa_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Enlace por codigo: EXAMEN_<codigo de grado> y RECOMPENSA_<codigo de grado>, en todos los
-- sistemas de la organización 1 (taekwondo y la copia de kickboxing).
UPDATE grado g
JOIN producto p
  ON p.organizacion_id = g.organizacion_id
 AND CONVERT(p.codigo USING utf8mb4) COLLATE utf8mb4_bin
     = CONVERT(CONCAT('EXAMEN_', g.codigo) USING utf8mb4) COLLATE utf8mb4_bin
SET g.producto_examen_id = p.id
WHERE g.organizacion_id = 1
  AND g.codigo IS NOT NULL
  AND g.producto_examen_id IS NULL;

UPDATE grado g
JOIN producto p
  ON p.organizacion_id = g.organizacion_id
 AND CONVERT(p.codigo USING utf8mb4) COLLATE utf8mb4_bin
     = CONVERT(CONCAT('RECOMPENSA_', g.codigo) USING utf8mb4) COLLATE utf8mb4_bin
SET g.producto_recompensa_id = p.id
WHERE g.organizacion_id = 1
  AND g.codigo IS NOT NULL
  AND g.producto_recompensa_id IS NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'grado'
              AND CONSTRAINT_NAME = 'fk_grado_producto_examen' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_grado_producto_examen already exists''',
        'ALTER TABLE grado ADD CONSTRAINT fk_grado_producto_examen FOREIGN KEY (producto_examen_id) REFERENCES producto(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'grado'
              AND CONSTRAINT_NAME = 'fk_grado_producto_recompensa' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_grado_producto_recompensa already exists''',
        'ALTER TABLE grado ADD CONSTRAINT fk_grado_producto_recompensa FOREIGN KEY (producto_recompensa_id) REFERENCES producto(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;

-- ----------------------------------------------------------------------------
-- disciplina: producto_licencia_id / producto_competidor_id
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'disciplina' AND COLUMN_NAME = 'producto_licencia_id'
        ),
        'SELECT ''disciplina product columns already exist''',
        'ALTER TABLE disciplina
           ADD COLUMN producto_licencia_id BIGINT NULL,
           ADD COLUMN producto_competidor_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'disciplina' AND INDEX_NAME = 'idx_disciplina_producto_licencia'
        ),
        'SELECT ''idx_disciplina_producto_licencia already exists''',
        'ALTER TABLE disciplina ADD INDEX idx_disciplina_producto_licencia (producto_licencia_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'disciplina' AND INDEX_NAME = 'idx_disciplina_producto_competidor'
        ),
        'SELECT ''idx_disciplina_producto_competidor already exists''',
        'ALTER TABLE disciplina ADD INDEX idx_disciplina_producto_competidor (producto_competidor_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Competidor: COMPETIDOR_<codigo de disciplina> (TARIFA COMPETIDOR TAEKWONDO / KICKBOXING).
UPDATE disciplina d
JOIN producto p
  ON p.organizacion_id = d.organizacion_id
 AND CONVERT(p.codigo USING utf8mb4) COLLATE utf8mb4_bin
     = CONVERT(CONCAT('COMPETIDOR_', d.codigo) USING utf8mb4) COLLATE utf8mb4_bin
SET d.producto_competidor_id = p.id
WHERE d.organizacion_id = 1
  AND d.producto_competidor_id IS NULL;

SET @fk_checks_previo = @@foreign_key_checks;
SET foreign_key_checks = 0;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'disciplina'
              AND CONSTRAINT_NAME = 'fk_disciplina_producto_licencia' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_disciplina_producto_licencia already exists''',
        'ALTER TABLE disciplina ADD CONSTRAINT fk_disciplina_producto_licencia FOREIGN KEY (producto_licencia_id) REFERENCES producto(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'disciplina'
              AND CONSTRAINT_NAME = 'fk_disciplina_producto_competidor' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
        ),
        'SELECT ''fk_disciplina_producto_competidor already exists''',
        'ALTER TABLE disciplina ADD CONSTRAINT fk_disciplina_producto_competidor FOREIGN KEY (producto_competidor_id) REFERENCES producto(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET foreign_key_checks = @fk_checks_previo;
