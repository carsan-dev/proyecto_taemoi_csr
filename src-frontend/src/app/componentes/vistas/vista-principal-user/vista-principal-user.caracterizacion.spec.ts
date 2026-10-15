// Caracterización de Taemoi. Si cambia, debe ser una decisión explícita.
// Los colores y las etiquetas salen del catálogo (D3: `grado.nombre`; D4: KB ROJO marrón y «MARRÓN»; D5: color1/color2).
import { VistaPrincipalUserComponent } from './vista-principal-user.component';
import { crearCatalogoServiceTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { Catalogo } from '../../../features/catalogo/catalogo.models';
import { TestBed } from '@angular/core/testing';

interface Visual {
  topColor: string;
  bottomColor: string;
  isSplit: boolean;
  stripeOffsets: number[];
  label: string;
}

/** Catálogo de Taemoi con los colores de los grados sin rellenar (BD sin V30): paleta de respaldo de la vista. */
const CATALOGO_TAEMOI_SIN_COLORES: Catalogo = {
  ...CATALOGO_TAEMOI,
  sistemasGrados: CATALOGO_TAEMOI.sistemasGrados.map((s) => ({
    ...s,
    grados: s.grados.map((g) => ({ ...g, color1: null, color2: null })),
  })),
};

/** Instancia sin constructor ni TestBed de componente: solo se usan métodos que dependen de estos campos. */
function crear(catalogo: Catalogo = CATALOGO_TAEMOI) {
  const c = Object.create(VistaPrincipalUserComponent.prototype);
  c.beltVisualCache = new Map();
  c.beltCacheCatalogo = null;
  c.beltWidthPx = 84;
  TestBed.resetTestingModule();
  c.catalogo = TestBed.runInInjectionContext(() => crearCatalogoServiceTesting(catalogo));
  return c as {
    getBeltLabel(g: string, d: string | null | undefined): string;
    getBeltVisual(g: string | null | undefined, d: string | null | undefined): Visual;
  };
}

describe('vista-principal-user cinturones (caracterización)', () => {
  describe('getBeltLabel', () => {
    // Antes: «BLANCO/AMARILLO», «ROJO/NEGRO 1 PUM», «NEGRO 1 DAN», «MARRON». Ahora: grado.nombre (D3) y D4.
    const tabla: [string, string | null, string][] = [
      ['BLANCO', 'TAEKWONDO', 'BLANCO'],
      ['BLANCO_AMARILLO', 'TAEKWONDO', 'BLANCO-AMARILLO'], // D3
      ['AMARILLO_NARANJA', 'TAEKWONDO', 'AMARILLO-NARANJA'], // D3
      ['AZUL_ROJO', 'TAEKWONDO', 'AZUL-ROJO'], // D3
      ['AZUL_ROJO', 'KICKBOXING', 'AZUL-ROJO'], // D3 (el nombre de KB no cambia para este grado)
      ['ROJO', 'TAEKWONDO', 'ROJO'],
      ['ROJO', 'KICKBOXING', 'MARRÓN'], // D3, D4 (antes «MARRON»)
      ['ROJO_NEGRO_1_PUM', 'TAEKWONDO', 'ROJO-NEGRO 1º PUM'], // D3
      ['ROJO_NEGRO_3_PUM', 'KICKBOXING', 'ROJO-NEGRO 3º PUM'], // D3
      ['NEGRO_1_DAN', 'TAEKWONDO', 'NEGRO 1º DAN'], // D3
      ['NEGRO_5_DAN', 'KICKBOXING', 'NEGRO 5º DAN'], // D3
      ['', 'TAEKWONDO', ''],
      ['DESCONOCIDO', 'TAEKWONDO', 'DESCONOCIDO'],
      // Sin disciplina no hay sistema de grados donde buscar: el propio código.
      ['negro_2_dan', null, 'NEGRO_2_DAN'],
    ];
    tabla.forEach(([grado, deporte, etiqueta]) => {
      it(`${JSON.stringify(grado)} (${deporte}) -> ${JSON.stringify(etiqueta)}`, () => {
        expect(crear().getBeltLabel(grado, deporte)).toBe(etiqueta);
      });
    });
  });

  describe('getBeltVisual', () => {
    const visual = (topColor: string, bottomColor: string, isSplit: boolean, stripeOffsets: number[], label: string): Visual =>
      ({ topColor, bottomColor, isSplit, stripeOffsets, label });

    // Con beltWidthPx=84: ancho de franja 5 + hueco 1, margen inicial 3 -> offsets 3, 9, 15, 21, 27
    const tabla: [string, string | null, string | null, Visual][] = [
      ['vacío', 'TAEKWONDO', '', visual('#CCCCCC', '#CCCCCC', false, [], 'SIN GRADO')],
      ['null', null, null, visual('#CCCCCC', '#CCCCCC', false, [], 'SIN GRADO')],
      ['BLANCO', 'TAEKWONDO', 'BLANCO', visual('#FFFFFF', '#FFFFFF', false, [], 'BLANCO')],
      ['AMARILLO_NARANJA', 'TAEKWONDO', 'AMARILLO_NARANJA', visual('#FFA500', '#FFFF00', true, [], 'AMARILLO-NARANJA')],
      ['AZUL_ROJO TKD', 'TAEKWONDO', 'AZUL_ROJO', visual('#FF0000', '#0000FF', true, [], 'AZUL-ROJO')],
      ['ROJO TKD', 'TAEKWONDO', 'ROJO', visual('#FF0000', '#FF0000', false, [], 'ROJO')],
      ['ROJO KB', 'KICKBOXING', 'ROJO', visual('#8B4513', '#8B4513', false, [], 'MARRÓN')],
      ['ROJO_NEGRO_1_PUM', 'TAEKWONDO', 'ROJO_NEGRO_1_PUM', visual('#000000', '#FF0000', true, [3], 'ROJO-NEGRO 1º PUM')],
      ['NEGRO_1_DAN', 'TAEKWONDO', 'NEGRO_1_DAN', visual('#000000', '#000000', false, [3], 'NEGRO 1º DAN')],
      ['NEGRO_5_DAN', 'KICKBOXING', 'NEGRO_5_DAN', visual('#000000', '#000000', false, [3, 9, 15, 21, 27], 'NEGRO 5º DAN')],
      ['minúsculas', 'kickboxing', 'negro_2_dan', visual('#000000', '#000000', false, [3, 9], 'NEGRO 2º DAN')],
    ];
    tabla.forEach(([desc, deporte, grado, esperado]) => {
      it(desc, () => {
        expect(crear().getBeltVisual(grado, deporte)).toEqual(esperado);
      });
    });

    it('AZUL_ROJO en KB: color2 del catálogo (rojo del propio grado; solo el grado ROJO es marrón en KB, D4)', () => {
      expect(crear().getBeltVisual('AZUL_ROJO', 'KICKBOXING')).toEqual(
        visual('#FF0000', '#0000FF', true, [], 'AZUL-ROJO')
      );
    });

    // Antes el rojo de las franjas ROJO_NEGRO era marrón por código en KB; el catálogo (V30) solo hace marrón al grado ROJO.
    it('ROJO_NEGRO_3_PUM en KB: franjas del código y colores del catálogo', () => {
      expect(crear().getBeltVisual('ROJO_NEGRO_3_PUM', 'KICKBOXING')).toEqual(
        visual('#000000', '#FF0000', true, [3, 9, 15], 'ROJO-NEGRO 3º PUM')
      );
    });

    it('NEGRO sin sistema de grados que lo defina: negro por la paleta de respaldo (en el catálogo no existe «NEGRO»)', () => {
      expect(crear().getBeltVisual('NEGRO', 'TAEKWONDO')).toEqual(visual('#000000', '#000000', false, [], 'NEGRO'));
    });

    it('desconocido: paleta de respaldo por nombre (gris sin color en el código) y el propio código como etiqueta', () => {
      expect(crear().getBeltVisual('DESCONOCIDO', 'TAEKWONDO')).toEqual(
        visual('#CCCCCC', '#CCCCCC', false, [], 'DESCONOCIDO')
      );
    });

    it('cachea por deporte|grado y devuelve la misma referencia', () => {
      const fresco = crear();
      const a = fresco.getBeltVisual('AZUL', 'TAEKWONDO');
      expect(fresco.getBeltVisual('AZUL', 'TAEKWONDO')).toBe(a);
      expect(fresco.getBeltVisual('azul', 'taekwondo')).toBe(a);
    });

    describe('catálogo sin colores (BD sin V30): paleta de respaldo de la vista', () => {
      it('conserva la paleta por nombre de color (ROJO rojo también en KB, sin marrón por código) y los nombres', () => {
        const c = crear(CATALOGO_TAEMOI_SIN_COLORES);
        expect(c.getBeltVisual('AMARILLO_NARANJA', 'TAEKWONDO')).toEqual(
          visual('#FFA500', '#FFFF00', true, [], 'AMARILLO-NARANJA')
        );
        expect(c.getBeltVisual('ROJO', 'KICKBOXING')).toEqual(visual('#FF0000', '#FF0000', false, [], 'MARRÓN'));
        expect(c.getBeltVisual('ROJO_NEGRO_2_PUM', 'TAEKWONDO')).toEqual(
          visual('#000000', '#FF0000', true, [3, 9], 'ROJO-NEGRO 2º PUM')
        );
      });
    });
  });
});
