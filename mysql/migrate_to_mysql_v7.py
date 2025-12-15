#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
migrate_to_mysql_v7.py
----------------------
Complete migration script based on comprehensive field mappings.
Handles all tables with proper field transformations.

IMPORTANT: Accented characters are transliterated to their ASCII equivalents (á->a, é->e, ñ->n, etc.)
and spaces are replaced with underscores. No URL encoding is used.
This ensures documento and imagen URLs work correctly without percent-encoding.

Tables migrated:
  - grado (generated to match InicializadorDatos.java logic)
  - categoria (generated to match InicializadorDatos.java logic)
  - alumno (with all fields, including deprecated multi-sport fields)
  - alumno_deporte (generated from alumno deprecated fields for multi-sport support)
  - imagen (from FOTOS_ALUMNOS)
  - documento (from DOCUMENTOS_VINCULADOS)
  - grupo (generated to match InicializadorDatos.java logic with deporte field)
  - turno (generated to match InicializadorDatos.java logic)
  - alumno_grupo (student-group assignments mapped to predefined grupos)
  - alumno_turno (student-schedule assignments mapped to predefined turnos)
  - producto (from PAGOS unique concepts)
  - producto_alumno (from PAGOS)
  - convocatoria (from EXAMENES unique ID_CONVEXAM)
  - alumno_convocatoria (from EXAMENES)

Important: Grupos and Turnos Generation
----------------------------------------
This script generates SQL INSERT statements for grupos and turnos that match exactly
what InicializadorDatos.java creates. The predefined schedules are:
  - Taekwondo Lunes y Miércoles (3 turnos: 17:00-18:00, 18:00-19:00, 19:00-20:30)
  - Taekwondo Martes y Jueves (3 turnos: 17:00-18:00, 18:00-19:00, 19:00-20:00)
  - Taekwondo Competición (Jueves 20:00-21:30)
  - Pilates Martes y Jueves (10:00-11:15)
  - Kickboxing Lunes y Miércoles (20:30-21:30)
  - Defensa Personal Femenina Lunes y Miércoles (09:30-10:30)

Access database schedules from AUX_GRUPOS are mapped to these predefined schedules
based on day of week, time slots, and activity type.

Important: foto_alumno_id Linking
----------------------------------
After images are inserted, alumno.foto_alumno_id is automatically set to the
corresponding imagen.id by matching numero_expediente. This creates the
relationship: alumno -> imagen (one-to-one).

Process:
1. Insert alumnos with foto_alumno_id = NULL
2. Insert images with nombre = 'alumno_{numero_expediente}'
3. UPDATE alumnos: SET foto_alumno_id = imagen.id WHERE matching numero_expediente

Verification queries are included at the end of the generated SQL file.

Important: SQL File Self-Contained
-----------------------------------
The generated SQL file is completely self-contained and includes all necessary data:
  - Grados, categorias, grupos, and turnos (matching InicializadorDatos.java logic)
  - Alumnos, images, documents from Access database
  - Products and payments from Access database
  - Exam calls and assignments from Access database

You can run the generated SQL file directly without needing to start the backend first.
Note: InicializadorDatos.java uses INSERT IGNORE, so it won't conflict with the migration data.

Usage:
  python migrate_to_mysql_v7.py --access "path/to/database.mdb" --out "output.sql" --schema taemoi_test --env [local|server|docker] --base-url "https://yourdomain.com"

Examples:
  # Local development
  python migrate_to_mysql_v7.py --access "db.mdb" --out "migration.sql" --env local

  # Server deployment
  python migrate_to_mysql_v7.py --access "db.mdb" --out "migration.sql" --env server

  # Docker deployment (uses moiskimdo.es by default)
  python migrate_to_mysql_v7.py --access "db.mdb" --out "migration.sql" --env docker

  # Custom domain
  python migrate_to_mysql_v7.py --access "db.mdb" --out "migration.sql" --env server --base-url "https://custom-domain.com"
"""

import argparse
import os
import sys
import re
from datetime import date, datetime
from typing import Any, Dict, List, Optional, Tuple
from collections import OrderedDict

try:
    import pyodbc
except Exception:
    pyodbc = None

DEFAULT_SCHEMA = os.environ.get("SCHEMA", "taemoi_db")

# ========== ENVIRONMENT CONFIGURATION ==========

ENVIRONMENT_PATHS = {
    "local": {
        "documentos": r"C:\Users\croly\Documents\proyecto_taemoi_csr\static_resources\documentos\Documentos_Alumnos_Moiskimdo",
        "imagenes": r"C:\Users\croly\Documents\proyecto_taemoi_csr\static_resources\imagenes\alumnos"
    },
    "server": {
        "documentos": "/opt/taemoi/static_resources/documentos/Documentos_Alumnos_Moiskimdo",
        "imagenes": "/opt/taemoi/static_resources/imagenes/alumnos"
    },
    "docker": {
        "documentos": "/var/www/app/documentos/Documentos_Alumnos_Moiskimdo",
        "imagenes": "/var/www/app/imagenes/alumnos"
    }
}

ENVIRONMENT_BASE_URLS = {
    "local": "http://localhost:8080",
    "server": "https://moiskimdo.es",
    "docker": "https://moiskimdo.es"
}

def get_image_path(env: str, numero_expediente: int) -> str:
    """Generate image path based on environment and expediente number"""
    base_path = ENVIRONMENT_PATHS.get(env, ENVIRONMENT_PATHS["local"])["imagenes"]
    # Use appropriate path separator for each environment
    sep = "\\" if env == "local" else "/"
    # Images are named as {numero_expediente}.jpg
    return f"{base_path}{sep}{numero_expediente}.jpg"

def get_image_url(env: str, numero_expediente: int) -> str:
    """Generate image URL based on environment and expediente number"""
    base_url = ENVIRONMENT_BASE_URLS.get(env, ENVIRONMENT_BASE_URLS["local"])
    # Images are named by numero_expediente (number only, no encoding needed)
    # URLs always use forward slashes
    # Images are stored in alumnos subdirectory
    return f"{base_url}/imagenes/alumnos/{numero_expediente}.jpg"

def get_documento_folder(env: str, numero_expediente: int, nombre: str, apellidos: str) -> str:
    """Generate documento folder path based on environment"""
    base_path = ENVIRONMENT_PATHS.get(env, ENVIRONMENT_PATHS["local"])["documentos"]
    # Use appropriate path separator for each environment
    sep = "\\" if env == "local" else "/"
    # Folder format: {numero_expediente}_{UPPERCASE_NOMBRE}_{UPPERCASE_APELLIDOS}
    nombre_limpio = clean_filename(nombre)
    apellidos_limpio = clean_filename(apellidos)
    folder_name = f"{numero_expediente}_{nombre_limpio}_{apellidos_limpio}"
    return f"{base_path}{sep}{folder_name}"

def get_documento_url(env: str, numero_expediente: int, nombre: str, apellidos: str, filename: str) -> str:
    """Generate documento URL based on environment and student info (accents removed, no URL encoding)"""
    base_url = ENVIRONMENT_BASE_URLS.get(env, ENVIRONMENT_BASE_URLS["local"])
    # Folder format: {numero_expediente}_{UPPERCASE_NOMBRE}_{UPPERCASE_APELLIDOS}
    # clean_filename removes accents and spaces, so no URL encoding needed
    nombre_limpio = clean_filename(nombre)
    apellidos_limpio = clean_filename(apellidos)
    folder_name = f"{numero_expediente}_{nombre_limpio}_{apellidos_limpio}"
    # Keep filename mostly as-is (just basic cleanup, preserve extension and structure)
    # URLs always use forward slashes
    return f"{base_url}/documentos/Documentos_Alumnos_Moiskimdo/{folder_name}/{filename}"

def get_mime_type_from_filename(filename: str) -> str:
    """Get MIME type from file extension"""
    ext = os.path.splitext(filename)[1].lower()
    mime_types = {
        # Images
        '.jpg': 'image/jpeg',
        '.jpeg': 'image/jpeg',
        '.png': 'image/png',
        '.gif': 'image/gif',
        '.webp': 'image/webp',
        '.bmp': 'image/bmp',
        '.svg': 'image/svg+xml',
        # Documents
        '.pdf': 'application/pdf',
        '.doc': 'application/msword',
        '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        '.xls': 'application/vnd.ms-excel',
        '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        '.txt': 'text/plain',
        '.zip': 'application/zip',
        '.rar': 'application/x-rar-compressed',
    }
    return mime_types.get(ext, 'application/octet-stream')

# ========== HELPER FUNCTIONS ==========

def sql_escape(s: str) -> str:
    return s.replace("'", "''").replace("\\", "\\\\")

def clean_filename(text: str) -> str:
    """Return folder name in ALL UPPERCASE with underscores and accents removed."""
    if not text:
        return ""

    # Normalize Unicode and remove combining marks (accents)
    import unicodedata
    text = unicodedata.normalize('NFD', text)
    text = ''.join(ch for ch in text if unicodedata.category(ch) != 'Mn')

    # Additional explicit accent replacement map for any remaining precomposed characters
    accent_map = str.maketrans({
        'á': 'a', 'é': 'e', 'í': 'i', 'ó': 'o', 'ú': 'u', 'ñ': 'n',
        'Á': 'A', 'É': 'E', 'Í': 'I', 'Ó': 'O', 'Ú': 'U', 'Ñ': 'N',
        'à': 'a', 'è': 'e', 'ì': 'i', 'ò': 'o', 'ù': 'u',
        'À': 'A', 'È': 'E', 'Ì': 'I', 'Ò': 'O', 'Ù': 'U',
        'ä': 'a', 'ë': 'e', 'ï': 'i', 'ö': 'o', 'ü': 'u',
        'Ä': 'A', 'Ë': 'E', 'Ï': 'I', 'Ö': 'O', 'Ü': 'U',
        'â': 'a', 'ê': 'e', 'î': 'i', 'ô': 'o', 'û': 'u',
        'Â': 'A', 'Ê': 'E', 'Î': 'I', 'Ô': 'O', 'Û': 'U',
        'ç': 'c', 'Ç': 'C'
    })
    text = text.translate(accent_map)

    # Remove extra spaces and split by spaces
    words = text.strip().split()

    # Join with underscores
    result = "_".join(words)

    # Remove invalid characters for folder names
    result = re.sub(r'[<>:"/\|?*]', "", result)

    # Collapse duplicate underscores
    result = re.sub(r"_+", "_", result).strip("_")

    return result.upper()

def sql_str_or_null(v: Optional[Any]) -> str:
    if v is None:
        return "NULL"
    s = str(v).strip()
    if s == "" or s.upper() == "NULL":
        return "NULL"
    return "'" + sql_escape(s) + "'"

def sql_num_or_null(v: Optional[Any]) -> str:
    if v is None:
        return "NULL"
    s = str(v).strip().replace(",", ".")
    if s == "" or s.upper() == "NULL":
        return "NULL"
    try:
        float(s)
        return s
    except Exception:
        return "NULL"

def sql_int_or_null(v: Optional[Any]) -> str:
    if v is None:
        return "NULL"
    s = str(v).strip()
    if s == "" or s.upper() == "NULL":
        return "NULL"
    try:
        return str(int(float(s)))
    except Exception:
        return "NULL"

def _parse_date(x: Any) -> Optional[date]:
    if x is None:
        return None
    if isinstance(x, (date, datetime)):
        return date(x.year, x.month, x.day)
    if isinstance(x, str):
        sx = x.strip()
        if not sx or sx.startswith("0000"):
            return None
        for fmt in ("%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%m/%d/%Y", "%d.%m.%Y", "%m-%d-%Y"):
            try:
                return datetime.strptime(sx, fmt).date()
            except Exception:
                continue
    return None

def sql_date_or_null(v: Optional[Any]) -> str:
    d = _parse_date(v)
    if d is None:
        return "NULL"
    return f"'{d.strftime('%Y-%m-%d')}'"

def sql_date_or_fallback(v: Optional[Any], fallback="1900-01-01") -> str:
    d = _parse_date(v)
    if d is None:
        return f"'{fallback}'"
    return f"'{d.strftime('%Y-%m-%d')}'"

def sql_bool(v: Any) -> str:
    """Convert to MySQL BIT(1): 0 or 1"""
    if v is None:
        return "0"
    if isinstance(v, bool):
        return "1" if v else "0"
    s = str(v).strip().lower()
    if s in ("1", "true", "yes", "-1"):
        return "1"
    return "0"

def parse_time(v: Any) -> Optional[str]:
    """Parse time from Access datetime to HH:MM format"""
    if v is None:
        return None
    if isinstance(v, datetime):
        return v.strftime("%H:%M")
    if isinstance(v, str):
        v = v.strip()
        if ":" in v:
            parts = v.split(":")
            if len(parts) >= 2:
                try:
                    return f"{int(parts[0]):02d}:{int(parts[1]):02d}"
                except:
                    pass
    return None

def clean_phone(phone: Optional[Any]) -> Optional[int]:
    """Clean and validate phone number (must be 9 digits)"""
    if phone is None:
        return None
    s = str(phone).strip()
    # Remove spaces, dashes, parentheses, country codes
    s = re.sub(r'[\s\-\(\)\+]', '', s)
    if s.startswith('34'):  # Remove Spain country code
        s = s[2:]
    if s.startswith('0'):
        s = s[1:]
    if len(s) != 9:
        return None
    try:
        num = int(s)
        if 100000000 <= num <= 999999999:
            return num
    except:
        pass
    return None

def clean_nif(nif: Optional[Any]) -> Optional[str]:
    """Clean NIF/DNI to 9 characters uppercase"""
    if nif is None:
        return None
    s = str(nif).strip().upper().replace(" ", "").replace("-", "")
    if len(s) == 8:  # Add trailing X if missing
        s = s + "X"
    if len(s) == 9:
        return s
    return None

def map_tipo_tarifa(tarifa: Optional[Any]) -> str:
    """Map Access tarifa to MySQL TipoTarifa enum"""
    if tarifa is None:
        return "'INFANTIL'"
    s = str(tarifa).strip().upper()

    mapping = {
        "INFANTIL": "INFANTIL",
        "ADULTO": "ADULTO",
        "ADULTO GRUPO": "ADULTO_GRUPO",
        "FAMILIAR": "FAMILIAR",
        "HERMANOS": "HERMANOS",
        "INFANTIL GRUPO": "INFANTIL_GRUPO",
        "PADRES HIJOS": "PADRES_HIJOS",
        "PILATES": "PILATES",
        "DEFENSA PERSONAL FEMENINA": "DEFENSA_PERSONAL_FEMENINA",
    }

    for key, value in mapping.items():
        if key in s:
            return f"'{value}'"

    return "'INFANTIL'"

def map_rol_familiar(rol: Optional[Any]) -> str:
    """Map Access rol familiar to MySQL RolFamiliar enum"""
    if rol is None:
        return "'NINGUNO'"
    s = str(rol).strip().upper()

    mapping = {
        "PADRE": "PADRE",
        "MADRE": "PADRE",  # Map MADRE to PADRE (parent role)
        "HIJO": "HIJO",
        "HIJA": "HIJO",  # Map HIJA to HIJO (child role)
        "NINGUNO": "NINGUNO",
    }

    for key, value in mapping.items():
        if key in s:
            return f"'{value}'"

    return "'NINGUNO'"

def map_grado_to_tipo(grado: Optional[Any]) -> Optional[str]:
    """Map Access grado text to MySQL TipoGrado enum"""
    if grado is None:
        return None
    s = str(grado).strip().upper()

    mapping = {
        "BLANCO": "BLANCO",
        "BLANCO-AMARILLO": "BLANCO_AMARILLO",
        "BLANCO AMARILLO": "BLANCO_AMARILLO",
        "AMARILLO": "AMARILLO",
        "AMARILLO-NARANJA": "AMARILLO_NARANJA",
        "AMARILLO NARANJA": "AMARILLO_NARANJA",
        "NARANJA": "NARANJA",
        "NARANJA-VERDE": "NARANJA_VERDE",
        "NARANJA VERDE": "NARANJA_VERDE",
        "VERDE": "VERDE",
        "VERDE-AZUL": "VERDE_AZUL",
        "VERDE AZUL": "VERDE_AZUL",
        "AZUL": "AZUL",
        "AZUL-ROJO": "AZUL_ROJO",
        "AZUL ROJO": "AZUL_ROJO",
        "ROJO": "ROJO",
        "ROJO-NEGRO 1": "ROJO_NEGRO_1_PUM",
        "ROJO NEGRO 1": "ROJO_NEGRO_1_PUM",
        "1 PUM": "ROJO_NEGRO_1_PUM",
        "1º PUM": "ROJO_NEGRO_1_PUM",
        "ROJO-NEGRO 2": "ROJO_NEGRO_2_PUM",
        "ROJO NEGRO 2": "ROJO_NEGRO_2_PUM",
        "2 PUM": "ROJO_NEGRO_2_PUM",
        "2º PUM": "ROJO_NEGRO_2_PUM",
        "ROJO-NEGRO 3": "ROJO_NEGRO_3_PUM",
        "ROJO NEGRO 3": "ROJO_NEGRO_3_PUM",
        "3 PUM": "ROJO_NEGRO_3_PUM",
        "3º PUM": "ROJO_NEGRO_3_PUM",
        "1 DAN": "NEGRO_1_DAN",
        "1º DAN": "NEGRO_1_DAN",
        "2 DAN": "NEGRO_2_DAN",
        "2º DAN": "NEGRO_2_DAN",
        "3 DAN": "NEGRO_3_DAN",
        "3º DAN": "NEGRO_3_DAN",
        "4 DAN": "NEGRO_4_DAN",
        "4º DAN": "NEGRO_4_DAN",
        "5 DAN": "NEGRO_5_DAN",
        "5º DAN": "NEGRO_5_DAN",
    }

    for key, value in mapping.items():
        if key in s:
            return f"'{value}'"

    return None

def map_dia_semana(dia: Optional[Any]) -> Optional[str]:
    """Map day to Spanish day name"""
    if dia is None:
        return None

    # If it's a number (1-7), convert to day name
    try:
        day_num = int(str(dia).strip())
        dias = {
            1: "Lunes",
            2: "Martes",
            3: "Miércoles",
            4: "Jueves",
            5: "Viernes",
            6: "Sábado",
            7: "Domingo"
        }
        return dias.get(day_num)
    except:
        pass

    # Already text, normalize
    s = str(dia).strip().title()
    return s if s else None

def fk_alumno_subquery(schema: str, expediente: Optional[Any]) -> Tuple[str, bool]:
    """Returns (SQL subquery, is_orphan)"""
    if expediente is None:
        return "NULL", True
    s = str(expediente).strip()
    if s == "" or s == "0":
        return "NULL", True
    try:
        exp_int = int(float(s))
        if exp_int <= 0:
            return "NULL", True
    except Exception:
        return "NULL", True
    return f"(SELECT id FROM `{schema}`.`alumno` WHERE `numero_expediente` = {exp_int} LIMIT 1)", False

def fk_grado_subquery(schema: str, grado_text: Optional[Any]) -> str:
    """Returns SQL subquery to find grado_id by tipo_grado"""
    mapped = map_grado_to_tipo(grado_text)
    if mapped is None:
        return "NULL"
    return f"(SELECT id FROM `{schema}`.`grado` WHERE `tipo_grado` = {mapped} LIMIT 1)"

# ========== DATABASE CONNECTION ==========

def open_access(path: str):
    if pyodbc is None:
        raise RuntimeError("pyodbc not installed")
    if not os.path.exists(path):
        raise FileNotFoundError(path)

    drivers = pyodbc.drivers()
    drv = None
    for cand in ("Microsoft Access Driver (*.mdb, *.accdb)", "Microsoft Access Driver (*.mdb)"):
        if cand in drivers:
            drv = cand
            break
    if drv is None:
        if drivers:
            drv = drivers[-1]
        else:
            raise RuntimeError("No ODBC driver for Access")

    return pyodbc.connect(f"DRIVER={{{drv}}};DBQ={path};")

def fetch_all(conn, table: str) -> List[Dict[str, Any]]:
    cursor = conn.cursor()
    try:
        cursor.execute(f"SELECT * FROM [{table}]")
    except Exception as e:
        print(f"Warning: Could not read table {table}: {e}", file=sys.stderr)
        return []

    cols = [c[0] for c in cursor.description]
    rows = []
    for r in cursor.fetchall():
        row_dict = {}
        for i, col in enumerate(cols):
            row_dict[col] = getattr(r, col) if hasattr(r, col) else r[i]
        rows.append(row_dict)
    return rows

# ========== MIGRATION LOGIC ==========

def write_grados(f, schema: str):
    """Generate grado INSERT statements (matching InicializadorDatos logic)"""
    f.write("-- ===== GRADO (generated to match InicializadorDatos.java) =====\n")

    grados = [
        "BLANCO", "BLANCO_AMARILLO", "AMARILLO", "AMARILLO_NARANJA",
        "NARANJA", "NARANJA_VERDE", "VERDE", "VERDE_AZUL",
        "AZUL", "AZUL_ROJO", "ROJO",
        "ROJO_NEGRO_1_PUM", "ROJO_NEGRO_2_PUM", "ROJO_NEGRO_3_PUM",
        "NEGRO_1_DAN", "NEGRO_2_DAN", "NEGRO_3_DAN", "NEGRO_4_DAN", "NEGRO_5_DAN"
    ]

    for grado in grados:
        f.write(f"INSERT IGNORE INTO `{schema}`.`grado` (`tipo_grado`) VALUES ('{grado}');\n")

    f.write("\n")

def write_categorias(f, schema: str):
    """Generate categoria INSERT statements (matching InicializadorDatos logic)"""
    f.write("-- ===== CATEGORIA (generated to match InicializadorDatos.java) =====\n")

    categorias = [
        ("Precadete", "PRECADETE"),
        ("Infantil", "INFANTIL"),
        ("Cadete", "CADETE"),
        ("Junior", "JUNIOR"),
        ("Senior", "SENIOR")
    ]

    for nombre, tipo in categorias:
        f.write(f"INSERT IGNORE INTO `{schema}`.`categoria` (`nombre`, `tipo_categoria`) VALUES ('{nombre}', '{tipo}');\n")

    f.write("\n")

def write_alumnos(f, schema: str, rows: List[Dict[str, Any]], stats: Dict):
    """Migrate ALUMNOS table"""
    f.write("-- ===== ALUMNO =====\n")

    processed = 0
    skipped = 0
    seen_expedientes = set()

    for r in rows:
        # numero_expediente (required, unique)
        exp = r.get("Exp")
        if exp is None or str(exp).strip() in ("", "0"):
            skipped += 1
            continue

        try:
            exp_int = int(float(str(exp).strip()))
            if exp_int <= 0 or exp_int in seen_expedientes:
                skipped += 1
                continue
            seen_expedientes.add(exp_int)
        except:
            skipped += 1
            continue

        # Required fields
        nombre = sql_str_or_null(r.get("Nombre"))
        if nombre == "NULL":
            nombre = "'Sin Nombre'"

        apellidos = sql_str_or_null(r.get("Apellidos"))
        if apellidos == "NULL":
            apellidos = "'Sin Apellidos'"

        # NIF (required, 9 chars)
        nif_clean = clean_nif(r.get("DNI") or r.get("NIF"))
        nif = sql_str_or_null(nif_clean) if nif_clean else "'00000000Z'"

        # Direccion (required)
        direccion = sql_str_or_null(r.get("Direccion"))
        if direccion == "NULL":
            direccion = "'Sin dirección'"

        # Telefono (required INT 9 digits)
        phone = clean_phone(r.get("Telefono_movil")) or clean_phone(r.get("Telefono_fijo"))
        telefono = str(phone) if phone else "600000000"

        # Email (optional)
        email = sql_str_or_null(r.get("Email"))

        # Fecha nacimiento (required, fallback to 1900-01-01)
        fecha_nacimiento = sql_date_or_fallback(r.get("Fnac"))

        # Tipo tarifa y cuantia
        tipo_tarifa = map_tipo_tarifa(r.get("Tarifa"))
        cuantia_tarifa = sql_num_or_null(r.get("Cuota"))
        if cuantia_tarifa == "NULL":
            cuantia_tarifa = "0"

        # Rol familiar y grupo familiar (new fields for family grouping)
        rol_familiar = map_rol_familiar(r.get("Rol_Familiar") or r.get("RolFamiliar"))
        grupo_familiar = sql_str_or_null(r.get("Grupo_Familiar") or r.get("GrupoFamiliar"))

        # Dates
        fecha_alta = sql_date_or_null(r.get("Fecha_Alta"))
        fecha_alta_inicial = fecha_alta  # Set fechaAltaInicial the same as fechaAlta during migration
        fecha_baja = sql_date_or_null(r.get("Fecha_baja"))
        fecha_grado = sql_date_or_null(r.get("Fgrado"))
        fecha_licencia = sql_date_or_null(r.get("Fecha_licencia"))
        fecha_peso = sql_date_or_null(r.get("Fecha_Peso"))

        # Booleans (Baja is inverted to activo)
        baja = r.get("Baja")
        activo = "0" if sql_bool(baja) == "1" else "1"

        autorizacion_web = sql_bool(r.get("Autorizacion_foto_web"))

        # License
        numero_licencia = sql_int_or_null(r.get("N_licencia"))
        tiene_licencia = "1" if numero_licencia != "NULL" and numero_licencia != "0" else "0"

        # Physical data
        peso = sql_num_or_null(r.get("Peso"))

        # Grado FK
        grado_id = fk_grado_subquery(schema, r.get("Grado"))

        # Defaults
        competidor = "0"
        deporte = "'TAEKWONDO'"
        categoria_id = "NULL"
        tiene_discapacidad = "0"
        apto_para_examen = "NULL"
        tiene_derecho_examen = "0"
        foto_alumno_id = "NULL"

        # Build INSERT
        cols = [
            "numero_expediente", "nombre", "apellidos", "nif", "direccion",
            "telefono", "email", "fecha_nacimiento",
            "tipo_tarifa", "cuantia_tarifa", "rol_familiar", "grupo_familiar",
            "fecha_alta", "fecha_alta_inicial", "fecha_baja", "activo",
            "autorizacion_web", "competidor",
            "peso", "fecha_peso",
            "tiene_licencia", "numero_licencia", "fecha_licencia",
            "deporte", "categoria_id", "grado_id", "fecha_grado",
            "apto_para_examen", "tiene_derecho_examen", "tiene_discapacidad",
            "foto_alumno_id"
        ]

        vals = [
            str(exp_int), nombre, apellidos, nif, direccion,
            telefono, email, fecha_nacimiento,
            tipo_tarifa, cuantia_tarifa, rol_familiar, grupo_familiar,
            fecha_alta, fecha_alta_inicial, fecha_baja, activo,
            autorizacion_web, competidor,
            peso, fecha_peso,
            tiene_licencia, numero_licencia, fecha_licencia,
            deporte, categoria_id, grado_id, fecha_grado,
            apto_para_examen, tiene_derecho_examen, tiene_discapacidad,
            foto_alumno_id
        ]

        f.write(f"INSERT INTO `{schema}`.`alumno` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join(vals)});\n")
        processed += 1

    stats["alumno"] = {"processed": processed, "skipped": skipped}
    f.write(f"\n-- Processed: {processed}, Skipped: {skipped}\n\n")

def write_alumno_deporte(f, schema: str, stats: Dict):
    """
    Generate alumno_deporte records from deprecated alumno fields.
    Migrates all per-sport fields from alumno to alumno_deporte:
    - Sport and grade data: deporte, grado_id, fecha_grado, apto_para_examen
    - Tarifa data: tipo_tarifa, cuantia_tarifa, rol_familiar, grupo_familiar
    - Competitor data: competidor, peso, fecha_peso
    - License data: tiene_licencia, numero_licencia, fecha_licencia
    - Dates: fecha_alta, fecha_alta_inicial, fecha_baja, activo
    """
    f.write("-- =====================================================\n")
    f.write("-- ALUMNO_DEPORTE (Multi-sport migration)\n")
    f.write("-- Migrates deprecated fields from alumno table\n")
    f.write("-- Includes all per-sport fields for complete migration\n")
    f.write("-- =====================================================\n\n")

    f.write(f"INSERT INTO `{schema}`.`alumno_deporte` \n")
    f.write("  (`alumno_id`, `deporte`, `grado_id`, `fecha_grado`, `fecha_alta`, `fecha_alta_inicial`, \n")
    f.write("   `apto_para_examen`, `activo`, `fecha_baja`, \n")
    f.write("   `tipo_tarifa`, `cuantia_tarifa`, `rol_familiar`, `grupo_familiar`, \n")
    f.write("   `competidor`, `peso`, `fecha_peso`, \n")
    f.write("   `tiene_licencia`, `numero_licencia`, `fecha_licencia`)\n")
    f.write("SELECT \n")
    f.write("  a.id,\n")
    f.write("  a.deporte,\n")
    f.write("  a.grado_id,\n")
    f.write("  a.fecha_grado,\n")
    f.write("  a.fecha_alta,\n")
    f.write("  COALESCE(a.fecha_alta_inicial, a.fecha_alta) as fecha_alta_inicial,\n")
    f.write("  COALESCE(a.apto_para_examen, false) as apto_para_examen,\n")
    f.write("  COALESCE(a.activo, true) as activo,\n")
    f.write("  a.fecha_baja,\n")
    f.write("  a.tipo_tarifa,\n")
    f.write("  a.cuantia_tarifa,\n")
    f.write("  a.rol_familiar,\n")
    f.write("  a.grupo_familiar,\n")
    f.write("  COALESCE(a.competidor, false) as competidor,\n")
    f.write("  a.peso,\n")
    f.write("  a.fecha_peso,\n")
    f.write("  COALESCE(a.tiene_licencia, false) as tiene_licencia,\n")
    f.write("  a.numero_licencia,\n")
    f.write("  a.fecha_licencia\n")
    f.write(f"FROM `{schema}`.`alumno` a\n")
    f.write("WHERE a.deporte IS NOT NULL;\n\n")

    f.write("-- Migrated alumno_deporte records from deprecated alumno fields\n")
    f.write("-- NOTE: After migration, alumno legacy fields should be set to NULL for multi-sport mode\n\n")
    stats["alumno_deporte"] = {"note": "Generated from alumno SELECT-INSERT with all per-sport fields"}

def write_imagenes(f, schema: str, rows: List[Dict[str, Any]], stats: Dict, env: str):
    """Migrate FOTOS_ALUMNOS to imagen table"""
    f.write("-- ===== IMAGEN (fotos de alumnos) =====\n")

    processed = 0
    orphans = 0

    for r in rows:
        exp = r.get("Exp")
        fk_sql, is_orphan = fk_alumno_subquery(schema, exp)

        if is_orphan:
            orphans += 1
            continue

        # Generate nombre from expediente
        try:
            exp_int = int(float(str(exp).strip()))
            nombre = f"alumno_{exp_int}"
        except:
            continue

        # Generate ruta and URL based on environment
        # Image format: {numero_expediente}.jpg (assuming jpg, could be png)
        # Check if it's jpg or png from Access data if available
        image_filename = f"{exp_int}.jpg"  # Default to jpg
        ruta = get_image_path(env, exp_int)
        url = get_image_url(env, exp_int)

        # Get MIME type from filename
        tipo = get_mime_type_from_filename(image_filename)

        cols = ["nombre", "ruta", "tipo", "url"]
        vals = [sql_str_or_null(nombre), sql_str_or_null(ruta), sql_str_or_null(tipo), sql_str_or_null(url)]

        f.write(f"INSERT INTO `{schema}`.`imagen` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join(vals)});\n")
        processed += 1

    # Update alumno.foto_alumno_id to link photos to students
    if processed > 0:
        f.write("\n-- Link photos to alumnos (set foto_alumno_id to imagen.id)\n")
        f.write(f"UPDATE `{schema}`.`alumno` a\n")
        f.write(f"INNER JOIN `{schema}`.`imagen` i ON i.`nombre` = CONCAT('alumno_', a.`numero_expediente`)\n")
        f.write(f"SET a.`foto_alumno_id` = i.`id`\n")
        f.write(f"WHERE i.`tipo` LIKE 'image/%' AND a.`foto_alumno_id` IS NULL;\n")
        f.write("\n")

        # Add verification query to show linking results
        f.write("-- Verification: Count students with photos linked\n")
        f.write(f"-- SELECT COUNT(*) as alumnos_con_foto FROM `{schema}`.`alumno` WHERE `foto_alumno_id` IS NOT NULL;\n")
        f.write(f"-- SELECT COUNT(*) as alumnos_sin_foto FROM `{schema}`.`alumno` WHERE `foto_alumno_id` IS NULL;\n")
        f.write("\n")

    stats["imagen"] = {"processed": processed, "orphans": orphans}
    f.write(f"\n-- Processed: {processed}, Orphans: {orphans}\n\n")

def write_documentos(f, schema: str, rows: List[Dict[str, Any]], alumnos: List[Dict[str, Any]], stats: Dict, env: str):
    """Migrate DOCUMENTOS_VINCULADOS to documento table"""
    f.write("-- ===== DOCUMENTO =====\n")

    # Build alumno lookup: exp -> (nombre, apellidos)
    alumno_lookup = {}
    for alumno in alumnos:
        exp = alumno.get("Exp")
        if exp:
            try:
                exp_int = int(float(str(exp).strip()))
                nombre = str(alumno.get("Nombre", "Sin_Nombre")).strip()
                apellidos = str(alumno.get("Apellidos", "Sin_Apellidos")).strip()
                alumno_lookup[exp_int] = (nombre, apellidos)
            except:
                continue

    processed = 0
    orphans = 0

    for r in rows:
        exp = r.get("ID_EXPEDIENTES") or r.get("Exp")
        fk_sql, is_orphan = fk_alumno_subquery(schema, exp)

        if is_orphan:
            orphans += 1
            continue

        try:
            exp_int = int(float(str(exp).strip()))
        except:
            orphans += 1
            continue

        # Get original document path/filename from Access
        original_ruta = r.get("DOC_VINCULADO") or r.get("VINCULO_DOC") or ""

        # Extract just the filename if it's a full path
        if original_ruta:
            # Handle both Windows and Unix paths
            filename = os.path.basename(original_ruta.replace("\\", "/"))
        else:
            # Fallback: use NUM_REGISTRO or generate a default name
            doc_registro = r.get("NUM_REGISTRO") or f"documento_{r.get('ID_VINCULOS', 'x')}"
            filename = f"{doc_registro}.pdf"  # Default extension

        # Clean filename to remove accents and special characters (preserve extension)
        if filename:
            # Split filename into name and extension
            name_part, ext_part = os.path.splitext(filename)
            # Clean the name part (remove accents, spaces, etc.)
            name_clean = clean_filename(name_part)
            # Reconstruct filename with cleaned name and original extension
            filename_clean = f"{name_clean}{ext_part.lower()}"
        else:
            filename_clean = filename

        # Use the cleaned filename (without extension) as the document name
        doc_nombre = os.path.splitext(filename_clean)[0]

        # Construct folder path and URL based on environment and student info
        if exp_int in alumno_lookup:
            nombre, apellidos = alumno_lookup[exp_int]
            folder_path = get_documento_folder(env, exp_int, nombre, apellidos)
            # Use appropriate path separator for each environment
            sep = "\\" if env == "local" else "/"
            ruta = f"{folder_path}{sep}{filename_clean}"
            # Generate URL
            url = get_documento_url(env, exp_int, nombre, apellidos, filename_clean)
        else:
            # Fallback if student not found
            ruta = original_ruta
            url = None

        # Get MIME type from cleaned filename
        tipo = get_mime_type_from_filename(filename_clean)

        cols = ["nombre", "ruta", "tipo", "url", "alumno_id"]
        vals = [sql_str_or_null(doc_nombre), sql_str_or_null(ruta), sql_str_or_null(tipo), sql_str_or_null(url) if url else "NULL", fk_sql]

        f.write(f"INSERT INTO `{schema}`.`documento` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join(vals)});\n")
        processed += 1

    stats["documento"] = {"processed": processed, "orphans": orphans}
    f.write(f"\n-- Processed: {processed}, Orphans: {orphans}\n\n")

def map_to_predefined_grupo(dia: Optional[str], hora_inicio: Optional[str], hora_fin: Optional[str],
                             old_grupo: Optional[str]) -> Optional[str]:
    """
    Map Access database schedule data to predefined grupo names from InicializadorDatos.

    Predefined grupos and their schedules:
    - Taekwondo Lunes y Miércoles Primer Turno: Lunes/Miércoles 17:00-18:00
    - Taekwondo Lunes y Miércoles Segundo Turno: Lunes/Miércoles 18:00-19:00
    - Taekwondo Lunes y Miércoles Tercer Turno: Lunes/Miércoles 19:00-20:30
    - Taekwondo Martes y Jueves Primer Turno: Martes/Jueves 17:00-18:00
    - Taekwondo Martes y Jueves Segundo Turno: Martes/Jueves 18:00-19:00
    - Taekwondo Martes y Jueves Tercer Turno: Martes/Jueves 19:00-20:00
    - Taekwondo Competición: Jueves 20:00-21:30
    - Pilates Martes y Jueves: Martes/Jueves 10:00-11:15
    - Kickboxing Lunes y Miércoles: Lunes/Miércoles 20:30-21:30
    - Defensa Personal Femenina Lunes y Miércoles: Lunes/Miércoles 09:30-10:30
    """
    if not dia or not hora_inicio or not hora_fin:
        return None

    dia = dia.strip()
    old_grupo_upper = old_grupo.upper() if old_grupo else ""

    # Normalize day names
    dia_normalized = dia.lower()

    # Defensa Personal Femenina
    if "DEFENSA" in old_grupo_upper or "FEMENINA" in old_grupo_upper:
        if dia_normalized in ["lunes", "miércoles", "miercoles"] and hora_inicio == "09:30" and hora_fin == "10:30":
            return "Defensa Personal Femenina Lunes y Miércoles"

    # Pilates
    if "PILATES" in old_grupo_upper:
        if dia_normalized in ["martes", "jueves"] and hora_inicio == "10:00" and hora_fin == "11:15":
            return "Pilates Martes y Jueves"

    # Kickboxing
    if "KICK" in old_grupo_upper or "KBX" in old_grupo_upper:
        if dia_normalized in ["lunes", "miércoles", "miercoles"] and hora_inicio == "20:30" and hora_fin == "21:30":
            return "Kickboxing Lunes y Miércoles"

    # Taekwondo Competición
    if "COMPETICION" in old_grupo_upper or "COMPETICIÓN" in old_grupo_upper:
        if dia_normalized == "jueves" and hora_inicio == "20:00" and hora_fin == "21:30":
            return "Taekwondo Competición"

    # Taekwondo - Lunes y Miércoles
    if dia_normalized in ["lunes", "miércoles", "miercoles"]:
        if hora_inicio == "17:00" and hora_fin == "18:00":
            return "Taekwondo Lunes y Miércoles Primer Turno"
        elif hora_inicio == "18:00" and hora_fin == "19:00":
            return "Taekwondo Lunes y Miércoles Segundo Turno"
        elif hora_inicio == "19:00" and hora_fin == "20:30":
            return "Taekwondo Lunes y Miércoles Tercer Turno"

    # Taekwondo - Martes y Jueves
    if dia_normalized in ["martes", "jueves"]:
        if hora_inicio == "17:00" and hora_fin == "18:00":
            return "Taekwondo Martes y Jueves Primer Turno"
        elif hora_inicio == "18:00" and hora_fin == "19:00":
            return "Taekwondo Martes y Jueves Segundo Turno"
        elif hora_inicio == "19:00" and hora_fin == "20:00":
            return "Taekwondo Martes y Jueves Tercer Turno"

    # Default fallback: try to infer from old grupo name and time
    # This handles cases where the schedule might not perfectly match
    if "TAEKWONDO" in old_grupo_upper or old_grupo_upper == "":
        if dia_normalized in ["lunes", "miércoles", "miercoles"]:
            # Try to map based on approximate time ranges
            hora_int = int(hora_inicio.split(":")[0])
            if 16 <= hora_int < 18:
                return "Taekwondo Lunes y Miércoles Primer Turno"
            elif 18 <= hora_int < 19:
                return "Taekwondo Lunes y Miércoles Segundo Turno"
            elif 19 <= hora_int < 21:
                return "Taekwondo Lunes y Miércoles Tercer Turno"
        elif dia_normalized in ["martes", "jueves"]:
            hora_int = int(hora_inicio.split(":")[0])
            if 16 <= hora_int < 18:
                return "Taekwondo Martes y Jueves Primer Turno"
            elif 18 <= hora_int < 19:
                return "Taekwondo Martes y Jueves Segundo Turno"
            elif 19 <= hora_int < 21:
                return "Taekwondo Martes y Jueves Tercer Turno"

    return None

def write_grupos_turnos_relations(f, schema: str, aux_grupos: List[Dict[str, Any]], stats: Dict):
    """
    Generate grupos and turnos INSERT statements (matching InicializadorDatos logic),
    then assign alumnos to them based on Access database data.
    """

    # ===== GENERATE GRUPOS (matching InicializadorDatos.java) =====
    f.write("-- ===== GRUPO (generated to match InicializadorDatos.java) =====\n")

    # Format: (nombre, tipo, deporte_enum)
    predefined_grupos = [
        ("Taekwondo Lunes y Miércoles Primer Turno", "Taekwondo", "TAEKWONDO"),
        ("Taekwondo Lunes y Miércoles Segundo Turno", "Taekwondo", "TAEKWONDO"),
        ("Taekwondo Lunes y Miércoles Tercer Turno", "Taekwondo", "TAEKWONDO"),
        ("Taekwondo Martes y Jueves Primer Turno", "Taekwondo", "TAEKWONDO"),
        ("Taekwondo Martes y Jueves Segundo Turno", "Taekwondo", "TAEKWONDO"),
        ("Taekwondo Martes y Jueves Tercer Turno", "Taekwondo", "TAEKWONDO"),
        ("Taekwondo Competición", "Taekwondo Competición", "TAEKWONDO"),
        ("Pilates Martes y Jueves", "Pilates", "PILATES"),
        ("Kickboxing Lunes y Miércoles", "Kickboxing", "KICKBOXING"),
        ("Defensa Personal Femenina Lunes y Miércoles", "Defensa Personal Femenina", "DEFENSA_PERSONAL_FEMENINA"),
    ]

    for nombre, tipo, deporte in predefined_grupos:
        f.write(f"INSERT IGNORE INTO `{schema}`.`grupo` (`nombre`, `tipo`, `deporte`) VALUES ('{sql_escape(nombre)}', '{sql_escape(tipo)}', '{deporte}');\n")

    f.write(f"\n-- Generated: {len(predefined_grupos)} grupos\n\n")

    # ===== GENERATE TURNOS (matching InicializadorDatos.java) =====
    f.write("-- ===== TURNO (generated to match InicializadorDatos.java) =====\n")

    # Define turnos exactly as in InicializadorDatos.java
    predefined_turnos = [
        # Lunes
        ("Lunes", "09:30", "10:30", "Defensa Personal Femenina Lunes y Miércoles", "Defensa Personal Femenina Lunes"),
        ("Lunes", "17:00", "18:00", "Taekwondo Lunes y Miércoles Primer Turno", "Taekwondo Primer Turno Lunes"),
        ("Lunes", "18:00", "19:00", "Taekwondo Lunes y Miércoles Segundo Turno", "Taekwondo Segundo Turno Lunes"),
        ("Lunes", "19:00", "20:30", "Taekwondo Lunes y Miércoles Tercer Turno", "Taekwondo Tercer Turno Lunes"),
        ("Lunes", "20:30", "21:30", "Kickboxing Lunes y Miércoles", "Kickboxing Lunes"),
        # Martes
        ("Martes", "10:00", "11:15", "Pilates Martes y Jueves", "Pilates Martes"),
        ("Martes", "17:00", "18:00", "Taekwondo Martes y Jueves Primer Turno", "Taekwondo Primer Turno Martes"),
        ("Martes", "18:00", "19:00", "Taekwondo Martes y Jueves Segundo Turno", "Taekwondo Segundo Turno Martes"),
        ("Martes", "19:00", "20:00", "Taekwondo Martes y Jueves Tercer Turno", "Taekwondo Tercer Turno Martes"),
        # Miércoles
        ("Miércoles", "09:30", "10:30", "Defensa Personal Femenina Lunes y Miércoles", "Defensa Personal Femenina Miércoles"),
        ("Miércoles", "17:00", "18:00", "Taekwondo Lunes y Miércoles Primer Turno", "Taekwondo Primer Turno Miércoles"),
        ("Miércoles", "18:00", "19:00", "Taekwondo Lunes y Miércoles Segundo Turno", "Taekwondo Segundo Turno Miércoles"),
        ("Miércoles", "19:00", "20:30", "Taekwondo Lunes y Miércoles Tercer Turno", "Taekwondo Tercer Turno Miércoles"),
        ("Miércoles", "20:30", "21:30", "Kickboxing Lunes y Miércoles", "Kickboxing Miércoles"),
        # Jueves
        ("Jueves", "10:00", "11:15", "Pilates Martes y Jueves", "Pilates Jueves"),
        ("Jueves", "17:00", "18:00", "Taekwondo Martes y Jueves Primer Turno", "Taekwondo Primer Turno Jueves"),
        ("Jueves", "18:00", "19:00", "Taekwondo Martes y Jueves Segundo Turno", "Taekwondo Segundo Turno Jueves"),
        ("Jueves", "19:00", "20:00", "Taekwondo Martes y Jueves Tercer Turno", "Taekwondo Tercer Turno Jueves"),
        ("Jueves", "20:00", "21:30", "Taekwondo Competición", "Taekwondo Competición"),
    ]

    for dia, hora_inicio, hora_fin, grupo_nombre, tipo in predefined_turnos:
        grupo_fk = f"(SELECT id FROM `{schema}`.`grupo` WHERE `nombre` = '{sql_escape(grupo_nombre)}' LIMIT 1)"
        f.write(f"INSERT IGNORE INTO `{schema}`.`turno` (`dia_semana`, `hora_inicio`, `hora_fin`, `tipo`, `grupo_id`) ")
        f.write(f"VALUES ('{dia}', '{hora_inicio}', '{hora_fin}', '{sql_escape(tipo)}', {grupo_fk});\n")

    f.write(f"\n-- Generated: {len(predefined_turnos)} turnos\n\n")

    # ===== ASSIGN ALUMNOS TO GRUPOS AND TURNOS =====
    f.write("-- ===== ALUMNO ASSIGNMENTS TO GRUPOS AND TURNOS =====\n")

    # Map Access data to predefined grupos
    alumno_grupo_map = {}  # exp -> set of grupo names
    alumno_turno_map = {}  # exp -> set of (grupo_name, dia, hora_inicio, hora_fin)

    unmapped_count = 0
    for r in aux_grupos:
        exp = r.get("Exp")
        if not exp:
            continue

        dia = map_dia_semana(r.get("DIA") or r.get("DiaSemana"))
        hora_inicio = parse_time(r.get("HoraInicio"))
        hora_fin = parse_time(r.get("HoraFin"))
        old_grupo = str(r.get("Turno", "")).strip()

        # Map to predefined grupo
        grupo_name = map_to_predefined_grupo(dia, hora_inicio, hora_fin, old_grupo)

        if not grupo_name:
            unmapped_count += 1
            continue

        # Track grupo assignments
        if exp not in alumno_grupo_map:
            alumno_grupo_map[exp] = set()
        alumno_grupo_map[exp].add(grupo_name)

        # Track turno assignments (grupo + specific schedule)
        if exp not in alumno_turno_map:
            alumno_turno_map[exp] = set()
        if dia and hora_inicio and hora_fin:
            alumno_turno_map[exp].add((grupo_name, dia, hora_inicio, hora_fin))

    # Write alumno_grupo relationships
    f.write("-- ===== ALUMNO_GRUPO =====\n")
    ag_count = 0
    ag_orphans = 0

    for exp, grupo_names in alumno_grupo_map.items():
        fk_alumno, is_orphan = fk_alumno_subquery(schema, exp)
        if is_orphan:
            ag_orphans += len(grupo_names)
            continue

        for grupo_name in grupo_names:
            grupo_fk = f"(SELECT id FROM `{schema}`.`grupo` WHERE `nombre` = '{sql_escape(grupo_name)}' LIMIT 1)"
            f.write(f"INSERT IGNORE INTO `{schema}`.`alumno_grupo` (`alumno_id`, `grupo_id`) VALUES ({fk_alumno}, {grupo_fk});\n")
            ag_count += 1

    f.write(f"\n-- Processed: {ag_count}, Orphans: {ag_orphans}\n\n")

    # Write alumno_turno relationships
    f.write("-- ===== ALUMNO_TURNO =====\n")
    at_count = 0
    at_orphans = 0

    for exp, turno_data in alumno_turno_map.items():
        fk_alumno, is_orphan = fk_alumno_subquery(schema, exp)
        if is_orphan:
            at_orphans += len(turno_data)
            continue

        for grupo_name, dia, hora_inicio, hora_fin in turno_data:
            # Find turno by matching grupo, dia, and horas
            turno_fk = f"""(SELECT t.id FROM `{schema}`.`turno` t
            JOIN `{schema}`.`grupo` g ON t.`grupo_id` = g.`id`
            WHERE g.`nombre` = '{sql_escape(grupo_name)}'
            AND t.`dia_semana` = '{dia}'
            AND t.`hora_inicio` = '{hora_inicio}'
            AND t.`hora_fin` = '{hora_fin}'
            LIMIT 1)"""

            f.write(f"INSERT IGNORE INTO `{schema}`.`alumno_turno` (`alumno_id`, `turno_id`) VALUES ({fk_alumno}, {turno_fk});\n")
            at_count += 1

    f.write(f"\n-- Processed: {at_count}, Orphans: {at_orphans}, Unmapped schedules: {unmapped_count}\n\n")

    stats["grupo"] = {"generated": len(predefined_grupos)}
    stats["turno"] = {"generated": len(predefined_turnos)}
    stats["alumno_grupo"] = {"processed": ag_count, "orphans": ag_orphans}
    stats["alumno_turno"] = {"processed": at_count, "orphans": at_orphans, "unmapped": unmapped_count}

def write_productos_pagos(f, schema: str, pagos: List[Dict[str, Any]], stats: Dict):
    """Write producto and producto_alumno tables"""

    # Extract unique conceptos
    f.write("-- ===== PRODUCTO =====\n")
    conceptos_set = set()
    concepto_precio = {}

    for r in pagos:
        concepto = r.get("Concepto")
        if concepto:
            concepto_clean = str(concepto).strip()
            if concepto_clean and concepto_clean.upper() != "NULL":
                conceptos_set.add(concepto_clean)
                if concepto_clean not in concepto_precio:
                    precio = r.get("Cuantia")
                    concepto_precio[concepto_clean] = precio

    for concepto in sorted(conceptos_set):
        precio = sql_num_or_null(concepto_precio.get(concepto))

        cols = ["concepto", "precio"]
        vals = [sql_str_or_null(concepto), precio]

        f.write(f"INSERT INTO `{schema}`.`producto` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join(vals)});\n")

    producto_count = len(conceptos_set)
    f.write(f"\n-- Processed: {producto_count}\n\n")

    # producto_alumno
    f.write("-- ===== PRODUCTO_ALUMNO =====\n")
    pa_count = 0
    pa_orphans = 0
    pa_no_concepto = 0

    for r in pagos:
        concepto = r.get("Concepto")
        if not concepto or str(concepto).strip() == "" or str(concepto).strip().upper() == "NULL":
            pa_no_concepto += 1
            continue

        exp = r.get("Exp")
        fk_alumno, is_orphan = fk_alumno_subquery(schema, exp)
        if is_orphan:
            pa_orphans += 1
            continue

        concepto_clean = str(concepto).strip()
        cantidad = "1"
        precio = sql_num_or_null(r.get("Cuantia"))
        fecha_pago = sql_date_or_null(r.get("Fecha") or r.get("FechaPago"))
        pagado = sql_bool(r.get("Pagado")) if r.get("Pagado") is not None else ("1" if fecha_pago != "NULL" else "0")
        fecha_asignacion = "NULL"
        notas = sql_str_or_null(r.get("Notas"))

        producto_fk = f"(SELECT id FROM `{schema}`.`producto` WHERE `concepto` = {sql_str_or_null(concepto_clean)} LIMIT 1)"

        cols = ["cantidad", "concepto", "fecha_asignacion", "fecha_pago", "notas", "pagado", "precio", "alumno_id", "producto_id"]
        vals = [cantidad, sql_str_or_null(concepto_clean), fecha_asignacion, fecha_pago, notas, pagado, precio, fk_alumno, producto_fk]

        f.write(f"INSERT INTO `{schema}`.`producto_alumno` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join(vals)});\n")
        pa_count += 1

    stats["producto"] = {"processed": producto_count}
    stats["producto_alumno"] = {"processed": pa_count, "orphans": pa_orphans, "no_concepto": pa_no_concepto}
    f.write(f"\n-- Processed: {pa_count}, Orphans: {pa_orphans}, No concepto: {pa_no_concepto}\n\n")

    # Link producto_alumno to alumno_deporte (multi-sport support)
    f.write("-- Link producto_alumno to alumno_deporte\n")
    f.write(f"UPDATE `{schema}`.`producto_alumno` pa\n")
    f.write(f"INNER JOIN `{schema}`.`alumno_deporte` ad ON pa.`alumno_id` = ad.`alumno_id`\n")
    f.write(f"SET pa.`alumno_deporte_id` = ad.`id`\n")
    f.write(f"WHERE pa.`alumno_id` IS NOT NULL;\n\n")

def write_convocatorias_examenes(f, schema: str, examenes: List[Dict[str, Any]], stats: Dict):
    """Write convocatoria and alumno_convocatoria tables"""

    # Extract unique convocatorias
    f.write("-- ===== CONVOCATORIA =====\n")
    convoc_map = {}  # id_conv -> earliest date

    for r in examenes:
        id_conv = r.get("ID_CONVEXAM")
        if id_conv is None:
            continue

        try:
            conv_id = int(float(str(id_conv).strip()))
        except:
            continue

        fecha = _parse_date(r.get("FECHA_CONVOCATORIA"))

        if conv_id not in convoc_map or (fecha and (convoc_map[conv_id] is None or fecha < convoc_map[conv_id])):
            convoc_map[conv_id] = fecha

    for conv_id in sorted(convoc_map.keys()):
        fecha = convoc_map[conv_id]
        deporte = "'TAEKWONDO'"

        cols = ["id", "deporte", "fecha_convocatoria"]
        vals = [str(conv_id), deporte, sql_date_or_null(fecha)]

        f.write(f"INSERT INTO `{schema}`.`convocatoria` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join(vals)});\n")

    convoc_count = len(convoc_map)
    f.write(f"\n-- Processed: {convoc_count}\n\n")

    # alumno_convocatoria
    f.write("-- ===== ALUMNO_CONVOCATORIA =====\n")
    ac_count = 0
    ac_orphans = 0

    for r in examenes:
        exp = r.get("Exp")
        fk_alumno, is_orphan = fk_alumno_subquery(schema, exp)
        if is_orphan:
            ac_orphans += 1
            continue

        id_conv = r.get("ID_CONVEXAM")
        if id_conv is None:
            ac_orphans += 1
            continue

        try:
            conv_id = int(float(str(id_conv).strip()))
        except:
            ac_orphans += 1
            continue

        grado_actual = map_grado_to_tipo(r.get("GRADO_EXAMEN"))
        grado_siguiente = map_grado_to_tipo(r.get("GRADO_PROMO"))
        derecho_examen = sql_bool(r.get("DERECHO_EXAMEN"))
        fecha_pago = sql_date_or_null(r.get("FECHA_CONVOCATORIA"))
        cuantia_examen = "0.0"  # Default value, can be updated later from producto_alumno
        pagado = derecho_examen
        producto_alumno_id = "NULL"

        cols = ["cuantia_examen", "fecha_pago", "grado_actual", "grado_siguiente", "pagado", "alumno_id", "convocatoria_id", "producto_alumno_id"]
        vals = [cuantia_examen, fecha_pago, grado_actual or "NULL", grado_siguiente or "NULL", pagado, fk_alumno, str(conv_id), producto_alumno_id]

        f.write(f"INSERT INTO `{schema}`.`alumno_convocatoria` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join(vals)});\n")
        ac_count += 1

    stats["convocatoria"] = {"processed": convoc_count}
    stats["alumno_convocatoria"] = {"processed": ac_count, "orphans": ac_orphans}
    f.write(f"\n-- Processed: {ac_count}, Orphans: {ac_orphans}\n\n")

    # Link alumno_convocatoria to alumno_deporte (multi-sport support)
    f.write("-- Link alumno_convocatoria to alumno_deporte\n")
    f.write(f"UPDATE `{schema}`.`alumno_convocatoria` ac\n")
    f.write(f"INNER JOIN `{schema}`.`alumno_deporte` ad ON ac.`alumno_id` = ad.`alumno_id`\n")
    f.write(f"SET ac.`alumno_deporte_id` = ad.`id`\n")
    f.write(f"WHERE ac.`alumno_id` IS NOT NULL;\n\n")

# ========== MAIN ==========

def migrate(access_path: str, out_sql: str, schema: str, env: str, base_url: Optional[str] = None) -> int:
    """Main migration function"""
    # Override base URL if provided
    if base_url:
        ENVIRONMENT_BASE_URLS[env] = base_url
        print(f"Using custom base URL: {base_url}")

    conn = open_access(access_path)

    print(f"Reading Access database: {access_path}")
    print(f"Target environment: {env}")
    print(f"Base URL: {ENVIRONMENT_BASE_URLS[env]}")

    # Fetch all tables
    alumnos = fetch_all(conn, "ALUMNOS")
    fotos = fetch_all(conn, "FOTOS_ALUMNOS")
    documentos = fetch_all(conn, "DOCUMENTOS_VINCULADOS")
    aux_grupos = fetch_all(conn, "AUX_GRUPOS")
    pagos = fetch_all(conn, "PAGOS")
    examenes = fetch_all(conn, "EXAMENES")

    conn.close()

    print(f"Read {len(alumnos)} alumnos, {len(fotos)} photos, {len(documentos)} documents")
    print(f"Read {len(aux_grupos)} group assignments, {len(pagos)} payments, {len(examenes)} exams")

    stats = {}

    # Write SQL file
    with open(out_sql, "w", encoding="utf-8") as f:
        f.write("-- =====================================================\n")
        f.write("-- MIGRATION SCRIPT V7 - Complete Field Mappings\n")
        f.write("-- =====================================================\n")
        f.write(f"-- Schema: `{schema}`\n")
        f.write(f"-- Environment: {env}\n")
        f.write(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("-- =====================================================\n\n")

        f.write("SET NAMES utf8mb4;\n")
        f.write("SET CHARACTER SET utf8mb4;\n")
        f.write("SET character_set_connection = utf8mb4;\n")
        f.write("SET FOREIGN_KEY_CHECKS = 0;\n")
        f.write("SET UNIQUE_CHECKS = 0;\n")
        f.write("SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';\n\n")

        # Clear existing data (in reverse FK order)
        f.write("-- Clear existing data\n")
        for table in ["alumno_convocatoria", "convocatoria", "producto_alumno", "producto",
                     "alumno_turno", "alumno_grupo", "turno", "grupo",
                     "documento", "alumno_deporte", "alumno", "imagen", "categoria", "grado"]:
            f.write(f"DELETE FROM `{schema}`.`{table}`;\n")
        f.write("\n")

        # Write data
        write_grados(f, schema)
        write_categorias(f, schema)
        write_alumnos(f, schema, alumnos, stats)
        write_alumno_deporte(f, schema, stats)
        write_imagenes(f, schema, fotos, stats, env)
        write_documentos(f, schema, documentos, alumnos, stats, env)
        write_grupos_turnos_relations(f, schema, aux_grupos, stats)
        write_productos_pagos(f, schema, pagos, stats)
        write_convocatorias_examenes(f, schema, examenes, stats)

        # Add verification queries
        f.write("\n")
        f.write("-- =====================================================\n")
        f.write("-- VERIFICATION QUERIES\n")
        f.write("-- =====================================================\n\n")

        f.write("-- Run these queries to verify the migration:\n\n")

        f.write("-- 1. Check foto_alumno_id linking\n")
        f.write(f"-- SELECT \n")
        f.write(f"--   (SELECT COUNT(*) FROM `{schema}`.`alumno` WHERE `foto_alumno_id` IS NOT NULL) as alumnos_con_foto,\n")
        f.write(f"--   (SELECT COUNT(*) FROM `{schema}`.`alumno` WHERE `foto_alumno_id` IS NULL) as alumnos_sin_foto,\n")
        f.write(f"--   (SELECT COUNT(*) FROM `{schema}`.`imagen` WHERE `tipo` LIKE 'image/%') as total_imagenes;\n\n")

        f.write("-- 2. List students with their photos\n")
        f.write(f"-- SELECT a.id, a.numero_expediente, a.nombre, a.apellidos, \n")
        f.write(f"--        a.foto_alumno_id, i.nombre as imagen_nombre, i.url as imagen_url\n")
        f.write(f"-- FROM `{schema}`.`alumno` a\n")
        f.write(f"-- LEFT JOIN `{schema}`.`imagen` i ON a.foto_alumno_id = i.id\n")
        f.write(f"-- ORDER BY a.numero_expediente\n")
        f.write(f"-- LIMIT 10;\n\n")

        f.write("-- 3. Find students without photos (should investigate why)\n")
        f.write(f"-- SELECT a.id, a.numero_expediente, a.nombre, a.apellidos\n")
        f.write(f"-- FROM `{schema}`.`alumno` a\n")
        f.write(f"-- WHERE a.foto_alumno_id IS NULL AND a.activo = 1;\n\n")

        f.write("-- 4. Find orphaned images (images not linked to any student)\n")
        f.write(f"-- SELECT i.id, i.nombre, i.ruta\n")
        f.write(f"-- FROM `{schema}`.`imagen` i\n")
        f.write(f"-- LEFT JOIN `{schema}`.`alumno` a ON a.foto_alumno_id = i.id\n")
        f.write(f"-- WHERE a.id IS NULL AND i.tipo LIKE 'image/%';\n\n")

        f.write("-- 5. Overall table counts\n")
        f.write(f"-- SELECT 'alumno' as tabla, COUNT(*) as registros FROM `{schema}`.`alumno`\n")
        f.write(f"-- UNION ALL SELECT 'alumno_activos', COUNT(*) FROM `{schema}`.`alumno` WHERE activo = 1\n")
        f.write(f"-- UNION ALL SELECT 'alumno_con_foto', COUNT(*) FROM `{schema}`.`alumno` WHERE foto_alumno_id IS NOT NULL\n")
        f.write(f"-- UNION ALL SELECT 'imagen', COUNT(*) FROM `{schema}`.`imagen`\n")
        f.write(f"-- UNION ALL SELECT 'documento', COUNT(*) FROM `{schema}`.`documento`\n")
        f.write(f"-- UNION ALL SELECT 'grupo', COUNT(*) FROM `{schema}`.`grupo`\n")
        f.write(f"-- UNION ALL SELECT 'turno', COUNT(*) FROM `{schema}`.`turno`\n")
        f.write(f"-- UNION ALL SELECT 'producto', COUNT(*) FROM `{schema}`.`producto`\n")
        f.write(f"-- UNION ALL SELECT 'convocatoria', COUNT(*) FROM `{schema}`.`convocatoria`;\n\n")

        f.write("SET FOREIGN_KEY_CHECKS = 1;\n")
        f.write("SET UNIQUE_CHECKS = 1;\n\n")

        f.write("-- =====================================================\n")
        f.write("-- MIGRATION COMPLETE\n")
        f.write("-- =====================================================\n")

    # Print stats
    print("\n" + "="*60)
    print("MIGRATION STATISTICS")
    print("="*60)
    for table, data in sorted(stats.items()):
        print(f"\n{table}:")
        for key, value in data.items():
            print(f"  {key}: {value}")
    print("\n" + "="*60)

    return 0

def main(argv=None):
    parser = argparse.ArgumentParser(description="Complete Access to MySQL migration")
    parser.add_argument("--access", required=True, help="Path to Access .mdb/.accdb file")
    parser.add_argument("--out", required=True, help="Output SQL file path")
    parser.add_argument("--schema", default=DEFAULT_SCHEMA, help=f"Target schema (default: {DEFAULT_SCHEMA})")
    parser.add_argument("--env", default="local", choices=["local", "server", "docker"],
                       help="Target environment for file paths (default: local)")
    parser.add_argument("--base-url", help="Override base URL for documento/imagen URLs (e.g., https://yourdomain.com)")

    args = parser.parse_args(argv)

    # Validate environment
    if args.env not in ENVIRONMENT_PATHS:
        print(f"\n✗ Error: Invalid environment '{args.env}'. Must be one of: {', '.join(ENVIRONMENT_PATHS.keys())}", file=sys.stderr)
        return 1

    try:
        rc = migrate(args.access, args.out, args.schema, args.env, args.base_url)
        print(f"\n✓ SQL file written to: {args.out}")
        print(f"✓ Environment: {args.env}")
        return rc
    except Exception as e:
        print(f"\n✗ Error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return 1

if __name__ == "__main__":
    sys.exit(main())
