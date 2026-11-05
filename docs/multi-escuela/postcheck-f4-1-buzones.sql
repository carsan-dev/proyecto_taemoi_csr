-- Comprobación posterior de la fase 4, pieza 1 (decisión 391: buzón de cada escuela, organizacion.email_contacto).
-- Solo lectura. Sin migración: el buzón es la columna email_contacto que existe desde V31_12.
--
-- Deben ser (justo tras desplegar):
--   organización 1   email_contacto  moiskimdotaekwondo@gmail.com
--                    sin_buzon       0
--   resto            una fila por escuela; sin_buzon = 1 si email_contacto está vacío o en blanco
--
-- Una escuela con sin_buzon = 1 no recibe avisos internos de preinscripción (alta ni promoción de la lista de espera)
-- y su formulario de contacto responde 409 sin enviar nada. El correo a la familia sale igual.
SELECT id, nombre, activa, email_contacto,
  (email_contacto IS NULL OR TRIM(email_contacto) = '') AS sin_buzon
FROM organizacion
ORDER BY id;
