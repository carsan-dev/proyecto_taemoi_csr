// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';

interface Interno {
  seleccion: { deporte?: string; fechaNacimiento?: string } | null;
  tarifasDisponibles: string[];
  esMenorSeleccionado: boolean;
  tarifaInicial(): string;
  cuantiaEstandar(t: string): number;
}

/** Instancia sin constructor ni TestBed; solo se fija la selección que leen los métodos. */
function crear(seleccion: Interno['seleccion']): Interno {
  const c = Object.create(PreinscripcionesAdminComponent.prototype);
  c.seleccion = seleccion;
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
    it('KICKBOXING, PILATES y DEFENSA_PERSONAL_FEMENINA solo ofrecen su tarifa', () => {
      expect(crear({ deporte: 'KICKBOXING' }).tarifasDisponibles).toEqual(['KICKBOXING']);
      expect(crear({ deporte: 'PILATES' }).tarifasDisponibles).toEqual(['PILATES']);
      expect(crear({ deporte: 'DEFENSA_PERSONAL_FEMENINA' }).tarifasDisponibles).toEqual(['DEFENSA_PERSONAL_FEMENINA']);
    });
    it('TAEKWONDO, otro o sin selección -> lista de Taekwondo en orden', () => {
      const tkd = ['INFANTIL', 'ADULTO', 'INFANTIL_GRUPO', 'ADULTO_GRUPO', 'FAMILIAR', 'HERMANOS', 'PADRES_HIJOS'];
      expect(crear({ deporte: 'TAEKWONDO' }).tarifasDisponibles).toEqual(tkd);
      expect(crear({ deporte: 'OTRO' }).tarifasDisponibles).toEqual(tkd);
      expect(crear(null).tarifasDisponibles).toEqual(tkd);
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
