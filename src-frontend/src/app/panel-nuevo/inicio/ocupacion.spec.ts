import { GrupoOcupacion, aforosPorTurno, calcularOcupacion, clasesDeHoy, claveDia, pasoDe } from './ocupacion';

function grupo(id: number, numAlumnos: number, turnos: [string, string, string][], deporte = 'TAEKWONDO'): GrupoOcupacion {
  return {
    id,
    nombre: `Grupo ${id}`,
    deporte,
    numAlumnos,
    turnos: turnos.map(([diaSemana, horaInicio, horaFin], i) => ({ id: id * 10 + i, diaSemana, horaInicio, horaFin })),
  };
}

describe('ocupacion (8b-2, decisión 245)', () => {
  const grupos: GrupoOcupacion[] = [
    // Dos días: sus 6 alumnos cuentan el lunes y el miércoles.
    grupo(1, 6, [['Lunes', '17:00', '18:00'], ['Miércoles', '17:00:00', '18:00']]),
    // Mismo día y franja que otro grupo: la celda suma inscritos y plazas.
    grupo(2, 4, [['Lunes', '19:00', '20:00']]),
    grupo(3, 3, [['Lunes', '19:00', '20:30']]),
    grupo(4, 9, [['Miercoles', '9:30', '10:30']]),
    grupo(5, 30, [['Sábado', '11:00', '12:00']]),
    grupo(6, 0, [['Viernes', '20:15', '21:15']]),
  ];

  it('franjas ordenadas por hora de inicio, sin repetir y en «HH:mm»', () => {
    const o = calcularOcupacion(grupos, 36);
    expect(o.franjas).toEqual(['09:30', '11:00', '17:00', '19:00', '20:15']);
    expect(o.filas.map((f) => f.franja)).toEqual(o.franjas);
  });

  it('columnas de lunes a sábado; el domingo solo si tiene clase', () => {
    expect(calcularOcupacion(grupos, 36).dias.map((d) => d.corto)).toEqual(['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb']);
    const conDomingo = calcularOcupacion([...grupos, grupo(7, 2, [['Domingo', '10:00', '11:00']])], 36);
    expect(conDomingo.dias.map((d) => d.corto)).toEqual(['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']);
  });

  it('celda sin clase: sin paso, sin texto y con su descripción', () => {
    const o = calcularOcupacion(grupos, 36);
    const martes = o.filas[0].celdas[1];
    expect(martes.dia).toBe('martes');
    expect(martes.turnos).toBe(0);
    expect(martes.paso).toBeNull();
    expect(martes.texto).toBe('');
    expect(martes.descripcion).toBe('Martes, 09:30: sin clase');
  });

  it('capacidad = turnos × límite, e inscritos = suma de los grupos de la celda', () => {
    const o = calcularOcupacion(grupos, 36);
    const lunes19 = o.filas[3].celdas[0];
    expect(lunes19.turnos).toBe(2);
    expect(lunes19.plazas).toBe(72);
    expect(lunes19.inscritos).toBe(7);
    expect(lunes19.texto).toBe('7/72');
    expect(calcularOcupacion(grupos, 20).filas[3].celdas[0].plazas).toBe(40);
  });

  it('9-7: cada turno cuenta el aforo de su sede y, sin él, el límite común', () => {
    // Grupo 2 (turno 20) con aforo 10; grupo 3 (turno 30) sin dato: el límite común.
    const o = calcularOcupacion(grupos, 36, new Map([[20, 10]]));
    const lunes19 = o.filas[3].celdas[0];
    expect(lunes19.plazas).toBe(46);
    expect(lunes19.texto).toBe('7/46');
    expect(lunes19.descripcion).toContain('7 inscritos de 46 plazas (15 %)');
    expect(o.plazas).toBe(calcularOcupacion(grupos, 36).plazas - 26);
  });

  it('9-7: sin aforos, igual que con el límite común', () => {
    expect(calcularOcupacion(grupos, 36, new Map())).toEqual(calcularOcupacion(grupos, 36));
  });

  it('9-7: las clases de hoy usan el aforo de su turno', () => {
    const lunes = new Date(2026, 9, 26, 10, 0);
    const clases = clasesDeHoy(grupos, 36, lunes, new Map([[20, 10]]));
    expect(clases.find((c) => c.turnoId === 20)?.plazas).toBe(10);
    expect(clases.find((c) => c.turnoId === 30)?.plazas).toBe(36);
  });

  it('9-7: aforosPorTurno lee el aforo del resumen y descarta lo que no es un número positivo', () => {
    const aforos = aforosPorTurno([{ id: 1, aforoEfectivo: 12 }, { id: 2 }, { id: 3, aforoEfectivo: 0 }]);
    expect([...aforos]).toEqual([[1, 12]]);
    expect(aforosPorTurno(null).size).toBe(0);
  });

  it('un grupo con dos días cuenta sus alumnos en ambos', () => {
    const o = calcularOcupacion(grupos, 36);
    expect(o.filas[2].celdas[0].inscritos).toBe(6);
    expect(o.filas[2].celdas[2].inscritos).toBe(6);
  });

  it('pasos 0/25/50/75 %: menos del 25, 25-49, 50-74 y 75 o más', () => {
    expect(pasoDe(0, 36)).toBe(1);
    expect(pasoDe(8, 36)).toBe(1);
    expect(pasoDe(9, 36)).toBe(2);
    expect(pasoDe(17, 36)).toBe(2);
    expect(pasoDe(18, 36)).toBe(3);
    expect(pasoDe(26, 36)).toBe(3);
    expect(pasoDe(27, 36)).toBe(4);
    expect(pasoDe(40, 36)).toBe(4);
    const o = calcularOcupacion(grupos, 36);
    expect(o.filas[1].celdas[5].paso).toBe(4);
    expect(o.filas[4].celdas[4].paso).toBe(1);
  });

  it('el texto y la descripción de cada celda con clase', () => {
    const o = calcularOcupacion(grupos, 36);
    const sabado = o.filas[1].celdas[5];
    expect(sabado.texto).toBe('30/36');
    expect(sabado.descripcion).toBe('Sábado, 11:00: 30 inscritos de 36 plazas (83 %), 1 turno');
    const lunes19 = o.filas[3].celdas[0];
    expect(lunes19.descripcion).toBe('Lunes, 19:00: 7 inscritos de 72 plazas (10 %), 2 turnos');
    const viernes = o.filas[4].celdas[4];
    expect(viernes.texto).toBe('0/36');
    expect(viernes.descripcion).toBe('Viernes, 20:15: 0 inscritos de 36 plazas (0 %), 1 turno');
  });

  it('totales de la semana: inscritos y plazas de todas las celdas', () => {
    const o = calcularOcupacion(grupos, 36);
    expect(o.inscritos).toBe(6 + 6 + 7 + 9 + 30 + 0);
    expect(o.plazas).toBe(36 * 7);
  });

  it('sin grupos ni turnos: sin franjas', () => {
    const o = calcularOcupacion([], 36);
    expect(o.franjas).toEqual([]);
    expect(o.inscritos).toBe(0);
    expect(o.plazas).toBe(0);
  });

  it('claveDia normaliza tildes y mayúsculas', () => {
    expect(claveDia('Miércoles')).toBe('miercoles');
    expect(claveDia('SABADO')).toBe('sabado');
    expect(claveDia(new Date(2026, 9, 21))).toBe('miercoles');
  });

  describe('clasesDeHoy', () => {
    const miercoles1730 = new Date(2026, 9, 21, 17, 30);

    it('las del día por hora, con la siguiente resaltada', () => {
      const hoy = clasesDeHoy(grupos, 36, miercoles1730);
      expect(hoy.map((c) => [c.horaInicio, c.grupoId])).toEqual([
        ['09:30', 4],
        ['17:00', 1],
      ]);
      expect(hoy.every((c) => !c.siguiente)).toBeTrue();

      const temprano = clasesDeHoy(grupos, 36, new Date(2026, 9, 21, 8, 0));
      expect(temprano.map((c) => c.siguiente)).toEqual([true, false]);
      expect(temprano[0].plazas).toBe(36);
      expect(temprano[0].inscritos).toBe(9);
    });

    it('un día sin clases: lista vacía', () => {
      expect(clasesDeHoy(grupos, 36, new Date(2026, 9, 20, 10, 0))).toEqual([]);
    });
  });
});
