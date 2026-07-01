ALTER TABLE preinscripcion
  ADD COLUMN grupo_id BIGINT NULL AFTER turno_snapshot,
  ADD COLUMN grupo_snapshot LONGTEXT NULL AFTER grupo_id,
  ADD KEY idx_preinscripcion_grupo (grupo_id),
  ADD CONSTRAINT fk_pre_grupo FOREIGN KEY (grupo_id) REFERENCES grupo(id);

ALTER TABLE preinscripcion
  MODIFY turno_id BIGINT NULL,
  MODIFY turno_snapshot LONGTEXT NULL;
