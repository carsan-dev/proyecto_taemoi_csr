ALTER TABLE preinscripcion
  ADD COLUMN email_finalizacion_estado VARCHAR(20) NOT NULL DEFAULT 'NO_ENVIADO',
  ADD COLUMN email_finalizacion_enviado_en DATETIME(6) NULL,
  ADD COLUMN email_finalizacion_intentos INT NOT NULL DEFAULT 0,
  ADD COLUMN email_finalizacion_ultimo_error VARCHAR(500) NULL;
