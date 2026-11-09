-- Rallas hacia el examen (decisiones 510, 511 y 513; boceto docs/multi-escuela/boceto-rallas-cinturon.html): un
-- contador de MEDIAS rallas por alumno y disciplina (0 a 10: dos medias son una ralla y 10 son las 5 rallas del examen)
-- y un interruptor por disciplina de cada escuela, apagado en todas.
--
-- SOLO AMPLÍA: dos columnas NOT NULL con valor por defecto al final de sus tablas y un CHECK de 0 a 10. Ningún alumno
-- tiene rallas (todos en 0) y ninguna disciplina las tiene activas. No cambia ninguna otra columna, índice ni fila. El
-- código anterior sigue funcionando sobre este esquema: no conoce las columnas (ddl-auto=validate solo mira lo mapeado)
-- y sus INSERT de alumno_deporte y disciplina las dejan en 0 y en FALSE.
--
--   alumno_deporte.medias_rallas  Medias rallas hacia el examen en esa disciplina (0 a 10). Solo se ven y se cambian si
--                                 la disciplina las tiene activas, el alumno tiene grado y su grado no es PUM ni DAN.
--                                 Desactivar la disciplina no las borra: vuelven al reactivarla.
--   disciplina.rallas_activas     La escuela usa las rallas en esa disciplina (Configuración). FALSE por defecto.
--
-- Coste en producción (mysql:8.0): los dos ADD COLUMN ... NOT NULL DEFAULT al final de la tabla son INSTANT (solo
-- metadatos); el ADD CONSTRAINT ... CHECK reconstruye alumno_deporte con ALGORITHM=COPY y comprueba cada fila (una por
-- alumno y disciplina: pocos miles). `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar lo nuevo borraría las rallas de cada alumno y el interruptor de cada disciplina:
-- ALTER TABLE alumno_deporte DROP CHECK ck_alumno_deporte_medias_rallas, DROP COLUMN medias_rallas;
-- ALTER TABLE disciplina DROP COLUMN rallas_activas.
--
-- Repetible (el DDL de MySQL no es transaccional): cada pieza lleva su guarda de existencia (information_schema, como
-- V32_7) y solo se añade si falta, así que tras un fallo a medias se completa lo que quedó sin hacer. Tras un fallo,
-- `flyway repair` y volver a desplegar.
-- Comprobación posterior: docs/multi-escuela/postcheck-rallas.sql.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte'
                  AND COLUMN_NAME = 'medias_rallas'),
        'SELECT ''alumno_deporte.medias_rallas already exists''',
        'ALTER TABLE alumno_deporte ADD COLUMN medias_rallas TINYINT NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'alumno_deporte'
                  AND CONSTRAINT_NAME = 'ck_alumno_deporte_medias_rallas' AND CONSTRAINT_TYPE = 'CHECK'),
        'SELECT ''ck_alumno_deporte_medias_rallas already exists''',
        'ALTER TABLE alumno_deporte ADD CONSTRAINT ck_alumno_deporte_medias_rallas
           CHECK (medias_rallas BETWEEN 0 AND 10)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'disciplina'
                  AND COLUMN_NAME = 'rallas_activas'),
        'SELECT ''disciplina.rallas_activas already exists''',
        'ALTER TABLE disciplina ADD COLUMN rallas_activas BOOLEAN NOT NULL DEFAULT FALSE'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
