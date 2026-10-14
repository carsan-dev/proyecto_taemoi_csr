// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { VistaPrincipalUserComponent } from './vista-principal-user.component';

interface Visual {
  topColor: string;
  bottomColor: string;
  isSplit: boolean;
  stripeOffsets: number[];
  label: string;
}

/** Instancia sin constructor ni TestBed: solo se usan métodos puros que dependen de estos dos campos. */
function crear() {
  const c = Object.create(VistaPrincipalUserComponent.prototype);
  c.beltVisualCache = new Map();
  c.beltWidthPx = 84;
  return c as {
    getBeltColorByName(n: string, kb: boolean): string;
    getBeltLabel(g: string, d: string | null | undefined): string;
    getBeltVisual(g: string | null | undefined, d: string | null | undefined): Visual;
  };
}

describe('vista-principal-user cinturones (caracterización)', () => {
  const c = crear();

  describe('getBeltColorByName', () => {
    const tabla: [string, boolean, string][] = [
      ['BLANCO', false, '#FFFFFF'],
      ['AMARILLO', false, '#FFFF00'],
      ['NARANJA', false, '#FFA500'],
      ['VERDE', false, '#008000'],
      ['AZUL', false, '#0000FF'],
      ['ROJO', false, '#FF0000'],
      ['NEGRO', false, '#000000'],
      ['BLANCO', true, '#FFFFFF'],
      ['AMARILLO', true, '#FFFF00'],
      ['NARANJA', true, '#FFA500'],
      ['VERDE', true, '#008000'],
      ['AZUL', true, '#0000FF'],
      ['ROJO', true, '#8B4513'],
      ['NEGRO', true, '#000000'],
      ['rojo', true, '#8B4513'],
      [' rojo ', false, '#FF0000'],
      ['MARRON', false, '#CCCCCC'],
      ['XYZ', false, '#CCCCCC'],
      ['', false, '#CCCCCC'],
    ];
    tabla.forEach(([nombre, kb, color]) => {
      it(`${JSON.stringify(nombre)} kickboxing=${kb} -> ${color}`, () => {
        expect(c.getBeltColorByName(nombre, kb)).toBe(color);
      });
    });
  });

  describe('getBeltLabel', () => {
    const tabla: [string, string | null, string][] = [
      ['BLANCO', 'TAEKWONDO', 'BLANCO'],
      ['BLANCO_AMARILLO', 'TAEKWONDO', 'BLANCO/AMARILLO'],
      ['AMARILLO_NARANJA', 'TAEKWONDO', 'AMARILLO/NARANJA'],
      ['AZUL_ROJO', 'TAEKWONDO', 'AZUL/ROJO'],
      ['AZUL_ROJO', 'KICKBOXING', 'AZUL/MARRON'],
      ['ROJO', 'TAEKWONDO', 'ROJO'],
      ['ROJO', 'KICKBOXING', 'MARRON'],
      ['ROJO_NEGRO', 'TAEKWONDO', 'ROJO/NEGRO'],
      ['ROJO_NEGRO', 'KICKBOXING', 'MARRON/NEGRO'],
      ['ROJO_NEGRO_1_PUM', 'TAEKWONDO', 'ROJO/NEGRO 1 PUM'],
      ['ROJO_NEGRO_3_PUM', 'KICKBOXING', 'MARRON/NEGRO 3 PUM'],
      ['NEGRO_1_DAN', 'TAEKWONDO', 'NEGRO 1 DAN'],
      ['NEGRO_5_DAN', 'KICKBOXING', 'NEGRO 5 DAN'],
      ['NEGRO', 'TAEKWONDO', 'NEGRO'],
      ['negro_2_dan', null, 'NEGRO 2 DAN'],
      ['', 'TAEKWONDO', ''],
      ['DESCONOCIDO', 'TAEKWONDO', 'DESCONOCIDO'],
    ];
    tabla.forEach(([grado, deporte, etiqueta]) => {
      it(`${JSON.stringify(grado)} (${deporte}) -> ${JSON.stringify(etiqueta)}`, () => {
        expect(c.getBeltLabel(grado, deporte)).toBe(etiqueta);
      });
    });
  });

  describe('getBeltVisual', () => {
    const visual = (topColor: string, bottomColor: string, isSplit: boolean, stripeOffsets: number[], label: string): Visual =>
      ({ topColor, bottomColor, isSplit, stripeOffsets, label });

    // Con beltWidthPx=84: ancho de franja 5 + hueco 1, margen inicial 3 -> offsets 3, 9, 15, 21, 27
    const tabla: [string, string, string | null, string | null, Visual][] = [
      ['vacío', '', 'TAEKWONDO', '', visual('#CCCCCC', '#CCCCCC', false, [], 'SIN GRADO')],
      ['null', 'null', null, null, visual('#CCCCCC', '#CCCCCC', false, [], 'SIN GRADO')],
      ['BLANCO', 'BLANCO', 'TAEKWONDO', 'BLANCO', visual('#FFFFFF', '#FFFFFF', false, [], 'BLANCO')],
      ['AMARILLO_NARANJA', 'AMARILLO_NARANJA', 'TAEKWONDO', 'AMARILLO_NARANJA', visual('#FFA500', '#FFFF00', true, [], 'AMARILLO/NARANJA')],
      ['AZUL_ROJO TKD', 'AZUL_ROJO', 'TAEKWONDO', 'AZUL_ROJO', visual('#FF0000', '#0000FF', true, [], 'AZUL/ROJO')],
      ['AZUL_ROJO KB', 'AZUL_ROJO', 'KICKBOXING', 'AZUL_ROJO', visual('#8B4513', '#0000FF', true, [], 'AZUL/MARRON')],
      ['ROJO TKD', 'ROJO', 'TAEKWONDO', 'ROJO', visual('#FF0000', '#FF0000', false, [], 'ROJO')],
      ['ROJO KB', 'ROJO', 'KICKBOXING', 'ROJO', visual('#8B4513', '#8B4513', false, [], 'MARRON')],
      ['ROJO_NEGRO (sin PUM)', 'ROJO_NEGRO', 'TAEKWONDO', 'ROJO_NEGRO', visual('#000000', '#FF0000', true, [], 'ROJO/NEGRO')],
      ['ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_1_PUM', 'TAEKWONDO', 'ROJO_NEGRO_1_PUM', visual('#000000', '#FF0000', true, [3], 'ROJO/NEGRO 1 PUM')],
      ['ROJO_NEGRO_3_PUM KB', 'ROJO_NEGRO_3_PUM', 'KICKBOXING', 'ROJO_NEGRO_3_PUM', visual('#000000', '#8B4513', true, [3, 9, 15], 'MARRON/NEGRO 3 PUM')],
      ['NEGRO', 'NEGRO', 'TAEKWONDO', 'NEGRO', visual('#000000', '#000000', false, [], 'NEGRO')],
      ['NEGRO_1_DAN', 'NEGRO_1_DAN', 'TAEKWONDO', 'NEGRO_1_DAN', visual('#000000', '#000000', false, [3], 'NEGRO 1 DAN')],
      ['NEGRO_5_DAN', 'NEGRO_5_DAN', 'KICKBOXING', 'NEGRO_5_DAN', visual('#000000', '#000000', false, [3, 9, 15, 21, 27], 'NEGRO 5 DAN')],
      ['desconocido', 'DESCONOCIDO', 'TAEKWONDO', 'DESCONOCIDO', visual('#CCCCCC', '#CCCCCC', false, [], 'DESCONOCIDO')],
      ['minúsculas', 'negro_2_dan', 'kickboxing', 'negro_2_dan', visual('#000000', '#000000', false, [3, 9], 'NEGRO 2 DAN')],
    ];
    tabla.forEach(([desc, , deporte, grado, esperado]) => {
      it(desc, () => {
        const fresco = crear();
        expect(fresco.getBeltVisual(grado, deporte)).toEqual(esperado);
      });
    });

    it('cachea por deporte|grado y devuelve la misma referencia', () => {
      const fresco = crear();
      const a = fresco.getBeltVisual('AZUL', 'TAEKWONDO');
      expect(fresco.getBeltVisual('AZUL', 'TAEKWONDO')).toBe(a);
      expect(fresco.getBeltVisual('azul', 'taekwondo')).toBe(a);
    });
  });
});
