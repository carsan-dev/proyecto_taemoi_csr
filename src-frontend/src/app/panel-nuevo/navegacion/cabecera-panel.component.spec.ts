import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { SolicitudesContadorService } from '../../servicios/endpoints/solicitudes-contador.service';
import { MarcaPanel } from '../marca-escuela-panel.service';
import { contraste } from '../../componentes/endpoints/plataforma-logos/regla-contraste';
import { CabeceraPanelComponent, RECARGAR_PAGINA, fondoActivoCabecera, iniciales, ocultaBarraInferior } from './cabecera-panel.component';
import { itSinReloj } from '../../testing/reloj-prueba';

const API = environment.apiUrl;

@Component({ standalone: true, template: '' })
class PantallaFalsaComponent {}

/** Escuela y cuenta inventadas. */
function marca(parcial: Partial<MarcaPanel> = {}): MarcaPanel {
  return {
    nombre: 'Club Ficticio del Sur',
    logo: 'LEGADO',
    logoVersion: null,
    tema: {
      primario: '#1B2B2E',
      sobrePrimario: '#FFFFFF',
      sobrePrimario2: '#C6CACB',
      primarioTinta: '#1B2B2E',
      primarioSuave: '#E8EAEA',
      ajustado: false,
    },
    ...parcial,
  };
}

function permisos(parcial: Partial<PermisosSesion> & { nombreVisible?: string | null } = {}): PermisosSesion {
  return {
    roles: ['PROPIETARIO', 'PROFESOR'],
    esAdminPlataforma: false,
    sedes: [],
    variasSedes: false,
    tieneGrupos: true,
    capacidades: ['DAR_GESTION', 'MIS_GRUPOS', 'MI_CAJA'],
    nombreVisible: 'Carla S.',
    ...parcial,
  } as PermisosSesion;
}

describe('ocultaBarraInferior', () => {
  it('solo en pasar lista, del profesor y de gestión, con o sin consulta', () => {
    expect(ocultaBarraInferior('/mis-grupos/7/lista')).toBeTrue();
    expect(ocultaBarraInferior('/mis-grupos/7/lista?turno=9&fecha=2026-10-28')).toBeTrue();
    expect(ocultaBarraInferior('/gruposListar/7/lista')).toBeTrue();
    expect(ocultaBarraInferior('/mis-grupos/7')).toBeFalse();
    expect(ocultaBarraInferior('/mis-grupos')).toBeFalse();
    expect(ocultaBarraInferior('/gruposListar')).toBeFalse();
    expect(ocultaBarraInferior('/mis-grupos/7/listado')).toBeFalse();
  });
});

describe('fondoActivoCabecera e iniciales', () => {
  it('el texto de la cabecera llega a 4,5:1 sobre el fondo de la entrada actual', () => {
    const casos: [string, string][] = [
      ['#FFFFFF', '#1B2B2E'],
      ['#1A2230', '#3E9B4F'],
      ['#1A2230', '#8FD19E'],
      ['#FFFFFF', '#1A2230'],
      ['#FFFFFF', '#6B6B6B'],
    ];
    for (const [texto, primario] of casos) {
      const fondo = fondoActivoCabecera(texto, primario);
      expect(contraste(texto, fondo)).withContext(`${texto} sobre ${fondo}`).toBeGreaterThanOrEqual(4.5);
    }
    expect(fondoActivoCabecera('#FFFFFF', '#1B2B2E')).not.toBe('#1B2B2E');
  });

  it('iniciales del nombre visible', () => {
    expect(iniciales('Carla S.')).toBe('CS');
    expect(iniciales('Íñigo')).toBe('Í');
    expect(iniciales(null)).toBe('');
  });
});

describe('CabeceraPanelComponent (8b-N)', () => {
  let fixture: ComponentFixture<CabeceraPanelComponent>;
  let http: HttpTestingController;
  let router: Router;
  let roles: string[];
  let refrescar: jasmine.Spy;
  let reiniciarPermisos: jasmine.Spy;
  let refrescadoPermisos: Subject<void>;
  let recargar: jasmine.Spy;
  let logout: jasmine.Spy;
  let total: ReturnType<typeof signal<number>>;
  let datosPermisos: PermisosSesion;

  async function montar(ruta = '/alumnosListar'): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CabeceraPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: PantallaFalsaComponent }]),
        {
          provide: AuthenticationService,
          useValue: {
            emailCambio: new BehaviorSubject<string | null>('cuenta@ejemplo.test'),
            usernameCambio: new BehaviorSubject<string | null>('cuenta'),
            usuarioLogueadoCambio: new BehaviorSubject<boolean>(true),
            getRoles: () => of(roles),
            getRolesActuales: () => roles,
            esAdminPlataforma: () => of(false),
            tieneAccesoDual: (r: string[]) =>
              r.includes('ROLE_USER') && (r.includes('ROLE_ADMIN') || r.includes('ROLE_MANAGER')),
            guardarVistaPreferida: jasmine.createSpy('guardarVistaPreferida'),
            logout,
          },
        },
        {
          provide: PermisosSesionService,
          useValue: {
            obtener: () => of(datosPermisos),
            capacidades: () => of(datosPermisos.capacidades),
            reiniciar: reiniciarPermisos,
            refrescado$: refrescadoPermisos,
          },
        },
        { provide: SolicitudesContadorService, useValue: { total, refrescar, reiniciar: () => undefined } },
        { provide: RECARGAR_PAGINA, useValue: recargar },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    await router.navigateByUrl(ruta);
    fixture = TestBed.createComponent(CabeceraPanelComponent);
    fixture.detectChanges();
  }

  function responderMarca(m: MarcaPanel = marca()): void {
    http.expectOne(`${API}/escuela/marca`).flush(m);
    fixture.detectChanges();
  }

  // Las entradas solo se ven desde 1024 px; la ventana de Karma es más estrecha y un elemento oculto no toma el foco.
  let escritorio: HTMLStyleElement;
  beforeEach(() => {
    escritorio = document.createElement('style');
    escritorio.textContent = '.pn-cab__nav { display: block !important; }';
    document.head.appendChild(escritorio);
  });
  afterEach(() => escritorio.remove());

  beforeEach(() => {
    roles = ['ROLE_ADMIN', 'ROLE_PROFESOR'];
    refrescar = jasmine.createSpy('refrescar');
    reiniciarPermisos = jasmine.createSpy('reiniciar');
    refrescadoPermisos = new Subject<void>();
    recargar = jasmine.createSpy('recargar');
    logout = jasmine.createSpy('logout');
    total = signal(0);
    datosPermisos = permisos();
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;
  const entrada = (id: string) => el().querySelector<HTMLElement>(`.pn-cab__entrada[data-seccion="${id}"]`)!;
  const tecla = (destino: Element, key: string) =>
    destino.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));

  describe('Ver la web', () => {
    itSinReloj('el enlace navega a la portada con el estado verWeb', async () => {
      await montar();
      responderMarca();
      entrada('escuela').click();
      fixture.detectChanges();
      const enlace = Array.from(el().querySelectorAll<HTMLAnchorElement>('.pn-menu__opcion')).find((a) =>
        a.textContent?.includes('Ver la web')
      )!;
      expect(enlace).withContext('enlace').toBeDefined();
      enlace.click();
      await fixture.whenStable();
      expect(router.url).toBe('/');
      expect(router.lastSuccessfulNavigation?.extras.state?.['verWeb']).toBeTrue();
    });
  });

  describe('antes de pintar', () => {
    itSinReloj('no pinta la cabecera hasta tener la marca; luego la escribe en el .pn de la raíz', async () => {
      await montar();
      const raiz = el().querySelector<HTMLElement>('.pn')!;
      expect(raiz).withContext('raíz .pn').not.toBeNull();
      expect(el().querySelector('header.pn-cab')).toBeNull();

      responderMarca();

      expect(el().querySelector('header.pn-cab')).not.toBeNull();
      expect(raiz.style.getPropertyValue('--pn-marca-primario')).toBe('#1B2B2E');
      expect(document.documentElement.style.getPropertyValue('--pn-marca-primario')).toBe('');
    });

    itSinReloj('si la marca falla, pinta igual con los colores por defecto y sin logo', async () => {
      await montar();
      http.expectOne(`${API}/escuela/marca`).flush('no', { status: 500, statusText: 'Error' });
      fixture.detectChanges();

      expect(el().querySelector('header.pn-cab')).not.toBeNull();
      expect(el().querySelector('img.pn-cab__logo')).toBeNull();
      expect(el().querySelector('.pn-cab__nombre')?.textContent?.trim()).toBe('Panel de gestión');
    });
  });

  describe('logo (plan B)', () => {
    itSinReloj('LEGADO: el SVG de siempre', async () => {
      await montar();
      responderMarca(marca({ logo: 'LEGADO' }));
      expect(el().querySelector<HTMLImageElement>('img.pn-cab__logo')?.getAttribute('src')).toBe(
        'assets/media/Logo_escuela.svg'
      );
      expect(el().querySelector('.pn-cab__nombre')?.textContent?.trim()).toBe('Club Ficticio del Sur');
    });

    itSinReloj('NINGUNO: el nombre de la escuela sin logo', async () => {
      await montar();
      responderMarca(marca({ logo: 'NINGUNO' }));
      expect(el().querySelector('img.pn-cab__logo')).toBeNull();
      expect(el().querySelector('.pn-cab__nombre')?.textContent?.trim()).toBe('Club Ficticio del Sur');
    });

    itSinReloj('SUBIDO que da error (404): se quita la imagen y queda el nombre', async () => {
      await montar();
      responderMarca(marca({ logo: 'SUBIDO', logoVersion: 31 }));
      const img = el().querySelector<HTMLImageElement>('img.pn-cab__logo')!;
      expect(img.getAttribute('src')).toBe(`${API}/escuela/logo?v=31`);

      img.dispatchEvent(new Event('error'));
      fixture.detectChanges();

      expect(el().querySelector('img.pn-cab__logo')).toBeNull();
      expect(el().querySelector('.pn-cab__nombre')?.textContent?.trim()).toBe('Club Ficticio del Sur');
    });
  });

  describe('entradas', () => {
    for (const misGrupos of [true, false]) {
      itSinReloj(`solo-profesor con MIS_GRUPOS=${misGrupos}: enlaces directos y cuenta sin gestión`, async () => {
        roles = ['ROLE_PROFESOR'];
        datosPermisos = permisos({ roles: ['PROFESOR'], capacidades: misGrupos ? ['MIS_GRUPOS', 'MI_CAJA'] : ['MI_CAJA'] });
        total.set(7);
        await montar(misGrupos ? '/mis-grupos/4?mes=10' : '/mi-caja');
        responderMarca();
        const nombres = misGrupos ? ['Mis grupos', 'Mi caja'] : ['Mi caja'];
        const enlaces = Array.from(el().querySelectorAll<HTMLAnchorElement>('.pn-cab__nav .pn-cab__entrada'));
        expect(enlaces.map((e) => e.textContent?.trim())).toEqual(nombres);
        expect(enlaces.every((e) => e.tagName === 'A' && !e.hasAttribute('aria-haspopup'))).toBeTrue();
        expect(enlaces[0].getAttribute('aria-current')).toBe('page');
        expect(el().querySelector('.pn-cab__marca')?.getAttribute('href')).toBe(misGrupos ? '/mis-grupos' : '/mi-caja');
        expect(refrescar).not.toHaveBeenCalled();
        expect(el().querySelector('.pn-contador')).toBeNull();
        expect(Array.from(el().querySelectorAll('.pn-barra__nombre')).map((e) => e.textContent?.trim())).toEqual(nombres);
        expect(el().querySelector('.pn-barra__mas')).toBeNull();

        el().querySelector<HTMLButtonElement>('#pn-cuenta-boton')!.click();
        fixture.detectChanges();
        const cuenta = el().querySelector<HTMLElement>('#pn-menu-cuenta')!;
        expect(cuenta.querySelector('.pn-menu__quien-rol')?.textContent?.trim()).toBe('Profesor');
        expect(cuenta.querySelector('a[href="/politica-privacidad"]')).not.toBeNull();
        const botones = Array.from(cuenta.querySelectorAll<HTMLButtonElement>('button'));
        expect(botones.some((b) => b.textContent?.trim() === 'Configurar cookies')).toBeTrue();
        expect(botones.some((b) => /diseño/i.test(b.textContent ?? ''))).withContext('el profesor no puede cambiar de diseño').toBeFalse();
        botones.find((b) => b.textContent?.trim() === 'Cerrar sesión')!.click();
        expect(logout).toHaveBeenCalled();
      });
    }

    itSinReloj('solo-profesor: Mi caja se marca al navegar, sin pedir solicitudes', async () => {
      roles = ['ROLE_PROFESOR'];
      datosPermisos = permisos({ roles: ['PROFESOR'], capacidades: ['MIS_GRUPOS'] });
      await montar('/mis-grupos/4');
      responderMarca();
      await router.navigateByUrl('/mi-caja?mes=10');
      fixture.detectChanges();
      expect(entrada('grupos').hasAttribute('aria-current')).toBeFalse();
      expect(entrada('tesoreria').getAttribute('aria-current')).toBe('page');
      expect(refrescar).not.toHaveBeenCalled();
    });

    itSinReloj('gestor y profesor mantiene Mis grupos y Mi caja en sus desplegables', async () => {
      roles = ['ROLE_MANAGER', 'ROLE_PROFESOR'];
      datosPermisos = permisos({ roles: ['ADMINISTRADOR', 'PROFESOR'] });
      await montar();
      responderMarca();
      expect(el().querySelectorAll('.pn-cab__nav .pn-cab__entrada').length).toBe(6);
      entrada('grupos').click();
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-grupos a[href="/mis-grupos"]')).not.toBeNull();
      entrada('tesoreria').click();
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-tesoreria a[href="/mi-caja"]')).not.toBeNull();
      expect(el().querySelector('.pn-barra__mas')).not.toBeNull();
    });

    itSinReloj('seis con icono y nombre; cuatro con desplegable (aria-haspopup y aria-expanded)', async () => {
      await montar();
      responderMarca();
      const entradas = Array.from(el().querySelectorAll<HTMLElement>('nav.pn-cab__nav .pn-cab__entrada'));
      expect(entradas.map((e) => e.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
        'Inicio',
        'Alumnos',
        'Grupos',
        'Tesorería',
        'Solicitudes',
        'Escuela',
      ]);
      entradas.forEach((e) => expect(e.querySelector('i.bi')).not.toBeNull());
      const conMenu = entradas.filter((e) => e.getAttribute('aria-haspopup') === 'menu');
      expect(conMenu.map((e) => e.dataset['seccion'])).toEqual(['alumnos', 'grupos', 'tesoreria', 'escuela']);
      conMenu.forEach((e) => expect(e.getAttribute('aria-expanded')).toBe('false'));
    });

    itSinReloj('aria-current en la entrada actual', async () => {
      await montar('/alumnosEditar/4');
      responderMarca();
      expect(entrada('alumnos').getAttribute('aria-current')).toBe('true');
      expect(entrada('inicio').hasAttribute('aria-current')).toBeFalse();

      await router.navigateByUrl('/adminpage');
      fixture.detectChanges();
      expect(entrada('inicio').getAttribute('aria-current')).toBe('page');
      expect(entrada('alumnos').hasAttribute('aria-current')).toBeFalse();
    });

    itSinReloj('menú con teclado: flechas, Inicio/Fin y Esc, que cierra y devuelve el foco', async () => {
      await montar();
      responderMarca();
      const disparador = entrada('alumnos');
      disparador.focus();

      tecla(disparador, 'ArrowDown');
      fixture.detectChanges();
      const menu = el().querySelector<HTMLElement>('#pn-menu-alumnos')!;
      expect(disparador.getAttribute('aria-expanded')).toBe('true');
      expect(menu.getAttribute('role')).toBe('menu');
      const items = Array.from(menu.querySelectorAll<HTMLElement>('[role="menuitem"]'));
      expect(items.length).toBeGreaterThan(3);
      expect(document.activeElement).toBe(items[0]);

      tecla(items[0], 'ArrowDown');
      expect(document.activeElement).toBe(items[1]);
      tecla(items[1], 'ArrowUp');
      tecla(items[0], 'ArrowUp');
      expect(document.activeElement).withContext('vuelve al último').toBe(items[items.length - 1]);
      tecla(items[items.length - 1], 'Home');
      expect(document.activeElement).toBe(items[0]);
      tecla(items[0], 'End');
      expect(document.activeElement).toBe(items[items.length - 1]);

      tecla(items[items.length - 1], 'Escape');
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-alumnos')).toBeNull();
      expect(disparador.getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(disparador);
    });

    itSinReloj('aria-current en la opción de la página actual, y solo en ella', async () => {
      await montar('/alumnosListar');
      responderMarca();
      entrada('alumnos').click();
      fixture.detectChanges();
      const opciones = Array.from(el().querySelectorAll<HTMLElement>('#pn-menu-alumnos [role="menuitem"]'));
      const actuales = opciones.filter((o) => o.hasAttribute('aria-current'));
      expect(actuales.map((o) => o.getAttribute('href'))).toEqual(['/alumnosListar']);
      expect(actuales[0].getAttribute('aria-current')).toBe('page');
    });

    itSinReloj('flechas a los lados entre entradas; un clic fuera cierra el menú', async () => {
      await montar();
      responderMarca();
      entrada('grupos').focus();
      tecla(entrada('grupos'), 'ArrowRight');
      expect(document.activeElement).toBe(entrada('tesoreria'));
      tecla(entrada('tesoreria'), 'ArrowLeft');
      expect(document.activeElement).toBe(entrada('grupos'));

      entrada('grupos').click();
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-grupos')).not.toBeNull();
      document.body.click();
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-grupos')).toBeNull();
    });

    itSinReloj('el propietario de Moiskimdo (ADMIN y PROFESOR con grupos) ve «Mi caja» y «Mis grupos»', async () => {
      await montar();
      responderMarca();
      entrada('grupos').click();
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-grupos a[href="/mis-grupos"]')).not.toBeNull();
      entrada('tesoreria').click();
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-tesoreria a[href="/mi-caja"]')).not.toBeNull();
      entrada('escuela').click();
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-escuela a[href="/auditoriaSistema"]')).not.toBeNull();
    });

    itSinReloj('un gestor sin profesor no ve «Mi caja», «Mis grupos» ni lo solo del propietario', async () => {
      roles = ['ROLE_MANAGER'];
      datosPermisos = permisos({ roles: ['ADMINISTRADOR'], capacidades: [] });
      await montar();
      responderMarca();
      for (const id of ['grupos', 'tesoreria', 'escuela']) {
        entrada(id).click();
        fixture.detectChanges();
      }
      expect(el().querySelector('a[href="/mis-grupos"]')).toBeNull();
      expect(el().querySelector('a[href="/mi-caja"]')).toBeNull();
      expect(el().querySelector('a[href="/auditoriaSistema"]')).toBeNull();
      expect(el().querySelector('a[href="/configuracion-sistema"]')).toBeNull();
    });
  });

  describe('entrada «Sedes» (E9-3): solo con la capacidad SEDES', () => {
    for (const [nombre, capacidades, sale] of [
      ['con SEDES', ['SEDES'], true],
      ['sin SEDES', [], false],
    ] as const) {
      itSinReloj(`${nombre}: ${sale ? 'sale' : 'no sale'} en el desplegable de Escuela, antes de Configuración, y en «Más»`, async () => {
        roles = ['ROLE_ADMIN'];
        datosPermisos = permisos({ roles: ['ADMINISTRADOR'], capacidades: [...capacidades] });
        await montar();
        responderMarca();
        entrada('escuela').click();
        fixture.detectChanges();
        const enlaces = Array.from(el().querySelectorAll<HTMLAnchorElement>('#pn-menu-escuela a')).map((a) => a.getAttribute('href'));
        if (sale) {
          expect(enlaces.indexOf('/sedes')).toBeGreaterThan(-1);
          expect(enlaces.indexOf('/sedes')).toBeLessThan(enlaces.indexOf('/configuracion-sistema'));
        } else {
          expect(enlaces).not.toContain('/sedes');
        }
        el().querySelector<HTMLElement>('nav.pn-barra .pn-barra__mas')!.click();
        fixture.detectChanges();
        const hoja = el().querySelector<HTMLElement>('#pn-menu-mas')!;
        expect(hoja.querySelector('a[href="/sedes"]') !== null).toBe(sale);
      });
    }
  });

  describe('contador de solicitudes', () => {
    itSinReloj('se refresca al entrar y al navegar, y se ve en la entrada', async () => {
      await montar();
      responderMarca();
      expect(refrescar).toHaveBeenCalledTimes(1);

      total.set(3);
      fixture.detectChanges();
      const solicitudes = entrada('solicitudes');
      expect(solicitudes.querySelector('.pn-contador')?.textContent?.trim()).toBe('3');
      expect(solicitudes.getAttribute('aria-label')).toBe('Solicitudes: 3 por atender');

      await router.navigateByUrl('/tesoreriaCobros');
      expect(refrescar).toHaveBeenCalledTimes(2);

      total.set(140);
      fixture.detectChanges();
      expect(solicitudes.querySelector('.pn-contador')?.textContent?.trim()).toBe('99+');
    });

    itSinReloj('tras destruir la cabecera, navegar no refresca el contador', async () => {
      await montar();
      responderMarca();
      expect(refrescar).toHaveBeenCalledTimes(1);
      fixture.destroy();

      await router.navigateByUrl('/tesoreriaCobros');

      expect(refrescar).toHaveBeenCalledTimes(1);
    });
  });

  describe('cuenta', () => {
    function abrirCuenta(): HTMLElement {
      const boton = el().querySelector<HTMLElement>('#pn-cuenta-boton')!;
      boton.click();
      fixture.detectChanges();
      return el().querySelector<HTMLElement>('#pn-menu-cuenta')!;
    }

    itSinReloj('iniciales y nombre visible', async () => {
      await montar();
      responderMarca();
      const boton = el().querySelector<HTMLElement>('#pn-cuenta-boton')!;
      expect(boton.querySelector('.pn-cab__iniciales')?.textContent?.trim()).toBe('CS');
      expect(boton.querySelector('.pn-cab__cuenta-nombre')?.textContent?.trim()).toBe('Carla S.');
      expect(boton.getAttribute('aria-haspopup')).toBe('menu');
    });

    itSinReloj('«Mi perfil» en la cuenta del propietario y del gestor', async () => {
      for (const rolesCaso of [['ROLE_ADMIN'], ['ROLE_MANAGER']]) {
        TestBed.resetTestingModule();
        roles = rolesCaso;
        await montar();
        responderMarca();
        const enlace = abrirCuenta().querySelector<HTMLAnchorElement>('a[href="/panel/mi-perfil"]');
        expect(enlace?.textContent?.trim()).withContext(rolesCaso.join()).toBe('Mi perfil');
        http.verify();
      }
    });

    itSinReloj('«Mi perfil» no sale en la cuenta de un solo-profesor', async () => {
      roles = ['ROLE_PROFESOR'];
      datosPermisos = permisos({ roles: ['PROFESOR'], capacidades: ['MI_CAJA'] });
      await montar('/mi-caja');
      responderMarca();
      expect(abrirCuenta().querySelector('a[href="/panel/mi-perfil"]')).toBeNull();
    });

    itSinReloj('el nombre del botón se actualiza tras `refrescado$`, sin recargar', async () => {
      await montar();
      responderMarca();
      const nombre = () => el().querySelector('#pn-cuenta-boton .pn-cab__cuenta-nombre')?.textContent?.trim();
      expect(nombre()).toBe('Carla S.');
      datosPermisos = permisos({ nombreVisible: 'Luci' });
      refrescadoPermisos.next();
      fixture.detectChanges();
      expect(nombre()).toBe('Luci');
    });

    itSinReloj('enlaces legales y «Configurar cookies»', async () => {
      await montar();
      responderMarca();
      let configurar = 0;
      fixture.componentInstance.configurarCookies.subscribe(() => configurar++);
      const menu = abrirCuenta();
      for (const ruta of ['/politica-privacidad', '/politica-cookies', '/aviso-legal']) {
        expect(menu.querySelector(`a[href="${ruta}"]`)).withContext(ruta).not.toBeNull();
      }
      const boton = Array.from(menu.querySelectorAll<HTMLButtonElement>('button')).find(
        (b) => b.textContent?.trim() === 'Configurar cookies'
      )!;
      boton.click();
      expect(configurar).toBe(1);
    });

    itSinReloj('Esc en el menú de cuenta lo cierra y devuelve el foco al botón', async () => {
      await montar();
      responderMarca();
      const boton = el().querySelector<HTMLElement>('#pn-cuenta-boton')!;
      boton.focus();
      tecla(boton, 'ArrowDown');
      fixture.detectChanges();
      const primera = el().querySelector<HTMLElement>('#pn-menu-cuenta [role="menuitem"]')!;
      expect(document.activeElement).toBe(primera);

      tecla(primera, 'Escape');
      fixture.detectChanges();

      expect(el().querySelector('#pn-menu-cuenta')).toBeNull();
      expect(boton.getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(boton);
    });

    itSinReloj('«Cambiar a familia» solo con acceso doble, como «Cambiar a usuario» de hoy', async () => {
      roles = ['ROLE_ADMIN', 'ROLE_USER'];
      await montar();
      responderMarca();
      const auth = TestBed.inject(AuthenticationService) as unknown as { guardarVistaPreferida: jasmine.Spy };
      const boton = Array.from(abrirCuenta().querySelectorAll<HTMLButtonElement>('button')).find(
        (b) => b.textContent?.trim() === 'Cambiar a familia'
      );
      expect(boton).toBeDefined();
      expect(boton!.closest('[role="group"]')?.getAttribute('aria-label')).toBe('Panel');
      boton!.click();
      expect(auth.guardarVistaPreferida).toHaveBeenCalledWith('user');
    });

    for (const rolesSesion of [['ROLE_ADMIN'], ['ROLE_MANAGER']]) {
      itSinReloj(`8b-3o: ${rolesSesion.join(', ')} ve el grupo Panel solo con «Mi perfil»`, async () => {
        roles = rolesSesion;
        await montar();
        responderMarca();
        const menu = abrirCuenta();
        const panel = menu.querySelector('[role="group"][aria-label="Panel"]')!;
        expect(Array.from(panel.querySelectorAll('[role="menuitem"]')).map((o) => o.textContent?.trim())).toEqual(['Mi perfil']);
        expect(Array.from(menu.querySelectorAll('.pn-menu__grupo')).map((g) => g.getAttribute('aria-label')))
          .toEqual(['Panel', 'Legal', 'Sesión']);
        expect(menu.querySelector('.pn-menu__titulo')?.textContent?.trim()).toBe('Legal');
        const botones = Array.from(menu.querySelectorAll<HTMLButtonElement>('button'));
        expect(botones.some((b) => /diseño/i.test(b.textContent ?? ''))).toBeFalse();
        expect(menu.querySelector('[role="alert"]')).toBeNull();
        expect(recargar).not.toHaveBeenCalled();
      });
    }

    itSinReloj('«Cerrar sesión» cierra la sesión', async () => {
      await montar();
      responderMarca();
      const boton = Array.from(abrirCuenta().querySelectorAll<HTMLButtonElement>('button')).find(
        (b) => b.textContent?.trim() === 'Cerrar sesión'
      )!;
      boton.click();
      expect(logout).toHaveBeenCalled();
    });

    itSinReloj('«Cerrar sesión» sin SweetAlert2: lleva a la web y no monta los avisos del panel nuevo fuera de él (8b-D2a)', async () => {
      await montar();
      responderMarca();
      const fire = spyOn(Swal, 'fire').and.callThrough();
      const logueado = TestBed.inject(AuthenticationService).usuarioLogueadoCambio as BehaviorSubject<boolean>;
      logout.and.callFake(() => logueado.next(false));
      const navegar = spyOn(router, 'navigate').and.resolveTo(true);
      Array.from(abrirCuenta().querySelectorAll<HTMLButtonElement>('button'))
        .find((b) => b.textContent?.trim() === 'Cerrar sesión')!
        .click();
      await fixture.whenStable();
      expect(navegar).toHaveBeenCalledWith(['/']);
      expect(fire).not.toHaveBeenCalled();
      expect(document.querySelector('pn-avisos, .pn-aviso')).toBeNull();
    });
  });

  describe('barra inferior', () => {
    itSinReloj('«Más» lleva aria-current cuando la página no está en la barra (Grupos, Solicitudes, Escuela)', async () => {
      await montar('/solicitudes');
      responderMarca();
      const mas = el().querySelector<HTMLElement>('.pn-barra__mas')!;
      expect(mas.getAttribute('aria-current')).toBe('true');

      await router.navigateByUrl('/alumnosListar');
      fixture.detectChanges();
      expect(mas.hasAttribute('aria-current')).toBeFalse();
    });

    itSinReloj('Inicio, Alumnos, Tesorería y Más; «Más» lleva todo, lo legal y «Configurar cookies»', async () => {
      await montar('/tesoreriaCobros');
      responderMarca();
      total.set(2);
      fixture.detectChanges();
      const barra = el().querySelector<HTMLElement>('nav.pn-barra')!;
      expect(barra).not.toBeNull();
      const nombres = Array.from(barra.querySelectorAll('.pn-barra__entrada .pn-barra__nombre')).map((e) =>
        e.textContent?.trim()
      );
      expect(nombres).toEqual(['Inicio', 'Alumnos', 'Tesorería', 'Más']);
      expect(barra.querySelector('.pn-barra__mas .pn-contador')?.textContent?.trim()).toBe('2');
      expect(barra.querySelector('.pn-barra__mas')?.getAttribute('aria-label')).toBe('Más: 2 solicitudes por atender');
      expect(barra.querySelector('a[href="/tesoreriaCobros"]')?.getAttribute('aria-current')).toBe('page');

      const mas = barra.querySelector<HTMLElement>('.pn-barra__mas')!;
      expect(mas.getAttribute('aria-expanded')).toBe('false');
      mas.focus();
      mas.click();
      fixture.detectChanges();
      expect(mas.getAttribute('aria-expanded')).toBe('true');
      const hoja = el().querySelector<HTMLElement>('#pn-menu-mas')!;
      for (const ruta of [
        '/adminpage',
        '/alumnosListar',
        '/preinscripciones',
        '/gruposListar',
        '/mis-grupos',
        '/tesoreriaCobros',
        '/mi-caja',
        '/solicitudes',
        '/eventosListar',
        '/configuracion-sistema',
        '/politica-privacidad',
        '/politica-cookies',
        '/aviso-legal',
      ]) {
        expect(hoja.querySelector(`a[href="${ruta}"]`)).withContext(ruta).not.toBeNull();
      }
      expect(Array.from(hoja.querySelectorAll('button')).some((b) => b.textContent?.trim() === 'Configurar cookies')).toBeTrue();

      expect(Number(getComputedStyle(hoja).zIndex)).withContext('la hoja, por encima del botón de subir (1000)').toBeGreaterThan(1000);
      // La hoja vive dentro de la barra (contexto de apilamiento propio): la barra también ha de quedar por encima.
      expect(Number(getComputedStyle(barra).zIndex)).withContext('la barra, por encima del botón de subir (1000)').toBeGreaterThan(1000);

      const primero = hoja.querySelector<HTMLElement>('[role="menuitem"]')!;
      tecla(primero, 'Escape');
      fixture.detectChanges();
      expect(el().querySelector('#pn-menu-mas')).toBeNull();
      expect(document.activeElement).toBe(mas);
    });
  });
});
