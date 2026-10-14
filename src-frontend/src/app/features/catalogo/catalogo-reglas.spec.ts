import { obtenerCuantiaTarifaEstandar } from '../../constants/tarifa.constants';
import { calcularCategoriaPorEdad, esMenor as esMenorActual } from '../../utilities/categoria-por-edad';
import { getGradoColors, getGradoTextStyle } from '../../utilities/grado-colors';
import { obtenerSiguienteGrado } from '../../utilities/grado-progresion';
import { CatalogoDisciplina } from './catalogo.models';
import {
  EDAD_ADULTO_SIN_DISCIPLINA,
  categoriaPorEdad,
  categoriasDeDisciplina,
  claseCssDisciplina,
  coloresGrado,
  cuantiaEstandar,
  disciplinasActivasOrdenadas,
  edadDesde,
  esMenor,
  estiloTextoGrado,
  gradosDeDisciplina,
  gradosSeleccionables,
  nombreCategoriaPorFecha,
  siguienteGrado,
  tarifasDeDisciplina,
} from './catalogo-reglas';
import { categoriasParaDisciplina } from './legado-taemoi';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from './testing/catalogo.fixtures';

// Fecha fija: 15/06/2026 12:00 (hora local), la misma que las pruebas de caracterización.
const HOY = new Date(2026, 5, 15, 12, 0, 0);

function nac(anio: number, mes: number, dia: number): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${anio}-${p(mes)}-${p(dia)}T12:00:00`;
}

const T = CATALOGO_TAEMOI;
const disc = (codigo: string): CatalogoDisciplina | undefined => T.disciplinas.find((d) => d.codigo === codigo);
const CODIGOS_DISCIPLINA = ['TAEKWONDO', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA'];

/** Fechas de nacimiento: cada edad (-1..90) con cuatro cumpleaños (antes, el mismo día, después y fin de año). */
function fechasDePrueba(): string[] {
  const fechas: string[] = [];
  for (let edad = -1; edad <= 90; edad++) {
    for (const [mes, dia] of [[1, 1], [6, 14], [6, 15], [6, 16], [12, 31]]) {
      fechas.push(nac(2026 - edad, mes, dia));
    }
  }
  return fechas;
}

describe('catalogo-reglas', () => {
  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(HOY);
  });
  afterEach(() => jasmine.clock().uninstall());

  describe('equivalencia con las utilidades actuales (fixture de Taemoi)', () => {
    describe('esMenor', () => {
      it('igual que utilities/categoria-por-edad para todas las edades y disciplinas', () => {
        const fechas = fechasDePrueba();
        for (const fecha of fechas) {
          for (const codigo of [...CODIGOS_DISCIPLINA, 'DESCONOCIDA']) {
            expect(esMenor(fecha, disc(codigo))).toBe(esMenorActual(fecha, codigo), `${fecha} ${codigo}`);
          }
          expect(esMenor(fecha, undefined)).toBe(esMenorActual(fecha), `${fecha} sin disciplina`);
        }
      });

      it('sin fecha, vacía o inválida -> menor (también con Date)', () => {
        [null, undefined, '', 'no-es-fecha'].forEach((f) => {
          expect(esMenor(f, disc('TAEKWONDO'))).toBe(true);
          expect(esMenor(f, disc('KICKBOXING'))).toBe(true);
        });
        expect(() => esMenor(new Date(NaN), disc('TAEKWONDO'))).toThrowError(RangeError);
        expect(() => esMenorActual(new Date(NaN))).toThrowError(RangeError);
      });

      it('acepta objeto Date igual que la utilidad actual', () => {
        for (const d of [new Date(2012, 11, 31, 12), new Date(2013, 0, 1, 12), new Date(2011, 11, 31, 12)]) {
          expect(esMenor(d, disc('TAEKWONDO'))).toBe(esMenorActual(d));
          expect(esMenor(d, disc('KICKBOXING'))).toBe(esMenorActual(d, 'KICKBOXING'));
        }
      });

      it('sin disciplina o sin edadAdulto usa 14', () => {
        expect(EDAD_ADULTO_SIN_DISCIPLINA).toBe(14);
        const fecha = nac(2012, 12, 31); // 13 años que cumple 14 en 2026
        expect(esMenor(fecha, null)).toBe(false);
        expect(esMenor(fecha, { edadAdulto: null })).toBe(false);
        expect(esMenor(fecha, disc('YOGA'))).toBe(false);
      });
    });

    describe('categorías', () => {
      it('igual que calcularCategoriaPorEdad en las disciplinas de competición', () => {
        for (const fecha of fechasDePrueba()) {
          for (const codigo of ['TAEKWONDO', 'KICKBOXING']) {
            const categorias = categoriasParaDisciplina(true, T.categorias, disc(codigo));
            expect(nombreCategoriaPorFecha(categorias, fecha)).toBe(calcularCategoriaPorEdad(fecha, codigo), `${fecha} ${codigo}`);
          }
        }
      });

      it('sin fecha o inválida -> cadena vacía; Date inválido lanza como la utilidad actual', () => {
        const categorias = categoriasParaDisciplina(true, T.categorias, disc('TAEKWONDO'));
        [null, undefined, '', 'no-es-fecha'].forEach((f) => {
          expect(nombreCategoriaPorFecha(categorias, f)).toBe(calcularCategoriaPorEdad(f));
          expect(nombreCategoriaPorFecha(categorias, f)).toBe('');
        });
        expect(() => nombreCategoriaPorFecha(categorias, new Date(NaN))).toThrowError(RangeError);
      });

      it('límites de los rangos', () => {
        const categorias = categoriasDeDisciplina(T.categorias, 'TAEKWONDO');
        const esperado: [number, string][] = [
          [0, 'Infantil'], [9, 'Infantil'], [10, 'Precadete'], [11, 'Precadete'], [12, 'Cadete'], [13, 'Cadete'],
          [14, 'Junior'], [16, 'Junior'], [17, 'Senior'], [90, 'Senior'],
        ];
        esperado.forEach(([edad, nombre]) => expect(categoriaPorEdad(categorias, edad)?.nombre).toBe(nombre));
      });

      // Latente en crear-alumno: los flags ocultos competidor/categoria no se reinician al cambiar de deporte;
      // la rebanada de migración de crear-alumno debe reiniciarlos. Se mantiene el comportamiento nuevo.
      it('las disciplinas que no compiten (Pilates) no reciben categorías', () => {
        expect(categoriasParaDisciplina(true, T.categorias, disc('PILATES'))).toEqual([]);
        expect(calcularCategoriaPorEdad(nac(1990, 1, 1), 'PILATES')).toBe('Senior');
      });
    });

    describe('siguiente grado', () => {
      const MENOR = '2018-01-01T12:00:00';
      const ADULTO = '1990-01-01T12:00:00';
      const CODIGOS = [
        'BLANCO', 'BLANCO_AMARILLO', 'AMARILLO', 'AMARILLO_NARANJA', 'NARANJA', 'NARANJA_VERDE', 'VERDE',
        'VERDE_AZUL', 'AZUL', 'AZUL_ROJO', 'ROJO', 'ROJO_NEGRO', 'NEGRO', 'ROJO_NEGRO_1_PUM',
        'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM', 'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN',
        'NEGRO_5_DAN', 'DESCONOCIDO', 'blanco', '',
      ];

      function nuevo(codigoDisciplina: string, codigoGrado: string | null | undefined, fecha: string | Date | null | undefined): string | null {
        const d = disc(codigoDisciplina);
        return siguienteGrado(gradosDeDisciplina(T.sistemasGrados, d), codigoGrado, esMenor(fecha, d))?.codigo ?? null;
      }

      it('igual que obtenerSiguienteGrado en TAEKWONDO y KICKBOXING, menores, adultos y sin fecha', () => {
        for (const deporte of ['TAEKWONDO', 'KICKBOXING']) {
          for (const grado of CODIGOS) {
            for (const fecha of [MENOR, ADULTO, null, undefined]) {
              expect(nuevo(deporte, grado, fecha)).toBe(obtenerSiguienteGrado(deporte, grado, fecha), `${deporte} ${grado} ${fecha}`);
            }
          }
        }
      });

      it('igual en las fronteras de edad (TKD 14 / KB 15)', () => {
        for (const fecha of fechasDePrueba()) {
          for (const deporte of ['TAEKWONDO', 'KICKBOXING']) {
            for (const grado of ['BLANCO_AMARILLO', 'AZUL', 'ROJO', 'ROJO_NEGRO_1_PUM', 'NEGRO_1_DAN']) {
              expect(nuevo(deporte, grado, fecha)).toBe(obtenerSiguienteGrado(deporte, grado, fecha), `${deporte} ${grado} ${fecha}`);
            }
          }
        }
      });

      it('disciplina sin sistema de grados, grado vacío o nulo -> null', () => {
        for (const deporte of ['PILATES', 'DEFENSA_PERSONAL_FEMENINA']) {
          expect(nuevo(deporte, 'BLANCO', ADULTO)).toBeNull();
          expect(obtenerSiguienteGrado(deporte, 'BLANCO', ADULTO)).toBeNull();
        }
        [null, undefined, ''].forEach((g) => expect(nuevo('TAEKWONDO', g, ADULTO)).toBeNull());
      });
    });

    describe('grados seleccionables', () => {
      it('TAEKWONDO: los 19 grados, por orden', () => {
        expect(gradosSeleccionables(T.sistemasGrados, disc('TAEKWONDO')).map((g) => g.codigo)).toEqual([
          'BLANCO', 'BLANCO_AMARILLO', 'AMARILLO', 'AMARILLO_NARANJA', 'NARANJA', 'NARANJA_VERDE', 'VERDE', 'VERDE_AZUL',
          'AZUL', 'AZUL_ROJO', 'ROJO', 'ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM', 'NEGRO_1_DAN',
          'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN',
        ]);
      });

      it('KICKBOXING: el subconjunto fijo de 11 que usan hoy crear/editar alumno', () => {
        expect(gradosSeleccionables(T.sistemasGrados, disc('KICKBOXING')).map((g) => g.codigo)).toEqual([
          'BLANCO', 'AMARILLO', 'NARANJA', 'VERDE', 'AZUL', 'ROJO', 'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN',
          'NEGRO_4_DAN', 'NEGRO_5_DAN',
        ]);
      });

      it('con esMenor filtra por grupo de edad (listarGradosAplicables)', () => {
        const menores = gradosSeleccionables(T.sistemasGrados, disc('TAEKWONDO'), true).map((g) => g.codigo);
        const adultos = gradosSeleccionables(T.sistemasGrados, disc('TAEKWONDO'), false).map((g) => g.codigo);
        expect(menores.length).toBe(14);
        expect(menores).not.toContain('NEGRO_1_DAN');
        expect(adultos.length).toBe(11);
        expect(adultos).not.toContain('BLANCO_AMARILLO');
        expect(adultos).toContain('NEGRO_5_DAN');
      });

      it('Pilates / defensa personal: sin grados', () => {
        expect(gradosSeleccionables(T.sistemasGrados, disc('PILATES'))).toEqual([]);
        expect(gradosSeleccionables(T.sistemasGrados, null)).toEqual([]);
      });
    });

    describe('tarifas', () => {
      const ROLES: (string | undefined | null)[] = ['PADRE', 'MADRE', 'HIJO', 'HIJA', undefined, null, '', 'NINGUNO', 'OTRO'];

      it('cuantiaEstandar igual que obtenerCuantiaTarifaEstandar para cada tarifa y rol', () => {
        for (const tarifa of T.tarifas) {
          for (const rol of ROLES) {
            expect(cuantiaEstandar(tarifa, rol)).toBe(obtenerCuantiaTarifaEstandar(tarifa.codigo, rol ?? undefined), `${tarifa.codigo} ${rol}`);
          }
        }
      });

      it('tarifa desconocida -> 0', () => {
        expect(cuantiaEstandar(undefined, 'PADRE')).toBe(0);
        expect(cuantiaEstandar(null)).toBe(0);
        expect(obtenerCuantiaTarifaEstandar('INEXISTENTE', 'PADRE')).toBe(0);
      });

      it('PADRES_HIJOS por rol: 28 progenitor, 26 hijo, 0 en otro caso', () => {
        const t = T.tarifas.find((x) => x.codigo === 'PADRES_HIJOS');
        expect([cuantiaEstandar(t, 'PADRE'), cuantiaEstandar(t, 'MADRE'), cuantiaEstandar(t, 'HIJO'), cuantiaEstandar(t, 'HIJA')]).toEqual([28, 28, 26, 26]);
        expect([cuantiaEstandar(t), cuantiaEstandar(t, 'NINGUNO')]).toEqual([0, 0]);
      });

      it('tarifasDeDisciplina: las de la disciplina por orden del catálogo (crear-alumno)', () => {
        expect(tarifasDeDisciplina(T.tarifas, 'TAEKWONDO').map((t) => t.codigo)).toEqual([
          'ADULTO', 'ADULTO_GRUPO', 'FAMILIAR', 'INFANTIL', 'INFANTIL_GRUPO', 'HERMANOS', 'PADRES_HIJOS',
        ]);
        expect(tarifasDeDisciplina(T.tarifas, 'KICKBOXING').map((t) => t.codigo)).toEqual(['KICKBOXING']);
        expect(tarifasDeDisciplina(T.tarifas, 'PILATES').map((t) => t.codigo)).toEqual(['PILATES']);
        expect(tarifasDeDisciplina(T.tarifas, 'DEFENSA_PERSONAL_FEMENINA').map((t) => t.codigo)).toEqual(['DEFENSA_PERSONAL_FEMENINA']);
      });
    });

    describe('colores de grado', () => {
      const grados = [
        ...T.sistemasGrados[0].grados.map((g) => ['TAEKWONDO', g] as const),
        ...T.sistemasGrados[1].grados.filter((g) => g.codigo !== 'ROJO').map((g) => ['KICKBOXING', g] as const),
      ];

      it('coloresGrado y estiloTextoGrado igual que getGradoColors / getGradoTextStyle', () => {
        for (const [deporte, grado] of grados) {
          expect(coloresGrado(grado)).toEqual(getGradoColors(grado.codigo), `${deporte} ${grado.codigo}`);
          expect(estiloTextoGrado(grado)).toBe(getGradoTextStyle(grado.codigo), `${deporte} ${grado.codigo}`);
        }
      });

      it('KICKBOXING ROJO es marrón (D4); getGradoColors lo daba rojo', () => {
        const rojoKb = T.sistemasGrados[1].grados.find((g) => g.codigo === 'ROJO')!;
        expect(rojoKb.nombre).toBe('MARRÓN');
        expect(coloresGrado(rojoKb)).toEqual({ colors: ['#8B4513'], isSplit: false, needsBorder: false });
        expect(getGradoColors('ROJO').colors).toEqual(['#FF0000']);
      });

      it('color1 = mitad inferior, color2 = mitad superior', () => {
        const info = coloresGrado({ color1: '#0000FF', color2: '#FF0000' });
        expect(info.colors).toEqual(['#0000FF', '#FF0000']);
        expect(estiloTextoGrado({ color1: '#0000FF', color2: '#FF0000' })).toContain('linear-gradient(to bottom, #FF0000 50%, #0000FF 50%)');
      });

      it('sin grado o sin color1 -> gris por defecto', () => {
        const gris = { colors: ['#CCCCCC'], isSplit: false, needsBorder: false };
        expect(coloresGrado(null)).toEqual(gris);
        expect(coloresGrado({ color1: null, color2: null })).toEqual(gris);
        expect(coloresGrado(undefined)).toEqual(getGradoColors('DESCONOCIDO'));
      });
    });
  });

  describe('segunda escuela', () => {
    const S = CATALOGO_SEGUNDA_ESCUELA;
    const judo = S.disciplinas[0];

    it('esMenor usa la edad adulta de su disciplina (16)', () => {
      // 14 años y 15 años cumplidos: menores; 15 que cumple 16 este año: adulto
      expect(esMenor(nac(2012, 1, 1), judo)).toBe(true);
      expect(esMenor(nac(2011, 1, 1), judo)).toBe(true);
      expect(esMenor(nac(2010, 12, 31), judo)).toBe(false); // 15 años que cumple 16 en 2026
      expect(esMenor(nac(2009, 1, 1), judo)).toBe(false);
    });

    it('categorías propias, sin relleno con las de otra disciplina', () => {
      const cats = categoriasParaDisciplina(false, S.categorias, judo);
      expect(cats.map((c) => c.codigo)).toEqual(['IT-INF', 'IT-ABS']);
      expect(nombreCategoriaPorFecha(cats, nac(2018, 1, 1))).toBe('Infantil judo');
      expect(nombreCategoriaPorFecha(cats, nac(2012, 1, 1))).toBe(''); // 14 años: ninguna categoría
      expect(nombreCategoriaPorFecha(cats, nac(1990, 1, 1))).toBe('Absoluta judo');
    });

    it('tarifas: la general (disciplina nula) y la de la disciplina, por orden', () => {
      expect(tarifasDeDisciplina(S.tarifas, 'JUDO').map((t) => t.codigo)).toEqual(['BONO', 'JUDO_MES']);
      expect(tarifasDeDisciplina(S.tarifas, 'YOGA').map((t) => t.codigo)).toEqual(['BONO']);
      expect(tarifasDeDisciplina(S.tarifas, null).map((t) => t.codigo)).toEqual(['BONO']);
    });

    it('tarifasDeDisciplina oculta las inactivas salvo que se pidan', () => {
      const tarifas = [{ ...S.tarifas[1], activa: false }, S.tarifas[0]];
      expect(tarifasDeDisciplina(tarifas, 'JUDO').map((t) => t.codigo)).toEqual(['BONO']);
      expect(tarifasDeDisciplina(tarifas, 'JUDO', true).map((t) => t.codigo)).toEqual(['BONO', 'JUDO_MES']);
    });

    it('cuantiaEstandar por rol con importes propios', () => {
      const t = S.tarifas[1];
      expect(cuantiaEstandar(t, 'PADRE')).toBe(40);
      expect(cuantiaEstandar(t, 'HIJA')).toBe(30);
      expect(cuantiaEstandar(t, 'NINGUNO')).toBe(35);
      expect(cuantiaEstandar(t)).toBe(35);
      expect(cuantiaEstandar(S.tarifas[0], 'PADRE')).toBe(10); // sin importeAdulto: importe general
    });

    it('grados propios: seleccionables y siguiente', () => {
      const grados = gradosDeDisciplina(S.sistemasGrados, judo);
      expect(grados.map((g) => g.codigo)).toEqual(['BLANCO_JUDO', 'MARRON_JUDO']);
      expect(gradosSeleccionables(S.sistemasGrados, judo).map((g) => g.codigo)).toEqual(['BLANCO_JUDO', 'MARRON_JUDO']);
      expect(siguienteGrado(grados, 'BLANCO_JUDO', true)?.codigo).toBe('MARRON_JUDO');
      expect(siguienteGrado(grados, 'MARRON_JUDO', false)).toBeNull();
      expect(gradosDeDisciplina(S.sistemasGrados, S.disciplinas[1])).toEqual([]);
    });

    it('colores propios', () => {
      expect(coloresGrado(S.sistemasGrados[0].grados[0])).toEqual({ colors: ['#FAFAFA'], isSplit: false, needsBorder: false });
      expect(coloresGrado(S.sistemasGrados[0].grados[1]).isSplit).toBeTrue();
    });

    it('disciplinas activas: solo las activas, por orden', () => {
      expect(disciplinasActivasOrdenadas(S.disciplinas).map((d) => d.codigo)).toEqual(['JUDO']);
      expect(disciplinasActivasOrdenadas(T.disciplinas).map((d) => d.codigo)).toEqual(CODIGOS_DISCIPLINA);
    });
  });

  describe('siguienteGrado con reglas aplicaA propias', () => {
    const g = (codigo: string, orden: number, aplicaA: 'TODOS' | 'MENORES' | 'ADULTOS' | 'NINGUNO') => ({
      codigo, nombre: codigo, orden, aplicaA, color1: null, color2: null, requiereExamen: true,
    });
    const sistema = [g('C', 3, 'ADULTOS'), g('A', 1, 'TODOS'), g('B', 2, 'MENORES'), g('D', 4, 'TODOS')];

    it('ordena por orden aunque lleguen desordenados y respeta el grupo de edad', () => {
      expect(siguienteGrado(sistema, 'A', true)?.codigo).toBe('B');
      expect(siguienteGrado(sistema, 'A', false)?.codigo).toBe('C');
      expect(siguienteGrado(sistema, 'B', false)?.codigo).toBe('D'); // el actual no es de adultos: solo TODOS
      expect(siguienteGrado(sistema, 'C', true)?.codigo).toBe('D');
      expect(siguienteGrado(sistema, 'D', true)).toBeNull();
      expect(siguienteGrado(sistema, 'X', true)).toBeNull();
    });
  });

  describe('utilidades', () => {
    it('claseCssDisciplina: minúsculas y _ -> -', () => {
      expect(claseCssDisciplina('DEFENSA_PERSONAL_FEMENINA')).toBe('defensa-personal-femenina');
      expect(claseCssDisciplina('TAEKWONDO')).toBe('taekwondo');
      expect(claseCssDisciplina(null)).toBe('');
    });

    it('edadDesde', () => {
      expect(edadDesde(nac(2016, 6, 15))).toBe(10);
      expect(edadDesde(nac(2016, 6, 16))).toBe(9);
      expect(edadDesde('no-es-fecha')).toBeNull();
      expect(edadDesde(null)).toBeNull();
    });
  });
});
