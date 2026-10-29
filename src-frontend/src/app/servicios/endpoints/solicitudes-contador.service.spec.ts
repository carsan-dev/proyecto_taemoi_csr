import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthenticationService } from '../authentication/authentication.service';
import { SolicitudesContadorService } from './solicitudes-contador.service';

describe('SolicitudesContadorService', () => {
  const url = `${environment.apiUrl}/solicitudes/contador`;
  let http: HttpTestingController;
  let servicio: SolicitudesContadorService;
  let soloProfesor: boolean;

  beforeEach(() => {
    soloProfesor = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthenticationService, useValue: { esSoloProfesor: () => soloProfesor } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(SolicitudesContadorService);
  });

  afterEach(() => http.verify());

  it('pide el contador a la gestión y guarda el total', () => {
    servicio.refrescar();
    http.expectOne(url).flush({ total: 3 });
    expect(servicio.total()).toBe(3);
  });

  it('una sesión solo profesor no lo pide (el servidor le daría 403) y su total es cero', () => {
    servicio.refrescar();
    http.expectOne(url).flush({ total: 3 });
    soloProfesor = true;
    servicio.refrescar();
    http.expectNone(url);
    expect(servicio.total()).toBe(0);
  });
});
