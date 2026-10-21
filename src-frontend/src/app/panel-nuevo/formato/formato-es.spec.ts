import { agruparMiles, eur, fecha, mes, numero, plural } from './formato-es';

describe('formato-es', () => {
  const NBSP = ' ';

  it('eur(1010) separa miles con punto, coma decimal y espacio duro antes de €', () => {
    expect(eur(1010)).toBe(`1.010,00${NBSP}€`);
  });

  it('eur(0) muestra cero con dos decimales', () => {
    expect(eur(0)).toBe(`0,00${NBSP}€`);
  });

  it('eur agrupa también los miles de cuatro cifras y redondea a céntimos', () => {
    expect(eur(1234.5)).toBe(`1.234,50${NBSP}€`);
    expect(eur(9999.999)).toBe(`10.000,00${NBSP}€`);
  });

  it('eur con negativos conserva el signo', () => {
    expect(eur(-25.5)).toBe(`-25,50${NBSP}€`);
    expect(eur(-1010)).toBe(`-1.010,00${NBSP}€`);
  });

  it('eur con null, undefined o NaN devuelve cadena vacía', () => {
    expect(eur(null)).toBe('');
    expect(eur(undefined)).toBe('');
    expect(eur(Number.NaN)).toBe('');
  });

  it('numero(1044) agrupa miles aunque sean cuatro cifras', () => {
    expect(numero(1044)).toBe('1.044');
    expect(numero(12)).toBe('12');
    expect(numero(1234567)).toBe('1.234.567');
  });

  it('plural elige singular solo con 1 y antepone el número', () => {
    expect(plural(1, 'clase', 'clases')).toBe('1 clase');
    expect(plural(7, 'clase', 'clases')).toBe('7 clases');
    expect(plural(0, 'clase', 'clases')).toBe('0 clases');
    expect(plural(1044, 'alumno', 'alumnos')).toBe('1.044 alumnos');
  });

  it('mes escribe «octubre de 2026»', () => {
    expect(mes(new Date(2026, 9, 21))).toBe('octubre de 2026');
    expect(mes('2026-10-21')).toBe('octubre de 2026');
  });

  it('fecha escribe día/mes/año con ceros y no desplaza el día de una fecha ISO', () => {
    expect(fecha(new Date(2026, 2, 5))).toBe('05/03/2026');
    expect(fecha('2026-10-01')).toBe('01/10/2026');
  });

  it('eur normaliza el cero negativo tras redondear a céntimos', () => {
    expect(eur(-0.001)).toBe(`0,00${NBSP}€`);
    expect(eur(-0)).toBe(`0,00${NBSP}€`);
    expect(eur(-0.004)).toBe(`0,00${NBSP}€`);
    expect(eur(-0.01)).toBe(`-0,01${NBSP}€`);
  });

  it('numero normaliza el cero negativo', () => {
    expect(numero(-0)).toBe('0');
  });

  it('numero agrupa los negativos', () => {
    expect(numero(-1044)).toBe('-1.044');
  });

  it('plural con NaN o no finito devuelve cadena vacía', () => {
    expect(plural(Number.NaN, 'clase', 'clases')).toBe('');
    expect(plural(Number.POSITIVE_INFINITY, 'clase', 'clases')).toBe('');
    expect(plural(Number.NEGATIVE_INFINITY, 'clase', 'clases')).toBe('');
  });

  it('fecha y mes devuelven cadena vacía sin valor o con fecha inválida en lugar de lanzar', () => {
    for (const invalido of [null, undefined, '', 'no es una fecha', new Date(Number.NaN)]) {
      expect(fecha(invalido)).toBe('');
      expect(mes(invalido)).toBe('');
    }
  });

  describe('agruparMiles', () => {
    it('agrupa de tres en tres con punto', () => {
      expect(agruparMiles('0')).toBe('0');
      expect(agruparMiles('999')).toBe('999');
      expect(agruparMiles('1000')).toBe('1.000');
      expect(agruparMiles('1044')).toBe('1.044');
      expect(agruparMiles('12345')).toBe('12.345');
      expect(agruparMiles('1234567')).toBe('1.234.567');
    });

    it('conserva el signo menos', () => {
      expect(agruparMiles('-1044')).toBe('-1.044');
      expect(agruparMiles('-999')).toBe('-999');
    });
  });
});
