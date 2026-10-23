import { Component } from '@angular/core';
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, NEVER, Observable, of, throwError } from 'rxjs';

import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { PermisosSesion, PermisosSesionService } from '../../../../servicios/authentication/permisos-sesion.service';
import { EstilosPanelService } from '../../../../panel-nuevo/estilos-panel.service';
import { HeaderComponent } from '../header/header.component';
import { RECORDATORIO_PANEL, SkeletonComponent, esRutaDelPanel } from './skeleton.component';

@Component({ selector: 'app-header', standalone: true, template: '' })
class HeaderFalsoComponent {}

@Component({ selector: 'app-botonscroll', standalone: true, template: '' })
class BotonscrollFalsoComponent {}

@Component({ standalone: true, template: '' })
class PantallaFalsaComponent {}

function permisos(activo: boolean, roles: string[] = ['PROPIETARIO', 'PROFESOR']): PermisosSesion {
  return {
    roles,
    esAdminPlataforma: false,
    sedes: [],
    variasSedes: false,
    tieneGrupos: false,
    capacidades: [],
    disenoNuevo: { activo, permitido: activo },
  };
}

/**
 * Cabecera del panel nuevo en `skeleton` (8b-N, fase3-e8b-plan.md §3 «Cabecera» y §8). Apagado = hoy: sin interruptor
 * o con error, la cabecera y el pie de hoy. Encendido, solo en las rutas de gestión: la cabecera nueva (`@defer`) y sin
 * pie; en las públicas y de familia, las de hoy.
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
        { provide: AuthenticationService, useValue: { usuarioLogueadoCambio: sesion } },
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

  it('apagado en gestión: la cabecera y el pie de hoy, sin cargar la hoja', async () => {
    obtener.and.returnValue(of(permisos(false)));
    const raiz = await montar('/adminpage');
    esperarHoy(raiz);
    expect(cargarHoja).not.toHaveBeenCalled();
  });

  it('error de permisos: lo de hoy', async () => {
    obtener.and.returnValue(throwError(() => new Error('500')));
    esperarHoy(await montar('/alumnosListar'));
  });

  it('sin el campo disenoNuevo (servidor anterior): lo de hoy', async () => {
    const p = permisos(true);
    delete (p as Partial<PermisosSesion>).disenoNuevo;
    obtener.and.returnValue(of(p));
    esperarHoy(await montar('/adminpage'));
  });

  it('encendido pero la hoja panel-nuevo.css no carga: lo de hoy', async () => {
    obtener.and.returnValue(of(permisos(true)));
    cargarHoja.and.rejectWith(new Error('hoja'));
    esperarHoy(await montar('/adminpage'));
  });

  it('encendido en la sesión solo-profesor (decisión 260): lo de hoy', async () => {
    obtener.and.returnValue(of(permisos(true, ['PROFESOR'])));
    esperarHoy(await montar('/mi-caja'));
  });

  it('encendido en una ruta pública: la cabecera y el pie de hoy, sin pedir permisos', async () => {
    obtener.and.returnValue(of(permisos(true)));
    esperarHoy(await montar('/'));
    expect(obtener).not.toHaveBeenCalled();
  });

  it('encendido en una ruta de familia (/userpage): la cabecera y el pie de hoy', async () => {
    obtener.and.returnValue(of(permisos(true)));
    esperarHoy(await montar('/userpage'));
  });

  it('encendido en gestión: la cabecera nueva en @defer, raíz del panel y sin pie', async () => {
    obtener.and.returnValue(of(permisos(true)));
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

  it('el botón «volver arriba» solo con el diseño de hoy: encendido en gestión no está (taparía la barra inferior)', async () => {
    obtener.and.returnValue(of(permisos(true)));
    const nuevo = await montar('/adminpage');
    expect(nuevo.querySelector('app-botonscroll')).toBeNull();
    TestBed.resetTestingModule();
    obtener.and.returnValue(of(permisos(false)));
    const hoy = await montar('/adminpage');
    expect(hoy.querySelector('app-botonscroll')).not.toBeNull();
  });

  it('mientras no se sabe el interruptor y sin recordatorio: la cabecera y el pie de hoy desde el primer píxel', async () => {
    obtener.and.returnValue(NEVER);
    esperarHoy(await montar('/alumnosListar'));
  });

  it('con el recordatorio de encendido y sin respuesta todavía: el hueco, ni la cabecera de hoy ni el pie', async () => {
    localStorage.setItem(RECORDATORIO_PANEL, '1');
    obtener.and.returnValue(NEVER);
    const raiz = await montar('/alumnosListar');

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();
  });

  it('con el recordatorio y la respuesta apagado: lo de hoy, y se borra el recordatorio', async () => {
    localStorage.setItem(RECORDATORIO_PANEL, '1');
    obtener.and.returnValue(of(permisos(false)));
    esperarHoy(await montar('/adminpage'));
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBeNull();
  });

  it('respuesta encendido: se guarda el recordatorio (un booleano global, sin cuenta)', async () => {
    obtener.and.returnValue(of(permisos(true)));
    await montar('/adminpage');
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBe('1');
  });

  it('error de permisos: ni se escribe ni se borra el recordatorio', async () => {
    localStorage.setItem(RECORDATORIO_PANEL, '1');
    obtener.and.returnValue(throwError(() => new Error('500')));
    esperarHoy(await montar('/adminpage'));
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBe('1');
  });

  it('si localStorage lanza, no rompe: sin recordatorio y con la respuesta de siempre', async () => {
    const espias = [
      spyOn(Storage.prototype, 'getItem').and.throwError('bloqueado'),
      spyOn(Storage.prototype, 'setItem').and.throwError('bloqueado'),
      spyOn(Storage.prototype, 'removeItem').and.throwError('bloqueado'),
    ];
    obtener.and.returnValue(of(permisos(true)));
    const raiz = await montar('/adminpage');
    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(marco(raiz).classList.contains('marco-panel')).toBeTrue();

    obtener.and.returnValue(NEVER);
    sesion.next(false);
    raiz.detectar();
    esperarHoy(raiz);
    espias.forEach((e) => e.and.callThrough());
  });

  it('al cerrar sesión se borra el recordatorio y vuelve lo de hoy', async () => {
    obtener.and.returnValue(of(permisos(true)));
    const raiz = await montar('/adminpage');
    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBe('1');

    obtener.and.returnValue(NEVER);
    sesion.next(false);
    raiz.detectar();

    expect(localStorage.getItem(RECORDATORIO_PANEL)).toBeNull();
    esperarHoy(raiz);
  });

  it('se vuelve a leer al navegar a una ruta del panel (tras cambiar el propio diseño y reiniciar los permisos)', async () => {
    obtener.and.returnValue(of(permisos(false)));
    const raiz = await montar('/adminpage');
    esperarHoy(raiz);

    obtener.and.returnValue(of(permisos(true)));
    await TestBed.inject(Router).navigateByUrl('/panel/diseno-nuevo');
    await (raiz as any).fixture.whenStable();
    raiz.detectar();

    expect(cabeceraDeHoy(raiz)).toBeNull();
    expect(pie(raiz)).toBeNull();
  });

  it('encendido: al pasar de gestión a una ruta pública vuelve lo de hoy, y al volver, lo nuevo', async () => {
    obtener.and.returnValue(of(permisos(true)));
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
});

describe('esRutaDelPanel', () => {
  it('las rutas de gestión y las del panel sin portal de gestión', () => {
    for (const ruta of ['/adminpage', '/alumnosListar', '/mi-caja', '/mis-grupos', '/mis-grupos/4', '/plataformaErrores', '/plataformaLogos', '/panel/diseno-nuevo']) {
      expect(esRutaDelPanel(ruta)).withContext(ruta).toBeTrue();
    }
  });

  it('las públicas y las de familia, no', () => {
    for (const ruta of ['/', '', '/horarios', '/userpage', '/userpage/turnos', '/mi-cajas', '/login']) {
      expect(esRutaDelPanel(ruta)).withContext(ruta).toBeFalse();
    }
  });
});
