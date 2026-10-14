// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { TipoGrado } from './tipo-grado';

describe('TipoGrado (caracterización)', () => {
  it('conjunto exacto de valores y orden', () => {
    expect(Object.values(TipoGrado) as string[]).toEqual([
      'BLANCO', 'BLANCO_AMARILLO', 'AMARILLO', 'AMARILLO_NARANJA', 'NARANJA', 'NARANJA_VERDE',
      'VERDE', 'VERDE_AZUL', 'AZUL', 'AZUL_ROJO', 'ROJO', 'ROJO_NEGRO', 'NEGRO',
    ]);
  });

  it('cada clave coincide con su valor', () => {
    Object.entries(TipoGrado).forEach(([k, v]) => expect(v).toBe(k));
  });
});
