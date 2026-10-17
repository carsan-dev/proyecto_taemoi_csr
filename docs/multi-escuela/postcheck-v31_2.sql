-- Comprobación posterior de V31_2 (sede principal de los alumnos). Solo lectura y solo recuentos: no devuelve
-- emails, nombres ni identificadores de alumnos.
--
-- Debe ser 0:
--   alumnos_sin_sede_escuelas_una_sede   alumnos con sede_principal_id NULL en escuelas con una sola sede (en
--                                        Moiskimdo, organización 1, las altas posteriores a V31)
-- Informativa:
--   alumnos_sin_sede_escuelas_varias     alumnos con sede_principal_id NULL en escuelas con varias sedes o ninguna
--                                        (V31_2 no las toca: la sede la elige la gestión)
SELECT
  (SELECT COUNT(*)
     FROM alumno a
     JOIN (SELECT organizacion_id FROM sede GROUP BY organizacion_id HAVING COUNT(*) = 1) unica
       ON unica.organizacion_id = a.organizacion_id
    WHERE a.sede_principal_id IS NULL)
    AS alumnos_sin_sede_escuelas_una_sede,
  (SELECT COUNT(*)
     FROM alumno a
    WHERE a.sede_principal_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM sede s WHERE s.organizacion_id = a.organizacion_id
                      GROUP BY s.organizacion_id HAVING COUNT(*) = 1))
    AS alumnos_sin_sede_escuelas_varias;
