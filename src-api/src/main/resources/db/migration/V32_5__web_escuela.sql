-- Web por plantilla, pieza W1 (docs/multi-escuela/fase4-web-plan.md §4.1): tema publicado de cada escuela y bloques
-- editables de su web.
--
-- SOLO AMPLÍA: dos tablas nuevas y VACÍAS. No cambia ninguna columna, índice ni fila existente. La organización 1
-- (Moiskimdo) no tiene fila: su web pública sigue siendo la de siempre y GET /api/publico/web le responde 404. El
-- código anterior sigue funcionando sobre este esquema: no conoce las tablas (ddl-auto=validate solo mira lo mapeado).
--
--   web_escuela   Una fila por escuela con web de plantilla: tema (linea-comarcal, chandal, sede-por-pantalla o
--                 estandar), si está publicada, cuándo y quién la publicó. Sin fila = sin web de plantilla. El tema
--                 elegido y no publicado no se guarda (decisión del §9, n.º 2). FK a organizacion. publicada_por (id
--                 de usuario) sin clave ajena, como en el §4.1: no entra en el borrado de cuentas de la cancelación de
--                 invitaciones.
--   web_bloque    Bloques editables de la web (textos e imágenes por clave: portada.titular, presentacion.texto,
--                 profesor.<n>.nombre, galeria.<n>, ...), por escuela. Clave única por escuela. FK a organizacion y a
--                 imagen.
--
-- Coste en producción (mysql:8.0): dos CREATE TABLE; `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar lo nuevo borraría las webs y bloques guardados: DROP TABLE web_bloque, web_escuela.
--
-- Repetible: CREATE TABLE IF NOT EXISTS es atómico en MySQL 8. Tras un fallo a medias, `flyway repair` y volver a
-- desplegar crea solo lo que falte.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-web-escuela.sql.

CREATE TABLE IF NOT EXISTS web_escuela (
  organizacion_id BIGINT NOT NULL,
  tema VARCHAR(30) NOT NULL,
  publicada BOOLEAN NOT NULL DEFAULT FALSE,
  publicada_en DATETIME NULL,
  publicada_por BIGINT NULL,
  actualizada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (organizacion_id),
  CONSTRAINT fk_web_escuela_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS web_bloque (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  clave VARCHAR(60) NOT NULL,
  texto TEXT NULL,
  imagen_id BIGINT NULL,
  orden INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_web_bloque (organizacion_id, clave),
  KEY idx_web_bloque_imagen (imagen_id),
  CONSTRAINT fk_web_bloque_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_web_bloque_imagen FOREIGN KEY (imagen_id) REFERENCES imagen(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
