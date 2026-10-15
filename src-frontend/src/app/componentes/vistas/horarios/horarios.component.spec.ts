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
});
