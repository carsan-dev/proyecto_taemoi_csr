-- Comprobación posterior de la E10. Solo lectura y solo recuentos. Deben ser 0 las tres primeras columnas.
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario_rol') AS tabla_usuario_rol,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id') AS columna_alumno_id,
  (SELECT COUNT(*) FROM usuario_alumno ua JOIN alumno a ON a.id = ua.alumno_id
    WHERE a.organizacion_id <> ua.organizacion_id) AS vinculos_de_otra_escuela,
  -- Igual que antes de V32 (familias sin ninguna ficha visible en la org 1; V32 no debe cambiarlo):
  (SELECT COUNT(*) FROM membresia f JOIN usuario u ON u.id = f.usuario_id
    WHERE f.organizacion_id = 1 AND f.rol = 'FAMILIA' AND f.activa = TRUE
      AND NOT EXISTS (SELECT 1 FROM alumno a WHERE a.organizacion_id = 1 AND a.activo = TRUE
                        AND a.email IS NOT NULL AND TRIM(u.email) <> '' AND LOWER(a.email) = LOWER(TRIM(u.email)))
      AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua JOIN alumno a ON a.id = ua.alumno_id
                       WHERE ua.usuario_id = u.id AND ua.organizacion_id = 1 AND a.activo = TRUE
                         AND ua.origen = 'MANUAL')) AS familias_org1_sin_ficha_visible;

SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;

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

-- Solo tras V32_1: debe dar NO y 1 (indice_unico = 1 solo si uk_grupo_sede_nombre es único y exactamente
-- (sede_id, nombre), en ese orden).
SELECT
  (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'sede_id') AS sede_id_admite_null,
  (SELECT COUNT(*) = 2
          AND SUM(NON_UNIQUE = 0 AND ((SEQ_IN_INDEX = 1 AND COLUMN_NAME = 'sede_id')
                                      OR (SEQ_IN_INDEX = 2 AND COLUMN_NAME = 'nombre'))) = 2
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND INDEX_NAME = 'uk_grupo_sede_nombre') AS indice_unico;
