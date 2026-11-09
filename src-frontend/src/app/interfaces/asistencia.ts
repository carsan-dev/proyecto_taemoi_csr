import { DeporteEnLista } from './profesor';

/** Motivo opcional de una falta (decisión 455); sin motivo, la falta es como hasta ahora. */
export type MotivoFalta = 'INJUSTIFICADA' | 'MEDICA' | 'FAMILIAR' | 'OTRA_JUSTIFICADA';

export type TipoFestivo = 'DIA' | 'RANGO' | 'ANUAL';

/** El festivo que cubre una clase sin lista (decisiones 456-457). */
export interface FestivoDeClase {
  id: number;
  nombre: string | null;
  tipo: TipoFestivo;
  /** Un solo día y una sola sede: se quita desde la propia lista. */
  deUnDiaYUnaSede: boolean;
  /** `yyyy-MM-dd`; en un ANUAL, la fecha de la clase en los dos. `null` si el API no los manda. */
  fechaDesde: string | null;
  fechaHasta: string | null;
  /** Sedes que cubre (1 si el API no lo manda). */
  sedes: number;
}

export interface SedeDeClase {
  id: number;
  nombre: string;
}

/** Un turno en una fecha (`/clases`). `pasada`: la clase ya terminó. Lista pasada: `presentes !== null`. */
export interface ClaseAsistencia {
  turnoId: number;
  /** `yyyy-MM-dd`. */
  fecha: string;
  /** `HH:mm`. */
  horaInicio: string;
  horaFin: string;
  pasada: boolean;
  /** `null` sin lista. */
  presentes: number | null;
  total: number | null;
  /** Tamaño de la lista de esta clase; 0, turno sin alumnos. */
  alumnos: number;
  /** Solo si la clase es festiva y no tiene lista (con lista, manda la lista); entonces `presentes` y `total` son null. */
  festivo: FestivoDeClase | null;
  /**
   * Festivo que cubre una clase con lista guardada (decisiones 495-496; entonces `festivo` es null). Opcional: un API
   * anterior no lo manda; el servicio lo deja en null.
   */
  festivoConLista?: FestivoDeClase | null;
}

export interface ProximaClase {
  fecha: string;
  horaInicio: string;
}

export interface ClasesDelGrupo {
  hoy: ClaseAsistencia[];
  recientes: ClaseAsistencia[];
  proxima: ProximaClase | null;
}

export interface AlumnoDeLista {
  id: number;
  nombre: string;
  apellidos: string;
  presente: boolean;
  deportes: DeporteEnLista[];
  /** Solo en las faltas de una lista guardada. */
  motivo?: MotivoFalta | null;
  /** `FEMENINO`, `MASCULINO` o null si la ficha no lo tiene (apto/apta, listo/lista; decisión 513 d). */
  genero?: string | null;
  /** Quien pide puede darle o quitarle rallas: permiso en el grupo y sus rallas se usan (abre la hoja). */
  puedeCambiarRallas?: boolean;
}

export interface ListaDeClase {
  clase: ClaseAsistencia;
  alumnos: AlumnoDeLista[];
  /** Sede del grupo; null si no tiene. */
  sede?: SedeDeClase | null;
  /** Quien pide puede cambiar rallas en este grupo (false para el ayudante): el texto de la cabecera. */
  puedeCambiarRallas?: boolean;
}

/** Respuesta de un paso de rallas o de «Marcar apto» desde la hoja: cómo queda la inscripción. */
export interface RallasDelAlumno {
  alumnoId: number;
  deporte: string;
  mediasRallas: number;
  aptoParaExamen: boolean;
}

/** Un paso de la hoja de rallas: −2 («− Una»), −1 («− Media»), +1 («+ Media») o +2 («+ Una»). */
export type PasoRallas = -2 | -1 | 1 | 2;

export interface ResumenAsistenciaAlumno {
  alumnoId: number;
  presentes: number;
  clases: number;
}

export type EstadoDia = 'PRESENTE' | 'FALTA' | 'SIN_LISTA' | 'FESTIVO';

export interface DiaAsistencia {
  fecha: string;
  horaInicio: string;
  estado: EstadoDia;
  /** Solo en las faltas. */
  motivo?: MotivoFalta | null;
}

export interface AsistenciaDeAlumno {
  presentes: number;
  clases: number;
  dias: DiaAsistencia[];
}

/** Quién pide: el profesor (`/profesor/grupos/{id}`) o la gestión (`/grupos/{id}`). */
export type PrefijoAsistencia = 'profesor' | 'gestion';
