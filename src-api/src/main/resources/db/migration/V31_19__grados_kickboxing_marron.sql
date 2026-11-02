-- Grados de kickboxing de la organización 1 sin «ROJO»: en kickboxing el cinturón que el código llama ROJO es marrón.
-- V30 ya llamó «MARRÓN» (color #8B4513) al grado ROJO del sistema de kickboxing de la organización 1, pero dejó «ROJO» en
-- los intermedios que V23 copió de taekwondo. Esta migración los renombra en la fuente (el catálogo), para que todas las
-- pantallas y PDF que leen grado.nombre los muestren bien:
--
--   codigo             antes (V30)           después
--   -----------------  --------------------  ----------------------
--   AZUL_ROJO          AZUL-ROJO             AZUL-MARRÓN
--   ROJO_NEGRO_1_PUM   ROJO-NEGRO 1º PUM     MARRÓN-NEGRO 1º PUM
--   ROJO_NEGRO_2_PUM   ROJO-NEGRO 2º PUM     MARRÓN-NEGRO 2º PUM
--   ROJO_NEGRO_3_PUM   ROJO-NEGRO 3º PUM     MARRÓN-NEGRO 3º PUM
--
-- SOLO cambia grado.nombre de esas filas. No cambian los códigos (AZUL_ROJO, ROJO_NEGRO_n_PUM: los usan el código, los
-- productos EXAMEN_/RECOMPENSA_ y los alumnos), ni los colores (color_1/color_2 siguen como los dejó V30), ni taekwondo, ni
-- productos, ni alumnos. Ninguna fila se borra ni se crea; el esquema no cambia.
--
-- Alcance: solo la organización 1. Los grados de kickboxing con estos nombres solo los sembraron V23 y V30, y solo para la
-- organización 1 (la API del catálogo es de solo lectura y ningún código crea sistemas de kickboxing para otras escuelas).
-- Si otra escuela tuviera un grado con el mismo texto, lo habría escrito ella y es suyo: no se toca.
-- Filas: las del sistema de grados al que apunta la disciplina KICKBOXING de la organización 1 (y que tenga plantilla_origen
-- KICKBOXING, como el de V23: así nunca se toca el de taekwondo), con el código de la tabla y el nombre EXACTAMENTE igual
-- (comparación binaria) al que dejó V30. Un nombre distinto (otro texto, otras mayúsculas) no se toca.
--
-- Código anterior: no depende de estos nombres (solo los muestra), así que la imagen anterior funciona sobre este esquema.
-- Vuelta atrás: el UPDATE inverso con la misma tabla (después -> antes), si hiciera falta.
--
-- Repetible: una sola sentencia UPDATE (atómica en InnoDB). Tras aplicarla, ningún nombre coincide con el «antes», así que
-- repetirla no cambia nada; si falló, no aplicó nada y volver a lanzarla la completa. Tras un fallo, `flyway repair` y
-- volver a desplegar. Va como V31_19 (detrás de 31.18) porque V32 está reservada para la contracción multi-escuela.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-grados-kickboxing.sql.

UPDATE grado g
JOIN disciplina kb
  ON kb.organizacion_id = 1
 AND CONVERT(kb.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT('KICKBOXING' USING utf8mb4) COLLATE utf8mb4_bin
 AND kb.sistema_grados_id = g.sistema_grados_id
JOIN sistema_grados sg
  ON sg.id = g.sistema_grados_id
 AND sg.organizacion_id = 1
 AND CONVERT(sg.plantilla_origen USING utf8mb4) COLLATE utf8mb4_bin = CONVERT('KICKBOXING' USING utf8mb4) COLLATE utf8mb4_bin
JOIN (
  SELECT 'AZUL_ROJO' AS codigo, 'AZUL-ROJO' AS antes, 'AZUL-MARRÓN' AS despues
  UNION ALL SELECT 'ROJO_NEGRO_1_PUM', 'ROJO-NEGRO 1º PUM', 'MARRÓN-NEGRO 1º PUM'
  UNION ALL SELECT 'ROJO_NEGRO_2_PUM', 'ROJO-NEGRO 2º PUM', 'MARRÓN-NEGRO 2º PUM'
  UNION ALL SELECT 'ROJO_NEGRO_3_PUM', 'ROJO-NEGRO 3º PUM', 'MARRÓN-NEGRO 3º PUM'
) c
  ON CONVERT(c.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(g.codigo USING utf8mb4) COLLATE utf8mb4_bin
 AND CONVERT(c.antes USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(g.nombre USING utf8mb4) COLLATE utf8mb4_bin
SET g.nombre = c.despues
WHERE g.organizacion_id = 1;
