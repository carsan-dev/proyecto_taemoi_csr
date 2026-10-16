import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom, isObservable, of } from 'rxjs';

import { plataformaGuard } from './plataforma.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

describe('plataformaGuard', () => {
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
  const authMock = {
    esAdminPlataforma: jasmine.createSpy('esAdminPlataforma'),
    resolverRolesDisponibles: jasmine.createSpy('resolverRolesDisponibles'),
  };

  const executeGuard: CanActivateFn = (...params) =>
    TestBed.runInInjectionContext(() => plataformaGuard(...params));

  async function resolver(result: ReturnType<CanActivateFn>): Promise<unknown> {
    return isObservable(result) ? firstValueFrom(result) : Promise.resolve(result);
  }

  beforeEach(() => {
    routerSpy.navigate.calls.reset();
    authMock.esAdminPlataforma.calls.reset();
    authMock.resolverRolesDisponibles.calls.reset();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthenticationService, useValue: authMock },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('permite el acceso al administrador de plataforma', async () => {
    authMock.esAdminPlataforma.and.returnValue(of(true));

    expect(await resolver(executeGuard({} as any, {} as any))).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('devuelve a la raíz a quien no es administrador de plataforma', async () => {
    authMock.esAdminPlataforma.and.returnValue(of(false));

    expect(await resolver(executeGuard({} as any, {} as any))).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('no depende de /api/auth/roles', async () => {
    authMock.esAdminPlataforma.and.returnValue(of(true));

    await resolver(executeGuard({} as any, {} as any));

    expect(authMock.resolverRolesDisponibles).not.toHaveBeenCalled();
  });
});
