import { EurPipe } from './eur.pipe';

describe('EurPipe', () => {
  const pipe = new EurPipe();

  it('formatea importes en es-ES', () => {
    expect(pipe.transform(1010)).toBe('1.010,00 €');
    expect(pipe.transform(0)).toBe('0,00 €');
    expect(pipe.transform(-3)).toBe('-3,00 €');
  });

  it('devuelve cadena vacía sin valor', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
