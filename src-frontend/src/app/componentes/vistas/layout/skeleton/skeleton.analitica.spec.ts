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
  let cargarScript: jasmine.Spy;
  let componente: SkeletonComponent;

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
      // `routerState` hace falta desde el 404 del panel (8b-M2b): la cáscara mira `data.panelNuevo` de la ruta activa.
      providers: [{ provide: Router, useValue: { events: eventos, routerState: { snapshot: { root: { data: {} } } } } }],
    });
    TestBed.overrideComponent(SkeletonComponent, { set: { imports: [], template: '' } });
    const fixture = TestBed.createComponent(SkeletonComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(() => {
    gtagAnterior = global.gtag;
    gtag = jasmine.createSpy('gtag');
    global.gtag = gtag;
    consentimientoAnterior = localStorage.getItem('cookieConsent');
    localStorage.setItem('cookieConsent', 'accepted');
    urlAnterior = location.pathname + location.search + location.hash;
    // Nunca se descarga gtag.js de verdad en las pruebas: solo se comprueba si se pide.
    cargarScript = spyOn(SkeletonComponent.prototype as any, 'cargarScript');
  });

  afterEach(() => {
    global.gtag = gtagAnterior;
    delete global['ga-disable-G-R3H7GC1T4X'];
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
   * El page_location se fija en cada navegación, limpio, decida lo que decida el usuario: sin consentimiento se queda en
   * dataLayer (gtag.js no se carga, decisión 233) y, si luego acepta, la cola que procesa gtag.js ya va limpia.
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

  /** Consentimiento básico (decisión 233): sin «Aceptar» no se carga gtag.js ni se manda ningún hit. */
  describe('consentimiento básico', () => {
    const selectorScript = 'script[src*="googletagmanager.com"]';

    function hitsQueMandan(): ReadonlyArray<readonly unknown[]> {
      return hits().filter((a) => a[0] === 'config' || a[0] === 'event' || a[0] === 'js');
    }

    function cookiesGa(): string[] {
      return document.cookie
        .split(';')
        .map((c) => c.split('=')[0].trim())
        .filter((n) => n.startsWith('_ga'));
    }

    it('con «Rechazar» guardado no carga gtag.js ni manda ningún hit, tampoco al navegar', () => {
      localStorage.setItem('cookieConsent', 'rejected');
      montar();
      eventos.next(new NavigationEnd(1, '/reset-password?token=SECRETO2', '/reset-password?token=SECRETO2'));
      expect(cargarScript).not.toHaveBeenCalled();
      expect(document.querySelector(selectorScript)).toBeNull();
      expect(hitsQueMandan()).toEqual([]);
    });

    it('sin decidir (aviso abierto) no carga gtag.js ni manda ningún hit', () => {
      localStorage.removeItem('cookieConsent');
      montar();
      eventos.next(new NavigationEnd(1, '/horarios', '/horarios'));
      expect(cargarScript).not.toHaveBeenCalled();
      expect(document.querySelector(selectorScript)).toBeNull();
      expect(hitsQueMandan()).toEqual([]);
    });

    it('al pulsar «Rechazar» tampoco carga gtag.js', () => {
      localStorage.removeItem('cookieConsent');
      montar();
      componente.rejectCookies();
      expect(cargarScript).not.toHaveBeenCalled();
      expect(hitsQueMandan()).toEqual([]);
    });

    it('al pulsar «Aceptar» carga gtag.js una vez y lo configura sin token desde el primer hit', () => {
      history.replaceState(history.state, '', '/reset-password?token=SECRETO1');
      localStorage.removeItem('cookieConsent');
      montar();
      expect(cargarScript).not.toHaveBeenCalled();
      componente.acceptCookies();
      expect(cargarScript).toHaveBeenCalledTimes(1);
      const llamadas = hits();
      expect(JSON.stringify(llamadas)).not.toContain('SECRETO1');
      const consentimiento = llamadas.findIndex(
        (a) => a[0] === 'consent' && (a[2] as { analytics_storage: string }).analytics_storage === 'granted'
      );
      const js = llamadas.findIndex((a) => a[0] === 'js');
      const config = llamadas.findIndex((a) => a[0] === 'config');
      expect(consentimiento).toBeGreaterThanOrEqual(0);
      expect(js).toBeGreaterThan(consentimiento);
      expect(config).toBeGreaterThan(js);
      expect(llamadas[config][2]).toEqual(
        jasmine.objectContaining({ send_page_view: false, page_location: `${location.origin}/reset-password` })
      );
      expect(pageViews()).toEqual([{ page_location: `${location.origin}/reset-password`, page_path: '/reset-password' }]);
    });

    it('con «Aceptar» guardado de una visita anterior carga gtag.js al arrancar', () => {
      montar();
      expect(cargarScript).toHaveBeenCalledTimes(1);
      expect(hits().some((a) => a[0] === 'config')).toBeTrue();
    });

    it('con «Rechazar» guardado borra las cookies _ga que quedaran de antes', () => {
      document.cookie = '_ga=GA1.1.prueba; path=/';
      document.cookie = '_ga_PRUEBA=GS1.1.prueba; path=/';
      localStorage.setItem('cookieConsent', 'rejected');
      montar();
      expect(cookiesGa()).toEqual([]);
    });

    it('si retira el consentimiento tras aceptar: consent denied, borra las _ga y deja de mandar page_view', () => {
      montar();
      document.cookie = '_ga=GA1.1.prueba; path=/';
      gtag.calls.reset();
      componente.rejectCookies();
      expect(hits()).toContain([
        'consent',
        'update',
        jasmine.objectContaining({ analytics_storage: 'denied' }) as unknown as object,
      ]);
      expect(cookiesGa()).toEqual([]);
      // gtag.js ya cargado deja de mandar hasta recargar (decisión 233, ronda 2)
      expect(global['ga-disable-G-R3H7GC1T4X']).toBeTrue();
      delete global['ga-disable-G-R3H7GC1T4X'];
      eventos.next(new NavigationEnd(1, '/horarios', '/horarios'));
      expect(pageViews()).toEqual([]);
      expect(localStorage.getItem('cookieConsent')).toBe('rejected');
    });
  });
});
