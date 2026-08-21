CREATE TABLE preinscripcion_turno (
  preinscripcion_id BIGINT NOT NULL,
  turno_id BIGINT NOT NULL,
  PRIMARY KEY (preinscripcion_id, turno_id),
  KEY idx_preinscripcion_turno_turno (turno_id),
  CONSTRAINT fk_preinscripcion_turno_preinscripcion
    FOREIGN KEY (preinscripcion_id) REFERENCES preinscripcion(id) ON DELETE CASCADE,
  CONSTRAINT fk_preinscripcion_turno_turno
    FOREIGN KEY (turno_id) REFERENCES turno(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO preinscripcion_turno (preinscripcion_id, turno_id)
SELECT p.id, t.id
FROM preinscripcion p
JOIN turno t ON t.grupo_id = p.grupo_id
WHERE p.grupo_id IS NOT NULL;

INSERT IGNORE INTO preinscripcion_turno (preinscripcion_id, turno_id)
SELECT p.id, p.turno_id
FROM preinscripcion p
WHERE p.turno_id IS NOT NULL;
