import { TestBed } from '@angular/core/testing';
import { CanActivateFn, Router } from '@angular/router';
import { BehaviorSubject, firstValueFrom, isObservable } from 'rxjs';

import { adminOnlyGuard } from './admin-only.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';

describe('adminOnlyGuard', () => {
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
  const rolesSubject = new BehaviorSubject<string[]>([]);
  const authServiceMock = {
    rolesCambio: rolesSubject.asObservable(),
    rolesEstanCargados: jasmine.createSpy('rolesEstanCargados'),
    obtenerRoles: jasmine.createSpy('obtenerRoles'),
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
    rolesSubject.next([]);
    routerSpy.navigate.calls.reset();
    (authServiceMock.rolesEstanCargados as jasmine.Spy).calls.reset();
    (authServiceMock.obtenerRoles as jasmine.Spy).calls.reset();

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthenticationService, useValue: authServiceMock },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('permite acceso a admin', async () => {
    (authServiceMock.rolesEstanCargados as jasmine.Spy).and.returnValue(true);
    rolesSubject.next(['ROLE_ADMIN']);

    const result = await resolveGuardResult(executeGuard({} as any, {} as any));

    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('deniega acceso a manager', async () => {
    (authServiceMock.rolesEstanCargados as jasmine.Spy).and.returnValue(false);
    (authServiceMock.obtenerRoles as jasmine.Spy).and.returnValue(new BehaviorSubject(['ROLE_MANAGER']).asObservable());

    const result = await resolveGuardResult(executeGuard({} as any, {} as any));

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });
});
