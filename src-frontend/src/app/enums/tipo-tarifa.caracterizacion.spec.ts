// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { TipoTarifa } from './tipo-tarifa';

describe('TipoTarifa (caracterización)', () => {
  it('conjunto exacto de valores y orden', () => {
    expect(Object.values(TipoTarifa) as string[]).toEqual([
      'ADULTO', 'ADULTO_GRUPO', 'FAMILIAR', 'INFANTIL', 'INFANTIL_GRUPO', 'HERMANOS',
      'PADRES_HIJOS', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA',
    ]);
  });

  it('cada clave coincide con su valor', () => {
    Object.entries(TipoTarifa).forEach(([k, v]) => expect(v).toBe(k));
  });
});
