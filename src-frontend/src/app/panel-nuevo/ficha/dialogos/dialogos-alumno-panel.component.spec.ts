import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { CONSULTA_HOJA } from '../../capas/capa-modal';
import { DialogosAlumnoPanelComponent } from './dialogos-alumno-panel.component';
import { FotoAmpliadaPanelComponent } from './foto-ampliada-panel.component';
import { itSinReloj } from '../../../testing/reloj-prueba';

/** Lo que los diálogos leen y llaman de la ficha (`[ficha]="this"`): el estado y los métodos de la ficha de hoy. */
function fichaFalsa(): any {
  const f: any = {
    alumno: { id: 7, nombre: 'Rubén', apellidos: 'Prueba Ficticia' },
    mostrarModalConvocatorias: false,
    mostrarModalEliminarConvocatorias: false,
    mostrarModalWhatsapp: false,
    mostrarModalAgregarDeporte: false,
    mostrarModalActualizarGrado: false,
    deporteParaConvocatoria: 'TAEKWONDO',
    convocatoriaActual: { id: 60, fechaConvocatoria: '2026-12-10', deporte: 'TAEKWONDO' },
    convocatoriasFiltradasPorDeporte: [{ id: 61, fechaConvocatoria: '2027-03-15', deporte: 'TAEKWONDO' }],
    convocatoriasDelAlumno: [{ id: 50, fechaConvocatoria: '2026-06-01', deporte: 'TAEKWONDO' }],
    documentosAlumno: [
      { id: 900, nombre: 'documento-ficticio-1.pdf', tipo: 'application/pdf' },
      { id: 901, nombre: 'documento-ficticio-2.pdf', tipo: 'application/pdf' },
    ],
    whatsappMensaje: 'Hola',
    whatsappTelefonoSeleccionado: 600000000,
    whatsappDocumentosSeleccionados: new Set<number>(),
    deportesDisponibles: ['KICKBOXING', 'PILATES'],
    nuevoDeporte: 'KICKBOXING',
    gradoInicialDeporte: 'BLANCO',
    fechaAltaDeporte: '2026-10-22',
    fechaAltaInicialDeporte: '',
    fechaGradoDeporte: '2026-10-22',
    deporteParaActualizarGrado: 'TAEKWONDO',
    nuevoGradoActualizar: 'BLANCO',
    fechaGradoActualizar: '2023-03-22',
    getDeporteLabel: (d: string) => ({ TAEKWONDO: 'Taekwondo', KICKBOXING: 'Kickboxing', PILATES: 'Pilates' } as any)[d] ?? d,
    getGradoDelDeporte: () => 'Blanco',
    gradoGuardado: () => 'BLANCO',
    gradosDeLaDisciplina: () => [
      { codigo: 'BLANCO', nombre: 'Blanco' },
      { codigo: 'AMARILLO', nombre: 'Amarillo' },
    ],
    esDeporteSinGrado: (d: string) => d === 'PILATES',
    requiereGradoNuevoDeporte: () => f.nuevoDeporte !== 'PILATES',
    isWhatsappDocumentoSeleccionado: (id: number) => f.whatsappDocumentosSeleccionados.has(id),
  };
  for (const m of [
    'cerrarModalConvocatorias',
    'cerrarModalEliminarConvocatorias',
    'cerrarModalWhatsapp',
    'cerrarModalAgregarDeporte',
    'cerrarModalActualizarGrado',
    'agregarAConvocatoriaPorDeporte',
    'eliminarDeConvocatoriaSeleccionada',
    'toggleWhatsappDocumento',
    'enviarWhatsappConDocumentos',
    'onNuevoDeporteChange',
    'agregarDeporte',
    'actualizarGradoDeporte',
  ]) {
    f[m] = jasmine.createSpy(m);
  }
  f.cerrarModalConvocatorias.and.callFake(() => (f.mostrarModalConvocatorias = false));
  f.cerrarModalWhatsapp.and.callFake(() => (f.mostrarModalWhatsapp = false));
  f.cerrarModalAgregarDeporte.and.callFake(() => (f.mostrarModalAgregarDeporte = false));
  f.cerrarModalActualizarGrado.and.callFake(() => (f.mostrarModalActualizarGrado = false));
  f.toggleWhatsappDocumento.and.callFake((id: number, e: Event) => {
    const s = new Set<number>(f.whatsappDocumentosSeleccionados);
    (e.target as HTMLInputElement).checked ? s.add(id) : s.delete(id);
    f.whatsappDocumentosSeleccionados = s;
  });
  return f;
}

@Component({
  standalone: true,
  imports: [DialogosAlumnoPanelComponent],
  template: '<div class="pn"><button id="disparador">Abrir</button><pn-dialogos-alumno [ficha]="ficha" /></div>',
})
class Anfitrion {
  ficha = fichaFalsa();
}

describe('DialogosAlumnoPanelComponent: los diálogos de la ficha en el panel nuevo (8b-D2b)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let ficha: any;
  let quitarTokens: () => void;

  const panel = () => document.querySelector<HTMLDialogElement>('dialog.pn-panel');
  const dialogo = () => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const esperar = () => new Promise<void>((r) => setTimeout(r));

  function boton(raiz: ParentNode, texto: string): HTMLButtonElement {
    const lista = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button'));
    const b = lista.find((x) => (x.getAttribute('aria-label') ?? x.textContent ?? '').replace(/\s+/g, ' ').trim() === texto);
    if (!b) throw new Error(`Sin botón «${texto}»: ${lista.map((x) => x.textContent?.replace(/\s+/g, ' ').trim()).join(' | ')}`);
    return b;
  }

  function abrir(bandera: string): void {
    const disparador = fixture.nativeElement.querySelector('#disparador') as HTMLButtonElement;
    disparador.focus();
    ficha[bandera] = true;
    fixture.detectChanges();
    pintarCapas();
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    fixture = TestBed.createComponent(Anfitrion);
    ficha = fixture.componentInstance.ficha;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    quitarTokens();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
  });

  it('cerrados no pintan nada', () => {
    expect(panel()).toBeNull();
    expect(dialogo()).toBeNull();
  });

  it('convocatorias: panel lateral con la actual y las demás; cada una llama al método de la ficha', () => {
    abrir('mostrarModalConvocatorias');
    const p = panel()!;
    expect(p.open).toBeTrue();
    expect(p.querySelector('h2')!.textContent!.trim()).toBe('Seleccionar Convocatoria - Taekwondo');
    const filas = Array.from(p.querySelectorAll<HTMLButtonElement>('.dialogos__fila'));
    const textos = (f: Element) =>
      Array.from(f.querySelectorAll('.dialogos__fila-titulo, .dialogos__fila-detalle')).map((s) => s.textContent!.trim()).join(' ');
    expect(filas.map(textos)).toEqual([
      'Convocatoria Actual 10/12/2026 - TAEKWONDO',
      'TAEKWONDO 15/03/2027',
    ]);
    filas[1].click();
    expect(ficha.agregarAConvocatoriaPorDeporte).toHaveBeenCalledOnceWith(ficha.convocatoriasFiltradasPorDeporte[0]);
    filas[0].click();
    expect(ficha.agregarAConvocatoriaPorDeporte).toHaveBeenCalledWith(ficha.convocatoriaActual);
  });

  it('convocatorias sin ninguna: el vacío de hoy', () => {
    ficha.convocatoriaActual = null;
    ficha.convocatoriasFiltradasPorDeporte = [];
    abrir('mostrarModalConvocatorias');
    expect(panel()!.textContent).toContain('No hay convocatorias disponibles para este deporte');
  });

  itSinReloj('Esc, la X y «Cerrar» cierran con el método de la ficha y el foco vuelve al disparador', async () => {
    abrir('mostrarModalConvocatorias');
    panel()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(ficha.cerrarModalConvocatorias).toHaveBeenCalledTimes(1);
    fixture.detectChanges();
    expect(panel()).toBeNull();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#disparador'));
    abrir('mostrarModalConvocatorias');
    boton(panel()!, 'Cerrar').click();
    expect(ficha.cerrarModalConvocatorias).toHaveBeenCalledTimes(2);
  });

  it('eliminar de convocatoria: cada convocatoria del alumno llama a eliminarDeConvocatoriaSeleccionada', () => {
    abrir('mostrarModalEliminarConvocatorias');
    const p = panel()!;
    expect(p.querySelector('h2')!.textContent!.trim()).toBe('Eliminar de Convocatoria');
    expect(p.textContent).toContain('Elige la convocatoria de la que deseas eliminar al alumno');
    p.querySelector<HTMLButtonElement>('.dialogos__fila')!.click();
    expect(ficha.eliminarDeConvocatoriaSeleccionada).toHaveBeenCalledOnceWith(ficha.convocatoriasDelAlumno[0]);
  });

  it('WhatsApp: mensaje, documentos y teléfono; «Enviar» llama a enviarWhatsappConDocumentos', () => {
    abrir('mostrarModalWhatsapp');
    const p = panel()!;
    expect(p.querySelector('h2')!.textContent!.trim()).toBe('Enviar WhatsApp');
    const area = p.querySelector<HTMLTextAreaElement>('textarea#whatsappMensaje')!;
    expect(p.querySelector('label[for="whatsappMensaje"]')!.textContent!.trim()).toBe('Mensaje');
    fixture.detectChanges();
    area.value = 'Hola, te envío la documentación.';
    area.dispatchEvent(new Event('input'));
    expect(ficha.whatsappMensaje).toBe('Hola, te envío la documentación.');
    const casilla = p.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    casilla.click();
    expect(ficha.toggleWhatsappDocumento).toHaveBeenCalledWith(900, jasmine.any(Event));
    expect(p.textContent).toContain('Numero seleccionado: 600000000');
    boton(p, 'Enviar').click();
    expect(ficha.enviarWhatsappConDocumentos).toHaveBeenCalledTimes(1);
  });

  it('agregar deporte: panel lateral con sus campos ligados a la ficha; «Agregar Deporte» llama a agregarDeporte', () => {
    abrir('mostrarModalAgregarDeporte');
    const p = panel()!;
    expect(p.querySelector('h2')!.textContent!.trim()).toBe('Agregar Deporte al Alumno');
    const deporte = p.querySelector<HTMLSelectElement>('select#nuevoDeporte')!;
    expect(Array.from(deporte.options).map((o) => o.textContent!.trim())).toEqual(['Kickboxing', 'Pilates']);
    expect(p.querySelector('select#gradoInicialDeporte')).not.toBeNull();
    expect(p.querySelector<HTMLInputElement>('input#fechaAltaDeporte')!.type).toBe('date');
    expect(p.querySelector('input#fechaAltaInicialDeporte')).not.toBeNull();
    expect(p.querySelector('input#fechaGradoDeporte')).not.toBeNull();
    deporte.value = 'PILATES';
    deporte.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(ficha.onNuevoDeporteChange).toHaveBeenCalledWith('PILATES');
    expect(p.querySelector('select#gradoInicialDeporte')).withContext('sin grado en Pilates').toBeNull();
    boton(p, 'Agregar Deporte').click();
    expect(ficha.agregarDeporte).toHaveBeenCalledTimes(1);
  });

  it('agregar deporte: sin fecha de alta, «Agregar Deporte» deshabilitado como hoy', () => {
    ficha.fechaAltaDeporte = '';
    abrir('mostrarModalAgregarDeporte');
    expect(boton(panel()!, 'Agregar Deporte').disabled).toBeTrue();
  });

  it('agregar deporte por debajo de 768 px: hoja inferior', () => {
    spyOn(window, 'matchMedia').and.callFake(
      (c: string) => ({ matches: c === CONSULTA_HOJA, media: c, addEventListener: () => undefined, removeEventListener: () => undefined }) as any
    );
    abrir('mostrarModalAgregarDeporte');
    expect(panel()!.classList).toContain('pn-panel--hoja');
  });

  itSinReloj('actualizar grado: diálogo con el grado actual, «Nuevo Grado» y «Fecha de Grado»; aceptar pone los valores y llama a la ficha', async () => {
    abrir('mostrarModalActualizarGrado');
    const d = dialogo()!;
    expect(d.querySelector('h2')!.textContent!.trim()).toBe('Actualizar Grado - Taekwondo');
    expect(d.textContent!.replace(/\s+/g, ' ')).toContain('Grado actual: Blanco');
    const grado = d.querySelector<HTMLElement>('[role="radiogroup"]')!;
    const radios = Array.from(grado.querySelectorAll<HTMLButtonElement>('[role="radio"]'));
    expect(radios.map((r) => r.getAttribute('aria-checked'))).toEqual(['true', 'false']);
    radios[1].click();
    pintarCapas();
    const fecha = d.querySelector<HTMLInputElement>('input[type="date"]')!;
    expect(fecha.value).toBe('2023-03-22');
    boton(d, 'Actualizar Grado').click();
    await esperar();
    expect(ficha.nuevoGradoActualizar).toBe('AMARILLO');
    expect(ficha.fechaGradoActualizar).toBe('2023-03-22');
    expect(ficha.actualizarGradoDeporte).toHaveBeenCalledTimes(1);
    expect(ficha.cerrarModalActualizarGrado).not.toHaveBeenCalled();
    expect(ficha.mostrarModalActualizarGrado).toBeFalse();
  });

  itSinReloj('actualizar grado: cancelar llama a cerrarModalActualizarGrado sin actualizar', async () => {
    abrir('mostrarModalActualizarGrado');
    boton(dialogo()!, 'Cancelar').click();
    await esperar();
    expect(ficha.cerrarModalActualizarGrado).toHaveBeenCalledTimes(1);
    expect(ficha.actualizarGradoDeporte).not.toHaveBeenCalled();
  });
});

@Component({
  standalone: true,
  imports: [FotoAmpliadaPanelComponent],
  template: `<div class="pn"><button id="foto">Foto</button>
    @if (url) {<pn-foto-ampliada [url]="url" alt="Foto de Rubén Prueba Ficticia" (cerrar)="url = null" />}</div>`,
})
class AnfitrionFoto {
  url: string | null = null;
}

describe('FotoAmpliadaPanelComponent: la foto ampliada en el panel nuevo (8b-D2b)', () => {
  let fixture: ComponentFixture<AnfitrionFoto>;
  let quitarTokens: () => void;
  const caja = () => document.querySelector<HTMLDialogElement>('dialog.pn-foto');

  function abrir(): void {
    (fixture.nativeElement.querySelector('#foto') as HTMLButtonElement).focus();
    fixture.componentInstance.url = '/assets/media/default.webp';
    fixture.detectChanges();
  }

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    fixture = TestBed.createComponent(AnfitrionFoto);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    quitarTokens();
  });

  it('caja de luz modal sobre el velo: imagen centrada de min(90vw, 960px) × 90dvh y X de 44 px con el foco', () => {
    abrir();
    const d = caja()!;
    expect(d.open).toBeTrue();
    expect(d.matches(':modal')).toBeTrue();
    expect(d.getAttribute('aria-label')).toBe('Foto ampliada del alumno');
    const img = d.querySelector('img')!;
    expect(img.getAttribute('src')).toBe('/assets/media/default.webp');
    expect(img.alt).toBe('Foto de Rubén Prueba Ficticia');
    const muestra = document.createElement('div');
    muestra.style.cssText = 'position: fixed; width: min(90vw, 960px); height: 90dvh';
    d.appendChild(muestra);
    const estilo = getComputedStyle(img);
    expect(parseFloat(estilo.maxWidth)).toBeCloseTo(parseFloat(getComputedStyle(muestra).width), 1);
    expect(parseFloat(estilo.maxHeight)).toBeCloseTo(parseFloat(getComputedStyle(muestra).height), 1);
    muestra.remove();
    const x = d.querySelector<HTMLButtonElement>('.pn-foto__cerrar')!;
    expect(x.getAttribute('aria-label')).toBe('Cerrar foto ampliada');
    expect(x.getBoundingClientRect().width).toBe(44);
    expect(x.getBoundingClientRect().height).toBe(44);
    expect(document.activeElement).toBe(x);
  });

  it('Esc, la X y el velo cierran; el foco vuelve a la foto', () => {
    abrir();
    caja()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(caja()).toBeNull();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#foto'));
    abrir();
    caja()!.querySelector<HTMLButtonElement>('.pn-foto__cerrar')!.click();
    fixture.detectChanges();
    expect(caja()).toBeNull();
    abrir();
    caja()!.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1, clientX: 5, clientY: 5 }));
    fixture.detectChanges();
    expect(caja()).toBeNull();
  });

  it('un clic en la imagen no cierra', () => {
    abrir();
    caja()!.querySelector('img')!.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1, clientX: 50, clientY: 50 }));
    fixture.detectChanges();
    expect(caja()).not.toBeNull();
  });
});
