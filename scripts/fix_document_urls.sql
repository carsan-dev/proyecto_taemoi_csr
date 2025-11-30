-- ============================================================================
-- SQL Script to Fix Document URLs in Production Database
-- ============================================================================
-- This script fixes common issues with document URLs:
-- 1. Updates base URL to production domain (moiskimdo.es)
-- 2. Removes URL encoding (%20, %C3%, etc.) from URLs
-- 3. Ensures correct path format: /documentos/Documentos_Alumnos_Moiskimdo/{folder}/{file}
-- ============================================================================

-- STEP 1: Show current document URLs to identify issues
-- Run this first to see what needs to be fixed
SELECT
    d.id,
    d.nombre,
    d.url,
    a.numero_expediente,
    a.nombre as alumno_nombre,
    a.apellidos
FROM documento d
JOIN alumno a ON d.alumno_id = a.id
ORDER BY d.id
LIMIT 20;

-- STEP 2: Check for URLs with encoding issues
SELECT
    COUNT(*) as total_with_percent_encoding,
    'URLs with percent encoding (%, spaces, accents)' as issue_type
FROM documento
WHERE url LIKE '%\%%'
   OR url LIKE '% %';

-- STEP 3: Check for URLs with wrong base domain
SELECT
    COUNT(*) as total_with_wrong_domain,
    'URLs pointing to localhost instead of production' as issue_type
FROM documento
WHERE url LIKE '%localhost%';

-- ============================================================================
-- FIX SCRIPT - Run this ONLY after reviewing the above queries
-- ============================================================================

-- BACKUP FIRST (IMPORTANT!)
-- Create a backup table before making changes:
CREATE TABLE IF NOT EXISTS documento_backup_before_url_fix AS
SELECT * FROM documento;

-- Fix 1: Replace localhost with production domain
UPDATE documento
SET url = REPLACE(url, 'http://localhost:8080', 'https://moiskimdo.es')
WHERE url LIKE '%localhost%';

-- Fix 2: Remove common URL encodings for Spanish accents
-- Note: This assumes the actual folder/file names on disk don't have URL encoding
UPDATE documento
SET url = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
    url,
    '%C3%A1', 'a'),  -- á
    '%C3%A9', 'e'),  -- é
    '%C3%AD', 'i'),  -- í
    '%C3%B3', 'o'),  -- ó
    '%C3%BA', 'u'),  -- ú
    '%C3%B1', 'n'),  -- ñ
    '%20', '_'),     -- space to underscore
    '%C3%81', 'A'),  -- Á
    '%C3%89', 'E'),  -- É
    '%C3%8D', 'I')   -- Í
WHERE url LIKE '%\%%';

-- Fix 3: Ensure all URLs use HTTPS in production
UPDATE documento
SET url = REPLACE(url, 'http://moiskimdo.es', 'https://moiskimdo.es')
WHERE url LIKE 'http://moiskimdo.es%';

-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Verify the changes
SELECT
    'After Fix' as status,
    COUNT(*) as total_documents,
    SUM(CASE WHEN url LIKE '%localhost%' THEN 1 ELSE 0 END) as with_localhost,
    SUM(CASE WHEN url LIKE '%\%%' THEN 1 ELSE 0 END) as with_percent_encoding,
    SUM(CASE WHEN url LIKE 'http://moiskimdo.es%' THEN 1 ELSE 0 END) as using_http,
    SUM(CASE WHEN url LIKE 'https://moiskimdo.es%' THEN 1 ELSE 0 END) as using_https
FROM documento;

-- Show sample of fixed URLs
SELECT
    d.id,
    d.nombre,
    d.url,
    a.numero_expediente
FROM documento d
JOIN alumno a ON d.alumno_id = a.id
ORDER BY d.id
LIMIT 10;

-- ============================================================================
-- ROLLBACK SCRIPT (if something goes wrong)
-- ============================================================================
/*
-- Restore from backup:
DELETE FROM documento;
INSERT INTO documento SELECT * FROM documento_backup_before_url_fix;

-- Drop backup table:
DROP TABLE documento_backup_before_url_fix;
*/
