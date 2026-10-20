import { RolAsignacionProfesor } from './profesor';

/** GrupoProfesoresDTOs.Profesor: un profesor asignado a un grupo (vista de gestión). */
export interface ProfesorDeGrupo {
  usuarioId: number;
  nombre: string;
  apellidos: string;
  email: string;
  rol: RolAsignacionProfesor;
}
