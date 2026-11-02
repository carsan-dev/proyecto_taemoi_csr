-- Grados de kickboxing de la organización 1 sin rojo: en kickboxing el cinturón que el código llama ROJO es marrón.
-- V30 ya llamó «MARRÓN» (color #8B4513) al grado ROJO del sistema de kickboxing de la organización 1, pero dejó «ROJO» y
-- el color rojo #FF0000 en los intermedios que V23 copió de taekwondo. Esta migración los corrige en la fuente (el
-- catálogo), para que todas las pantallas y PDF que leen grado.nombre, grado.color_1 y grado.color_2 los muestren bien.
-- La franja roja pasa al mismo marrón que V30 dio al grado ROJO de kickboxing, #8B4513:
--
--   codigo             nombre antes (V30)    nombre después         color_1 antes→después  color_2 antes→después
--   -----------------  --------------------  ---------------------  ---------------------  ---------------------
--   AZUL_ROJO          AZUL-ROJO             AZUL-MARRÓN            #0000FF (igual)        #FF0000 → #8B4513
--   ROJO_NEGRO_1_PUM   ROJO-NEGRO 1º PUM     MARRÓN-NEGRO 1º PUM    #FF0000 → #8B4513      #000000 (igual)
--   ROJO_NEGRO_2_PUM   ROJO-NEGRO 2º PUM     MARRÓN-NEGRO 2º PUM    #FF0000 → #8B4513      #000000 (igual)
--   ROJO_NEGRO_3_PUM   ROJO-NEGRO 3º PUM     MARRÓN-NEGRO 3º PUM    #FF0000 → #8B4513      #000000 (igual)
--
-- Son todos los grados de ese sistema con «ROJO» en el nombre o #FF0000 de color tras V30 (ROJO ya es «MARRÓN» #8B4513).
-- Convención de V30: color_1 = mitad inferior (primer color del código), color_2 = mitad superior.
--
-- SOLO cambian grado.nombre, grado.color_1 y grado.color_2 de esas filas. No cambian los códigos (AZUL_ROJO,
-- ROJO_NEGRO_n_PUM: los usan el código, los productos EXAMEN_/RECOMPENSA_ y los alumnos), ni el resto de columnas, ni
-- taekwondo, ni productos, ni alumnos. Ninguna fila se borra ni se crea; el esquema no cambia.
--
-- Alcance: solo la organización 1. Los grados de kickboxing con estos valores solo los sembraron V23 y V30, y solo para la
-- organización 1 (la API del catálogo es de solo lectura y ningún código crea sistemas de kickboxing para otras escuelas).
-- Si otra escuela tuviera un grado con el mismo valor, lo habría escrito ella y es suyo: no se toca.
-- Filas: las del sistema de grados al que apunta la disciplina KICKBOXING de la organización 1 (y que tenga plantilla_origen
-- KICKBOXING, como el de V23: así nunca se toca el de taekwondo), con el código de la tabla. Cada valor (nombre, color_1,
-- color_2) cambia por separado y solo si es EXACTAMENTE (comparación binaria) el que dejó V30: un valor distinto (otro
-- texto, otras mayúsculas, otro color) no se toca, aunque los demás de la fila sí cambien.
--
-- Código anterior: no depende de estos nombres ni colores (solo los muestra), así que la imagen anterior funciona sobre
-- este esquema. Vuelta atrás: el UPDATE inverso con la misma tabla (después -> antes), si hiciera falta.
--
-- Repetible: una sola sentencia UPDATE (atómica en InnoDB: o cambian nombres y colores juntos o nada). Cada SET solo lee su
-- propia columna, así que el orden en que MySQL los aplique no importa. Tras aplicarla, ningún valor coincide con el
-- «antes», así que repetirla no cambia nada; si falló, no aplicó nada y volver a lanzarla la completa. Tras un fallo,
-- `flyway repair` y volver a desplegar. Va como V31_19 (detrás de 31.18) porque V32 está reservada para la contracción
-- multi-escuela.
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
  SELECT 'AZUL_ROJO' AS codigo, 'AZUL-ROJO' AS nombre_antes, 'AZUL-MARRÓN' AS nombre_despues,
         CAST(NULL AS CHAR(7)) AS color_1_antes, CAST('#FF0000' AS CHAR(7)) AS color_2_antes
  UNION ALL SELECT 'ROJO_NEGRO_1_PUM', 'ROJO-NEGRO 1º PUM', 'MARRÓN-NEGRO 1º PUM', '#FF0000', NULL
  UNION ALL SELECT 'ROJO_NEGRO_2_PUM', 'ROJO-NEGRO 2º PUM', 'MARRÓN-NEGRO 2º PUM', '#FF0000', NULL
  UNION ALL SELECT 'ROJO_NEGRO_3_PUM', 'ROJO-NEGRO 3º PUM', 'MARRÓN-NEGRO 3º PUM', '#FF0000', NULL
) c
  ON CONVERT(c.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(g.codigo USING utf8mb4) COLLATE utf8mb4_bin
SET g.nombre = CASE
      WHEN CONVERT(g.nombre USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(c.nombre_antes USING utf8mb4) COLLATE utf8mb4_bin
        THEN c.nombre_despues
      ELSE g.nombre
    END,
    g.color_1 = CASE
      WHEN c.color_1_antes IS NOT NULL
       AND CONVERT(g.color_1 USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(c.color_1_antes USING utf8mb4) COLLATE utf8mb4_bin
        THEN '#8B4513'
      ELSE g.color_1
    END,
    g.color_2 = CASE
      WHEN c.color_2_antes IS NOT NULL
       AND CONVERT(g.color_2 USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(c.color_2_antes USING utf8mb4) COLLATE utf8mb4_bin
        THEN '#8B4513'
      ELSE g.color_2
    END
WHERE g.organizacion_id = 1;
