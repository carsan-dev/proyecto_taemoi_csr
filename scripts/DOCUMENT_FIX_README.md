# Document Access Fix - Production Server Guide

## Problem Summary

Existing documents in the database cannot be opened because they redirect to OAuth2 login, while newly uploaded documents work fine. This indicates that the old document URLs in the database have encoding issues or incorrect paths.

## Root Cause

Old documents likely have:
1. **URL encoding** in their paths (e.g., `%20` for spaces, `%C3%A1` for á)
2. **Incorrect base URL** (localhost instead of moiskimdo.es)
3. **HTTP instead of HTTPS**

When the browser tries to access these incorrectly formatted URLs, Spring Boot can't find the files and redirects to OAuth2 login.

## Solution Steps

Follow these steps **on your Ubuntu production server**:

### Step 1: Diagnose the Problem

First, run the diagnostic script to identify the exact issue:

```bash
cd /path/to/proyecto_taemoi_csr
chmod +x scripts/diagnose-documents.sh
./scripts/diagnose-documents.sh
```

This will show you:
- Sample document URLs from the database
- Whether backend returns 200 OK or 302 REDIRECT
- Where the redirect goes (OAuth2 = problem confirmed)

### Step 2: Deploy Updated Backend Code

The backend security configuration has been updated to fix the OAuth2 redirect issue. Deploy it:

```bash
# Navigate to project directory
cd /path/to/proyecto_taemoi_csr

# Pull latest changes from git
git pull origin develop

# Rebuild and restart containers
docker-compose -f docker-compose.production.yml down
docker-compose -f docker-compose.production.yml up -d --build
```

**Important:** The updated `SecurityConfiguration.java` and `WebConfig.java` now properly handle `/documentos/**` paths without OAuth2 redirects.

### Step 3: Fix Database URLs

Run the fix script to clean up old document URLs in the database:

```bash
chmod +x scripts/fix_document_access.sh
./scripts/fix_document_access.sh
```

This script will:
1. Show you a sample of current URLs
2. Ask for confirmation before making changes
3. Create a backup table (documento_backup_YYYYMMDD)
4. Remove URL encoding (%, accents, spaces)
5. Replace localhost with moiskimdo.es
6. Ensure HTTPS is used
7. Show you the results

### Step 4: Verify the Fix

After running the fix script, test an old document:

```bash
# Get a sample document URL from the database
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -se "SELECT url FROM documento LIMIT 1;"

# Test the URL (should return 200, not 302)
curl -I <paste-the-url-here>
```

Also test in your browser:
1. Open the application at https://moiskimdo.es
2. Login as admin/manager
3. Go to an existing student (Alumno)
4. Try to open one of their old documents
5. It should now open directly without OAuth2 redirect

## What Changed in the Code

### 1. SecurityConfiguration.java
- Added `.permitAll()` to OAuth2 login configuration
- Ensured static resources (`/documentos/**`) are explicitly allowed before OAuth2 rules
- Enhanced exception handler to prevent OAuth2 redirects for static resources

### 2. WebConfig.java
- Added explicit CORS mappings for `/imagenes/**` and `/documentos/**`
- This allows direct file access from the frontend without CORS errors

## Troubleshooting

### If documents still redirect to OAuth2:

1. **Check if backend was rebuilt:**
   ```bash
   docker logs taemoi-backend-prod | grep "Started ProjectApplication"
   ```
   Look for a recent startup timestamp.

2. **Check Spring resource handlers:**
   ```bash
   docker exec taemoi-backend-prod curl -I http://localhost:8080/documentos/Documentos_Alumnos_Moiskimdo/882_AARON_OROZCO_ORTEGA/AL882.pdf
   ```
   Should return `200 OK`, not `302 Found`.

3. **Check nginx proxy:**
   ```bash
   docker exec taemoi-frontend-prod cat /etc/nginx/conf.d/default.conf | grep -A 10 "location /documentos"
   ```
   Verify proxy_pass is set to `http://backend`.

### If some documents still don't work:

The issue might be that the **actual folder/file names on disk** don't match the cleaned URLs.

1. **Check actual folder names:**
   ```bash
   docker exec taemoi-backend-prod ls -la /var/www/app/documentos/Documentos_Alumnos_Moiskimdo/ | head -20
   ```

2. **Compare with database URLs:**
   ```bash
   docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "SELECT url FROM documento LIMIT 10;"
   ```

If folder names on disk have spaces or accents but URLs have underscores, you need to either:
- **Option A:** Rename physical folders to match cleaned names (safer)
- **Option B:** Update URLs in database to match physical folder names

### Rollback (if needed)

If something goes wrong, restore from backup:

```bash
docker exec taemoi-mysql-prod mysql -u root -p${MYSQL_ROOT_PASSWORD} -D taemoi_db -e "
DELETE FROM documento;
INSERT INTO documento SELECT * FROM documento_backup_$(date +%Y%m%d);
DROP TABLE documento_backup_$(date +%Y%m%d);
"
```

## Prevention for Future

When migrating documents or creating new document records, ensure:
1. URLs use HTTPS and production domain (https://moiskimdo.es)
2. Folder and file names don't have URL encoding
3. Names use underscores instead of spaces
4. Accents are removed from folder/file names

## Files Modified

- `src-api/src/main/java/com/taemoi/project/config/SecurityConfiguration.java`
- `src-api/src/main/java/com/taemoi/project/config/WebConfig.java`
- `scripts/diagnose-documents.sh` (already existed)
- `scripts/fix_document_access.sh` (new)
- `scripts/fix_document_urls.sql` (new - manual SQL if needed)
