import {
  mediasVisibles, notaPasos, pasoBloqueado, textoApto, textoCuentaRallas, textoFaltaRallas, textoListo, textoOcultoRallas,
} from './rallas-textos';

describe('rallas-textos (decisiones 510-513, textos del boceto aprobado)', () => {
  it('la cuenta: «Sin rallas», «Media ralla de 5», «2 y media de 5 rallas», «5 de 5 rallas»', () => {
    expect(textoCuentaRallas(0)).toBe('Sin rallas');
    expect(textoCuentaRallas(1)).toBe('Media ralla de 5');
    expect(textoCuentaRallas(2)).toBe('1 de 5 rallas');
    expect(textoCuentaRallas(5)).toBe('2 y media de 5 rallas');
    expect(textoCuentaRallas(9)).toBe('4 y media de 5 rallas');
    expect(textoCuentaRallas(10)).toBe('5 de 5 rallas');
  });

  it('lo que falta para el examen', () => {
    expect(textoFaltaRallas(0)).toBe('Le faltan 5 para el examen.');
    expect(textoFaltaRallas(5)).toBe('Le faltan 2 y media para el examen.');
    expect(textoFaltaRallas(8)).toBe('Le falta 1 para el examen.');
    expect(textoFaltaRallas(9)).toBe('Le falta media para el examen.');
    expect(textoFaltaRallas(10)).toBe('');
  });

  it('el texto para el lector de pantalla', () => {
    expect(textoOcultoRallas(0)).toBe('sin rallas');
    expect(textoOcultoRallas(1)).toBe('media ralla de 5');
    expect(textoOcultoRallas(5)).toBe('2 y media de 5 rallas');
  });

  it('listo/lista y apto/apta según el género; sin dato, en masculino', () => {
    expect(textoListo('FEMENINO')).toBe('Lista para examen');
    expect(textoListo('MASCULINO')).toBe('Listo para examen');
    expect(textoListo(null)).toBe('Listo para examen');
    expect(textoApto('FEMENINO')).toBe('apta');
    expect(textoApto(undefined)).toBe('apto');
  });

  it('pasos bloqueados en los límites: en 0 los dos «−», con media «− Una», con 9 «+ Una», con 10 los dos «+»', () => {
    const bloqueados = (n: number) => [-2, -1, 1, 2].filter((p) => pasoBloqueado(n, p));
    expect(bloqueados(0)).toEqual([-2, -1]);
    expect(bloqueados(1)).toEqual([-2]);
    expect(bloqueados(5)).toEqual([]);
    expect(bloqueados(9)).toEqual([2]);
    expect(bloqueados(10)).toEqual([1, 2]);
  });

  it('la nota de los pasos', () => {
    expect(notaPasos(0)).toBe('Aún no tiene rallas.');
    expect(notaPasos(1)).toBe('Solo tiene media ralla.');
    expect(notaPasos(9)).toBe('Solo cabe media ralla más.');
    expect(notaPasos(5)).toBe('');
    expect(notaPasos(10)).toBe('');
  });

  it('las medias solo se pintan si las rallas se usan', () => {
    expect(mediasVisibles({ mediasRallas: 3, aplicaRallas: true })).toBe(3);
    expect(mediasVisibles({ mediasRallas: 3, aplicaRallas: false })).toBeNull();
    expect(mediasVisibles({ mediasRallas: null, aplicaRallas: true })).toBeNull();
    expect(mediasVisibles({})).toBeNull();
    expect(mediasVisibles(null)).toBeNull();
    expect(mediasVisibles({ mediasRallas: 14, aplicaRallas: true })).toBe(10);
  });
});
