/**
 * Pantallas del profesor (entrega 7, pieza E7-5). Espejo de ProfesorDTOs.java y ProfesorRequests.java de la API
 * (`/api/profesor/**`). Las respuestas ya traen los nombres de grado: estas pantallas no piden el catálogo. Las fechas
 * de grado llegan como texto `yyyy-MM-dd`; los importes, como número.
 */

export type RolAsignacionProfesor = 'TITULAR' | 'AYUDANTE';
export type ParentescoTutor = 'PADRE' | 'MADRE' | 'TUTOR_LEGAL' | 'OTRO';
export type TipoContacto = 'ALUMNO' | 'TUTOR';

export interface SedeProfesor {
  id: number;
  nombre: string;
}

export interface GradoProfesor {
  codigo: string;
  nombre: string;
}

/** ProfesorDTOs.Grupo */
export interface GrupoProfesor {
  id: number;
  nombre: string;
  sede: SedeProfesor | null;
  rol: RolAsignacionProfesor;
  alumnosActivos: number;
  disciplina: { codigo: string; nombre: string } | null;
}

/** ProfesorDTOs.DeporteEnLista */
export interface DeporteEnLista {
  deporte: string;
  deporteNombre: string;
  grado: GradoProfesor | null;
  fechaGrado: string | null;
  apto: boolean;
  delGrupo: boolean;
}

/** ProfesorDTOs.AlumnoDeGrupo */
export interface AlumnoDeGrupo {
  id: number;
  nombre: string;
  apellidos: string;
  deportes: DeporteEnLista[];
}

/** ProfesorDTOs.GrupoCompartido */
export interface GrupoCompartido {
  id: number;
  nombre: string;
  sede: SedeProfesor | null;
}

/** ProfesorDTOs.Contacto: del alumno (sin parentesco) o de un tutor. */
export interface ContactoAlumno {
  tipo: TipoContacto;
  nombre: string | null;
  apellidos: string | null;
  parentesco: ParentescoTutor | null;
  telefono: string | null;
}

/** ProfesorDTOs.DeporteDelAlumno */
export interface DeporteDelAlumno {
  deporte: string;
  deporteNombre: string;
  grado: GradoProfesor | null;
  fechaGrado: string | null;
  apto: boolean;
  /** La disciplina es la de algún grupo que comparte con el profesor; si no, solo lectura. */
  deMisGrupos: boolean;
  /** Hay grado actual y siguiente (el apto va aparte). */
  admitePases: boolean;
  siguienteGrado: GradoProfesor | null;
  /** El pase pide elegir «Rojo» o «Rojo bordado». */
  pideRojoBordado: boolean;
}

/** ProfesorDTOs.Ficha */
export interface FichaAlumnoProfesor {
  id: number;
  nombre: string;
  apellidos: string;
  edad: number | null;
  grupos: GrupoCompartido[];
  contacto: ContactoAlumno[];
  deportes: DeporteDelAlumno[];
}

/** ProfesorDTOs.CargoDelPase */
export interface CargoDelPase {
  id: number;
  concepto: string;
  importe: number;
}

/** ProfesorDTOs.PaseCreado: el deporte tal como queda (el grado no cambia hasta cobrar) y el cargo creado. */
export interface PaseCreado {
  deporte: DeporteDelAlumno;
  cargo: CargoDelPase;
}

export type TipoPase = 'examen' | 'recompensa';

/** AlumnoSaludDTO. `registrado = false`: todavía no hay datos. */
export interface SaludAlumno {
  alumnoId: number;
  registrado: boolean;
  lesionEnfermedad: boolean | null;
  descripcion: string | null;
  impideActividad: boolean | null;
  impideDetalle: string | null;
  alergias: string | null;
}

/** La hoja de asistencia descargada: el PDF y el nombre que propone el servidor. */
export interface HojaAsistencia {
  blob: Blob;
  nombre: string;
}
