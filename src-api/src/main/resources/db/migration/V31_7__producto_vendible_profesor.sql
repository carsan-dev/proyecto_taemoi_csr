-- Marca «Lo puede vender el profesor» en la ficha del producto (pieza 6b-3b-1, decisión 182 del propietario).
--
-- SOLO AÑADE una columna a producto:
--   vendible_profesor  BOOLEAN NOT NULL DEFAULT FALSE. La gestión la marca en la ficha del producto; la venta del
--                      profesor («Vender» en «Mi caja») solo ofrece y cobra los productos marcados que ADEMÁS cumplen la
--                      regla fija de la 6b-2 (tipo OTRO, sin grado, concepto sin efectos). Vale en todas las sedes.
-- Sin relleno: ningún producto queda marcado hasta que la gestión lo marque (hoy no hay ninguna membresía PROFESOR en
-- producción, así que nadie deja de poder vender nada). El código anterior sigue funcionando sobre este esquema: no lee
-- la columna e inserta sin ella (queda FALSE por el DEFAULT), así que no lleva guarda. Va como V31_7 porque V32 está
-- reservada para la contracción multi-escuela.
--
-- Coste en producción (mysql:8.0): ADD COLUMN ... NOT NULL DEFAULT al final de la tabla es INSTANT (solo metadatos, sin
-- copiar filas ni bloquear escrituras). Tabla pequeña (el catálogo de cada escuela).
--
-- Repetible: la guarda de existencia (information_schema, como V31_6) solo añade la columna si falta. Tras un fallo,
-- `flyway repair` y volver a desplegar.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v31_7.sql.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto' AND COLUMN_NAME = 'vendible_profesor'),
        'SELECT ''producto.vendible_profesor already exists''',
        'ALTER TABLE producto ADD COLUMN vendible_profesor BOOLEAN NOT NULL DEFAULT FALSE'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
