import { GrupoWeb, Plazas, SedeWeb, TurnoWeb } from '../datos/modelos';

/**
 * Lógica sin estilo de los horarios de la web por plantilla (fase4-web-plan.md §3.1, «comun/»): días, plazas
 * (§4.3 y §10 n.º 5), próxima clase, salidas de una sede, enlace a mapas y cifras de la presentación. La comparten
 * todos los temas; cada uno solo decide cómo se pinta.
 *
 * Los días van como en `Date.getDay()`: 0 domingo, 1 lunes … 6 sábado.
 */

/** Los días en el orden de la semana española, con su letra y su nombre. */
export const DIAS_SEMANA: readonly { numero: number; letra: string; nombre: string }[] = [
  { numero: 1, letra: 'L', nombre: 'lunes' },
  { numero: 2, letra: 'M', nombre: 'martes' },
  { numero: 3, letra: 'X', nombre: 'miércoles' },
  { numero: 4, letra: 'J', nombre: 'jueves' },
  { numero: 5, letra: 'V', nombre: 'viernes' },
  { numero: 6, letra: 'S', nombre: 'sábado' },
  { numero: 0, letra: 'D', nombre: 'domingo' },
];

const NOMBRE_DIA: Record<number, string> = Object.fromEntries(DIAS_SEMANA.map((d) => [d.numero, d.nombre]));

const POR_NOMBRE: Record<string, number> = {
  lunes: 1,
  martes: 2,
  miercoles: 3,
  jueves: 4,
  viernes: 5,
  sabado: 6,
  domingo: 0,
};

/** «Miércoles», «MIERCOLES», «miércoles» → 3; null si no es un día. */
export function numeroDia(diaSemana: string | null | undefined): number | null {
  if (!diaSemana) {
    return null;
  }
  const clave = diaSemana
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
  return clave in POR_NOMBRE ? POR_NOMBRE[clave] : null;
}

export function nombreDia(numero: number): string {
  return NOMBRE_DIA[numero] ?? '';
}

/** [1, 3] → «lunes y miércoles»; [1, 3, 5] → «lunes, miércoles y viernes» (en el orden de la semana). */
export function diasEnTexto(dias: readonly number[]): string {
  const nombres = DIAS_SEMANA.filter((d) => dias.includes(d.numero)).map((d) => d.nombre);
  if (nombres.length <= 1) {
    return nombres.join('');
  }
  return `${nombres.slice(0, -1).join(', ')} y ${nombres[nombres.length - 1]}`;
}

/** «17:05» → 1025; null si no es una hora. */
export function minutos(hora: string | null | undefined): number | null {
  const partes = /^(\d{1,2}):(\d{2})/.exec(hora ?? '');
  return partes ? Number(partes[1]) * 60 + Number(partes[2]) : null;
}

function horaDeMinutos(total: number): string {
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

// ---------------------------------------------------------------------------------------------------------------
// Plazas (§4.3): «Quedan N», «Lleno» o «Lista de espera». Sin «Últimas N».
// ---------------------------------------------------------------------------------------------------------------

export type EstadoPlazas = 'quedan' | 'lleno' | 'espera';

/** El estado de unas plazas; `QUEDAN` sin ninguna libre se toma por lleno. null si no hay dato. */
export function estadoPlazas(plazas: Plazas | null | undefined): EstadoPlazas | null {
  if (!plazas) {
    return null;
  }
  switch (plazas.estado) {
    case 'QUEDAN':
      return (plazas.quedan ?? 0) >= 1 ? 'quedan' : 'lleno';
    case 'LLENO':
      return 'lleno';
    case 'LISTA_ESPERA':
      return 'espera';
    default:
      return null;
  }
}

/** El peor de varios (§10 n.º 5): lista de espera, luego lleno, luego el que menos libres tiene. */
export function peorPlazas(lista: readonly (Plazas | null | undefined)[]): Plazas | null {
  const peso = (p: Plazas): number => {
    const estado = estadoPlazas(p);
    return estado === 'espera' ? 0 : estado === 'lleno' ? 1 : 2;
  };
  let peor: Plazas | null = null;
  for (const plazas of lista) {
    if (!plazas || estadoPlazas(plazas) === null) {
      continue;
    }
    if (
      peor === null ||
      peso(plazas) < peso(peor) ||
      (peso(plazas) === 2 && peso(peor) === 2 && (plazas.quedan ?? 0) < (peor.quedan ?? 0))
    ) {
      peor = plazas;
    }
  }
  return peor;
}

export interface TextosPlazas {
  lleno: string;
  espera: string;
}

/** «Queda 1 plaza», «Quedan 4 plazas», o el texto de lleno o de lista de espera. Vacío sin dato. */
export function textoPlazas(plazas: Plazas | null | undefined, textos: TextosPlazas): string {
  switch (estadoPlazas(plazas)) {
    case 'quedan': {
      const n = plazas!.quedan ?? 0;
      return n === 1 ? 'Queda 1 plaza' : `Quedan ${n} plazas`;
    }
    case 'lleno':
      return textos.lleno;
    case 'espera':
      return textos.espera;
    default:
      return '';
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Salidas de una sede: un grupo con un mismo horario en varios días
// ---------------------------------------------------------------------------------------------------------------

export interface Salida {
  grupo: GrupoWeb;
  inicio: string;
  fin: string;
  /** Días de la semana (0 domingo … 6 sábado), en el orden de la semana española. */
  dias: number[];
  /** El peor de sus turnos; null sin dato. */
  plazas: Plazas | null;
  turnos: TurnoWeb[];
}

/**
 * Las salidas de una sede, por hora de inicio: cada grupo junta en una salida los turnos con la misma hora de inicio
 * y de fin (los días van juntos, como en el boceto).
 */
export function salidasDeSede(sede: SedeWeb): Salida[] {
  const salidas: Salida[] = [];
  for (const grupo of sede.grupos) {
    const porHorario = new Map<string, TurnoWeb[]>();
    for (const turno of grupo.turnos) {
      const clave = `${turno.horaInicio}|${turno.horaFin}`;
      porHorario.set(clave, [...(porHorario.get(clave) ?? []), turno]);
    }
    for (const turnos of porHorario.values()) {
      const dias = new Set(turnos.map((t) => numeroDia(t.diaSemana)).filter((d): d is number => d !== null));
      salidas.push({
        grupo,
        inicio: turnos[0].horaInicio,
        fin: turnos[0].horaFin,
        dias: DIAS_SEMANA.map((d) => d.numero).filter((d) => dias.has(d)),
        plazas: peorPlazas(turnos.map((t) => t.plazas)),
        turnos,
      });
    }
  }
  return salidas.sort(
    (a, b) => (minutos(a.inicio) ?? 0) - (minutos(b.inicio) ?? 0) || a.grupo.nombre.localeCompare(b.grupo.nombre, 'es'),
  );
}

/** «4 a 6 años», «Desde 12 años», «Hasta 6 años»; null sin edades. */
export function edadesEnTexto(min: number | null | undefined, max: number | null | undefined): string | null {
  if (min != null && max != null) {
    return min === max ? `${min} años` : `${min} a ${max} años`;
  }
  if (min != null) {
    return `Desde ${min} años`;
  }
  if (max != null) {
    return `Hasta ${max} años`;
  }
  return null;
}

// ---------------------------------------------------------------------------------------------------------------
// Próxima clase
// ---------------------------------------------------------------------------------------------------------------

export interface ProximaClase {
  hoy: boolean;
  /** «Mañana» o el día con mayúscula («Jueves»); vacío si es hoy. */
  cuando: string;
  hora: string;
}

/** La próxima clase de la sede desde `ahora` (la de hoy solo si aún no ha empezado); null sin turnos. */
export function proximaClase(sede: SedeWeb, ahora: Date): ProximaClase | null {
  const turnos = sede.grupos.flatMap((g) => g.turnos);
  const hoyDia = ahora.getDay();
  const ahoraMin = ahora.getHours() * 60 + ahora.getMinutes();
  for (let k = 0; k < 8; k++) {
    const dia = (hoyDia + k) % 7;
    let mejor: number | null = null;
    for (const turno of turnos) {
      if (numeroDia(turno.diaSemana) !== dia) {
        continue;
      }
      const m = minutos(turno.horaInicio);
      if (m === null || (k === 0 && m <= ahoraMin)) {
        continue;
      }
      if (mejor === null || m < mejor) {
        mejor = m;
      }
    }
    if (mejor !== null) {
      const nombre = nombreDia(dia);
      return {
        hoy: k === 0,
        cuando: k === 0 ? '' : k === 1 ? 'Mañana' : nombre.charAt(0).toUpperCase() + nombre.slice(1),
        hora: horaDeMinutos(mejor),
      };
    }
  }
  return null;
}

// ---------------------------------------------------------------------------------------------------------------
// Enlaces y cifras
// ---------------------------------------------------------------------------------------------------------------

/** Búsqueda en Google Maps con las partes que haya; null si no hay ninguna. */
export function enlaceMapa(partes: readonly (string | null | undefined)[]): string | null {
  const texto = partes.map((p) => p?.trim()).filter((p): p is string => !!p);
  return texto.length ? `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(texto.join(', '))}` : null;
}

/** La edad de inicio: la menor edad mínima de los grupos; null si ninguno la tiene. */
export function edadDeInicio(sedes: readonly SedeWeb[]): number | null {
  const edades = sedes.flatMap((s) => s.grupos.map((g) => g.rangoEdadMin)).filter((e): e is number => e != null);
  return edades.length ? Math.min(...edades) : null;
}

/** Grupos por sede: el máximo y si todas tienen los mismos; null sin sedes con grupos. */
export function gruposPorSede(sedes: readonly SedeWeb[]): { maximo: number; iguales: boolean } | null {
  const cuentas = sedes.map((s) => s.grupos.length);
  const maximo = cuentas.length ? Math.max(...cuentas) : 0;
  return maximo > 0 ? { maximo, iguales: cuentas.every((n) => n === maximo) } : null;
}

const NUMEROS = ['', 'una', 'dos', 'tres', 'cuatro', 'cinco', 'seis', 'siete', 'ocho', 'nueve', 'diez'];

/** 6 → «seis» (femenino: «una sede»); a partir de 11, la cifra. */
export function numeroEnLetras(n: number): string {
  return NUMEROS[n] ?? String(n);
}

/** 25 → «25 €»; 25.5 → «25,50 €». */
export function importeEnTexto(importe: number): string {
  const entero = Number.isInteger(importe);
  return `${importe.toLocaleString('es-ES', {
    minimumFractionDigits: entero ? 0 : 2,
    maximumFractionDigits: 2,
    useGrouping: true,
  })} €`;
}

/** «600000000» → «600 000 000» (los nueve dígitos españoles en grupos de tres); otro formato, tal cual. */
export function telefonoVisible(telefono: string): string {
  const digitos = telefono.replace(/\D/g, '');
  const nacional = digitos.length === 11 && digitos.startsWith('34') ? digitos.slice(2) : digitos;
  return nacional.length === 9 ? nacional.replace(/(\d{3})(\d{3})(\d{3})/, '$1 $2 $3') : telefono.trim();
}

/** `tel:` con prefijo +34 si son los nueve dígitos españoles. */
export function telefonoEnlace(telefono: string): string {
  const limpio = telefono.replace(/[^\d+]/g, '');
  return `tel:${/^\d{9}$/.test(limpio) ? `+34${limpio}` : limpio}`;
}
