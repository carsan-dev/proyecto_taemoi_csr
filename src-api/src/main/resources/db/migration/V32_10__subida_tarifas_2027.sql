-- Subida de 2 € de las tarifas de taekwondo y kickboxing de la organización 1 (Moiskimdo), decidida por el propietario
-- para cobrarse desde la mensualidad de enero de 2027. La fecha la pone el despliegue (después de generar diciembre): la
-- migración no tiene lógica de fechas.
--
--   tarifa           antes (V24)                después
--   ---------------  -------------------------  -------------------------
--   INFANTIL         28                         30
--   ADULTO           30                         32
--   KICKBOXING       30                         32
--   HERMANOS         26                         28
--   ADULTO_GRUPO     20                         22
--   INFANTIL_GRUPO   20                         22
--   PADRES_HIJOS     0 / adulto 28 / menor 26   0 / adulto 30 / menor 28
--
-- No cambian FAMILIAR (0), PILATES, DEFENSA_PERSONAL_FEMENINA, los productos (tampoco el de competidor), los cargos ya
-- generados (producto_alumno, D6-32) ni las tarifas por sede (tarifa_sede).
--
-- Inscripciones (alumno_deporte.cuantia_tarifa, la cuantía que cobra el generador): sube solo la que tiene EXACTAMENTE
-- la cuantía de catálogo vieja de su tarifa con su rol, activa o no (para que una reactivación cobre el precio nuevo).
-- La de catálogo con su rol es la regla de TarifasService.calcularImporte (D6-27): PADRE/MADRE -> importe_adulto,
-- HIJO/HIJA -> importe_menor (si la tarifa los tiene); sin rol, NINGUNO o tarifa sin importes por rol -> importe. Así,
-- en PADRES_HIJOS el progenitor con 28 pasa a 30 y el hijo con 26 a 28; sin rol (importe 0) no cambia. Cualquier otra
-- cuantía es personalizada y se queda igual. Comparación exacta (sin redondeo). Sin tarifa: no se toca.
--
-- Alcance y seguridad: solo filas de la organización 1 (tarifa e inscripción). Cada tarifa se cambia, con sus
-- inscripciones, solo si su catálogo tiene exactamente los tres importes viejos de la tabla; si producción difiere en
-- alguno, esa tarifa y sus inscripciones no cambian (el postcheck lo enseña).
--
-- Orden: primero las inscripciones (comparan con el catálogo viejo), después el catálogo. Repetible: tras aplicarla
-- ningún catálogo tiene los importes viejos, así que no cambia nada; si fallara entre las dos sentencias (fuera de la
-- transacción de Flyway), repetirla completa el catálogo sin subir dos veces las inscripciones (ya no tienen la cuantía
-- vieja). Ni preinscripciones ni otras tablas guardan cuantía de mensualidad copiada del catálogo.
--
-- Código anterior: no cambia el esquema; la imagen anterior funciona igual. Vuelta atrás: las dos sentencias con
-- antes y después intercambiados. Comprobación posterior: docs/multi-escuela/postcheck-subida-tarifas-2027.sql.

UPDATE alumno_deporte ad
JOIN tarifa t
  ON t.id = ad.tarifa_id
 AND t.organizacion_id = 1
JOIN (
  SELECT 'INFANTIL' AS codigo,
         CAST(28.00 AS DECIMAL(8,2)) AS importe_antes, CAST(NULL AS DECIMAL(8,2)) AS adulto_antes,
         CAST(NULL AS DECIMAL(8,2)) AS menor_antes,
         CAST(30.00 AS DECIMAL(8,2)) AS importe_nuevo, CAST(NULL AS DECIMAL(8,2)) AS adulto_nuevo,
         CAST(NULL AS DECIMAL(8,2)) AS menor_nuevo
  UNION ALL SELECT 'ADULTO', 30.00, NULL, NULL, 32.00, NULL, NULL
  UNION ALL SELECT 'KICKBOXING', 30.00, NULL, NULL, 32.00, NULL, NULL
  UNION ALL SELECT 'HERMANOS', 26.00, NULL, NULL, 28.00, NULL, NULL
  UNION ALL SELECT 'ADULTO_GRUPO', 20.00, NULL, NULL, 22.00, NULL, NULL
  UNION ALL SELECT 'INFANTIL_GRUPO', 20.00, NULL, NULL, 22.00, NULL, NULL
  UNION ALL SELECT 'PADRES_HIJOS', 0.00, 28.00, 26.00, 0.00, 30.00, 28.00
) c
  ON CAST(CONVERT(t.codigo USING utf8mb4) AS BINARY) = CAST(CONVERT(c.codigo USING utf8mb4) AS BINARY)
 AND t.importe = c.importe_antes
 AND t.importe_adulto <=> c.adulto_antes
 AND t.importe_menor <=> c.menor_antes
SET ad.cuantia_tarifa = CASE
      WHEN ad.rol_familiar IN ('PADRE', 'MADRE') AND c.adulto_nuevo IS NOT NULL THEN c.adulto_nuevo
      WHEN ad.rol_familiar IN ('HIJO', 'HIJA') AND c.menor_nuevo IS NOT NULL THEN c.menor_nuevo
      ELSE c.importe_nuevo
    END
WHERE ad.organizacion_id = 1
  AND ad.cuantia_tarifa = CASE
        WHEN ad.rol_familiar IN ('PADRE', 'MADRE') AND c.adulto_antes IS NOT NULL THEN c.adulto_antes
        WHEN ad.rol_familiar IN ('HIJO', 'HIJA') AND c.menor_antes IS NOT NULL THEN c.menor_antes
        ELSE c.importe_antes
      END
  AND CASE
        WHEN ad.rol_familiar IN ('PADRE', 'MADRE') AND c.adulto_nuevo IS NOT NULL THEN c.adulto_nuevo
        WHEN ad.rol_familiar IN ('HIJO', 'HIJA') AND c.menor_nuevo IS NOT NULL THEN c.menor_nuevo
        ELSE c.importe_nuevo
      END <> CASE
        WHEN ad.rol_familiar IN ('PADRE', 'MADRE') AND c.adulto_antes IS NOT NULL THEN c.adulto_antes
        WHEN ad.rol_familiar IN ('HIJO', 'HIJA') AND c.menor_antes IS NOT NULL THEN c.menor_antes
        ELSE c.importe_antes
      END;

UPDATE tarifa t
JOIN (
  SELECT 'INFANTIL' AS codigo,
         CAST(28.00 AS DECIMAL(8,2)) AS importe_antes, CAST(NULL AS DECIMAL(8,2)) AS adulto_antes,
         CAST(NULL AS DECIMAL(8,2)) AS menor_antes,
         CAST(30.00 AS DECIMAL(8,2)) AS importe_nuevo, CAST(NULL AS DECIMAL(8,2)) AS adulto_nuevo,
         CAST(NULL AS DECIMAL(8,2)) AS menor_nuevo
  UNION ALL SELECT 'ADULTO', 30.00, NULL, NULL, 32.00, NULL, NULL
  UNION ALL SELECT 'KICKBOXING', 30.00, NULL, NULL, 32.00, NULL, NULL
  UNION ALL SELECT 'HERMANOS', 26.00, NULL, NULL, 28.00, NULL, NULL
  UNION ALL SELECT 'ADULTO_GRUPO', 20.00, NULL, NULL, 22.00, NULL, NULL
  UNION ALL SELECT 'INFANTIL_GRUPO', 20.00, NULL, NULL, 22.00, NULL, NULL
  UNION ALL SELECT 'PADRES_HIJOS', 0.00, 28.00, 26.00, 0.00, 30.00, 28.00
) c
  ON CAST(CONVERT(t.codigo USING utf8mb4) AS BINARY) = CAST(CONVERT(c.codigo USING utf8mb4) AS BINARY)
 AND t.importe = c.importe_antes
 AND t.importe_adulto <=> c.adulto_antes
 AND t.importe_menor <=> c.menor_antes
SET t.importe = c.importe_nuevo,
    t.importe_adulto = c.adulto_nuevo,
    t.importe_menor = c.menor_nuevo
WHERE t.organizacion_id = 1;
