-- Comprobación previa de V31 (sedes y vínculos familia-alumno). Solo lectura y solo recuentos: no devuelve
-- emails, nombres ni identificadores. Ejecutar ANTES de desplegar V31 y guardar el resultado.
-- V31 no se detiene por ningún recuento (solo añade); estos números dicen qué hará el relleno de
-- usuario_alumno (fase3-sedes-permisos.md §5) y qué hay que revisar a mano.
--
-- «Email igual» = como hoy el acceso de familia y como V31: email de la cuenta normalizado (TRIM + LOWER)
-- contra LOWER(alumno.email), sin recortar el del alumno, con la colación de la columna.
-- El email en blanco (TRIM = '') nunca cuenta como coincidencia, igual que AlumnoAccessControlServiceImpl.
-- «Gestión» = membresía ACTIVA PROPIETARIO, ADMINISTRADOR, RESPONSABLE_SEDE o PROFESOR (una inactiva no da
-- autoridades: la pantalla de roles degrada desactivando la membresía).
--
-- Consulta 1, usuario.alumno_id (regla R1):
--   usuarios_con_alumno_id              total de cuentas con alumno_id
--   alumno_id_inexistente               alumno_id que no apunta a ningún alumno (debe ser 0: hay FK)
--   alumno_id_escuela_sin_membresia     el alumno es de una escuela donde la cuenta no tiene membresía: V31
--                                       NO crea fila aunque el email coincida (R3)
--   alumno_id_email_distinto_familia    email distinto y sin gestión en la escuela del alumno: V31 NO crea
--                                       fila (hoy tampoco le da acceso); revisar a mano
--   alumno_id_email_distinto_gestion    email distinto con gestión en esa escuela: V31 crea fila
-- Consulta 2, emails (reglas R2 y R3):
--   alumnos_email_solo_mayusculas       alumnos que coinciden con una cuenta solo sin distinguir mayúsculas (o
--                                       acentos, por la colación): hoy ya son visibles; V31 crea fila
--   alumnos_email_solo_espacios         alumnos que coincidirían con una cuenta quitando espacios: hoy NO son
--                                       visibles y V31 NO crea fila; corregir el email del alumno a mano
--   alumno_cuenta_escuela_sin_membresia parejas alumno-cuenta con el mismo email en una escuela donde la
--                                       cuenta no tiene membresía (R3: sin fila)
--   emails_compartidos_por_alumnos      emails (por escuela) que llevan dos o más alumnos (hermanos…)
--   alumnos_con_email_compartido        alumnos que comparten email con otro de su escuela
-- Consulta 3, distribución: cuántas cuentas (con membresía en la escuela) ven N alumnos por email.
-- Consulta 4, resto:
--   tutor_email_candidatos              parejas tutor-cuenta: tutor.email es el de una cuenta que no ve a ese
--                                       alumno ni por email ni por alumno_id (R4: nunca automático; lista
--                                       manual de candidatos)
--   cuentas_solo_familia_sin_alumno     (cuenta, escuela) con membresía activa solo FAMILIA y ningún alumno
--                                       activo con su email: hoy no pueden entrar
--   membresias_responsable_o_profesor   membresías RESPONSABLE_SEDE o PROFESOR (debe ser 0)
--   grupos_nombre_repetido              (escuela, nombre) con más de un grupo (para el UNIQUE de V32)
--   organizaciones                      organizaciones; antes de V31 ninguna tiene sede y V31 crea una para
--                                       cada una
--   filas_usuario_alumno_r1             filas que creará R1
--   filas_usuario_alumno_r2             filas que creará R2
--   filas_usuario_alumno_total          filas que creará V31 (R1 ∪ R2)

-- Consulta 1 ---------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM usuario u WHERE u.alumno_id IS NOT NULL)
    AS usuarios_con_alumno_id,
  (SELECT COUNT(*) FROM usuario u LEFT JOIN alumno a ON a.id = u.alumno_id
    WHERE u.alumno_id IS NOT NULL AND a.id IS NULL)
    AS alumno_id_inexistente,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
    AS alumno_id_escuela_sin_membresia,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> '')
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                        AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR')))
    AS alumno_id_email_distinto_familia,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> '')
      AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                    AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR')))
    AS alumno_id_email_distinto_gestion;

-- Consulta 2 ---------------------------------------------------------------------
SELECT
  (SELECT COUNT(DISTINCT a.id) FROM alumno a JOIN usuario u ON LOWER(a.email) = LOWER(TRIM(u.email))
    WHERE a.email IS NOT NULL
      AND CAST(a.email AS BINARY) <> CAST(TRIM(u.email) AS BINARY))
    AS alumnos_email_solo_mayusculas,
  (SELECT COUNT(DISTINCT a.id) FROM alumno a JOIN usuario u ON LOWER(TRIM(a.email)) = LOWER(TRIM(u.email))
    WHERE a.email IS NOT NULL AND TRIM(a.email) <> ''
      AND NOT EXISTS (SELECT 1 FROM usuario u2 WHERE LOWER(a.email) = LOWER(TRIM(u2.email))))
    AS alumnos_email_solo_espacios,
  (SELECT COUNT(*) FROM alumno a JOIN usuario u ON LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''
    WHERE a.email IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
    AS alumno_cuenta_escuela_sin_membresia,
  (SELECT COUNT(*) FROM (
     SELECT a.organizacion_id, LOWER(TRIM(a.email)) AS email_normalizado
     FROM alumno a WHERE a.email IS NOT NULL AND TRIM(a.email) <> ''
     GROUP BY a.organizacion_id, LOWER(TRIM(a.email)) HAVING COUNT(*) > 1) e)
    AS emails_compartidos_por_alumnos,
  (SELECT COALESCE(SUM(e.n), 0) FROM (
     SELECT COUNT(*) AS n
     FROM alumno a WHERE a.email IS NOT NULL AND TRIM(a.email) <> ''
     GROUP BY a.organizacion_id, LOWER(TRIM(a.email)) HAVING COUNT(*) > 1) e)
    AS alumnos_con_email_compartido;

-- Consulta 3: distribución (alumnos por email -> cuentas) ------------------------
SELECT d.alumnos_por_email, COUNT(*) AS cuentas
FROM (
  SELECT u.id AS usuario_id, a.organizacion_id, COUNT(*) AS alumnos_por_email
  FROM usuario u
  JOIN alumno a ON a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''
  WHERE EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id)
  GROUP BY u.id, a.organizacion_id
) d
GROUP BY d.alumnos_por_email
ORDER BY d.alumnos_por_email;

-- Consulta 4 ---------------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM tutor t
     JOIN usuario u ON LOWER(TRIM(t.email)) = LOWER(TRIM(u.email))
     JOIN alumno a ON a.id = t.alumno_id
    WHERE t.email IS NOT NULL AND TRIM(t.email) <> ''
      AND NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)))
      AND NOT (u.alumno_id <=> a.id))
    AS tutor_email_candidatos,
  (SELECT COUNT(*) FROM (
     SELECT m.usuario_id, m.organizacion_id
     FROM membresia m JOIN usuario u ON u.id = m.usuario_id
     WHERE m.activa = TRUE
     GROUP BY m.usuario_id, m.organizacion_id, u.email
     HAVING SUM(m.rol <> 'FAMILIA') = 0
        AND NOT EXISTS (SELECT 1 FROM alumno a
                        WHERE a.organizacion_id = m.organizacion_id AND a.activo = TRUE
                          AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> '')) f)
    AS cuentas_solo_familia_sin_alumno,
  (SELECT COUNT(*) FROM membresia m WHERE m.rol IN ('RESPONSABLE_SEDE', 'PROFESOR'))
    AS membresias_responsable_o_profesor,
  (SELECT COUNT(*) FROM (
     SELECT g.organizacion_id, g.nombre FROM grupo g
     GROUP BY g.organizacion_id, g.nombre HAVING COUNT(*) > 1) r)
    AS grupos_nombre_repetido,
  (SELECT COUNT(*) FROM organizacion o)
    AS organizaciones,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''
           AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
       OR EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                    AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR')))
    AS filas_usuario_alumno_r1,
  (SELECT COUNT(*) FROM alumno a JOIN usuario u ON LOWER(a.email) = LOWER(TRIM(u.email))
    WHERE a.email IS NOT NULL AND TRIM(u.email) <> ''
      AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
    AS filas_usuario_alumno_r2,
  (SELECT COUNT(*) FROM (
     SELECT u.id AS usuario_id, a.id AS alumno_id FROM usuario u JOIN alumno a ON a.id = u.alumno_id
     WHERE (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''
            AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
        OR EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                     AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR'))
     UNION
     SELECT u.id, a.id FROM alumno a JOIN usuario u ON LOWER(a.email) = LOWER(TRIM(u.email))
     WHERE a.email IS NOT NULL AND TRIM(u.email) <> ''
       AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id)) t)
    AS filas_usuario_alumno_total;
