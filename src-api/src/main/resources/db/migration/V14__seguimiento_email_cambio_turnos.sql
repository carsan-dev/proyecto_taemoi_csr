ALTER TABLE preinscripcion
  ADD COLUMN email_cambio_turnos_estado VARCHAR(20) NOT NULL DEFAULT 'NO_ENVIADO',
  ADD COLUMN email_cambio_turnos_intentos INT NOT NULL DEFAULT 0,
  ADD COLUMN email_cambio_turnos_enviado_en DATETIME(6) NULL,
  ADD COLUMN email_cambio_turnos_ultimo_error VARCHAR(500) NULL,
  ADD COLUMN turnos_modificados_en DATETIME(6) NULL,
  ADD COLUMN email_cambio_turnos_anterior_snapshot LONGTEXT NULL,
  ADD COLUMN email_cambio_turnos_nuevo_snapshot LONGTEXT NULL;
