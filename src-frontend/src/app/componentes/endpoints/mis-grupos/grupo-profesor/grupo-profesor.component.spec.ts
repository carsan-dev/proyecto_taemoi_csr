import { ComponentFixture, TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { GrupoProfesorComponent, esApto } from './grupo-profesor.component';
import { AlumnoDeGrupo, GrupoProfesor } from '../../../../interfaces/profesor';
import { ProfesorService } from '../../../../servicios/endpoints/profesor.service';
import { mesesAsistencia } from '../profesor-textos';
import { environment } from '../../../../../environments/environment';
import type { DocumentoInforme } from '../../../../servicios/generales/informe-pdf.service';
import { EstilosPanelService } from '../../../../panel-nuevo/estilos-panel.service';
import { VisorPdfComponent } from '../../../../panel-nuevo/visor-pdf/visor-pdf.component';
import { Toast } from '../../../../utils/toast.util';

@Component({ selector: 'pn-visor-pdf', standalone: true, template: '' })
class ViewerStub {
  @Input() documentos: DocumentoInforme[] = [];
  @Input() aviso = '';
  @Input() focoRetorno: HTMLElement | null = null;
  @Output() readonly cerrar = new EventEmitter<void>();
}

const GRUPO: GrupoProfesor = {
  id: 4, nombre: 'Infantil lunes y miércoles', sede: { id: 1, nombre: 'Sede Centro' }, rol: 'TITULAR', alumnosActivos: 3, disciplina: null, turnos: [],
};

function alumno(id: number, nombre: string, apto: boolean, grado = 'Amarillo'): AlumnoDeGrupo {
  return {
    id, nombre, apellidos: 'Prueba',
    deportes: [{ deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: { codigo: 'G', nombre: grado }, fechaGrado: '2026-03-12', apto, delGrupo: true }],
  };
}

const ALUMNOS = [alumno(1, 'Ana', true), alumno(2, 'Luis', false, 'Blanco'), alumno(3, 'Eva', false)];

describe('GrupoProfesorComponent', () => {
  let fixture: ComponentFixture<GrupoProfesorComponent>;
  let profesor: jasmine.SpyObj<ProfesorService>;

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const filtro = (i: number) => el().querySelectorAll<HTMLButtonElement>('.filtro button')[i];

  function crear(alumnos = of(ALUMNOS)): void {
    profesor = jasmine.createSpyObj<ProfesorService>('ProfesorService', ['misGrupos', 'alumnosDelGrupo', 'hojaAsistencia', 'salud']);
    profesor.misGrupos.and.returnValue(of([GRUPO]));
    profesor.alumnosDelGrupo.and.returnValue(alumnos);
    TestBed.configureTestingModule({
      imports: [GrupoProfesorComponent],
      providers: [
        provideRouter([]),
        { provide: ProfesorService, useValue: profesor },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ grupoId: '4' }) } } },
      ],
    });
    fixture = TestBed.createComponent(GrupoProfesorComponent);
    fixture.detectChanges();
  }

  it('pinta el grupo y sus alumnos con deporte, grado y chip «apto», sin pedir la salud', () => {
    crear();
    expect(profesor.alumnosDelGrupo).toHaveBeenCalledWith(4);
    expect(texto('h1')).toBe('Infantil lunes y miércoles');
    expect(texto('.subtitulo')).toBe('Sede Centro · Titular');
    const filas = el().querySelectorAll<HTMLAnchorElement>('a.alumno');
    expect(filas.length).toBe(3);
    expect(filas[0].getAttribute('href')).toBe('/mis-grupos/alumnos/1?grupo=4');
    expect(filas[0].textContent?.replace(/\s+/g, ' ')).toContain('Taekwondo · Amarillo');
    expect(filas[0].querySelector('.chip-apto')).not.toBeNull();
    expect(filas[1].querySelector('.chip-apto')).toBeNull();
    expect(profesor.salud).not.toHaveBeenCalled();
  });

  it('filtro segmentado «Todos N | Aptos M»', () => {
    crear();
    expect(filtro(0).textContent?.replace(/\s+/g, ' ').trim()).toBe('Todos 3');
    expect(filtro(1).textContent?.replace(/\s+/g, ' ').trim()).toBe('Aptos 1');
    expect(filtro(0).getAttribute('aria-pressed')).toBe('true');

    filtro(1).click();
    fixture.detectChanges();
    expect(filtro(1).getAttribute('aria-pressed')).toBe('true');
    expect(Array.from(el().querySelectorAll('.alumno-nombre')).map((n) => n.textContent?.trim())).toEqual(['Ana Prueba']);

    filtro(0).click();
    fixture.detectChanges();
    expect(el().querySelectorAll('a.alumno').length).toBe(3);
  });

  it('esApto: apto en alguno de sus deportes', () => {
    const mixto = alumno(5, 'Mar', false);
    mixto.deportes.push({ ...mixto.deportes[0], deporte: 'KICKBOXING', apto: true });
    expect(esApto(mixto)).toBeTrue();
    expect(esApto(alumno(6, 'Leo', false))).toBeFalse();
  });

  it('sin alumnos activos muestra el vacío y no el filtro', () => {
    crear(of([]));
    expect(texto('.estado')).toBe('Este grupo no tiene alumnos activos.');
    expect(el().querySelector('.filtro')).toBeNull();
  });

  it('un 403 muestra el mensaje del servidor', () => {
    crear(throwError(() => new HttpErrorResponse({ status: 403, error: { mensaje: 'No tienes acceso a este grupo' } })));
    expect(texto('.estado-error p')).toBe('No tienes acceso a este grupo');
  });

});

describe('GrupoProfesorComponent — vista previa de asistencia', () => {
  let fixture: ComponentFixture<GrupoProfesorComponent>;
  let http: HttpTestingController;
  let estilos: jasmine.SpyObj<EstilosPanelService>;
  const BASE = `${environment.apiUrl}/profesor/grupos`;
  const root = (): HTMLElement => fixture.nativeElement;
  const boton = () => root().querySelector<HTMLButtonElement>('.btn-asistencia')!;
  const visor = () => fixture.debugElement.query(By.directive(ViewerStub));
  const solicitud = () => http.expectOne(r => r.url === `${BASE}/4/asistencia`);
  const blob = new Blob(['%PDF'], { type: 'application/pdf' });

  beforeEach(() => {
    estilos = jasmine.createSpyObj<EstilosPanelService>('EstilosPanelService', ['cargar']);
    estilos.cargar.and.resolveTo();
    TestBed.configureTestingModule({
      imports: [GrupoProfesorComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: EstilosPanelService, useValue: estilos },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ grupoId: '4' }) } } },
      ],
    }).overrideComponent(GrupoProfesorComponent, {
      remove: { imports: [VisorPdfComponent] }, add: { imports: [ViewerStub] },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GrupoProfesorComponent);
    fixture.detectChanges();
    http.expectOne(BASE).flush([GRUPO]);
    http.expectOne(`${BASE}/4/alumnos`).flush(ALUMNOS);
    fixture.detectChanges();
  });

  afterEach(() => { fixture.destroy(); http.verify(); });

  it('abre el visor con el blob y Content-Disposition, sin descarga directa, y conecta el cierre y el foco', fakeAsync(() => {
    const actual = mesesAsistencia()[0];
    expect(fixture.componentInstance.mes).toBe(actual.valor);
    const clic = spyOn(HTMLAnchorElement.prototype, 'click');
    const url = spyOn(URL, 'createObjectURL');
    const disparador = boton();
    disparador.click();
    const request = solicitud();
    expect(request.request.responseType).toBe('blob');
    expect(request.request.params.get('year')).toBe(String(actual.year));
    expect(request.request.params.get('month')).toBe(String(actual.month));
    request.flush(blob, { headers: { 'Content-Disposition': 'attachment; filename="Asistencia-grupo-4.pdf"' } });
    flushMicrotasks();
    fixture.detectChanges();

    const viewer = visor().componentInstance as ViewerStub;
    expect(viewer.documentos).toEqual([{ blob, nombreArchivo: 'Asistencia-grupo-4.pdf', titulo: 'Hoja de asistencia' }]);
    expect(viewer.documentos[0].blob).toBe(blob);
    expect(viewer.focoRetorno).toBe(disparador);
    expect(viewer.aviso).toBe('');
    expect(visor().nativeElement.closest('.pn[pnAcento]')).not.toBeNull();
    expect(estilos.cargar).toHaveBeenCalledTimes(1);
    expect(clic).not.toHaveBeenCalled();
    expect(url).not.toHaveBeenCalled();
    viewer.cerrar.emit();
    fixture.detectChanges();
    expect(visor()).toBeNull();
    expect(fixture.componentInstance.focoRetorno).toBeNull();
    expect(boton().disabled).toBeFalse();
  }));

  it('mantiene el mes y año elegidos y bloquea el doble clic incluso antes de actualizar la vista', fakeAsync(() => {
    const elegido = fixture.componentInstance.meses[2];
    const select = root().querySelector<HTMLSelectElement>('#mesAsistencia')!;
    select.value = elegido.valor;
    select.dispatchEvent(new Event('change'));
    boton().click();
    expect(fixture.componentInstance.descargando).toBeTrue();
    boton().click();
    fixture.detectChanges();
    // NgModel aplica disabled en una microtarea; el HTTP sigue pendiente aunque el CSS ya esté listo.
    flushMicrotasks();
    fixture.detectChanges();
    expect(boton().disabled).toBeTrue();
    expect(boton().getAttribute('aria-busy')).toBe('true');
    expect(root().querySelector('.spinner-border')).not.toBeNull();
    expect(select.disabled).toBeTrue();
    const request = solicitud();
    expect(request.request.params.get('year')).toBe(String(elegido.year));
    expect(request.request.params.get('month')).toBe(String(elegido.month));
    request.flush(blob);
    flushMicrotasks();
    fixture.detectChanges();
    expect(fixture.componentInstance.mes).toBe(elegido.valor);
    expect(boton().disabled).toBeFalse();
    expect(estilos.cargar).toHaveBeenCalledTimes(1);
  }));

  it('conserva el aviso de error HTTP y permite reintentar y abrir el visor', fakeAsync(() => {
    const aviso = spyOn(Toast, 'fire');
    boton().click();
    const primera = solicitud();
    primera.flush(null, { status: 500, statusText: 'Server Error' });
    flushMicrotasks();
    fixture.detectChanges();
    expect(aviso).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', title: 'No se ha podido descargar la hoja de asistencia.' }));
    expect(visor()).toBeNull();
    expect(boton().disabled).toBeFalse();

    boton().click();
    expect(fixture.componentInstance.descargando).toBeTrue();
    const segunda = solicitud();
    expect(segunda).not.toBe(primera);
    segunda.flush(blob, { headers: { 'Content-Disposition': 'attachment; filename="Reintento.pdf"' } });
    flushMicrotasks();
    fixture.detectChanges();
    expect((visor().componentInstance as ViewerStub).documentos).toEqual([
      { blob, nombreArchivo: 'Reintento.pdf', titulo: 'Hoja de asistencia' },
    ]);
    expect(boton().disabled).toBeFalse();
    expect(estilos.cargar).toHaveBeenCalledTimes(2);
    expect(aviso).toHaveBeenCalledTimes(1);
  }));

  it('mantiene el mensaje del servidor cuando el error llega como blob JSON', async () => {
    const aviso = spyOn(Toast, 'fire');
    // Blob.text() no forma parte de la estabilidad de Angular: esperamos la entrega real del aviso.
    const mostrado = new Promise<void>(resolve => {
      aviso.and.callFake(() => {
        resolve();
        return Promise.resolve({ isConfirmed: false, isDenied: false, isDismissed: true });
      });
    });
    boton().click();
    solicitud().flush(new Blob([JSON.stringify({ mensaje: 'No tienes acceso a este grupo' })], { type: 'application/json' }),
      { status: 403, statusText: 'Forbidden' });
    await mostrado;
    await fixture.whenStable();
    fixture.detectChanges();
    expect(aviso).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', title: 'No tienes acceso a este grupo' }));
    expect(visor()).toBeNull();
    expect(boton().disabled).toBeFalse();
  });

  it('espera al CSS del panel antes de abrir el visor', fakeAsync(() => {
    let terminar!: () => void;
    estilos.cargar.and.returnValue(new Promise<void>(resolve => { terminar = resolve; }));
    boton().click();
    solicitud().flush(blob);
    flushMicrotasks();
    fixture.detectChanges();
    expect(visor()).toBeNull();
    expect(boton().disabled).toBeTrue();
    terminar();
    flushMicrotasks();
    fixture.detectChanges();
    expect(visor()).not.toBeNull();
    expect(boton().disabled).toBeFalse();
  }));

  it('si falla el CSS, muestra el aviso y permite reintentar sin abrir un visor sin estilos', fakeAsync(() => {
    estilos.cargar.and.rejectWith(new Error('No se pudo cargar panel-nuevo.css'));
    const aviso = spyOn(Toast, 'fire');
    boton().click();
    const primera = solicitud();
    primera.flush(blob);
    flushMicrotasks();
    fixture.detectChanges();
    expect(aviso).toHaveBeenCalledWith(jasmine.objectContaining({ icon: 'error', title: 'No se ha podido descargar la hoja de asistencia.' }));
    expect(visor()).toBeNull();
    expect(boton().disabled).toBeFalse();

    estilos.cargar.and.resolveTo();
    boton().click();
    expect(fixture.componentInstance.descargando).toBeTrue();
    const segunda = solicitud();
    expect(segunda).not.toBe(primera);
    segunda.flush(blob, { headers: { 'Content-Disposition': 'attachment; filename="Reintento.pdf"' } });
    flushMicrotasks();
    fixture.detectChanges();
    expect((visor().componentInstance as ViewerStub).documentos).toEqual([
      { blob, nombreArchivo: 'Reintento.pdf', titulo: 'Hoja de asistencia' },
    ]);
    expect(boton().disabled).toBeFalse();
    expect(estilos.cargar).toHaveBeenCalledTimes(2);
    expect(aviso).toHaveBeenCalledTimes(1);
  }));

  for (const pendientes of ['HTTP', 'CSS', 'HTTP y CSS']) {
    it(`al destruir con ${pendientes} pendiente cancela la carga e ignora respuestas tardías`, fakeAsync(() => {
      let terminarCss: (() => void) | undefined;
      if (pendientes.includes('CSS')) {
        estilos.cargar.and.returnValue(new Promise<void>(resolve => { terminarCss = resolve; }));
      }
      boton().click();
      const request = solicitud();
      expect(request.cancelled).toBeFalse();
      if (pendientes === 'CSS') {
        request.flush(blob);
      }
      flushMicrotasks();
      fixture.detectChanges();
      expect(fixture.componentInstance.descargando).toBeTrue();
      expect(visor()).toBeNull();

      fixture.destroy();
      expect(request.cancelled).toBeTrue();
      expect(fixture.componentInstance.descargando).toBeFalse();
      expect(fixture.componentInstance.documentos).toEqual([]);

      if (pendientes !== 'CSS') {
        expect(() => request.flush(blob)).toThrowError('Cannot flush a cancelled request.');
      }
      terminarCss?.();
      flushMicrotasks();
      expect(fixture.componentInstance.descargando).toBeFalse();
      expect(fixture.componentInstance.documentos).toEqual([]);
      expect(root().querySelector('pn-visor-pdf')).toBeNull();
    }));
  }
});
