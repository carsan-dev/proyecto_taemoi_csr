import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import Swal from 'sweetalert2';

import { environment } from '../../../../environments/environment';
import {
  InformesAlumnos,
  OPCIONES_INFORME_ALUMNOS,
} from '../../../componentes/endpoints/listado-alumnos/informes-alumnos';
import { InformeModalComponent } from '../../../componentes/generales/informe-modal/informe-modal.component';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { AvisosService } from '../../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { GenerarInformePanelComponent } from './generar-informe-panel.component';
import { InformePdfPanelService } from './informe-pdf-panel.service';

const API = environment.apiUrl;
const ANO = new Date().getFullYear();
const ACTUAL = `${ANO}/${ANO + 1}`;
const ANTERIOR = `${ANO - 1}/${ANO}`;

interface Peticion {
  metodo: string;
  url: string;
}

@Component({
  standalone: true,
  imports: [GenerarInformePanelComponent],
  providers: [{ provide: InformePdfService, useClass: InformePdfPanelService }],
  template: `<div class="pn"><pn-generar-informe (cerrar)="cerradas = cerradas + 1" /></div>`,
})
class Anfitrion {
  cerradas = 0;
}

/**
 * «Generar informe» del listado nuevo en el panel lateral (8b-D2a, decisión 270): los mismos tipos, en los mismos grupos
 * y orden que el diálogo de hoy (`InformeModalComponent`), y las MISMAS peticiones y nombres de archivo que
 * `InformesAlumnos` con el diálogo de hoy. Cada fila lleva sus botones con el botón ocupado; una petición a la vez; el
 * panel no se cierra con una en vuelo; errores en avisos, nunca SweetAlert2, y sin el cargador global.
 */
describe('GenerarInformePanelComponent (8b-D2a, «Generar informe» en el panel lateral)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<Anfitrion>;
  let quitarTokens: () => void;
  let descargas: string[];
  let loading: jasmine.SpyObj<LoadingService>;

  const el = () => fixture.nativeElement as HTMLElement;
  const panel = () => el().querySelector<HTMLDialogElement>('dialog.pn-panel')!;
  const limpio = (t: string | null | undefined) => (t ?? '').replace(/\s+/g, ' ').trim();

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  function abrir(temporadas: string[] | 'error' = [ACTUAL, ANTERIOR]): void {
    fixture = TestBed.createComponent(Anfitrion);
    document.body.appendChild(el());
    fixture.detectChanges();
    const t = http.expectOne(`${API}/informes/reservas-plaza/temporadas`);
    if (temporadas === 'error') {
      t.flush('', { status: 500, statusText: 'Error' });
    } else {
      t.flush(temporadas);
    }
    pintar();
  }

  function quitar(): void {
    fixture.destroy();
    el().remove();
    fixture = undefined as unknown as ComponentFixture<Anfitrion>;
  }

  function informesPanel(): InformePdfService {
    return fixture.debugElement.children[0].children[0].injector.get(InformePdfService);
  }

  function fila(valor: string): HTMLElement {
    const f = panel().querySelector<HTMLElement>(`.informe__fila[data-informe="${valor}"]`);
    if (!f) {
      throw new Error(`Sin fila «${valor}»`);
    }
    return f;
  }

  function boton(valor: string, texto: string): HTMLButtonElement {
    const b = Array.from(fila(valor).querySelectorAll<HTMLButtonElement>('button')).find((x) => limpio(x.textContent) === texto);
    if (!b) {
      throw new Error(`Sin «${texto}» en «${valor}»`);
    }
    return b;
  }

  function anotar(respuesta?: (r: TestRequest) => void): Peticion[] {
    const lista: Peticion[] = [];
    for (const r of http.match(() => true)) {
      lista.push({ metodo: r.request.method, url: r.request.urlWithParams.replace(API, '') });
      if (respuesta) {
        respuesta(r);
      } else {
        r.flush(new Blob(['pdf']));
      }
    }
    if (fixture) {
      pintar();
    }
    return lista;
  }

  /** Lo que hace hoy el diálogo «Generar informe» con ese tipo (InformesAlumnos de siempre). */
  async function deHoy(tipo: string, accion: 'ver' | 'descargar', soloActivos = true, temporada?: string) {
    const informes = TestBed.inject(InformePdfService);
    const hoy = new InformesAlumnos(TestBed.inject(EndpointsService), informes);
    hoy.temporadas = [ACTUAL, ANTERIOR];
    hoy.generarInformeSeleccionado({ tipo, soloActivos, accion, temporada });
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    const peticiones = anotar();
    const nombres = accion === 'ver' ? informes.documentos.map((d) => d.nombreArchivo) : [...descargas];
    const titulos = informes.documentos.map((d) => d.titulo);
    informes.cerrar();
    descargas.length = 0;
    return { peticiones, nombres, titulos };
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    descargas = [];
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    TestBed.configureTestingModule({
      imports: [Anfitrion],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        InformePdfService,
        { provide: LoadingService, useValue: loading },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      descargas.push(this.download);
    });
  });

  afterEach(() => {
    fixture?.destroy();
    (fixture?.nativeElement as HTMLElement | undefined)?.remove();
    fixture = undefined as unknown as ComponentFixture<Anfitrion>;
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    quitarTokens();
  });

  it('los mismos tipos, grupos, orden y descripciones que el diálogo de hoy; «Solo alumnos activos» marcado', () => {
    const hoy = TestBed.runInInjectionContext(() => new InformeModalComponent());
    hoy.opcionesInforme = [...OPCIONES_INFORME_ALUMNOS];
    hoy.organizarInformesPorCategoria();
    const esperado = hoy.categoriasInforme.map((c) => ({
      titulo: c.title,
      filas: c.opciones.map((o) => [o.label, o.description ?? '']),
    }));

    abrir();
    expect(limpio(panel().querySelector('h2')!.textContent)).toBe('Generar informe');
    const grupos = Array.from(panel().querySelectorAll<HTMLElement>('.informe__grupo')).map((g) => ({
      titulo: limpio(g.querySelector('.informe__grupo-nombre')!.textContent),
      filas: Array.from(g.querySelectorAll<HTMLElement>('.informe__fila')).map((f) => [
        limpio(f.querySelector('.informe__nombre')!.textContent),
        limpio(f.querySelector('.informe__descripcion')?.textContent),
      ]),
    }));
    expect(grupos).toEqual(esperado);
    expect(grupos.reduce((n, g) => n + g.filas.length, 0)).toBe(18);
    const activos = panel().querySelector<HTMLInputElement>('#pn-informe-solo-activos')!;
    expect(activos.type).toBe('checkbox');
    expect(activos.checked).toBeTrue();
    expect(limpio(activos.closest('label')!.textContent)).toBe('Solo alumnos activos');
    // Cada fila, «Ver PDF» y «Descargar PDF»; las deudas, además «Descargar CSV» (hoy, un SweetAlert2 «PDF / CSV»).
    expect(Array.from(fila('general').querySelectorAll('button')).map((b) => limpio(b.textContent))).toEqual([
      'Ver PDF',
      'Descargar PDF',
    ]);
    expect(Array.from(fila('deudas').querySelectorAll('button')).map((b) => limpio(b.textContent))).toEqual([
      'Ver PDF',
      'Descargar PDF',
      'Descargar CSV',
    ]);
    expect(boton('general', 'Ver PDF').getAttribute('aria-describedby')).toBe(fila('general').querySelector('.informe__nombre')!.id);
  });

  it('cada tipo, «Ver PDF» y «Descargar PDF»: las mismas peticiones, títulos y nombres de archivo que hoy', async () => {
    const tipos = OPCIONES_INFORME_ALUMNOS.map((o) => o.value).filter((v) => v !== 'reservas-plaza' && v !== 'deudas');
    for (const tipo of tipos) {
      for (const accion of ['ver', 'descargar'] as const) {
        const hoy = await deHoy(tipo, accion);
        loading.show.calls.reset();
        abrir();
        boton(tipo, accion === 'ver' ? 'Ver PDF' : 'Descargar PDF').click();
        pintar();
        expect(anotar()).withContext(`${tipo} ${accion}`).toEqual(hoy.peticiones);
        const informes = informesPanel();
        if (accion === 'ver') {
          expect(informes.documentos.map((d) => d.nombreArchivo)).withContext(tipo).toEqual(hoy.nombres);
          expect(informes.documentos.map((d) => d.titulo)).withContext(tipo).toEqual(hoy.titulos);
          informes.cerrar();
        } else {
          expect(descargas).withContext(tipo).toEqual(hoy.nombres);
          descargas.length = 0;
        }
        expect(loading.show).withContext(`${tipo} ${accion}: sin cargador global`).not.toHaveBeenCalled();
        quitar();
      }
    }
  });

  it('sin «Solo alumnos activos», la misma petición que hoy con soloActivos=false', async () => {
    const hoy = await deHoy('licencias', 'descargar', false);
    abrir();
    const activos = panel().querySelector<HTMLInputElement>('#pn-informe-solo-activos')!;
    activos.click();
    pintar();
    boton('licencias', 'Descargar PDF').click();
    pintar();
    expect(anotar()).toEqual(hoy.peticiones);
    expect(hoy.peticiones[0].url).toContain('soloActivos=false');
  });

  it('deudas: «Descargar PDF» y «Descargar CSV» piden lo mismo que el «PDF» y el «CSV» del SweetAlert2 de hoy, sin abrirlo', async () => {
    const fire = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true, isDenied: false, isDismissed: false } as never);
    const pdf = await deHoy('deudas', 'descargar');
    fire.and.resolveTo({ isConfirmed: false, isDenied: true, isDismissed: false } as never);
    const csv = await deHoy('deudas', 'descargar');
    const ver = await deHoy('deudas', 'ver');
    expect(csv.nombres).toEqual(['informe_deudas_alumnos.csv']);
    fire.calls.reset();

    abrir();
    boton('deudas', 'Descargar PDF').click();
    pintar();
    expect(anotar()).toEqual(pdf.peticiones);
    expect(descargas).toEqual(pdf.nombres);
    descargas.length = 0;
    boton('deudas', 'Descargar CSV').click();
    pintar();
    expect(anotar()).toEqual(csv.peticiones);
    expect(descargas).toEqual(csv.nombres);
    boton('deudas', 'Ver PDF').click();
    pintar();
    expect(anotar()).toEqual(ver.peticiones);
    expect(informesPanel().documentos.map((d) => d.titulo)).toEqual(ver.titulos);
    expect(fire).not.toHaveBeenCalled();
  });

  it('reservas de plaza: la temporada se elige en la fila (la actual por defecto) y pide lo mismo que el SweetAlert2 de hoy', async () => {
    const fire = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true, value: ANTERIOR } as never);
    const hoy = await deHoy('reservas-plaza', 'ver', true, ANTERIOR);
    expect(hoy.nombres).toEqual([`informe_reservas_plaza_${ANTERIOR.replace('/', '_')}.pdf`]);
    fire.calls.reset();

    abrir();
    const select = fila('reservas-plaza').querySelector<HTMLSelectElement>('select')!;
    expect(limpio(fila('reservas-plaza').querySelector(`label[for="${select.id}"]`)!.textContent)).toBe('Temporada');
    expect(Array.from(select.options).map((o) => o.textContent!.trim())).toEqual([`Actual (${ACTUAL})`, ANTERIOR]);
    expect(select.value).toBe(ACTUAL);
    select.value = ANTERIOR;
    select.dispatchEvent(new Event('change'));
    pintar();
    boton('reservas-plaza', 'Ver PDF').click();
    pintar();
    expect(anotar()).toEqual(hoy.peticiones);
    expect(informesPanel().documentos.map((d) => d.nombreArchivo)).toEqual(hoy.nombres);
    expect(informesPanel().documentos.map((d) => d.titulo)).toEqual(hoy.titulos);
    expect(fire).not.toHaveBeenCalled();
  });

  it('reservas de plaza sin temporadas: sus botones deshabilitados y la frase de hoy; un fallo al leerlas avisa sin SweetAlert2', () => {
    abrir([]);
    expect(boton('reservas-plaza', 'Ver PDF').disabled).toBeTrue();
    expect(boton('reservas-plaza', 'Descargar PDF').disabled).toBeTrue();
    expect(limpio(fila('reservas-plaza').textContent)).toContain('No hay temporadas con reservas de plaza disponibles.');
    quitar();

    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    abrir('error');
    const avisos = Array.from(document.querySelectorAll('.pn-aviso__texto')).map((a) => limpio(a.textContent));
    expect(avisos).toEqual(['No se pudieron cargar las temporadas de reservas']);
    expect(document.querySelector('.swal2-container')).toBeNull();
  });

  it('una petición a la vez: botón ocupado, el resto deshabilitado y el panel no se cierra hasta que termina', () => {
    abrir();
    const descargar = boton('general', 'Descargar PDF');
    descargar.click();
    pintar();
    expect(descargar.getAttribute('aria-busy')).toBe('true');
    expect(descargar.getAttribute('aria-disabled')).toBe('true');
    expect(limpio(descargar.textContent)).toBe('Generando…');
    expect(boton('general', 'Ver PDF').disabled).toBeTrue();
    expect(boton('licencias', 'Descargar PDF').disabled).toBeTrue();
    descargar.click();
    boton('licencias', 'Descargar PDF').click();
    pintar();
    // Esc, el evento `cancel`, la X y «Cerrar»: nada.
    panel().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    panel().dispatchEvent(new Event('cancel', { cancelable: true }));
    pintar();
    const cerrar = Array.from(panel().querySelectorAll<HTMLButtonElement>('.pn-panel__pie button')).find(
      (b) => limpio(b.textContent) === 'Cerrar'
    )!;
    expect(cerrar.disabled).toBeTrue();
    expect(panel().querySelector<HTMLButtonElement>('.pn-panel__cerrar')!.disabled).toBeTrue();
    expect(fixture.componentInstance.cerradas).toBe(0);
    const peticiones = anotar();
    expect(peticiones.length).toBe(1);
    expect(descargas).toEqual(['informe.pdf']);
    expect(descargar.getAttribute('aria-busy')).toBeNull();
    expect(cerrar.disabled).toBeFalse();
    cerrar.click();
    pintar();
    expect(fixture.componentInstance.cerradas).toBe(1);
    expect(loading.show).not.toHaveBeenCalled();
    expect(loading.hide).not.toHaveBeenCalled();
  });

  it('un PDF que falla avisa dentro del panel con el texto de hoy, sin SweetAlert2 ni cargador global', () => {
    const fire = spyOn(Swal, 'fire').and.callThrough();
    abrir();
    boton('general', 'Ver PDF').click();
    pintar();
    anotar((r) => r.flush(new Blob(['']), { status: 500, statusText: 'Error' }));
    const aviso = panel().querySelector('.pn-aviso--error .pn-aviso__texto');
    expect(limpio(aviso?.textContent)).toBe('No se pudo generar el informe general');
    expect(fire).not.toHaveBeenCalled();
    expect(loading.show).not.toHaveBeenCalled();
    expect(informesPanel().documentos.length).toBe(0);
  });

  it('cerrar sin generar no envía nada (solo la lectura de temporadas al abrir, como hoy)', () => {
    abrir();
    panel().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    pintar();
    expect(fixture.componentInstance.cerradas).toBe(1);
    expect(http.match(() => true).length).toBe(0);
  });
});
