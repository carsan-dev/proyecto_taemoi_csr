-- Comprobación de V26 (cambio de llamadores a disciplinas y grados). Solo lectura y solo recuentos:
-- no devuelve datos personales. Ejecutar antes de desplegar V26 y guardar el resultado; puede
-- repetirse después.

-- 1. Filas cuyo deporte no tiene disciplina en su organización. Debe ser 0 en las cinco tablas:
--    si no, V26 se detiene antes de poner disciplina_id NOT NULL.
SELECT 'alumno_deporte' AS tabla, COUNT(*) AS sin_disciplina
FROM alumno_deporte x
LEFT JOIN disciplina d ON d.organizacion_id = x.organizacion_id AND d.codigo = CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin
WHERE d.id IS NULL
UNION ALL
SELECT 'grupo', COUNT(*)
FROM grupo x
LEFT JOIN disciplina d ON d.organizacion_id = x.organizacion_id AND d.codigo = CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin
WHERE d.id IS NULL
UNION ALL
SELECT 'convocatoria', COUNT(*)
FROM convocatoria x
LEFT JOIN disciplina d ON d.organizacion_id = x.organizacion_id AND d.codigo = CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin
WHERE d.id IS NULL
UNION ALL
SELECT 'plantilla_preinscripcion', COUNT(*)
FROM plantilla_preinscripcion x
LEFT JOIN disciplina d ON d.organizacion_id = x.organizacion_id AND d.codigo = CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin
WHERE d.id IS NULL
UNION ALL
SELECT 'preinscripcion', COUNT(*)
FROM preinscripcion x
LEFT JOIN disciplina d ON d.organizacion_id = x.organizacion_id AND d.codigo = CONVERT(x.deporte USING utf8mb4) COLLATE utf8mb4_bin
WHERE d.id IS NULL;

-- 2. Inscripciones de disciplinas sin sistema de grados que tienen grado (V26 no las toca:
--    conservan el grado de taekwondo que muestran hoy).
SELECT deporte, COUNT(*) AS con_grado
FROM alumno_deporte
WHERE grado_id IS NOT NULL AND deporte IN ('PILATES', 'DEFENSA_PERSONAL_FEMENINA')
GROUP BY deporte;

-- 3. Inscripciones de kickboxing que V26 pasa al sistema de kickboxing (antes de V26) / que siguen
--    en otro sistema (después de V26, debe ser 0).
SELECT COUNT(*) AS kickboxing_fuera_de_su_sistema
FROM alumno_deporte ad
JOIN disciplina d ON d.organizacion_id = ad.organizacion_id AND d.codigo = CONVERT(ad.deporte USING utf8mb4) COLLATE utf8mb4_bin
JOIN grado g ON g.id = ad.grado_id
WHERE ad.deporte = 'KICKBOXING' AND (g.sistema_grados_id IS NULL OR g.sistema_grados_id <> d.sistema_grados_id);

-- 4. Productos de examen o recompensa sin grado asignado (promueven por su nombre: concepto exacto
--    o nombre de producto contenido en el concepto).
SELECT COUNT(*) AS productos_examen_o_recompensa_sin_grado
FROM producto
WHERE organizacion_id = 1
  AND grado_id IS NULL
  AND (concepto LIKE '%DERECHO A EXAMEN%' OR concepto LIKE '%RECOMPENSA%');
