import { PluralPipe } from './plural.pipe';

describe('PluralPipe', () => {
  const pipe = new PluralPipe();

  it('usa el singular con 1 y el plural en el resto', () => {
    expect(pipe.transform(1, 'clase', 'clases')).toBe('1 clase');
    expect(pipe.transform(7, 'clase', 'clases')).toBe('7 clases');
    expect(pipe.transform(0, 'clase', 'clases')).toBe('0 clases');
  });

  it('agrupa los miles', () => {
    expect(pipe.transform(1044, 'alumno', 'alumnos')).toBe('1.044 alumnos');
  });
});
