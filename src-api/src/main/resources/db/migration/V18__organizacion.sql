-- Multi-escuela, fase 1: organización (escuela), configuración por organización y contadores.
-- Taemoi pasa a ser la organización 1. No se incluyen datos personales ni legales
-- (firmante_*, cif...): se cargan fuera del repositorio.
-- Todas las sentencias son repetibles sin efectos duplicados.

CREATE TABLE IF NOT EXISTS organizacion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  slug VARCHAR(60) NOT NULL,
  nombre VARCHAR(150) NOT NULL,
  nombre_legal VARCHAR(200) NULL,
  cif VARCHAR(16) NULL,
  direccion VARCHAR(255) NULL,
  localidad VARCHAR(100) NULL,
  provincia VARCHAR(100) NULL,
  codigo_postal VARCHAR(10) NULL,
  email_contacto VARCHAR(180) NULL,
  telefono VARCHAR(20) NULL,
  web_url VARCHAR(255) NULL,
  logo_imagen_id BIGINT NULL,
  color_primario CHAR(7) NULL,
  email_remitente_nombre VARCHAR(100) NULL,
  firmante_nombre VARCHAR(180) NULL,
  firmante_cargo VARCHAR(100) NULL,
  firmante_nif VARCHAR(16) NULL,
  zona_horaria VARCHAR(40) NOT NULL DEFAULT 'Europe/Madrid',
  activa BOOLEAN NOT NULL DEFAULT TRUE,
  creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_organizacion_slug (slug),
  KEY idx_organizacion_logo_imagen (logo_imagen_id),
  CONSTRAINT fk_organizacion_logo_imagen FOREIGN KEY (logo_imagen_id) REFERENCES imagen(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO organizacion (id, slug, nombre)
SELECT 1, 'taemoi', 'Moi''s Kim Do'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM organizacion WHERE id = 1);

CREATE TABLE IF NOT EXISTS configuracion_organizacion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  clave VARCHAR(80) NOT NULL,
  valor VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configuracion_organizacion_clave (organizacion_id, clave),
  CONSTRAINT fk_configuracion_organizacion_organizacion
    FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Copia de configuracion_sistema (que se mantiene hasta la fase de contracción).
-- Si una clave aparece repetida se toma la fila más reciente (mayor id).
-- La sentencia es atómica: si la organización 1 ya tiene configuración, no se copia nada
-- (se evita comparar texto entre tablas con posibles colaciones distintas).
INSERT INTO configuracion_organizacion (organizacion_id, clave, valor)
SELECT 1, cs.clave, CAST(cs.valor AS CHAR)
FROM configuracion_sistema cs
JOIN (
  SELECT clave, MAX(id) AS id
  FROM configuracion_sistema
  GROUP BY clave
) ultima ON ultima.id = cs.id
WHERE NOT EXISTS (
  SELECT 1
  FROM configuracion_organizacion co
  WHERE co.organizacion_id = 1
);

CREATE TABLE IF NOT EXISTS contador (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  clave VARCHAR(40) NOT NULL,
  valor BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_contador_clave (organizacion_id, clave),
  CONSTRAINT fk_contador_organizacion
    FOREIGN KEY (organizacion_id) REFERENCES organizacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- El contador de expedientes parte del máximo actual: el siguiente alta recibe MAX + 1,
-- igual que con el cálculo anterior.
INSERT INTO contador (organizacion_id, clave, valor)
SELECT 1, 'numero_expediente', maximo.valor
FROM (
  SELECT COALESCE(MAX(numero_expediente), 0) AS valor
  FROM alumno
) maximo
WHERE NOT EXISTS (
  SELECT 1
  FROM contador c
  WHERE c.organizacion_id = 1
    AND c.clave = 'numero_expediente'
);
