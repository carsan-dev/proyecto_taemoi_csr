import { BLANCO, POR_DEFECTO, TINTA, contraste, esColorValido, formatoRazon, temaDe } from './regla-contraste';

/**
 * Pieza 8b-T: la vista previa de «Logo y color» calcula el tema con la misma regla que el servidor
 * (`ReglaContraste.java`). Los vectores son los de `ReglaContrasteTest#vectoresCompartidosConElFrontend`.
 */
describe('regla-contraste (vista previa de «Logo y color»)', () => {
  const vectores: [string, string, string, string, string, string, boolean][] = [
    ['#1B2B2E', '#1B2B2E', '#FFFFFF', '#C6CACB', '#1B2B2E', '#E8EAEA', false],
    ['#3E9B4F', '#3E9B4F', '#1A2230', '#1A2230', '#24853A', '#ECF5ED', false],
    ['#808080', '#747474', '#FFFFFF', '#FFFFFF', '#747474', '#F1F1F1', true],
    ['#FFD400', '#FFD400', '#1A2230', '#534F24', '#986E00', '#FFFBE6', false],
  ];

  for (const [guardado, primario, sobrePrimario, sobrePrimario2, primarioTinta, primarioSuave, ajustado] of vectores) {
    it(`${guardado} da lo mismo que el servidor`, () => {
      expect(temaDe(guardado)).toEqual({ primario, sobrePrimario, sobrePrimario2, primarioTinta, primarioSuave, ajustado });
    });
  }

  it('Moiskimdo: texto blanco a 14,67:1', () => {
    const tema = temaDe('#1B2B2E');
    expect(tema.sobrePrimario).toBe(BLANCO);
    expect(contraste(tema.sobrePrimario, tema.primario)).toBeCloseTo(14.67, 2);
  });

  it('sin color usa #1A2230 (decisión 257)', () => {
    expect(POR_DEFECTO).toBe('#1A2230');
    expect(temaDe(null).primario).toBe('#1A2230');
    expect(temaDe(null).sobrePrimario).toBe(BLANCO);
  });

  it('minúsculas igual que mayúsculas', () => {
    expect(temaDe('#3e9b4f')).toEqual(temaDe('#3E9B4F'));
  });

  it('rejilla de 4.096 colores: texto, secundario y tinta ≥ 4,5:1', () => {
    const fallos: string[] = [];
    const nivel = (n: number) => (n * 17).toString(16).padStart(2, '0').toUpperCase();
    for (let r = 0; r < 16; r++) {
      for (let g = 0; g < 16; g++) {
        for (let b = 0; b < 16; b++) {
          const color = `#${nivel(r)}${nivel(g)}${nivel(b)}`;
          const tema = temaDe(color);
          if (contraste(tema.sobrePrimario, tema.primario) < 4.5) fallos.push(`${color} texto`);
          if (contraste(tema.sobrePrimario2, tema.primario) < 4.5) fallos.push(`${color} secundario`);
          if (contraste(tema.primarioTinta, BLANCO) < 4.5) fallos.push(`${color} tinta`);
          if (contraste(TINTA, tema.primarioSuave) < 4.5) fallos.push(`${color} suave`);
        }
      }
    }
    expect(fallos).toEqual([]);
  });

  it('valida el formato #RRGGBB exacto', () => {
    expect(esColorValido('#a1B2c3')).toBeTrue();
    for (const malo of ['#12345', '123456', '#GGGGGG', ' #123456', '#1234567', '', 'rojo', '#123456 ']) {
      expect(esColorValido(malo)).withContext(malo).toBeFalse();
    }
  });

  it('la razón se muestra en es-ES truncada a dos decimales (nunca redondea hacia arriba hasta 4,5)', () => {
    expect(formatoRazon(14.6688)).toBe('14,66:1');
    expect(formatoRazon(4.4999)).toBe('4,49:1');
    expect(formatoRazon(21)).toBe('21,00:1');
  });
});
