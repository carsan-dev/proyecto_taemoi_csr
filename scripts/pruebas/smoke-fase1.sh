#!/usr/bin/env bash
# Pruebas de humo de la fase 1 multi-escuela contra la API real del entorno de pruebas.
# Requisitos: backend en http://localhost:8081 y frontend en http://localhost:8088 (docker-compose.pruebas.yml),
# datos sintéticos + segunda-escuela.sql cargados, Python 3 (no hace falta jq).
# Crea un alumno nuevo en la organización 2 en cada ejecución.
# Uso: scripts/pruebas/smoke-fase1.sh      (sale con código 1 si falla alguna comprobación)
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$DIR/../.." && pwd)"
COMPOSE_FILE="$RAIZ/docker-compose.pruebas.yml"

sql() {  # consulta auxiliar (ids y conteos esperados); salida sin cabeceras
  docker compose -f "$COMPOSE_FILE" exec -T database \
    mysql -N -B -utaemoi -ptaemoi-pruebas-local taemoi_pruebas -e "$1" 2>/dev/null
}

export API="${API:-http://localhost:8081}"
export FRONT="${FRONT:-http://localhost:8088}"
export PASSWORD='Pruebas-Taemoi-2026!'
export ID_ALUMNO_ORG1="$(sql "SELECT MIN(id) FROM alumno WHERE organizacion_id = 1")"
export ID_USUARIO_ORG2="$(sql "SELECT id FROM usuario WHERE email = 'admin2@example.test'")"
export TOTAL_ORG1="$(sql "SELECT COUNT(*) FROM alumno WHERE organizacion_id = 1")"
export ACTIVOS_ORG1="$(sql "SELECT COUNT(*) FROM alumno WHERE organizacion_id = 1 AND activo = 1")"
export MAX_EXP_ORG2="$(sql "SELECT COALESCE(MAX(numero_expediente), 0) FROM alumno WHERE organizacion_id = 2")"
# Familia: usuario FAMILIA de la org 1 cuyo correo comparten varios alumnos activos.
read -r FAMILIA_EMAIL FAMILIA_ALUMNOS <<<"$(sql "
  SELECT u.email, COUNT(*) FROM usuario u
  JOIN membresia m ON m.usuario_id = u.id AND m.organizacion_id = 1 AND m.rol = 'FAMILIA'
  JOIN alumno a ON a.email = u.email AND a.activo = 1 AND a.organizacion_id = 1
  WHERE NOT EXISTS (SELECT 1 FROM membresia m2 WHERE m2.usuario_id = u.id AND m2.organizacion_id <> 1)
  GROUP BY u.id, u.email HAVING COUNT(*) > 1 ORDER BY u.id LIMIT 1")"
export FAMILIA_EMAIL FAMILIA_ALUMNOS
export SIN_ROL_ALUMNO_EMAIL="$(sql "
  SELECT u.email FROM usuario u WHERE u.alumno_id IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id) ORDER BY u.id LIMIT 1")"

PYTHONIOENCODING=utf-8 python - <<'PY'
import http.cookiejar, json, os, sys, urllib.error, urllib.request, uuid

API, FRONT, PWD = os.environ["API"], os.environ["FRONT"], os.environ["PASSWORD"]
E = os.environ
resultados = []


def check(nombre, ok, detalle=""):
    resultados.append(ok)
    print(("PASS " if ok else "FAIL ") + nombre + ("" if ok else f"  -> {detalle}"))


class Sesion:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def req(self, metodo, ruta, cuerpo=None, crudo=None, cabeceras=None, base=API):
        datos, hdrs = None, dict(cabeceras or {})
        if cuerpo is not None:
            datos = json.dumps(cuerpo).encode()
            hdrs["Content-Type"] = "application/json"
        if crudo is not None:
            datos = crudo
        r = urllib.request.Request(base + ruta, data=datos, method=metodo, headers=hdrs)
        try:
            with self.op.open(r, timeout=30) as resp:
                return resp.status, resp.read().decode("utf-8", "replace"), resp.headers
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode("utf-8", "replace"), e.headers

    def login(self, email):
        return self.req("POST", "/api/auth/signin", {"email": email, "contrasena": PWD})

    def roles(self):
        s, b, _ = self.req("GET", "/api/auth/roles")
        return sorted(json.loads(b)) if s == 200 else f"HTTP {s}"


def js(texto):
    try:
        return json.loads(texto)
    except Exception:
        return None


def total_alumnos(s):
    st, b, _ = s.req("GET", "/api/alumnos?incluirInactivos=true")
    d = js(b)
    return (len(d), d) if isinstance(d, list) else (f"HTTP {st}", [])


# 1. Admin org 1
admin = Sesion()
st, b, _ = admin.login("admin@example.test")
check("admin org1: login 200", st == 200, f"HTTP {st} {b[:120]}")
check("admin org1: roles = [ROLE_ADMIN]", admin.roles() == ["ROLE_ADMIN"], admin.roles())
n, lista = total_alumnos(admin)
check(f"admin org1: GET /api/alumnos (incluidas bajas) = {E['TOTAL_ORG1']}", str(n) == E["TOTAL_ORG1"], n)
st, b, _ = admin.req("GET", "/api/alumnos?page=1&size=10")
d = js(b) or {}
check(f"admin org1: listado paginado (solo activos) totalElements = {E['ACTIVOS_ORG1']}",
      str(d.get("totalElements")) == E["ACTIVOS_ORG1"], (st, d.get("totalElements")))
ids = {a.get("id") for a in (lista or [])}
check("admin org1: no ve alumnos de la org 2 (ids 1001-1010)", not (ids & set(range(1001, 1011))), sorted(ids & set(range(1001, 1011))))

# 2. Gestor org 1
gestor = Sesion()
st, _, _ = gestor.login("gestor1@example.test")
check("gestor org1: login 200 y roles = [ROLE_MANAGER]", st == 200 and gestor.roles() == ["ROLE_MANAGER"], (st, gestor.roles()))

# 3. Familia org 1
fam = Sesion()
st, _, _ = fam.login(E["FAMILIA_EMAIL"])
check("familia org1: login 200 y roles = [ROLE_USER]", st == 200 and fam.roles() == ["ROLE_USER"], (st, fam.roles()))
st, b, _ = fam.req("GET", "/api/auth/user/alumnos")
d = js(b)
check(f"familia org1: /api/auth/user/alumnos devuelve solo sus {E['FAMILIA_ALUMNOS']} alumnos",
      st == 200 and isinstance(d, list) and str(len(d)) == E["FAMILIA_ALUMNOS"], (st, len(d) if isinstance(d, list) else b[:120]))
st, _, _ = fam.req("GET", "/api/alumnos")
check("familia org1: GET /api/alumnos prohibido (403)", st == 403, f"HTTP {st}")

# 4. Usuarios sin roles
for email in ("sin-rol@example.test", E["SIN_ROL_ALUMNO_EMAIL"]):
    s = Sesion()
    st, b, _ = s.login(email)
    msg = (js(b) or {}).get("mensaje")
    check(f"sin roles ({email.split('@')[0][:12]}...): login rechazado con 'no tiene acceso a ninguna escuela'",
          st == 403 and msg == "El usuario no tiene acceso a ninguna escuela.", (st, msg))

# 5. Propietario org 2
p2 = Sesion()
st, b, _ = p2.login("propietario2@example.test")
check("propietario org2: login 200 y roles = [ROLE_ADMIN]", st == 200 and p2.roles() == ["ROLE_ADMIN"], (st, p2.roles(), b[:120]))
n2, lista2 = total_alumnos(p2)
ids2 = {a.get("id") for a in (lista2 or [])}
check("propietario org2: ve solo los 10+ alumnos de la org 2",
      isinstance(n2, int) and n2 >= 10 and ids2 and all(i > 1000 for i in ids2), (n2, sorted(ids2)[:5]))
st, b, _ = p2.req("GET", f"/api/alumnos/{E['ID_ALUMNO_ORG1']}")
check("propietario org2: GET alumno de la org 1 -> 404/403", st in (403, 404), f"HTTP {st} {b[:120]}")
nuevo = {"nombre": "Smoke", "apellidos": "Prueba " + uuid.uuid4().hex[:6], "fechaNacimiento": "1990-05-05",
         "direccion": "Calle Sintetica 99", "email": "smoke.org2@example.test", "telefono": 699000000,
         "deportesInicial": [{"deporte": "PILATES", "tipoTarifa": "PILATES", "cuantiaTarifa": 30.0}]}
limite = "----smoke" + uuid.uuid4().hex
cuerpo = (f"--{limite}\r\nContent-Disposition: form-data; name=\"nuevo\"\r\n\r\n{json.dumps(nuevo)}\r\n--{limite}--\r\n").encode()
st, b, _ = p2.req("POST", "/api/alumnos/crear", crudo=cuerpo,
                  cabeceras={"Content-Type": f"multipart/form-data; boundary={limite}"})
d = js(b) or {}
esperado = int(E["MAX_EXP_ORG2"]) + 1
check(f"propietario org2: alta nueva -> 201 con expediente {esperado} (secuencia propia de la org 2, no 204)",
      st == 201 and d.get("numeroExpediente") == esperado, (st, d.get("numeroExpediente"), b[:200]))

# 6. Usuario en dos escuelas
m = Sesion()
st, _, _ = m.login("multiescuela@example.test")
check("multi-escuela: login 200 (entra en org 1 como familia: ROLE_USER)", st == 200 and m.roles() == ["ROLE_USER"], (st, m.roles()))
st, b, _ = m.req("GET", "/api/auth/organizaciones")
d = js(b)
check("multi-escuela: GET /api/auth/organizaciones lista 2", st == 200 and isinstance(d, list) and len(d) == 2, (st, b[:200]))
st, b, _ = m.req("POST", "/api/auth/organizacion", {"organizacionId": 2})
check("multi-escuela: POST /api/auth/organizacion {2} -> 200", st == 200, (st, b[:120]))
check("multi-escuela: tras cambiar, roles = [ROLE_MANAGER]", m.roles() == ["ROLE_MANAGER"], m.roles())
n, lst = total_alumnos(m)
check("multi-escuela: en org 2 ve solo alumnos de la org 2",
      isinstance(n, int) and n >= 10 and all(a.get("id", 0) > 1000 for a in lst), n)
st, b, _ = m.req("POST", "/api/auth/organizacion", {"organizacionId": 999})
check("multi-escuela: cambiar a una escuela sin membresía -> 403", st == 403, (st, b[:120]))
st, b, _ = p2.req("POST", "/api/auth/organizacion", {"organizacionId": 1})
check("propietario org2: cambiar a org 1 (sin membresía) -> 403", st == 403, (st, b[:120]))
st, b, _ = m.req("POST", "/api/auth/organizacion", {"organizacionId": 1})
check("multi-escuela: volver a org 1 -> 200 y roles = [ROLE_USER]", st == 200 and m.roles() == ["ROLE_USER"], (st, m.roles()))

# 7. Admin org 1 no puede cambiar la contraseña de un usuario de la org 2
st, b, _ = admin.req("PUT", f"/api/admin/users/{E['ID_USUARIO_ORG2']}/password", {"nuevaContrasena": "OtraClave123"})
check("admin org1: cambiar contraseña de usuario org 2 -> 403/404", st in (403, 404), (st, b[:160]))

# 8. Errores con cuerpo JSON
st, b, h = admin.req("GET", "/api/no-existe-smoke")
check("404 ruta desconocida con cuerpo JSON", st == 404 and js(b) is not None, (st, b[:120]))
st, b, h = admin.req("POST", "/api/auth/signin", crudo=b"{malformado", cabeceras={"Content-Type": "application/json"})
check("400 JSON malformado con cuerpo JSON (no 500)", st == 400 and js(b) is not None, (st, b[:120]))

# 9. Frontend
f = Sesion()
st, b, _ = f.req("GET", "/", base=FRONT)
check("frontend: GET / sirve index.html", st == 200 and "<app-root" in b.lower(), (st, b[:80]))
st, b, _ = f.req("GET", "/api/auth/auth-status", base=FRONT)
check("frontend: proxy /api -> backend (auth-status)", st == 200 and b.strip() in ("true", "false"), (st, b[:80]))

fallos = resultados.count(False)
print(f"\n{len(resultados) - fallos}/{len(resultados)} PASS")
sys.exit(1 if fallos else 0)
PY
