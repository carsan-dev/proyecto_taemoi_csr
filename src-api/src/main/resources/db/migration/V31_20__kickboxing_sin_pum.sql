-- Kickboxing de la organización 1 sin grados PUM en el material de examen: en kickboxing no existen los PUM.
-- V23 copió de taekwondo los tres grados PUM (códigos ROJO_NEGRO_n_PUM; V31_19 los llamó MARRÓN-NEGRO nº PUM) con su
-- bloque de temario. Por eso el material de examen de kickboxing agrupa cada PUM con un DAN y dice, por ejemplo,
-- «Alumnos de Negro 1º DAN a Marrón-negro 1º PUM». Esta migración les quita el bloque:
--
--   codigo             bloque_temario antes (V23)   después
--   -----------------  ---------------------------  -------
--   ROJO_NEGRO_1_PUM   b07_negro_1_a_negro_2        NULL
--   ROJO_NEGRO_2_PUM   b08_negro_2_a_negro_3        NULL
--   ROJO_NEGRO_3_PUM   b09_negro_3_a_negro_4        NULL
--
-- Los bloques b07, b08 y b09 siguen existiendo: los tienen NEGRO_1_DAN, NEGRO_2_DAN y NEGRO_3_DAN de kickboxing.
--
-- SOLO cambia grado.bloque_temario de esas filas. Las filas NO se borran (alumno_deporte.grado_id y las convocatorias
-- antiguas pueden nombrarlas) y no cambian su código, nombre, orden, colores ni aplica_a (ya son NINGUNO desde V23: nunca
-- son destino ni se pueden elegir). Ni taekwondo, ni productos, ni alumnos. El esquema no cambia.
--
-- Alumnos: un PUM de kickboxing que tenga alguna inscripción (alumno_deporte.grado_id, activa o no) conserva su bloque,
-- para que ese alumno no pierda su material de examen (el material visible es el del bloque de su grado actual). Esas
-- inscripciones, si las hubiera, las decide el propietario; el postcheck las cuenta.
--
-- Alcance: solo la organización 1. Los grados de kickboxing con estos valores solo los sembró V23, y solo para la
-- organización 1. Filas: las del sistema de grados al que apunta la disciplina KICKBOXING de la organización 1 (y que
-- tenga plantilla_origen KICKBOXING, como el de V23: así nunca se toca el de taekwondo), con el código de la tabla, y
-- solo si su bloque_temario es EXACTAMENTE el sembrado: un valor distinto (otro texto, otras mayúsculas, un espacio de
-- más al final) no se toca.
-- Comparación exacta: todas las igualdades (KICKBOXING, código y bloque) comparan los bytes UTF-8 con
-- CAST(CONVERT(x USING utf8mb4) AS BINARY). No vale COLLATE utf8mb4_bin: es PAD SPACE en MySQL 8 y daría
-- 'b07_negro_1_a_negro_2 ' = 'b07_negro_1_a_negro_2'. Es el patrón de V31_19 y de postcheck-v24.sql.
--
-- Código anterior: ya admite grados sin bloque (bloque_temario es NULL-able desde V23 y los grados de otras escuelas
-- pueden no tenerlo), así que la imagen anterior funciona sobre este esquema. Vuelta atrás: el UPDATE inverso con la
-- tabla (NULL -> antes) para esos tres códigos del sistema de kickboxing de la organización 1, si hiciera falta.
--
-- Repetible: una sola sentencia UPDATE (atómica en InnoDB: o cambian las tres filas o ninguna). Tras aplicarla, ningún
-- bloque coincide con el «antes», así que repetirla no cambia nada; si falló, no aplicó nada y volver a lanzarla la
-- completa. Tras un fallo, `flyway repair` y volver a desplegar. Va como V31_20 (detrás de 31.19) porque V32 está
-- reservada para la contracción multi-escuela.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-kb-sin-pum.sql.

UPDATE grado g
JOIN disciplina kb
  ON kb.organizacion_id = 1
 AND CAST(CONVERT(kb.codigo USING utf8mb4) AS BINARY) = CAST(CONVERT('KICKBOXING' USING utf8mb4) AS BINARY)
 AND kb.sistema_grados_id = g.sistema_grados_id
JOIN sistema_grados sg
  ON sg.id = g.sistema_grados_id
 AND sg.organizacion_id = 1
 AND CAST(CONVERT(sg.plantilla_origen USING utf8mb4) AS BINARY) = CAST(CONVERT('KICKBOXING' USING utf8mb4) AS BINARY)
JOIN (
  SELECT 'ROJO_NEGRO_1_PUM' AS codigo, 'b07_negro_1_a_negro_2' AS bloque_antes
  UNION ALL SELECT 'ROJO_NEGRO_2_PUM', 'b08_negro_2_a_negro_3'
  UNION ALL SELECT 'ROJO_NEGRO_3_PUM', 'b09_negro_3_a_negro_4'
) c
  ON CAST(CONVERT(c.codigo USING utf8mb4) AS BINARY) = CAST(CONVERT(g.codigo USING utf8mb4) AS BINARY)
 AND CAST(CONVERT(g.bloque_temario USING utf8mb4) AS BINARY) = CAST(CONVERT(c.bloque_antes USING utf8mb4) AS BINARY)
SET g.bloque_temario = NULL
WHERE g.organizacion_id = 1
  AND NOT EXISTS (SELECT 1 FROM alumno_deporte ad WHERE ad.grado_id = g.id);
