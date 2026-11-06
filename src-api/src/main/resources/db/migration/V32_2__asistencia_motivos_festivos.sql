-- Pasar lista: motivo de la falta y festivos por sede (decisiones 455-463, docs/multi-escuela/fase3-pasar-lista.md,
-- sección «Motivos y festivos»).
--
-- SOLO AMPLÍA: una columna NULL en asistencia_alumno y dos tablas nuevas y VACÍAS. No cambia ninguna columna, índice ni
-- fila existente. El código anterior sigue funcionando sobre este esquema: no conoce la columna ni las tablas
-- (ddl-auto=validate solo mira lo mapeado).
--
--   asistencia_alumno.motivo_falta  Motivo opcional de una falta: INJUSTIFICADA, MEDICA, FAMILIAR u OTRA_JUSTIFICADA.
--                                   NULL = falta sin motivo (como hasta ahora) o presente. Las filas existentes quedan
--                                   con NULL.
--   festivo                         Días sin clase de la escuela. tipo DIA (fecha_desde = fecha_hasta), RANGO (de una
--                                   fecha a otra, ambas incluidas) o ANUAL (mes y dia, cada año; fechas NULL). Nombre
--                                   opcional. FK: a organizacion; a usuario, ON DELETE SET NULL (borrar una cuenta no
--                                   falla, como clase_asistencia en V31_15).
--   festivo_sede                    Sedes de cada festivo. FK a festivo y a sede, ON DELETE CASCADE.
--
-- Coste en producción (mysql:8.0): un ALTER TABLE ... ADD COLUMN NULL (INSTANT en MySQL 8: no reescribe la tabla) y dos
-- CREATE TABLE; `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar lo nuevo borraría los motivos y los festivos guardados:
-- ALTER TABLE asistencia_alumno DROP COLUMN motivo_falta; DROP TABLE festivo_sede, festivo.
--
-- Repetible: la columna se añade solo si no existe (se consulta information_schema) y CREATE TABLE IF NOT EXISTS es
-- atómico en MySQL 8. Tras un fallo a medias, `flyway repair` y volver a desplegar crea solo lo que falte.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-asistencia-motivos-festivos.sql.

SET @schema_name = DATABASE();

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'asistencia_alumno'
                        AND COLUMN_NAME = 'motivo_falta'),
    'SELECT ''v32_2: asistencia_alumno.motivo_falta ya existe''',
    'ALTER TABLE asistencia_alumno ADD COLUMN motivo_falta VARCHAR(20) NULL');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS festivo (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  tipo VARCHAR(10) NOT NULL,
  fecha_desde DATE NULL,
  fecha_hasta DATE NULL,
  mes TINYINT NULL,
  dia TINYINT NULL,
  nombre VARCHAR(60) NULL,
  creado_por_usuario_id BIGINT NULL,
  creado_en DATETIME NOT NULL,
  modificado_por_usuario_id BIGINT NULL,
  modificado_en DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_festivo_organizacion_fecha_hasta (organizacion_id, fecha_hasta),
  KEY idx_festivo_creado_por (creado_por_usuario_id),
  KEY idx_festivo_modificado_por (modificado_por_usuario_id),
  CONSTRAINT fk_festivo_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_festivo_creado_por FOREIGN KEY (creado_por_usuario_id) REFERENCES usuario(id) ON DELETE SET NULL,
  CONSTRAINT fk_festivo_modificado_por FOREIGN KEY (modificado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS festivo_sede (
  festivo_id BIGINT NOT NULL,
  sede_id BIGINT NOT NULL,
  organizacion_id BIGINT NOT NULL,
  PRIMARY KEY (festivo_id, sede_id),
  KEY idx_festivo_sede_sede (sede_id),
  KEY idx_festivo_sede_organizacion (organizacion_id),
  CONSTRAINT fk_festivo_sede_festivo FOREIGN KEY (festivo_id) REFERENCES festivo(id) ON DELETE CASCADE,
  CONSTRAINT fk_festivo_sede_sede FOREIGN KEY (sede_id) REFERENCES sede(id) ON DELETE CASCADE,
  CONSTRAINT fk_festivo_sede_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
