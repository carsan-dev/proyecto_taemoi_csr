import { CargoPendienteProfesor } from '../../interfaces/caja';

/**
 * Lógica pura de «Mi caja» que comparten la pantalla de hoy y la del panel nuevo (8b-3p): pestañas, nombres, búsqueda,
 * cargos agrupados por alumno y fechas cortas. Vive aparte del componente para que el chunk del panel nuevo no arrastre
 * la pantalla de hoy.
 */

export type PestanaMiCaja = 'cobrar' | 'vender' | 'encima' | 'pedidos';

export interface GrupoAlumno {
  alumnoId: number;
  nombre: string;
  cargos: CargoPendienteProfesor[];
}

/** Texto sin tildes y en minúsculas, para buscar «lucia» y encontrar «Lucía». */
export function normalizarBusqueda(texto: string | null | undefined): string {
  return (texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

export function nombreCompleto(nombre: string | null | undefined, apellidos: string | null | undefined): string {
  return [nombre, apellidos].map((p) => (p ?? '').trim()).filter(Boolean).join(' ') || 'Alumno sin nombre';
}

/** Cargos agrupados por alumno, alumnos por nombre y, dentro, del más antiguo al más reciente. */
export function agruparPorAlumno(cargos: CargoPendienteProfesor[], busqueda: string = ''): GrupoAlumno[] {
  const filtro = normalizarBusqueda(busqueda);
  const grupos = new Map<number, GrupoAlumno>();
  for (const cargo of cargos) {
    const nombre = nombreCompleto(cargo.alumnoNombre, cargo.alumnoApellidos);
    if (filtro && !normalizarBusqueda(nombre).includes(filtro)) {
      continue;
    }
    let grupo = grupos.get(cargo.alumnoId);
    if (!grupo) {
      grupo = { alumnoId: cargo.alumnoId, nombre, cargos: [] };
      grupos.set(cargo.alumnoId, grupo);
    }
    grupo.cargos.push(cargo);
  }
  const porFecha = (c: CargoPendienteProfesor) => (c.fechaAsignacion ? new Date(c.fechaAsignacion).getTime() : 0);
  return Array.from(grupos.values())
    .map((g) => ({ ...g, cargos: [...g.cargos].sort((a, b) => porFecha(a) - porFecha(b)) }))
    .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
}

/** «01/10» de una fecha ISO (o «—»). */
export function diaMes(fecha: string | null | undefined): string {
  if (!fecha) {
    return '—';
  }
  const d = new Date(fecha);
  if (Number.isNaN(d.getTime())) {
    return '—';
  }
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}
