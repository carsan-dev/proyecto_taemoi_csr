import { AlumnoDTO } from "./alumno-dto";

export interface GrupoDTO {
  id: number;
  nombre: string;
  deporte?: string;
  rangoEdadMin?: number | null;
  rangoEdadMax?: number | null;
  /** Sede del grupo (E9-1); null si no tiene. */
  sedeId?: number | null;
  alumnos: AlumnoDTO[];
}
