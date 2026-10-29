import { Component } from '@angular/core';
import { TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { CLASE_ERROR_HTTP, PilaCapas } from '../capas/capa-modal';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { estilosPanelGuard } from '../estilos-panel.guard';
import { EstilosPanelService } from '../estilos-panel.service';
import { AcentoEscuelaActual } from '../acento-escuela.directive';
import { SIERRA_PRUEBA } from '../tema-prueba';
import { AvisosService, DURACION_AVISO, MAX_AVISOS, RETRASO_ANUNCIO } from './avisos.service';
import { itSinReloj } from '../../testing/reloj-prueba';

@Component({ standalone: true, template: '<p>Pantalla nueva</p>' })
class PantallaNueva {}

describe('AvisosService y AvisosComponent (8b-D1, avisos)', () => {
  let avisos: AvisosService;
  let quitarTokens: () => void;

  function pintar(): void {
    pintarCapas();
  }

  function region(): HTMLElement | null {
    return document.querySelector<HTMLElement>('section.pn-avisos');
  }

  function tarjetas(): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso'));
  }

  /** Las regiones vivas persistentes: `role=status` (éxito e información) y `role=alert` (error). */
  function vivo(rol: 'status' | 'alert'): HTMLElement | null {
    return document.querySelector<HTMLElement>(`pn-avisos .pn-avisos__vivo[role="${rol}"]`);
  }

  function anunciado(rol: 'status' | 'alert'): string[] {
    return Array.from(vivo(rol)?.querySelectorAll('p') ?? []).map((p) => p.textContent!.trim());
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'alumnosListar', canMatch: [estilosPanelGuard], component: PantallaNueva },
          { path: 'pantalla-a', component: PantallaNueva },
          { path: 'pantalla-b', component: PantallaNueva },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({}), reiniciar: () => undefined },
        },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    });
    avisos = TestBed.inject(AvisosService);
    // El effect del servicio deja pedido un pintado (setTimeout y requestAnimationFrame reales). Si se cancelara ya dentro
    // de fakeAsync, la zona simulada borraría el temporizador simulado con el mismo número (el retraso del anuncio de la
    // prueba): pasaba con la prueba primera del navegador, cuando los dos números aún son bajos. Se pinta aquí, fuera.
    pintar();
  });

  afterEach(() => {
    avisos.cerrarTodos();
    pintar();
    quitarTokens();
  });

  it('los constantes: 5 s y como mucho 3', () => {
    expect(DURACION_AVISO).toBe(5000);
    expect(MAX_AVISOS).toBe(3);
  });

  it('éxito: role=status, abajo a la derecha, con barra de tiempo, y se cierra a los 5 s', fakeAsync(() => {
    avisos.exito('Cobro registrado: 30,00 € · Adrián Prueba Ficticia');
    pintar();
    const r = region()!;
    expect(r.getAttribute('aria-label')).toBe('Avisos');
    expect(r.closest('.pn')).not.toBeNull();
    const [aviso] = tarjetas();
    expect(aviso.hasAttribute('role')).toBeFalse();
    expect(aviso.textContent).toContain('Cobro registrado: 30,00 € · Adrián Prueba Ficticia');
    tick(RETRASO_ANUNCIO);
    pintar();
    expect(anunciado('status')).toEqual(['Cobro registrado: 30,00 € · Adrián Prueba Ficticia']);
    expect(anunciado('alert')).toEqual([]);
    expect(aviso.querySelector('.pn-aviso__tiempo')).not.toBeNull();
    tick(DURACION_AVISO - 1 - RETRASO_ANUNCIO);
    pintar();
    expect(tarjetas().length).toBe(1);
    tick(1);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('8b-M: un éxito con mascota (cierre de tarea larga) lleva la gota de 32 px decorativa en lugar del icono; sin ella, el icono', fakeAsync(() => {
    avisos.exito('Mensualidades asignadas a todos los alumnos activos', undefined, { mascota: 'celebrar' });
    avisos.exito('Guardado');
    pintar();
    const [conMascota, sinMascota] = tarjetas();
    const gota = conMascota.querySelector<HTMLElement>('pn-mascota')!;
    expect(gota.classList).toContain('pn-mascota--sm');
    expect(gota.getAttribute('aria-hidden')).toBe('true');
    expect(conMascota.querySelector('.pn-aviso__icono')).toBeNull();
    expect(conMascota.querySelector('.pn-aviso__texto')!.textContent).toContain('Mensualidades asignadas');
    expect(sinMascota.querySelector('pn-mascota')).toBeNull();
    expect(sinMascota.querySelector('.pn-aviso__icono')).not.toBeNull();
    tick(DURACION_AVISO);
    flush();
  }));

  it('información: role=status y también se cierra a los 5 s', fakeAsync(() => {
    avisos.info('Lista de asistencia preparada');
    pintar();
    tick(RETRASO_ANUNCIO);
    pintar();
    expect(anunciado('status')).toEqual(['Lista de asistencia preparada']);
    tick(DURACION_AVISO - RETRASO_ANUNCIO);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('error: role=alert, sin barra de tiempo, y no se cierra solo', fakeAsync(() => {
    avisos.error('No se pudo guardar la ficha. Revisa la conexión y vuelve a intentarlo.');
    pintar();
    const [aviso] = tarjetas();
    expect(aviso.hasAttribute('role')).toBeFalse();
    expect(aviso.querySelector('.pn-aviso__tiempo')).toBeNull();
    tick(RETRASO_ANUNCIO);
    pintar();
    expect(anunciado('alert')).toEqual(['No se pudo guardar la ficha. Revisa la conexión y vuelve a intentarlo.']);
    expect(anunciado('status')).toEqual([]);
    tick(60_000);
    pintar();
    expect(tarjetas().length).toBe(1);
    aviso.querySelector<HTMLButtonElement>('button[aria-label="Cerrar aviso"]')!.click();
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('como mucho 3 a la vez: el cuarto saca al más antiguo', fakeAsync(() => {
    avisos.error('uno');
    avisos.error('dos');
    avisos.error('tres');
    avisos.error('cuatro');
    pintar();
    expect(tarjetas().map((t) => t.querySelector('p')!.textContent!.trim())).toEqual(['dos', 'tres', 'cuatro']);
    flush();
  }));

  it('las regiones vivas se crean vacías y los avisos llegan después (un lector no anuncia una región que nace llena)', fakeAsync(() => {
    avisos.exito('Guardado');
    pintar();
    expect(vivo('status')).not.toBeNull();
    expect(vivo('alert')).not.toBeNull();
    expect(vivo('status')!.textContent!.trim()).toBe('');
    expect(vivo('alert')!.textContent!.trim()).toBe('');
    expect(tarjetas().length).toBe(1);
    tick(RETRASO_ANUNCIO);
    pintar();
    expect(anunciado('status')).toEqual(['Guardado']);
    avisos.error('No se pudo guardar');
    pintar();
    tick(RETRASO_ANUNCIO);
    pintar();
    expect(anunciado('alert')).toEqual(['No se pudo guardar']);
    // Siguen en su sitio aunque no quede ningún aviso: la siguiente vez ya existen.
    avisos.cerrarTodos();
    pintar();
    expect(vivo('status')!.textContent!.trim()).toBe('');
    expect(vivo('alert')!.textContent!.trim()).toBe('');
    flush();
  }));

  it('barra de tiempo: sin animación con prefers-reduced-motion', fakeAsync(() => {
    spyOn(window, 'matchMedia').and.callFake(
      (consulta: string) =>
        ({
          matches: consulta.includes('prefers-reduced-motion'),
          media: consulta,
          addEventListener: () => undefined,
          removeEventListener: () => undefined,
        }) as unknown as MediaQueryList
    );
    avisos.exito('Guardado');
    pintar();
    const barra = tarjetas()[0].querySelector<HTMLElement>('.pn-aviso__tiempo')!;
    expect(barra.classList).toContain('pn-aviso__tiempo--quieta');
    expect(getComputedStyle(barra).animationName).toBe('none');
    flush();
  }));

  itSinReloj('un error en una pantalla nueva cierra el SweetAlert2 del interceptor (como el aviso de hoy, que lo sustituía)', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosListar');
    void Swal.fire({ title: 'Error', showClass: { popup: '' }, hideClass: { popup: '' }, customClass: { popup: CLASE_ERROR_HTTP } });
    expect(Swal.isVisible()).toBeTrue();
    avisos.error('No se pudo cargar la mensualidad');
    expect(Swal.isVisible()).toBeFalse();
  });

  itSinReloj('un error no cierra un SweetAlert2 que no sea el del interceptor', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosListar');
    void Swal.fire({ title: 'Confirmar cobro', showClass: { popup: '' }, hideClass: { popup: '' } });
    avisos.error('No se pudo cargar la mensualidad');
    expect(Swal.isVisible()).toBeTrue();
    Swal.close();
  });

  it('acción opcional («Deshacer»): la llama y cierra el aviso', fakeAsync(() => {
    const deshacer = jasmine.createSpy('deshacer');
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: deshacer });
    pintar();
    const boton = tarjetas()[0].querySelector<HTMLButtonElement>('.pn-aviso__accion')!;
    expect(boton.textContent!.trim()).toBe('Deshacer');
    boton.click();
    pintar();
    expect(deshacer).toHaveBeenCalledTimes(1);
    expect(tarjetas().length).toBe(0);
    flush();
  }));

  it('segunda acción (accionPrevia): va antes de la principal y cada botón llama a la suya', fakeAsync(() => {
    const recibo = jasmine.createSpy('recibo');
    const deshacer = jasmine.createSpy('deshacer');
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: deshacer }, { accionPrevia: { texto: 'Recibo', alPulsar: recibo } });
    pintar();
    const botones = Array.from(tarjetas()[0].querySelectorAll<HTMLButtonElement>('.pn-aviso__accion'));
    expect(botones.map((b) => b.textContent!.trim())).toEqual(['Recibo', 'Deshacer']);
    botones[0].click();
    pintar();
    expect(recibo).toHaveBeenCalledTimes(1);
    expect(deshacer).not.toHaveBeenCalled();
    expect(tarjetas().length).toBe(0);
    flush();
  }));

  it('accionPrevia con cierra: false no cierra el aviso y «Deshacer» sigue disponible y cierra', fakeAsync(() => {
    const recibo = jasmine.createSpy('recibo');
    const deshacer = jasmine.createSpy('deshacer');
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: deshacer }, { accionPrevia: { texto: 'Recibo', cierra: false, alPulsar: recibo } });
    pintar();
    tarjetas()[0].querySelectorAll<HTMLButtonElement>('.pn-aviso__accion')[0].click();
    pintar();
    expect(recibo).toHaveBeenCalledTimes(1);
    expect(tarjetas().length).toBe(1);
    const botones = Array.from(tarjetas()[0].querySelectorAll<HTMLButtonElement>('.pn-aviso__accion'));
    expect(botones.map((b) => b.textContent!.trim())).toEqual(['Recibo', 'Deshacer']);
    botones[1].click();
    pintar();
    expect(deshacer).toHaveBeenCalledTimes(1);
    expect(tarjetas().length).toBe(0);
    flush();
  }));

  it('un aviso con una sola acción sigue pintando un único botón', fakeAsync(() => {
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: () => undefined });
    pintar();
    expect(tarjetas()[0].querySelectorAll('.pn-aviso__accion').length).toBe(1);
    flush();
  }));

  it('8b-C: con la marca, la acción del aviso va en el acento de la escuela', fakeAsync(() => {
    TestBed.inject(AcentoEscuelaActual).poner(SIERRA_PRUEBA);
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: () => undefined });
    pintar();
    const capa = document.querySelector<HTMLElement>('pn-avisos')!;
    expect(capa.style.getPropertyValue('--pn-acento')).toBe('#0E782D');
    expect(capa.style.getPropertyValue('--pn-marca-primario-tinta')).withContext('tema completo').toBe('#24853A');
    const accion = tarjetas()[0].querySelector<HTMLButtonElement>('.pn-aviso__accion')!;
    expect(getComputedStyle(accion).color).toBe('rgb(14, 120, 45)');
    flush();
  }));

  it('el tiempo se para con el ratón encima y sigue al salir', fakeAsync(() => {
    avisos.exito('Guardado');
    pintar();
    const aviso = tarjetas()[0];
    tick(2000);
    aviso.dispatchEvent(new MouseEvent('mouseenter'));
    tick(10_000);
    pintar();
    expect(tarjetas().length).toBe(1);
    aviso.dispatchEvent(new MouseEvent('mouseleave'));
    tick(3000);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('con una capa modal abierta, los avisos se pintan dentro de ella (si no, quedarían inertes)', fakeAsync(() => {
    const capa = document.createElement('dialog');
    document.body.appendChild(capa);
    capa.showModal();
    const pila = TestBed.inject(PilaCapas);
    pila.abrir(capa);
    avisos.error('Dentro de la capa');
    pintar();
    expect(capa.contains(region())).toBeTrue();
    pila.cerrar(capa);
    pintar();
    expect(capa.contains(region())).toBeFalse();
    expect(region()!.parentElement!.parentElement).toBe(document.body);
    capa.close();
    capa.remove();
    discardPeriodicTasks();
  }));

  it('éxito con acción, con mascota y con «Recibo» (cierra: false) se cierran solos a los 5 s; información con acción también', fakeAsync(() => {
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: () => undefined });
    avisos.exito('Mensualidades generadas', undefined, { mascota: 'celebrar' });
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: () => undefined }, { accionPrevia: { texto: 'Recibo', cierra: false, alPulsar: () => undefined } });
    pintar();
    expect(tarjetas().length).toBe(3);
    tick(DURACION_AVISO - 1);
    pintar();
    expect(tarjetas().length).toBe(3);
    tick(1);
    pintar();
    expect(tarjetas().length).toBe(0);
    avisos.info('PDF preparado', { texto: 'Abrir', alPulsar: () => undefined });
    pintar();
    tick(DURACION_AVISO);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('«Recibo» (cierra: false): pulsarlo no cierra el aviso, pero el tiempo sí', fakeAsync(() => {
    const recibo = jasmine.createSpy('recibo');
    avisos.exito('Cobro registrado', undefined, { accionPrevia: { texto: 'Recibo', cierra: false, alPulsar: recibo } });
    pintar();
    tick(1000);
    tarjetas()[0].querySelector<HTMLButtonElement>('.pn-aviso__accion')!.click();
    pintar();
    expect(recibo).toHaveBeenCalledTimes(1);
    expect(tarjetas().length).toBe(1);
    tick(DURACION_AVISO - 1000);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('con el foco dentro el tiempo se para y, al salir el foco, el aviso se cierra con lo que le quedaba', fakeAsync(() => {
    avisos.exito('Cobro registrado', { texto: 'Deshacer', alPulsar: () => undefined });
    pintar();
    const aviso = tarjetas()[0];
    tick(1000);
    aviso.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    tick(30_000);
    pintar();
    expect(tarjetas().length).toBe(1);
    aviso.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    tick(DURACION_AVISO - 1000);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  describe('al cerrar o caducar la sesión se vacían todos los avisos', () => {
    function mostrarVarios(): void {
      avisos.exito('Guardado');
      avisos.info('Preparado');
      avisos.error('No se pudo generar el recibo');
      pintar();
      expect(tarjetas().length).toBe(3);
    }

    it('limpiarEstadoLocal', fakeAsync(() => {
      mostrarVarios();
      TestBed.inject(AuthenticationService).limpiarEstadoLocal();
      pintar();
      expect(tarjetas().length).toBe(0);
      expect(avisos.lista()).toEqual([]);
      // Sin temporizadores colgando: ni el cierre ni el anuncio.
      tick(DURACION_AVISO);
      expect(avisos.anuncios()).toEqual([]);
    }));

    it('logout (tras la respuesta del servidor)', fakeAsync(() => {
      mostrarVarios();
      TestBed.inject(AuthenticationService).logout();
      TestBed.inject(HttpTestingController).expectOne((r) => r.url.endsWith('/auth/logout')).flush({});
      pintar();
      expect(tarjetas().length).toBe(0);
      tick(DURACION_AVISO);
    }));

    it('forzarCierreSesion (sesión caducada)', fakeAsync(() => {
      mostrarVarios();
      void TestBed.inject(AuthenticationService).forzarCierreSesion();
      pintar();
      expect(tarjetas().length).toBe(0);
      TestBed.inject(HttpTestingController).expectOne((r) => r.url.endsWith('/auth/logout')).flush({});
      tick(DURACION_AVISO);
    }));
  });

  describe('los errores se cierran al cambiar de pantalla', () => {
    it('navegar a otra ruta quita los errores; el éxito en curso sigue su temporizador', fakeAsync(() => {
      const router = TestBed.inject(Router);
      void router.navigateByUrl('/pantalla-a');
      tick();
      avisos.error('No se pudo generar el recibo');
      tick(1000);
      avisos.exito('Guardado');
      pintar();
      void router.navigateByUrl('/pantalla-b');
      tick();
      pintar();
      expect(tarjetas().map((t) => t.querySelector('.pn-aviso__texto')!.textContent!.trim())).toEqual(['Guardado']);
      tick(DURACION_AVISO - 1);
      pintar();
      expect(tarjetas().length).toBe(1);
      tick(1);
      pintar();
      expect(tarjetas().length).toBe(0);
    }));

    it('cambiar solo la query string o el fragmento (pestaña interna) no quita el error', fakeAsync(() => {
      const router = TestBed.inject(Router);
      void router.navigateByUrl('/pantalla-a');
      tick();
      avisos.error('No se pudo generar el recibo');
      pintar();
      void router.navigateByUrl('/pantalla-a?pestana=pagos');
      tick();
      void router.navigateByUrl('/pantalla-a?pestana=pagos#cobros');
      tick();
      pintar();
      expect(tarjetas().length).toBe(1);
      tick(60_000);
      pintar();
      expect(tarjetas().length).toBe(1);
    }));
  });
});
