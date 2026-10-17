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

describe('HeaderComponent: cabecera móvil', () => {
  async function renderizar(rol: 'visitante' | 'familia') {
    const logueado = rol === 'familia';
    const roles = logueado ? ['ROLE_USER'] : [];
    const authServiceMock = {
      usuarioLogueadoCambio: new BehaviorSubject<boolean>(logueado),
      usernameCambio: new BehaviorSubject<string | null>(logueado ? 'familia' : null),
      rolesCambio: new BehaviorSubject<string[]>(roles),
      comprobarLogueado: () => logueado,
      getRoles: () => of(roles),
      esAdminPlataforma: () => of(false),
      marcarVistaPreferidaSegunRuta: () => undefined,
      tieneAccesoAdmin: () => false,
      tieneAccesoUser: () => logueado,
      tieneAccesoDual: () => false,
      logout: () => undefined,
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
    return fixture;
  }

  const textoDe = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim();

  it('el visitante ve «Preinscríbete» y «Alumnos» con texto y sin redes en la cabecera', async () => {
    const fixture = await renderizar('visitante');
    const el = fixture.nativeElement as HTMLElement;
    const acciones = el.querySelector('.header-actions-mobile');

    expect(textoDe(acciones?.querySelector('a[href="/preinscripcion"]') ?? null)).toBe('Preinscríbete');
    expect(textoDe(acciones?.querySelector('a[href="/login"]') ?? null)).toBe('Alumnos');
    expect(acciones?.querySelector('a[href*="facebook"], a[href*="instagram"], a[href*="tiktok"]')).toBeNull();
  });

  it('las redes sociales están en el menú móvil del visitante', async () => {
    const fixture = await renderizar('visitante');
    fixture.componentInstance.toggleMobileMenu();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const redes = el.querySelectorAll<HTMLAnchorElement>('.mobile-header-panel .mobile-header-social a');
    expect(Array.from(redes).map((a) => a.getAttribute('aria-label'))).toEqual(['Facebook', 'Instagram', 'TikTok']);
    redes.forEach((a) => {
      expect(a.getAttribute('target')).toBe('_blank');
      expect(a.getAttribute('rel')).toBe('noopener noreferrer');
    });
  });

  it('la familia ve «Mi cuenta» y al pulsarlo se abre el menú con «Cerrar sesión»', async () => {
    const fixture = await renderizar('familia');
    const el = fixture.nativeElement as HTMLElement;
    const boton = el.querySelector<HTMLButtonElement>('.btn-cuenta-mobile');

    expect(textoDe(boton)).toBe('Mi cuenta');
    expect(boton?.getAttribute('aria-haspopup')).toBe('menu');
    expect(boton?.getAttribute('aria-expanded')).toBe('false');
    expect(el.querySelector('.mobile-user-quick-menu-panel')).toBeNull();

    boton?.click();
    fixture.detectChanges();

    expect(boton?.getAttribute('aria-expanded')).toBe('true');
    const panel = el.querySelector('.mobile-user-quick-menu-panel');
    expect(textoDe(panel)).toContain('Mis Clases');
    expect(textoDe(panel)).toContain('Cerrar sesión');
  });

  it('la familia tiene las redes y la preinscripción en el menú móvil', async () => {
    const fixture = await renderizar('familia');
    fixture.componentInstance.toggleMobileMenu();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const panel = el.querySelector('.mobile-header-panel');

    expect(panel?.querySelectorAll('.mobile-header-social a').length).toBe(3);
    expect(panel?.querySelector('a[href="/preinscripcion"]')).not.toBeNull();
  });

  it('en escritorio el visitante ve «Preinscríbete» y «Acceso alumnos», sin «Iniciar Sesión»', async () => {
    const fixture = await renderizar('visitante');
    const el = fixture.nativeElement as HTMLElement;
    const escritorio = el.querySelector('.navbar-collapse');

    expect(textoDe(escritorio?.querySelector('a.nav-link-join') ?? null)).toBe('Preinscríbete');
    expect(textoDe(escritorio?.querySelector('a.btn-login') ?? null)).toBe('Acceso alumnos');
    expect(escritorio?.querySelector('a.btn-login i.bi-person')).not.toBeNull();
    expect(textoDe(escritorio ?? null)).not.toContain('Iniciar Sesión');
    expect(textoDe(escritorio ?? null)).not.toContain('Únete a nosotros');
    expect(escritorio?.querySelector('a.btn-login')?.getAttribute('aria-label')).toBe('Acceso para alumnos');
  });

  it('en escritorio la familia ve la pastilla «Mi cuenta» y abre el mismo menú', async () => {
    const fixture = await renderizar('familia');
    const el = fixture.nativeElement as HTMLElement;
    const boton = el.querySelector<HTMLButtonElement>('.navbar-collapse .user-menu-toggle');

    expect(textoDe(boton)).toBe('Mi cuenta');
    expect(boton?.getAttribute('aria-haspopup')).toBe('menu');
    expect(boton?.getAttribute('aria-expanded')).toBe('false');
    expect(boton?.querySelector('i.bi-person')).not.toBeNull();
    expect(boton?.querySelector('i.bi-chevron-down')).not.toBeNull();

    boton?.click();
    fixture.detectChanges();

    expect(boton?.getAttribute('aria-expanded')).toBe('true');
    const panel = el.querySelector('.user-desktop-menu-panel');
    expect(textoDe(panel)).toContain('Mis Clases');
    expect(textoDe(panel)).toContain('Cerrar sesión');
  });

  it('en escritorio solo hay una pastilla rellena (Preinscríbete) junto a las de contorno', async () => {
    const fixture = await renderizar('visitante');
    const el = fixture.nativeElement as HTMLElement;
    const acciones = el.querySelectorAll('.navbar-collapse a.nav-link-join, .navbar-collapse a.btn-login');

    expect(acciones.length).toBe(2);
    expect(el.querySelectorAll('.navbar-collapse a.nav-link-join').length).toBe(1);
    // «Acceso alumnos» y «Mi cuenta» son de contorno: no usan la clase rellena de la acción principal.
    expect(el.querySelector('.navbar-collapse a.btn-login')?.classList.contains('nav-link-join')).toBeFalse();
    const relleno = (e: Element | null) => e ? getComputedStyle(e).backgroundImage !== 'none' : false;
    expect(relleno(el.querySelector('.navbar-collapse a.nav-link-join'))).toBeTrue();
    expect(relleno(el.querySelector('.navbar-collapse a.btn-login'))).toBeFalse();
  });

  it('las redes siguen en el DOM de escritorio (se ocultan por CSS en tablet apaisada)', async () => {
    const fixture = await renderizar('visitante');
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('.navbar-right .nav-item-social a.social-link').length).toBe(3);
  });

  it('el título móvil tiene corte fijo «MOI\'S» / «KIM DO» con líneas indivisibles y nunca se oculta', async () => {
    for (const rol of ['visitante', 'familia'] as const) {
      const fixture = await renderizar(rol);
      const titulo = (fixture.nativeElement as HTMLElement).querySelector('.header-title-mobile .title-main');
      const lineas = Array.from(titulo?.querySelectorAll('.title-line') ?? []);

      expect(lineas.map((l) => l.textContent)).withContext(rol).toEqual(['MOI\'S', 'KIM DO']);
      lineas.forEach((l) => expect(getComputedStyle(l).whiteSpace).withContext(rol).toBe('nowrap'));
      expect(getComputedStyle(titulo?.parentElement as Element).display).withContext(rol).not.toBe('none');
    }
  });
});
