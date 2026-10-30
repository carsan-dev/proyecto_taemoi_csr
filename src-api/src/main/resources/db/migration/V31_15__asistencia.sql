-- Pasar lista (decisión 324, docs/multi-escuela/fase3-pasar-lista.md): la asistencia deja de ser solo el PDF por grupo y
-- mes. El PDF se queda como respaldo y no cambia.
--
-- SOLO AMPLÍA: dos tablas nuevas y VACÍAS. No toca ninguna tabla existente ni escribe filas. El código anterior sigue
-- funcionando sobre este esquema: no conoce las tablas. Va como V31_15 (31.15 va detrás de 31.14) porque V32 está
-- reservada para la contracción multi-escuela.
--
--   clase_asistencia   Una clase con lista guardada: un turno y una fecha, como cada columna del papel. Sin fila, la clase
--                      está «sin lista» y no cuenta. hora_inicio y hora_fin son una copia del horario al guardar.
--                      FK: a grupo, ON DELETE CASCADE (borrar el grupo borra sus listas); a turno, ON DELETE SET NULL
--                      (borrar o rehacer un turno no pierde el histórico: varias filas con turno_id NULL no chocan en la
--                      UNIQUE); a usuario, ON DELETE SET NULL (borrar una cuenta no falla, como solicitud en V31_8).
--   asistencia_alumno  Una fila por CADA alumno de la lista guardada, presente o no.
--                      FK: a la clase y al alumno, ON DELETE CASCADE (borrar un alumno se lleva sus filas).
--
-- Coste en producción (mysql:8.0): dos CREATE TABLE, sin tocar filas; `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema (ddl-auto=validate solo mira lo mapeado), así que basta con
-- volver a ella, sin tocar la base de datos. Quitar las tablas borraría las listas guardadas:
-- DROP TABLE asistencia_alumno, clase_asistencia.
--
-- Repetible: CREATE TABLE IF NOT EXISTS es atómico en MySQL 8. Tras un fallo a medias, `flyway repair` y volver a
-- desplegar crea solo lo que falte.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-pasar-lista.sql.

CREATE TABLE IF NOT EXISTS clase_asistencia (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  grupo_id BIGINT NOT NULL,
  turno_id BIGINT NULL,
  fecha DATE NOT NULL,
  hora_inicio VARCHAR(5) NOT NULL,
  hora_fin VARCHAR(5) NOT NULL,
  registrado_por_usuario_id BIGINT NULL,
  registrado_en DATETIME(6) NOT NULL,
  modificado_por_usuario_id BIGINT NULL,
  modificado_en DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_clase_asistencia_turno_fecha (turno_id, fecha),
  KEY idx_clase_asistencia_organizacion_grupo_fecha (organizacion_id, grupo_id, fecha),
  KEY idx_clase_asistencia_grupo (grupo_id),
  KEY idx_clase_asistencia_registrado_por (registrado_por_usuario_id),
  KEY idx_clase_asistencia_modificado_por (modificado_por_usuario_id),
  CONSTRAINT fk_clase_asistencia_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_clase_asistencia_grupo FOREIGN KEY (grupo_id) REFERENCES grupo(id) ON DELETE CASCADE,
  CONSTRAINT fk_clase_asistencia_turno FOREIGN KEY (turno_id) REFERENCES turno(id) ON DELETE SET NULL,
  CONSTRAINT fk_clase_asistencia_registrado_por FOREIGN KEY (registrado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_clase_asistencia_modificado_por FOREIGN KEY (modificado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS asistencia_alumno (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  clase_id BIGINT NOT NULL,
  alumno_id BIGINT NOT NULL,
  presente BIT(1) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_asistencia_alumno_clase_alumno (clase_id, alumno_id),
  KEY idx_asistencia_alumno_organizacion_alumno (organizacion_id, alumno_id),
  KEY idx_asistencia_alumno_alumno (alumno_id),
  CONSTRAINT fk_asistencia_alumno_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_asistencia_alumno_clase FOREIGN KEY (clase_id) REFERENCES clase_asistencia(id) ON DELETE CASCADE,
  CONSTRAINT fk_asistencia_alumno_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
