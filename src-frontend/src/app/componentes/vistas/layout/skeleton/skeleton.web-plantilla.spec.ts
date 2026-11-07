import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
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
 * sin cabecera, pie, botón de subir ni aviso de cookies, y sin GA4. Sin la meta (Moiskimdo), todo como hoy.
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

    it('de la plantilla al login: el login con lo de hoy (y su decisión de cookies); al volver, la plantilla otra vez', async () => {
      localStorage.setItem('cookieConsent', 'accepted');
      const fixture = await arrancar('/');
      esperarPlantilla(fixture);
      expect(aceptar).not.toHaveBeenCalled();

      await navegar(fixture, '/login');
      esperarHoy(fixture, '.pagina-hoy');
      expect(aceptar).toHaveBeenCalledTimes(1);

      await navegar(fixture, '/');
      esperarPlantilla(fixture);
      expect(aceptar).toHaveBeenCalledTimes(1);
    });

    it('fuera de la plantilla desde el arranque (login): lo de hoy, con el aviso si no hay decisión', async () => {
      localStorage.removeItem('cookieConsent');
      const fixture = await arrancar('/login');
      esperarHoy(fixture, '.pagina-hoy');
      expect(raiz(fixture).querySelector('#cookie-consent')).not.toBeNull();
    });
  });
});
