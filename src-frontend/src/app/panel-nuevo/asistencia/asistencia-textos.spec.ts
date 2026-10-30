import { ClaseAsistencia } from '../../interfaces/asistencia';
import { diaCorto, diaLargo, diaSemana, estadoClase, fechaClase, fechaClaseCapital, franja, mismaClase, tieneLista } from './asistencia-textos';

const clase = (cambios: Partial<ClaseAsistencia> = {}): ClaseAsistencia => ({
  turnoId: 9, fecha: '2026-10-28', horaInicio: '18:00', horaFin: '19:00', pasada: true, presentes: null, total: null, alumnos: 18, ...cambios,
});

describe('asistencia-textos', () => {
  it('fechas de calendario sin zona horaria', () => {
    expect(fechaClase('2026-10-28')).toBe('miércoles 28 de octubre');
    expect(fechaClaseCapital('2026-11-02')).toBe('Lunes 2 de noviembre');
    expect(diaSemana('2026-10-28')).toBe('miércoles');
    expect(diaCorto('2026-10-05')).toBe('lun 5');
    expect(diaLargo('2026-10-07')).toBe('Miércoles 7');
  });

  it('franja y estado de la clase', () => {
    expect(franja(clase())).toBe('18:00–19:00');
    expect(estadoClase(clase())).toBe('Sin pasar');
    expect(estadoClase(clase({ presentes: 16, total: 18 }))).toBe('16 de 18');
  });

  it('lista pasada es presentes distinto de null, aunque la clase no haya terminado', () => {
    expect(tieneLista(clase({ pasada: true, presentes: null }))).toBeFalse();
    expect(tieneLista(clase({ pasada: false, presentes: 0, total: 18 }))).toBeTrue();
  });

  it('misma clase: turno y fecha', () => {
    expect(mismaClase(clase(), clase({ horaInicio: '20:00' }))).toBeTrue();
    expect(mismaClase(clase(), clase({ turnoId: 10 }))).toBeFalse();
    expect(mismaClase(clase(), clase({ fecha: '2026-10-26' }))).toBeFalse();
  });
});
