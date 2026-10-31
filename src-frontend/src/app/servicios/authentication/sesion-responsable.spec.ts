import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthenticationService, esSoloProfesor } from './authentication.service';
import { Capacidad, PermisosSesionService, pidePermisos } from './permisos-sesion.service';

const PERMISOS = `${environment.apiUrl}/auth/permisos`;

function permisos(capacidades: Capacidad[]) {
  return { roles: ['RESPONSABLE_SEDE'], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false, capacidades };
}

describe('Sesión del responsable de sede (pieza 9-5)', () => {
  let auth: AuthenticationService;
  let servicio: PermisosSesionService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('taemoi_vista_preferida');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthenticationService);
    servicio = TestBed.inject(PermisosSesionService);
    http = TestBed.inject(HttpTestingController);
    http.expectOne(`${environment.apiUrl}/auth/auth-status`).flush(false);
  });

  afterEach(() => {
    localStorage.removeItem('taemoi_vista_preferida');
    http.verify();
  });

  async function destino(roles: string[], capacidades: Capacidad[] | 'fallo' | null): Promise<string> {
    const ruta = firstValueFrom(auth.resolverRutaInicio(roles));
    if (capacidades === 'fallo') {
      http.expectOne(PERMISOS).flush('error', { status: 500, statusText: 'Error' });
    } else if (capacidades) {
      http.expectOne(PERMISOS).flush(permisos(capacidades));
    }
    return ruta;
  }

  it('R + Pr ya no es solo profesor; el resto de cuentas, igual', () => {
    expect(esSoloProfesor(['ROLE_RESPONSABLE_SEDE', 'ROLE_PROFESOR'])).toBeFalse();
    expect(esSoloProfesor(['ROLE_PROFESOR'])).toBeTrue();
    expect(esSoloProfesor(['ROLE_PROFESOR', 'ROLE_USER'])).toBeFalse();
  });

  it('R pide los permisos, solo o con familia', () => {
    expect(pidePermisos(['ROLE_RESPONSABLE_SEDE'])).toBeTrue();
    expect(pidePermisos(['ROLE_USER', 'ROLE_RESPONSABLE_SEDE'])).toBeTrue();
    expect(pidePermisos(['ROLE_USER'])).toBeFalse();
  });

  it('R solo aterriza en el panel', async () => {
    expect(await destino(['ROLE_RESPONSABLE_SEDE'], ['PANEL_SEDE'])).toBe('/adminpage');
  });

  it('R + Pr aterriza en el panel, no en Mis grupos (371)', async () => {
    expect(await destino(['ROLE_RESPONSABLE_SEDE', 'ROLE_PROFESOR'], ['MIS_GRUPOS', 'MI_CAJA', 'PANEL_SEDE'])).toBe('/adminpage');
  });

  it('R + F elige vista como gestión + familia: el panel, salvo que prefiera la de familia (371)', async () => {
    const capacidades: Capacidad[] = ['PANEL_SEDE', 'ELEGIR_VISTA'];
    expect(await destino(['ROLE_RESPONSABLE_SEDE', 'ROLE_USER'], capacidades)).toBe('/adminpage');
    auth.guardarVistaPreferida('user');
    expect(await destino(['ROLE_RESPONSABLE_SEDE', 'ROLE_USER'], null)).toBe('/userpage');
  });

  it('sin permisos legibles, falla cerrado: R solo no entra en el panel', async () => {
    expect(await destino(['ROLE_RESPONSABLE_SEDE'], 'fallo')).toBe('/');
  });

  it('gestión, profesor y familia no piden permisos para resolver el destino', async () => {
    expect(await destino(['ROLE_ADMIN'], null)).toBe('/adminpage');
    expect(await destino(['ROLE_MANAGER', 'ROLE_USER'], null)).toBe('/adminpage');
    expect(await destino(['ROLE_PROFESOR'], null)).toBe('/mis-grupos');
    expect(await destino(['ROLE_USER'], null)).toBe('/userpage');
    expect(await destino([], null)).toBe('/');
  });

  it('R + G es gestión aunque el servidor no le dé PANEL_SEDE', async () => {
    expect(await destino(['ROLE_RESPONSABLE_SEDE', 'ROLE_ADMIN'], [])).toBe('/adminpage');
  });

  it('tieneAccesoDual suma ELEGIR_VISTA y deja igual a gestión + familia', () => {
    expect(auth.tieneAccesoDual(['ROLE_RESPONSABLE_SEDE', 'ROLE_USER'], ['PANEL_SEDE', 'ELEGIR_VISTA'])).toBeTrue();
    expect(auth.tieneAccesoDual(['ROLE_RESPONSABLE_SEDE'], ['PANEL_SEDE'])).toBeFalse();
    expect(auth.tieneAccesoDual(['ROLE_ADMIN', 'ROLE_USER'])).toBeTrue();
    expect(auth.tieneAccesoDual(['ROLE_USER'])).toBeFalse();
  });

  describe('tiene()', () => {
    it('da la capacidad en cuanto llegan los permisos; el cierre de sesión la borra', () => {
      expect(servicio.tiene('PANEL_SEDE')).toBeFalse();
      servicio.obtener().subscribe();
      http.expectOne(PERMISOS).flush(permisos(['PANEL_SEDE']));
      expect(servicio.tiene('PANEL_SEDE')).toBeTrue();
      servicio.reiniciar();
      expect(servicio.tiene('PANEL_SEDE')).toBeFalse();
    });

    it('«refrescar» (Mi perfil) la conserva mientras llega la respuesta nueva', () => {
      servicio.obtener().subscribe();
      http.expectOne(PERMISOS).flush(permisos(['PANEL_SEDE']));
      servicio.refrescar();
      expect(servicio.tiene('PANEL_SEDE')).toBeTrue();
    });

    it('una respuesta de la sesión anterior no pisa la capacidad', () => {
      servicio.obtener().subscribe();
      const anterior = http.expectOne(PERMISOS);
      servicio.reiniciar();
      anterior.flush(permisos(['PANEL_SEDE']));
      expect(servicio.tiene('PANEL_SEDE')).toBeFalse();
    });
  });
});
