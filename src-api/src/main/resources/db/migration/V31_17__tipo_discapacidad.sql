-- Tipo de discapacidad en los datos de salud del alumno. `alumno.tiene_discapacidad` sigue siendo un booleano en claro
-- (decide el precio de la licencia); el detalle es dato de salud y va aparte, cifrado.
--
-- SOLO AMPLÍA alumno_salud; no escribe ninguna fila:
--   discapacidad_tipos  TEXT NULL. Lista de tipos cifrada con AES-GCM por AlumnoSaludService, ligada a su fila y columna
--                       (alumno_salud.discapacidad_tipos#<alumno_id>). NULL = sin tipos.
--   discapacidad_otra   TEXT NULL. Texto de «Otra», cifrado igual (alumno_salud.discapacidad_otra#<alumno_id>).
-- Sin relleno: todas las filas quedan a NULL hasta que se guarde el tipo. El código anterior sigue funcionando sobre este
-- esquema: no lee las columnas e inserta sin ellas (quedan NULL). Va como V31_17 (31.17 va detrás de 31.16) porque V32
-- está reservada para la contracción multi-escuela.
--
-- Coste en producción (mysql:8.0): cada ADD COLUMN NULL al final de la tabla es INSTANT (solo metadatos), también con TEXT.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema (ddl-auto=validate solo mira lo mapeado), así que basta con
-- volver a ella, sin tocar la base de datos; pero esa imagen no limpia estas columnas al poner tiene_discapacidad a falso:
-- si se vuelve después de guardar tipos, repasa las filas con tipos y sin discapacidad (último recuento del postcheck).
-- Quitar las columnas borraría lo guardado: ALTER TABLE alumno_salud DROP COLUMN discapacidad_tipos,
-- DROP COLUMN discapacidad_otra.
--
-- Repetible (el DDL de MySQL no es transaccional): cada columna lleva su guarda de existencia (information_schema, como
-- V31_14) y solo se añade si falta, así que tras un fallo a medias se completa lo que quedó sin hacer. Tras un fallo,
-- `flyway repair` y volver a desplegar.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v31-17.sql.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_salud' AND COLUMN_NAME = 'discapacidad_tipos'),
        'SELECT ''alumno_salud.discapacidad_tipos already exists''',
        'ALTER TABLE alumno_salud ADD COLUMN discapacidad_tipos TEXT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_salud' AND COLUMN_NAME = 'discapacidad_otra'),
        'SELECT ''alumno_salud.discapacidad_otra already exists''',
        'ALTER TABLE alumno_salud ADD COLUMN discapacidad_otra TEXT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
