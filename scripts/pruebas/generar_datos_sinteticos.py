#!/usr/bin/env python3
"""Genera scripts/pruebas/datos-sinteticos.sql: datos SINTÉTICOS con la forma de producción
para una base de datos en Flyway V17 (esquema de V1..V17).

- Solo biblioteca estándar. Determinista: semilla y "hoy" fijos.
- Nombres inventados (combinaciones aleatorias), DNI generados con letra de control válida,
  correos @example.test.
- El SQL borra antes las tablas que rellena (no toca plantilla_preinscripcion ni
  flyway_schema_history), así que se puede cargar varias veces sobre la misma base.

Uso: python scripts/pruebas/generar_datos_sinteticos.py [ruta_salida]
"""
import hashlib
import random
import sys
from datetime import date, datetime, timedelta
from pathlib import Path

SEMILLA = 20261013
HOY = date(2026, 10, 13)
TEMPORADA = "2026-2027"
rng = random.Random(SEMILLA)

# Hash bcrypt (coste 10) de la contraseña de pruebas documentada en README.md.
# Generado con: docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'Pruebas-Taemoi-2026!'
# (prefijo $2y$ cambiado a $2a$, mismo algoritmo, el que emite Spring Security).
BCRYPT_PASSWORD = "$2a$10$cGP1XzmNaGM3aN1zBb2Xgu9kxqubleJSV4iUxyS/3HEiz5CeHig.a"

# --------------------------------------------------------------------------- catálogos

GRADOS = [  # orden de declaración de TipoGrado => id 1..19
    "BLANCO", "BLANCO_AMARILLO", "AMARILLO", "AMARILLO_NARANJA", "NARANJA", "NARANJA_VERDE",
    "VERDE", "VERDE_AZUL", "AZUL", "AZUL_ROJO", "ROJO", "ROJO_NEGRO_1_PUM", "ROJO_NEGRO_2_PUM",
    "ROJO_NEGRO_3_PUM", "NEGRO_1_DAN", "NEGRO_2_DAN", "NEGRO_3_DAN", "NEGRO_4_DAN", "NEGRO_5_DAN",
]
GRADO_ID = {g: i + 1 for i, g in enumerate(GRADOS)}

PRODUCTO_EXAMEN = {
    "BLANCO_AMARILLO": "DERECHO A EXAMEN BLANCO/AMARILLO", "AMARILLO": "DERECHO A EXAMEN AMARILLO",
    "AMARILLO_NARANJA": "DERECHO A EXAMEN AMARILLO/NARANJA", "NARANJA": "DERECHO A EXAMEN NARANJA",
    "NARANJA_VERDE": "DERECHO A EXAMEN NARANJA/VERDE", "VERDE": "DERECHO A EXAMEN VERDE",
    "VERDE_AZUL": "DERECHO A EXAMEN VERDE/AZUL", "AZUL": "DERECHO A EXAMEN AZUL",
    "AZUL_ROJO": "DERECHO A EXAMEN AZUL/ROJO", "ROJO": "DERECHO A EXAMEN ROJO",
    "ROJO_NEGRO_1_PUM": "DERECHO A EXAMEN ROJO/NEGRO 1º PUM",
    "ROJO_NEGRO_2_PUM": "DERECHO A EXAMEN ROJO/NEGRO 2º PUM",
    "NEGRO_1_DAN": "DERECHO A EXAMEN NEGRO 1º DAN", "NEGRO_2_DAN": "DERECHO A EXAMEN NEGRO 2º DAN",
    "NEGRO_3_DAN": "DERECHO A EXAMEN NEGRO 3º DAN",
}
PRODUCTO_RECOMPENSA = [
    "DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA",
    "DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA",
    "DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA",
    "DERECHO DE CAMBIO A NARANJA POR RECOMPENSA",
    "DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA",
    "DERECHO DE CAMBIO A VERDE RECOMPENSA",
    "DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA",
    "DERECHO DE CAMBIO A AZUL POR RECOMPENSA",
    "DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA",
    "DERECHO DE CAMBIO A ROJO POR RECOMPENSA",
    "DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA",
]

# Progresión (valores-actuales.md, sección 2).
TKD_MENOR = {
    "BLANCO": "BLANCO_AMARILLO", "BLANCO_AMARILLO": "AMARILLO", "AMARILLO": "AMARILLO_NARANJA",
    "AMARILLO_NARANJA": "NARANJA", "NARANJA": "NARANJA_VERDE", "NARANJA_VERDE": "VERDE",
    "VERDE": "VERDE_AZUL", "VERDE_AZUL": "AZUL", "AZUL": "AZUL_ROJO", "AZUL_ROJO": "ROJO",
    "ROJO": "ROJO_NEGRO_1_PUM", "ROJO_NEGRO_1_PUM": "ROJO_NEGRO_2_PUM",
    "ROJO_NEGRO_2_PUM": "ROJO_NEGRO_3_PUM",
}
ADULTO = {  # Taekwondo adultos y Kickboxing
    "BLANCO": "AMARILLO", "BLANCO_AMARILLO": "AMARILLO", "AMARILLO": "NARANJA",
    "AMARILLO_NARANJA": "NARANJA", "NARANJA": "VERDE", "NARANJA_VERDE": "VERDE", "VERDE": "AZUL",
    "VERDE_AZUL": "AZUL", "AZUL": "ROJO", "AZUL_ROJO": "ROJO", "ROJO": "NEGRO_1_DAN",
    "NEGRO_1_DAN": "NEGRO_2_DAN", "NEGRO_2_DAN": "NEGRO_3_DAN", "NEGRO_3_DAN": "NEGRO_4_DAN",
    "NEGRO_4_DAN": "NEGRO_5_DAN",
}
ESCALA_MENOR_TKD = ["BLANCO", "BLANCO_AMARILLO", "AMARILLO", "AMARILLO_NARANJA", "NARANJA",
                    "NARANJA_VERDE", "VERDE", "VERDE_AZUL", "AZUL", "AZUL_ROJO", "ROJO",
                    "ROJO_NEGRO_1_PUM", "ROJO_NEGRO_2_PUM", "ROJO_NEGRO_3_PUM"]
ESCALA_ADULTO = ["BLANCO", "AMARILLO", "NARANJA", "VERDE", "AZUL", "ROJO", "NEGRO_1_DAN",
                 "NEGRO_2_DAN", "NEGRO_3_DAN", "NEGRO_4_DAN"]

EDAD_ADULTA = {"TAEKWONDO": 14, "KICKBOXING": 15, "PILATES": 14, "DEFENSA_PERSONAL_FEMENINA": 14}
NOMBRE_DEPORTE = {"TAEKWONDO": "Taekwondo", "KICKBOXING": "Kickboxing", "PILATES": "Pilates",
                  "DEFENSA_PERSONAL_FEMENINA": "Defensa Personal Femenina"}
CUANTIA = {"ADULTO": 30.0, "ADULTO_GRUPO": 20.0, "FAMILIAR": 0.0, "INFANTIL": 28.0,
           "INFANTIL_GRUPO": 20.0, "HERMANOS": 26.0, "KICKBOXING": 30.0, "PILATES": 30.0,
           "DEFENSA_PERSONAL_FEMENINA": 25.0}
CUANTIA_PADRES_HIJOS = {"PADRE": 28.0, "MADRE": 28.0, "HIJO": 26.0, "HIJA": 26.0}
MESES = ["ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO", "JULIO", "AGOSTO",
         "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"]

# Grupos y turnos tal y como los siembra InicializadorDatos (grupo id 1..10, turno id 1..19).
GRUPOS = [
    (1, "Taekwondo Lunes y Miércoles Primer Turno", "Taekwondo", "TAEKWONDO"),
    (2, "Taekwondo Lunes y Miércoles Segundo Turno", "Taekwondo", "TAEKWONDO"),
    (3, "Taekwondo Lunes y Miércoles Tercer Turno", "Taekwondo", "TAEKWONDO"),
    (4, "Taekwondo Martes y Jueves Primer Turno", "Taekwondo", "TAEKWONDO"),
    (5, "Taekwondo Martes y Jueves Segundo Turno", "Taekwondo", "TAEKWONDO"),
    (6, "Taekwondo Martes y Jueves Tercer Turno", "Taekwondo", "TAEKWONDO"),
    (7, "Taekwondo Competición", "Taekwondo Competición", "TAEKWONDO"),
    (8, "Pilates Martes y Jueves", "Pilates", "PILATES"),
    (9, "Kickboxing Lunes y Miércoles", "Kickboxing", "KICKBOXING"),
    (10, "Defensa Personal Femenina Martes y Viernes", "Defensa Personal Femenina",
     "DEFENSA_PERSONAL_FEMENINA"),
]
TURNOS = [  # (id, dia, inicio, fin, grupo_id, tipo)
    (1, "Lunes", "17:00", "18:00", 1, "Taekwondo Primer Turno Lunes"),
    (2, "Lunes", "18:00", "19:00", 2, "Taekwondo Segundo Turno Lunes"),
    (3, "Lunes", "19:00", "20:00", 3, "Taekwondo Tercer Turno Lunes"),
    (4, "Lunes", "20:15", "21:15", 9, "Kickboxing Lunes"),
    (5, "Martes", "10:00", "11:15", 8, "Pilates Martes"),
    (6, "Martes", "17:00", "18:00", 4, "Taekwondo Primer Turno Martes"),
    (7, "Martes", "18:00", "19:00", 5, "Taekwondo Segundo Turno Martes"),
    (8, "Martes", "19:00", "20:00", 6, "Taekwondo Tercer Turno Martes"),
    (9, "Martes", "20:00", "21:00", 10, "Defensa Personal Femenina Martes"),
    (10, "Miércoles", "17:00", "18:00", 1, "Taekwondo Primer Turno Miércoles"),
    (11, "Miércoles", "18:00", "19:00", 2, "Taekwondo Segundo Turno Miércoles"),
    (12, "Miércoles", "19:00", "20:30", 3, "Taekwondo Tercer Turno Miércoles"),
    (13, "Miércoles", "20:00", "21:00", 9, "Kickboxing Miércoles"),
    (14, "Jueves", "10:00", "11:15", 8, "Pilates Jueves"),
    (15, "Jueves", "17:00", "18:00", 4, "Taekwondo Primer Turno Jueves"),
    (16, "Jueves", "18:00", "19:00", 5, "Taekwondo Segundo Turno Jueves"),
    (17, "Jueves", "19:00", "20:00", 6, "Taekwondo Tercer Turno Jueves"),
    (18, "Jueves", "20:00", "21:30", 7, "Taekwondo Competición"),
    (19, "Viernes", "18:30", "19:30", 10, "Defensa Personal Femenina Viernes"),
]
TURNOS_DE_GRUPO = {}
for _t in TURNOS:
    TURNOS_DE_GRUPO.setdefault(_t[4], []).append(_t)

CATEGORIAS = [(1, "Infantil"), (2, "Precadete"), (3, "Cadete"), (4, "Junior"), (5, "Senior")]

NOMBRES_H = ["Hugo", "Mateo", "Martin", "Lucas", "Leo", "Daniel", "Alejandro", "Pablo", "Manuel",
             "Alvaro", "Adrian", "Mario", "Diego", "Javier", "Bruno", "Marco", "Izan", "Nicolas",
             "Thiago", "Gonzalo", "Ruben", "Sergio", "Ivan", "Rafael", "Joaquin", "Ismael", "Elias",
             "Dario", "Saul", "Teo", "Aitor", "Unai", "Gael", "Eloy", "Cristian", "Raul"]
NOMBRES_M = ["Lucia", "Sofia", "Martina", "Maria", "Julia", "Paula", "Valeria", "Emma", "Daniela",
             "Carla", "Alba", "Noa", "Olivia", "Sara", "Carmen", "Vega", "Lola", "Claudia",
             "Irene", "Alma", "Elena", "Aitana", "Candela", "Rocio", "Nerea", "Ines", "Marta",
             "Adriana", "Ainhoa", "Celia", "Africa", "Triana", "Reyes", "Macarena", "Nuria", "Eva"]
APELLIDOS = ["Garcia", "Rodriguez", "Gonzalez", "Fernandez", "Lopez", "Martinez", "Sanchez",
             "Perez", "Gomez", "Martin", "Jimenez", "Ruiz", "Hernandez", "Diaz", "Moreno",
             "Alvarez", "Romero", "Navarro", "Torres", "Dominguez", "Vazquez", "Ramos", "Gil",
             "Ramirez", "Serrano", "Blanco", "Molina", "Morales", "Suarez", "Ortega", "Delgado",
             "Castro", "Ortiz", "Rubio", "Marin", "Sanz", "Iglesias", "Medina", "Garrido",
             "Cortes", "Castillo", "Santos", "Lozano", "Guerrero", "Cano", "Prieto", "Mendez",
             "Cruz", "Calvo", "Gallego", "Vidal", "Leon", "Marquez", "Herrera", "Pena", "Flores",
             "Cabrera", "Campos", "Vega", "Fuentes", "Carrasco", "Diez", "Caballero", "Reyes"]
CALLES = ["Calle Azahar", "Calle Olivo", "Avenida de la Vega", "Calle Jazmin", "Calle Romero",
          "Plaza del Pozo", "Calle Alamo", "Calle Naranjo", "Calle Mirador", "Calle Albero",
          "Avenida del Aljarafe", "Calle Sierra Norte", "Calle Pinsapo", "Calle Lirio"]
LOCALIDADES = ["Umbrete", "Bollullos de la Mitacion", "Benacazon", "Espartinas", "Villanueva del Ariscal"]

# --------------------------------------------------------------------------- utilidades

LETRAS_DNI = "TRWAGMYFPDXBNJZSQVHLCKE"
_dnis_usados = set()


def nuevo_dni():
    while True:
        n = rng.randint(20000000, 79999999)
        dni = f"{n:08d}{LETRAS_DNI[n % 23]}"
        if dni not in _dnis_usados:
            _dnis_usados.add(dni)
            return dni


def dni_valido(dni):
    return len(dni) == 9 and dni[:8].isdigit() and LETRAS_DNI[int(dni[:8]) % 23] == dni[8]


def es_menor(nacimiento, deporte="TAEKWONDO"):
    return nacimiento.year + EDAD_ADULTA[deporte] > HOY.year


def edad(nacimiento):
    return HOY.year - nacimiento.year - ((HOY.month, HOY.day) < (nacimiento.month, nacimiento.day))


def fecha_aleatoria(desde, hasta):
    return desde + timedelta(days=rng.randint(0, (hasta - desde).days))


def q(v):
    """Literal SQL."""
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return repr(v)
    if isinstance(v, datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S.%f") + "'"
    if isinstance(v, date):
        return "'" + v.isoformat() + "'"
    if isinstance(v, bytes):
        return "X'" + v.hex() + "'"
    if isinstance(v, Raw):
        return v.sql
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


class Raw:
    def __init__(self, sql):
        self.sql = sql


def insert(tabla, columnas, filas, lote=50):
    out = []
    for i in range(0, len(filas), lote):
        valores = ",\n".join("(" + ", ".join(q(v) for v in f) + ")" for f in filas[i:i + lote])
        out.append(f"INSERT INTO `{tabla}` ({', '.join(columnas)}) VALUES\n{valores};")
    return "\n".join(out)


def sha256(texto):
    return hashlib.sha256(texto.encode("utf-8")).hexdigest()


def sin_tilde_minus(s):
    import unicodedata
    s = unicodedata.normalize("NFD", s.lower())
    return "".join(c for c in s if c.isalnum() and unicodedata.category(c) != "Mn")

# --------------------------------------------------------------------------- personas


class Persona:
    def __init__(self, sexo, nacimiento, apellidos, nombre=None):
        self.sexo = sexo
        self.nacimiento = nacimiento
        self.apellidos = apellidos
        self.nombre = nombre or rng.choice(NOMBRES_H if sexo == "H" else NOMBRES_M)
        self.nif = None
        self.email = None
        self.deportes = []          # lista de deportes; el primero es el principal
        self.tarifa = None          # tarifa del deporte principal
        self.rol = None
        self.grupo_familiar = None
        self.activo = True
        self.responsable = None     # (nombre, nif)
        self.direccion = f"{rng.choice(CALLES)}, {rng.randint(1, 60)}, {rng.choice(LOCALIDADES)}"
        self.telefono = rng.randint(600000000, 699999999)


def apellidos_nuevos():
    return f"{rng.choice(APELLIDOS)} {rng.choice(APELLIDOS)}"


def nacimiento_menor(min_anio=2013, max_anio=2020):
    return fecha_aleatoria(date(min_anio, 1, 1), date(max_anio, 12, 31))


def nacimiento_adulto(min_anio=1968, max_anio=2008):
    return fecha_aleatoria(date(min_anio, 1, 1), date(max_anio, 12, 31))


personas = []
grupos_dni = []  # listas de personas que comparten DNI
familia_seq = 0


def nueva_familia():
    global familia_seq
    familia_seq += 1
    return f"FAM-{familia_seq:03d}"


def email_de(p, extra=""):
    base = f"{sin_tilde_minus(p.nombre)}.{sin_tilde_minus(p.apellidos.split()[0])}"
    return f"{base}{extra}@example.test"


# 20 grupos padre/madre + hijo/a, Taekwondo PADRES_HIJOS (el DNI compartido es el del adulto).
for _ in range(20):
    ap_padre = apellidos_nuevos()
    sexo_p = rng.choice("HM")
    padre = Persona(sexo_p, nacimiento_adulto(1975, 1992), ap_padre)
    sexo_h = rng.choice("HM")
    hijo = Persona(sexo_h, nacimiento_menor(2013, 2019),
                   f"{ap_padre.split()[0]} {rng.choice(APELLIDOS)}")
    fam = nueva_familia()
    for p, rol in ((padre, "PADRE" if sexo_p == "H" else "MADRE"),
                   (hijo, "HIJO" if sexo_h == "H" else "HIJA")):
        p.deportes = ["TAEKWONDO"]
        p.tarifa = "PADRES_HIJOS"
        p.rol = rol
        p.grupo_familiar = fam
    grupos_dni.append([padre, hijo])

# 12 grupos de hermanos menores (DNI de un progenitor que no es alumno), HERMANOS.
for _ in range(12):
    ap = apellidos_nuevos()
    a = Persona(rng.choice("HM"), nacimiento_menor(2013, 2016), ap)
    b = Persona(rng.choice("HM"), nacimiento_menor(2016, 2020), ap)
    while b.nombre == a.nombre:
        b.nombre = rng.choice(NOMBRES_H if b.sexo == "H" else NOMBRES_M)
    fam = nueva_familia()
    for p in (a, b):
        p.deportes = ["TAEKWONDO"]
        p.tarifa = "HERMANOS"
        p.rol = "HIJO" if p.sexo == "H" else "HIJA"
        p.grupo_familiar = fam
    grupos_dni.append([a, b])

# 1 grupo de tres: madre + dos hijos.
ap = apellidos_nuevos()
madre = Persona("M", nacimiento_adulto(1980, 1988), ap)
h1 = Persona("H", nacimiento_menor(2014, 2016), f"{ap.split()[0]} {rng.choice(APELLIDOS)}")
h2 = Persona("M", nacimiento_menor(2017, 2019), h1.apellidos)
fam = nueva_familia()
for p, rol in ((madre, "MADRE"), (h1, "HIJO"), (h2, "HIJA")):
    p.deportes = ["TAEKWONDO"]
    p.tarifa = "PADRES_HIJOS"
    p.rol = rol
    p.grupo_familiar = fam
grupos_dni.append([madre, h1, h2])

# 7 grupos solo de adultos (parejas o hermanos adultos con el mismo DNI registrado).
combos_adultos = [("TAEKWONDO", "PILATES"), ("TAEKWONDO", "TAEKWONDO"), ("KICKBOXING", "TAEKWONDO"),
                  ("PILATES", "DEFENSA_PERSONAL_FEMENINA"), ("TAEKWONDO", "KICKBOXING"),
                  ("TAEKWONDO", "DEFENSA_PERSONAL_FEMENINA"), ("KICKBOXING", "PILATES")]
for dep_a, dep_b in combos_adultos:
    a = Persona("H" if dep_a in ("TAEKWONDO", "KICKBOXING") else "M", nacimiento_adulto(1970, 2006),
                apellidos_nuevos())
    b = Persona("M", nacimiento_adulto(1970, 2006), apellidos_nuevos())
    a.deportes, b.deportes = [dep_a], [dep_b]
    grupos_dni.append([a, b])

assert len(grupos_dni) == 40 and sum(len(g) for g in grupos_dni) == 81

for g in grupos_dni:
    dni = nuevo_dni()
    correo = email_de(g[0], str(rng.randint(1, 99)))
    for p in g:
        p.nif = dni
        p.email = correo
        personas.append(p)

# 69 alumnos sin DNI compartido.
singles = []
for i in range(10):   # menores pequeños sin DNI
    p = Persona(rng.choice("HM"), nacimiento_menor(2017, 2021), apellidos_nuevos())
    p.deportes = ["TAEKWONDO"]
    singles.append(p)
for i in range(18):   # menores con DNI propio
    p = Persona(rng.choice("HM"), nacimiento_menor(2013, 2018), apellidos_nuevos())
    p.deportes = ["KICKBOXING"] if i < 3 else ["TAEKWONDO"]
    singles.append(p)
for i in range(3):    # adolescentes de 13-14 años en Kickboxing (menores en KB)
    p = Persona(rng.choice("HM"), fecha_aleatoria(date(2012, 1, 1), date(2013, 12, 31)), apellidos_nuevos())
    p.deportes = ["KICKBOXING"]
    singles.append(p)
adult_plan = (["TAEKWONDO"] * 16 + [["TAEKWONDO", "KICKBOXING"]] * 5 + ["KICKBOXING"] * 6
              + ["PILATES"] * 6 + [["PILATES", "DEFENSA_PERSONAL_FEMENINA"]] * 2
              + ["DEFENSA_PERSONAL_FEMENINA"] * 3)
for plan in adult_plan:
    deps = plan if isinstance(plan, list) else [plan]
    mujer = deps[0] in ("PILATES", "DEFENSA_PERSONAL_FEMENINA") or "DEFENSA_PERSONAL_FEMENINA" in deps
    sexo = "M" if mujer else rng.choice("HM")
    p = Persona(sexo, nacimiento_adulto(1966, 2008), apellidos_nuevos())
    p.deportes = list(deps)
    singles.append(p)
assert len(singles) == 69, len(singles)

for idx, p in enumerate(singles):
    if idx >= 10:
        p.nif = nuevo_dni()
    if rng.random() < 0.9:
        p.email = email_de(p, str(rng.randint(100, 999)))
    personas.append(p)

# Tarifas de los que no la tienen todavía.
for p in personas:
    principal = p.deportes[0]
    if p.tarifa:
        continue
    if principal == "TAEKWONDO":
        if es_menor(p.nacimiento):
            p.tarifa = rng.choices(["INFANTIL", "INFANTIL_GRUPO"], [9, 1])[0]
        else:
            p.tarifa = rng.choices(["ADULTO", "ADULTO_GRUPO", "FAMILIAR"], [12, 2, 1])[0]
    else:
        p.tarifa = principal
    if p.rol is None:
        p.rol = "NINGUNO"

# Bajas (~14 %), nunca dentro de un grupo de DNI compartido que quede sin activos.
candidatos_baja = [p for p in personas if p.grupo_familiar is None]
for p in rng.sample(candidatos_baja, 21):
    p.activo = False

# Responsable legal para algunos menores: siempre un DNI nuevo, nunca el DNI compartido.
for p in personas:
    if es_menor(p.nacimiento) and rng.random() < 0.35:
        tutor_nombre = f"{rng.choice(NOMBRES_H + NOMBRES_M)} {p.apellidos}"
        p.responsable = (tutor_nombre, nuevo_dni())

# Discapacidad para un par de alumnos.
for p in rng.sample(personas, 3):
    p.discapacidad = True

# --------------------------------------------------------------------------- fichas

# Altas: el expediente sigue el orden de alta inicial, con huecos hasta ~205.
for p in personas:
    primer_anio = max(2014, p.nacimiento.year + 4)
    p.alta_inicial = fecha_aleatoria(date(primer_anio, 9, 1), date(2026, 9, 30))
    if not p.activo:
        desde = min(max(p.alta_inicial + timedelta(days=60), date(2024, 1, 1)), date(2026, 9, 30))
        p.baja = fecha_aleatoria(desde, date(2026, 9, 30))
    else:
        p.baja = None
personas.sort(key=lambda p: (p.alta_inicial, p.apellidos, p.nombre))
expedientes = sorted(rng.sample(range(1, 206), len(personas)))
for i, p in enumerate(personas):
    p.id = i + 1
    p.expediente = expedientes[i]

numeros_licencia = rng.sample(range(10000, 99999), 400)


def elegir_grado(p, deporte):
    if deporte not in ("TAEKWONDO", "KICKBOXING"):
        return None
    anios = max(0, (HOY - p.alta_inicial).days // 365)
    menor = es_menor(p.nacimiento, deporte)
    if deporte == "TAEKWONDO" and menor:
        tope = min(len(ESCALA_MENOR_TKD) - 1, anios * 2 + 1)
        return ESCALA_MENOR_TKD[rng.randint(0, tope)]
    escala = ESCALA_ADULTO[:7] if deporte == "KICKBOXING" else ESCALA_ADULTO
    tope = min(len(escala) - 1, anios + 1)
    grado = escala[rng.randint(0, tope)]
    # Adolescentes que pasaron a adultos con un PUM todavía sin convertir.
    if deporte == "TAEKWONDO" and edad(p.nacimiento) in (14, 15, 16) and anios >= 6 and rng.random() < 0.6:
        grado = rng.choice(["ROJO_NEGRO_1_PUM", "ROJO_NEGRO_2_PUM"])
    return grado


def categoria_de(nac):
    e = edad(nac)
    if e <= 9:
        return 1
    if e <= 11:
        return 2
    if e <= 13:
        return 3
    if e <= 16:
        return 4
    return 5


alumno_deportes = []  # dicts
ad_id = 0
for p in personas:
    p.ads = []
    for orden, dep in enumerate(p.deportes):
        ad_id += 1
        grado = elegir_grado(p, dep)
        principal = orden == 0
        tarifa = p.tarifa if principal else dep if dep != "TAEKWONDO" else "ADULTO"
        if tarifa == "PADRES_HIJOS":
            cuantia = CUANTIA_PADRES_HIJOS[p.rol]
        else:
            cuantia = CUANTIA[tarifa]
        alta = p.alta_inicial if principal else fecha_aleatoria(p.alta_inicial, date(2026, 9, 30))
        activo = p.activo and (principal or rng.random() < 0.85)
        baja = None if activo else (p.baja or fecha_aleatoria(alta, date(2026, 9, 30)))
        competidor = (dep == "TAEKWONDO" and edad(p.nacimiento) >= 8 and grado not in (None, "BLANCO")
                      and rng.random() < 0.18) or (dep == "KICKBOXING" and not es_menor(p.nacimiento, dep)
                                                    and rng.random() < 0.25)
        tiene_licencia = dep in ("TAEKWONDO", "KICKBOXING") and (grado != "BLANCO" or competidor)
        fecha_grado = None
        if grado and grado != "BLANCO":
            anio = rng.randint(max(alta.year, 2019), 2026)
            fecha_grado = date(anio, 6, 13) if anio == 2026 or rng.random() < 0.5 else date(anio, 12, 14)
            if fecha_grado > HOY or fecha_grado < alta:
                fecha_grado = date(2026, 6, 13) if alta < date(2026, 6, 13) else None
                if fecha_grado is None:
                    grado = "BLANCO"
                    tiene_licencia = competidor
        ad = {
            "id": ad_id, "persona": p, "deporte": dep, "principal": principal, "activo": activo,
            "apto": False, "alta": alta, "alta_inicial": alta, "baja": baja,
            "grado": grado, "fecha_grado": fecha_grado,
            "fecha_licencia": date(2026, 1, rng.randint(8, 31)) if tiene_licencia else None,
            "tarifa": tarifa, "cuantia": cuantia,
            "rol": p.rol if principal else "NINGUNO",
            "grupo_familiar": p.grupo_familiar if principal else None,
            "competidor": competidor,
            "alta_competicion": fecha_aleatoria(max(alta, date(2023, 9, 1)), date(2026, 9, 30)) if competidor else None,
            "categoria": categoria_de(p.nacimiento) if competidor and dep == "TAEKWONDO" else None,
            "peso": round(max(22.0, min(95.0, 18 + edad(p.nacimiento) * 2.6 + rng.uniform(-6, 12))), 1) if competidor else None,
            "tiene_licencia": tiene_licencia,
            "numero_licencia": numeros_licencia.pop() if tiene_licencia else None,
        }
        ad["fecha_peso"] = date(2026, 9, rng.randint(1, 30)) if competidor else None
        p.ads.append(ad)
        alumno_deportes.append(ad)

# Aptos para examen de diciembre: TKD/KB activos con grado de origen en la progresión.
for ad in alumno_deportes:
    if ad["activo"] and ad["grado"] and rng.random() < 0.3:
        mapa = TKD_MENOR if ad["deporte"] == "TAEKWONDO" and es_menor(ad["persona"].nacimiento) else ADULTO
        sig = mapa.get(ad["grado"])
        if sig and sig in PRODUCTO_EXAMEN:
            ad["apto"] = True
            ad["siguiente"] = sig

# --------------------------------------------------------------------------- productos

productos = [
    ("MENSUALIDAD", 28.0), ("LICENCIA FEDERATIVA", 0.0), ("RESERVA DE PLAZA", 10.0),
    ("TARIFA COMPETIDOR TAEKWONDO", 20.0), ("TARIFA COMPETIDOR KICKBOXING", 20.0),
    ("LICENCIA FEDERATIVA TAEKWONDO ADULTO", 46.0), ("LICENCIA FEDERATIVA TAEKWONDO INFANTIL", 35.0),
    ("LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD", 30.0),
    ("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO", 35.0),
    ("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL", 20.0),
    ("PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD", 20.0),
    ("LICENCIA FEDERATIVA KICKBOXING ADULTO", 71.95), ("LICENCIA FEDERATIVA KICKBOXING INFANTIL", 54.95),
    ("MATRICULA", 20.0),
]
precio_examen = {}
for g, concepto in PRODUCTO_EXAMEN.items():
    if "DAN" in g:
        precio = 150.0 + 30 * int(g.split("_")[1])
    elif "PUM" in g:
        precio = 90.0
    elif "_" in g:
        precio = 20.0
    else:
        precio = 25.0
    productos.append((concepto, precio))
    precio_examen[g] = precio
productos.append(("DERECHO A EXAMEN ROJO BORDADO", 30.0))
for concepto in PRODUCTO_RECOMPENSA:
    productos.append((concepto, 15.0))
PRODUCTO_ID = {c: i + 1 for i, (c, _) in enumerate(productos)}
PRODUCTO_PRECIO = dict(productos)

producto_alumno = []  # filas


def asignar_producto(ad, concepto_producto, concepto, precio, asignado, pagado, fecha_pago=None, notas=None):
    pa_id = len(producto_alumno) + 1
    producto_alumno.append([
        pa_id, 1, concepto, datetime.combine(asignado, datetime.min.time()).replace(hour=10),
        datetime.combine(fecha_pago, datetime.min.time()).replace(hour=18) if pagado and fecha_pago else None,
        notas, pagado, precio, ad["persona"].id, PRODUCTO_ID[concepto_producto], ad["id"],
    ])
    return pa_id


for ad in alumno_deportes:
    p = ad["persona"]
    dep = ad["deporte"]
    # Mensualidades: septiembre y octubre de 2026 para activos; la última antes de la baja para bajas.
    if ad["activo"]:
        meses = [(2026, 9), (2026, 10)]
    elif ad["baja"]:
        meses = [(ad["baja"].year, ad["baja"].month)]
    else:
        meses = []
    for anio, mes in meses:
        concepto = f"MENSUALIDAD {MESES[mes - 1]} {anio} - {dep}"
        asignado = date(anio, mes, 1)
        if (anio, mes) == (2026, 10):
            pagado = rng.random() < 0.7
        else:
            pagado = rng.random() < 0.95
        asignar_producto(ad, "MENSUALIDAD", concepto, ad["cuantia"], asignado, pagado,
                         asignado + timedelta(days=rng.randint(0, 6)))
    # Licencia federativa del año.
    if ad["tiene_licencia"] and ad["fecha_licencia"]:
        if dep == "TAEKWONDO":
            tramo = "DISCAPACIDAD" if getattr(p, "discapacidad", False) else (
                "INFANTIL" if es_menor(p.nacimiento, dep) else "ADULTO")
            prod = f"LICENCIA FEDERATIVA TAEKWONDO {tramo}"
        else:
            prod = f"LICENCIA FEDERATIVA KICKBOXING {'INFANTIL' if es_menor(p.nacimiento, dep) else 'ADULTO'}"
        concepto = f"RENOVACION {prod} ENERO 2026" if rng.random() < 0.6 else prod
        asignar_producto(ad, prod, concepto, PRODUCTO_PRECIO[prod], ad["fecha_licencia"],
                         rng.random() < 0.9, ad["fecha_licencia"] + timedelta(days=3))
    # Tarifa de competidor de octubre.
    if ad["competidor"] and ad["activo"] and dep in ("TAEKWONDO", "KICKBOXING"):
        prod = f"TARIFA COMPETIDOR {dep}"
        asignar_producto(ad, prod, f"{prod} OCTUBRE 2026", 20.0, date(2026, 10, 1), rng.random() < 0.6,
                         date(2026, 10, 4))
    # Matrícula de las altas de esta temporada y reserva de plaza de antiguos.
    if ad["principal"] and ad["alta_inicial"] >= date(2026, 9, 1):
        asignar_producto(ad, "MATRICULA", "MATRICULA", 20.0, ad["alta_inicial"], True, ad["alta_inicial"])
    elif ad["principal"] and ad["activo"] and dep == "TAEKWONDO" and rng.random() < 0.4:
        asignar_producto(ad, "RESERVA DE PLAZA", "RESERVA DE PLAZA", 10.0, date(2026, 6, 15),
                         rng.random() < 0.85, date(2026, 6, 20))

# --------------------------------------------------------------------------- convocatorias

convocatorias = [
    (1, "TAEKWONDO", date(2026, 6, 13)), (2, "KICKBOXING", date(2026, 6, 13)),
    (3, "TAEKWONDO", date(2026, 12, 12)), (4, "KICKBOXING", date(2026, 12, 12)),
]
alumno_convocatoria = []


def grado_anterior(ad):
    menor = es_menor(ad["persona"].nacimiento) and ad["deporte"] == "TAEKWONDO"
    mapa = TKD_MENOR if menor else ADULTO
    candidatos = [k for k, v in mapa.items() if v == ad["grado"]]
    return candidatos[0] if candidatos else None


# Junio 2026: quienes subieron de grado en esa convocatoria (pagado).
for ad in alumno_deportes:
    if ad["fecha_grado"] == date(2026, 6, 13) and ad["grado"]:
        antes = grado_anterior(ad)
        if not antes or ad["grado"] not in PRODUCTO_EXAMEN:
            continue
        conv = 1 if ad["deporte"] == "TAEKWONDO" else 2
        precio = precio_examen[ad["grado"]]
        pa = asignar_producto(ad, PRODUCTO_EXAMEN[ad["grado"]],
                              f"{PRODUCTO_EXAMEN[ad['grado']]} - {NOMBRE_DEPORTE[ad['deporte']]}",
                              precio, date(2026, 5, 20), True, date(2026, 6, 1))
        alumno_convocatoria.append([len(alumno_convocatoria) + 1, precio, date(2026, 6, 1), antes,
                                    ad["grado"], True, ad["persona"].id, conv, pa, ad["id"]])

# Diciembre 2026: aptos convocados; algunos ya han pagado, otros no.
for ad in alumno_deportes:
    if ad.get("apto") and rng.random() < 0.6:
        conv = 3 if ad["deporte"] == "TAEKWONDO" else 4
        sig = ad["siguiente"]
        precio = precio_examen[sig]
        pagado = rng.random() < 0.4
        pa = asignar_producto(ad, PRODUCTO_EXAMEN[sig],
                              f"{PRODUCTO_EXAMEN[sig]} - {NOMBRE_DEPORTE[ad['deporte']]}",
                              precio, date(2026, 10, 5), pagado, date(2026, 10, 10))
        alumno_convocatoria.append([len(alumno_convocatoria) + 1, precio,
                                    date(2026, 10, 10) if pagado else None, ad["grado"], sig, pagado,
                                    ad["persona"].id, conv, pa, ad["id"]])
        ad["persona"].derecho_examen = True

# --------------------------------------------------------------------------- grupos y turnos


def grupos_para(ad):
    p = ad["persona"]
    dep = ad["deporte"]
    if dep == "TAEKWONDO":
        e = edad(p.nacimiento)
        dia = 0 if (p.id % 2 == 0) else 3   # Lunes/Miércoles o Martes/Jueves
        if e <= 9:
            gs = [1 + dia]
        elif e <= 13:
            gs = [2 + dia]
        else:
            gs = [3 + dia]
        if ad["competidor"]:
            gs.append(7)
        return gs
    return {"PILATES": [8], "KICKBOXING": [9], "DEFENSA_PERSONAL_FEMENINA": [10]}[dep]


alumno_grupo = set()
alumno_turno = set()
for ad in alumno_deportes:
    if not ad["activo"]:
        continue
    for g in grupos_para(ad):
        alumno_grupo.add((g, ad["persona"].id))
        for t in TURNOS_DE_GRUPO[g]:
            alumno_turno.add((ad["persona"].id, t[0]))

# --------------------------------------------------------------------------- usuarios

usuarios = []   # [id, apellidos, contrasena, email, nombre, alumno_id, auth_provider]
usuario_rol = []
uid = 0


def nuevo_usuario(email, nombre, apellidos, alumno_id=None, roles=(), proveedor="LOCAL"):
    global uid
    uid += 1
    usuarios.append([uid, apellidos[:50], BCRYPT_PASSWORD, email, nombre[:50], alumno_id, proveedor])
    for r in roles:
        usuario_rol.append([uid, r])
    return uid


nuevo_usuario("admin@example.test", "Administracion", "Pruebas", roles=["ROLE_ADMIN"])
nuevo_usuario("gestor1@example.test", "Gestor", "Uno Pruebas", roles=["ROLE_MANAGER"])
nuevo_usuario("gestor2@example.test", "Gestora", "Dos Pruebas", roles=["ROLE_MANAGER"])
nuevo_usuario("direccion@example.test", "Direccion", "Pruebas", roles=["ROLE_ADMIN", "ROLE_MANAGER"])

# Correos de alumnos activos: primero los familiares (DNI compartido), luego el resto.
por_email = {}
for p in personas:
    if p.activo and p.email:
        por_email.setdefault(p.email, []).append(p)
familiares = [e for e in por_email if len(por_email[e]) > 1 or por_email[e][0].grupo_familiar]
resto = [e for e in por_email if e not in familiares]
emails_usuario = familiares + resto

sin_rol_email = emails_usuario.pop()  # usuario sin roles vinculado a un alumno activo
alumno_sin_rol = por_email[sin_rol_email][0]
nuevo_usuario(sin_rol_email, alumno_sin_rol.nombre, alumno_sin_rol.apellidos, alumno_id=alumno_sin_rol.id)
nuevo_usuario("sin-rol@example.test", "Usuario", "Sin Rol Pruebas")

for i, e in enumerate(emails_usuario[:40]):
    titular = por_email[e][0]
    nuevo_usuario(e, titular.nombre, titular.apellidos, alumno_id=titular.id, roles=["ROLE_USER"],
                  proveedor="GOOGLE" if i % 13 == 5 else "LOCAL")

# --------------------------------------------------------------------------- preinscripciones

FIRMA_PNG = bytes.fromhex("89504e470d0a1a0a0000000d4948445200000001000000010806000000"
                          "1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082")
PDF = b"%PDF-1.4\n% documento sintetico de pruebas\n%%EOF\n"


def referencia():
    return "PRE-2026-" + "".join(rng.choice("0123456789ABCDEF") for _ in range(8))


def identidad(dni, tutor_dni, nombre, apellidos, nacimiento):
    if dni:
        return sha256("DNI|" + dni.upper())
    return sha256("MENOR|" + (tutor_dni or "").upper() + "|" + sin_tilde_minus(nombre) + "|"
                  + sin_tilde_minus(apellidos) + "|" + nacimiento.isoformat())


nifs_previos = {}
for _p in personas:
    if _p.nif:
        nifs_previos.setdefault(_p.nif, []).append(_p)


def snapshot(deporte, grupo_id):
    ts = TURNOS_DE_GRUPO[grupo_id]
    nombre = next(g[1] for g in GRUPOS if g[0] == grupo_id)
    return NOMBRE_DEPORTE[deporte] + " · " + "; ".join(f"{t[1]} {t[2]}-{t[3]} ({nombre})" for t in ts)


# La preinscripción finalizada se vincula al alta más reciente de taekwondo con DNI propio.
finalizada_alumno = max((p for p in personas if p.nif and p.activo and p.grupo_familiar is None
                         and p.deportes[0] == "TAEKWONDO" and len(nifs_previos[p.nif]) == 1),
                        key=lambda p: p.alta_inicial)
pre_planes = [
    # estado, deporte, grupo, nombre, apellidos, dni, nacimiento, tutor, alumno, observaciones
    ("PENDIENTE", "TAEKWONDO", 1, "Noa", "Prieto Calvo", None, date(2019, 4, 2),
     ("Ruben Prieto Lozano", nuevo_dni()), None, None),
    ("EN_LISTA_ESPERA", "KICKBOXING", 9, "Saul", "Herrera Cano", nuevo_dni(), date(1994, 2, 17), None, None,
     "Puede venir solo los miércoles."),
    ("FINALIZADA", "TAEKWONDO", 3, finalizada_alumno.nombre, finalizada_alumno.apellidos,
     finalizada_alumno.nif, finalizada_alumno.nacimiento, None, finalizada_alumno.id, None),
    ("CANCELADA", "PILATES", 8, "Reyes", "Fuentes Vidal", "PIL-DNI", date(1979, 11, 5), None, None,
     "Cancelada por la interesada."),
    ("PENDIENTE", "PILATES", 8, "Reyes", "Fuentes Vidal", "PIL-DNI", date(1979, 11, 5), None, None,
     "Segundo intento tras cancelar."),
    ("CANCELADA", "DEFENSA_PERSONAL_FEMENINA", 10, "Triana", "Leon Ortega", nuevo_dni(), date(2001, 7, 9),
     None, None, None),
    ("EN_LISTA_ESPERA", "TAEKWONDO", 2, "Gael", "Campos Diez", None, date(2015, 9, 30),
     ("Celia Diez Rubio", nuevo_dni()), None, None),
]
dni_pilates = nuevo_dni()
preinscripciones = []
pre_turnos = []
for i, (estado, dep, grupo, nombre, apellidos, dni, nac, tutor, alumno_id, obs) in enumerate(pre_planes, 1):
    if dni == "PIL-DNI":
        dni = dni_pilates
    creada = datetime(2026, 6, 20 + i, 11, 30) if estado != "PENDIENTE" else datetime(2026, 9, 1 + i, 19, 5)
    actualizada = creada + timedelta(days=2)
    tutor_nombre, tutor_dni = tutor if tutor else (None, None)
    preinscripciones.append({
        "id": i, "referencia": referencia(), "temporada": TEMPORADA, "deporte": dep, "estado": estado,
        "nombre": nombre, "apellidos": apellidos, "dni": dni,
        "identidad_hash": identidad(dni, tutor_dni, nombre, apellidos, nac),
        "fecha_nacimiento": nac, "direccion": f"{rng.choice(CALLES)}, {rng.randint(1, 40)}, Umbrete",
        "telefono": str(rng.randint(600000000, 699999999)), "telefono2": None,
        "email": f"preinscripcion{i}@example.test", "observaciones": obs, "tiene_discapacidad": False,
        "tutor_nombre": tutor_nombre, "tutor_dni": tutor_dni, "consentimiento_fotografico": i % 2 == 0,
        "aceptacion_normas": True, "firmante_nombre": tutor_nombre or f"{nombre} {apellidos}",
        "firma": FIRMA_PNG, "turno_id": None, "turno_snapshot": None, "grupo_id": grupo,
        "grupo_snapshot": snapshot(dep, grupo), "pdf_firmado": PDF,
        "token_documento_hash": sha256(f"token-sintetico-{i}"), "idempotency_key_hash": sha256(f"idem-{i}"),
        "email_enviado": True, "email_intentos": 1, "email_ultimo_error": None,
        "creada_en": creada, "actualizada_en": actualizada,
        "finalizada_en": actualizada if estado == "FINALIZADA" else None,
        "cancelada_en": actualizada if estado == "CANCELADA" else None,
        "promocionada_en": None, "alumno_id": alumno_id,
        "email_finalizacion_estado": "ENVIADO" if estado == "FINALIZADA" else "NO_ENVIADO",
        "email_finalizacion_enviado_en": actualizada if estado == "FINALIZADA" else None,
        "email_finalizacion_intentos": 1 if estado == "FINALIZADA" else 0,
    })
    for t in TURNOS_DE_GRUPO[grupo]:
        pre_turnos.append([i, t[0]])

# --------------------------------------------------------------------------- comprobaciones

nifs = {}
for p in personas:
    if p.nif:
        assert dni_valido(p.nif), p.nif
        nifs.setdefault(p.nif, []).append(p)
    if p.responsable:
        assert dni_valido(p.responsable[1])
        assert p.responsable[1] != p.nif
compartidos = {k: v for k, v in nifs.items() if len(v) > 1}
assert len(compartidos) == 40, len(compartidos)
assert sum(len(v) for v in compartidos.values()) == 81
assert sum(1 for v in compartidos.values() if any(es_menor(x.nacimiento) for x in v)) == 33
for v in compartidos.values():
    assert len({(x.nombre, x.apellidos, x.nacimiento) for x in v}) == len(v)
    assert len({x.nacimiento for x in v}) == len(v)
assert all(p.ads for p in personas)
ocupacion = {}
for (_, t) in alumno_turno:
    ocupacion[t] = ocupacion.get(t, 0) + 1
assert max(ocupacion.values()) <= 36, ocupacion

# --------------------------------------------------------------------------- SQL

TABLAS_A_VACIAR = [
    "preinscripcion_turno", "preinscripcion", "usuario_rol", "registro_pendiente", "usuario",
    "alumno_convocatoria", "convocatoria", "producto_alumno", "alumno_turno", "alumno_grupo",
    "alumno_reto_diario_log", "documento", "alumno_deporte", "alumno", "turno", "grupo", "producto",
    "evento", "imagen", "categoria", "grado", "configuracion_sistema", "auditoria_evento",
]

partes = [
    "-- Datos SINTÉTICOS para el entorno local de pruebas (Flyway V17).",
    "-- Generado por scripts/pruebas/generar_datos_sinteticos.py (semilla %d, hoy %s). No editar a mano."
    % (SEMILLA, HOY.isoformat()),
    "SET NAMES utf8mb4;",
    "SET FOREIGN_KEY_CHECKS = 0;",
]
partes += [f"DELETE FROM `{t}`;" for t in TABLAS_A_VACIAR]
partes.append("SET FOREIGN_KEY_CHECKS = 1;")
partes.append("START TRANSACTION;")

partes.append(insert("grado", ["id", "tipo_grado"], [[GRADO_ID[g], g] for g in GRADOS]))
partes.append(insert("categoria", ["id", "nombre", "tipo_categoria"], [[i, n, None] for i, n in CATEGORIAS]))
partes.append(insert("grupo", ["id", "nombre", "tipo", "deporte", "rango_edad_min", "rango_edad_max"],
                     [[g[0], g[1], g[2], g[3], None, None] for g in GRUPOS]))
partes.append(insert("turno", ["id", "dia_semana", "hora_inicio", "hora_fin", "grupo_id", "tipo"],
                     [list(t) for t in TURNOS]))
partes.append(insert("producto", ["id", "concepto", "precio"],
                     [[PRODUCTO_ID[c], c, pr] for c, pr in productos]))
partes.append(insert("configuracion_sistema", ["id", "clave", "valor"], [[1, "limite_turno", 36]]))
partes.append(insert("evento", ["id", "titulo", "descripcion", "fecha_evento", "orden", "foto_evento_id", "visible"], [
    [1, "Exhibicion de fin de temporada", "Exhibicion de taekwondo y kickboxing con las familias (evento sintetico).",
     date(2026, 6, 27), 1, None, True],
    [2, "Campeonato provincial", "Participacion del equipo de competicion (evento sintetico).",
     date(2026, 11, 21), 2, None, True],
    [3, "Seminario de defensa personal", "Seminario abierto de defensa personal femenina (evento sintetico).",
     date(2026, 10, 24), 3, None, True],
    [4, "Jornada de puertas abiertas", "Clases de prueba gratuitas (evento sintetico, oculto).",
     date(2026, 9, 5), 4, None, False],
]))

# Esquema de V17: el responsable legal todavía va en alumno.responsable_legal_* (no existe `tutor`).
# V25 lo copia a `tutor` y V29 rellena los que solo tienen NIF y borra las columnas.
cols_alumno = ["id", "activo", "apellidos", "apto_para_examen", "autorizacion_web", "competidor",
               "cuantia_tarifa", "deporte", "direccion", "email", "rol_familiar", "grupo_familiar",
               "fecha_alta", "fecha_alta_inicial", "fecha_baja", "fecha_grado", "fecha_licencia",
               "fecha_nacimiento", "fecha_peso", "nif", "nombre", "numero_expediente", "numero_licencia",
               "peso", "telefono", "tiene_derecho_examen", "tiene_discapacidad", "tiene_licencia",
               "tipo_tarifa", "categoria_id", "foto_alumno_id", "grado_id", "responsable_legal_nombre",
               "responsable_legal_nif", "telefono2", "observaciones", "fecha_reto_diario_completado",
               "racha_reto_diario"]
filas_alumno = []
for p in personas:
    a = p.ads[0]  # columnas heredadas = deporte principal
    filas_alumno.append([
        p.id, p.activo, p.apellidos, a["apto"], rng.random() < 0.8, a["competidor"], a["cuantia"],
        a["deporte"], p.direccion, p.email, a["rol"], a["grupo_familiar"], a["alta"], p.alta_inicial,
        p.baja, a["fecha_grado"], a["fecha_licencia"], p.nacimiento, a["fecha_peso"], p.nif, p.nombre,
        p.expediente, a["numero_licencia"], a["peso"], p.telefono, getattr(p, "derecho_examen", False),
        getattr(p, "discapacidad", False), a["tiene_licencia"], a["tarifa"], a["categoria"], None,
        GRADO_ID[a["grado"]] if a["grado"] else None,
        p.responsable[0] if p.responsable else None, p.responsable[1] if p.responsable else None,
        rng.randint(600000000, 699999999) if rng.random() < 0.3 else None,
        "Ficha sintetica de pruebas." if rng.random() < 0.1 else None, None, 0,
    ])
partes.append(insert("alumno", cols_alumno, filas_alumno))

cols_ad = ["id", "activo", "principal", "apto_para_examen", "deporte", "fecha_alta", "fecha_alta_inicial",
           "fecha_baja", "fecha_grado", "fecha_licencia", "fecha_peso", "tipo_tarifa", "cuantia_tarifa",
           "rol_familiar", "grupo_familiar", "competidor", "fecha_alta_competicion",
           "fecha_alta_competidor_inicial", "categoria_id", "peso", "tiene_licencia", "numero_licencia",
           "alumno_id", "grado_id"]
partes.append(insert("alumno_deporte", cols_ad, [[
    a["id"], a["activo"], a["principal"], a["apto"], a["deporte"], a["alta"], a["alta_inicial"], a["baja"],
    a["fecha_grado"], a["fecha_licencia"], a["fecha_peso"], a["tarifa"], a["cuantia"], a["rol"],
    a["grupo_familiar"], a["competidor"], a["alta_competicion"], a["alta_competicion"], a["categoria"],
    a["peso"], a["tiene_licencia"], a["numero_licencia"], a["persona"].id,
    GRADO_ID[a["grado"]] if a["grado"] else None] for a in alumno_deportes]))

partes.append(insert("alumno_grupo", ["grupo_id", "alumno_id"], [list(x) for x in sorted(alumno_grupo)]))
partes.append(insert("alumno_turno", ["alumno_id", "turno_id"], [list(x) for x in sorted(alumno_turno)]))
partes.append(insert("producto_alumno", ["id", "cantidad", "concepto", "fecha_asignacion", "fecha_pago",
                                         "notas", "pagado", "precio", "alumno_id", "producto_id",
                                         "alumno_deporte_id"], producto_alumno))
partes.append(insert("convocatoria", ["id", "deporte", "fecha_convocatoria"], [list(c) for c in convocatorias]))
partes.append(insert("alumno_convocatoria", ["id", "cuantia_examen", "fecha_pago", "grado_actual",
                                             "grado_siguiente", "pagado", "alumno_id", "convocatoria_id",
                                             "producto_alumno_id", "alumno_deporte_id"], alumno_convocatoria))
partes.append(insert("usuario", ["id", "apellidos", "contrasena", "email", "nombre", "alumno_id",
                                 "auth_provider"], usuarios))
partes.append(insert("usuario_rol", ["usuario_id", "roles"], usuario_rol))

cols_pre = ["id", "referencia", "temporada", "deporte", "estado", "nombre", "apellidos", "dni",
            "identidad_hash", "fecha_nacimiento", "direccion", "telefono", "telefono2", "email",
            "observaciones", "tiene_discapacidad", "tutor_nombre", "tutor_dni", "consentimiento_fotografico",
            "aceptacion_normas", "firmante_nombre", "firma", "turno_id", "turno_snapshot", "grupo_id",
            "grupo_snapshot", "pdf_firmado", "token_documento_hash", "idempotency_key_hash",
            "email_enviado", "email_intentos", "email_ultimo_error", "creada_en", "actualizada_en",
            "finalizada_en", "cancelada_en", "promocionada_en", "alumno_id", "email_finalizacion_estado",
            "email_finalizacion_enviado_en", "email_finalizacion_intentos"]
# plantilla_id y plantilla_snapshot salen de la plantilla activa de cada deporte (creada por V3/V17).
for pr in preinscripciones:
    valores = ", ".join(q(pr[c]) for c in cols_pre)
    partes.append(
        f"INSERT INTO `preinscripcion` ({', '.join(cols_pre)}, plantilla_id, plantilla_snapshot)\n"
        f"SELECT {valores}, pp.id, JSON_OBJECT('version', pp.version, 'contenido', pp.contenido, "
        f"'instrucciones', pp.instrucciones)\n"
        f"FROM plantilla_preinscripcion pp WHERE pp.deporte = {q(pr['deporte'])} AND pp.activa = 1;")
partes.append(insert("preinscripcion_turno", ["preinscripcion_id", "turno_id"], pre_turnos))
partes.append("COMMIT;")

salida = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).with_name("datos-sinteticos.sql")
salida.write_text("\n".join(partes) + "\n", encoding="utf-8", newline="\n")
print(f"{salida}: {len(personas)} alumnos, {len(alumno_deportes)} alumno_deporte, "
      f"{len(producto_alumno)} producto_alumno, {len(usuarios)} usuarios, {len(preinscripciones)} preinscripciones")
