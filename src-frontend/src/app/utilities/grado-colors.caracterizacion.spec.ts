// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { getGradoColors, getGradoNombreParaDeporte, getGradoTextStyle } from './grado-colors';

const B = '#FFFFFF';
const A = '#FFFF00';
const N = '#FFA500';
const V = '#008000';
const Z = '#0000FF';
const R = '#FF0000';
const K = '#000000';
const GRIS = '#CCCCCC';

const SOMBRA_TEXTO = ' text-shadow: 0px 1px 2px rgba(0,0,0,0.5), 0px 0px 3px rgba(0,0,0,0.3), 0px 0px 5px rgba(0,0,0,0.2);';
const FILTRO = ' filter: drop-shadow(0px 1px 1px rgba(0,0,0,0.4)) drop-shadow(0px 0px 2px rgba(0,0,0,0.3));';

describe('grado-colors (caracterización)', () => {
  describe('getGradoColors', () => {
    // [código, colores, isSplit, needsBorder]
    const tabla: [string, string[], boolean, boolean][] = [
      ['BLANCO', [B], false, true],
      ['BLANCO_AMARILLO', [B, A], true, true],
      ['AMARILLO', [A], false, false],
      ['AMARILLO_NARANJA', [A, N], true, false],
      ['NARANJA', [N], false, false],
      ['NARANJA_VERDE', [N, V], true, false],
      ['VERDE', [V], false, false],
      ['VERDE_AZUL', [V, Z], true, false],
      ['AZUL', [Z], false, false],
      ['AZUL_ROJO', [Z, R], true, false],
      ['ROJO', [R], false, false],
      ['ROJO_NEGRO', [R, K], true, false],
      ['NEGRO', [K], false, false],
      ['ROJO_NEGRO_1_PUM', [R, K], true, false],
      ['ROJO_NEGRO_2_PUM', [R, K], true, false],
      ['ROJO_NEGRO_3_PUM', [R, K], true, false],
      ['NEGRO_1_DAN', [K], false, false],
      ['NEGRO_2_DAN', [K], false, false],
      ['NEGRO_3_DAN', [K], false, false],
      ['NEGRO_4_DAN', [K], false, false],
      ['NEGRO_5_DAN', [K], false, false],
    ];
    tabla.forEach(([codigo, colors, isSplit, needsBorder]) => {
      it(`${codigo}`, () => {
        expect(getGradoColors(codigo)).toEqual({ colors, isSplit, needsBorder });
      });
    });

    it('vacío o sin valor -> gris por defecto', () => {
      expect(getGradoColors('')).toEqual({ colors: [GRIS], isSplit: false, needsBorder: false });
      expect(getGradoColors(null as unknown as string)).toEqual({ colors: [GRIS], isSplit: false, needsBorder: false });
      expect(getGradoColors(undefined as unknown as string)).toEqual({ colors: [GRIS], isSplit: false, needsBorder: false });
    });

    it('código desconocido -> gris por defecto', () => {
      expect(getGradoColors('DESCONOCIDO')).toEqual({ colors: [GRIS], isSplit: false, needsBorder: false });
    });

    it('minúsculas y separadores alternativos (guion, barra, espacio)', () => {
      const esperado = { colors: [B, A], isSplit: true, needsBorder: true };
      expect(getGradoColors('blanco_amarillo')).toEqual(esperado);
      expect(getGradoColors('blanco-amarillo')).toEqual(esperado);
      expect(getGradoColors('blanco/amarillo')).toEqual(esperado);
      expect(getGradoColors('blanco amarillo')).toEqual(esperado);
      expect(getGradoColors('verde')).toEqual({ colors: [V], isSplit: false, needsBorder: false });
    });

    it('3 o más colores: solo los dos primeros', () => {
      expect(getGradoColors('BLANCO_AMARILLO_NARANJA')).toEqual({ colors: [B, A], isSplit: true, needsBorder: true });
      expect(getGradoColors('AZUL_ROJO_NEGRO')).toEqual({ colors: [Z, R], isSplit: true, needsBorder: false });
    });

    it('colores repetidos cuentan como dos partes', () => {
      expect(getGradoColors('ROJO_ROJO')).toEqual({ colors: [R, R], isSplit: true, needsBorder: false });
    });

    it('por prefijo / contiene (sin partes reconocidas)', () => {
      expect(getGradoColors('BLANCOS')).toEqual({ colors: [B], isSplit: false, needsBorder: true });
      expect(getGradoColors('AMARILLOX')).toEqual({ colors: [A], isSplit: false, needsBorder: false });
      expect(getGradoColors('DAN')).toEqual({ colors: [K], isSplit: false, needsBorder: false });
      expect(getGradoColors('1_DAN')).toEqual({ colors: [K], isSplit: false, needsBorder: false });
    });
  });

  describe('getGradoNombreParaDeporte', () => {
    it('KICKBOXING + ROJO -> MARRÓN (también en minúsculas)', () => {
      expect(getGradoNombreParaDeporte('ROJO', 'KICKBOXING')).toBe('MARRÓN');
      expect(getGradoNombreParaDeporte('rojo', 'KICKBOXING')).toBe('MARRÓN');
    });
    it('KICKBOXING con otros grados no cambia', () => {
      ['BLANCO', 'AZUL_ROJO', 'ROJO_NEGRO', 'NEGRO_1_DAN'].forEach(g =>
        expect(getGradoNombreParaDeporte(g, 'KICKBOXING')).toBe(g));
    });
    it('otros deportes no cambian ROJO', () => {
      expect(getGradoNombreParaDeporte('ROJO', 'TAEKWONDO')).toBe('ROJO');
      expect(getGradoNombreParaDeporte('ROJO', 'PILATES')).toBe('ROJO');
      expect(getGradoNombreParaDeporte('ROJO', null)).toBe('ROJO');
      expect(getGradoNombreParaDeporte('ROJO', undefined)).toBe('ROJO');
    });
    it('deporte en minúsculas no se normaliza', () => {
      expect(getGradoNombreParaDeporte('ROJO', 'kickboxing')).toBe('ROJO');
    });
    it('grado vacío o nulo -> cadena vacía', () => {
      expect(getGradoNombreParaDeporte('', 'KICKBOXING')).toBe('');
      expect(getGradoNombreParaDeporte(null, 'KICKBOXING')).toBe('');
      expect(getGradoNombreParaDeporte(undefined, null)).toBe('');
    });
  });

  describe('getGradoTextStyle', () => {
    const simple = (c: string) => `color: ${c}; font-weight: bold;`;
    const degradado = (arriba: string, abajo: string) =>
      `background: linear-gradient(to bottom, ${arriba} 50%, ${abajo} 50%); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; font-weight: bold;`;

    // [código, estilo exacto]
    const tabla: [string, string][] = [
      ['BLANCO', simple(B) + SOMBRA_TEXTO],
      ['AMARILLO', simple(A) + SOMBRA_TEXTO],
      ['NARANJA', simple(N)],
      ['VERDE', simple(V)],
      ['AZUL', simple(Z)],
      ['ROJO', simple(R)],
      ['NEGRO', simple(K)],
      ['NEGRO_1_DAN', simple(K)],
      ['NEGRO_5_DAN', simple(K)],
      ['DESCONOCIDO', simple(GRIS) + SOMBRA_TEXTO], // el gris por defecto cuenta como claro
      ['', simple(GRIS) + SOMBRA_TEXTO],
      ['BLANCO_AMARILLO', degradado(A, B) + FILTRO],
      ['AMARILLO_NARANJA', degradado(N, A) + FILTRO],
      ['NARANJA_VERDE', degradado(V, N)],
      ['VERDE_AZUL', degradado(Z, V)],
      ['AZUL_ROJO', degradado(R, Z)],
      ['ROJO_NEGRO', degradado(K, R)],
      ['ROJO_NEGRO_1_PUM', degradado(K, R)],
      ['ROJO_NEGRO_3_PUM', degradado(K, R)],
    ];
    tabla.forEach(([codigo, estilo]) => {
      it(`${JSON.stringify(codigo)}`, () => {
        expect(getGradoTextStyle(codigo)).toBe(estilo);
      });
    });
  });
});
