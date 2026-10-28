import { importeCargo } from './importe-cargo';

describe('importeCargo (decisión 297, la regla de ImporteCargo de la API)', () => {
  it('cuenta el precio nulo o ausente como 0', () => {
    expect(importeCargo(null, 3)).toBe(0);
    expect(importeCargo(undefined, 3)).toBe(0);
    expect(importeCargo(null, null)).toBe(0);
  });

  it('cuenta la cantidad nula o ausente como 1', () => {
    expect(importeCargo(28, null)).toBe(28);
    expect(importeCargo(28, undefined)).toBe(28);
  });

  it('cuenta la cantidad 0 o negativa como 1', () => {
    expect(importeCargo(28, 0)).toBe(28);
    expect(importeCargo(28, -2)).toBe(28);
  });

  it('multiplica el precio por la cantidad desde 1', () => {
    expect(importeCargo(28, 1)).toBe(28);
    expect(importeCargo(28, 3)).toBe(84);
  });

  it('no redondea los decimales (se formatean al mostrar)', () => {
    expect(importeCargo(12.5, 3)).toBe(37.5);
    expect(importeCargo(0.1, 3)).toBe(0.1 * 3);
  });

  it('con precio 0 da 0', () => {
    expect(importeCargo(0, 5)).toBe(0);
  });
});
