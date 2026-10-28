-- Pieza 279 (recibo de pago): datos legales de Moiskimdo (organización 1) para la cabecera del recibo. El recibo los
-- lee de organizacion y omite lo vacío; hasta ahora nadie los cargaba (V18 los dejó fuera del repositorio) y el
-- certificado de cobros los lleva fijos en el código (TesoreriaServiceImpl: CLUB_NOMBRE_LEGAL y CLUB_CIF). Decisión
-- del propietario: cargarlos aquí, con el nombre legal y el CIF idénticos a esas constantes. El propietario confirmó
-- también que la dirección es la fiscal y los datos de contacto (correo y teléfono).
--
-- SOLO DATOS: no cambia el esquema.
--   - Solo la organización 1. Si no existe, actualiza 0 filas y no falla.
--   - Solo los campos vacíos (NULL, cadena vacía o solo espacios): nunca pisa un valor ya cargado.
--   - Incluye email_contacto y telefono (confirmados por el propietario), con la misma regla: solo si están vacíos.
--   - Idempotente: repetirla no cambia nada.
--   - El código anterior sigue funcionando (las columnas ya existían).
-- Fichero en UTF-8 («Cigüeña»); la tabla es utf8mb4 (V18). Comprobación posterior:
-- docs/multi-escuela/postcheck-recibo-279.sql. Va como V31_12 (detrás de V31_11; V32 sigue reservada).
UPDATE organizacion
SET nombre_legal  = CASE WHEN nombre_legal IS NULL OR TRIM(nombre_legal) = ''
                         THEN 'Club Deportivo Moi''s Kim do Taekwondo' ELSE nombre_legal END,
    cif           = CASE WHEN cif IS NULL OR TRIM(cif) = ''
                         THEN 'G90341173' ELSE cif END,
    direccion     = CASE WHEN direccion IS NULL OR TRIM(direccion) = ''
                         THEN 'Calle Parada de la Cigüeña, 36' ELSE direccion END,
    codigo_postal = CASE WHEN codigo_postal IS NULL OR TRIM(codigo_postal) = ''
                         THEN '41806' ELSE codigo_postal END,
    localidad     = CASE WHEN localidad IS NULL OR TRIM(localidad) = ''
                         THEN 'Umbrete' ELSE localidad END,
    provincia     = CASE WHEN provincia IS NULL OR TRIM(provincia) = ''
                         THEN 'Sevilla' ELSE provincia END,
    email_contacto = CASE WHEN email_contacto IS NULL OR TRIM(email_contacto) = ''
                         THEN 'moiskimdotaekwondo@gmail.com' ELSE email_contacto END,
    telefono      = CASE WHEN telefono IS NULL OR TRIM(telefono) = ''
                         THEN '625753952' ELSE telefono END
WHERE id = 1;
