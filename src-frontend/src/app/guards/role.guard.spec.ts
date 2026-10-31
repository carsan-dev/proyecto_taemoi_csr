import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CanActivateFn } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, Observable, firstValueFrom, isObservable, of } from 'rxjs';

import { roleGuard } from './role.guard';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { environment } from '../../environments/environment';

describe('roleGuard', () => {
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
  const authServiceMock = {
    resolverRolesDisponibles: jasmine.createSpy('resolverRolesDisponibles'),
    capacidadesDeResponsable: jasmine.createSpy('capacidadesDeResponsable'),
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
    (authServiceMock.capacidadesDeResponsable as jasmine.Spy).and.returnValue(of([]));

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

  describe('responsable de sede (decisiones 370 y 371)', () => {
    const PANEL_SEDE = { data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_RESPONSABLE_SEDE'] } } as any;
    const GESTION = { data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER'] } } as any;
    const FAMILIA = { data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER'] } } as any;

    function sesion(roles: string[], capacidades: string[]): void {
      (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(of(roles));
      (authServiceMock.capacidadesDeResponsable as jasmine.Spy).and.returnValue(of(capacidades));
    }

    async function entrar(ruta: unknown): Promise<unknown> {
      return resolveGuardResult(executeGuard(ruta as any, { url: '/x' } as any));
    }

    for (const roles of [
      ['ROLE_RESPONSABLE_SEDE'],
      ['ROLE_RESPONSABLE_SEDE', 'ROLE_PROFESOR'],
      ['ROLE_RESPONSABLE_SEDE', 'ROLE_USER'],
    ]) {
      it(`${roles.join(' + ')} entra en las pantallas de la 370`, async () => {
        sesion(roles, ['PANEL_SEDE']);
        expect(await entrar(PANEL_SEDE)).toBeTrue();
      });

      it(`${roles.join(' + ')} va a su panel desde una pantalla de gestión`, async () => {
        sesion(roles, ['PANEL_SEDE']);
        expect(await entrar(GESTION)).toBeFalse();
        expect(routerSpy.navigate).toHaveBeenCalledWith(['/adminpage']);
      });
    }

    it('R solo no entra en el portal de familia: va a su panel (entrada con Google)', async () => {
      sesion(['ROLE_RESPONSABLE_SEDE'], ['PANEL_SEDE']);
      expect(await entrar(FAMILIA)).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/adminpage']);
    });

    it('R + F entra en el portal de familia (371)', async () => {
      sesion(['ROLE_RESPONSABLE_SEDE', 'ROLE_USER'], ['PANEL_SEDE', 'ELEGIR_VISTA']);
      expect(await entrar(FAMILIA)).toBeTrue();
    });

    it('falla cerrado: sin PANEL_SEDE un responsable no entra y va a la raíz', async () => {
      sesion(['ROLE_RESPONSABLE_SEDE'], []);
      expect(await entrar(PANEL_SEDE)).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
    });

    it('R + G entra por su gestión, sin PANEL_SEDE', async () => {
      sesion(['ROLE_RESPONSABLE_SEDE', 'ROLE_ADMIN'], []);
      expect(await entrar(GESTION)).toBeTrue();
    });
  });

  it('manda a «Mis grupos» a una sesión solo profesor que llega a /userpage (entrada con Google)', async () => {
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(
      new BehaviorSubject(['ROLE_PROFESOR']).asObservable()
    );

    const result = await resolveGuardResult(
      executeGuard({ data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER'] } } as any, { url: '/userpage' } as any)
    );

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/mis-grupos']);
  });

  it('una sesión sin roles sigue yendo a la raíz', async () => {
    (authServiceMock.resolverRolesDisponibles as jasmine.Spy).and.returnValue(new BehaviorSubject([]).asObservable());

    const result = await resolveGuardResult(
      executeGuard({ data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER'] } } as any, { url: '/userpage' } as any)
    );

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('deniega acceso si la ruta no declara requiredRoles', async () => {
    const result = await resolveGuardResult(executeGuard({ data: {} } as any, { url: '/alumnosListar' } as any));

    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
    expect(authServiceMock.resolverRolesDisponibles).not.toHaveBeenCalled();
  });
});

describe('roleGuard con el servicio real: arranque con un fallo de red en /roles', () => {
  const routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

  beforeEach(() => {
    routerSpy.navigate.calls.reset();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: routerSpy }],
    });
  });

  it('si /roles falla una vez con estado 0 y luego da ROLE_ADMIN, deja pasar sin navegar', fakeAsync(() => {
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthenticationService);
    http.expectOne(`${environment.apiUrl}/auth/auth-status`).flush(true);
    http.expectOne(`${environment.apiUrl}/auth/user`).flush({ email: 'admin@escuela.es' });
    // La carga de roles del arranque falla sin llegar al servidor (Firefox al restaurar la sesión).
    http.expectOne(`${environment.apiUrl}/auth/roles`).error(new ProgressEvent('error'));

    let resultado: unknown;
    const guard = TestBed.runInInjectionContext(() =>
      roleGuard({ data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_MANAGER'] } } as any, { url: '/convocatoriasListar' } as any)
    ) as Observable<unknown>;
    guard.subscribe((r) => (resultado = r));
    http.expectNone(`${environment.apiUrl}/auth/roles`);

    tick(500);
    http.expectOne(`${environment.apiUrl}/auth/roles`).flush(['ROLE_ADMIN']);

    expect(resultado).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    http.verify();
  }));
});
