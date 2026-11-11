-- Comprobación posterior de V32_9 (web por plantilla, pieza W12a de fase4-w12-plan.md). Solo lectura y SOLO RECUENTOS:
-- no devuelve nombres, textos ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_32_9                      1   V32_9 aplicada con éxito
--   ultima_es_32_9                   1   es la última versión aplicada
--   columna_oculto                   1   web_bloque.oculto: tinyint(1) NOT NULL DEFAULT 0
--   columnas_permiso                 3   web_bloque.permiso_origen, permiso_por y permiso_en, las tres NULL
--   tabla_web_contenido              1   existe web_contenido
--   columnas_sede                    2   sede.foto_alt y foto2_alt: varchar(150) NULL
--   bloques                          =   el mismo número de filas de web_bloque que antes de desplegar
--   bloques_ocultos                  0   V32_9 no oculta nada. Crece cuando el propietario oculta una sección.
--   bloques_con_permiso              0   V32_9 no rellena nada. Crece con los profesores de la web.
--   versiones_de_contenido           0   V32_9 no crea filas. Crece con el primer guardado de cada escuela.
--   sedes                            =   el mismo número de sedes que antes de desplegar
--   sedes_con_descripcion            0   V32_9 no rellena nada. Crece cuando la gestión describe una foto.
--   moiskimdo_bloques                0   SIEMPRE 0: Moiskimdo (organización 1) no usa la web por plantilla.
--   moiskimdo_contenido              0   SIEMPRE 0: ídem.
--   moiskimdo_web                    0   SIEMPRE 0: Moiskimdo no tiene fila en web_escuela.
--   moiskimdo_sedes_con_descripcion  0   0 tras desplegar (crece solo si su gestión describe una foto).
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.9' AND success = 1)
    AS flyway_32_9,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '32.9')
    AS ultima_es_32_9,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'web_bloque' AND COLUMN_NAME = 'oculto'
      AND COLUMN_TYPE = 'tinyint(1)' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = '0')
    AS columna_oculto,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'web_bloque' AND IS_NULLABLE = 'YES'
      AND COLUMN_NAME IN ('permiso_origen', 'permiso_por', 'permiso_en'))
    AS columnas_permiso,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'web_contenido')
    AS tabla_web_contenido,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sede' AND IS_NULLABLE = 'YES'
      AND COLUMN_TYPE = 'varchar(150)' AND COLUMN_NAME IN ('foto_alt', 'foto2_alt'))
    AS columnas_sede,
  (SELECT COUNT(*) FROM web_bloque)
    AS bloques,
  (SELECT COUNT(*) FROM web_bloque WHERE oculto)
    AS bloques_ocultos,
  (SELECT COUNT(*) FROM web_bloque
    WHERE permiso_origen IS NOT NULL OR permiso_por IS NOT NULL OR permiso_en IS NOT NULL)
    AS bloques_con_permiso,
  (SELECT COUNT(*) FROM web_contenido)
    AS versiones_de_contenido,
  (SELECT COUNT(*) FROM sede)
    AS sedes,
  (SELECT COUNT(*) FROM sede WHERE foto_alt IS NOT NULL OR foto2_alt IS NOT NULL)
    AS sedes_con_descripcion,
  (SELECT COUNT(*) FROM web_bloque WHERE organizacion_id = 1)
    AS moiskimdo_bloques,
  (SELECT COUNT(*) FROM web_contenido WHERE organizacion_id = 1)
    AS moiskimdo_contenido,
  (SELECT COUNT(*) FROM web_escuela WHERE organizacion_id = 1)
    AS moiskimdo_web,
  (SELECT COUNT(*) FROM sede WHERE organizacion_id = 1 AND (foto_alt IS NOT NULL OR foto2_alt IS NOT NULL))
    AS moiskimdo_sedes_con_descripcion;
