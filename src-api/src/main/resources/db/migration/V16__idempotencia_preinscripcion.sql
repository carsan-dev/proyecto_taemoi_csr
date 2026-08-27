ALTER TABLE preinscripcion
  ADD COLUMN idempotency_key_hash VARCHAR(64) NULL AFTER token_documento_hash,
  ADD UNIQUE KEY uk_preinscripcion_idempotencia (idempotency_key_hash);
