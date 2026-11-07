-- Comprobación posterior de V32_5 (web por plantilla, pieza W1 de fase4-web-plan.md). Solo lectura y SOLO RECUENTOS: no
-- devuelve nombres, textos, emails ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_32_5                  1   V32_5 aplicada con éxito
--   ultima_es_32_5               1   es la última versión aplicada
--   tablas                       2   web_escuela y web_bloque existen
--   claves_ajenas                3   web_escuela -> organizacion; web_bloque -> organizacion e imagen
--   webs                         0   V32_5 no crea ninguna. Crece cuando una escuela publica su web.
--   bloques                      0   V32_5 no crea ninguno. Crece con el contenido de las webs.
--   web_de_moiskimdo             0   SIEMPRE 0: la organización 1 tiene su web propia, sin fila.
--   bloques_de_moiskimdo         0   SIEMPRE 0: ídem.
--   temas_desconocidos           0   SIEMPRE 0: solo linea-comarcal, chandal, sede-por-pantalla o estandar.
--   publicadas_sin_fecha         0   SIEMPRE 0: una web publicada lleva fecha de publicación.
--   imagenes_de_otra_escuela     0   SIEMPRE 0: la imagen de un bloque es de su misma escuela.
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.5' AND success = 1)
    AS flyway_32_5,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '32.5')
    AS ultima_es_32_5,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('web_escuela', 'web_bloque'))
    AS tablas,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND (
      (TABLE_NAME = 'web_escuela' AND DELETE_RULE = 'NO ACTION' AND REFERENCED_TABLE_NAME = 'organizacion')
      OR (TABLE_NAME = 'web_bloque' AND DELETE_RULE = 'NO ACTION' AND REFERENCED_TABLE_NAME IN ('organizacion', 'imagen'))))
    AS claves_ajenas,
  (SELECT COUNT(*) FROM web_escuela)
    AS webs,
  (SELECT COUNT(*) FROM web_bloque)
    AS bloques,
  (SELECT COUNT(*) FROM web_escuela WHERE organizacion_id = 1)
    AS web_de_moiskimdo,
  (SELECT COUNT(*) FROM web_bloque WHERE organizacion_id = 1)
    AS bloques_de_moiskimdo,
  (SELECT COUNT(*) FROM web_escuela
    WHERE tema NOT IN ('linea-comarcal', 'chandal', 'sede-por-pantalla', 'estandar'))
    AS temas_desconocidos,
  (SELECT COUNT(*) FROM web_escuela WHERE publicada = TRUE AND publicada_en IS NULL)
    AS publicadas_sin_fecha,
  (SELECT COUNT(*) FROM web_bloque b JOIN imagen i ON i.id = b.imagen_id
    WHERE i.organizacion_id <> b.organizacion_id)
    AS imagenes_de_otra_escuela;
