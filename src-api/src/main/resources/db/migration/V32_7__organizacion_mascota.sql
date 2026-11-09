-- Mascota del panel por escuela: MAKI para Moiskimdo (organización 1) y TAMI para el resto. Más adelante será editable
-- por escuela (plan premium); hoy no hay ruta ni pantalla para cambiarla.
--
-- SOLO AMPLÍA: una columna NOT NULL con valor por defecto 'TAMI' al final de organizacion, un CHECK (TAMI o MAKI
-- exactos: con utf8mb4_0900_bin no valen minúsculas ni espacios al final) y la organización 1 en MAKI. No cambia
-- ninguna otra columna, índice ni fila. El código anterior sigue funcionando sobre este esquema: no conoce la columna
-- (ddl-auto=validate solo mira lo mapeado) y su INSERT nativo del alta de escuela la deja en TAMI.
--
--   organizacion.mascota  Mascota del panel de gestión de la escuela (enum Mascota): TAMI o MAKI.
--
-- Coste en producción (mysql:8.0): el ADD COLUMN ... NOT NULL DEFAULT al final de la tabla es INSTANT (solo metadatos);
-- el ADD CONSTRAINT ... CHECK reconstruye la tabla con ALGORITHM=COPY y comprueba cada fila, pero organizacion tiene
-- una fila por escuela; el UPDATE toca una fila. `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar lo nuevo borraría la mascota de cada escuela:
-- ALTER TABLE organizacion DROP CHECK ck_organizacion_mascota, DROP COLUMN mascota.
--
-- Repetible (el DDL de MySQL no es transaccional): cada pieza lleva su guarda de existencia (information_schema, como
-- V32_3) y solo se añade si falta; el UPDATE deja la organización 1 en MAKI las veces que se ejecute. Tras un fallo a
-- medias, `flyway repair` y volver a desplegar completa lo pendiente.
-- Comprobación posterior: docs/multi-escuela/postcheck-32_7.sql.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'organizacion'
                  AND COLUMN_NAME = 'mascota'),
        'SELECT ''organizacion.mascota already exists''',
        'ALTER TABLE organizacion ADD COLUMN mascota VARCHAR(16) NOT NULL DEFAULT ''TAMI'''
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'organizacion'
                  AND CONSTRAINT_NAME = 'ck_organizacion_mascota' AND CONSTRAINT_TYPE = 'CHECK'),
        'SELECT ''ck_organizacion_mascota already exists''',
        'ALTER TABLE organizacion ADD CONSTRAINT ck_organizacion_mascota
           CHECK (mascota COLLATE utf8mb4_0900_bin IN (''TAMI'', ''MAKI''))'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE organizacion SET mascota = 'MAKI' WHERE id = 1;
