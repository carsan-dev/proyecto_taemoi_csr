import { AlumnoDTO } from "./alumno-dto";

export interface GrupoDTO {
  id: number;
  nombre: string;
  deporte?: string;
  rangoEdadMin?: number | null;
  rangoEdadMax?: number | null;
  alumnos: AlumnoDTO[];
}
