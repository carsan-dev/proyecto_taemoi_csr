import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router } from '@angular/router';
import { BehaviorSubject, firstValueFrom, isObservable } from 'rxjs';

import { adminOnlyGuard } from './admin-only.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

describe('adminOnlyGuard', () => {
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
  const authServiceMock = {
    resolverRolesDisponibles: jasmine.createSpy('resolverRolesDisponibles'),
  } as unknown as AuthenticationService;

  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => adminOnlyGuard(...guardParameters));

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

  it('permite acceso a admin', async () => {
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(
      new BehaviorSubject(['ROLE_ADMIN']).asObservable()
    );

    const result = await resolveGuardResult(executeGuard({} as any, {} as any));

    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('deniega acceso a manager', async () => {
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(
      new BehaviorSubject(['ROLE_MANAGER']).asObservable()
    );

    const result = await resolveGuardResult(executeGuard({} as any, {} as any));

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });
});
