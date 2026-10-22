import { Component } from '@angular/core';
import { TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { CLASE_ERROR_HTTP, PilaCapas } from '../capas/capa-modal';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { disenoNuevoGuard } from '../diseno-nuevo.guard';
import { EstilosPanelService } from '../estilos-panel.service';
import { AvisosService, DURACION_AVISO, MAX_AVISOS } from './avisos.service';

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

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'alumnosListar', canMatch: [disenoNuevoGuard], component: PantallaNueva }]),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) },
        },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    });
    avisos = TestBed.inject(AvisosService);
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
    expect(aviso.getAttribute('role')).toBe('status');
    expect(aviso.textContent).toContain('Cobro registrado: 30,00 € · Adrián Prueba Ficticia');
    expect(aviso.querySelector('.pn-aviso__tiempo')).not.toBeNull();
    tick(DURACION_AVISO - 1);
    pintar();
    expect(tarjetas().length).toBe(1);
    tick(1);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('información: role=status y también se cierra a los 5 s', fakeAsync(() => {
    avisos.info('Lista de asistencia preparada');
    pintar();
    expect(tarjetas()[0].getAttribute('role')).toBe('status');
    tick(DURACION_AVISO);
    pintar();
    expect(tarjetas().length).toBe(0);
  }));

  it('error: role=alert, sin barra de tiempo, y no se cierra solo', fakeAsync(() => {
    avisos.error('No se pudo guardar la ficha. Revisa la conexión y vuelve a intentarlo.');
    pintar();
    const [aviso] = tarjetas();
    expect(aviso.getAttribute('role')).toBe('alert');
    expect(aviso.querySelector('.pn-aviso__tiempo')).toBeNull();
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

  it('un error en una pantalla nueva cierra el SweetAlert2 del interceptor (como el aviso de hoy, que lo sustituía)', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosListar');
    void Swal.fire({ title: 'Error', showClass: { popup: '' }, hideClass: { popup: '' }, customClass: { popup: CLASE_ERROR_HTTP } });
    expect(Swal.isVisible()).toBeTrue();
    avisos.error('No se pudo cargar la mensualidad');
    expect(Swal.isVisible()).toBeFalse();
  });

  it('un error no cierra un SweetAlert2 que no sea el del interceptor', async () => {
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
});
