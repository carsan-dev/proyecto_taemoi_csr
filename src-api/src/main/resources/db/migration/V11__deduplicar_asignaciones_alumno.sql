DROP TEMPORARY TABLE IF EXISTS alumno_grupo_deduplicado;

CREATE TEMPORARY TABLE alumno_grupo_deduplicado (
  grupo_id BIGINT NOT NULL,
  alumno_id BIGINT NOT NULL,
  PRIMARY KEY (grupo_id, alumno_id)
) ENGINE=InnoDB;

INSERT INTO alumno_grupo_deduplicado (grupo_id, alumno_id)
SELECT DISTINCT ag.grupo_id, ag.alumno_id
FROM alumno_grupo ag
JOIN grupo g ON g.id = ag.grupo_id
JOIN alumno a ON a.id = ag.alumno_id;

DELETE FROM alumno_grupo;

INSERT INTO alumno_grupo (grupo_id, alumno_id)
SELECT grupo_id, alumno_id
FROM alumno_grupo_deduplicado;

DROP TEMPORARY TABLE alumno_grupo_deduplicado;

SET @crear_uk_alumno_grupo = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'alumno_grupo'
      AND index_name = 'uk_alumno_grupo_grupo_alumno'
  ),
  'SELECT 1',
  'ALTER TABLE alumno_grupo ADD CONSTRAINT uk_alumno_grupo_grupo_alumno UNIQUE (grupo_id, alumno_id)'
);
PREPARE crear_uk_alumno_grupo FROM @crear_uk_alumno_grupo;
EXECUTE crear_uk_alumno_grupo;
DEALLOCATE PREPARE crear_uk_alumno_grupo;

DROP TEMPORARY TABLE IF EXISTS alumno_turno_deduplicado;

CREATE TEMPORARY TABLE alumno_turno_deduplicado (
  alumno_id BIGINT NOT NULL,
  turno_id BIGINT NOT NULL,
  PRIMARY KEY (alumno_id, turno_id)
) ENGINE=InnoDB;

INSERT INTO alumno_turno_deduplicado (alumno_id, turno_id)
SELECT DISTINCT at.alumno_id, at.turno_id
FROM alumno_turno at
JOIN alumno a ON a.id = at.alumno_id
JOIN turno t ON t.id = at.turno_id;

DELETE FROM alumno_turno;

INSERT INTO alumno_turno (alumno_id, turno_id)
SELECT alumno_id, turno_id
FROM alumno_turno_deduplicado;

DROP TEMPORARY TABLE alumno_turno_deduplicado;

SET @crear_uk_alumno_turno = IF(
  EXISTS (
    SELECT 1
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'alumno_turno'
      AND index_name = 'uk_alumno_turno_alumno_turno'
  ),
  'SELECT 1',
  'ALTER TABLE alumno_turno ADD CONSTRAINT uk_alumno_turno_alumno_turno UNIQUE (alumno_id, turno_id)'
);
PREPARE crear_uk_alumno_turno FROM @crear_uk_alumno_turno;
EXECUTE crear_uk_alumno_turno;
DEALLOCATE PREPARE crear_uk_alumno_turno;
