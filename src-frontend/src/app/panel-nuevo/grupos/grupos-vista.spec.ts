import { GrupoResumen } from '../../interfaces/grupo-resumen';
import { hora, rangoEdad, resumenHorario, seccionesPorDisciplina, textoTurno } from './grupos-vista';

const T = (id: number, diaSemana: string, horaInicio: string, horaFin = '19:00') => ({ id, diaSemana, horaInicio, horaFin });

function grupo(id: number, deporte: string | undefined, numAlumnos = 1): GrupoResumen {
  return { id, nombre: `Grupo ${id}`, deporte, rangoEdadMin: null, rangoEdadMax: null, numAlumnos, turnos: [] };
}

describe('grupos-vista (8b-3c)', () => {
  it('rango de edad: «6–12 años», desde, hasta y «Sin rango» (los casos de hoy)', () => {
    expect(rangoEdad(6, 12)).toBe('6–12 años');
    expect(rangoEdad(0, 3)).toBe('0–3 años');
    expect(rangoEdad(16, null)).toBe('Desde 16 años');
    expect(rangoEdad(null, 60)).toBe('Hasta 60 años');
    expect(rangoEdad(null, null)).toBe('Sin rango');
    expect(rangoEdad(undefined, undefined)).toBe('Sin rango');
  });

  it('hora sin segundos y turno «Lunes 18:00–19:00»', () => {
    expect(hora('18:00:00')).toBe('18:00');
    expect(hora('9:30')).toBe('9:30');
    expect(hora(null)).toBe('');
    expect(textoTurno(T(1, 'Lunes', '18:00:00', '19:00:00'))).toBe('Lunes 18:00–19:00');
  });

  it('horario resumido: días con la misma hora, «N turnos» si difieren, «Sin turnos» sin ninguno', () => {
    expect(resumenHorario([])).toBe('Sin turnos');
    expect(resumenHorario([T(1, 'Viernes', '17:00')])).toBe('V · 17:00');
    expect(resumenHorario([T(1, 'Lunes', '18:00'), T(2, 'Miércoles', '18:00')])).toBe('L y X · 18:00');
    // Ordenados por la semana, no por la llegada.
    expect(resumenHorario([T(1, 'Viernes', '18:00'), T(2, 'Lunes', '18:00'), T(3, 'Miércoles', '18:00:00')])).toBe('L, X y V · 18:00');
    expect(resumenHorario([T(1, 'Sábado', '10:00'), T(2, 'Domingo', '10:00'), T(3, 'Martes', '10:00'), T(4, 'Jueves', '10:00')])).toBe(
      'M, J, S y D · 10:00'
    );
    expect(resumenHorario([T(1, 'Lunes', '18:00'), T(2, 'Miércoles', '19:00')])).toBe('2 turnos');
    expect(resumenHorario([T(1, 'Lunes', '18:00'), T(2, 'Miércoles', '19:00'), T(3, 'Viernes', '18:00')])).toBe('3 turnos');
  });

  it('horario resumido con otro separador entre los días y la hora', () => {
    expect(resumenHorario([T(1, 'Lunes', '18:00'), T(2, 'Miércoles', '18:00')], ' ')).toBe('L y X 18:00');
    expect(resumenHorario([T(1, 'Lunes', '18:00'), T(2, 'Miércoles', '19:00')], ' ')).toBe('2 turnos');
    expect(resumenHorario([], ' ')).toBe('Sin turnos');
  });

  it('secciones en el orden del catálogo, cada una con sus grupos en el orden de hoy y su total de alumnos', () => {
    const disciplinas = [
      { codigo: 'TAEKWONDO', nombre: 'Taekwondo' },
      { codigo: 'KICKBOXING', nombre: 'Kickboxing' },
      { codigo: 'PILATES', nombre: 'Pilates' },
    ];
    const grupos = [grupo(1, 'KICKBOXING', 4), grupo(2, 'TAEKWONDO', 10), grupo(3, 'ANTIGUA', 2), grupo(4, 'TAEKWONDO', 1_200), grupo(5, undefined, 3)];
    const s = seccionesPorDisciplina(grupos, disciplinas, (c) => (c === 'ANTIGUA' ? 'Antigua' : c));
    expect(s.map((x) => [x.codigo, x.nombre, x.grupos.map((g) => g.id), x.alumnos])).toEqual([
      ['TAEKWONDO', 'Taekwondo', [2, 4], 1_210],
      ['KICKBOXING', 'Kickboxing', [1], 4],
      ['PILATES', 'Pilates', [], 0],
      ['ANTIGUA', 'Antigua', [3], 2],
      ['', 'Sin disciplina', [5], 3],
    ]);
    // Color de disciplina por el orden del catálogo (DESIGN.md: disc-1 a disc-4); fuera del catálogo, sin color propio.
    expect(s.map((x) => x.color)).toEqual(['1', '2', '3', 'otra', 'otra']);
  });
});
