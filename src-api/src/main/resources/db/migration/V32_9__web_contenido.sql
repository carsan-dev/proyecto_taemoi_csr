-- Web por plantilla, pieza W12a (docs/multi-escuela/fase4-w12-plan.md): lo que necesita el editor del contenido de la
-- web y la descripción de las fotos de sede.
--
-- SOLO AMPLÍA: columnas nuevas con valor por defecto o NULL, sin relleno, y una tabla nueva VACÍA. No cambia ninguna
-- columna, índice, restricción ni fila existente. La organización 1 (Moiskimdo) no tiene filas en web_bloque ni en
-- web_contenido y sus sedes quedan con las columnas nuevas a NULL. El código anterior sigue funcionando sobre este
-- esquema: no conoce lo nuevo (ddl-auto=validate solo mira lo mapeado), las columnas admiten NULL o tienen valor por
-- defecto y sus INSERT no las nombran.
--
--   web_bloque.oculto          Bloque oculto en la web (§9 de fase4-web-plan.md): FALSE por defecto. Lo lleva la fila
--                              de cabecera de cada sección movible (presentacion, profesores, galeria, sedes,
--                              texto_foto.<n>).
--   web_bloque.permiso_origen  Constancia del permiso de publicación de cada profesor (d521, d526), en su fila
--   web_bloque.permiso_por     profesor.<n>.nombre: PANEL (lo marcó en el editor el usuario permiso_por) o ALTA
--   web_bloque.permiso_en      («Permiso dado en el alta», sin usuario), y cuándo. Solo la lee el editor: la API
--                              pública no la expone. permiso_por (id de usuario) sin clave ajena, como
--                              web_escuela.publicada_por: no entra en el borrado de cuentas.
--   web_contenido              Versión del contenido de la web de cada escuela (d522): el editor la lee y la devuelve
--                              al guardar; si otra persona guardó antes, 409 sin escribir nada. Sin fila = versión 0
--                              (escuela que aún no ha guardado). Tabla aparte de web_escuela porque el contenido se
--                              guarda también antes de publicar (web_escuela.tema es obligatorio). guardado_por sin
--                              clave ajena, por lo mismo que permiso_por.
--   sede.foto_alt              Descripción de cada una de las dos fotos de la sede (d523, máx. 150). NULL = el texto
--   sede.foto2_alt             por defecto del tema («Foto de la sede de X»); no se guarda ningún valor por defecto.
--
-- Coste en producción (mysql:8.0): web_bloque está vacía o casi (ninguna escuela la usa aún en producción) y sede es
-- pequeña (decenas de filas); las columnas se añaden al final; un CREATE TABLE. `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar lo nuevo borraría lo guardado por el editor: DROP TABLE web_contenido y ALTER TABLE ... DROP COLUMN de
-- cada columna de arriba.
--
-- Repetible: en MySQL el DDL no se deshace, así que cada paso consulta antes information_schema y solo hace lo que
-- falte (CREATE TABLE IF NOT EXISTS es atómico en MySQL 8). Tras un fallo a medias, `flyway repair` y volver a
-- desplegar completa lo pendiente.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v32-9.sql.

SET @schema_name = DATABASE();

-- ============================================================================
-- 1. web_bloque
-- ============================================================================
SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'web_bloque' AND COLUMN_NAME = 'oculto'),
    'SELECT ''web_bloque.oculto already exists''',
    'ALTER TABLE web_bloque ADD COLUMN oculto BOOLEAN NOT NULL DEFAULT FALSE'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'web_bloque' AND COLUMN_NAME = 'permiso_origen'),
    'SELECT ''web_bloque.permiso_origen already exists''',
    'ALTER TABLE web_bloque ADD COLUMN permiso_origen VARCHAR(10) NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'web_bloque' AND COLUMN_NAME = 'permiso_por'),
    'SELECT ''web_bloque.permiso_por already exists''',
    'ALTER TABLE web_bloque ADD COLUMN permiso_por BIGINT NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'web_bloque' AND COLUMN_NAME = 'permiso_en'),
    'SELECT ''web_bloque.permiso_en already exists''',
    'ALTER TABLE web_bloque ADD COLUMN permiso_en DATETIME NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. web_contenido
-- ============================================================================
CREATE TABLE IF NOT EXISTS web_contenido (
  organizacion_id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  guardado_en DATETIME NULL,
  guardado_por BIGINT NULL,
  PRIMARY KEY (organizacion_id),
  CONSTRAINT fk_web_contenido_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- 3. sede
-- ============================================================================
SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND COLUMN_NAME = 'foto_alt'),
    'SELECT ''sede.foto_alt already exists''',
    'ALTER TABLE sede ADD COLUMN foto_alt VARCHAR(150) NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sede' AND COLUMN_NAME = 'foto2_alt'),
    'SELECT ''sede.foto2_alt already exists''',
    'ALTER TABLE sede ADD COLUMN foto2_alt VARCHAR(150) NULL'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
