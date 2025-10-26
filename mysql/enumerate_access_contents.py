#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
enumerate_access_contents.py
----------------------------
- Lista todas las tablas/vistas del MDB/ACCDB (vía ODBC).
- Cuenta filas por tabla (si se puede hacer SELECT *).
- Para tablas con columnas tipo alumno (Exp, Nombre, Apellidos), cuenta DISTINCT Exp.
- Exporta un CSV resumen.

Uso:
  python enumerate_access_contents.py --access "C:\ruta\CLUBMOISKIMDO_AUX.mdb" --out "C:\ruta\resumen_access.csv"
"""
import argparse, os, sys, csv
from typing import List, Dict, Any

try:
    import pyodbc
except Exception:
    pyodbc = None

CANDIDATE_EXP_COLS = ["Exp", "expediente", "NumeroExpediente", "NExp"]
CANDIDATE_NAME_COLS = ["Nombre", "Apellidos", "name", "apellido", "apellidos"]

def open_access(path: str):
    if pyodbc is None:
        raise RuntimeError("pyodbc no está instalado. `pip install pyodbc` y driver de Access.")
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    drivers = pyodbc.drivers()
    drv = None
    for d in ("Microsoft Access Driver (*.mdb, *.accdb)", "Microsoft Access Driver (*.mdb)"):
        if d in drivers:
            drv = d
            break
    if drv is None:
        if drivers:
            drv = drivers[-1]
        else:
            raise RuntimeError("No hay driver ODBC de Access disponible.")
    return pyodbc.connect(f"DRIVER={{{drv}}};DBQ={path};")

def list_tables(conn) -> List[Dict[str, Any]]:
    cur = conn.cursor()
    rows = []
    for row in cur.tables():
        # row.table_type could be 'TABLE', 'VIEW', 'SYSTEM TABLE'
        rows.append({"table_cat": row.table_cat, "table_schem": row.table_schem, "table_name": row.table_name, "table_type": row.table_type})
    return rows

def list_columns(conn, table_name: str) -> List[str]:
    cur = conn.cursor()
    cols = []
    for c in cur.columns(table=table_name):
        cols.append(c.column_name)
    return cols

def can_read(conn, table_name: str) -> bool:
    cur = conn.cursor()
    try:
        cur.execute(f"SELECT TOP 1 * FROM [{table_name}]")
        cur.fetchone()
        return True
    except Exception:
        return False

def count_rows(conn, table_name: str) -> int:
    cur = conn.cursor()
    try:
        cur.execute(f"SELECT COUNT(*) FROM [{table_name}]")
        (n,) = cur.fetchone()
        return int(n)
    except Exception:
        return -1

def count_distinct_exp(conn, table_name: str, exp_col: str) -> int:
    cur = conn.cursor()
    try:
        cur.execute(f"SELECT COUNT(DISTINCT [{exp_col}]) FROM [{table_name}]")
        (n,) = cur.fetchone()
        return int(n)
    except Exception:
        return -1

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--access", required=True, help="Ruta al .mdb/.accdb")
    ap.add_argument("--out", required=True, help="CSV de salida")
    a = ap.parse_args()

    conn = open_access(a.access)
    tables = list_tables(conn)

    summary = []
    for t in tables:
        name = t["table_name"]
        ttype = t["table_type"]
        cols = list_columns(conn, name)
        readable = can_read(conn, name)
        total = count_rows(conn, name) if readable else -1

        # detectar posibles tablas de alumnos
        exp_candidate = None
        has_nameish = any(c in cols for c in CANDIDATE_NAME_COLS)
        for c in CANDIDATE_EXP_COLS:
            if c in cols:
                exp_candidate = c
                break
        distinct_exp = count_distinct_exp(conn, name, exp_candidate) if (readable and exp_candidate) else -1

        summary.append({
            "table_name": name,
            "table_type": ttype,
            "row_count": total,
            "has_name_fields": "yes" if has_nameish else "no",
            "exp_col": exp_candidate or "",
            "distinct_exp": distinct_exp if distinct_exp >= 0 else "",
            "columns": ", ".join(cols),
        })

    # ordenar por row_count desc
    summary.sort(key=lambda x: (x["row_count"] if isinstance(x["row_count"], int) else -1), reverse=True)

    # imprimir top 15
    print("Tabla".ljust(35), "Tipo".ljust(12), "Filas".rjust(8), "Exp?".rjust(6), "DistinctExp".rjust(12))
    print("-"*80)
    for row in summary[:15]:
        print(
            str(row["table_name"]).ljust(35),
            str(row["table_type"]).ljust(12),
            str(row["row_count"]).rjust(8),
            ("Y" if row["exp_col"] else "N").rjust(6),
            (str(row["distinct_exp"]) if row["distinct_exp"] != "" else "").rjust(12),
        )

    # export CSV
    import csv
    with open(a.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["table_name","table_type","row_count","has_name_fields","exp_col","distinct_exp","columns"])
        w.writeheader()
        for r in summary:
            w.writerow(r)

    print(f"[ok] Resumen exportado a: {a.out}")

if __name__ == "__main__":
    main()
