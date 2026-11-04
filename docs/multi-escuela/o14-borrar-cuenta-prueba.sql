-- o14: borra la cuenta de prueba «profesor + familia» de producción (creada el 04/11 por invitación).
--
-- Qué hace: con el id que da o14-buscar-cuenta-prueba.sql, y dentro de una transacción, borra la cuenta y sus
-- filas propias, en este orden: sus sedes de membresía (membresia_sede), sus asignaciones de profesor a grupos
-- (asignacion_profesor), sus membresías (membresia) y la fila de usuario (que lleva también su token de invitación
-- o restablecimiento). Nunca borra alumnos, grupos, turnos ni sedes, ni vínculos con fichas (usuario_alumno: si la
-- cuenta tiene alguno, para), ni toca auditoria_evento (no tiene clave ajena a usuario: se conserva como
-- histórico). No desactiva las claves ajenas.
--
-- Guardas: antes de borrar nada, el script se DETIENE con un error «Table '...o14_parada_<motivo>' doesn't exist»
-- (el cliente mysql en batch para en el primer error y la transacción, sin COMMIT, se deshace al cerrar) si:
--   usuario_id_nulo                    falta la línea SET @usuario_id
--   el_usuario_no_existe               no hay usuario con ese id (también al repetirlo tras borrarlo)
--   no_tiene_profesor_y_familia_org_1  no tiene a la vez membresía PROFESOR y FAMILIA en la org 1
--   tiene_otro_rol_o_es_admin          alguna membresía con otro rol, o marca de admin de plataforma
--   tiene_membresia_en_otra_escuela    alguna membresía fuera de la org 1
--   es_la_cuenta_de_una_ficha_propio   alguna fila PROPIO en usuario_alumno
--   tiene_vinculos_con_fichas          CUALQUIER fila en usuario_alumno (TUTOR, REGISTRO, MIGRACION, MANUAL): la
--                                      invitación y el marcado de familia nunca la escriben, así que es de una
--                                      familia real (la app tampoco borra una cuenta con vínculos al cancelar)
--   tiene_historico                    filas suyas en cobros, caja, ventas, solicitudes, asistencias, o
--                                      asignaciones en otra escuela (sus claves son ON DELETE SET NULL: borrar
--                                      la cuenta perdería el autor)
--   alta_anterior_al_2026_11_04        su membresía más antigua es anterior al 2026-11-04 (usuario no tiene
--                                      fecha de alta; se usa membresia.creada_en)
--   no_nacio_por_invitacion            no hay auditoria_evento MIEMBRO_INVITADO de la cuenta en la org 1 (la
--                                      escribe el alta solo cuando crea la cuenta pendiente; invitar a una cuenta
--                                      que ya existía no lo escribe)
--   asignaciones_distintas_de_las_esperadas
--                                      solo si se fija @esperado_asignaciones: el número de grupos como profesor
--                                      de la cuenta no es ese
-- Después de borrar, si los recuentos no cuadran (la cuenta no queda a 0, se borró algo de más, o cambian
-- vínculos con fichas, alumnos, grupos, turnos o sedes) se detiene con o14_parada_recuentos_no_cuadran y la
-- transacción se deshace.
--
-- Cómo se ejecuta (bash, desde /opt/taemoi; N es el usuario_id y M el grupos_profesor del buscador):
--   { echo "SET @usuario_id := N; SET @esperado_asignaciones := M;"; cat docs/multi-escuela/o14-borrar-cuenta-prueba.sql; } | docker exec -i taemoi-mysql-prod sh -c 'mysql -t -h 127.0.0.1 -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE'
-- @esperado_asignaciones es opcional (sin él no se comprueba), pero se recomienda: para si la cuenta no tiene
-- exactamente los M grupos que enseñó el buscador.
-- Los literales no llevan tildes: no hace falta --default-character-set.
--
-- Qué debe dar: una fila «guardas superadas», la fila «antes» (con membresias_cuenta = 2, asignaciones_cuenta = M
-- y usuario_alumno_cuenta = 0 para la cuenta de prueba), la fila «borradas» (filas borradas por tabla,
-- usuario = 1), la fila «despues» con todas las columnas *_cuenta a 0, los totales bajados exactamente en lo
-- borrado, usuario_alumno, alumnos, grupos, turnos y sedes iguales que antes, y la fila final «COMMIT hecho». Si
-- sale un error o14_parada_*, no ha cambiado nada.
--
-- Vuelta atrás: hasta el COMMIT, automática (cualquier error deshace la transacción). Después del COMMIT, solo
-- restaurando una copia de seguridad tomada justo antes: la copia de desplegar_migrar no aplica (aquí no hay
-- despliegue). Haz la copia antes con tu procedimiento habitual.

START TRANSACTION;

-- Bloquea la cuenta y sus membresías mientras se comprueba y se borra.
SELECT COUNT(*) FROM usuario WHERE id = @usuario_id FOR UPDATE INTO @o14_bloqueo_usuario;
SELECT COUNT(*) FROM membresia WHERE usuario_id = @usuario_id FOR UPDATE INTO @o14_bloqueo_membresias;

-- ============================================================================
-- 1. Guardas (antes de tocar nada)
-- ============================================================================
SET @o14_existe = (SELECT COUNT(*) FROM usuario WHERE id = @usuario_id);
SET @o14_profesor = (SELECT COUNT(*) FROM membresia
                      WHERE usuario_id = @usuario_id AND organizacion_id = 1 AND rol = 'PROFESOR');
SET @o14_familia = (SELECT COUNT(*) FROM membresia
                     WHERE usuario_id = @usuario_id AND organizacion_id = 1 AND rol = 'FAMILIA');
SET @o14_otro_rol = (SELECT COUNT(*) FROM membresia
                      WHERE usuario_id = @usuario_id AND rol NOT IN ('PROFESOR', 'FAMILIA'))
                  + (SELECT COUNT(*) FROM usuario WHERE id = @usuario_id AND es_admin_plataforma = TRUE);
SET @o14_otra_escuela = (SELECT COUNT(*) FROM membresia WHERE usuario_id = @usuario_id AND organizacion_id <> 1);
SET @o14_propio = (SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = @usuario_id AND relacion = 'PROPIO');
SET @o14_vinculos = (SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = @usuario_id);
SET @o14_historico =
    (SELECT COUNT(*) FROM cobro WHERE cobrado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM cobro WHERE anulado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM entrega_caja WHERE entregado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM entrega_caja WHERE recibido_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM entrega_caja WHERE anulada_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM movimiento_caja WHERE creado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM movimiento_caja WHERE anulado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM producto_alumno WHERE vendido_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM producto_alumno WHERE cancelado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM solicitud WHERE solicitado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM solicitud WHERE resuelto_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM solicitud WHERE encargado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM solicitud WHERE recibido_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM clase_asistencia WHERE registrado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM clase_asistencia WHERE modificado_por_usuario_id = @usuario_id)
  + (SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = @usuario_id AND organizacion_id <> 1);
SET @o14_alta = (SELECT MIN(creada_en) FROM membresia WHERE usuario_id = @usuario_id);
-- El evento que deja el alta al crear la cuenta pendiente (GestionMiembrosService.alta, Cambios.rol):
-- accion CREATE, modulo miembros, recurso_id = la cuenta, organizacion de la sesion y
-- resumen «MIEMBROS MIEMBRO_INVITADO usuario=#<id> rol=<ROL>».
SET @o14_invitada = (SELECT COUNT(*) FROM auditoria_evento
                      WHERE organizacion_id = 1 AND modulo = 'miembros' AND accion = 'CREATE'
                        AND recurso_id = @usuario_id
                        AND resumen LIKE CONCAT('MIEMBROS MIEMBRO_INVITADO usuario=#', @usuario_id, ' %'));
SET @o14_asignaciones = (SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = @usuario_id);

SET @o14_motivo = CASE
  WHEN @usuario_id IS NULL THEN 'usuario_id_nulo'
  WHEN @o14_existe = 0 THEN 'el_usuario_no_existe'
  WHEN @o14_profesor = 0 OR @o14_familia = 0 THEN 'no_tiene_profesor_y_familia_org_1'
  WHEN @o14_otro_rol > 0 THEN 'tiene_otro_rol_o_es_admin'
  WHEN @o14_otra_escuela > 0 THEN 'tiene_membresia_en_otra_escuela'
  WHEN @o14_propio > 0 THEN 'es_la_cuenta_de_una_ficha_propio'
  WHEN @o14_vinculos > 0 THEN 'tiene_vinculos_con_fichas'
  WHEN @o14_historico > 0 THEN 'tiene_historico'
  WHEN @o14_alta IS NULL OR @o14_alta < '2026-11-04' THEN 'alta_anterior_al_2026_11_04'
  WHEN @o14_invitada = 0 THEN 'no_nacio_por_invitacion'
  WHEN @esperado_asignaciones IS NOT NULL AND @o14_asignaciones <> @esperado_asignaciones
    THEN 'asignaciones_distintas_de_las_esperadas'
  ELSE NULL
END;

SET @sql = IF(@o14_motivo IS NULL,
    'SELECT ''o14: guardas superadas'' AS guardas',
    CONCAT('SELECT 1 FROM o14_parada_', @o14_motivo));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. Recuentos antes (agregados, sin emails)
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM usuario),
  (SELECT COUNT(*) FROM usuario WHERE id = @usuario_id),
  (SELECT COUNT(*) FROM membresia),
  (SELECT COUNT(*) FROM membresia WHERE usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM membresia_sede),
  (SELECT COUNT(*) FROM membresia_sede ms JOIN membresia m ON m.id = ms.membresia_id
    WHERE m.usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM asignacion_profesor),
  (SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM usuario_alumno),
  (SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM alumno),
  (SELECT COUNT(*) FROM grupo),
  (SELECT COUNT(*) FROM turno),
  (SELECT COUNT(*) FROM sede)
INTO @a_usuarios, @a_usuario_cuenta, @a_membresias, @a_membresias_cuenta, @a_membresia_sede,
     @a_membresia_sede_cuenta, @a_asignaciones, @a_asignaciones_cuenta, @a_usuario_alumno,
     @a_usuario_alumno_cuenta, @a_alumnos, @a_grupos, @a_turnos, @a_sedes;

SELECT 'antes' AS momento, @a_usuarios AS usuarios, @a_usuario_cuenta AS usuario_cuenta,
       @a_membresias AS membresias, @a_membresias_cuenta AS membresias_cuenta,
       @a_membresia_sede AS membresia_sede, @a_membresia_sede_cuenta AS membresia_sede_cuenta,
       @a_asignaciones AS asignaciones, @a_asignaciones_cuenta AS asignaciones_cuenta,
       @a_usuario_alumno AS usuario_alumno, @a_usuario_alumno_cuenta AS usuario_alumno_cuenta,
       @a_alumnos AS alumnos, @a_grupos AS grupos, @a_turnos AS turnos, @a_sedes AS sedes;

-- ============================================================================
-- 3. Borrado, de las hijas a la cuenta, solo con filas de esta cuenta
-- ============================================================================
DELETE ms FROM membresia_sede ms JOIN membresia m ON m.id = ms.membresia_id
 WHERE m.usuario_id = @usuario_id;
SET @b_membresia_sede = ROW_COUNT();

DELETE FROM asignacion_profesor WHERE usuario_id = @usuario_id;
SET @b_asignaciones = ROW_COUNT();

DELETE FROM membresia WHERE usuario_id = @usuario_id;
SET @b_membresias = ROW_COUNT();

DELETE FROM usuario WHERE id = @usuario_id AND es_admin_plataforma = FALSE;
SET @b_usuario = ROW_COUNT();

SELECT 'borradas' AS filas, @b_usuario AS usuario, @b_membresias AS membresias,
       @b_membresia_sede AS membresia_sede, @b_asignaciones AS asignaciones;

-- ============================================================================
-- 4. Recuentos después y comprobación final (si no cuadra, se detiene y se deshace)
-- ============================================================================
SELECT
  (SELECT COUNT(*) FROM usuario),
  (SELECT COUNT(*) FROM usuario WHERE id = @usuario_id),
  (SELECT COUNT(*) FROM membresia),
  (SELECT COUNT(*) FROM membresia WHERE usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM membresia_sede),
  (SELECT COUNT(*) FROM membresia_sede ms JOIN membresia m ON m.id = ms.membresia_id
    WHERE m.usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM asignacion_profesor),
  (SELECT COUNT(*) FROM asignacion_profesor WHERE usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM usuario_alumno),
  (SELECT COUNT(*) FROM usuario_alumno WHERE usuario_id = @usuario_id),
  (SELECT COUNT(*) FROM alumno),
  (SELECT COUNT(*) FROM grupo),
  (SELECT COUNT(*) FROM turno),
  (SELECT COUNT(*) FROM sede)
INTO @d_usuarios, @d_usuario_cuenta, @d_membresias, @d_membresias_cuenta, @d_membresia_sede,
     @d_membresia_sede_cuenta, @d_asignaciones, @d_asignaciones_cuenta, @d_usuario_alumno,
     @d_usuario_alumno_cuenta, @d_alumnos, @d_grupos, @d_turnos, @d_sedes;

SELECT 'despues' AS momento, @d_usuarios AS usuarios, @d_usuario_cuenta AS usuario_cuenta,
       @d_membresias AS membresias, @d_membresias_cuenta AS membresias_cuenta,
       @d_membresia_sede AS membresia_sede, @d_membresia_sede_cuenta AS membresia_sede_cuenta,
       @d_asignaciones AS asignaciones, @d_asignaciones_cuenta AS asignaciones_cuenta,
       @d_usuario_alumno AS usuario_alumno, @d_usuario_alumno_cuenta AS usuario_alumno_cuenta,
       @d_alumnos AS alumnos, @d_grupos AS grupos, @d_turnos AS turnos, @d_sedes AS sedes;

SET @o14_cuadra =
      @a_usuario_cuenta = 1 AND @b_usuario = 1
  AND @d_usuario_cuenta = 0 AND @d_membresias_cuenta = 0 AND @d_membresia_sede_cuenta = 0
  AND @d_asignaciones_cuenta = 0 AND @d_usuario_alumno_cuenta = 0
  AND @d_usuarios = @a_usuarios - 1
  AND @d_membresias = @a_membresias - @a_membresias_cuenta AND @b_membresias = @a_membresias_cuenta
  AND @d_membresia_sede = @a_membresia_sede - @a_membresia_sede_cuenta
  AND @b_membresia_sede = @a_membresia_sede_cuenta
  AND @d_asignaciones = @a_asignaciones - @a_asignaciones_cuenta AND @b_asignaciones = @a_asignaciones_cuenta
  AND @a_usuario_alumno_cuenta = 0 AND @d_usuario_alumno = @a_usuario_alumno
  AND @d_alumnos = @a_alumnos AND @d_grupos = @a_grupos AND @d_turnos = @a_turnos AND @d_sedes = @a_sedes;

SET @sql = IF(@o14_cuadra = 1,
    'SELECT ''o14: recuentos cuadran'' AS comprobacion',
    'SELECT 1 FROM o14_parada_recuentos_no_cuadran');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

COMMIT;

SELECT 'o14: COMMIT hecho' AS resultado;
