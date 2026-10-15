// Equivalencia de importes de tarifa (rebanada 6). Sustituye a las caracterizaciones de
// `constants/tarifa.constants.ts` (TARIFAS_ESTANDAR, obtenerCuantiaTarifaEstandar), `enums/tipo-tarifa.ts`,
// `editar-alumno.asignarCuantiaTarifa` (D1, borrado) y `preinscripciones-admin.cuantiaEstandar`, que ya no
// existen: sus valores se copian aquí literalmente y se comparan con `cuantiaEstandar` sobre el fixture de Taemoi.
import { CatalogoTarifa } from './catalogo.models';
import { cuantiaEstandar, tarifasDeDisciplina } from './catalogo-reglas';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from './testing/catalogo.fixtures';

/** `TARIFAS_ESTANDAR` tal como estaba en `constants/tarifa.constants.ts`. */
const TARIFAS_ESTANDAR_ANTIGUAS: Readonly<Record<string, number>> = {
  FAMILIAR: 0.0,
  PADRES_HIJOS: 0.0,
  ADULTO_GRUPO: 20.0,
  INFANTIL_GRUPO: 20.0,
  HERMANOS: 26.0,
  INFANTIL: 28.0,
  ADULTO: 30.0,
  KICKBOXING: 30.0,
  PILATES: 30.0,
  DEFENSA_PERSONAL_FEMENINA: 25.0,
};

/** `obtenerCuantiaTarifaEstandar` antes del catálogo: PADRES_HIJOS 28 (padre/madre) o 26 (hijo/hija). */
function cuantiaAntigua(tipoTarifa: string, rolFamiliar?: string | null): number {
  if (tipoTarifa === 'PADRES_HIJOS') {
    if (rolFamiliar === 'PADRE' || rolFamiliar === 'MADRE') {
      return 28.0;
    }
    if (rolFamiliar === 'HIJO' || rolFamiliar === 'HIJA') {
      return 26.0;
    }
  }
  return TARIFAS_ESTANDAR_ANTIGUAS[tipoTarifa] ?? 0;
}

/** Valores de `TipoTarifa` en el orden del enum (que era el de crear-alumno). */
const TIPO_TARIFA_ANTIGUO = [
  'ADULTO', 'ADULTO_GRUPO', 'FAMILIAR', 'INFANTIL', 'INFANTIL_GRUPO', 'HERMANOS',
  'PADRES_HIJOS', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA',
];

const tarifaTaemoi = (codigo: string): CatalogoTarifa | undefined =>
  CATALOGO_TAEMOI.tarifas.find((t) => t.codigo === codigo);

describe('Tarifas de Taemoi: equivalencia de importes con los valores anteriores al catálogo', () => {
  const roles: (string | null | undefined)[] = ['PADRE', 'MADRE', 'HIJO', 'HIJA', 'NINGUNO', '', 'OTRO', null, undefined];
  const codigos = [...Object.keys(TARIFAS_ESTANDAR_ANTIGUAS), 'INEXISTENTE', ''];

  codigos.forEach((codigo) => {
    roles.forEach((rol) => {
      const esperado = cuantiaAntigua(codigo, rol);
      it(`${JSON.stringify(codigo)} x rol ${JSON.stringify(rol)} -> ${esperado}`, () => {
        expect(cuantiaEstandar(tarifaTaemoi(codigo), rol)).toBe(esperado);
      });
    });
  });

  it('el catálogo de Taemoi tiene exactamente las tarifas de TipoTarifa, en el orden del enum', () => {
    expect(CATALOGO_TAEMOI.tarifas.map((t) => t.codigo).sort()).toEqual([...TIPO_TARIFA_ANTIGUO].sort());
    const porOrden = ['TAEKWONDO', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA']
      .flatMap((d) => tarifasDeDisciplina(CATALOGO_TAEMOI.tarifas, d).map((t) => t.codigo));
    expect(porOrden).toEqual(TIPO_TARIFA_ANTIGUO);
  });

  it('preinscripciones-admin (importe sin rol): igual que su tabla anterior, también PADRES_HIJOS -> 0', () => {
    Object.entries(TARIFAS_ESTANDAR_ANTIGUAS).forEach(([codigo, importe]) => {
      expect(cuantiaEstandar(tarifaTaemoi(codigo))).withContext(codigo).toBe(importe);
    });
  });

  it('editar-alumno asignarCuantiaTarifa (D1, borrado): sus importes coinciden con el catálogo', () => {
    const antiguos: [string, string | null, number][] = [
      ['ADULTO', 'NINGUNO', 30], ['ADULTO_GRUPO', 'NINGUNO', 20], ['FAMILIAR', 'NINGUNO', 0],
      ['INFANTIL', 'NINGUNO', 28], ['INFANTIL_GRUPO', 'NINGUNO', 20], ['HERMANOS', 'NINGUNO', 26],
      ['KICKBOXING', 'NINGUNO', 30], ['PADRES_HIJOS', 'PADRE', 28], ['PADRES_HIJOS', 'MADRE', 28],
      ['PADRES_HIJOS', 'HIJO', 26], ['PADRES_HIJOS', 'HIJA', 26], ['PADRES_HIJOS', 'NINGUNO', 0], ['PADRES_HIJOS', null, 0],
    ];
    antiguos.forEach(([codigo, rol, importe]) => {
      expect(cuantiaEstandar(tarifaTaemoi(codigo), rol)).withContext(`${codigo}/${rol}`).toBe(importe);
    });
  });

  it('banderas: solo PADRES_HIJOS pide rol y solo HERMANOS pide grupo (las reglas de pantalla anteriores)', () => {
    expect(CATALOGO_TAEMOI.tarifas.filter((t) => t.requiereRolFamiliar).map((t) => t.codigo)).toEqual(['PADRES_HIJOS']);
    expect(CATALOGO_TAEMOI.tarifas.filter((t) => t.requiereGrupoFamiliar).map((t) => t.codigo)).toEqual(['HERMANOS']);
  });

  it('segunda escuela: importes de su catálogo; códigos de Taemoi desconocidos -> 0', () => {
    const tarifa = (codigo: string) => CATALOGO_SEGUNDA_ESCUELA.tarifas.find((t) => t.codigo === codigo);
    expect(cuantiaEstandar(tarifa('BONO'))).toBe(10);
    expect(cuantiaEstandar(tarifa('JUDO_MES'))).toBe(35);
    expect(cuantiaEstandar(tarifa('JUDO_MES'), 'PADRE')).toBe(40);
    expect(cuantiaEstandar(tarifa('JUDO_MES'), 'HIJA')).toBe(30);
    expect(cuantiaEstandar(tarifa('ADULTO'))).toBe(0);
  });
});
