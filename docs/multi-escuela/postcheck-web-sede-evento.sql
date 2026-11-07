-- Comprobación posterior de V32_6 (web por plantilla, pieza W2 de fase4-web-plan.md §4.2). Solo lectura y SOLO
-- RECUENTOS: no devuelve nombres, textos, slugs ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_32_6                      1   V32_6 aplicada con éxito
--   ultima_es_32_6                   1   es la última versión aplicada
--   columnas_sede                    5   slug, lugar, indicaciones_acceso, foto_imagen_id, foto2_imagen_id
--   columnas_evento                  3   hora, lugar, sede_id
--   claves_ajenas                    3   sede -> imagen (dos) y evento -> sede, las tres ON DELETE SET NULL
--   slug_unico_por_escuela           1   índice único (organizacion_id, slug)
--   sedes                            =   el mismo número de sedes que antes de desplegar (V32_6 no crea ni borra)
--   eventos                          =   el mismo número de eventos que antes de desplegar
--   sedes_con_datos_web              0   V32_6 no rellena nada. Crece cuando la gestión los pone.
--   eventos_con_datos_web            0   ídem.
--   sedes_moiskimdo_con_datos_web    0   ídem para la organización 1 (crece solo si su gestión los pone).
--   eventos_moiskimdo_con_datos_web  0   ídem.
--   eventos_con_sede_de_otra_escuela 0   SIEMPRE 0: la sede de un evento es de su misma escuela.
--   fotos_de_otra_escuela            0   SIEMPRE 0: las fotos de una sede son imágenes de su misma escuela.
--   slugs_no_validos                 0   SIEMPRE 0: minúsculas sin tildes, cifras y guiones sueltos entre ellas.
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.6' AND success = 1)
    AS flyway_32_6,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '32.6')
    AS ultima_es_32_6,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sede' AND IS_NULLABLE = 'YES'
      AND COLUMN_NAME IN ('slug', 'lugar', 'indicaciones_acceso', 'foto_imagen_id', 'foto2_imagen_id'))
    AS columnas_sede,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'evento' AND IS_NULLABLE = 'YES'
      AND COLUMN_NAME IN ('hora', 'lugar', 'sede_id'))
    AS columnas_evento,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND DELETE_RULE = 'SET NULL' AND (
      (TABLE_NAME = 'sede' AND REFERENCED_TABLE_NAME = 'imagen'
        AND CONSTRAINT_NAME IN ('fk_sede_foto_imagen', 'fk_sede_foto2_imagen'))
      OR (TABLE_NAME = 'evento' AND REFERENCED_TABLE_NAME = 'sede' AND CONSTRAINT_NAME = 'fk_evento_sede')))
    AS claves_ajenas,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sede' AND INDEX_NAME = 'uk_sede_organizacion_slug'
      AND NON_UNIQUE = 0)
    AS slug_unico_por_escuela,
  (SELECT COUNT(*) FROM sede)
    AS sedes,
  (SELECT COUNT(*) FROM evento)
    AS eventos,
  (SELECT COUNT(*) FROM sede
    WHERE slug IS NOT NULL OR lugar IS NOT NULL OR indicaciones_acceso IS NOT NULL
       OR foto_imagen_id IS NOT NULL OR foto2_imagen_id IS NOT NULL)
    AS sedes_con_datos_web,
  (SELECT COUNT(*) FROM evento WHERE hora IS NOT NULL OR lugar IS NOT NULL OR sede_id IS NOT NULL)
    AS eventos_con_datos_web,
  (SELECT COUNT(*) FROM sede
    WHERE organizacion_id = 1 AND (slug IS NOT NULL OR lugar IS NOT NULL OR indicaciones_acceso IS NOT NULL
       OR foto_imagen_id IS NOT NULL OR foto2_imagen_id IS NOT NULL))
    AS sedes_moiskimdo_con_datos_web,
  (SELECT COUNT(*) FROM evento
    WHERE organizacion_id = 1 AND (hora IS NOT NULL OR lugar IS NOT NULL OR sede_id IS NOT NULL))
    AS eventos_moiskimdo_con_datos_web,
  (SELECT COUNT(*) FROM evento e JOIN sede s ON s.id = e.sede_id WHERE s.organizacion_id <> e.organizacion_id)
    AS eventos_con_sede_de_otra_escuela,
  (SELECT COUNT(*) FROM sede s JOIN imagen i ON i.id IN (s.foto_imagen_id, s.foto2_imagen_id)
    WHERE i.organizacion_id <> s.organizacion_id)
    AS fotos_de_otra_escuela,
  (SELECT COUNT(*) FROM sede
    WHERE slug IS NOT NULL AND NOT REGEXP_LIKE(slug, '^[a-z0-9]+(-[a-z0-9]+)*$', 'c'))
    AS slugs_no_validos;
