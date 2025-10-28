#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Document Migration Script: Files containing AL{numero_expediente} -> Organized per-student folders
Migrates documents from flat structure to organized per-student folders

This script finds ANY file containing "AL{numero_expediente}" pattern and
associates it with that student (by numero_expediente), moving it to their folder.

IMPORTANT: The AL{number} in filenames refers to numero_expediente, not the current database ID.
This handles cases where IDs changed during database migration but numero_expediente stayed stable.

Examples of files it will find:
  AL7_certificado.pdf -> moves to student with numero_expediente=7
  documento_AL15.pdf -> moves to student with numero_expediente=15
  foto_AL22_carnet.jpg -> moves to student with numero_expediente=22

Author: TaeMoi Team
Date: 2025-10-25
Version: 2.1 (Lookup by numero_expediente)
"""
import mysql.connector
import os
import sys
import re
import shutil
import unicodedata
from pathlib import Path
from datetime import datetime
from collections import defaultdict
from typing import Dict, List, Tuple, Optional

# Set UTF-8 encoding for Windows console
if sys.platform.startswith("win"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except:
        pass

# =============================================================================
# CONFIGURATION
# =============================================================================

# Environment detection
IS_WINDOWS = sys.platform.startswith("win")
IS_LINUX = sys.platform.startswith("linux")

# Default paths (can be overridden via command line)
if IS_WINDOWS:
    DEFAULT_DOCS_PATH = os.path.join(
        os.environ.get("USERPROFILE", ""),
        "static_resources",
        "documentos",
        "Documentos_Alumnos_Moiskimdo",
    )
else:
    DEFAULT_DOCS_PATH = "/var/www/app/documentos/Documentos_Alumnos_Moiskimdo"

# Database configuration (from .env file or defaults)
# Default to taemoi_test which has 685 students
DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "database": "taemoi_test",  # Main database with 685 students
    "user": "root",
    "password": "root",
}

# Base URL for document access
BASE_URL = "http://localhost:8080"

# Report file
REPORT_FILE = "migration_documentos_report.txt"

# Global stats
stats = defaultdict(int)
errors = []
warnings = []

# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================

import re
import unicodedata

_MOJIBAKE_REPLACEMENTS = {
    # common UTF-8 bytes mis-decoded as Latin-1 / CP1252 sequences
    "Ã¡": "á",
    "Ã©": "é",
    "Ã­": "í",
    "Ã³": "ó",
    "Ãº": "ú",
    "ÃĄ": "Á",
    "Ã‰": "É",
    "Ã\u008d": "Í",
    "Ã“": "Ó",
    "Ãš": "Ú",
    "Ã±": "ñ",
    "Ã‘": "Ñ",
    "Ã¼": "ü",
    "Ãœ": "Ü",
    "Â¡": "¡",
    "Â¿": "¿",
    "Â°": "°",
    "Â": "",  # stray Â often appears before other chars
    "\ufffd": "",  # replacement character garbage
    # Some other typical artifacts
    "Ã ": "á",
    "Â´": "'",
    "Â`": "'",
    "â\x80\x99": "'",
}

# Build a regex to replace occurrences quickly
_mojibake_pattern = re.compile(
    "|".join(re.escape(k) for k in _MOJIBAKE_REPLACEMENTS.keys())
)


def _fix_mojibake_substrings(s: str) -> str:
    if not s:
        return s
    # Replace common sequences repeatedly until stable (limit iterations)
    prev = None
    cur = s
    for _ in range(4):
        if cur == prev:
            break
        prev = cur
        cur = _mojibake_pattern.sub(lambda m: _MOJIBAKE_REPLACEMENTS[m.group(0)], cur)
    return cur


# --------------------
# Try multiple encoding repair strategies (iterative)
# --------------------
def _try_recode(s: str, enc_from: str, enc_to: str) -> str:
    try:
        return s.encode(enc_from).decode(enc_to)
    except Exception:
        return s


def fix_encoding_if_needed(text: str) -> str:
    """
    Aggressive encoder fixer:
     - Applies a series of recoding attempts and common substring fixes (mojibake).
     - Tries iterative recodings (latin1<->utf-8, cp1252<->utf-8, etc).
     - Stops when a "clean" Spanish-friendly string is obtained or when attempts exhausted.
    """
    if not text:
        return text

    # Quick heuristic: if it already contains normal Spanish letters or only ascii, try substring fixes first
    # but not blindly re-encode
    # We'll attempt several strategies and pick the "best" candidate by a simple score:
    # - prefer strings with more letters in common Spanish alphabet and fewer replacement chars.

    candidates = set()
    candidates.add(text)
    # 1) substring mojibake fixes
    candidates.add(_fix_mojibake_substrings(text))

    # 2) direct recodings (single pass)
    enc_pairs = [
        ("latin1", "utf-8"),
        ("utf-8", "latin1"),
        ("cp1252", "utf-8"),
        ("utf-8", "cp1252"),
        ("latin1", "cp1252"),
        ("cp1252", "latin1"),
    ]
    for a, b in enc_pairs:
        candidates.add(_try_recode(text, a, b))
        candidates.add(_try_recode(_fix_mojibake_substrings(text), a, b))

    # 3) iterative double passes (useful for double-encoded)
    for a, b in enc_pairs:
        first = _try_recode(text, a, b)
        second = _try_recode(first, a, b)
        candidates.add(first)
        candidates.add(second)
        # also try alternating
        alt = _try_recode(first, b, a)
        candidates.add(alt)

    # 4) try applying mojibake fixes after recoding attempts
    more = set()
    for c in list(candidates):
        more.add(_fix_mojibake_substrings(c))
    candidates.update(more)

    # Evaluate candidates by a simple score:
    # score = number of "good" characters (letters, space, ñ, accented) minus number of suspicious chars
    def score_candidate(s: str) -> int:
        good = 0
        bad = 0
        for ch in s:
            o = ord(ch)
            if ch.isalpha() or ch.isdigit() or ch in " _-.'":
                good += 2
                # boost Spanish accented letters slightly
                if ch in "áéíóúÁÉÍÓÚñÑüÜçÇ":
                    good += 1
            elif 32 <= o < 127:
                good += 1  # other printable ascii
            else:
                bad += 3  # nonprintable/weird
        return good - bad

    # Choose best-scoring candidate
    best = max(candidates, key=score_candidate)

    # If best still contains odd replacement characters, attempt final substring pass
    best = _fix_mojibake_substrings(best)

    return best


# --------------------
# Explicit accent map to remove diacritics to base letters
# --------------------
_ACCENT_MAP = {
    # lowercase
    "á": "a",
    "à": "a",
    "ä": "a",
    "â": "a",
    "ã": "a",
    "å": "a",
    "ā": "a",
    "é": "e",
    "è": "e",
    "ë": "e",
    "ê": "e",
    "ē": "e",
    "í": "i",
    "ì": "i",
    "ï": "i",
    "î": "i",
    "ī": "i",
    "ó": "o",
    "ò": "o",
    "ö": "o",
    "ô": "o",
    "õ": "o",
    "ō": "o",
    "ú": "u",
    "ù": "u",
    "ü": "u",
    "û": "u",
    "ū": "u",
    "ñ": "n",
    "ç": "c",
    # uppercase
    "Á": "A",
    "À": "A",
    "Ä": "A",
    "Â": "A",
    "Ã": "A",
    "Å": "A",
    "Ā": "A",
    "É": "E",
    "È": "E",
    "Ë": "E",
    "Ê": "E",
    "Ē": "E",
    "Í": "I",
    "Ì": "I",
    "Ï": "I",
    "Î": "I",
    "Ī": "I",
    "Ó": "O",
    "Ò": "O",
    "Ö": "O",
    "Ô": "O",
    "Õ": "O",
    "Ō": "O",
    "Ú": "U",
    "Ù": "U",
    "Ü": "U",
    "Û": "U",
    "Ū": "U",
    "Ñ": "N",
    "Ç": "C",
}
# make translate map for str.translate
_ACCENT_TRANSLATE = str.maketrans(_ACCENT_MAP)


# --------------------
# Helper: proper-case only first letter of each word
# --------------------
def _to_proper_case_words(s: str) -> str:
    parts = s.split()
    out = []
    for p in parts:
        if not p:
            continue
        out.append(p[0].upper() + p[1:].lower() if len(p) > 1 else p.upper())
    return " ".join(out)


# --------------------
# Main cleaning function
# --------------------
def clean_filename(text: str, proper_case: bool = False) -> str:
    """
    Robust cleaning:
      - Aggressively repairs encoding (multi-strategy).
      - Normalizes and removes diacritics (á->a, ñ->n, etc).
      - If proper_case=True: only first letter of each word uppercase.
      - Replaces whitespace with underscores and strips invalid chars.
    """
    if not text:
        return ""

    # 1) Attempt to repair encoding and mojibake
    text = fix_encoding_if_needed(text)

    # 2) Normalize (NFD) and remove combining marks (defensive)
    text = unicodedata.normalize("NFD", text)
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")

    # 3) Apply explicit translate map for any remaining precomposed accents
    text = text.translate(_ACCENT_TRANSLATE)

    # 4) If proper_case requested, lowercase then capitalize first letter of each word
    if proper_case:
        text = _to_proper_case_words(text.lower())

    # 5) Replace whitespace with underscore
    text = re.sub(r"\s+", "_", text.strip())

    # 6) Final cleanup: keep only safe filename chars
    text = re.sub(r"[^A-Za-z0-9._-]", "", text)

    return text


def extract_alumno_id_from_filename(filename: str) -> Optional[int]:
    """
    Extract alumno ID from filename, allowing non-digit separators:
    AL7, AL_7, AL-7, AL"7", etc.
    """
    m = re.search(r"AL\D*(\d+)", filename, re.IGNORECASE)
    return int(m.group(1)) if m else None


def clean_filename_for_storage(filename: str, alumno_id: int) -> str:
    """
    Remove the AL{id} token only if the filename has other content.
    If the base name is just the token (e.g. AL17.pdf), keep it unchanged.
    """
    p = Path(filename)
    stem, suffix = p.stem, p.suffix

    # If the *entire* stem is just the token (e.g. "AL17"), preserve
    if re.fullmatch(rf"(?i)AL\D*{alumno_id}", stem):
        return filename

    # Otherwise, strip the token (and nearby separators) from anywhere
    cleaned = re.sub(rf"(?i)AL\D*{alumno_id}(?:[_\-\s]*)?", "", filename)

    # Tidy leftovers
    cleaned = re.sub(r"__+", "_", cleaned)
    cleaned = cleaned.lstrip(" _-.")

    # If stripping would leave only an extension (e.g. ".pdf"), keep original
    if not cleaned or (Path(cleaned).stem == "" and Path(cleaned).suffix):
        return filename

    return cleaned


def load_env_file(env_path: str = ".env") -> Dict[str, str]:
    """Load environment variables from .env file"""
    env_vars = {}
    try:
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, value = line.split("=", 1)
                    env_vars[key.strip()] = value.strip()
    except FileNotFoundError:
        warnings.append(
            f"Warning: .env file not found at {env_path}, using default DB configuration"
        )
    except Exception as e:
        warnings.append(f"Warning: Error reading .env file: {e}")

    return env_vars


def update_db_config_from_env(env_vars: Dict[str, str], override_database: str = None):
    """
    Update database configuration from environment variables

    Args:
        env_vars: Environment variables dictionary
        override_database: If provided, use this database instead of .env value
    """
    if "SPRING_DATASOURCE_URL" in env_vars and not override_database:
        # Parse JDBC URL: jdbc:mysql://localhost:3306/taemoi_test
        url = env_vars["SPRING_DATASOURCE_URL"]
        match = re.search(r"jdbc:mysql://([^:]+):(\d+)/(.+?)(?:\?|$)", url)
        if match:
            DB_CONFIG["host"] = match.group(1)
            DB_CONFIG["port"] = int(match.group(2))
            DB_CONFIG["database"] = match.group(3)

    if override_database:
        DB_CONFIG["database"] = override_database

    if "SPRING_DATASOURCE_USERNAME" in env_vars:
        DB_CONFIG["user"] = env_vars["SPRING_DATASOURCE_USERNAME"]

    if "SPRING_DATASOURCE_PASSWORD" in env_vars:
        DB_CONFIG["password"] = env_vars["SPRING_DATASOURCE_PASSWORD"]


# =============================================================================
# DATABASE FUNCTIONS
# =============================================================================


def connect_to_db():
    """Connect to MySQL database"""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        print(
            f"[OK] Connected to database: {DB_CONFIG['database']} at {DB_CONFIG['host']}"
        )
        return conn
    except mysql.connector.Error as e:
        print(f"[ERROR] Error connecting to database: {e}")
        print(
            f"  Configuration: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}"
        )
        sys.exit(1)


def get_alumno_info(conn, alumno_id: int) -> Optional[Dict]:
    """Fetch alumno information from database by id"""
    cursor = conn.cursor(dictionary=True)
    query = "SELECT id, numero_expediente, nombre, apellidos FROM alumno WHERE id = %s"
    cursor.execute(query, (alumno_id,))
    result = cursor.fetchone()
    cursor.close()
    return result


def get_alumno_info_by_numero_expediente(conn, numero_expediente: int) -> Optional[Dict]:
    """Fetch alumno information from database by numero_expediente"""
    cursor = conn.cursor(dictionary=True)
    query = "SELECT id, numero_expediente, nombre, apellidos FROM alumno WHERE numero_expediente = %s"
    cursor.execute(query, (numero_expediente,))
    result = cursor.fetchone()
    cursor.close()
    return result


def get_all_documentos(conn) -> List[Dict]:
    """Fetch all documento records from database"""
    cursor = conn.cursor(dictionary=True)
    query = "SELECT id, nombre, tipo, url, ruta, alumno_id FROM documento"
    cursor.execute(query)
    results = cursor.fetchall()
    cursor.close()
    return results


def update_documento_in_db(conn, documento_id: int, nombre: str, url: str, ruta: str):
    """Update documento record in database"""
    cursor = conn.cursor()
    query = """
        UPDATE documento
        SET nombre = %s, url = %s, ruta = %s
        WHERE id = %s
    """
    cursor.execute(query, (nombre, url, ruta, documento_id))
    conn.commit()
    cursor.close()


# =============================================================================
# FILE MIGRATION FUNCTIONS
# =============================================================================


def scan_files_with_alumno_id(docs_path: str) -> List[Tuple[str, int, str]]:
    """
    Scan for files containing AL{id} pattern ANYWHERE in filename
    Returns list of (full_path, alumno_id, original_filename)
    """
    files_with_id = []
    docs_dir = Path(docs_path)

    if not docs_dir.exists():
        print(f"[ERROR] Directory not found: {docs_path}")
        return files_with_id

    # Scan all files in the directory (non-recursive first level)
    for file_path in docs_dir.iterdir():
        if file_path.is_file():
            alumno_id = extract_alumno_id_from_filename(file_path.name)
            if alumno_id:
                files_with_id.append((str(file_path), alumno_id, file_path.name))

    return files_with_id


def create_alumno_folder(
    base_path: str, alumno_info: Dict, use_num_expediente: bool = False
) -> Path:
    """
    Create folder for alumno using new naming convention:
    {id}_{Nombre}_{Apellidos} or {numero_expediente}_{Nombre}_{Apellidos}

    Folder names use proper case (only first letter uppercase):
    - Example: 7_Moises_Sanchez_Roman
    - Example: 21_Javier_Lazcano_Diajara

    Args:
        base_path: Base directory path
        alumno_info: Student information dictionary
        use_num_expediente: Use numero_expediente instead of id (default: False)
    """
    # Use numero_expediente if requested, otherwise use id
    identifier = (
        alumno_info["numero_expediente"] if use_num_expediente else alumno_info["id"]
    )

    # Clean names with proper case (Title Case: First Letter Uppercase)
    # Repara posibles codificaciones dañadas antes de limpiar
    nombre = fix_encoding_if_needed(alumno_info["nombre"])
    apellidos = fix_encoding_if_needed(alumno_info["apellidos"])

    # Limpia y aplica formato
    nombre_limpio = clean_filename(nombre, proper_case=True)
    apellidos_limpio = clean_filename(apellidos, proper_case=True)

    folder_name = f"{identifier}_{nombre_limpio}_{apellidos_limpio}"
    folder_path = Path(base_path) / folder_name

    folder_path.mkdir(parents=True, exist_ok=True)

    return folder_path


def migrate_file(
    old_path: str,
    new_folder: Path,
    original_filename: str,
    alumno_id: int,
    dry_run: bool = False,
) -> Tuple[str, str]:
    """
    Migrate file from old location to new folder structure
    Returns (new_filename, new_full_path)
    """
    old_file = Path(old_path)

    # Clean the filename by removing AL{id} pattern
    new_filename = clean_filename_for_storage(original_filename, alumno_id)
    new_path = new_folder / new_filename

    # Handle duplicates
    counter = 1
    while new_path.exists():
        name_parts = new_filename.rsplit(".", 1)
        if len(name_parts) == 2:
            new_filename = f"{name_parts[0]}_{counter}.{name_parts[1]}"
        else:
            new_filename = f"{new_filename}_{counter}"
        new_path = new_folder / new_filename
        counter += 1

    if not dry_run:
        shutil.move(str(old_file), str(new_path))
        print(f"  Moved: {old_file.name} -> {new_path.relative_to(new_folder.parent)}")
    else:
        print(
            f"  [DRY RUN] Would move: {old_file.name} -> {new_path.relative_to(new_folder.parent)}"
        )

    return new_filename, str(new_path)


# =============================================================================
# MAIN MIGRATION LOGIC
# =============================================================================


def migrate_documents(docs_path: str, dry_run: bool = False, database: str = None):
    """
    Main migration function

    Args:
        docs_path: Path to documents directory
        dry_run: If True, only preview changes without making them
        database: Database name to use (overrides .env)
    """
    print("\n" + "=" * 80)
    print("DOCUMENT MIGRATION: Files containing AL{numero_expediente} -> Student Folders")
    print("=" * 80 + "\n")

    if dry_run:
        print("🔍 DRY RUN MODE - No files will be moved or database updated\n")

    # Load environment configuration
    env_vars = load_env_file()
    update_db_config_from_env(env_vars, override_database=database)

    # Connect to database
    print(
        f"📊 Database: {DB_CONFIG['database']} at {DB_CONFIG['host']}:{DB_CONFIG['port']}"
    )
    conn = connect_to_db()

    # Show database stats
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM alumno")
    total_students = cursor.fetchone()[0]
    cursor.close()
    print(f"✓ Found {total_students} students in database")

    # Scan for files with alumno IDs
    print(f"\n[SCAN] Scanning directory: {docs_path}")
    print("   Looking for files containing AL{numero_expediente} pattern...\n")
    files_with_ids = scan_files_with_alumno_id(docs_path)

    if not files_with_ids:
        print("[OK] No files containing AL{numero_expediente} pattern found. Migration not needed.")
        conn.close()
        return

    print(f"[OK] Found {len(files_with_ids)} files containing AL{{numero_expediente}} pattern\n")
    stats["files_found"] = len(files_with_ids)

    # Group files by alumno_id
    files_by_alumno = defaultdict(list)
    for file_path, alumno_id, original_filename in files_with_ids:
        files_by_alumno[alumno_id].append((file_path, original_filename))

    print(f"[INFO] Files belong to {len(files_by_alumno)} different students\n")

    # Process each alumno
    for numero_expediente, files in sorted(files_by_alumno.items()):
        print(
            f"\n[ALUMNO] Processing numero_expediente: {numero_expediente} ({len(files)} file{'s' if len(files) > 1 else ''})"
        )

        # Get alumno info from database by numero_expediente (old ID from filename)
        alumno_info = get_alumno_info_by_numero_expediente(conn, numero_expediente)

        if not alumno_info:
            error_msg = f"Alumno with numero_expediente {numero_expediente} not found in database"
            print(f"  [ERROR] {error_msg}")
            errors.append(error_msg)
            stats["errors"] += len(files)
            for file_path, _ in files:
                print(f"    Skipped: {Path(file_path).name}")
            continue

        print(f"  Name: {alumno_info['nombre']} {alumno_info['apellidos']}")

        # Create new folder for alumno
        try:
            alumno_folder = create_alumno_folder(docs_path, alumno_info)
            print(f"  [OK] Folder: {alumno_folder.name}")
        except Exception as e:
            error_msg = f"Error creating folder for Alumno {alumno_id}: {e}"
            print(f"  [ERROR] {error_msg}")
            errors.append(error_msg)
            stats["errors"] += len(files)
            continue

        # Migrate each file
        for file_path, original_filename in files:
            try:
                new_filename, new_full_path = migrate_file(
                    file_path, alumno_folder, original_filename, alumno_id, dry_run
                )

                stats["files_migrated"] += 1

            except Exception as e:
                error_msg = f"Error migrating file {file_path}: {e}"
                print(f"  [ERROR] {error_msg}")
                errors.append(error_msg)
                stats["errors"] += 1

    conn.close()

    # Print summary
    print("\n" + "=" * 80)
    print("MIGRATION SUMMARY")
    print("=" * 80)
    print(f"Files found in old format: {stats['files_found']}")
    print(f"Files successfully migrated: {stats['files_migrated']}")
    print(f"Errors encountered: {stats['errors']}")

    if warnings:
        print(f"\n[WARNING] Warnings ({len(warnings)}):")
        for warning in warnings:
            print(f"  - {warning}")

    if errors:
        print(f"\n[ERROR] Errors ({len(errors)}):")
        for error in errors:
            print(f"  - {error}")

    # Generate report
    generate_report()

    if dry_run:
        print(f"\n[DRY RUN] DRY RUN COMPLETED - No changes were made")
        print("   Run without --dry-run flag to perform actual migration")
    else:
        print(f"\n[OK] MIGRATION COMPLETED")


def generate_report():
    """Generate detailed migration report"""
    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write("=" * 80 + "\n")
        f.write("DOCUMENT MIGRATION REPORT\n")
        f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("=" * 80 + "\n\n")

        f.write("STATISTICS\n")
        f.write("-" * 80 + "\n")
        for key, value in stats.items():
            f.write(f"{key}: {value}\n")

        f.write("\n\nWARNINGS\n")
        f.write("-" * 80 + "\n")
        if warnings:
            for warning in warnings:
                f.write(f"- {warning}\n")
        else:
            f.write("No warnings\n")

        f.write("\n\nERRORS\n")
        f.write("-" * 80 + "\n")
        if errors:
            for error in errors:
                f.write(f"- {error}\n")
        else:
            f.write("No errors\n")

    print(f"\n[REPORT] Detailed report saved to: {REPORT_FILE}")


# =============================================================================
# COMMAND LINE INTERFACE
# =============================================================================


def print_help():
    """Print usage instructions"""
    print(
        """
Document Migration Script - Usage (v2.1)
=========================================

This script finds ANY file containing "AL{numero_expediente}" pattern and associates
it with that student (by numero_expediente), moving it to their organized folder.

IMPORTANT: The AL{number} in filenames is matched to numero_expediente, NOT database ID.
This handles cases where database IDs changed during migration but numero_expediente stayed stable.

Pattern Detection:
    The script looks for AL{numero_expediente} ANYWHERE in the filename:
    - AL7_certificado.pdf -> Student with numero_expediente=7
    - documento_AL15.pdf -> Student with numero_expediente=15
    - foto_AL22_carnet.jpg -> Student with numero_expediente=22

Usage:
    python migrate_documentos.py [OPTIONS]

Options:
    --path PATH       Path to Documentos_Alumnos_Moiskimdo directory
                      Default (Windows): %USERPROFILE%/static_resources/documentos/Documentos_Alumnos_Moiskimdo
                      Default (Linux): /var/www/app/documentos/Documentos_Alumnos_Moiskimdo

    --dry-run         Run in dry-run mode (no actual changes, preview only)

    --help            Show this help message

Examples:
    # Dry run with default path (RECOMMENDED FIRST)
    python migrate_documentos.py --dry-run

    # Actual migration with default path
    python migrate_documentos.py

    # Custom path
    python migrate_documentos.py --path "C:\\custom\\path\\to\\docs"

    # Dry run with custom path
    python migrate_documentos.py --path "C:\\custom\\path\\to\\docs" --dry-run

Configuration:
    Database configuration is read from .env file in the project root.
    If not found, defaults to: localhost:3306/taemoi_test with root/root credentials.

Note: You have databases: taemoi_test (685 alumnos), taemoidb (21 alumnos)
      Update .env to point to the correct database if needed.
"""
    )


def main():
    """Main entry point"""
    import argparse

    parser = argparse.ArgumentParser(
        description="Migrate documents from old to new format", add_help=False
    )
    parser.add_argument(
        "--path",
        type=str,
        default=DEFAULT_DOCS_PATH,
        help="Path to documents directory",
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="Run in dry-run mode (no changes)"
    )
    parser.add_argument(
        "--database",
        type=str,
        help='Database name to use (overrides .env). Use "taemoi_test" for 685 students or "taemoidb" for 21 students',
    )
    parser.add_argument("--help", action="store_true", help="Show help message")

    args = parser.parse_args()

    if args.help:
        print_help()
        sys.exit(0)

    try:
        migrate_documents(args.path, args.dry_run, args.database)
    except KeyboardInterrupt:
        print("\n\n[WARNING] Migration interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n[ERROR] Unexpected error: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
