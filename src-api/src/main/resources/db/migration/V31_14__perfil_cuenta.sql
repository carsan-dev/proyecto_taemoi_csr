-- Perfil de la cuenta (decisión 322): nombre por el que quiere que la llamen y cumpleaños sin año.
--
-- SOLO AMPLÍA usuario (tabla global, una fila por cuenta, sin organización); no escribe ninguna fila:
--   nombre_preferido  VARCHAR(40) NULL. 40 caracteres de utf8mb4, también emojis; el charset va explícito para no
--                     depender del de la tabla.
--   cumple_dia        TINYINT NULL. Día del cumpleaños, 1-31.
--   cumple_mes        TINYINT NULL. Mes del cumpleaños, 1-12.
--   ck_usuario_cumple los dos NULL, o los dos con valor y en rango. Que la fecha exista para ese mes (nada de 31 de abril)
--                     lo comprueba PerfilCuentaService.
-- Sin relleno: todas las cuentas quedan a NULL hasta que cada una guarde su perfil. El código anterior sigue funcionando
-- sobre este esquema: no lee las columnas e inserta sin ellas (quedan NULL). Va como V31_14 (31.14 va detrás de 31.13)
-- porque V32 está reservada para la contracción multi-escuela.
--
-- Coste en producción (mysql:8.0): cada ADD COLUMN NULL al final de la tabla es INSTANT (solo metadatos). El ADD
-- CONSTRAINT ... CHECK reconstruye la tabla con ALGORITHM=COPY y comprueba cada fila (todas a NULL), pero usuario es
-- pequeña (una fila por cuenta) y `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema (ddl-auto=validate solo mira lo mapeado), así que basta con
-- volver a ella, sin tocar la base de datos. Quitar las columnas borraría lo que las cuentas hayan guardado:
-- ALTER TABLE usuario DROP CHECK ck_usuario_cumple, DROP COLUMN nombre_preferido, DROP COLUMN cumple_dia,
-- DROP COLUMN cumple_mes.
--
-- Repetible (el DDL de MySQL no es transaccional): cada pieza lleva su guarda de existencia (information_schema, como
-- V31_7 y V29) y solo se añade si falta, así que tras un fallo a medias se completa lo que quedó sin hacer. Tras un
-- fallo, `flyway repair` y volver a desplegar.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-perfil-cuenta.sql.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'nombre_preferido'),
        'SELECT ''usuario.nombre_preferido already exists''',
        'ALTER TABLE usuario ADD COLUMN nombre_preferido VARCHAR(40)
           CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'cumple_dia'),
        'SELECT ''usuario.cumple_dia already exists''',
        'ALTER TABLE usuario ADD COLUMN cumple_dia TINYINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'cumple_mes'),
        'SELECT ''usuario.cumple_mes already exists''',
        'ALTER TABLE usuario ADD COLUMN cumple_mes TINYINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'usuario'
                  AND CONSTRAINT_NAME = 'ck_usuario_cumple' AND CONSTRAINT_TYPE = 'CHECK'),
        'SELECT ''ck_usuario_cumple already exists''',
        'ALTER TABLE usuario ADD CONSTRAINT ck_usuario_cumple
           CHECK ((cumple_dia IS NULL AND cumple_mes IS NULL)
                  OR (cumple_dia IS NOT NULL AND cumple_mes IS NOT NULL
                      AND cumple_dia BETWEEN 1 AND 31 AND cumple_mes BETWEEN 1 AND 12))'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
