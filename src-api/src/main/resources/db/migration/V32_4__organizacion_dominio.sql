-- Escuela por dominio, pieza 1 (docs/multi-escuela/fase4-dominio-plan.md §3.1 y §4.1): hosts de cada escuela.
--
-- SOLO AMPLÍA: una tabla nueva, global (sin @TenantId: la lee el filtro antes de saber la escuela), con una sola fila.
-- No cambia ninguna columna, índice ni fila existente. El código anterior sigue funcionando sobre este esquema: no
-- conoce la tabla (ddl-auto=validate solo mira lo mapeado).
--
--   organizacion_dominio   Un host (minúsculas, sin puerto ni punto final) → su escuela. Una escuela puede tener varios
--                          (subdominio de la plataforma y dominio propio); el principal da los enlaces de correo (una
--                          pieza posterior). Un principal por escuela: lo valida la aplicación (MySQL 8 no tiene índices
--                          únicos parciales). Host único sin distinguir mayúsculas (collation ai_ci).
--   Semilla                moiskimdo.es → organización 1, principal. Es la escuela por defecto: Moiskimdo no nota nada.
--                          www.moiskimdo.es no hace falta (nginx lo redirige antes). Solo si existe la organización 1.
--
-- Coste en producción (mysql:8.0): un CREATE TABLE y un INSERT de una fila; `desplegar_migrar` para antes la app.
--
-- Vuelta atrás: la imagen anterior arranca sobre este esquema, así que basta con volver a ella, sin tocar la base de
-- datos. Quitar la tabla borraría los dominios dados de alta: DROP TABLE organizacion_dominio.
--
-- Repetible: CREATE TABLE IF NOT EXISTS es atómico en MySQL 8 y la semilla solo se inserta si falta el host. Tras un
-- fallo a medias, `flyway repair` y volver a desplegar crea solo lo que falte.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-organizacion-dominio.sql.

CREATE TABLE IF NOT EXISTS organizacion_dominio (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  host VARCHAR(253) NOT NULL,
  principal BOOLEAN NOT NULL DEFAULT FALSE,
  creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_organizacion_dominio_host (host),
  KEY idx_organizacion_dominio_organizacion (organizacion_id),
  CONSTRAINT fk_organizacion_dominio_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO organizacion_dominio (organizacion_id, host, principal)
SELECT 1, 'moiskimdo.es', TRUE
FROM DUAL
WHERE EXISTS (SELECT 1 FROM organizacion WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM organizacion_dominio WHERE host = 'moiskimdo.es');
