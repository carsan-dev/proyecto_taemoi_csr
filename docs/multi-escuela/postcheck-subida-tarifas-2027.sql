-- Postcheck de V32_10 (subida de 2 euros de las tarifas de taekwondo y kickboxing de la organizacion 1, desde la
-- mensualidad de enero de 2027). Solo lectura. Tres consultas:
--   1. Tarifas de la organizacion 1 que suben, y PILATES (no cambia). Esperado: INFANTIL 30, ADULTO 32,
--      KICKBOXING 32, HERMANOS 28, ADULTO_GRUPO 22, INFANTIL_GRUPO 22, PADRES_HIJOS 0 / adulto 30 / menor 28 y
--      PILATES 30. Si alguna conserva el valor viejo, produccion no tenia el catalogo esperado y V32_10 no la toco
--      (ni a sus inscripciones).
--   2. Inscripciones de la organizacion 1 (activas o no) por tarifa, rol y cuantia, con su situacion frente al
--      catalogo ACTUAL con la regla de rol de TarifasService.calcularImporte: catalogo (precio nuevo), personalizada,
--      sin cuantia o sin tarifa. Una personalizada que ya valia el precio nuevo sale como catalogo.
--   3. La fila de V32_10 en flyway_schema_history (success = 1).
-- Sin literales con tildes: no hace falta --default-character-set=utf8mb4.

SELECT t.id, t.codigo, t.importe, t.importe_adulto, t.importe_menor, t.activa
FROM tarifa t
WHERE t.organizacion_id = 1
  AND t.codigo IN ('INFANTIL', 'ADULTO', 'KICKBOXING', 'HERMANOS', 'ADULTO_GRUPO', 'INFANTIL_GRUPO', 'PADRES_HIJOS',
                   'PILATES')
ORDER BY t.orden, t.id;

SELECT x.tarifa, x.rol_familiar, x.cuantia_tarifa, x.situacion, COUNT(*) AS inscripciones, SUM(x.activo) AS activas
FROM (
  SELECT t.codigo AS tarifa, ad.rol_familiar, ad.cuantia_tarifa, IF(ad.activo, 1, 0) AS activo,
         CASE
           WHEN t.id IS NULL THEN 'sin tarifa'
           WHEN ad.cuantia_tarifa IS NULL THEN 'sin cuantia'
           WHEN ad.cuantia_tarifa = CASE
                  WHEN ad.rol_familiar IN ('PADRE', 'MADRE') AND t.importe_adulto IS NOT NULL THEN t.importe_adulto
                  WHEN ad.rol_familiar IN ('HIJO', 'HIJA') AND t.importe_menor IS NOT NULL THEN t.importe_menor
                  ELSE t.importe
                END THEN 'catalogo'
           ELSE 'personalizada'
         END AS situacion
  FROM alumno_deporte ad
  LEFT JOIN tarifa t ON t.id = ad.tarifa_id
  WHERE ad.organizacion_id = 1
) x
GROUP BY x.tarifa, x.rol_familiar, x.cuantia_tarifa, x.situacion
ORDER BY x.tarifa, x.rol_familiar, x.cuantia_tarifa, x.situacion;

SELECT installed_rank, version, description, script, installed_on, execution_time, success
FROM flyway_schema_history
WHERE version = '32.10';
