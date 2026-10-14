-- Comprobación posterior de V31. Solo lectura y solo recuentos: no devuelve emails, nombres ni identificadores.
-- Ejecutar DESPUÉS de V31 y guardar el resultado; volver a ejecutarla antes de V32 (contracción).
-- Hasta la entrega 2 (registro y Google escriben usuario_alumno) las altas nuevas no crean vínculos: justo tras
-- V31 las dos primeras columnas son 0; más tarde pueden crecer con las cuentas nuevas.
--
-- Deben ser 0:
--   familia_alumnos_email_sin_vinculo   organización 1, cuentas con membresía FAMILIA activa y sin rol de
--                                       gestión activo (un gestor degradado cuenta como familia): parejas cuenta-alumno activo visibles hoy por email
--                                       (TRIM + LOWER de la cuenta contra LOWER(alumno.email)) sin fila en
--                                       usuario_alumno
--   familia_alumnos_vinculo_sin_email   ídem al revés: filas de usuario_alumno de alumnos activos que la
--                                       cuenta no ve hoy por email
--   grupos_sin_sede                     grupos con sede_id NULL (todas las organizaciones)
--   organizaciones_sin_sede             organizaciones sin ninguna sede
-- Informativas:
--   mixtas_alumnos_email_sin_vinculo    como las dos primeras, para cuentas FAMILIA que además tienen rol de
--   mixtas_alumnos_vinculo_sin_email    gestión activo en la organización 1 (R1 puede vincularlas con un alumno de otro
--                                       email; hoy ya lo ven todo)
--   alumnos_sin_sede_principal          alumnos con sede_principal_id NULL (las altas posteriores a V31 aún no
--                                       la escriben)
--   usuarios_admin_plataforma           cuentas marcadas como administrador de plataforma (0 tras V31; la
--                                       marca se pone con SQL fuera del repositorio)
-- Consulta 2: filas de usuario_alumno por origen y relación.
SELECT
  (SELECT COUNT(*)
     FROM usuario u
     JOIN alumno a ON a.organizacion_id = 1 AND a.activo = TRUE
                  AND a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email))
    WHERE EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                    AND m.rol = 'FAMILIA' AND m.activa = TRUE)
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                        AND m.rol <> 'FAMILIA' AND m.activa = TRUE)
      AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id))
    AS familia_alumnos_email_sin_vinculo,
  (SELECT COUNT(*)
     FROM usuario_alumno ua
     JOIN usuario u ON u.id = ua.usuario_id
     JOIN alumno a ON a.id = ua.alumno_id AND a.organizacion_id = 1 AND a.activo = TRUE
    WHERE EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                    AND m.rol = 'FAMILIA' AND m.activa = TRUE)
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                        AND m.rol <> 'FAMILIA' AND m.activa = TRUE)
      AND NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email))))
    AS familia_alumnos_vinculo_sin_email,
  (SELECT COUNT(*) FROM grupo g WHERE g.sede_id IS NULL)
    AS grupos_sin_sede,
  (SELECT COUNT(*) FROM organizacion o WHERE NOT EXISTS (SELECT 1 FROM sede s WHERE s.organizacion_id = o.id))
    AS organizaciones_sin_sede,
  (SELECT COUNT(*)
     FROM usuario u
     JOIN alumno a ON a.organizacion_id = 1 AND a.activo = TRUE
                  AND a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email))
    WHERE EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                    AND m.rol = 'FAMILIA' AND m.activa = TRUE)
      AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                    AND m.rol <> 'FAMILIA' AND m.activa = TRUE)
      AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id))
    AS mixtas_alumnos_email_sin_vinculo,
  (SELECT COUNT(*)
     FROM usuario_alumno ua
     JOIN usuario u ON u.id = ua.usuario_id
     JOIN alumno a ON a.id = ua.alumno_id AND a.organizacion_id = 1 AND a.activo = TRUE
    WHERE EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                    AND m.rol = 'FAMILIA' AND m.activa = TRUE)
      AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = 1
                    AND m.rol <> 'FAMILIA' AND m.activa = TRUE)
      AND NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email))))
    AS mixtas_alumnos_vinculo_sin_email,
  (SELECT COUNT(*) FROM alumno a WHERE a.sede_principal_id IS NULL)
    AS alumnos_sin_sede_principal,
  (SELECT COUNT(*) FROM usuario u WHERE u.es_admin_plataforma = TRUE)
    AS usuarios_admin_plataforma;

-- Consulta 2: filas de usuario_alumno por origen y relación -----------------------
SELECT ua.origen, ua.relacion, COUNT(*) AS filas
FROM usuario_alumno ua
GROUP BY ua.origen, ua.relacion
ORDER BY ua.origen, ua.relacion;
