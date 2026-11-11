import { MAX_BYTES_SUBIDA, MOTIVO_HEIC, MOTIVO_LECTURA, OpcionesSubida } from '../../utils/subida-archivos.util';

/**
 * Pantalla Sedes del panel nuevo (E9, piezas 9-3 y 9-7): tipos, reglas y textos sin Angular. `PUT /api/sedes/{id}`
 * REEMPLAZA todos los datos editables, así que el cuerpo de una edición siempre lleva el `aforoTurno` del formulario.
 */

/** `SedeDTO` de `GET /api/sedes`. */
export interface Sede {
  id: number;
  nombre: string;
  direccion: string | null;
  localidad: string | null;
  codigoPostal: string | null;
  telefono: string | null;
  email: string | null;
  codigoClubFederacion: string | null;
  aforoTurno: number | null;
  activa: boolean;
  /** Web por plantilla (W2, W12a): opcionales en la respuesta; null si no se han puesto. */
  slug?: string | null;
  lugar?: string | null;
  indicacionesAcceso?: string | null;
  fotoImagenId?: number | null;
  foto2ImagenId?: number | null;
  fotoAlt?: string | null;
  foto2Alt?: string | null;
}

/** Respuesta de `PUT /api/sedes/{id}`: `turnosSobreAforo`, el aforo cambiado queda por debajo de la ocupación de algún turno. */
export interface SedeEditada extends Sede {
  turnosSobreAforo?: boolean;
}

/** `SedeRequest` de `POST` y `PUT /api/sedes`. */
export interface CuerpoSede {
  nombre: string;
  direccion: string | null;
  localidad: string | null;
  codigoPostal: string | null;
  telefono: string | null;
  email: string | null;
  codigoClubFederacion: string | null;
  aforoTurno: number | null;
}

export type ClaveCampo =
  | 'nombre'
  | 'direccion'
  | 'localidad'
  | 'codigoPostal'
  | 'telefono'
  | 'email'
  | 'codigoClubFederacion'
  | 'aforoTurno';

export type CamposSede = Record<ClaveCampo, string>;

export interface DefinicionCampo {
  clave: ClaveCampo;
  etiqueta: string;
  maximo: number;
  type: 'text' | 'email' | 'tel';
  autocomplete: string;
  /** Texto de ayuda bajo el control. */
  ayuda?: string;
  /** `inputmode` del control. */
  modoEntrada?: 'numeric';
}

/** Los campos del panel, en orden y con las etiquetas aprobadas. */
export const CAMPOS_SEDE: readonly DefinicionCampo[] = [
  { clave: 'nombre', etiqueta: 'Nombre (obligatorio)', maximo: 100, type: 'text', autocomplete: 'off' },
  { clave: 'direccion', etiqueta: 'Dirección', maximo: 255, type: 'text', autocomplete: 'off' },
  { clave: 'localidad', etiqueta: 'Localidad', maximo: 100, type: 'text', autocomplete: 'off' },
  { clave: 'codigoPostal', etiqueta: 'Código postal', maximo: 10, type: 'text', autocomplete: 'off' },
  { clave: 'telefono', etiqueta: 'Teléfono', maximo: 20, type: 'tel', autocomplete: 'off' },
  { clave: 'email', etiqueta: 'Email', maximo: 180, type: 'email', autocomplete: 'off' },
  { clave: 'codigoClubFederacion', etiqueta: 'Código del club en la federación', maximo: 40, type: 'text', autocomplete: 'off' },
  {
    clave: 'aforoTurno',
    etiqueta: 'Aforo por turno (opcional)',
    maximo: 6,
    type: 'text',
    autocomplete: 'off',
    ayuda: 'Si lo dejas vacío, se usa el límite común de la escuela.',
    modoEntrada: 'numeric',
  },
];

export const AVISO_SEGUNDA_SEDE =
  'Será la segunda sede de la escuela. Desde entonces, las altas de alumnos y grupos pedirán sede y la caja se llevará por sede.';

export const AVISO_AFORO_BAJO =
  'Hay turnos con más inscritos que este aforo: no se da de baja a nadie, pero no entrarán nuevas plazas.';

export const TEXTO_DESACTIVAR = 'No admitirá grupos, turnos ni cargos nuevos. Lo ya guardado se conserva.';

export const TEXTO_UNICA_ACTIVA = 'Es la única sede activa de la escuela.';

// Mismo criterio que el servidor (`GestionSedesService`): algo@dominio.ext, sin espacios.
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// Entero positivo, como exige el servidor.
const AFORO = /^0*[1-9]\d*$/;

export function camposVacios(): CamposSede {
  return { nombre: '', direccion: '', localidad: '', codigoPostal: '', telefono: '', email: '', codigoClubFederacion: '', aforoTurno: '' };
}

export function camposDe(sede: Sede | null): CamposSede {
  if (!sede) {
    return camposVacios();
  }
  return {
    nombre: sede.nombre ?? '',
    direccion: sede.direccion ?? '',
    localidad: sede.localidad ?? '',
    codigoPostal: sede.codigoPostal ?? '',
    telefono: sede.telefono ?? '',
    email: sede.email ?? '',
    codigoClubFederacion: sede.codigoClubFederacion ?? '',
    aforoTurno: sede.aforoTurno?.toString() ?? '',
  };
}

function aNulo(valor: string): string | null {
  const limpio = valor.trim();
  return limpio === '' ? null : limpio;
}

/** El cuerpo que se envía: textos recortados y vacíos como null; el aforo, ya validado, como número. */
export function cuerpoSede(campos: CamposSede): CuerpoSede {
  const aforo = aNulo(campos.aforoTurno);
  return {
    nombre: campos.nombre.trim(),
    direccion: aNulo(campos.direccion),
    localidad: aNulo(campos.localidad),
    codigoPostal: aNulo(campos.codigoPostal),
    telefono: aNulo(campos.telefono),
    email: aNulo(campos.email),
    codigoClubFederacion: aNulo(campos.codigoClubFederacion),
    aforoTurno: aforo === null ? null : Number(aforo),
  };
}

/** Errores por campo antes de enviar (el servidor vuelve a comprobarlo todo). */
export function validarCampos(campos: CamposSede): Partial<Record<ClaveCampo, string>> {
  const errores: Partial<Record<ClaveCampo, string>> = {};
  if (campos.nombre.trim() === '') {
    errores.nombre = 'Indica el nombre de la sede.';
  }
  for (const campo of CAMPOS_SEDE) {
    if (!errores[campo.clave] && [...campos[campo.clave].trim()].length > campo.maximo) {
      errores[campo.clave] = `No puede pasar de ${campo.maximo} caracteres.`;
    }
  }
  const email = campos.email.trim();
  if (!errores.email && email !== '' && !EMAIL.test(email)) {
    errores.email = 'El email no es válido.';
  }
  const aforo = campos.aforoTurno.trim();
  if (!errores.aforoTurno && aforo !== '' && !AFORO.test(aforo)) {
    errores.aforoTurno = 'Escribe un número entero mayor que 0 o déjalo vacío.';
  }
  return errores;
}

export function totalActivas(sedes: readonly Sede[]): number {
  return sedes.filter((s) => s.activa).length;
}

/** Crear con una sola sede activa convierte la nueva en la segunda. */
export function seraSegundaSede(sedes: readonly Sede[]): boolean {
  return totalActivas(sedes) === 1;
}

/** Activar una inactiva con una sola activa deja dos. */
export function activarCreaSegunda(sedes: readonly Sede[], sede: Sede): boolean {
  return !sede.activa && totalActivas(sedes) === 1;
}

/** La única activa no se puede desactivar (el servidor también lo impide con 409). */
export function esUnicaActiva(sedes: readonly Sede[], sede: Sede): boolean {
  return sede.activa && totalActivas(sedes) === 1;
}

/** Segunda línea de la fila: localidad y teléfono si existen. */
export function lineaSecundaria(sede: Sede): string {
  return [sede.localidad, sede.telefono]
    .map((v) => (v ?? '').trim())
    .filter((v) => v !== '')
    .join(' · ');
}

/** El `mensaje` del servidor si lo trae; si no, el de la pantalla. */
export function mensajeError(error: unknown, porDefecto: string): string {
  const mensaje = (error as { error?: { mensaje?: unknown } } | null)?.error?.mensaje;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje : porDefecto;
}

export function estadoHttp(error: unknown): number | null {
  const estado = (error as { status?: unknown } | null)?.status;
  return typeof estado === 'number' ? estado : null;
}

// ------------------------------------------------------------------ W12g: grupo «En la web» (d523, d524)

/**
 * Los campos de la web de la sede (solo escuelas con web por plantilla; con `legado` no se pintan ni se envían). En el
 * `PUT` el servidor conserva los que no llegan (`SedeRequest.CAMPOS_WEB`); vacíos, se borran. Máximos del boceto
 * aprobado (`boceto-panel-contenido-web-comarcal.html`, `sedesPantallaHtml`); el servidor admite 120 en `lugar`.
 */
export interface CamposWebSede {
  slug: string;
  lugar: string;
  indicacionesAcceso: string;
  fotoAlt: string;
  foto2Alt: string;
}

export type ClaveCampoWeb = keyof CamposWebSede;

export const MAXIMOS_WEB: Readonly<Record<ClaveCampoWeb, number>> = {
  slug: 60,
  lugar: 100,
  indicacionesAcceso: 300,
  fotoAlt: 150,
  foto2Alt: 150,
};

/** Los mensajes del servidor (`GestionSedesService`) que se ven bajo su campo. */
export const SLUG_REPETIDO_SERVIDOR = 'Ya hay una sede con esa dirección web.';
export const SLUG_NO_VALIDO =
  'La dirección web de la sede solo admite minúsculas sin tildes, cifras y guiones entre ellas (hasta 60 caracteres).';

// Como `GestionSedesService.SLUG`.
const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export function camposWebDe(sede: Sede | null): CamposWebSede {
  return {
    slug: sede?.slug ?? '',
    lugar: sede?.lugar ?? '',
    indicacionesAcceso: sede?.indicacionesAcceso ?? '',
    fotoAlt: sede?.fotoAlt ?? '',
    foto2Alt: sede?.foto2Alt ?? '',
  };
}

/** El cuerpo con los campos de la web: recortados, vacíos como null y la dirección en minúsculas (como el servidor). */
export function cuerpoSedeWeb(campos: CamposSede, web: CamposWebSede): CuerpoSede & Record<ClaveCampoWeb, string | null> {
  const slug = aNulo(web.slug);
  return {
    ...cuerpoSede(campos),
    slug: slug === null ? null : slug.toLowerCase(),
    lugar: aNulo(web.lugar),
    indicacionesAcceso: aNulo(web.indicacionesAcceso),
    fotoAlt: aNulo(web.fotoAlt),
    foto2Alt: aNulo(web.foto2Alt),
  };
}

/** La sede de la lista (otra que la abierta) que ya usa esa dirección, o null. */
export function sedeConSlug(sedes: readonly Sede[], slug: string, propia: number | null): Sede | null {
  const buscado = slug.trim().toLowerCase();
  if (!buscado) {
    return null;
  }
  return sedes.find((s) => s.id !== propia && (s.slug ?? '').toLowerCase() === buscado) ?? null;
}

/** «Ya la usa la sede de Estepa. Elige otra.» (boceto); sin la sede, el mensaje del servidor. */
export function textoSlugRepetido(otra: Sede | null): string {
  return otra ? `Ya la usa la sede de ${otra.nombre}. Elige otra.` : SLUG_REPETIDO_SERVIDOR;
}

/** Errores de los campos de la web antes de enviar (el servidor vuelve a comprobarlo todo). */
export function validarCamposWeb(
  web: CamposWebSede,
  sedes: readonly Sede[],
  propia: number | null
): Partial<Record<ClaveCampoWeb, string>> {
  const errores: Partial<Record<ClaveCampoWeb, string>> = {};
  const slug = web.slug.trim().toLowerCase();
  if (slug !== '' && (slug.length > MAXIMOS_WEB.slug || !SLUG.test(slug))) {
    errores.slug = SLUG_NO_VALIDO;
  } else if (slug !== '') {
    const otra = sedeConSlug(sedes, slug, propia);
    if (otra) {
      errores.slug = textoSlugRepetido(otra);
    }
  }
  for (const clave of ['lugar', 'indicacionesAcceso', 'fotoAlt', 'foto2Alt'] as const) {
    if ([...web[clave].trim()].length > MAXIMOS_WEB[clave]) {
      errores[clave] = `No puede pasar de ${MAXIMOS_WEB[clave]} caracteres.`;
    }
  }
  return errores;
}

/**
 * El campo de la web al que va un error del servidor (400 de datos o 409 de dirección repetida), o null si es de otro
 * campo. Por el texto de `GestionSedesService`.
 */
export function campoWebDeError(mensaje: string): ClaveCampoWeb | null {
  if (mensaje === SLUG_REPETIDO_SERVIDOR || mensaje.startsWith('La dirección web de la sede')) {
    return 'slug';
  }
  if (mensaje.startsWith('El lugar de la sede')) {
    return 'lugar';
  }
  if (mensaje.startsWith('Las indicaciones de acceso')) {
    return 'indicacionesAcceso';
  }
  if (mensaje.startsWith('La descripción de la foto 1')) {
    return 'fotoAlt';
  }
  if (mensaje.startsWith('La descripción de la foto 2')) {
    return 'foto2Alt';
  }
  return null;
}

/** Las dos fotos de la sede: 1, la principal; 2, «Cómo llegar». */
export type NumeroFoto = 1 | 2;

export interface TextosFoto {
  nombre: string;
  donde: string;
}

/** Cabecera de cada foto (boceto, `fotoSedeForm`). */
export const TEXTOS_FOTO: Readonly<Record<NumeroFoto, TextosFoto>> = {
  1: { nombre: 'Foto principal', donde: 'Sale arriba en su página y en «Fotos de las sedes».' },
  2: { nombre: 'Segunda foto', donde: 'Sale en «Cómo llegar», en su página.' },
};

export const FORMATOS_FOTO = 'JPG, PNG o WebP, hasta 50 MB';

/** «Foto de la sede de Herrera»: el texto por defecto del tema (`textos.ts`, `fotoSede`) que sale si no hay descripción. */
export function altPorDefecto(nombreSede: string): string {
  return `Foto de la sede de ${nombreSede.trim() || 'esta sede'}`;
}

export function ayudaAlt(nombreSede: string): string {
  return `Qué se ve, para quien no puede verla. Si la dejas vacía, la web dice «${altPorDefecto(nombreSede)}». Sin nombres de alumnos.`;
}

export const AYUDA_SLUG = 'Minúsculas, números y guiones. Sin ella, la sede no tiene página propia en la web.';
export const AYUDA_SLUG_EDITAR = ' Si la cambias, los enlaces que ya hayas compartido dejan de funcionar.';

/** Un fallo al elegir o subir una foto: título y explicación (boceto, `fallo-f`). */
export interface FalloFoto {
  titulo: string;
  texto: string;
}

/** «64 MB»: megas enteros, como el boceto. */
export function megas(bytes: number): string {
  return `${Math.max(1, Math.round(bytes / (1024 * 1024)))} MB`;
}

/** «2,4 MB»: el peso de la foto elegida, con un decimal. */
export function pesoFoto(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  if (mb < 0.1) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${mb.toLocaleString('es-ES', { maximumFractionDigits: 1 })} MB`;
}

/** Fotos de sede: JPEG, PNG o WebP de hasta 50 MB (`SedeFotoService`; HEIC rechazado). */
export const SUBIDA_FOTO_SEDE: OpcionesSubida = {
  formatos: { jpg: ['image/jpeg', 'image/pjpeg'], jpeg: ['image/jpeg', 'image/pjpeg'], png: ['image/png'], webp: ['image/webp'] },
  comprobarMime: false,
  maxBytes: MAX_BYTES_SUBIDA,
  motivoFormato: 'formato',
  motivoTamano: 'tamano',
};

/** El fallo que se enseña en la foto cuando `prepararFicheroParaSubida` la rechaza. */
export function falloAlElegir(motivo: string, archivo: Pick<File, 'name' | 'size'>): FalloFoto {
  if (motivo === SUBIDA_FOTO_SEDE.motivoTamano) {
    return { titulo: 'Demasiado grande', texto: `«${archivo.name}» pesa ${megas(archivo.size)}. Usa una foto de menos de 50 MB.` };
  }
  if (motivo === MOTIVO_HEIC) {
    return { titulo: 'Formato no admitido', texto: `«${archivo.name}»: convierte la foto a JPG o desactiva HEIF en la cámara.` };
  }
  if (motivo === MOTIVO_LECTURA) {
    return { titulo: 'No se pudo leer la foto', texto: MOTIVO_LECTURA };
  }
  return { titulo: 'Formato no admitido', texto: `«${archivo.name}»: usa una foto ${FORMATOS_FOTO}.` };
}
