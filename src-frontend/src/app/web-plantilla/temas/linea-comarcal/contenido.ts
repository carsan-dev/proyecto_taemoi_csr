import { environment } from '../../../../environments/environment';
import { BloquesWeb, FotoGaleria, ImagenBloque, ProfesorBloque, ordenarSecciones } from '../../comun/bloques';
import { derivarColores } from '../../comun/colores';
import {
  DIAS_SEMANA,
  EstadoPlazas,
  ProximaClase,
  diasEnTexto,
  edadDeInicio,
  edadesEnTexto,
  enlaceMapa,
  estadoPlazas,
  gruposPorSede,
  importeEnTexto,
  nombreDia,
  numeroEnLetras,
  proximaClase,
  salidasDeSede,
  telefonoEnlace,
  telefonoVisible,
  textoPlazas,
} from '../../comun/horario';
import { EventoWeb, SedeWeb, SitioWeb } from '../../datos/modelos';
import {
  ClaveTexto,
  DISCIPLINA_TAEKWONDO,
  TEXTOS_FIJOS_LINEA_COMARCAL as FIJOS,
  TEXTOS_LINEA_COMARCAL,
  TEXTOS_PAGINAS_LINEA_COMARCAL,
} from './textos';

/**
 * Lo que pinta el tema «La línea comarcal» a partir de `GET /api/publico/web` (boceto
 * `fase4-web/boceto-linea-comarcal.html`): textos por clave con el del boceto por defecto, variantes de una y de varias
 * sedes, plazas (§4.3), secciones vacías fuera (§9 n.º 11) y orden de las movibles por `orden`. Sin Angular: los
 * componentes del tema solo lo pintan.
 */

/** Los textos de la escuela con el del boceto por defecto. */
export class TextosTema {
  readonly bloques: BloquesWeb;

  constructor(sitio: SitioWeb) {
    this.bloques = new BloquesWeb(sitio.bloques);
  }

  t(clave: ClaveTexto): string {
    return this.bloques.texto(clave, TEXTOS_LINEA_COMARCAL[clave]);
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Marco: cabecera y pie, comunes a todas las páginas del tema
// ---------------------------------------------------------------------------------------------------------------

export interface EnlaceAncla {
  texto: string;
  ruta: string;
  /** Ancla de la portada (`/#linea`); null si el enlace va a otra página (`/tarifas`). */
  fragmento: string | null;
  /** Si es la página en la que se está (`aria-current="page"`). */
  actual?: boolean;
}

/**
 * En qué página está el marco. En la portada, el menú lleva a sus secciones; en las demás páginas (boceto
 * `boceto-linea-comarcal-sede.html`, `-tarifas.html`, `-eventos.html`…), «Tarifas» y «Eventos» van a su página y el
 * resto a la portada. En la de eventos (y en la de un evento), «Eventos» es la actual.
 */
export type PaginaMarco = 'portada' | 'tarifas' | 'eventos' | 'otra';

/** Las entradas del menú que fuera de la portada van a su página (siempre visibles). */
const PAGINAS_DEL_MENU: Partial<Record<ClaveTexto, { ruta: string; pagina: PaginaMarco }>> = {
  'menu.tarifas': { ruta: '/tarifas', pagina: 'tarifas' },
  'menu.eventos': { ruta: '/eventos', pagina: 'eventos' },
};

export interface VistaMarco {
  nombre: string;
  /** El nombre en dos líneas, como el boceto («Taekwondo» / «Sierra Sur»). */
  nombrePartes: string[];
  iniciales: string;
  logoUrl: string | null;
  /** Variables CSS del color de la escuela (derivarColores). */
  colores: Record<string, string>;
  menu: EnlaceAncla[];
  cta: string;
  anio: number;
  legales: EnlaceAncla[];
  creditos: string | null;
}

export function vistaMarco(sitio: SitioWeb, ahora: Date, pagina: PaginaMarco = 'portada'): VistaMarco {
  const textos = new TextosTema(sitio);
  const nombre = sitio.escuela.nombre.trim();
  const palabras = nombre.split(/\s+/).filter(Boolean);
  const c = derivarColores(sitio.escuela.colorPrimario);
  const secciones = seccionesConContenido(sitio);
  const menu: [ClaveTexto, string, boolean][] = [
    ['menu.horarios', 'linea', secciones.horarios],
    ['menu.sedes', 'sedes', secciones.horarios],
    ['menu.tarifas', 'tarifas', secciones.tarifas],
    ['menu.eventos', 'eventos', secciones.eventos],
    ['menu.contacto', 'contacto', secciones.contacto],
  ];
  return {
    nombre,
    nombrePartes: palabras.length > 1 ? [palabras[0], palabras.slice(1).join(' ')] : [nombre],
    iniciales: iniciales(palabras),
    logoUrl:
      sitio.escuela.logoVersion != null
        ? `${environment.apiUrl}/publico/escuela/logo?v=${sitio.escuela.logoVersion}`
        : null,
    colores: {
      '--escuela': c.base,
      '--escuela-trazo': c.trazo,
      '--escuela-texto': c.texto,
      '--boton-fondo': c.botonFondo,
      '--boton-tinta': c.botonTinta,
      '--boton-hover': c.botonHover,
      '--tarifa-fondo': c.tarifaFondo,
      '--tarifa-tinta': c.tarifaTinta,
      '--tarifa-boton-tinta': c.tarifaBotonTinta,
    },
    menu: menu
      .filter(([clave, , visible]) => visible || (pagina !== 'portada' && PAGINAS_DEL_MENU[clave]))
      .map(([clave, fragmento]): EnlaceAncla => {
        const texto = textos.t(clave);
        const propia = pagina !== 'portada' ? PAGINAS_DEL_MENU[clave] : undefined;
        return propia
          ? { texto, ruta: propia.ruta, fragmento: null, actual: pagina === propia.pagina }
          : { texto, ruta: '/', fragmento };
      })
      .filter((e) => e.texto),
    cta: textos.t('cabecera.boton'),
    anio: ahora.getFullYear(),
    legales: (
      [
        ['pie.aviso_legal', 'aviso-legal'],
        ['pie.privacidad', 'privacidad'],
        ['pie.cookies', 'cookies'],
      ] as [ClaveTexto, string][]
    )
      .map(([clave, fragmento]) => ({ texto: textos.t(clave), ruta: '/legales', fragmento }))
      .filter((e) => e.texto),
    creditos: textos.bloques.textoPropio('pie.creditos'),
  };
}

/** «Taekwondo Sierra Sur» → «TSS»: la inicial de cada palabra con mayúscula (hasta tres). */
function iniciales(palabras: string[]): string {
  const conMayuscula = palabras.filter((p) => /^\p{Lu}/u.test(p));
  const fuente = conMayuscula.length ? conMayuscula : palabras;
  return fuente
    .slice(0, 3)
    .map((p) => p.charAt(0).toUpperCase())
    .join('');
}

/** Qué secciones de la portada tienen datos (el menú no enlaza a una sección que no se pinta). */
export function seccionesConContenido(sitio: SitioWeb): {
  horarios: boolean;
  tarifas: boolean;
  eventos: boolean;
  contacto: boolean;
} {
  return {
    horarios: sitio.sedes.length > 0,
    tarifas: tarifaPrincipal(sitio) !== null,
    eventos: sitio.proximosEventos.length > 0,
    contacto: sitio.sedes.length > 0 || !!sitio.contacto.email?.trim() || !!sitio.contacto.telefono?.trim(),
  };
}

// ---------------------------------------------------------------------------------------------------------------
// Portada
// ---------------------------------------------------------------------------------------------------------------

export interface VistaDia {
  letra: string;
  si: boolean;
  /** El día de hoy (página de sede: el día se marca en amarillo). */
  hoy: boolean;
}

export interface VistaSalida {
  clave: string;
  inicio: string;
  fin: string;
  grupo: string;
  edades: string | null;
  /** Nombre de la disciplina del grupo («4 a 6 años · Taekwondo» en la página de sede). */
  disciplina: string;
  /** Si tiene clase hoy (página de sede: «HOY» junto a la hora). */
  hoy: boolean;
  dias: VistaDia[];
  diasTexto: string;
  plazas: EstadoPlazas | null;
  plazasTexto: string;
  /** Preinscribirme (con plazas) o lista de espera (lleno o en espera). */
  accion: { texto: string; secundaria: boolean; etiqueta: string; consulta: Record<string, string> };
}

export interface VistaSede {
  id: number;
  /** Para los id del DOM y la sede recordada. */
  clave: string;
  nombre: string;
  lugar: string | null;
  direccion: string | null;
  enlaceSede: string | null;
  mapa: string | null;
  proxima: ProximaClase | null;
  salidas: VistaSalida[];
}

export interface VistaHorarios {
  numero: string;
  titulo: string;
  apoyoMovil: string;
  apoyoEscritorio: string;
  verSede: string;
  comoLlegar: string;
  proximaRotulo: string;
  sedes: VistaSede[];
}

export interface VistaDisciplina {
  codigo: string;
  nombre: string;
  parrafos: string[];
  filas: { rotulo: string; texto: string }[];
  foto: ImagenBloque | null;
}

export interface VistaEvento {
  id: number;
  titulo: string;
  fecha: string;
  dia: string;
  semana: string;
  lugar: string | null;
  hora: string | null;
}

export interface VistaDato {
  cifra: string;
  etiqueta: string;
}

/** Una sede en «Fotos de las sedes» (boceto del editor, `sedesFotosHtml`): su foto 1 con su descripción. */
export interface VistaSedeFoto {
  clave: string;
  nombre: string;
  lugar: string | null;
  foto: ImagenBloque | null;
  enlace: string | null;
}

type CuerpoSeccion =
  | { tipo: 'presentacion'; numero: string; rotulo: string; titulo: string; parrafos: string[]; foto: ImagenBloque | null; datos: VistaDato[] }
  | { tipo: 'disciplinas'; numero: string; rotulo: string; disciplinas: VistaDisciplina[] }
  | { tipo: 'profesores'; numero: string; rotulo: string; titulo: string; profesores: (ProfesorBloque & { monograma: string })[] }
  | { tipo: 'galeria'; numero: string; rotulo: string; fotos: (FotoGaleria & { forma: string; numero: string })[] }
  | { tipo: 'sedes'; numero: string; rotulo: string; titulo: string; verSede: string; sedes: VistaSedeFoto[] }
  | { tipo: 'texto_foto'; numero: string; rotulo: string; titulo: string; parrafos: string[]; foto: ImagenBloque | null }
  | { tipo: 'eventos'; numero: string; rotulo: string; titulo: string; enlace: string; eventos: VistaEvento[] }
  | {
      tipo: 'tarifas';
      numero: string;
      titulo: string;
      importe: string;
      alMes: string;
      igual: string | null;
      puntos: string[];
      boton: string;
    }
  | {
      tipo: 'contacto';
      numero: string;
      rotulo: string;
      titulo: string;
      correo: { rotulo: string; texto: string; enlace: string } | null;
      telefono: { rotulo: string; texto: string; enlace: string } | null;
      sedesTitulo: string;
      sedes: { clave: string; nombre: string; enlace: string | null; detalle: string }[];
    };

/**
 * Una sección de la portada: `id` es su ancla y su clave en el `@for` (`texto-foto-<n>` en las repetibles); `anden`,
 * el fondo alterno del boceto del editor (`numerar`/`fondo`: entre las que se pintan, sin horarios ni tarifas, una sí y
 * otra no, empezando por la segunda).
 */
export type VistaSeccion = CuerpoSeccion & { id: string; anden: boolean };

export interface VistaPortada {
  una: boolean;
  rotulo: string;
  titular: string;
  frase: string;
  botonHorarios: string;
  enlaceTarifas: string | null;
  foto: ImagenBloque | null;
  horarios: VistaHorarios | null;
  secciones: VistaSeccion[];
}

type TipoSeccion = VistaSeccion['tipo'];

interface SeccionTema {
  /** La clave de su fila de cabecera (`texto_foto.<n>` en las repetibles). */
  clave: string;
  tipo: TipoSeccion;
  movible: boolean;
  prefijo?: string;
  /** Contacto: no tiene cabecera ni se mueve; va siempre al final. */
  sinCabecera?: boolean;
}

/**
 * Secciones de la portada en el orden del tema (§9 n.º 4 y 11; W12b). Sin filas de cabecera, las movibles (presentación,
 * profesores, galería) cambian entre sí por `orden` y las fijas siguen en su sitio; con cabeceras, cada una va por la
 * suya (`ordenarSecciones`). «Fotos de las sedes» solo sale si la escuela la ha añadido (su cabecera) y «Texto con
 * foto», si tiene filas.
 */
function seccionesDelTema(bloques: BloquesWeb): SeccionTema[] {
  return [
    { clave: 'presentacion', tipo: 'presentacion', movible: true },
    { clave: 'disciplinas', tipo: 'disciplinas', movible: false },
    ...(bloques.tiene('sedes') ? [{ clave: 'sedes', tipo: 'sedes', movible: true } as SeccionTema] : []),
    { clave: 'profesores', tipo: 'profesores', movible: true, prefijo: 'profesor' },
    { clave: 'galeria', tipo: 'galeria', movible: true },
    ...bloques.numeros('texto_foto').map((n): SeccionTema => ({ clave: `texto_foto.${n}`, tipo: 'texto_foto', movible: true })),
    { clave: 'eventos', tipo: 'eventos', movible: false },
    { clave: 'tarifas', tipo: 'tarifas', movible: false },
    { clave: 'contacto', tipo: 'contacto', movible: false, sinCabecera: true },
  ];
}

/** Las que se pueden ocultar (las fijas y contacto, nunca). */
const OCULTABLES = new Set<TipoSeccion>(['presentacion', 'profesores', 'galeria', 'sedes', 'texto_foto']);

/** Sin filas de cabecera, el fondo de andén va fijo en estas secciones (la portada de antes de W12b). */
const TIPOS_CON_ANDEN = new Set<TipoSeccion>(['disciplinas', 'galeria', 'contacto']);

/** El ancla de la sección: `#sedes` ya es la lista de sedes del contacto (menú «Sedes»). */
function idSeccion(tipo: TipoSeccion, clave: string): string {
  if (tipo === 'texto_foto') {
    return clave.replace(/[_.]/g, '-');
  }
  return tipo === 'sedes' ? 'fotos-sedes' : tipo;
}

/** Formas de la galería del boceto, en ciclo de seis. */
const FORMAS_GALERIA = ['g-grande', '', 'g-alta', '', 'g-ancha', 'g-ancha'];

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic'];

export function vistaPortada(sitio: SitioWeb, ahora: Date): VistaPortada {
  const textos = new TextosTema(sitio);
  const { bloques } = textos;
  const sedes = sitio.sedes;
  const una = sedes.length === 1;
  const edad = edadDeInicio(sedes);
  const disciplinas = disciplinasDe(sitio);
  const lugares = una ? sedes[0].nombre : `${numeroEnLetras(sedes.length)} pueblos`;

  const nombresDisciplinas = disciplinas.map((d) => d.nombre);
  const rotuloSedes = sedes.length ? `${sedes.length} ${una ? 'sede' : 'sedes'}` : '';
  const rotulo = [
    nombresDisciplinas.length ? `Escuela de ${enLista(nombresDisciplinas.map((n) => n.toLowerCase()))}` : '',
    rotuloSedes,
  ]
    .filter(Boolean)
    .join(' · ');
  const disciplinaTitular = nombresDisciplinas.length ? enLista(nombresDisciplinas) : sitio.escuela.nombre;
  const titularPorDefecto = sedes.length ? `${disciplinaTitular} en ${lugares}` : disciplinaTitular;
  const desde = edad != null ? ` desde los ${edad} años` : '';
  const frasePorDefecto = una
    ? `Grupos por edad${desde}, en ${sedes[0].nombre}. Mira el horario, elige grupo y preinscríbete.`
    : `Grupos por edad${desde}, en el pabellón o el colegio de cada pueblo. Busca el tuyo y preinscríbete.`;

  const horarios = sedes.length ? vistaHorarios(sitio, textos, ahora) : null;
  const tarifa = tarifaPrincipal(sitio, ahora);

  let numero = horarios ? 1 : 0;
  const siguiente = () => String(++numero).padStart(2, '0');
  const secciones: VistaSeccion[] = [];
  let conFondo = 0;
  for (const { clave, tipo } of ordenarSecciones(seccionesDelTema(bloques), bloques)) {
    if (OCULTABLES.has(tipo) && bloques.oculto(clave)) {
      continue;
    }
    const seccion = construirSeccion(tipo, clave, { sitio, textos, ahora, una, edad, disciplinas, tarifa, siguiente, lugares });
    if (seccion) {
      // Con cabeceras, alternan (boceto del editor); sin ellas, el fondo es fijo por tipo, como antes de W12b.
      const anden = bloques.hayCabeceras() ? tipo !== 'tarifas' && conFondo++ % 2 === 1 : TIPOS_CON_ANDEN.has(tipo);
      secciones.push({ ...seccion, id: idSeccion(tipo, clave), anden });
    }
  }

  return {
    una,
    rotulo,
    titular: bloques.texto('portada.titular', titularPorDefecto),
    frase: bloques.texto('portada.frase', frasePorDefecto),
    botonHorarios: horarios
      ? bloques.texto('portada.boton_horarios', una ? FIJOS.botonHorariosUnaSede : TEXTOS_LINEA_COMARCAL['portada.boton_horarios'])
      : '',
    enlaceTarifas: tarifa ? textos.t('portada.enlace_tarifas') || null : null,
    // La descripción es el texto del bloque de la foto; `portada.alt` es solo respaldo de filas anteriores a W12a.
    foto: fotoPortada(bloques),
    horarios: horarios ? { ...horarios, numero: '01' } : null,
    secciones,
  };
}

function fotoPortada(bloques: BloquesWeb): ImagenBloque | null {
  const foto = bloques.imagen('portada.foto');
  if (foto && !foto.alt) {
    const antigua = bloques.textoPropio('portada.alt')?.trim();
    return antigua ? { ...foto, alt: antigua } : foto;
  }
  return foto;
}

interface Contexto {
  sitio: SitioWeb;
  textos: TextosTema;
  ahora: Date;
  una: boolean;
  edad: number | null;
  disciplinas: { codigo: string; nombre: string; edad: number | null }[];
  tarifa: { importe: number; igual: boolean } | null;
  siguiente: () => string;
  lugares: string;
}

/**
 * Cada sección, o null si no tiene nada que pintar (§9 n.º 11; vacía como `estaVacio()` del boceto del editor). El
 * número solo se gasta si se pinta.
 */
function construirSeccion(tipo: TipoSeccion, clave: string, x: Contexto): CuerpoSeccion | null {
  const { sitio, textos } = x;
  const { bloques } = textos;
  switch (tipo) {
    case 'presentacion': {
      const lugaresTexto = x.una ? x.lugares : `${x.lugares} de la comarca`;
      const porDefecto =
        `Damos clase en ${lugaresTexto}, en el pabellón o el colegio de cada localidad. Cada familia elige la sede que ` +
        'le queda cerca y el horario que le encaja, sin coger el coche para entrenar.\n\n' +
        `Los grupos van por edad${x.edad != null ? `, desde los ${x.edad} años hasta adultos` : ''}. Los grados se ` +
        'examinan en el propio club, y quien quiere compite en campeonatos de la provincia; quien no, entrena igual, a ' +
        'su ritmo y con su grupo.';
      const parrafos = enParrafos(bloques.texto('presentacion.texto', sitio.sedes.length ? porDefecto : ''));
      const foto = bloques.imagen('presentacion.foto');
      // Vacía (boceto del editor, `estaVacio`): sin texto y sin foto.
      if (!parrafos.length && !foto) {
        return null;
      }
      return {
        tipo,
        numero: x.siguiente(),
        rotulo: textos.t('presentacion.rotulo'),
        titulo: textos.t('presentacion.titulo'),
        parrafos,
        foto,
        datos: datosDelClub(sitio, x),
      };
    }
    case 'disciplinas': {
      const disciplinas = x.disciplinas.map((d) => vistaDisciplina(d, bloques));
      return disciplinas.length
        ? { tipo, numero: x.siguiente(), rotulo: textos.t('disciplinas.titulo'), disciplinas }
        : null;
    }
    case 'profesores': {
      const profesores = bloques.profesores().map((p) => ({ ...p, monograma: monograma(p.nombre) }));
      return profesores.length
        ? { tipo, numero: x.siguiente(), rotulo: textos.t('profesores.rotulo'), titulo: textos.t('profesores.titulo'), profesores }
        : null;
    }
    case 'galeria': {
      const fotos = bloques.galeria().map((f, i) => ({
        ...f,
        forma: FORMAS_GALERIA[i % FORMAS_GALERIA.length],
        numero: String(i + 1).padStart(2, '0'),
      }));
      return fotos.length ? { tipo, numero: x.siguiente(), rotulo: textos.t('galeria.rotulo'), fotos } : null;
    }
    case 'sedes': {
      // Vacía si ninguna sede tiene su foto 1 (boceto del editor, `estaVacio`).
      if (!sitio.sedes.some((s) => s.fotoUrl)) {
        return null;
      }
      return {
        tipo,
        numero: x.siguiente(),
        rotulo: textos.t('sedes.rotulo'),
        titulo: textos.t('sedes.titulo'),
        verSede: textos.t('sedes.ver_sede'),
        sedes: sitio.sedes.map((s) => ({
          clave: claveSede(s),
          nombre: s.nombre,
          lugar: s.lugar?.trim() || null,
          foto: s.fotoUrl ? { url: s.fotoUrl, alt: altFotoSede(s, 1) } : null,
          enlace: s.slug ? `/sedes/${s.slug}` : null,
        })),
      };
    }
    case 'texto_foto': {
      // Repetible y sin textos por defecto (boceto del editor, `nuevoBloque`); vacío sin título, texto ni foto.
      const titulo = bloques.textoPropio(`${clave}.titulo`) ?? '';
      const parrafos = enParrafos(bloques.textoPropio(`${clave}.texto`) ?? '');
      const foto = bloques.imagen(`${clave}.foto`);
      if (!titulo && !parrafos.length && !foto) {
        return null;
      }
      return { tipo, numero: x.siguiente(), rotulo: bloques.textoPropio(`${clave}.rotulo`) ?? '', titulo, parrafos, foto };
    }
    case 'eventos': {
      const eventos = sitio.proximosEventos.filter((e) => e.fecha).slice(0, 3).map(vistaEvento);
      return eventos.length
        ? {
            tipo,
            numero: x.siguiente(),
            rotulo: textos.t('eventos.rotulo'),
            titulo: textos.t('eventos.titulo'),
            enlace: textos.t('eventos.enlace_ver'),
            eventos,
          }
        : null;
    }
    case 'tarifas': {
      if (!x.tarifa) {
        return null;
      }
      const puntos = [textos.t('tarifas.punto1'), textos.t('tarifas.punto2')];
      for (let n = 3; n <= 20; n++) {
        puntos.push(bloques.textoPropio(`tarifas.punto${n}`) ?? '');
      }
      return {
        tipo,
        numero: x.siguiente(),
        titulo: textos.t('tarifas.titulo'),
        importe: importeEnTexto(x.tarifa.importe),
        alMes: textos.t('tarifas.al_mes'),
        igual: x.tarifa.igual
          ? bloques.texto('tarifas.igual', x.una ? FIJOS.igualUnaSede : FIJOS.igualEnTodasLasSedes) || null
          : null,
        puntos: puntos.filter(Boolean),
        boton: textos.t('tarifas.boton_preinscripcion'),
      };
    }
    case 'contacto': {
      const email = sitio.contacto.email?.trim();
      const telefono = sitio.contacto.telefono?.trim();
      if (!sitio.sedes.length && !email && !telefono) {
        return null;
      }
      return {
        tipo,
        numero: x.siguiente(),
        rotulo: textos.t('contacto.rotulo'),
        titulo: textos.t('contacto.titulo'),
        correo: email ? { rotulo: textos.t('contacto.correo_rotulo'), texto: email, enlace: `mailto:${email}` } : null,
        telefono: telefono
          ? { rotulo: textos.t('contacto.telefono_rotulo'), texto: telefonoVisible(telefono), enlace: telefonoEnlace(telefono) }
          : null,
        sedesTitulo: textos.t('contacto.sedes_titulo'),
        sedes: sitio.sedes.map((s) => ({
          clave: claveSede(s),
          nombre: s.nombre,
          enlace: s.slug ? `/sedes/${s.slug}` : null,
          detalle: [s.lugar, s.direccion].map((v) => v?.trim()).filter(Boolean).join(' · '),
        })),
      };
    }
  }
}

function vistaHorarios(sitio: SitioWeb, textos: TextosTema, ahora: Date): Omit<VistaHorarios, 'numero'> {
  const apoyoPropio = textos.bloques.tiene('horarios.apoyo') ? textos.bloques.texto('horarios.apoyo', '') : null;
  return {
    titulo: textos.t('horarios.titulo'),
    apoyoMovil: apoyoPropio ?? FIJOS.apoyoHorariosMovil,
    apoyoEscritorio: apoyoPropio ?? FIJOS.apoyoHorariosEscritorio,
    verSede: textos.t('horarios.ver_sede'),
    comoLlegar: textos.t('horarios.como_llegar'),
    proximaRotulo: textos.t('sedes.proxima_rotulo'),
    sedes: sitio.sedes.map((sede) => {
      const localidad = sede.localidad?.trim() || sede.nombre;
      const direccion = sede.direccion?.trim() ? `${sede.direccion.trim()}, ${localidad}` : null;
      return {
        id: sede.id,
        clave: claveSede(sede),
        nombre: sede.nombre,
        lugar: sede.lugar?.trim() || null,
        direccion,
        enlaceSede: sede.slug ? `/sedes/${sede.slug}` : null,
        mapa: sede.lugar?.trim() || sede.direccion?.trim() ? enlaceMapa([sede.lugar, sede.direccion, localidad]) : null,
        proxima: proximaClase(sede, ahora),
        salidas: vistaSalidas(sede, textos, ahora),
      };
    }),
  };
}

/** Las salidas de una sede (tablero de la portada y de la página de sede). */
export function vistaSalidas(sede: SedeWeb, textos: TextosTema, ahora: Date): VistaSalida[] {
  const textosPlazas = { lleno: textos.t('horarios.lleno'), espera: textos.t('horarios.espera') };
  const hoy = ahora.getDay();
  const salidas = salidasDeSede(sede);
  return salidas.map((s) => {
    const estado = estadoPlazas(s.plazas);
    const edades = edadesEnTexto(s.grupo.rangoEdadMin, s.grupo.rangoEdadMax);
    const quien = [s.grupo.nombre, edades?.toLowerCase(), sede.nombre].filter(Boolean).join(', ');
    const espera = estado === 'lleno' || estado === 'espera';
    const texto = espera ? textos.t('horarios.boton_espera') : textos.t('horarios.boton');
    return {
      clave: `${s.grupo.id}-${s.inicio}-${s.fin}`,
      inicio: s.inicio,
      fin: s.fin,
      grupo: s.grupo.nombre,
      edades,
      disciplina: s.grupo.disciplinaNombre,
      hoy: s.dias.includes(hoy),
      dias: DIAS_SEMANA.map((d) => ({ letra: d.letra, si: s.dias.includes(d.numero), hoy: d.numero === hoy })),
      diasTexto: diasEnTexto(s.dias),
      plazas: estado,
      plazasTexto: textoPlazas(s.plazas, textosPlazas),
      accion: {
        texto,
        secundaria: espera,
        etiqueta: `${texto}: ${quien}`,
        consulta: {
          sede: sede.slug ?? String(sede.id),
          grupo: String(s.grupo.id),
          // Un grupo a varias horas: la preinscripción elige la primera salida del grupo; las demás, por sus días.
          ...(salidas.find((x) => x.grupo.id === s.grupo.id) === s ? {} : { dias: s.dias.join(',') }),
        },
      },
    };
  });
}

function datosDelClub(sitio: SitioWeb, x: Contexto): VistaDato[] {
  const datos: VistaDato[] = [];
  if (sitio.sedes.length) {
    datos.push({ cifra: String(sitio.sedes.length), etiqueta: x.una ? 'sede' : 'sedes' });
  }
  if (x.edad != null) {
    datos.push({ cifra: String(x.edad), etiqueta: 'años, edad de inicio' });
  }
  if (x.tarifa) {
    datos.push({ cifra: importeEnTexto(x.tarifa.importe), etiqueta: x.textos.t('tarifas.al_mes') });
  }
  const grupos = gruposPorSede(sitio.sedes);
  if (grupos) {
    datos.push({
      cifra: String(grupos.maximo),
      etiqueta: grupos.iguales ? 'grupos por sede' : 'grupos por sede, como máximo',
    });
  }
  return datos;
}

/** Las disciplinas de los grupos, en el orden en que aparecen, con su edad de inicio. */
function disciplinasDe(sitio: SitioWeb): { codigo: string; nombre: string; edad: number | null }[] {
  const porCodigo = new Map<string, { codigo: string; nombre: string; edad: number | null }>();
  for (const grupo of sitio.sedes.flatMap((s) => s.grupos)) {
    const actual = porCodigo.get(grupo.disciplina) ?? { codigo: grupo.disciplina, nombre: grupo.disciplinaNombre, edad: null };
    if (grupo.rangoEdadMin != null && (actual.edad == null || grupo.rangoEdadMin < actual.edad)) {
      actual.edad = grupo.rangoEdadMin;
    }
    porCodigo.set(grupo.disciplina, actual);
  }
  return [...porCodigo.values()];
}

function vistaDisciplina(d: { codigo: string; nombre: string; edad: number | null }, bloques: BloquesWeb): VistaDisciplina {
  const clave = `disciplina.${d.codigo.toLowerCase()}`;
  const esTaekwondo = d.codigo.toLowerCase() === 'taekwondo';
  const parrafos = enParrafos(bloques.texto(`${clave}.descripcion`, esTaekwondo ? DISCIPLINA_TAEKWONDO.descripcion : ''));
  const numeros = new Set<number>(esTaekwondo ? DISCIPLINA_TAEKWONDO.filas.map((_, i) => i + 1) : []);
  for (const n of filasConClave(bloques, clave)) {
    numeros.add(n);
  }
  const filas = [...numeros]
    .sort((a, b) => a - b)
    .map((n) => {
      const defecto = esTaekwondo ? DISCIPLINA_TAEKWONDO.filas[n - 1] : undefined;
      const textoDefecto = defecto
        ? 'sinEdad' in defecto && d.edad == null
          ? defecto.sinEdad
          : defecto.texto.replace('{edad}', String(d.edad))
        : '';
      return {
        rotulo: bloques.texto(`${clave}.fila${n}.rotulo`, defecto?.rotulo ?? ''),
        texto: bloques.texto(`${clave}.fila${n}.texto`, textoDefecto),
      };
    })
    .filter((f) => f.texto);
  return { codigo: d.codigo, nombre: d.nombre, parrafos, filas, foto: bloques.imagen(`${clave}.foto`) };
}

/** Los n de `disciplina.<codigo>.fila<n>.*`. */
function filasConClave(bloques: BloquesWeb, clave: string): number[] {
  const numeros: number[] = [];
  for (let n = 1; n <= 20; n++) {
    if (bloques.tiene(`${clave}.fila${n}.texto`) || bloques.tiene(`${clave}.fila${n}.rotulo`)) {
      numeros.push(n);
    }
  }
  return numeros;
}

function vistaEvento(evento: EventoWeb): VistaEvento {
  const [anio, mes, dia] = evento.fecha!.split('-').map(Number);
  const fecha = new Date(anio, mes - 1, dia);
  const lugar = [evento.lugar, evento.sedeNombre].map((v) => v?.trim()).filter(Boolean).join(', ');
  return {
    id: evento.id,
    titulo: evento.titulo,
    fecha: evento.fecha!,
    dia: `${dia} ${MESES[mes - 1]}`,
    semana: nombreDia(fecha.getDay()),
    lugar: lugar || null,
    hora: evento.hora?.trim() || null,
  };
}

/**
 * La mensualidad de la portada: la primera tarifa con importe (la API las da por su orden). «Igual en todas las sedes»
 * solo si ninguna sede tiene hoy un importe propio distinto.
 */
export function tarifaPrincipal(sitio: SitioWeb, hoy = new Date()): { importe: number; igual: boolean } | null {
  const tarifa = sitio.tarifas.find((t) => t.importe != null);
  if (!tarifa) {
    return null;
  }
  const hoyIso = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
  const igual = !sitio.tarifasSede.some(
    (ts) =>
      ts.tarifaId === tarifa.id &&
      ts.importe != null &&
      ts.importe !== tarifa.importe &&
      (ts.vigenteDesde == null || ts.vigenteDesde <= hoyIso),
  );
  return { importe: tarifa.importe!, igual };
}

/** Sede recordada (fase4-web-plan.md §6, «Legales»: almacenamiento técnico, sin aviso de cookies). La guardan la línea de
 * la portada y la página de sede. */
export const CLAVE_SEDE_RECORDADA = 'linea-comarcal:sede';

export function claveSede(sede: SedeWeb): string {
  return sede.slug ?? `sede-${sede.id}`;
}

/** Descripción de la foto 1 o 2 de una sede (d523): la suya o, vacía, «Foto de la sede de {nombre}». */
export function altFotoSede(sede: SedeWeb, n: 1 | 2): string {
  const propia = (n === 1 ? sede.fotoAlt : sede.foto2Alt)?.trim();
  return propia || TEXTOS_PAGINAS_LINEA_COMARCAL.fotoSede.replace('{x}', () => sede.nombre);
}

function enParrafos(texto: string): string[] {
  return texto
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);
}

function enLista(partes: string[]): string {
  return partes.length <= 1 ? partes.join('') : `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`;
}

function monograma(nombre: string): string {
  const partes = nombre.split(/\s+/).filter(Boolean);
  return (partes[0]?.charAt(0) ?? '') + (partes[1]?.charAt(0) ?? '');
}
