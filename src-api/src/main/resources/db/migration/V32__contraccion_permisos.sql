-- Multi-escuela, fase 3, E10-3 (contraer): quita el rol antiguo (tabla usuario_rol) y el vínculo antiguo (columna
-- usuario.alumno_id). Plan: docs/multi-escuela/fase3-e10-plan.md §3, §4 y §6.
--
-- REQUISITO DE DESPLIEGUE: el código ya no mapea, lee ni escribe ninguno de los dos (E10-2, PR #201; el último
-- lector, UsuarioRepository.tieneDatosFueraDeLaInvitacion, sale en esta misma entrega). Con ddl-auto=validate, una
-- imagen anterior a la E10-2 no arranca sobre este esquema. Antes: docs/multi-escuela/precheck-e10.sql; después:
-- docs/multi-escuela/postcheck-e10.sql. Volver atrás exige restaurar la copia de seguridad de desplegar_migrar.
-- Sin la guarda «no en el mismo despliegue que V31» de fase3-sedes-permisos.md (decisión 433): V31 está aplicada
-- desde el 15/10 y la guarda solo estorbaría al montar una base desde cero.
--
-- Pasos (cada uno repetible tras un fallo a medias y `flyway repair`: se vuelve a consultar information_schema antes
-- de cada cambio):
--   1. Última resincronización R1: la misma sentencia que V31_21 (paso 1). Inserta en usuario_alumno las filas que
--      falten de los pares usuario.alumno_id que cumplen R1, sin alumnos anonimizados, origen MIGRACION. Sin efecto
--      si ya está al día. Va como cadena preparada: si la columna ya no existe (repetición), no se ejecuta.
--   2. Claves ajenas de usuario.alumno_id hacia alumno(id), buscadas por definición (la de V1 la nombró Hibernate;
--      V31_21 dejó una sola, fk_usuario_alumno_legado). Caen todas en una sola sentencia.
--   3. La columna usuario.alumno_id (su índice único de V1 cae con ella).
--   4. La tabla usuario_rol (su clave ajena hacia usuario cae con ella).
--
-- Se pierden (solo recuperables desde la copia): usuario_rol entera (todo lo que valía se copió a membresia en V20) y
-- los pares usuario.alumno_id que R1 excluye a propósito (plan §3).
SET @schema_name = DATABASE();

-- ============================================================================
-- 1. usuario_alumno: filas R1 que falten (misma sentencia que V31_21)
-- ============================================================================
SET @v32_existe_alumno_id = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id'
);
SET @sql = IF(@v32_existe_alumno_id = 0,
    'SELECT ''v32: usuario.alumno_id ya no existe (sin resincronizacion)''',
    'INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
     SELECT u.id,
            x.id,
            x.organizacion_id,
            IF(x.fecha_nacimiento IS NOT NULL AND TIMESTAMPDIFF(YEAR, x.fecha_nacimiento, CURDATE()) >= 18,
               ''PROPIO'', ''TUTOR''),
            ''MIGRACION''
     FROM usuario u
     JOIN alumno x ON x.id = u.alumno_id
     WHERE x.anonimizado_en IS NULL
       AND ((x.email IS NOT NULL AND LOWER(x.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''''
             AND EXISTS (SELECT 1 FROM membresia m
                         WHERE m.usuario_id = u.id
                           AND m.organizacion_id = x.organizacion_id))
            OR EXISTS (SELECT 1 FROM membresia m
                       WHERE m.usuario_id = u.id
                         AND m.organizacion_id = x.organizacion_id
                         AND m.activa = TRUE AND m.rol IN (''PROPIETARIO'', ''ADMINISTRADOR'', ''RESPONSABLE_SEDE'', ''PROFESOR'')))
       AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua
                       WHERE ua.usuario_id = u.id AND ua.alumno_id = x.id)');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. usuario.alumno_id: claves ajenas hacia alumno, buscadas por definición
-- ============================================================================
SET @v32_quitar = (
    SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', rc.CONSTRAINT_NAME, '`') ORDER BY rc.CONSTRAINT_NAME SEPARATOR ', ')
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = @schema_name AND rc.TABLE_NAME = 'usuario'
      AND rc.REFERENCED_TABLE_NAME = 'alumno'
      AND EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
                  WHERE k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND k.TABLE_NAME = rc.TABLE_NAME
                    AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                    AND k.COLUMN_NAME = 'alumno_id' AND k.REFERENCED_TABLE_NAME = 'alumno')
);
SET @sql = IF(@v32_quitar IS NULL,
    'SELECT ''v32: usuario.alumno_id sin clave ajena''',
    CONCAT('ALTER TABLE usuario ', @v32_quitar));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 3. La columna usuario.alumno_id (su índice único cae con ella)
-- ============================================================================
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id') = 0,
    'SELECT ''v32: usuario.alumno_id ya no existe''',
    'ALTER TABLE usuario DROP COLUMN alumno_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 4. La tabla de roles antiguos (su clave ajena hacia usuario cae con ella)
-- ============================================================================
DROP TABLE IF EXISTS usuario_rol;
