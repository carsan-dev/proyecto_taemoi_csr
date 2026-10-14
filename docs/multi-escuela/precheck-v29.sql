-- Comprobación previa de V29 (contracción de personas). Solo lectura y solo recuentos: no devuelve
-- datos personales. Ejecutar ANTES de desplegar V29 (con alumno.responsable_legal_* todavía en la base)
-- y guardar el resultado.
--
-- Las tres primeras columnas deben ser 0. V29 hace las mismas comprobaciones y, si alguna no es 0, se
-- detiene ANTES de cambiar nada con «Table '…v29_<motivo>' doesn't exist»:
--   responsable_nombre_diferente  alumnos cuyo responsable_legal_nombre (no vacío) no es el nombre del
--                                 primer tutor responsable legal (por orden, id), sin espacios sobrantes
--                                 -> v29_responsable_legal_nombre_no_coincide_con_el_tutor
--   responsable_nif_diferente     ídem con responsable_legal_nif (no vacío; mayúsculas, sin espacios
--                                 exteriores, 00000000Z = sin NIF)
--                                 -> v29_responsable_legal_nif_no_coincide_con_el_tutor
--   tutores_sin_nombre_ni_nif     tutores que incumplirían la restricción nueva -> v29_tutor_sin_nombre_ni_nif
-- Informativas (lo que V29 hará):
--   alumnos_solo_nif_sin_tutor    sin tutor responsable legal y solo NIF heredado: reciben un tutor
--                                 TUTOR_LEGAL responsable con nombre NULL
--   alumnos_nombre_sin_tutor      sin tutor responsable legal y con nombre heredado: reciben un tutor
--                                 TUTOR_LEGAL responsable con ese nombre (y el NIF, si lo hay)
--   tutores_nombre_vacio          tutores con nombre vacío: pasa a NULL
SELECT
  (SELECT COUNT(*)
     FROM alumno a
     JOIN (SELECT t.alumno_id, t.nombre, t.apellidos,
                  ROW_NUMBER() OVER (PARTITION BY t.alumno_id ORDER BY t.orden, t.id) AS posicion
           FROM tutor t WHERE t.responsable_legal = TRUE) p
       ON p.alumno_id = a.id AND p.posicion = 1
    WHERE TRIM(REGEXP_REPLACE(COALESCE(a.responsable_legal_nombre, ''), '[[:space:]]+', ' ')) <> ''
      AND NOT (CONVERT(TRIM(REGEXP_REPLACE(a.responsable_legal_nombre, '[[:space:]]+', ' ')) USING utf8mb4) COLLATE utf8mb4_bin
               <=> CONVERT(NULLIF(TRIM(REGEXP_REPLACE(LEFT(TRIM(CONCAT(TRIM(COALESCE(p.nombre, '')), ' ',
                           TRIM(COALESCE(p.apellidos, '')))), 180), '[[:space:]]+', ' ')), '') USING utf8mb4) COLLATE utf8mb4_bin))
    AS responsable_nombre_diferente,
  (SELECT COUNT(*)
     FROM alumno a
     JOIN (SELECT t.alumno_id, t.nif,
                  ROW_NUMBER() OVER (PARTITION BY t.alumno_id ORDER BY t.orden, t.id) AS posicion
           FROM tutor t WHERE t.responsable_legal = TRUE) p
       ON p.alumno_id = a.id AND p.posicion = 1
    WHERE NULLIF(NULLIF(UPPER(TRIM(a.responsable_legal_nif)), ''), '00000000Z') IS NOT NULL
      AND NOT (CONVERT(NULLIF(NULLIF(UPPER(TRIM(a.responsable_legal_nif)), ''), '00000000Z') USING utf8mb4) COLLATE utf8mb4_bin
               <=> CONVERT(NULLIF(NULLIF(UPPER(TRIM(p.nif)), ''), '00000000Z') USING utf8mb4) COLLATE utf8mb4_bin))
    AS responsable_nif_diferente,
  (SELECT COUNT(*) FROM tutor
    WHERE TRIM(COALESCE(nombre, '')) = '' AND TRIM(COALESCE(nif, '')) = '')
    AS tutores_sin_nombre_ni_nif,
  (SELECT COUNT(*) FROM alumno a
    WHERE NOT EXISTS (SELECT 1 FROM tutor t WHERE t.alumno_id = a.id AND t.responsable_legal = TRUE)
      AND TRIM(REGEXP_REPLACE(COALESCE(a.responsable_legal_nombre, ''), '[[:space:]]+', ' ')) = ''
      AND NULLIF(NULLIF(UPPER(TRIM(a.responsable_legal_nif)), ''), '00000000Z') IS NOT NULL)
    AS alumnos_solo_nif_sin_tutor,
  (SELECT COUNT(*) FROM alumno a
    WHERE NOT EXISTS (SELECT 1 FROM tutor t WHERE t.alumno_id = a.id AND t.responsable_legal = TRUE)
      AND TRIM(REGEXP_REPLACE(COALESCE(a.responsable_legal_nombre, ''), '[[:space:]]+', ' ')) <> '')
    AS alumnos_nombre_sin_tutor,
  (SELECT COUNT(*) FROM tutor WHERE nombre IS NOT NULL AND TRIM(nombre) = '')
    AS tutores_nombre_vacio;
