-- Comprobación previa de V28 (contracción del catálogo). Solo lectura y solo recuentos: no devuelve
-- datos personales. Ejecutar ANTES de desplegar V28 (con el código de V26/V27 en producción) y guardar
-- el resultado.
--
-- Las siete primeras columnas deben ser 0: son las filas en las que una columna heredada no coincide con
-- su equivalente del catálogo. V28 hace la misma comprobación y, si alguna no es 0, se detiene ANTES de
-- cambiar nada con el error «Table '…v28_<tabla>_<columna>_no_coincide_con_el_catalogo' doesn't exist».
-- alumno_deporte_disciplina_repetida y plantilla_version_repetida también deben ser 0: si no, falla la
-- creación de los únicos por disciplina (tampoco se habrá borrado nada).
-- categorias_por_nombre_sin_taekwondo es informativa: categorías con los nombres que el código asignaba
-- por edad en una organización sin disciplina TAEKWONDO (quedan sin disciplina ni rango).
SELECT
  (SELECT COUNT(*) FROM alumno_deporte x LEFT JOIN disciplina d ON d.id = x.disciplina_id
    WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
       OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin)
    AS alumno_deporte_deporte_diferente,
  (SELECT COUNT(*) FROM alumno_deporte x LEFT JOIN tarifa t ON t.id = x.tarifa_id
    WHERE x.tipo_tarifa IS NOT NULL
      AND (t.id IS NULL OR t.organizacion_id <> x.organizacion_id
           OR CONVERT(t.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.tipo_tarifa USING utf8mb4) COLLATE utf8mb4_bin))
    AS alumno_deporte_tipo_tarifa_diferente,
  (SELECT COUNT(*) FROM grupo x LEFT JOIN disciplina d ON d.id = x.disciplina_id
    WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
       OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin)
    AS grupo_deporte_diferente,
  (SELECT COUNT(*) FROM convocatoria x LEFT JOIN disciplina d ON d.id = x.disciplina_id
    WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
       OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin)
    AS convocatoria_deporte_diferente,
  (SELECT COUNT(*) FROM plantilla_preinscripcion x LEFT JOIN disciplina d ON d.id = x.disciplina_id
    WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
       OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin)
    AS plantilla_deporte_diferente,
  (SELECT COUNT(*) FROM preinscripcion x LEFT JOIN disciplina d ON d.id = x.disciplina_id
    WHERE d.id IS NULL OR d.organizacion_id <> x.organizacion_id
       OR CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin)
    AS preinscripcion_deporte_diferente,
  (SELECT COUNT(*) FROM grado
    WHERE codigo IS NULL
       OR CONVERT(codigo USING utf8mb4) COLLATE utf8mb4_bin <> CONVERT(tipo_grado USING utf8mb4) COLLATE utf8mb4_bin)
    AS grado_codigo_diferente,
  (SELECT COUNT(*) FROM (SELECT alumno_id, disciplina_id FROM alumno_deporte
                         GROUP BY alumno_id, disciplina_id HAVING COUNT(*) > 1) r)
    AS alumno_deporte_disciplina_repetida,
  (SELECT COUNT(*) FROM (SELECT organizacion_id, disciplina_id, version FROM plantilla_preinscripcion
                         GROUP BY organizacion_id, disciplina_id, version HAVING COUNT(*) > 1) r)
    AS plantilla_version_repetida,
  (SELECT COUNT(*) FROM categoria c
    WHERE c.nombre IN ('Infantil', 'Precadete', 'Cadete', 'Junior', 'Senior')
      AND NOT EXISTS (SELECT 1 FROM disciplina d WHERE d.organizacion_id = c.organizacion_id AND d.codigo = 'TAEKWONDO'))
    AS categorias_por_nombre_sin_taekwondo;
