-- Modo privado de importes (decisión 419): preferencia por cuenta para que el panel abra con el dinero tapado.
--
-- SOLO AMPLÍA usuario (tabla global, una fila por cuenta, sin organización); no escribe ninguna fila:
--   importes_tapados  BOOLEAN NOT NULL DEFAULT FALSE. TRUE: el panel de esta cuenta abre con los importes tapados. Es de
--                     la cuenta, no de la escuela: una cuenta con dos escuelas tiene una sola preferencia.
-- Sin relleno: todas las cuentas quedan a FALSE, por el valor por defecto, hasta que cada una guarde su perfil. El código
-- anterior sigue funcionando sobre este esquema: no lee la columna e inserta sin ella (queda FALSE). Va como V31_18 (31.18
-- va detrás de 31.17) porque V32 está reservada para la contracción multi-escuela.
--
-- Coste en producción (mysql:8.0): ADD COLUMN con valor por defecto al final de la tabla es INSTANT (solo metadatos), sin
-- reescribir las filas, y usuario es pequeña (una fila por cuenta).
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema (ddl-auto=validate solo mira lo mapeado), así que basta con
-- volver a ella, sin tocar la base de datos. Quitar la columna borraría lo que las cuentas hayan guardado:
-- ALTER TABLE usuario DROP COLUMN importes_tapados.
--
-- Repetible (el DDL de MySQL no es transaccional): la columna lleva su guarda de existencia (information_schema, como
-- V31_14) y solo se añade si falta, así que tras un fallo se completa lo que quedó sin hacer. Tras un fallo, `flyway
-- repair` y volver a desplegar.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-importes-tapados.sql.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'importes_tapados'),
        'SELECT ''usuario.importes_tapados already exists''',
        'ALTER TABLE usuario ADD COLUMN importes_tapados BOOLEAN NOT NULL DEFAULT FALSE'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
