/**
 * Turno en versión ligera (`GET /api/turnos/resumen`): día, horas y grupo, sin alumnos.
 * `grupoNombre` vale "Sin grupo" si el turno no tiene grupo.
 */
export interface TurnoResumen {
  id: number;
  diaSemana: string;
  horaInicio: string;
  horaFin: string;
  grupoId: number | null;
  grupoNombre: string;
  /** Tipo del turno, tal cual (puede ser nulo o vacío); lo pinta la pantalla de turnos de un alumno. */
  tipoGrupo: string | null;
  /** Aforo del turno: el de la sede de su grupo o, si no lo tiene, el límite común de la escuela (pieza 9-7). */
  aforoEfectivo?: number;
}
