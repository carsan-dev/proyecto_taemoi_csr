import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router, UrlTree, provideRouter } from '@angular/router';
import { firstValueFrom, isObservable, of, throwError } from 'rxjs';

import { profesorGuard } from './profesor.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

describe('profesorGuard', () => {
  const auth = { resolverRolesDisponibles: jasmine.createSpy('resolverRolesDisponibles') };

  const ejecutar: CanActivateFn = (...parametros) => TestBed.runInInjectionContext(() => profesorGuard(...parametros));

  async function resultado(): Promise<unknown> {
    const r = ejecutar({} as any, { url: '/mi-caja' } as any);
    return isObservable(r) ? firstValueFrom(r) : r;
  }

  beforeEach(() => {
    auth.resolverRolesDisponibles.calls.reset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthenticationService, useValue: auth }],
    });
  });

  const conAcceso = [
    ['ROLE_PROFESOR'],
    ['ROLE_PROFESOR', 'ROLE_RESPONSABLE_SEDE'],
    ['ROLE_USER', 'ROLE_PROFESOR'],
    ['ROLE_MANAGER', 'ROLE_PROFESOR'],
  ];
  for (const roles of conAcceso) {
    it(`deja pasar a ${roles.join(' + ')}`, async () => {
      auth.resolverRolesDisponibles.and.returnValue(of(roles));
      expect(await resultado()).toBeTrue();
    });
  }

  it('manda a la raíz a quien no es profesor', async () => {
    auth.resolverRolesDisponibles.and.returnValue(of(['ROLE_USER']));
    const r = await resultado();
    expect(r instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(r as UrlTree)).toBe('/');
  });

  it('manda a la raíz si no se pueden leer los roles', async () => {
    auth.resolverRolesDisponibles.and.returnValue(throwError(() => new Error('sin red')));
    expect((await resultado()) instanceof UrlTree).toBeTrue();
  });
});
