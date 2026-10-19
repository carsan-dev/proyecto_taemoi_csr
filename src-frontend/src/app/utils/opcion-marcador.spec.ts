import { esOpcionMarcador } from './opcion-marcador';

describe('esOpcionMarcador', () => {
  const opcion = (value: string, atributos: Record<string, string> = {}): HTMLOptionElement => {
    const o = document.createElement('option');
    o.value = value;
    Object.entries(atributos).forEach(([k, v]) => o.setAttribute(k, v));
    return o;
  };

  it('trata como marcador el valor vacio, "null" y los ngValue nulos de Angular', () => {
    expect(esOpcionMarcador(opcion(''))).toBeTrue();
    expect(esOpcionMarcador(opcion('null'))).toBeTrue();
    expect(esOpcionMarcador(opcion('0: null'))).toBeTrue();
    expect(esOpcionMarcador(opcion('3: undefined'))).toBeTrue();
  });

  it('no trata como marcador una opcion con valor', () => {
    expect(esOpcionMarcador(opcion('TODOS'))).toBeFalse();
    expect(esOpcionMarcador(opcion('1: Kimono'))).toBeFalse();
    expect(esOpcionMarcador(opcion('0'))).toBeFalse();
  });

  it('data-opcion-real anula la regla aunque el valor este vacio', () => {
    expect(esOpcionMarcador(opcion('', { 'data-opcion-real': '' }))).toBeFalse();
  });
});
