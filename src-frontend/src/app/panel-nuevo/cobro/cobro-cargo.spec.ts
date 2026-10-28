import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AvisosService } from '../avisos/avisos.service';
import { ReciboPagoService } from './recibo-pago.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { CargoACobrar, CobroCargoService, textosCobro } from './cobro-cargo';

const API = environment.apiUrl;
const NBSP = ' ';

const CARGO: CargoACobrar = {
  productoAlumnoId: 801,
  concepto: 'MENSUALIDAD OCTUBRE 2026 - TAEKWONDO',
  importe: 30,
  alumnoNombre: 'Adrián Prueba Ficticia',
  tipo: 'MENSUALIDAD',
};

/**
 * «Cobrar» de las pantallas nuevas (8b-D1, punto de revisión 8): el diálogo nuevo con el método como pastillas, pero la
 * misma llamada y el mismo cuerpo que «Marcar pagado» de la tesorería de hoy (`PUT /api/productos-alumno/{id}` con
 * `cambiosDeCobro(metodo)`), y «Deshacer» con la misma llamada que «Revertir» de hoy, solo para quien hoy puede revertir.
 */
describe('CobroCargoService (8b-D1, «Cobrar» con el diálogo nuevo)', () => {
  let http: HttpTestingController;
  let servicio: CobroCargoService;
  let esAdmin: boolean;
  let esGestion: boolean;
  let quitarTokens: () => void;

  function pintar(): void {
    pintarCapas();
  }

  function dialogo(): HTMLDialogElement | null {
    return document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  }

  function botonDialogo(texto: string): HTMLButtonElement {
    return Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').trim() === texto
    )!;
  }

  function metodo(texto: string): void {
    Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('[role="radio"]'))
      .find((b) => b.textContent!.trim() === texto)!
      .click();
    pintar();
  }

  function avisos(): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso'));
  }

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintar();
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    esAdmin = false;
    esGestion = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => esAdmin, tieneAccesoAdmin: () => esAdmin || esGestion } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    servicio = TestBed.inject(CobroCargoService);
  });

  afterEach(() => {
    TestBed.inject(AvisosService).cerrarTodos();
    pintar();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    http.verify();
    quitarTokens();
  });

  it('textos: título y frase por tipo; sin tipo, «cargo»', () => {
    expect(textosCobro('MENSUALIDAD')).toEqual({ titulo: 'Cobrar mensualidad', que: 'la mensualidad' });
    expect(textosCobro('EXAMEN')).toEqual({ titulo: 'Cobrar derecho de examen', que: 'el derecho de examen' });
    expect(textosCobro(null)).toEqual({ titulo: 'Cobrar cargo', que: 'el cargo' });
  });

  it('el diálogo lleva concepto, importe es-ES, el nombre y el método como pastillas con «Sin indicar» por defecto', () => {
    void servicio.cobrar(CARGO, new Set(), () => undefined);
    pintar();
    const d = dialogo()!;
    expect(d.querySelector('h2')!.textContent!.trim()).toBe('Cobrar mensualidad');
    expect(d.textContent!.replace(/\s+/g, ' ')).toContain(
      'Se registra el pago de Adrián Prueba Ficticia y la mensualidad deja de figurar como pendiente.'
    );
    expect(d.textContent).toContain('MENSUALIDAD OCTUBRE 2026 - TAEKWONDO');
    expect(d.querySelector('.pn-dialogo__destacado-valor')!.textContent!.trim()).toBe(`30,00${NBSP}€`);
    const radios = Array.from(d.querySelectorAll('[role="radio"]'));
    expect(radios.map((r) => r.textContent!.trim())).toEqual(['Sin indicar', 'Efectivo', 'Transferencia', 'Tarjeta', 'Bizum']);
    expect(radios[0].getAttribute('aria-checked')).toBe('true');
    expect(botonDialogo('Marcar como pagado')).toBeTruthy();
    botonDialogo('Cancelar').click();
  });

  it('cancelar no envía nada', async () => {
    const alCambiar = jasmine.createSpy('alCambiar');
    const promesa = servicio.cobrar(CARGO, new Set(), alCambiar);
    pintar();
    dialogo()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await promesa;
    http.expectNone(`${API}/productos-alumno/801`);
    expect(alCambiar).not.toHaveBeenCalled();
  });

  it('con método: el PUT de hoy con {pagado, metodoPago}; botón «Cobrando…» en vuelo; aviso de éxito sin «Deshacer» para la gestión', async () => {
    const alCambiar = jasmine.createSpy('alCambiar');
    const cobrando = new Set<number>();
    const promesa = servicio.cobrar(CARGO, cobrando, alCambiar);
    pintar();
    metodo('Bizum');
    botonDialogo('Marcar como pagado').click();
    pintar();
    expect(botonDialogo('Cobrando…').getAttribute('aria-busy')).toBe('true');
    expect(cobrando.has(801)).toBeTrue();
    const put = http.expectOne(`${API}/productos-alumno/801`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ pagado: true, metodoPago: 'BIZUM' });
    expect(put.request.withCredentials).toBeTrue();
    put.flush({ id: 801, pagado: true });
    await promesa;
    await esperar();
    expect(dialogo()).toBeNull();
    expect(cobrando.has(801)).toBeFalse();
    expect(alCambiar).toHaveBeenCalledTimes(1);
    const [aviso] = avisos();
    expect(aviso.classList).toContain('pn-aviso--exito');
    expect(aviso.textContent).toContain(`Cobro registrado: 30,00${NBSP}€ · Adrián Prueba Ficticia`);
    expect(aviso.querySelector('.pn-aviso__accion')).toBeNull();
  });

  it('«Sin indicar» no envía metodoPago', async () => {
    const promesa = servicio.cobrar(CARGO, new Set(), () => undefined);
    pintar();
    botonDialogo('Marcar como pagado').click();
    const put = http.expectOne(`${API}/productos-alumno/801`);
    expect(put.request.body).toEqual({ pagado: true });
    put.flush({});
    await promesa;
  });

  it('el propietario ve «Deshacer»: la misma llamada que «Revertir» de hoy, y vuelve a leer', async () => {
    esAdmin = true;
    const alCambiar = jasmine.createSpy('alCambiar');
    const promesa = servicio.cobrar(CARGO, new Set(), alCambiar);
    pintar();
    botonDialogo('Marcar como pagado').click();
    http.expectOne(`${API}/productos-alumno/801`).flush({});
    await promesa;
    await esperar();
    const deshacer = Array.from(avisos()[0].querySelectorAll<HTMLButtonElement>('.pn-aviso__accion')).find(
      (b) => b.textContent!.trim() === 'Deshacer'
    )!;
    expect(deshacer).toBeDefined();
    deshacer.click();
    const revertir = http.expectOne(`${API}/productos-alumno/801`);
    expect(revertir.request.method).toBe('PUT');
    expect(revertir.request.body).toEqual({ pagado: false, motivoCambio: null });
    revertir.flush({});
    await esperar();
    expect(alCambiar).toHaveBeenCalledTimes(2);
    expect(avisos().map((a) => a.textContent)).toContain(jasmine.stringMatching('Cobro revertido a pendiente'));
  });

  it('error al cobrar: aviso de error (role=alert) con el texto de hoy; un 403 con el suyo', async () => {
    let promesa = servicio.cobrar(CARGO, new Set(), () => undefined);
    pintar();
    botonDialogo('Marcar como pagado').click();
    http.expectOne(`${API}/productos-alumno/801`).flush({}, { status: 500, statusText: 'Error' });
    await promesa;
    await esperar();
    expect(avisos()[0].classList).toContain('pn-aviso--error');
    expect(avisos()[0].textContent).toContain('No se pudo actualizar el estado del cobro');

    promesa = servicio.cobrar(CARGO, new Set(), () => undefined);
    pintar();
    botonDialogo('Marcar como pagado').click();
    http.expectOne(`${API}/productos-alumno/801`).flush({}, { status: 403, statusText: 'Forbidden' });
    await promesa;
    await esperar();
    expect(avisos().map((a) => a.textContent)).toContain(jasmine.stringMatching('Solo administradores pueden revertir cobros'));
  });

  it('no cobra dos veces el mismo cargo con un cobro en vuelo', async () => {
    const cobrando = new Set<number>([801]);
    await servicio.cobrar(CARGO, cobrando, () => undefined);
    expect(dialogo()).toBeNull();
    http.expectNone(`${API}/productos-alumno/801`);
  });

  describe('«Recibo» en el aviso de éxito (pieza 279)', () => {
    async function cobrarCargo(): Promise<void> {
      const promesa = servicio.cobrar(CARGO, new Set(), () => undefined);
      pintar();
      botonDialogo('Marcar como pagado').click();
      http.expectOne(`${API}/productos-alumno/801`).flush({});
      await promesa;
      await esperar();
    }

    function acciones(): string[] {
      return Array.from(avisos()[0].querySelectorAll('.pn-aviso__accion')).map((b) => b.textContent!.trim());
    }

    it('administración (no propietario): solo «Recibo», que descarga el recibo del cargo', async () => {
      esGestion = true;
      const descargar = spyOn(TestBed.inject(ReciboPagoService), 'descargar').and.resolveTo();
      await cobrarCargo();
      expect(acciones()).toEqual(['Recibo']);
      avisos()[0].querySelector<HTMLButtonElement>('.pn-aviso__accion')!.click();
      expect(descargar).toHaveBeenCalledOnceWith(801);
      expect(avisos().length).toBe(1);
    });

    it('propietario: «Recibo» antes de «Deshacer»', async () => {
      esAdmin = true;
      spyOn(TestBed.inject(ReciboPagoService), 'descargar').and.resolveTo();
      await cobrarCargo();
      expect(acciones()).toEqual(['Recibo', 'Deshacer']);
      avisos()[0].querySelector<HTMLButtonElement>('.pn-aviso__accion')!.click();
      expect(acciones()).toEqual(['Recibo', 'Deshacer']);
    });

    it('sin acceso de gestión: ni «Recibo» ni «Deshacer»', async () => {
      await cobrarCargo();
      expect(avisos()[0].querySelector('.pn-aviso__accion')).toBeNull();
    });

    it('tras «Deshacer» la acción «Recibo» del aviso ya no pide el recibo (bandera deshecho)', async () => {
      esAdmin = true;
      const descargar = spyOn(TestBed.inject(ReciboPagoService), 'descargar').and.resolveTo();
      await cobrarCargo();
      // El aviso y su acción «Recibo» se guardan antes de «Deshacer», que cierra el aviso y quita los botones del DOM:
      // así se invoca la misma acción después, como un clic que llegara tarde.
      const servicioAvisos = TestBed.inject(AvisosService);
      const aviso = servicioAvisos.lista()[0];
      const recibo = aviso.accionPrevia!;
      expect(recibo.texto).toBe('Recibo');
      const deshacer = Array.from(avisos()[0].querySelectorAll<HTMLButtonElement>('.pn-aviso__accion')).find(
        (b) => b.textContent!.trim() === 'Deshacer'
      )!;
      deshacer.click();
      http.expectOne(`${API}/productos-alumno/801`).flush({});
      await esperar();
      servicioAvisos.pulsarAccion(aviso, recibo);
      expect(descargar).not.toHaveBeenCalled();
    });
  });
});
