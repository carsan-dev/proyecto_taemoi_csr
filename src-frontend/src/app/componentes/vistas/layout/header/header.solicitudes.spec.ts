import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { SolicitudesContadorService } from '../../../../servicios/endpoints/solicitudes-contador.service';
import { PageScrollService } from '../../../../servicios/generales/page-scroll.service';
import { ScrollLockService } from '../../../../servicios/generales/scroll-lock.service';
import { HeaderComponent } from './header.component';

/** Pieza 6c-7 (D6c-4): «Solicitudes» con contador en la cabecera de gestión, pedido al entrar y al navegar. */
describe('HeaderComponent: contador de solicitudes', () => {
  let contador: { total: ReturnType<typeof signal<number>>; refrescar: jasmine.Spy; reiniciar: jasmine.Spy };
  let logueado: BehaviorSubject<boolean>;

  async function renderizar(roles: string[], total = 0) {
    contador = {
      total: signal(total),
      refrescar: jasmine.createSpy('refrescar'),
      reiniciar: jasmine.createSpy('reiniciar'),
    };
    logueado = new BehaviorSubject<boolean>(true);
    const gestion = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
    const authServiceMock = {
      usuarioLogueadoCambio: logueado,
      usernameCambio: new BehaviorSubject<string | null>('cuenta'),
      rolesCambio: new BehaviorSubject<string[]>(roles),
      comprobarLogueado: () => true,
      getRoles: () => of(roles),
      resolverRolesDisponibles: () => of(roles),
      esAdminPlataforma: () => of(false),
      marcarVistaPreferidaSegunRuta: () => undefined,
      tieneAccesoAdmin: () => gestion,
      tieneAccesoUser: () => roles.includes('ROLE_USER'),
      tieneAccesoDual: () => false,
      logout: () => undefined,
    };

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideRouter([
          { path: 'a', children: [] },
          { path: 'b', children: [] },
        ]),
        { provide: AuthenticationService, useValue: authServiceMock },
        { provide: PageScrollService, useValue: { scrollTop: 0 } },
        { provide: ScrollLockService, useValue: { lock: () => () => undefined } },
        { provide: SolicitudesContadorService, useValue: contador },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('gestión pide el contador al entrar y en cada navegación', async () => {
    const fixture = await renderizar(['ROLE_MANAGER']);
    const alEntrar = contador.refrescar.calls.count();
    expect(alEntrar).toBeGreaterThan(0);

    const router = TestBed.inject(Router);
    await fixture.ngZone!.run(() => router.navigateByUrl('/a'));
    expect(contador.refrescar.calls.count()).toBe(alEntrar + 1);
    await fixture.ngZone!.run(() => router.navigateByUrl('/b'));
    expect(contador.refrescar.calls.count()).toBe(alEntrar + 2);
  });

  it('la familia, la sesión solo-profesor y PROFESOR + RESPONSABLE_SEDE no lo piden', async () => {
    for (const roles of [['ROLE_USER'], ['ROLE_PROFESOR'], ['ROLE_PROFESOR', 'ROLE_RESPONSABLE_SEDE']]) {
      const fixture = await renderizar(roles);
      await fixture.ngZone!.run(() => TestBed.inject(Router).navigateByUrl('/a'));
      expect(contador.refrescar).not.toHaveBeenCalled();
    }
  });

  it('con 3 pinta el distintivo en el acceso rápido y en la barra lateral', async () => {
    const fixture = await renderizar(['ROLE_ADMIN'], 3);
    const el = fixture.nativeElement as HTMLElement;

    const acceso = el.querySelector<HTMLAnchorElement>('.admin-quick-nav a.quick-link[href="/solicitudes"]');
    expect(acceso).not.toBeNull();
    expect(acceso!.querySelector('.contador-aviso')?.textContent?.trim()).toBe('3');
    expect(acceso!.getAttribute('aria-label')).toBe('Solicitudes: 3 por atender');

    const lateral = el.querySelector<HTMLAnchorElement>('#admin-sidebar a.menu-link[href="/solicitudes"]');
    expect(lateral?.textContent).toContain('Solicitudes');
    expect(lateral?.querySelector('.contador-aviso')?.textContent?.trim()).toBe('3');
  });

  it('con 0, sin distintivo', async () => {
    const fixture = await renderizar(['ROLE_MANAGER'], 0);
    const el = fixture.nativeElement as HTMLElement;

    const acceso = el.querySelector<HTMLAnchorElement>('.admin-quick-nav a.quick-link[href="/solicitudes"]');
    expect(acceso).not.toBeNull();
    expect(acceso!.getAttribute('aria-label')).toBe('Solicitudes');
    expect(el.querySelector('.contador-aviso')).toBeNull();
  });

  it('al cerrar sesión vuelve a 0 y deja de pedirlo', async () => {
    const fixture = await renderizar(['ROLE_ADMIN'], 2);
    logueado.next(false);
    expect(contador.reiniciar).toHaveBeenCalled();

    const antes = contador.refrescar.calls.count();
    await fixture.ngZone!.run(() => TestBed.inject(Router).navigateByUrl('/a'));
    expect(contador.refrescar.calls.count()).toBe(antes);
  });
});
