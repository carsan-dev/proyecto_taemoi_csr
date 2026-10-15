// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { getGradoColors, getGradoNombreParaDeporte, getGradoTextStyle } from './grado-colors';
import { fijarCatalogoActual, restablecerCatalogoActual } from '../features/catalogo/testing/catalogo-actual.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../features/catalogo/testing/catalogo.fixtures';

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
  // Las utilidades leen el catálogo cargado: equivalencia con el fixture de Taemoi (rebanada 3).
  beforeEach(() => fijarCatalogoActual(CATALOGO_TAEMOI));
  afterEach(() => restablecerCatalogoActual());

  describe('con el catálogo (deporte indicado)', () => {
    it('Taemoi TKD: mismos colores y estilos que la paleta por nombre, para todos los grados', () => {
      CATALOGO_TAEMOI.sistemasGrados[0].grados.forEach(g => {
        expect(getGradoColors(g.codigo, 'TAEKWONDO')).toEqual(getGradoColors(g.codigo), g.codigo);
        expect(getGradoTextStyle(g.codigo, 'TAEKWONDO')).toBe(getGradoTextStyle(g.codigo), g.codigo);
      });
    });

    it('D4: KICKBOXING ROJO es marrón #8B4513 (antes rojo); el resto de KB igual que la paleta', () => {
      expect(getGradoColors('ROJO', 'KICKBOXING')).toEqual({ colors: ['#8B4513'], isSplit: false, needsBorder: false });
      expect(getGradoColors('ROJO', 'TAEKWONDO').colors).toEqual([R]);
      expect(getGradoColors('ROJO').colors).toEqual([R]); // sin deporte: paleta por nombre
      expect(getGradoTextStyle('ROJO', 'KICKBOXING')).toContain('color: #8B4513;');
      CATALOGO_TAEMOI.sistemasGrados[1].grados.filter(g => g.codigo !== 'ROJO').forEach(g =>
        expect(getGradoColors(g.codigo, 'KICKBOXING')).toEqual(getGradoColors(g.codigo), g.codigo));
    });

    it('grado desconocido, deporte desconocido o color1 nulo -> paleta por nombre', () => {
      expect(getGradoColors('ROJO_NEGRO', 'TAEKWONDO')).toEqual({ colors: [R, K], isSplit: true, needsBorder: false });
      expect(getGradoColors('AZUL', 'OTRO')).toEqual({ colors: [Z], isSplit: false, needsBorder: false });
      const sinColor = {
        ...CATALOGO_TAEMOI,
        sistemasGrados: CATALOGO_TAEMOI.sistemasGrados.map(s => ({ ...s, grados: s.grados.map(g => ({ ...g, color1: null, color2: null })) })),
      };
      fijarCatalogoActual(sinColor);
      expect(getGradoColors('ROJO', 'KICKBOXING').colors).toEqual([R]);
    });

    it('segunda escuela: colores y nombres de su catálogo', () => {
      fijarCatalogoActual(CATALOGO_SEGUNDA_ESCUELA);
      expect(getGradoColors('MARRON_JUDO', 'JUDO')).toEqual({ colors: ['#8B4513', '#FAFAFA'], isSplit: true, needsBorder: false });
      expect(getGradoNombreParaDeporte('MARRON_JUDO', 'JUDO')).toBe('Marrón judo');
      expect(getGradoNombreParaDeporte('ROJO', 'KICKBOXING')).toBe('ROJO'); // no existe en su catálogo
    });

    it('catálogo no cargado: nombre = código, colores = paleta por nombre (D15)', () => {
      restablecerCatalogoActual();
      expect(getGradoNombreParaDeporte('ROJO', 'KICKBOXING')).toBe('ROJO');
      expect(getGradoColors('ROJO', 'KICKBOXING').colors).toEqual([R]);
    });
  });

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
    it('KICKBOXING con otros grados: nombre del catálogo (D3); desconocidos, el código', () => {
      // D3: antes devolvía el código en bruto (AZUL_ROJO, NEGRO_1_DAN); ahora `grado.nombre`.
      expect(getGradoNombreParaDeporte('BLANCO', 'KICKBOXING')).toBe('BLANCO');
      expect(getGradoNombreParaDeporte('AZUL_ROJO', 'KICKBOXING')).toBe('AZUL-ROJO');
      expect(getGradoNombreParaDeporte('NEGRO_1_DAN', 'KICKBOXING')).toBe('NEGRO 1º DAN');
      expect(getGradoNombreParaDeporte('ROJO_NEGRO', 'KICKBOXING')).toBe('ROJO_NEGRO'); // no existe en el catálogo
    });
    it('D3: TAEKWONDO usa el nombre del catálogo (antes el código en bruto)', () => {
      expect(getGradoNombreParaDeporte('BLANCO_AMARILLO', 'TAEKWONDO')).toBe('BLANCO-AMARILLO');
      expect(getGradoNombreParaDeporte('ROJO_NEGRO_1_PUM', 'TAEKWONDO')).toBe('ROJO-NEGRO 1º PUM');
      expect(getGradoNombreParaDeporte('NEGRO_5_DAN', 'TAEKWONDO')).toBe('NEGRO 5º DAN');
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
