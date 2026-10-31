import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { BotonscrollComponent } from '../../../generales/botonscroll/botonscroll.component';
import { AuthenticationService } from '../../../../servicios/authentication/authentication.service';
import { HeaderComponent } from '../header/header.component';
import { SkeletonComponent } from './skeleton.component';

@Component({ selector: 'app-header', standalone: true, template: '' })
class HeaderFalsoComponent {}

@Component({ selector: 'app-botonscroll', standalone: true, template: '' })
class BotonscrollFalsoComponent {}

/**
 * Decisión 250: «Configurar cookies», en el pie, vuelve a abrir el aviso de cookies para aceptar o rechazar de nuevo.
 * Rechazar tras aceptar deja de medir (consent denied, ga-disable y borrado de las cookies _ga*).
 */
describe('SkeletonComponent · «Configurar cookies» en el pie', () => {
  const global = globalThis as any;
  const GA_DISABLE = 'ga-disable-G-R3H7GC1T4X';
  let gtag: jasmine.Spy;
  let gtagAnterior: unknown;
  let consentimientoAnterior: string | null;

  function montar(): { raiz: HTMLElement; detectar: () => void } {
    TestBed.configureTestingModule({ imports: [SkeletonComponent], providers: [
        provideRouter([]),
        { provide: AuthenticationService, useValue: { tieneAccesoAdmin: () => false, tieneRolResponsable: () => false, esSoloProfesor: () => false } },
      ],
    });
    TestBed.overrideComponent(SkeletonComponent, {
      remove: { imports: [HeaderComponent, BotonscrollComponent] },
      add: { imports: [HeaderFalsoComponent, BotonscrollFalsoComponent] },
    });
    const fixture = TestBed.createComponent(SkeletonComponent);
    fixture.detectChanges();
    return { raiz: fixture.nativeElement as HTMLElement, detectar: () => fixture.detectChanges() };
  }

  function botonConfigurar(raiz: HTMLElement): HTMLButtonElement {
    const boton = Array.from(raiz.querySelectorAll<HTMLButtonElement>('app-footer button')).find(
      (b) => b.textContent?.trim() === 'Configurar cookies'
    );
    expect(boton).withContext('botón «Configurar cookies» en el pie').toBeDefined();
    return boton!;
  }

  function cookiesGa(): string[] {
    return document.cookie
      .split(';')
      .map((c) => c.split('=')[0].trim())
      .filter((n) => n.startsWith('_ga'));
  }

  beforeEach(() => {
    gtagAnterior = global.gtag;
    gtag = jasmine.createSpy('gtag');
    global.gtag = gtag;
    consentimientoAnterior = localStorage.getItem('cookieConsent');
    spyOn(SkeletonComponent.prototype as any, 'cargarScript');
  });

  afterEach(() => {
    global.gtag = gtagAnterior;
    delete global[GA_DISABLE];
    if (consentimientoAnterior === null) {
      localStorage.removeItem('cookieConsent');
    } else {
      localStorage.setItem('cookieConsent', consentimientoAnterior);
    }
  });

  it('es un botón con aspecto de enlace (type="button") que vuelve a abrir el aviso tras decidir', () => {
    localStorage.setItem('cookieConsent', 'rejected');
    const { raiz, detectar } = montar();
    expect(raiz.querySelector('#cookie-consent')).toBeNull();
    const boton = botonConfigurar(raiz);
    expect(boton.type).toBe('button');
    boton.click();
    detectar();
    expect(raiz.querySelector('#cookie-consent')).not.toBeNull();
  });

  it('rechazar tras aceptar: consent denied, ga-disable y sin cookies _ga', () => {
    localStorage.setItem('cookieConsent', 'accepted');
    const { raiz, detectar } = montar();
    document.cookie = '_ga=GA1.1.prueba; path=/';
    document.cookie = '_ga_R3H7GC1T4X=GS1.1.prueba; path=/';
    botonConfigurar(raiz).click();
    detectar();
    gtag.calls.reset();
    raiz.querySelector<HTMLButtonElement>('#cookie-consent-reject')!.click();
    detectar();

    expect(gtag.calls.allArgs()).toContain([
      'consent',
      'update',
      jasmine.objectContaining({ analytics_storage: 'denied' }) as unknown as object,
    ]);
    expect(global[GA_DISABLE]).toBeTrue();
    expect(cookiesGa()).toEqual([]);
    expect(localStorage.getItem('cookieConsent')).toBe('rejected');
    expect(raiz.querySelector('#cookie-consent')).toBeNull();
  });

  it('aceptar de nuevo en la misma sesión quita el ga-disable antes de configurar', () => {
    localStorage.setItem('cookieConsent', 'accepted');
    const { raiz, detectar } = montar();
    botonConfigurar(raiz).click();
    detectar();
    raiz.querySelector<HTMLButtonElement>('#cookie-consent-reject')!.click();
    detectar();
    gtag.and.callFake((...args: unknown[]) => {
      if (args[0] === 'config') {
        expect(global[GA_DISABLE]).toBeFalse();
      }
    });
    gtag.calls.reset();
    botonConfigurar(raiz).click();
    detectar();
    raiz.querySelector<HTMLButtonElement>('#cookie-consent-accept')!.click();
    detectar();

    expect(global[GA_DISABLE]).toBeFalse();
    expect(gtag.calls.allArgs().some((a) => a[0] === 'config')).toBeTrue();
    expect(localStorage.getItem('cookieConsent')).toBe('accepted');
  });
});
