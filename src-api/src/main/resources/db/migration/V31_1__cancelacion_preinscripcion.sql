-- Cancelación de preinscripciones con motivo y aviso a la familia.
--
-- SOLO AÑADE columnas NULL sin valor por defecto: el código anterior sigue funcionando sobre este esquema (no
-- las lee ni las escribe) y las filas existentes quedan con NULL (motivo no registrado; aviso nunca enviado,
-- que la entidad lee como NO_ENVIADO y 0 intentos). Va como V31_1 porque V32 está reservada para la
-- contracción multi-escuela (docs/multi-escuela/fase3-sedes-permisos.md); no toca la guarda de V31/V32.
--
-- Seguimiento del correo con las mismas columnas que V12 (finalización) y V14 (cambio de turnos).
ALTER TABLE preinscripcion
  ADD COLUMN motivo_cancelacion VARCHAR(500) NULL,
  ADD COLUMN email_cancelacion_estado VARCHAR(20) NULL,
  ADD COLUMN email_cancelacion_intentos INT NULL,
  ADD COLUMN email_cancelacion_enviado_en DATETIME(6) NULL,
  ADD COLUMN email_cancelacion_ultimo_error VARCHAR(500) NULL;
