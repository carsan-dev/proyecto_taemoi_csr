-- Multi-escuela, fase 3 (ampliar): sedes, vínculos familia-alumno, profesores y administrador de plataforma
-- (docs/multi-escuela/fase3-sedes-permisos.md §2 y §5; despliegue 3, con la aplicación parada).
--
-- SOLO AÑADE: el código anterior sigue funcionando sobre este esquema (columnas nuevas NULL o con valor por
-- defecto, tablas nuevas que no lee). Por eso no lleva guarda ni comprobación que la detenga; la guarda va en
-- V32 (contracción), que nunca se despliega en la misma ejecución. Antes de desplegar se ejecuta
-- docs/multi-escuela/precheck-v31.sql (solo recuentos) y después docs/multi-escuela/postcheck-v31.sql.
--
-- Pasos (cada uno repetible: se vuelve a consultar information_schema y los datos antes de cada cambio; nada
-- se decide antes de una DDL para saltarlo al repetir tras un fallo a medias y `flyway repair`):
--   0. Tabla TEMPORAL v31_aplicada_en_esta_conexion para la guarda de V32 (como V27). Va primero: sin el
--      permiso CREATE TEMPORARY TABLES, V31 falla sin cambiar nada.
--   1. sede (UNIQUE organizacion_id + nombre; aforo_turno NULL = limite_turno de la configuración).
--   2. Una sede por cada organización que no tenga ninguna: la 1 (Taemoi) «Umbrete» (decisión D1, dirección
--      pública de la web), las demás «Sede principal». Todas las organizaciones, tengan grupos o no (§2.2).
--   3. grupo.sede_id BIGINT NULL, índice y FK; relleno con la única sede de su organización.
--   4. alumno.sede_principal_id BIGINT NULL, índice y FK (ON DELETE SET NULL); relleno en las organizaciones
--      con una sola sede.
--   5. membresia_sede (membresia_id, sede_id), PK compuesta, FK ON DELETE CASCADE a las dos.
--   6. asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol TITULAR/AYUDANTE), PK (usuario_id,
--      grupo_id); borrar el usuario o el grupo borra la asignación.
--   7. usuario.es_admin_plataforma BOOLEAN NOT NULL DEFAULT FALSE. NINGUNA fila se marca aquí (se hace con SQL
--      fuera del repositorio).
--   8. usuario_alumno (usuario_id, alumno_id, organizacion_id = la del alumno, relacion PROPIO/TUTOR, origen
--      MIGRACION/REGISTRO/MANUAL, creado_en), PK (usuario_id, alumno_id), FK ON DELETE CASCADE. Relleno (§5),
--      origen = MIGRACION, en una sola sentencia:
--        R1 usuario.alumno_id -> fila si el email del alumno es el de la cuenta o si el usuario tiene una
--           membresía de gestión ACTIVA (PROPIETARIO, ADMINISTRADOR, RESPONSABLE_SEDE o PROFESOR) en la escuela
--           del alumno (como hoy: solo las membresías activas dan autoridades; la pantalla de roles degrada
--           desactivando la membresía y un gestor degradado ya no ve ese alumno). Solo familia y email distinto: sin fila (hoy no le da acceso). Con el email
--           igual pero sin ninguna membresía en la escuela del alumno, tampoco (prevalece R3): toda fila de
--           usuario_alumno tiene una membresía del usuario en la escuela del alumno.
--        R2 cada alumno, activo o no, cuyo email es el de una cuenta con alguna membresía (activa o no) en la
--           escuela del alumno.
--        R3 email de alumnos de una escuela donde la cuenta no tiene membresía: sin fila (solo precheck).
--        R4 tutor.email: nunca (solo precheck, candidatos manuales).
--      «Email igual» se compara EXACTAMENTE como hoy el acceso de familia (AlumnoAccessControlServiceImpl,
--      AlumnoRepository.contarActivosPorEmailEnOrganizacion): email de la cuenta normalizado (TRIM + LOWER,
--      EmailUtils.normalizeEmail) contra LOWER(alumno.email) con la colación de la columna. El email del alumno
--      NO se recorta: un alumno con espacios sobrantes no es visible hoy y no gana acceso (el precheck los
--      cuenta en emails_alumno_solo_espacios).
--      relacion: PROPIO si el alumno tiene 18 años o más hoy, TUTOR si no o sin fecha (D7; informativa).
--   9. usuario_rol: su FK pasa a ON DELETE CASCADE. El código deja de mapear Usuario.roles (fase 3, entrega 1)
--      y ya no borra esas filas al borrar un usuario; usuario_rol se borra en V32.

SET @schema_name = DATABASE();

-- ============================================================================
-- 0. Marca para la guarda de V32 (solo existe en la conexión de esta ejecución de Flyway)
-- ============================================================================
CREATE TEMPORARY TABLE IF NOT EXISTS v31_aplicada_en_esta_conexion (
    marca TINYINT NOT NULL PRIMARY KEY
);
INSERT IGNORE INTO v31_aplicada_en_esta_conexion (marca) VALUES (1);

-- ============================================================================
-- 1. sede
-- ============================================================================
CREATE TABLE IF NOT EXISTS sede (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  direccion VARCHAR(255) NULL,
  localidad VARCHAR(100) NULL,
  codigo_postal VARCHAR(10) NULL,
  telefono VARCHAR(20) NULL,
  email VARCHAR(180) NULL,
  codigo_club_federacion VARCHAR(40) NULL,
  -- NULL = se usa limite_turno de configuracion_organizacion.
  aforo_turno INT NULL,
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sede_organizacion_nombre (organizacion_id, nombre),
  CONSTRAINT fk_sede_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 2. Una sede por organización sin ninguna
-- ============================================================================
INSERT INTO sede (organizacion_id, nombre, direccion, localidad)
SELECT o.id,
       IF(o.id = 1, 'Umbrete', 'Sede principal'),
       IF(o.id = 1, 'Calle Parada de la Cigüeña 36', NULL),
       IF(o.id = 1, 'Umbrete', NULL)
FROM organizacion o
WHERE NOT EXISTS (SELECT 1 FROM sede s WHERE s.organizacion_id = o.id);

-- ============================================================================
-- 3. grupo.sede_id
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'sede_id'),
        'SELECT ''grupo.sede_id already exists''',
        'ALTER TABLE grupo ADD COLUMN sede_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'grupo' AND INDEX_NAME = 'idx_grupo_sede'),
        'SELECT ''idx_grupo_sede already exists''',
        'ALTER TABLE grupo ADD INDEX idx_grupo_sede (sede_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'grupo'
                  AND CONSTRAINT_NAME = 'fk_grupo_sede' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_grupo_sede already exists''',
        'ALTER TABLE grupo ADD CONSTRAINT fk_grupo_sede FOREIGN KEY (sede_id) REFERENCES sede(id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE grupo g
JOIN (SELECT organizacion_id, MIN(id) AS sede_id
      FROM sede
      GROUP BY organizacion_id
      HAVING COUNT(*) = 1) unica
  ON unica.organizacion_id = g.organizacion_id
SET g.sede_id = unica.sede_id
WHERE g.sede_id IS NULL;

-- ============================================================================
-- 4. alumno.sede_principal_id
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'sede_principal_id'),
        'SELECT ''alumno.sede_principal_id already exists''',
        'ALTER TABLE alumno ADD COLUMN sede_principal_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'alumno' AND INDEX_NAME = 'idx_alumno_sede_principal'),
        'SELECT ''idx_alumno_sede_principal already exists''',
        'ALTER TABLE alumno ADD INDEX idx_alumno_sede_principal (sede_principal_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'alumno'
                  AND CONSTRAINT_NAME = 'fk_alumno_sede_principal' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_alumno_sede_principal already exists''',
        'ALTER TABLE alumno ADD CONSTRAINT fk_alumno_sede_principal FOREIGN KEY (sede_principal_id) REFERENCES sede(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE alumno a
JOIN (SELECT organizacion_id, MIN(id) AS sede_id
      FROM sede
      GROUP BY organizacion_id
      HAVING COUNT(*) = 1) unica
  ON unica.organizacion_id = a.organizacion_id
SET a.sede_principal_id = unica.sede_id
WHERE a.sede_principal_id IS NULL;

-- ============================================================================
-- 5. membresia_sede (solo RESPONSABLE_SEDE y PROFESOR; sin filas = todas las sedes)
-- ============================================================================
CREATE TABLE IF NOT EXISTS membresia_sede (
  membresia_id BIGINT NOT NULL,
  sede_id BIGINT NOT NULL,
  PRIMARY KEY (membresia_id, sede_id),
  KEY idx_membresia_sede_sede (sede_id),
  CONSTRAINT fk_membresia_sede_membresia FOREIGN KEY (membresia_id) REFERENCES membresia(id) ON DELETE CASCADE,
  CONSTRAINT fk_membresia_sede_sede FOREIGN KEY (sede_id) REFERENCES sede(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 6. asignacion_profesor
-- ============================================================================
CREATE TABLE IF NOT EXISTS asignacion_profesor (
  organizacion_id BIGINT NOT NULL,
  usuario_id BIGINT NOT NULL,
  grupo_id BIGINT NOT NULL,
  rol ENUM('TITULAR', 'AYUDANTE') NOT NULL,
  PRIMARY KEY (usuario_id, grupo_id),
  KEY idx_asignacion_profesor_grupo (grupo_id),
  KEY idx_asignacion_profesor_organizacion (organizacion_id),
  CONSTRAINT fk_asignacion_profesor_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_asignacion_profesor_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
  CONSTRAINT fk_asignacion_profesor_grupo FOREIGN KEY (grupo_id) REFERENCES grupo(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 7. usuario.es_admin_plataforma (nadie marcado)
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'es_admin_plataforma'),
        'SELECT ''usuario.es_admin_plataforma already exists''',
        'ALTER TABLE usuario ADD COLUMN es_admin_plataforma BOOLEAN NOT NULL DEFAULT FALSE'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 8. usuario_alumno y relleno (R1 + R2, una sola sentencia)
-- ============================================================================
CREATE TABLE IF NOT EXISTS usuario_alumno (
  usuario_id BIGINT NOT NULL,
  alumno_id BIGINT NOT NULL,
  -- La organización del alumno (desnormalizada para consultar sin la sesión de Hibernate).
  organizacion_id BIGINT NOT NULL,
  relacion ENUM('PROPIO', 'TUTOR') NOT NULL,
  origen ENUM('MIGRACION', 'REGISTRO', 'MANUAL') NOT NULL,
  creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (usuario_id, alumno_id),
  KEY idx_usuario_alumno_alumno (alumno_id),
  KEY idx_usuario_alumno_organizacion_usuario (organizacion_id, usuario_id),
  CONSTRAINT fk_usuario_alumno_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
  CONSTRAINT fk_usuario_alumno_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE CASCADE,
  CONSTRAINT fk_usuario_alumno_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO usuario_alumno (usuario_id, alumno_id, organizacion_id, relacion, origen)
SELECT v.usuario_id,
       a.id,
       a.organizacion_id,
       IF(a.fecha_nacimiento IS NOT NULL AND TIMESTAMPDIFF(YEAR, a.fecha_nacimiento, CURDATE()) >= 18,
          'PROPIO', 'TUTOR'),
       'MIGRACION'
FROM (
  -- R1: vínculo directo usuario.alumno_id
  SELECT u.id AS usuario_id, x.id AS alumno_id
  FROM usuario u
  JOIN alumno x ON x.id = u.alumno_id
  WHERE (x.email IS NOT NULL AND LOWER(x.email) = LOWER(TRIM(u.email))
         AND EXISTS (SELECT 1 FROM membresia m
                     WHERE m.usuario_id = u.id
                       AND m.organizacion_id = x.organizacion_id))
     OR EXISTS (SELECT 1 FROM membresia m
                WHERE m.usuario_id = u.id
                  AND m.organizacion_id = x.organizacion_id
                  AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR'))
  UNION
  -- R2: email del alumno = email de una cuenta con membresía en la escuela del alumno
  SELECT u.id AS usuario_id, x.id AS alumno_id
  FROM alumno x
  JOIN usuario u ON LOWER(x.email) = LOWER(TRIM(u.email))
  WHERE x.email IS NOT NULL
    AND TRIM(u.email) <> ''
    AND EXISTS (SELECT 1 FROM membresia m
                WHERE m.usuario_id = u.id
                  AND m.organizacion_id = x.organizacion_id)
) v
JOIN alumno a ON a.id = v.alumno_id
WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua
                  WHERE ua.usuario_id = v.usuario_id AND ua.alumno_id = v.alumno_id);

-- ============================================================================
-- 9. usuario_rol: FK hacia usuario con ON DELETE CASCADE
-- ============================================================================
-- Se busca la FK por su definición (no por nombre: el de V1 lo generó Hibernate). Una sola sentencia cambia
-- la FK antigua por la nueva; si un fallo dejara la tabla sin FK, la repetición la añade.
SET @v31_fk_sin_cascada = (
    SELECT MIN(rc.CONSTRAINT_NAME)
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = @schema_name AND rc.TABLE_NAME = 'usuario_rol'
      AND rc.REFERENCED_TABLE_NAME = 'usuario' AND rc.DELETE_RULE <> 'CASCADE'
);
SET @v31_fk_con_cascada = (
    SELECT COUNT(*)
    FROM information_schema.REFERENTIAL_CONSTRAINTS rc
    WHERE rc.CONSTRAINT_SCHEMA = @schema_name AND rc.TABLE_NAME = 'usuario_rol'
      AND rc.REFERENCED_TABLE_NAME = 'usuario' AND rc.DELETE_RULE = 'CASCADE'
);
SET @v31_existe_usuario_rol = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'usuario_rol'
);
SET @sql = CASE
    WHEN @v31_existe_usuario_rol = 0 THEN 'SELECT ''usuario_rol does not exist'''
    WHEN @v31_fk_sin_cascada IS NOT NULL AND @v31_fk_con_cascada > 0
        THEN CONCAT('ALTER TABLE usuario_rol DROP FOREIGN KEY `', @v31_fk_sin_cascada, '`')
    WHEN @v31_fk_sin_cascada IS NOT NULL
        THEN CONCAT('ALTER TABLE usuario_rol DROP FOREIGN KEY `', @v31_fk_sin_cascada, '`, ',
                    'ADD CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE')
    WHEN @v31_fk_con_cascada > 0 THEN 'SELECT ''usuario_rol FK already ON DELETE CASCADE'''
    ELSE 'ALTER TABLE usuario_rol ADD CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
