-- Día límite de pago de la mensualidad por escuela (decisiones 267 y 375; pieza 394, «Datos de la escuela»). Hasta ahora
-- era la constante 5 de PendienteVencidoService («del día 1 al 5 del mes corriente», normas de Moiskimdo).
--
-- SOLO AMPLÍA: una columna NOT NULL con valor por defecto 5 al final de organizacion y un CHECK de 1 a 28. Todas las
-- escuelas existentes quedan en 5, así que el pendiente vencido de Moiskimdo (organización 1) no cambia. No cambia
-- ninguna otra columna, índice ni fila. El código anterior sigue funcionando sobre este esquema: no conoce la columna
-- (ddl-auto=validate solo mira lo mapeado) y su INSERT nativo del alta de escuela la deja en 5.
--
--   organizacion.dia_limite_mensualidad  Último día del mes para pagar la mensualidad del mes en curso (1 a 28: existe en
--                                        todos los meses). La mensualidad vence el día siguiente.
--
-- Coste en producción (mysql:8.0): el ADD COLUMN ... NOT NULL DEFAULT al final de la tabla es INSTANT (solo metadatos);
-- el ADD CONSTRAINT ... CHECK reconstruye la tabla con ALGORITHM=COPY y comprueba cada fila, pero organizacion tiene
-- una fila por escuela, y `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar lo nuevo borraría el día guardado por cada escuela:
-- ALTER TABLE organizacion DROP CHECK ck_organizacion_dia_limite, DROP COLUMN dia_limite_mensualidad.
--
-- Repetible (el DDL de MySQL no es transaccional): cada pieza lleva su guarda de existencia (information_schema, como
-- V31_14) y solo se añade si falta, así que tras un fallo a medias se completa lo que quedó sin hacer. Tras un fallo,
-- `flyway repair` y volver a desplegar.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-dia-limite-mensualidad.sql.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'organizacion'
                  AND COLUMN_NAME = 'dia_limite_mensualidad'),
        'SELECT ''organizacion.dia_limite_mensualidad already exists''',
        'ALTER TABLE organizacion ADD COLUMN dia_limite_mensualidad TINYINT NOT NULL DEFAULT 5'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'organizacion'
                  AND CONSTRAINT_NAME = 'ck_organizacion_dia_limite' AND CONSTRAINT_TYPE = 'CHECK'),
        'SELECT ''ck_organizacion_dia_limite already exists''',
        'ALTER TABLE organizacion ADD CONSTRAINT ck_organizacion_dia_limite
           CHECK (dia_limite_mensualidad BETWEEN 1 AND 28)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
