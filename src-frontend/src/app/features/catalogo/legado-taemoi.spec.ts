import { tarifasDeDisciplina } from './catalogo-reglas';
import { fijarCatalogoActual, restablecerCatalogoActual } from './testing/catalogo-actual.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from './testing/catalogo.fixtures';
import {
  COLOR_DISCIPLINA_NEUTRO,
  EMOJI_DISCIPLINA_NEUTRO,
  catalogoEsLegadoTaemoi,
  categoriasParaDisciplina,
  colorDisciplina,
  emojiDisciplina,
  esLegadoTaemoi,
  ofreceDerechoExamenManual,
  ofreceRecompensa,
  requiereAvisoRojoBordado,
  tarifaInicialPreinscripcion,
} from './legado-taemoi';
import { obtenerSiguienteGrado, esSiguienteGradoRojo } from '../../utilities/grado-progresion';

describe('legado-taemoi', () => {
  beforeEach(() => {
    fijarCatalogoActual(CATALOGO_TAEMOI); // los adaptadores de utilities/ leen el catálogo cargado
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 5, 15, 12, 0, 0));
  });
  afterEach(() => {
    jasmine.clock().uninstall();
    restablecerCatalogoActual();
  });

  const T = CATALOGO_TAEMOI;
  const S = CATALOGO_SEGUNDA_ESCUELA;

  it('esLegadoTaemoi: solo con el flag a true', () => {
    expect(catalogoEsLegadoTaemoi(T)).toBeTrue();
    expect(catalogoEsLegadoTaemoi(S)).toBeFalse();
    expect(catalogoEsLegadoTaemoi(null)).toBeFalse();
    expect(esLegadoTaemoi(undefined)).toBeFalse();
    expect(esLegadoTaemoi({ comportamientoLegadoTaemoi: true })).toBeTrue();
  });

  describe('D6 categorías de KB', () => {
    it('Taemoi: KICKBOXING (competición, sin categorías propias) usa las de TAEKWONDO', () => {
      const kb = T.disciplinas.find((d) => d.codigo === 'KICKBOXING');
      expect(categoriasParaDisciplina(true, T.categorias, kb).map((c) => c.nombre)).toEqual([
        'Infantil', 'Precadete', 'Cadete', 'Junior', 'Senior',
      ]);
    });

    it('Taemoi: las disciplinas que no compiten no reciben categorías', () => {
      const pilates = T.disciplinas.find((d) => d.codigo === 'PILATES');
      expect(categoriasParaDisciplina(true, T.categorias, pilates)).toEqual([]);
      expect(categoriasParaDisciplina(true, T.categorias, undefined)).toEqual([]);
    });

    it('Taemoi: si KB tuviera categorías propias, se usan solo esas', () => {
      const propias = [{ codigo: 'KB1', nombre: 'KB Uno', disciplina: 'KICKBOXING', edadMin: null, edadMax: null, orden: 1 }];
      const kb = T.disciplinas.find((d) => d.codigo === 'KICKBOXING');
      expect(categoriasParaDisciplina(true, [...T.categorias, ...propias], kb).map((c) => c.codigo)).toEqual(['KB1']);
    });

    it('sin legado: solo categorías propias o generales, nunca las de TAEKWONDO', () => {
      const kb = T.disciplinas.find((d) => d.codigo === 'KICKBOXING');
      expect(categoriasParaDisciplina(false, T.categorias, kb)).toEqual([]);
      const taekwondo = T.disciplinas.find((d) => d.codigo === 'TAEKWONDO');
      expect(categoriasParaDisciplina(false, T.categorias, taekwondo).length).toBe(5);
    });

    it('segunda escuela: una disciplina de competición sin categorías no recibe las de Taemoi', () => {
      const sinCategorias = { codigo: 'KARATE', permiteCompeticion: true };
      expect(categoriasParaDisciplina(false, S.categorias, sinCategorias)).toEqual([]);
    });
  });

  describe('D9 tarifa inicial de preinscripciones-admin', () => {
    const casos: [string | null, string][] = [
      ['2018-01-01T12:00:00', 'INFANTIL'],
      ['2008-06-16T12:00:00', 'INFANTIL'], // 17 años
      ['2008-06-15T12:00:00', 'ADULTO'], // 18 años hoy
      ['1990-01-01T12:00:00', 'ADULTO'],
      // 'yyyy-MM-dd' (diaApi): edad por año/mes/día, sin pasar por UTC
      ['2008-06-16', 'INFANTIL'],
      ['2008-06-15', 'ADULTO'],
      ['1990-01-01', 'ADULTO'],
      ['no-es-fecha', 'INFANTIL'],
      [null, 'INFANTIL'],
    ];
    casos.forEach(([fecha, esperado]) => {
      it(`TAEKWONDO con legado, ${fecha} -> ${esperado}`, () => {
        expect(tarifaInicialPreinscripcion(true, 'TAEKWONDO', fecha)).toBe(esperado);
      });
    });

    it('otras disciplinas con legado: la primera tarifa de la disciplina en orden del catálogo', () => {
      const kb = tarifasDeDisciplina(T.tarifas, 'KICKBOXING');
      expect(tarifaInicialPreinscripcion(true, 'KICKBOXING', '1990-01-01', kb)).toBe('KICKBOXING');
      expect(tarifaInicialPreinscripcion(true, 'PILATES', null, tarifasDeDisciplina(T.tarifas, 'PILATES'))).toBe('PILATES');
      expect(tarifaInicialPreinscripcion(true, 'DEFENSA_PERSONAL_FEMENINA', null, tarifasDeDisciplina(T.tarifas, 'DEFENSA_PERSONAL_FEMENINA'))).toBe('DEFENSA_PERSONAL_FEMENINA');
      expect(tarifaInicialPreinscripcion(true, 'KICKBOXING', '1990-01-01', [])).toBeNull();
      expect(tarifaInicialPreinscripcion(true, undefined, '1990-01-01')).toBeNull();
    });

    it('sin legado: sin preselección', () => {
      expect(tarifaInicialPreinscripcion(false, 'TAEKWONDO', '1990-01-01')).toBeNull();
      expect(tarifaInicialPreinscripcion(false, 'KICKBOXING', '1990-01-01', tarifasDeDisciplina(T.tarifas, 'KICKBOXING'))).toBeNull();
    });
  });

  describe('D10 derecho a examen / recompensa', () => {
    it('Taemoi: derecho a examen solo KB; recompensa en el resto', () => {
      expect(ofreceDerechoExamenManual(true, 'KICKBOXING')).toBeTrue();
      expect(ofreceRecompensa(true, 'KICKBOXING')).toBeFalse();
      for (const codigo of ['TAEKWONDO', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA']) {
        expect(ofreceDerechoExamenManual(true, codigo)).toBeFalse();
        expect(ofreceRecompensa(true, codigo)).toBeTrue();
      }
    });

    it('otras escuelas: solo recompensa', () => {
      expect(ofreceDerechoExamenManual(false, 'KICKBOXING')).toBeFalse();
      expect(ofreceRecompensa(false, 'KICKBOXING')).toBeTrue();
      expect(ofreceRecompensa(false, 'JUDO')).toBeTrue();
    });
  });

  describe('D11 cinturón rojo bordado', () => {
    it('con legado, solo cuando el siguiente grado es ROJO (igual que esSiguienteGradoRojo)', () => {
      expect(requiereAvisoRojoBordado(true, 'ROJO')).toBeTrue();
      expect(requiereAvisoRojoBordado(true, 'AZUL_ROJO')).toBeFalse();
      expect(requiereAvisoRojoBordado(true, null)).toBeFalse();
      expect(esSiguienteGradoRojo('TAEKWONDO', 'AZUL', '1990-01-01T12:00:00')).toBe(
        requiereAvisoRojoBordado(true, obtenerSiguienteGrado('TAEKWONDO', 'AZUL', '1990-01-01T12:00:00'))
      );
    });

    it('sin legado nunca', () => {
      expect(requiereAvisoRojoBordado(false, 'ROJO')).toBeFalse();
    });
  });

  describe('D12 emoji y color por disciplina', () => {
    const emojis: Record<string, string> = {
      TAEKWONDO: '🥋', KICKBOXING: '🥊', PILATES: '🧘', DEFENSA_PERSONAL_FEMENINA: '🛡️',
    };
    const colores: Record<string, string> = {
      TAEKWONDO: '#4a90d9', KICKBOXING: '#e74c3c', PILATES: '#50c878', DEFENSA_PERSONAL_FEMENINA: '#f5a623',
    };

    Object.keys(emojis).forEach((codigo) => {
      it(`${codigo} con legado: los de Taemoi; sin legado: neutros`, () => {
        expect(emojiDisciplina(true, codigo)).toBe(emojis[codigo]);
        expect(colorDisciplina(true, codigo)).toBe(colores[codigo]);
        expect(emojiDisciplina(false, codigo)).toBe(EMOJI_DISCIPLINA_NEUTRO);
        expect(colorDisciplina(false, codigo)).toBe(COLOR_DISCIPLINA_NEUTRO);
      });
    });

    it('códigos propios y valores por defecto', () => {
      expect(emojiDisciplina(true, 'JUDO')).toBe('⭐');
      expect(colorDisciplina(true, 'JUDO')).toBe('#6c757d');
      expect(emojiDisciplina(true, 'constructor')).toBe('⭐');
      expect(colorDisciplina(false, null)).toBe('#6c757d');
    });
  });
});
