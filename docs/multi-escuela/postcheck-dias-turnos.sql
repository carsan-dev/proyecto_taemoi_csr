-- Solo lectura (decisión 380): cuántos turnos hay por cada valor de dia_semana, para ver si existen variantes
-- («Miércoles» / «miercoles» / «MIÉRCOLES»…) y si hay turnos en sábado o domingo. Agregado, sin datos personales.
-- Ejecutar con el mysql de producción y --default-character-set=utf8mb4.
SELECT dia_semana, COUNT(*) AS turnos FROM turno GROUP BY dia_semana ORDER BY 1;
