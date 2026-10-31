/**
 * Pantalla Miembros del panel nuevo (E9, pieza 9-4): textos, tipos y reglas sin Angular. Los contratos y mensajes son los
 * de `docs/multi-escuela/fase3-e8-miembros.md` (D8-4 a D8-11). Nada de aquí escribe emails ni nombres en consola.
 */
import {
  AsignacionDeAlta,
  FichaCompleta,
  GrupoDeMiembro,
  Miembro,
  RolDeMiembro,
  RolMembresia,
} from '../../servicios/endpoints/miembros.service';
import { RolAsignacionProfesor } from '../../interfaces/profesor';
import { plural } from '../formato/formato-es';

export { estadoHttp, mensajeError } from '../sedes/sedes-vista';

/** Los roles de personal, en el orden de la ficha. `ADMINISTRADOR` se llama «Gestor» en pantalla. */
export const ROLES_PERSONAL: readonly RolMembresia[] = ['PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR'];

const ETIQUETA: Record<RolMembresia, string> = {
  PROPIETARIO: 'Propietario',
  ADMINISTRADOR: 'Gestor',
  RESPONSABLE_SEDE: 'Responsable de sede',
  PROFESOR: 'Profesor',
  FAMILIA: 'Familia',
};

export function etiquetaRol(rol: RolMembresia): string {
  return ETIQUETA[rol] ?? rol;
}

/** El nombre del rol dentro de una frase («el rol de gestor»). */
export function rolEnFrase(rol: RolMembresia): string {
  return etiquetaRol(rol).toLowerCase();
}

/** Opciones del selector de la lista; «Todos los roles» es el valor vacío. */
export const OPCIONES_ROL: readonly { valor: RolMembresia | ''; etiqueta: string }[] = [
  { valor: '', etiqueta: 'Todos los roles' },
  { valor: 'PROPIETARIO', etiqueta: 'Propietario' },
  { valor: 'ADMINISTRADOR', etiqueta: 'Gestor' },
  { valor: 'RESPONSABLE_SEDE', etiqueta: 'Responsable de sede' },
  { valor: 'PROFESOR', etiqueta: 'Profesor' },
];

export const POR_PAGINA = 20;
export const ESPERA_BUSQUEDA_MS = 300;
export const CARGO_MAXIMO = 100;

export const AYUDA_SOLO_PROPIETARIO = 'Solo el propietario puede cambiar este rol.';
export const TEXTO_QUITAR = 'Podrás devolvérselo después.';
export const TEXTO_CANCELAR_INVITACION =
  'El enlace dejará de valer y le avisaremos por correo. Si no tiene nada más en ninguna escuela, su cuenta se borrará.';
export const AVISO_SIN_CUENTA = 'No hay ninguna cuenta registrada con ese email. Le enviaremos una invitación para crear su contraseña.';
export const MENSAJE_SIN_SEDES = 'Indica al menos una sede.';

export function nombreCompleto(m: Pick<Miembro, 'nombre' | 'apellidos'>): string {
  return [m.nombre, m.apellidos]
    .map((v) => (v ?? '').trim())
    .filter((v) => v !== '')
    .join(' ');
}

/** Los roles ACTIVOS de la fila, en el orden de la API. */
export function rolesActivos(m: Pick<Miembro, 'roles'>): RolMembresia[] {
  return (m.roles ?? []).filter((r) => r.activa).map((r) => r.rol);
}

export function rolDe(m: Pick<Miembro, 'roles'>, rol: RolMembresia): RolDeMiembro | null {
  return (m.roles ?? []).find((r) => r.rol === rol) ?? null;
}

export function tieneRolActivo(m: Pick<Miembro, 'roles'>, rol: RolMembresia): boolean {
  return rolDe(m, rol)?.activa === true;
}

/** Segunda línea de la fila: sus sedes si es responsable y el número de grupos si tiene. */
export function lineaSecundaria(m: Miembro): string {
  const partes: string[] = [];
  if (tieneRolActivo(m, 'RESPONSABLE_SEDE') && (m.sedes ?? []).length > 0) {
    partes.push(m.sedes.map((s) => s.nombre).join(', '));
  }
  if ((m.numeroGrupos ?? 0) > 0) {
    partes.push(plural(m.numeroGrupos, 'grupo', 'grupos'));
  }
  return partes.join(' · ');
}

/** Propietario y Gestor solo los cambia un propietario (D8-8); el servidor lo vuelve a comprobar con 403. */
export function esRolReservado(rol: RolMembresia): boolean {
  return rol === 'PROPIETARIO' || rol === 'ADMINISTRADOR';
}

export function puedeCambiarRol(rol: RolMembresia, sesionPropietaria: boolean): boolean {
  return sesionPropietaria || !esRolReservado(rol);
}

/** Los roles que la sesión puede dar en el alta. */
export function rolesParaAlta(sesionPropietaria: boolean): RolMembresia[] {
  return ROLES_PERSONAL.filter((r) => puedeCambiarRol(r, sesionPropietaria));
}

export function tituloQuitar(rol: RolMembresia, nombre: string): string {
  return `¿Quitar el rol de ${rolEnFrase(rol)} a ${nombre}?`;
}

export function tituloCancelarInvitacion(nombre: string): string {
  return `¿Cancelar la invitación de ${nombre}?`;
}

/**
 * Cancelar retira todos sus roles activos de esta escuela (decisión 389): hace falta alguno y que la sesión pueda
 * cambiarlos todos. El servidor lo vuelve a comprobar con 403.
 */
export function puedeCancelarInvitacion(m: Pick<Miembro, 'invitacionPendiente' | 'roles'>, sesionPropietaria: boolean): boolean {
  const activos = rolesActivos(m);
  return m.invitacionPendiente && activos.length > 0 && activos.every((rol) => puedeCambiarRol(rol, sesionPropietaria));
}

/** Nombre del grupo con su sede cuando la escuela tiene varias. */
export function etiquetaGrupo(grupo: Pick<GrupoDeMiembro, 'nombre' | 'sedeId'>, sedes: ReadonlyMap<number, string>, variasSedes: boolean): string {
  const sede = variasSedes && grupo.sedeId !== null ? sedes.get(grupo.sedeId) : undefined;
  return sede ? `${grupo.nombre} · ${sede}` : grupo.nombre;
}

export const ROLES_ASIGNACION: readonly { valor: RolAsignacionProfesor; etiqueta: string }[] = [
  { valor: 'TITULAR', etiqueta: 'Titular' },
  { valor: 'AYUDANTE', etiqueta: 'Ayudante' },
];

export function etiquetaAsignacion(rol: RolAsignacionProfesor): string {
  return rol === 'TITULAR' ? 'Titular' : 'Ayudante';
}

/** Dos conjuntos de grupos son iguales aunque cambie el orden. */
export function mismosGrupos(a: readonly AsignacionDeAlta[], b: readonly AsignacionDeAlta[]): boolean {
  if (a.length !== b.length) {
    return false;
  }
  const mapa = new Map(a.map((g) => [g.grupoId, g.rol]));
  return b.every((g) => mapa.get(g.grupoId) === g.rol);
}

export function asignacionesDe(ficha: Pick<FichaCompleta, 'grupos'>): AsignacionDeAlta[] {
  return (ficha.grupos ?? []).map((g) => ({ grupoId: g.grupoId, rol: g.rol }));
}

export function mismasSedes(a: readonly number[], b: readonly number[]): boolean {
  return a.length === b.length && new Set(a).size === new Set([...a, ...b]).size;
}

/** Cambia, en la lista, la fila de la ficha que devolvió la API (sin los grupos). */
export function filaDeFicha(ficha: FichaCompleta): Miembro {
  const { grupos: _grupos, ...fila } = ficha;
  return fila;
}

// ------------------------------------------------------------------ alta

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface CamposAlta {
  email: string;
  rol: RolMembresia | '';
  sedeIds: number[];
  nombre: string;
  apellidos: string;
}

export type ClaveAlta = 'email' | 'rol' | 'sedes' | 'nombre' | 'apellidos';

export function validarAlta(campos: CamposAlta, pideNombre: boolean): Partial<Record<ClaveAlta, string>> {
  const errores: Partial<Record<ClaveAlta, string>> = {};
  const email = campos.email.trim();
  if (email === '') {
    errores.email = 'Indica el email.';
  } else if (!EMAIL.test(email)) {
    errores.email = 'El email no es válido.';
  } else if ([...email].length > 180) {
    errores.email = 'No puede pasar de 180 caracteres.';
  }
  if (campos.rol === '') {
    errores.rol = 'Elige un rol.';
  } else if (campos.rol === 'RESPONSABLE_SEDE' && campos.sedeIds.length === 0) {
    errores.sedes = MENSAJE_SIN_SEDES;
  }
  if (pideNombre) {
    if (campos.nombre.trim() === '') {
      errores.nombre = 'Indica el nombre.';
    }
    if (campos.apellidos.trim() === '') {
      errores.apellidos = 'Indica los apellidos.';
    }
  }
  return errores;
}

/** El cuerpo del alta: nombre y apellidos solo al invitar; `reactivar` solo si se ofreció. */
export function cuerpoAlta(campos: CamposAlta, invitar: boolean, reactivar: boolean) {
  const rol = campos.rol as RolMembresia;
  return {
    email: campos.email.trim(),
    rol,
    ...(rol === 'RESPONSABLE_SEDE' ? { sedeIds: [...campos.sedeIds] } : {}),
    ...(invitar ? { nombre: campos.nombre.trim(), apellidos: campos.apellidos.trim() } : {}),
    ...(reactivar ? { reactivar: true } : {}),
  };
}

/** El 409 del rol desactivado: la API pide `reactivar` (D8-6). */
export function pideReactivar(estado: number | null, mensaje: string): boolean {
  return estado === 409 && /desactivado/i.test(mensaje);
}
