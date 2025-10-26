#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
migrate_to_mysql_v6.py
----------------------
Migración Access (.mdb/.accdb) -> MySQL alineada con el DDL de `taemoi_test`:

Tablas destino cubiertas:
- alumno (id AUTO_INCREMENT)
- imagen (fotos de alumnos)
- documento (documentos vinculados)
- producto (catálogo por concepto de pago)
- producto_alumno (pagos por alumno)
- convocatoria (a partir de EXAMENES.ID_CONVEXAM)
- alumno_convocatoria (inscripciones/exámenes por alumno y convocatoria)

Notas:
- FKs a alumno se resuelven vía subconsulta por `numero_expediente` (Exp en Access).
- Fechas inválidas/vacías -> NULL (salvo fecha_nacimiento, fallback '1900-01-01').
- `numero_expediente` duplicado/0/vacío -> NULL para respetar UNIQUE.
- `grado_actual`/`grado_siguiente` en alumno_convocatoria se dejan NULL para evitar choques con ENUM si los valores de Access no coinciden.

Uso:
  python migrate_to_mysql_v6.py --access "C:\\...\CLUBMOISKIMDO_AUX.mdb" --out "C:\\...\export_v6.sql" --schema taemoi_test
"""

import argparse, os, sys
from datetime import date, datetime
from typing import Any, Dict, List, Optional, Tuple

try:
    import pyodbc
except Exception:
    pyodbc = None

DEFAULT_SCHEMA = os.environ.get("SCHEMA","taemoi_test")

ACCESS = {
    "ALUMNOS": "ALUMNOS",
    "PAGOS": "PAGOS",
    "EXAMENES": "EXAMENES",
    "FOTOS_ALUMNOS": "FOTOS_ALUMNOS",
    "DOCUMENTOS_VINCULADOS": "DOCUMENTOS_VINCULADOS",
}

def sql_escape(s: str) -> str:
    return s.replace("'", "''")

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

def _parse_date(x: Any) -> Optional[date]:
    if x is None:
        return None
    if isinstance(x,(date,datetime)):
        return date(x.year, x.month, x.day)
    if isinstance(x,str):
        sx = x.strip()
        if not sx or sx.startswith("0000"):
            return None
        for fmt in ("%Y-%m-%d","%d/%m/%Y","%d-%m-%Y","%m/%d/%Y","%d.%m.%Y","%m-%d-%Y"):
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

def sql_date_or_fallback_notnull(v: Optional[Any], fallback="1900-01-01") -> str:
    d = _parse_date(v)
    if d is None:
        return f"'{fallback}'"
    return f"'{d.strftime('%Y-%m-%d')}'"

def fk_alumno(schema: str, expediente: Optional[Any]) -> str:
    if expediente is None:
        return "NULL"
    s = str(expediente).strip()
    if s == "" or s == "0":
        return "NULL"
    try:
        exp_int = int(float(s))
    except Exception:
        return "NULL"
    return f"(SELECT id FROM `{schema}`.`alumno` WHERE `numero_expediente` = {exp_int} LIMIT 1)"

def open_access(path: str):
    if pyodbc is None:
        raise RuntimeError("pyodbc no está instalado. Instala pyodbc y el Access Database Engine.")
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    drivers = []
    try:
        drivers = pyodbc.drivers()
    except Exception:
        pass
    drv = None
    for cand in ("Microsoft Access Driver (*.mdb, *.accdb)","Microsoft Access Driver (*.mdb)"):
        if cand in drivers:
            drv = cand
            break
    if drv is None:
        if drivers:
            drv = drivers[-1]
        else:
            raise RuntimeError("No hay driver ODBC de Access disponible.")
    return pyodbc.connect(f"DRIVER={{{drv}}};DBQ={path};")

def fetch_all(conn, table: str) -> Tuple[List[Dict[str,Any]], List[str]]:
    cur = conn.cursor()
    try:
        cur.execute(f"SELECT * FROM [{table}]")
    except Exception:
        return [], []
    cols = [c[0] for c in cur.description]
    out = []
    for r in cur.fetchall():
        out.append({c: getattr(r,c) if hasattr(r,c) else r[i] for i,c in enumerate(cols)})
    return out, cols

def extract_alumnos(rows: List[Dict[str,Any]]) -> List[Dict[str,Any]]:
    out = []
    for r in rows:
        o = {
            "numero_expediente": r.get("Exp"),
            "nombre": r.get("Nombre"),
            "apellidos": r.get("Apellidos"),
            "dni": r.get("DNI"),
            "telefono": r.get("Telefono"),
            "email": r.get("Email"),
            "direccion": r.get("Direccion",""),
            "fecha_nacimiento": r.get("Fnac"),
            "fecha_alta": r.get("Falta"),
            "fecha_baja": r.get("Fbaja"),
            "fecha_grado": r.get("FecGrado"),
            "fecha_licencia": r.get("FecLic"),
            "fecha_peso": r.get("FecPeso"),
            "numero_licencia": r.get("NumLicencia"),
            "peso": r.get("Peso"),
            "tipo_tarifa": r.get("TipoTarifa"),
            "cuantia_tarifa": r.get("CuantiaTarifa", 0),
            "activo": r.get("Activo", 1),
            "autorizacion_web": r.get("AutorizacionWeb", 0),
            "competidor": r.get("Competidor", 0),
            "categoria_id": r.get("CategoriaId"),
            "grado_id": r.get("GradoId"),
        }
        out.append(o)
    return out

def gen_sql_alumno(schema: str, o: Dict[str,Any]) -> str:
    cols = [
        "numero_expediente","nombre","apellidos","dni","telefono","email","direccion",
        "fecha_nacimiento","fecha_alta","fecha_baja","fecha_grado","fecha_licencia","fecha_peso",
        "numero_licencia","peso","tipo_tarifa","cuantia_tarifa","activo","autorizacion_web","competidor",
        "categoria_id","grado_id","foto_alumno_id"
    ]
    vals = [
        "NULL" if o.get("numero_expediente") in (None,"",0) else str(int(float(o["numero_expediente"]))),
        sql_str_or_null(o.get("nombre","")),
        sql_str_or_null(o.get("apellidos","")),
        sql_str_or_null(o.get("dni")),
        sql_str_or_null(o.get("telefono")),
        sql_str_or_null(o.get("email")),
        sql_str_or_null(o.get("direccion","")),
        sql_date_or_fallback_notnull(o.get("fecha_nacimiento")),
        sql_date_or_null(o.get("fecha_alta")),
        sql_date_or_null(o.get("fecha_baja")),
        sql_date_or_null(o.get("fecha_grado")),
        sql_date_or_null(o.get("fecha_licencia")),
        sql_date_or_null(o.get("fecha_peso")),
        sql_str_or_null(o.get("numero_licencia")),
        sql_num_or_null(o.get("peso")),
        sql_str_or_null(o.get("tipo_tarifa")),
        sql_num_or_null(o.get("cuantia_tarifa")) if o.get("cuantia_tarifa") is not None else "0",
        "1" if str(o.get("activo","1")).strip() in ("1","true","True") else "0",
        "1" if str(o.get("autorizacion_web","0")).strip() in ("1","true","True") else "0",
        "1" if str(o.get("competidor","0")).strip() in ("1","true","True") else "0",
        "NULL" if o.get("categoria_id") in (None,"") else str(int(float(o["categoria_id"]))),
        "NULL" if o.get("grado_id") in (None,"") else str(int(float(o["grado_id"]))),
        "NULL",  # foto_alumno_id se rellenará luego con un UPDATE
    ]
    return f"INSERT INTO `{schema}`.`alumno` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});"

def migrate(access_path: str, out_sql: str, schema: str) -> None:
    conn = open_access(access_path)

    alumnos_rows, _ = fetch_all(conn, ACCESS["ALUMNOS"])
    pagos_rows, _ = fetch_all(conn, ACCESS["PAGOS"])
    exam_rows, _ = fetch_all(conn, ACCESS["EXAMENES"])
    fotos_rows, _ = fetch_all(conn, ACCESS["FOTOS_ALUMNOS"])
    docs_rows, _ = fetch_all(conn, ACCESS["DOCUMENTOS_VINCULADOS"])

    alumnos = extract_alumnos(alumnos_rows)

    # Distinct conceptos de pagos para producto
    conceptos = []
    for r in pagos_rows:
        c = r.get("Concepto")
        if c is None:
            continue
        s = str(c).strip()
        if s == "" or s.upper()=="NULL":
            continue
        if s not in conceptos:
            conceptos.append(s)

    # Distinct convocatorias por ID_CONVEXAM (con fecha mínima conocida)
    convoc_map = {}  # id_conv -> fecha (min)
    for r in exam_rows:
        cid = r.get("ID_CONVEXAM")
        if cid is None:
            continue
        try:
            key = int(float(str(cid).strip()))
        except Exception:
            continue
        d = _parse_date(r.get("Fecha") or r.get("FecExamen"))
        if key not in convoc_map:
            convoc_map[key] = d
        else:
            if d and (convoc_map[key] is None or d < convoc_map[key]):
                convoc_map[key] = d

    with open(out_sql, "w", encoding="utf-8") as f:
        f.write("-- SQL generado por migrate_to_mysql_v6.py (alineado DDL)\n")
        f.write(f"-- schema: `{schema}`\n\n")

        # 1) alumnos
        f.write("-- 1) ALUMNOS\n")
        seen = set()
        for al in alumnos:
            exp = al.get("numero_expediente")
            key = None
            if exp not in (None,"",0):
                try:
                    key = int(float(str(exp).strip()))
                except Exception:
                    key = None
            if key is not None:
                if key in seen:
                    al["numero_expediente"] = None
                else:
                    seen.add(key)
                    al["numero_expediente"] = key
            else:
                al["numero_expediente"] = None
            f.write(gen_sql_alumno(schema, al) + "\n")

        # 2) imagen: insertar fotos y luego actualizar alumno.foto_alumno_id
        if fotos_rows:
            f.write("\n-- 2) IMAGEN (fotos de alumnos)\n")
            for r in fotos_rows:
                exp = r.get("Exp")
                nombre = None
                if exp is not None and str(exp).strip() not in ("","0"):
                    try:
                        nombre = f"alumno_{int(float(str(exp).strip()))}"
                    except Exception:
                        nombre = None
                if not nombre:
                    # fallback: nombre basado en ruta
                    ruta = str(r.get("FOTOGRAFIA") or "").strip()
                    base = os.path.basename(ruta) if ruta else "foto"
                    nombre = f"alumno_{base}"
                ruta = r.get("FOTOGRAFIA") or r.get("Ruta")
                tipo = "FOTO_ALUMNO"
                url = None
                cols = ["nombre","ruta","tipo","url"]
                vals = [sql_str_or_null(nombre), sql_str_or_null(ruta), sql_str_or_null(tipo), sql_str_or_null(url)]
                f.write(f"INSERT INTO `{schema}`.`imagen` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")
            # update alumno.foto_alumno_id por nombre
            f.write("\n-- Vincular imagen a alumno.foto_alumno_id por nombre alumno_<Exp>\n")
            f.write(f"UPDATE `{schema}`.`alumno` a\n"
                    f"JOIN `{schema}`.`imagen` i ON i.`nombre` = CONCAT('alumno_', a.`numero_expediente`)\n"
                    f"SET a.`foto_alumno_id` = i.`id`;\n")

        # 3) documento: desde DOCUMENTOS_VINCULADOS
        if docs_rows:
            f.write("\n-- 3) DOCUMENTO\n")
            for r in docs_rows:
                exp = r.get("ID_EXPEDIENTES") or r.get("Exp")
                nombre = r.get("NUM_REGISTRO") or os.path.basename(str(r.get('DOC_PATH') or '')) or "documento"
                ruta = r.get("DOC_PATH") or r.get("Ruta") or ""
                tipo = r.get("DOC_TIPO") or r.get("Tipo") or "OTRO"
                url = None
                cols = ["nombre","ruta","tipo","url","alumno_id"]
                vals = [sql_str_or_null(str(nombre)), sql_str_or_null(ruta), sql_str_or_null(tipo), sql_str_or_null(url), fk_alumno(schema, exp)]
                f.write(f"INSERT INTO `{schema}`.`documento` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")

        # 4) producto: conceptos únicos de PAGOS
        if conceptos:
            f.write("\n-- 4) PRODUCTO (desde conceptos de PAGOS)\n")
            for c in conceptos:
                cols = ["concepto","precio"]
                vals = [sql_str_or_null(c), "NULL"]
                f.write(f"INSERT INTO `{schema}`.`producto` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")

        # 5) producto_alumno: cada fila de PAGOS
        if pagos_rows:
            f.write("\n-- 5) PRODUCTO_ALUMNO (desde PAGOS)\n")
            for r in pagos_rows:
                concepto = r.get("Concepto")
                importe = r.get("Cantidad") or r.get("Importe")
                fecha = r.get("Fecha") or r.get("FecPago")
                notas = None
                pagado = "1" if fecha not in (None,"") else "0"
                cols = ["cantidad","concepto","fecha_asignacion","fecha_pago","notas","pagado","precio","alumno_id","producto_id"]
                vals = [
                    "1",
                    sql_str_or_null(concepto),
                    "NULL",  # fecha_asignacion no disponible
                    sql_date_or_null(fecha),
                    sql_str_or_null(notas),
                    pagado,
                    sql_num_or_null(importe),
                    fk_alumno(schema, r.get("Exp")),
                    f"(SELECT id FROM `{schema}`.`producto` WHERE `concepto` = {sql_str_or_null(concepto)} LIMIT 1)"
                ]
                f.write(f"INSERT INTO `{schema}`.`producto_alumno` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")

        # 6) convocatoria: por ID_CONVEXAM con fecha mínima; deporte por defecto 'TAEKWONDO'
        if convoc_map:
            f.write("\n-- 6) CONVOCATORIA (desde EXAMENES.ID_CONVEXAM)\n")
            for cid, d in sorted(convoc_map.items(), key=lambda x: x[0]):
                cols = ["id","deporte","fecha_convocatoria"]
                vals = [str(cid), "'TAEKWONDO'", sql_date_or_null(d)]
                f.write(f"INSERT INTO `{schema}`.`convocatoria` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")

        # 7) alumno_convocatoria: desde EXAMENES
        if exam_rows:
            f.write("\n-- 7) ALUMNO_CONVOCATORIA (desde EXAMENES)\n")
            for r in exam_rows:
                cols = ["cuantia_examen","fecha_pago","grado_actual","grado_siguiente","pagado","alumno_id","convocatoria_id","producto_alumno_id"]
                # Mapeamos mínimamente: dejamos grados NULL para no chocar con ENUM
                vals = [
                    "NULL",
                    sql_date_or_null(r.get("Fecha") or r.get("FecExamen")),
                    "NULL",
                    "NULL",
                    "0",
                    fk_alumno(schema, r.get("Exp")),
                    sql_num_or_null(r.get("ID_CONVEXAM")),
                    "NULL",
                ]
                f.write(f"INSERT INTO `{schema}`.`alumno_convocatoria` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")

    print(f"[ok] SQL escrito: {out_sql}")

def main(argv=None):
    ap = argparse.ArgumentParser(description="Migrador Access -> MySQL alineado con DDL (alumno, imagen, documento, producto, producto_alumno, convocatoria, alumno_convocatoria).")
    ap.add_argument("--access", required=True, help="Ruta al .mdb/.accdb (Access)")
    ap.add_argument("--out", required=True, help="Fichero .sql de salida")
    ap.add_argument("--schema", default=DEFAULT_SCHEMA, help=f"Esquema destino (por defecto {DEFAULT_SCHEMA})")
    args = ap.parse_args(argv)
    try:
        migrate(args.access, args.out, args.schema)
        return 0
    except Exception as e:
        sys.stderr.write(f"[error] {e}\n")
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
