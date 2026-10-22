import { ACENTO_POR_DEFECTO, BORDE_CONTROL, FONDO, acentoDe, contraste, desdeOklch, oklch } from './acento-escuela';

/** Tema de una escuela sin tono: el acento devuelve su tinta y su suave tal cual. */
const GRIS = { primario: '#808080', primarioTinta: '#767676', primarioSuave: '#F2F2F2' };

/** Solo el primario cuenta cuando tiene tono: tinta y suave son las de la regla de hoy y no se usan. */
function tema(primario: string) {
  return { primario, primarioTinta: '#000000', primarioSuave: '#FFFFFF' };
}

describe('acentoDe (8b-C, decisión 273)', () => {
  const casos: [string, string, string, string, string][] = [
    ['Moiskimdo', '#1B2B2E', '#01707F', '#D5F5FC', '#4595A3'],
    ['Sierra Sur de prueba', '#3E9B4F', '#0E782D', '#E0F5E1', '#67956B'],
    ['tema por defecto (decisión 257)', '#1A2230', '#466396', '#E6EFFF', '#6F8ABB'],
    ['amarillo', '#F5D90A', '#726401', '#F4F0D5', '#958B50'],
    ['rojo', '#D7263D', '#BB012B', '#FFE8E7', '#B77876'],
    ['azul claro', '#8EC5FF', '#32669A', '#E3F0FF', '#648DB8'],
  ];

  for (const [nombre, primario, acento, suave, borde] of casos) {
    it(`${nombre} ${primario}: acento ${acento}, suave ${suave} y borde ${borde}`, () => {
      expect(acentoDe(tema(primario))).toEqual({ acento, acentoSuave: suave, acentoBorde: borde });
    });
  }

  it('un gris puro (croma < 0,01) se queda con los neutros de hoy', () => {
    expect(acentoDe(GRIS)).toEqual({ acento: '#767676', acentoSuave: '#F2F2F2', acentoBorde: BORDE_CONTROL });
  });

  it('el acento por defecto es el de #1A2230, el de panel-nuevo.scss', () => {
    expect(ACENTO_POR_DEFECTO).toEqual({ acento: '#466396', acentoSuave: '#E6EFFF', acentoBorde: '#6F8ABB' });
  });

  it('conserva el tono del color de la escuela', () => {
    for (const [, primario] of casos) {
      const tono = oklch(primario)[2];
      const { acento, acentoSuave, acentoBorde } = acentoDe(tema(primario));
      for (const derivado of [acento, acentoSuave, acentoBorde]) {
        const diferencia = Math.abs(((oklch(derivado)[2] - tono + 540) % 360) - 180);
        expect(diferencia).withContext(`${primario} → ${derivado}`).toBeLessThan(6);
      }
    }
  });

  it('en una rejilla de colores siempre cumple los tres contrastes', () => {
    let probados = 0;
    for (let tono = 0; tono < 360; tono += 15) {
      for (const luz of [0.25, 0.45, 0.65, 0.85, 0.95]) {
        for (const croma of [0.03, 0.08, 0.15, 0.25]) {
          const primario = desdeOklch(luz, croma, tono);
          if (oklch(primario)[1] < 0.01) {
            continue;
          }
          probados++;
          const { acento, acentoSuave, acentoBorde } = acentoDe(tema(primario));
          const donde = `${primario}: ${acento} ${acentoSuave} ${acentoBorde}`;
          expect(contraste(acento, '#FFFFFF')).withContext(`acento/blanco ${donde}`).toBeGreaterThanOrEqual(4.5);
          expect(contraste(acento, acentoSuave)).withContext(`acento/suave ${donde}`).toBeGreaterThanOrEqual(4.5);
          expect(contraste(acentoBorde, '#FFFFFF')).withContext(`borde/blanco ${donde}`).toBeGreaterThanOrEqual(3);
          expect(contraste(acentoBorde, FONDO)).withContext(`borde/fondo ${donde}`).toBeGreaterThanOrEqual(3);
          expect(contraste(acentoBorde, acentoSuave)).withContext(`borde/suave ${donde}`).toBeGreaterThanOrEqual(3);
        }
      }
    }
    expect(probados).toBeGreaterThan(400);
  });

  it('contraste de WCAG 2.2 (blanco sobre negro, 21:1)', () => {
    expect(contraste('#FFFFFF', '#000000')).toBeCloseTo(21, 5);
    expect(contraste('#01707F', '#FFFFFF')).toBeCloseTo(5.79, 2);
  });
});
