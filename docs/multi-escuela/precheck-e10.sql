-- Comprobación previa de la E10 (V31_21, V32 y V32_1). Solo lectura y solo recuentos.
-- R1 = la regla de V31: email del alumno igual al de la cuenta (TRIM + LOWER de la cuenta contra LOWER del
-- alumno, sin blancos) con alguna membresía en la escuela del alumno, o membresía de gestión ACTIVA allí.

-- Consulta 1: versión de Flyway
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;

-- Consulta 2: usuario_rol frente a su membresía equivalente de V20 en la organización 1
SELECT IFNULL(ur.roles, 'NULL') AS rol_antiguo,
       COUNT(*) AS filas,
       COUNT(DISTINCT ur.usuario_id) AS cuentas,
       SUM(EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.organizacion_id = 1
                     AND m.activa = TRUE
                     AND m.rol = CASE ur.roles WHEN 'ROLE_ADMIN' THEN 'PROPIETARIO'
                                               WHEN 'ROLE_MANAGER' THEN 'ADMINISTRADOR'
                                               WHEN 'ROLE_USER' THEN 'FAMILIA' END)) AS equivalente_activa,
       SUM(EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.organizacion_id = 1
                     AND m.activa = FALSE
                     AND m.rol = CASE ur.roles WHEN 'ROLE_ADMIN' THEN 'PROPIETARIO'
                                               WHEN 'ROLE_MANAGER' THEN 'ADMINISTRADOR'
                                               WHEN 'ROLE_USER' THEN 'FAMILIA' END)) AS equivalente_inactiva,
       SUM(NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.organizacion_id = 1
                         AND m.rol = CASE ur.roles WHEN 'ROLE_ADMIN' THEN 'PROPIETARIO'
                                                   WHEN 'ROLE_MANAGER' THEN 'ADMINISTRADOR'
                                                   WHEN 'ROLE_USER' THEN 'FAMILIA' END)) AS sin_equivalente,
       SUM(NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.activa = TRUE))
         AS cuenta_sin_membresia_activa
FROM usuario_rol ur
GROUP BY ur.roles
ORDER BY rol_antiguo;

-- Consulta 3: usuario.alumno_id. con_fila y las cuatro sin_fila suman usuarios_con_alumno_id.
SELECT
  (SELECT COUNT(*) FROM usuario u WHERE u.alumno_id IS NOT NULL) AS usuarios_con_alumno_id,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id))
    AS con_fila,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NULL
      AND ((a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''
            AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
           OR EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                        AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR'))))
    AS sin_fila_r1,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NULL
      AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id)
      AND NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> '')
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                        AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR')))
    AS sin_fila_otro_email_sin_gestion,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NULL
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
    AS sin_fila_sin_membresia,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NOT NULL)
    AS sin_fila_anonimizado,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id WHERE a.activo = FALSE)
    AS alumno_inactivo,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''))
    AS email_distinto_de_la_cuenta,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id AND a.activo = TRUE
    WHERE EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                    AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR'))
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                        AND m.activa = TRUE AND m.rol = 'FAMILIA'))
    AS gestion_sin_familia_con_ficha;

-- Consulta 4: «la ficha tiene cuenta», hoy (alumno_id) frente a mañana (usuario_alumno)
SELECT
  (SELECT COUNT(DISTINCT u.alumno_id) FROM usuario u WHERE u.alumno_id IS NOT NULL) AS alumnos_con_cuenta_hoy,
  (SELECT COUNT(DISTINCT ua.alumno_id) FROM usuario_alumno ua) AS alumnos_con_fila,
  (SELECT COUNT(*) FROM alumno a
    WHERE EXISTS (SELECT 1 FROM usuario u WHERE u.alumno_id = a.id)
      AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.alumno_id = a.id)) AS alumnos_cuenta_sin_fila,
  (SELECT COUNT(*) FROM alumno a
    WHERE NOT EXISTS (SELECT 1 FROM usuario u WHERE u.alumno_id = a.id)
      AND EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.alumno_id = a.id)) AS alumnos_fila_sin_cuenta;

-- Consulta 5: fichas con N cuentas vinculadas
SELECT d.cuentas, COUNT(*) AS fichas
FROM (SELECT ua.alumno_id, COUNT(*) AS cuentas FROM usuario_alumno ua GROUP BY ua.alumno_id) d
GROUP BY d.cuentas ORDER BY d.cuentas;

-- Consulta 6: usuario_alumno por origen y relación
SELECT ua.origen, ua.relacion, COUNT(*) AS filas FROM usuario_alumno ua
GROUP BY ua.origen, ua.relacion ORDER BY ua.origen, ua.relacion;

-- Consulta 7: membresías por escuela, rol y estado (deben coincidir antes y después de V32)
SELECT m.organizacion_id, m.rol, m.activa, COUNT(*) AS membresias FROM membresia m
GROUP BY m.organizacion_id, m.rol, m.activa ORDER BY m.organizacion_id, m.rol, m.activa;

-- Consulta 8: claves ajenas del modelo antiguo (V31_21 deja usuario.alumno_id en SET NULL)
SELECT k.TABLE_NAME AS tabla, k.COLUMN_NAME AS columna, rc.DELETE_RULE AS al_borrar, COUNT(*) AS claves
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
  ON rc.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = k.CONSTRAINT_NAME
 AND rc.TABLE_NAME = k.TABLE_NAME
WHERE k.TABLE_SCHEMA = DATABASE()
  AND ((k.TABLE_NAME = 'usuario' AND k.COLUMN_NAME = 'alumno_id') OR k.TABLE_NAME = 'usuario_rol')
GROUP BY k.TABLE_NAME, k.COLUMN_NAME, rc.DELETE_RULE;

-- Consulta 9: grupos (V32_1). Deben ser 0 las dos primeras.
SELECT
  (SELECT COUNT(*) FROM grupo g WHERE g.sede_id IS NULL) AS grupos_sin_sede,
  (SELECT COUNT(*) FROM (SELECT g.sede_id, g.nombre FROM grupo g WHERE g.sede_id IS NOT NULL
                         GROUP BY g.sede_id, g.nombre HAVING COUNT(*) > 1) r) AS nombres_repetidos_en_sede,
  (SELECT COUNT(*) FROM organizacion o
    WHERE NOT EXISTS (SELECT 1 FROM sede s WHERE s.organizacion_id = o.id AND s.activa = TRUE))
    AS organizaciones_sin_sede_activa;

-- Consulta 10: familias de la org 1 sin ficha visible (comparar con el postcheck; no depende de alumno_id)
SELECT
  (SELECT COUNT(*) FROM membresia f JOIN usuario u ON u.id = f.usuario_id
    WHERE f.organizacion_id = 1 AND f.rol = 'FAMILIA' AND f.activa = TRUE
      AND NOT EXISTS (SELECT 1 FROM alumno a WHERE a.organizacion_id = 1 AND a.activo = TRUE
                        AND a.email IS NOT NULL AND TRIM(u.email) <> '' AND LOWER(a.email) = LOWER(TRIM(u.email)))
      AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua JOIN alumno a ON a.id = ua.alumno_id
                       WHERE ua.usuario_id = u.id AND ua.organizacion_id = 1 AND a.activo = TRUE
                         AND ua.origen = 'MANUAL')) AS familias_org1_sin_ficha_visible;
