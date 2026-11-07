import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  CANAL_CAMBIO_ESCUELA,
  CambioEscuelaPestanasService,
  IR_A_URL,
  MENSAJE_ESCUELA_CAMBIADA,
  RUTA_ENTRADA_SESION,
  TEXTO_CAMBIO_OTRA_PESTANA,
  bloqueoCambioEscuelaInterceptor,
} from './cambio-escuela-pestanas';

describe('CambioEscuelaPestanasService (aviso entre pestañas)', () => {
  let servicio: CambioEscuelaPestanasService;
  let irA: jasmine.Spy;
  let otraPestana: BroadcastChannel;
  let fuera: HTMLButtonElement;

  const capa = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.taemoi-cambio-escuela');

  /** Los mensajes de BroadcastChannel llegan en otra tarea. */
  async function esperarMensaje(): Promise<void> {
    for (let i = 0; i < 20 && !capa(); i++) {
      await new Promise((r) => setTimeout(r, 10));
    }
  }

  beforeEach(() => {
    irA = jasmine.createSpy('irA');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([bloqueoCambioEscuelaInterceptor])),
        provideHttpClientTesting(),
        { provide: IR_A_URL, useValue: irA },
      ],
    });
    servicio = TestBed.inject(CambioEscuelaPestanasService);
    otraPestana = new BroadcastChannel(CANAL_CAMBIO_ESCUELA);
    fuera = document.createElement('button');
    fuera.textContent = 'Botón de la pantalla vieja';
    document.body.appendChild(fuera);
  });

  afterEach(() => {
    otraPestana.close();
    fuera.remove();
    TestBed.resetTestingModule();
    expect(capa()).toBeNull();
  });

  it('al recibir el aviso de otra pestaña, tapa esta con una capa modal y la bloquea', async () => {
    servicio.iniciar();
    expect(servicio.bloqueada).toBeFalse();
    otraPestana.postMessage({ tipo: MENSAJE_ESCUELA_CAMBIADA });
    await esperarMensaje();

    const dialogo = capa()!;
    expect(dialogo).not.toBeNull();
    expect(dialogo.matches(':modal')).toBeTrue();
    expect(dialogo.getAttribute('role')).toBe('alertdialog');
    expect(dialogo.textContent).toContain(TEXTO_CAMBIO_OTRA_PESTANA);
    expect(TEXTO_CAMBIO_OTRA_PESTANA).toBe('Has cambiado de escuela en otra pestaña');
    const recargar = dialogo.querySelector('button')!;
    expect(recargar.textContent).toBe('Recargar');
    expect(document.activeElement).toBe(recargar);
    expect(servicio.bloqueada).toBeTrue();

    // El fondo es inerte: no recibe el foco.
    fuera.focus();
    expect(document.activeElement).not.toBe(fuera);

    // Esc no la cierra.
    const esc = new Event('cancel', { cancelable: true });
    dialogo.dispatchEvent(esc);
    expect(esc.defaultPrevented).toBeTrue();
    expect(dialogo.open).toBeTrue();
  });

  it('«Recargar» vacía el sessionStorage de esta pestaña y recarga la app en la entrada de la sesión', async () => {
    sessionStorage.setItem('alumnoId', '41');
    servicio.iniciar();
    otraPestana.postMessage({ tipo: MENSAJE_ESCUELA_CAMBIADA });
    await esperarMensaje();
    capa()!.querySelector('button')!.click();
    expect(sessionStorage.getItem('alumnoId')).toBeNull();
    expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
  });

  it('sin BroadcastChannel, la alternativa del evento storage también la tapa', () => {
    servicio.iniciar();
    window.dispatchEvent(new StorageEvent('storage', { key: 'otra-clave', newValue: '1' }));
    expect(capa()).toBeNull();
    window.dispatchEvent(new StorageEvent('storage', { key: CANAL_CAMBIO_ESCUELA, newValue: String(Date.now()) }));
    expect(capa()?.matches(':modal')).toBeTrue();
    expect(servicio.bloqueada).toBeTrue();
  });

  it('ignora otros mensajes del canal', async () => {
    servicio.iniciar();
    otraPestana.postMessage({ tipo: 'otra-cosa' });
    await new Promise((r) => setTimeout(r, 50));
    expect(capa()).toBeNull();
    expect(servicio.bloqueada).toBeFalse();
  });

  it('avisarOtras manda el mensaje a las demás pestañas (y no se tapa a sí misma)', async () => {
    servicio.iniciar();
    const recibidos: unknown[] = [];
    otraPestana.onmessage = (evento) => recibidos.push(evento.data);
    servicio.avisarOtras();
    for (let i = 0; i < 20 && recibidos.length === 0; i++) {
      await new Promise((r) => setTimeout(r, 10));
    }
    expect(recibidos).toEqual([{ tipo: MENSAJE_ESCUELA_CAMBIADA }]);
    expect(capa()).toBeNull();
  });

  it('una pestaña bloqueada no deja salir ninguna petición', () => {
    const http = TestBed.inject(HttpClient);
    const control = TestBed.inject(HttpTestingController);
    http.get('/api/antes').subscribe();
    control.expectOne('/api/antes').flush({});

    servicio.bloquear();
    let respondida = false;
    http.post('/api/alumnos/41', {}).subscribe({ next: () => (respondida = true), error: () => (respondida = true) });
    http.get('/api/despues').subscribe();
    control.expectNone('/api/alumnos/41');
    control.expectNone('/api/despues');
    expect(respondida).toBeFalse();
    control.verify();
  });
});
