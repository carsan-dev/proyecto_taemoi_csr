import { HeaderComponent } from './header.component';
import { NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { PageScrollService } from '../../../../servicios/generales/page-scroll.service';
import { ScrollLockService } from '../../../../servicios/generales/scroll-lock.service';

describe('HeaderComponent', () => {
  function createComponent(): HeaderComponent {
    const authServiceMock = {
      usuarioLogueadoCambio: new Subject<boolean>(),
      usernameCambio: new Subject<string | null>(),
      rolesCambio: new Subject<string[]>(),
      comprobarLogueado: () => false,
      getRoles: () => of([]),
      marcarVistaPreferidaSegunRuta: jasmine.createSpy('marcarVistaPreferidaSegunRuta'),
      tieneAccesoAdmin: (roles: string[]) => roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER'),
      tieneAccesoUser: (roles: string[]) => roles.includes('ROLE_USER'),
      tieneAccesoDual: (roles: string[]) => roles.includes('ROLE_USER') && (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')),
    };

    const routerMock = {
      url: '/adminpage',
      events: of(),
    };

    const pageScroll = { scrollTop: 0 };
    const scrollLock = { lock: () => () => undefined };
    return new HeaderComponent(
      authServiceMock as any,
      routerMock as any,
      new NgZone({}),
      pageScroll as any,
      scrollLock as any
    );
  }

  let component: HeaderComponent;

  beforeEach(() => {
    component = createComponent();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('oculta accesos rapidos solo-admin para manager', () => {
    component.isAdminOnly = false;

    const routes = component.visibleAdminQuickLinks.map((item) => item.route);

    expect(routes).not.toContain('/auditoriaSistema');
    expect(routes).not.toContain('/configuracion-sistema');
  });

  it('muestra accesos rapidos solo-admin para admin', () => {
    component.isAdminOnly = true;

    const routes = component.visibleAdminQuickLinks.map((item) => item.route);

    expect(routes).toContain('/auditoriaSistema');
    expect(routes).toContain('/configuracion-sistema');
  });

  it('muestra el acceso a errores de plataforma solo al administrador de plataforma', () => {
    component.isAdminOnly = true;
    component.esAdminPlataforma = false;
    expect(component.visibleAdminQuickLinks.map((item) => item.route)).not.toContain('/plataformaErrores');

    component.esAdminPlataforma = true;
    expect(component.visibleAdminQuickLinks.map((item) => item.route)).toContain('/plataformaErrores');
  });

  it('detecta el portal user usando metadata de rutas', () => {
    (component as any).actualizarContextoRuta('/userpage/turnos');

    expect(component.isUserRoute).toBeTrue();
  });

  it('detecta el portal admin usando metadata de rutas', () => {
    (component as any).actualizarContextoRuta('/alumnosListar');

    expect(component.isUserRoute).toBeFalse();
  });
});

describe('HeaderComponent: menú lateral de administración', () => {
  async function renderizar(esAdminPlataforma: boolean): Promise<HTMLElement> {
    const authServiceMock = {
      usuarioLogueadoCambio: new BehaviorSubject<boolean>(true),
      usernameCambio: new BehaviorSubject<string | null>('admin'),
      rolesCambio: new BehaviorSubject<string[]>(['ROLE_ADMIN']),
      comprobarLogueado: () => true,
      getRoles: () => of(['ROLE_ADMIN']),
      esAdminPlataforma: () => of(esAdminPlataforma),
      marcarVistaPreferidaSegunRuta: () => undefined,
      tieneAccesoAdmin: () => true,
      tieneAccesoUser: () => false,
      tieneAccesoDual: () => false,
    };

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideRouter([]),
        { provide: AuthenticationService, useValue: authServiceMock },
        { provide: PageScrollService, useValue: { scrollTop: 0 } },
        { provide: ScrollLockService, useValue: { lock: () => () => undefined } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const enlacesMenu = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('#admin-sidebar a.menu-link')).map((a) => a.textContent?.trim());

  it('muestra la sección Superadmin con Errores de plataforma al administrador de plataforma', async () => {
    const el = await renderizar(true);

    expect(el.querySelector('#admin-sidebar')?.textContent).toContain('Superadmin');
    const enlace = el.querySelector<HTMLAnchorElement>('#admin-sidebar a.menu-link[href="/plataformaErrores"]');
    expect(enlace?.textContent?.trim()).toBe('Errores de plataforma');
    expect(enlacesMenu(el)).toContain('Registro de acciones');
  });

  it('la oculta cuando el sondeo de plataforma es false', async () => {
    const el = await renderizar(false);

    expect(el.querySelector('#admin-sidebar')?.textContent).not.toContain('Superadmin');
    expect(el.querySelector('#admin-sidebar a[href="/plataformaErrores"]')).toBeNull();
    expect(enlacesMenu(el)).toContain('Registro de acciones');
  });
});
