#!/usr/bin/env python3
"""
Quick script to check document URLs in the database
"""
import mysql.connector
import sys
from urllib.parse import unquote

# Database configuration
DB_CONFIG = {
    "host": "localhost",
    "port": 3307,  # Docker port
    "database": "taemoi_db",
    "user": "taemoi",
    "password": "taemoi",
}

def check_document_urls():
    """Check document URLs and identify issues"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor(dictionary=True)

        # Get all documents
        query = """
            SELECT d.id, d.nombre, d.url, d.ruta, d.alumno_id,
                   a.numero_expediente, a.nombre as alumno_nombre, a.apellidos
            FROM documento d
            JOIN alumno a ON d.alumno_id = a.id
            ORDER BY d.id
            LIMIT 20
        """
        cursor.execute(query)
        documents = cursor.fetchall()

        print("=" * 100)
        print("DOCUMENT URL ANALYSIS")
        print("=" * 100)
        print(f"\nFound {len(documents)} documents (showing first 20)\n")

        issues_found = []

        for doc in documents:
            print(f"\nDocument ID: {doc['id']}")
            print(f"  Alumno: {doc['alumno_nombre']} {doc['apellidos']} (Exp: {doc['numero_expediente']})")
            print(f"  Nombre: {doc['nombre']}")
            print(f"  URL: {doc['url']}")
            print(f"  URL Decoded: {unquote(doc['url'])}")

            # Check for issues
            url = doc['url']

            # Issue 1: URL encoding issues
            if '%C3%' in url or '%20' in url:
                issues_found.append({
                    'id': doc['id'],
                    'issue': 'URL has percent encoding (accents or spaces)',
                    'url': url
                })
                print("  ⚠️  URL has percent encoding!")

            # Issue 2: Wrong base URL
            if 'localhost' in url:
                issues_found.append({
                    'id': doc['id'],
                    'issue': 'URL points to localhost instead of production domain',
                    'url': url
                })
                print("  ⚠️  URL points to localhost!")

            # Issue 3: Missing /documentos/ path
            if '/documentos/' not in url:
                issues_found.append({
                    'id': doc['id'],
                    'issue': 'URL missing /documentos/ path',
                    'url': url
                })
                print("  ⚠️  URL missing /documentos/ path!")

            # Issue 4: Missing Documentos_Alumnos_Moiskimdo
            if '/Documentos_Alumnos_Moiskimdo/' not in url:
                issues_found.append({
                    'id': doc['id'],
                    'issue': 'URL missing Documentos_Alumnos_Moiskimdo folder',
                    'url': url
                })
                print("  ⚠️  URL missing Documentos_Alumnos_Moiskimdo folder!")

        # Summary
        print("\n" + "=" * 100)
        print("SUMMARY")
        print("=" * 100)
        print(f"Total documents checked: {len(documents)}")
        print(f"Issues found: {len(issues_found)}")

        if issues_found:
            print("\n⚠️  ISSUES DETECTED:")
            issue_types = {}
            for issue in issues_found:
                issue_type = issue['issue']
                issue_types[issue_type] = issue_types.get(issue_type, 0) + 1

            for issue_type, count in issue_types.items():
                print(f"  - {issue_type}: {count} documents")
        else:
            print("\n✓ All documents have valid URLs!")

        cursor.close()
        conn.close()

    except mysql.connector.Error as e:
        print(f"❌ Database error: {e}")
        print(f"   Config: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")
        sys.exit(1)

if __name__ == "__main__":
    check_document_urls()
