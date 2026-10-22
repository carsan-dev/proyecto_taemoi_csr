import {
  PESTANAS,
  barraCobro as barraCobroReal,
  etiquetaPeriodo,
  fechaHora,
  fechaPagoParaCampo,
  nombreExportacion,
  pagadoDeEstado,
} from './tesoreria-vista';

describe('tesoreria-vista (8b-3d)', () => {
  it('pestañas: Pendientes, Cobrados y Todos, con los estados de hoy', () => {
    expect(PESTANAS.map((p) => [p.estado, p.texto])).toEqual([
      ['PENDIENTES', 'Pendientes'],
      ['PAGADOS', 'Cobrados'],
      ['TODOS', 'Todos'],
    ]);
  });

  it('pagadoDeEstado: el mismo `pagado` que la pantalla de hoy', () => {
    expect(pagadoDeEstado('PENDIENTES')).toBeFalse();
    expect(pagadoDeEstado('PAGADOS')).toBeTrue();
    expect(pagadoDeEstado('TODOS')).toBeUndefined();
  });

  it('nombreExportacion: el nombre de archivo de hoy (CSV y PDF)', () => {
    const base = { mes: 10, ano: 2026, deporte: 'TODOS', estado: 'PENDIENTES' as const, soloActivos: true };
    expect(nombreExportacion(base, 'csv')).toBe('tesoreria_2026_10_todos_pendientes_activos.csv');
    expect(nombreExportacion(base, 'pdf')).toBe('tesoreria_2026_10_todos_pendientes_activos.pdf');
    expect(nombreExportacion({ mes: null, ano: null, deporte: 'KICKBOXING', estado: 'PAGADOS', soloActivos: false }, 'csv')).toBe(
      'tesoreria_total_todos_meses_kickboxing_pagados_todos_alumnos.csv'
    );
    expect(nombreExportacion({ ...base, mes: 3, estado: 'TODOS' }, 'pdf')).toBe('tesoreria_2026_03_todos_todos_activos.pdf');
  });

  it('etiquetaPeriodo: los textos de hoy', () => {
    expect(etiquetaPeriodo(null, null)).toBe('Totales (todos los años)');
    expect(etiquetaPeriodo(null, 2026)).toBe('Año 2026');
    expect(etiquetaPeriodo(10, 2026)).toBe('Octubre 2026');
  });

  it('fechaHora: «22/10/2026 09:05»', () => {
    expect(fechaHora(new Date(2026, 9, 22, 9, 5))).toBe('22/10/2026 09:05');
  });

  it('fechaPagoParaCampo: el valor inicial de hoy (fecha ISO del pago, o la de hoy sin pago o con una fecha no válida)', () => {
    const pago = '2026-10-03T12:00:00';
    expect(fechaPagoParaCampo(pago)).toBe(new Date(pago).toISOString().slice(0, 10));
    const hoy = new Date().toISOString().slice(0, 10);
    expect(fechaPagoParaCampo(null)).toBe(hoy);
    expect(fechaPagoParaCampo('no es fecha')).toBe(hoy);
  });

  it('barraCobro: partes en % que suman 100, texto equivalente y sin importes', () => {
    const barraCobro = (a: number, b: number) => {
      const r = barraCobroReal(a, b);
      return { ...r, texto: r.texto.replace(/\s+/g, ' ') };
    };
    expect(barraCobro(12107.5, 6235)).toEqual({
      cobrado: 66,
      pendiente: 34,
      vacia: false,
      texto: 'Cobrado 12.107,50 € (66 %), pendiente 6.235,00 € (34 %)',
    });
    expect(barraCobro(0, 50)).toEqual({ cobrado: 0, pendiente: 100, vacia: false, texto: 'Cobrado 0,00 € (0 %), pendiente 50,00 € (100 %)' });
    expect(barraCobro(1, 999)).toEqual({ cobrado: 0.1, pendiente: 99.9, vacia: false, texto: 'Cobrado 1,00 € (0,1 %), pendiente 999,00 € (99,9 %)' });
    expect(barraCobro(0, 0)).toEqual({ cobrado: 0, pendiente: 0, vacia: true, texto: 'Sin importes en el periodo' });
  });
});
