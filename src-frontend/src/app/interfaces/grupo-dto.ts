import { AlumnoDTO } from "./alumno-dto";

export interface GrupoDTO {
  id: number;
  nombre: string;
  alumnos: AlumnoDTO[];
}
