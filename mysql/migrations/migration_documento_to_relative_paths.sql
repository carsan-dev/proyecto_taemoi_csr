-- Migration to convert all documento absolute paths to relative paths
-- This makes the database environment-independent
-- Run this AFTER deploying the new code with relative path support

-- Step 1: Convert /opt/taemoi paths to /var/www/app (production fix)
UPDATE documento
SET ruta = REPLACE(ruta, '/opt/taemoi/static_resources/documentos/', '/var/www/app/documentos/')
WHERE ruta LIKE '/opt/taemoi/static_resources/documentos/%';

-- Step 2: Convert all Linux absolute paths to relative paths
UPDATE documento
SET ruta = SUBSTRING(ruta, LENGTH('/var/www/app/documentos/') + 1)
WHERE ruta LIKE '/var/www/app/documentos/%';

-- Step 3: Convert all Windows absolute paths to relative paths (if any exist)
-- Pattern: C:/Users/.../static_resources/documentos/...
UPDATE documento
SET ruta = SUBSTRING_INDEX(ruta, 'documentos/', -1)
WHERE ruta LIKE '%/documentos/%' AND ruta LIKE '_:%';

-- Verify the migration
SELECT
    id,
    nombre,
    ruta,
    CASE
        WHEN ruta LIKE '/%' THEN 'ABSOLUTE (Linux)'
        WHEN ruta LIKE '_:%' THEN 'ABSOLUTE (Windows)'
        ELSE 'RELATIVE (OK)'
    END as path_type
FROM documento
ORDER BY path_type, id
LIMIT 20;

-- Count paths by type
SELECT
    CASE
        WHEN ruta LIKE '/%' THEN 'ABSOLUTE (Linux)'
        WHEN ruta LIKE '_:%' THEN 'ABSOLUTE (Windows)'
        ELSE 'RELATIVE (OK)'
    END as path_type,
    COUNT(*) as count
FROM documento
GROUP BY path_type;
