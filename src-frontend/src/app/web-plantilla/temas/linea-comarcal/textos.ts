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
