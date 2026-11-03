import { claveDiaSemana, diasConFinDeSemana } from './dias-semana.util';

describe('diasConFinDeSemana (decisión 380)', () => {
  const base = { dias: ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'], cortos: ['Lun', 'Mar', 'Mié', 'Jue', 'Vie'] };
  const sabado = { dia: 'Sábado', corto: 'Sáb' };
  const domingo = { dia: 'Domingo', corto: 'Dom' };
  const con = (...dias: string[]) => diasConFinDeSemana(base, sabado, domingo, dias.map((diaSemana) => ({ diaSemana })));

  it('sin turnos de fin de semana, los cinco días de siempre', () => {
    expect(con().dias).toEqual(base.dias);
    expect(con('Lunes', 'Viernes').cortos).toEqual(base.cortos);
  });

  it('con un turno en sábado, seis días y Sábado al final', () => {
    expect(con('Lunes', 'Sábado').dias).toEqual([...base.dias, 'Sábado']);
    expect(con('sabado').cortos).toEqual([...base.cortos, 'Sáb']);
  });

  it('solo con domingo, seis días y Domingo detrás del viernes', () => {
    expect(con('DOMINGO').dias).toEqual([...base.dias, 'Domingo']);
  });

  it('con ambos, siete días en orden', () => {
    expect(con('Domingo', 'Sábado').dias).toEqual([...base.dias, 'Sábado', 'Domingo']);
    expect(con('Domingo', 'Sábado').cortos).toEqual([...base.cortos, 'Sáb', 'Dom']);
  });

  it('no modifica la base ni falla con turnos vacíos', () => {
    con('Sábado');
    expect(base.dias.length).toBe(5);
    expect(diasConFinDeSemana(base, sabado, domingo, [null, undefined, {}]).dias.length).toBe(5);
  });

  it('claveDiaSemana ignora tildes y mayúsculas', () => {
    expect(claveDiaSemana('Sábado')).toBe('sabado');
    expect(claveDiaSemana('MIÉRCOLES')).toBe('miercoles');
    expect(claveDiaSemana(null)).toBe('');
  });
});
