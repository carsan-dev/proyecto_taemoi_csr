-- Comprobación de V27 (cambio de llamadores a tarifas y productos). Solo lectura y SOLO RECUENTOS:
-- no devuelve ids, nombres ni conceptos. Ejecutar en producción antes de desplegar V27 (después de
-- docs/multi-escuela/postcheck-v24.sql) y repetir después. Un recuento distinto de lo indicado es algo
-- que revisar a mano; ninguno detiene la migración.
--
--   inscripciones_sin_tarifa: tipo_tarifa sin tarifa_id. 0 después de V27 (si no, la organización no
--     tiene esa tarifa: el código sigue funcionando con tipo_tarifa).
--   inscripciones_tarifa_distinta: tarifa_id cuyo codigo no es tipo_tarifa. 0 después de V27.
--   inscripciones_activas_sin_cuantia: inscripciones activas sin cuantia_tarifa. Su mensualidad se
--     cobra sin importe, igual que antes de V27 (la cuantía de la tarifa no la sustituye).
--   productos_mensualidad_generales / productos_reserva_generales: productos del catálogo de ese tipo
--     sin disciplina. 1 en Taemoi (si 0, se usa el de concepto «MENSUALIDAD» / «RESERVA DE PLAZA»).
--   mensualidad_con_otro_nombre: productos de mensualidad cuyo concepto no es exactamente «MENSUALIDAD».
--     Informativo: con el código heredado MENSUALIDAD los cargos siguen llamándose «MENSUALIDAD <MES> <AÑO>»
--     (antes de V27, con el producto renombrado, la generación fallaba).
--   competicion_sin_producto: disciplinas que admiten competidores sin producto de competidor enlazado.
--     0 en Taemoi (si no, se busca por codigo, después por concepto y, si no hay, se crea).
--   competidor_con_otro_nombre: productos de competidor enlazados cuyo concepto no es
--     «TARIFA COMPETIDOR <DISCIPLINA>». Informativo: con los códigos heredados el cargo usa ese literal.
--   competidor_precio_no_es_20: productos de competidor enlazados con precio distinto de 20.00. Con los
--     códigos heredados se sigue cobrando 20 € (como antes); el precio del producto solo vale para códigos
--     propios. Si el dueño decide cobrar el precio del producto, este recuento dice a cuántos afecta.
--   licencias_sin_codigo: de las 8 licencias de Taemoi, las que no tienen producto con su codigo
--     (se usa el producto con el concepto de antes, si existe).
--   variantes_con_id_menor: productos sin clasificar cuyo concepto coincide, sin distinguir mayúsculas
--     ni acentos, con el de mensualidad, reserva de plaza, competidor o licencia, y con id MENOR que el
--     clasificado. El código sigue eligiéndolos como antes (findFirstByConcepto primero); informativo.
--   cargos_periodicos_solo_por_tipo / cargos_examen_solo_por_tipo / cargos_categoria_solo_por_tipo:
--     cargos de productos clasificados cuyo concepto (editado a mano) no reconoce la regla heredada
--     (prefijo MENSUALIDAD/TARIFA COMPETIDOR, «DERECHO A EXAMEN», categoría OTRO). Con los códigos
--     heredados de Taemoi tesorería y el derecho a examen siguen mirando SOLO el concepto, como antes;
--     estos recuentos dicen a cuántos cargos afectaría pasar a clasificar por el tipo del catálogo.
SELECT
  (SELECT COUNT(*) FROM alumno_deporte ad
   WHERE ad.organizacion_id = 1 AND ad.tipo_tarifa IS NOT NULL AND ad.tarifa_id IS NULL
  ) AS inscripciones_sin_tarifa,
  (SELECT COUNT(*) FROM alumno_deporte ad
   JOIN tarifa t ON t.id = ad.tarifa_id
   WHERE ad.organizacion_id = 1
     AND ad.tipo_tarifa IS NOT NULL
     AND CONVERT(t.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(ad.tipo_tarifa USING utf8mb4) COLLATE utf8mb4_bin
  ) AS inscripciones_tarifa_distinta,
  (SELECT COUNT(*) FROM alumno_deporte ad
   WHERE ad.organizacion_id = 1 AND ad.activo = 1 AND ad.cuantia_tarifa IS NULL
  ) AS inscripciones_activas_sin_cuantia,
  (SELECT COUNT(*) FROM producto p
   WHERE p.organizacion_id = 1 AND p.tipo = 'MENSUALIDAD' AND p.disciplina_id IS NULL
  ) AS productos_mensualidad_generales,
  (SELECT COUNT(*) FROM producto p
   WHERE p.organizacion_id = 1 AND p.tipo = 'RESERVA_PLAZA' AND p.disciplina_id IS NULL
  ) AS productos_reserva_generales,
  (SELECT COUNT(*) FROM producto p
   WHERE p.organizacion_id = 1 AND p.tipo = 'MENSUALIDAD'
     AND (p.concepto IS NULL
          OR CAST(CONVERT(p.concepto USING utf8mb4) AS BINARY) <> CAST(CONVERT('MENSUALIDAD' USING utf8mb4) AS BINARY))
  ) AS mensualidad_con_otro_nombre,
  (SELECT COUNT(*) FROM disciplina d
   WHERE d.organizacion_id = 1 AND d.permite_competicion = 1 AND d.producto_competidor_id IS NULL
  ) AS competicion_sin_producto,
  (SELECT COUNT(*) FROM disciplina d
   JOIN producto p ON p.id = d.producto_competidor_id
   WHERE d.organizacion_id = 1
     AND (p.concepto IS NULL
          OR CAST(CONVERT(p.concepto USING utf8mb4) AS BINARY)
             <> CAST(CONVERT(CONCAT('TARIFA COMPETIDOR ', d.codigo) USING utf8mb4) AS BINARY))
  ) AS competidor_con_otro_nombre,
  (SELECT COUNT(*) FROM disciplina d
   JOIN producto p ON p.id = d.producto_competidor_id
   WHERE d.organizacion_id = 1 AND (p.precio IS NULL OR p.precio <> 20.0)
  ) AS competidor_precio_no_es_20,
  (SELECT COUNT(*)
   FROM (
     SELECT 'LICENCIA_TAEKWONDO_ADULTO' AS codigo
     UNION ALL SELECT 'LICENCIA_TAEKWONDO_INFANTIL'
     UNION ALL SELECT 'LICENCIA_TAEKWONDO_DISCAPACIDAD'
     UNION ALL SELECT 'LICENCIA_TAEKWONDO_ADULTO_PROPORCIONAL'
     UNION ALL SELECT 'LICENCIA_TAEKWONDO_INFANTIL_PROPORCIONAL'
     UNION ALL SELECT 'LICENCIA_TAEKWONDO_DISCAPACIDAD_PROPORCIONAL'
     UNION ALL SELECT 'LICENCIA_KICKBOXING_ADULTO'
     UNION ALL SELECT 'LICENCIA_KICKBOXING_INFANTIL'
   ) e
   WHERE NOT EXISTS (
     SELECT 1 FROM producto p
     WHERE p.organizacion_id = 1
       AND CONVERT(p.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(e.codigo USING utf8mb4) COLLATE utf8mb4_bin
   )
  ) AS licencias_sin_codigo,
  (SELECT COUNT(*)
   FROM producto v
   JOIN producto c
     ON c.organizacion_id = v.organizacion_id
    AND c.tipo IN ('MENSUALIDAD', 'RESERVA_PLAZA', 'COMPETIDOR', 'LICENCIA')
    AND c.id > v.id
    AND TRIM(c.concepto) COLLATE utf8mb4_0900_ai_ci = TRIM(v.concepto) COLLATE utf8mb4_0900_ai_ci
   WHERE v.organizacion_id = 1 AND v.codigo IS NULL
  ) AS variantes_con_id_menor,
  (SELECT COUNT(*) FROM producto_alumno pa
   JOIN producto p ON p.id = pa.producto_id
   WHERE pa.organizacion_id = 1
     AND p.tipo IN ('MENSUALIDAD', 'COMPETIDOR')
     AND (pa.concepto IS NULL
          OR (UPPER(pa.concepto) NOT LIKE 'MENSUALIDAD%' AND UPPER(pa.concepto) NOT LIKE 'TARIFA COMPETIDOR%'))
  ) AS cargos_periodicos_solo_por_tipo,
  (SELECT COUNT(*) FROM producto_alumno pa
   JOIN producto p ON p.id = pa.producto_id
   WHERE pa.organizacion_id = 1
     AND p.tipo = 'EXAMEN'
     AND (pa.concepto IS NULL
          OR (CAST(CONVERT(pa.concepto USING utf8mb4) AS BINARY) NOT LIKE CAST('%DERECHO A EXAMEN%' AS BINARY)
              AND CAST(CONVERT(pa.concepto USING utf8mb4) AS BINARY) NOT LIKE CAST('%PASE DE GRADO POR RECOMPENSA%' AS BINARY)))
  ) AS cargos_examen_solo_por_tipo,
  (SELECT COUNT(*) FROM producto_alumno pa
   JOIN producto p ON p.id = pa.producto_id
   WHERE pa.organizacion_id = 1
     AND p.tipo <> 'OTRO'
     AND (pa.concepto IS NULL
          OR (UPPER(pa.concepto) NOT LIKE 'MENSUALIDAD%'
              AND UPPER(pa.concepto) NOT LIKE 'TARIFA COMPETIDOR%'
              AND UPPER(TRIM(LEADING ' ' FROM pa.concepto)) NOT LIKE 'MATR_CULA%'
              AND UPPER(pa.concepto) NOT LIKE '%LICENCIA%'
              AND UPPER(pa.concepto) NOT LIKE 'RESERVA DE PLAZA%'
              AND UPPER(pa.concepto) NOT LIKE '%EXAMEN%'
              AND UPPER(pa.concepto) NOT LIKE '%RECOMPENSA%'))
  ) AS cargos_categoria_solo_por_tipo;
