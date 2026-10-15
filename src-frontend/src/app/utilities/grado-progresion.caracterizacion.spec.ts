// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { esSiguienteGradoRojo, obtenerSiguienteGrado } from './grado-progresion';
import { fijarCatalogoActual, restablecerCatalogoActual } from '../features/catalogo/testing/catalogo-actual.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../features/catalogo/testing/catalogo.fixtures';

const HOY = new Date(2026, 5, 15, 12, 0, 0);
const MENOR = '2018-01-01T12:00:00'; // 8 años
const ADULTO = '1990-01-01T12:00:00';

const TKD_MENORES: Record<string, string> = {
  BLANCO: 'BLANCO_AMARILLO',
  BLANCO_AMARILLO: 'AMARILLO',
  AMARILLO: 'AMARILLO_NARANJA',
  AMARILLO_NARANJA: 'NARANJA',
  NARANJA: 'NARANJA_VERDE',
  NARANJA_VERDE: 'VERDE',
  VERDE: 'VERDE_AZUL',
  VERDE_AZUL: 'AZUL',
  AZUL: 'AZUL_ROJO',
  AZUL_ROJO: 'ROJO',
  ROJO: 'ROJO_NEGRO_1_PUM',
  ROJO_NEGRO_1_PUM: 'ROJO_NEGRO_2_PUM',
  ROJO_NEGRO_2_PUM: 'ROJO_NEGRO_3_PUM',
};

const MAYORES: Record<string, string> = {
  BLANCO: 'AMARILLO',
  BLANCO_AMARILLO: 'AMARILLO',
  AMARILLO: 'NARANJA',
  AMARILLO_NARANJA: 'NARANJA',
  NARANJA: 'VERDE',
  NARANJA_VERDE: 'VERDE',
  VERDE: 'AZUL',
  VERDE_AZUL: 'AZUL',
  AZUL: 'ROJO',
  AZUL_ROJO: 'ROJO',
  ROJO: 'NEGRO_1_DAN',
  NEGRO_1_DAN: 'NEGRO_2_DAN',
  NEGRO_2_DAN: 'NEGRO_3_DAN',
  NEGRO_3_DAN: 'NEGRO_4_DAN',
  NEGRO_4_DAN: 'NEGRO_5_DAN',
};

const TODOS = [
  'BLANCO', 'BLANCO_AMARILLO', 'AMARILLO', 'AMARILLO_NARANJA', 'NARANJA', 'NARANJA_VERDE', 'VERDE',
  'VERDE_AZUL', 'AZUL', 'AZUL_ROJO', 'ROJO', 'ROJO_NEGRO', 'NEGRO', 'ROJO_NEGRO_1_PUM',
  'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM', 'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN',
  'NEGRO_5_DAN', 'DESCONOCIDO',
];

describe('grado-progresion (caracterización)', () => {
  // Las utilidades leen el catálogo cargado: equivalencia con el fixture de Taemoi (rebanada 3).
  beforeEach(() => {
    fijarCatalogoActual(CATALOGO_TAEMOI);
    jasmine.clock().install();
    jasmine.clock().mockDate(HOY);
  });
  afterEach(() => {
    jasmine.clock().uninstall();
    restablecerCatalogoActual();
  });

  describe('catálogo de otra escuela y sin catálogo', () => {
    it('segunda escuela: progresión por orden del sistema de grados; sin aviso de rojo bordado (D11)', () => {
      fijarCatalogoActual(CATALOGO_SEGUNDA_ESCUELA);
      expect(obtenerSiguienteGrado('JUDO', 'BLANCO_JUDO', ADULTO)).toBe('MARRON_JUDO');
      expect(obtenerSiguienteGrado('JUDO', 'MARRON_JUDO', ADULTO)).toBeNull();
      expect(obtenerSiguienteGrado('YOGA', 'BLANCO_JUDO', ADULTO)).toBeNull();
      expect(obtenerSiguienteGrado('TAEKWONDO', 'BLANCO', ADULTO)).toBeNull();
      expect(esSiguienteGradoRojo('JUDO', 'BLANCO_JUDO', ADULTO)).toBeFalse();
    });

    it('D11: el aviso solo existe con legado de Taemoi', () => {
      const sinLegado = { ...CATALOGO_TAEMOI, organizacion: { id: 3, comportamientoLegadoTaemoi: false } };
      fijarCatalogoActual(sinLegado);
      expect(obtenerSiguienteGrado('TAEKWONDO', 'AZUL', ADULTO)).toBe('ROJO');
      expect(esSiguienteGradoRojo('TAEKWONDO', 'AZUL', ADULTO)).toBeFalse();
    });

    it('catálogo no cargado -> null / false (D15)', () => {
      restablecerCatalogoActual();
      expect(obtenerSiguienteGrado('TAEKWONDO', 'BLANCO', MENOR)).toBeNull();
      expect(esSiguienteGradoRojo('TAEKWONDO', 'AZUL', ADULTO)).toBeFalse();
    });
  });

  describe('TAEKWONDO menores', () => {
    TODOS.forEach(g => {
      it(`${g} -> ${TKD_MENORES[g] ?? null}`, () => {
        expect(obtenerSiguienteGrado('TAEKWONDO', g, MENOR)).toBe(TKD_MENORES[g] ?? null);
      });
    });
    it('sin fecha de nacimiento se trata como menor', () => {
      expect(obtenerSiguienteGrado('TAEKWONDO', 'BLANCO', null)).toBe('BLANCO_AMARILLO');
      expect(obtenerSiguienteGrado('TAEKWONDO', 'BLANCO', undefined)).toBe('BLANCO_AMARILLO');
    });
    it('último grado (ROJO_NEGRO_3_PUM) -> null', () => {
      expect(obtenerSiguienteGrado('TAEKWONDO', 'ROJO_NEGRO_3_PUM', MENOR)).toBeNull();
    });
  });

  describe('TAEKWONDO mayores', () => {
    TODOS.forEach(g => {
      it(`${g} -> ${MAYORES[g] ?? null}`, () => {
        expect(obtenerSiguienteGrado('TAEKWONDO', g, ADULTO)).toBe(MAYORES[g] ?? null);
      });
    });
    it('último grado (NEGRO_5_DAN) -> null', () => {
      expect(obtenerSiguienteGrado('TAEKWONDO', 'NEGRO_5_DAN', ADULTO)).toBeNull();
    });
  });

  describe('KICKBOXING (misma tabla que TKD mayores, sin depender de la edad)', () => {
    TODOS.forEach(g => {
      it(`${g} -> ${MAYORES[g] ?? null}`, () => {
        expect(obtenerSiguienteGrado('KICKBOXING', g, ADULTO)).toBe(MAYORES[g] ?? null);
        expect(obtenerSiguienteGrado('KICKBOXING', g, MENOR)).toBe(MAYORES[g] ?? null);
        expect(obtenerSiguienteGrado('KICKBOXING', g, null)).toBe(MAYORES[g] ?? null);
      });
    });
    it('deporte en minúsculas se normaliza', () => {
      expect(obtenerSiguienteGrado('kickboxing', 'ROJO', ADULTO)).toBe('NEGRO_1_DAN');
      expect(obtenerSiguienteGrado('taekwondo', 'BLANCO', MENOR)).toBe('BLANCO_AMARILLO');
    });
  });

  describe('casos límite', () => {
    it('deporte desconocido o vacío -> null', () => {
      ['PILATES', 'DEFENSA_PERSONAL_FEMENINA', 'OTRO', '', null, undefined].forEach(d =>
        expect(obtenerSiguienteGrado(d, 'BLANCO', ADULTO)).toBeNull());
    });
    it('grado vacío o nulo -> null', () => {
      ['', null, undefined].forEach(g => expect(obtenerSiguienteGrado('TAEKWONDO', g, ADULTO)).toBeNull());
    });
    it('el grado es sensible a mayúsculas', () => {
      expect(obtenerSiguienteGrado('TAEKWONDO', 'blanco', ADULTO)).toBeNull();
    });
  });

  describe('esSiguienteGradoRojo', () => {
    it('TKD menor: AZUL_ROJO -> ROJO es true', () => {
      expect(esSiguienteGradoRojo('TAEKWONDO', 'AZUL_ROJO', MENOR)).toBeTrue();
    });
    it('TKD mayor: AZUL y AZUL_ROJO -> ROJO es true', () => {
      expect(esSiguienteGradoRojo('TAEKWONDO', 'AZUL', ADULTO)).toBeTrue();
      expect(esSiguienteGradoRojo('TAEKWONDO', 'AZUL_ROJO', ADULTO)).toBeTrue();
    });
    it('TKD menor: AZUL -> AZUL_ROJO es false', () => {
      expect(esSiguienteGradoRojo('TAEKWONDO', 'AZUL', MENOR)).toBeFalse();
    });
    it('KICKBOXING: AZUL y AZUL_ROJO es true', () => {
      expect(esSiguienteGradoRojo('KICKBOXING', 'AZUL', ADULTO)).toBeTrue();
      expect(esSiguienteGradoRojo('KICKBOXING', 'AZUL_ROJO', MENOR)).toBeTrue();
    });
    it('resto es false', () => {
      expect(esSiguienteGradoRojo('TAEKWONDO', 'BLANCO', ADULTO)).toBeFalse();
      expect(esSiguienteGradoRojo('PILATES', 'AZUL', ADULTO)).toBeFalse();
      expect(esSiguienteGradoRojo(null, null, null)).toBeFalse();
      expect(esSiguienteGradoRojo('TAEKWONDO', 'ROJO', ADULTO)).toBeFalse();
    });
  });
});
