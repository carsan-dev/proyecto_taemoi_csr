import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HorariosComponent } from './horarios.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';

describe('HorariosComponent', () => {
  let component: HorariosComponent;
  let router: {navigate: jasmine.Spy};

  beforeEach(() => {
    const endpoints = {obtenerLimiteTurno:()=>of(36),obtenerTurnosDTO:()=>of([])};
    router = {navigate:jasmine.createSpy('navigate')};
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    component = TestBed.runInInjectionContext(() => new HorariosComponent(endpoints as any,router as any));
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('navega con el deporte y únicamente el ID del turno pulsado', () => {
    component.navegarAPreinscripcion({id:17,grupoId:4,tipoGrupo:'Pilates'});

    expect(router.navigate).toHaveBeenCalledOnceWith(['/preinscripcion'],{
      queryParams:{deporte:'PILATES',turnoId:17}
    });
  });

  it('mapea la etiqueta del turno a su disciplina para cada actividad de Taemoi', () => {
    const casos: [string, string][] = [
      ['Taekwondo', 'TAEKWONDO'], ['Kickboxing', 'KICKBOXING'], ['Pilates', 'PILATES'],
      ['Defensa personal', 'DEFENSA_PERSONAL_FEMENINA'],
    ];
    casos.forEach(([tipoGrupo, codigo]) => {
      router.navigate.calls.reset();
      component.navegarAPreinscripcion({ id: 1, grupoId: 2, tipoGrupo });
      expect(router.navigate).toHaveBeenCalledOnceWith(['/preinscripcion'], { queryParams: { deporte: codigo, turnoId: 1 } });
    });
  });

  it('sin disciplina asociada (p. ej. competición) usa la primera del catálogo', () => {
    component.navegarAPreinscripcion({ id: 3, grupoId: 4, tipoGrupo: 'Competición' });
    expect(router.navigate).toHaveBeenCalledOnceWith(['/preinscripcion'], { queryParams: { deporte: 'TAEKWONDO', turnoId: 3 } });
  });

  describe('rango de edades (calculado en el servidor, /api/turnos/dto ya no envía alumnos)', () => {
    it('usa el rango configurado del grupo antes que las edades de los alumnos', () => {
      expect(component.obtenerRangoEdades({ rangoEdadMin: 6, rangoEdadMax: 12, edadMinAlumnos: 8, edadMaxAlumnos: 9 }))
        .toBe('6 - 12 años');
      expect(component.obtenerRangoEdades({ rangoEdadMin: 6, rangoEdadMax: null })).toBe('Desde 6 años');
      expect(component.obtenerRangoEdades({ rangoEdadMin: null, rangoEdadMax: 12 })).toBe('Hasta 12 años');
      expect(component.obtenerRangoEdades({ rangoEdadMin: 0, rangoEdadMax: 99 })).toBe('0 - 99 años');
    });

    it('sin rango configurado pinta el mínimo y el máximo de los alumnos', () => {
      expect(component.obtenerRangoEdades({ rangoEdadMin: null, rangoEdadMax: null, edadMinAlumnos: 10, edadMaxAlumnos: 14 }))
        .toBe('10 - 14 años');
    });

    it('con una sola edad la muestra sin guion', () => {
      expect(component.obtenerRangoEdades({ edadMinAlumnos: 9, edadMaxAlumnos: 9 })).toBe('9 años');
      expect(component.obtenerRangoEdades({ edadMinAlumnos: 0, edadMaxAlumnos: 0 })).toBe('0 años');
    });

    it('sin alumnos que cuenten muestra "Sin alumnos"', () => {
      expect(component.obtenerRangoEdades({ rangoEdadMin: null, rangoEdadMax: null, edadMinAlumnos: null, edadMaxAlumnos: null }))
        .toBe('Sin alumnos');
      expect(component.obtenerRangoEdades({ id: 1 })).toBe('Sin alumnos');
    });

    it('no depende de datos personales de los alumnos aunque llegaran', () => {
      const turno = { edadMinAlumnos: 10, edadMaxAlumnos: 14, alumnos: [{ fechaNacimiento: '1950-01-01' }] };
      expect(component.obtenerRangoEdades(turno)).toBe('10 - 14 años');
    });
  });
});
