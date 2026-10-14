// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
// Rebanada 6: importes, lista y tarifa inicial salen del catálogo (fixture de Taemoi). Cambios explícitos:
// D7 (lista de la disciplina en orden del catálogo; sin disciplina conocida, ninguna) y D8 (etiqueta = nombre).
import { TestBed } from '@angular/core/testing';
import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';
import { crearCatalogoServiceTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';

interface Interno {
  seleccion: { deporte?: string; fechaNacimiento?: string } | null;
  tarifasDisponibles: { codigo: string; nombre: string }[];
  esMenorSeleccionado: boolean;
  tarifaInicial(): string;
  cuantiaEstandar(t: string): number;
}

/** Instancia sin constructor: se fijan la selección y el catálogo (de Taemoi) que leen los métodos. */
function crear(seleccion: Interno['seleccion']): Interno {
  const c = Object.create(PreinscripcionesAdminComponent.prototype);
  c.seleccion = seleccion;
  c.catalogo = TestBed.runInInjectionContext(() => crearCatalogoServiceTesting(CATALOGO_TAEMOI));
  return c as Interno;
}

describe('preinscripciones-admin importes y regla de menor de 18 (caracterización)', () => {
  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 5, 15, 12, 0, 0));
  });
  afterEach(() => jasmine.clock().uninstall());

  describe('cuantiaEstandar', () => {
    const tabla: [string, number][] = [
      ['FAMILIAR', 0], ['PADRES_HIJOS', 0], ['ADULTO_GRUPO', 20], ['INFANTIL_GRUPO', 20], ['HERMANOS', 26],
      ['INFANTIL', 28], ['ADULTO', 30], ['KICKBOXING', 30], ['PILATES', 30], ['DEFENSA_PERSONAL_FEMENINA', 25],
      ['DESCONOCIDA', 0], ['', 0],
    ];
    tabla.forEach(([tarifa, importe]) => {
      it(`${JSON.stringify(tarifa)} -> ${importe}`, () => {
        expect(crear(null).cuantiaEstandar(tarifa)).toBe(importe);
      });
    });
  });

  describe('tarifasDisponibles', () => {
    const codigos = (i: Interno) => i.tarifasDisponibles.map((t) => t.codigo);
    it('KICKBOXING, PILATES y DEFENSA_PERSONAL_FEMENINA solo ofrecen su tarifa', () => {
      expect(codigos(crear({ deporte: 'KICKBOXING' }))).toEqual(['KICKBOXING']);
      expect(codigos(crear({ deporte: 'PILATES' }))).toEqual(['PILATES']);
      expect(codigos(crear({ deporte: 'DEFENSA_PERSONAL_FEMENINA' }))).toEqual(['DEFENSA_PERSONAL_FEMENINA']);
    });
    it('TAEKWONDO -> sus 7 tarifas en orden del catálogo (D7; antes INFANTIL, ADULTO, INFANTIL_GRUPO, ...)', () => {
      const tkd = ['ADULTO', 'ADULTO_GRUPO', 'FAMILIAR', 'INFANTIL', 'INFANTIL_GRUPO', 'HERMANOS', 'PADRES_HIJOS'];
      expect(codigos(crear({ deporte: 'TAEKWONDO' }))).toEqual(tkd);
    });
    it('disciplina desconocida o sin selección -> ninguna (D7; antes la lista de Taekwondo)', () => {
      expect(codigos(crear({ deporte: 'OTRO' }))).toEqual([]);
      expect(codigos(crear(null))).toEqual([]);
    });
  });

  describe('tarifaInicial (regla < 18)', () => {
    const tkd = (fechaNacimiento: string) => crear({ deporte: 'TAEKWONDO', fechaNacimiento }).tarifaInicial();

    it('17 años (cumple 18 mañana) -> INFANTIL', () => {
      expect(tkd('2008-06-16T12:00:00')).toBe('INFANTIL');
    });
    it('18 años hoy -> ADULTO', () => {
      expect(tkd('2008-06-15T12:00:00')).toBe('ADULTO');
    });
    it('niño y adulto', () => {
      expect(tkd('2018-01-01T12:00:00')).toBe('INFANTIL');
      expect(tkd('1990-01-01T12:00:00')).toBe('ADULTO');
    });
    it('fecha inválida -> INFANTIL', () => {
      expect(tkd('no-es-fecha')).toBe('INFANTIL');
    });
    it('otros deportes -> primera tarifa disponible', () => {
      expect(crear({ deporte: 'KICKBOXING', fechaNacimiento: '1990-01-01' }).tarifaInicial()).toBe('KICKBOXING');
      expect(crear({ deporte: 'PILATES' }).tarifaInicial()).toBe('PILATES');
      expect(crear({ deporte: 'DEFENSA_PERSONAL_FEMENINA' }).tarifaInicial()).toBe('DEFENSA_PERSONAL_FEMENINA');
    });
  });

  describe('esMenorSeleccionado', () => {
    it('17 -> true, 18 -> false', () => {
      expect(crear({ fechaNacimiento: '2008-06-16T12:00:00' }).esMenorSeleccionado).toBeTrue();
      expect(crear({ fechaNacimiento: '2008-06-15T12:00:00' }).esMenorSeleccionado).toBeFalse();
    });
    it('fecha inválida -> false (distinto de esMenor, que da true)', () => {
      expect(crear({ fechaNacimiento: 'no-es-fecha' }).esMenorSeleccionado).toBeFalse();
    });
  });
});
