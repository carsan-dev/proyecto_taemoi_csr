// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { TARIFAS_ESTANDAR, obtenerCuantiaTarifaEstandar } from './tarifa.constants';
import { fijarCatalogoActual, restablecerCatalogoActual } from '../features/catalogo/testing/catalogo-actual.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../features/catalogo/testing/catalogo.fixtures';

describe('tarifa.constants (caracterización)', () => {
  // Las utilidades leen el catálogo cargado: equivalencia con el fixture de Taemoi (rebanada 3).
  beforeEach(() => fijarCatalogoActual(CATALOGO_TAEMOI));
  afterEach(() => restablecerCatalogoActual());

  it('segunda escuela: importes de su catálogo; tarifas de Taemoi desconocidas -> 0', () => {
    fijarCatalogoActual(CATALOGO_SEGUNDA_ESCUELA);
    expect(obtenerCuantiaTarifaEstandar('BONO')).toBe(10);
    expect(obtenerCuantiaTarifaEstandar('JUDO_MES')).toBe(35);
    expect(obtenerCuantiaTarifaEstandar('JUDO_MES', 'PADRE')).toBe(40);
    expect(obtenerCuantiaTarifaEstandar('JUDO_MES', 'HIJA')).toBe(30);
    expect(obtenerCuantiaTarifaEstandar('ADULTO')).toBe(0);
  });

  it('catálogo no cargado o sin tarifas (no gestor) -> 0, nunca TARIFAS_ESTANDAR (D15)', () => {
    restablecerCatalogoActual();
    expect(obtenerCuantiaTarifaEstandar('ADULTO')).toBe(0);
    fijarCatalogoActual({ ...CATALOGO_TAEMOI, tarifas: [] });
    expect(obtenerCuantiaTarifaEstandar('PADRES_HIJOS', 'PADRE')).toBe(0);
  });

  it('TARIFAS_ESTANDAR exacto', () => {
    expect({ ...TARIFAS_ESTANDAR }).toEqual({
      FAMILIAR: 0,
      PADRES_HIJOS: 0,
      ADULTO_GRUPO: 20,
      INFANTIL_GRUPO: 20,
      HERMANOS: 26,
      INFANTIL: 28,
      ADULTO: 30,
      KICKBOXING: 30,
      PILATES: 30,
      DEFENSA_PERSONAL_FEMENINA: 25,
    });
  });

  const roles: (string | undefined)[] = ['PADRE', 'MADRE', 'HIJO', 'HIJA', undefined, 'NINGUNO', 'OTRO'];
  const base: Record<string, number> = {
    FAMILIAR: 0, ADULTO_GRUPO: 20, INFANTIL_GRUPO: 20, HERMANOS: 26, INFANTIL: 28,
    ADULTO: 30, KICKBOXING: 30, PILATES: 30, DEFENSA_PERSONAL_FEMENINA: 25,
  };

  Object.keys(base).forEach(tarifa => {
    roles.forEach(rol => {
      it(`${tarifa} con rol ${rol} -> ${base[tarifa]} (el rol no influye)`, () => {
        expect(obtenerCuantiaTarifaEstandar(tarifa, rol)).toBe(base[tarifa]);
      });
    });
  });

  const padresHijos: [string | undefined, number][] = [
    ['PADRE', 28], ['MADRE', 28], ['HIJO', 26], ['HIJA', 26],
    [undefined, 0], ['', 0], ['NINGUNO', 0], ['OTRO', 0],
  ];
  padresHijos.forEach(([rol, esperado]) => {
    it(`PADRES_HIJOS con rol ${JSON.stringify(rol)} -> ${esperado}`, () => {
      expect(obtenerCuantiaTarifaEstandar('PADRES_HIJOS', rol)).toBe(esperado);
    });
  });

  it('tarifa desconocida -> 0 (con y sin rol)', () => {
    expect(obtenerCuantiaTarifaEstandar('INEXISTENTE')).toBe(0);
    expect(obtenerCuantiaTarifaEstandar('INEXISTENTE', 'PADRE')).toBe(0);
    expect(obtenerCuantiaTarifaEstandar('')).toBe(0);
  });
});
