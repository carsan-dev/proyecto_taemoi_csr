# Entorno local de pruebas (staging)

Reproduce el estado de **producción antes de las migraciones nuevas**: MySQL 8.0, Flyway en **V17**
(V18+ pendientes) y datos **sintéticos** con la forma de producción. Nada de este entorno contiene
personas ni DNI reales: nombres inventados, DNI generados con letra válida, correos `@example.test`.

| Servicio | Puerto local | Notas |
|---|---|---|
| `database` (mysql:8.0) | 3308 | BD `taemoi_pruebas`, usuario `taemoi` / `taemoi-pruebas-local`, root `root-pruebas-local` |
| `backend` | 8081 | Perfil `pruebas` (cookie sin `Secure`, login por http) |
| `frontend` | 8088 | nginx; `/api` se reenvía al backend (alias de red `springboot`) |

Proyecto compose: `taemoi-pruebas` (fichero `docker-compose.pruebas.yml`). No choca con
`docker-compose.yml` (3307/8080/80) ni con `docker-compose.e2e.yml`.

## Pasos

Desde la raíz del repositorio. Cada script tiene versión `.sh` (Git Bash) y `.ps1` (PowerShell).

1. Arrancar solo la base de datos:

   ```sh
   docker compose -f docker-compose.pruebas.yml up -d database
   ```

2. Migrar hasta V17 con la imagen oficial `flyway/flyway:11.7.2` (misma versión mayor que usa
   Spring Boot 3.5). Monta `src-api/src/main/resources/db/migration` en solo lectura y termina con
   `flyway info`, donde V1–V17 salen `Success` y V18+ `Pending`:

   ```sh
   scripts/pruebas/migrar-hasta-v17.sh        # o .\scripts\pruebas\migrar-hasta-v17.ps1
   ```

3. Generar el SQL (Python 3, solo biblioteca estándar, determinista) y cargarlo:

   ```sh
   python scripts/pruebas/generar_datos_sinteticos.py   # escribe scripts/pruebas/datos-sinteticos.sql
   scripts/pruebas/cargar-datos.sh                      # o .\scripts\pruebas\cargar-datos.ps1
   ```

   La carga se puede repetir: el SQL vacía antes las tablas que rellena (no toca
   `plantilla_preinscripcion` ni `flyway_schema_history`).

4. Copia de seguridad del estado V17 (antes de arrancar el backend nuevo) para volver rápido:

   ```sh
   scripts/pruebas/respaldar-v17.sh     # escribe scripts/pruebas/backup-v17.sql (ignorado por git)
   ```

5. Construir y arrancar backend y frontend de la rama actual (árbol de trabajo tal cual). Al
   arrancar, el backend aplica V18–V20 con Flyway sobre los datos sintéticos (~7–10 s):

   ```sh
   docker compose -f docker-compose.pruebas.yml up -d --build backend frontend
   docker compose -f docker-compose.pruebas.yml logs -f backend   # esperar "Started TaeMoiApplication"
   ```

   Abrir http://localhost:8088 (API directa en http://localhost:8081).

6. Cargar la segunda escuela sintética (organización 2, slug `sierra-sur-sintetica`) sobre la base
   ya en V20. Repetible (borra antes lo suyo):

   ```sh
   docker compose -f docker-compose.pruebas.yml exec -T database      mysql --default-character-set=utf8mb4 -utaemoi -ptaemoi-pruebas-local taemoi_pruebas      < scripts/pruebas/segunda-escuela.sql
   ```

   Crea 10 alumnos (expedientes 1–10 propios de la org 2, sin grado: grados y categorías siguen
   siendo filas de la org 1), un grupo con dos turnos, productos y cobros, y los usuarios de la
   tabla siguiente. Al usuario `multiescuela@example.test` le asigna el correo de un alumno activo
   de la org 1 (el de menor id cuyo correo no tenía usuario).

7. Pruebas de humo de la fase 1 contra la API real (bash + Python 3 estándar; no necesita jq):

   ```sh
   scripts/pruebas/smoke-fase1.sh       # imprime PASS/FAIL por comprobación; código 1 si falla alguna
   ```

   Cada ejecución da de alta un alumno nuevo en la org 2 (expediente = máximo de la org 2 + 1).

### Volver a V17

```sh
scripts/pruebas/restaurar-v17.sh        # o .ps1: para el backend y restaura backup-v17.sql
docker compose -f docker-compose.pruebas.yml up -d backend   # vuelve a migrar a V20 al arrancar
# esperar "Started TaeMoiApplication" y repetir el paso 6
```

## Usuarios de prueba

Contraseña de **todos**: `Pruebas-Taemoi-2026!` (hash bcrypt fijado en `generar_datos_sinteticos.py`;
se generó con `docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'Pruebas-Taemoi-2026!'`).

| Email | Roles |
|---|---|
| `admin@example.test` | `ROLE_ADMIN` |
| `gestor1@example.test`, `gestor2@example.test` | `ROLE_MANAGER` |
| `direccion@example.test` | `ROLE_ADMIN` + `ROLE_MANAGER` |
| `sin-rol@example.test` | ninguno, sin alumno (login rechazado: «El usuario no tiene acceso a ninguna escuela.») |
| un correo de alumno activo (`nombre.apellidoNN@example.test`) | ninguno, vinculado a ese alumno (login rechazado igual) |
| 40 correos de alumnos activos (`nombre.apellidoNN@example.test`) | `ROLE_USER`, vinculados a un alumno; los familiares comparten correo con sus hermanos o hijos |
| `propietario2@example.test` | org 2: PROPIETARIO (`ROLE_ADMIN`) |
| `admin2@example.test` | org 2: ADMINISTRADOR (`ROLE_MANAGER`) |
| `multiescuela@example.test` | org 1: FAMILIA (`ROLE_USER`) + org 2: ADMINISTRADOR (`ROLE_MANAGER`); entra en la org 1 |

Desde V20 los permisos salen de `membresia` (ADMIN → PROPIETARIO, MANAGER → ADMINISTRADOR,
USER → FAMILIA, todo en la org 1); `usuario_rol` se conserva sin escribirse.

Para listar los correos familiares:

```sql
SELECT u.email, GROUP_CONCAT(r.roles) FROM usuario u
LEFT JOIN usuario_rol r ON r.usuario_id = u.id GROUP BY u.id;
```

## Forma de los datos

- 150 alumnos (129 activos, 21 bajas), expedientes hasta ~200 con huecos; todos con al menos un
  `alumno_deporte` (157 en total: taekwondo, kickboxing, pilates y defensa personal femenina).
- 40 DNI compartidos por 81 alumnos, todos personas distintas; 33 grupos con algún menor.
  `responsable_legal_nif` nunca coincide con el DNI compartido.
- Grupos y turnos iguales a los que siembra `InicializadorDatos`; `limite_turno` = 36.
- Productos con los conceptos exactos que busca el código (`docs/multi-escuela/valores-actuales.md`),
  mensualidades, licencias, tarifas de competidor, derechos de examen (pagados y pendientes),
  convocatorias de junio y diciembre, 7 preinscripciones en todos los estados y eventos sin imagen.

## Consultas de comprobación

```sql
SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1;  -- 17
SELECT COUNT(*) dni, SUM(n) alumnos, SUM(menores > 0) grupos_con_menores
FROM (SELECT nif, COUNT(*) n, SUM(YEAR(fecha_nacimiento) + 14 > YEAR(CURDATE())) menores
      FROM alumno WHERE nif IS NOT NULL GROUP BY nif HAVING COUNT(*) > 1) g;        -- 40 / 81 / 33
```

## Reiniciar desde cero

```sh
docker compose -f docker-compose.pruebas.yml down -v   # borra los volúmenes del proyecto taemoi-pruebas
```

Después, repetir los pasos 1–3 (y 4–6 si se quiere el estado V20).
