import { TEXTOS_LINEA_COMARCAL, TEXTOS_PREINSCRIPCION_LINEA_COMARCAL } from '../temas/linea-comarcal/textos';

/**
 * Textos que se cambian en el sitio en «Web · Contenido» (W12d; boceto `boceto-panel-contenido-web-comarcal.html`, `TX`,
 * `CAMPOS`, `campoDef`, `nombreError`). Lógica pura que comparten la capa de edición (dentro del iframe) y el panel.
 *
 * Solo las claves que el tema «La línea comarcal» pinta desde `web_bloque`: las de Inicio, la cabecera y el pie y, desde
 * W12d-bis, las de las otras seis páginas (`textosDePaginas`, `camposTextoPagina`).
 */

export interface DefTexto {
  /** El nombre del chip y de la etiqueta («Titular», «Enlace del menú»). */
  nombre: string;
  max: number;
  obligatorio: boolean;
  /** Varias líneas: Intro hace un salto de línea en lugar de terminar. */
  multi: boolean;
  /** Lo que se ve cuando está vacío. */
  vacio: string;
  /** El bloque o la parte donde sale, para el resumen de errores (`nombreBloque` del boceto). */
  parte: string;
  /** El error si falta (obligatorio y vacío). */
  falta: string;
  /** El texto del tema que se cita en el resumen de errores (`TX[k][5]` del boceto); solo en los fijos del tema. */
  ejemplo?: string;
}

type Fila = readonly [parte: string, nombre: string, max: number, obligatorio: boolean, ejemplo: string];

/** `TX` del boceto (Inicio, cabecera y pie): [parte, nombre, máximo, obligatorio, texto del tema]. Ninguno es de varias líneas salvo los créditos. */
const TX: Readonly<Record<string, Fila>> = {
  'menu.horarios': ['cabecera', 'Enlace del menú', 20, true, 'Horarios'],
  'menu.sedes': ['cabecera', 'Enlace del menú', 20, true, 'Sedes'],
  'menu.tarifas': ['cabecera', 'Enlace del menú', 20, true, 'Tarifas'],
  'menu.eventos': ['cabecera', 'Enlace del menú', 20, true, 'Eventos'],
  'menu.contacto': ['cabecera', 'Enlace del menú', 20, true, 'Contacto'],
  'cabecera.boton': ['cabecera', 'Botón', 24, true, 'Preinscribirme'],
  'portada.boton_horarios': ['portada', 'Botón', 24, true, 'Busca tu pueblo'],
  'portada.enlace_tarifas': ['portada', 'Enlace', 24, true, 'Ver tarifas'],
  'horarios.titulo': ['horarios', 'Rótulo', 24, true, 'Horarios'],
  'horarios.apoyo': ['horarios', 'Pista', 120, false, 'Pulsa en tu pueblo para ver sus grupos y horarios'],
  'disciplinas.titulo': ['disciplinas', 'Rótulo', 24, true, 'Disciplinas'],
  'sedes.ver_sede': ['sedes', 'Enlace', 24, true, 'Ver la sede'],
  'eventos.rotulo': ['eventos', 'Rótulo', 24, true, 'Eventos'],
  'eventos.titulo': ['eventos', 'Título', 60, true, 'Próximos eventos'],
  'eventos.enlace_ver': ['eventos', 'Enlace', 24, true, 'Ver evento'],
  'tarifas.titulo': ['tarifas', 'Rótulo', 24, true, 'Tarifas'],
  'tarifas.al_mes': ['tarifas', 'Detrás del importe', 16, true, 'al mes'],
  'tarifas.igual': ['tarifas', 'Frase grande', 40, false, 'Igual en todas las sedes'],
  'tarifas.punto1': ['tarifas', 'Punto de la lista', 80, false, 'Mensualidad por alumno'],
  'tarifas.punto2': ['tarifas', 'Punto de la lista', 80, false, 'Licencia y exámenes aparte, se informan en la sede'],
  'tarifas.boton_preinscripcion': ['tarifas', 'Botón', 24, true, 'Preinscribirme'],
  'contacto.rotulo': ['contacto', 'Rótulo', 24, true, 'Contacto'],
  'contacto.titulo': ['contacto', 'Título', 60, true, '¿Hablamos?'],
  'contacto.correo_rotulo': ['contacto', 'Rótulo', 24, true, 'Correo'],
  'contacto.telefono_rotulo': ['contacto', 'Rótulo', 24, true, 'Teléfono'],
  'contacto.sedes_titulo': ['contacto', 'Título de la lista', 40, true, 'Sedes'],
  'pie.aviso_legal': ['pie', 'Enlace', 24, true, 'Aviso legal'],
  'pie.privacidad': ['pie', 'Enlace', 24, true, 'Privacidad'],
  'pie.cookies': ['pie', 'Enlace', 24, true, 'Cookies'],
  'pie.creditos': ['pie', 'Créditos de las fotos', 300, false, 'Fotos: Camila Sanabria (Unsplash)'],
  ...textosDePaginas(),
};

/**
 * W12d-bis: los textos de las otras seis páginas y el botón del menú en el móvil (`TX` del boceto, partes `sp-`, `tp-`,
 * `ep-`, `ev-`, `lg-` y `pr-`), con el máximo y la obligatoriedad de `ClavesWeb`. El texto de ejemplo es el del tema.
 * Fuera, por decisión del propietario (12/11): `sede.preinscribirme_en` (solo da el `aria-label` del botón) y los textos
 * legales que el tema no lee (`legal.priv.h1`, `legal.priv.p1`, `legal.aviso.p3`, `legal.priv.p2`, `legal.cookies.p0`,
 * `legal.normas.p0`): ese texto sigue fijo. Los de los grupos de una sede (`horarios.lleno`…, `sedes.proxima_rotulo`)
 * solo se cambian en «Una sede» (en Inicio van dentro del horario plegado): su parte es la de esa página.
 */
function textosDePaginas(): Record<string, Fila> {
  const filas: [clave: string, parte: string, nombre: string, max: number, obligatorio: boolean][] = [
    ['cabecera.menu', 'cabecera', 'Botón del menú en el móvil', 12, true],
    // Una sede
    ['sede.anterior', 'sp-cartel', 'Rótulo de la parada anterior', 16, true],
    ['sede.siguiente', 'sp-cartel', 'Rótulo de la parada siguiente', 16, true],
    ['sede.ver_grupos', 'sp-cartel', 'Enlace', 24, true],
    ['sede.rotulo_horarios', 'sp-horarios', 'Rótulo', 40, true],
    ['sede.titulo_horarios', 'sp-horarios', 'Título', 60, true],
    ['sedes.proxima_rotulo', 'sp-horarios', 'Rótulo de la próxima clase', 24, false],
    ['sede.sin_clases', 'sp-horarios', 'Aviso sin próxima clase', 40, true],
    ['sede.cabeza_hora', 'sp-horarios', 'Cabecera de columna', 20, true],
    ['sede.cabeza_grupo', 'sp-horarios', 'Cabecera de columna', 20, true],
    ['sede.cabeza_dias', 'sp-horarios', 'Cabecera de columna', 20, true],
    ['horarios.lleno', 'sp-horarios', 'Aviso de grupo lleno', 24, true],
    ['horarios.espera', 'sp-horarios', 'Aviso de lista de espera', 24, true],
    ['horarios.boton', 'sp-horarios', 'Botón', 24, true],
    ['horarios.boton_espera', 'sp-horarios', 'Botón de la lista de espera', 32, true],
    ['sede.leyenda_quedan', 'sp-horarios', 'Leyenda', 60, true],
    ['sede.leyenda_lleno', 'sp-horarios', 'Leyenda', 60, true],
    ['sede.leyenda_espera', 'sp-horarios', 'Leyenda', 60, true],
    ['sede.rotulo_llegar', 'sp-llegar', 'Rótulo', 40, true],
    ['sede.titulo_llegar', 'sp-llegar', 'Título', 60, true],
    ['sede.ficha_lugar', 'sp-llegar', 'Rótulo de la fila', 24, true],
    ['sede.ficha_entrada', 'sp-llegar', 'Rótulo de la fila', 24, true],
    ['sede.ficha_telefono', 'sp-llegar', 'Rótulo de la fila', 24, true],
    ['sede.abrir_mapa', 'sp-llegar', 'Botón', 24, true],
    ['sede.rotulo_tarifa', 'sp-tarifa', 'Rótulo', 24, true],
    ['sede.rotulo_ruta', 'sp-ruta', 'Rótulo', 24, true],
    ['sede.titulo_ruta', 'sp-ruta', 'Título', 60, true],
    ['sede.estas_aqui', 'sp-ruta', 'Marca de la sede actual', 16, true],
    // Tarifas
    ['tarifas.cuota_igual', 'tp-banda', 'Frase de la cuota', 80, true],
    ['tarifas.tambien', 'tp-banda', 'Punto de otras tarifas', 40, true],
    ['tarifas.incluye_aparte', 'tp-banda', 'Punto de la lista', 60, true],
    ['tarifas.ver_horarios', 'tp-banda', 'Enlace', 24, true],
    ['tarifas.vacio_titular', 'tp-banda', 'Titular sin tarifas', 40, true],
    ['tarifas.vacio_texto', 'tp-banda', 'Texto sin tarifas', 160, true],
    ['tarifas.vacio_correo', 'tp-banda', 'Rótulo de la fila', 24, true],
    ['tarifas.vacio_telefono', 'tp-banda', 'Rótulo de la fila', 24, true],
    ['tarifas.rotulo_cuadro', 'tp-cuadro', 'Rótulo', 24, true],
    ['tarifas.titulo_cuadro', 'tp-cuadro', 'Título', 60, true],
    ['tarifas.leyenda_cuadro', 'tp-cuadro', 'Rótulo de la tabla', 48, true],
    ['tarifas.col_tarifa', 'tp-cuadro', 'Cabecera de columna', 20, true],
    ['tarifas.col_quien', 'tp-cuadro', 'Cabecera de columna', 20, true],
    ['tarifas.col_importe', 'tp-cuadro', 'Cabecera de columna', 20, true],
    ['tarifas.cada_alumno', 'tp-cuadro', 'Quién paga', 40, true],
    ['tarifas.hermanos', 'tp-cuadro', 'Quién paga', 40, true],
    ['tarifas.pie_cuadro', 'tp-cuadro', 'Nota de la tabla', 160, false],
    ['tarifas.rotulo_sede', 'tp-sede', 'Rótulo', 24, true],
    ['tarifas.titulo_sede', 'tp-sede', 'Título', 60, true],
    ['tarifas.rotulo_aparte', 'tp-aparte', 'Rótulo', 24, true],
    ['tarifas.titulo_aparte', 'tp-aparte', 'Título', 60, true],
    ['tarifas.licencia', 'tp-aparte', 'Concepto', 32, true],
    ['tarifas.examen', 'tp-aparte', 'Concepto', 32, true],
    ['tarifas.examen_desde', 'tp-aparte', 'Precio del examen', 40, true],
    ['tarifas.nota_aparte', 'tp-aparte', 'Nota', 160, false],
    // Eventos
    ['eventos.pag_titulo', 'ep-cabeza', 'Título de la página', 40, true],
    ['eventos.pag_entrada', 'ep-cabeza', 'Entrada', 160, false],
    ['eventos.proximos', 'ep-proximos', 'Rótulo', 24, true],
    ['eventos.n_eventos', 'ep-proximos', 'Cuenta', 24, true],
    ['eventos.un_evento', 'ep-proximos', 'Cuenta con un evento', 24, true],
    ['eventos.sin_proximos_titular', 'ep-proximos', 'Titular sin eventos', 40, true],
    ['eventos.sin_proximos_texto', 'ep-proximos', 'Texto sin eventos', 160, false],
    ['eventos.ver_anteriores', 'ep-proximos', 'Enlace', 40, true],
    ['eventos.anteriores', 'ep-anteriores', 'Rótulo', 24, true],
    ['eventos.sin_anteriores', 'ep-anteriores', 'Texto sin anteriores', 120, true],
    // Un evento
    ['evento.volver', 'ev-cabeza', 'Enlace', 24, true],
    ['evento.documentos', 'ev-cuerpo', 'Rótulo', 24, true],
    ['evento.galeria', 'ev-galeria', 'Rótulo', 24, true],
    ['evento.n_fotos', 'ev-galeria', 'Cuenta', 24, true],
    ['evento.una_foto', 'ev-galeria', 'Cuenta con una foto', 24, true],
    // Legales: solo los 20 que el tema lee
    ['legal.titulo', 'lg-cabeza', 'Título de la página', 40, true],
    ['legal.entrada', 'lg-cabeza', 'Entrada', 200, false],
    ['legal.actualizada', 'lg-cabeza', 'Rótulo de la fecha', 30, true],
    ['legal.en_esta_pagina', 'lg-texto', 'Rótulo del índice', 24, true],
    ['legal.aviso', 'lg-texto', 'Título de la sección', 40, true],
    ['legal.aviso.p0', 'lg-texto', 'Párrafo', 1200, false],
    ['legal.aviso.h1', 'lg-texto', 'Subtítulo', 80, true],
    ['legal.aviso.p1', 'lg-texto', 'Párrafo', 1200, false],
    ['legal.aviso.h2', 'lg-texto', 'Subtítulo', 80, true],
    ['legal.aviso.p2', 'lg-texto', 'Párrafo', 1200, false],
    ['legal.aviso.h3', 'lg-texto', 'Subtítulo', 80, true],
    ['legal.privacidad', 'lg-texto', 'Título de la sección', 40, true],
    ['legal.priv.p0', 'lg-texto', 'Párrafo', 1200, false],
    ['legal.priv.h2', 'lg-texto', 'Subtítulo', 80, true],
    ['legal.cookies', 'lg-texto', 'Título de la sección', 40, true],
    ['legal.normas', 'lg-texto', 'Título de la sección', 40, true],
    ['legal.ficha_titular', 'lg-texto', 'Rótulo de la fila', 24, true],
    ['legal.ficha_cif', 'lg-texto', 'Rótulo de la fila', 24, true],
    ['legal.ficha_domicilio', 'lg-texto', 'Rótulo de la fila', 24, true],
    ['legal.ficha_correo', 'lg-texto', 'Rótulo de la fila', 24, true],
    // Preinscripción
    ['pre.atras', 'pr-flujo', 'Enlace', 16, true],
    ['pre.paso_n', 'pr-flujo', 'Rótulo del paso', 24, true],
    ['pre.paso_pueblo', 'pr-flujo', 'Nombre del paso', 16, true],
    ['pre.paso_grupo', 'pr-flujo', 'Nombre del paso', 16, true],
    ['pre.paso_datos', 'pr-flujo', 'Nombre del paso', 16, true],
    ['pre.titulo_pueblo', 'pr-flujo', 'Pregunta', 40, true],
    ['pre.pista_pueblo', 'pr-flujo', 'Pista', 160, false],
    ['pre.continuar', 'pr-flujo', 'Botón', 24, true],
    ['pre.titulo_grupo', 'pr-flujo', 'Pregunta', 40, true],
    ['pre.cambiar_pueblo', 'pr-flujo', 'Enlace', 24, true],
    ['pre.fecha_leyenda', 'pr-flujo', 'Título del campo', 60, true],
    ['pre.fecha_pista', 'pr-flujo', 'Pista', 160, false],
    ['pre.dia', 'pr-flujo', 'Etiqueta', 12, true],
    ['pre.mes', 'pr-flujo', 'Etiqueta', 12, true],
    ['pre.anio', 'pr-flujo', 'Etiqueta', 12, true],
    ['pre.ver_grupos', 'pr-flujo', 'Botón', 24, true],
    ['pre.espera_fecha', 'pr-flujo', 'Texto de espera', 120, false],
    ['pre.vacio_titulo', 'pr-flujo', 'Titular sin grupo', 60, true],
    ['pre.vacio_otras', 'pr-flujo', 'Texto sin grupo', 160, false],
    ['pre.vacio_llamar', 'pr-flujo', 'Frase del teléfono', 80, true],
    ['pre.vacio_llamar_fin', 'pr-flujo', 'Final de la frase', 40, true],
    ['pre.revisar_fecha', 'pr-flujo', 'Enlace', 40, true],
    ['pre.titulo_datos', 'pr-flujo', 'Pregunta', 40, true],
    ['pre.espera_titulo', 'pr-flujo', 'Aviso de lista de espera', 80, true],
    ['pre.espera_texto', 'pr-flujo', 'Texto del aviso', 160, false],
    ['pre.pista_datos', 'pr-flujo', 'Pista', 200, false],
    ['pre.bloque_alumno', 'pr-flujo', 'Título del bloque', 40, true],
    ['pre.nombre', 'pr-flujo', 'Etiqueta', 24, true],
    ['pre.apellidos', 'pr-flujo', 'Etiqueta', 24, true],
    ['pre.bloque_contacto', 'pr-flujo', 'Título del bloque', 40, true],
    ['pre.telefono', 'pr-flujo', 'Etiqueta', 24, true],
    ['pre.email', 'pr-flujo', 'Etiqueta', 24, true],
    ['pre.email_pista', 'pr-flujo', 'Pista', 120, false],
    ['pre.bloque_normas', 'pr-flujo', 'Título del bloque', 40, true],
    ['pre.acepto', 'pr-flujo', 'Casilla', 80, true],
    ['pre.fotos', 'pr-flujo', 'Casilla', 80, true],
    ['pre.bloque_firma', 'pr-flujo', 'Título del bloque', 40, true],
    ['pre.firma_pista', 'pr-flujo', 'Pista', 80, false],
    ['pre.enviar', 'pr-flujo', 'Botón', 32, true],
    ['pre.enviar_espera', 'pr-flujo', 'Botón', 48, true],
    ['pre.fin_plaza', 'pr-flujo', 'Titular', 40, true],
    ['pre.fin_espera', 'pr-flujo', 'Titular', 40, true],
    ['pre.referencia', 'pr-flujo', 'Rótulo', 24, true],
    ['pre.estado_plaza', 'pr-flujo', 'Frase', 160, true],
    ['pre.estado_espera', 'pr-flujo', 'Frase', 160, true],
    ['pre.que_ahora', 'pr-flujo', 'Título', 40, true],
    ['pre.paso_correo', 'pr-flujo', 'Paso', 40, true],
    ['pre.paso_correo_texto', 'pr-flujo', 'Texto del paso', 200, false],
    ['pre.paso_correo_espera', 'pr-flujo', 'Texto del paso', 200, false],
    ['pre.paso_revisa', 'pr-flujo', 'Paso', 40, true],
    ['pre.paso_revisa_texto', 'pr-flujo', 'Texto del paso', 200, false],
    ['pre.descargar', 'pr-flujo', 'Botón', 40, true],
    ['pre.volver_inicio', 'pr-flujo', 'Enlace', 24, true],
    ['pre.resumen_titulo', 'pr-flujo', 'Título del resumen', 32, true],
    ['pre.resumen_pueblo', 'pr-flujo', 'Rótulo', 16, true],
    ['pre.resumen_grupo', 'pr-flujo', 'Rótulo', 16, true],
    ['pre.resumen_dias', 'pr-flujo', 'Rótulo', 16, true],
    ['pre.sin_elegir', 'pr-flujo', 'Texto sin elegir', 24, true],
    ['pre.tarifa_igual', 'pr-flujo', 'Nota de la tarifa', 80, false],
  ];
  const tema = TEXTOS_LINEA_COMARCAL as Readonly<Record<string, string>>;
  return Object.fromEntries(filas.map(([clave, parte, nombre, max, obligatorio]) => [clave, [parte, nombre, max, obligatorio, tema[clave] ?? '']]));
}

/** Los párrafos de Legales y los créditos: Intro hace un salto de línea (una línea en blanco separa párrafos). */
const VARIAS_LINEAS = new Set(['pie.creditos', 'legal.aviso.p0', 'legal.aviso.p1', 'legal.aviso.p2', 'legal.priv.p0']);

/** `TIPO_N` y `PARTE_N` del boceto, con los ids de la capa. */
export const NOMBRE_PARTE: Readonly<Record<string, string>> = {
  cabecera: 'Cabecera',
  pie: 'Pie',
  portada: 'Portada',
  horarios: 'Horarios (la línea)',
  presentacion: 'Presentación',
  disciplinas: 'Disciplinas',
  sedes: 'Fotos de las sedes',
  profesores: 'Profesores',
  galeria: 'Galería',
  texto_foto: 'Texto con foto',
  eventos: 'Próximos eventos',
  tarifas: 'Tarifas',
  contacto: 'Contacto',
  // W12d-bis: partes de las otras seis páginas (`PARTE_N` del boceto)
  'sp-cartel': 'Cartel de la sede',
  'sp-horarios': 'Grupos y horas',
  'sp-llegar': 'Cómo llegar',
  'sp-tarifa': 'Tarifa',
  'sp-ruta': 'Todas las sedes',
  'tp-banda': 'Cuota',
  'tp-cuadro': 'Cuadro de tarifas',
  'tp-sede': 'Por sede',
  'tp-aparte': 'Licencia y exámenes',
  'ep-cabeza': 'Cabeza de la página',
  'ep-proximos': 'Próximos',
  'ep-anteriores': 'Anteriores',
  'ev-cabeza': 'Evento',
  'ev-cuerpo': 'Descripción y documentos',
  'ev-galeria': 'Galería del evento',
  'lg-cabeza': 'Cabeza de la página',
  'lg-texto': 'Texto legal',
  'pr-flujo': 'Preinscripción',
};

// ------------------------------------------------------------------ dónde se edita cada texto (W12d-bis)

/** Las páginas del editor (las mismas que `PAGINAS_EDITOR`, sin importar la capa). */
export type PaginaTexto = 'inicio' | 'sede' | 'tarifas' | 'eventos' | 'evento' | 'legales' | 'preinscripcion';

const PAGINA_DE_PREFIJO: Readonly<Record<string, PaginaTexto>> = {
  sp: 'sede',
  tp: 'tarifas',
  ep: 'eventos',
  ev: 'evento',
  lg: 'legales',
  pr: 'preinscripcion',
};

/** Boceto `PAG_DE_PARTE`: la página donde se edita un texto (los de Inicio, la cabecera y el pie, en Inicio). */
export function paginaDeTexto(clave: string): PaginaTexto {
  const parte = defTexto(clave)?.parte ?? '';
  return PAGINA_DE_PREFIJO[parte.split('-')[0]] ?? 'inicio';
}

/**
 * Boceto `CASO_DE`: el caso poco frecuente de «Ver el caso» en el que sale un texto (null: con los datos reales). Más los
 * de los grupos de una sede (`horarios.lleno`…), que solo salen con un grupo lleno o con lista de espera.
 */
const CASO_DE: Readonly<Record<string, string>> = {
  'sede.sin_clases': 'sin-clases',
  'horarios.lleno': 'lleno',
  'horarios.boton_espera': 'lleno',
  'horarios.espera': 'espera',
  'tarifas.vacio_titular': 'sin-tarifas',
  'tarifas.vacio_texto': 'sin-tarifas',
  'tarifas.vacio_correo': 'sin-tarifas',
  'tarifas.vacio_telefono': 'sin-tarifas',
  'eventos.sin_proximos_titular': 'sin-proximos',
  'eventos.sin_proximos_texto': 'sin-proximos',
  'eventos.ver_anteriores': 'sin-proximos',
  'eventos.un_evento': 'un-evento',
  'eventos.sin_anteriores': 'sin-anteriores',
  'evento.una_foto': 'una-foto',
  'pre.vacio_titulo': 'sin-grupo',
  'pre.vacio_otras': 'sin-grupo',
  'pre.vacio_llamar': 'sin-grupo',
  'pre.vacio_llamar_fin': 'sin-grupo',
  'pre.revisar_fecha': 'sin-grupo',
  'pre.espera_titulo': 'espera',
  'pre.espera_texto': 'espera',
  'pre.enviar_espera': 'espera',
  'pre.fin_espera': 'fin-espera',
  'pre.estado_espera': 'fin-espera',
  'pre.paso_correo_espera': 'fin-espera',
};

export function casoDeTexto(clave: string): string | null {
  return CASO_DE[clave] ?? null;
}

/**
 * El paso de la preinscripción en el que sale un texto (1 Pueblo, 2 Grupo, 3 Datos y firma, 4 Enviada). `'flujo'`: en
 * los pasos 1 a 3 (atrás, progreso y resumen lateral). null: no es de la preinscripción.
 */
const PASO_DE: Readonly<Record<string, 1 | 2 | 3 | 4>> = {
  'pre.titulo_pueblo': 1,
  'pre.pista_pueblo': 1,
  'pre.titulo_grupo': 2,
  'pre.cambiar_pueblo': 2,
  'pre.fecha_leyenda': 2,
  'pre.fecha_pista': 2,
  'pre.ver_grupos': 2,
  'pre.espera_fecha': 2,
  'pre.vacio_titulo': 2,
  'pre.vacio_otras': 2,
  'pre.vacio_llamar': 2,
  'pre.vacio_llamar_fin': 2,
  'pre.revisar_fecha': 2,
  'pre.titulo_datos': 3,
  'pre.espera_titulo': 3,
  'pre.espera_texto': 3,
  'pre.pista_datos': 3,
  'pre.bloque_alumno': 3,
  'pre.nombre': 3,
  'pre.apellidos': 3,
  'pre.bloque_contacto': 3,
  'pre.telefono': 3,
  'pre.email': 3,
  'pre.email_pista': 3,
  'pre.bloque_normas': 3,
  'pre.acepto': 3,
  'pre.fotos': 3,
  'pre.bloque_firma': 3,
  'pre.firma_pista': 3,
  'pre.enviar': 3,
  'pre.enviar_espera': 3,
  'pre.fin_plaza': 4,
  'pre.fin_espera': 4,
  'pre.referencia': 4,
  'pre.estado_plaza': 4,
  'pre.estado_espera': 4,
  'pre.que_ahora': 4,
  'pre.paso_correo': 4,
  'pre.paso_correo_texto': 4,
  'pre.paso_correo_espera': 4,
  'pre.paso_revisa': 4,
  'pre.paso_revisa_texto': 4,
  'pre.descargar': 4,
  'pre.volver_inicio': 4,
};

export function pasoDeTexto(clave: string): 1 | 2 | 3 | 4 | 'flujo' | null {
  if (paginaDeTexto(clave) !== 'preinscripcion') {
    return null;
  }
  // `pre.dia`, `pre.mes` y `pre.anio` salen en los pasos 2 y 3: se va al 2.
  return PASO_DE[clave] ?? (['pre.dia', 'pre.mes', 'pre.anio'].includes(clave) ? 2 : 'flujo');
}

const TEXTO_FOTO = /^texto_foto\.([1-9][0-9]?)\.(rotulo|titulo|texto)$/;
/** Los puntos de la tarifa más allá del 2: hasta el 9, como la API (`ClavesWeb`, `tarifas.punto<n>` de 1 a 9). */
const PUNTO = /^tarifas\.punto[3-9]$/;

/** Los campos de los bloques (`CAMPOS` y `campoDef` del boceto). */
function defCampo(clave: string): DefTexto | null {
  if (clave === 'portada.titular') {
    return { nombre: 'Titular', max: 60, obligatorio: true, multi: false, vacio: 'Escribe aquí el titular', parte: 'portada', falta: 'Escribe el titular.' };
  }
  if (clave === 'portada.frase') {
    return {
      nombre: 'Frase de debajo',
      max: 160,
      obligatorio: false,
      multi: true,
      vacio: 'Escribe aquí una frase: para quién son las clases y qué hacer',
      parte: 'portada',
      falta: '',
    };
  }
  const tf = TEXTO_FOTO.exec(clave);
  const [bloque, campo] = tf ? ['texto_foto', tf[2]] : clave.split('.');
  if (!['presentacion', 'profesores', 'galeria', 'sedes', 'texto_foto'].includes(bloque)) {
    return null;
  }
  switch (campo) {
    case 'rotulo': {
      const galeria = bloque === 'galeria';
      return {
        nombre: 'Rótulo',
        max: 24,
        obligatorio: galeria,
        multi: false,
        vacio: 'Rótulo corto (opcional)',
        parte: bloque,
        falta: galeria ? 'Escribe el rótulo.' : '',
      };
    }
    case 'titulo':
      return bloque === 'galeria'
        ? null
        : { nombre: 'Título', max: 60, obligatorio: true, multi: false, vacio: 'Escribe aquí el título', parte: bloque, falta: 'Escribe el título.' };
    case 'texto':
      return bloque === 'presentacion' || bloque === 'texto_foto'
        ? {
            nombre: 'Texto',
            max: bloque === 'texto_foto' ? 600 : 1200,
            obligatorio: false,
            multi: true,
            vacio: bloque === 'texto_foto' ? 'Escribe aquí el texto' : 'Escribe aquí la presentación',
            parte: bloque,
            falta: '',
          }
        : null;
    default:
      return null;
  }
}

/** La definición de un texto editable; null si la clave no se cambia en el sitio. */
export function defTexto(clave: string): DefTexto | null {
  const fila = TX[clave] ?? (PUNTO.test(clave) ? TX['tarifas.punto1'] : undefined);
  if (fila) {
    const [parte, nombre, max, obligatorio, ejemplo] = fila;
    return {
      nombre,
      max,
      obligatorio,
      multi: VARIAS_LINEAS.has(clave),
      vacio: `Escribe aquí: ${nombre.toLowerCase()}`,
      parte,
      falta: nombre.startsWith('Título') ? 'Escribe el título.' : 'Escribe el texto.',
      ejemplo,
    };
  }
  return defCampo(clave);
}

export const esTextoEditable = (clave: string): boolean => defTexto(clave) !== null;

/**
 * El texto por defecto del tema, cuando no depende de los datos de la escuela: el que pinta si la clave no está. Un
 * texto igual a este no se manda al guardar (W12a). Los que cambian con los datos (titular, frase, presentación, pista
 * de horarios, botón de la portada con una sede, «Igual en todas las sedes») no están: la capa los lee de la página.
 */
export const DEFECTO_FIJO: Readonly<Record<string, string>> = Object.fromEntries(
  Object.entries(TEXTOS_LINEA_COMARCAL).filter(([clave]) => clave !== 'portada.boton_horarios' && esTextoEditable(clave)),
);

export function defectoFijo(clave: string): string | null {
  return Object.prototype.hasOwnProperty.call(DEFECTO_FIJO, clave) ? DEFECTO_FIJO[clave] : null;
}

/**
 * Las claves cuyo texto por defecto depende de los datos de la escuela y se lee de la página (sin fila). Solo estas: en
 * las demás lo que enseña el tema no es un texto por defecto (el número de la sección en el rótulo de un texto con foto,
 * el «Escribe aquí…» de un vacío).
 */
const CLAVES_DEFECTO_DE_LA_PAGINA: ReadonlySet<string> = new Set([
  'portada.titular',
  'portada.frase',
  'portada.boton_horarios',
  'horarios.apoyo',
  'tarifas.igual',
  'presentacion.texto',
]);

/** Si un texto leído de la página vale como texto por defecto de la clave: de la lista cerrada y nunca vacío ni su «Escribe aquí…». */
export function defectoDeLaPagina(clave: string, texto: string): boolean {
  const def = defTexto(clave);
  return CLAVES_DEFECTO_DE_LA_PAGINA.has(clave) && !!def && !!texto.trim() && texto.trim() !== def.vacio;
}

/** El bloque de un texto con foto (`texto_foto.3`) o null. */
export function bloqueTextoFoto(clave: string): string | null {
  const tf = TEXTO_FOTO.exec(clave);
  return tf ? `texto_foto.${tf[1]}` : null;
}

export const recortar = (texto: string, maximo: number): string =>
  texto.length > maximo ? `${texto.slice(0, maximo - 1).trimEnd()}…` : texto;

// ------------------------------------------------------------------ dónde está cada texto en el tema

/**
 * Un texto en el DOM del tema: el elemento que casa con `selector` muestra esa clave (y, a lo sumo, delante, el número
 * de su sección: «02 · El club»). `clave` puede depender del elemento (texto con foto, puntos de tarifas).
 */
export interface CampoTexto {
  selector: string;
  /**
   * `ver(clave)`: el texto que pinta ahora el tema para esa clave (el del borrador o el de por defecto; vacío, su
   * «Escribe aquí…»), para distinguir por el texto dos sitios iguales del tema (la fila de hermanos y la de cada alumno).
   */
  clave: string | ((el: Element, ver: (clave: string) => string) => string | null);
  /**
   * W12d-bis: el texto es solo los textos sueltos del elemento antes de su primer hijo (`antes`: «Referencia» antes de
   * la referencia) o después del último (`despues`). La capa los envuelve una vez en un `span` y ese es el texto.
   */
  envolver?: 'antes' | 'despues';
}

const ENLACE_MENU = (fragmento: string, pagina?: string) =>
  `.lc > .cabecera .nav-lista a:is([href$="#${fragmento}"]${pagina ? `,[href^="/${pagina}"]` : ''})`;

/** Cabecera y pie: en todas las páginas. */
export const CAMPOS_TEXTO_MARCO: readonly CampoTexto[] = [
  { selector: ENLACE_MENU('linea'), clave: 'menu.horarios' },
  { selector: ENLACE_MENU('sedes'), clave: 'menu.sedes' },
  { selector: ENLACE_MENU('tarifas', 'tarifas'), clave: 'menu.tarifas' },
  { selector: ENLACE_MENU('eventos', 'eventos'), clave: 'menu.eventos' },
  { selector: ENLACE_MENU('contacto'), clave: 'menu.contacto' },
  { selector: '.lc > .cabecera .cabecera-cta', clave: 'cabecera.boton' },
  // W12d-bis: solo se ve en el móvil (en escritorio el menú va abierto en la cabecera).
  { selector: '.lc > .cabecera .menu-boton .menu-texto', clave: 'cabecera.menu' },
  { selector: '.lc > .pie .pie-legal a[href$="#aviso-legal"]', clave: 'pie.aviso_legal' },
  { selector: '.lc > .pie .pie-legal a[href$="#privacidad"]', clave: 'pie.privacidad' },
  { selector: '.lc > .pie .pie-legal a[href$="#cookies"]', clave: 'pie.cookies' },
  { selector: '.lc > .pie .pie-creditos', clave: 'pie.creditos' },
];

const deSeccionTextoFoto = (campo: string) => (el: Element) => {
  const id = el.closest('section[id^="texto-foto-"]')?.id ?? '';
  const n = /^texto-foto-([1-9][0-9]?)$/.exec(id);
  return n ? `texto_foto.${n[1]}.${campo}` : null;
};

/** Los puntos de la tarifa de Inicio, en el orden del tema: punto1, punto2 y los demás que tengan texto. */
export function clavesPuntos(conTexto: (clave: string) => boolean): string[] {
  const claves = ['tarifas.punto1', 'tarifas.punto2'];
  for (let n = 3; n <= 9; n++) {
    if (conTexto(`tarifas.punto${n}`)) {
      claves.push(`tarifas.punto${n}`);
    }
  }
  return claves;
}

/**
 * Los de Inicio (`inicioHtml` del boceto). Fuera: lo que va dentro del horario plegado de cada sede (no se abre en el
 * editor), los profesores (W12f) y las disciplinas (su clave lleva el código, que no está en la página).
 */
export function camposTextoInicio(conTexto: (clave: string) => boolean): CampoTexto[] {
  const puntos = clavesPuntos(conTexto);
  return [
    { selector: '.portada .titular', clave: 'portada.titular' },
    { selector: '.portada .entrada', clave: 'portada.frase' },
    { selector: '.portada .acciones .boton', clave: 'portada.boton_horarios' },
    { selector: '.portada .acciones .enlace-flecha', clave: 'portada.enlace_tarifas' },
    { selector: '#linea-titulo', clave: 'horarios.titulo' },
    { selector: '.red-pista', clave: 'horarios.apoyo' },
    { selector: 'section#presentacion .presentacion-velo .rotulo', clave: 'presentacion.rotulo' },
    { selector: '#presentacion-titulo', clave: 'presentacion.titulo' },
    { selector: 'section#presentacion .cuerpo', clave: 'presentacion.texto' },
    { selector: 'section#disciplinas .cabeza-seccion .rotulo', clave: 'disciplinas.titulo' },
    { selector: 'section#fotos-sedes .cabeza-seccion .rotulo', clave: 'sedes.rotulo' },
    { selector: 'section#fotos-sedes .cabeza-seccion .titulo-seccion', clave: 'sedes.titulo' },
    { selector: 'section#fotos-sedes .fs-parada .enlace-flecha', clave: 'sedes.ver_sede' },
    { selector: 'section#profesores .cabeza-seccion .rotulo', clave: 'profesores.rotulo' },
    { selector: '#profesores-titulo', clave: 'profesores.titulo' },
    { selector: '#galeria-titulo', clave: 'galeria.rotulo' },
    { selector: 'section[id^="texto-foto-"] .cabeza-seccion .rotulo', clave: deSeccionTextoFoto('rotulo') },
    { selector: 'section[id^="texto-foto-"] .cabeza-seccion .titulo-seccion', clave: deSeccionTextoFoto('titulo') },
    { selector: 'section[id^="texto-foto-"] .tf-texto > .cuerpo', clave: deSeccionTextoFoto('texto') },
    { selector: 'section#eventos .cabeza-seccion .rotulo', clave: 'eventos.rotulo' },
    { selector: '#eventos-titulo', clave: 'eventos.titulo' },
    { selector: 'section#eventos .evento .enlace-flecha', clave: 'eventos.enlace_ver' },
    { selector: '#tarifas-titulo', clave: 'tarifas.titulo' },
    { selector: 'section#tarifas .tarifa-mes', clave: 'tarifas.al_mes' },
    { selector: 'section#tarifas .tarifa-igual', clave: 'tarifas.igual' },
    {
      selector: 'section#tarifas .tarifa-incluye > li',
      clave: (el) => puntos[Array.from(el.parentElement?.children ?? []).indexOf(el)] ?? null,
    },
    { selector: 'section#tarifas .tarifa-lado .boton', clave: 'tarifas.boton_preinscripcion' },
    { selector: 'section#contacto .contacto-principal > .rotulo', clave: 'contacto.rotulo' },
    { selector: '#contacto-titulo', clave: 'contacto.titulo' },
    { selector: 'section#contacto .contacto-datos dt:has(+ dd > a[href^="mailto:"])', clave: 'contacto.correo_rotulo' },
    { selector: 'section#contacto .contacto-datos dt:has(+ dd > a.cifras)', clave: 'contacto.telefono_rotulo' },
    { selector: 'section#contacto .sedes-titulo', clave: 'contacto.sedes_titulo' },
  ];
}

// ------------------------------------------------------------------ las otras seis páginas (W12d-bis)

/** El puesto de un elemento entre sus hermanos que casan con `selector` (-1 si no casa). */
function puesto(el: Element, selector: string): number {
  return Array.from(el.parentElement?.children ?? []).filter((h) => h.matches(selector)).indexOf(el);
}

/** El hermano anterior más cercano que casa con `selector` (el título o subtítulo que abre un párrafo). */
function anterior(el: Element, selector: string): Element | null {
  let x = el.previousElementSibling;
  while (x && !x.matches(selector)) {
    x = x.previousElementSibling;
  }
  return x;
}

const indiceEnPadre = (el: Element) => Array.from(el.parentElement?.children ?? []).indexOf(el);

/** La página de una sede (`sedePagHtml` del boceto). Los puntos de la tarifa, como en Inicio. */
function camposSede(conTexto: (clave: string) => boolean): CampoTexto[] {
  const puntos = clavesPuntos(conTexto);
  return [
    { selector: '.p-sede .tramo > .tramo-parada:has(~ .tramo-parada--actual) .tramo-sentido', clave: 'sede.anterior' },
    { selector: '.p-sede .tramo-parada--actual ~ .tramo-parada .tramo-sentido', clave: 'sede.siguiente' },
    { selector: '.p-sede .sede-portada .acciones .enlace-flecha', clave: 'sede.ver_grupos' },
    { selector: '.p-sede > #horarios .cabeza-seccion .rotulo', clave: 'sede.rotulo_horarios' },
    { selector: '.p-sede #horarios-titulo', clave: 'sede.titulo_horarios' },
    { selector: '.p-sede .proxima > .proxima-rotulo:has(+ .proxima-cuando)', clave: 'sedes.proxima_rotulo' },
    { selector: '.p-sede .proxima > .proxima-rotulo:only-child', clave: 'sede.sin_clases' },
    { selector: '.p-sede .salidas-cabeza > span:nth-child(1)', clave: 'sede.cabeza_hora' },
    { selector: '.p-sede .salidas-cabeza > span:nth-child(2)', clave: 'sede.cabeza_grupo' },
    { selector: '.p-sede .salidas-cabeza > span:nth-child(3)', clave: 'sede.cabeza_dias' },
    { selector: '.p-sede .salida .plazas--lleno > .cifras', clave: 'horarios.lleno' },
    { selector: '.p-sede .salida .plazas--espera > .cifras', clave: 'horarios.espera' },
    { selector: '.p-sede .salida-accion .boton:not(.boton--secundario)', clave: 'horarios.boton' },
    { selector: '.p-sede .salida-accion .boton.boton--secundario', clave: 'horarios.boton_espera' },
    { selector: '.p-sede .leyenda > li:nth-child(1)', clave: 'sede.leyenda_quedan' },
    { selector: '.p-sede .leyenda > li:nth-child(2)', clave: 'sede.leyenda_lleno' },
    { selector: '.p-sede .leyenda > li:nth-child(3)', clave: 'sede.leyenda_espera' },
    { selector: '.p-sede > #llegar .cabeza-seccion .rotulo', clave: 'sede.rotulo_llegar' },
    { selector: '.p-sede #llegar-titulo', clave: 'sede.titulo_llegar' },
    { selector: '.p-sede .ficha dt:has(+ [data-dato="lugar"])', clave: 'sede.ficha_lugar' },
    { selector: '.p-sede .ficha dt:has(+ [data-dato="acceso"])', clave: 'sede.ficha_entrada' },
    { selector: '.p-sede .ficha dt:has(+ [data-dato="telefono"])', clave: 'sede.ficha_telefono' },
    { selector: '.p-sede .llegar-acciones .boton', clave: 'sede.abrir_mapa' },
    { selector: '.p-sede #tarifas-titulo', clave: 'sede.rotulo_tarifa' },
    { selector: '.p-sede > #tarifas .tarifa-mes', clave: 'tarifas.al_mes' },
    { selector: '.p-sede > #tarifas .tarifa-igual', clave: 'tarifas.igual' },
    { selector: '.p-sede > #tarifas .tarifa-incluye > li', clave: (el) => puntos[indiceEnPadre(el)] ?? null },
    { selector: '.p-sede > #tarifas .tarifa-lado .boton', clave: 'tarifas.boton_preinscripcion' },
    { selector: '.p-sede > #sedes .cabeza-seccion .rotulo', clave: 'sede.rotulo_ruta' },
    { selector: '.p-sede #sedes-titulo', clave: 'sede.titulo_ruta' },
    { selector: '.p-sede .ruta-aqui', clave: 'sede.estas_aqui' },
  ];
}

/**
 * La página de tarifas (`tarifasPagHtml`). El concepto de la licencia no: el tema pinta «Licencia de <disciplina>» en
 * un solo texto. «Cada adulto» y «Cada menor» de la tarifa familiar son fijos (candado de la capa).
 */
const CAMPOS_TARIFAS: readonly CampoTexto[] = [
  { selector: '.p-tarifas #tarifas-titulo', clave: 'tarifas.titulo' },
  { selector: '.p-tarifas > #tarifas .tarifa-mes', clave: 'tarifas.al_mes' },
  { selector: '.p-tarifas > #tarifas .tarifa-cifra ~ .tarifa-lado > .tarifa-igual', clave: 'tarifas.cuota_igual' },
  {
    // `tarifas.punto1`, «También…» (si hay otras tarifas) y «Licencia y exámenes, aparte» (si se pinta «Aparte»).
    selector: '.p-tarifas > #tarifas .tarifa-incluye > li',
    clave: (el) => {
      const n = el.parentElement?.children.length ?? 0;
      const aparte = !!el.ownerDocument.querySelector('.p-tarifas > #aparte');
      const claves = ['tarifas.punto1'];
      if (n - 1 - (aparte ? 1 : 0) > 0) {
        claves.push('tarifas.tambien');
      }
      if (aparte) {
        claves.push('tarifas.incluye_aparte');
      }
      return claves[indiceEnPadre(el)] ?? null;
    },
  },
  { selector: '.p-tarifas .tarifas-acciones .boton', clave: 'tarifas.boton_preinscripcion' },
  { selector: '.p-tarifas .tarifas-acciones .enlace-flecha', clave: 'tarifas.ver_horarios' },
  { selector: '.p-tarifas > #tarifas .tarifa-vacio', clave: 'tarifas.vacio_titular' },
  { selector: '.p-tarifas > #tarifas .tarifa-vacio ~ .tarifa-lado > .tarifa-igual', clave: 'tarifas.vacio_texto' },
  { selector: '.p-tarifas .tarifa-contacto dt:has(+ dd > a[href^="mailto:"])', clave: 'tarifas.vacio_correo' },
  { selector: '.p-tarifas .tarifa-contacto dt:has(+ dd > a.cifras)', clave: 'tarifas.vacio_telefono' },
  { selector: '.p-tarifas > #cuadro .cabeza-seccion .rotulo', clave: 'tarifas.rotulo_cuadro' },
  { selector: '.p-tarifas #cuadro-titulo', clave: 'tarifas.titulo_cuadro' },
  { selector: '.p-tarifas .cuadro caption .rotulo', clave: 'tarifas.leyenda_cuadro' },
  { selector: '.p-tarifas .cuadro thead th:nth-child(1)', clave: 'tarifas.col_tarifa' },
  { selector: '.p-tarifas .cuadro thead th:nth-child(2)', clave: 'tarifas.col_quien' },
  { selector: '.p-tarifas .cuadro thead th:nth-child(3)', clave: 'tarifas.col_importe' },
  {
    // La fila de hermanos o la de cada alumno, por el `data-quien` que pone el tema en la celda (no por lo que dice:
    // las dos pueden decir lo mismo).
    selector: '.p-tarifas .cuadro tbody:not(.grupo-tarifa) td.quien',
    clave: (el) => (el.getAttribute('data-quien') === 'hermanos' ? 'tarifas.hermanos' : 'tarifas.cada_alumno'),
  },
  { selector: '.p-tarifas .cuadro td.importe > small', clave: 'tarifas.al_mes' },
  { selector: '.p-tarifas .cuadro-pie', clave: 'tarifas.pie_cuadro' },
  { selector: '.p-tarifas > #por-sede .cabeza-seccion .rotulo', clave: 'tarifas.rotulo_sede' },
  { selector: '.p-tarifas #por-sede-titulo', clave: 'tarifas.titulo_sede' },
  { selector: '.p-tarifas > #aparte .cabeza-seccion .rotulo', clave: 'tarifas.rotulo_aparte' },
  { selector: '.p-tarifas #aparte-titulo', clave: 'tarifas.titulo_aparte' },
  { selector: '.p-tarifas .aparte li:has(> [data-dato="examen"]) > .aparte-concepto', clave: 'tarifas.examen' },
  { selector: '.p-tarifas .aparte [data-dato="examen"]', clave: 'tarifas.examen_desde' },
  { selector: '.p-tarifas .aparte-nota', clave: 'tarifas.nota_aparte' },
  { selector: '.p-tarifas .cierre .boton:not(.boton--secundario)', clave: 'tarifas.boton_preinscripcion' },
  { selector: '.p-tarifas .cierre .boton.boton--secundario', clave: 'tarifas.ver_horarios' },
];

/** La página de eventos (`eventosPagHtml`). La cuenta: «1 evento» o «{n} eventos», según los próximos que haya. */
const CAMPOS_EVENTOS: readonly CampoTexto[] = [
  { selector: '.p-eventos .pagina-titular', clave: 'eventos.pag_titulo' },
  { selector: '.p-eventos .pagina-entrada', clave: 'eventos.pag_entrada' },
  { selector: '.p-eventos #proximos-titulo', clave: 'eventos.proximos' },
  {
    selector: '.p-eventos > #proximos .bloque-cuenta',
    clave: (el) => {
      const seccion = el.closest('#proximos');
      if (!seccion?.querySelector('.destacado')) {
        return null;
      }
      return seccion.querySelectorAll('.ev-filas > li').length === 0 ? 'eventos.un_evento' : 'eventos.n_eventos';
    },
  },
  { selector: '.p-eventos .destacado .boton', clave: 'eventos.enlace_ver' },
  { selector: '.p-eventos .ev-fila > .enlace-flecha', clave: 'eventos.enlace_ver' },
  { selector: '.p-eventos > #proximos .ev-vacio > h3', clave: 'eventos.sin_proximos_titular' },
  { selector: '.p-eventos > #proximos .ev-vacio > p', clave: 'eventos.sin_proximos_texto' },
  { selector: '.p-eventos > #proximos .ev-vacio > .enlace-flecha', clave: 'eventos.ver_anteriores' },
  { selector: '.p-eventos #anteriores-titulo', clave: 'eventos.anteriores' },
  { selector: '.p-eventos > #anteriores p.ev-vacio', clave: 'eventos.sin_anteriores' },
];

/** La página de un evento (`eventoPagHtml`). El visor de fotos («Anterior», «Siguiente») sigue fijo. */
const CAMPOS_EVENTO: readonly CampoTexto[] = [
  { selector: '.p-evento a.atras', clave: 'evento.volver' },
  { selector: '.p-evento #docs-titulo', clave: 'evento.documentos' },
  { selector: '.p-evento #galeria-titulo', clave: 'evento.galeria' },
  {
    selector: '.p-evento .galeria-cuenta',
    clave: (el) => (el.closest('#galeria')?.querySelectorAll('.rejilla-fotos > li').length === 1 ? 'evento.una_foto' : 'evento.n_fotos'),
  },
];

/** Los subtítulos del aviso legal, en su orden; el cuarto («Legislación») es fijo. */
const SUBTITULOS_AVISO = ['legal.aviso.h1', 'legal.aviso.h2', 'legal.aviso.h3'];

/** La página de legales (`legalesHtml`): solo los 20 textos que lee el tema (decisión del propietario, 12/11). */
const CAMPOS_LEGALES: readonly CampoTexto[] = [
  { selector: '.p-legales .legal-titular', clave: 'legal.titulo' },
  { selector: '.p-legales .legal-entrada', clave: 'legal.entrada' },
  { selector: '.p-legales .actualizado', clave: 'legal.actualizada', envolver: 'antes' },
  { selector: '.p-legales #indice-titulo', clave: 'legal.en_esta_pagina' },
  { selector: '.p-legales #t-aviso', clave: 'legal.aviso' },
  { selector: '.p-legales #t-privacidad', clave: 'legal.privacidad' },
  { selector: '.p-legales #t-cookies', clave: 'legal.cookies' },
  { selector: '.p-legales #t-normas', clave: 'legal.normas' },
  { selector: '.p-legales #aviso-legal > h3.legal-h3', clave: (el) => SUBTITULOS_AVISO[puesto(el, 'h3')] ?? null },
  {
    // Cada párrafo, por lo que lo abre: el título de la sección (antes de la ficha) o el subtítulo 1 o 2.
    selector: '.p-legales #aviso-legal > p',
    clave: (el) => {
      const abre = anterior(el, 'h2, h3, dl');
      if (abre?.matches('h2')) {
        return 'legal.aviso.p0';
      }
      const n = abre?.matches('h3') ? puesto(abre, 'h3') : -1;
      return n === 0 ? 'legal.aviso.p1' : n === 1 ? 'legal.aviso.p2' : null;
    },
  },
  { selector: '.p-legales #privacidad > p', clave: (el) => (anterior(el, 'h2, h3')?.matches('h2') ? 'legal.priv.p0' : null) },
  { selector: '.p-legales #privacidad > h3.legal-h3:last-of-type', clave: 'legal.priv.h2' },
  { selector: '.p-legales .ficha dt:has(+ [data-dato="titular"])', clave: 'legal.ficha_titular' },
  { selector: '.p-legales .ficha dt:has(+ [data-dato="cif"])', clave: 'legal.ficha_cif' },
  { selector: '.p-legales .ficha dt:has(+ [data-dato="domicilio"])', clave: 'legal.ficha_domicilio' },
  { selector: '.p-legales .ficha dt:has(+ [data-dato="correo"])', clave: 'legal.ficha_correo' },
];

const PASOS_PROGRESO = ['pre.paso_pueblo', 'pre.paso_grupo', 'pre.paso_datos'];

/**
 * La preinscripción (`preinscripcionHtml`), en el paso que se ve. Con una sede no hay paso «Pueblo». En el resumen
 * lateral, desde el paso 2 va la fila fija «Edad» tras «Pueblo» (y quizá «Plaza» al final). En el paso 4, la lista de
 * espera si la primera fila de la ficha lo dice («Lista de espera, sin plaza»).
 */
function camposPreinscripcion(): CampoTexto[] {
  const fichaEspera = TEXTOS_PREINSCRIPCION_LINEA_COMARCAL.fichaEstadoEspera;
  const enEspera = (el: Element) =>
    (el.ownerDocument.querySelector('.p-preinscripcion .fin-resumen .ficha-fila:first-child > dd')?.textContent ?? '').trim() === fichaEspera;
  return [
    { selector: '.p-preinscripcion .flujo-principal > a.atras', clave: 'pre.atras' },
    { selector: '.p-preinscripcion .progreso-texto', clave: 'pre.paso_n' },
    {
      selector: '.p-preinscripcion .progreso-linea > li',
      clave: (el) => {
        const n = el.parentElement?.querySelectorAll(':scope > li').length ?? 0;
        return PASOS_PROGRESO.slice(Math.max(0, PASOS_PROGRESO.length - n))[puesto(el, 'li')] ?? null;
      },
    },
    // Paso 1
    { selector: '.p-preinscripcion #campo-sede .pregunta-titulo', clave: 'pre.titulo_pueblo' },
    { selector: '.p-preinscripcion #sede-pista', clave: 'pre.pista_pueblo' },
    { selector: '.p-preinscripcion form > button.continuar', clave: 'pre.continuar' },
    // Paso 2
    { selector: '.p-preinscripcion form > h1.pregunta-titulo:has(+ .contexto)', clave: 'pre.titulo_grupo' },
    { selector: '.p-preinscripcion .contexto > a', clave: 'pre.cambiar_pueblo' },
    { selector: '.p-preinscripcion fieldset.nacimiento > legend', clave: 'pre.fecha_leyenda' },
    { selector: '.p-preinscripcion fieldset.nacimiento > #fecha-pista', clave: 'pre.fecha_pista' },
    { selector: '.p-preinscripcion .fecha label[for="dia"]', clave: 'pre.dia' },
    { selector: '.p-preinscripcion .fecha label[for="mes"]', clave: 'pre.mes' },
    { selector: '.p-preinscripcion .fecha label[for="anio"]', clave: 'pre.anio' },
    { selector: '.p-preinscripcion #ver-grupos', clave: 'pre.ver_grupos' },
    { selector: '.p-preinscripcion .grupos-espera', clave: 'pre.espera_fecha' },
    { selector: '.p-preinscripcion .vacio > h2', clave: 'pre.vacio_titulo' },
    { selector: '.p-preinscripcion .vacio > p:has(+ ul.otras)', clave: 'pre.vacio_otras' },
    { selector: '.p-preinscripcion .vacio > .vacio-contacto:has(> a.cifras)', clave: 'pre.vacio_llamar', envolver: 'antes' },
    { selector: '.p-preinscripcion .vacio > .vacio-contacto:has(> a.cifras)', clave: 'pre.vacio_llamar_fin', envolver: 'despues' },
    { selector: '.p-preinscripcion .vacio > .vacio-contacto > a:not(.cifras)', clave: 'pre.revisar_fecha' },
    { selector: '.p-preinscripcion .continuar > .boton', clave: 'pre.continuar' },
    // Paso 3
    { selector: '.p-preinscripcion form > h1.pregunta-titulo:has(+ .eleccion)', clave: 'pre.titulo_datos' },
    { selector: '.p-preinscripcion .aviso-espera > p > strong', clave: 'pre.espera_titulo' },
    { selector: '.p-preinscripcion .aviso-espera > p', clave: 'pre.espera_texto', envolver: 'despues' },
    { selector: '.p-preinscripcion form > p.pista', clave: 'pre.pista_datos' },
    { selector: '.p-preinscripcion #b-alumno', clave: 'pre.bloque_alumno' },
    { selector: '.p-preinscripcion label[for="nombre"]', clave: 'pre.nombre' },
    { selector: '.p-preinscripcion label[for="apellidos"]', clave: 'pre.apellidos' },
    { selector: '.p-preinscripcion #b-contacto', clave: 'pre.bloque_contacto' },
    { selector: '.p-preinscripcion label[for="telefono"]', clave: 'pre.telefono' },
    { selector: '.p-preinscripcion label[for="email"]', clave: 'pre.email' },
    { selector: '.p-preinscripcion #email-pista', clave: 'pre.email_pista' },
    { selector: '.p-preinscripcion #b-normas', clave: 'pre.bloque_normas' },
    { selector: '.p-preinscripcion .marca-casilla:has(> #normas) > span', clave: 'pre.acepto' },
    { selector: '.p-preinscripcion .marca-casilla:has(> #fotos) > span', clave: 'pre.fotos', envolver: 'antes' },
    { selector: '.p-preinscripcion #b-firma', clave: 'pre.bloque_firma' },
    { selector: '.p-preinscripcion #firma-pista', clave: 'pre.firma_pista' },
    { selector: '.p-preinscripcion form:not(:has(.aviso-espera)) .enviar > .boton > span', clave: 'pre.enviar' },
    { selector: '.p-preinscripcion form:has(.aviso-espera) .enviar > .boton > span', clave: 'pre.enviar_espera' },
    // Paso 4
    { selector: '.p-preinscripcion #fin-titulo', clave: (el) => (enEspera(el) ? 'pre.fin_espera' : 'pre.fin_plaza') },
    { selector: '.p-preinscripcion .banda-ref', clave: 'pre.referencia', envolver: 'antes' },
    { selector: '.p-preinscripcion .banda-estado', clave: (el) => (enEspera(el) ? 'pre.estado_espera' : 'pre.estado_plaza') },
    { selector: '.p-preinscripcion #fin-ahora', clave: 'pre.que_ahora' },
    {
      selector: '.p-preinscripcion .fin-pasos .ruta-sede > .ruta-nombre',
      clave: (el) => {
        const n = puesto(el.parentElement!, '.ruta-sede');
        return n === 0 ? 'pre.paso_correo' : n === 1 && !enEspera(el) ? 'pre.paso_revisa' : null;
      },
    },
    {
      selector: '.p-preinscripcion .fin-pasos .ruta-sede > .ruta-dir',
      clave: (el) => {
        const n = puesto(el.parentElement!, '.ruta-sede');
        const espera = enEspera(el);
        return n === 0 ? (espera ? 'pre.paso_correo_espera' : 'pre.paso_correo_texto') : n === 1 && !espera ? 'pre.paso_revisa_texto' : null;
      },
    },
    { selector: '.p-preinscripcion .fin-acciones > .boton', clave: 'pre.descargar' },
    { selector: '.p-preinscripcion .fin-acciones > .enlace-flecha', clave: 'pre.volver_inicio' },
    // Resumen lateral (pasos 1 a 3)
    { selector: '.p-preinscripcion #resumen-titulo', clave: 'pre.resumen_titulo' },
    {
      selector: '.p-preinscripcion .resumen .resumen-fila > dt',
      clave: (el) => {
        const filas = Array.from(el.closest('dl')?.querySelectorAll(':scope > .resumen-fila') ?? []);
        const n = filas.indexOf(el.parentElement!);
        const orden = filas.length >= 4
          ? ['pre.resumen_pueblo', null, 'pre.resumen_grupo', 'pre.resumen_dias']
          : ['pre.resumen_pueblo', 'pre.resumen_grupo', 'pre.resumen_dias'];
        return orden[n] ?? null;
      },
    },
    { selector: '.p-preinscripcion .resumen .resumen-vacio', clave: 'pre.sin_elegir' },
    { selector: '.p-preinscripcion .resumen-tarifa', clave: 'pre.tarifa_igual' },
  ];
}

/** Los textos de una página interior (sin la cabecera y el pie, que son `CAMPOS_TEXTO_MARCO`). */
export function camposTextoPagina(
  pagina: Exclude<PaginaTexto, 'inicio'>,
  conTexto: (clave: string) => boolean,
): CampoTexto[] {
  switch (pagina) {
    case 'sede':
      return camposSede(conTexto);
    case 'tarifas':
      return [...CAMPOS_TARIFAS];
    case 'eventos':
      return [...CAMPOS_EVENTOS];
    case 'evento':
      return [...CAMPOS_EVENTO];
    case 'legales':
      return [...CAMPOS_LEGALES];
    case 'preinscripcion':
      return camposPreinscripcion();
  }
}
