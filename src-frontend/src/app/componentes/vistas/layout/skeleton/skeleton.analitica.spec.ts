import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';

import { SkeletonComponent } from './skeleton.component';

/** Revisión E8-6 F1: ningún hit de analítica lleva el `token` de los enlaces de contraseña. */
describe('SkeletonComponent · analítica sin token', () => {
  const global = globalThis as any;
  let gtag: jasmine.Spy;
  let eventos: Subject<unknown>;
  let gtagAnterior: unknown;
  let consentimientoAnterior: string | null;
  let urlAnterior: string;

  function hits(): ReadonlyArray<readonly unknown[]> {
    return gtag.calls.allArgs();
  }

  function pageViews(): Array<{ page_location: string; page_path: string }> {
    return hits()
      .filter((a) => a[0] === 'event' && a[1] === 'page_view')
      .map((a) => a[2] as { page_location: string; page_path: string });
  }

  function montar(): void {
    eventos = new Subject();
    TestBed.configureTestingModule({
      imports: [SkeletonComponent],
      providers: [{ provide: Router, useValue: { events: eventos } }],
    });
    TestBed.overrideComponent(SkeletonComponent, { set: { imports: [], template: '' } });
    TestBed.createComponent(SkeletonComponent).detectChanges();
  }

  beforeEach(() => {
    gtagAnterior = global.gtag;
    gtag = jasmine.createSpy('gtag');
    global.gtag = gtag;
    consentimientoAnterior = localStorage.getItem('cookieConsent');
    localStorage.setItem('cookieConsent', 'accepted');
    urlAnterior = location.pathname + location.search + location.hash;
  });

  afterEach(() => {
    global.gtag = gtagAnterior;
    if (consentimientoAnterior === null) {
      localStorage.removeItem('cookieConsent');
    } else {
      localStorage.setItem('cookieConsent', consentimientoAnterior);
    }
    history.replaceState(history.state, '', urlAnterior);
  });

  it('el page_view del arranque en /crear-contrasena?token=… no lleva el token y conserva los demás parámetros', () => {
    history.replaceState(history.state, '', '/crear-contrasena?token=SECRETO1&utm_source=correo');
    montar();
    expect(JSON.stringify(hits())).not.toContain('SECRETO1');
    expect(JSON.stringify(hits())).not.toContain('token');
    const config = hits().find((a) => a[0] === 'config')!;
    expect((config[2] as { send_page_view: boolean }).send_page_view).toBeFalse();
    expect(pageViews()).toEqual([
      {
        page_location: `${location.origin}/crear-contrasena?utm_source=correo`,
        page_path: '/crear-contrasena?utm_source=correo',
      },
    ]);
  });

  it('en la navegación (reset-password) el page_view tampoco lleva el token y no se repite la página inicial', () => {
    history.replaceState(history.state, '', '/crear-contrasena?token=SECRETO1');
    montar();
    eventos.next(new NavigationEnd(1, '/crear-contrasena?token=SECRETO1', '/crear-contrasena?token=SECRETO1'));
    eventos.next(new NavigationEnd(2, '/reset-password?token=SECRETO2&a=b', '/reset-password?token=SECRETO2&a=b'));
    eventos.next(new NavigationEnd(3, '/horarios?dia=lunes', '/horarios?dia=lunes'));
    expect(JSON.stringify(hits())).not.toContain('SECRETO');
    expect(pageViews().map((p) => p.page_path)).toEqual(['/crear-contrasena', '/reset-password?a=b', '/horarios?dia=lunes']);
    expect(pageViews()[1].page_location).toBe(`${location.origin}/reset-password?a=b`);
  });

  it('sin consentimiento no se manda ningún page_view al navegar', () => {
    localStorage.setItem('cookieConsent', 'rejected');
    montar();
    eventos.next(new NavigationEnd(1, '/reset-password?token=SECRETO2', '/reset-password?token=SECRETO2'));
    expect(pageViews()).toEqual([]);
  });

  /**
   * Con Consent Mode avanzado, gtag.js manda eventos automáticos (scroll, user_engagement, formularios) también sin
   * consentimiento, con el page_location fijado. Se fija en cada navegación, limpio, decida lo que decida el usuario.
   */
  it('sin consentimiento, en cada navegación fija el page_location sin token y no manda ningún evento', () => {
    localStorage.setItem('cookieConsent', 'rejected');
    montar();
    gtag.calls.reset();
    eventos.next(new NavigationEnd(1, '/reset-password?token=SECRETO2&a=b', '/reset-password?token=SECRETO2&a=b'));
    eventos.next(new NavigationEnd(2, '/horarios', '/horarios'));
    expect(hits()).toEqual([
      ['set', { page_location: `${location.origin}/reset-password?a=b` }],
      ['set', { page_location: `${location.origin}/horarios` }],
    ]);
    expect(JSON.stringify(hits())).not.toContain('SECRETO');
  });

  it('sin decidir (aviso abierto) también fija el page_location sin token al navegar', () => {
    localStorage.removeItem('cookieConsent');
    montar();
    gtag.calls.reset();
    eventos.next(new NavigationEnd(1, '/crear-contrasena?token=SECRETO3', '/crear-contrasena?token=SECRETO3'));
    expect(hits()).toEqual([['set', { page_location: `${location.origin}/crear-contrasena` }]]);
  });

  it('con consentimiento, el set con la URL limpia va antes del page_view de esa navegación', () => {
    montar();
    gtag.calls.reset();
    eventos.next(new NavigationEnd(1, '/reset-password?token=SECRETO2', '/reset-password?token=SECRETO2'));
    const limpia = `${location.origin}/reset-password`;
    const llamadas = hits();
    const set = llamadas.findIndex((a) => a[0] === 'set' && (a[1] as { page_location: string }).page_location === limpia);
    const pageView = llamadas.findIndex((a) => a[0] === 'event' && a[1] === 'page_view');
    expect(set).toBeGreaterThanOrEqual(0);
    expect(pageView).toBeGreaterThan(set);
    expect(JSON.stringify(llamadas)).not.toContain('SECRETO');
  });

  it('al quitar el token de la barra (misma página) se vuelve a fijar el page_location pero no se repite la visita', () => {
    montar();
    eventos.next(new NavigationEnd(1, '/reset-password?token=SECRETO2', '/reset-password?token=SECRETO2'));
    gtag.calls.reset();
    eventos.next(new NavigationEnd(2, '/reset-password', '/reset-password'));
    expect(hits()).toEqual([['set', { page_location: `${location.origin}/reset-password` }]]);
  });
});
