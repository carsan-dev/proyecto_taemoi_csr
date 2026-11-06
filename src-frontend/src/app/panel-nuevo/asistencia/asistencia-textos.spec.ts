import { ClaseAsistencia } from '../../interfaces/asistencia';
import {
  MOTIVOS_FALTA, diaCorto, diaLargo, diaSemana, estadoClase, fechaClase, fechaClaseCapital, franja, letraMotivo, mismaClase,
  textoFaltas, textoMotivo, tieneLista,
} from './asistencia-textos';

const clase = (cambios: Partial<ClaseAsistencia> = {}): ClaseAsistencia => ({
  turnoId: 9, fecha: '2026-10-28', horaInicio: '18:00', horaFin: '19:00', pasada: true, presentes: null, total: null, alumnos: 18, festivo: null, ...cambios,
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

  it('clase festiva: «Festivo» en la hoja', () => {
    expect(estadoClase(clase({ festivo: { id: 1, nombre: null, tipo: 'DIA', deUnDiaYUnaSede: true } }))).toBe('Festivo');
  });

  it('motivos de falta: orden, texto y letra del PDF', () => {
    expect(MOTIVOS_FALTA.map((m) => m.texto)).toEqual(['Injustificada', 'Médica', 'Familiar', 'Otra justificada']);
    expect(textoMotivo('MEDICA')).toBe('Médica');
    expect(textoMotivo(null)).toBe('');
    expect(['INJUSTIFICADA', 'MEDICA', 'FAMILIAR', 'OTRA_JUSTIFICADA'].map((m) => letraMotivo(m as never))).toEqual(['I', 'M', 'F', 'J']);
  });

  it('faltas de la barra: «Ninguna falta», «1 falta», «2 faltas · 1 con motivo»', () => {
    expect(textoFaltas(0, 0)).toBe('Ninguna falta');
    expect(textoFaltas(1, 0)).toBe('1 falta');
    expect(textoFaltas(2, 1)).toBe('2 faltas · 1 con motivo');
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
