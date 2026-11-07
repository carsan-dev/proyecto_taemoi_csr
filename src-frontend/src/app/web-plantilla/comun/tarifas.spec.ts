import { sitioConSedes } from '../datos/datos-de-prueba';
import { TarifaSedeWeb } from '../datos/modelos';
import { fechaIso, mesYAnio, tarifaPorSede } from './tarifas';

const HOY = new Date(2026, 10, 2, 16, 0);
const propia = (sedeId: number, importe: number | null, vigenteDesde: string | null): TarifaSedeWeb => ({
  tarifaId: 1,
  sedeId,
  importe,
  importeAdulto: null,
  importeMenor: null,
  vigenteDesde,
});

/** Tarifas por sede (§10 n.º 8): la vigente hoy y el próximo cambio anunciado. */
describe('tarifaPorSede', () => {
  it('sin importes propios: la general en todas, sin cambios', () => {
    const sitio = sitioConSedes(3);
    expect(tarifaPorSede(sitio, sitio.tarifas[0], HOY).map((x) => [x.sede.nombre, x.importe, x.propia, x.cambio])).toEqual([
      ['Herrera', 25, false, null],
      ['Estepa', 25, false, null],
      ['Casariche', 25, false, null],
    ]);
  });

  it('vigente hoy: la de fecha más reciente que no pasa de hoy; la futura distinta es el cambio', () => {
    const sitio = sitioConSedes(3, {
      tarifasSede: [propia(1, 26, null), propia(1, 27, '2026-09-01'), propia(1, 30, '2027-09-01'), propia(1, 28, '2027-01-01'), propia(2, 25, '2027-01-01')],
    });
    const [herrera, estepa] = tarifaPorSede(sitio, sitio.tarifas[0], HOY);
    expect(herrera.importe).toBe(27);
    expect(herrera.propia).toBeTrue();
    expect(herrera.cambio).toEqual({ importe: 28, desde: '2027-01-01' });
    // Un cambio futuro con el mismo importe no es un cambio.
    expect(estepa.cambio).toBeNull();
  });

  it('las de otra tarifa o sin importe no cuentan', () => {
    const sitio = sitioConSedes(1, { tarifasSede: [{ ...propia(1, 30, null), tarifaId: 9 }, propia(1, null, null)] });
    expect(tarifaPorSede(sitio, sitio.tarifas[0], HOY)[0].importe).toBe(25);
  });

  it('fechas', () => {
    expect(fechaIso(new Date(2027, 0, 5))).toBe('2027-01-05');
    expect(mesYAnio('2027-01-01')).toBe('enero de 2027');
  });
});
