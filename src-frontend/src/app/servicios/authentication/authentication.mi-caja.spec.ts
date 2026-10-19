import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthenticationService, esSoloProfesor } from './authentication.service';
import { environment } from '../../../environments/environment';

describe('AuthenticationService: entrada de «Mi caja»', () => {
  let auth: AuthenticationService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('taemoi_vista_preferida');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthenticationService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne(`${environment.apiUrl}/auth/auth-status`).flush(false);
  });

  afterEach(() => {
    localStorage.removeItem('taemoi_vista_preferida');
    http.verify();
  });

  it('solo profesor entra en /mi-caja', () => {
    expect(auth.resolverRutaInicioPorRoles(['ROLE_PROFESOR'])).toBe('/mi-caja');
  });

  it('profesor y responsable de sede entra en /mi-caja', () => {
    expect(auth.resolverRutaInicioPorRoles(['ROLE_PROFESOR', 'ROLE_RESPONSABLE_SEDE'])).toBe('/mi-caja');
  });

  it('familia + profesor y gestión + profesor entran como hoy', () => {
    expect(auth.resolverRutaInicioPorRoles(['ROLE_USER', 'ROLE_PROFESOR'])).toBe('/userpage');
    expect(auth.resolverRutaInicioPorRoles(['ROLE_MANAGER', 'ROLE_PROFESOR'])).toBe('/adminpage');
    expect(auth.resolverRutaInicioPorRoles(['ROLE_ADMIN'])).toBe('/adminpage');
    expect(auth.resolverRutaInicioPorRoles(['ROLE_USER'])).toBe('/userpage');
    expect(auth.resolverRutaInicioPorRoles([])).toBe('/');
  });

  it('esSoloProfesor distingue las cuentas mixtas', () => {
    expect(esSoloProfesor(['ROLE_PROFESOR'])).toBeTrue();
    expect(esSoloProfesor(['ROLE_PROFESOR', 'ROLE_RESPONSABLE_SEDE'])).toBeTrue();
    expect(esSoloProfesor(['ROLE_PROFESOR', 'ROLE_USER'])).toBeFalse();
    expect(esSoloProfesor(['ROLE_PROFESOR', 'ROLE_ADMIN'])).toBeFalse();
    expect(esSoloProfesor(['ROLE_RESPONSABLE_SEDE'])).toBeFalse();
  });

  it('las peticiones de roles simultáneas comparten una sola llamada a /roles', () => {
    let a: string[] | undefined;
    let b: string[] | undefined;
    auth.obtenerRoles().subscribe((r) => (a = r));
    auth.resolverRolesDisponibles().subscribe((r) => (b = r));
    http.expectOne(`${environment.apiUrl}/auth/roles`).flush(['ROLE_PROFESOR']);
    expect(a).toEqual(['ROLE_PROFESOR']);
    expect(b).toEqual(['ROLE_PROFESOR']);

    // Terminada, la siguiente vuelve a pedirlos.
    auth.obtenerRoles().subscribe();
    http.expectOne(`${environment.apiUrl}/auth/roles`).flush(['ROLE_PROFESOR']);
  });
});
