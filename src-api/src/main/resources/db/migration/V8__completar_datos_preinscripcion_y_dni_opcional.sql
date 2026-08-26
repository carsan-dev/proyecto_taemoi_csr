ALTER TABLE preinscripcion
  DROP INDEX uk_preinscripcion_activa,
  DROP COLUMN clave_preinscripcion_activa,
  MODIFY dni VARCHAR(16) NULL,
  ADD COLUMN telefono2 VARCHAR(20) NULL AFTER telefono,
  ADD COLUMN tiene_discapacidad BIT NULL AFTER email,
  ADD COLUMN identidad_hash CHAR(64) NULL AFTER dni;

UPDATE preinscripcion
SET identidad_hash = LOWER(SHA2(CONCAT('DNI|', UPPER(TRIM(dni))), 256));

ALTER TABLE preinscripcion
  MODIFY identidad_hash CHAR(64) NOT NULL,
  ADD COLUMN clave_preinscripcion_activa VARCHAR(140)
    GENERATED ALWAYS AS (
      CASE
        WHEN estado IN ('PENDIENTE', 'EN_LISTA_ESPERA', 'FINALIZADA')
        THEN CONCAT(identidad_hash, '|', deporte, '|', temporada)
        ELSE NULL
      END
    ) STORED,
  ADD UNIQUE KEY uk_preinscripcion_activa (clave_preinscripcion_activa);

UPDATE preinscripcion p
JOIN turno t ON t.id = p.turno_id
SET p.grupo_id = t.grupo_id
WHERE p.grupo_id IS NULL AND t.grupo_id IS NOT NULL;

ALTER TABLE alumno
  ADD COLUMN responsable_legal_nombre VARCHAR(180) NULL,
  ADD COLUMN responsable_legal_nif VARCHAR(16) NULL;
