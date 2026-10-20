import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { AuthenticationService } from './authentication.service';
import { PermisosSesionService } from './permisos-sesion.service';

describe('AuthenticationService · invitación', () => {
  let servicio: AuthenticationService;
  let http: HttpTestingController;
  const api = `${environment.apiUrl}/auth`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(AuthenticationService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne(`${api}/auth-status`).flush(false);
  });

  afterEach(() => http.verify());

  it('datosInvitacion manda el token en el cuerpo, con el error tratado en la página', () => {
    let recibido: unknown;
    servicio.datosInvitacion('tok-1').subscribe((d) => (recibido = d));
    const req = http.expectOne(`${api}/invitacion/datos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'tok-1' });
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush({ email: 'a@ejemplo.test', nombre: 'A', escuela: 'E' });
    expect(recibido).toEqual({ email: 'a@ejemplo.test', nombre: 'A', escuela: 'E' });
  });

  it('aceptarInvitacion abre la sesión como el login: reinicia permisos y pide los roles nuevos', () => {
    const reiniciar = spyOn(TestBed.inject(PermisosSesionService), 'reiniciar').and.callThrough();
    let roles: string[] | undefined;
    servicio.aceptarInvitacion('tok-1', 'Segura123', 'lucia@ejemplo.test').subscribe((r) => (roles = r));
    expect(reiniciar).toHaveBeenCalled();
    const req = http.expectOne(`${api}/invitacion/aceptar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({ token: 'tok-1', nuevaContrasena: 'Segura123' });
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush({ token: 'jwt' });
    http.expectOne(`${api}/roles`).flush(['ROLE_PROFESOR']);
    expect(roles).toEqual(['ROLE_PROFESOR']);
    expect(servicio.comprobarLogueado()).toBeTrue();
    expect(servicio.getRolesActuales()).toEqual(['ROLE_PROFESOR']);
  });
});
