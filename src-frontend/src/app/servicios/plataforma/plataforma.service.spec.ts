import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { PlataformaService } from './plataforma.service';
import { AuthenticationService } from '../authentication/authentication.service';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';

describe('PlataformaService', () => {
  let service: PlataformaService;
  let auth: AuthenticationService;
  let http: HttpTestingController;

  const esSondeo = (url: string) => url.endsWith('/plataforma/errores');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlataformaService);
    auth = TestBed.inject(AuthenticationService);
    http = TestBed.inject(HttpTestingController);
    // AuthenticationService comprueba la sesión al crearse: sin sesión para estas pruebas.
    http.expectOne((r) => r.url.endsWith('/auth/auth-status')).flush(false);
  });

  afterEach(() => http.verify());

  it('el sondeo devuelve true cuando la API responde 200', async () => {
    const resultado = firstValueFrom(service.esAdminPlataforma());

    const req = http.expectOne((r) => esSondeo(r.url));
    expect(req.request.params.get('size')).toBe('1');
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 1, number: 0 });

    expect(await resultado).toBeTrue();
  });

  it('el sondeo devuelve false con 403 y con 401', async () => {
    const r403 = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush(null, { status: 403, statusText: 'Forbidden' });
    expect(await r403).toBeFalse();

    auth.actualizarEstadoLogueado(true);
    const r401 = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(await r401).toBeFalse();
  });

  it('guarda el resultado en caché durante la sesión', async () => {
    const primero = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush({ content: [] });
    expect(await primero).toBeTrue();

    expect(await firstValueFrom(service.esAdminPlataforma())).toBeTrue();
    http.expectNone((r) => esSondeo(r.url));
  });

  it('vuelve a sondear tras cambiar la sesión (login o logout)', async () => {
    auth.actualizarEstadoLogueado(true);
    const primero = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush({ content: [] });
    await primero;

    auth.limpiarEstadoLocal();

    const segundo = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush(null, { status: 403, statusText: 'Forbidden' });
    expect(await segundo).toBeFalse();
  });

  it('un login nuevo (sin logout previo) vuelve a sondear', async () => {
    auth.actualizarEstadoLogueado(true);
    const primero = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush({ content: [] });
    expect(await primero).toBeTrue();

    // Otro usuario inicia sesión mientras el estado sigue "logueado": el login falla aquí, da igual.
    auth.login({ email: 'otra@test.com', password: 'x' } as any).subscribe({ error: () => undefined });
    http.expectOne((r) => r.url.endsWith('/auth/signin')).flush(null, { status: 401, statusText: 'Unauthorized' });

    const segundo = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush(null, { status: 403, statusText: 'Forbidden' });
    expect(await segundo).toBeFalse();
  });

  it('no guarda en caché un fallo que no sea de permisos', async () => {
    const primero = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush(null, { status: 500, statusText: 'Error' });
    expect(await primero).toBeFalse();

    const segundo = firstValueFrom(service.esAdminPlataforma());
    http.expectOne((r) => esSondeo(r.url)).flush({ content: [] });
    expect(await segundo).toBeTrue();
  });

  it('errores() envía solo los filtros informados', () => {
    service.errores({ organizacionId: 3, estado: '5xx', desde: '2026-10-10', hasta: '2026-10-16', page: 2, size: 25 }).subscribe();

    const req = http.expectOne((r) => esSondeo(r.url));
    expect(req.request.params.get('organizacionId')).toBe('3');
    expect(req.request.params.get('estado')).toBe('5xx');
    expect(req.request.params.get('desde')).toBe('2026-10-10');
    expect(req.request.params.get('hasta')).toBe('2026-10-16');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    req.flush({ content: [] });

    service.errores({}).subscribe();
    const vacia = http.expectOne((r) => esSondeo(r.url));
    expect(vacia.request.params.keys()).toEqual(['page', 'size']);
    vacia.flush({ content: [] });
  });
});
