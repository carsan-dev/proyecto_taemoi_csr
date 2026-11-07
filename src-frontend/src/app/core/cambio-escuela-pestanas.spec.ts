import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  CANAL_CAMBIO_ESCUELA,
  CambioEscuelaPestanasService,
  ESPERA_CIERRE_OTRA_PESTANA_MS,
  EstadoCambioEscuela,
  IR_A_URL,
  MENSAJE_CAMBIO_ESCUELA,
  MensajeCambioEscuela,
  PASA_BLOQUEO_CAMBIO_ESCUELA,
  RECARGAR_PAGINA,
  RUTA_ENTRADA_SESION,
  bloqueoCambioEscuelaInterceptor,
} from './cambio-escuela-pestanas';

describe('CambioEscuelaPestanasService (aviso entre pestañas)', () => {
  let servicio: CambioEscuelaPestanasService;
  let irA: jasmine.Spy;
  let recarga: jasmine.Spy;
  let otraPestana: BroadcastChannel;
  let fuera: HTMLButtonElement;
  let http: HttpClient;
  let control: HttpTestingController;

  const capas = (): HTMLDialogElement[] =>
    Array.from(document.querySelectorAll<HTMLDialogElement>('dialog.taemoi-cambio-escuela'));
  const capa = (): HTMLDialogElement | null => capas()[0] ?? null;

  const SUR = { id: 2, nombre: 'Club Ficticio Sur' };
  const mensaje = (
    idCambio: string,
    estado: EstadoCambioEscuela,
    escuelaNueva: { id: number; nombre: string } | null = SUR
  ): MensajeCambioEscuela => ({ tipo: MENSAJE_CAMBIO_ESCUELA, idCambio, estado, escuelaNueva });

  /** Mensaje de otra pestaña por el evento `storage` (llega en el acto). */
  const porAlmacen = (m: MensajeCambioEscuela): void => {
    window.dispatchEvent(new StorageEvent('storage', { key: CANAL_CAMBIO_ESCUELA, newValue: JSON.stringify(m) }));
  };

  /** Los mensajes de BroadcastChannel y la carga de la capa llegan en otra tarea. */
  async function esperarA(condicion: () => boolean): Promise<void> {
    for (let i = 0; i < 100 && !condicion(); i++) {
      await new Promise((r) => setTimeout(r, 10));
    }
  }
  const pausa = (ms = 60): Promise<void> => new Promise((r) => setTimeout(r, ms));

  beforeEach(() => {
    irA = jasmine.createSpy('irA');
    recarga = jasmine.createSpy('recarga');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([bloqueoCambioEscuelaInterceptor])),
        provideHttpClientTesting(),
        { provide: IR_A_URL, useValue: irA },
        { provide: RECARGAR_PAGINA, useValue: recarga },
      ],
    });
    servicio = TestBed.inject(CambioEscuelaPestanasService);
    http = TestBed.inject(HttpClient);
    control = TestBed.inject(HttpTestingController);
    otraPestana = new BroadcastChannel(CANAL_CAMBIO_ESCUELA);
    fuera = document.createElement('button');
    fuera.textContent = 'Botón de la pantalla vieja';
    document.body.appendChild(fuera);
  });

  afterEach(() => {
    otraPestana.close();
    fuera.remove();
    control.verify();
    TestBed.resetTestingModule();
    expect(capa()).toBeNull();
  });

  describe('la pestaña que cambia', () => {
    it('anuncia «cambiando» con la escuela nueva por los dos canales y bloquea todo salvo la petición del cambio', async () => {
      servicio.iniciar();
      const recibidos: MensajeCambioEscuela[] = [];
      otraPestana.onmessage = (evento) => recibidos.push(evento.data);
      const almacen = spyOn(Storage.prototype, 'setItem').and.callThrough();

      const id = servicio.anunciarCambio(SUR);
      expect(servicio.bloqueada).toBeTrue();
      expect(almacen).toHaveBeenCalledOnceWith(CANAL_CAMBIO_ESCUELA, JSON.stringify(mensaje(id, 'cambiando')));
      await esperarA(() => recibidos.length > 0);
      expect(recibidos).toEqual([mensaje(id, 'cambiando')]);
      // La clave se quita al momento: el almacén no guarda nada.
      expect(localStorage.getItem(CANAL_CAMBIO_ESCUELA)).toBeNull();

      http.get('/api/alumnos').subscribe();
      http.post('/api/auth/organizacion', {}, { context: new HttpContext().set(PASA_BLOQUEO_CAMBIO_ESCUELA, true) }).subscribe();
      control.expectNone('/api/alumnos');
      control.expectOne('/api/auth/organizacion').flush({});
      // Nunca se tapa a sí misma.
      expect(capa()).toBeNull();

      // Rechazado: «cancelado» y la petición que esperaba sale tal cual.
      servicio.cancelarCambio(id);
      expect(servicio.bloqueada).toBeFalse();
      control.expectOne('/api/alumnos').flush([]);
      await esperarA(() => recibidos.length > 1);
      expect(recibidos[1]).toEqual(mensaje(id, 'cancelado', null));
    });

    it('confirmado: publica el nombre y ninguna petición vuelve a salir hasta recargar', async () => {
      servicio.iniciar();
      const recibidos: MensajeCambioEscuela[] = [];
      otraPestana.onmessage = (evento) => recibidos.push(evento.data);
      const id = servicio.anunciarCambio(SUR);
      let respondida = false;
      http.post('/api/alumnos/41', {}).subscribe({ next: () => (respondida = true), error: () => (respondida = true) });

      servicio.confirmarCambio(id);
      await esperarA(() => recibidos.length > 1);
      expect(recibidos[1]).toEqual(mensaje(id, 'confirmado'));
      http.get('/api/despues').subscribe();
      control.expectNone('/api/alumnos/41');
      control.expectNone('/api/despues');
      expect(respondida).toBeFalse();
      expect(servicio.bloqueada).toBeTrue();
      expect(capa()).toBeNull();
    });

    it('sus propios mensajes de vuelta (por el otro canal) no la tocan', () => {
      servicio.iniciar();
      const id = servicio.anunciarCambio(SUR);
      servicio.cancelarCambio(id);
      porAlmacen(mensaje(id, 'cambiando'));
      porAlmacen(mensaje(id, 'confirmado'));
      expect(servicio.bloqueada).toBeFalse();
      expect(capa()).toBeNull();
    });

    it('incierto (bloquear): avisa ya a las demás con «confirmado» sin nombre, y esta pestaña queda bloqueada sin capa', async () => {
      servicio.iniciar();
      const recibidos: MensajeCambioEscuela[] = [];
      otraPestana.onmessage = (evento) => recibidos.push(evento.data);
      const id = servicio.anunciarCambio(SUR);
      servicio.bloquear(id);
      await esperarA(() => recibidos.length > 1);
      expect(recibidos[1]).toEqual(mensaje(id, 'confirmado', null));
      expect(servicio.bloqueada).toBeTrue();
      expect(capa()).toBeNull();
    });
  });

  describe('las demás pestañas', () => {
    it('incierto: el aviso sin nombre muestra la capa al momento, sin esperar a la espera de 30 s', async () => {
      servicio.iniciar();
      const t0 = Date.now();
      porAlmacen(mensaje('a10', 'confirmado', null));
      await esperarA(() => !!capa());
      expect(capa()).not.toBeNull();
      expect(Date.now() - t0).toBeLessThan(ESPERA_CIERRE_OTRA_PESTANA_MS);
      expect(capa()!.querySelector('strong')).toBeNull();
      expect(capa()!.querySelector('#taemoi-cambio-escuela-texto')!.textContent).toBe(
        'Lo hiciste en otra pestaña. Recarga esta para seguir con los datos de la escuela nueva.'
      );
      expect(servicio.bloqueada).toBeTrue();
      capa()!.querySelector('button')!.click();
      expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
    });

    it('con «cambiando» bloquean ya sus peticiones, sin capa; con «cancelado» se desbloquean y salen', async () => {
      servicio.iniciar();
      otraPestana.postMessage(mensaje('a1', 'cambiando'));
      await esperarA(() => servicio.bloqueada);
      expect(servicio.bloqueada).toBeTrue();

      let respuesta: unknown = null;
      http.post('/api/alumnos/41', { nombre: 'Lucía' }).subscribe((r) => (respuesta = r));
      control.expectNone('/api/alumnos/41');
      await pausa();
      expect(capa()).toBeNull();

      otraPestana.postMessage(mensaje('a1', 'cancelado', null));
      await esperarA(() => !servicio.bloqueada);
      expect(servicio.bloqueada).toBeFalse();
      control.expectOne('/api/alumnos/41').flush({ ok: true });
      expect(respuesta).toEqual({ ok: true });
      await pausa();
      expect(capa()).toBeNull();
    });

    it('con «confirmado» se tapan con la capa (con el nombre) y lo pendiente no sale nunca', async () => {
      servicio.iniciar();
      otraPestana.postMessage(mensaje('a2', 'cambiando'));
      await esperarA(() => servicio.bloqueada);
      http.post('/api/alumnos/41', {}).subscribe();
      otraPestana.postMessage(mensaje('a2', 'confirmado'));
      await esperarA(() => !!capa());

      const dialogo = capa()!;
      expect(dialogo.matches(':modal')).toBeTrue();
      expect(dialogo.getAttribute('role')).toBe('alertdialog');
      expect(dialogo.querySelector('h2')!.textContent).toBe('Has cambiado de escuela');
      expect(dialogo.querySelector('#taemoi-cambio-escuela-texto')!.textContent).toBe(
        'Lo hiciste en otra pestaña. Recarga esta para seguir con los datos de Club Ficticio Sur.'
      );
      expect(dialogo.querySelector('strong')!.textContent).toBe('Club Ficticio Sur');
      const recargar = dialogo.querySelector('button')!;
      expect(document.activeElement).toBe(recargar);
      expect(servicio.bloqueada).toBeTrue();
      control.expectNone('/api/alumnos/41');

      // El fondo es inerte y Esc no la cierra.
      fuera.focus();
      expect(document.activeElement).not.toBe(fuera);
      const esc = new Event('cancel', { cancelable: true });
      dialogo.dispatchEvent(esc);
      expect(esc.defaultPrevented).toBeTrue();
      expect(dialogo.open).toBeTrue();

      // Un «cancelado» tardío no la quita: el cambio ya se confirmó.
      porAlmacen(mensaje('a2', 'cancelado', null));
      expect(capa()).toBe(dialogo);
      expect(servicio.bloqueada).toBeTrue();
    });

    it('«Recargar» vacía el sessionStorage de esta pestaña y recarga la app en la entrada de la sesión', async () => {
      sessionStorage.setItem('alumnoId', '41');
      servicio.iniciar();
      porAlmacen(mensaje('a3', 'confirmado'));
      await esperarA(() => !!capa());
      capa()!.querySelector('button')!.click();
      expect(sessionStorage.getItem('alumnoId')).toBeNull();
      expect(irA).toHaveBeenCalledOnceWith(RUTA_ENTRADA_SESION);
    });

    it(`sin cierre en ${ESPERA_CIERRE_OTRA_PESTANA_MS / 1000} s muestran la capa (sin nombre); un «cancelado» tardío la quita`, async () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const mostrar = spyOn<any>(servicio, 'mostrarCapa').and.callThrough();
      jasmine.clock().install();
      try {
        servicio.iniciar();
        porAlmacen(mensaje('a4', 'cambiando'));
        expect(servicio.bloqueada).toBeTrue();
        jasmine.clock().tick(ESPERA_CIERRE_OTRA_PESTANA_MS - 1);
        expect(mostrar).not.toHaveBeenCalled();
        jasmine.clock().tick(1);
        expect(mostrar).toHaveBeenCalledOnceWith(null);
      } finally {
        jasmine.clock().uninstall();
      }
      await esperarA(() => !!capa());
      expect(capa()!.querySelector('#taemoi-cambio-escuela-texto')!.textContent).toBe(
        'Lo hiciste en otra pestaña. Recarga esta para seguir con los datos de la escuela nueva.'
      );
      expect(servicio.bloqueada).toBeTrue();

      porAlmacen(mensaje('a4', 'cancelado', null));
      expect(capa()).toBeNull();
      expect(servicio.bloqueada).toBeFalse();
    });

    it('si tras la espera llega el «confirmado», la capa sigue y toma el nombre', async () => {
      jasmine.clock().install();
      try {
        servicio.iniciar();
        porAlmacen(mensaje('a5', 'cambiando'));
        jasmine.clock().tick(ESPERA_CIERRE_OTRA_PESTANA_MS);
      } finally {
        jasmine.clock().uninstall();
      }
      await esperarA(() => !!capa());
      porAlmacen(mensaje('a5', 'confirmado'));
      expect(capas().length).toBe(1);
      expect(capa()!.querySelector('strong')?.textContent).toBe('Club Ficticio Sur');
    });

    it('el mismo mensaje por los dos canales se atiende una vez (una capa)', async () => {
      servicio.iniciar();
      porAlmacen(mensaje('a6', 'cambiando'));
      otraPestana.postMessage(mensaje('a6', 'cambiando'));
      otraPestana.postMessage(mensaje('a6', 'confirmado'));
      porAlmacen(mensaje('a6', 'confirmado'));
      await esperarA(() => !!capa());
      await pausa();
      expect(capas().length).toBe(1);
    });

    it('un «cambiando» repetido tras su «cancelado» no vuelve a bloquear', () => {
      servicio.iniciar();
      porAlmacen(mensaje('a8', 'cambiando'));
      porAlmacen(mensaje('a8', 'cancelado', null));
      porAlmacen(mensaje('a8', 'cambiando'));
      expect(servicio.bloqueada).toBeFalse();
    });

    it('ignora otros mensajes, valores ajenos del almacén y su borrado', async () => {
      servicio.iniciar();
      otraPestana.postMessage({ tipo: 'otra-cosa' });
      otraPestana.postMessage({ tipo: MENSAJE_CAMBIO_ESCUELA, idCambio: 'x', estado: 'raro' });
      window.dispatchEvent(new StorageEvent('storage', { key: 'otra-clave', newValue: '1' }));
      window.dispatchEvent(new StorageEvent('storage', { key: CANAL_CAMBIO_ESCUELA, newValue: 'no es JSON' }));
      window.dispatchEvent(new StorageEvent('storage', { key: CANAL_CAMBIO_ESCUELA, newValue: null }));
      await pausa();
      expect(capa()).toBeNull();
      expect(servicio.bloqueada).toBeFalse();
    });

    it('el nombre de la escuela se pinta como texto, nunca como HTML', async () => {
      servicio.iniciar();
      porAlmacen(mensaje('a9', 'confirmado', { id: 9, nombre: '<img src=x onerror="alert(1)">' }));
      await esperarA(() => !!capa());
      expect(capa()!.querySelector('img:not(.tce-web__logo)')).toBeNull();
      expect(capa()!.querySelector('strong')!.textContent).toBe('<img src=x onerror="alert(1)">');
    });
  });
  describe('al volver con Atrás desde la caché (bfcache)', () => {
    const volver = (persistida: boolean): void => {
      window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: persistida }));
    };

    it('con la pestaña bloqueada por un cambio, recarga', () => {
      servicio.iniciar();
      const idCambio = servicio.anunciarCambio(SUR);
      volver(true);
      expect(recarga).toHaveBeenCalledTimes(1);
      servicio.cancelarCambio(idCambio);
    });

    it('sin bloqueo, o sin que la página venga de la caché, no recarga', () => {
      servicio.iniciar();
      volver(true);
      const idCambio = servicio.anunciarCambio(SUR);
      volver(false);
      expect(recarga).not.toHaveBeenCalled();
      servicio.cancelarCambio(idCambio);
    });
  });
});
