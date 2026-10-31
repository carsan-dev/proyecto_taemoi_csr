/**
 * Pantalla Sedes del panel nuevo (E9, pieza 9-3): tipos, reglas y textos sin Angular. `PUT /api/sedes/{id}` REEMPLAZA
 * todos los datos editables, así que el cuerpo de una edición siempre lleva el `aforoTurno` que devolvió la API aunque
 * la pantalla no lo muestre (lo usará la pieza 9-7).
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

export type ClaveCampo = 'nombre' | 'direccion' | 'localidad' | 'codigoPostal' | 'telefono' | 'email' | 'codigoClubFederacion';

export type CamposSede = Record<ClaveCampo, string>;

export interface DefinicionCampo {
  clave: ClaveCampo;
  etiqueta: string;
  maximo: number;
  type: 'text' | 'email' | 'tel';
  autocomplete: string;
}

/** Los campos del panel, en orden y con las etiquetas aprobadas. El aforo por turno no se muestra. */
export const CAMPOS_SEDE: readonly DefinicionCampo[] = [
  { clave: 'nombre', etiqueta: 'Nombre (obligatorio)', maximo: 100, type: 'text', autocomplete: 'off' },
  { clave: 'direccion', etiqueta: 'Dirección', maximo: 255, type: 'text', autocomplete: 'off' },
  { clave: 'localidad', etiqueta: 'Localidad', maximo: 100, type: 'text', autocomplete: 'off' },
  { clave: 'codigoPostal', etiqueta: 'Código postal', maximo: 10, type: 'text', autocomplete: 'off' },
  { clave: 'telefono', etiqueta: 'Teléfono', maximo: 20, type: 'tel', autocomplete: 'off' },
  { clave: 'email', etiqueta: 'Email', maximo: 180, type: 'email', autocomplete: 'off' },
  { clave: 'codigoClubFederacion', etiqueta: 'Código del club en la federación', maximo: 40, type: 'text', autocomplete: 'off' },
];

export const AVISO_SEGUNDA_SEDE =
  'Será la segunda sede de la escuela. Desde entonces, las altas de alumnos y grupos pedirán sede y la caja se llevará por sede.';

export const TEXTO_DESACTIVAR = 'No admitirá grupos, turnos ni cargos nuevos. Lo ya guardado se conserva.';

export const TEXTO_UNICA_ACTIVA = 'Es la única sede activa de la escuela.';

// Mismo criterio que el servidor (`GestionSedesService`): algo@dominio.ext, sin espacios.
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function camposVacios(): CamposSede {
  return { nombre: '', direccion: '', localidad: '', codigoPostal: '', telefono: '', email: '', codigoClubFederacion: '' };
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
  };
}

function aNulo(valor: string): string | null {
  const limpio = valor.trim();
  return limpio === '' ? null : limpio;
}

/**
 * El cuerpo que se envía: textos recortados y vacíos como null. `aforoTurno` es el que devolvió la API (o null al
 * crear): el `PUT` reemplaza todo, y omitirlo lo borraría.
 */
export function cuerpoSede(campos: CamposSede, aforoTurno: number | null): CuerpoSede {
  return {
    nombre: campos.nombre.trim(),
    direccion: aNulo(campos.direccion),
    localidad: aNulo(campos.localidad),
    codigoPostal: aNulo(campos.codigoPostal),
    telefono: aNulo(campos.telefono),
    email: aNulo(campos.email),
    codigoClubFederacion: aNulo(campos.codigoClubFederacion),
    aforoTurno: aforoTurno ?? null,
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
