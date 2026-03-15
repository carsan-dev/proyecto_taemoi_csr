import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CanActivateFn } from '@angular/router';
import { BehaviorSubject, firstValueFrom, isObservable } from 'rxjs';

import { roleGuard } from './role.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

describe('roleGuard', () => {
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
  const authServiceMock = {
    resolverRolesDisponibles: jasmine.createSpy('resolverRolesDisponibles'),
  } as unknown as AuthenticationService;

  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => roleGuard(...guardParameters));

  async function resolveGuardResult(result: ReturnType<CanActivateFn>): Promise<unknown> {
    if (isObservable(result)) {
      return firstValueFrom(result);
    }

    return Promise.resolve(result);
  }

  beforeEach(() => {
    routerSpy.navigate.calls.reset();
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).calls.reset();

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthenticationService, useValue: authServiceMock },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('permite acceso de manager a rutas de gestión', async () => {
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(
      new BehaviorSubject(['ROLE_MANAGER']).asObservable()
    );

    const result = await resolveGuardResult(
      executeGuard({ data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER'] } } as any, { url: '/alumnosListar' } as any)
    );

    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('deniega acceso de user a rutas de gestión', async () => {
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(
      new BehaviorSubject(['ROLE_USER']).asObservable()
    );

    const result = await resolveGuardResult(
      executeGuard({ data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER'] } } as any, { url: '/alumnosListar' } as any)
    );

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('permite acceso de user al portal de usuario', async () => {
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(
      new BehaviorSubject(['ROLE_USER']).asObservable()
    );

    const result = await resolveGuardResult(
      executeGuard({ data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER'] } } as any, { url: '/userpage' } as any)
    );

    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('deniega acceso si la ruta no declara requiredRoles', async () => {
    const result = await resolveGuardResult(executeGuard({ data: {} } as any, { url: '/alumnosListar' } as any));

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
    expect(authServiceMock.resolverRolesDisponibles).not.toHaveBeenCalled();
  });
});
