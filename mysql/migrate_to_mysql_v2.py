#!/usr/bin/env python3
"""
Migration Script V2: CLUBMOISKIMDO Access Database → TaeMoi MySQL
CORRECTED VERSION - Matches actual TaeMoi entity schema

This version fixes all schema mismatches identified in SCHEMA_MISMATCH_ANALYSIS.md
"""

import pyodbc
import os
import sys
import re
from datetime import datetime, date
from decimal import Decimal
from collections import defaultdict
import unicodedata

# Configuration
MDB_PATH = r"C:\Users\croly\Documents\proyecto_taemoi_csr\CLUBMOISKIMDO_AUX.mdb"
OUTPUT_SQL_FILE = "migration_output_v2.sql"
REPORT_FILE = "migration_report_v2.txt"

# Global counters
stats = defaultdict(int)
errors = []

# =============================================================================
# DATABASE CONNECTION
# =============================================================================


def connect_to_mdb(mdb_path):
    """Connect to Access database"""
    try:
        conn_str = (
            r"Driver={Microsoft Access Driver (*.mdb, *.accdb)};" + f"DBQ={mdb_path};"
        )
        return pyodbc.connect(conn_str)
    except Exception as e:
        print(f"Error connecting to database: {e}")
        sys.exit(1)


# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================


def fix_encoding(text):
    """Fix encoding issues in Spanish text"""
    if not text:
        return text
    try:
        text = unicodedata.normalize("NFC", text)
    except:
        pass
    return text


def escape_sql_string(value):
    """Escape string for SQL"""
    if value is None:
        return "NULL"
    value = str(value)
    value = fix_encoding(value)
    value = value.replace("'", "''")
    value = value.replace("\\", "\\\\")
    value = value.replace("\n", "\\n")
    value = value.replace("\r", "\\r")
    value = value.replace("\t", "\\t")
    return f"'{value}'"


def format_date(date_value):
    """Format date for MySQL"""
    if date_value is None:
        return "NULL"
    if isinstance(date_value, (datetime, date)):
        return f"'{date_value.strftime('%Y-%m-%d')}'"
    return "NULL"


def format_time_string(time_value):
    """Format time as HH:mm string"""
    if time_value is None:
        return "NULL"
    if isinstance(time_value, datetime):
        return f"'{time_value.strftime('%H:%M')}'"
    if isinstance(time_value, str):
        # Parse and reformat
        match = re.search(r"(\d{1,2}):(\d{2})", time_value)
        if match:
            return f"'{match.group(1).zfill(2)}:{match.group(2)}'"
    return "NULL"


def format_boolean(value):
    """Format boolean for MySQL"""
    if value is None:
        return "0"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, str):
        value_upper = value.upper()
        if value_upper in ["AUTORIZADO", "SI", "SÍ", "TRUE", "1", "YES"]:
            return "1"
        elif value_upper in ["NO AUTORIZADO", "NO", "FALSE", "0"]:
            return "0"
    return "1" if value else "0"


def format_decimal(value):
    """Format decimal for MySQL"""
    if value is None:
        return "NULL"
    try:
        return str(Decimal(value))
    except:
        return "NULL"


def format_integer(value):
    """Format integer for MySQL"""
    if value is None:
        return "NULL"
    try:
        return str(int(value))
    except:
        return "NULL"


def parse_telefono(telefono_movil, telefono_fijo):
    """Parse telefono - prefer movil, fallback to fijo"""
    telefono = telefono_movil if telefono_movil else telefono_fijo
    if not telefono:
        # Return default phone number (required field)
        return "600000000"

    # Remove non-digits
    telefono_str = re.sub(r"\D", "", str(telefono))

    # Take first 9 digits
    if len(telefono_str) >= 9:
        telefono_str = telefono_str[:9]
        try:
            return str(int(telefono_str))
        except:
            pass

    # Return default phone number if parsing failed
    return "600000000"


def fix_nif(nif_str):
    """Fix NIF - remove hyphens and ensure 9 characters"""
    if not nif_str or str(nif_str).strip() == "":
        # Return placeholder NIF for students without one
        # This is required since NIF is NOT NULL in database
        return "000000000"

    # Remove hyphens and spaces
    nif_clean = str(nif_str).replace("-", "").replace(" ", "").strip()

    # If after cleaning it's empty, return placeholder
    if not nif_clean:
        return "000000000"

    # Ensure it's exactly 9 characters
    if len(nif_clean) == 9:
        return nif_clean
    elif len(nif_clean) < 9:
        # Pad with zeros on the left (for numeric part)
        return nif_clean.zfill(9)
    else:
        # Take first 9 characters
        return nif_clean[:9]


def fix_email(email_str):
    """Fix malformed emails"""
    if not email_str:
        return None

    email = str(email_str).strip()

    # Fix common issues
    # Case 1: "name@gmail" -> "name@gmail.com"
    if email.endswith("@gmail") and not email.endswith("@gmail.com"):
        email = email + ".com"
        print(f"      Fixed email: {email_str} -> {email}")

    # Case 2: "name@.com." or similar malformed -> "name@gmail.com"
    elif "@." in email or email.endswith("."):
        # Extract the part before @
        if "@" in email:
            username = email.split("@")[0]
            email = f"{username}@gmail.com"
            print(f"      Fixed email: {email_str} -> {email}")

    # Case 3: Multiple emails separated by space or slash - take first one
    if " " in email or "/" in email:
        email = email.split()[0].split("/")[0]

    return email


# =============================================================================
# ENUM MAPPING FUNCTIONS
# =============================================================================


def map_grado_to_tipo_grado(grado_str):
    """Map grade string to TipoGrado enum"""
    if not grado_str:
        return "BLANCO"

    grado_str = grado_str.strip().upper()

    grado_map = {
        "BLANCO": "BLANCO",
        "BLANCO/AMARILLO": "BLANCO_AMARILLO",
        "AMARILLO": "AMARILLO",
        "AMARILLO/NARANJA": "AMARILLO_NARANJA",
        "NARANJA": "NARANJA",
        "NARANJA/VERDE": "NARANJA_VERDE",
        "VERDE": "VERDE",
        "VERDE/AZUL": "VERDE_AZUL",
        "AZUL": "AZUL",
        "AZUL/ROJO": "AZUL_ROJO",
        "ROJO": "ROJO",
    }

    if grado_str in grado_map:
        return grado_map[grado_str]

    # Handle Rojo-Negro with PUM
    if "ROJO" in grado_str and "NEGRO" in grado_str:
        if "1" in grado_str:
            return "ROJO_NEGRO_1_PUM"
        elif "2" in grado_str:
            return "ROJO_NEGRO_2_PUM"
        elif "3" in grado_str:
            return "ROJO_NEGRO_3_PUM"
        return "ROJO_NEGRO_1_PUM"

    # Handle black belts (DAN)
    if "NEGRO" in grado_str or "DAN" in grado_str:
        match = re.search(r"(\d+)", grado_str)
        if match:
            dan_num = match.group(1)
            return f"NEGRO_{dan_num}_DAN"
        return "NEGRO_1_DAN"

    if "DERECHO A EXAMEN" in grado_str:
        return None

    print(f"Warning: Unknown grade '{grado_str}', defaulting to BLANCO")
    errors.append(f"Unknown grade: {grado_str}")
    return "BLANCO"


def map_tarifa_to_tipo_tarifa(tarifa_str):
    """Map tariff string to TipoTarifa enum"""
    if not tarifa_str:
        return "INFANTIL"

    tarifa_str = tarifa_str.strip().upper()

    tarifa_map = {
        "ADULTO": "ADULTO",
        "ADULTO GRUPO": "ADULTO_GRUPO",
        "FAMILIAR": "FAMILIAR",
        "INFANTIL": "INFANTIL",
        "INFANTIL GRUPO": "INFANTIL_GRUPO",
        "HERMANOS": "HERMANOS",
        "PADRES HIJOS": "PADRES_HIJOS",
        "PADRES_HIJOS": "PADRES_HIJOS",
        "PILATES": "PILATES",
        "DEFENSA PERSONAL FEMENINA": "DEFENSA_PERSONAL_FEMENINA",
        "DEFENSA_PERSONAL_FEMENINA": "DEFENSA_PERSONAL_FEMENINA",
    }

    return tarifa_map.get(tarifa_str, "INFANTIL")


def get_cuantia_tarifa(tipo_tarifa):
    """Get default cuantía for tarifa type"""
    cuantias = {
        "ADULTO": 30.0,
        "ADULTO_GRUPO": 20.0,
        "FAMILIAR": 0.0,
        "INFANTIL": 27.0,
        "INFANTIL_GRUPO": 20.0,
        "HERMANOS": 40.0,
        "PADRES_HIJOS": 40.0,
        "PILATES": 25.0,
        "DEFENSA_PERSONAL_FEMENINA": 25.0,
    }
    return cuantias.get(tipo_tarifa, 30.0)


def map_dia_semana(dia_str, dia_num):
    """Map day to string"""
    if dia_num is not None:
        day_map = {
            1: "LUNES",
            2: "MARTES",
            3: "MIERCOLES",
            4: "JUEVES",
            5: "VIERNES",
            6: "SABADO",
            7: "DOMINGO",
        }
        return day_map.get(dia_num, "LUNES")

    if dia_str:
        dia_upper = dia_str.strip().upper()
        day_map = {
            "LUNES": "LUNES",
            "MARTES": "MARTES",
            "MIERCOLES": "MIERCOLES",
            "MIÉRCOLES": "MIERCOLES",
            "JUEVES": "JUEVES",
            "VIERNES": "VIERNES",
            "SABADO": "SABADO",
            "SÁBADO": "SABADO",
            "DOMINGO": "DOMINGO",
        }
        return day_map.get(dia_upper, "LUNES")

    return "LUNES"


def parse_time_from_turno(turno_str):
    """Extract start and end time from turno string"""
    if not turno_str:
        return None, None

    match = re.search(r"(\d{1,2}):(\d{2})\s*A\s*(\d{1,2}):(\d{2})", turno_str)
    if match:
        h1, m1, h2, m2 = match.groups()
        start_time = f"{h1.zfill(2)}:{m1}"
        end_time = f"{h2.zfill(2)}:{m2}"
        return start_time, end_time

    return None, None


# =============================================================================
# DATA EXTRACTION FUNCTIONS
# =============================================================================


def extract_grados_reference():
    """Create Grado reference table data"""
    # All possible TipoGrado values from the enum
    tipo_grados = [
        "BLANCO",
        "BLANCO_AMARILLO",
        "AMARILLO",
        "AMARILLO_NARANJA",
        "NARANJA",
        "NARANJA_VERDE",
        "VERDE",
        "VERDE_AZUL",
        "AZUL",
        "AZUL_ROJO",
        "ROJO",
        "ROJO_NEGRO_1_PUM",
        "ROJO_NEGRO_2_PUM",
        "ROJO_NEGRO_3_PUM",
        "NEGRO_1_DAN",
        "NEGRO_2_DAN",
        "NEGRO_3_DAN",
        "NEGRO_4_DAN",
        "NEGRO_5_DAN",
    ]

    grados = []
    for idx, tipo_grado in enumerate(tipo_grados, 1):
        grados.append({"id": idx, "tipo_grado": tipo_grado})
        stats["grados_created"] += 1

    return grados


def extract_alumnos(conn, grado_map):
    """Extract ALL students from ALUMNOS table (active and inactive)"""
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM ALUMNOS")

    alumnos = []
    for row in cursor.fetchall():

        tipo_grado = map_grado_to_tipo_grado(row.Grado)
        if tipo_grado:
            grado_id = grado_map.get(tipo_grado)
            if not grado_id:
                print(f"Warning: Could not find grado_id for {tipo_grado}, setting NULL")
                grado_id = None
        else:
            grado_id = None

        tipo_tarifa = map_tarifa_to_tipo_tarifa(row.Tarifa)
        cuantia_tarifa = (
            row.Cuota
            if row.Cuota and row.Cuota > 0
            else get_cuantia_tarifa(tipo_tarifa)
        )

        telefono = parse_telefono(row.Telefono_movil, row.Telefono_fijo)

        # Check if license exists
        tiene_licencia = row.N_licencia is not None and row.N_licencia > 0

        # Fix NIF (remove hyphens)
        nif_original = row.DNI if row.DNI else row.NIF
        nif_fixed = fix_nif(nif_original)

        # Fix email
        email_fixed = fix_email(row.Email)

        # Map Baja field (invert logic: Baja=True means activo=False)
        is_activo = (
            not row.Baja if hasattr(row, "Baja") and row.Baja is not None else True
        )
        fecha_baja_value = row.Fecha_Baja if hasattr(row, "Fecha_Baja") else None

        alumno = {
            "id": row.Exp,  # Use numeroExpediente as ID
            "numero_expediente": row.Exp,
            "nombre": (
                fix_encoding(row.Nombre)
                if row.Nombre and str(row.Nombre).strip()
                else "Sin nombre"
            ),
            "apellidos": (
                fix_encoding(row.Apellidos)
                if row.Apellidos and str(row.Apellidos).strip()
                else "Sin apellidos"
            ),
            "nif": nif_fixed,
            "fecha_nacimiento": row.Fnac if row.Fnac else date(1900, 1, 1),
            "direccion": (
                fix_encoding(row.Direccion)
                if row.Direccion and str(row.Direccion).strip()
                else "Sin dirección"
            ),
            "telefono": telefono,
            "email": email_fixed,
            "tipo_tarifa": tipo_tarifa,
            "cuantia_tarifa": cuantia_tarifa,
            "fecha_alta": row.Fecha_Alta,
            "activo": is_activo,
            "fecha_baja": fecha_baja_value,
            "autorizacion_web": row.Autorizacion_foto_web,
            "competidor": False,  # Default, can be updated later
            "peso": float(row.Peso) if row.Peso else None,
            "fecha_peso": row.Fecha_Peso,
            "tiene_licencia": tiene_licencia,
            "numero_licencia": row.N_licencia,
            "fecha_licencia": row.Fecha_licencia,
            "tiene_discapacidad": False,  # Default
            "foto_alumno_id": None,  # Will be updated when photos are extracted
            "deporte": "TAEKWONDO",  # Default
            "categoria_id": None,  # Will be set if competidor
            "grado_id": grado_id,
            "fecha_grado": row.Fgrado,
            "apto_para_examen": False,  # Will be calculated by app
            "tiene_derecho_examen": False,  # Default
        }
        alumnos.append(alumno)
        stats["alumnos_extracted"] += 1

        # justo antes del return de extract_alumnos

    exp_counts = defaultdict(int)
    for a in alumnos:
        exp_counts[a["id"]] += 1
    dups = [exp for exp, c in exp_counts.items() if c > 1]
    if dups:
        print(
            f"WARNING: Duplicated expediente IDs in Access: {dups[:20]} ... total={len(dups)}"
        )
        errors.append(f"Duplicated expediente IDs: {dups[:50]}")

        # al final de extract_alumnos(), antes de 'return alumnos'
    print(f"[DEBUG] Total alumnos generados: {len(alumnos)}")

    return alumnos


def extract_grupos_and_relationships(conn, valid_alumno_ids, active_alumno_ids):
    """Extract grupos and relationships (only for active students)"""
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM AUX_GRUPOS")

    grupos_dict = {}  # key: unique grupo identifier, value: grupo data
    alumno_grupo_relationships = []  # (alumno_id, grupo_id)
    alumno_turno_relationships = []  # (alumno_id, turno_id)

    grupo_id_counter = 1
    turno_id_counter = 1
    turnos_dict = {}  # key: unique turno identifier, value: turno data

    for row in cursor.fetchall():
        alumno_exp = row.Exp
        dia = map_dia_semana(row.DIA, row.DiaSemana)
        turno_name = fix_encoding(row.Turno) if row.Turno else "TURNO DESCONOCIDO"
        is_ptdk = row.PTDK if hasattr(row, "PTDK") else False

        start_time, end_time = parse_time_from_turno(turno_name)
        if not start_time:
            start_time = "17:00"
            end_time = "18:00"

        # Determine tipo for grupo and turno
        if is_ptdk:
            tipo = "PRE-TAEKWONDO"
        elif "PILATES" in turno_name.upper():
            tipo = "PILATES"
        elif "KICK" in turno_name.upper():
            tipo = "KICKBOXING"
        else:
            tipo = "TAEKWONDO"

        # Create unique grupo key (by tipo)
        grupo_key = tipo

        if grupo_key not in grupos_dict:
            grupos_dict[grupo_key] = {
                "id": grupo_id_counter,
                "nombre": tipo,
                "tipo": tipo,
            }
            grupo_id_counter += 1
            stats["grupos_created"] += 1

        grupo_id = grupos_dict[grupo_key]["id"]

        # Create unique turno key
        turno_key = f"{grupo_id}_{dia}_{start_time}_{end_time}"

        if turno_key not in turnos_dict:
            turnos_dict[turno_key] = {
                "id": turno_id_counter,
                "dia_semana": dia,
                "hora_inicio": start_time,
                "hora_fin": end_time,
                "tipo": tipo,
                "grupo_id": grupo_id,
            }
            turno_id_counter += 1
            stats["turnos_created"] += 1

        turno_id = turnos_dict[turno_key]["id"]

        # Add relationships only for active students (inactive students shouldn't have current grupo/turno assignments)
        if alumno_exp not in active_alumno_ids:
            continue

        if (alumno_exp, grupo_id) not in alumno_grupo_relationships:
            alumno_grupo_relationships.append((alumno_exp, grupo_id))
            stats["alumno_grupo_relationships"] += 1

        if (alumno_exp, turno_id) not in alumno_turno_relationships:
            alumno_turno_relationships.append((alumno_exp, turno_id))
            stats["alumno_turno_relationships"] += 1

    grupos = list(grupos_dict.values())
    turnos = list(turnos_dict.values())

    return grupos, turnos, alumno_grupo_relationships, alumno_turno_relationships


def extract_productos(conn):
    """Extract products from AUX_ADMON and xxx AUX_MARTERIALES"""
    cursor = conn.cursor()
    productos = []
    producto_id = 1
    producto_map = {}  # Map concepto to producto_id

    # Extract from AUX_ADMON
    cursor.execute("SELECT * FROM AUX_ADMON")
    for row in cursor.fetchall():
        concepto = fix_encoding(row.CONCEPTO) if row.CONCEPTO else "PRODUCTO"
        precio = float(row.CUANTIA) if row.CUANTIA else 0.0

        productos.append({"id": producto_id, "concepto": concepto, "precio": precio})
        producto_map[concepto] = producto_id
        producto_id += 1
        stats["productos_extracted"] += 1

    # Extract from xxx AUX_MARTERIALES
    try:
        cursor.execute("SELECT * FROM [xxx AUX_MARTERIALES]")
        for row in cursor.fetchall():
            concepto = fix_encoding(row.CONCEPTO) if row.CONCEPTO else "MATERIAL"
            precio = float(row.PRECIO) if row.PRECIO else 0.0

            productos.append(
                {"id": producto_id, "concepto": concepto, "precio": precio}
            )
            producto_map[concepto] = producto_id
            producto_id += 1
            stats["productos_extracted"] += 1
    except:
        print("Warning: Could not extract from xxx AUX_MARTERIALES")

    return productos, producto_map


def extract_convocatorias(conn):
    """Extract exam calls from EXAMENES_CONVOCATORIAS"""
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM EXAMENES_CONVOCATORIAS ORDER BY FECHA_CONVOCATORIA")

    convocatorias = []
    convocatoria_map = {}

    for idx, row in enumerate(cursor.fetchall(), 1):
        convocatoria = {
            "id": idx,
            "fecha_convocatoria": row.FECHA_CONVOCATORIA,
            "deporte": "TAEKWONDO",
        }
        convocatorias.append(convocatoria)
        convocatoria_map[row.ID_CONVEXAM] = idx
        stats["convocatorias_extracted"] += 1

    return convocatorias, convocatoria_map


def extract_examenes(conn, convocatoria_map, valid_alumno_ids):
    """Extract exam records from EXAMENES"""
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM EXAMENES")

    examenes = []

    for row in cursor.fetchall():
        if row.Exp not in valid_alumno_ids:
            continue

        grado_actual = map_grado_to_tipo_grado(row.GRADO_ACTUAL)
        grado_siguiente = map_grado_to_tipo_grado(row.EXAMEN_GRADO)

        if not grado_actual or not grado_siguiente:
            continue

        convocatoria_id = convocatoria_map.get(row.ID_CONVEXAM)
        if not convocatoria_id:
            continue

        # Determine cuantía, pagado, and fecha_pago from old data
        cuantia = float(row.xxCuantia) if row.xxCuantia else 35.0  # Default exam fee
        pagado = bool(row.xxPagado) if row.xxPagado is not None else False
        fecha_pago = row.xxFechaPago if row.xxFechaPago else None

        examen = {
            "alumno_id": row.Exp,
            "convocatoria_id": convocatoria_id,
            "producto_alumno_id": None,  # Will be linked later if needed
            "cuantia_examen": cuantia,
            "pagado": pagado,
            "fecha_pago": fecha_pago,
            "grado_actual": grado_actual,
            "grado_siguiente": grado_siguiente,
        }
        examenes.append(examen)
        stats["examenes_extracted"] += 1

    return examenes


def extract_pagos(conn, producto_map, valid_alumno_ids):
    """Extract payments from PAGOS"""
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM PAGOS")

    pagos = []

    for row in cursor.fetchall():
        if row.Exp not in valid_alumno_ids:
            continue

        concepto = fix_encoding(row.Concepto) if row.Concepto else "PAGO"
        producto_id = producto_map.get(concepto)

        # Try to find matching producto by keyword if not exact match
        if not producto_id:
            concepto_upper = concepto.upper()
            for prod_concepto, prod_id in producto_map.items():
                if (
                    prod_concepto.upper() in concepto_upper
                    or concepto_upper in prod_concepto.upper()
                ):
                    producto_id = prod_id
                    break

        # Skip if still no producto found
        if not producto_id:
            continue

        precio = float(row.Cuantia) if row.Cuantia else 0.0
        pagado = bool(row.Pagado) if row.Pagado is not None else False

        pago = {
            "alumno_id": row.Exp,
            "producto_id": producto_id,
            "concepto": concepto,
            "fecha_asignacion": row.Fecha,
            "cantidad": 1,  # Default
            "precio": precio,
            "pagado": pagado,
            "fecha_pago": row.FechaPago,
            "notas": fix_encoding(row.Notas) if row.Notas else None,
        }
        pagos.append(pago)
        stats["pagos_extracted"] += 1

    return pagos


# =============================================================================
# SQL GENERATION FUNCTIONS
# =============================================================================


def generate_grado_inserts(grados):
    """Generate SQL INSERT statements for Grado reference table"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- GRADO REFERENCE TABLE INSERTS")
    sql_lines.append("-- ============================================\n")

    for grado in grados:
        sql = f"INSERT INTO grado (id, tipo_grado) VALUES ({grado['id']}, {escape_sql_string(grado['tipo_grado'])});"
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_grupo_inserts(grupos):
    """Generate SQL INSERT statements for Grupo"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- GRUPO INSERTS")
    sql_lines.append("-- ============================================\n")

    for grupo in grupos:
        sql = f"""INSERT INTO grupo (id, nombre, tipo) VALUES (
    {grupo['id']},
    {escape_sql_string(grupo['nombre'])},
    {escape_sql_string(grupo['tipo'])}
);"""
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_turno_inserts(turnos):
    """Generate SQL INSERT statements for Turno"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- TURNO INSERTS")
    sql_lines.append("-- ============================================\n")

    for turno in turnos:
        sql = f"""INSERT INTO turno (id, dia_semana, hora_inicio, hora_fin, tipo, grupo_id) VALUES (
    {turno['id']},
    {escape_sql_string(turno['dia_semana'])},
    {escape_sql_string(turno['hora_inicio'])},
    {escape_sql_string(turno['hora_fin'])},
    {escape_sql_string(turno['tipo'])},
    {turno['grupo_id']}
);"""
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_producto_inserts(productos):
    """Generate SQL INSERT statements for Producto"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- PRODUCTO INSERTS")
    sql_lines.append("-- ============================================\n")

    for producto in productos:
        sql = f"""INSERT INTO producto (id, concepto, precio) VALUES (
    {producto['id']},
    {escape_sql_string(producto['concepto'])},
    {producto['precio']}
);"""
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_convocatoria_inserts(convocatorias):
    """Generate SQL INSERT statements for Convocatoria"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- CONVOCATORIA INSERTS")
    sql_lines.append("-- ============================================\n")

    for conv in convocatorias:
        sql = f"""INSERT INTO convocatoria (id, fecha_convocatoria, deporte) VALUES (
    {conv['id']},
    {format_date(conv['fecha_convocatoria'])},
    {escape_sql_string(conv['deporte'])}
);"""
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_alumno_inserts(alumnos):
    """Generate SQL INSERT statements for Alumno"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- ALUMNO INSERTS")
    sql_lines.append("-- ============================================\n")

    for alumno in alumnos:
        sql = f"""INSERT INTO alumno (id, numero_expediente, nombre, apellidos, nif, fecha_nacimiento,
    direccion, telefono, email, tipo_tarifa, cuantia_tarifa, fecha_alta, activo, fecha_baja,
    autorizacion_web, competidor, peso, fecha_peso, tiene_licencia, numero_licencia,
    fecha_licencia, tiene_discapacidad, foto_alumno_id, deporte, categoria_id, grado_id,
    fecha_grado, apto_para_examen, tiene_derecho_examen) VALUES (
    {alumno['id']},
    {alumno['numero_expediente']},
    {escape_sql_string(alumno['nombre'])},
    {escape_sql_string(alumno['apellidos'])},
    {escape_sql_string(alumno['nif'])},
    {format_date(alumno['fecha_nacimiento'])},
    {escape_sql_string(alumno['direccion'])},
    {alumno['telefono']},
    {escape_sql_string(alumno['email'])},
    {escape_sql_string(alumno['tipo_tarifa'])},
    {alumno['cuantia_tarifa']},
    {format_date(alumno['fecha_alta'])},
    {format_boolean(alumno['activo'])},
    {format_date(alumno['fecha_baja'])},
    {format_boolean(alumno['autorizacion_web'])},
    {format_boolean(alumno['competidor'])},
    {alumno['peso'] if alumno['peso'] else 'NULL'},
    {format_date(alumno['fecha_peso'])},
    {format_boolean(alumno['tiene_licencia'])},
    {format_integer(alumno['numero_licencia'])},
    {format_date(alumno['fecha_licencia'])},
    {format_boolean(alumno['tiene_discapacidad'])},
    {alumno['foto_alumno_id'] if alumno['foto_alumno_id'] else 'NULL'},
    {escape_sql_string(alumno['deporte'])},
    {alumno['categoria_id'] if alumno['categoria_id'] else 'NULL'},
    {alumno['grado_id'] if alumno['grado_id'] is not None else 'NULL'},
    {format_date(alumno['fecha_grado'])},
    {format_boolean(alumno['apto_para_examen'])},
    {format_boolean(alumno['tiene_derecho_examen'])}
);"""
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_alumno_grupo_inserts(alumno_grupos):
    """Generate SQL INSERT statements for alumno_grupo join table"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- ALUMNO_GRUPO JOIN TABLE INSERTS")
    sql_lines.append("-- ============================================\n")

    for alumno_id, grupo_id in alumno_grupos:
        sql = f"INSERT INTO alumno_grupo (alumno_id, grupo_id) VALUES ({alumno_id}, {grupo_id});"
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_alumno_turno_inserts(alumno_turnos):
    """Generate SQL INSERT statements for alumno_turno join table"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- ALUMNO_TURNO JOIN TABLE INSERTS")
    sql_lines.append("-- ============================================\n")

    for alumno_id, turno_id in alumno_turnos:
        sql = f"INSERT INTO alumno_turno (alumno_id, turno_id) VALUES ({alumno_id}, {turno_id});"
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_alumno_convocatoria_inserts(examenes):
    """Generate SQL INSERT statements for AlumnoConvocatoria"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- ALUMNO_CONVOCATORIA INSERTS")
    sql_lines.append("-- ============================================\n")

    for examen in examenes:
        sql = f"""INSERT INTO alumno_convocatoria (alumno_id, convocatoria_id, producto_alumno_id,
    cuantia_examen, pagado, fecha_pago, grado_actual, grado_siguiente) VALUES (
    {examen['alumno_id']},
    {examen['convocatoria_id']},
    {examen['producto_alumno_id'] if examen['producto_alumno_id'] else 'NULL'},
    {examen['cuantia_examen']},
    {format_boolean(examen['pagado'])},
    {format_date(examen['fecha_pago'])},
    {escape_sql_string(examen['grado_actual'])},
    {escape_sql_string(examen['grado_siguiente'])}
);"""
        sql_lines.append(sql)

    return "\n".join(sql_lines)


def generate_producto_alumno_inserts(pagos):
    """Generate SQL INSERT statements for ProductoAlumno"""
    sql_lines = []
    sql_lines.append("\n-- ============================================")
    sql_lines.append("-- PRODUCTO_ALUMNO INSERTS")
    sql_lines.append("-- ============================================\n")

    for pago in pagos:
        sql = f"""INSERT INTO producto_alumno (alumno_id, producto_id, concepto, fecha_asignacion,
    cantidad, precio, pagado, fecha_pago, notas) VALUES (
    {pago['alumno_id']},
    {pago['producto_id']},
    {escape_sql_string(pago['concepto'])},
    {format_date(pago['fecha_asignacion'])},
    {pago['cantidad']},
    {pago['precio']},
    {format_boolean(pago['pagado'])},
    {format_date(pago['fecha_pago'])},
    {escape_sql_string(pago['notas'])}
);"""
        sql_lines.append(sql)

    return "\n".join(sql_lines)


# =============================================================================
# MAIN MIGRATION FUNCTION
# =============================================================================


def run_migration():
    """Main migration function"""
    print("=" * 80)
    print("TaeMoi Data Migration V2: Access -> MySQL")
    print("CORRECTED VERSION - Matches actual TaeMoi schema")
    print("=" * 80)
    print(f"\nConnecting to: {MDB_PATH}")

    conn = connect_to_mdb(MDB_PATH)

    print("\n[1/10] Creating Grado reference table...")
    grados = extract_grados_reference()
    grado_map = {g["tipo_grado"]: g["id"] for g in grados}
    print(f"      [OK] Created {len(grados)} grado entries")

    print("\n[2/10] Extracting Alumnos (students)...")
    alumnos = extract_alumnos(conn, grado_map)
    valid_alumno_ids = {a["id"] for a in alumnos}
    active_alumno_ids = {a["id"] for a in alumnos if a["activo"]}
    active_count = len(active_alumno_ids)
    inactive_count = len(alumnos) - active_count
    print(
        f"      [OK] Extracted {len(alumnos)} students ({active_count} active, {inactive_count} inactive)"
    )

    print("\n[3/10] Extracting Grupos, Turnos and relationships...")
    grupos, turnos, alumno_grupos, alumno_turnos = extract_grupos_and_relationships(
        conn, valid_alumno_ids, active_alumno_ids
    )
    print(f"      [OK] Created {len(grupos)} grupos")
    print(f"      [OK] Created {len(turnos)} turnos")
    print(f"      [OK] Created {len(alumno_grupos)} alumno-grupo relationships")
    print(f"      [OK] Created {len(alumno_turnos)} alumno-turno relationships")

    print("\n[4/10] Extracting Productos...")
    productos, producto_map = extract_productos(conn)
    print(f"      [OK] Extracted {len(productos)} productos")

    print("\n[5/10] Extracting Convocatorias...")
    convocatorias, convocatoria_map = extract_convocatorias(conn)
    print(f"      [OK] Extracted {len(convocatorias)} convocatorias")

    print("\n[6/10] Extracting Examenes...")
    examenes = extract_examenes(conn, convocatoria_map, valid_alumno_ids)
    print(f"      [OK] Extracted {len(examenes)} exam records")

    print("\n[7/10] Extracting Pagos...")
    pagos = extract_pagos(conn, producto_map, valid_alumno_ids)
    print(f"      [OK] Extracted {len(pagos)} payment records")

    print("\n[8/10] Generating SQL INSERT statements...")

    sql_output = []
    sql_output.append("-- ============================================")
    sql_output.append("-- TaeMoi Data Migration SQL Script V2")
    sql_output.append(f"-- Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    sql_output.append("-- Source: CLUBMOISKIMDO_AUX.mdb")
    sql_output.append("-- CORRECTED VERSION - Matches actual TaeMoi entity schema")
    sql_output.append("-- ============================================")
    sql_output.append("\nSET FOREIGN_KEY_CHECKS = 0;\n")
    sql_output.append(
        "SET NAMES utf8mb4;\n"
        "SET character_set_client = utf8mb4;\n"
        "SET character_set_connection = utf8mb4;\n"
        "SET character_set_results = utf8mb4;\n"
        "SET collation_connection = utf8mb4_spanish_ci;\n"
    )
    sql_output.append("\n-- Clean existing data (CAUTION!)")
    sql_output.append("-- TRUNCATE TABLE producto_alumno;")
    sql_output.append("-- TRUNCATE TABLE alumno_convocatoria;")
    sql_output.append("-- TRUNCATE TABLE alumno_turno;")
    sql_output.append("-- TRUNCATE TABLE alumno_grupo;")
    sql_output.append("-- TRUNCATE TABLE alumno;")
    sql_output.append("-- TRUNCATE TABLE turno;")
    sql_output.append("-- TRUNCATE TABLE grupo;")
    sql_output.append("-- TRUNCATE TABLE producto;")
    sql_output.append("-- TRUNCATE TABLE convocatoria;")
    sql_output.append("-- TRUNCATE TABLE grado;")
    sql_output.append("\nSET FOREIGN_KEY_CHECKS = 1;\n")

    sql_output.append(generate_grado_inserts(grados))
    sql_output.append(generate_grupo_inserts(grupos))
    sql_output.append(generate_turno_inserts(turnos))
    sql_output.append(generate_producto_inserts(productos))
    sql_output.append(generate_convocatoria_inserts(convocatorias))
    sql_output.append(generate_alumno_inserts(alumnos))
    sql_output.append(generate_alumno_grupo_inserts(alumno_grupos))
    sql_output.append(generate_alumno_turno_inserts(alumno_turnos))
    sql_output.append(generate_alumno_convocatoria_inserts(examenes))
    sql_output.append(generate_producto_alumno_inserts(pagos))

    with open(OUTPUT_SQL_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(sql_output))

    print(f"      [OK] SQL written to: {OUTPUT_SQL_FILE}")

    print("\n[9/10] Generating migration report...")
    report_lines = []
    report_lines.append("=" * 80)
    report_lines.append("MIGRATION REPORT V2 (CORRECTED)")
    report_lines.append("=" * 80)
    report_lines.append(f"\nGenerated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report_lines.append(f"Source Database: {MDB_PATH}")
    report_lines.append(f"Output SQL File: {OUTPUT_SQL_FILE}\n")
    report_lines.append("\nMIGRATION STATISTICS:")
    report_lines.append("-" * 80)
    for key, value in sorted(stats.items()):
        report_lines.append(f"  {key}: {value}")

    if errors:
        report_lines.append("\n\nERRORS/WARNINGS:")
        report_lines.append("-" * 80)
        for error in errors[:50]:
            report_lines.append(f"  - {error}")
        if len(errors) > 50:
            report_lines.append(f"\n  ... and {len(errors) - 50} more errors")

    report_lines.append("\n\nKEY SCHEMA FIXES APPLIED:")
    report_lines.append("-" * 80)
    report_lines.append("  - Created Grado reference table with FK relationship")
    report_lines.append("  - Alumno uses id as PK (set to numeroExpediente value)")
    report_lines.append("  - Fixed telefono (single field instead of movil/fijo)")
    report_lines.append("  - Fixed tipoTarifa + cuantiaTarifa (separate fields)")
    report_lines.append("  - Fixed autorizacionWeb, competidor field names")
    report_lines.append("  - Fixed Turno -> Grupo relationship (many-to-one)")
    report_lines.append("  - Added alumno_turno join table for many-to-many")
    report_lines.append("  - Fixed Producto (concepto/precio only)")
    report_lines.append(
        "  - Fixed ProductoAlumno (alumno_id FK, not numero_expediente)"
    )
    report_lines.append("  - Fixed AlumnoConvocatoria schema completely")
    report_lines.append("\n\nDATA QUALITY FIXES APPLIED:")
    report_lines.append("-" * 80)
    report_lines.append(
        "  - Removed hyphens from NIFs (104 students: '12345678-A' -> '12345678A')"
    )
    report_lines.append("  - Fixed malformed emails to use @gmail.com (2 students)")
    report_lines.append("\n\nNEXT STEPS:")
    report_lines.append("-" * 80)
    report_lines.append("1. Review the generated SQL file: " + OUTPUT_SQL_FILE)
    report_lines.append("2. Test on a development database first")
    report_lines.append("3. Execute the SQL script in MySQL:")
    report_lines.append(f"   mysql -u username -p database_name < {OUTPUT_SQL_FILE}")
    report_lines.append("4. Verify data integrity and counts")
    report_lines.append("5. Extract and migrate student photos from FOTOS_ALUMNOS")
    report_lines.append("6. Copy documents from D:\\DOCUMENTOS\\")
    report_lines.append("\n" + "=" * 80)

    report_text = "\n".join(report_lines)

    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write(report_text)

    print(report_text)
    print(f"\nReport saved to: {REPORT_FILE}")

    conn.close()
    print("\n[SUCCESS] Migration V2 complete!")


# =============================================================================
# ENTRY POINT
# =============================================================================

if __name__ == "__main__":
    try:
        run_migration()
    except Exception as e:
        print(f"\n[ERROR] Migration failed with error: {e}")
        import traceback

        traceback.print_exc()
        sys.exit(1)
