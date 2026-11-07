/**
 * Textos por defecto del tema «La línea comarcal»: los del boceto `fase4-web/boceto-linea-comarcal.html`, por clave
 * de `web_bloque` (fase4-web-plan.md §9, lista bajo la tabla). Si la escuela guarda la clave, manda la suya; si la
 * guarda vacía, no se pinta.
 *
 * Las claves de título de sección que la lista aún no fija (`presentacion.titulo`, `profesores.titulo`) siguen el
 * patrón de `disciplinas.titulo` y `eventos.titulo`; las fija W12.
 */
export const TEXTOS_LINEA_COMARCAL = {
  'menu.horarios': 'Horarios',
  'menu.sedes': 'Sedes',
  'menu.tarifas': 'Tarifas',
  'menu.eventos': 'Eventos',
  'menu.contacto': 'Contacto',
  'cabecera.boton': 'Preinscribirme',

  'portada.boton_horarios': 'Busca tu pueblo',

  'horarios.titulo': 'Horarios',
  'horarios.ver_sede': 'Ver la sede',
  'horarios.como_llegar': 'Cómo llegar',
  'horarios.lleno': 'Lleno',
  'horarios.espera': 'Lista de espera',
  'horarios.boton': 'Preinscribirme',
  'horarios.boton_espera': 'Apuntarme a la lista de espera',
  'sedes.proxima_rotulo': 'Próxima clase',

  'presentacion.titulo': 'Clases cerca de casa',

  'disciplinas.titulo': 'Disciplinas',

  'profesores.titulo': 'Quién da la clase',

  'eventos.titulo': 'Próximos eventos',
  'eventos.enlace_ver': 'Ver evento',

  'tarifas.titulo': 'Tarifas',
  'tarifas.punto1': 'Mensualidad por alumno',
  'tarifas.punto2': 'Licencia y exámenes aparte, se informan en la sede',
  'tarifas.boton_preinscripcion': 'Preinscribirme',

  'contacto.titulo': '¿Hablamos?',
  'contacto.correo_rotulo': 'Correo',
  'contacto.telefono_rotulo': 'Teléfono',
  'contacto.sedes_titulo': 'Sedes',

  'pie.aviso_legal': 'Aviso legal',
  'pie.privacidad': 'Privacidad',
  'pie.cookies': 'Cookies',
} as const;

export type ClaveTexto = keyof typeof TEXTOS_LINEA_COMARCAL;

/**
 * Textos del boceto que aún no tienen clave en la lista del plan (o que cambian con los datos): fijos del tema hasta
 * que W12 los haga editables. `horarios.apoyo` tiene dos por defecto (móvil y escritorio); si la escuela lo guarda,
 * vale para los dos.
 */
export const TEXTOS_FIJOS_LINEA_COMARCAL = {
  botonHorariosUnaSede: 'Ver horarios',
  enlaceTarifas: 'Ver tarifas',
  apoyoHorariosMovil: 'Toca tu pueblo para ver sus grupos',
  apoyoHorariosEscritorio: 'Pulsa en tu pueblo para ver sus grupos y horarios',
  rotuloPresentacion: 'El club',
  rotuloProfesores: 'Profesores',
  rotuloGaleria: 'Galería',
  rotuloEventos: 'Eventos',
  rotuloContacto: 'Contacto',
  alMes: 'al mes',
  igualEnTodasLasSedes: 'Igual en todas las sedes',
  igualUnaSede: 'Para todos los grupos',
  menu: 'Menú',
  cerrar: 'Cerrar',
  saltar: 'Saltar al contenido',
} as const;

/**
 * Textos fijos de las páginas de sede y de tarifas (bocetos `boceto-linea-comarcal-sede.html` y
 * `boceto-linea-comarcal-tarifas.html`): aún sin clave en la lista del plan; los hará editables W12. `{n}`, `{total}`,
 * `{x}`: se sustituyen con los datos.
 */
export const TEXTOS_PAGINAS_LINEA_COMARCAL = {
  // Sede
  anterior: 'Anterior',
  siguiente: 'Siguiente',
  unicaSede: 'Única sede',
  parada: 'Parada {n} de {total}',
  posicion: 'Posición en la línea',
  preinscribirmeEn: 'Preinscribirme en {x}',
  verGrupos: 'Ver grupos y horas',
  fotoSede: 'Foto de la sede de {x}',
  rotuloHorarios: 'Horarios',
  rotuloHorariosCurso: 'Horarios · curso {x}',
  tituloHorarios: 'Grupos y horas',
  sinClases: 'Sin clases esta semana',
  cabezaHora: 'Hora',
  cabezaGrupo: 'Grupo',
  cabezaDias: 'Días y plazas',
  gruposDe: 'Grupos de {x}',
  leyenda: 'Qué significa cada marca de plazas',
  leyendaQuedan: 'Quedan plazas',
  leyendaLleno: '{x}: puedes apuntarte a la lista de espera',
  leyendaEspera: 'Ya hay lista de espera',
  rotuloLlegar: 'Dirección y contacto',
  tituloLlegar: 'Cómo llegar',
  fichaLugar: 'Lugar',
  fichaEntrada: 'Entrada',
  fichaTelefono: 'Teléfono',
  abrirMapa: 'Abrir en el mapa',
  otraPestana: ' (abre en otra pestaña)',
  rotuloTarifaSede: 'Tarifa',
  rotuloRuta: 'La línea',
  tituloRuta: 'Todas las sedes',
  estasAqui: 'Estás aquí',
  // Tarifas
  cuotaUnaSede: 'Una cuota al mes, para todos los grupos.',
  cuotaIgual: 'Una cuota al mes, igual en las {x} sedes.',
  cuota: 'Una cuota al mes.',
  incluyeMensualidad: 'Mensualidad por alumno',
  incluyeTambien: 'También {x}',
  incluyeFamiliar: 'tarifa familiar',
  incluyeHermanos: 'de hermanos',
  incluyeAparte: 'Licencia y exámenes, aparte',
  verHorarios: 'Ver horarios',
  vacioTitular: 'Pregunta la cuota en tu sede',
  vacioTexto: 'Ahora no hay tarifas publicadas. Te la dicen en la sede o aquí:',
  vacioTextoSinContacto: 'Ahora no hay tarifas publicadas. Te la dicen en la sede.',
  correo: 'Correo',
  telefono: 'Teléfono',
  rotuloCuadro: 'Cuadro de tarifas',
  tituloCuadro: 'Cuánto se paga',
  leyendaCuadro: 'Mensualidades',
  leyendaCuadroCurso: 'Mensualidades del curso {x}',
  columnaTarifa: 'Tarifa',
  columnaQuien: 'Quién',
  columnaImporte: 'Importe',
  cadaAdulto: 'Cada adulto',
  cadaMenor: 'Cada menor',
  cadaAlumno: 'Cada alumno',
  hermanos: 'Desde el segundo hermano',
  pieCuadro: 'Se paga una mensualidad por alumno, la misma en todos los grupos.',
  rotuloPorSede: 'Por sede',
  tituloPorSede: 'Parada a parada',
  etiquetaPorSede: 'Mensualidad en cada sede',
  cambio: 'Desde {x}: {importe} al mes',
  leyendaCambio: 'Cambio de tarifa anunciado en esa sede',
  rotuloAparte: 'Aparte',
  tituloAparte: 'Licencia y exámenes',
  licencia: 'Licencia',
  examen: 'Derecho de examen',
  examenDesde: 'Según grado, desde {x}',
  notaAparte: 'No van en la mensualidad: se pagan cuando tocan.',
} as const;

/**
 * Textos de una disciplina en el boceto (es de taekwondo): solo valen por defecto para el código `taekwondo`. Otra
 * disciplina sin bloques no pinta descripción ni ficha.
 */
export const DISCIPLINA_TAEKWONDO = {
  descripcion:
    'Arte marcial coreano de patadas y golpes. En clase se calienta, se trabaja la técnica, las formas (poomsae) y el ' +
    'combate, y se acaba con ejercicios por parejas adaptados a cada edad.\n\n' +
    'Los cinturones se examinan en el propio club, cuando el profesor ve que el alumno está listo. La competición es ' +
    'opcional y se prepara aparte con quien quiera.',
  filas: [
    { rotulo: 'Edades', texto: 'Desde {edad} años, en grupos por edad', sinEdad: 'En grupos por edad' },
    { rotulo: 'Grados', texto: 'Cinturones de color hasta el negro, examinados en el club' },
    { rotulo: 'Competición', texto: 'Opcional, en campeonatos de la provincia' },
    { rotulo: 'Material', texto: 'Dobok (uniforme); protecciones para el combate' },
  ],
} as const;
