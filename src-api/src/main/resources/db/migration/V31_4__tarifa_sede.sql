-- Tarifa por sede (entrega 6-4, docs/multi-escuela/entrega6-plan.md §4.4; decisiones D6-27 a D6-36).
--
-- SOLO AÑADE una tabla nueva, vacía: el código anterior sigue funcionando sobre este esquema (no la conoce) y, con la
-- tabla vacía, el código nuevo cobra exactamente lo mismo que antes (la cuantía de la inscripción). Sin datos: la fila
-- de una sede se crea con la API (POST /api/tarifas-sede) ANTES de generar el primer mes en que aplica. Sin guarda
-- (como V31_2 y V31_3) y repetible (IF NOT EXISTS). Va como V31_4 porque V32 está reservada para la contracción
-- multi-escuela.
--
-- Una fila es la excepción de una tarifa del catálogo en una sede desde un mes: vigente_desde es SIEMPRE el día 1
-- (D6-34, lo valida el servicio) y no hay vigente_hasta (D6-36). Para un mes vale la fila con el mayor vigente_desde
-- menor o igual que el día 1 de ese mes. importe_adulto / importe_menor siguen la regla de rol de tarifa.
-- Las FK son simples, como en V31 y V31_3: la coherencia de organización entre tarifa y sede la valida el servicio.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v31_4.sql.

CREATE TABLE IF NOT EXISTS tarifa_sede (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organizacion_id BIGINT NOT NULL,
  tarifa_id BIGINT NOT NULL,
  sede_id BIGINT NOT NULL,
  importe DECIMAL(8,2) NOT NULL,
  importe_adulto DECIMAL(8,2) NULL,
  importe_menor DECIMAL(8,2) NULL,
  -- Día 1 del mes desde el que aplica (validado en el servicio).
  vigente_desde DATE NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tarifa_sede_vigencia (organizacion_id, tarifa_id, sede_id, vigente_desde),
  KEY idx_tarifa_sede_sede (sede_id),
  CONSTRAINT fk_tarifa_sede_organizacion FOREIGN KEY (organizacion_id) REFERENCES organizacion(id),
  CONSTRAINT fk_tarifa_sede_tarifa FOREIGN KEY (tarifa_id) REFERENCES tarifa(id),
  CONSTRAINT fk_tarifa_sede_sede FOREIGN KEY (sede_id) REFERENCES sede(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
