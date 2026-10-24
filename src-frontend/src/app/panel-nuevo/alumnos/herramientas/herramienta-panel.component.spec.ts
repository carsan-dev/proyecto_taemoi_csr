import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../../environments/environment';
import { HerramientasAlumnosComponent } from '../../../componentes/endpoints/listado-alumnos/herramientas-alumnos/herramientas-alumnos.component';
import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { InformePdfService } from '../../../servicios/generales/informe-pdf.service';
import { LoadingService } from '../../../servicios/generales/loading.service';
import { AvisosService } from '../../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { HerramientaAlumnos, HerramientaPanelComponent } from './herramienta-panel.component';
import { InformePdfPanelService } from './informe-pdf-panel.service';

const API = environment.apiUrl;

interface Peticion {
  metodo: string;
  url: string;
  cuerpo: unknown;
}

@Component({
  standalone: true,
  imports: [HerramientaPanelComponent],
  providers: [{ provide: InformePdfService, useClass: InformePdfPanelService }],
  template: `<div class="pn">
    <pn-herramienta-alumnos [herramienta]="herramienta" (cerrar)="cerrada = true" (asignado)="asignados = asignados + 1" />
  </div>`,
})
class Anfitrion {
  @Input() herramienta: HerramientaAlumnos = 'asistencia';
  cerrada = false;
  asignados = 0;
}

/**
 * Las tres herramientas del pie en el panel lateral del listado nuevo (8b-D1, decisión 270): mismos campos y acciones,
 * y las MISMAS peticiones (método, ruta, parámetros y cuerpo) y nombres de PDF que el componente de hoy, que se usa tal
 * cual en la pantalla de hoy. Cada caso hace lo mismo en el componente de hoy (con sus métodos) y en el panel (con su
 * plantilla) y compara.
 */
describe('HerramientaPanelComponent (8b-D1, herramientas de alumnos en el panel lateral)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<Anfitrion>;
  let quitarTokens: () => void;
  let descargas: string[];

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  /** Responde y anota todo lo pedido (blobs para los PDF, vacío para el resto). */
  function anotar(respuesta?: (r: TestRequest) => void): Peticion[] {
    const lista: Peticion[] = [];
    for (const r of http.match(() => true)) {
      lista.push({ metodo: r.request.method, url: r.request.urlWithParams.replace(API, ''), cuerpo: r.request.body });
      if (respuesta) {
        respuesta(r);
      } else if (r.request.responseType === 'blob') {
        r.flush(new Blob(['pdf']));
      } else {
        r.flush(r.request.method === 'GET' ? [] : {});
      }
    }
    if (fixture) {
      pintar();
    }
    return lista;
  }

  function hoy(): { comp: HerramientasAlumnosComponent; informes: InformePdfService } {
    const informes = TestBed.inject(InformePdfService);
    const comp = TestBed.runInInjectionContext(() => new HerramientasAlumnosComponent());
    return { comp, informes };
  }

  function abrir(herramienta: HerramientaAlumnos): void {
    fixture = TestBed.createComponent(Anfitrion);
    fixture.componentRef.setInput('herramienta', herramienta);
    document.body.appendChild(el());
    fixture.detectChanges();
  }

  function panel(): HTMLDialogElement {
    return el().querySelector<HTMLDialogElement>('dialog.pn-panel')!;
  }

  function elegir(selector: string, valor: string): void {
    const campo = panel().querySelector<HTMLSelectElement | HTMLInputElement>(selector)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo.tagName === 'SELECT' ? 'change' : 'input'));
    pintar();
  }

  function pulsar(texto: string): void {
    const boton = Array.from(panel().querySelectorAll<HTMLElement>('button, label')).find(
      (b) => b.textContent!.replace(/\s+/g, ' ').trim() === texto
    );
    if (!boton) {
      throw new Error(`Sin «${texto}» en el panel`);
    }
    boton.click();
    pintar();
  }

  function botonPie(texto: string): HTMLButtonElement {
    return Array.from(panel().querySelectorAll<HTMLButtonElement>('.pn-panel__pie button')).find(
      (b) => b.textContent!.trim() === texto
    )!;
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    descargas = [];
    TestBed.configureTestingModule({
      imports: [Anfitrion],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        InformePdfService,
        { provide: LoadingService, useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']) },
        {
          provide: CatalogoService,
          useValue: {
            disciplinasActivas: () => [
              { codigo: 'TAEKWONDO', nombre: 'Taekwondo', requiereLicencia: true },
              { codigo: 'KICKBOXING', nombre: 'Kickboxing', requiereLicencia: true },
              { codigo: 'PILATES', nombre: 'Pilates', requiereLicencia: false },
            ],
            etiquetaDisciplina: (c: string) => c.charAt(0) + c.slice(1).toLowerCase(),
          },
        },
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
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    quitarTokens();
  });

  it('listado de asistencia: los campos del boceto, «Ver PDF» y «Descargar PDF» piden lo mismo que hoy, con los mismos nombres', () => {
    const { comp, informes } = hoy();
    comp.mesAnoAsistencia = '2026-09';
    comp.toggleGrupoSeleccionado('lunes');
    comp.toggleGrupoSeleccionado('miércoles');
    comp.generarListadoAsistencia('ver');
    const deHoy = anotar();
    const nombresHoy = informes.documentos.map((d) => d.nombreArchivo);
    informes.cerrar();

    abrir('asistencia');
    expect(panel().querySelector('h2')!.textContent!.trim()).toBe('Listado de asistencia');
    const dias = Array.from(panel().querySelectorAll('fieldset label')).map((l) => l.textContent!.trim());
    expect(dias).toEqual(['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes']);
    expect(botonPie('Ver PDF').disabled).toBeTrue();
    elegir('#pn-asistencia-mes', '09');
    elegir('#pn-asistencia-mes-ano', '2026');
    pulsar('Lunes');
    expect(panel().textContent).toContain('Se genera 1 PDF.');
    pulsar('Miércoles');
    expect(panel().textContent).toContain('Se generan 2 PDF, uno por día.');
    const lunes = panel().querySelector<HTMLInputElement>('fieldset input[type="checkbox"]')!;
    expect(lunes.checked).toBeTrue();
    botonPie('Ver PDF').click();
    pintar();
    expect(anotar()).toEqual(deHoy);
    // El de la pantalla (lo provee el anfitrión, como el listado nuevo), no el de la raíz que usa el de hoy.
    const informesPanel = fixture.debugElement.children[0].children[0].injector.get(InformePdfService);
    expect(deHoy.map((p) => p.url)).toEqual([
      '/informes/asistencia?year=2026&month=9&grupo=lunes',
      '/informes/asistencia?year=2026&month=9&grupo=mi%C3%A9rcoles',
    ]);
    expect(nombresHoy).toEqual(['Asistencia-lunes-2026-09.pdf', 'Asistencia-miércoles-2026-09.pdf']);
    expect(informesPanel.documentos.map((d) => d.nombreArchivo)).toEqual(nombresHoy);
    informesPanel.cerrar();
    botonPie('Descargar PDF').click();
    pintar();
    expect(anotar()).toEqual(deHoy);
    expect(descargas).toEqual(nombresHoy);
  });

  it('listado de mensualidades: la misma petición y el mismo nombre de PDF que hoy', () => {
    const { comp, informes } = hoy();
    comp.mesAnoMensualidad = '2026-09';
    comp.generarListadoMensualidadMensual('ver');
    const deHoy = anotar();
    const nombre = informes.documentos[0].nombreArchivo;
    informes.cerrar();
    expect(deHoy).toEqual([{ metodo: 'GET', url: '/informes/listado-mensualidad-mensual?mesAno=2026-09&soloActivos=true', cuerpo: null }]);
    expect(nombre).toBe('listado_mensualidad_SEPTIEMBRE 2026.pdf');

    abrir('listado-mensualidades');
    expect(panel().querySelector('h2')!.textContent!.trim()).toBe('Listado de mensualidades');
    elegir('#pn-listado-mes', '09');
    elegir('#pn-listado-mes-ano', '2026');
    botonPie('Descargar PDF').click();
    pintar();
    expect(anotar()).toEqual(deHoy);
    expect(descargas).toEqual([nombre]);
  });

  it('un PDF que falla avisa con el aviso del panel nuevo (dentro del panel, no detrás) y no con SweetAlert2', () => {
    const fire = spyOn(Swal, 'fire');
    abrir('listado-mensualidades');
    elegir('#pn-listado-mes', '09');
    elegir('#pn-listado-mes-ano', '2026');
    botonPie('Ver PDF').click();
    pintar();
    anotar((r) => r.flush(new Blob(['x']), { status: 500, statusText: 'Error' }));
    pintar();
    expect(fire).not.toHaveBeenCalled();
    const aviso = document.querySelector<HTMLElement>('.pn-aviso')!;
    expect(aviso.classList).toContain('pn-aviso--error');
    expect(aviso.textContent).toContain('No se pudo generar el listado de mensualidad mensual');
    expect(panel().contains(aviso)).toBeTrue();
  });

  it('el aviso de un PDF parcial (el «Aviso» de hoy, de tipo warning) sale como información con el mismo texto, no como error', () => {
    const avisos = TestBed.inject(AvisosService);
    const info = spyOn(avisos, 'info').and.callThrough();
    const error = spyOn(avisos, 'error').and.callThrough();
    const servicio = TestBed.runInInjectionContext(() => new InformePdfPanelService());
    const texto = 'No se pudo generar el PDF de: martes.';
    servicio.generar(of({ documentos: [], aviso: texto }), 'descargar', 'No se pudo generar el listado');
    expect(info).toHaveBeenCalledOnceWith(texto);
    expect(error).not.toHaveBeenCalled();
    servicio.generar(throwError(() => new Error('500')), 'descargar', 'No se pudo generar el listado');
    expect(error).toHaveBeenCalledOnceWith('No se pudo generar el listado');
    servicio.ngOnDestroy();
  });

  it('gestión: mensualidades generales y por deporte, con la fecha de asignación, como hoy', () => {
    const { comp } = hoy();
    comp.mesAnoSeleccionado = '2026-10';
    comp.fechaMensualidadGeneral = '2026-10-01';
    comp.cargarMensualidadesGenerales();
    const generalHoy = anotar();
    comp.deporteSeleccionado = 'TAEKWONDO';
    comp.cargarMensualidadesGenerales();
    const deporteHoy = anotar();

    abrir('mensualidades');
    expect(panel().querySelector('h2')!.textContent!.trim()).toBe('Gestión de mensualidades y licencias');
    const tipos = Array.from(panel().querySelectorAll('[role="radio"]')).map((r) => r.textContent!.trim());
    expect(tipos).toEqual(['Mensualidades generales', 'Mensualidades individuales', 'Licencias generales', 'Licencias individuales']);
    elegir('#pn-mensualidad-mes', '10');
    elegir('#pn-mensualidad-mes-ano', '2026');
    elegir('#pn-fecha-mensualidad-general', '2026-10-01');
    botonPie('Cargar mensualidades').click();
    pintar();
    expect(anotar()).toEqual(generalHoy);
    expect(generalHoy[0]).toEqual({ metodo: 'POST', url: '/productos-alumno/mensualidades/general?fechaAsignacion=2026-10-01', cuerpo: '2026-10' });
    expect(fixture.componentInstance.asignados).toBe(1);
    expect(document.querySelector('.pn-aviso')!.textContent).toContain('Mensualidades asignadas a todos los alumnos activos');
    // 8b-M: el cierre de la tarea larga lleva la gota de 32 px celebrando en lugar del icono; el texto sigue diciendo lo mismo.
    const mascota = document.querySelector<HTMLElement>('.pn-aviso pn-mascota')!;
    expect(mascota.classList).toContain('pn-mascota--sm');
    expect(mascota.getAttribute('aria-hidden')).toBe('true');
    expect(document.querySelector('.pn-aviso .pn-aviso__icono')).toBeNull();
    elegir('#pn-deporte-mensualidad-general', 'TAEKWONDO');
    botonPie('Cargar mensualidades').click();
    pintar();
    expect(anotar()).toEqual(deporteHoy);
  });

  it('8b-C: el buscador de alumnos: foco de 3 px en el acento-borde y su lista, dentro del <dialog>, con el acento de la pantalla', async () => {
    abrir('mensualidades');
    // Lo que deja escribirTema en el .pn de la pantalla (Alumnos) con una escuela verde.
    const pantalla = el().querySelector<HTMLElement>('.pn')!;
    pantalla.style.setProperty('--pn-acento', '#0E782D');
    pantalla.style.setProperty('--pn-acento-suave', '#E0F5E1');
    pantalla.style.setProperty('--pn-acento-borde', '#67956B');
    pulsar('Mensualidades individuales');
    http
      .expectOne((r) => r.url === `${API}/alumnos` && r.params.get('incluirInactivos') === 'false')
      .flush([
        { id: 5, nombre: 'Ada', apellidos: 'Simulada Maqueta' },
        { id: 6, nombre: 'Bruno', apellidos: 'Ficticio Prueba' },
      ]);
    pintar();
    // La directiva rehace las opciones con un MutationObserver: se espera a que lleguen.
    await new Promise((r) => setTimeout(r));
    elegir('#pn-alumno-mensualidad', '5');
    await new Promise((r) => setTimeout(r));
    pintar();
    const entrada = panel().querySelector<HTMLInputElement>('#pn-alumno-mensualidad-input')!;
    entrada.focus();
    entrada.click();
    pintar();
    const foco = getComputedStyle(entrada);
    expect(entrada.matches(':focus-visible')).toBeTrue();
    expect(foco.outlineWidth).withContext('anillo de 3 px').toBe('3px');
    expect(foco.outlineStyle).toBe('solid');
    expect(foco.outlineColor).withContext('en el acento-borde').toBe('rgb(103, 149, 107)');
    expect(foco.outlineOffset).withContext('2 px de separación').toBe('2px');

    const lista = document.getElementById(entrada.getAttribute('aria-controls')!)!;
    expect(lista.parentElement).withContext('la lista va dentro del <dialog>').toBe(panel());
    expect(lista.classList).withContext('sin .pn propio: hereda las variables de la pantalla').not.toContain('pn');
    const elegida = getComputedStyle(lista.querySelector('.admin-select-option.is-selected')!);
    expect(elegida.color).withContext('opción elegida: texto').toBe('rgb(14, 120, 45)');
    expect(elegida.backgroundColor).withContext('opción elegida: fondo').toBe('rgb(224, 245, 225)');
    entrada.blur();
  });

  it('gestión: mensualidad individual con el 409 «confirmar» en el diálogo nuevo y el reintento forzado de hoy', async () => {
    const { comp } = hoy();
    const fire = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as never);
    comp.alumnoSeleccionado = 5;
    comp.mesAnoSeleccionadoIndividual = '2026-10';
    comp.fechaMensualidadIndividual = '2026-10-02';
    comp.cargarMensualidadIndividual();
    const conflicto = (r: TestRequest) => r.flush({ accion: 'confirmar', mensaje: 'La mensualidad ya existe.' }, { status: 409, statusText: 'Conflict' });
    const primeraHoy = anotar(conflicto);
    await Promise.resolve();
    await Promise.resolve();
    const forzadaHoy = anotar();
    expect(fire).toHaveBeenCalled();
    fire.calls.reset();

    abrir('mensualidades');
    pulsar('Mensualidades individuales');
    // Como hoy, al pasar a «individual» se pide la lista completa de alumnos.
    const lista = http.expectOne((r) => r.url === `${API}/alumnos` && r.params.get('incluirInactivos') === 'false');
    lista.flush([{ id: 5, nombre: 'Ada', apellidos: 'Simulada Maqueta' }]);
    pintar();
    elegir('#pn-alumno-mensualidad', '5');
    elegir('#pn-mensualidad-individual-mes', '10');
    elegir('#pn-mensualidad-individual-mes-ano', '2026');
    elegir('#pn-fecha-mensualidad-individual', '2026-10-02');
    botonPie('Cargar mensualidad').click();
    pintar();
    expect(anotar(conflicto)).toEqual(primeraHoy);
    pintar();
    const dialogo = document.querySelector<HTMLDialogElement>('dialog.pn-dialogo')!;
    expect(dialogo.querySelector('h2')!.textContent!.trim()).toBe('Atención');
    expect(dialogo.textContent).toContain('La mensualidad ya existe.');
    Array.from(dialogo.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim() === 'Sí, cargar')!.click();
    for (let i = 0; i < 4; i++) {
      await Promise.resolve();
    }
    expect(anotar()).toEqual(forzadaHoy);
    expect(forzadaHoy[0].url).toContain('forzar=true');
    expect(fire).not.toHaveBeenCalled();
  });

  it('gestión: licencias generales e individuales como hoy', () => {
    const { comp } = hoy();
    comp.anoLicenciaSeleccionado = 2026;
    comp.fechaLicenciaGeneral = '2026-02-01';
    comp.cargarLicenciasGenerales();
    const generalHoy = anotar();
    comp.alumnoSeleccionado = 5;
    comp.anoLicenciaSeleccionadoIndividual = 2026;
    comp.deporteLicenciaSeleccionadoIndividual = 'KICKBOXING';
    comp.fechaLicenciaIndividual = '2026-02-03';
    comp.cargarLicenciaIndividual();
    const individualHoy = anotar();

    abrir('mensualidades');
    pulsar('Licencias generales');
    const deportes = Array.from(panel().querySelectorAll<HTMLOptionElement>('#pn-deporte-licencia-general option')).map((o) => o.textContent!.trim());
    expect(deportes).toEqual(['Todos los alumnos', 'Solo Taekwondo', 'Solo Kickboxing']);
    elegir('#pn-ano-licencia-general', '2026');
    elegir('#pn-fecha-licencia-general', '2026-02-01');
    botonPie('Cargar licencias').click();
    pintar();
    expect(anotar()).toEqual(generalHoy);

    pulsar('Licencias individuales');
    http.expectOne((r) => r.url === `${API}/alumnos`).flush([{ id: 5, nombre: 'Ada', apellidos: 'Simulada Maqueta' }]);
    pintar();
    elegir('#pn-alumno-licencia', '5');
    elegir('#pn-deporte-licencia-individual', 'KICKBOXING');
    elegir('#pn-ano-licencia-individual', '2026');
    elegir('#pn-fecha-licencia-individual', '2026-02-03');
    botonPie('Cargar licencia').click();
    pintar();
    expect(anotar()).toEqual(individualHoy);
  });

  /** Las cuatro vías de cierre del panel: «Cancelar», la X, Esc (y el evento «cancel» del navegador) y el velo. */
  function intentarCerrar(): void {
    const d = panel();
    botonPie('Cancelar').click();
    d.querySelector<HTMLButtonElement>('button[aria-label="Cerrar"]')!.click();
    d.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    d.dispatchEvent(new Event('cancel', { cancelable: true }));
    d.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 10, clientY: 300, detail: 1 }));
    pintar();
  }

  it('con «Cargar mensualidades» en vuelo el panel NO se cierra por ninguna vía ni deja un segundo envío; tras la respuesta, sí', () => {
    abrir('mensualidades');
    elegir('#pn-mensualidad-mes', '10');
    elegir('#pn-mensualidad-mes-ano', '2026');
    const cargar = botonPie('Cargar mensualidades');
    cargar.focus();
    cargar.click();
    pintar();
    const [envio] = http.match((r) => r.method === 'POST');
    expect(envio).withContext('primer POST').toBeDefined();
    expect(cargar.getAttribute('aria-busy')).toBe('true');
    expect(cargar.textContent!.trim()).toBe('Cargando…');
    expect(document.activeElement).toBe(cargar);
    expect(botonPie('Cancelar').disabled).toBeTrue();
    expect(panel().querySelector<HTMLButtonElement>('button[aria-label="Cerrar"]')!.disabled).toBeTrue();

    intentarCerrar();
    expect(fixture.componentInstance.cerrada).toBeFalse();
    expect(panel().open).toBeTrue();
    cargar.click();
    pintar();
    expect(http.match((r) => r.method === 'POST').length).withContext('segundo POST').toBe(0);

    envio.flush({});
    pintar();
    expect(fixture.componentInstance.asignados).toBe(1);
    expect(cargar.getAttribute('aria-busy')).toBeNull();
    expect(botonPie('Cancelar').disabled).toBeFalse();
    botonPie('Cancelar').click();
    expect(fixture.componentInstance.cerrada).toBeTrue();
  });

  it('con «Cargar licencias» en vuelo tampoco se cierra; tras la respuesta, la X lo cierra', () => {
    abrir('mensualidades');
    pulsar('Licencias generales');
    elegir('#pn-ano-licencia-general', '2026');
    botonPie('Cargar licencias').click();
    pintar();
    const [envio] = http.match((r) => r.method === 'POST');
    expect(envio).toBeDefined();
    intentarCerrar();
    expect(fixture.componentInstance.cerrada).toBeFalse();
    envio.flush({});
    pintar();
    expect(fixture.componentInstance.asignados).toBe(1);
    panel().querySelector<HTMLButtonElement>('button[aria-label="Cerrar"]')!.click();
    expect(fixture.componentInstance.cerrada).toBeTrue();
  });

  it('con un PDF generándose tampoco se cierra; tras la respuesta, Esc lo cierra', () => {
    abrir('listado-mensualidades');
    elegir('#pn-listado-mes', '09');
    elegir('#pn-listado-mes-ano', '2026');
    botonPie('Descargar PDF').click();
    pintar();
    const [pdf] = http.match((r) => r.method === 'GET' && r.url.includes('/informes/'));
    expect(pdf).toBeDefined();
    intentarCerrar();
    expect(fixture.componentInstance.cerrada).toBeFalse();
    pdf.flush(new Blob(['pdf']));
    pintar();
    panel().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    expect(fixture.componentInstance.cerrada).toBeTrue();
  });
});
