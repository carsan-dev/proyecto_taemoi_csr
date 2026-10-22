# Product

<!-- impeccable:product-schema 1 -->

> Escrito sin entrevista: el propietario no estaba disponible y pidió expresamente no esperar respuesta.
> Todo lo que no figura en el roadmap ni en `docs/multi-escuela/` va marcado con «Supuesto:».

## Platform

web

Aplicación web responsive: frontend Angular 20 (componentes standalone, Angular Material y Bootstrap 5 con
Bootstrap Icons, SweetAlert2), API Spring Boot con MySQL 8. Se usa en escritorio y en móvil (Chrome en Android).

## Users

- **Propietario de la escuela (`ROLE_ADMIN`).** Dirige la escuela y en Moiskimdo además da clase en la sede de
  Umbrete. Usa mucho el móvil: prueba y hasta despliega desde él. Necesita ver de un vistazo cómo va la escuela
  (alumnos, cobros pendientes, caja, asistencia) y resolver las solicitudes de su equipo.
  Supuesto: alterna entre el móvil, durante y entre clases, y el escritorio para tareas largas (altas, tesorería).
- **Gestor (`ROLE_MANAGER`).** Lleva la administración diaria: alumnos y su ficha, cobros, mensualidades,
  convocatorias, documentos, eventos. Supuesto: trabaja sobre todo en escritorio, en sesiones largas y
  repetitivas en las que importa la rapidez y no equivocarse.
- **Profesor (titular o ayudante).** Ve solo «Mis grupos» y «Mi caja»; no ve NIF, email ni dirección de los
  alumnos. Pasa lista, cobra en mano y propone pases de grado. Móvil primero (375 px), en el tatami o en la
  puerta de la sala, con prisa y a veces con mala cobertura (riesgo citado en D7-17).
- **Responsable de sede.** Llega en la E9; hoy su rol falla cerrado. Supuesto: hará gestión acotada a sus sedes.
- **Familias y alumnos (`ROLE_USER`).** Área de familias: consultan a sus hijos (o a sí mismos), pagos,
  convocatorias y material de examen. Supuesto: casi siempre desde el móvil y de forma ocasional.
- **Superadmin de plataforma.** Solo edita la marca de cada escuela: logo y, con la E8b, colores
  (decisiones 135, 136 y 212). No gestiona alumnos ni miembros.
- **Visitantes de la web pública.** Posibles alumnos o sus familias que buscan escuela y se preinscriben.

## Product Purpose

Gestionar clubes y escuelas deportivas: alumnos, grupos, asistencia, cobros, progresión (grados o niveles) y
comunicación con las familias. Nicho de entrada: las artes marciales (taekwondo, kickboxing, pilates y defensa
personal femenina en Moiskimdo; taekwondo en Sierra Sur). Visión: cualquier deporte que se organice en grupos con
cuotas y progresión. La aplicación nació para una escuela y se está convirtiendo en SaaS multi-escuela «sin que
la escuela actual note nada en el camino».

Éxito: la escuela 1 (Moiskimdo, 800+ alumnos reales) sigue funcionando sin sorpresas mientras entran escuelas
nuevas, empezando por Taekwondo Sierra Sur (un club, seis sedes, caja por sede, tarifas comunes).
Supuesto: éxito también es que el profesor resuelva su clase desde el móvil sin pedir nada a la oficina.

## Positioning

Hecha desde dentro de una escuela real: su propietario es profesor en activo y el producto modela las reglas
del oficio (el grado sube solo al cobrar el derecho de examen, caja por sede, profesor titular o ayudante,
pases de grado, convocatorias, consentimientos firmados en papel). Supuesto: ese conocimiento del día a día de
un club real es lo que un software genérico de gimnasios no puede copiar de verdad. Las reglas propias de las
artes marciales son el primer caso y deben poder generalizarse a otros deportes sin romperlas.

## Operating Context

### Superficies y su modo

| Superficie | Quién | Modo |
|---|---|---|
| Panel de gestión (admin) | Propietario, gestor, profesor, responsable de sede | Operate |
| Área de familias | Familias y alumnos | Operate (Supuesto) |
| Web pública de cada escuela, con preinscripciones | Visitantes | Persuade |

Son superficies distintas, cada una con su propio brief; el rediseño de la E8b es el panel.

### Trabajos principales

- Alumnos y su ficha (datos, tutores, salud, consentimientos, documentos escaneados).
- Grupos y asistencia; profesores en varios grupos e invitación desde la ficha del grupo.
- Cobros, tesorería, mensualidades y caja por sede; «Mi caja» del profesor con entregas y solicitudes de deshacer.
- Convocatorias de examen y pases de grado (el grado sube solo al cobrar el derecho de examen).
- Documentos, eventos y preinscripciones llegadas desde la web pública.
- Invitaciones de miembros por correo, con rol, cargo, sedes y grupos.

### Escena de uso

- Propietario y profesor: móvil (Chrome en Android). Supuesto: de pie, entre clases, a menudo con una mano.
- Gestor: Supuesto: escritorio, sesión larga.
- Qué debe contar el panel (pedido para la E8b): asistencia por disciplina, plazas por grupo, próximas clases
  y pagos pendientes.

## Capabilities and Constraints

- **Multi-escuela.** Todo dato pertenece a una organización; una escuela nunca ve datos de otra.
- **Varias sedes por escuela.** Caja por sede; tarifas comunes a la escuela (caso Sierra Sur).
- **Tema por escuela.** Logo y colores de marca por organización, editados solo por el superadmin. El diseño
  respeta la paleta de cada escuela para no sentirse genérico.
- **Moiskimdo no nota cambios no decididos.** Arranca con su paleta actual y sin cambios visibles hasta que se
  toque. Cualquier cambio visible para ella es una decisión explícita del propietario.
- **Decisión 228.** La E8b se diseña antes de construir nada y el propietario elige la dirección.
- **Móvil primero** para propietario y profesor (375 px); en escritorio, el mismo contenido con más aire.
- **Datos personales de menores.** Muchos alumnos son menores. El profesor no ve NIF, email ni dirección. Los
  datos de salud van cifrados y solo los leen propietario, administración y profesores del alumno. El
  registro de acciones nunca guarda email, nombre, cargo, dirección ni teléfono.
- **Permisos que fallan cerrados.** Cada rol ve solo lo que su regla nombra; la interfaz no muestra lo que el
  rol no puede hacer (p. ej. «Mis grupos» solo con la capacidad `MIS_GRUPOS`).
- **Terminología.** Escuela (organización), sede, grupo, disciplina, grado, pase de grado, convocatoria,
  derecho de examen, mensualidad, caja, titular, ayudante, familia, preinscripción. Grado, pase de grado,
  convocatoria y derecho de examen son del nicho de entrada; Supuesto: otros deportes pedirán equivalentes
  (nivel, evaluación).
- **Idioma.** Textos en español de España, neutro y profesional.

## Brand Commitments

- La marca es de cada escuela, no de la plataforma: logo y colores por organización.
- Moiskimdo conserva su paleta actual mientras no se decida otra cosa.
- Tono pedido para el panel: claro, sobrio, con acento cálido.
- Referencias del propietario, solo como inspiración y no vinculantes: Academa (Nija Works) y Acero
  (MindInventory) para el tono; Rhythm Bars (Varvara Yaremenko) para qué contar; Growly (Phenomenon Studio)
  para la densidad ordenada y las gráficas variadas.
- **Anti-referencias:** degradados morados; filas de tarjetas pastel con un solo número.
- Mascota de la plataforma (en diseño): neutra, sin colores propios (toma los de cada escuela) y de ningún
  deporte concreto. Solo en momentos clave: más presente con familias y alumnos, poca y medida en el panel, y
  nunca en dinero, bajas ni errores serios.
- Personalidad. Supuesto: la de un buen entrenador; ordenado, directo, respetuoso, sin alardes.
- Voz. Supuesto: frases cortas, verbos de acción, sin jerga técnica ni anglicismos innecesarios; los errores
  dicen qué pasó y qué hacer («No hay ninguna cuenta con ese email. Que se registre primero.»).

## Evidence on Hand

- Escuela real en producción: Moiskimdo, 800+ alumnos y sus familias.
- Primer cliente externo: Taekwondo Sierra Sur (seis sedes), con importación de fichas en papel.
- Bocetos aprobados en texto: `docs/multi-escuela/fase3-e7-profesor.md` §5 y `fase3-e8-miembros.md` §5.
- No hay testimonios, métricas de uso, precios ni casos de estudio documentados: no inventarlos.

## Product Principles

1. **La escuela que ya funciona manda.** Nada cambia para Moiskimdo sin una decisión explícita.
2. **Cada escuela se ve como suya.** La marca de la organización se nota; la plataforma no se impone.
3. **Lo justo para cada rol.** Mostrar solo lo que ese rol puede ver y hacer; los datos de menores, al mínimo.
4. **Lo urgente, a un toque en el móvil.** Pasar lista, cobrar y ver pendientes se resuelve de pie y rápido.
5. **Contar lo que decide algo.** Asistencia, plazas, próximas clases y pagos pendientes antes que cifras sueltas.

## Accessibility & Inclusion

- Supuesto: objetivo WCAG 2.2 AA en el panel, el área de familias y la web pública.
- Supuesto: el tema por escuela no puede romper el contraste; si un color de marca no llega a AA, se ajusta su
  uso, no la norma.
- Supuesto: objetivos táctiles amplios y buena legibilidad en exterior para el uso en móvil en sala o en la puerta.
- Supuesto: familias de todas las edades y con poca soltura digital; lenguaje llano en el área de familias.

## Preguntas abiertas para el propietario

1. ¿Qué pantalla abre cada rol al entrar (resumen, agenda del día, Mis grupos) y qué debe verse sin desplazarse?
2. ¿El gestor trabaja sobre todo en escritorio, o también en móvil?
3. ¿Hasta dónde llega el color de la escuela en el panel: solo acentos y logo, o también cabecera y gráficas?
4. ¿Cuándo debe Moiskimdo ver el panel nuevo: a la vez que las demás escuelas, después, o solo si lo activas?
5. ¿El área de familias y la web pública entran en la E8b o se quedan como están por ahora?
6. ¿Quieres modo oscuro o basta con el claro?
7. ¿Qué cuenta como «pago pendiente» en el resumen: mensualidades vencidas, derechos de examen o ambos?
8. ¿Se asume WCAG 2.2 AA como requisito o hay otra norma que cumplir (federación, administración)?
