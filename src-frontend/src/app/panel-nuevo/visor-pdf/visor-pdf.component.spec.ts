import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import Swal from 'sweetalert2';

import { DocumentoInforme } from '../../servicios/generales/informe-pdf.service';
import { LoadingService } from '../../servicios/generales/loading.service';
import { ScrollLockService } from '../../servicios/generales/scroll-lock.service';
import { PilaCapas, enfocables } from '../capas/capa-modal';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { VisorPdfComponent } from './visor-pdf.component';

/** Un Blob cuyo contenido no llega nunca: el visor se queda cargando (sin pdf.js de por medio). */
function blobQuieto(): Blob {
  return { arrayBuffer: () => new Promise<ArrayBuffer>(() => undefined), size: 3, type: 'application/pdf' } as unknown as Blob;
}

const UNO: DocumentoInforme = { blob: blobQuieto(), nombreArchivo: 'informe.pdf', titulo: 'Informe de Estado de Licencias' };
const DOS: DocumentoInforme = { blob: blobQuieto(), nombreArchivo: 'Asistencia-martes-2026-09.pdf', titulo: 'Asistencia martes' };

@Component({
  standalone: true,
  imports: [VisorPdfComponent],
  template: `<div class="pn">
    <button type="button" id="disparador">Ver PDF</button>
    @if (abierto) {
    <pn-visor-pdf [documentos]="documentos" [aviso]="aviso" [focoRetorno]="foco" (cerrar)="cerrar()" />
    }
  </div>`,
})
class Anfitrion {
  abierto = false;
  documentos: DocumentoInforme[] = [UNO];
  aviso = '';
  foco: HTMLElement | null = null;
  cerradas = 0;
  cerrar(): void {
    this.cerradas++;
    this.abierto = false;
  }
}

/**
 * Visor de PDF del panel nuevo (8b-D2a), sobre las capas de 8b-D1: `<dialog>` modal con el foco contenido, Esc, X y velo
 * cierran y el foco vuelve a quien lo abrió; cabecera de 56 px con el título, «Descargar PDF» y la X; el mismo visor de
 * hoy (`app-pdf-viewer` con el Blob) y el mismo nombre de descarga; esqueleto mientras se lee el PDF.
 */
describe('VisorPdfComponent (8b-D2a, visor de PDF del panel nuevo)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let quitarTokens: () => void;
  let descargas: string[];
  let loading: jasmine.SpyObj<LoadingService>;

  const el = () => fixture.nativeElement as HTMLElement;
  const visor = () => el().querySelector<HTMLDialogElement>('dialog.pn-visor');
  const boton = (texto: string) =>
    Array.from(visor()!.querySelectorAll<HTMLButtonElement>('button')).find(
      (b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').trim() === texto
    )!;

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  function abrir(cambios: Partial<Anfitrion> = {}): void {
    fixture = TestBed.createComponent(Anfitrion);
    document.body.appendChild(el());
    fixture.detectChanges();
    const disparador = el().querySelector<HTMLButtonElement>('#disparador')!;
    disparador.focus();
    Object.assign(fixture.componentInstance, { foco: disparador, ...cambios, abierto: true });
    pintar();
  }

  /** Lo que mide `100vw` y `100dvh` en esta ventana (con o sin barra de desplazamiento). */
  function ventana(): { vw: number; dvh: number } {
    const sonda = document.createElement('div');
    sonda.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100dvh;visibility:hidden';
    document.body.appendChild(sonda);
    const r = sonda.getBoundingClientRect();
    sonda.remove();
    return { vw: Math.round(r.width), dvh: Math.round(r.height) };
  }

  function forzarAncho(hoja: boolean): void {
    spyOn(window, 'matchMedia').and.callFake(
      (q: string) =>
        ({ matches: hoja && q.includes('767.98'), media: q, addEventListener: () => undefined, removeEventListener: () => undefined }) as never
    );
  }

  function tecla(key: string, shiftKey = false): KeyboardEvent {
    const e = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
    (document.activeElement ?? visor()!).dispatchEvent(e);
    pintar();
    return e;
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    descargas = [];
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    TestBed.configureTestingModule({ imports: [Anfitrion], providers: [{ provide: LoadingService, useValue: loading }] });
    spyOn(URL, 'createObjectURL').and.returnValue('blob:visor');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      descargas.push(this.download);
    });
    spyOn(Swal, 'fire').and.callThrough();
  });

  afterEach(() => {
    fixture?.destroy();
    (fixture?.nativeElement as HTMLElement | undefined)?.remove();
    quitarTokens();
  });

  it('se abre como capa modal: cabecera con el título, «Descargar PDF» y la X; el visor de hoy con el Blob; foco en la X', () => {
    abrir();
    const dialogo = visor()!;
    expect(dialogo.open).toBeTrue();
    expect(dialogo.matches(':modal')).toBeTrue();
    expect(dialogo.closest('.pn')).not.toBeNull();
    const titulo = dialogo.querySelector('h2')!;
    expect(titulo.textContent!.trim()).toBe(UNO.titulo);
    expect(dialogo.getAttribute('aria-labelledby')).toBe(titulo.id);
    const cabecera = Array.from(dialogo.querySelectorAll('.pn-visor__cabecera button')).map(
      (b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').trim()
    );
    expect(cabecera).toEqual(['Descargar PDF', 'Cerrar']);
    expect(document.activeElement).toBe(boton('Cerrar'));
    expect(dialogo.querySelector('app-pdf-viewer')).not.toBeNull();
    expect(TestBed.inject(PilaCapas).arriba()).toBe(dialogo);
    expect(TestBed.inject(ScrollLockService).activeLocks).toBe(1);
  });

  it('escritorio: min(1040 px, 100vw − 48 px) de ancho, 100dvh − 48 px de alto y cabecera de 56 px', () => {
    forzarAncho(false);
    abrir();
    expect(visor()!.classList).not.toContain('pn-visor--hoja');
    const { vw, dvh } = ventana();
    const caja = visor()!.getBoundingClientRect();
    expect(Math.round(caja.width)).toBe(Math.min(1040, vw - 48));
    expect(Math.round(caja.height)).toBe(dvh - 48);
    expect(Math.round(visor()!.querySelector('.pn-visor__cabecera')!.getBoundingClientRect().height)).toBe(56);
  });

  it('por debajo de 768 px es una hoja a pantalla completa', () => {
    forzarAncho(true);
    abrir();
    expect(visor()!.classList).toContain('pn-visor--hoja');
    const { vw, dvh } = ventana();
    const caja = visor()!.getBoundingClientRect();
    expect(Math.round(caja.width)).toBe(vw);
    expect(Math.round(caja.height)).toBe(dvh);
    expect(getComputedStyle(visor()!).borderTopLeftRadius).toBe('0px');
  });

  it('«Descargar PDF» guarda el documento visible con el nombre de hoy y libera su URL, sin pedir nada', () => {
    abrir();
    boton('Descargar PDF').click();
    expect(URL.createObjectURL).toHaveBeenCalledOnceWith(UNO.blob);
    expect(URL.revokeObjectURL).toHaveBeenCalledOnceWith('blob:visor');
    expect(descargas).toEqual(['informe.pdf']);
    expect(Swal.fire).not.toHaveBeenCalled();
    expect(loading.show).not.toHaveBeenCalled();
  });

  it('varios documentos: «Documento» elige cuál se ve y cuál se descarga; el aviso parcial se lee', () => {
    abrir({ documentos: [UNO, DOS], aviso: 'No se pudo generar el PDF del jueves' });
    const select = visor()!.querySelector<HTMLSelectElement>('select')!;
    expect(visor()!.querySelector(`label[for="${select.id}"]`)!.textContent!.trim()).toBe('Documento');
    expect(Array.from(select.options).map((o) => o.textContent!.trim())).toEqual([UNO.titulo, DOS.titulo]);
    expect(visor()!.querySelector('[role="alert"]')!.textContent!.trim()).toBe('No se pudo generar el PDF del jueves');
    select.value = '1';
    select.dispatchEvent(new Event('change'));
    pintar();
    expect(visor()!.querySelector('h2')!.textContent!.trim()).toBe(DOS.titulo);
    boton('Descargar PDF').click();
    expect(descargas).toEqual([DOS.nombreArchivo]);
  });

  it('Esc cierra y el foco vuelve a quien lo abrió; scroll y pila liberados', () => {
    abrir();
    const dialogo = visor()!;
    const e = tecla('Escape');
    expect(e.defaultPrevented).toBeTrue();
    expect(fixture.componentInstance.cerradas).toBe(1);
    pintar();
    expect(visor()).toBeNull();
    expect(dialogo.open).toBeFalse();
    expect(document.activeElement).toBe(el().querySelector('#disparador'));
    expect(TestBed.inject(ScrollLockService).activeLocks).toBe(0);
    expect(TestBed.inject(PilaCapas).arriba()).toBeNull();
  });

  it('Esc con el visor expandido solo sale de la expansión', () => {
    abrir();
    // «Expandir» ya no está en el visor del panel; la expansión sigue existiendo en el componente de hoy.
    const comp = fixture.debugElement.query((d) => d.componentInstance instanceof VisorPdfComponent)
      .componentInstance as VisorPdfComponent;
    comp.visor!.toggleVisorExpandido();
    pintar();
    tecla('Escape');
    expect(fixture.componentInstance.cerradas).toBe(0);
    expect(visor()!.open).toBeTrue();
    tecla('Escape');
    expect(fixture.componentInstance.cerradas).toBe(1);
  });

  it('el evento `cancel`, la X y el clic en el velo cierran; un clic dentro no', () => {
    abrir();
    const cancel = new Event('cancel', { cancelable: true });
    visor()!.dispatchEvent(cancel);
    pintar();
    expect(cancel.defaultPrevented).toBeTrue();
    expect(fixture.componentInstance.cerradas).toBe(1);

    fixture.componentInstance.abierto = true;
    pintar();
    visor()!.querySelector('h2')!.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1, clientX: 30, clientY: 30 }));
    pintar();
    expect(fixture.componentInstance.cerradas).toBe(1);
    visor()!.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1, clientX: 2, clientY: 2 }));
    pintar();
    expect(fixture.componentInstance.cerradas).toBe(2);

    fixture.componentInstance.abierto = true;
    pintar();
    boton('Cerrar').click();
    pintar();
    expect(fixture.componentInstance.cerradas).toBe(3);
  });

  it('si el navegador cierra el `<dialog>` (un segundo Esc), avisa una sola vez', () => {
    abrir();
    visor()!.close();
    visor()?.dispatchEvent(new Event('close'));
    pintar();
    expect(fixture.componentInstance.cerradas).toBe(1);
  });

  it('el foco queda contenido: Tab desde el último vuelve al primero y Mayús+Tab desde el primero va al último', () => {
    abrir();
    const dialogo = visor()!;
    const lista = Array.from(dialogo.querySelectorAll<HTMLElement>('button:not([disabled]), select, [tabindex="0"]')).filter(
      (b) => b.getClientRects().length > 0
    );
    const primero = lista[0];
    const ultimo = lista[lista.length - 1];
    ultimo.focus();
    tecla('Tab');
    expect(document.activeElement).toBe(primero);
    tecla('Tab', true);
    expect(document.activeElement).toBe(ultimo);
  });

  it('esqueleto a los 300 ms mientras se lee el PDF (no antes), con «Cargando el PDF» para los lectores', fakeAsync(() => {
    abrir();
    tick(299);
    pintar();
    expect(visor()!.querySelector('.pn-visor__esqueleto [role="status"]')).toBeNull();
    tick(1);
    pintar();
    const aviso = visor()!.querySelector('.pn-visor__esqueleto [role="status"]');
    expect(aviso?.textContent!.trim()).toBe('Cargando el PDF');
    expect(visor()!.querySelector('.pn-visor__esqueleto .pn-esqueleto__forma')!.getAttribute('aria-hidden')).toBe('true');
    fixture.destroy();
  }));

  it('el esqueleto se va cuando el visor ya tiene el documento', fakeAsync(() => {
    abrir();
    tick(300);
    pintar();
    expect(visor()!.querySelector('.pn-visor__esqueleto')).not.toBeNull();
    const comp = fixture.debugElement.query((d) => d.componentInstance instanceof VisorPdfComponent)
      .componentInstance as VisorPdfComponent;
    comp.visor!.totalPaginasPdf = 2;
    pintar();
    expect(visor()!.querySelector('.pn-visor__esqueleto')).toBeNull();
    fixture.destroy();
  }));

  it('la zona con scroll del PDF es una región enfocable con nombre y `enfocables()` la cuenta la última, en orden DOM', () => {
    abrir();
    const dialogo = visor()!;
    const zona = dialogo.querySelector<HTMLElement>('.pdf-canvas-wrap')!;
    expect(zona.getAttribute('tabindex')).toBe('0');
    expect(zona.getAttribute('role')).toBe('region');
    expect(zona.getAttribute('aria-label')).toBe('Página del PDF');
    const lista = enfocables(dialogo);
    expect(lista).toContain(zona);
    expect(lista[lista.length - 1]).toBe(zona);
    expect(lista.filter((e) => e.tagName === 'BUTTON').every((b) => lista.indexOf(b) < lista.indexOf(zona))).toBeTrue();
  });

  describe('barra compacta (8b-D2a)', () => {
    const visorPdf = () =>
      fixture.debugElement.query((d) => d.componentInstance instanceof VisorPdfComponent).componentInstance as VisorPdfComponent;
    const textos = () =>
      Array.from(visor()!.querySelectorAll('.pdf-canvas-toolbar button')).map((b) =>
        (b.getAttribute('aria-label') ?? b.textContent ?? '').trim()
      );

    it('«Expandir» no existe dentro del visor', () => {
      abrir();
      visorPdf().visor!.totalPaginasPdf = 3;
      pintar();
      expect(textos()).not.toContain('Expandir');
      expect(visor()!.querySelector('.pdf-toolbar-btn-full')).toBeNull();
    });

    it('con una sola página no hay paginación; con varias, sí', () => {
      abrir();
      visorPdf().visor!.totalPaginasPdf = 1;
      pintar();
      expect(visor()!.querySelector('.pdf-canvas-page')).toBeNull();
      expect(textos()).toEqual(['Reducir zoom', 'Aumentar zoom']);
      visorPdf().visor!.totalPaginasPdf = 4;
      pintar();
      expect(visor()!.querySelector('.pdf-canvas-page')).not.toBeNull();
      expect(textos()).toEqual(['Anterior', 'Siguiente', 'Reducir zoom', 'Aumentar zoom']);
    });

    it('en el móvil «Descargar PDF» es un icono de 44 px con su etiqueta accesible', () => {
      forzarAncho(true);
      abrir();
      const b = visor()!.querySelector<HTMLButtonElement>('.pn-visor__descargar')!;
      expect(b.getAttribute('aria-label')).toBe('Descargar PDF');
      const caja = b.getBoundingClientRect();
      expect(Math.round(caja.width)).toBe(44);
      expect(Math.round(caja.height)).toBe(44);
    });
  });
});
