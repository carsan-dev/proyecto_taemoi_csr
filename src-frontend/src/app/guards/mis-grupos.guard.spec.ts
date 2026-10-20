import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { misGruposGuard } from './mis-grupos.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

describe('misGruposGuard', () => {
  const auth = { resolverRolesDisponibles: jasmine.createSpy('resolverRolesDisponibles') };

  const ejecutar: CanActivateFn = (...parametros) => TestBed.runInInjectionContext(() => misGruposGuard(...parametros));

  async function resultado(): Promise<unknown> {
    const r = ejecutar({} as any, { url: '/mis-grupos' } as any);
    return isObservable(r) ? firstValueFrom(r) : r;
  }

  function url(r: unknown): string {
    expect(r instanceof UrlTree).toBeTrue();
    return TestBed.inject(Router).serializeUrl(r as UrlTree);
  }

  beforeEach(() => {
    auth.resolverRolesDisponibles.calls.reset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthenticationService, useValue: auth }],
    });
  });

  for (const roles of [['ROLE_PROFESOR'], ['ROLE_PROFESOR', 'ROLE_RESPONSABLE_SEDE'], ['ROLE_USER', 'ROLE_PROFESOR']]) {
    it(`deja pasar a ${roles.join(' + ')}`, async () => {
      auth.resolverRolesDisponibles.and.returnValue(of(roles));
      expect(await resultado()).toBeTrue();
    });
  }

  for (const roles of [
    ['ROLE_ADMIN', 'ROLE_PROFESOR'],
    ['ROLE_MANAGER', 'ROLE_PROFESOR'],
    ['ROLE_USER', 'ROLE_MANAGER', 'ROLE_PROFESOR'],
  ]) {
    it(`manda al panel de gestión a ${roles.join(' + ')}`, async () => {
      auth.resolverRolesDisponibles.and.returnValue(of(roles));
      expect(url(await resultado())).toBe('/adminpage');
    });
  }

  it('manda a la raíz a quien no es profesor', async () => {
    auth.resolverRolesDisponibles.and.returnValue(of(['ROLE_USER']));
    expect(url(await resultado())).toBe('/');
  });

  it('manda a la raíz a un gestor que no es profesor', async () => {
    auth.resolverRolesDisponibles.and.returnValue(of(['ROLE_ADMIN']));
    expect(url(await resultado())).toBe('/');
  });

  it('manda a la raíz si no se pueden leer los roles', async () => {
    auth.resolverRolesDisponibles.and.returnValue(throwError(() => new Error('sin red')));
    expect(url(await resultado())).toBe('/');
  });
});
