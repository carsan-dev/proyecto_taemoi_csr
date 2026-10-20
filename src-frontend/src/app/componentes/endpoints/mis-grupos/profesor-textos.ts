import { HttpErrorResponse } from '@angular/common/http';

import { ContactoAlumno, ParentescoTutor, RolAsignacionProfesor } from '../../../interfaces/profesor';

const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

const ROLES: Record<RolAsignacionProfesor, string> = { TITULAR: 'Titular', AYUDANTE: 'Ayudante' };

const PARENTESCOS: Record<ParentescoTutor, string> = {
  PADRE: 'Padre',
  MADRE: 'Madre',
  TUTOR_LEGAL: 'Tutor legal',
  OTRO: 'Tutor',
};

export interface MesAsistencia {
  /** `yyyy-MM`, valor del selector. */
  valor: string;
  year: number;
  month: number;
  etiqueta: string;
}

export function etiquetaRol(rol: RolAsignacionProfesor | null | undefined): string {
  return rol ? ROLES[rol] ?? rol : '';
}

export function nombreCompleto(nombre: string | null | undefined, apellidos: string | null | undefined): string {
  return [nombre, apellidos].filter((t) => !!t && t.trim()).join(' ').trim();
}

export function textoAlumnos(n: number): string {
  return n === 1 ? '1 alumno' : `${n} alumnos`;
}

/** Quién es el contacto: «Madre», «Tutor legal» o «Alumno». */
export function etiquetaContacto(c: ContactoAlumno): string {
  if (c.tipo === 'ALUMNO') {
    return 'Alumno';
  }
  return c.parentesco ? PARENTESCOS[c.parentesco] ?? 'Tutor' : 'Tutor';
}

/** `2026-03-12` → `12/03/2026`, sin pasar por `Date` (una fecha de calendario no tiene zona). */
export function fechaCorta(iso: string | null | undefined): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
}

/** Hoy en `yyyy-MM-dd` (hora local), tope del selector de fecha de grado. */
export function hoyIso(hoy: Date = new Date()): string {
  const mes = String(hoy.getMonth() + 1).padStart(2, '0');
  const dia = String(hoy.getDate()).padStart(2, '0');
  return `${hoy.getFullYear()}-${mes}-${dia}`;
}

/** El mes actual y los once anteriores, del más reciente al más antiguo. */
export function mesesAsistencia(hoy: Date = new Date(), cuantos = 12): MesAsistencia[] {
  const meses: MesAsistencia[] = [];
  for (let i = 0; i < cuantos; i++) {
    const d = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    const year = d.getFullYear();
    const month = d.getMonth() + 1;
    const nombre = MESES[month - 1];
    meses.push({
      valor: `${year}-${String(month).padStart(2, '0')}`,
      year,
      month,
      etiqueta: `${nombre.charAt(0).toUpperCase()}${nombre.slice(1)} ${year}`,
    });
  }
  return meses;
}

/** Teléfono para `tel:`: solo dígitos y un `+` inicial. */
export function enlaceTelefono(telefono: string): string {
  const limpio = telefono.replace(/[^\d+]/g, '');
  return `tel:${limpio.startsWith('+') ? '+' + limpio.slice(1).replace(/\+/g, '') : limpio.replace(/\+/g, '')}`;
}

export function formatearEuros(importe: number): string {
  return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(importe);
}

/** El `{mensaje}` del backend, o el texto propuesto. Sin conexión, lo dice. */
export function mensajeDelServidor(error: unknown, porDefecto: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No hay conexión con el servidor. Comprueba la cobertura y vuelve a intentarlo.';
    }
    const cuerpo = error.error;
    if (typeof cuerpo === 'string' && cuerpo.trim() && !cuerpo.trim().startsWith('<')) {
      return cuerpo.trim();
    }
    const mensaje = cuerpo?.mensaje ?? cuerpo?.message;
    if (typeof mensaje === 'string' && mensaje.trim()) {
      return mensaje.trim();
    }
  }
  return porDefecto;
}
