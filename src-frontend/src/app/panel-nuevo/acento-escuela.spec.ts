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

  it('un gris puro (croma < 0,01): tinta y suave de su tema, y el borde con la búsqueda de L de la regla y C = 0', () => {
    const { acento, acentoSuave, acentoBorde } = acentoDe(GRIS);
    expect(acento).toBe('#767676');
    expect(acentoSuave).toBe('#F2F2F2');
    expect(oklch(acentoBorde)[1]).withContext(`${acentoBorde} sin tono`).toBeLessThan(0.01);
    expect(acentoBorde).withContext('el borde-control de hoy no llega a 3:1 sobre el suave').not.toBe(BORDE_CONTROL);
    expect(acentoBorde).withContext('L más alta con ≥ 3:1: 3,36 blanco, 3,08 fondo y 3,00 suave').toBe('#8C8C8C');
  });

  it('caso gris: el borde llega a 3:1 sobre blanco, el fondo y su acento-suave (el borde-control daba 2,99 y 2,75)', () => {
    expect(contraste(BORDE_CONTROL, '#F2F2F2')).toBeLessThan(3);
    expect(contraste(BORDE_CONTROL, '#E8E9EA')).toBeLessThan(3);
    const grises: [string, string][] = [
      ['#808080', '#F2F2F2'],
      ['#1A1A1A', '#E8E8E8'],
      ['#E0E0E0', '#FCFCFC'],
      ['#7F7F80', '#F2F2F2'],
    ];
    for (const [primario, suave] of grises) {
      const { acentoBorde } = acentoDe({ primario, primarioTinta: '#1A2230', primarioSuave: suave });
      const donde = `${primario}: ${acentoBorde}`;
      expect(contraste(acentoBorde, '#FFFFFF')).withContext(`borde/blanco ${donde}`).toBeGreaterThanOrEqual(3);
      expect(contraste(acentoBorde, FONDO)).withContext(`borde/fondo ${donde}`).toBeGreaterThanOrEqual(3);
      expect(contraste(acentoBorde, suave)).withContext(`borde/suave ${donde}`).toBeGreaterThanOrEqual(3);
    }
  });

  it('una entrada que no es #RRGGBB devuelve el acento por defecto (y no lanza)', () => {
    const malas = ['', 'abc', '#abc', '#GGGGGG', '#12345', '#1234567', ' #1A2230', null, undefined];
    for (const primario of malas) {
      const tema = { primario: primario as unknown as string, primarioTinta: '#1A2230', primarioSuave: '#E8E9EA' };
      expect(acentoDe(tema)).withContext(String(primario)).toEqual(ACENTO_POR_DEFECTO);
    }
    expect(acentoDe(tema('#3e9b4f'))).withContext('minúsculas valen').toEqual(acentoDe(tema('#3E9B4F')));
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
