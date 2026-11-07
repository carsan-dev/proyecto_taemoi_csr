import { fakeAsync, tick } from '@angular/core/testing';

import { EVENTO_NOMBRE_CAPA } from './cambio-escuela-pestanas';
import {
  SitioCapaCambioEscuela,
  TITULO_CAPA_CAMBIO_ESCUELA,
  pintarCapaCambioEscuela,
  sitioDeLaPestana,
} from './capa-cambio-escuela';

describe('Capa «Has cambiado de escuela» (demás pestañas, boceto aprobado)', () => {
  let capa: HTMLDialogElement | null;
  let alRecargar: jasmine.Spy;
  let fuera: HTMLButtonElement;

  const CON_NOMBRE = 'Lo hiciste en otra pestaña. Recarga esta para seguir con los datos de Taekwondo Sierra Sur.';
  const SIN_NOMBRE = 'Lo hiciste en otra pestaña. Recarga esta para seguir con los datos de la escuela nueva.';

  const pintar = (sitio: SitioCapaCambioEscuela, nombre: string | null = 'Taekwondo Sierra Sur'): HTMLDialogElement =>
    (capa = pintarCapaCambioEscuela(document, { nombre, alRecargar }, sitio));
  const boton = (): HTMLButtonElement => capa!.querySelector('button')!;
  const texto = (): string => capa!.querySelector('#taemoi-cambio-escuela-texto')!.textContent ?? '';

  beforeEach(() => {
    alRecargar = jasmine.createSpy('alRecargar');
    fuera = document.createElement('button');
    fuera.textContent = 'Fondo';
    document.body.appendChild(fuera);
    capa = null;
  });

  afterEach(() => {
    capa?.remove();
    fuera.remove();
  });

  for (const sitio of ['panel', 'familias', 'web'] as const) {
    describe(`en ${sitio}`, () => {
      it('es un alertdialog modal con los textos del boceto, el foco en «Recargar» y un botón de 44 px o más', () => {
        const dialogo = pintar(sitio);
        expect(dialogo.matches(':modal')).toBeTrue();
        expect(dialogo.classList).toContain(`tce-${sitio}`);
        expect(dialogo.getAttribute('role')).toBe('alertdialog');
        expect(dialogo.getAttribute('aria-modal')).toBe('true');
        expect(document.getElementById(dialogo.getAttribute('aria-labelledby')!)!.textContent).toBe(
          'Has cambiado de escuela'
        );
        expect(dialogo.getAttribute('aria-describedby')).toBe('taemoi-cambio-escuela-texto');
        expect(TITULO_CAPA_CAMBIO_ESCUELA).toBe('Has cambiado de escuela');
        expect(texto()).toBe(CON_NOMBRE);
        expect(dialogo.querySelector('strong')!.textContent).toBe('Taekwondo Sierra Sur');
        expect(boton().textContent!.trim()).toBe('Recargar');
        expect(document.activeElement).toBe(boton());
        // offsetHeight: el alto de la caja sin la escala de la entrada (forma de SweetAlert2).
        expect(boton().offsetHeight).toBeGreaterThanOrEqual(44);
        // Sin X ni otros controles.
        expect(dialogo.querySelectorAll('button, a, input').length).toBe(1);
      });

      it('sin el nombre de la escuela nueva, la variante sin nombre', () => {
        pintar(sitio, null);
        expect(texto()).toBe(SIN_NOMBRE);
        expect(capa!.querySelector('strong')).toBeNull();
      });

      it('no se cierra: ni Esc, ni el clic fuera; el foco no sale de la capa', async () => {
        const dialogo = pintar(sitio);
        const cancelar = new Event('cancel', { cancelable: true });
        dialogo.dispatchEvent(cancelar);
        expect(cancelar.defaultPrevented).toBeTrue();
        const esc = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
        boton().dispatchEvent(esc);
        expect(esc.defaultPrevented).toBeTrue();
        dialogo.click();
        expect(dialogo.open).toBeTrue();

        const tab = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true });
        boton().dispatchEvent(tab);
        expect(tab.defaultPrevented).toBeTrue();
        expect(document.activeElement).toBe(boton());
        fuera.focus();
        expect(document.activeElement).not.toBe(fuera);

        // Si el navegador la cierra igual, vuelve a abrirse.
        dialogo.close();
        await new Promise((r) => setTimeout(r, 20));
        expect(dialogo.open).toBeTrue();
      });

      it('«Recargar» pasa a «Recargando…» ocupado, lo anuncia y recarga una sola vez', () => {
        pintar(sitio);
        boton().click();
        expect(boton().textContent!.trim()).toBe('Recargando…');
        expect(boton().getAttribute('aria-busy')).toBe('true');
        expect(boton().getAttribute('aria-disabled')).toBe('true');
        expect(document.activeElement).toBe(boton());
        expect(capa!.querySelector('[role="status"]')!.textContent).toBe('Recargando…');
        boton().click();
        expect(alRecargar).toHaveBeenCalledTimes(1);
      });
    });
  }

  it('cada sitio con su forma: el panel sin icono ni logo, familias con el icono de recarga, la web con el logo', () => {
    pintar('panel');
    expect(capa!.querySelector('svg, img')).toBeNull();
    // Diálogo por encima de 768 px; hoja inferior (radio arriba) por debajo.
    const hoja = window.matchMedia('(max-width: 767.98px)').matches;
    expect(getComputedStyle(capa!).borderRadius).toBe(hoja ? '14px 14px 0px 0px' : '8px');
    expect(getComputedStyle(boton()).backgroundColor).toBe('rgb(26, 34, 48)');
    capa!.remove();

    pintar('familias');
    expect(capa!.querySelector('.tce-familias__icono svg')).not.toBeNull();
    expect(getComputedStyle(boton()).backgroundColor).toBe('rgb(10, 88, 202)');
    capa!.remove();

    pintar('web');
    const logo = capa!.querySelector<HTMLImageElement>('img.tce-web__logo')!;
    expect(logo.getAttribute('src')).toBe('/assets/media/Logo_escuela.svg');
    expect(logo.alt).toBe('');
    expect(getComputedStyle(boton()).borderRadius).toBe('50px');
    expect(getComputedStyle(boton()).backgroundColor).toBe('rgb(27, 43, 46)');
  });

  it('pone los estilos una sola vez', () => {
    pintar('panel');
    capa!.remove();
    pintar('web');
    expect(document.querySelectorAll('#taemoi-cambio-escuela-estilos').length).toBe(1);
  });

  it('toma el nombre si llega después (el «confirmado» tras la espera), siempre como texto', () => {
    pintar('panel', null);
    capa!.dispatchEvent(new CustomEvent(EVENTO_NOMBRE_CAPA, { detail: '<b>Club</b>' }));
    expect(texto()).toBe('Lo hiciste en otra pestaña. Recarga esta para seguir con los datos de <b>Club</b>.');
    expect(capa!.querySelector('b')).toBeNull();
  });

  describe('el sitio sale del área que pinta la pestaña', () => {
    let marco: HTMLElement | null = null;
    afterEach(() => marco?.remove());

    it('panel nuevo con el marco del panel; familias en el portal de usuario; web en las públicas', () => {
      expect(sitioDeLaPestana(document, '/')).toBe('web');
      expect(sitioDeLaPestana(document, '/login')).toBe('web');
      expect(sitioDeLaPestana(document, '/userpage')).toBe('familias');
      // Gestión con la cabecera de hoy (sin panel nuevo): la forma de sus avisos (SweetAlert2).
      expect(sitioDeLaPestana(document, '/adminpage')).toBe('familias');
      marco = document.createElement('div');
      marco.className = 'marco-panel';
      document.body.appendChild(marco);
      expect(sitioDeLaPestana(document, '/alumnos')).toBe('panel');
    });
  });

  it('si la página sigue tras «Recargar» (un beforeunload ha cancelado la carga), a los 2 s el botón vuelve a «Recargar»', fakeAsync(() => {
    pintar('panel');
    boton().click();
    expect(boton().getAttribute('aria-disabled')).toBe('true');
    expect(alRecargar).toHaveBeenCalledTimes(1);
    tick(1999);
    expect(boton().getAttribute('aria-disabled')).toBe('true');
    tick(1);
    expect(boton().hasAttribute('aria-disabled')).toBeFalse();
    expect(boton().hasAttribute('aria-busy')).toBeFalse();
    expect(boton().textContent!.trim()).toBe('Recargar');
    expect(capa!.querySelector('[role=status]')!.textContent).toBe('');
    boton().click();
    expect(alRecargar).toHaveBeenCalledTimes(2);
    tick(2000);
  }));
});
