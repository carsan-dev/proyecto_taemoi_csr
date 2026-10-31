import { Location } from '@angular/common';
import { Component } from '@angular/core';
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, NEVER, Observable, of, throwError } from 'rxjs';

import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { PermisosSesion, PermisosSesionService } from '../../../../servicios/authentication/permisos-sesion.service';
import { EstilosPanelService } from '../../../../panel-nuevo/estilos-panel.service';
import { HeaderComponent } from '../header/header.component';
import { RECORDATORIO_PANEL, SkeletonComponent, esRutaConocida, esRutaDelPanel } from './skeleton.component';
import { itSinReloj } from '../../../../testing/reloj-prueba';

@Component({ selector: 'app-header', standalone: true, template: '' })
class HeaderFalsoComponent {}

@Component({ selector: 'app-botonscroll', standalone: true, template: '' })
class BotonscrollFalsoComponent {}

@Component({ standalone: true, template: '' })
class PantallaFalsaComponent {}

function permisos(roles: string[] = ['PROPIETARIO', 'PROFESOR']): PermisosSesion {
  return {
    roles,
    esAdminPlataforma: false,
    sedes: [],
    variasSedes: false,
    tieneGrupos: false,
    capacidades: [],
  };
}

/**
 * Cabecera del panel nuevo en `skeleton` (8b-N, fase3-e8b-plan.md §3 «Cabecera» y §8). Desde 8b-Z sin interruptor: una
 * sesión de gestión o de profesor en una ruta del panel ve la cabecera nueva (`@defer`) y sin pie; con error de permisos,
 * sin la hoja, una sesión de familia o en las rutas públicas y de familia, la cabecera y el pie de hoy.
 */
describe('SkeletonComponent · cabecera del panel nuevo (8b-N)', () => {
  let obtener: jasmine.Spy<() => Observable<PermisosSesion>>;
  let cargarHoja: jasmine.Spy<() => Promise<void>>;
  let sesion: BehaviorSubject<boolean>;

  async function montar(ruta: string): Promise<HTMLElement & { detectar: () => void }> {
    TestBed.configureTestingModule({
      imports: [SkeletonComponent],
      providers: [
        provideRouter([{ path: '**', component: PantallaFalsaComponent }]),
        { provide: PermisosSesionService, useValue: { obtener } },
        { provide: EstilosPanelService, useValue: { cargar: cargarHoja } },
        { provide: AuthenticationService, useValue: { usuarioLogueadoCambio: sesion, tieneAccesoAdmin: () => false, tieneRolResponsable: () => false, esSoloProfesor: () => false } },
      ],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    TestBed.overrideComponent(SkeletonComponent, {
      remove: { imports: [HeaderComponent, BotonscrollComponent] },
      add: { imports: [HeaderFalsoComponent, BotonscrollFalsoComponent] },
    });
    await TestBed.inject(Router).navigateByUrl(ruta);
    const fixture = TestBed.createComponent(SkeletonComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const raiz = fixture.nativeElement as HTMLElement & { detectar: () => void };
    raiz.detectar = () => fixture.detectChanges();
    (raiz as any).fixture = fixture;
    return raiz;
  }

  const cabeceraDeHoy = (raiz: HTMLElement) => raiz.querySelector('app-header');
  const pie = (raiz: HTMLElement) => raiz.querySelector('app-footer');
  const marco = (raiz: HTMLElement) => raiz.querySelector('.container-fluid')!;

  beforeEach(() => {
    obtener = jasmine.createSpy('obtener');
    cargarHoja = jasmine.createSpy('cargar').and.resolveTo();
    sesion = new BehaviorSubject<boolean>(true);
    localStorage.removeItem(RECORDATORIO_PANEL);
  });

  afterEach(() => {
    localStorage.removeItem(RECORDATORIO_PANEL);
  });

  function esperarHoy(raiz: HTMLElement): void {
    expect(cabeceraDeHoy(raiz)).withContext('cabecera de hoy').not.toBeNull();
    expect(pie(raiz)).withContext('pie de hoy').not.toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeFalse();
    expect(marco(raiz).classList.contains('admin-ui')).toBeFalse();
  }

  function esperarNuevo(raiz: HTMLElement): void {
    expect(cabeceraDeHoy(raiz)).withContext('sin cabecera de hoy').toBeNull();
    expect(pie(raiz)).withContext('sin pie').toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
  }

  itSinReloj('gestión en una ruta del panel: la cabecera nueva', async () => {
    obtener.and.returnValue(of(permisos()));
    esperarNuevo(await montar('/adminpage'));
    expect(cargarHoja).toHaveBeenCalledTimes(1);
  });

  itSinReloj('error de permisos: lo de hoy', async () => {
    obtener.and.returnValue(throwError(() => new Error('500')));
    esperarHoy(await montar('/alumnosListar'));
  });

  itSinReloj('la hoja panel-nuevo.css no carga: lo de hoy', async () => {
    obtener.and.returnValue(of(permisos()));
    cargarHoja.and.rejectWith(new Error('hoja'));
    esperarHoy(await montar('/adminpage'));
  });

  itSinReloj('la hoja nunca resuelve: sale del hueco al tope de estilos (reloj simulado)', async () => {
    obtener.and.returnValue(of(permisos()));
    cargarHoja.and.returnValue(new Promise<void>(() => {}));
    const raiz = await montar('/adminpage');
    esperarNuevo(raiz);
  });

  for (const ruta of ['/mi-caja', '/mis-grupos', '/mis-grupos/4']) {
    itSinReloj(`en la sesión solo-profesor (decisión 326, R6): marco nuevo en ${ruta}`, async () => {
      obtener.and.returnValue(of(permisos(['PROFESOR'])));
      const raiz = await montar(ruta);
      expect(cabeceraDeHoy(raiz)).toBeNull();
      expect(pie(raiz)).toBeNull();
      expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
      expect(cargarHoja).toHaveBeenCalledTimes(1);
    });
  }

  itSinReloj('profesor en una ruta del panel: el marco nuevo', async () => {
    obtener.and.returnValue(of(permisos(['PROFESOR'])));
    esperarNuevo(await montar('/mis-grupos'));
  });

  itSinReloj('sesión de plataforma sin membresía de gestión ni de profesor: lo de hoy en sus pantallas', async () => {
    obtener.and.returnValue(of(permisos([])));
    esperarHoy(await montar('/plataformaErrores'));
  });

  itSinReloj('solo familia: no abre el marco nuevo', async () => {
    obtener.and.returnValue(of(permisos(['FAMILIA'])));
    esperarHoy(await montar('/mi-caja'));
    expect(cargarHoja).not.toHaveBeenCalled();
  });

  itSinReloj('en una ruta pública: la cabecera y el pie de hoy, sin pedir permisos', async () => {
    obtener.and.returnValue(of(permisos()));
    esperarHoy(await montar('/'));
    expect(obtener).not.toHaveBeenCalled();
  });

  itSinReloj('en una ruta de familia (/userpage): la cabecera y el pie de hoy', async () => {
    obtener.and.returnValue(of(permisos()));
    esperarHoy(await montar('/userpage'));
  });

  itSinReloj('en gestión: la cabecera nueva en @defer, raíz del panel y sin pie', async () => {
    obtener.and.returnValue(of(permisos()));
    const raiz = await montar('/adminpage');

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
    expect(marco(raiz).classList.contains('admin-ui')).withContext('estilos compartidos de las pantallas de hoy').toBeTrue();
    expect(cargarHoja).toHaveBeenCalledTimes(1);
    expect(raiz.querySelector('main#content')).not.toBeNull();
    const fixture = (raiz as any).fixture;
    expect((await fixture.getDeferBlocks()).length).toBe(1);
  });

  itSinReloj('el botón «volver arriba» solo con el diseño de hoy: en gestión no está (taparía la barra inferior)', async () => {
    obtener.and.returnValue(of(permisos()));
    const nuevo = await montar('/adminpage');
    expect(nuevo.querySelector('app-botonscroll')).toBeNull();
    TestBed.resetTestingModule();
    obtener.and.returnValue(of(permisos(['FAMILIA'])));
    const hoy = await montar('/adminpage');
    expect(hoy.querySelector('app-botonscroll')).not.toBeNull();
  });

  itSinReloj('primera visita sin recordatorio en una ruta de gestión: el hueco, nunca la cabecera de hoy', async () => {
    obtener.and.returnValue(NEVER);
    const raiz = await montar('/alumnosListar');

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBeNull();
  });

  itSinReloj('con el recordatorio de sesión del panel y sin respuesta todavía: el hueco, ni la cabecera de hoy ni el pie', async () => {
    localStorage.setItem(RECORDATORIO_PANEL, '1');
    obtener.and.returnValue(NEVER);
    const raiz = await montar('/alumnosListar');

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
  });

  itSinReloj('con el recordatorio y una sesión que no es del panel (familia): lo de hoy, y se borra el recordatorio', async () => {
    localStorage.setItem(RECORDATORIO_PANEL, '1');
    obtener.and.returnValue(of(permisos(['FAMILIA'])));
    esperarHoy(await montar('/adminpage'));
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBeNull();
  });

  itSinReloj('sesión del panel: se guarda el recordatorio (un booleano global, sin cuenta)', async () => {
    obtener.and.returnValue(of(permisos()));
    await montar('/adminpage');
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBe('1');
  });

  itSinReloj('error de permisos: ni se escribe ni se borra el recordatorio', async () => {
    localStorage.setItem(RECORDATORIO_PANEL, '1');
    obtener.and.returnValue(throwError(() => new Error('500')));
    esperarHoy(await montar('/adminpage'));
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBe('1');
  });

  itSinReloj('si localStorage lanza, no rompe: sin recordatorio y con la respuesta de siempre', async () => {
    const espias = [
      spyOn(Storage.prototype, 'getItem').and.throwError('bloqueado'),
      spyOn(Storage.prototype, 'setItem').and.throwError('bloqueado'),
      spyOn(Storage.prototype, 'removeItem').and.throwError('bloqueado'),
    ];
    obtener.and.returnValue(of(permisos()));
    const raiz = await montar('/adminpage');
    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();

    obtener.and.returnValue(NEVER);
    sesion.next(false);
    raiz.detectar();
    esperarHoy(raiz);
    espias.forEach((e) => e.and.callThrough());
  });

  itSinReloj('al cerrar sesión se borra el recordatorio y vuelve lo de hoy', async () => {
    obtener.and.returnValue(of(permisos()));
    const raiz = await montar('/adminpage');
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBe('1');

    obtener.and.returnValue(NEVER);
    sesion.next(false);
    raiz.detectar();

    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBeNull();
    esperarHoy(raiz);
  });

  itSinReloj('cerrar sesión y volver a entrar sin recargar: el hueco en gestión, no la cabecera de hoy', async () => {
    obtener.and.returnValue(of(permisos()));
    const raiz = await montar('/adminpage');
    expect(cabeceraDeHoy(raiz)).toBeNull();

    obtener.and.returnValue(NEVER);
    sesion.next(false);
    raiz.detectar();
    esperarHoy(raiz);

    sesion.next(true);
    await TestBed.inject(Router).navigateByUrl('/alumnosListar');
    raiz.detectar();

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
  });

  itSinReloj('con el marco nuevo, una emisión repetida de «con sesión» no vuelve al hueco', async () => {
    obtener.and.returnValue(of(permisos()));
    const raiz = await montar('/adminpage');
    const fixture = (raiz as any).fixture;
    expect((await fixture.getDeferBlocks()).length).toBe(1);

    obtener.and.returnValue(NEVER);
    sesion.next(true);
    raiz.detectar();

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
    expect((await fixture.getDeferBlocks()).length).withContext('sigue el marco nuevo, no el hueco').toBe(1);
  });

  itSinReloj('se vuelve a leer al navegar a una ruta del panel (tras reiniciar los permisos)', async () => {
    obtener.and.returnValue(of(permisos(['FAMILIA'])));
    const raiz = await montar('/adminpage');
    esperarHoy(raiz);

    obtener.and.returnValue(of(permisos()));
    await TestBed.inject(Router).navigateByUrl('/alumnosListar');
    await (raiz as any).fixture.whenStable();
    raiz.detectar();

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
  });

  itSinReloj('gestión: al pasar a una ruta pública vuelve lo de hoy, y al volver, lo nuevo', async () => {
    obtener.and.returnValue(of(permisos()));
    const raiz = await montar('/adminpage');
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/horarios');
    raiz.detectar();
    esperarHoy(raiz);

    await router.navigateByUrl('/tesoreriaCobros');
    raiz.detectar();
    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
  });

  describe('carga directa, antes del primer NavigationEnd', () => {
    /**
     * Monta la cáscara con la barra en `ruta` sin haber navegado todavía (F5 o URL pegada): el router de la prueba
     * tiene el comodín del panel (`data.panelNuevo`) para cuando se navegue.
     */
    function montarSinNavegar(ruta: string): HTMLElement & { detectar: () => void; fixture: any } {
      TestBed.configureTestingModule({
        imports: [SkeletonComponent],
        providers: [
          provideRouter([
            { path: '', component: PantallaFalsaComponent },
            { path: '**', component: PantallaFalsaComponent, data: { panelNuevo: true } },
          ]),
          { provide: PermisosSesionService, useValue: { obtener } },
          { provide: EstilosPanelService, useValue: { cargar: cargarHoja } },
          { provide: AuthenticationService, useValue: { usuarioLogueadoCambio: sesion, tieneAccesoAdmin: () => false, tieneRolResponsable: () => false, esSoloProfesor: () => false } },
        ],
        deferBlockBehavior: DeferBlockBehavior.Manual,
      });
      TestBed.overrideComponent(SkeletonComponent, {
        remove: { imports: [HeaderComponent, BotonscrollComponent] },
        add: { imports: [HeaderFalsoComponent, BotonscrollFalsoComponent] },
      });
      spyOn(TestBed.inject(Location), 'path').and.returnValue(ruta);
      const fixture = TestBed.createComponent(SkeletonComponent);
      fixture.detectChanges();
      const raiz = fixture.nativeElement as HTMLElement & { detectar: () => void; fixture: any };
      raiz.detectar = () => fixture.detectChanges();
      raiz.fixture = fixture;
      return raiz;
    }

    itSinReloj('ruta de gestión sin recordatorio, antes del NavigationEnd: ni un fotograma de la cabecera de hoy', async () => {
      obtener.and.returnValue(NEVER);
      const raiz = montarSinNavegar('/adminpage');

      expect(cabeceraDeHoy(raiz)).toBeNull();
      expect(pie(raiz)).toBeNull();
      expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
    });

    itSinReloj('sin recordatorio, permisos de gestión y hoja aún cargando: el hueco, no lo de hoy', async () => {
      obtener.and.returnValue(of(permisos()));
      cargarHoja.and.returnValue(new Promise<void>(() => {}));
      const raiz = montarSinNavegar('/alumnosListar');

      expect(cabeceraDeHoy(raiz)).toBeNull();
      expect(pie(raiz)).toBeNull();
    });

    itSinReloj('URL inexistente con el recordatorio: ni un fotograma de la cabecera de hoy, y tras navegar sigue sin ella', async () => {
      localStorage.setItem(RECORDATORIO_PANEL, '1');
      obtener.and.returnValue(NEVER);
      const raiz = montarSinNavegar('/no-existe');

      expect(cabeceraDeHoy(raiz)).withContext('antes del NavigationEnd').toBeNull();
      expect(pie(raiz)).toBeNull();
      expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();

      await TestBed.inject(Router).navigateByUrl('/no-existe');
      raiz.detectar();
      expect(cabeceraDeHoy(raiz)).withContext('404 del panel, sesión aún sin respuesta').toBeNull();
      expect(obtener).toHaveBeenCalled();
    });

    itSinReloj('URL inexistente con el recordatorio que resuelve a la 404 pública: vuelve lo de hoy al navegar', async () => {
      localStorage.setItem(RECORDATORIO_PANEL, '1');
      const raiz = montarSinNavegar('/no-existe');
      expect(cabeceraDeHoy(raiz)).toBeNull();

      TestBed.inject(Router).resetConfig([{ path: '**', component: PantallaFalsaComponent }]);
      await TestBed.inject(Router).navigateByUrl('/no-existe');
      raiz.detectar();
      esperarHoy(raiz);
    });

    itSinReloj('«/» con el recordatorio: la cabecera de hoy desde el primer fotograma', async () => {
      localStorage.setItem(RECORDATORIO_PANEL, '1');
      esperarHoy(montarSinNavegar('/'));
      expect(obtener).not.toHaveBeenCalled();
    });

    itSinReloj('ruta pública o de familia conocida con el recordatorio: la cabecera de hoy desde el primer fotograma', async () => {
      localStorage.setItem(RECORDATORIO_PANEL, '1');
      esperarHoy(montarSinNavegar('/horarios'));
      TestBed.resetTestingModule();
      esperarHoy(montarSinNavegar('/userpage/turnos?x=1'));
    });

    itSinReloj('URL inexistente sin recordatorio: lo de hoy desde el primer fotograma, como siempre', async () => {
      esperarHoy(montarSinNavegar('/no-existe'));
      expect(obtener).not.toHaveBeenCalled();
    });
  });
});

describe('esRutaConocida', () => {
  it('las rutas declaradas (con parámetros y con hijas perezosas) sí', () => {
    for (const ruta of ['/', '', '/horarios', '/userpage/turnos', '/login?x=1', '/mis-grupos/4', '/adminpage']) {
      expect(esRutaConocida(ruta)).withContext(ruta).toBeTrue();
    }
  });

  it('las que solo casan con el comodín, no', () => {
    for (const ruta of ['/no-existe', '/horarios/extra', '/adminpage/zzz/yyy']) {
      expect(esRutaConocida(ruta)).withContext(ruta).toBeFalse();
    }
  });
});

describe('esRutaDelPanel', () => {
  it('las rutas de gestión y las del panel sin portal de gestión', () => {
    for (const ruta of ['/adminpage', '/alumnosListar', '/mi-caja', '/mis-grupos', '/mis-grupos/4', '/plataformaErrores', '/plataformaLogos']) {
      expect(esRutaDelPanel(ruta)).withContext(ruta).toBeTrue();
    }
  });

  it('las públicas y las de familia, no', () => {
    for (const ruta of ['/', '', '/horarios', '/userpage', '/userpage/turnos', '/mi-cajas', '/login']) {
      expect(esRutaDelPanel(ruta)).withContext(ruta).toBeFalse();
    }
  });
});
