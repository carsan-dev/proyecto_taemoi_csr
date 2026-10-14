-- Multi-escuela, fase C: catálogo visual (decisiones del propietario del 14/10/2026, D2-D5 y orden de
-- tarifas; docs/multi-escuela/catalogo-frontend.md §5). Todas las pantallas, PDF incluidos, muestran
-- grado.nombre, grado.color_1/color_2 y disciplina.nombre del catálogo, también en Taemoi.
--
--   1. grado.nombre de la organización 1 (todos sus sistemas: taekwondo y la copia de kickboxing), por
--      código. Mayúsculas, colores unidos con «-», PUM/DAN con ordinal; en kickboxing ROJO = «MARRÓN»:
--
--        codigo             nombre                 color_1   color_2
--        -----------------  ---------------------  --------  --------
--        BLANCO             BLANCO                 #FFFFFF   NULL
--        BLANCO_AMARILLO    BLANCO-AMARILLO        #FFFFFF   #FFFF00
--        AMARILLO           AMARILLO               #FFFF00   NULL
--        AMARILLO_NARANJA   AMARILLO-NARANJA       #FFFF00   #FFA500
--        NARANJA            NARANJA                #FFA500   NULL
--        NARANJA_VERDE      NARANJA-VERDE          #FFA500   #008000
--        VERDE              VERDE                  #008000   NULL
--        VERDE_AZUL         VERDE-AZUL             #008000   #0000FF
--        AZUL               AZUL                   #0000FF   NULL
--        AZUL_ROJO          AZUL-ROJO              #0000FF   #FF0000
--        ROJO (taekwondo)   ROJO                   #FF0000   NULL
--        ROJO (kickboxing)  MARRÓN                 #8B4513   NULL      (D4)
--        ROJO_NEGRO_1_PUM   ROJO-NEGRO 1º PUM      #FF0000   #000000
--        ROJO_NEGRO_2_PUM   ROJO-NEGRO 2º PUM      #FF0000   #000000
--        ROJO_NEGRO_3_PUM   ROJO-NEGRO 3º PUM      #FF0000   #000000
--        NEGRO_1_DAN        NEGRO 1º DAN           #000000   NULL
--        NEGRO_2_DAN        NEGRO 2º DAN           #000000   NULL
--        NEGRO_3_DAN        NEGRO 3º DAN           #000000   NULL
--        NEGRO_4_DAN        NEGRO 4º DAN           #000000   NULL
--        NEGRO_5_DAN        NEGRO 5º DAN           #000000   NULL
--
--   2. grado.color_1 / color_2 de la organización 1: la paleta actual del frontend
--      (src-frontend/src/app/utilities/grado-colors.ts). Convención:
--        color_1 = color base = PRIMER color del código = mitad INFERIOR del cinturón;
--        color_2 = SEGUNDO color del código = mitad SUPERIOR; NULL = cinturón de un solo color.
--      Es la de getGradoColors (colors = [primero, segundo]) + getGradoTextStyle
--      («linear-gradient(to bottom, colors[1] 50%, colors[0] 50%)»: colors[1] arriba) y la de
--      getBeltVisual (topColor = parts[1], bottomColor = parts[0]) y PDFServiceImpl. Las franjas de
--      PUM/DAN no se guardan: se derivan del código (patrón _<n>_PUM / _<n>_DAN). Excepción D4:
--      kickboxing ROJO = marrón #8B4513, un solo color.
--   3. disciplina.nombre de DEFENSA_PERSONAL_FEMENINA de la organización 1 = «Defensa Personal Femenina» (D2).
--   4. tarifa.orden (INT NOT NULL DEFAULT 0). Organización 1: ADULTO, ADULTO_GRUPO, FAMILIAR, INFANTIL,
--      INFANTIL_GRUPO, HERMANOS, PADRES_HIJOS, KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA (1..10; el
--      orden de crear-alumno) y después sus tarifas con código propio, por id (11, 12…). Demás
--      organizaciones: 1, 2, 3… por id dentro de cada organización (el orden que tenían). Las tarifas
--      creadas después sin orden reciben 0 y se listan primero (orden, id).
--
-- Los grados se identifican por (sistema de la organización 1, codigo) y el ROJO de kickboxing por el
-- sistema de la disciplina KICKBOXING de la organización 1, nunca por id. Grados con códigos propios de
-- Taemoi (no listados) y grados y disciplinas de otras organizaciones no se tocan.
--
-- Repetible tras un fallo a medias (MySQL confirma cada DDL): los UPDATE de nombres y colores fijan
-- valores constantes; cada paso de tarifa.orden vuelve a consultar information_schema y los datos
-- (relleno solo de filas con orden NULL; NOT NULL solo si la columna aún admite NULL). Nada se decide
-- antes de una DDL para saltarlo al repetir.

SET @schema_name = DATABASE();

-- ============================================================================
-- 1-2. grado: nombres y colores de la organización 1 (una sola sentencia: atómica)
-- ============================================================================
UPDATE grado g
JOIN sistema_grados sg
  ON sg.id = g.sistema_grados_id
 AND sg.organizacion_id = 1
JOIN (
  SELECT 'BLANCO' AS codigo, 'BLANCO' AS nombre, '#FFFFFF' AS color_1, NULL AS color_2
  UNION ALL SELECT 'BLANCO_AMARILLO', 'BLANCO-AMARILLO', '#FFFFFF', '#FFFF00'
  UNION ALL SELECT 'AMARILLO', 'AMARILLO', '#FFFF00', NULL
  UNION ALL SELECT 'AMARILLO_NARANJA', 'AMARILLO-NARANJA', '#FFFF00', '#FFA500'
  UNION ALL SELECT 'NARANJA', 'NARANJA', '#FFA500', NULL
  UNION ALL SELECT 'NARANJA_VERDE', 'NARANJA-VERDE', '#FFA500', '#008000'
  UNION ALL SELECT 'VERDE', 'VERDE', '#008000', NULL
  UNION ALL SELECT 'VERDE_AZUL', 'VERDE-AZUL', '#008000', '#0000FF'
  UNION ALL SELECT 'AZUL', 'AZUL', '#0000FF', NULL
  UNION ALL SELECT 'AZUL_ROJO', 'AZUL-ROJO', '#0000FF', '#FF0000'
  UNION ALL SELECT 'ROJO', 'ROJO', '#FF0000', NULL
  UNION ALL SELECT 'ROJO_NEGRO_1_PUM', 'ROJO-NEGRO 1º PUM', '#FF0000', '#000000'
  UNION ALL SELECT 'ROJO_NEGRO_2_PUM', 'ROJO-NEGRO 2º PUM', '#FF0000', '#000000'
  UNION ALL SELECT 'ROJO_NEGRO_3_PUM', 'ROJO-NEGRO 3º PUM', '#FF0000', '#000000'
  UNION ALL SELECT 'NEGRO_1_DAN', 'NEGRO 1º DAN', '#000000', NULL
  UNION ALL SELECT 'NEGRO_2_DAN', 'NEGRO 2º DAN', '#000000', NULL
  UNION ALL SELECT 'NEGRO_3_DAN', 'NEGRO 3º DAN', '#000000', NULL
  UNION ALL SELECT 'NEGRO_4_DAN', 'NEGRO 4º DAN', '#000000', NULL
  UNION ALL SELECT 'NEGRO_5_DAN', 'NEGRO 5º DAN', '#000000', NULL
) c
  ON CONVERT(c.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(g.codigo USING utf8mb4) COLLATE utf8mb4_bin
LEFT JOIN disciplina kb
  ON kb.organizacion_id = 1
 AND CONVERT(kb.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT('KICKBOXING' USING utf8mb4) COLLATE utf8mb4_bin
 AND kb.sistema_grados_id = g.sistema_grados_id
SET g.nombre = CASE WHEN kb.id IS NOT NULL AND c.codigo = 'ROJO' THEN 'MARRÓN' ELSE c.nombre END,
    g.color_1 = CASE WHEN kb.id IS NOT NULL AND c.codigo = 'ROJO' THEN '#8B4513' ELSE c.color_1 END,
    g.color_2 = CASE WHEN kb.id IS NOT NULL AND c.codigo = 'ROJO' THEN NULL ELSE c.color_2 END
WHERE g.organizacion_id = 1;

-- ============================================================================
-- 3. disciplina: nombre de defensa personal femenina de la organización 1 (D2)
-- ============================================================================
UPDATE disciplina d
SET d.nombre = 'Defensa Personal Femenina'
WHERE d.organizacion_id = 1
  AND CONVERT(d.codigo USING utf8mb4) COLLATE utf8mb4_bin
      = CONVERT('DEFENSA_PERSONAL_FEMENINA' USING utf8mb4) COLLATE utf8mb4_bin;

-- ============================================================================
-- 4. tarifa.orden
-- ============================================================================
-- 4a. Columna (NULL mientras se rellena).
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'tarifa' AND COLUMN_NAME = 'orden'
        ),
        'SELECT ''tarifa.orden already exists''',
        'ALTER TABLE tarifa ADD COLUMN orden INT NULL AFTER activa'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4b. Relleno de las filas sin orden (una sola sentencia). Organización 1: posición fija de los 10
-- códigos; sus tarifas con código propio, detrás por id. Resto: posición por id en su organización.
UPDATE tarifa t
JOIN (
  SELECT n.id,
         CASE
           WHEN n.organizacion_id = 1 AND n.posicion IS NOT NULL THEN n.posicion
           WHEN n.organizacion_id = 1 THEN 10 + n.rn_sin_posicion
           ELSE n.rn_organizacion
         END AS orden
  FROM (
    SELECT x.id,
           x.organizacion_id,
           o.posicion,
           ROW_NUMBER() OVER (PARTITION BY x.organizacion_id, (o.posicion IS NULL) ORDER BY x.id) AS rn_sin_posicion,
           ROW_NUMBER() OVER (PARTITION BY x.organizacion_id ORDER BY x.id) AS rn_organizacion
    FROM tarifa x
    LEFT JOIN (
      SELECT 'ADULTO' AS codigo, 1 AS posicion
      UNION ALL SELECT 'ADULTO_GRUPO', 2
      UNION ALL SELECT 'FAMILIAR', 3
      UNION ALL SELECT 'INFANTIL', 4
      UNION ALL SELECT 'INFANTIL_GRUPO', 5
      UNION ALL SELECT 'HERMANOS', 6
      UNION ALL SELECT 'PADRES_HIJOS', 7
      UNION ALL SELECT 'KICKBOXING', 8
      UNION ALL SELECT 'PILATES', 9
      UNION ALL SELECT 'DEFENSA_PERSONAL_FEMENINA', 10
    ) o
      ON x.organizacion_id = 1
     AND CONVERT(o.codigo USING utf8mb4) COLLATE utf8mb4_bin = CONVERT(x.codigo USING utf8mb4) COLLATE utf8mb4_bin
  ) n
) r ON r.id = t.id
SET t.orden = r.orden
WHERE t.orden IS NULL;

-- 4c. NOT NULL DEFAULT 0 (si quedara alguna fila sin orden, MySQL lo rechaza en modo estricto).
SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'tarifa' AND COLUMN_NAME = 'orden'
              AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = '0'
        ),
        'SELECT ''tarifa.orden already NOT NULL DEFAULT 0''',
        'ALTER TABLE tarifa MODIFY COLUMN orden INT NOT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
