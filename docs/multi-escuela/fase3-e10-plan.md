# E10 · Plan: contracción de permisos (V32)

Propuesta para revisión · 03/11/2026 · `develop` = producción en `2c8aed16` (Flyway 31.20). Solo diseño: no hay código
ni migraciones escritas. Rutas relativas a `src-api/src/main/java/com/taemoi/project/` (API), a
`src-api/src/main/resources/db/migration/` (MIG), a `src-api/src/test/java/com/taemoi/project/` (TEST) y a
`src-frontend/src/app/` (FE).

Fuentes: la E10 del roadmap (`roadmap.md:90`, «V32: contracción de permisos (quita roles y vínculo antiguos)», nivel
careful) y su diseño original en `fase3-sedes-permisos.md` §2 «V32» (`:124-134`), fila 10 de las entregas (`:335`),
lista de despliegue (`:350`) y cambio visible 9 (`:260`). Lo heredado de la E9: «`NOT NULL` y `UNIQUE`: E10»
(`fase3-e9-plan.md:5`; `fase3-e9-inventario.md:50`).

Regla: ampliar → cambiar el código → contraer, cada paso en su despliegue; cada pieza se prueba en la app real antes
del merge (341).

## 1. Qué son el «rol antiguo» y el «vínculo antiguo»

| Antiguo | Dónde | Sustituto | Desde |
|---|---|---|---|
| Tabla `usuario_rol` (`usuario_id` BIGINT, `roles` ENUM(`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_USER`) NULL), sin clave primaria | MIG `V1__baseline_schema.sql:511-520` | `membresia` (`usuario_id`, `organizacion_id`, `rol` PROPIETARIO/ADMINISTRADOR/RESPONSABLE_SEDE/PROFESOR/FAMILIA, `activa`), por escuela | MIG `V20__membresia.sql:12-26` (tabla) y `:28-46` (relleno ADMIN→PROPIETARIO, MANAGER→ADMINISTRADOR, USER→FAMILIA, todo en la org 1) |
| Columna `usuario.alumno_id` (BIGINT NULL, UNIQUE `UKiwqbs97sir17hipge3olmu6i6`, FK `FKs2jdpk0wmqgyj2i2jer600y8b` sin regla de borrado) | MIG `V1__baseline_schema.sql:435,443-446` | `usuario_alumno` (`usuario_id`, `alumno_id`, `organizacion_id`, `relacion` PROPIO/TUTOR, `origen` MIGRACION/REGISTRO/MANUAL), varias cuentas por ficha | MIG `V31__sedes_y_vinculos.sql:233-283` (tabla y relleno R1 + R2) |

Estado de cada uno hoy:

- **`usuario_rol`: muerto, solo queda la tabla.** No se escribe desde V20 (`V20__membresia.sql:2-3`); el mapeo
  `Usuario.roles` se quitó en la entrega 1 (comentario en API `entities/Usuario.java:83-85`); V31 pasó su FK a
  `ON DELETE CASCADE` (`V31__sedes_y_vinculos.sql:285-317`). Un único lector en código de producción (§2, L5).
- **`usuario.alumno_id`: vivo.** Mapeado (`Usuario.alumno`, `Alumno.usuario`), escrito por registro y Google, y leído
  en seis sitios con efecto (§2). El acceso de familia ya NO lo usa: `AlumnoRepository.ACCESO_FAMILIA`
  (API `repositories/AlumnoRepository.java:223-247`) mira email (marca `comportamiento_legado_taemoi`) y
  `usuario_alumno`.

**Qué NO es la E10** (comprobado):

- Los nombres de autoridad `ROLE_ADMIN`/`ROLE_MANAGER`/`ROLE_USER` siguen: son las autoridades del modelo nuevo
  (`entities/RolMembresia.java:26-30`). Los 301 `@PreAuthorize` de `controllers/` y los guards del frontend
  (FE `app.routes.ts:21-35`, `guards/`, `servicios/authentication/authentication.service.ts:27-29,389-411`) no
  cambian.
- El JWT no lleva roles: solo `sub` y `org` (API `services/JwtService.java:8`, `services/impl/JwtServiceImpl.java:132`);
  las autoridades se calculan en cada petición desde `membresia`. `Usuario.getAuthorities()` devuelve vacío.
- Ya contraído: `/api/admin/users*` (#177, `7c1e3275`, decisión 397) y la pantalla «Gestión de Roles» (#175, 9-8).
  No queda `reemplazarAutoridades` ni `desdeAutoridad`.
- El acceso de familia por email de Moiskimdo (D6, `fase3-sedes-permisos.md:275-277`) es otro legado: fuera salvo
  que el propietario diga lo contrario (pregunta 2).

## 2. Inventario de lectores y escritores

### Código de producción (API)

| # | Sitio | L/E | Qué hace | Sustituto propuesto |
|---|---|---|---|---|
| L1 | `entities/Usuario.java:105-108` (`@OneToOne` `@JoinColumn(name = "alumno_id")`), `:225` `getAlumno`, `:231-240` `getAlumnoId`, `:250-252` `setAlumno` | L+E | Mapeo del vínculo | Se borra |
| L2 | `entities/Alumno.java:159-160` (`List<Usuario> usuario`, `mappedBy = "alumno"`), `:455-458` `getUsuario` (`@JsonManagedReference`), `:461-466` `setUsuario` | L | Lado inverso; saca la clave `usuario` en el JSON de la entidad | Se borra |
| L3 | `controllers/AuthenticationController.java:388-419` `obtenerAlumnoVisibleParaUsuario`, usado por `GET /api/auth/user` (`:303-321`) | L | `alumnoDTO`: gestión sin familia ve el alumno de `alumno_id` si es de la escuela activa y está activo; familia, el de `alumno_id` si lo ve y si no el primero | `usuario_alumno` (pregunta 6). FE lo guarda como `alumnoId` (`servicios/authentication/authentication.service.ts:361-362,526-545`) y lo usa `componentes/vistas/vista-principal-user/turnos-usuario/turnos-usuario.component.ts:213,231,243-248` |
| L4 | `services/impl/RegistroServiceImpl.java:153` («El alumno ya tiene usuario asociado»), `:164` `setAlumno`, `:176-177` `alumno.setUsuario` + `save` | L+E | Bloquea el registro si la ficha tiene cuenta; escribe el vínculo | «Tiene cuenta» = fila de otra cuenta en `usuario_alumno` (pregunta 3); ya escribe `usuario_alumno` (`:172-174`) |
| L5 | `repositories/UsuarioRepository.java:143` (`u.alumno_id IS NOT NULL`) y `:145` (`FROM usuario_rol x`) en `tieneDatosFueraDeLaInvitacion` (`:139-164`), llamado por `services/GestionMiembrosService.java:360` | L | Cancelar una invitación conserva la cuenta si tiene vínculo legado o `usuario_rol` | Quitar las dos líneas en la PR de V32 (lo pide su Javadoc, `:136-138`) |
| L6 | `services/impl/OAuth2UserServiceImpl.java:112` (filtro `getUsuario() == null`), `:135` `setAlumno(primerAlumno)`, `:151` (inactivos sin cuenta), `:157-158` `primerAlumno.setUsuario` + `save` | L+E | Primer login con Google: crea la cuenta con los alumnos «sin cuenta» del email | Mismo criterio con `usuario_alumno` (decisión 15/10, opción A, `fase3-sedes-permisos.md:233-236`) |
| L7 | `services/impl/AlumnoServiceImpl.java:786-813` (`actualizarAlumno`) | L+E | Si la ficha tiene cuenta, cambiar su email cambia el de la cuenta (con las reglas 6-1 y de cuenta compartida); si no, decisión 96 | Pregunta 5 |
| L8 | `services/impl/AlumnoServiceImpl.java:1834-1845` (`eliminarAlumno`) | L+E | Desvincula la cuenta de `alumno_id`, le quita FAMILIA en la escuela y la borra si no le queda ninguna membresía. Las cuentas solo de `usuario_alumno` se quedan como están | Pregunta 4. Hoy el FK sin regla obliga a desvincular antes de borrar (§4, V31_21) |
| L9 | `services/impl/AlumnoServiceImpl.java:1954-1963` (`anonimizarAlumno`) | L+E | Igual que L8 tras borrar los `usuario_alumno` de la ficha (`:1952-1953`) | Pregunta 4 |
| L10 | `services/PreinscripcionService.java:213` (`validarYAplicarDecisionEmail`) | L | Con cuenta: el responsable no cambia el email; sin cuenta: decisión 96 | Mismo «tiene cuenta» que L4 |
| L11 | `controllers/AdminController.java:65-84` (`GET /api/admin`) y `:95-102` (`GET /api/admin/{id}`), solo `ROLE_ADMIN` | L | Devuelven la entidad `Alumno`: llevan la clave `usuario` (vía L2) con id, nombre, apellidos, email y proveedor de la cuenta | Desaparece la clave (pregunta 9). El frontend no llama a estas rutas (solo `/admin/auditoria` y `/admin/configuracion`, FE `servicios/endpoints/endpoints.service.ts:1078-1095,2217`) |

Sin lectores (comprobado con CodeGraph y grep): seguridad (`config/`, `organizacion/`), JWT, JPQL (ninguna consulta
navega `Usuario.alumno` ni `Alumno.usuario`), consultas derivadas de `UsuarioRepository`, cachés (solo
`CacheManagerPorOrganizacion`; ninguna de cuentas), acceso de familia (`AccesoFamiliaService` →
`ACCESO_FAMILIA`), profesor y responsable. `registro_pendiente.alumno_id` es otra cosa y se queda.

`ddl-auto=validate` (API `src/main/resources/application-production.properties:38`): con el mapeo L1 vivo y la
columna borrada, la app **no arranca**. Por eso L1/L2 se van antes que la columna.

### Tests (24 ficheros; lista en el scratchpad de la sesión)

- **El que avisa:** TEST `integracion/CancelarInvitacionIntegrationTest.java`: `cadaClaveAjenaAUsuarioEstaClasificada`
  (`:193-210`; exige `usuario_rol.usuario_id` en `CONSERVAN`, `:109`, y `u.alumno_id IS NOT NULL` en la consulta,
  `:209`), `alumnoLegado_conservaLaCuenta` (`:471-479`) y `usuarioRolLegado_conservaLaCuenta` (`:492-499`).
- `usuario_rol`: además `MembresiaMigracionIntegrationTest` (`:57-58,86`, migra a la última versión y cuenta 7 filas),
  `SedesVinculosMigracionIntegrationTest` (`:160-173,424-460,515,702-703`), `SedesYVinculosEntidadesIntegrationTest`
  (`:145-148`), `SesionPorOrganizacionIntegrationTest` (`:362,426`), `OrganizacionMigracionIntegrationTest` (`:43`,
  revisar) y `servicios/PasswordResetServiceImplTest` (`:38`, comentario).
- `usuario.alumno_id` por SQL o accesores: `AccesoFamiliaEquivalencia`, `AltasFamiliaUsuarioAlumno` (`:169-170`,
  «se mantiene hasta V32»), `AnonimizarAlumno` (`:442`), `BorradoAlumno` (mapa de claves ajenas `:91`
  `usuario.alumno_id` NO ACTION; `:202-222`), `MedicionRendimientoAlumnos`, `MoiskimdoAlumnosJsonDorado` (`:403`, y
  el dorado `src/test/resources/dorado/r2/admin_alumno_con_usuario.json` con la clave `usuario`), `PerfilCuenta`,
  `ResponsableSedeAlumnos`, `ResponsableSedeEntrada`, `ResponsableSedePreinscripcionesConvocatorias`,
  `RutasFamiliaTaemoi`, `SesionPorOrganizacion` (`:463,726`), `SesionSoloProfesor`, `UsuarioSinSecretosEnJson`,
  `UsuarioVinculadoAlumno`, `controladores/AuthenticationControllerTest` (`:154`), `servicios/OAuth2UserServiceImplTest`
  (`:101-305`), `servicios/PasswordResetServiceImplTest` (`:45,88`) y `servicios/RegistroServiceImplTest` (`:93`).
- Las pruebas de migración que hoy migran «a la última» (`flyway(null)`) y miran `usuario_rol` o `alumno_id`
  pasan a migrar a `31.21` como objetivo.

### Scripts y documentación con SQL del modelo antiguo

- `scripts/pruebas/generar_datos_sinteticos.py:677-712,825,909-911` y `scripts/pruebas/datos-sinteticos.sql:7,1502`
  (escriben `usuario_rol` y `usuario.alumno_id`: fallarán contra un esquema con V32), `scripts/pruebas/README.md:109-115`,
  `scripts/pruebas/segunda-escuela.sql:147`, `scripts/pruebas/smoke-fase1.sh:35`.
- Históricos, no se tocan: `docs/multi-escuela/precheck-v31.sql`, `checklist-despliegue.md:17,81,282`,
  `scripts/pruebas/backup-v17.sql`, `mysql/ddl_schema*.sql` (nadie los usa).
- Frontend: nada lee el vínculo ni `usuario_rol`. Solo cambia, si se decide, qué `alumnoDTO` llega (L3).

### Doble escritura

Registro y Google escriben a la vez `usuario.alumno_id` y `usuario_alumno` (origen REGISTRO) desde la entrega 2b
(`b70c567a`): L4 (`:164` y `:172-177`) y L6 (`:135` y `:146-154`). Es la única sincronización doble. El registro solo
escribe `usuario_alumno` si el email del alumno sigue siendo el de la cuenta (`RegistroServiceImpl.java:172`); el
`alumno_id`, siempre. Ningún código escribe filas MANUAL ni MIGRACION.

## 3. Qué borra la V32 y qué se pierde

DDL propuesto (MIG `V32__contraccion_permisos.sql`), cada paso repetible tras un fallo y `flyway repair`, consultando
`information_schema` antes de cada cambio, como V31 y V29:

```sql
SET @schema_name = DATABASE();

-- 1. Última resincronización R1 (la misma sentencia que V31_21; sin efecto si ya está al día).
--    Cadena preparada: si la columna ya no existe (repetición), no se ejecuta.
-- 2. FK de usuario.alumno_id, buscada por definición (el nombre de V1 lo puso Hibernate):
SET @v32_fk = (SELECT MIN(k.CONSTRAINT_NAME) FROM information_schema.KEY_COLUMN_USAGE k
               WHERE k.TABLE_SCHEMA = @schema_name AND k.TABLE_NAME = 'usuario'
                 AND k.COLUMN_NAME = 'alumno_id' AND k.REFERENCED_TABLE_NAME = 'alumno');
SET @sql = IF(@v32_fk IS NULL, 'SELECT ''v32: usuario.alumno_id sin FK''',
              CONCAT('ALTER TABLE usuario DROP FOREIGN KEY `', @v32_fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 3. La columna (su índice único cae con ella):
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name
                 AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id') = 0,
              'SELECT ''v32: usuario.alumno_id ya no existe''', 'ALTER TABLE usuario DROP COLUMN alumno_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 4. La tabla de roles antiguos (su FK hacia usuario cae con ella):
DROP TABLE IF EXISTS usuario_rol;
```

Sin la guarda «no en el mismo despliegue que V31» de `fase3-sedes-permisos.md:125-127`: V31 está aplicada desde el
15/10 y la guarda solo impediría montar una base nueva desde cero (pregunta 8). Sin comprobación que la detenga: el
único motivo previsto («vínculo R1 sin fila», `:128-129`) lo arregla el paso 1.

Datos que se pierden (solo recuperables desde la copia de `desplegar_migrar`):

- **`usuario_rol` entera:** la foto de los roles anteriores a V20. Todo lo que valía se copió a `membresia` en V20;
  lo que cambió después (roles quitados o desactivados) ya manda en `membresia`. Se detecta con la consulta 2 del
  precheck: filas sin membresía equivalente (`sin_equivalente`), con ella inactiva y filas con rol NULL.
- **Pares `usuario.alumno_id` sin fila en `usuario_alumno`.** Tras V31_21 solo quedan los que la regla R1 excluye
  a propósito: cuenta solo familia con otro email (hoy ya no le da acceso), cuenta sin membresía en la escuela del
  alumno y alumnos anonimizados. Se cuentan en la consulta 3 del precheck. Con ellos se pierde solo el «esta ficha
  tiene cuenta» (consulta 4: `alumnos_cuenta_sin_fila`): esas fichas pasan a admitir registro o alta con Google.
- **El orden «cuenta principal»:** hoy cada ficha tiene como mucho una cuenta (UNIQUE) y cada cuenta una ficha
  preferida. Tras V32 una ficha puede tener varias cuentas: afecta a L3, L7, L8 y L9 (preguntas 4 a 6).

Si entra el resto de la V32 original (pregunta 1), va aparte como MIG `V32_1__grupo_sede_obligatoria.sql`:
comprobaciones que la detienen (truco «tabla inexistente» de V28, MIG `V28__contraccion_catalogo.sql:62-70`) si hay
grupos sin sede o dos grupos con el mismo nombre en una sede; luego `ALTER TABLE grupo MODIFY sede_id BIGINT NOT NULL`
(`fk_grupo_sede` no tiene `ON DELETE SET NULL`, `V31__sedes_y_vinculos.sql:122`, así que se puede) y
`ADD UNIQUE KEY uk_grupo_sede_nombre (sede_id, nombre)`. La colación `utf8mb4_0900_ai_ci` hace que «Infantil» e
«infantil» choquen: el precheck agrupa igual.

## 4. Reparto en PR

| PR | Contenido | Migración | Atajo | Riesgo |
|---|---|---|---|---|
| E10-0 | `docs/multi-escuela/precheck-e10.sql` y `postcheck-e10.sql` (§5). El propietario ejecuta el precheck en producción y responde las preguntas con las cifras | No | — | Bajo |
| E10-1 (ampliar) | MIG `V31_21__vinculos_legado_al_dia.sql`: (a) inserta en `usuario_alumno` las filas R1 que falten, misma regla que V31 (`V31__sedes_y_vinculos.sql:257-270`), sin anonimizados, origen MIGRACION; (b) cambia la FK de `usuario.alumno_id` a `ON DELETE SET NULL` (buscada por definición, como V31 paso 9). Test: `BorradoAlumnoIntegrationTest` (`:91` → SET NULL) y uno nuevo de la migración. El código no cambia | V31_21 | `desplegar_migrar` | Medio |
| E10-2 (cambiar) | Quita L1, L2 y la doble escritura; sustituye L3, L4, L6-L10 por lecturas de `usuario_alumno` según las preguntas 3 a 6 (un único «cuentas de la ficha» en `VinculoFamiliaService`, filtrado por organización como exige `UsuarioAlumnoRepository`); tests de §2. L5 se queda: la columna y la tabla siguen | No | `desplegar` | Alto |
| E10-3 (contraer) | MIG `V32__contraccion_permisos.sql` (§3); quita las líneas de L5 y sus tests (`CancelarInvitacion` `:109,209,471-499`), `BorradoAlumno` `:91`, pruebas de migración con objetivo `31.21`, scripts de `scripts/pruebas` y comentarios «hasta V32» | V32 | `desplegar_migrar` + postcheck | Alto (sin vuelta atrás sin copia) |
| E10-4 (cambiar, si pregunta 1 = sí) | `GestionPorSede.sedeDeAlta` (API `acceso/GestionPorSede.java:97-120`) deja de devolver `null` con una escuela sin sede activa; el alta y la edición de grupo comprueban el nombre repetido en la sede antes de guardar (hoy no hay ningún manejador de `DataIntegrityViolationException`: daría 500); `Grupo.sedeId` `nullable = false`. Textos de error: pregunta 10 | No | `desplegar` | Medio |
| E10-5 (contraer, si pregunta 1 = sí) | MIG `V32_1__grupo_sede_obligatoria.sql` (§3); tests que insertan grupos sin `sede_id` en la versión actual (p. ej. TEST `integracion/AsistenciaV31_15MigracionIntegrationTest.java:270`) | V32_1 | `desplegar_migrar` + postcheck | Medio |

Orden: E10-0 → E10-1 → E10-2 → E10-3. E10-4 puede ir en paralelo con E10-2; E10-5, después de E10-3 (una V32_x
aplicada antes de V32 haría que Flyway rechazara V32, `V31_10__diseno_panel_usuario.sql:4-6`). Entre E10-2 y
E10-3, unos días en producción: la vuelta atrás de E10-2 aún es solo de código.

Por qué E10-1 es obligatoria: E10-2 deja de desvincular la cuenta antes de borrar una ficha (L8); con la FK de V1
sin regla, borrar una ficha con `alumno_id` heredado fallaría hasta V32. Y E10-2 lee `usuario_alumno` para
«tiene cuenta» y para el `alumnoDTO`: necesita las filas R1 que falten antes de desplegarse.

Qué probar en la app real antes de cada merge (local, `taemoi-pruebas` con copia de la base; decisión 341):

- **E10-1:** migrar la copia; `precheck-e10.sql` da `alumno_id_sin_fila_r1 = 0` y la FK en SET NULL; borrar una
  ficha con cuenta y anonimizar otra (siguen como hoy); entrar como familia, gestión y profesor.
- **E10-2:** registro por email de una ficha nueva; registro de una ficha que ya tiene cuenta (debe rechazarse);
  primer login con Google y login de una cuenta existente (en local si hay cliente OAuth de pruebas; si no, queda
  para producción con el propietario, pendiente ya abierto); portal de una familia con dos hermanos; cuenta con dos
  vistas (gestión + familia) que elige vista; gestor que tiene su propia ficha (portal y `alumnoDTO`); cambiar el
  email de una ficha con cuenta (como gestión y como responsable); borrar y anonimizar fichas con cuenta propia y con
  cuenta tutora; finalizar una preinscripción con decisión de email; cancelar una invitación.
- **E10-3:** en la copia, precheck → arranque con V32 (`validate` pasa) → postcheck; repetir los recorridos de E10-2
  más cortos (login de cada perfil, borrar ficha, cancelar invitación); regenerar los datos sintéticos.
- **E10-4/5:** crear un grupo sin elegir sede en una escuela con una sede, crear otro con nombre repetido, editar
  uno; migrar la copia con V32_1 y postcheck.

## 5. Precheck y postcheck (agregados)

Solo `COUNT`, `SUM` y `GROUP BY`: sin emails, nombres ni ids. Ningún literal lleva tildes ni «º», así que no hace
falta `--default-character-set=utf8mb4` (no estorba; los comentarios los quita el cliente). Comando, desde
`/opt/taemoi`:
`docker exec -i taemoi-mysql-prod sh -c 'mysql -t -h 127.0.0.1 -u$MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE' < docs/multi-escuela/precheck-e10.sql`

### `docs/multi-escuela/precheck-e10.sql`

Ejecutar antes de E10-1, otra vez después de E10-1 (hace de postcheck de V31_21) y antes de E10-3.

```sql
-- Comprobación previa de la E10 (V31_21, V32 y V32_1). Solo lectura y solo recuentos.
-- R1 = la regla de V31: email del alumno igual al de la cuenta (TRIM + LOWER de la cuenta contra LOWER del
-- alumno, sin blancos) con alguna membresía en la escuela del alumno, o membresía de gestión ACTIVA allí.

-- Consulta 1: versión de Flyway
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;

-- Consulta 2: usuario_rol frente a su membresía equivalente de V20 en la organización 1
SELECT IFNULL(ur.roles, 'NULL') AS rol_antiguo,
       COUNT(*) AS filas,
       COUNT(DISTINCT ur.usuario_id) AS cuentas,
       SUM(EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.organizacion_id = 1
                     AND m.activa = TRUE
                     AND m.rol = CASE ur.roles WHEN 'ROLE_ADMIN' THEN 'PROPIETARIO'
                                               WHEN 'ROLE_MANAGER' THEN 'ADMINISTRADOR'
                                               WHEN 'ROLE_USER' THEN 'FAMILIA' END)) AS equivalente_activa,
       SUM(EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.organizacion_id = 1
                     AND m.activa = FALSE
                     AND m.rol = CASE ur.roles WHEN 'ROLE_ADMIN' THEN 'PROPIETARIO'
                                               WHEN 'ROLE_MANAGER' THEN 'ADMINISTRADOR'
                                               WHEN 'ROLE_USER' THEN 'FAMILIA' END)) AS equivalente_inactiva,
       SUM(NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.organizacion_id = 1
                         AND m.rol = CASE ur.roles WHEN 'ROLE_ADMIN' THEN 'PROPIETARIO'
                                                   WHEN 'ROLE_MANAGER' THEN 'ADMINISTRADOR'
                                                   WHEN 'ROLE_USER' THEN 'FAMILIA' END)) AS sin_equivalente,
       SUM(NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = ur.usuario_id AND m.activa = TRUE))
         AS cuenta_sin_membresia_activa
FROM usuario_rol ur
GROUP BY ur.roles
ORDER BY rol_antiguo;

-- Consulta 3: usuario.alumno_id. con_fila y las cuatro sin_fila suman usuarios_con_alumno_id.
SELECT
  (SELECT COUNT(*) FROM usuario u WHERE u.alumno_id IS NOT NULL) AS usuarios_con_alumno_id,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id))
    AS con_fila,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NULL
      AND ((a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''
            AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
           OR EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                        AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR'))))
    AS sin_fila_r1,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NULL
      AND EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id)
      AND NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> '')
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                        AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR')))
    AS sin_fila_otro_email_sin_gestion,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NULL
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id))
    AS sin_fila_sin_membresia,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.usuario_id = u.id AND ua.alumno_id = a.id)
      AND a.anonimizado_en IS NOT NULL)
    AS sin_fila_anonimizado,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id WHERE a.activo = FALSE)
    AS alumno_inactivo,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id
    WHERE NOT (a.email IS NOT NULL AND LOWER(a.email) = LOWER(TRIM(u.email)) AND TRIM(u.email) <> ''))
    AS email_distinto_de_la_cuenta,
  (SELECT COUNT(*) FROM usuario u JOIN alumno a ON a.id = u.alumno_id AND a.activo = TRUE
    WHERE EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                    AND m.activa = TRUE AND m.rol IN ('PROPIETARIO', 'ADMINISTRADOR'))
      AND NOT EXISTS (SELECT 1 FROM membresia m WHERE m.usuario_id = u.id AND m.organizacion_id = a.organizacion_id
                        AND m.activa = TRUE AND m.rol = 'FAMILIA'))
    AS gestion_sin_familia_con_ficha;

-- Consulta 4: «la ficha tiene cuenta», hoy (alumno_id) frente a mañana (usuario_alumno)
SELECT
  (SELECT COUNT(DISTINCT u.alumno_id) FROM usuario u WHERE u.alumno_id IS NOT NULL) AS alumnos_con_cuenta_hoy,
  (SELECT COUNT(DISTINCT ua.alumno_id) FROM usuario_alumno ua) AS alumnos_con_fila,
  (SELECT COUNT(*) FROM alumno a
    WHERE EXISTS (SELECT 1 FROM usuario u WHERE u.alumno_id = a.id)
      AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.alumno_id = a.id)) AS alumnos_cuenta_sin_fila,
  (SELECT COUNT(*) FROM alumno a
    WHERE NOT EXISTS (SELECT 1 FROM usuario u WHERE u.alumno_id = a.id)
      AND EXISTS (SELECT 1 FROM usuario_alumno ua WHERE ua.alumno_id = a.id)) AS alumnos_fila_sin_cuenta;

-- Consulta 5: fichas con N cuentas vinculadas
SELECT d.cuentas, COUNT(*) AS fichas
FROM (SELECT ua.alumno_id, COUNT(*) AS cuentas FROM usuario_alumno ua GROUP BY ua.alumno_id) d
GROUP BY d.cuentas ORDER BY d.cuentas;

-- Consulta 6: usuario_alumno por origen y relación
SELECT ua.origen, ua.relacion, COUNT(*) AS filas FROM usuario_alumno ua
GROUP BY ua.origen, ua.relacion ORDER BY ua.origen, ua.relacion;

-- Consulta 7: membresías por escuela, rol y estado (deben coincidir antes y después de V32)
SELECT m.organizacion_id, m.rol, m.activa, COUNT(*) AS membresias FROM membresia m
GROUP BY m.organizacion_id, m.rol, m.activa ORDER BY m.organizacion_id, m.rol, m.activa;

-- Consulta 8: claves ajenas del modelo antiguo (V31_21 deja usuario.alumno_id en SET NULL)
SELECT k.TABLE_NAME AS tabla, k.COLUMN_NAME AS columna, rc.DELETE_RULE AS al_borrar, COUNT(*) AS claves
FROM information_schema.KEY_COLUMN_USAGE k
JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
  ON rc.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = k.CONSTRAINT_NAME
 AND rc.TABLE_NAME = k.TABLE_NAME
WHERE k.TABLE_SCHEMA = DATABASE()
  AND ((k.TABLE_NAME = 'usuario' AND k.COLUMN_NAME = 'alumno_id') OR k.TABLE_NAME = 'usuario_rol')
GROUP BY k.TABLE_NAME, k.COLUMN_NAME, rc.DELETE_RULE;

-- Consulta 9: grupos (V32_1). Deben ser 0 las dos primeras.
SELECT
  (SELECT COUNT(*) FROM grupo g WHERE g.sede_id IS NULL) AS grupos_sin_sede,
  (SELECT COUNT(*) FROM (SELECT g.sede_id, g.nombre FROM grupo g WHERE g.sede_id IS NOT NULL
                         GROUP BY g.sede_id, g.nombre HAVING COUNT(*) > 1) r) AS nombres_repetidos_en_sede,
  (SELECT COUNT(*) FROM organizacion o
    WHERE NOT EXISTS (SELECT 1 FROM sede s WHERE s.organizacion_id = o.id AND s.activa = TRUE))
    AS organizaciones_sin_sede_activa;
```

Lectura esperada: tras E10-1, `sin_fila_r1 = 0` y la consulta 8 muestra `usuario.alumno_id` en `SET NULL`.
`sin_fila_otro_email_sin_gestion`, `sin_fila_sin_membresia` y `alumnos_cuenta_sin_fila` dicen cuánto se pierde
(§3). `gestion_sin_familia_con_ficha` dice cuántas cuentas de gestión ven hoy su propia ficha en el portal (L3).

### `docs/multi-escuela/postcheck-e10.sql`

Ejecutar después de E10-3 (y de E10-5 si se hace). Consultas 5, 6 y 7 del precheck, más:

```sql
-- Comprobación posterior de la E10. Solo lectura y solo recuentos. Deben ser 0 las tres primeras columnas.
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario_rol') AS tabla_usuario_rol,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'alumno_id') AS columna_alumno_id,
  (SELECT COUNT(*) FROM usuario_alumno ua JOIN alumno a ON a.id = ua.alumno_id
    WHERE a.organizacion_id <> ua.organizacion_id) AS vinculos_de_otra_escuela,
  -- Igual que antes de V32 (familias sin ninguna ficha visible en la org 1; V32 no debe cambiarlo):
  (SELECT COUNT(*) FROM membresia f JOIN usuario u ON u.id = f.usuario_id
    WHERE f.organizacion_id = 1 AND f.rol = 'FAMILIA' AND f.activa = TRUE
      AND NOT EXISTS (SELECT 1 FROM alumno a WHERE a.organizacion_id = 1 AND a.activo = TRUE
                        AND a.email IS NOT NULL AND TRIM(u.email) <> '' AND LOWER(a.email) = LOWER(TRIM(u.email)))
      AND NOT EXISTS (SELECT 1 FROM usuario_alumno ua JOIN alumno a ON a.id = ua.alumno_id
                       WHERE ua.usuario_id = u.id AND ua.organizacion_id = 1 AND a.activo = TRUE
                         AND ua.origen = 'MANUAL')) AS familias_org1_sin_ficha_visible;

SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;

-- Solo tras V32_1: debe dar NO y 1.
SELECT
  (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'sede_id') AS sede_id_admite_null,
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND INDEX_NAME = 'uk_grupo_sede_nombre'
      AND SEQ_IN_INDEX = 1) AS indice_unico;
```

`familias_org1_sin_ficha_visible` reproduce `ACCESO_FAMILIA` para la org 1 (marca de legado: email o fila MANUAL; las
REGISTRO y MIGRACION solo cuentan con el email igual, que ya cubre la primera condición). Se añade también al
precheck antes de E10-3 para comparar: no depende de `alumno_id`, así que debe dar lo mismo.

## 6. Riesgos y vuelta atrás

- **Sesiones y JWT:** sin efecto. El token solo lleva `sub` y `org`; las autoridades salen de `membresia` en cada
  petición. Las sesiones abiertas siguen tras el despliegue. El `alumnoId` que el frontend guarda en
  `sessionStorage` puede quedar viejo en una sesión de gestión si L3 deja de darle ficha; el backend sigue
  decidiendo el acceso, así que solo afecta a qué ficha abre el portal hasta volver a entrar.
- **Cachés:** ninguna guarda cuentas, roles ni vínculos. El riesgo de arranque es `ddl-auto=validate`: el mapeo
  tiene que irse (E10-2) antes que la columna (E10-3).
- **Cuentas con dos vistas (gestión + familia):** L3 elige hoy la ficha de `alumno_id` entre las visibles; con
  hermanos, el portal podría abrir otra ficha (pregunta 6). La elección de vista (`/login/google` y contraseña, #198)
  no lee el vínculo.
- **Google (`/login/google`):** solo cambia el alta de cuenta nueva (L6); una cuenta existente no lee el vínculo.
  Sigue pendiente probar Google de verdad en producción (gestión + familia, familia y profesor): hacerlo tras E10-2.
- **Familias:** quién ve qué ficha no cambia (no depende de `alumno_id`). Sí cambian, según las preguntas 4 y 5,
  el borrado y la anonimización (qué cuenta pierde FAMILIA o se borra) y el cambio de email en la ficha. Es lo más
  delicado: datos de 800+ alumnos reales.
- **Profesores y responsables:** no usan ninguno de los dos legados. Una invitación cancelada (L5) sigue
  conservando la cuenta con vínculos, dinero o autoría; las invitaciones nunca tienen `alumno_id` ni `usuario_rol`
  (son cuentas nuevas), así que quitar esas dos líneas no cambia ningún caso real.
- **Escuelas nuevas:** el alta de escuela (390 y 392, pendiente) debe crear una sede si entra V32_1, o el primer
  grupo dará error.
- **Vuelta atrás:**
  - E10-1: imagen anterior; las filas MIGRACION nuevas y la FK en SET NULL no estorban al código viejo.
  - E10-2: imagen anterior (solo código). Las cuentas creadas en ese tiempo no tienen `alumno_id`: con el código
    viejo, una cuenta de gestión no vería su ficha en el portal y esas fichas parecerían «sin cuenta» en registro y
    Google (el email ya existe, así que no se crea otra cuenta).
  - E10-3 y E10-5: no hay vuelta atrás de código. La imagen anterior no arranca (`validate` y una migración aplicada
    que no conoce). Se restaura la copia de `desplegar_migrar` (se pierde lo escrito desde entonces) o se arregla
    hacia delante.

## 7. Preguntas al propietario

1. **¿La E10 incluye `grupo.sede_id` obligatoria y el nombre único por sede (V32_1)?** Recomendado: sí, en PR aparte
   (E10-4/5) tras V32; cierra lo prometido en la E9 y en el plan de sedes, a cambio de un despliegue con migración
   más. Alternativa: dejarlo fuera; menos trabajo ahora, pero queda sin dueño.
2. **¿El acceso por email de Moiskimdo (D6) queda fuera de la E10?** Recomendado: fuera; D6 dice quitarlo cuando el
   postcheck dé 0 diferencias durante un tiempo y es un cambio visible para familias. Alternativa: incluirlo; cierra
   todo el legado, pero mezcla un cambio de acceso con una contracción invisible.
3. **«La ficha ya tiene cuenta» (registro, Google, cambio de email, preinscripción) pasa a ser «tiene una fila en
   `usuario_alumno` de otra cuenta».** Recomendado: sí; es la opción A del 15/10 y coincide con hoy salvo
   `alumnos_cuenta_sin_fila`. Alternativa: solo filas PROPIO; deja registrarse a más fichas, pero se aleja de hoy.
4. **Al borrar o anonimizar una ficha, ¿qué cuentas pierden FAMILIA?** Recomendado: cada cuenta vinculada que se
   quede sin ninguna ficha visible en la escuela pierde FAMILIA, y se borra si no le queda ninguna membresía;
   generaliza lo de hoy, pero también afecta a las cuentas tutoras, que hoy se quedan como están. Alternativa: solo
   la cuenta de origen REGISTRO cuyo email es el de la ficha; se parece más a hoy, pero deja cuentas tutoras sin
   fichas.
5. **Cambiar el email de una ficha con cuenta, ¿cambia el de la cuenta?** Recomendado: sí, solo si hay una única
   cuenta vinculada y su email era el de la ficha (el caso de hoy); en Moiskimdo las filas REGISTRO solo cuentan con
   el email igual, así que no propagarlo dejaría a la familia sin acceso. Alternativa: no propagar nunca; más simple,
   pero esa familia pierde el acceso.
6. **¿Qué ficha recibe `alumnoDTO` en `/api/auth/user`?** Recomendado: familia, la primera ficha PROPIO visible y, si
   no hay, la de menor id; gestión sin familia, su ficha PROPIO de la escuela si está activa. Así lo que ve hoy cambia
   lo mínimo. Alternativa: gestión sin familia sin ficha; más simple, pero las cuentas de
   `gestion_sin_familia_con_ficha` dejan de ver la suya en el portal.
7. **¿Hace falta una tabla de archivo con `usuario_rol` y los pares `alumno_id` antes de borrarlos?** Recomendado:
   no, basta la copia de `desplegar_migrar`, como en V28 y V29. Alternativa: tabla `legado_*`; evita restaurar para
   consultar algo, pero es otra contracción pendiente y guarda datos de cuentas.
8. **¿Se quita la guarda «V32 no en el mismo despliegue que V31» del plan de sedes?** Recomendado: sí; V31 lleva
   aplicada desde el 15/10 y la guarda solo estorbaría al montar una base desde cero. Alternativa: mantenerla con su
   marcador para pruebas; sigue el plan al pie de la letra, pero es código sin uso.
9. **La clave `usuario` desaparece del JSON de `GET /api/admin` y `/api/admin/{id}`.** Recomendado: quitarla; nadie
   llama a esas rutas desde el frontend y expone datos de la cuenta. Alternativa: devolverla con `null`; mantiene la
   forma del JSON, pero sin ningún consumidor.
10. **Solo si la 1 es sí: textos de error del grupo.** Propuesta, para validar: «Ya hay un grupo con ese nombre en
    esta sede.» y «La escuela no tiene ninguna sede activa.». Alternativa: los propone `impeccable` en E10-4.

## 8. Respuestas del propietario (03/11/2026, decisiones 426 a 435)

1. Sí: V32_1 entra en la E10, en PR aparte (E10-4 y E10-5) después de la V32 (426).
2. El acceso por email de Moiskimdo (D6) queda fuera de la E10 (427).
3. «La ficha ya tiene cuenta» = alguna fila en `usuario_alumno` de otra cuenta, de cualquier origen (428).
4. Al borrar o anonimizar una ficha, pierde FAMILIA cada cuenta vinculada que se quede sin fichas visibles en la
   escuela, tutoras incluidas; se borra si no le queda membresía (429).
5. El email de la ficha pasa a la cuenta solo si hay una única cuenta vinculada y su email era el de la ficha (430).
6. `alumnoDTO`: la regla recomendada, la más parecida a hoy (431).
7. Sin tabla de archivo: basta la copia de `desplegar_migrar` (432).
8. Se quita la guarda «V32 no con V31» (433).
9. La clave `usuario` sale del JSON de `GET /api/admin` y `/api/admin/{id}` (434).
10. Los textos de error del grupo los propone `impeccable` en E10-4 y los aprueba el propietario (435).
