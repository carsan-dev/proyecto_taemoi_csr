-- Registro de cobros y tablas de caja (pieza 6b-1, docs/multi-escuela/entrega6-plan.md §7).
--
-- SOLO AÑADE tres tablas nuevas y VACÍAS; no toca ninguna tabla existente. El código anterior sigue funcionando sobre
-- este esquema (no las conoce). Sin relleno: los cargos ya pagados antes de esta versión no tienen fila de cobro.
-- producto_alumno.pagado y producto_alumno.fecha_pago se siguen escribiendo igual: son el resumen que lee la
-- tesorería. Va como V31_5 porque V32 está reservada para la contracción multi-escuela.
--
--   entrega_caja     dinero que se entrega de una sede (quien entrega, quien recibe, declarado y contado). La usa la 6b-2.
--   cobro            un cobro de un cargo (producto_alumno): quién cobró, método, importe y fecha del cobro, origen
--                    (TESORERIA, CONVOCATORIA; PROFESOR en la 6b-2), estado (ACTIVO, ANULADO) y entrega en la que va.
--                    Como mucho UN cobro ACTIVO por cargo: columna generada producto_alumno_activo_id (el cargo si el
--                    cobro está ACTIVO, NULL si no) con clave única. Es VIRTUAL: MySQL no admite ON DELETE SET NULL en
--                    la columna base de una columna generada STORED.
--   movimiento_caja  sobrantes, faltantes, devoluciones y errores de caja (importe con signo). La usa la 6b-2.
--
-- TODAS las FK a usuario (también quién entrega, quién recibe y quién crea un movimiento) y la de alumno admiten NULL y
-- son ON DELETE SET NULL: borrar una cuenta (UsuarioServiceImpl, AlumnoServiceImpl#eliminarAlumno) o un alumno (que
-- borra también sus cargos) no debe fallar por la caja. Que quien entrega, recibe o crea sea obligatorio lo exigirá el
-- servicio de la 6b-2 al crearlos. La FK del cargo es ON DELETE SET NULL por lo mismo; el servicio anula el cobro activo
-- antes de borrar el cargo (CobroService). Solo organización y sede son obligatorias y sin acción: una sede con caja
-- no se borra.
-- Repetible: cada CREATE TABLE es atómico en MySQL 8 y lleva IF NOT EXISTS; tras un fallo a medias, `flyway repair` y
-- volver a desplegar crea solo las que falten. Sin guarda (como V31_3 y V31_4).
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v31_5.sql.

CREATE TABLE IF NOT EXISTS entrega_caja (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  sede_id BIGINT NOT NULL,
  entregado_por_usuario_id BIGINT NULL,
  recibido_por_usuario_id BIGINT NULL,
  importe_declarado DECIMAL(10,2) NOT NULL,
  importe_contado DECIMAL(10,2) NOT NULL,
  fecha DATETIME(6) NOT NULL,
  nota VARCHAR(500) NULL,
  -- ACTIVA, ANULADA
  estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
  anulada_por_usuario_id BIGINT NULL,
  anulada_en DATETIME(6) NULL,
  motivo_anulacion VARCHAR(500) NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_entrega_caja_sede_estado (organizacion_id, sede_id, estado),
  KEY idx_entrega_caja_sede (sede_id),
  KEY idx_entrega_caja_entregado_por (entregado_por_usuario_id),
  KEY idx_entrega_caja_recibido_por (recibido_por_usuario_id),
  KEY idx_entrega_caja_anulada_por (anulada_por_usuario_id),
  CONSTRAINT fk_entrega_caja_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_entrega_caja_sede FOREIGN KEY (sede_id) REFERENCES sede(id),
  CONSTRAINT fk_entrega_caja_entregado_por FOREIGN KEY (entregado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_entrega_caja_recibido_por FOREIGN KEY (recibido_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_entrega_caja_anulada_por FOREIGN KEY (anulada_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cobro (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  -- El cargo cobrado; NULL si el cargo se borró.
  producto_alumno_id BIGINT NULL,
  -- Copiada del cargo al cobrar.
  sede_id BIGINT NULL,
  -- Instantánea del alumno del cargo: COALESCE(producto_alumno.alumno_id, alumno_deporte.alumno_id).
  alumno_id BIGINT NULL,
  -- Instantánea del concepto del cargo.
  concepto VARCHAR(255) NULL,
  -- Lo que la tesorería suma para el cargo: COALESCE(precio, 0).
  importe DECIMAL(10,2) NOT NULL,
  -- EFECTIVO, TRANSFERENCIA, TARJETA, BIZUM, SIN_INDICAR
  metodo VARCHAR(20) NOT NULL DEFAULT 'SIN_INDICAR',
  -- Mismo tipo y valor que producto_alumno.fecha_pago.
  fecha_cobro DATETIME(6) NOT NULL,
  cobrado_por_usuario_id BIGINT NULL,
  -- TESORERIA, CONVOCATORIA (PROFESOR, desde la 6b-2)
  origen VARCHAR(20) NOT NULL,
  -- ACTIVO, ANULADO
  estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',
  anulado_por_usuario_id BIGINT NULL,
  anulado_en DATETIME(6) NULL,
  motivo_anulacion VARCHAR(500) NULL,
  entrega_caja_id BIGINT NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  producto_alumno_activo_id BIGINT
    GENERATED ALWAYS AS (IF(estado = 'ACTIVO', producto_alumno_id, NULL)) VIRTUAL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cobro_activo_por_cargo (producto_alumno_activo_id),
  KEY idx_cobro_sede_estado (organizacion_id, sede_id, estado),
  KEY idx_cobro_producto_alumno (producto_alumno_id),
  KEY idx_cobro_entrega_caja (entrega_caja_id),
  KEY idx_cobro_sede (sede_id),
  KEY idx_cobro_alumno (alumno_id),
  KEY idx_cobro_cobrado_por (cobrado_por_usuario_id),
  KEY idx_cobro_anulado_por (anulado_por_usuario_id),
  CONSTRAINT fk_cobro_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_cobro_producto_alumno FOREIGN KEY (producto_alumno_id) REFERENCES producto_alumno(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_cobro_sede FOREIGN KEY (sede_id) REFERENCES sede(id) ON DELETE SET NULL,
  CONSTRAINT fk_cobro_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE SET NULL,
  CONSTRAINT fk_cobro_cobrado_por FOREIGN KEY (cobrado_por_usuario_id) REFERENCES usuario(id) ON DELETE SET NULL,
  CONSTRAINT fk_cobro_anulado_por FOREIGN KEY (anulado_por_usuario_id) REFERENCES usuario(id) ON DELETE SET NULL,
  CONSTRAINT fk_cobro_entrega_caja FOREIGN KEY (entrega_caja_id) REFERENCES entrega_caja(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS movimiento_caja (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  sede_id BIGINT NOT NULL,
  -- DEVOLUCION, ERROR, SOBRANTE, FALTANTE, OTRO
  tipo VARCHAR(20) NOT NULL,
  -- Con signo: positivo entra en caja, negativo sale.
  importe DECIMAL(10,2) NOT NULL,
  alumno_id BIGINT NULL,
  entrega_caja_id BIGINT NULL,
  nota VARCHAR(500) NOT NULL,
  creado_por_usuario_id BIGINT NULL,
  fecha DATETIME(6) NOT NULL,
  -- ACTIVO, ANULADO
  estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',
  anulado_por_usuario_id BIGINT NULL,
  anulado_en DATETIME(6) NULL,
  motivo_anulacion VARCHAR(500) NULL,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_movimiento_caja_sede_estado (organizacion_id, sede_id, estado),
  KEY idx_movimiento_caja_sede (sede_id),
  KEY idx_movimiento_caja_entrega_caja (entrega_caja_id),
  KEY idx_movimiento_caja_alumno (alumno_id),
  KEY idx_movimiento_caja_creado_por (creado_por_usuario_id),
  KEY idx_movimiento_caja_anulado_por (anulado_por_usuario_id),
  CONSTRAINT fk_movimiento_caja_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_movimiento_caja_sede FOREIGN KEY (sede_id) REFERENCES sede(id),
  CONSTRAINT fk_movimiento_caja_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(id) ON DELETE SET NULL,
  CONSTRAINT fk_movimiento_caja_entrega_caja FOREIGN KEY (entrega_caja_id) REFERENCES entrega_caja(id),
  CONSTRAINT fk_movimiento_caja_creado_por FOREIGN KEY (creado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL,
  CONSTRAINT fk_movimiento_caja_anulado_por FOREIGN KEY (anulado_por_usuario_id) REFERENCES usuario(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
