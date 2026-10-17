-- Precheck de la entrega 6 (solo lectura, SOLO RECUENTOS: sin nombres, emails ni DNI).
-- Preinscripciones cuyos turnos no se pueden resolver (sin turnos propios, sin turno histórico y sin grupo con
-- turnos). Hoy el listado GET /api/preinscripciones responde 409 si una fila así cae en la página (comportamiento
-- anterior a la entrega 6, sin cambios). Esperado: 0 filas. Si sale alguna, lo decide el propietario.
SELECT p.organizacion_id, p.estado, COUNT(*) AS preinscripciones_sin_turnos_resolubles
FROM preinscripcion p
WHERE p.turno_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM preinscripcion_turno pt WHERE pt.preinscripcion_id = p.id)
  AND (p.grupo_id IS NULL OR NOT EXISTS (SELECT 1 FROM turno t WHERE t.grupo_id = p.grupo_id))
GROUP BY p.organizacion_id, p.estado
ORDER BY p.organizacion_id, p.estado;
