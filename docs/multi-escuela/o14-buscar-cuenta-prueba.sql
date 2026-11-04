-- o14: busca la cuenta de prueba «profesor + familia» de producción. SOLO LECTURA.
--
-- Qué hace: lista las cuentas con membresía PROFESOR y FAMILIA (en cualquier estado) en la organización 1
-- y alta el 2026-11-04 o después. Sin nombres ni emails completos: el email va enmascarado (las 3 primeras letras
-- de la parte local, o solo la primera si tiene 3 o menos, más ***@ y el dominio).
--
-- Alta: la tabla usuario no tiene fecha de alta. Se usa la membresía más antigua de la cuenta, en cualquier
-- escuela (membresia.creada_en, que pone la base al insertar: DEFAULT CURRENT_TIMESTAMP, hora del servidor).
-- Una cuenta antigua a la que se añadió un rol después conserva su membresía vieja y no sale.
-- invitada_en: primera auditoría «MIEMBRO_INVITADO» de la cuenta en la org 1 (accion CREATE, módulo miembros,
-- recurso_id = usuario.id). El alta la escribe solo cuando crea la cuenta pendiente: NULL = no nació por invitación
-- y el borrado para.
--
-- Cómo se ejecuta (bash, desde /opt/taemoi):
--   docker exec -i taemoi-mysql-prod sh -c 'mysql -t -h 127.0.0.1 -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE' < docs/multi-escuela/o14-buscar-cuenta-prueba.sql
--
-- Qué debe dar: una sola fila (la cuenta de prueba), con roles PROFESOR y FAMILIA de la org 1 y nada más,
-- invitada_en con fecha, usuario_alumno = '-' (ningún vínculo con fichas: si hay alguno, el borrado para),
-- historico = 0. Su id (N) y su grupos_profesor (M) son los que recibe o14-borrar-cuenta-prueba.sql.
-- historico: filas suyas en las tablas que impiden el borrado (cobros, caja, ventas, solicitudes, asistencias y
-- asignaciones en otra escuela). auditoria_actor: eventos de auditoría hechos por la cuenta; no impiden el
-- borrado y se conservan (auditoria_evento no tiene clave ajena a usuario).
-- fichas_por_email: alumnos activos de la org 1 con el mismo email; la org 1 da acceso de familia por email.
-- Esas fichas son reales y el borrado no las toca.

SELECT
  u.id AS usuario_id,
  CONCAT(LEFT(u.email, IF(CHAR_LENGTH(SUBSTRING_INDEX(u.email, '@', 1)) <= 3, 1, 3)), '***@',
         SUBSTRING_INDEX(u.email, '@', -1)) AS email_enmascarado,
  (SELECT MIN(m.creada_en) FROM membresia m WHERE m.usuario_id = u.id) AS alta,
  (SELECT MIN(e.fecha_evento) FROM auditoria_evento e
    WHERE e.organizacion_id = 1 AND e.modulo = 'miembros' AND e.accion = 'CREATE' AND e.recurso_id = u.id
      AND e.resumen LIKE CONCAT('MIEMBROS MIEMBRO_INVITADO usuario=#', u.id, ' %')) AS invitada_en,
  (SELECT GROUP_CONCAT(CONCAT('org', m.organizacion_id, ':', m.rol, IF(m.activa, ':activa', ':inactiva'))
           ORDER BY m.organizacion_id, m.rol SEPARATOR ' ')
     FROM membresia m WHERE m.usuario_id = u.id) AS roles,
  (SELECT COUNT(*) FROM asignacion_profesor ap WHERE ap.usuario_id = u.id AND ap.organizacion_id = 1) AS grupos_profesor,
  (SELECT COUNT(*) FROM membresia_sede ms JOIN membresia m ON m.id = ms.membresia_id
    WHERE m.usuario_id = u.id) AS sedes_de_membresia,
  (SELECT COALESCE(GROUP_CONCAT(CONCAT(t.relacion, '/', t.origen, '=', t.n) ORDER BY t.relacion, t.origen
           SEPARATOR ' '), '-')
     FROM (SELECT ua.relacion, ua.origen, COUNT(*) AS n FROM usuario_alumno ua
            WHERE ua.usuario_id = u.id GROUP BY ua.relacion, ua.origen) t) AS usuario_alumno,
  (SELECT COUNT(*) FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.relacion = 'PROPIO') AS propio,
  (SELECT COUNT(*) FROM alumno a
    WHERE a.organizacion_id = 1 AND a.activo = TRUE AND TRIM(u.email) <> ''
      AND LOWER(a.email) = LOWER(TRIM(u.email))) AS fichas_por_email,
  IF(u.contrasena IS NULL, 'NO', 'SI') AS con_contrasena,
  COALESCE(u.auth_provider, '-') AS proveedor,
  IF(u.es_admin_plataforma, 'SI', 'NO') AS admin_plataforma,
  (  (SELECT COUNT(*) FROM cobro x WHERE x.cobrado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM cobro x WHERE x.anulado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM entrega_caja x WHERE x.entregado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM entrega_caja x WHERE x.recibido_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM entrega_caja x WHERE x.anulada_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM movimiento_caja x WHERE x.creado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM movimiento_caja x WHERE x.anulado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM producto_alumno x WHERE x.vendido_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM producto_alumno x WHERE x.cancelado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM solicitud x WHERE x.solicitado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM solicitud x WHERE x.resuelto_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM solicitud x WHERE x.encargado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM solicitud x WHERE x.recibido_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM clase_asistencia x WHERE x.registrado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM clase_asistencia x WHERE x.modificado_por_usuario_id = u.id)
   + (SELECT COUNT(*) FROM asignacion_profesor x WHERE x.usuario_id = u.id AND x.organizacion_id <> 1)
  ) AS historico,
  (SELECT COUNT(*) FROM auditoria_evento e WHERE e.usuario_id = u.id) AS auditoria_actor
FROM usuario u
WHERE EXISTS (SELECT 1 FROM membresia m
               WHERE m.usuario_id = u.id AND m.organizacion_id = 1 AND m.rol = 'PROFESOR')
  AND EXISTS (SELECT 1 FROM membresia m
               WHERE m.usuario_id = u.id AND m.organizacion_id = 1 AND m.rol = 'FAMILIA')
  AND (SELECT MIN(m.creada_en) FROM membresia m WHERE m.usuario_id = u.id) >= '2026-11-04'
ORDER BY u.id;

-- Contexto: cuentas con PROFESOR y FAMILIA en la org 1 que NO salen arriba por tener un alta anterior.
SELECT COUNT(*) AS profesor_y_familia_con_alta_anterior
FROM usuario u
WHERE EXISTS (SELECT 1 FROM membresia m
               WHERE m.usuario_id = u.id AND m.organizacion_id = 1 AND m.rol = 'PROFESOR')
  AND EXISTS (SELECT 1 FROM membresia m
               WHERE m.usuario_id = u.id AND m.organizacion_id = 1 AND m.rol = 'FAMILIA')
  AND (SELECT MIN(m.creada_en) FROM membresia m WHERE m.usuario_id = u.id) < '2026-11-04';
