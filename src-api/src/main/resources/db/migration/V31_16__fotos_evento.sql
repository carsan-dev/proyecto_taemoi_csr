-- Fotos de eventos (decisión 342, docs/multi-escuela/fase3-e8c-plan.md, pieza F1): un evento pasa a tener una galería
-- además de su cartel. El cartel (evento.foto_evento_id) no cambia.
--
-- SOLO AMPLÍA: una tabla nueva y VACÍA. No toca ninguna tabla existente ni escribe filas. Va como V31_16 (31.16 va detrás
-- de 31.15) porque V32 está reservada para la contracción multi-escuela.
--
--   evento_foto  Una fila por foto: su evento, su imagen (los datos del fichero siguen en `imagen`) y su orden.
--                El orden no es UNIQUE: reordenar escribe fila a fila y pasaría por duplicados. El de varias filas con el
--                mismo orden lo deshace la API por id.
--                FK: a evento, ON DELETE CASCADE, porque la imagen anterior de la API no conoce esta tabla y debe poder
--                seguir borrando eventos con fotos; a imagen, sin cascada: una imagen en uso no se borra por accidente.
--
-- Coste en producción (mysql:8.0): un CREATE TABLE, sin tocar filas; `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema (ddl-auto=validate solo mira lo mapeado), así que basta con
-- volver a ella, sin tocar la base de datos. Si esa imagen borra un evento con fotos, se van sus filas de evento_foto; las
-- de `imagen` y sus ficheros quedan sin referencias en org-<id>/fotos-eventos/, una carpeta privada. Quitar la tabla
-- borraría la galería: DROP TABLE evento_foto.
--
-- Repetible: CREATE TABLE IF NOT EXISTS es atómico en MySQL 8.

CREATE TABLE IF NOT EXISTS evento_foto (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  evento_id BIGINT NOT NULL,
  imagen_id BIGINT NOT NULL,
  orden INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_evento_foto_imagen (imagen_id),
  KEY idx_evento_foto_evento_orden (evento_id, orden),
  KEY idx_evento_foto_organizacion_evento (organizacion_id, evento_id),
  CONSTRAINT fk_evento_foto_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_evento_foto_evento FOREIGN KEY (evento_id) REFERENCES evento(id) ON DELETE CASCADE,
  CONSTRAINT fk_evento_foto_imagen FOREIGN KEY (imagen_id) REFERENCES imagen(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
