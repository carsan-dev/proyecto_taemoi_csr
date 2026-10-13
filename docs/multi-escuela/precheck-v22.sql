-- Read-only, counts only. Run on production BEFORE deploying V22.
-- "perdido" = legacy alumno column has a value and NO alumno_deporte row of that alumno carries one.
SELECT
  (SELECT COUNT(*) FROM alumno a WHERE NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id)) AS sin_alumno_deporte,
  SUM(a.deporte IS NOT NULL AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.deporte=a.deporte)) AS deporte_no_esta_en_ad,
  SUM(a.grado_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.deporte=a.deporte AND d.grado_id IS NOT NULL)) AS grado_perdido,
  SUM(a.tipo_tarifa IS NOT NULL AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.tipo_tarifa IS NOT NULL)) AS tipo_tarifa_perdido,
  SUM(a.cuantia_tarifa IS NOT NULL AND (SELECT COUNT(*) FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.cuantia_tarifa IS NULL) > 0) AS cuantia_a_rellenar,
  SUM(a.cuantia_tarifa IS NOT NULL AND (SELECT COUNT(*) FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.cuantia_tarifa IS NULL) > 0
      AND (SELECT COUNT(*) FROM alumno_deporte d WHERE d.alumno_id=a.id) > 1) AS cuantia_a_rellenar_multideporte,
  SUM(a.rol_familiar IS NOT NULL AND a.rol_familiar<>'NINGUNO' AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.rol_familiar IS NOT NULL AND d.rol_familiar<>'NINGUNO')) AS rol_familiar_perdido,
  SUM(COALESCE(a.grupo_familiar,'')<>'' AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND COALESCE(d.grupo_familiar,'')<>'')) AS grupo_familiar_perdido,
  SUM(a.tiene_licencia=1 AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.tiene_licencia=1)) AS licencia_perdida,
  SUM(a.numero_licencia IS NOT NULL AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.numero_licencia IS NOT NULL)) AS numero_licencia_perdido,
  SUM(a.fecha_licencia IS NOT NULL AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.fecha_licencia IS NOT NULL)) AS fecha_licencia_perdida,
  SUM(a.competidor=1 AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.competidor=1)) AS competidor_perdido,
  SUM(a.peso IS NOT NULL AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.peso IS NOT NULL)) AS peso_perdido
FROM alumno a;

-- Unlinked charges whose PDF sport attribution can change (was alumno.deporte, now concepto -> principal)
SELECT COUNT(*) AS cargos_sin_deporte_reatribuibles
FROM producto_alumno pa JOIN alumno a ON a.id = pa.alumno_id
WHERE pa.alumno_deporte_id IS NULL
  AND UPPER(pa.concepto) NOT REGEXP 'KICKBOXING|TAEKWONDO|PILATES|DEFENSA'
  AND a.deporte IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM alumno_deporte d WHERE d.alumno_id=a.id AND d.principal=1 AND d.activo=1 AND d.deporte=a.deporte);
