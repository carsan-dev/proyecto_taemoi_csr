// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { Deporte, DeporteLabels, DeportesArray, getDeporteLabel, isValidDeporte } from './deporte';

describe('Deporte (caracterización)', () => {
  it('valores del enum', () => {
    expect(Object.values(Deporte) as string[]).toEqual(['TAEKWONDO', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA']);
  });

  it('DeporteLabels exactos', () => {
    expect({ ...DeporteLabels }).toEqual({
      TAEKWONDO: 'Taekwondo',
      KICKBOXING: 'Kickboxing',
      PILATES: 'Pilates',
      DEFENSA_PERSONAL_FEMENINA: 'Defensa Personal Femenina',
    });
  });

  it('DeportesArray con orden exacto', () => {
    expect(DeportesArray).toEqual([Deporte.TAEKWONDO, Deporte.KICKBOXING, Deporte.PILATES, Deporte.DEFENSA_PERSONAL_FEMENINA]);
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

  const validos: [string, boolean][] = [
    ['TAEKWONDO', true],
    ['KICKBOXING', true],
    ['PILATES', true],
    ['DEFENSA_PERSONAL_FEMENINA', true],
    ['taekwondo', false],
    ['OTRO', false],
    ['', false],
  ];
  validos.forEach(([valor, esperado]) => {
    it(`isValidDeporte(${JSON.stringify(valor)}) -> ${esperado}`, () => {
      expect(isValidDeporte(valor)).toBe(esperado);
    });
  });
});
