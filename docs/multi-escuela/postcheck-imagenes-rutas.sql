-- Solo lectura (03/11/2026): rutas y URL de la tabla imagen antes de desplegar fix/imagenes-rutas-hosts.
-- Sin escrituras ni datos personales: solo recuentos. Nunca sale un nombre de fichero, una ruta completa ni una URL
-- completa; de las rutas, como mucho sus 4 primeras carpetas, y de las URL, solo el esquema y el host (una IP sale
-- como «ip-numerica»). Ejecutar con --default-character-set=utf8mb4.
--
-- Cómo leer cada bloque:
--  (a) Tipo de ruta. «relativa org-<id>» es lo normal (ficheros nuevos). «absoluta» son filas heredadas: tras el
--      despliegue solo se leen o se borran si están dentro de la carpeta de imágenes o de app.imagenes.directorios-heredados.
--      «relativa con ..» o «absoluta con ..» ya no se resuelven (deberían salir 0).
--  (b) Rutas absolutas agrupadas por sus 4 primeras carpetas (sin el fichero). Lo esperado: /var/www/app/imagenes
--      (las 805 filas reescritas el 15/10/2026). Cualquier otra carpeta hay que mirarla antes de desplegar.
--  (c) URL guardadas por esquema y host. «relativa /imagenes/» es lo normal. Con host, la columna
--      redirige_tras_el_cambio dice si la foto sin fichero seguirá redirigiendo a esa URL: solo https y el host de
--      app.base.url / app.frontend.base-url (aquí se supone moiskimdo.es; si APP_BASE_URL es otro, léelo con ese).
--  (d) Rutas absolutas que quedan FUERA de las carpetas permitidas por defecto en Linux (/var/www/app/imagenes/ y
--      /opt/taemoi/static_resources/imagenes/; la de %USERPROFILE% solo cuenta en Windows), o con «..». Esas filas
--      dejan de servirse y de borrarse (el borrado del alumno o evento sigue funcionando). Lo esperado: 0, o solo
--      fotos que ya no existían en disco. Si sale algo, se añade su carpeta a APP_IMAGENES_DIRECTORIOS_HEREDADOS.
--      Como en Linux, distingue mayúsculas y no toma «\» por «/»: una ruta de Windows cuenta como fuera.

-- (a) Filas por tipo de ruta
SELECT tipo_ruta, COUNT(*) AS filas
FROM (
  SELECT CASE
           WHEN ruta IS NULL OR TRIM(ruta) = '' THEN 'sin ruta'
           WHEN r LIKE '/%' OR r REGEXP '^[A-Za-z]:' THEN
             CASE WHEN CONCAT(r, '/') LIKE '%/../%' OR CONCAT(r, '/') LIKE '%/./%' THEN 'absoluta con ..' ELSE 'absoluta' END
           WHEN CONCAT('/', r, '/') LIKE '%/../%' THEN 'relativa con ..'
           WHEN r REGEXP '^[Oo][Rr][Gg]-[0-9]+/' THEN 'relativa org-<id>'
           ELSE 'relativa sin prefijo'
         END AS tipo_ruta
  FROM (SELECT ruta, REPLACE(TRIM(ruta), CHAR(92), '/') AS r FROM imagen) i
) t
GROUP BY tipo_ruta
ORDER BY filas DESC;

-- (b) Rutas absolutas por sus 4 primeras carpetas (nunca el nombre del fichero), distinguiendo mayúsculas
SELECT primeras_carpetas, COUNT(*) AS filas
FROM (
  SELECT CONVERT(SUBSTRING_INDEX(SUBSTRING(r, 1, CHAR_LENGTH(r) - CHAR_LENGTH(SUBSTRING_INDEX(r, '/', -1))), '/',
           CASE WHEN r LIKE '/%' THEN 5 ELSE 4 END) USING utf8mb4) COLLATE utf8mb4_bin AS primeras_carpetas
  FROM (SELECT REPLACE(TRIM(ruta), CHAR(92), '/') AS r FROM imagen WHERE ruta IS NOT NULL) i
  WHERE r LIKE '/%' OR r REGEXP '^[A-Za-z]:'
) c
GROUP BY primeras_carpetas
ORDER BY filas DESC;

-- (c) URL guardadas por esquema y host (sin ruta ni consulta)
SELECT esquema, host, COUNT(*) AS filas,
       CASE WHEN esquema = 'relativa /imagenes/' THEN 'si (servidor propio)'
            WHEN esquema = 'https' AND host = 'moiskimdo.es' THEN 'si'
            ELSE 'no' END AS redirige_tras_el_cambio
FROM (
  SELECT CASE
           WHEN u LIKE '/imagenes/%' THEN 'relativa /imagenes/'
           WHEN u LIKE '%://%' THEN LOWER(SUBSTRING_INDEX(u, '://', 1))
           ELSE 'otra'
         END AS esquema,
         CASE
           WHEN u NOT LIKE '%://%' OR u LIKE '/imagenes/%' THEN ''
           WHEN h REGEXP '^[0-9.]+(:[0-9]+)?$' OR h LIKE '[%' THEN 'ip-numerica'
           ELSE LOWER(h)
         END AS host
  FROM (
    SELECT TRIM(url) AS u,
           SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(SUBSTRING_INDEX(TRIM(url), '://', -1), '/', 1), '?', 1), '#', 1) AS h
    FROM imagen
    WHERE url IS NOT NULL AND TRIM(url) <> ''
  ) x
) y
GROUP BY esquema, host
ORDER BY filas DESC;

-- (d) Rutas absolutas fuera de las carpetas permitidas por defecto (o con ..), y cuántas son fotos de alumnos
SELECT COUNT(*) AS absolutas_fuera,
       COALESCE(SUM(CASE WHEN a.id IS NOT NULL THEN 1 ELSE 0 END), 0) AS de_ellas_fotos_de_alumno
FROM (SELECT id, TRIM(ruta) AS r FROM imagen WHERE ruta IS NOT NULL) i
LEFT JOIN alumno a ON a.foto_alumno_id = i.id
WHERE (i.r LIKE '/%' OR LEFT(i.r, 1) = CHAR(92) OR i.r REGEXP '^[A-Za-z]:')
  AND (
    CONCAT(i.r, '/') LIKE '%/../%' OR CONCAT(i.r, '/') LIKE '%/./%'
    OR NOT (
      (LENGTH(i.r) > 22 AND CAST(LEFT(i.r, 22) AS BINARY) = CAST('/var/www/app/imagenes/' AS BINARY))
      OR (LENGTH(i.r) > 38 AND CAST(LEFT(i.r, 38) AS BINARY) = CAST('/opt/taemoi/static_resources/imagenes/' AS BINARY))
    )
  );
