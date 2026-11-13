import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CambioEscuelaPestanasService } from '../../core/cambio-escuela-pestanas';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AvisosService } from '../avisos/avisos.service';
import { CONSULTA_HOJA, PilaCapas, enfocables } from '../capas/capa-modal';
import { SALIDA_HOJA_MS, deslizarAsa } from '../hoja/asa-hoja-prueba';
import { escuela } from './escuelas-prueba';
import { SelectorEscuelaCabeceraComponent } from './selector-escuela-cabecera.component';
import {
  EscuelaUsuario,
  IR_A_URL,
  RUTA_CAMBIANDO_ESCUELA,
  RUTA_ENTRADA_SESION,
  SelectorEscuelaService,
} from './selector-escuela.service';
import { RUTAS_CAMBIANDO_ESCUELA } from './selector-escuela.routes';

const URL_CAMBIO = `${environment.apiUrl}/auth/organizacion`;

@Component({
  standalone: true,
  imports: [SelectorEscuelaCabeceraComponent],
  template: `
    <div class="pn">
      <button type="button" class="fuera">Otra cosa de la cabecera</button>
      <pn-selector-escuela [escuelas]="escuelas()">
        <span class="nombre-proyectado">Escuela Ficticia Norte</span>
      </pn-selector-escuela>
    </div>
  `,
})
class AnfitrionComponent {
  readonly escuelas = signal<EscuelaUsuario[]>([]);
}

describe('SelectorEscuelaCabeceraComponent (en el panel)', () => {
  let fixture: ComponentFixture<AnfitrionComponent>;
  let http: HttpTestingController;
  let irA: jasmine.Spy;
  let avisos: jasmine.SpyObj<AvisosService>;
  let pestanas: jasmine.SpyObj<CambioEscuelaPestanasService>;

  const NORTE = escuela({ id: 1, nombre: 'Escuela Ficticia Norte', activa: true, roles: ['ROLE_ADMIN'] });
  const SUR = escuela({
    id: 2,
    nombre: 'Club Ficticio Sur',
    roles: ['ROLE_MANAGER'],
    numeroSedes: 6,
    logo: 'SUBIDO',
    logoVersion: 9,
  });
  const ESTE = escuela({ id: 3, nombre: 'Escuela del Este', roles: ['ROLE_USER', 'ROLE_MANAGER'], numeroSedes: 2 });

  async function montar(escuelas: EscuelaUsuario[] = [SUR, NORTE, ESTE]): Promise<void> {
    irA = jasmine.createSpy('irA');
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['error']);
    pestanas = jasmine.createSpyObj<CambioEscuelaPestanasService>('CambioEscuelaPestanasService', [
      'anunciarCambio',
      'confirmarCambio',
      'cancelarCambio',
      'bloquear',
    ]);
    pestanas.anunciarCambio.and.returnValue('id-1');
    await TestBed.configureTestingModule({
      imports: [AnfitrionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'cambiando-escuela', children: RUTAS_CAMBIANDO_ESCUELA }]),
        { provide: AuthenticationService, useValue: { emailCambio: new BehaviorSubject('a@example.test') } },
        { provide: IR_A_URL, useValue: irA },
        { provide: AvisosService, useValue: avisos },
        { provide: CambioEscuelaPestanasService, useValue: pestanas },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnfitrionComponent);
    fixture.componentInstance.escuelas.set(escuelas);
    fixture.detectChanges();
  }

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const boton = (): HTMLButtonElement => el().querySelector<HTMLButtonElement>('#pn-escuela-boton')!;
  const menu = (): HTMLElement | null => el().querySelector<HTMLElement>('#pn-menu-escuela');
  const items = (): HTMLButtonElement[] =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('#pn-menu-escuela [role="menuitemradio"]'));
  const abrir = (): void => {
    boton().click();
    fixture.detectChanges();
  };
  const tecla = (destino: HTMLElement, key: string): void => {
    destino.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  /** Deja correr la navegación del router (promesas y temporizadores). */
  async function esperar(): Promise<void> {
    for (let i = 0; i < 5; i++) {
      await new Promise((r) => setTimeout(r));
    }
  }

  afterEach(() => http.verify());

  it('el nombre de la escuela es un botón que abre el menú «Cambiar de escuela»', async () => {
    await montar();
    expect(boton().textContent).toContain('Escuela Ficticia Norte');
    expect(boton().getAttribute('aria-haspopup')).toBe('menu');
    expect(boton().getAttribute('aria-expanded')).toBe('false');
    expect(menu()).toBeNull();

    abrir();
    expect(boton().getAttribute('aria-expanded')).toBe('true');
    expect(menu()?.getAttribute('role')).toBe('menu');
    expect(el().querySelector('#pn-escuela-titulo')?.textContent?.trim()).toBe('Cambiar de escuela');
  });

  it('lista las escuelas en el orden de la API con su rol, sus sedes y la actual marcada', async () => {
    await montar();
    abrir();
    const lista = items();
    expect(lista.map((b) => b.querySelector('.pn-selector__nombre')?.textContent?.trim())).toEqual([
      'Club Ficticio Sur',
      'Escuela Ficticia Norte',
      'Escuela del Este',
    ]);
    expect(lista.map((b) => b.querySelector('.pn-selector__meta')?.textContent?.trim())).toEqual([
      'Gestión · 6 sedes',
      'Administración',
      'Gestión y familia · 2 sedes',
    ]);
    expect(lista.map((b) => b.getAttribute('aria-checked'))).toEqual(['false', 'true', 'false']);
    // Logo subido de una escuela que no es la activa: su ruta propia; sin logo, las iniciales.
    expect(lista[0].querySelector('img')?.getAttribute('src')).toBe(
      `${environment.apiUrl}/auth/organizaciones/2/logo?v=9`
    );
    expect(lista[1].querySelector('.pn-logo-escuela__iniciales')?.textContent?.trim()).toBe('EF');
    // Al abrir, el foco va a la actual.
    expect(document.activeElement).toBe(lista[1]);
  });

  it('pide al anfitrión ponerse encima mientras el menú está abierto', async () => {
    await montar();
    const host = el().querySelector('pn-selector-escuela')!;
    expect(host.classList).not.toContain('pn-selector--encima');
    abrir();
    expect(host.classList).toContain('pn-selector--encima');
  });

  it('flechas, Inicio y Fin mueven el foco; Esc cierra y devuelve el foco al botón', async () => {
    await montar();
    abrir();
    const lista = items();
    tecla(lista[1], 'ArrowDown');
    expect(document.activeElement).toBe(lista[2]);
    tecla(lista[2], 'ArrowDown');
    expect(document.activeElement).toBe(lista[0]);
    tecla(lista[0], 'End');
    expect(document.activeElement).toBe(lista[2]);
    tecla(lista[2], 'Home');
    expect(document.activeElement).toBe(lista[0]);
    tecla(lista[0], 'Escape');
    expect(menu()).toBeNull();
    expect(document.activeElement).toBe(boton());
  });

  it('un clic fuera cierra el menú', async () => {
    await montar();
    abrir();
    document.body.click();
    fixture.detectChanges();
    expect(menu()).toBeNull();
  });

  it('elegir la escuela actual solo cierra el menú', async () => {
    await montar();
    abrir();
    items()[1].click();
    fixture.detectChanges();
    expect(menu()).toBeNull();
    http.expectNone(URL_CAMBIO);
    expect(irA).not.toHaveBeenCalled();
  });

  it('elegir otra sale a la ruta neutra («Entrando en…»), cambia la escuela y recarga la app', async () => {
    await montar();
    abrir();
    items()[0].click();
    fixture.detectChanges();
    expect(menu()).toBeNull();
    await esperar();
    expect(TestBed.inject(SelectorEscuelaService).entrando()?.nombre).toBe('Club Ficticio Sur');
    expect(TestBed.inject(Router).url).toBe(RUTA_CAMBIANDO_ESCUELA);

    const peticion = http.expectOne(URL_CAMBIO);
    expect(peticion.request.body).toEqual({ organizacionId: 2 });
    peticion.flush({ token: 'nuevo' });
    fixture.detectChanges();
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
    expect(pestanas.anunciarCambio).toHaveBeenCalledOnceWith({ id: 2, nombre: 'Club Ficticio Sur' });
    expect(pestanas.confirmarCambio).toHaveBeenCalledOnceWith('id-1');
  });

  it('un doble clic no lanza dos cambios', async () => {
    await montar();
    const componente = fixture.debugElement.query(By.directive(SelectorEscuelaCabeceraComponent))
      .componentInstance as SelectorEscuelaCabeceraComponent;
    componente.elegir(SUR);
    componente.elegir(SUR);
    componente.elegir(ESTE);
    await esperar();
    http.expectOne(URL_CAMBIO).flush({});
    expect(irA).toHaveBeenCalledTimes(1);
  });

  it('si el cambio falla, la escuela no cambia: vuelve a la pantalla y avisa con «Reintentar»', async () => {
    await montar();
    abrir();
    items()[0].click();
    fixture.detectChanges();
    await esperar();
    http.expectOne(URL_CAMBIO).flush({ mensaje: 'Acceso denegado.' }, { status: 403, statusText: 'Forbidden' });
    await esperar();
    fixture.detectChanges();

    expect(TestBed.inject(SelectorEscuelaService).entrando()).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/');
    expect(irA).not.toHaveBeenCalled();
    expect(avisos.error).toHaveBeenCalledTimes(1);
    const [texto, accion] = avisos.error.calls.mostRecent().args;
    expect(texto).toBe('No se ha podido cambiar de escuela. Inténtalo otra vez.');
    expect(accion?.texto).toBe('Reintentar');
    expect(document.activeElement).toBe(boton());

    accion!.alPulsar();
    fixture.detectChanges();
    await esperar();
    http.expectOne(URL_CAMBIO).flush({});
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
  });

  it('en el escritorio, Tab cierra el menú y deja seguir', async () => {
    const original = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((consulta: string) =>
      consulta === CONSULTA_HOJA ? ({ ...original(consulta), matches: false } as MediaQueryList) : original(consulta)
    );
    await montar();
    abrir();
    expect(el().querySelector('dialog')).toBeNull();
    tecla(items()[1], 'Tab');
    expect(menu()).toBeNull();
  });

  describe('al cruzar los 768 px con el menú o la hoja abiertos (N3)', () => {
    let alCambiar: (() => void) | null;
    let quitado: jasmine.Spy;
    let hojaAhora: boolean;

    beforeEach(() => {
      alCambiar = null;
      quitado = jasmine.createSpy('removeEventListener');
      hojaAhora = false;
      const original = window.matchMedia.bind(window);
      spyOn(window, 'matchMedia').and.callFake((consulta: string) =>
        consulta === CONSULTA_HOJA
          ? ({
              get matches() {
                return hojaAhora;
              },
              media: consulta,
              addEventListener: (_tipo: string, oyente: () => void) => (alCambiar = oyente),
              removeEventListener: quitado,
            } as unknown as MediaQueryList)
          : original(consulta)
      );
    });

    it('al estrecharse con el menú de escritorio abierto, lo cierra y devuelve el foco al botón', async () => {
      await montar();
      abrir();
      expect(menu()).not.toBeNull();
      hojaAhora = true;
      alCambiar!();
      fixture.detectChanges();
      expect(menu()).toBeNull();
      expect(document.activeElement).toBe(boton());
    });

    it('al ensancharse con la hoja abierta, la cierra (sale de la pila de capas) y devuelve el foco', async () => {
      hojaAhora = true;
      await montar();
      abrir();
      expect(el().querySelector('dialog.pn-selector__hoja')).not.toBeNull();
      hojaAhora = false;
      alCambiar!();
      fixture.detectChanges();
      expect(el().querySelector('dialog.pn-selector__hoja')).toBeNull();
      expect(TestBed.inject(PilaCapas).arriba()).toBeNull();
      expect(document.activeElement).toBe(boton());
    });

    it('cerrado no hace nada, y deja de escuchar al destruirse', async () => {
      await montar();
      boton().blur();
      alCambiar!();
      fixture.detectChanges();
      expect(menu()).toBeNull();
      expect(document.activeElement).not.toBe(boton());
      fixture.destroy();
      expect(quitado).toHaveBeenCalledOnceWith('change', alCambiar);
    });
  });

  describe('por debajo de 768 px (hoja inferior modal)', () => {
    beforeEach(() => {
      const original = window.matchMedia.bind(window);
      spyOn(window, 'matchMedia').and.callFake((consulta: string) =>
        consulta === CONSULTA_HOJA ? ({ ...original(consulta), matches: true } as MediaQueryList) : original(consulta)
      );
    });

    const hoja = (): HTMLDialogElement | null => el().querySelector<HTMLDialogElement>('dialog.pn-selector__hoja');
    const conMayus = (destino: HTMLElement): void => {
      destino.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };

    it('es un diálogo modal: el resto de la página queda inerte', async () => {
      await montar();
      abrir();
      const dialogo = hoja()!;
      expect(dialogo).not.toBeNull();
      expect(dialogo.matches(':modal')).toBeTrue();
      expect(dialogo.getAttribute('aria-labelledby')).toBe('pn-escuela-titulo');
      expect(dialogo.querySelector('#pn-menu-escuela')).not.toBeNull();
      expect(document.activeElement).toBe(items()[1]);
      const fuera = el().querySelector<HTMLElement>('.fuera')!;
      fuera.focus();
      expect(document.activeElement).not.toBe(fuera);
    });

    it('Tab y Mayús+Tab dan la vuelta dentro de la hoja', async () => {
      await montar();
      abrir();
      const lista = enfocables(hoja()!);
      expect(lista.length).toBeGreaterThan(1);
      const primero = lista[0];
      const ultimo = lista[lista.length - 1];
      ultimo.focus();
      tecla(ultimo, 'Tab');
      expect(document.activeElement).toBe(primero);
      expect(hoja()).not.toBeNull();
      conMayus(primero);
      expect(document.activeElement).toBe(ultimo);
      expect(hoja()).not.toBeNull();
    });

    it('Esc la cierra y devuelve el foco al botón', async () => {
      await montar();
      abrir();
      tecla(items()[1], 'Escape');
      expect(hoja()).toBeNull();
      expect(document.activeElement).toBe(boton());
      expect(TestBed.inject(PilaCapas).arriba()).toBeNull();
    });

    it('el Esc del navegador (cancel del diálogo) también la cierra y devuelve el foco', async () => {
      await montar();
      abrir();
      expect(TestBed.inject(PilaCapas).arriba()).toBe(hoja());
      const cancelar = new Event('cancel', { cancelable: true });
      hoja()!.dispatchEvent(cancelar);
      fixture.detectChanges();
      expect(cancelar.defaultPrevented).toBeTrue();
      expect(hoja()).toBeNull();
      expect(document.activeElement).toBe(boton());
    });

    it('d546: deslizar el asa hacia abajo la cierra (sale de la pila de capas); lo que se desplaza es el menú', async () => {
      await montar();
      abrir();
      const menu = hoja()!.querySelector<HTMLElement>('.pn-selector__menu')!;
      deslizarAsa(hoja()!.querySelector('.pn-selector__asa')!);
      expect(menu.style.transform).toBe('translateY(100%)');
      expect(hoja()!.style.transform).toBe('');
      await new Promise((resuelve) => setTimeout(resuelve, SALIDA_HOJA_MS));
      fixture.detectChanges();
      expect(hoja()).toBeNull();
      expect(TestBed.inject(PilaCapas).arriba()).toBeNull();
      expect(document.activeElement).toBe(boton());
    });
  });
});
