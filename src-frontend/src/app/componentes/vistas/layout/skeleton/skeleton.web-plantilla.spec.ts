import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../../../servicios/authentication/permisos-sesion.service';
import { EstilosPanelService } from '../../../../panel-nuevo/estilos-panel.service';
import { ponerMetaWebPlantilla, quedaMetaWebPlantilla } from '../../../../testing/meta-web-plantilla';
import { HeaderComponent } from '../header/header.component';
import { SkeletonComponent } from './skeleton.component';

@Component({ selector: 'app-header', standalone: true, template: '' })
class HeaderFalsoComponent {}

@Component({ selector: 'app-botonscroll', standalone: true, template: '' })
class BotonscrollFalsoComponent {}

@Component({ standalone: true, template: '<p class="pagina-plantilla">plantilla</p>' })
class PaginaPlantillaComponent {}

@Component({ standalone: true, template: '<p class="pagina-hoy">hoy</p>' })
class PaginaHoyComponent {}

/** Como `app.component.html`: la cáscara con el router-outlet proyectado. */
@Component({
  standalone: true,
  imports: [SkeletonComponent, RouterOutlet],
  template: '<app-skeleton><router-outlet></router-outlet></app-skeleton>',
})
class RaizComponent {}

/**
 * Cáscara en modo «plantilla» (fase4-web-plan.md §3.2, punto 6): en una ruta con `data.webPlantilla` solo el contenido,
 * sin cabecera, pie, botón de subir ni aviso de cookies, y sin GA4. Con la meta, en ninguna ruta (login, panel…) hay
 * aviso de cookies ni GA4 de Moiskimdo (W4, parte C); la cabecera y el pie siguen como hoy fuera de la plantilla. Sin
 * la meta (Moiskimdo), todo como hoy.
 */
describe('SkeletonComponent · web por plantilla', () => {
  const global = globalThis as any;
  let gtag: jasmine.Spy;
  let gtagAnterior: unknown;
  let consentimientoAnterior: string | null;
  let aceptar: jasmine.Spy;
  let rechazar: jasmine.Spy;
  let cargarScript: jasmine.Spy;
  let quitarMeta: (() => void) | null = null;

  function configurar(): void {
    TestBed.configureTestingModule({
      imports: [RaizComponent],
      providers: [
        provideRouter([
          { path: '', component: PaginaPlantillaComponent, data: { webPlantilla: true } },
          { path: 'login', component: PaginaHoyComponent },
          { path: 'mi-caja', component: PaginaHoyComponent },
          { path: '**', component: PaginaHoyComponent },
        ]),
        {
          provide: AuthenticationService,
          useValue: {
            usuarioLogueadoCambio: new BehaviorSubject(false),
            tieneAccesoAdmin: () => false,
            tieneRolResponsable: () => false,
            esSoloProfesor: () => false,
          },
        },
        // Ruta del panel con una sesión sin panel nuevo: cabecera de hoy, sin pedir nada al servidor.
        { provide: PermisosSesionService, useValue: { obtener: () => of({ roles: [], capacidades: [] }) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    });
    TestBed.overrideComponent(SkeletonComponent, {
      remove: { imports: [HeaderComponent, BotonscrollComponent] },
      add: { imports: [HeaderFalsoComponent, BotonscrollFalsoComponent] },
    });
  }

  /** Como en el arranque real: la cáscara se crea antes de que acabe la primera navegación. */
  async function arrancar(url: string): Promise<ComponentFixture<RaizComponent>> {
    configurar();
    const fixture = TestBed.createComponent(RaizComponent);
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl(url);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  async function navegar(fixture: ComponentFixture<RaizComponent>, url: string): Promise<void> {
    await fixture.ngZone!.run(() => TestBed.inject(Router).navigateByUrl(url));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const raiz = (f: ComponentFixture<RaizComponent>) => f.nativeElement as HTMLElement;
  const avisoCookies = (f: ComponentFixture<RaizComponent>) => raiz(f).querySelector('#cookie-consent');

  /** «Configurar cookies» del pie de hoy. */
  function configurarCookiesDelPie(f: ComponentFixture<RaizComponent>): void {
    const boton = Array.from(raiz(f).querySelectorAll<HTMLButtonElement>('app-footer button')).find((b) =>
      b.textContent?.includes('Configurar cookies')
    );
    expect(boton).withContext('botón «Configurar cookies» del pie').toBeDefined();
    boton!.click();
    f.detectChanges();
  }

  /** Nada de la analítica ni del consentimiento de Moiskimdo. */
  function esperarSinAnalitica(): void {
    expect(aceptar).withContext('acceptCookies').not.toHaveBeenCalled();
    expect(rechazar).withContext('rejectCookies').not.toHaveBeenCalled();
    expect(cargarScript).withContext('gtag.js').not.toHaveBeenCalled();
    expect(gtag).withContext('gtag (consent, js, config, set, page_view)').not.toHaveBeenCalled();
    expect(global['ga-disable-G-R3H7GC1T4X']).withContext('ga-disable').toBeUndefined();
  }
  const cascara = (f: ComponentFixture<RaizComponent>) =>
    f.debugElement.children[0].componentInstance as SkeletonComponent;

  function esperarPlantilla(f: ComponentFixture<RaizComponent>): void {
    const r = raiz(f);
    expect(cascara(f).modo()).toBe('plantilla');
    expect(r.querySelector('app-header')).withContext('sin cabecera').toBeNull();
    expect(r.querySelector('app-cabecera-panel')).withContext('sin cabecera del panel').toBeNull();
    expect(r.querySelector('app-footer')).withContext('sin pie').toBeNull();
    expect(r.querySelector('app-botonscroll')).withContext('sin botón de subir').toBeNull();
    expect(r.querySelector('#cookie-consent')).withContext('sin aviso de cookies').toBeNull();
    expect(r.querySelector('.container-fluid')).withContext('sin el marco de Moiskimdo').toBeNull();
    expect(r.querySelector('main#content')).withContext('sin el <main> de la cáscara').toBeNull();
    expect(r.querySelector('.pagina-plantilla')).withContext('la página proyectada').not.toBeNull();
  }

  function esperarHoy(f: ComponentFixture<RaizComponent>, pagina: string): void {
    const r = raiz(f);
    expect(cascara(f).modo()).toBe('hoy');
    expect(r.querySelector('app-header')).withContext('cabecera de hoy').not.toBeNull();
    expect(r.querySelector('app-footer')).withContext('pie de hoy').not.toBeNull();
    expect(r.querySelector('app-botonscroll')).withContext('botón de subir').not.toBeNull();
    expect(r.querySelector(`main#content ${pagina}`)).withContext('la página dentro del <main> de hoy').not.toBeNull();
  }

  beforeEach(() => {
    gtagAnterior = global.gtag;
    gtag = jasmine.createSpy('gtag');
    global.gtag = gtag;
    consentimientoAnterior = localStorage.getItem('cookieConsent');
    aceptar = spyOn(SkeletonComponent.prototype, 'acceptCookies').and.callThrough();
    rechazar = spyOn(SkeletonComponent.prototype, 'rejectCookies').and.callThrough();
    // Nunca se descarga gtag.js de verdad en las pruebas: solo se comprueba si se pide.
    cargarScript = spyOn(SkeletonComponent.prototype as any, 'cargarScript');
  });

  afterEach(() => {
    quitarMeta?.();
    quitarMeta = null;
    expect(quedaMetaWebPlantilla()).withContext('la meta no se queda').toBeFalse();
    global.gtag = gtagAnterior;
    delete global['ga-disable-G-R3H7GC1T4X'];
    if (consentimientoAnterior === null) {
      localStorage.removeItem('cookieConsent');
    } else {
      localStorage.setItem('cookieConsent', consentimientoAnterior);
    }
  });

  describe('sin la meta (Moiskimdo)', () => {
    it('cabecera, pie y botón de hoy; con las cookies aceptadas, GA4 al crearse la cáscara, como hoy', async () => {
      localStorage.setItem('cookieConsent', 'accepted');
      configurar();
      const fixture = TestBed.createComponent(RaizComponent);
      fixture.detectChanges();
      // Antes de la primera navegación: la decisión guardada ya se ha aplicado (sin esperar al router).
      expect(aceptar).toHaveBeenCalledTimes(1);
      expect(cargarScript).toHaveBeenCalledTimes(1);
      await navegar(fixture, '/login');
      esperarHoy(fixture, '.pagina-hoy');
      expect(gtag).toHaveBeenCalledWith('set', jasmine.objectContaining({ page_location: jasmine.any(String) }));
    });

    it('sin decisión guardada: el aviso de cookies, como hoy', async () => {
      localStorage.removeItem('cookieConsent');
      const fixture = await arrancar('/login');
      esperarHoy(fixture, '.pagina-hoy');
      expect(raiz(fixture).querySelector('#cookie-consent')).not.toBeNull();
    });

    it('ruta del panel con las cookies aceptadas: acceptCookies, set y page_view, como hoy', async () => {
      localStorage.setItem('cookieConsent', 'accepted');
      const fixture = await arrancar('/mi-caja');
      esperarHoy(fixture, '.pagina-hoy');
      expect(aceptar).toHaveBeenCalledTimes(1);
      expect(cargarScript).toHaveBeenCalledTimes(1);
      await navegar(fixture, '/login');
      expect(gtag).toHaveBeenCalledWith('set', jasmine.objectContaining({ page_location: jasmine.any(String) }));
      expect(gtag).toHaveBeenCalledWith('event', 'page_view', jasmine.objectContaining({ page_path: '/login' }));
    });

    it('«Configurar cookies» del pie en el login abre el aviso, como hoy', async () => {
      localStorage.setItem('cookieConsent', 'rejected');
      const fixture = await arrancar('/login');
      expect(avisoCookies(fixture)).toBeNull();
      configurarCookiesDelPie(fixture);
      expect(avisoCookies(fixture)).not.toBeNull();
    });
  });

  describe('con la meta (index-plantilla.html)', () => {
    beforeEach(() => {
      quitarMeta = ponerMetaWebPlantilla();
    });

    it('antes de la primera navegación ya es «plantilla»: nunca asoma la cabecera de Moiskimdo', () => {
      localStorage.setItem('cookieConsent', 'accepted');
      configurar();
      const fixture = TestBed.createComponent(RaizComponent);
      fixture.detectChanges();
      expect(cascara(fixture).modo()).toBe('plantilla');
      expect(raiz(fixture).querySelector('app-header')).toBeNull();
      expect(raiz(fixture).querySelector('app-footer')).toBeNull();
      expect(aceptar).not.toHaveBeenCalled();
    });

    it('ruta de la plantilla con las cookies aceptadas: solo el contenido, sin acceptCookies, sin GA4 ni hits', async () => {
      localStorage.setItem('cookieConsent', 'accepted');
      const fixture = await arrancar('/');
      esperarPlantilla(fixture);
      expect(aceptar).not.toHaveBeenCalled();
      expect(cargarScript).not.toHaveBeenCalled();
      expect(gtag).not.toHaveBeenCalled();
    });

    it('ruta de la plantilla sin decisión guardada: sin aviso de cookies y sin GA4', async () => {
      localStorage.removeItem('cookieConsent');
      const fixture = await arrancar('/');
      esperarPlantilla(fixture);
      expect(aceptar).not.toHaveBeenCalled();
      expect(rechazar).not.toHaveBeenCalled();
      expect(cargarScript).not.toHaveBeenCalled();
      expect(gtag).not.toHaveBeenCalled();
    });

    it('de la plantilla al login y al panel: lo de hoy, pero sin aviso ni GA4 aunque estén aceptadas; al volver, la plantilla', async () => {
      localStorage.setItem('cookieConsent', 'accepted');
      const fixture = await arrancar('/');
      esperarPlantilla(fixture);

      await navegar(fixture, '/login');
      esperarHoy(fixture, '.pagina-hoy');
      expect(avisoCookies(fixture)).toBeNull();

      await navegar(fixture, '/mi-caja');
      esperarHoy(fixture, '.pagina-hoy');
      expect(avisoCookies(fixture)).toBeNull();

      await navegar(fixture, '/');
      esperarPlantilla(fixture);
      esperarSinAnalitica();
      expect(localStorage.getItem('cookieConsent')).withContext('la decisión guardada no se toca').toBe('accepted');
    });

    for (const guardado of ['accepted', 'rejected', null]) {
      it(`login desde el arranque (decisión guardada: ${guardado}): lo de hoy, sin aviso ni GA4`, async () => {
        if (guardado === null) {
          localStorage.removeItem('cookieConsent');
        } else {
          localStorage.setItem('cookieConsent', guardado);
        }
        const fixture = await arrancar('/login');
        esperarHoy(fixture, '.pagina-hoy');
        expect(avisoCookies(fixture)).toBeNull();
        await navegar(fixture, '/otra-de-hoy');
        esperarSinAnalitica();
        expect(localStorage.getItem('cookieConsent')).toBe(guardado);
      });

      it(`ruta del panel desde el arranque (decisión guardada: ${guardado}): sin aviso ni GA4`, async () => {
        if (guardado === null) {
          localStorage.removeItem('cookieConsent');
        } else {
          localStorage.setItem('cookieConsent', guardado);
        }
        const fixture = await arrancar('/mi-caja');
        esperarHoy(fixture, '.pagina-hoy');
        expect(avisoCookies(fixture)).toBeNull();
        await navegar(fixture, '/login');
        esperarSinAnalitica();
        expect(localStorage.getItem('cookieConsent')).toBe(guardado);
      });
    }

    it('«Configurar cookies» del pie en el login no abre el aviso', async () => {
      localStorage.setItem('cookieConsent', 'accepted');
      const fixture = await arrancar('/login');
      configurarCookiesDelPie(fixture);
      expect(cascara(fixture).showCookieBanner).toBeFalse();
      expect(avisoCookies(fixture)).toBeNull();
      esperarSinAnalitica();
    });

    it('acceptCookies y rejectCookies llamados a mano no hacen nada (ni GA4, ni la decisión guardada)', async () => {
      localStorage.removeItem('cookieConsent');
      const fixture = await arrancar('/login');
      cascara(fixture).acceptCookies();
      cascara(fixture).rejectCookies();
      expect(cargarScript).not.toHaveBeenCalled();
      expect(gtag).not.toHaveBeenCalled();
      expect(global['ga-disable-G-R3H7GC1T4X']).toBeUndefined();
      expect(localStorage.getItem('cookieConsent')).toBeNull();
    });

    it('sin gtag definido (index-plantilla.html no lo trae): nada entra en dataLayer al navegar', async () => {
      delete global.gtag;
      const dataLayerAnterior = global.dataLayer;
      const cola: unknown[] = [];
      global.dataLayer = cola;
      try {
        localStorage.setItem('cookieConsent', 'accepted');
        const fixture = await arrancar('/login');
        await navegar(fixture, '/mi-caja');
        await navegar(fixture, '/');
        expect(cola).toEqual([]);
      } finally {
        global.dataLayer = dataLayerAnterior;
      }
    });
  });
});
