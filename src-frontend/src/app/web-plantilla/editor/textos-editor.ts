import { TEXTOS_LINEA_COMARCAL } from '../temas/linea-comarcal/textos';

/**
 * Textos que se cambian en el sitio en «Web · Contenido» (W12d; boceto `boceto-panel-contenido-web-comarcal.html`, `TX`,
 * `CAMPOS`, `campoDef`, `nombreError`). Lógica pura que comparten la capa de edición (dentro del iframe) y el panel.
 *
 * Solo las claves que el tema «La línea comarcal» ya pinta desde `web_bloque`: las de Inicio, la cabecera y el pie. Los
 * textos de las otras seis páginas siguen fijos en el tema (sin clave que leer) y no se editan aquí.
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
};

const VARIAS_LINEAS = new Set(['pie.creditos']);

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
};

const TEXTO_FOTO = /^texto_foto\.([1-9][0-9]?)\.(rotulo|titulo|texto)$/;
const PUNTO = /^tarifas\.punto([3-9]|1[0-9]|20)$/;

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
  clave: string | ((el: Element) => string | null);
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
  for (let n = 3; n <= 20; n++) {
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
