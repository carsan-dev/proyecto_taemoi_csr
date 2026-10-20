import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { AuthenticationService } from './authentication.service';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

describe('AuthenticationService: ¿administrador de plataforma?', () => {
  const url = `${environment.apiUrl}/auth/plataforma`;
  let auth: AuthenticationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthenticationService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne(`${environment.apiUrl}/auth/auth-status`).flush(false);
  });

  // verify() también asegura que nadie sondea /plataforma/errores.
  afterEach(() => http.verify());

  it('pregunta a /auth/plataforma, sin spinner y tratando el error aquí', async () => {
    const resultado = firstValueFrom(auth.esAdminPlataforma());

    const req = http.expectOne(url);
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('X-Skip-Loading')).toBe('true');
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush({ adminPlataforma: true });

    expect(await resultado).toBeTrue();
  });

  it('false cuando la sesión no es de plataforma', async () => {
    const resultado = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush({ adminPlataforma: false });
    expect(await resultado).toBeFalse();
  });

  it('una sola consulta por sesión', async () => {
    const primero = firstValueFrom(auth.esAdminPlataforma());
    const enParalelo = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush({ adminPlataforma: true });
    expect(await primero).toBeTrue();
    expect(await enParalelo).toBeTrue();

    expect(await firstValueFrom(auth.esAdminPlataforma())).toBeTrue();
    http.expectNone(url);
  });

  it('un 401 da false y no se repite', async () => {
    const primero = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(await primero).toBeFalse();

    expect(await firstValueFrom(auth.esAdminPlataforma())).toBeFalse();
    http.expectNone(url);
  });

  it('un 5xx da false y se reintenta en la siguiente llamada', async () => {
    const primero = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush(null, { status: 503, statusText: 'Service Unavailable' });
    expect(await primero).toBeFalse();

    const segundo = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush({ adminPlataforma: true });
    expect(await segundo).toBeTrue();
  });

  it('un fallo de red da false y se reintenta en la siguiente llamada', async () => {
    const primero = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).error(new ProgressEvent('error'));
    expect(await primero).toBeFalse();

    const segundo = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush({ adminPlataforma: true });
    expect(await segundo).toBeTrue();
  });

  it('vuelve a preguntar tras cerrar sesión', async () => {
    auth.actualizarEstadoLogueado(true);
    const primero = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush({ adminPlataforma: true });
    expect(await primero).toBeTrue();

    auth.limpiarEstadoLocal();

    const segundo = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(await segundo).toBeFalse();
  });

  it('un login nuevo (sin logout previo) vuelve a preguntar', async () => {
    auth.actualizarEstadoLogueado(true);
    const primero = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush({ adminPlataforma: true });
    expect(await primero).toBeTrue();

    // Otro usuario inicia sesión mientras el estado sigue "logueado": el login falla aquí, da igual.
    auth.login({ email: 'otra@test.com', password: 'x' } as any).subscribe({ error: () => undefined });
    http.expectOne(`${environment.apiUrl}/auth/signin`).flush(null, { status: 401, statusText: 'Unauthorized' });

    const segundo = firstValueFrom(auth.esAdminPlataforma());
    http.expectOne(url).flush({ adminPlataforma: false });
    expect(await segundo).toBeFalse();
  });
});
