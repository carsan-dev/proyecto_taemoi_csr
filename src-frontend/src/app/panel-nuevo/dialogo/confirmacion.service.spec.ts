import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of } from 'rxjs';
import Swal from 'sweetalert2';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { CLASE_ERROR_HTTP, CONSULTA_HOJA } from '../capas/capa-modal';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { disenoNuevoGuard } from '../diseno-nuevo.guard';
import { EstilosPanelService } from '../estilos-panel.service';
import { AcentoEscuelaActual } from '../acento-escuela.directive';
import { SIERRA_PRUEBA } from '../tema-prueba';
import { ConfirmacionService } from './confirmacion.service';
import { OpcionesConfirmacion, ResultadoConfirmacion } from './dialogo.component';

const METODOS = [
  { valor: '', texto: 'Sin indicar' },
  { valor: 'EFECTIVO', texto: 'Efectivo' },
  { valor: 'TRANSFERENCIA', texto: 'Transferencia' },
  { valor: 'TARJETA', texto: 'Tarjeta' },
  { valor: 'BIZUM', texto: 'Bizum' },
];

const COBRAR: OpcionesConfirmacion = {
  titulo: 'Cobrar mensualidad',
  mensaje: ['Se registra el pago de ', { fuerte: 'Adrián Prueba Ficticia' }, ' y la mensualidad deja de figurar como pendiente.'],
  destacado: { etiqueta: 'MENSUALIDAD OCTUBRE 2026 - TAEKWONDO', valor: '30,00 €' },
  campo: { etiqueta: 'Método (opcional)', opciones: METODOS, valor: '' },
  accion: 'Marcar como pagado',
  accionOcupada: 'Cobrando…',
};

const BAJA: OpcionesConfirmacion = {
  titulo: '¿Dar de baja a Ada Simulada Maqueta?',
  mensaje: 'Se desactivan sus disciplinas; su ficha y sus pagos se conservan.',
  accion: 'Dar de baja',
  destructivo: true,
};

@Component({ standalone: true, template: '<p>Pantalla nueva</p>' })
class PantallaNueva {}

/** `matchMedia` simulado: la consulta de la hoja da `hoja`; las demás, false. */
function anchoDeMovil(hoja: boolean): void {
  spyOn(window, 'matchMedia').and.callFake(
    (consulta: string) =>
      ({
        matches: consulta === CONSULTA_HOJA ? hoja : false,
        media: consulta,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList
  );
}

describe('ConfirmacionService y DialogoComponent (8b-D1, diálogo y hoja)', () => {
  let servicio: ConfirmacionService;
  let disparador: HTMLButtonElement;

  function dialogo(): HTMLDialogElement | null {
    return document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  }

  function boton(texto: string): HTMLButtonElement {
    const lista = Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('button'));
    const hallado = lista.find((b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').trim() === texto);
    if (!hallado) {
      throw new Error(`Sin botón «${texto}»: ${lista.map((b) => b.textContent?.trim()).join(' | ')}`);
    }
    return hallado;
  }

  function tick(): void {
    pintarCapas();
  }

  function abrir(opciones: OpcionesConfirmacion): Promise<ResultadoConfirmacion> {
    disparador.focus();
    const promesa = servicio.confirmar(opciones);
    tick();
    return promesa;
  }

  let quitarTokens: () => void;

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
    servicio = TestBed.inject(ConfirmacionService);
    disparador = document.createElement('button');
    disparador.textContent = 'Cobrar';
    document.body.appendChild(disparador);
  });

  afterEach(() => {
    quitarTokens();
    disparador.remove();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
  });

  it('abre un <dialog> modal en el body, dentro de una raíz .pn, con título, frase, concepto, importe y pastillas', () => {
    void abrir(COBRAR);
    const d = dialogo()!;
    expect(d).not.toBeNull();
    expect(d.open).toBeTrue();
    expect(d.matches(':modal')).toBeTrue();
    expect(d.closest('.pn')).not.toBeNull();
    const titulo = d.querySelector('h2')!;
    expect(titulo.textContent!.trim()).toBe('Cobrar mensualidad');
    expect(d.getAttribute('aria-labelledby')).toBe(titulo.id);
    const mensaje = document.getElementById(d.getAttribute('aria-describedby')!)!;
    expect(mensaje.textContent!.replace(/\s+/g, ' ').trim()).toBe(
      'Se registra el pago de Adrián Prueba Ficticia y la mensualidad deja de figurar como pendiente.'
    );
    expect(mensaje.querySelector('strong')!.textContent).toBe('Adrián Prueba Ficticia');
    expect(d.textContent).toContain('MENSUALIDAD OCTUBRE 2026 - TAEKWONDO');
    expect(d.querySelector('.pn-dialogo__destacado-valor')!.textContent!.trim()).toBe('30,00 €');
    const grupo = d.querySelector('[role="radiogroup"]')!;
    expect(document.getElementById(grupo.getAttribute('aria-labelledby')!)!.textContent!.trim()).toBe('Método (opcional)');
    const radios = Array.from(grupo.querySelectorAll('[role="radio"]'));
    expect(radios.map((r) => r.textContent!.trim())).toEqual(['Sin indicar', 'Efectivo', 'Transferencia', 'Tarjeta', 'Bizum']);
    expect(radios.map((r) => r.getAttribute('aria-checked'))).toEqual(['true', 'false', 'false', 'false', 'false']);
    expect(radios.map((r) => r.getAttribute('tabindex'))).toEqual(['0', '-1', '-1', '-1', '-1']);
  });

  it('al abrirse en una pantalla nueva cierra el SweetAlert2 del interceptor de errores (el 409 de las herramientas)', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosListar');
    void Swal.fire({ title: 'Error', showClass: { popup: '' }, hideClass: { popup: '' }, customClass: { popup: CLASE_ERROR_HTTP } });
    expect(Swal.isVisible()).toBeTrue();
    void abrir(COBRAR);
    expect(Swal.isVisible()).toBeFalse();
  });

  it('al abrirse no cierra otro SweetAlert2 ni, fuera del panel nuevo, el del interceptor', async () => {
    void Swal.fire({ title: 'Error', showClass: { popup: '' }, hideClass: { popup: '' }, customClass: { popup: CLASE_ERROR_HTTP } });
    void abrir(COBRAR);
    expect(Swal.isVisible()).withContext('ruta de hoy').toBeTrue();
    boton('Cancelar').click();
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/alumnosListar');
    void Swal.fire({ title: 'Confirmar cobro', showClass: { popup: '' }, hideClass: { popup: '' } });
    void abrir(COBRAR);
    expect(Swal.isVisible()).withContext('otro SweetAlert2').toBeTrue();
    Swal.close();
  });

  it('«Cancelar» a la izquierda y la acción a la derecha; foco inicial en la acción', () => {
    anchoDeMovil(false);
    void abrir(COBRAR);
    const pie = Array.from(dialogo()!.querySelectorAll('.pn-dialogo__pie button')).map((b) => b.textContent!.trim());
    expect(pie).toEqual(['Cancelar', 'Marcar como pagado']);
    expect(document.activeElement).toBe(boton('Marcar como pagado'));
    expect(dialogo()!.classList).not.toContain('pn-dialogo--hoja');
  });

  it('destructivo: la acción en peligro y el foco inicial en «Cancelar»', () => {
    void abrir(BAJA);
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(document.activeElement).toBe(boton('Cancelar'));
    const accion = boton('Dar de baja');
    expect(getComputedStyle(accion).backgroundColor).toBe('rgb(180, 35, 24)');
  });

  it('8b-C: con la marca, el diálogo lleva el tema completo: principal con la tinta de la escuela y «Cancelar» con el acento', () => {
    TestBed.inject(AcentoEscuelaActual).poner(SIERRA_PRUEBA);
    void abrir(COBRAR);
    const capa = dialogo()!.closest<HTMLElement>('pn-dialogo')!;
    expect(capa.style.getPropertyValue('--pn-marca-primario-tinta')).toBe('#24853A');
    const accion = getComputedStyle(boton('Marcar como pagado'));
    expect(accion.backgroundColor).withContext('principal: la tinta de la escuela').toBe('rgb(36, 133, 58)');
    expect(accion.color).withContext('principal: sobre-primario de la escuela').toBe('rgb(26, 34, 48)');
    const cancelar = getComputedStyle(boton('Cancelar'));
    expect(cancelar.borderTopColor).withContext('«Cancelar»: borde').toBe('rgb(103, 149, 107)');
    expect(cancelar.color).withContext('«Cancelar»: texto').toBe('rgb(14, 120, 45)');
    boton('Cancelar').click();
  });

  it('8b-C: el diálogo de peligro sigue en neutros con la marca: «Cancelar», su hover y su foco como sin marca', () => {
    TestBed.inject(AcentoEscuelaActual).poner(SIERRA_PRUEBA);
    void abrir(BAJA);
    const d = dialogo()!;
    const cancelar = boton('Cancelar');
    const estilo = getComputedStyle(cancelar);
    expect(estilo.color).withContext('«Cancelar»: texto en la tinta neutra').toBe('rgb(26, 34, 48)');
    expect(estilo.borderTopColor).withContext('«Cancelar»: borde línea fuerte').toBe('rgb(207, 207, 200)');
    expect(getComputedStyle(boton('Dar de baja')).backgroundColor).withContext('acción en peligro').toBe('rgb(180, 35, 24)');
    // El hover y el foco leen estas variables: las neutras de #1A2230, no las de la escuela.
    const muestra = document.createElement('span');
    d.querySelector('.pn-dialogo__pie')!.appendChild(muestra);
    muestra.style.color = 'var(--pn-acento-suave)';
    muestra.style.backgroundColor = 'var(--pn-marca-primario-suave)';
    muestra.style.borderTopColor = 'var(--pn-marca-primario-tinta)';
    const neutro = getComputedStyle(muestra);
    expect(neutro.color).withContext('hover de «Cancelar»').toBe('rgb(232, 233, 234)');
    expect(neutro.backgroundColor).withContext('suave neutro').toBe('rgb(232, 233, 234)');
    expect(neutro.borderTopColor).withContext('tinta neutra (foco)').toBe('rgb(26, 34, 48)');
    muestra.remove();
    cancelar.click();
  });

  it('Esc cancela sin hacer nada, cierra el diálogo y devuelve el foco al disparador', async () => {
    const promesa = abrir(COBRAR);
    dialogo()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    const resultado = await promesa;
    expect(resultado).toEqual({ confirmado: false, valor: null });
    expect(dialogo()).toBeNull();
    expect(document.activeElement).toBe(disparador);
  });

  it('el evento «cancel» del navegador también cancela', async () => {
    const promesa = abrir(COBRAR);
    dialogo()!.dispatchEvent(new Event('cancel', { cancelable: true }));
    expect((await promesa).confirmado).toBeFalse();
  });

  it('clic fuera (en el velo) cancela; un clic dentro no', async () => {
    const promesa = abrir(COBRAR);
    let terminado = false;
    void promesa.then(() => (terminado = true));
    dialogo()!.querySelector<HTMLElement>('.pn-dialogo__cuerpo')!.dispatchEvent(
      new MouseEvent('click', { bubbles: true, clientX: 600, clientY: 300, detail: 1 })
    );
    await Promise.resolve();
    expect(terminado).toBeFalse();
    dialogo()!.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 5, clientY: 5, detail: 1 }));
    expect((await promesa).confirmado).toBeFalse();
    expect(document.activeElement).toBe(disparador);
  });

  it('la X «Cerrar» y «Cancelar» cancelan', async () => {
    let promesa = abrir(COBRAR);
    boton('Cerrar').click();
    expect((await promesa).confirmado).toBeFalse();
    promesa = abrir(COBRAR);
    boton('Cancelar').click();
    expect((await promesa).confirmado).toBeFalse();
  });

  it('confirmar devuelve la pastilla elegida («Sin indicar» por defecto)', async () => {
    let promesa = abrir(COBRAR);
    boton('Marcar como pagado').click();
    expect(await promesa).toEqual({ confirmado: true, valor: '' });

    promesa = abrir(COBRAR);
    const bizum = Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('[role="radio"]'))[4];
    bizum.click();
    tick();
    expect(bizum.getAttribute('aria-checked')).toBe('true');
    expect(bizum.getAttribute('tabindex')).toBe('0');
    boton('Marcar como pagado').click();
    expect(await promesa).toEqual({ confirmado: true, valor: 'BIZUM' });
  });

  it('pastillas: flechas, Inicio y Fin mueven la elección y el foco, con vuelta', () => {
    void abrir(COBRAR);
    const grupo = dialogo()!.querySelector<HTMLElement>('[role="radiogroup"]')!;
    const radios = () => Array.from(grupo.querySelectorAll<HTMLElement>('[role="radio"]'));
    radios()[0].focus();
    grupo.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    tick();
    expect(radios()[1].getAttribute('aria-checked')).toBe('true');
    expect(document.activeElement).toBe(radios()[1]);
    grupo.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    tick();
    expect(document.activeElement).toBe(radios()[4]);
    grupo.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    tick();
    expect(document.activeElement).toBe(radios()[0]);
    grupo.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    tick();
    expect(radios()[4].getAttribute('aria-checked')).toBe('true');
  });

  it('foco contenido: Tab desde el último va al primero y Mayús+Tab desde el primero, al último', () => {
    anchoDeMovil(false);
    void abrir(BAJA);
    const d = dialogo()!;
    const cerrar = boton('Cerrar');
    const accion = boton('Dar de baja');
    accion.focus();
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    accion.dispatchEvent(tab);
    expect(tab.defaultPrevented).toBeTrue();
    expect(document.activeElement).toBe(cerrar);
    const atras = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true });
    cerrar.dispatchEvent(atras);
    expect(document.activeElement).toBe(accion);
    expect(d.contains(document.activeElement)).toBeTrue();
  });

  it('por debajo de 768 px es una hoja inferior: asa, la acción arriba y «Cancelar» debajo, a ancho completo', () => {
    anchoDeMovil(true);
    void abrir(COBRAR);
    const d = dialogo()!;
    expect(d.classList).toContain('pn-dialogo--hoja');
    expect(d.querySelector('.pn-dialogo__asa')).not.toBeNull();
    const pie = Array.from(d.querySelectorAll<HTMLButtonElement>('.pn-dialogo__pie button'));
    expect(pie.map((b) => b.textContent!.trim())).toEqual(['Marcar como pagado', 'Cancelar']);
    expect(getComputedStyle(pie[0]).minHeight).toBe('44px');
    expect(getComputedStyle(d).borderTopLeftRadius).toBe('14px');
    expect(getComputedStyle(d).borderBottomLeftRadius).toBe('0px');
  });

  it('con `ejecutar`: botón ocupado («Cobrando…», aria-disabled, aria-busy) y Esc no cierra hasta que termina', async () => {
    const respuesta = new Subject<string>();
    const ejecutar = jasmine.createSpy('ejecutar').and.returnValue(respuesta);
    const promesa = abrir({ ...COBRAR, ejecutar });
    const accion = boton('Marcar como pagado');
    const ancho = accion.getBoundingClientRect().width;
    accion.focus();
    accion.click();
    tick();
    // aria-disabled y no `disabled`: el foco no cae al body (si cayera, el Esc ya no pasaría por el diálogo).
    expect(accion.disabled).toBeFalse();
    expect(accion.getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(accion);
    accion.click();
    expect(ejecutar).toHaveBeenCalledTimes(1);
    expect(accion.getAttribute('aria-busy')).toBe('true');
    expect(accion.textContent!.trim()).toBe('Cobrando…');
    expect(accion.querySelector('svg')!.getBoundingClientRect().width).toBe(16);
    expect(accion.getBoundingClientRect().width).toBeCloseTo(ancho, 0);
    expect(boton('Cancelar').disabled).toBeTrue();
    for (let i = 0; i < 2; i++) {
      const esc = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
      accion.dispatchEvent(esc);
      expect(esc.defaultPrevented).toBeTrue();
      const cancel = new Event('cancel', { cancelable: true });
      dialogo()!.dispatchEvent(cancel);
      expect(cancel.defaultPrevented).toBeTrue();
    }
    expect(dialogo()!.open).toBeTrue();
    respuesta.next('hecho');
    respuesta.complete();
    expect(await promesa).toEqual({ confirmado: true, valor: '', respuesta: 'hecho' });
    expect(dialogo()).toBeNull();
  });

  it('con `ejecutar` en vuelo: si el navegador cierra el <dialog> igualmente (segundo Esc), se vuelve a abrir', async () => {
    const respuesta = new Subject<string>();
    let resuelto = false;
    const promesa = abrir({ ...COBRAR, ejecutar: () => respuesta }).then((r) => {
      resuelto = true;
      return r;
    });
    boton('Marcar como pagado').click();
    tick();
    const d = dialogo()!;
    const cerrado = new Promise((r) => d.addEventListener('close', () => setTimeout(r), { once: true }));
    d.close();
    await cerrado;
    tick();
    expect(d.open).toBeTrue();
    expect(d.matches(':modal')).toBeTrue();
    expect(resuelto).toBeFalse();
    respuesta.next('hecho');
    respuesta.complete();
    expect(await promesa).toEqual({ confirmado: true, valor: '', respuesta: 'hecho' });
    expect(dialogo()).toBeNull();
  });

  it('sin nada en vuelo, un cierre del navegador (evento «close») cancela como Esc', async () => {
    const promesa = abrir(COBRAR);
    dialogo()!.close();
    expect(await promesa).toEqual({ confirmado: false, valor: null });
    expect(dialogo()).toBeNull();
  });

  it('con `ejecutar` que falla: cierra y devuelve el error a quien lo abrió', async () => {
    const respuesta = new Subject<string>();
    const promesa = abrir({ ...COBRAR, ejecutar: () => respuesta });
    boton('Marcar como pagado').click();
    respuesta.error({ status: 500 });
    const resultado = await promesa;
    expect(resultado.confirmado).toBeTrue();
    expect(resultado.error).toEqual({ status: 500 });
  });

  it('campo requerido: sin elección la acción está deshabilitada y el foco va a «Cancelar»', async () => {
    const promesa = abrir({
      titulo: 'Selecciona el deporte a activar',
      campo: { etiqueta: 'Deporte', opciones: [{ valor: 'TODOS', texto: 'Todos los deportes' }], requerido: true },
      accion: 'Dar de alta',
    });
    expect(boton('Dar de alta').disabled).toBeTrue();
    expect(document.activeElement).toBe(boton('Cancelar'));
    dialogo()!.querySelector<HTMLButtonElement>('[role="radio"]')!.click();
    tick();
    expect(boton('Dar de alta').disabled).toBeFalse();
    boton('Dar de alta').click();
    expect(await promesa).toEqual({ confirmado: true, valor: 'TODOS' });
  });
});
