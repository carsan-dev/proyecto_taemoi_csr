import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';

import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { LecturasPanelService } from './lecturas-panel.service';

/** Lo que se compara de una petición: método, ruta con parámetros, cabeceras y credenciales. */
function forma(r: TestRequest) {
  const cabeceras = r.request.headers.keys().map((k) => [k, r.request.headers.get(k)]);
  return { metodo: r.request.method, url: r.request.urlWithParams, cabeceras, credenciales: r.request.withCredentials };
}

/**
 * Las lecturas de bloque de las pantallas nuevas (8b-D2a): la misma petición que su método de `EndpointsService`, con
 * `ERROR_HANDLED_LOCALLY` (el de hoy, sin él).
 */
describe('LecturasPanelService (8b-D2a)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function pedir(fuente: Observable<unknown>): TestRequest {
    fuente.subscribe({ error: () => undefined });
    const [r] = http.match(() => true);
    return r;
  }

  it('alumnos: la misma petición que obtenerAlumnos, con y sin filtros, y con ERROR_HANDLED_LOCALLY', () => {
    const hoy = TestBed.inject(EndpointsService);
    const panel = TestBed.inject(LecturasPanelService);
    const casos: [number, number, string, boolean, boolean, string | null][] = [
      [1, 25, '', false, false, null],
      [3, 25, 'Ana María', true, true, 'TAEKWONDO'],
    ];
    for (const caso of casos) {
      const deHoy = pedir(hoy.obtenerAlumnos(...caso));
      const delPanel = pedir(panel.alumnos(...caso));
      expect(forma(delPanel)).toEqual(forma(deHoy));
      expect(deHoy.request.context.get(ERROR_HANDLED_LOCALLY)).toBeFalse();
      expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      deHoy.flush({});
      delPanel.flush({});
    }
  });

  it('límite por turno: la misma petición que obtenerLimiteTurno, con ERROR_HANDLED_LOCALLY', () => {
    const deHoy = pedir(TestBed.inject(EndpointsService).obtenerLimiteTurno());
    const delPanel = pedir(TestBed.inject(LecturasPanelService).limiteTurno());
    expect(forma(delPanel)).toEqual(forma(deHoy));
    expect(delPanel.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    deHoy.flush(36);
    delPanel.flush(36);
  });
});
