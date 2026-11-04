import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, isObservable } from 'rxjs';

import { entradaGoogleGuard } from './entrada-google.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { Capacidad } from '../servicios/authentication/permisos-sesion.service';
import { environment } from '../../environments/environment';

const ROLES = `${environment.apiUrl}/auth/roles`;
const PERMISOS = `${environment.apiUrl}/auth/permisos`;

/**
 * Decisión 384: tras entrar con Google, el servidor manda a `/login/google` a las sesiones cuyo destino depende de la
 * vista preferida o de las capacidades. El guard les da el mismo destino que el login con contraseña
 * (`resolverRutaInicio`), con el servicio real.
 */
describe('entradaGoogleGuard (decisión 384)', () => {
  let http: HttpTestingController;
  let router: Router;
  let auth: AuthenticationService;

  beforeEach(() => {
    localStorage.removeItem('taemoi_vista_preferida');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    auth = TestBed.inject(AuthenticationService);
    http.expectOne(`${environment.apiUrl}/auth/auth-status`).flush(false);
  });

  afterEach(() => {
    localStorage.removeItem('taemoi_vista_preferida');
    http.verify();
  });

  async function destino(
    roles: string[] | 'fallo',
    capacidades: Capacidad[] | null = null,
    queryParams: Record<string, string> = {}
  ): Promise<string> {
    const resultado = TestBed.runInInjectionContext(() =>
      entradaGoogleGuard(
        { queryParams } as unknown as ActivatedRouteSnapshot,
        { url: '/login/google' } as RouterStateSnapshot
      )
    );
    expect(isObservable(resultado)).toBeTrue();
    const valor = firstValueFrom(resultado as Observable<unknown>);
    if (roles === 'fallo') {
      http.expectOne(ROLES).flush('sin sesión', { status: 401, statusText: 'Unauthorized' });
    } else {
      http.expectOne(ROLES).flush(roles);
    }
    if (capacidades) {
      http.expectOne(PERMISOS).flush({
        roles: [],
        esAdminPlataforma: false,
        sedes: [],
        variasSedes: false,
        tieneGrupos: false,
        capacidades,
      });
    }
    const arbol = (await valor) as UrlTree;
    expect(arbol instanceof UrlTree).toBeTrue();
    return router.serializeUrl(arbol);
  }

  it('responsable + familia elige vista como con contraseña: sin preferencia, el panel', async () => {
    expect(await destino(['ROLE_RESPONSABLE_SEDE', 'ROLE_USER'], ['PANEL_SEDE', 'ELEGIR_VISTA'])).toBe('/adminpage');
  });

  it('responsable + familia con la vista de familia preferida va a /userpage', async () => {
    auth.guardarVistaPreferida('user');
    expect(await destino(['ROLE_RESPONSABLE_SEDE', 'ROLE_USER'], ['PANEL_SEDE', 'ELEGIR_VISTA'])).toBe('/userpage');
  });

  it('gestión + familia sin preferencia va al panel', async () => {
    expect(await destino(['ROLE_ADMIN', 'ROLE_USER'])).toBe('/adminpage');
  });

  it('gestión + familia con la vista de familia preferida va a /userpage', async () => {
    auth.guardarVistaPreferida('user');
    expect(await destino(['ROLE_MANAGER', 'ROLE_USER'])).toBe('/userpage');
  });

  it('responsable solo va a su panel', async () => {
    expect(await destino(['ROLE_RESPONSABLE_SEDE'], ['PANEL_SEDE'])).toBe('/adminpage');
  });

  it('responsable sin PANEL_SEDE cae a la portada, como con contraseña', async () => {
    expect(await destino(['ROLE_RESPONSABLE_SEDE'], [])).toBe('/');
  });

  it('profesor + familia con grupos elige vista como con contraseña: sin preferencia, Mis grupos (439)', async () => {
    expect(await destino(['ROLE_PROFESOR', 'ROLE_USER'], ['MIS_GRUPOS', 'MI_CAJA', 'ELEGIR_VISTA'])).toBe('/mis-grupos');
  });

  it('profesor + familia con la vista de familia preferida va a /userpage (439)', async () => {
    auth.guardarVistaPreferida('user');
    expect(await destino(['ROLE_PROFESOR', 'ROLE_USER'], ['MIS_GRUPOS', 'MI_CAJA', 'ELEGIR_VISTA'])).toBe('/userpage');
  });

  it('profesor + familia sin grupos entra como familia (439)', async () => {
    expect(await destino(['ROLE_PROFESOR', 'ROLE_USER'], ['MI_CAJA'])).toBe('/userpage');
  });

  it('solo profesor va a Mis grupos', async () => {
    expect(await destino(['ROLE_PROFESOR'])).toBe('/mis-grupos');
  });

  it('plataforma sin roles de escuela va a la portada', async () => {
    expect(await destino([])).toBe('/');
  });

  it('si no se pueden leer los roles, a la portada (falla cerrado)', async () => {
    expect(await destino('fallo')).toBe('/');
  });

  it('no sigue ningún destino de la URL: siempre una ruta propia', async () => {
    expect(await destino(['ROLE_USER'], null, { redirect: 'https://ajeno.example', next: '/adminpage' })).toBe(
      '/userpage'
    );
  });
});
