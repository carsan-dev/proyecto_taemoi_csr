-- Multi-escuela, fase 2 (ampliar): personas. Ver docs/multi-escuela/modelo-datos.md
-- (decisión 7, tablas tutor/alumno_salud/consentimiento y la fila de alumno).
--
--   1. alumno: genero, localidad, codigo_postal (NULL). telefono/telefono2 pasan de INT a
--      VARCHAR(20) conservando los dígitos (MySQL convierte 600111222 -> '600111222'; los ceros a
--      la izquierda ya se perdieron al guardarse como INT) y telefono admite NULL (fichas en papel
--      sin teléfono; la obligatoriedad se valida en el alta, no en la base).
--   2. tutor: una fila por alumno con responsable_legal_nombre (parentesco TUTOR_LEGAL,
--      responsable_legal = TRUE, nombre = el nombre completo guardado, nif = responsable_legal_nif).
--      Las columnas responsable_legal_* de alumno se conservan hasta la contracción y el código las
--      mantiene sincronizadas con el primer tutor responsable legal.
--   3. alumno_salud: vacía. Los textos se guardan cifrados (AES-GCM) por la aplicación.
--   4. consentimiento: solo se añaden filas (una retirada es una fila nueva con otorgado = FALSE).
--      Se rellena desde las preinscripciones FINALIZADAS vinculadas a un alumno:
--        consentimiento_fotografico -> USO_IMAGEN, aceptacion_normas -> NORMAS,
--      con fecha = día de la firma en Europe/Madrid (creada_en está en UTC), firmante = firmante_nombre y relación TUTOR si el
--      alumno era menor de 18 años al firmar. alumno.autorizacion_web NO se toca aquí: la
--      finalización ya la fijó con el mismo valor y después pudo editarse a mano.
--
-- Claves ajenas a alumno con ON DELETE CASCADE: el borrado de un alumno arrastra sus tutores,
-- salud y consentimientos (como hoy hace la aplicación con el resto de su ficha). La de
-- preinscripción es ON DELETE SET NULL y la de documento, restrictiva.
-- Todas las sentencias son repetibles sin efectos duplicados.

SET @schema_name = DATABASE();

-- ----------------------------------------------------------------------------
-- 1. alumno: columnas nuevas
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'genero'
        ),
        'SELECT ''alumno.genero already exists''',
        'ALTER TABLE alumno ADD COLUMN genero ENUM(''FEMENINO'', ''MASCULINO'') NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'localidad'
        ),
        'SELECT ''alumno.localidad already exists''',
        'ALTER TABLE alumno ADD COLUMN localidad VARCHAR(100) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'codigo_postal'
        ),
        'SELECT ''alumno.codigo_postal already exists''',
        'ALTER TABLE alumno ADD COLUMN codigo_postal VARCHAR(10) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 1b. alumno: teléfonos INT -> VARCHAR(20) NULL (una sola reconstrucción de la tabla)
-- ----------------------------------------------------------------------------
SET @sql = (
    SELECT IF(
        (
            SELECT COUNT(*)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME IN ('telefono', 'telefono2')
              AND DATA_TYPE = 'varchar'
              AND CHARACTER_MAXIMUM_LENGTH = 20
              AND IS_NULLABLE = 'YES'
        ) = 2,
        'SELECT ''alumno.telefono/telefono2 already VARCHAR(20) NULL''',
        'ALTER TABLE alumno
           MODIFY COLUMN telefono VARCHAR(20) NULL,
           MODIFY COLUMN telefono2 VARCHAR(20) NULL DEFAULT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 2. tutor
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tutor (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  alumno_id BIGINT NOT NULL,
  nombre VARCHAR(180) NOT NULL,
  apellidos VARCHAR(160) NULL,
  nif VARCHAR(16) NULL,
  parentesco ENUM('PADRE', 'MADRE', 'TUTOR_LEGAL', 'OTRO') NOT NULL DEFAULT 'OTRO',
  telefono VARCHAR(20) NULL,
  email VARCHAR(180) NULL,
  responsable_legal BOOLEAN NOT NULL DEFAULT FALSE,
  orden INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tutor_organizacion (organizacion_id),
  KEY idx_tutor_alumno (alumno_id, orden),
  CONSTRAINT fk_tutor_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_tutor_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Un tutor responsable legal por alumno con nombre guardado (los que solo tienen NIF se quedan en
-- las columnas heredadas: tutor.nombre es obligatorio).
INSERT INTO tutor (organizacion_id, alumno_id, nombre, apellidos, nif, parentesco, telefono, email,
                   responsable_legal, orden)
SELECT a.organizacion_id, a.id, TRIM(a.responsable_legal_nombre), NULL,
       NULLIF(UPPER(TRIM(a.responsable_legal_nif)), ''), 'TUTOR_LEGAL', NULL, NULL, TRUE, 1
FROM alumno a
WHERE a.responsable_legal_nombre IS NOT NULL
  AND TRIM(a.responsable_legal_nombre) <> ''
  AND NOT EXISTS (
    SELECT 1 FROM tutor t WHERE t.alumno_id = a.id AND t.responsable_legal = TRUE
  );

-- ----------------------------------------------------------------------------
-- 3. alumno_salud (vacía)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alumno_salud (
  alumno_id BIGINT NOT NULL,
  organizacion_id BIGINT NOT NULL,
  lesion_enfermedad BOOLEAN NULL,
  descripcion TEXT NULL,
  impide_actividad BOOLEAN NULL,
  impide_detalle TEXT NULL,
  alergias TEXT NULL,
  origen ENUM('FICHA_PAPEL', 'PREINSCRIPCION', 'APP') NOT NULL,
  actualizado_en DATETIME(6) NOT NULL,
  PRIMARY KEY (alumno_id),
  KEY idx_alumno_salud_organizacion (organizacion_id),
  CONSTRAINT fk_alumno_salud_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_alumno_salud_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------------------------------------------
-- 4. consentimiento (solo se añaden filas)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS consentimiento (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  alumno_id BIGINT NOT NULL,
  tipo ENUM('NORMAS', 'PRIMEROS_AUXILIOS', 'USO_IMAGEN', 'COMPROMISO_PAGO', 'PROTECCION_DATOS') NOT NULL,
  otorgado BOOLEAN NOT NULL,
  fecha DATE NULL,
  fecha_aproximada BOOLEAN NOT NULL DEFAULT FALSE,
  firmante_nombre VARCHAR(180) NULL,
  firmante_relacion ENUM('PROPIO', 'TUTOR') NULL,
  origen ENUM('FICHA_PAPEL', 'PREINSCRIPCION', 'APP') NOT NULL,
  documento_id BIGINT NULL,
  preinscripcion_id BIGINT NULL,
  registrado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_consentimiento_organizacion (organizacion_id),
  KEY idx_consentimiento_alumno_tipo (alumno_id, tipo),
  KEY idx_consentimiento_documento (documento_id),
  -- Una fila por tipo y preinscripción: la finalización y este relleno no se duplican.
  UNIQUE KEY uk_consentimiento_preinscripcion_tipo (preinscripcion_id, tipo),
  CONSTRAINT fk_consentimiento_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_consentimiento_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE CASCADE,
  CONSTRAINT fk_consentimiento_documento FOREIGN KEY (documento_id) REFERENCES documento(id),
  -- Si se borra la preinscripción (hoy solo las canceladas, que no dejan consentimientos), el
  -- consentimiento se conserva sin el enlace.
  CONSTRAINT fk_consentimiento_preinscripcion FOREIGN KEY (preinscripcion_id) REFERENCES preinscripcion(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- creada_en es un Instant que Hibernate guarda en UTC (DATETIME sin zona; comprobado en
-- PersonasAlumnoIntegrationTest.losInstantesSeGuardanEnUtc). La fecha de la firma es el día en
-- Europe/Madrid: CONVERT_TZ si el servidor tiene cargadas las zonas horarias; si no (devuelve NULL, como
-- en la imagen mysql:8.0 por defecto), +1 h en invierno y +2 h en verano (último domingo de marzo a
-- último domingo de octubre, 01:00 UTC).
INSERT INTO consentimiento (organizacion_id, alumno_id, tipo, otorgado, fecha, fecha_aproximada,
                            firmante_nombre, firmante_relacion, origen, documento_id, preinscripcion_id)
SELECT a.organizacion_id, a.id, c.tipo,
       IF(c.tipo = 'USO_IMAGEN', IF(p.consentimiento_fotografico = 1, 1, 0),
                                 IF(p.aceptacion_normas = 1, 1, 0)),
       p.fecha_firma, FALSE, p.firmante_nombre,
       IF(TIMESTAMPDIFF(YEAR, p.fecha_nacimiento, p.fecha_firma) < 18, 'TUTOR', 'PROPIO'),
       'PREINSCRIPCION', NULL, p.id
FROM (
  SELECT x.id, x.alumno_id, x.consentimiento_fotografico, x.aceptacion_normas, x.firmante_nombre,
         x.fecha_nacimiento,
         DATE(COALESCE(
           CONVERT_TZ(x.creada_en, '+00:00', 'Europe/Madrid'),
           x.creada_en + INTERVAL IF(
             x.creada_en >= (LAST_DAY(CONCAT(YEAR(x.creada_en), '-03-01'))
                             - INTERVAL (DAYOFWEEK(LAST_DAY(CONCAT(YEAR(x.creada_en), '-03-01'))) - 1) DAY)
                            + INTERVAL 1 HOUR
             AND x.creada_en < (LAST_DAY(CONCAT(YEAR(x.creada_en), '-10-01'))
                                - INTERVAL (DAYOFWEEK(LAST_DAY(CONCAT(YEAR(x.creada_en), '-10-01'))) - 1) DAY)
                               + INTERVAL 1 HOUR,
             2, 1) HOUR)) AS fecha_firma
  FROM preinscripcion x
  WHERE x.estado = 'FINALIZADA'
    AND x.alumno_id IS NOT NULL
) p
JOIN alumno a ON a.id = p.alumno_id
CROSS JOIN (
  SELECT 'NORMAS' AS tipo
  UNION ALL SELECT 'USO_IMAGEN'
) c
WHERE NOT EXISTS (
    SELECT 1 FROM consentimiento x WHERE x.preinscripcion_id = p.id AND x.tipo = c.tipo
  );
