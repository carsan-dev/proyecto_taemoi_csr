import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { AuthenticationService } from './authentication.service';
import { PermisosSesion, PermisosSesionService, pidePermisos } from './permisos-sesion.service';
import { environment } from '../../../environments/environment';

const URL_PERMISOS = `${environment.apiUrl}/auth/permisos`;

function permisos(capacidades: PermisosSesion['capacidades']): PermisosSesion {
  return { roles: ['PROFESOR'], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: true, capacidades };
}

describe('PermisosSesionService', () => {
  let servicio: PermisosSesionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(PermisosSesionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide los permisos una sola vez aunque lo pidan varios a la vez', () => {
    const recibidos: PermisosSesion[] = [];
    servicio.obtener().subscribe((p) => recibidos.push(p));
    servicio.obtener().subscribe((p) => recibidos.push(p));
    servicio.capacidades().subscribe();

    http.expectOne(URL_PERMISOS).flush(permisos(['MIS_GRUPOS']));
    expect(recibidos.length).toBe(2);
  });

  it('las llamadas posteriores salen de la caché, sin petición', async () => {
    const primera = firstValueFrom(servicio.obtener());
    http.expectOne(URL_PERMISOS).flush(permisos(['MI_CAJA']));
    await primera;

    expect(await firstValueFrom(servicio.capacidades())).toEqual(['MI_CAJA']);
    http.expectNone(URL_PERMISOS);
  });

  it('reiniciar() descarta la caché y la siguiente llamada vuelve a pedir', async () => {
    const primera = firstValueFrom(servicio.obtener());
    http.expectOne(URL_PERMISOS).flush(permisos([]));
    await primera;

    servicio.reiniciar();
    const segunda = firstValueFrom(servicio.capacidades());
    http.expectOne(URL_PERMISOS).flush(permisos(['MIS_GRUPOS']));
    expect(await segunda).toEqual(['MIS_GRUPOS']);
  });

  it('un error no se guarda: capacidades() da [] y la siguiente llamada vuelve a pedir', async () => {
    const fallida = firstValueFrom(servicio.capacidades());
    http.expectOne(URL_PERMISOS).flush('error', { status: 500, statusText: 'Error' });
    expect(await fallida).toEqual([]);

    const otra = firstValueFrom(servicio.capacidades());
    http.expectOne(URL_PERMISOS).flush(permisos(['MIS_GRUPOS']));
    expect(await otra).toEqual(['MIS_GRUPOS']);
  });

  it('obtener() propaga el error para quien necesita distinguirlo', (hecho) => {
    servicio.obtener().subscribe({ error: (e) => { expect(e.status).toBe(500); hecho(); } });
    http.expectOne(URL_PERMISOS).flush('error', { status: 500, statusText: 'Error' });
  });

  it('un 401 o 403 tampoco rompe: sin capacidades', async () => {
    const resultado = firstValueFrom(servicio.capacidades());
    http.expectOne(URL_PERMISOS).flush('no', { status: 403, statusText: 'Forbidden' });
    expect(await resultado).toEqual([]);
  });
});

describe('pidePermisos: quién pide GET /api/auth/permisos (8b-I)', () => {
  it('con ROLE_PROFESOR, como hasta ahora', () => {
    expect(pidePermisos(['ROLE_PROFESOR'])).toBeTrue();
    expect(pidePermisos(['ROLE_USER', 'ROLE_PROFESOR'])).toBeTrue();
  });

  it('gestión también pide: ROLE_ADMIN o ROLE_MANAGER', () => {
    expect(pidePermisos(['ROLE_ADMIN'])).toBeTrue();
    expect(pidePermisos(['ROLE_MANAGER'])).toBeTrue();
    expect(pidePermisos(['ROLE_MANAGER', 'ROLE_USER'])).toBeTrue();
  });

  it('la familia, el responsable de sede solo o sin roles no piden', () => {
    expect(pidePermisos(['ROLE_USER'])).toBeFalse();
    expect(pidePermisos(['ROLE_USER', 'ROLE_RESPONSABLE_SEDE'])).toBeFalse();
    expect(pidePermisos([])).toBeFalse();
  });
});

describe('PermisosSesionService con AuthenticationService: cuándo se refresca', () => {
  let auth: AuthenticationService;
  let servicio: PermisosSesionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthenticationService);
    servicio = TestBed.inject(PermisosSesionService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne(`${environment.apiUrl}/auth/auth-status`).flush(false);
  });

  afterEach(() => http.verify());

  async function conPermisosEnCache(): Promise<void> {
    const p = firstValueFrom(servicio.obtener());
    http.expectOne(URL_PERMISOS).flush(permisos(['MIS_GRUPOS']));
    await p;
  }

  it('login() descarta los permisos del usuario anterior', async () => {
    await conPermisosEnCache();
    auth.login({ email: 'cuenta@ejemplo.test', contrasena: 'x', rememberMe: false }).subscribe({ error: () => undefined });
    http.expectOne(`${environment.apiUrl}/auth/signin`).flush('no', { status: 401, statusText: 'No' });

    servicio.capacidades().subscribe();
    http.expectOne(URL_PERMISOS).flush(permisos([]));
  });

  it('el cierre de sesión descarta los permisos', async () => {
    await conPermisosEnCache();
    auth.limpiarEstadoLocal();

    servicio.capacidades().subscribe();
    http.expectOne(URL_PERMISOS).flush(permisos([]));
  });
});
