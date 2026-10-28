-- Comprobación posterior de V31_12 (pieza 279, recibo de pago: datos legales de Moiskimdo, organización 1).
-- Solo lectura. Los datos que devuelve son los públicos del club (nombre legal, CIF y domicilio social).
--
-- Deben ser (justo tras desplegar):
--   flyway_31_12            1   V31_12 aplicada con éxito
--   organización 1          nombre_legal  Club Deportivo Moi's Kim do Taekwondo
--                           cif           G90341173
--                           direccion     Calle Parada de la Cigüeña, 36
--                           codigo_postal 41806
--                           localidad     Umbrete
--                           provincia     Sevilla
--                           email_contacto moiskimdotaekwondo@gmail.com
--                           telefono      625753952
--     (si alguno ya tenía valor antes de V31_12, se conserva ese valor)
--   direccion_exacta        1   la dirección es la esperada byte a byte en UTF-8 (independiente del juego de
--                               caracteres del cliente mysql: si la «ü» o la «ñ» se ven raras en pantalla, manda esto)
--   direccion_caracteres   30
--   direccion_bytes        32   «ü» y «ñ» ocupan dos bytes; más de 32 sería doble codificación
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.12' AND success = 1)
    AS flyway_31_12;

SELECT id, nombre_legal, cif, direccion, codigo_postal, localidad, provincia, email_contacto, telefono
FROM organizacion WHERE id = 1;

SELECT
  (CAST(direccion AS BINARY) = X'43616C6C6520506172616461206465206C6120436967C3BC65C3B1612C203336')
    AS direccion_exacta,
  CHAR_LENGTH(direccion) AS direccion_caracteres,
  LENGTH(direccion) AS direccion_bytes
FROM organizacion WHERE id = 1;
