// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { getDeporteLabel } from './deporte';
import { fijarCatalogoActual, restablecerCatalogoActual } from '../features/catalogo/testing/catalogo-actual.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../features/catalogo/testing/catalogo.fixtures';

describe('getDeporteLabel (caracterización)', () => {
  // getDeporteLabel lee el catálogo cargado: equivalencia con el fixture de Taemoi (rebanada 3).
  beforeEach(() => fijarCatalogoActual(CATALOGO_TAEMOI));
  afterEach(() => restablecerCatalogoActual());

  it('segunda escuela: nombres de su catálogo; los de Taemoi no se inventan (D15)', () => {
    fijarCatalogoActual(CATALOGO_SEGUNDA_ESCUELA);
    expect(getDeporteLabel('JUDO')).toBe('Judo');
    expect(getDeporteLabel('TAEKWONDO')).toBe('TAEKWONDO');
    restablecerCatalogoActual();
    expect(getDeporteLabel('TAEKWONDO')).toBe('TAEKWONDO');
  });

  const etiquetas: [string, string][] = [
    ['TAEKWONDO', 'Taekwondo'],
    ['KICKBOXING', 'Kickboxing'],
    ['PILATES', 'Pilates'],
    ['DEFENSA_PERSONAL_FEMENINA', 'Defensa Personal Femenina'],
    ['DESCONOCIDO', 'DESCONOCIDO'],
    ['taekwondo', 'taekwondo'],
    ['', ''],
  ];
  etiquetas.forEach(([codigo, etiqueta]) => {
    it(`getDeporteLabel(${JSON.stringify(codigo)}) -> ${JSON.stringify(etiqueta)}`, () => {
      expect(getDeporteLabel(codigo)).toBe(etiqueta);
    });
  });
});
