-- Comprobación posterior de V31_16 (fotos de eventos, decisión 342). Solo lectura y SOLO RECUENTOS: no devuelve rutas,
-- nombres de fichero ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_31_16                 1   V31_16 aplicada con éxito
--   ultima_es_31_16              1   es la última versión aplicada
--   tabla_evento_foto            1   evento_foto existe
--   columnas_evento_foto         6   id, organizacion_id, evento_id, imagen_id, miniatura_id y orden
--   miniatura_obligatoria        1   miniatura_id es NOT NULL
--   unicas                       2   UNIQUE de imagen_id y de miniatura_id
--   claves_ajenas                4   organización, imagen y miniatura sin cascada; evento con ON DELETE CASCADE
--   filas_evento_foto            0   V31_16 no rellena nada. Crece cuando se suben fotos.
--   fotos_sin_miniatura          0   fotos sin fila de miniatura en `imagen`. Debe seguir en 0 siempre.
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.16' AND success = 1)
    AS flyway_31_16,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '31.16')
    AS ultima_es_31_16,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'evento_foto')
    AS tabla_evento_foto,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'evento_foto'
      AND COLUMN_NAME IN ('id', 'organizacion_id', 'evento_id', 'imagen_id', 'miniatura_id', 'orden'))
    AS columnas_evento_foto,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'evento_foto' AND COLUMN_NAME = 'miniatura_id'
      AND IS_NULLABLE = 'NO')
    AS miniatura_obligatoria,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'evento_foto' AND CONSTRAINT_TYPE = 'UNIQUE'
      AND CONSTRAINT_NAME IN ('uk_evento_foto_imagen', 'uk_evento_foto_miniatura'))
    AS unicas,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'evento_foto' AND (
      (DELETE_RULE = 'CASCADE' AND REFERENCED_TABLE_NAME = 'evento')
      OR (DELETE_RULE IN ('NO ACTION', 'RESTRICT') AND REFERENCED_TABLE_NAME IN ('organizacion', 'imagen'))))
    AS claves_ajenas,
  (SELECT COUNT(*) FROM evento_foto)
    AS filas_evento_foto,
  (SELECT COUNT(*) FROM evento_foto f LEFT JOIN imagen i ON i.id = f.miniatura_id WHERE i.id IS NULL)
    AS fotos_sin_miniatura;
