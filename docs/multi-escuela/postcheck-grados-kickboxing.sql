-- Comprobación posterior de V31_19 (grados de kickboxing de la organización 1 sin «ROJO»: AZUL-MARRÓN y MARRÓN-NEGRO
-- 1º/2º/3º PUM). Solo lectura y SOLO RECUENTOS: no devuelve nombres, emails ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_31_19                  1   V31_19 aplicada con éxito
--   ultima_es_31_19               1   es la última versión aplicada
--   grados_kb_con_rojo            0   grados del kickboxing de la organización 1 con «ROJO» en el nombre
--   grados_kb_renombrados         4   AZUL_ROJO = AZUL-MARRÓN y ROJO_NEGRO_n_PUM = MARRÓN-NEGRO nº PUM (n = 1, 2, 3)
--   grados_kb_marron              5   los 4 anteriores y ROJO = MARRÓN (de V30)
--   grados_tk_con_rojo            5   taekwondo de la organización 1 sin cambios: AZUL-ROJO, ROJO y ROJO-NEGRO nº PUM
--   grados_tk_sembrados_con_rojo  5   esos 5, con el código y el nombre exactos de V30
--   grados_tk_marron              0   ningún grado de taekwondo de la organización 1 se llama marrón
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.19' AND success = 1)
    AS flyway_31_19,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '31.19')
    AS ultima_es_31_19,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'KICKBOXING'
    WHERE g.organizacion_id = 1 AND UPPER(g.nombre) LIKE '%ROJO%')
    AS grados_kb_con_rojo,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'KICKBOXING'
    WHERE g.organizacion_id = 1
      AND CONVERT(CONCAT(g.codigo, '=', g.nombre) USING utf8mb4) COLLATE utf8mb4_bin IN (
            'AZUL_ROJO=AZUL-MARRÓN', 'ROJO_NEGRO_1_PUM=MARRÓN-NEGRO 1º PUM',
            'ROJO_NEGRO_2_PUM=MARRÓN-NEGRO 2º PUM', 'ROJO_NEGRO_3_PUM=MARRÓN-NEGRO 3º PUM'))
    AS grados_kb_renombrados,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'KICKBOXING'
    WHERE g.organizacion_id = 1 AND UPPER(g.nombre) LIKE '%MARRÓN%')
    AS grados_kb_marron,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'TAEKWONDO'
    WHERE g.organizacion_id = 1 AND UPPER(g.nombre) LIKE '%ROJO%')
    AS grados_tk_con_rojo,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'TAEKWONDO'
    WHERE g.organizacion_id = 1
      AND CONVERT(CONCAT(g.codigo, '=', g.nombre) USING utf8mb4) COLLATE utf8mb4_bin IN (
            'AZUL_ROJO=AZUL-ROJO', 'ROJO=ROJO', 'ROJO_NEGRO_1_PUM=ROJO-NEGRO 1º PUM',
            'ROJO_NEGRO_2_PUM=ROJO-NEGRO 2º PUM', 'ROJO_NEGRO_3_PUM=ROJO-NEGRO 3º PUM'))
    AS grados_tk_sembrados_con_rojo,
  (SELECT COUNT(*) FROM grado g
     JOIN disciplina d ON d.sistema_grados_id = g.sistema_grados_id AND d.organizacion_id = 1 AND d.codigo = 'TAEKWONDO'
    WHERE g.organizacion_id = 1 AND UPPER(g.nombre) LIKE '%MARRÓN%')
    AS grados_tk_marron;
