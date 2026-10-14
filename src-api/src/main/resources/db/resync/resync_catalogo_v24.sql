-- Resincronización del catálogo de tarifas y productos (datos de V24). NO es una migración de
-- Flyway: V24 incluye este mismo texto (un test lo comprueba) y la migración del cambio de
-- llamadores debe volver a incluirlo, porque hasta entonces el código actual escribe
-- tipo_tarifa y crea productos sin tocar las columnas nuevas.
-- Requiere el esquema de V24. Repetible: sobre datos ya sincronizados no cambia nada.
--
--   1. alumno_deporte.tarifa_id = tarifa de su organización con codigo = tipo_tarifa, en TODAS
--      las filas (también las ya rellenadas, por si tipo_tarifa cambió); NULL si no hay tipo.
--   2. producto: clasificación (codigo, tipo, disciplina) de la organización 1 por concepto
--      EXACTO (bytes iguales) con los nombres que el código usa hoy. Concepto repetido: el de
--      menor id. Un codigo ya asignado no se reasigna. Sin coincidencia exacta: OTRO, codigo NULL.
--   3. producto.grado_id: grado que concede cada producto de examen o recompensa clasificado, en el
--      sistema de taekwondo (EXAMEN_<grado>, RECOMPENSA_<grado>; las dos variantes «rojo
--      bordado» conceden ROJO). Otras disciplinas que comparten el producto resuelven el mismo
--      codigo de grado en su propio sistema (ProductosCatalogoService).
--   4. grado.producto_examen_id / producto_recompensa_id (producto que se cobra por defecto, el
--      del cinturón normal) y disciplina.producto_competidor_id, donde estén vacíos.

-- 1. alumno_deporte.tarifa_id ------------------------------------------------
UPDATE alumno_deporte ad
JOIN tarifa t
  ON t.organizacion_id = ad.organizacion_id
 AND CONVERT(t.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(ad.tipo_tarifa USING utf8mb4) COLLATE utf8mb4_bin
SET ad.tarifa_id = t.id
WHERE ad.tarifa_id IS NULL
   OR ad.tarifa_id <> t.id;

UPDATE alumno_deporte ad
SET ad.tarifa_id = NULL
WHERE ad.tipo_tarifa IS NULL
  AND ad.tarifa_id IS NOT NULL;

-- 2. producto: clasificación ---------------------------------------------------
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

-- Un codigo ya asignado no se vuelve a asignar (ni a otro producto).
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

-- 3. producto.grado_id -----------------------------------------------------------
UPDATE producto p
JOIN disciplina d
  ON d.organizacion_id = p.organizacion_id
 AND CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT('TAEKWONDO' USING utf8mb4) COLLATE utf8mb4_bin
JOIN grado g
  ON g.organizacion_id = p.organizacion_id
 AND g.sistema_grados_id = d.sistema_grados_id
 AND CONVERT(g.codigo USING utf8mb4) COLLATE utf8mb4_bin
     = CONVERT(CASE
         WHEN p.codigo IN ('EXAMEN_ROJO_BORDADO', 'RECOMPENSA_ROJO_BORDADO') THEN 'ROJO'
         ELSE SUBSTRING(p.codigo, LOCATE('_', p.codigo) + 1)
       END USING utf8mb4) COLLATE utf8mb4_bin
SET p.grado_id = g.id
WHERE p.organizacion_id = 1
  AND p.tipo IN ('EXAMEN', 'RECOMPENSA')
  AND p.codigo IS NOT NULL
  AND p.grado_id IS NULL;

-- 4. Productos por defecto de cada grado y de cada disciplina ----------------------
-- EXAMEN_<codigo de grado> y RECOMPENSA_<codigo de grado>, en todos los sistemas de la
-- organización 1 (taekwondo y la copia de kickboxing, que hoy usa los mismos productos).
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

-- Competidor: COMPETIDOR_<codigo de disciplina> (TARIFA COMPETIDOR TAEKWONDO / KICKBOXING).
-- La licencia queda NULL: hay variantes por edad, discapacidad y periodo (por codigo).
UPDATE disciplina d
JOIN producto p
  ON p.organizacion_id = d.organizacion_id
 AND CONVERT(p.codigo USING utf8mb4) COLLATE utf8mb4_bin
     = CONVERT(CONCAT('COMPETIDOR_', d.codigo) USING utf8mb4) COLLATE utf8mb4_bin
SET d.producto_competidor_id = p.id
WHERE d.organizacion_id = 1
  AND d.producto_competidor_id IS NULL;
