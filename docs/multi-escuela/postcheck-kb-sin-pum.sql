-- Comprobación posterior de V31_20 (kickboxing de la organización 1 sin grados PUM en el material de examen: los tres
-- MARRÓN-NEGRO nº PUM, códigos ROJO_NEGRO_n_PUM, sin bloque_temario). Solo lectura y SOLO RECUENTOS: no devuelve
-- nombres, emails ni identificadores.
-- Juego de caracteres: no compara literales con tildes, así que no depende del juego de caracteres del cliente mysql;
-- aun así, lánzala como las demás (cliente en utf8mb4, p. ej. --default-character-set=utf8mb4).
--
-- Deben ser (justo tras desplegar):
--   flyway_31_20                  1   V31_20 aplicada con éxito
--   ultima_es_31_20               1   es la última versión aplicada
--   pum_kb                        3   los tres PUM de kickboxing siguen existiendo (no se borra ninguna fila)
--   pum_kb_con_bloque             0   ningún PUM de kickboxing tiene bloque de temario
--   inscripciones_kb_con_pum      0   inscripciones de kickboxing (activas o no) cuyo grado es un PUM
--   dan_kb_con_bloque_b07_a_b09   3   NEGRO_1/2/3_DAN de kickboxing conservan b07, b08 y b09: ningún bloque desaparece
--   pum_tk_con_bloque_sembrado    3   taekwondo sin cambios: sus tres PUM siguen con b07, b08 y b09
--
-- Si inscripciones_kb_con_pum sale mayor que 0: hay alumnos (activos o no) con un PUM como grado actual en kickboxing.
-- V31_20 NO les quita el bloque (para que no pierdan su material de examen), así que pum_kb_con_bloque sale igual al
-- número de PUM distintos que tienen esas inscripciones (entre 1 y 3). No es un fallo de la migración: esos alumnos
-- siguen sin grado siguiente (los PUM de kickboxing son NINGUNO desde V23) y hay que decidir con el propietario a qué
-- grado pasan; después, una pieza aparte quitará el bloque que quede.
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.20' AND success = 1)
    AS flyway_31_20,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '31.20')
    AS ultima_es_31_20,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'KICKBOXING'
    WHERE g.organizacion_id = 1 AND g.codigo IN ('ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM'))
    AS pum_kb,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'KICKBOXING'
    WHERE g.organizacion_id = 1 AND g.codigo IN ('ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM')
      AND g.bloque_temario IS NOT NULL)
    AS pum_kb_con_bloque,
  (SELECT COUNT(*) FROM alumno_deporte ad
     JOIN disciplina d ON d.id = ad.disciplina_id AND d.organizacion_id = 1 AND d.codigo = 'KICKBOXING'
     JOIN grado g ON g.id = ad.grado_id
    WHERE g.codigo IN ('ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM'))
    AS inscripciones_kb_con_pum,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'KICKBOXING'
    WHERE g.organizacion_id = 1
      AND CAST(CONVERT(CONCAT(g.codigo, '=', g.bloque_temario) USING utf8mb4) AS BINARY) IN (
            CAST(CONVERT('NEGRO_1_DAN=b07_negro_1_a_negro_2' USING utf8mb4) AS BINARY),
            CAST(CONVERT('NEGRO_2_DAN=b08_negro_2_a_negro_3' USING utf8mb4) AS BINARY),
            CAST(CONVERT('NEGRO_3_DAN=b09_negro_3_a_negro_4' USING utf8mb4) AS BINARY)))
    AS dan_kb_con_bloque_b07_a_b09,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'TAEKWONDO'
    WHERE g.organizacion_id = 1
      AND CAST(CONVERT(CONCAT(g.codigo, '=', g.bloque_temario) USING utf8mb4) AS BINARY) IN (
            CAST(CONVERT('ROJO_NEGRO_1_PUM=b07_negro_1_a_negro_2' USING utf8mb4) AS BINARY),
            CAST(CONVERT('ROJO_NEGRO_2_PUM=b08_negro_2_a_negro_3' USING utf8mb4) AS BINARY),
            CAST(CONVERT('ROJO_NEGRO_3_PUM=b09_negro_3_a_negro_4' USING utf8mb4) AS BINARY)))
    AS pum_tk_con_bloque_sembrado;
