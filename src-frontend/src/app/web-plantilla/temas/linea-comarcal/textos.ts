/**
 * Textos por defecto del tema «La línea comarcal»: los del boceto `fase4-web/boceto-linea-comarcal.html`, por clave
 * de `web_bloque` (fase4-web-plan.md §9, lista bajo la tabla). Si la escuela guarda la clave, manda la suya; si la
 * guarda vacía, no se pinta.
 *
 * Los nombres de las claves son los de `ClavesWeb` (API, W12a). Los de «Fotos de las sedes» (`sedes.*`) son los del
 * boceto del editor (`boceto-panel-contenido-web-comarcal.html`, `TX` y `nuevoBloque`).
 */
export const TEXTOS_LINEA_COMARCAL = {
  'menu.horarios': 'Horarios',
  'menu.sedes': 'Sedes',
  'menu.tarifas': 'Tarifas',
  'menu.eventos': 'Eventos',
  'menu.contacto': 'Contacto',
  'cabecera.boton': 'Preinscribirme',

  'portada.boton_horarios': 'Busca tu pueblo',
  'portada.enlace_tarifas': 'Ver tarifas',

  'horarios.titulo': 'Horarios',
  'horarios.ver_sede': 'Ver la sede',
  'horarios.como_llegar': 'Cómo llegar',
  'horarios.lleno': 'Lleno',
  'horarios.espera': 'Lista de espera',
  'horarios.boton': 'Preinscribirme',
  'horarios.boton_espera': 'Apuntarme a la lista de espera',
  'sedes.proxima_rotulo': 'Próxima clase',

  'presentacion.rotulo': 'El club',
  'presentacion.titulo': 'Clases cerca de casa',

  'disciplinas.titulo': 'Disciplinas',

  'sedes.rotulo': 'Las sedes',
  'sedes.titulo': 'Un tatami en cada pueblo',
  'sedes.ver_sede': 'Ver la sede',

  'profesores.rotulo': 'Profesores',
  'profesores.titulo': 'Quién da la clase',

  'galeria.rotulo': 'Galería',

  'eventos.rotulo': 'Eventos',
  'eventos.titulo': 'Próximos eventos',
  'eventos.enlace_ver': 'Ver evento',

  'tarifas.titulo': 'Tarifas',
  'tarifas.al_mes': 'al mes',
  'tarifas.punto1': 'Mensualidad por alumno',
  'tarifas.punto2': 'Licencia y exámenes aparte, se informan en la sede',
  'tarifas.boton_preinscripcion': 'Preinscribirme',

  'contacto.rotulo': 'Contacto',
  'contacto.titulo': '¿Hablamos?',
  'contacto.correo_rotulo': 'Correo',
  'contacto.telefono_rotulo': 'Teléfono',
  'contacto.sedes_titulo': 'Sedes',

  'pie.aviso_legal': 'Aviso legal',
  'pie.privacidad': 'Privacidad',
  'pie.cookies': 'Cookies',

  // W12d-bis: el botón del menú en el móvil y los textos de las otras seis páginas (`ClavesWeb`, `TX` del boceto del
  // editor). Por defecto, el texto que el tema pintaba fijo (no el del boceto si difiere). `{x}`, `{n}`…: huecos que se
  // sustituyen con los datos después de leer la clave (`rellenarHuecos`).
  'cabecera.menu': 'Menú',

  // Una sede
  'sede.anterior': 'Anterior',
  'sede.siguiente': 'Siguiente',
  /** Ya no se usa en el tema: el `aria-label` del botón es automático, «<horarios.boton> en <sede>» (d544.3). Se conserva porque la API aún la declara. */
  'sede.preinscribirme_en': 'Preinscribirme en {x}',
  'sede.ver_grupos': 'Ver grupos y horas',
  /** Con temporada; sin ella, `TEXTOS_PAGINAS_LINEA_COMARCAL.rotuloHorarios` (variante, misma clave). */
  'sede.rotulo_horarios': 'Horarios · curso {x}',
  'sede.titulo_horarios': 'Grupos y horas',
  'sede.cabeza_hora': 'Hora',
  'sede.cabeza_grupo': 'Grupo',
  'sede.cabeza_dias': 'Días y plazas',
  'sede.leyenda_quedan': 'Quedan plazas',
  /** `{x}`: el texto de `horarios.lleno` (el que tenga la escuela). */
  'sede.leyenda_lleno': '{x}: puedes apuntarte a la lista de espera',
  'sede.leyenda_espera': 'Ya hay lista de espera',
  'sede.rotulo_llegar': 'Dirección y contacto',
  'sede.titulo_llegar': 'Cómo llegar',
  'sede.ficha_lugar': 'Lugar',
  'sede.ficha_entrada': 'Entrada',
  'sede.ficha_telefono': 'Teléfono',
  'sede.abrir_mapa': 'Abrir en el mapa',
  'sede.rotulo_tarifa': 'Tarifa',
  'sede.rotulo_ruta': 'La línea',
  'sede.titulo_ruta': 'Todas las sedes',
  'sede.estas_aqui': 'Estás aquí',
  'sede.sin_clases': 'Sin clases esta semana',

  // Tarifas (página)
  /** Varias sedes con la misma cuota; con una sede o cuotas distintas, otra variante (misma clave). */
  'tarifas.cuota_igual': 'Una cuota al mes, igual en las {x} sedes.',
  /** `{x}`: «tarifa familiar», «de hermanos» o las dos. */
  'tarifas.tambien': 'También {x}',
  'tarifas.incluye_aparte': 'Licencia y exámenes, aparte',
  'tarifas.ver_horarios': 'Ver horarios',
  'tarifas.rotulo_cuadro': 'Cuadro de tarifas',
  'tarifas.titulo_cuadro': 'Cuánto se paga',
  /** Con temporada; sin ella, `TEXTOS_PAGINAS_LINEA_COMARCAL.leyendaCuadro` (variante, misma clave). */
  'tarifas.leyenda_cuadro': 'Mensualidades del curso {x}',
  'tarifas.col_tarifa': 'Tarifa',
  'tarifas.col_quien': 'Quién',
  'tarifas.col_importe': 'Importe',
  /** También «Cada adulto» y «Cada menor» de la tarifa familiar (variantes, misma clave). */
  'tarifas.cada_alumno': 'Cada alumno',
  'tarifas.hermanos': 'Desde el segundo hermano',
  'tarifas.pie_cuadro': 'Se paga una mensualidad por alumno, la misma en todos los grupos.',
  'tarifas.rotulo_sede': 'Por sede',
  'tarifas.titulo_sede': 'Parada a parada',
  'tarifas.rotulo_aparte': 'Aparte',
  'tarifas.titulo_aparte': 'Licencia y exámenes',
  'tarifas.licencia': 'Licencia',
  'tarifas.examen': 'Derecho de examen',
  'tarifas.examen_desde': 'Según grado, desde {x}',
  'tarifas.nota_aparte': 'No van en la mensualidad: se pagan cuando tocan.',
  'tarifas.vacio_titular': 'Pregunta la cuota en tu sede',
  /** Con correo o teléfono; sin ninguno, `TEXTOS_PAGINAS_LINEA_COMARCAL.vacioTextoSinContacto` (variante, misma clave). */
  'tarifas.vacio_texto': 'Ahora no hay tarifas publicadas. Te la dicen en la sede o aquí:',
  'tarifas.vacio_correo': 'Correo',
  'tarifas.vacio_telefono': 'Teléfono',

  // Eventos
  'eventos.pag_titulo': 'Eventos',
  'eventos.pag_entrada': 'Exámenes, torneos y exhibiciones del club, con sus circulares y sus fotos.',
  'eventos.proximos': 'Próximos',
  'eventos.n_eventos': '{n} eventos',
  'eventos.un_evento': '1 evento',
  'eventos.anteriores': 'Anteriores',
  'eventos.sin_proximos_titular': 'No hay eventos próximos',
  'eventos.sin_proximos_texto': 'Cuando el club anuncie uno, aparecerá aquí.',
  'eventos.ver_anteriores': 'Ver los eventos anteriores',
  'eventos.sin_anteriores': 'Todavía no hay eventos anteriores.',

  // Un evento
  'evento.volver': 'Volver a eventos',
  'evento.documentos': 'Documentos',
  'evento.galeria': 'Galería',
  'evento.n_fotos': '{n} fotos',
  'evento.una_foto': '1 foto',

  // Legales (decisión del propietario, 12/11): solo los títulos, la entrada, los rótulos de la ficha y los párrafos del
  // tema iguales a los del boceto. El resto del texto legal sigue fijo en la plantilla. Sin sitio en el tema, y no se
  // pintan: `legal.priv.h1`, `legal.priv.p1`; más largos en el tema, siguen fijos: `legal.aviso.p3`, `legal.priv.p2`,
  // `legal.cookies.p0`, `legal.normas.p0`.
  'legal.titulo': 'Información legal',
  'legal.entrada': 'Quién está detrás de esta web, qué hacemos con tus datos, qué cookies usamos y las normas del club.',
  'legal.actualizada': 'Última actualización:',
  'legal.en_esta_pagina': 'En esta página',
  'legal.aviso': 'Aviso legal',
  'legal.aviso.p0':
    'En cumplimiento del artículo 10 de la Ley 34/2002, de servicios de la sociedad de la información y de comercio ' +
    'electrónico (LSSI), estos son los datos del titular de esta web.',
  'legal.aviso.h1': 'Para qué sirve esta web',
  'legal.aviso.p1':
    'Informa de las actividades, sedes, horarios y tarifas del club, y permite enviar una preinscripción. La ' +
    'preinscripción es una solicitud: el club la revisa y te contesta.',
  'legal.aviso.h2': 'Propiedad intelectual',
  /** `{x}`: el titular (nombre legal o, sin él, el de la escuela). */
  'legal.aviso.p2':
    'Los textos, fotos, logotipos y diseño de esta web son de {x} o de sus autores. No se pueden copiar ni usar con ' +
    'otros fines sin permiso.',
  'legal.aviso.h3': 'Responsabilidad',
  'legal.privacidad': 'Privacidad',
  'legal.priv.p0':
    'Tratamos tus datos según el Reglamento (UE) 2016/679, general de protección de datos (RGPD), y la Ley Orgánica ' +
    '3/2018, de protección de datos personales y garantía de los derechos digitales.',
  'legal.priv.h2': 'Tus derechos',
  'legal.cookies': 'Cookies',
  'legal.normas': 'Normas del club',
  'legal.ficha_titular': 'Titular',
  'legal.ficha_cif': 'CIF',
  'legal.ficha_domicilio': 'Domicilio',
  'legal.ficha_correo': 'Correo',

  // Preinscripción
  'pre.atras': 'Atrás',
  'pre.paso_n': 'Paso {n} de {total}',
  'pre.paso_pueblo': 'Pueblo',
  'pre.paso_grupo': 'Grupo',
  'pre.paso_datos': 'Datos',
  'pre.titulo_pueblo': '¿En qué pueblo?',
  'pre.pista_pueblo': 'Elige la sede donde quieres entrenar. Después verás sus grupos y horas.',
  'pre.continuar': 'Continuar',
  'pre.titulo_grupo': '¿Qué grupo?',
  'pre.cambiar_pueblo': 'Cambiar de pueblo',
  'pre.fecha_leyenda': 'Fecha de nacimiento del alumno',
  'pre.fecha_pista': 'Los grupos van por edad: cuenta los años que cumple este año. Por ejemplo, 14 3 2016.',
  'pre.dia': 'Día',
  'pre.mes': 'Mes',
  'pre.anio': 'Año',
  'pre.ver_grupos': 'Ver sus grupos',
  'pre.espera_fecha': 'Escribe su fecha de nacimiento y aquí verás los grupos de {x} para su edad.',
  'pre.titulo_datos': 'Datos y firma',
  'pre.pista_datos':
    'Te pedimos lo mismo que en la hoja de inscripción del club. Todo es obligatorio salvo lo que pone «opcional».',
  'pre.bloque_alumno': 'El alumno o la alumna',
  'pre.nombre': 'Nombre',
  'pre.apellidos': 'Apellidos',
  'pre.bloque_contacto': 'Contacto',
  'pre.telefono': 'Teléfono',
  'pre.email': 'Email',
  'pre.email_pista': 'Te llegará aquí la solicitud firmada en PDF.',
  'pre.bloque_normas': 'Normas y consentimientos',
  'pre.acepto': 'He leído y acepto las normas de la actividad',
  'pre.fotos': 'Autorizo el uso de imágenes en actividades del club',
  'pre.bloque_firma': 'Firma',
  'pre.firma_pista': 'Con el dedo, el ratón o un lápiz.',
  'pre.enviar': 'Enviar preinscripción',
  'pre.fin_plaza': 'Preinscripción enviada',
  'pre.referencia': 'Referencia',
  'pre.estado_plaza': 'Tu preinscripción ya cuenta en las plazas de {grupo} en {sede}.',
  'pre.que_ahora': 'Qué pasa ahora',
  'pre.paso_correo': 'Te llega un correo',
  'pre.paso_correo_texto':
    'Con la solicitud firmada en PDF y las instrucciones del club. Mira también en correo no deseado.',
  'pre.paso_revisa': 'El club la revisa',
  'pre.paso_revisa_texto': 'Si falta algo, te llaman al {x}.',
  'pre.descargar': 'Descargar solicitud firmada',
  'pre.volver_inicio': 'Volver al inicio',
  'pre.resumen_titulo': 'Tu preinscripción',
  'pre.resumen_pueblo': 'Pueblo',
  'pre.resumen_grupo': 'Grupo',
  'pre.resumen_dias': 'Días y hora',
  'pre.sin_elegir': 'Sin elegir',
  /** Varias sedes; con una, `TEXTOS_PREINSCRIPCION_LINEA_COMARCAL.tarifaUna` (variante, misma clave). */
  'pre.tarifa_igual': '{x} al mes, igual en todas las sedes.',
  'pre.vacio_titulo': 'En {x} no hay grupo para {n} años',
  'pre.vacio_otras': 'Estas sedes sí tienen grupo para su edad. Al elegir una, mantenemos la fecha.',
  'pre.vacio_llamar': '¿Prefieres {x} o tienes dudas? Llama a la sede al',
  'pre.vacio_llamar_fin': 'y te orientamos.',
  'pre.revisar_fecha': 'Revisar la fecha de nacimiento',
  'pre.espera_titulo': 'Este grupo está completo: te apuntas a la lista de espera.',
  'pre.espera_texto': 'No es una plaza. Te avisaremos cuando haya una libre en {grupo} para {dias}.',
  'pre.enviar_espera': 'Enviar y apuntarme a la lista de espera',
  'pre.fin_espera': 'Estás en la lista de espera',
  /** Con posición; sin ella, `TEXTOS_PREINSCRIPCION_LINEA_COMARCAL.estadoEspera` (variante, misma clave). */
  'pre.estado_espera':
    'Todavía no tienes plaza. Estás en la posición {n} de la lista de espera de {grupo} en {sede}, para {dias}.',
  'pre.paso_correo_espera':
    'Con la solicitud firmada en PDF y tu posición en la lista. Mira también en correo no deseado.',
} as const;

export type ClaveTexto = keyof typeof TEXTOS_LINEA_COMARCAL;

/**
 * Textos del boceto sin clave propia (o con un valor por defecto que cambia con los datos). `horarios.apoyo` tiene dos
 * por defecto (móvil y escritorio); si la escuela lo guarda, vale para los dos. `tarifas.igual` cambia con una o varias
 * sedes; si la escuela lo guarda, vale para las dos.
 */
export const TEXTOS_FIJOS_LINEA_COMARCAL = {
  botonHorariosUnaSede: 'Ver horarios',
  apoyoHorariosMovil: 'Toca tu pueblo para ver sus grupos',
  apoyoHorariosEscritorio: 'Pulsa en tu pueblo para ver sus grupos y horarios',
  igualEnTodasLasSedes: 'Igual en todas las sedes',
  igualUnaSede: 'Para todos los grupos',
  cerrar: 'Cerrar',
  saltar: 'Saltar al contenido',
} as const;

/**
 * Textos fijos de las páginas de sede, tarifas, eventos, evento y legales (bocetos `boceto-linea-comarcal-sede.html`,
 * `-tarifas.html`, `-eventos.html`, `-evento.html` y `-legales.html`) que no tienen clave en `ClavesWeb`, y los valores por
 * defecto de las variantes que dependen de los datos (W12d-bis: si la escuela guarda la clave, su texto vale para todas
 * las variantes). Los que tienen clave están en `TEXTOS_LINEA_COMARCAL`. `{n}`, `{total}`, `{x}`: se sustituyen con los
 * datos. El texto legal sin clave va en la plantilla de su página.
 */
export const TEXTOS_PAGINAS_LINEA_COMARCAL = {
  // Sede
  /** El visor de fotos de un evento (sin clave: `sede.anterior` y `sede.siguiente` son los del cartel de la sede). */
  anterior: 'Anterior',
  siguiente: 'Siguiente',
  unicaSede: 'Única sede',
  parada: 'Parada {n} de {total}',
  posicion: 'Posición en la línea',
  fotoSede: 'Foto de la sede de {x}',
  /** Variante de `sede.rotulo_horarios` sin temporada. */
  rotuloHorarios: 'Horarios',
  gruposDe: 'Grupos de {x}',
  leyenda: 'Qué significa cada marca de plazas',
  otraPestana: ' (abre en otra pestaña)',
  // Tarifas
  /** Variantes de `tarifas.cuota_igual`: una sede; varias con cuotas distintas. */
  cuotaUnaSede: 'Una cuota al mes, para todos los grupos.',
  cuota: 'Una cuota al mes.',
  incluyeMensualidad: 'Mensualidad por alumno',
  incluyeFamiliar: 'tarifa familiar',
  incluyeHermanos: 'de hermanos',
  /** Variante de `tarifas.vacio_texto` sin correo ni teléfono. */
  vacioTextoSinContacto: 'Ahora no hay tarifas publicadas. Te la dicen en la sede.',
  /** Variante de `tarifas.leyenda_cuadro` sin temporada. */
  leyendaCuadro: 'Mensualidades',
  /** Variantes de `tarifas.cada_alumno` en la tarifa familiar. */
  cadaAdulto: 'Cada adulto',
  cadaMenor: 'Cada menor',
  etiquetaPorSede: 'Mensualidad en cada sede',
  cambio: 'Desde {x}: {importe} al mes',
  leyendaCambio: 'Cambio de tarifa anunciado en esa sede',
  // Eventos
  verEvento: 'Ver evento',
  masProximos: 'Más eventos próximos',
  // Evento
  ampliarFoto: 'Ampliar la foto {n} de {total}',
  fotoN: 'Foto {n} de {total}',
  de: 'de {total}',
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

/**
 * Textos fijos de la preinscripción (boceto `boceto-linea-comarcal-preinscripcion.html`, con la firma de la decisión
 * 493: dibujada y obligatoria) que no tienen clave en `ClavesWeb`, y los valores por defecto de las variantes que
 * dependen de los datos (W12d-bis: si la escuela guarda la clave, su texto vale para todas). Los que tienen clave
 * (`pre.*`) están en `TEXTOS_LINEA_COMARCAL`. `{x}`, `{n}`…: se sustituyen con los datos. Los mensajes de error son los
 * de la lógica común (`comun/preinscripcion`).
 */
export const TEXTOS_PREINSCRIPCION_LINEA_COMARCAL = {
  // Resumen lateral
  resumenEdad: 'Edad',
  resumenPlaza: 'Plaza',
  cambiar: 'Cambiar',
  cambiarPueblo: 'el pueblo',
  cambiarGrupo: 'el grupo',
  cambiarDias: 'los días',
  cumpleAnios: 'Cumple {n} años este año',
  plazaEspera: 'Lista de espera: no hay plaza ahora',
  /** Variante de `pre.tarifa_igual` con una sede. */
  tarifaUna: '{x} al mes, para todos los grupos.',
  // Paso 1
  elegida: 'Elegida',
  unGrupo: '1 grupo',
  nGrupos: '{n} grupos',
  proximaHoy: 'próxima clase hoy a las',
  proximaDia: 'próxima clase {x} a las',
  // Paso 2
  en: 'En',
  cumpleEsteAnio: 'Este año cumple',
  anios: '{n} años',
  hayUnGrupo: ': hay 1 grupo de su edad en {x}.',
  hayNGrupos: ': hay {n} grupos de su edad en {x}.',
  gruposPara: 'Grupos de {x} para {n} años',
  hoy: 'HOY',
  elegido: 'Elegido',
  elegirGrupo: 'Elegir este grupo',
  apuntarmeEspera: 'Apuntarme a la lista de espera',
  notaEspera:
    'Ahora no hay plaza. Si te apuntas, entras en la lista de espera de los días que elijas y te avisamos cuando se libere una.',
  queDias: '¿Qué días vendrá?',
  queDiasEspera: '¿Para qué días te apuntas a la lista?',
  diasPista: 'Vienen marcados todos los días del grupo. Desmarca los que no pueda venir; tiene que quedar al menos uno.',
  /** Grupo con plaza solo algunos días (§4.3, por día): los llenos no se marcan ni se eligen. */
  diasPistaConPlaza: 'Vienen marcados los días con plaza. Desmarca los que no pueda venir; tiene que quedar al menos uno.',
  diaUnico: 'Este grupo entrena un día a la semana:',
  elDia: 'el {x}',
  soloEseDia: 'Solo ese día; el grupo también entrena el {x}.',
  soloEsosDias: 'Solo esos días; el grupo también entrena el {x}.',
  vacioMinimo: 'Los grupos del club empiezan a los {min} años; ninguna sede tiene uno para {n}.',
  vacioNinguna: 'Ninguna sede tiene ahora un grupo para {n} años.',
  // Paso 3
  cambiarGrupoDias: ' el grupo o los días',
  fechaNacimiento: 'Fecha de nacimiento',
  fechaPistaDatos: 'La que escribiste al elegir grupo. Si la corriges, comprobamos que el grupo sigue siendo de su edad.',
  avisoEdad: 'Con esta fecha cumple {n} años este año y {grupo} es de {edades}.',
  avisoEdadEnlace: 'Elegir un grupo de su edad',
  dni: 'DNI o NIE',
  opcionalMenores: '(opcional para menores)',
  dniPista: 'Ocho números y una letra, sin espacios ni guiones.',
  direccion: 'Dirección',
  discapacidad: '¿Tiene discapacidad?',
  si: 'Sí',
  no: 'No',
  bloqueTutor: 'Responsable legal',
  pistaTutor: 'Porque el alumno es menor de edad. Es quien firma la solicitud.',
  tutorNombre: 'Nombre y apellidos',
  pistaContacto: 'Para enviarte la solicitud y avisarte.',
  pistaContactoEspera: 'Para enviarte la solicitud y avisarte cuando haya plaza.',
  telefonoPista: 'Nueve cifras; puedes añadir el prefijo +34.',
  telefono2: 'Teléfono secundario',
  opcional: '(opcional)',
  bloqueHorario: 'Sobre el horario',
  observaciones: 'Observaciones sobre el horario',
  observacionesPista:
    '¿Crees que otro grupo puede encajar mejor por edad, experiencia o recomendación del profesor? Cuéntanoslo y lo revisamos.',
  normasPista: 'Son las normas de la actividad en el club. Puedes leerlas aquí antes de aceptarlas.',
  leerNormas: 'Leer las normas ({n} puntos)',
  firmaTutor: 'Firma el responsable legal.',
  firmaAlumno: 'Firma el alumno o la alumna.',
  firmante: 'Nombre y apellidos de quien firma',
  firma: 'Firma',
  sinFirmar: 'Sin firmar',
  firmado: 'Firmado',
  borrarFirma: 'Borrar firma',
  enviando: 'Enviando…',
  /** Vista previa del panel: el formulario se usa, pero no se envía. */
  vistaPreviaNoEnvia: 'Es una vista previa: la preinscripción no se envía.',
  // Errores
  error: 'Error: ',
  revisaUno: 'Revisa este dato',
  revisaN: 'Revisa estos {n} datos',
  // Paso 4
  /** Variante de `pre.estado_espera` sin posición en la lista. */
  estadoEspera: 'Todavía no tienes plaza. Estás en la lista de espera de {grupo} en {sede}, para {dias}.',
  pasoPrimer: 'Al primer entrenamiento',
  pasoPrimerTexto: '{horario}, en {lugar}. Ropa cómoda y agua',
  pasoPrimerDobok: '; el dobok se pide en el club.',
  pasoEsperas: 'Esperas turno',
  pasoEsperasTexto: 'No hace falta que hagas nada. Si cambias de idea, llama a la sede y te quitamos de la lista.',
  pasoAviso: 'Te avisamos si hay plaza',
  pasoAvisoTexto: 'Cuando se libere una en {grupo} para esos días, te avisamos por correo. Hasta entonces no tienes plaza.',
  resumen: 'Resumen',
  fichaEstado: 'Estado',
  fichaEstadoPlaza: 'Con plaza en el grupo',
  fichaEstadoEspera: 'Lista de espera, sin plaza',
  fichaPueblo: 'Pueblo',
  fichaGrupo: 'Grupo',
  fichaDias: 'Días y hora',
  fichaAlumno: 'Alumno',
  fichaResponsable: 'Responsable',
  fichaContacto: 'Contacto',
} as const;
