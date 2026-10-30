import { DeporteEnLista } from './profesor';

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
}

export interface ListaDeClase {
  clase: ClaseAsistencia;
  alumnos: AlumnoDeLista[];
}

export interface ResumenAsistenciaAlumno {
  alumnoId: number;
  presentes: number;
  clases: number;
}

export type EstadoDia = 'PRESENTE' | 'FALTA' | 'SIN_LISTA';

export interface DiaAsistencia {
  fecha: string;
  horaInicio: string;
  estado: EstadoDia;
}

export interface AsistenciaDeAlumno {
  presentes: number;
  clases: number;
  dias: DiaAsistencia[];
}

/** Quién pide: el profesor (`/profesor/grupos/{id}`) o la gestión (`/grupos/{id}`). */
export type PrefijoAsistencia = 'profesor' | 'gestion';
