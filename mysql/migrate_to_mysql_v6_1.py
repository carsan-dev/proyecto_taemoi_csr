#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
migrate_to_mysql_v6_1.py
------------------------
Como v6, pero con **reporte detallado** de conciliación (Access vs INSERTs generados) y
detección de huérfanos/omisiones. Flags útiles para el flujo:

Flags:
  --skip-orphans / --require-alumno  No genera INSERTs dependientes si la FK alumno no se puede resolver.
  --strict                           Si hay huérfanos, aborta con exit code 1.
  --report                           Ruta del .txt con el informe (por defecto junto a --out).

Tablas cubiertas (alineadas con tu DDL):
  alumno, imagen, documento, producto, producto_alumno, convocatoria, alumno_convocatoria

Uso:
  python migrate_to_mysql_v6_1.py --access "C:\\...\CLUBMOISKIMDO_AUX.mdb" --out "C:\\...\export_v6_1.sql" --schema taemoi_test --skip-orphans
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

def fk_alumno(schema: str, expediente: Optional[Any]) -> Tuple[str, bool]:
    """
    Devuelve (sql, orphan). orphan=True si no se puede resolver (NULL).
    """
    if expediente is None:
        return "NULL", True
    s = str(expediente).strip()
    if s == "" or s == "0":
        return "NULL", True
    try:
        exp_int = int(float(s))
    except Exception:
        return "NULL", True
    return f"(SELECT id FROM `{schema}`.`alumno` WHERE `numero_expediente` = {exp_int} LIMIT 1)", False

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
    rows = []
    for r in cur.fetchall():
        rows.append({c: getattr(r,c) if hasattr(r,c) else r[i] for i,c in enumerate(cols)})
    return rows, cols

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
        "NULL",
    ]
    return f"INSERT INTO `{schema}`.`alumno` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});"

def migrate(access_path: str, out_sql: str, schema: str, report_path: str, skip_orphans: bool, strict: bool) -> int:
    conn = open_access(access_path)

    alumnos_rows, _ = fetch_all(conn, ACCESS["ALUMNOS"])
    pagos_rows, _ = fetch_all(conn, ACCESS["PAGOS"])
    exam_rows, _ = fetch_all(conn, ACCESS["EXAMENES"])
    fotos_rows, _ = fetch_all(conn, ACCESS["FOTOS_ALUMNOS"])
    docs_rows, _ = fetch_all(conn, ACCESS["DOCUMENTOS_VINCULADOS"])

    alumnos = extract_alumnos(alumnos_rows)

    # producto: conceptos únicos
    conceptos = []
    for r in pagos_rows:
        c = r.get("Concepto")
        if c is None:
            continue
        s = str(c).strip()
        if s and s.upper() != "NULL" and s not in conceptos:
            conceptos.append(s)

    # convocatoria: por ID_CONVEXAM
    convoc_map = {}  # id_conv -> fecha mínima
    for r in exam_rows:
        cid = r.get("ID_CONVEXAM")
        if cid is None:
            continue
        try:
            key = int(float(str(cid).strip()))
        except Exception:
            continue
        d = _parse_date(r.get("Fecha") or r.get("FecExamen"))
        if key not in convoc_map or (d and (convoc_map[key] is None or d < convoc_map[key])):
            convoc_map[key] = d

    counts_src = {
        "alumno": len(alumnos_rows),
        "imagen": len(fotos_rows),
        "documento": len(docs_rows),
        "producto": len(conceptos),
        "producto_alumno": len(pagos_rows),
        "convocatoria": len(convoc_map),
        "alumno_convocatoria": len(exam_rows),
    }
    counts_gen = {k:0 for k in counts_src.keys()}
    orphans = {"producto_alumno":0, "documento":0, "imagen":0, "alumno_convocatoria":0}
    product_missing = 0  # pagos sin concepto

    with open(out_sql, "w", encoding="utf-8") as f:
        f.write("-- SQL generado por migrate_to_mysql_v6_1.py (alineado DDL + reporte)\n")
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
            counts_gen["alumno"] += 1

        # 2) imagen
        if fotos_rows:
            f.write("\n-- 2) IMAGEN (fotos de alumnos)\n")
            for r in fotos_rows:
                exp = r.get("Exp")
                fk_sql, is_orphan = fk_alumno(schema, exp)
                if is_orphan:
                    orphans["imagen"] += 1
                    if skip_orphans:
                        continue
                nombre = None
                if exp is not None and str(exp).strip() not in ("","0"):
                    try:
                        nombre = f"alumno_{int(float(str(exp).strip()))}"
                    except Exception:
                        nombre = None
                if not nombre:
                    ruta_val = str(r.get("FOTOGRAFIA") or "").strip()
                    base = os.path.basename(ruta_val) if ruta_val else "foto"
                    nombre = f"alumno_{base}"
                ruta = r.get("FOTOGRAFIA") or r.get("Ruta")
                tipo = "FOTO_ALUMNO"
                url = None
                cols = ["nombre","ruta","tipo","url"]
                vals = [sql_str_or_null(nombre), sql_str_or_null(ruta), sql_str_or_null(tipo), sql_str_or_null(url)]
                f.write(f"INSERT INTO `{schema}`.`imagen` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")
                counts_gen["imagen"] += 1
            # Update alumno.foto_alumno_id
            if counts_gen["imagen"] > 0:
                f.write("\n-- Vincular imagen a alumno.foto_alumno_id por nombre alumno_<Exp>\n")
                f.write(f"UPDATE `{schema}`.`alumno` a\n"
                        f"JOIN `{schema}`.`imagen` i ON i.`nombre` = CONCAT('alumno_', a.`numero_expediente`)\n"
                        f"SET a.`foto_alumno_id` = i.`id`;\n")

        # 3) documento
        if docs_rows:
            f.write("\n-- 3) DOCUMENTO\n")
            for r in docs_rows:
                exp = r.get("ID_EXPEDIENTES") or r.get("Exp")
                fk_sql, is_orphan = fk_alumno(schema, exp)
                if is_orphan:
                    orphans["documento"] += 1
                    if skip_orphans:
                        continue
                nombre = r.get("NUM_REGISTRO") or os.path.basename(str(r.get('DOC_PATH') or '')) or "documento"
                ruta = r.get("DOC_PATH") or r.get("Ruta") or ""
                tipo = r.get("DOC_TIPO") or r.get("Tipo") or "OTRO"
                url = None
                cols = ["nombre","ruta","tipo","url","alumno_id"]
                vals = [sql_str_or_null(str(nombre)), sql_str_or_null(ruta), sql_str_or_null(tipo), sql_str_or_null(url), fk_sql]
                f.write(f"INSERT INTO `{schema}`.`documento` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")
                counts_gen["documento"] += 1

        # 4) producto
        if conceptos:
            f.write("\n-- 4) PRODUCTO (desde conceptos de PAGOS)\n")
            for c in conceptos:
                cols = ["concepto","precio"]
                vals = [sql_str_or_null(c), "NULL"]
                f.write(f"INSERT INTO `{schema}`.`producto` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")
                counts_gen["producto"] += 1

        # 5) producto_alumno
        if pagos_rows:
            f.write("\n-- 5) PRODUCTO_ALUMNO (desde PAGOS)\n")
            for r in pagos_rows:
                concepto = r.get("Concepto")
                if concepto is None or str(concepto).strip() == "" or str(concepto).strip().upper() == "NULL":
                    product_missing += 1
                    if skip_orphans:
                        continue
                importe = r.get("Cantidad") or r.get("Importe")
                fecha = r.get("Fecha") or r.get("FecPago")
                notas = None
                pagado = "1" if fecha not in (None,"") else "0"
                fk_sql, is_orphan = fk_alumno(schema, r.get("Exp"))
                if is_orphan:
                    orphans["producto_alumno"] += 1
                    if skip_orphans:
                        continue
                cols = ["cantidad","concepto","fecha_asignacion","fecha_pago","notas","pagado","precio","alumno_id","producto_id"]
                vals = [
                    "1",
                    sql_str_or_null(concepto),
                    "NULL",
                    sql_date_or_null(fecha),
                    sql_str_or_null(notas),
                    pagado,
                    sql_num_or_null(importe),
                    fk_sql,
                    f"(SELECT id FROM `{schema}`.`producto` WHERE `concepto` = {sql_str_or_null(concepto)} LIMIT 1)"
                ]
                f.write(f"INSERT INTO `{schema}`.`producto_alumno` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")
                counts_gen["producto_alumno"] += 1

        # 6) convocatoria
        if convoc_map:
            f.write("\n-- 6) CONVOCATORIA (desde EXAMENES.ID_CONVEXAM)\n")
            for cid, d in sorted(convoc_map.items(), key=lambda x: x[0]):
                cols = ["id","deporte","fecha_convocatoria"]
                vals = [str(cid), "'TAEKWONDO'", sql_date_or_null(d)]
                f.write(f"INSERT INTO `{schema}`.`convocatoria` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")
                counts_gen["convocatoria"] += 1

        # 7) alumno_convocatoria
        if exam_rows:
            f.write("\n-- 7) ALUMNO_CONVOCATORIA (desde EXAMENES)\n")
            for r in exam_rows:
                fk_sql, is_orphan = fk_alumno(schema, r.get("Exp"))
                if is_orphan:
                    orphans["alumno_convocatoria"] += 1
                    if skip_orphans:
                        continue
                cols = ["cuantia_examen","fecha_pago","grado_actual","grado_siguiente","pagado","alumno_id","convocatoria_id","producto_alumno_id"]
                vals = [
                    "NULL",
                    sql_date_or_null(r.get("Fecha") or r.get("FecExamen")),
                    "NULL",
                    "NULL",
                    "0",
                    fk_sql,
                    sql_num_or_null(r.get("ID_CONVEXAM")),
                    "NULL",
                ]
                f.write(f"INSERT INTO `{schema}`.`alumno_convocatoria` ({', '.join('`'+c+'`' for c in cols)}) VALUES ({', '.join(vals)});\n")
                counts_gen["alumno_convocatoria"] += 1

    # Reporte
    with open(report_path, "w", encoding="utf-8") as rep:
        rep.write("=============================================\n")
        rep.write("MIGRATION REPORT V6.1\n")
        rep.write("=============================================\n\n")
        rep.write(f"Access file: {access_path}\n")
        rep.write(f"Output SQL : {out_sql}\n\n")
        rep.write("COUNTS (Access vs Generated):\n")
        for k in ("alumno","imagen","documento","producto","producto_alumno","convocatoria","alumno_convocatoria"):
            rep.write(f"  {k:17s}: src={counts_src[k]:>6} | gen={counts_gen[k]:>6}\n")
        rep.write("\nORPHANS (dependientes sin alumno_id o sin producto):\n")
        rep.write(f"  imagen            : {orphans['imagen']:>6}\n")
        rep.write(f"  documento         : {orphans['documento']:>6}\n")
        rep.write(f"  producto_alumno   : {orphans['producto_alumno']:>6}  (product_missing={product_missing})\n")
        rep.write(f"  alumno_convocatoria: {orphans['alumno_convocatoria']:>6}\n")
        rep.write("\nNOTES:\n")
        rep.write(" - alumno.id AUTO_INCREMENT; FKs por subconsulta a numero_expediente (Exp).\n")
        rep.write(" - numero_expediente duplicado/0/invalido => NULL.\n")
        rep.write(" - Fechas vacías/0000-00-00 => NULL (salvo fecha_nacimiento, fallback 1900-01-01).\n")
        rep.write(" - producto: creado por conceptos únicos de PAGOS; producto_alumno enlaza por concepto.\n")
        rep.write(" - convocatoria: creada por ID_CONVEXAM, deporte='TAEKWONDO' (ajustable).\n")
        rep.write(f"\nOPTIONS:\n  skip_orphans={skip_orphans} | strict={strict}\n")

    if strict and (orphans["imagen"] or orphans["documento"] or orphans["producto_alumno"] or orphans["alumno_convocatoria"] or product_missing):
        return 1
    return 0

def main(argv=None):
    ap = argparse.ArgumentParser(description="Migrador Access -> MySQL alineado con DDL + reporte conciliación.")
    ap.add_argument("--access", required=True, help="Ruta al .mdb/.accdb (Access)")
    ap.add_argument("--out", required=True, help="Fichero .sql de salida")
    ap.add_argument("--schema", default=DEFAULT_SCHEMA, help=f"Esquema destino (por defecto {DEFAULT_SCHEMA})")
    ap.add_argument("--report", default=None, help="Ruta del reporte .txt (por defecto junto a --out)")
    ap.add_argument("--skip-orphans", "--require-alumno", action="store_true", dest="skip_orphans",
                    help="No generar dependientes cuya FK a alumno no se pueda resolver / concepto vacío.")
    ap.add_argument("--strict", action="store_true", help="Si hay huérfanos u omisiones de producto, terminar con error.")
    args = ap.parse_args(argv)
    report = args.report or (os.path.splitext(args.out)[0] + "_reporte_v6_1.txt")
    try:
        rc = migrate(args.access, args.out, args.schema, report, args.skip_orphans, args.strict)
        print(f"[ok] SQL: {args.out}")
        print(f"[ok] Reporte: {report}")
        if rc != 0:
            sys.exit(rc)
        return 0
    except Exception as e:
        sys.stderr.write(f"[error] {e}\n")
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
