// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { EDAD_ADULTO_KICKBOXING, EDAD_ADULTO_TAEKWONDO, calcularCategoriaPorEdad, esMenor } from './categoria-por-edad';

// Fecha fija: 15/06/2026 12:00 (hora local)
const HOY = new Date(2026, 5, 15, 12, 0, 0);

function nac(anio: number, mes: number, dia: number): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${anio}-${p(mes)}-${p(dia)}T12:00:00`;
}

/** Fecha de nacimiento que da exactamente `edad` años el día de hoy simulado. */
function conEdad(edad: number): string {
  return nac(2026 - edad, 1, 1);
}

describe('categoria-por-edad (caracterización)', () => {
  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(HOY);
  });
  afterEach(() => jasmine.clock().uninstall());

  it('constantes de edad adulta', () => {
    expect(EDAD_ADULTO_TAEKWONDO).toBe(14);
    expect(EDAD_ADULTO_KICKBOXING).toBe(15);
  });

  describe('esMenor', () => {
    it('sin fecha, vacía o inválida -> menor', () => {
      [null, undefined, '', 'no-es-fecha'].forEach(f => {
        expect(esMenor(f)).toBe(true);
        expect(esMenor(f, 'KICKBOXING')).toBe(true);
      });
      expect(() => esMenor(new Date(NaN))).toThrowError(RangeError); // Date inválido lanza (toISOString)
    });

    // [descripción, fecha, menor en TKD (y otros deportes), menor en KB]
    const casos: [string, string, boolean, boolean][] = [
      ['edad 12', conEdad(12), true, true],
      ['edad 13 que cumple 14 en 2026 (nace 2012-12-31)', nac(2012, 12, 31), false, true],
      ['edad 13 que cumple 14 en 2026 (nace 2012-06-16)', nac(2012, 6, 16), false, true],
      ['edad 13 que cumple 14 en 2027 (nace 2013-01-01)', nac(2013, 1, 1), true, true],
      ['edad 14 (nace 2012-06-15)', nac(2012, 6, 15), false, true],
      ['edad 14 (nace 2012-01-01) cumple 15 en 2027', nac(2012, 1, 1), false, true],
      ['edad 14 que cumple 15 en 2026 (nace 2011-12-31)', nac(2011, 12, 31), false, false],
      ['edad 15 (nace 2011-06-15)', nac(2011, 6, 15), false, false],
      ['edad 16', conEdad(16), false, false],
      ['edad 40', conEdad(40), false, false],
    ];
    casos.forEach(([desc, fecha, tkd, kb]) => {
      it(`${desc}: TKD=${tkd} KB=${kb}`, () => {
        expect(esMenor(fecha, 'TAEKWONDO')).toBe(tkd, 'TAEKWONDO');
        expect(esMenor(fecha)).toBe(tkd, 'por defecto');
        expect(esMenor(fecha, 'KICKBOXING')).toBe(kb, 'KICKBOXING');
        expect(esMenor(fecha, 'PILATES')).toBe(tkd, 'PILATES usa 14');
        expect(esMenor(fecha, 'DEFENSA_PERSONAL_FEMENINA')).toBe(tkd, 'DPF usa 14');
        expect(esMenor(fecha, 'kickboxing')).toBe(tkd, 'minúsculas no es KICKBOXING');
      });
    });

    it('acepta objeto Date', () => {
      expect(esMenor(new Date(2012, 11, 31, 12))).toBe(false);
      expect(esMenor(new Date(2013, 0, 1, 12))).toBe(true);
    });
  });

  describe('calcularCategoriaPorEdad', () => {
    const tabla: [number, string][] = [
      [0, 'Infantil'], [9, 'Infantil'], [10, 'Precadete'], [11, 'Precadete'], [12, 'Cadete'], [13, 'Cadete'],
      [14, 'Junior'], [16, 'Junior'], [17, 'Senior'], [30, 'Senior'],
    ];
    tabla.forEach(([edad, cat]) => {
      it(`edad ${edad} -> ${cat}`, () => {
        expect(calcularCategoriaPorEdad(conEdad(edad))).toBe(cat);
        ['TAEKWONDO', 'KICKBOXING', 'PILATES'].forEach(d =>
          expect(calcularCategoriaPorEdad(conEdad(edad), d)).toBe(cat));
      });
    });

    it('sin fecha o inválida -> cadena vacía', () => {
      [null, undefined, '', 'no-es-fecha'].forEach(f => expect(calcularCategoriaPorEdad(f)).toBe(''));
      expect(() => calcularCategoriaPorEdad(new Date(NaN))).toThrowError(RangeError); // Date inválido lanza
    });

    it('el día anterior al cumpleaños aún no cambia de categoría', () => {
      expect(calcularCategoriaPorEdad(nac(2016, 6, 16))).toBe('Infantil'); // 9 hoy
      expect(calcularCategoriaPorEdad(nac(2016, 6, 15))).toBe('Precadete'); // 10 hoy
    });
  });
});
