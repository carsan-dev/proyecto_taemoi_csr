import { ANDEN, BLANCO, COLOR_POR_DEFECTO, TINTA, contraste, derivarColores, esColorHex, hexARgb, rgbAHex } from './colores';

/** `derivarColores` del boceto «La línea comarcal» (fase4-web-plan.md §6, «Datos»): contraste AA con cualquier color. */
describe('colores · derivarColores', () => {
  it('contraste WCAG: blanco y negro 21:1, mismo color 1:1, azul del boceto sobre blanco ≥ 4,5:1', () => {
    expect(contraste('#FFFFFF', '#000000')).toBeCloseTo(21, 5);
    expect(contraste('#0B5CAD', '#0B5CAD')).toBeCloseTo(1, 5);
    expect(contraste('#0B5CAD', BLANCO)).toBeGreaterThan(4.5);
  });

  it('hexadecimal: corto y largo, sin almohadilla y en mayúsculas al volver', () => {
    expect(hexARgb('#abc')).toEqual([0xaa, 0xbb, 0xcc]);
    expect(hexARgb('0b5cad')).toEqual([11, 92, 173]);
    expect(rgbAHex([11, 92, 173])).toBe('#0B5CAD');
    expect(rgbAHex([300, -4, 12.6])).toBe('#FF000D');
    expect(esColorHex('#0B5CAD')).toBeTrue();
    expect(esColorHex('fff')).toBeTrue();
    expect(esColorHex('rojo')).toBeFalse();
    expect(esColorHex(null)).toBeFalse();
  });

  it('el azul del boceto se queda tal cual, con texto blanco, y el hover se oscurece', () => {
    const c = derivarColores('#0B5CAD');
    expect(c.base).toBe('#0B5CAD');
    expect(c.trazo).toBe('#0B5CAD');
    expect(c.botonFondo).toBe('#0B5CAD');
    expect(c.botonTinta).toBe(BLANCO);
    expect(c.texto).toBe('#0B5CAD');
    expect(c.tarifaFondo).toBe('#0B5CAD');
    expect(c.tarifaTinta).toBe(BLANCO);
    expect(c.tarifaBotonTinta).toBe('#0B5CAD');
    expect(contraste(c.botonHover, BLANCO)).toBeGreaterThan(contraste(c.botonFondo, BLANCO));
  });

  it('sin color o con uno que no es hexadecimal: el del boceto', () => {
    expect(derivarColores(null).base).toBe(COLOR_POR_DEFECTO);
    expect(derivarColores('azul').base).toBe(COLOR_POR_DEFECTO);
    expect(derivarColores('#abc').base).toBe('#AABBCC');
  });

  it('un amarillo claro: el trazo se oscurece hasta 3:1 y el botón lleva tinta oscura', () => {
    const c = derivarColores('#F2C230');
    expect(c.trazo).not.toBe('#F2C230');
    expect(c.cTrazo).toBeGreaterThanOrEqual(3);
    expect(c.botonTinta).toBe(TINTA);
    expect(c.tarifaFondo).toBe('#F2C230');
    expect(c.tarifaTinta).toBe(TINTA);
    expect(c.tarifaBotonTinta).toBe(TINTA);
  });

  it('un color medio sin AA con blanco ni con tinta: el botón se oscurece para el blanco', () => {
    const c = derivarColores('#7A7A7A');
    expect(c.trazo).toBe('#7A7A7A');
    expect(c.botonFondo).not.toBe(c.trazo);
    expect(c.botonTinta).toBe(BLANCO);
    expect(c.cBoton).toBeGreaterThanOrEqual(4.5);
  });

  for (const color of ['#0B5CAD', '#B3261E', '#F2C230', '#FFFFFF', '#000000', '#7FFF00', '#00B3B3', '#808080', '#7A7A7A', '#FF69B4', '#1E7B34']) {
    it(`AA en todas las combinaciones con ${color}`, () => {
      const c = derivarColores(color);
      expect(c.cTrazo).withContext('trazo sobre blanco y andén').toBeGreaterThanOrEqual(3);
      expect(contraste(c.trazo, ANDEN)).toBeGreaterThanOrEqual(3);
      expect(c.cBoton).withContext('botón').toBeGreaterThanOrEqual(4.5);
      expect(c.cBotonHover).withContext('hover del botón').toBeGreaterThanOrEqual(4.5);
      expect(c.cTexto).withContext('enlaces sobre blanco y andén').toBeGreaterThanOrEqual(4.5);
      expect(c.cTarifa).withContext('tarifas').toBeGreaterThanOrEqual(4.5);
      expect(c.cTarifaBoton).withContext('botón blanco de tarifas').toBeGreaterThanOrEqual(4.5);
    });
  }
});
