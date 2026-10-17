-- Comprobación posterior de V31_3 (sede de los cargos de tesorería, entrega 6-3). Solo lectura y SOLO RECUENTOS:
-- no devuelve nombres, emails, conceptos, importes ni identificadores de alumnos o cargos.
--
-- Deben ser 0:
--   cargos_sin_sede_escuelas_una_sede      cargos con sede_id NULL en escuelas con una sola sede (en Moiskimdo,
--                                          organización 1, todos sus cargos quedan en «Umbrete»)
--   cargos_con_sede_de_otra_escuela        cargos cuya sede es de otra organización (integridad)
-- Informativas:
--   cargos_sin_sede_escuelas_varias        cargos con sede_id NULL en escuelas con varias sedes o ninguna (alumno sin
--                                          sede principal: solo la gestión los ve, en la fila «Sin sede»)
--   cargos_total                           todos los cargos (para comparar con el recuento previo al despliegue)
SELECT
  (SELECT COUNT(*)
     FROM producto_alumno pa
     JOIN (SELECT organizacion_id FROM sede GROUP BY organizacion_id HAVING COUNT(*) = 1) unica
       ON unica.organizacion_id = pa.organizacion_id
    WHERE pa.sede_id IS NULL)
    AS cargos_sin_sede_escuelas_una_sede,
  (SELECT COUNT(*)
     FROM producto_alumno pa
     JOIN sede s ON s.id = pa.sede_id
    WHERE s.organizacion_id <> pa.organizacion_id)
    AS cargos_con_sede_de_otra_escuela,
  (SELECT COUNT(*)
     FROM producto_alumno pa
    WHERE pa.sede_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM sede s WHERE s.organizacion_id = pa.organizacion_id
                      GROUP BY s.organizacion_id HAVING COUNT(*) = 1))
    AS cargos_sin_sede_escuelas_varias,
  (SELECT COUNT(*) FROM producto_alumno)
    AS cargos_total;

-- Por organización (solo recuentos): cargos por sede y sin sede. En Moiskimdo, una sola fila con sin_sede = 0.
SELECT pa.organizacion_id,
       COUNT(*) AS cargos,
       SUM(pa.sede_id IS NOT NULL) AS con_sede,
       SUM(pa.sede_id IS NULL) AS sin_sede,
       COUNT(DISTINCT pa.sede_id) AS sedes_distintas
FROM producto_alumno pa
GROUP BY pa.organizacion_id
ORDER BY pa.organizacion_id;
