-- Interruptor del diseño nuevo del panel por escuela y usuario (pieza 8b-I, docs/multi-escuela/fase3-e8b-plan.md §3 y §6).
--
-- SOLO AMPLÍA: una tabla nueva y VACÍA. No escribe filas ni toca ninguna otra tabla. El código anterior sigue
-- funcionando sobre este esquema: no conoce la tabla. Va como V31_10 (Flyway compara por partes numéricas: 31.10 va
-- detrás de 31.9) porque V32 está reservada para la contracción multi-escuela: una V32_x aplicada antes dejaría V32
-- «por detrás» y Flyway la rechazaría cuando llegue.
--
--   diseno_panel_usuario  una fila por (escuela, usuario) cuando el propietario da o quita el permiso, o cuando el
--                         usuario lo enciende o apaga para sí. Regla efectiva (DisenoPanelService#activoPara): con fila
--                         y permitido, su activo; si no, el valor por defecto de la escuela (clave
--                         panel.diseno_por_defecto de configuracion_organizacion, que ya existe; sin la clave, VIEJO).
--                         Sin filas, nadie cambia: Moiskimdo sigue con el panel de siempre.
--                         Ni usuario (es global: cruzaría escuelas) ni membresia (una fila por rol: se duplicaría)
--                         sirven para guardarlo. Clave única (organizacion_id, usuario_id): la misma cuenta en dos
--                         escuelas tiene una fila en cada una. FK: a usuario ON DELETE CASCADE (como membresia: borrar
--                         una cuenta se lleva su preferencia), a quien lo cambió ON DELETE SET NULL, a organizacion sin
--                         acción.
--
-- Coste en producción (mysql:8.0): CREATE TABLE de una tabla vacía, sin bloqueos sobre las demás.
--
-- Repetible: CREATE TABLE IF NOT EXISTS es atómico en MySQL 8. Tras un fallo, `flyway repair` y volver a desplegar.
-- Comprobación posterior (solo lectura): Flyway en 31.10 y SELECT COUNT(*) FROM diseno_panel_usuario = 0.

CREATE TABLE IF NOT EXISTS diseno_panel_usuario (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  usuario_id BIGINT NOT NULL,
  permitido BOOLEAN NOT NULL,
  activo BOOLEAN NOT NULL,
  cambiado_por_usuario_id BIGINT NULL,
  cambiado_en DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_diseno_panel_usuario_organizacion_usuario (organizacion_id, usuario_id),
  KEY idx_diseno_panel_usuario_usuario (usuario_id),
  KEY idx_diseno_panel_usuario_cambiado_por (cambiado_por_usuario_id),
  CONSTRAINT fk_diseno_panel_usuario_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_diseno_panel_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
  CONSTRAINT fk_diseno_panel_usuario_cambiado_por FOREIGN KEY (cambiado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
