-- Multi-escuela, fase 3, E10-1 (ampliar): pone al día el vínculo antiguo usuario.alumno_id antes de que el código
-- deje de leerlo (E10-2) y de que V32 quite la columna (E10-3). Plan: docs/multi-escuela/fase3-e10-plan.md §3, §4 y §6.
--
-- SOLO AMPLÍA: no quita columnas ni tablas. El código anterior sigue funcionando: las filas MIGRACION nuevas son como
-- las de V31 y la clave en SET NULL no le estorba (eliminarAlumno desvincula la cuenta antes de borrar la ficha).
-- Va como V31_21 (detrás de V31_20; V32 sigue reservada). Antes y después: docs/multi-escuela/precheck-e10.sql
-- (consulta 3: sin_fila_r1 = 0, consulta 8: usuario.alumno_id con una sola clave en SET NULL).
--
-- Pasos (cada uno repetible tras un fallo a medias y `flyway repair`: se vuelve a consultar los datos e
-- information_schema antes de cada cambio):
--   1. usuario_alumno: inserta las filas R1 que falten, con la misma regla que V31 (V31__sedes_y_vinculos.sql,
--      paso 8): usuario.alumno_id -> fila si el email del alumno es el de la cuenta y la cuenta tiene alguna
--      membresía en la escuela del alumno, o si tiene allí una membresía de gestión ACTIVA (PROPIETARIO,
--      ADMINISTRADOR, RESPONSABLE_SEDE o PROFESOR). Sin alumnos anonimizados (V31_11). Organización = la del
--      alumno, relación por edad (mayor de edad: PROPIO, si no TUTOR), origen MIGRACION. No toca ni duplica las
--      filas que ya existen (PK usuario_id + alumno_id). En producción, el 03/11/2026 no faltaba ninguna.
--   2. Clave ajena de usuario.alumno_id hacia alumno(id): pasa a ON DELETE SET NULL, una sola y con nombre fijo
--      fk_usuario_alumno_legado. Se busca por su definición (no por nombre: la de V1 la nombró Hibernate). Una
--      sola sentencia ALTER quita todas las que no son SET NULL y, si no queda ninguna en SET NULL, añade la nueva.
--      Si ya está en SET NULL no hace nada; si no hay ninguna, la crea. El índice único de V1 sobre alumno_id se
--      conserva y lo usa la clave nueva.
SET @schema_name = DATABASE();

-- ============================================================================
-- 1. usuario_alumno: filas R1 que falten
-- ============================================================================
INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
SELECT u.id,
       x.id,
       x.organizacion_id,
       IF(x.fecha_nacimiento IS NOT NULL AND TIMESTAMPDIFF(YEAR, x.fecha_nacimiento, CURDATE()) >= 18,
          'PROPIO', 'TUTOR'),
       'MIGRACION'
FROM usuario u
JOIN alumno x ON x.id = u.alumno_id
WHERE x.anonimizado_en IS NULL
  AND ((x.email IS NOT NULL AND LOWER(x.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''
        AND EXISTS (SELECT 1 FROM membresia m
                    WHERE m.usuario_id = u.id
                      AND m.organizacion_id = x.organizacion_id))
       OR EXISTS (SELECT 1 FROM membresia m
                  WHERE m.usuario_id = u.id
                    AND m.organizacion_id = x.organizacion_id
                    AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR')))
  AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua
                  WHERE ua.usuario_id = u.id AND ua.alumno_id = x.id);

-- ============================================================================
-- 2. usuario.alumno_id: una sola clave ajena, ON DELETE SET NULL
-- ============================================================================
SET @v31_21_quitar = (
    SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', rc.CONSTRAINT_NAME, '`') ORDER BY rc.CONSTRAINT_NAME SEPARATOR ', ')
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = @schema_name AND rc.TABLE_NAME = 'usuario'
      AND rc.REFERENCED_TABLE_NAME = 'alumno' AND rc.DELETE_RULE <> 'SET NULL'
      AND EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
                  WHERE k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND k.TABLE_NAME = rc.TABLE_NAME
                    AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                    AND k.COLUMN_NAME = 'alumno_id' AND k.REFERENCED_TABLE_NAME = 'alumno')
);
SET @v31_21_en_set_null = (
    SELECT COUNT(*)
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = @schema_name AND rc.TABLE_NAME = 'usuario'
      AND rc.REFERENCED_TABLE_NAME = 'alumno' AND rc.DELETE_RULE = 'SET NULL'
      AND EXISTS (SELECT 1 FROM information_schema.KEY_COLUMN_USAGE k
                  WHERE k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND k.TABLE_NAME = rc.TABLE_NAME
                    AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
                    AND k.COLUMN_NAME = 'alumno_id' AND k.REFERENCED_TABLE_NAME = 'alumno')
);
SET @sql = CASE
    WHEN @v31_21_quitar IS NOT NULL AND @v31_21_en_set_null > 0
        THEN CONCAT('ALTER TABLE usuario ', @v31_21_quitar)
    WHEN @v31_21_quitar IS NOT NULL
        THEN CONCAT('ALTER TABLE usuario ', @v31_21_quitar, ', ',
                    'ADD CONSTRAINT fk_usuario_alumno_legado FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE SET NULL')
    WHEN @v31_21_en_set_null > 0 THEN 'SELECT ''usuario.alumno_id FK already ON DELETE SET NULL'''
    ELSE 'ALTER TABLE usuario ADD CONSTRAINT fk_usuario_alumno_legado FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE SET NULL'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
