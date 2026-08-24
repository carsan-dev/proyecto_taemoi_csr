CREATE TEMPORARY TABLE alumno_grupo_deduplicado (
  grupo_id BIGINT NOT NULL,
  alumno_id BIGINT NOT NULL,
  PRIMARY KEY (grupo_id, alumno_id)
) ENGINE=InnoDB;

INSERT INTO alumno_grupo_deduplicado (grupo_id, alumno_id)
SELECT DISTINCT grupo_id, alumno_id
FROM alumno_grupo;

DELETE FROM alumno_grupo;

INSERT INTO alumno_grupo (grupo_id, alumno_id)
SELECT grupo_id, alumno_id
FROM alumno_grupo_deduplicado;

DROP TEMPORARY TABLE alumno_grupo_deduplicado;

ALTER TABLE alumno_grupo
  ADD CONSTRAINT uk_alumno_grupo_grupo_alumno UNIQUE (grupo_id, alumno_id);

CREATE TEMPORARY TABLE alumno_turno_deduplicado (
  alumno_id BIGINT NOT NULL,
  turno_id BIGINT NOT NULL,
  PRIMARY KEY (alumno_id, turno_id)
) ENGINE=InnoDB;

INSERT INTO alumno_turno_deduplicado (alumno_id, turno_id)
SELECT DISTINCT alumno_id, turno_id
FROM alumno_turno;

DELETE FROM alumno_turno;

INSERT INTO alumno_turno (alumno_id, turno_id)
SELECT alumno_id, turno_id
FROM alumno_turno_deduplicado;

DROP TEMPORARY TABLE alumno_turno_deduplicado;

ALTER TABLE alumno_turno
  ADD CONSTRAINT uk_alumno_turno_alumno_turno UNIQUE (alumno_id, turno_id);
