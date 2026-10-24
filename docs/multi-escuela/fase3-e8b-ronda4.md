# E8b, ronda 4: bocetos de las pantallas de gestión que faltaban (8b-B4)

Estado: bocetos para elegir. Boceto: (enlace al publicar).

## Método

- Formato ligero elegido por el propietario el 24/10: maquetas HTML con datos ficticios, sin capturas de la aplicación. Cada maqueta se pinta a su ancho real (375, 768 y 1280) y adapta su composición con `@container`, no con `@media`.
- Mundo fijado por `DESIGN.md` (dirección C, solo modo claro, valores de Moiskimdo): botón principal único por pantalla, secundario, texto, campos de 42 px, filas de dos líneas, diálogo de 440 px y hoja inferior por debajo de 768, panel lateral de 480 px.
- Cada pantalla se compara con «Hoy» (estructura y fallos tomados del inventario de la sección «Anexo») y una o dos propuestas (P1 y P2). Se elige una versión por pantalla.
- «Nada se pierde» (decisión 232): cada campo, acción, confirmación, aviso y estado de hoy tiene una fila en el inventario de la página, con su lugar en P1 y en P2. Las filas nuevas van marcadas como «Añadido».
- Pieza 8b-3l: alta y eliminación de alumnos. Pieza 8b-3m: grupos, turnos y alumnos del grupo. Pieza 8b-3n: productos de un alumno.

## Las pantallas

### 1. Alta de alumno (8b-3l, `/alumnosCrear`)

**P1, Una columna por secciones con resumen al lado (recomendada).** Estados pintados: normal, con errores.

- Una sola columna en bloques con título (Datos personales, Contacto, Responsable legal, Deportes, Foto y permisos) y, desde 1280, un resumen fijo de 380 px a la derecha; en 768 y 375 el resumen pasa al final.
- Cada deporte es un sub-bloque con cabecera «Taekwondo · amarillo» y «Quitar»; el límite de 4 se dice en el botón «Añadir otro deporte (1 de 4)».
- Cada etiqueta dice «obligatorio» u «opcional», como en Editar evento.
- La fecha de nacimiento muestra la edad y la categoría al lado; si es menor de 18, el responsable legal pone la nota «Recomendado: es menor de edad» (sigue opcional).
- Con errores, un resumen arriba («Faltan 3 datos») enlaza a cada campo y los mensajes de hoy quedan bajo cada campo.
- Añadido: al cancelar con datos se pregunta «¿Salir sin dar de alta?» (diálogo del panel nuevo: «Seguir editando» / «Salir sin guardar»). No es un estado de la maqueta.

**P2, Por pasos.** Estados pintados: paso 2, normal, paso 1, con errores.

- Indicador «1 Persona · 2 Deportes · 3 Foto y permisos · 4 Revisar», un paso por vista, con «Anterior» y «Siguiente».
- El último paso muestra el resumen y «Dar de alta».
- Se pinta el paso 2 en el estado normal (con licencia y competición abiertas) y el paso 1 con errores.
- El resto de campos, textos y límites son los de P1.

**Riesgo.** P2 esconde los errores de otros pasos y suma clics al gestor en escritorio. P1 es larga en 375. En P1, «Dar de alta» deja de estar deshabilitado con el formulario inválido para poder mostrar el resumen de errores; hay que confirmarlo.

**Preguntas.** P-1, P-2.

### 2. Eliminar alumnos (8b-3l, `/alumnosEliminar`)

**P1, Bajas y eliminaciones (recomendada).** Estados pintados: lista, con selección, confirmar borrado, eliminando.

- Pantalla propia «Bajas y eliminaciones» con un aviso neutro (icono de peligro y texto reescrito: «Aquí se dan de baja o se eliminan alumnos. Eliminar borra al alumno para siempre.»).
- Filas de dos líneas con casilla, foto, nombre (ahora enlace a la ficha), «Exp. 1042 · DNI 12345678Z» y pastilla «Activo» / «De baja»; desde 1280, la tabla con las columnas de hoy.
- Por fila: «Dar de baja» o «Dar de alta» (texto) y «⋯» con «Eliminar definitivamente» en peligro.
- Con selección aparece la barra «3 elegidos · Quitar selección · Dar de baja · Eliminar» (fija abajo por debajo de 1024, sobre la lista en escritorio) y «Seleccionar los de esta página».
- El diálogo destructivo dice «¿Eliminar a 3 alumnos?», lista los nombres, repite «Se borran para siempre. No se puede deshacer.» y deja el foco en «Cancelar». Mientras corre, «Eliminando 2 de 3…»; al final, un resumen de los que fallaron.
- «Dar de alta» con varios deportes abre un diálogo con una opción por deporte y «Todos los deportes», como hoy.

**P2, Dentro de Alumnos.** Estados pintados: con selección.

- Se elimina la pantalla propia: el listado de Alumnos del panel nuevo gana un botón «Seleccionar» que activa casillas y la misma barra.
- La ruta /alumnosEliminar abre Alumnos en modo selección.
- Se pinta solo el estado con selección; la confirmación, el progreso y el resumen son los de P1.

**Riesgo.** El borrado es irreversible. ADMIN y MANAGER pueden ambos y no hay aviso de dependencias (pagos, cargos). En P2 el modo selección convive con el listado normal y puede dispararse por error.

**Preguntas.** P-3, P-4.

### 3. Crear grupo (8b-3m, `/gruposCrear`)

**P1, Panel lateral desde Grupos (recomendada).** Estados pintados: normal, con error.

- Grupos queda de fondo y «Nuevo grupo» se abre en un panel lateral de 480 px (hoja inferior en 375); el contexto no se pierde.
- Campo «Nombre del grupo» (obligatorio, máx. 50) con contador «23 / 50» y la ayuda de hoy.
- Pie fijo: «Cancelar» (texto) y «Crear grupo» (primario).
- Error: mensaje bajo el campo; el error del servidor sale con su texto real, no el genérico.
- Tras crear: aviso «Grupo creado» y el grupo nuevo resaltado en la lista unos segundos.

**P2, Página corta.** Estados pintados: normal, con error.

- Misma estructura en una página propia, con «Volver a Grupos» arriba.
- Mismo campo, contador, ayuda y errores; tras crear, vuelve a Grupos con el aviso y el grupo resaltado.

**Riesgo.** /gruposCrear tendría que abrir Grupos con el panel ya abierto (deep link). El texto del error del servidor de la maqueta es de ejemplo.

**Preguntas.** ninguna.

### 4. Editar grupo (8b-3m, `/gruposEditar/:id`)

**P1, Dos bloques (recomendada).** Estados pintados: normal, añadir profesor sin cuenta.

- Título «Infantiles A» con la miga «Grupos /» y la línea «18 alumnos · 2 turnos» con «Ver alumnos» (añadido: camino a la pantalla de alumnos del grupo).
- Bloque «Datos del grupo»: nombre y «Guardar cambios» (el único primario). El rango de edad sigue reenviándose sin mostrarse.
- Bloque «Profesores»: filas con iniciales, nombre, email, segmento «Titular | Ayudante», pastilla «Invitación pendiente» con «Reenviar» y «⋯» con «Quitar del grupo».
- «Quitar del grupo» pide: «¿Quitar a Marta Gil de este grupo? Seguirá teniendo el rol de profesor en la escuela.» (diálogo normal, no rojo: es reversible).
- Pie del bloque «Añadir profesor»: email, rol, pastillas «También en estos grupos» y un botón secundario con el texto variable de hoy (Añadir, Añadir a 3 grupos, Enviar invitación, Reactivar y añadir).
- Sin cuenta: aviso «No hay ninguna cuenta con ese email. Te pedimos su nombre para invitarle.» y los campos nombre y apellidos.

**Riesgo.** Hay dos acciones que pueden parecer principales. Solo «Guardar cambios» es primario; «Añadir» y sus variantes son secundarias. Guardar el nombre no guarda los cambios de profesores, que actúan al momento: la maqueta lo dice en cada bloque.

**Preguntas.** P-5.

### 5. Crear y editar turno (8b-3m, `/turnosCrear · /turnosEditar/:id`)

**P1, Panel lateral desde Grupos (recomendada).** Estados pintados: crear, editar con grupo, horas cruzadas.

- Panel lateral de 480 px desde Grupos (hoja inferior en 375): «Nuevo turno» y «Editar turno».
- Día como grupo de pastillas L M X J V S D (radiogroup de 44 px, con el nombre completo en el aria-label).
- Hora de inicio y fin lado a lado, con «Duración: 1 h 30 min» calculada (añadido) y el error cruzado de hoy bajo ellos.
- «Grupo» es un desplegable con «Sin grupo» primero: elegir un grupo equivale a asignar y sustituye al interruptor.
- «Tipo de grupo» sale solo al crear y con grupo elegido, con la lista fija de hoy y obligatorio.
- Pie: «Cancelar» y «Crear turno» o «Guardar cambios».

**Riesgo.** Quitar el interruptor cambia cuándo se pide el tipo de grupo: antes dependía del interruptor, ahora de haber elegido grupo. Al mover un turno de A a B, hoy solo se asigna a B y no se quita de A; la maqueta no lo corrige.

**Preguntas.** P-6, P-7.

### 6. Alumnos del grupo (8b-3m, `/gestionarAlumnos/:id`)

**P1, Lista del grupo (recomendada).** Estados pintados: lista, buscando, grupo vacío, sin resultados, confirmar quitar.

- Título «Alumnos de Infantiles A» (añadido: nombre del grupo), «18 alumnos» y «Añadir alumnos» como único primario, arriba.
- Buscador con «Mostrando 3 de 18». Paginación de 10 sin números: «Anterior», «1-10 de 18», «Siguiente».
- Filas de dos líneas: foto de 40 px, nombre (enlace a la ficha) y, en la segunda línea, los turnos («L y X 17:30») si el servidor da el dato.
- A la derecha, «Turnos» (texto) y «⋯» con «Quitar del grupo».
- Confirmación no destructiva: «¿Quitar a Hugo Martín del grupo? Sigue siendo alumno de la escuela y puedes volver a añadirlo.» (corrige el «No podrás revertir» de hoy).
- Vacío: «Aún no hay alumnos en este grupo» + «Añadir alumnos». Con búsqueda: «Ningún alumno coincide con «…»» + «Quitar búsqueda».

**P2, Lista + panel de añadir.** Estados pintados: con el panel abierto.

- Igual que P1, pero «Añadir alumnos» abre sin salir el panel lateral de la pantalla siguiente (Seleccionar alumnos); se pinta ese estado.
- Al añadir, la lista se actualiza detrás del panel.

**Riesgo.** Los inactivos siguen sin salir, como hoy. Los turnos de la segunda línea dependen de que el servidor los devuelva por alumno.

**Preguntas.** ninguna.

### 7. Seleccionar alumnos (8b-3m, `/seleccionarAlumnos/:id`)

**P1, Panel lateral sobre Alumnos del grupo (recomendada).** Estados pintados: con elegidos, sin elegidos, sin resultados.

- Panel lateral de 480 px sobre Alumnos del grupo (hoja inferior en 375): «Añadir alumnos a Infantiles A».
- Buscador fijo arriba que no desaparece al cargar.
- Lista con casillas y foto de 32 px; los que ya están en el grupo salen atenuados con «Ya está en el grupo» y sin casilla, en vez de ocultarse.
- Contador «3 elegidos» con «Quitar selección»; la selección se conserva al buscar y paginar.
- Pie: «Cancelar» y «Añadir 3 alumnos» (primario, desactivado con 0 elegidos).
- Sin resultados: «Ningún alumno activo coincide con «…»».

**P2, Página.** Estados pintados: con elegidos, sin resultados.

- La misma lista en una página propia, con «Volver al grupo», contador y botón fijo abajo.
- Mismos estados, textos y reglas de selección.

**Riesgo.** Mostrar los ya añadidos cambia cuántos alumnos se ven por página (siempre 10). En P1 el panel cubre la lista del grupo; la lista se refresca al cerrarlo.

**Preguntas.** P-8.

### 8. Turnos de un alumno (8b-3m, `/gestionarTurnosAlumno/:alumnoId`)

**P1, Lista por día (recomendada).** Estados pintados: con turnos, sin turnos.

- Título «Turnos de Lucía Ortega» con enlace «Ver ficha».
- Filas por día: el día en Montserrat («Lunes»), «17:30 – 18:30» tabular y, en la segunda línea, tipo de grupo y grupo; «Quitar» (texto).
- «Quitar» pide: «¿Quitar el turno del lunes 17:30 – 18:30 a Lucía? Puedes volver a asignarlo.»
- Bloque «Asignar turno»: desplegable con «Lunes · 17:30 – 18:30 · Taekwondo infantil» ordenado por día; los ya asignados, desactivados con «ya asignado». Botón secundario «Asignar turno».
- Sin turnos: «Lucía no tiene turnos asignados.» (hoy nunca salía).

**P2, Semana.** Estados pintados: con turnos, sin turnos.

- Rejilla L–S × franjas con los turnos de la escuela como celdas; los del alumno marcados como elegidos con una marca de verificación.
- Tocar una celda asigna o quita, con confirmación.
- En 375 la rejilla pasa a una lista de días plegables.

**Riesgo.** El día de la semana es dato añadido (hoy no se ve). P2 depende de que haya pocos turnos por franja; con muchos turnos simultáneos la rejilla se aprieta.

**Preguntas.** ninguna.

### 9. Productos de un alumno (8b-3n, `/alumnos/:id/productos`)

**P1, Cargos con edición en lote (recomendada).** Estados pintados: lista, cambios pendientes, cobrar.

- Cabecera con foto, «Lucía Ortega» y «Productos y cargos»; enlace «Volver a la ficha».
- Bloque «Asignar producto»: buscador de producto («Matrícula taekwondo · 25,00 €»), deporte solo si es matrícula y secundario «Añadir a la lista».
- Desde 1280, tabla con las 9 columnas de hoy y campos en línea; en «Pagado», la pastilla (Pagado, Pendiente, Cancelado, Devuelto) y «Cobrar» (texto) en los pendientes.
- En 768 y 375, filas de dos líneas (concepto y total; «Asignado 01/10/2026 · 1 × 30,00 €» y pastilla) que se despliegan con los campos editables.
- Las asignaciones nuevas salen arriba con la pastilla «Nuevo» y una X.
- «⋯» por cargo: Editar notas, Cancelar cargo (con motivo obligatorio, máx. 500), Reactivar, Eliminar (solo propietario, marca para borrar con «Deshacer») y, para el gestor en un cargo pagado, «Pedir que se revierta el cobro».
- Cambios pendientes: barra fija «3 cambios sin guardar · Descartar · Guardar cambios» y un punto de acento en las filas cambiadas.
- Cobrar: el diálogo de cobro de Tesorería (importe ocre de 28 px, método en pastillas, «Cobrar 30,00 €»). Añadido: aviso al salir con cambios sin guardar.

**P2, Un cargo cada vez.** Estados pintados: con el panel abierto.

- Lista de solo lectura; tocar un cargo abre el panel lateral con sus campos y «Guardar» inmediato, sin barra de cambios.
- Se pinta la lista con el panel abierto.
- Las asignaciones nuevas se guardan al añadirlas.

**Riesgo.** En P1, «Cobrar» usa la llamada de Tesorería en vez de marcar la casilla (decidido en el plan). P2 cambia el modelo de lote a guardado inmediato, lo que cambia la fiabilidad si falla una llamada a medias.

**Preguntas.** P-9.

## Hallazgos del inventario que son fallos de hoy

### Alta de alumno

- Cancelar vuelve al listado sin confirmar, aunque haya datos escritos.
- No hay campo de sede: con varias sedes el servidor responde «Indica la sede.» y Sierra Sur (6 sedes) no puede dar de alta desde aquí.
- No valida «fecha de alta ≥ fecha de nacimiento»; la ficha sí.
- El responsable legal siempre se ve y no se avisa por edad: no hay lógica de menor.
- Con el formulario inválido, el botón queda deshabilitado y solo un aviso emergente dice «Por favor completa todos los campos requeridos»; no señala cuáles.
- Un error del servidor sube la página arriba y abre un aviso emergente con su texto.
- Código muerto: onCompetidorChange, onLicenciaChange y aplicarFiltrosGrado leen controles que ya no existen.
- «Grado inicial» no tiene mensaje de error propio.
- Los errores de grados, de imagen y de límite de deportes salen en SweetAlert2, no en el aviso del panel nuevo.

### Eliminar alumnos

- El borrado definitivo no avisa de dependencias: pagos y cargos del alumno.
- La confirmación no repite el nombre ni cuenta a los seleccionados: «¿Estás seguro?».
- Foto y nombre no enlazan a la ficha.
- «Baja» (alumno entero) y «Alta» (por deporte) son asimétricas, y «Alta» no usa el endpoint de alta del alumno.
- La selección masiva solo alcanza a la página visible.
- ADMIN y MANAGER pueden ambos borrar definitivamente.
- No hay esqueleto de carga; mientras carga solo se deshabilita la paginación.
- Las acciones masivas van una a una, del último al primero, y un fallo abre un aviso por alumno.
- En móvil «Baja» queda en un icono sin nombre; el buscador lleva un emoji en el texto.
- Sin enlace desde los listados nuevos.

### Crear grupo

- Cualquier fallo del servidor sale con el mismo aviso genérico: «No has completado todos los campos requeridos».
- Cancelar usa location.back(): no siempre devuelve a Grupos.
- Tras crear, salta al listado sin resaltar el grupo nuevo.
- Una pantalla entera para un solo campo.
- El DTO tiene deporte y rango de edad, pero el alta no los pide; tampoco sede.
- Sin carga ni estado vacío: son innecesarios para un solo campo.

### Editar grupo

- No muestra los alumnos ni los turnos del grupo: no hay camino a «Gestionar alumnos» desde aquí.
- El rango de edad del grupo se carga y se reenvía en el PUT sin control en la plantilla: no se ve ni se edita.
- «Actualizar Grupo» y las acciones de profesores comparten pantalla: dos zonas con efectos distintos (uno guarda, los otros actúan al momento).
- «Cancelar» usa location.back() y tras guardar salta al listado.
- Quitar a un profesor no avisa con un aviso de éxito; la lista se recarga en silencio.
- La confirmación de quitar dice «Quitar» en rojo para una acción que se puede deshacer.
- No hay «Eliminar grupo» (existe en el servicio, no se ve aquí).
- Con una acción en curso se bloquean todos los demás controles sin decir por qué.

### Crear y editar turno

- El interruptor «Asignar a un grupo existente» no obliga a elegir grupo: se puede enviar con el interruptor activo y el grupo vacío (solo exige el tipo).
- Al mover un turno de A a B solo se llama a «asignar a B»; no se quita de A.
- «Tipo de grupo» es una lista fija en la plantilla (Taekwondo infantil… Competición), no sale del catálogo de disciplinas.
- Editar no tiene «Tipo de grupo»; crear sí, y solo con el interruptor.
- Cualquier error al crear sale con el aviso genérico «No has completado todos los campos requeridos».
- La carga de grupos no tiene indicador; el error es un aviso corto.
- No hay «Eliminar turno» (existe en el servicio) ni se ven los alumnos del turno.
- «Crear turno» acepta ?grupoId=N pero no se encontró ningún enlace que lo use.
- Los días de la semana van en un selector con buscador para solo siete opciones.

### Alumnos del grupo

- El título no dice el nombre del grupo.
- «Eliminar» abre «¿Estás seguro? No podrás revertir esta acción», pero solo quita del grupo y es reversible.
- El botón se llama «Eliminar» aunque no borra al alumno.
- La fila no muestra deporte, grado ni turnos.
- Solo salen los alumnos activos (los inactivos no aparecen).
- Sin acciones masivas.
- El buscador filtra en el navegador por «nombre apellidos»; «Mostrando X de Y» solo sale con texto.
- La paginación solo aparece con más de una página.

### Seleccionar alumnos

- Los alumnos que ya están en el grupo se ocultan en el navegador después de paginar: salen páginas con menos de 10 filas o vacías, sin mensaje.
- Sin texto de vacío ni de «sin resultados».
- El botón «Agregar Alumnos Seleccionados» no se deshabilita sin selección y no hace nada ni avisa.
- No hay contador de elegidos ni «quitar selección», aunque la selección se conserva al buscar y paginar.
- Cada búsqueda o cambio de página muestra el esqueleto y oculta el campo de filtro: puede perder el foco.
- La etiqueta de la casilla no tiene «for».
- La fila no muestra foto, deporte ni sede; no avisa si el alumno ya está en otros grupos.

### Turnos de un alumno

- Ni la lista ni el desplegable muestran el día de la semana: turnos del mismo tipo en días distintos se ven iguales.
- El desplegable ofrece todos los turnos, también los ya asignados.
- No indica el grupo del turno ni si el alumno está en él.
- Sin turnos no sale mensaje: el texto «Este alumno no tiene turnos asignados.» no llega a verse (un array vacío cuenta como verdadero).
- El selector no tiene opción de ayuda («Seleccione un turno»).
- «Remover Turno» dice «Esta acción no se puede deshacer» y es reversible.
- Usa observables compartidos del servicio: puede verse un instante el dato de otro alumno.
- Solo se llega desde Alumnos del grupo; no hay enlace desde la ficha.

### Productos de un alumno

- Sin aviso al salir con cambios pendientes: se pierden en silencio.
- Los cargos nuevos viajan con precio 0; el servidor debería poner el del producto (sin verificar).
- «Pagado» es una casilla: marcarla no pasa por el cobro de Tesorería.
- Un gestor que desmarca un cargo pagado ve la casilla volver y se abre la petición «Pedir al propietario que revierta este cobro».
- La papelera marca el borrado sin confirmar (solo ADMIN); «Deshacer eliminación» lo revierte.
- Confirmar aplica las operaciones una a una; los fallos salen como avisos sueltos y sigue con el resto.
- No muestra la sede del cargo ni la deuda total del alumno.
- Tabla de 9 columnas con campos en línea: en móvil obliga a comprimir u ocultar columnas.
- Cancelar y Reactivar cargo actúan al momento, mezclados con un modelo de lote.

## Preguntas para el propietario

- **P-1** (Alta de alumno). ¿Añadimos «Sede» al alta? Hoy no existe; Sierra Sur la necesita y, con varias sedes, el servidor responde «Indica la sede.»
- **P-2** (Alta de alumno). ¿Validamos «fecha de alta ≥ fecha de nacimiento» en el alta, como ya hace la ficha?
- **P-3** (Eliminar alumnos). ¿Avisamos de pagos y cargos antes del borrado definitivo? Pide un dato nuevo del servidor.
- **P-4** (Eliminar alumnos). ¿Dejamos «Eliminar definitivamente» solo al propietario? Hoy pueden ambos, ADMIN y MANAGER.
- **P-5** (Editar grupo). ¿Mostramos y dejamos editar el rango de edad del grupo? Hoy está oculto y se reenvía sin tocar.
- **P-6** (Crear y editar turno). ¿El tipo de grupo del turno pasa a salir del catálogo de disciplinas? Hoy es una lista fija en la plantilla.
- **P-7** (Crear y editar turno). ¿Corregimos el cambio de grupo de un turno para que lo quite del grupo anterior? Hoy solo lo asigna al nuevo.
- **P-8** (Seleccionar alumnos). ¿Los alumnos que ya están en el grupo se ven atenuados o se ocultan? Hoy se ocultan después de paginar y dejan páginas cortas.
- **P-9** (Productos de un alumno). Hoy los cargos nuevos viajan con precio 0: ¿confirmamos que el servidor pone el del producto antes de 8b-3n?

## Cómo responder

Por pantalla: Hoy, P1 o P2, y lo que cambiarías.

## Anexo: inventario de hoy (solo lectura)

Copia íntegra de `inventario-b4.md`. Las rutas de componente son relativas a `src-frontend/src/app/componentes/endpoints/`.

Formato: «cosa de hoy — notas». Rutas de componente relativas a `src-frontend/src/app/componentes/endpoints/`.
API base = `environment.apiUrl` (`/api`). Ninguna de las 8 tiene versión nueva (`panel-nuevo`) ni `disenoNuevoGuard`; todas usan la hoja «admin-*» de hoy (Bootstrap + admin-card, admin-btn).

### Común a las 8
- Guard: `roleGuard` con `requiredRoles = ['ROLE_ADMIN','ROLE_MANAGER']` (`app.routes.ts`, `ROUTE_ROLE_SETS.management`) + `resolve: catalogo`. Sin acceso: `/` (solo profesor: `/mis-grupos`).
- ROLE_RESPONSABLE_SEDE NO entra por el router en ninguna, aunque el backend le abre varios endpoints de estas pantallas (se indica en cada una).
- Avisos: SweetAlert2 (`Swal.fire`) para errores y confirmaciones; `showSuccessToast` / `showErrorToast` para avisos cortos.
- Ninguna pantalla muestra ni pide sede.

---

### 1. Alta de alumno

- Ruta `/alumnosCrear`. `crear-alumno/crear-alumno.component.{ts,html,scss}`. Roles front: ADMIN, MANAGER. Backend `POST /api/alumnos/crear`: ADMIN, MANAGER, RESPONSABLE_SEDE.
- Tamaño: html 34,7 kB + ts 24,2 kB = ~59 kB.
- Se llega desde: botón «Crear nuevo alumno» del listado de alumnos de hoy (`listado-alumnos.component.html:10`) y del nuevo (`panel-nuevo/alumnos`, también «Dar de alta el primero» en vacío); menú de cabecera; vista-principal-admin.
- Estructura: cabecera «Crear Nuevo Alumno» + subtítulo; tarjeta única (col-lg-10/xl-8) con 4 secciones: (1) «Datos Personales» (rejilla 2 col.); (2) «Deportes del Alumno» (1 a 4 bloques «Deporte N» con «Eliminar», botón «Agregar Otro Deporte» y nota); (3) «Imagen del Alumno»; (4) «Autorizaciones y Datos Adicionales»; pie Cancelar / Crear Alumno.

### Campos de datos personales
- Nombre — text — obligatorio — «El nombre del alumno es obligatorio».
- Apellidos — text — obligatorio — «Los apellidos del alumno son obligatorios».
- Fecha de Nacimiento — date — obligatorio — «La fecha de nacimiento es obligatoria». Al cambiar: `GET /api/grados/disponibles/{fecha}` (filtra grados por edad; error: Swal «No se pudieron cargar los grados disponibles.») y recalcula categoría de competición.
- NIF / DNI — text — opcional — patrón `^[0-9]{8}[A-Za-z]$` — «Formato inválido (8 números y una letra)». Al enviar: trim + mayúsculas; vacío = null. Backend: 409 si el NIF ya existe.
- Dirección — text — obligatorio — «La dirección es obligatoria».
- Teléfono Principal — tel — obligatorio — solo dígitos, máx. 9 — «El teléfono es obligatorio» / «Solo números permitidos» / «Máximo 9 caracteres».
- Teléfono Secundario — tel — opcional — mismo patrón y máx. 9.
- Email — email — obligatorio — «El email es obligatorio» / «Formato de email inválido».
- Responsable legal — text — opcional — máx. 180. Siempre visible (no depende de la edad).
- DNI/NIE del responsable — text — opcional — máx. 9 — patrón `^[0-9XYZxyz][0-9]{7}[A-Za-z]$` — «Introduce un DNI/NIE válido.» trim + mayúsculas; vacío = null. Backend lo guarda en tutor (`aplicarResponsableLegal`).
- No hay lógica menor/tutor: ni cálculo de «es menor», ni responsable obligatorio por edad, ni tutores adicionales, salud, documentos o consentimientos en el alta.

### Campos por deporte (FormArray `deportesInicial`, 1 a 4 filas)
- Deporte — select con buscador — obligatorio — `catalogo.disciplinasActivas()`; «Selecciona el deporte». Al cambiar: reinicia tarifa si no vale; exige o limpia grado y fecha de grado; reinicia licencia y competición si la disciplina no las admite. Backend: error si se repite («El deporte X está repetido en el alta.») o no está en la escuela.
- Grado Inicial — select con buscador — obligatorio solo si `catalogo.tieneGrados`; si no, oculto. Opciones: `GET /api/grados` filtrado por disciplina y edad. Sin mensaje de error propio.
- Fecha de Grado — date — obligatoria si hay grado; defecto hoy.
- Fecha de Alta en Deporte — date — obligatoria — defecto hoy.
- Fecha Alta Inicial (Antigüedad) — date — opcional; ayuda «Dejarlo vacío usa la Fecha de Alta».
- Tipo de Tarifa — select con buscador — obligatorio — `catalogo.tarifasDe(disciplina)` (sin deporte: vacío). Al cambiar autorrellena Cuantía (`cuantiaEstandar`) y activa Rol o Grupo familiar según banderas de la tarifa.
- Cuantía (€) — number min 0 step 0.01 — opcional — «Autorrelleno editable».
- Rol Familiar — select (enum RolFamiliar) — obligatorio solo si la tarifa `requiereRolFamiliar` (si no, oculto y deshabilitado). Al cambiar recalcula Cuantía.
- Grupo Familiar — text («Ej: FAMILIA_GARCIA») — obligatorio solo si la tarifa `requiereGrupoFamiliar`.
- ¿Tiene licencia federativa? — switch — solo si `catalogo.requiereLicencia`. Apagarlo limpia competidor, categoría, fechas de competición y peso.
- Número de Licencia (number) y Fecha de Licencia (date, defecto hoy) — opcionales — solo con licencia.
- ¿Es Competidor? — switch — solo si `catalogo.permiteCompeticion` y (la disciplina no pide licencia o hay licencia). Si falta licencia: aviso «El alumno debe tener una licencia federativa activa para poder ser marcado como competidor.»
- Con competidor: Fecha Alta Competicion (date; defecto = alta), Fecha Alta Competidor Inicial (date; defecto = alta inicial), Categoria (select Infantil/Precadete/Cadete/Junior/Senior; autorrelleno por edad y deporte), Peso (kg), Fecha de Peso (date; defecto hoy). Todos opcionales. Sin competidor al enviar: categoría '', fechas y peso null.
- Código muerto: `onCompetidorChange`, `onLicenciaChange`, `aplicarFiltrosGrado` leen controles que ya no existen.

### Campos finales
- Imagen del Alumno — file `image/*` (arrastrar o clic) — opcional — «JPG, PNG, GIF, WebP (máx. 50MB)». Si falla: Swal «No se pudo usar la imagen» + motivo. Vista previa («Imagen del alumno seleccionada / Vista previa disponible») + botón «Eliminar».
- Autorización Web — radio Sí/No — defecto Sí — ayuda «Autorización para publicar imágenes del alumno en la web». Backend registra consentimiento (origen APP).
- ¿Tiene discapacidad? — switch — defecto No.

### Acciones
- «Eliminar» (por deporte) — quita la fila; deshabilitado con 1; Swal «Acción no permitida / Un alumno debe tener al menos 1 deporte».
- «Agregar Otro Deporte» — deshabilitado con 4; Swal «Límite alcanzado / Un alumno puede tener un máximo de 4 deportes».
- «Cancelar» — `routerLink /alumnosListar`, sin confirmar aunque haya datos.
- «Crear Alumno» (submit; deshabilitado si inválido) — `POST /api/alumnos/crear` multipart: parte `nuevo` = JSON (nombre, apellidos, fechaNacimiento, nif, direccion, telefono, telefono2, email, responsableLegalNombre/Nif, autorizacionWeb, rolFamiliar, grupoFamiliar, tieneDiscapacidad, aptoParaExamen=false, deportesInicial[]) + parte `file` opcional. Éxito: toast «Alumno creado con N deporte(s)» y navega a `/alumnosEditar/{id}` (sin id: `/alumnosListar`). Error: scroll arriba + Swal «Error en la petición» con el texto del servidor o «No has completado todos los campos requeridos». Si inválido: Swal «Formulario incompleto / Por favor completa todos los campos requeridos».
- Nota fija bajo los deportes: «Nota: Puedes gestionar los deportes del alumno en la página de edición.»

### Estados
- Única carga: `GET /api/grados` (error: Swal «No se pudieron cargar los grados»). Sin esqueleto, vacío, paginación ni búsqueda.

### Particularidades
- SEDE: sin campo de sede; no envía `sedePrincipalId`. Backend (`GestionPorSede.sedeDeAlta`): gestión → sede por defecto (con varias sedes puede quedar NULL); responsable con una sola sede → automática; con varias → 400 «Indica la sede.». Sierra Sur (6 sedes) necesita campo de sede.
- CARGOS INICIALES: ningún campo. El backend llama a `asignarMensualidadGeneralSiCorresponde`; la licencia nunca se cobra en el alta. Matrícula y otros cargos se asignan después (pantalla 5).
- Duplicados: solo por NIF. La validación fechaAlta >= fechaNacimiento existe en editar-alumno, NO en el alta.
- Tras crear salta a la ficha (`alumnosEditar/:id`).

---

### 2. Crear y editar grupo

### 2a. Crear grupo
- Ruta `/gruposCrear`. `crear-grupo/crear-grupo.component.{ts,html}`. Roles front: ADMIN, MANAGER; backend `POST /api/grupos` también RESPONSABLE_SEDE.
- Tamaño: html 3,5 kB + ts 1,6 kB = ~5 kB.
- Se llega desde: «Crear grupo» del listado de grupos de hoy (`listado-grupos.component.html:14`) y del nuevo (`panel-nuevo/grupos`); menú de cabecera; vista-principal-admin.
- Estructura: cabecera «Crear Nuevo Grupo» + subtítulo; tarjeta estrecha con «Información del Grupo» (un campo) y pie Cancelar / Crear Grupo.
- Campo: Nombre del Grupo — text — obligatorio, máx. 50 — «El nombre del grupo es obligatorio» / «Máximo 50 caracteres». Placeholder «Ej: Infantiles Cinturón Blanco». Ayuda «Elige un nombre descriptivo para identificar el grupo fácilmente».
- Acciones: «Cancelar» (`location.back()`); «Crear Grupo» (deshabilitado si inválido) `POST /api/grupos` cuerpo `{nombre}`. Éxito: toast «Has creado un nuevo grupo» → `/gruposListar`. Error: toast «No has completado todos los campos requeridos» (genérico para cualquier fallo).
- Estados: sin carga ni vacío. El DTO tiene `deporte`, `rangoEdadMin/Max`, pero el alta no los pide; tampoco sede.

### 2b. Editar grupo
- Ruta `/gruposEditar/:id`. `editar-grupo/editar-grupo.component.{ts,html,scss}`. Roles: ADMIN, MANAGER. La tarjeta «Profesores» sale si `auth.tieneAccesoAdmin()` (siempre en esta ruta).
- Tamaño: html 12,2 kB + ts 12,2 kB = ~24 kB (scss 7,5).
- Se llega desde: «Editar» de cada grupo en el listado de hoy (`listado-grupos.component.html:117`) y del nuevo.
- Estructura: cabecera «Editar Grupo» + «Modifica el nombre del grupo»; esqueleto mientras carga; tarjeta 1 «Información del Grupo» (Nombre) + Cancelar / Actualizar Grupo; tarjeta 2 «Profesores»: lista (selector de rol, «Invitación pendiente», Reenviar, Quitar) y «Añadir profesor» (email, rol, chips «También en estos grupos», avisos, botón).
- Campos grupo: Nombre del Grupo (igual que crear). El formulario lleva también `rangoEdadMin` y `rangoEdadMax` (cargados y reenviados en el PUT) sin control en la plantilla: no se ven ni se editan.
- Campos «Añadir profesor» (ngModel): Email (email; botón deshabilitado si vacío; placeholder «profesor@ejemplo.com»; cambiarlo reinicia el modo); Rol (select Titular/Ayudante, defecto Titular); «También en estos grupos» (chips con los demás grupos de `GET /api/grupos/resumen`; oculto si no hay o si falla); Nombre y Apellidos (text, máx. 50, obligatorios, solo en modo «sin cuenta»).
- Acciones grupo: «Cancelar» (`location.back()`); «Actualizar Grupo» (deshabilitado si inválido) `PUT /api/grupos/{id}` cuerpo `{nombre, rangoEdadMin, rangoEdadMax}`; éxito toast «El grupo se ha actualizado correctamente» → `/gruposListar`; error toast «Error al actualizar el grupo». Carga `GET /api/grupos/{id}` (error: toast «No se pudo cargar el grupo»).
- Acciones profesores:
  - Lista `GET /api/grupos/{id}/profesores`. Cargando: barra esqueleto. Vacío: «Este grupo aún no tiene profesores.» Error: aviso («No se han podido cargar los profesores. Inténtalo de nuevo.» o mensaje del servidor) + «Reintentar».
  - Selector de rol por profesor: `POST /api/grupos/{id}/profesores` `{email, rol}`; si falla, devuelve el select al rol real y muestra el error bajo el formulario.
  - «Reenviar» (solo con invitación pendiente): `POST /api/miembros/{usuarioId}/invitacion`; toast «Invitación reenviada».
  - «Quitar»: Swal «¿Quitar a {nombre} de este grupo?» / «Seguirá teniendo el rol de profesor en la escuela.» («Quitar» rojo / «Cancelar») → `DELETE /api/grupos/{id}/profesores/{usuarioId}`; sin toast de éxito (recarga).
  - Botón de alta, texto variable: «Añadir» / «Añadir a N grupos» / «Enviar invitación» / «Reactivar y añadir» → `POST /api/miembros` `{email, rol:'PROFESOR', grupos:[{grupoId, rol}...]}` (+ `nombre`, `apellidos` si sin cuenta; + `reactivar:true` si desactivado). Toasts «Profesor añadido» / «Invitación enviada».
  - Modos por error: 404 «No hay ninguna cuenta con ese email…» → aviso «No hay ninguna cuenta con ese email. Te pedimos su nombre para invitarle.» + pide nombre y apellidos; 409 «…desactivado en esta escuela» → aviso «Tiene el rol de profesor desactivado en esta escuela.»; otros: mensaje rojo o «No se ha podido añadir al profesor. Inténtalo de nuevo.»
  - Con una acción en curso se bloquean los demás controles.
- Estados: esqueleto de tarjeta mientras `cargando`; profesores con carga/error/vacío propios. Sin paginación ni búsqueda.
- Particularidades: la pantalla no muestra los alumnos ni los turnos del grupo; no hay «Eliminar grupo» (existe `DELETE /api/grupos/{id}` en el servicio, no se vio su uso aquí). Sin sede ni deporte en pantalla.

---

### 3. Crear y editar turno

### 3a. Crear turno
- Ruta `/turnosCrear` (acepta `?grupoId=N` que preselecciona grupo y tipo). `crear-turno/crear-turno.component.{ts,html}`. Roles front: ADMIN, MANAGER; backend `crear-asignando-grupo` también RESPONSABLE_SEDE.
- Tamaño: html 10,8 kB + ts 4,4 kB = ~15 kB.
- Se llega desde: «Crear turno» del listado de grupos de hoy (`listado-grupos.component.html:10`) y del nuevo (`panel-nuevo/grupos`); menú de cabecera; vista-principal-admin. No se halló ningún enlace con `?grupoId=`.
- Estructura: cabecera «Crear Nuevo Turno» + «Define un horario con día de la semana y horas para las clases»; tarjeta con «Información del Horario» (día, hora inicio, hora fin, alerta) y «Asignación a Grupo (Opcional)» (switch + 2 selects); pie Cancelar / Crear Turno.
- Campos:
  - Día de la Semana — select con buscador — obligatorio — Lunes a Domingo (valor con tilde) — «El día de la semana es obligatorio».
  - Hora de Inicio / Hora de Fin — time — obligatorias — patrón HH:mm — «La hora de inicio|fin es obligatoria» / «Formato HH:mm requerido».
  - Validación cruzada inicio >= fin: alerta «La hora de inicio debe ser anterior a la hora de fin» (botón deshabilitado).
  - Asignar a un grupo existente — switch — defecto No — ayuda «Puedes asociar este turno a un grupo específico para mejor organización». Activo muestra:
  - Grupo — select con buscador — SIN validador (no obligatorio aunque el switch esté activo) — `GET /api/grupos/resumen`; «Selecciona un grupo...».
  - Tipo de Grupo — select con buscador — obligatorio solo con el switch activo — lista FIJA en la plantilla: Taekwondo Infantil, Taekwondo Joven, Taekwondo Adulto, Kickboxing, Pilates, Defensa Personal Femenina, Competición — «El tipo de grupo es obligatorio». No sale del catálogo de disciplinas.
- Acciones: «Cancelar» (`routerLink /gruposListar`, sin confirmar); «Crear Turno» (deshabilitado si inválido): sin grupo `POST /api/turnos/crear` con todo el formulario `{diaSemana, horaInicio, horaFin, asignarGrupo:false, grupoId:'', tipoGrupo:''}`; con grupo `POST /api/turnos/crear-asignando-grupo` `{…, asignarGrupo:true, grupoId, tipoGrupo}`. Éxito: toast «Has creado un nuevo turno» → `/gruposListar`. Error: toast «No has completado todos los campos requeridos» (genérico).
- Estados: carga de grupos sin indicador; error: toast «No hemos podido conectar con el servidor para obtener los grupos». Sin vacío.
- Particularidades: se puede enviar con switch activo y Grupo vacío (solo exige tipo); respuesta del backend no verificada. Sin sede.

### 3b. Editar turno
- Ruta `/turnosEditar/:id`. `editar-turno/editar-turno.component.{ts,html,scss}`. Roles front: ADMIN, MANAGER (backend `PUT|DELETE /api/turnos/{id}` también RESPONSABLE_SEDE).
- Tamaño: html 8,7 kB + ts 5,6 kB = ~14 kB (scss 1,5).
- Se llega desde: enlace a cada turno en las tarjetas del listado de grupos de hoy (`listado-grupos.component.html:74` y `:146`) y del nuevo.
- Estructura: cabecera «Editar Turno» + «Modifica el horario del turno»; esqueleto mientras carga; tarjeta «Información del Horario» (día, inicio, fin, alerta, Grupo) y pie Cancelar / Actualizar Turno.
- Campos: Día de la Semana (igual que crear, 7 opciones); Hora de Inicio y Hora de Fin (igual, misma alerta); Grupo — select con buscador, opcional, primera opción «Sin grupo asignado», ayuda «Puedes asignar este turno a un grupo o dejarlo sin grupo». No hay «Tipo de Grupo».
- Acciones: «Cancelar» (`location.back()`); «Actualizar Turno» (deshabilitado si inválido) `PUT /api/turnos/{id}` cuerpo `{diaSemana, horaInicio, horaFin}`. Si cambió el grupo: nuevo grupo `POST /api/grupos/{nuevo}/turnos/{id}` (toast «Turno actualizado y asignado al grupo» / error «Turno actualizado pero error al asignar grupo»); quitar grupo `DELETE /api/grupos/{viejo}/turnos/{id}` (toast «Turno actualizado y desvinculado del grupo» / «Turno actualizado pero error al desvincular grupo»). Sin cambio de grupo: toast «Turno actualizado correctamente». Todos van a `/gruposListar`. Error del PUT: toast «No hemos podido actualizar el turno».
- Al cambiar de grupo A a B solo se llama al POST de B (no al DELETE de A).
- Estados: `GET /api/turnos/{id}` (error: Swal «Error en la carga del turno / No hemos podido cargar los detalles del turno»); `GET /api/grupos/resumen` (error: toast «Error al cargar los grupos»). Sin paginación.
- Particularidades: sin «Eliminar turno» (existe `eliminarTurno` en el servicio; no visto en esta pantalla). No muestra los alumnos del turno.

---

### 4. Gestionar alumnos del grupo

- Ruta `/gestionarAlumnos/:id`. `listado-grupos/gestionar-alumnos/gestionar-alumnos.component.{ts,html,scss}`. Roles front: ADMIN, MANAGER. Backend `DELETE /api/grupos/{g}/alumnos/{a}` también RESPONSABLE_SEDE.
- Tamaño: html 3,7 kB + ts 5,2 kB = ~9 kB (scss 1,4).
- Se llega desde: «Gestionar alumnos» de cada grupo en el listado de hoy (`listado-grupos.component.html:113`) y del nuevo; vuelve aquí tras agregar alumnos (pantalla 7).
- Estructura: tarjeta con título «Gestionar Alumnos del Grupo» + «Volver»; esqueleto; buscador «Buscar alumno por nombre...» (con «Mostrando X de Y alumnos» si hay texto); lista (foto enlazada a la ficha, nombre enlazado, «Gestionar Turnos», «Eliminar»); paginación; botón central «Agregar Alumnos». El título no dice el nombre del grupo.
- Campos: solo el buscador (debounce 300 ms, filtra en el navegador por «nombre apellidos»).
- Acciones:
  - «Volver» (`location.back()`); foto o nombre → `/alumnosEditar/{id}`.
  - «Gestionar Turnos» → `/gestionarTurnosAlumno/{alumnoId}` (pantalla 8).
  - «Eliminar» — Swal «¿Estás seguro?» / «No podrás revertir esta acción» («Sí, eliminarlo» / «Cancelar») → `DELETE /api/grupos/{grupoId}/alumnos/{alumnoId}` (quita del grupo; no borra al alumno, aunque el texto lo sugiere). Éxito: toast «Alumno eliminado del grupo». Error: toast «No se pudo eliminar el alumno».
  - «Agregar Alumnos» → `/seleccionarAlumnos/{grupoId}` (pantalla 7).
- Estados: `GET /api/grupos/{id}` con todos los alumnos (error: Swal «Error en la petición / No hemos podido conectar con el servidor»); esqueleto de 10; vacío «No hay alumnos en este grupo» o, con búsqueda, «No se encontraron alumnos que coincidan con la búsqueda»; paginación local de 10 (solo si > 1 página).
- Particularidades: solo `activo !== false` (los inactivos no salen); paginación y búsqueda locales; sin acciones masivas; la fila no muestra deporte, grado ni turnos. Foto por defecto `assets/media/default.webp`.

---

### 5. Gestionar productos de un alumno

- Ruta `/alumnos/:id/productos`. `editar-alumno/productos-alumno/productos-alumno.component.{ts,html,scss}`. Roles front: ADMIN, MANAGER. Dentro: borrar cargo (papelera) solo ROLE_ADMIN (`esPropietario`); cancelar/reactivar para ADMIN y MANAGER. Backend responsable de sede: GET cargos, asignar, `PUT /api/productos-alumno/{id}`, cancelar/reactivar; NO el DELETE.
- Tamaño: html 13,6 kB + ts 21,5 kB = ~35 kB (scss 13,4).
- Se llega desde: «Gestionar productos» (icono engranaje) de la pestaña Pagos de la ficha nueva (`panel-nuevo/ficha/pagos/ficha-pagos.component.html:68`) y desde `editar-alumno.component.ts:2113` (ficha de hoy).
- Estructura: barra «Productos del Alumno» + «Volver»; tarjeta con tabla de 9 columnas (Concepto, Fecha Asignación, Cantidad, Precio, Total, Pagado, Fecha Pago, Notas, Acciones); paginación; «Asignar Nuevo Producto» (select de producto, select de deporte si es matrícula, «Asignar Producto»); lista «Pendientes de asignar»; barra flotante «Tienes cambios pendientes» (Cancelar / Confirmar); modal de notas.
- Modelo: edición en lote. Editar celdas, marcar borrado y asignar NO llaman al servidor hasta «Confirmar». Cancelar y Reactivar cargo actúan al momento.
- Campos por fila (todos opcionales, sin mensajes de validación):
  - Concepto — texto (tachado con «Cancelado por X el dd/mm/aaaa · «motivo»» si está cancelado; insignia «Pendiente de eliminar»).
  - Fecha Asignación — date (cambia el día y conserva la hora); Cantidad — number min 1; Precio — number min 0 step 0.01. Deshabilitados si pendiente de borrar o cancelado.
  - Total — calculado precio × cantidad en EUR (verde/rojo según pagado; tachado si cancelado).
  - Pagado — checkbox; sustituido por chip «cancelado» o «devuelto» (tooltip «Pagado y devuelto: su cobro tiene una devolución registrada»). Un gestor (no propietario) que desmarca un cargo ya pagado: el check vuelve a marcarse y se abre «Pedir al propietario que revierta este cobro» (Concepto, Importe, Fecha de pago; nota «Mientras tanto el pago sigue marcado.») → solicitud `REVERTIR_COBRO`.
  - Fecha Pago — solo lectura (`dd/MM/yyyy` o «-»).
  - Notas — texto truncado + lápiz «Editar notas» (modal `app-productos-alumno-notas`; cuenta como cambio pendiente).
- Campos de asignación: «Seleccionar Producto:» (select con buscador, «Seleccione un producto», opciones «concepto - precio»); «Deporte de la matrícula:» (solo si el concepto contiene MATRICULA, sin tildes; opciones = deportes del alumno; obligatorio en ese caso; sin deportes: «No hay deportes disponibles. Compruebe los deportes del alumno antes de asignar la matrícula.»).
- Acciones:
  - «Volver» (`location.back()`).
  - «Asignar Producto» — añade a «Pendientes de asignar» (deshabilitado sin producto o sin deporte en matrícula; si se fuerza: Swal «Error / Seleccione un producto» o «Seleccione un deporte / La matrícula debe asignarse a un deporte del alumno.»). Cada pendiente tiene X «Quitar de pendientes».
  - «Cancelar» por cargo (solo no pagados ni cancelados) — Swal «Cancelar cargo» / ««{concepto}». No se borra: queda tachado en la ficha y se puede reactivar.» + textarea «Motivo» obligatorio, máx. 500 («Indica el motivo de la cancelación» / «El motivo admite como mucho 500 caracteres»), botones «Cancelar cargo» / «Volver» → `POST /api/productos-alumno/{id}/cancelar` `{motivo}`; toast «Cargo cancelado». Errores 400/409: los muestra el interceptor.
  - «Reactivar» (cargos cancelados) — Swal «Reactivar cargo» / ««{concepto}» vuelve a estar pendiente de pago.» («Reactivar» / «Volver») → `POST /api/productos-alumno/{id}/reactivar`; toast «Cargo reactivado».
  - Papelera «Marcar para eliminar» (solo ADMIN; cargo no pagado ni cancelado) — marca el borrado (fila atenuada) sin confirmar; «Deshacer eliminación» lo revierte.
  - «Confirmar» (barra flotante) — en secuencia: `PUT /api/productos-alumno/{id}` con el cargo completo por cada fila cambiada (cantidad, precio, pagado, fechaPago, fechaAsignacion, notas); `DELETE /api/productos-alumno/{id}` por cada borrado; `POST /api/productos-alumno/alumno/{a}/producto/{p}` o `…/producto/{p}/deporte/{deporte}` con detalles `{cantidad:1, precio:0, pagado:false, fechaAsignacion:hoy, notas:''}` por cada asignación. Errores por operación: toasts «No se pudo actualizar un producto» (o mensaje del servidor) / «No se pudo eliminar un producto» / «No se pudo asignar un producto»; sigue con el resto. Al final recarga y, sin errores, toast «Cambios aplicados correctamente».
  - «Cancelar» (barra flotante) — descarta todo y recarga.
- Estados: `GET /api/productos-alumno/alumno/{id}` (más reciente primero; error Swal «Error al obtener / No se han podido obtener los productos del alumno»); `GET /api/productos/todos` (error «No se han podido obtener los productos»); `GET /api/alumnos/{id}` para los deportes (error toast «No se han podido obtener los deportes del alumno»); esqueleto de tabla; vacío con imagen «No hay productos asignados a este alumno»; paginación local de 10 con estado en sessionStorage `productosAlumnoEstado_{id}`. Sin búsqueda ni filtros.
- Particularidades: sin aviso al salir con cambios pendientes (no se vio `canDeactivate` ni `beforeunload`). Los cargos nuevos viajan con precio 0 (el servidor debería poner el del producto; sin verificar). No muestra sede del cargo ni deuda total del alumno.

---

### 6. Eliminar alumnos

- Ruta `/alumnosEliminar`. `eliminar-alumno/eliminar-alumno.component.{ts,html,scss}`. Roles front: ADMIN, MANAGER. Backend `PUT /api/alumnos/{id}/baja` de gestión; `DELETE /api/alumnos/{id}` solo gestión (D6-4), no responsable de sede.
- Tamaño: html 6,5 kB + ts 14,5 kB = ~21 kB (scss 1,8).
- Se llega desde: menú de cabecera (`header.component.html:408`) y vista-principal-admin (`:101`, `:387`). Sin enlace desde los listados nuevos.
- Estructura: barra «Gestión de Bajas y Eliminaciones» con «Dar de Baja Seleccionados» y «Eliminar Seleccionados»; filtros (buscador «🔍 Filtrar por nombre...» y botón «Mostrar todos» / «Mostrar solo activos»); alerta amarilla «¡Atención! Esta página permite dar de baja o eliminar alumnos permanentemente. Por favor, proceda con precaución ya que estas acciones pueden ser irreversibles.»; tabla (casilla maestra, Foto, Nombre, Apellidos, Nº Expediente, DNI, Estado, Acciones); paginación.
- Campos: casilla por fila y «Seleccionar todos» (solo la página visible); buscador (debounce 500 ms, servidor); botón activos/todos (defecto solo activos).
- Acciones:
  - «Baja» (fila activa; en móvil solo icono) — Swal «¿Estás seguro?» / «El alumno será dado de baja» («Sí, dar de baja» / «Cancelar») → `PUT /api/alumnos/{id}/baja`; éxito Swal «Alumno dado de baja / El alumno ha sido dado de baja correctamente.» (2 s); error «Error al dar de baja al alumno / Ha ocurrido un error al intentar dar de baja al alumno.»
  - «Alta» (fila inactiva) — `GET /api/alumnos/{id}/deportes`; ninguno inactivo: Swal info «El alumno ya tiene todos sus deportes activos.»; uno: `PUT /api/alumnos/{id}/deportes/{deporte}/activar` directo; varios: Swal «Selecciona el deporte a activar» con select buscable («Selecciona un deporte...», «✓ Todos los deportes», uno por deporte; validación «Por favor selecciona un deporte»; botón «Dar de Alta») y activa uno o todos en secuencia. Éxito «Alumno dado de alta / El alumno ha sido dado de alta en {deporte}.» (o «…en todos sus deportes: …»). Errores: texto del servidor o «Error al dar de alta al alumno» / «Error al activar algunos deportes» / «Error al obtener los deportes del alumno». No usa `PUT /alumnos/{id}/alta`.
  - «Eliminar» (fila) — Swal «¿Estás seguro?» / «El alumno será eliminado permanentemente» («Sí, eliminar» / «Cancelar») → `DELETE /api/alumnos/{id}`; éxito «Alumno eliminado / El alumno ha sido eliminado correctamente.»; error «Error al eliminar alumno / Ha ocurrido un error al intentar eliminar al alumno.»
  - Masivas — Swal «Los alumnos seleccionados serán dados de baja» («Sí, darlos de baja») / «…serán eliminados permanentemente» («Sí, eliminarlos»); sin selección, info «No hay alumnos seleccionados / Por favor, seleccione al menos un alumno para dar de baja|eliminar.» Una petición por alumno en secuencia (del último al primero); un fallo muestra Swal con nombre y apellidos y continúa; al final Swal «Alumnos dados de baja|eliminados» y recarga.
  - Foto y nombre sin enlace a la ficha.
- Estados: sin esqueleto (`cargando` solo deshabilita la paginación); vacío con imagen «No se encontraron alumnos que coincidan con los criterios de búsqueda.»; error de carga Swal «Error en la petición / No hemos podido conectar con el servidor»; paginación servidor `GET /api/alumnos?page&size=10&incluirInactivos&nombre`; estado (página, filtro, activos/todos) en sessionStorage `eliminarAlumnoEstado`; si la página queda fuera de rango, retrocede a la última.
- Particularidades: el borrado definitivo no avisa de dependencias (pagos, cargos); la confirmación no repite el nombre ni cuenta los seleccionados; selección masiva solo de la página; «Baja» (alumno entero) y «Alta» (por deporte) son asimétricas. ADMIN y MANAGER pueden ambos borrar definitivamente.

---

### 7. Seleccionar alumnos para un grupo

- Ruta `/seleccionarAlumnos/:id` (id = grupo). `listado-grupos/gestionar-alumnos/seleccionar-alumnos/seleccionar-alumnos.component.{ts,html,scss}`. Roles: ADMIN, MANAGER.
- Tamaño: html 1,8 kB + ts 4,8 kB = ~6,6 kB (scss 0,6).
- Se llega desde: «Agregar Alumnos» de la pantalla 4.
- Estructura: tarjeta con «Seleccionar Alumnos» + «Volver»; esqueleto; campo «Filtrar por nombre» (1/3 del ancho, a la derecha); lista de casillas «nombre apellidos»; botón «Agregar Alumnos Seleccionados»; paginación.
- Campos: buscador (debounce 500 ms, servidor; vuelve a página 1); casilla por alumno. El TS tiene `mostrarInactivos=false` fijo sin control.
- Acciones: «Volver» (`location.back()`); «Agregar Alumnos Seleccionados» — sin selección no hace nada (ni aviso; el botón no se deshabilita); con selección `POST /api/grupos/{grupoId}/alumnos` cuerpo `[ids]`, toast «Alumnos agregados al grupo» → `/gestionarAlumnos/{grupoId}`; error toast «No se pudieron agregar los alumnos».
- Estados: `GET /api/grupos/{id}` (alumnos ya en el grupo) y `GET /api/alumnos?page&size=10&nombre&incluirInactivos=false`; los que ya están en el grupo se ocultan en el cliente DESPUÉS de paginar (páginas con menos de 10 filas o vacías, sin mensaje); sin texto de vacío; error Swal «Error en la petición / No hemos podido conectar con el servidor».
- Particularidades: la selección (array de ids) se conserva al paginar y buscar, sin contador ni «seleccionar todos». Cada búsqueda o cambio de página muestra el esqueleto y oculta el campo de filtro (puede perder el foco). Etiqueta de casilla sin `for`. Sin foto, deporte ni sede del alumno; no avisa de alumnos que ya están en otros grupos.

---

### 8. Turnos de un alumno

- Ruta `/gestionarTurnosAlumno/:alumnoId`. `listado-grupos/gestionar-alumnos/gestionar-turnos-alumno/gestionar-turnos-alumno.component.{ts,html,scss}`. Roles front: ADMIN, MANAGER (backend `POST|DELETE /api/alumnos/{id}/turnos/{turnoId}` también RESPONSABLE_SEDE).
- Tamaño: html 2,3 kB + ts 4,7 kB = ~7 kB (scss 0,4).
- Se llega desde: «Gestionar Turnos» de cada alumno en la pantalla 4. No se halló otro enlace (ni desde la ficha).
- Estructura: tarjeta con «Gestionar Turnos de {nombre} {apellidos}»; lista de turnos del alumno («tipoGrupo - HH:mm a HH:mm» + «Remover Turno»); bloque «Agregar Turno» (select «Seleccione un turno» + «Asignar Turno»); «Volver».
- Campos: Seleccione un turno — select con buscador — obligatorio (botón deshabilitado si vacío) — opciones de `GET /api/turnos/resumen` «{tipoGrupo} - {inicio} a {fin}»; sin opción de placeholder.
- Acciones:
  - «Remover Turno» — Swal «¿Estás seguro?» / «El turno será removido del alumno. Esta acción no se puede deshacer.» («Sí, remover» / «Cancelar») → `DELETE /api/alumnos/{alumnoId}/turnos/{turnoId}` (devuelve la lista nueva); toast «Turno removido del alumno»; error «No se pudo remover el turno».
  - «Asignar Turno» (submit) — `POST /api/alumnos/{alumnoId}/turnos/{turnoId}` cuerpo `{}`; toast «Turno asignado al alumno»; recarga; error: toast con `err.error.message` o «No hemos podido asignar el turno al alumno».
  - «Volver» (`location.back()`).
- Estados: `GET /api/alumnos/{id}` (error Swal «Error en la petición / No hemos podido obtener los datos del alumno»); `GET /api/alumnos/{id}/turnos`; esqueleto de lista mientras carga el alumno. Vacío: «Este alumno no tiene turnos asignados.» solo sale si el observable aún no ha emitido (un array vacío es verdadero en `@if … as turnos`, así que con 0 turnos se ve la lista vacía sin mensaje). Sin paginación ni búsqueda.
- Particularidades: ni la lista ni el desplegable muestran el DÍA de la semana (solo tipo de grupo y horas), así que turnos del mismo tipo en días distintos se ven iguales. El desplegable ofrece todos los turnos, también los ya asignados. No indica el grupo del turno ni si el alumno está en él. Usa BehaviorSubjects compartidos del servicio (`turnosDelAlumno$`, `turnos$`): puede verse un instante el dato de otro alumno.

---

### No determinado
- Si el backend crea grupos y turnos con sede (D6-17) y qué sede les pone al crearse desde estas pantallas.
- Respuesta del backend a `crear-asignando-grupo` con `grupoId` vacío y `asignarGrupo` activo.
- Si basta `POST /grupos/{B}/turnos/{id}` para mover un turno de A a B.
- Quién usa `/turnosCrear?grupoId=`; si el listado de grupos de hoy tiene «Eliminar grupo/turno» (no leído al detalle).
- Precio real de un cargo asignado desde la pantalla 5 (se envía 0).
- Acceso real del responsable de sede a las pantallas 1 y 5 (el router lo bloquea; el backend lo admite).
