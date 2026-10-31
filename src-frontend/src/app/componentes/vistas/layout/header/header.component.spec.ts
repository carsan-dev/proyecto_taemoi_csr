import { HeaderComponent } from './header.component';
import { NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { PageScrollService } from '../../../../servicios/generales/page-scroll.service';
import { ScrollLockService } from '../../../../servicios/generales/scroll-lock.service';
import { Capacidad, PermisosSesionService } from '../../../../servicios/authentication/permisos-sesion.service';
import { itSinReloj } from '../../../../testing/reloj-prueba';

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
    TestBed.configureTestingModule({});
    return TestBed.runInInjectionContext(() => new HeaderComponent(
      authServiceMock as any,
      routerMock as any,
      new NgZone({}),
      pageScroll as any,
      scrollLock as any,
      { capacidades: () => of([]), obtener: () => of({}) } as any
    ));
  }

  let component: HeaderComponent;

  beforeEach(() => {
    component = createComponent();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
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
      resolverRolesDisponibles: () => of(roles),
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
        { provide: PermisosSesionService, useValue: { capacidades: () => of([]), obtener: () => of({}) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    return fixture;
  }

  const textoDe = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim();

  itSinReloj('el visitante ve «Preinscríbete» y «Alumnos» con texto y sin redes en la cabecera', async () => {
    const fixture = await renderizar('visitante');
    const el = fixture.nativeElement as HTMLElement;
    const acciones = el.querySelector('.header-actions-mobile');

    expect(textoDe(acciones?.querySelector('a[href="/preinscripcion"]') ?? null)).toBe('Preinscríbete');
    expect(textoDe(acciones?.querySelector('a[href="/login"]') ?? null)).toBe('Alumnos');
    expect(acciones?.querySelector('a[href*="facebook"], a[href*="instagram"], a[href*="tiktok"]')).toBeNull();
  });

  itSinReloj('las redes sociales están en el menú móvil del visitante', async () => {
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

  itSinReloj('la familia ve «Mi cuenta» y al pulsarlo se abre el menú con «Cerrar sesión»', async () => {
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

  itSinReloj('la familia tiene las redes y la preinscripción en el menú móvil', async () => {
    const fixture = await renderizar('familia');
    fixture.componentInstance.toggleMobileMenu();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const panel = el.querySelector('.mobile-header-panel');

    expect(panel?.querySelectorAll('.mobile-header-social a').length).toBe(3);
    expect(panel?.querySelector('a[href="/preinscripcion"]')).not.toBeNull();
  });

  itSinReloj('en escritorio el visitante ve «Preinscríbete» y «Acceso alumnos», sin «Iniciar Sesión»', async () => {
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

  itSinReloj('en escritorio la familia ve la pastilla «Mi cuenta» y abre el mismo menú', async () => {
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

  itSinReloj('en escritorio solo hay una pastilla rellena (Preinscríbete) junto a las de contorno', async () => {
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

  itSinReloj('las redes siguen en el DOM de escritorio (se ocultan por CSS en tablet apaisada)', async () => {
    const fixture = await renderizar('visitante');
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('.navbar-right .nav-item-social a.social-link').length).toBe(3);
  });

  itSinReloj('el título móvil tiene corte fijo «MOI\'S» / «KIM DO» con líneas indivisibles y nunca se oculta', async () => {
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

describe('HeaderComponent: sesiones con ROLE_PROFESOR', () => {
  /** Por defecto, la capacidad que el servidor da según la sesión (E8-1): solo-profesor y F+Pr, MIS_GRUPOS. */
  async function renderizar(roles: string[], capacidades?: Capacidad[] | 'error') {
    const gestion = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
    const porDefecto: Capacidad[] = roles.includes('ROLE_PROFESOR') ? (gestion ? ['MI_CAJA'] : ['MI_CAJA', 'MIS_GRUPOS']) : [];
    const permisos = {
      capacidades: jasmine
        .createSpy('capacidades')
        .and.callFake(() => of(capacidades === 'error' ? [] : (capacidades ?? porDefecto))),
      obtener: () => of({}),
    };
    const authServiceMock = {
      usuarioLogueadoCambio: new BehaviorSubject<boolean>(true),
      usernameCambio: new BehaviorSubject<string | null>('profe'),
      rolesCambio: new BehaviorSubject<string[]>(roles),
      comprobarLogueado: () => true,
      getRoles: () => of(roles),
      resolverRolesDisponibles: () => of(roles),
      esAdminPlataforma: () => of(false),
      marcarVistaPreferidaSegunRuta: () => undefined,
      tieneAccesoAdmin: (r: string[]) => r.includes('ROLE_ADMIN') || r.includes('ROLE_MANAGER'),
      tieneAccesoUser: (r: string[]) => r.includes('ROLE_USER'),
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
        { provide: PermisosSesionService, useValue: permisos },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, permisos };
  }

  itSinReloj('solo profesor: sin cabecera de familia (el menú público de la web lo cubren sus pruebas)', async () => {
    const { fixture } = await renderizar(['ROLE_PROFESOR', 'ROLE_RESPONSABLE_SEDE']);
    const el = fixture.nativeElement as HTMLElement;

    expect(fixture.componentInstance.mostrarHeaderUser).toBeFalse();
    expect(el.querySelector('a[href="/userpage"]')).toBeNull();
  });

  itSinReloj('familia + profesor: «Mi caja» en el menú de cuenta de escritorio y móvil', async () => {
    const { fixture } = await renderizar(['ROLE_USER', 'ROLE_PROFESOR']);
    const componente = fixture.componentInstance;
    const el = fixture.nativeElement as HTMLElement;

    componente.toggleDesktopUserMenu();
    fixture.detectChanges();
    expect(el.querySelector('.user-desktop-menu-panel a[href="/mi-caja"]')?.textContent?.trim()).toBe('Mi caja');

    componente.closeDesktopUserMenu();
    componente.toggleMobileUserQuickMenu();
    fixture.detectChanges();
    expect(el.querySelector('.mobile-user-quick-menu-panel a[href="/mi-caja"]')?.textContent?.trim()).toBe('Mi caja');
  });

  itSinReloj('familia + profesor: «Mis grupos» en el menú de cuenta y en el menú móvil (D7-7)', async () => {
    const { fixture } = await renderizar(['ROLE_USER', 'ROLE_PROFESOR']);
    const componente = fixture.componentInstance;
    const el = fixture.nativeElement as HTMLElement;

    componente.toggleDesktopUserMenu();
    fixture.detectChanges();
    expect(el.querySelector('.user-desktop-menu-panel a[href="/mis-grupos"]')?.textContent?.trim()).toBe('Mis grupos');

    componente.closeDesktopUserMenu();
    componente.toggleMobileUserQuickMenu();
    fixture.detectChanges();
    expect(el.querySelector('.mobile-user-quick-menu-panel a[href="/mis-grupos"]')?.textContent?.trim()).toBe('Mis grupos');

    componente.closeMobileUserQuickMenu();
    componente.toggleMobileMenu();
    fixture.detectChanges();
    expect(el.querySelector('.mobile-header-link[href="/mis-grupos"]')?.textContent?.trim()).toBe('Mis grupos');
  });

  itSinReloj('gestión + familia + profesor sin MIS_GRUPOS: ni el menú de cuenta ni el móvil ofrecen «Mis grupos»', async () => {
    const { fixture } = await renderizar(['ROLE_MANAGER', 'ROLE_USER', 'ROLE_PROFESOR']);
    const componente = fixture.componentInstance;
    const el = fixture.nativeElement as HTMLElement;
    expect(componente.veMisGrupos).toBeFalse();
    componente.toggleDesktopUserMenu();
    fixture.detectChanges();
    expect(el.querySelector('a[href="/mis-grupos"]')).toBeNull();
    componente.closeDesktopUserMenu();
    componente.toggleMobileMenu();
    fixture.detectChanges();
    expect(el.querySelector('a[href="/mis-grupos"]')).toBeNull();
  });

  itSinReloj('familia sin profesor: no ve «Mi caja» y sigue sin pedir permisos', async () => {
    const { fixture, permisos } = await renderizar(['ROLE_USER']);
    expect(permisos.capacidades).not.toHaveBeenCalled();
    fixture.componentInstance.toggleDesktopUserMenu();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/mi-caja"]')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/mis-grupos"]')).toBeNull();
  });
});

describe('HeaderComponent: cabecera de la web pública con sesión del panel', () => {
  async function renderizar(roles: string[], ruta = '/eventos', vistaPreferida: 'user' | 'admin' = 'admin') {
    const logueado = roles.length > 0;
    const gestion = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER');
    const authServiceMock = {
      usuarioLogueadoCambio: new BehaviorSubject<boolean>(logueado),
      usernameCambio: new BehaviorSubject<string | null>(logueado ? 'cuenta' : null),
      rolesCambio: new BehaviorSubject<string[]>(roles),
      comprobarLogueado: () => logueado,
      getRoles: () => of(roles),
      resolverRolesDisponibles: () => of(roles),
      esAdminPlataforma: () => of(false),
      marcarVistaPreferidaSegunRuta: () => undefined,
      obtenerVistaPreferida: () => vistaPreferida,
      tieneAccesoAdmin: () => gestion,
      tieneAccesoUser: () => roles.includes('ROLE_USER'),
      tieneAccesoDual: () => gestion && roles.includes('ROLE_USER'),
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
        { provide: PermisosSesionService, useValue: { capacidades: () => of([]), obtener: () => of({}) } },
      ],
    }).compileComponents();

    // ngOnInit lee la ruta de `router.url`: se fija antes de crear el componente.
    spyOnProperty(TestBed.inject(Router), 'url', 'get').and.returnValue(ruta);
    const fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const texto = (e: Element | null) => e?.textContent?.replace(/\s+/g, ' ').trim();

  itSinReloj('visitante: menú público con «Acceso alumnos» y «Alumnos», sin «Volver al panel»', async () => {
    const { el } = await renderizar([]);

    expect(el.querySelector('header.header-anonimo')).not.toBeNull();
    expect(el.querySelector('a.btn-login[href="/login"]')).not.toBeNull();
    expect(el.querySelector('a.btn-entrar-mobile[href="/login"]')).not.toBeNull();
    expect(texto(el)).not.toContain('Volver al panel');
  });

  itSinReloj('gestión en la web pública: menú público y «Volver al panel» a /adminpage, sin «Entrar»', async () => {
    const { el, fixture } = await renderizar(['ROLE_ADMIN']);

    expect(el.querySelector('header.header-anonimo')).not.toBeNull();
    expect(el.querySelector('.admin-top-navbar, .admin-sidebar')).toBeNull();
    expect(el.querySelector('a[href="/eventos"]')).not.toBeNull();
    expect(texto(el.querySelector('a.btn-login'))).toBe('Volver al panel');
    expect(el.querySelector('a.btn-login')?.getAttribute('href')).toBe('/adminpage');
    expect(texto(el.querySelector('a.btn-entrar-mobile'))).toBe('Volver al panel');
    expect(el.querySelector('a.btn-entrar-mobile')?.getAttribute('href')).toBe('/adminpage');
    expect(el.querySelector('a[href="/login"]')).toBeNull();

    fixture.componentInstance.toggleMobileMenu();
    fixture.detectChanges();
    const enlaceMovil = el.querySelector('.mobile-header-panel a[href="/adminpage"]');
    expect(texto(enlaceMovil)).toBe('Volver al panel');
    expect(el.querySelector('a[href="/login"]')).toBeNull();
  });

  itSinReloj('un gestor (ROLE_MANAGER) recibe el mismo tratamiento', async () => {
    const { el } = await renderizar(['ROLE_MANAGER']);

    expect(el.querySelector('a.btn-login[href="/adminpage"]')).not.toBeNull();
    expect(el.querySelector('a[href="/login"]')).toBeNull();
  });

  itSinReloj('gestión y familia con la vista de familia preferida: «Volver al panel» sigue yendo a /adminpage', async () => {
    const { el } = await renderizar(['ROLE_ADMIN', 'ROLE_USER'], '/eventos', 'user');

    expect(el.querySelector('header.header-anonimo')).not.toBeNull();
    expect(el.querySelector('header.header-user')).toBeNull();
    expect(el.querySelector('a.btn-login')?.getAttribute('href')).toBe('/adminpage');
    expect(el.querySelector('a.btn-entrar-mobile')?.getAttribute('href')).toBe('/adminpage');
  });

  itSinReloj('gestión y familia en una ruta de familia conservan su cabecera y el cambio de vista', async () => {
    const { el, fixture } = await renderizar(['ROLE_ADMIN', 'ROLE_USER'], '/userpage');
    fixture.componentInstance.toggleDesktopUserMenu();
    fixture.detectChanges();

    expect(el.querySelector('header.header-user')).not.toBeNull();
    expect(el.querySelector('header.header-anonimo')).toBeNull();
    expect(texto(el)).toContain('Cambiar a admin');
  });

  itSinReloj('familia: cabecera de familia sin cambios y sin «Volver al panel»', async () => {
    const { el } = await renderizar(['ROLE_USER']);

    expect(el.querySelector('header.header-user')).not.toBeNull();
    expect(el.querySelector('header.header-anonimo')).toBeNull();
    expect(texto(el)).not.toContain('Volver al panel');
  });

  itSinReloj('solo profesor: mismo menú público, «Volver al panel» a /mis-grupos', async () => {
    const { el } = await renderizar(['ROLE_PROFESOR']);

    expect(el.querySelector('header.header-anonimo')).not.toBeNull();
    expect(el.querySelector('a.btn-login')?.getAttribute('href')).toBe('/mis-grupos');
    expect(el.querySelector('a.btn-entrar-mobile')?.getAttribute('href')).toBe('/mis-grupos');
    expect(el.querySelector('a[href="/login"]')).toBeNull();
  });

  itSinReloj('gestión en una ruta del portal admin (skeleton en «lo de hoy»): menú público y «Volver al panel»', async () => {
    const { el } = await renderizar(['ROLE_ADMIN'], '/alumnosListar');

    expect(el.querySelector('header.header-anonimo')).not.toBeNull();
    expect(el.querySelector('a.btn-login')?.getAttribute('href')).toBe('/adminpage');
    expect(el.querySelector('a[href="/login"]')).toBeNull();
  });

  itSinReloj('los logos del menú público mandan verWeb para no volver al panel', async () => {
    for (const roles of [[], ['ROLE_ADMIN']]) {
      const { fixture } = await renderizar(roles);
      const enlaces = fixture.debugElement.queryAll(By.css('a.navbar-brand, a.navbar-brand-desktop'));

      expect(enlaces.length).withContext(roles.join()).toBe(2);
      const navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.returnValue(Promise.resolve(true));
      for (const enlace of enlaces) {
        enlace.nativeElement.click();
        expect(navegar.calls.mostRecent().args[1]?.state).withContext(roles.join()).toEqual({ verWeb: true });
      }
    }
  });
});
