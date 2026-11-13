# Fase 4 · Carga inicial de alumnos (importador)

Plan de la carga inicial de una escuela nueva a partir de una hoja Excel. Primer uso: Taekwondo Sierra Sur. Decisiones d556 y d558 (13/11/2026). Datos de menores: cuidado máximo y revisión fuerte.

## 1. Punto de partida (verificado el 13/11)

- No hay importador ni carga masiva en el código. `modelo-datos.md` («Importación de Taekwondo Sierra Sur») describía una importación de fichas escaneadas que no se llegó a construir; este plan la sustituye para la carga inicial.
- Fuentes de Sierra Sur, todas fuera del repositorio:
  - Export `.xls` de la federación andaluza: 260 deportistas, 6 columnas (Número, Nombre y apellidos juntos como «APELLIDO1 APELLIDO2 NOMBRE», DNI, Fecha licencia, Fecha nacimiento, Grado). 232 menores. 165 DNI/NIE válidos; el resto son identificadores de la federación. Sin teléfono, correo, dirección ni sede.
  - Sede, grupo, turnos y correo de la familia: los aporta el propietario.
  - Ficha de papel: domicilio, teléfonos, padres, salud y una sola firma para normas, primeros auxilios, uso de imagen y compromiso de pago.
- Hoja maestra: un `.xlsx` con una fila por deportista, generado a partir del export. Columnas: `numero_federacion`, `nombre_federacion`, `fecha_nacimiento`, `menor`, `grado_federacion`, `dni_federacion`, `fecha_licencia` (de la federación, no se editan); `nombre`, `apellidos`, `dni_nie`, `grado` (propuesta a revisar); `sede`, `grupo`, `email_familia`, `telefono`, `telefono2`, `direccion`, `localidad`, `codigo_postal`, `tutor_nombre`, `tutor_parentesco`, `tutor_dni`, `tarifa`, `autorizacion_imagen`, `observaciones` (a rellenar); `revisar` (avisos). Hojas auxiliares «Listas» e «Instrucciones».

## 2. Qué hace hoy el alta de alumno (y qué no)

- `AlumnoServiceImpl.crearAlumnoDesdeDTO` es transaccional y se puede llamar N veces dentro de una sola transacción.
- Exige nombre, apellidos, fecha de nacimiento, dirección, teléfono, sede (si hay varias activas) y al menos una disciplina. No exige correo, DNI, tutor, grupo ni tarifa.
- No envía correos, no crea cuentas y no cobra matrícula ni licencia.
- Siempre apunta un consentimiento `USO_IMAGEN` con fecha de hoy, origen `APP` y como firmante el usuario de la sesión.
- Puede crear una mensualidad del mes en curso si ya existe ese concepto en la escuela (`asignarMensualidadGeneralSiCorresponde`).
- El importe de la tarifa (`cuantiaTarifa`) no lo calcula la API: hoy lo calcula el formulario.
- Las mensualidades se generan solo con el botón de mensualidades generales, para el mes pedido; no hay tarea programada ni atrasos. Un alumno sin tarifa recibiría un cargo sin importe.
- El superadmin de plataforma no tiene rol de gestión en la escuela destino: el patrón para escribir en ella es `TransaccionEnEscuela.escribir(orgId, …)`, como en `AltaEscuelaService`.
- Una escuela nueva recibe disciplinas, grados, productos y tarifas copiados de la plantilla (tarifas con `activa = false`); no recibe sedes adicionales, grupos, turnos ni contadores.
- No existe consulta por número de licencia ni restricción única sobre él.

## 3. Decisiones (13/11)

| N.º | Decisión |
|---|---|
| d556 | Los alumnos de Sierra Sur entran con un importador a partir de la hoja maestra, no a mano ni solo por preinscripción. Las cuentas de familia siguen siendo por correo: con el correo en la ficha, la familia se registra sola (correo + fecha de nacimiento). Cuentas por teléfono, descartadas antes del alta. |
| d558.1 | Formato: se sube el `.xlsx` tal cual y lo lee el sistema. Se añade una librería de lectura de Excel a la API. |
| d558.2 | Teléfono obligatorio. Sin dirección, la fila entra con «Pendiente» y se lista en el informe. |
| d558.3 | Grados: conversión automática. Menor con «n DAN» entra como PUM del mismo número; «Sin grado» entra como BLANCO. |
| d558.4 | Los grupos y sus turnos deben existir antes. El importador solo asigna; un nombre de grupo que no exista en esa sede es un error. |
| d558.5 | Se lanza desde una pantalla nueva de Plataforma (solo superadmin). Necesita boceto aprobado antes de construirse. |

## 4. Diseño

### 4.1 Operación

- Endpoints de plataforma (`ROLE_ADMIN_PLATAFORMA`): ensayo y carga sobre una escuela concreta, con el `.xlsx` como fichero adjunto.
- Todo se ejecuta dentro de `TransaccionEnEscuela.escribir(orgId, …)`, abierta antes de tocar ningún repositorio.
- **Ensayo**: mismo código que la carga; al final deshace la transacción. Devuelve el informe y no deja nada escrito.
- **Carga**: todo o nada. Un error en cualquier fila impide toda la carga.

### 4.2 Fases

1. **Leer y validar el fichero**, sin base de datos: hojas y cabeceras esperadas; obligatorios (`numero_federacion`, `nombre`, `apellidos`, `fecha_nacimiento`, `sede`, `telefono`); formatos (fecha no futura, DNI/NIE `^[0-9XYZ][0-9]{7}[A-Z]$` o vacío, teléfono de 9 a 15 dígitos, correo); valores de lista (`sede`, `grado`, `tutor_parentesco`, `autorizacion_imagen`); duplicados dentro del fichero (`numero_federacion`, `dni_nie`).
2. **Validar contra la escuela**: sedes activas con ese nombre, disciplina, grados del sistema de la disciplina, tarifas, grupos con ese nombre en esa sede, DNI que ya exista en la escuela.
3. **Escribir**, por fila: alumno; disciplina con grado, licencia (`tieneLicencia = true`, `numeroLicencia = numero_federacion`, `fechaLicencia`) y tarifa con su importe calculado en servidor; tutor; grupo y turnos.

### 4.3 Reglas

- **No duplicar**: una fila cuyo `numero_federacion` ya exista como licencia de esa disciplina en la escuela se salta y se lista como «ya cargada». Requiere una consulta nueva.
- **Grados** (d558.3): la conversión se aplica en el importador, a partir de `grado` y de la edad a la fecha de la carga.
- **Dirección** (d558.2): vacía → «Pendiente».
- **Uso de imagen**: el importador no apunta el consentimiento automático del alta. `SI` o `NO` en la hoja → consentimiento con origen `FICHA_PAPEL` y fecha aproximada; vacío o `SIN REGISTRAR` → sin consentimiento y `autorizacionWeb = false`.
- **Cobros**: el importador no crea ningún cargo; no pasa por `asignarMensualidadGeneralSiCorresponde`. Los alumnos sin tarifa se listan en el informe.
- **Correos y cuentas**: ninguno.
- **Auditoría**: un apunte en la escuela destino con recuentos, sin nombres ni DNI.
- **Informe**: recuentos (creados, ya cargados, avisos, errores) y detalle por fila identificada por número de fila y `numero_federacion`, con columna y motivo. Sin nombres ni DNI.

### 4.4 Requisitos previos a la carga real

Escuela creada; las seis sedes; grupos con sus turnos; tarifas activadas y tarifa de sede; hoja maestra completa y revisada.

## 5. Piezas

| Pieza | Qué | Nivel |
|---|---|---|
| I1 | API: lectura del Excel, validación, ensayo y carga, consulta por licencia, auditoría y tests de integración | careful + revisión Opus |
| I2 | Boceto de la pantalla de Plataforma y aprobación del propietario | orquestador |
| I3 | Pantalla de Plataforma según el boceto | medium + revisión |
| I4 | Ensayo en la copia local con la hoja real | propietario + orquestador |
| I5 | Carga real, con copia de seguridad antes | propietario |

Tests de I1 (integración con MySQL): el ensayo no cambia nada; todo o nada ante un error; repetir la carga no duplica; nada escrito en otra escuela; cero cargos; cero correos; estados de uso de imagen; conversión de grados; dirección pendiente.

## 6. Pendiente de decidir

- Hermanos con el mismo correo: hoy solo se registra una cuenta por correo y queda vinculada al hermano cuya fecha de nacimiento se dio; los demás no se vinculan solos.
- Fecha de alta de los alumnos importados (hoy, la de la licencia u otra).
- Datos de salud y padres de la ficha de papel: fuera de esta carga.
- La autorización de imagen de la ficha de papel va en una sola firma junto a normas y pago: no se importa como «sí» si no consta expresamente.
