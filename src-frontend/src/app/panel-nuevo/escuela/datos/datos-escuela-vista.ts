/**
 * «Escuela · Datos de la escuela» (pieza 394, boceto `docs/multi-escuela/boceto-escuela-datos.html`): campos, límites,
 * formatos y mensajes. Reproducen los de `DatosEscuelaRequest` de la API (que valida de todos modos): el cliente avisa
 * antes, con las mismas palabras.
 */

export type ClaveDato =
  | 'nombreLegal'
  | 'cif'
  | 'direccion'
  | 'emailContacto'
  | 'telefono'
  | 'firmanteNombre'
  | 'firmanteCargo'
  | 'firmanteNif'
  | 'diaLimiteMensualidad';

/** Orden del formulario (y el de los errores de la API: el primero va en el aviso). */
export const ORDEN_DATOS: readonly ClaveDato[] = [
  'nombreLegal',
  'cif',
  'direccion',
  'emailContacto',
  'telefono',
  'firmanteNombre',
  'firmanteCargo',
  'firmanteNif',
  'diaLimiteMensualidad',
];

/** Lo que lleva el formulario: todo texto, también el día. */
export type ValoresDatos = Record<ClaveDato, string>;

/** `GET` y `PUT /api/escuela/datos` (`DatosEscuelaDTO`): un dato sin rellenar llega a `null`. */
export interface DatosEscuela {
  nombreLegal: string | null;
  cif: string | null;
  direccion: string | null;
  emailContacto: string | null;
  telefono: string | null;
  firmanteNombre: string | null;
  firmanteCargo: string | null;
  firmanteNif: string | null;
  diaLimiteMensualidad: number;
}

/** Cuerpo del `PUT` (`DatosEscuelaRequest`): formulario completo; lo vacío va a `null`. */
export interface SolicitudDatosEscuela extends Omit<DatosEscuela, 'diaLimiteMensualidad'> {
  diaLimiteMensualidad: number | null;
}

export interface CampoDato {
  etiqueta: string;
  obligatorio: boolean;
  /** Máximo de caracteres con contador visible. */
  max?: number;
  /** Máximo de caracteres sin contador (formatos cortos). */
  maxlength?: number;
  tipo?: 'text' | 'email' | 'tel';
  autocompletar?: string;
  mayusculas?: boolean;
  /** Campo corto (CIF, NIF): no ocupa todo el ancho. */
  corto?: boolean;
  ayuda?: string;
  falta?: string;
  mal?: string;
  /** Más largo que el máximo. */
  largo?: string;
}

export const DIA_NO_VALIDO = 'Escribe un día del 1 al 28.';
export const DIA_POR_DEFECTO = '5';

export const CAMPOS_DATOS: Readonly<Record<ClaveDato, CampoDato>> = {
  nombreLegal: {
    etiqueta: 'Nombre legal',
    obligatorio: true,
    max: 150,
    autocompletar: 'organization',
    falta: 'El nombre legal es obligatorio.',
    largo: 'El nombre legal no puede tener más de 150 caracteres.',
    mal: 'El nombre legal tiene caracteres no válidos.',
  },
  cif: {
    etiqueta: 'CIF o NIF',
    obligatorio: true,
    maxlength: 9,
    mayusculas: true,
    corto: true,
    ayuda: '9 caracteres, sin espacios ni guiones. Ej.: G12345678.',
    falta: 'El CIF es obligatorio.',
    mal: 'El CIF no tiene un formato válido.',
  },
  direccion: {
    etiqueta: 'Dirección fiscal',
    obligatorio: true,
    max: 255,
    autocompletar: 'street-address',
    falta: 'La dirección fiscal es obligatoria.',
    largo: 'La dirección fiscal no puede tener más de 255 caracteres.',
    mal: 'La dirección fiscal tiene caracteres no válidos.',
  },
  emailContacto: {
    etiqueta: 'Email de contacto',
    obligatorio: true,
    maxlength: 180,
    tipo: 'email',
    autocompletar: 'email',
    ayuda: 'Las familias lo ven en la web y responden a él.',
    falta: 'El email de contacto es obligatorio.',
    largo: 'El email no puede tener más de 180 caracteres.',
    mal: 'El email no tiene un formato válido.',
  },
  telefono: {
    etiqueta: 'Teléfono',
    obligatorio: false,
    maxlength: 16,
    tipo: 'tel',
    autocompletar: 'tel',
    mal: 'El teléfono solo lleva números y espacios.',
  },
  firmanteNombre: {
    etiqueta: 'Nombre y apellidos',
    obligatorio: true,
    max: 100,
    falta: 'El nombre del firmante es obligatorio.',
    largo: 'El nombre del firmante no puede tener más de 100 caracteres.',
    mal: 'El nombre del firmante tiene caracteres no válidos.',
  },
  firmanteCargo: {
    etiqueta: 'Cargo',
    obligatorio: true,
    max: 60,
    ayuda: 'Ej.: Presidenta, Director técnico.',
    falta: 'El cargo es obligatorio.',
    largo: 'El cargo no puede tener más de 60 caracteres.',
    mal: 'El cargo tiene caracteres no válidos.',
  },
  firmanteNif: {
    etiqueta: 'NIF',
    obligatorio: true,
    maxlength: 9,
    mayusculas: true,
    corto: true,
    ayuda: '8 números y una letra. Ej.: 12345678Z.',
    falta: 'El NIF del firmante es obligatorio.',
    mal: 'El NIF no tiene un formato válido.',
  },
  diaLimiteMensualidad: {
    etiqueta: 'Día límite de pago de la mensualidad',
    obligatorio: true,
    maxlength: 2,
    falta: DIA_NO_VALIDO,
    mal: DIA_NO_VALIDO,
  },
};

/** CIF de entidad (letra, 7 cifras y control) o NIF/NIE de persona. Solo formato, no la letra de control. */
const CIF = /^(?:[ABCDEFGHJNPQRSUVW]\d{7}[0-9A-J]|[0-9XYZ]\d{7}[A-Z])$/;
const NIF = /^[0-9XYZ]\d{7}[A-Z]$/;
const EMAIL = /^[^\x09-\x0D\x20@]+@[^\x09-\x0D\x20@]+\.[^\x09-\x0D\x20@]+$/;
const TELEFONO = /^\+?[0-9 ]{9,15}$/;
/** Caracteres de control, de formato o invisibles y separadores de línea y de párrafo (los de la API). */
const CONTROL = /[\p{C}\p{Zl}\p{Zp}]/u;
/** Lo que `Character.isWhitespace` de Java marca (el `strip()` de la API): sin U+00A0, U+2007 ni U+202F. */
const ESPACIO_JAVA = '\\t\\n\\u000B\\f\\r\\u001C-\\u001F\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000';
const EXTREMOS_JAVA = new RegExp(`^[${ESPACIO_JAVA}]+|[${ESPACIO_JAVA}]+$`, 'g');

/** Como `String.strip()` de Java: quita los espacios de los extremos (no `trim()`, que deja pasar otros). */
export function recortar(texto: string): string {
  return texto.replace(EXTREMOS_JAVA, '');
}

export function valoresVacios(): ValoresDatos {
  return {
    nombreLegal: '',
    cif: '',
    direccion: '',
    emailContacto: '',
    telefono: '',
    firmanteNombre: '',
    firmanteCargo: '',
    firmanteNif: '',
    diaLimiteMensualidad: DIA_POR_DEFECTO,
  };
}

/** De la API al formulario: `null` pasa a vacío y el día a texto. */
export function valoresDe(datos: DatosEscuela): ValoresDatos {
  return {
    nombreLegal: datos.nombreLegal ?? '',
    cif: datos.cif ?? '',
    direccion: datos.direccion ?? '',
    emailContacto: datos.emailContacto ?? '',
    telefono: datos.telefono ?? '',
    firmanteNombre: datos.firmanteNombre ?? '',
    firmanteCargo: datos.firmanteCargo ?? '',
    firmanteNif: datos.firmanteNif ?? '',
    diaLimiteMensualidad: String(datos.diaLimiteMensualidad ?? DIA_POR_DEFECTO),
  };
}

/** Escuela sin estrenar: ningún dato fiscal ni de contacto (el aviso «Rellena estos datos…» del boceto). */
export function escuelaSinRellenar(valores: ValoresDatos): boolean {
  return (['nombreLegal', 'cif', 'direccion', 'emailContacto'] as const).every((k) => recortar(valores[k]) === '');
}

/** El error del campo, o cadena vacía si vale. Vacío y opcional vale; vacío y obligatorio, no. */
export function errorDeDato(clave: ClaveDato, valor: string): string {
  const campo = CAMPOS_DATOS[clave];
  const limpio = recortar(valor ?? '');
  if (limpio === '') {
    return campo.obligatorio ? (campo.falta ?? '') : '';
  }
  if (clave === 'diaLimiteMensualidad') {
    return /^\d{1,2}$/.test(limpio) && +limpio >= 1 && +limpio <= 28 ? '' : DIA_NO_VALIDO;
  }
  const maximo = campo.max ?? (clave === 'emailContacto' ? campo.maxlength : undefined);
  if (maximo !== undefined && limpio.length > maximo) {
    return campo.largo ?? '';
  }
  if (clave === 'cif') {
    return CIF.test(limpio.toUpperCase()) ? '' : (campo.mal ?? '');
  }
  if (clave === 'firmanteNif') {
    return NIF.test(limpio.toUpperCase()) ? '' : (campo.mal ?? '');
  }
  if (clave === 'emailContacto') {
    return EMAIL.test(limpio) ? '' : (campo.mal ?? '');
  }
  if (clave === 'telefono') {
    return TELEFONO.test(limpio) ? '' : (campo.mal ?? '');
  }
  return CONTROL.test(limpio) ? (campo.mal ?? '') : '';
}

/** Todos los errores del formulario, en el orden de la pantalla. */
export function erroresDeDatos(valores: ValoresDatos): Partial<Record<ClaveDato, string>> {
  const errores: Partial<Record<ClaveDato, string>> = {};
  for (const clave of ORDEN_DATOS) {
    const error = errorDeDato(clave, valores[clave]);
    if (error) {
      errores[clave] = error;
    }
  }
  return errores;
}

/** Los campos cuyo valor no es el guardado. */
export function clavesCambiadas(valores: ValoresDatos, guardados: ValoresDatos): ClaveDato[] {
  return ORDEN_DATOS.filter((k) => valores[k] !== guardados[k]);
}

/** Recortado; vacío tras recortar, `null`. */
function valorONull(valor: string): string | null {
  const limpio = recortar(valor);
  return limpio === '' ? null : limpio;
}

/** Del formulario al cuerpo del `PUT`: espacios recortados, vacío a `null`, CIF y NIF en mayúsculas, día en número. */
export function solicitudDe(valores: ValoresDatos): SolicitudDatosEscuela {
  const mayusculas = (v: string) => valorONull(v)?.toUpperCase() ?? null;
  const dia = recortar(valores.diaLimiteMensualidad);
  return {
    nombreLegal: valorONull(valores.nombreLegal),
    cif: mayusculas(valores.cif),
    direccion: valorONull(valores.direccion),
    emailContacto: valorONull(valores.emailContacto),
    telefono: valorONull(valores.telefono),
    firmanteNombre: valorONull(valores.firmanteNombre),
    firmanteCargo: valorONull(valores.firmanteCargo),
    firmanteNif: mayusculas(valores.firmanteNif),
    diaLimiteMensualidad: /^\d{1,2}$/.test(dia) ? Number(dia) : null,
  };
}

/** Los errores por campo de un 400 de la API (`{mensaje, errores{campo: mensaje}}`); ignora claves que no conoce. */
export function erroresDeApi(cuerpo: unknown): Partial<Record<ClaveDato, string>> {
  const errores = (cuerpo as { errores?: unknown } | null)?.errores;
  const resultado: Partial<Record<ClaveDato, string>> = {};
  if (errores && typeof errores === 'object') {
    for (const clave of ORDEN_DATOS) {
      const mensaje = (errores as Record<string, unknown>)[clave];
      if (typeof mensaje === 'string' && mensaje.trim()) {
        resultado[clave] = mensaje.trim();
      }
    }
  }
  return resultado;
}
