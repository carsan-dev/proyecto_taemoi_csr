-- Invitaciones por correo (pieza E8-4, docs/multi-escuela/fase3-e8-plan.md §5; D8-13; decisión 223).
--
-- SOLO cambia una restricción: usuario.contrasena pasa a admitir NULL. Una cuenta LOCAL sin contraseña es una
-- invitación pendiente: la crea POST /api/miembros con nombre y apellidos para un email sin cuenta, y deja de estarlo
-- al crear su contraseña con el enlace del correo (o al entrar con Google). El login con contraseña la rechaza como
-- una contraseña incorrecta (BCrypt no casa con NULL).
--
-- Ninguna fila cambia. Se conservan el tipo, el juego de caracteres y la colación que tenga HOY la columna (se leen de
-- information_schema: en una base creada antes de Flyway podrían no ser los de la tabla), sin DEFAULT nuevo (queda
-- DEFAULT NULL, como cualquier columna NULL sin valor por defecto). MySQL 8.0 hace este cambio INPLACE y admite
-- escrituras a la vez; usuario es una tabla pequeña.
--
-- Repetible: si la columna ya admite NULL, no hace nada. El código anterior funciona sobre este esquema (siempre
-- escribe la contraseña). Volver a NOT NULL exige antes dar contraseña a las invitaciones pendientes o borrarlas:
--   SELECT COUNT(*) FROM usuario WHERE contrasena IS NULL;  -- solo las invitaciones pendientes
-- Va como V31_9 porque V32 está reservada para la contracción multi-escuela.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        c.IS_NULLABLE = 'YES',
        'SELECT ''usuario.contrasena already nullable''',
        CONCAT('ALTER TABLE usuario MODIFY COLUMN contrasena ', c.COLUMN_TYPE,
               IF(c.CHARACTER_SET_NAME IS NULL, '', CONCAT(' CHARACTER SET ', c.CHARACTER_SET_NAME)),
               IF(c.COLLATION_NAME IS NULL, '', CONCAT(' COLLATE ', c.COLLATION_NAME)),
               ' NULL')
    )
    FROM information_schema.COLUMNS c
    WHERE c.TABLE_SCHEMA = @schema_name
      AND c.TABLE_NAME = 'usuario'
      AND c.COLUMN_NAME = 'contrasena'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
