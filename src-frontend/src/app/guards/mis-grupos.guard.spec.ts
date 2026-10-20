import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { misGruposGuard } from './mis-grupos.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { PermisosSesion, PermisosSesionService } from '../servicios/authentication/permisos-sesion.service';

/** E8-5: «Mis grupos» se decide por la capacidad MIS_GRUPOS (decisión 217); sin ella, la gestión va a su panel. */
describe('misGruposGuard', () => {
  const auth = { resolverRolesDisponibles: jasmine.createSpy('resolverRolesDisponibles') };
  const permisos = { obtener: jasmine.createSpy('obtener') };

  const ejecutar: CanActivateFn = (...parametros) => TestBed.runInInjectionContext(() => misGruposGuard(...parametros));

  async function resultado(): Promise<unknown> {
    const r = ejecutar({} as any, { url: '/mis-grupos' } as any);
    return isObservable(r) ? firstValueFrom(r) : r;
  }

  function url(r: unknown): string {
    expect(r instanceof UrlTree).toBeTrue();
    return TestBed.inject(Router).serializeUrl(r as UrlTree);
  }

  function conPermisos(capacidades: PermisosSesion['capacidades'], roles: string[]): void {
    permisos.obtener.and.returnValue(
      of({ roles: [], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false, capacidades })
    );
    auth.resolverRolesDisponibles.and.returnValue(of(roles));
  }

  beforeEach(() => {
    auth.resolverRolesDisponibles.calls.reset();
    permisos.obtener.calls.reset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthenticationService, useValue: auth },
        { provide: PermisosSesionService, useValue: permisos },
      ],
    });
  });

  for (const roles of [
    ['ROLE_PROFESOR'],
    ['ROLE_PROFESOR', 'ROLE_RESPONSABLE_SEDE'],
    ['ROLE_USER', 'ROLE_PROFESOR'],
    ['ROLE_ADMIN', 'ROLE_PROFESOR'],
    ['ROLE_MANAGER', 'ROLE_USER', 'ROLE_PROFESOR'],
  ]) {
    it(`deja pasar a ${roles.join(' + ')} con MIS_GRUPOS`, async () => {
      conPermisos(['MI_CAJA', 'MIS_GRUPOS'], roles);
      expect(await resultado()).toBeTrue();
    });
  }

  for (const roles of [
    ['ROLE_ADMIN', 'ROLE_PROFESOR'],
    ['ROLE_MANAGER', 'ROLE_PROFESOR'],
    ['ROLE_USER', 'ROLE_MANAGER', 'ROLE_PROFESOR'],
    ['ROLE_ADMIN'],
  ]) {
    it(`manda al panel de gestión a ${roles.join(' + ')} sin MIS_GRUPOS`, async () => {
      conPermisos(['MI_CAJA'], roles);
      expect(url(await resultado())).toBe('/adminpage');
    });
  }

  it('manda a la raíz a la familia sin MIS_GRUPOS', async () => {
    conPermisos([], ['ROLE_USER']);
    expect(url(await resultado())).toBe('/');
  });

  it('manda a la raíz a un profesor sin MIS_GRUPOS que no es gestión', async () => {
    conPermisos(['MI_CAJA'], ['ROLE_USER', 'ROLE_PROFESOR']);
    expect(url(await resultado())).toBe('/');
  });

  it('manda a la raíz si no se pueden leer los permisos', async () => {
    permisos.obtener.and.returnValue(throwError(() => new Error('sin red')));
    auth.resolverRolesDisponibles.and.returnValue(of(['ROLE_ADMIN', 'ROLE_PROFESOR']));
    expect(url(await resultado())).toBe('/');
  });

  it('manda a la raíz si fallan los roles tras unos permisos sin MIS_GRUPOS', async () => {
    permisos.obtener.and.returnValue(of({ capacidades: [] }));
    auth.resolverRolesDisponibles.and.returnValue(throwError(() => new Error('sin red')));
    expect(url(await resultado())).toBe('/');
  });
});
