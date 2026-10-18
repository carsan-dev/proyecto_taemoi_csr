import { of } from 'rxjs';

import { EndpointsService } from './endpoints.service';

/** R2-P4: las listas ligeras de grupos y turnos piden sus endpoints nuevos, con sesión, sin los completos. */
describe('EndpointsService (resumen de grupos y turnos)', () => {
  function crear(): { servicio: EndpointsService; get: jasmine.Spy } {
    const get = jasmine.createSpy('get').and.returnValue(of([]));
    const http = jasmine.createSpyObj('HttpClient', ['get']);
    http.get = get;
    return { servicio: new EndpointsService(http as any), get };
  }

  it('obtenerResumenGrupos pide /grupos/resumen con credenciales', (done) => {
    const { servicio, get } = crear();

    servicio.obtenerResumenGrupos().subscribe((grupos) => {
      expect(grupos).toEqual([]);
      expect(get.calls.count()).toBe(1);
      expect(get.calls.mostRecent().args[0]).toMatch(/\/grupos\/resumen$/);
      expect(get.calls.mostRecent().args[1].withCredentials).toBeTrue();
      done();
    });
  });

  it('obtenerResumenTurnos pide /turnos/resumen con credenciales', (done) => {
    const { servicio, get } = crear();

    servicio.obtenerResumenTurnos().subscribe((turnos) => {
      expect(turnos).toEqual([]);
      expect(get.calls.count()).toBe(1);
      expect(get.calls.mostRecent().args[0]).toMatch(/\/turnos\/resumen$/);
      expect(get.calls.mostRecent().args[1].withCredentials).toBeTrue();
      done();
    });
  });
});
