/**
 * Turno en versión ligera (`GET /api/turnos/resumen`): día, horas y grupo, sin alumnos.
 * `grupoNombre` vale "Sin grupo" si el turno no tiene grupo (igual que en `GET /api/turnos`).
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
}
