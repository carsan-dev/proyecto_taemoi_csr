import { contraste, fechaParaCampo, lineaGrado, resumenPendiente } from './ficha-vista';

/** Valores de DESIGN.md (panel-nuevo.scss): el borde de los cinturones y los fondos sobre los que va. */
const CINTO_BORDE = '#7a8290';
const SUPERFICIE = '#ffffff';
const SUPERFICIE_2 = '#fafaf8';
const FONDO = '#f5f5f2';

describe('ficha-vista (8b-3b, cálculos puros del carnet)', () => {
  it('contraste WCAG: negro sobre blanco 21:1, igual en los dos sentidos, y con o sin almohadilla', () => {
    expect(contraste('#000000', '#ffffff')).toBeCloseTo(21, 5);
    expect(contraste('#ffffff', '#000')).toBeCloseTo(21, 5);
    expect(contraste('#ffffff', 'ffffff')).toBeCloseTo(1, 5);
  });

  it('el borde de los cinturones claros llega a 3:1 frente al carnet, la tabla y el fondo (WCAG 1.4.11)', () => {
    expect(contraste(CINTO_BORDE, SUPERFICIE)).toBeGreaterThanOrEqual(3);
    expect(contraste(CINTO_BORDE, SUPERFICIE_2)).toBeGreaterThanOrEqual(3);
    expect(contraste(CINTO_BORDE, FONDO)).toBeGreaterThanOrEqual(3);
    // Sin borde, el cinturón blanco no se distingue del carnet: por eso se corrige.
    expect(contraste('#ffffff', SUPERFICIE)).toBeLessThan(3);
    expect(contraste('#ffff00', SUPERFICIE)).toBeLessThan(3);
  });

  it('resumen del pendiente: plural y la fecha del cargo más antiguo en es-ES', () => {
    expect(
      resumenPendiente({
        alumnoId: 7,
        importe: 85,
        cargos: 3,
        detalle: [
          { productoAlumnoId: 2, concepto: 'B', importe: 30, vencidoDesde: '2026-10-06' },
          { productoAlumnoId: 1, concepto: 'A', importe: 30, vencidoDesde: '2026-09-06' },
          { productoAlumnoId: 3, concepto: 'C', importe: 25, vencidoDesde: '2026-10-06' },
        ],
      })
    ).toBe('3 cargos, el más antiguo vencido desde 06/09/2026');
    expect(
      resumenPendiente({
        alumnoId: 7,
        importe: 30,
        cargos: 1,
        detalle: [{ productoAlumnoId: 1, concepto: 'A', importe: 30, vencidoDesde: '2026-10-06' }],
      })
    ).toBe('1 cargo, vencido desde 06/10/2026');
    expect(
      resumenPendiente({ alumnoId: 7, importe: 1200, cargos: 1044, detalle: [] })
    ).toBe('1.044 cargos');
  });

  it('línea del grado en el carnet: grado y tiempo con él, o «desde hoy»', () => {
    expect(lineaGrado('BLANCO', '3 años y 6 meses')).toBe('BLANCO, 3 años y 6 meses');
    expect(lineaGrado('NARANJA', 'Desde hoy')).toBe('NARANJA, desde hoy');
    expect(lineaGrado('NARANJA', '')).toBe('NARANJA');
    expect(lineaGrado('', '')).toBe('');
  });

  it('fecha para un campo de fecha: ISO, instante en milisegundos de la API (hora local) o Date; si no, vacío', () => {
    expect(fechaParaCampo('2026-10-06')).toBe('2026-10-06');
    expect(fechaParaCampo(new Date(2019, 0, 1, 0, 0).getTime())).toBe('2019-01-01');
    expect(fechaParaCampo(new Date(2023, 2, 22))).toBe('2023-03-22');
    expect(fechaParaCampo(null)).toBe('');
    expect(fechaParaCampo('')).toBe('');
    expect(fechaParaCampo('no es una fecha')).toBe('');
  });
});
