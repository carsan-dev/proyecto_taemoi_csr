import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import Swal from 'sweetalert2';

import { AuthenticationService } from './authentication.service';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { environment } from '../../../environments/environment';

const URL_ROLES = `${environment.apiUrl}/auth/roles`;

/** Fallo de red: la petición no llega al servidor (estado 0), como Firefox al restaurar la sesión. */
function falloDeRed(req: TestRequest): void {
  req.error(new ProgressEvent('error'));
}

function falloConEstado(req: TestRequest, status: number): void {
  req.flush({}, { status, statusText: 'x' });
}

describe('AuthenticationService: reintento de la carga de roles al arrancar', () => {
  let auth: AuthenticationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    spyOn(Swal, 'fire');
    auth = TestBed.inject(AuthenticationService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne(`${environment.apiUrl}/auth/auth-status`).flush(false);
  });

  afterEach(() => http.verify());

  it('un fallo de red y luego 200: devuelve los roles y quedan cargados', fakeAsync(() => {
    let roles: string[] | undefined;
    auth.obtenerRoles().subscribe((r) => (roles = r));

    falloDeRed(http.expectOne(URL_ROLES));
    expect(roles).toBeUndefined();
    tick(499);
    http.expectNone(URL_ROLES);
    tick(1);
    http.expectOne(URL_ROLES).flush(['ROLE_ADMIN']);

    expect(roles).toEqual(['ROLE_ADMIN']);
    expect(auth.rolesEstanCargados()).toBeTrue();
  }));

  it('503 dos veces y luego 200: devuelve los roles con espera creciente', fakeAsync(() => {
    let roles: string[] | undefined;
    auth.obtenerRoles().subscribe((r) => (roles = r));

    falloConEstado(http.expectOne(URL_ROLES), 503);
    tick(500);
    falloConEstado(http.expectOne(URL_ROLES), 503);
    tick(999);
    http.expectNone(URL_ROLES);
    tick(1);
    http.expectOne(URL_ROLES).flush(['ROLE_MANAGER']);

    expect(roles).toEqual(['ROLE_MANAGER']);
    expect(auth.rolesEstanCargados()).toBeTrue();
  }));

  for (const status of [408, 429, 500, 502, 504]) {
    it(`reintenta un ${status}`, fakeAsync(() => {
      let roles: string[] | undefined;
      auth.obtenerRoles().subscribe((r) => (roles = r));
      falloConEstado(http.expectOne(URL_ROLES), status);
      tick(500);
      http.expectOne(URL_ROLES).flush(['ROLE_USER']);
      expect(roles).toEqual(['ROLE_USER']);
    }));
  }

  for (const status of [401, 403, 404]) {
    it(`un ${status} no se reintenta: una sola petición y []`, fakeAsync(() => {
      let roles: string[] | undefined;
      auth.obtenerRoles().subscribe((r) => (roles = r));
      falloConEstado(http.expectOne(URL_ROLES), status);
      tick(10_000);
      http.expectNone(URL_ROLES);

      expect(roles).toEqual([]);
      expect(auth.rolesEstanCargados()).toBeFalse();
    }));
  }

  it('cuatro fallos de red: [] tras cuatro peticiones (3 reintentos: 500 ms, 1 s, 2 s)', fakeAsync(() => {
    let roles: string[] | undefined;
    let actuales: string[] | undefined;
    auth.getRoles().subscribe((r) => (actuales = r));
    auth.obtenerRoles().subscribe((r) => (roles = r));

    falloDeRed(http.expectOne(URL_ROLES));
    tick(500);
    falloDeRed(http.expectOne(URL_ROLES));
    tick(1000);
    falloDeRed(http.expectOne(URL_ROLES));
    tick(1999);
    http.expectNone(URL_ROLES);
    expect(roles).toBeUndefined();
    tick(1);
    falloDeRed(http.expectOne(URL_ROLES));
    tick(10_000);
    http.expectNone(URL_ROLES);

    expect(roles).toEqual([]);
    expect(actuales).toEqual([]);
    expect(auth.rolesEstanCargados()).toBeFalse();
  }));

  it('dos suscriptores a la vez comparten una sola petición en cada intento', fakeAsync(() => {
    let a: string[] | undefined;
    let b: string[] | undefined;
    auth.obtenerRoles().subscribe((r) => (a = r));
    auth.resolverRolesDisponibles().subscribe((r) => (b = r));

    falloDeRed(http.expectOne(URL_ROLES));
    // Un tercero llega durante la espera: se une a la misma petición, no abre otra.
    let c: string[] | undefined;
    auth.obtenerRoles().subscribe((r) => (c = r));
    http.expectNone(URL_ROLES);
    tick(500);
    http.expectOne(URL_ROLES).flush(['ROLE_ADMIN']);

    expect(a).toEqual(['ROLE_ADMIN']);
    expect(b).toEqual(['ROLE_ADMIN']);
    expect(c).toEqual(['ROLE_ADMIN']);

    // Terminada, la siguiente vuelve a pedirlos.
    auth.obtenerRoles().subscribe();
    http.expectOne(URL_ROLES).flush(['ROLE_ADMIN']);
  }));

  it('un 503 que se recupera al reintentar no muestra ningún aviso de error', fakeAsync(() => {
    auth.obtenerRoles().subscribe();
    falloConEstado(http.expectOne(URL_ROLES), 503);
    tick(500);
    http.expectOne(URL_ROLES).flush(['ROLE_ADMIN']);

    expect(Swal.fire).not.toHaveBeenCalled();
  }));

  it('si todos los intentos dan 503, sale un solo aviso (el del último), no uno por intento', fakeAsync(() => {
    auth.obtenerRoles().subscribe();
    falloConEstado(http.expectOne(URL_ROLES), 503);
    tick(500);
    falloConEstado(http.expectOne(URL_ROLES), 503);
    tick(1000);
    falloConEstado(http.expectOne(URL_ROLES), 503);
    tick(2000);
    expect(Swal.fire).not.toHaveBeenCalled();
    falloConEstado(http.expectOne(URL_ROLES), 503);

    expect(Swal.fire).toHaveBeenCalledTimes(1);
    expect(Swal.fire).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Servicio No Disponible' }));
  }));

  it('los fallos de red de /roles no muestran aviso en ningún intento', fakeAsync(() => {
    auth.obtenerRoles().subscribe();
    falloDeRed(http.expectOne(URL_ROLES));
    tick(500);
    falloDeRed(http.expectOne(URL_ROLES));
    tick(1000);
    falloDeRed(http.expectOne(URL_ROLES));
    tick(2000);
    falloDeRed(http.expectOne(URL_ROLES));

    expect(Swal.fire).not.toHaveBeenCalled();
  }));
});
