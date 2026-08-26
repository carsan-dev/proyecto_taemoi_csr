ALTER TABLE preinscripcion
  DROP INDEX uk_preinscripcion_dni_deporte_temporada,
  ADD COLUMN clave_preinscripcion_activa VARCHAR(100)
    GENERATED ALWAYS AS (
      CASE
        WHEN estado IN ('PENDIENTE', 'FINALIZADA')
        THEN CONCAT(UPPER(dni), '|', deporte, '|', temporada)
        ELSE NULL
      END
    ) STORED,
  ADD UNIQUE KEY uk_preinscripcion_activa (clave_preinscripcion_activa),
  ADD KEY idx_preinscripcion_dni_deporte_temporada (dni, deporte, temporada);
