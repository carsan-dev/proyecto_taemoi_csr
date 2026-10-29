-- Retirada del interruptor del diseño nuevo del panel (pieza 8b-I, V31_10). Desde la pieza 8b-Z el diseño anterior ya
-- no existe y el interruptor no hace nada; este despliegue quita su código (DisenoPanelService, su controlador, su
-- entidad y el campo disenoNuevo de /api/auth/permisos) y aquí se borran sus datos.
--
-- CONTRACCIÓN en versión decimal, por decisión del propietario (29/10): código y tabla se retiran JUNTOS, en el mismo
-- despliegue, en lugar de ampliar, cambiar el código y contraer por separado. Va como V31_13 (31.13 va detrás de
-- 31.12); V32 sigue reservada para la contracción de permisos.
--
--   diseno_panel_usuario       se borra la tabla entera, con sus filas (preferencias por escuela y usuario) y sus FK.
--                              Ninguna otra tabla la referencia.
--   configuracion_organizacion se borra la clave panel.diseno_por_defecto en TODAS las escuelas. Las demás claves no
--                              se tocan.
--
-- Vuelta atrás: restaurar la copia de seguridad que hace `desplegar_migrar` antes de migrar. Volver solo a la imagen
-- anterior NO sirve: arranca con spring.jpa.hibernate.ddl-auto=validate y su entidad DisenoPanelUsuario exige la
-- tabla, que ya no existe.
--
-- Coste en producción (mysql:8.0): DROP TABLE de una tabla pequeña y un DELETE por clave; con la app parada.
--
-- Repetible: DROP TABLE IF EXISTS y un DELETE que, repetido, borra 0 filas. Tras un fallo, `flyway repair` y volver a
-- desplegar.
-- Comprobación posterior (solo lectura): Flyway en 31.13; la tabla no existe
-- (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
--  AND TABLE_NAME = 'diseno_panel_usuario' = 0) y
-- SELECT COUNT(*) FROM configuracion_organizacion WHERE clave = 'panel.diseno_por_defecto' = 0.

DROP TABLE IF EXISTS diseno_panel_usuario;

DELETE FROM configuracion_organizacion WHERE clave = 'panel.diseno_por_defecto';
