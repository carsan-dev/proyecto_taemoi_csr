import { etiquetaRol } from '../../formato/roles';

/** Una cuenta de `GET /api/admin/users` (la misma forma que lee `ConfiguracionSistemaComponent`). */
export interface CuentaConfiguracion {
  id: number;
  nombre: string;
  apellidos: string;
  email: string;
  /** Los roles en la escuela activa, con el formato de siempre: «[ROLE_USER, ROLE_ADMIN]». */
  rol: string;
  authProvider?: string;
}

/**
 * Los roles que se pueden dar desde «Roles y permisos», en el orden en que hoy van en el cuerpo de `PUT
 * /api/admin/users/{id}/roles` (`ROLE_USER`, `ROLE_MANAGER`, `ROLE_ADMIN`). Son los únicos que acepta esa ruta
 * (`RolMembresia.asignableDesdeApiDeRoles`): Profesorado y Responsable de sede se ven, pero no se ofrecen.
 */
export const ROLES_ASIGNABLES = ['ROLE_USER', 'ROLE_MANAGER', 'ROLE_ADMIN'] as const;
export type RolAsignable = (typeof ROLES_ASIGNABLES)[number];
export type SeleccionRoles = Record<RolAsignable, boolean>;

/** Orden de las pastillas de «roles actuales»: el de hoy (Administración, Gestión, Familia) y después los demás. */
const ORDEN_PASTILLAS = ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER', 'ROLE_RESPONSABLE_SEDE', 'ROLE_PROFESOR'];

/** Cuentas por página, como hoy. */
export const CUENTAS_POR_PAGINA = 5;

/** Ajustes del índice (P2, desde 768 px). */
export type SeccionConfiguracion = 'turno' | 'roles' | 'legal';
export const SECCIONES: ReadonlyArray<{ clave: SeccionConfiguracion; titulo: string }> = [
  { clave: 'turno', titulo: 'Alumnos por turno' },
  { clave: 'roles', titulo: 'Roles y permisos' },
  { clave: 'legal', titulo: 'Avisos legales y cookies' },
];

/** «[ROLE_USER, ROLE_ADMIN]» → `['ROLE_USER', 'ROLE_ADMIN']` (como `parseRoles` de hoy: sin corchetes ni espacios). */
export function rolesDeTexto(rol: string | null | undefined): string[] {
  return (rol ?? '')
    .replaceAll(/[[\]]/g, '')
    .split(',')
    .map((r) => r.trim())
    .filter((r) => r.length > 0);
}

/** Las casillas de una cuenta según sus roles guardados (igual que `parseRoles` de hoy). */
export function seleccionDe(rol: string | null | undefined): SeleccionRoles {
  const roles = new Set(rolesDeTexto(rol));
  return {
    ROLE_USER: roles.has('ROLE_USER'),
    ROLE_MANAGER: roles.has('ROLE_MANAGER'),
    ROLE_ADMIN: roles.has('ROLE_ADMIN'),
  };
}

/** El cuerpo de `PUT /api/admin/users/{id}/roles`: los `ROLE_*` marcados, en el orden de hoy. */
export function cuerpoRoles(seleccion: SeleccionRoles): string[] {
  return ROLES_ASIGNABLES.filter((r) => seleccion[r]);
}

export function mismaSeleccion(a: SeleccionRoles, b: SeleccionRoles): boolean {
  return ROLES_ASIGNABLES.every((r) => a[r] === b[r]);
}

export function algunRol(seleccion: SeleccionRoles): boolean {
  return ROLES_ASIGNABLES.some((r) => seleccion[r]);
}

/** Las pastillas de «roles actuales» (los guardados), con su nombre visible; los roles desconocidos no salen. */
export function pastillasRoles(rol: string | null | undefined): { rol: string; texto: string }[] {
  const roles = new Set(rolesDeTexto(rol));
  return ORDEN_PASTILLAS.filter((r) => roles.has(r)).map((r) => ({ rol: r, texto: etiquetaRol(r) }));
}

/** «Familia y Gestión», «Familia, Gestión y Administración»: para la frase del diálogo. */
export function listaRoles(roles: readonly string[]): string {
  const textos = roles.map((r) => etiquetaRol(r) || r);
  if (textos.length <= 1) {
    return textos.join('');
  }
  return `${textos.slice(0, -1).join(', ')} y ${textos[textos.length - 1]}`;
}

export function nombreCompleto(cuenta: Pick<CuentaConfiguracion, 'nombre' | 'apellidos'>): string {
  return `${cuenta.nombre ?? ''} ${cuenta.apellidos ?? ''}`.trim();
}

export function esCuentaGoogle(cuenta: Pick<CuentaConfiguracion, 'authProvider'>): boolean {
  return cuenta.authProvider === 'GOOGLE';
}

/** Sin mayúsculas ni acentos, como el buscador de hoy. */
export function normalizarTexto(valor: string | null | undefined): string {
  return (valor ?? '')
    .toString()
    .toLowerCase()
    .normalize('NFD')
    .replaceAll(/[̀-ͯ]/g, '')
    .trim();
}

/** El buscador de hoy: nombre y apellidos o email. */
export function filtrarCuentas<T extends CuentaConfiguracion>(cuentas: readonly T[], filtro: string): T[] {
  const f = normalizarTexto(filtro);
  if (!f) {
    return [...cuentas];
  }
  return cuentas.filter(
    (c) => normalizarTexto(`${c.nombre} ${c.apellidos}`).includes(f) || normalizarTexto(c.email).includes(f)
  );
}

/**
 * El nuevo límite escrito en el campo. Hoy el aviso salta con un valor menor que 1; aquí, además, sin número no se
 * puede guardar (hoy, un campo vacío daba el mismo aviso) y el límite es un número entero, como lo guarda la API.
 */
export function validarLimite(texto: string): { valor: number | null; error: string | null } {
  const limpio = (texto ?? '').trim();
  if (limpio === '') {
    return { valor: null, error: 'Escribe cuántos alumnos caben en un turno.' };
  }
  if (!/^-?\d+$/.test(limpio)) {
    return { valor: null, error: 'Escribe un número entero, por ejemplo 36.' };
  }
  const valor = Number(limpio);
  if (valor < 1) {
    return { valor: null, error: 'El límite debe ser al menos 1.' };
  }
  return { valor, error: null };
}

/** Las reglas de la contraseña de hoy (`contrasenaCumpleReglas` y «Coinciden»), en su orden. */
export interface ReglaContrasena {
  clave: 'largo' | 'mayuscula' | 'minuscula' | 'numero' | 'coinciden';
  texto: string;
  cumple: (nueva: string, confirmar: string) => boolean;
}

export const REGLAS_CONTRASENA: readonly ReglaContrasena[] = [
  { clave: 'largo', texto: 'Mínimo 8 caracteres', cumple: (n) => n.length >= 8 },
  { clave: 'mayuscula', texto: 'Al menos 1 mayúscula', cumple: (n) => /[A-Z]/.test(n) },
  { clave: 'minuscula', texto: 'Al menos 1 minúscula', cumple: (n) => /[a-z]/.test(n) },
  { clave: 'numero', texto: 'Al menos 1 número', cumple: (n) => /\d/.test(n) },
  { clave: 'coinciden', texto: 'Coinciden', cumple: (n, c) => n.length > 0 && n === c },
];

/** Igual que `contrasenaCumpleReglas` de hoy. */
export function contrasenaCumpleReglas(contrasena: string): boolean {
  return contrasena.length >= 8 && /[A-Z]/.test(contrasena) && /[a-z]/.test(contrasena) && /\d/.test(contrasena);
}

/** El error al guardar la contraseña (el de hoy no cambia: «mensaje», «message», «error», 403 y el genérico). */
export function mensajeErrorContrasena(error: any): string {
  return (
    error?.error?.mensaje ||
    error?.error?.message ||
    error?.error?.error ||
    (error?.status === 403 ? 'No tienes permisos para restablecer contraseñas.' : null) ||
    'No se pudo actualizar la contraseña.'
  );
}

/** El `mensaje` del servidor (el 409 de último propietario, decisión 221) o el texto de hoy. */
export function mensajeErrorRoles(error: any): string {
  return error?.error?.mensaje || 'No se pudieron actualizar los roles del usuario.';
}
