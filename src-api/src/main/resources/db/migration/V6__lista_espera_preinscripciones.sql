ALTER TABLE preinscripcion
  DROP INDEX uk_preinscripcion_activa,
  DROP COLUMN clave_preinscripcion_activa;

ALTER TABLE preinscripcion
  ADD COLUMN promocionada_en DATETIME(6) NULL AFTER cancelada_en,
  ADD COLUMN clave_preinscripcion_activa VARCHAR(100)
    GENERATED ALWAYS AS (
      CASE
        WHEN estado IN ('PENDIENTE', 'EN_LISTA_ESPERA', 'FINALIZADA')
        THEN CONCAT(UPPER(dni), '|', deporte, '|', temporada)
        ELSE NULL
      END
    ) STORED,
  ADD UNIQUE KEY uk_preinscripcion_activa (clave_preinscripcion_activa),
  ADD KEY idx_preinscripcion_cola (grupo_id, temporada, estado, creada_en, id);
