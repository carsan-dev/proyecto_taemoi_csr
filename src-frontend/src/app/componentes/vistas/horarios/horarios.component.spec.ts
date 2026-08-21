import { of } from 'rxjs';
import { HorariosComponent } from './horarios.component';

describe('HorariosComponent', () => {
  let component: HorariosComponent;
  let router: {navigate: jasmine.Spy};

  beforeEach(() => {
    const endpoints = {obtenerLimiteTurno:()=>of(36),obtenerTurnosDTO:()=>of([])};
    router = {navigate:jasmine.createSpy('navigate')};
    component = new HorariosComponent(endpoints as any,router as any);
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
});
