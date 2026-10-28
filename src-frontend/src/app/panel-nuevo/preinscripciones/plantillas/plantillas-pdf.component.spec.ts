import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Subject, of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { BloqueVacioComponent } from '../../carga/bloque-vacio.component';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { DocumentoInforme } from '../../../servicios/generales/informe-pdf.service';
import { AvisosService } from '../../avisos/avisos.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { salirSinGuardarGuard } from '../../productos-alumno/salir-sin-guardar.guard';
import { VisorPdfComponent } from '../../visor-pdf/visor-pdf.component';
import { PlantillasPdfComponent } from './plantillas-pdf.component';

@Component({ selector: 'pn-visor-pdf', standalone: true, template: '' })
class ViewerStub {
  @Input() documentos: DocumentoInforme[] = [];
  @Input() aviso = '';
  @Input() focoRetorno: HTMLElement | null = null;
}

const BASE = `${environment.apiUrl}/preinscripciones/plantillas`;
const CONTENT = { cabecera: 'Escuela', contacto: 'Contacto', titulo: 'Preinscripción',
  normas: ['Primera norma\nSegunda línea'], consentimiento: 'Consentimiento', importes: 'Pago' };
const version = (n = 4) => ({ id: n, deporte: 'TAEKWONDO', version: n, activa: n === 4,
  creadaEn: new Date(2026, 9, 12).getTime() / 1000, contenido: { ...CONTENT, normas: [...CONTENT.normas] }, instrucciones: 'Después de firmar' });

describe('PlantillasPdfComponent', () => {
  let fixture: ComponentFixture<PlantillasPdfComponent>;
  let http: HttpTestingController;
  let admin: boolean;
  let confirm: jasmine.Spy;
  let notices: jasmine.SpyObj<AvisosService>;
  const root = (): HTMLElement => fixture.nativeElement;
  const component = () => fixture.componentInstance;
  const buttons = () => Array.from(root().querySelectorAll<HTMLButtonElement>('button'));
  const button = (text: string) => buttons().find(b => b.textContent?.trim() === text)!;
  async function settle(): Promise<void> {
    for (let i = 0; i < 8; i++) await Promise.resolve();
    fixture.detectChanges();
  }
  async function load(): Promise<void> {
    const request = http.expectOne(`${BASE}/TAEKWONDO`);
    expect(request.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    request.flush({ activa: version(), historial: [version(), version(3)] });
    await settle();
  }
  async function edit(id: string, value: string): Promise<void> {
    const field = root().querySelector<HTMLInputElement | HTMLTextAreaElement>(`#plantilla-${id}`)!;
    field.value = value;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
  }

  beforeEach(async () => {
    admin = true;
    confirm = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: true, valor: null });
    notices = jasmine.createSpyObj<AvisosService>('avisos', ['exito', 'error']);
    await TestBed.configureTestingModule({
      imports: [PlantillasPdfComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => admin } },
        { provide: ConfirmacionService, useValue: { confirmar: confirm } },
        { provide: AvisosService, useValue: notices }],
    }).overrideComponent(PlantillasPdfComponent, {
      remove: { imports: [VisorPdfComponent] }, add: { imports: [ViewerStub] },
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlantillasPdfComponent);
    fixture.detectChanges();
  });

  afterEach(() => { fixture.destroy(); http.verify(); });

  it('loads the active version, catalogue pills, history and API date', async () => {
    expect(root().querySelector('pn-esqueleto')).not.toBeNull();
    await load();
    expect(root().querySelector<HTMLInputElement>('#plantilla-cabecera')!.value).toBe('Escuela');
    expect(root().textContent).toContain('Versión 4');
    expect(root().textContent).toContain('12 oct 2026');
    expect(root().querySelector('[role="radio"][aria-checked="true"]')!.textContent).toContain('Taekwondo');
    expect(root().querySelector('.activa')!.textContent).toBe('Activa');
    expect(component().cambios).toBe(0);
    expect(root().querySelector('.pendientes')).toBeNull();
    expect(button('Publicar versión 5')).toBeUndefined();
  });

  it('shows the publish bar at the bottom only with changes, never in the top bar', async () => {
    await load();
    await edit('cabecera', 'Otra cabecera');
    const bar = root().querySelector<HTMLElement>('.pendientes')!;
    expect(bar.getAttribute('role')).toBe('region');
    expect(bar.querySelector('.pendientes-aviso')!.textContent!.trim()).toBe('1 cambio sin publicar');
    expect(bar.contains(button('Publicar versión 5'))).toBeTrue();
    expect(bar.contains(button('Descartar'))).toBeTrue();
    expect(root().querySelector('.barra')!.textContent).not.toContain('Publicar');
    expect(root().querySelector('.barra')!.contains(button('Previsualizar'))).toBeTrue();
    expect(root().querySelector('.pendientes-estado')!.textContent!.trim()).toBe('1 cambio sin publicar');
  });

  it('marks every editable field as required, one card per block, and shows no marks to managers', async () => {
    await load();
    expect(root().querySelectorAll('.bloque').length).toBe(5);
    expect(Array.from(root().querySelectorAll('.bloque__titulo')).map(t => t.firstChild!.textContent!.trim()))
      .toEqual(['Encabezado', 'Normas', 'Consentimiento fotográfico', 'Importes o información de pago', 'Instrucciones posteriores']);
    expect(Array.from(root().querySelectorAll('.bloque__ayuda')).map(t => t.textContent!.trim())).toEqual([
      'Cabecera, contacto y título de la hoja.', 'Salen numeradas en el PDF, en este orden.',
      'Texto que acompaña a la casilla de autorización de imágenes.', 'Precios y forma de pago que verá la familia.',
      'Qué debe hacer la familia después de enviar la solicitud.']);
    for (const b of Array.from(root().querySelectorAll('.bloque'))) {
      expect(root().querySelector('#' + b.getAttribute('aria-describedby'))!.classList).toContain('bloque__ayuda');
    }
    for (const id of ['cabecera', 'contacto', 'titulo', 'norma-0']) {
      expect(root().querySelector(`label[for="plantilla-${id}"] .marca`)!.textContent).toBe('obligatorio');
    }
    for (const id of ['consentimiento', 'importes', 'instrucciones']) {
      expect(root().querySelector(`#titulo-${id} .marca`)!.textContent).toBe('obligatorio');
    }
    fixture.destroy();
    admin = false;
    fixture = TestBed.createComponent(PlantillasPdfComponent);
    fixture.detectChanges();
    await load();
    expect(root().querySelector('.marca')).toBeNull();
  });

  it('publishes only after confirming, with a flat payload and no version', async () => {
    await load();
    await edit('cabecera', 'Otra cabecera');
    const pending = component().publicar();
    expect(confirm).toHaveBeenCalledWith(jasmine.objectContaining({
      titulo: '¿Publicar la versión 5 de Taekwondo?', cancelar: 'Seguir editando', accion: 'Publicar',
    }));
    http.expectNone(r => r.method === 'POST');
    await settle();
    const request = http.expectOne(`${BASE}/TAEKWONDO`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ ...CONTENT, cabecera: 'Otra cabecera', instrucciones: 'Después de firmar' });
    expect(request.request.body.version).toBeUndefined();
    expect(request.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(root().querySelector('.primario')!.getAttribute('aria-busy')).toBe('true');
    request.flush({ ...version(5), contenido: { ...CONTENT, cabecera: 'Otra cabecera' } });
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO`).flush({ activa: version(5), historial: [version(5), version()] });
    await pending;
    await settle();
    expect(notices.exito).toHaveBeenCalledWith('Versión 5 publicada.');
    expect(component().cambios).toBe(0);
  });

  it('does not publish invalid text; marks, describes and focuses the first error', async () => {
    await load();
    await edit('contacto', '   ');
    await component().publicar();
    fixture.detectChanges();
    const field = root().querySelector('#plantilla-contacto')!;
    expect(field.getAttribute('aria-invalid')).toBe('true');
    expect(field.getAttribute('aria-describedby')).toBe('ayuda-contacto');
    expect(root().querySelector('#ayuda-contacto')!.textContent).toContain('Escribe este texto.');
    expect(document.activeElement).toBe(field);
    expect(confirm).not.toHaveBeenCalled();
    http.expectNone(r => r.method === 'POST');
  });

  it('does not preview invalid text', async () => {
    await load();
    await edit('titulo', '');
    await component().previsualizar(button('Previsualizar'));
    expect(component().errores['titulo']).toBe('Escribe este texto.');
    http.expectNone(r => r.method === 'POST');
  });

  it('opens the panel viewer with the preview blob, notice and return focus', async () => {
    await load();
    const trigger = button('Previsualizar');
    const pending = component().previsualizar(trigger);
    const request = http.expectOne(`${BASE}/TAEKWONDO/preview`);
    expect(request.request.body).toEqual({ ...CONTENT, instrucciones: 'Después de firmar' });
    const blob = new Blob(['pdf'], { type: 'application/pdf' });
    request.flush(blob);
    await pending;
    await settle();
    const viewer = fixture.debugElement.query(By.directive(ViewerStub)).componentInstance as ViewerStub;
    expect(viewer.documentos[0].blob).toBe(blob);
    expect(viewer.aviso).toBe('Vista previa sin publicar');
    expect(viewer.focoRetorno).toBe(trigger);
  });

  it('restores a historic version only after confirmation, warning about draft loss', async () => {
    await load();
    await edit('titulo', 'Nuevo título');
    const pending = component().restaurar(component().historial[1]);
    expect(confirm).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: '¿Restaurar la versión 3?',
      mensaje: 'Se publicará como versión 5 con los textos de la versión 3. El historial no cambia. Perderás los cambios sin publicar.' }));
    http.expectNone(r => r.method === 'POST');
    await settle();
    const request = http.expectOne(`${BASE}/TAEKWONDO/3/restaurar`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(version(5));
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO`).flush({ activa: version(5), historial: [version(5), version(), version(3)] });
    await pending;
    expect(notices.exito).toHaveBeenCalledWith('Versión 3 restaurada como versión 5.');
  });

  it('keeps the draft when publication fails with a server message', async () => {
    await load();
    await edit('titulo', 'No perder');
    const pending = component().publicar();
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO`).flush({ mensaje: 'Conflicto de versión.' }, { status: 409, statusText: 'Conflict' });
    await pending;
    expect(notices.error).toHaveBeenCalledWith('Conflicto de versión.');
    expect(component().borrador.titulo).toBe('No perder');
    expect(component().cambios).toBe(1);
    expect(component().ocupado).toBeFalse();
  });

  it('does not publish or restore when confirmation is cancelled', async () => {
    await load();
    await edit('titulo', 'Sin publicar');
    confirm.and.resolveTo({ confirmado: false, valor: null });
    await component().publicar();
    await component().restaurar(component().historial[1]);
    expect(component().borrador.titulo).toBe('Sin publicar');
    expect(component().cambios).toBe(1);
    http.expectNone(r => r.method === 'POST');
  });

  it('keeps the draft and history when restoring fails', async () => {
    await load();
    await edit('titulo', 'Borrador');
    const pending = component().restaurar(component().historial[1]);
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO/3/restaurar`).flush({}, { status: 500, statusText: 'Server error' });
    await pending;
    expect(notices.error).toHaveBeenCalledWith('No se pudo restaurar. Tus cambios siguen aquí.');
    expect(component().borrador.titulo).toBe('Borrador');
    expect(component().historial.length).toBe(2);
  });

  it('shows the preview fallback on failure', async () => {
    await load();
    const pending = component().previsualizar(button('Previsualizar'));
    http.expectOne(`${BASE}/TAEKWONDO/preview`).flush(new Blob(['failed']), { status: 500, statusText: 'Server error' });
    await pending;
    expect(notices.error).toHaveBeenCalledWith('No se pudo generar la vista previa. Inténtalo de nuevo.');
    expect(component().documentos).toEqual([]);
  });

  it('reads a server message from a preview JSON blob', async () => {
    await load();
    const pending = component().previsualizar(button('Previsualizar'));
    http.expectOne(`${BASE}/TAEKWONDO/preview`).flush(new Blob(['{"message":"PDF no disponible."}'], { type: 'application/json' }), { status: 500, statusText: 'Server error' });
    await pending;
    expect(notices.error).toHaveBeenCalledWith('PDF no disponible.');
  });

  it('asks before changing discipline and keeps the current draft on cancellation', async () => {
    await load();
    await edit('cabecera', 'Cambio');
    confirm.and.resolveTo({ confirmado: false, valor: null });
    await component().cambiarDisciplina('KICKBOXING');
    expect(confirm).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: '¿Descartar los cambios?',
      mensaje: 'Tienes cambios sin publicar en Taekwondo.', destructivo: true }));
    expect(component().disciplina).toBe('TAEKWONDO');
    expect(component().borrador.cabecera).toBe('Cambio');
    http.expectNone(`${BASE}/KICKBOXING`);
    confirm.and.resolveTo({ confirmado: true, valor: null });
    const pending = component().cambiarDisciplina('KICKBOXING');
    await settle();
    http.expectOne(`${BASE}/KICKBOXING`).flush({ activa: version(2), historial: [version(2)] });
    await pending;
    expect(component().disciplina).toBe('KICKBOXING');
    expect(component().cambios).toBe(0);
  });

  it('uses arrow keys and roving tabindex for discipline pills', async () => {
    await load();
    const pending = component().teclaDisciplinas(new KeyboardEvent('keydown', { key: 'ArrowRight', cancelable: true }));
    await settle();
    http.expectOne(`${BASE}/KICKBOXING`).flush({ activa: version(2), historial: [version(2)] });
    await pending;
    expect(root().querySelector('[role="radio"][aria-checked="true"]')!.getAttribute('tabindex')).toBe('0');
    expect(document.activeElement?.textContent).toContain('Kickboxing');
  });

  it('ignores a second discipline change while the first one is loading', async () => {
    await load();
    const first = component().cambiarDisciplina('KICKBOXING');
    const second = component().cambiarDisciplina('PILATES');
    await settle();
    const request = http.expectOne(`${BASE}/KICKBOXING`);
    http.expectNone(`${BASE}/PILATES`);
    request.flush({ activa: version(2), historial: [version(2)] });
    await Promise.all([first, second]);
    expect(component().disciplina).toBe('KICKBOXING');
  });

  it('shows managers read-only text with line breaks and no editing actions, but allows preview', async () => {
    admin = false;
    await load();
    expect(root().querySelector('input, textarea')).toBeNull();
    expect(root().querySelector('.lectura')!.textContent).toBe('Escuela');
    expect(root().querySelector('.normas li p')!.textContent).toBe(CONTENT.normas[0]);
    expect(buttons().some(b => /Publicar|Restaurar|Añadir|Quitar|Subir|Bajar/.test(b.textContent ?? '') || /norma/.test(b.getAttribute('aria-label') ?? ''))).toBeFalse();
    expect(button('Previsualizar').disabled).toBeFalse();
    expect(root().querySelector('.pendientes')).toBeNull();
    await component().publicar();
    await component().restaurar(component().historial[1]);
    http.expectNone(r => r.method === 'POST');
    const pending = component().previsualizar(button('Previsualizar'));
    const request = http.expectOne(`${BASE}/TAEKWONDO/preview`);
    expect(request.request.body).toEqual({ ...CONTENT, instrucciones: 'Después de firmar' });
    request.flush(new Blob(['pdf']));
    await pending;
  });

  it('shows admins a blank form with one norm and version 1 on 404', async () => {
    http.expectOne(`${BASE}/TAEKWONDO`).flush({}, { status: 404, statusText: 'Not found' });
    await settle();
    expect(root().textContent).toContain('Esta disciplina aún no tiene plantilla. Escribe los textos y publica la versión 1.');
    expect(root().querySelectorAll('.normas textarea').length).toBe(1);
    expect(component().cambios).toBe(0);
    // Sin versión activa no hay nada que comparar: la barra sigue a mano para publicar la versión 1.
    const bar = root().querySelector<HTMLElement>('.pendientes')!;
    expect(bar.querySelector('.pendientes-aviso')!.textContent!.trim()).toBe('Sin publicar');
    expect(bar.contains(button('Publicar versión 1'))).toBeTrue();
    expect(button('Publicar versión 1').disabled).toBeFalse();
    expect(button('Descartar')).toBeUndefined();
    await edit('cabecera', 'Escuela');
    expect(root().querySelector('.pendientes-aviso')!.textContent!.trim()).toBe('Cambios sin publicar');
    expect(button('Descartar')).toBeUndefined();
    Object.assign(component().borrador, { ...CONTENT, normas: [...CONTENT.normas], instrucciones: 'Después de firmar' });
    fixture.detectChanges();
    const pending = component().publicar();
    expect(confirm).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: '¿Publicar la versión 1 de Taekwondo?' }));
    await settle();
    http.expectOne(r => r.method === 'POST' && r.url === `${BASE}/TAEKWONDO`).flush(version(1));
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO`).flush({ activa: version(1), historial: [version(1)] });
    await pending;
    await settle();
    expect(root().querySelector('.pendientes')).toBeNull();
  });

  it('shows managers an empty state on 404', async () => {
    admin = false;
    http.expectOne(`${BASE}/TAEKWONDO`).flush({}, { status: 404, statusText: 'Not found' });
    await settle();
    expect(root().querySelector('pn-bloque-vacio')!.textContent).toContain('Esta disciplina aún no tiene plantilla.');
    expect(root().querySelector('input, textarea')).toBeNull();
    expect(button('Previsualizar').disabled).toBeTrue();
  });

  it('shows a retryable error on 500, not a blank form', async () => {
    http.expectOne(`${BASE}/TAEKWONDO`).flush({}, { status: 500, statusText: 'Server error' });
    await settle();
    expect(root().querySelector('pn-bloque-error')!.textContent).toContain('No se pudo cargar la plantilla.');
    expect(root().querySelector('input')).toBeNull();
    button('Reintentar').click();
    await load();
    expect(root().querySelector('pn-bloque-error')).toBeNull();
  });

  it('counts changed fields, norms as one, and resets on discard', async () => {
    await load();
    await edit('cabecera', 'Nueva');
    expect(component().cambios).toBe(1);
    expect(root().querySelector('.pendientes-aviso')!.textContent!.trim()).toBe('1 cambio sin publicar');
    await edit('titulo', 'Nuevo');
    component().borrador.normas = ['Una', 'Dos'];
    fixture.detectChanges();
    expect(component().cambios).toBe(3);
    expect(root().querySelector('.pendientes-aviso')!.textContent!.trim()).toBe('3 cambios sin publicar');
    expect(root().querySelector('.versiones')!.textContent).toContain('Borrador · sin publicar');
    component().borrador.cabecera = CONTENT.cabecera;
    expect(component().cambios).toBe(2);
    button('Descartar').click();
    await settle();
    expect(component().cambios).toBe(0);
    expect(component().borrador.normas).toEqual(CONTENT.normas);
    expect(component().borrador.titulo).toBe(CONTENT.titulo);
    expect(root().querySelector<HTMLInputElement>('#plantilla-titulo')!.value).toBe(CONTENT.titulo);
    expect(root().querySelector('.pendientes')).toBeNull();
    expect(root().querySelector('.versiones')!.textContent).not.toContain('Borrador · sin publicar');
  });

  it('always shows character counters, groups four digits and keeps them beside the error', async () => {
    await load();
    expect(root().querySelector('#ayuda-cabecera .contador')!.textContent).toBe('7 / 120');
    expect(root().querySelector('#ayuda-norma-0 .contador')!.textContent).toBe('27 / 2.000');
    await edit('consentimiento', 'a'.repeat(1201));
    expect(root().querySelector('#ayuda-consentimiento .contador')!.textContent).toBe('1.201 / 1.500');
    await edit('contacto', '');
    await component().publicar();
    fixture.detectChanges();
    const help = root().querySelector('#ayuda-contacto')!;
    expect(help.querySelector('.error')!.textContent).toBe('Escribe este texto.');
    expect(help.querySelector('.contador')!.textContent).toBe('0 / 180');
  });

  it('requires a norm, rejects oversized fields and keeps limits for add, remove and reorder', async () => {
    await load();
    component().quitarNorma(0);
    expect(component().borrador.normas.length).toBe(1);
    component().agregarNorma();
    component().borrador.normas[1] = 'Otra';
    component().moverNorma(1, -1);
    expect(component().borrador.normas[0]).toBe('Otra');
    component().borrador.normas = [];
    await component().publicar();
    expect(component().errores['normas']).toBe('Añade al menos una norma.');
    component().borrador.normas = Array(30).fill('Norma');
    component().agregarNorma();
    expect(component().borrador.normas.length).toBe(30);
    component().borrador.cabecera = 'a'.repeat(121);
    await component().publicar();
    expect(component().errores['cabecera']).toContain('120');
    http.expectNone(r => r.method === 'POST');
  });

  it('keeps beforeunload protection while the draft has changes', async () => {
    await load();
    const clean = new Event('beforeunload', { cancelable: true });
    component().antesDeSalir(clean as BeforeUnloadEvent);
    expect(clean.defaultPrevented).toBeFalse();
    await edit('titulo', 'Cambios');
    const dirty = new Event('beforeunload', { cancelable: true });
    component().antesDeSalir(dirty as BeforeUnloadEvent);
    expect(dirty.defaultPrevented).toBeTrue();
  });

  it('previews the edited texts, not the active version', async () => {
    await load();
    await edit('titulo', 'Título editado');
    await edit('norma-0', 'Norma editada');
    const pending = component().previsualizar(button('Previsualizar'));
    const request = http.expectOne(`${BASE}/TAEKWONDO/preview`);
    expect(request.request.body).toEqual({ ...CONTENT, titulo: 'Título editado', normas: ['Norma editada'], instrucciones: 'Después de firmar' });
    request.flush(new Blob(['pdf']));
    await pending;
  });

  it('leaves no pending changes after a successful restore', async () => {
    await load();
    await edit('titulo', 'Se pierde');
    const pending = component().restaurar(component().historial[1]);
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO/3/restaurar`).flush(version(5));
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO`).flush({ activa: version(5), historial: [version(5), version(), version(3)] });
    await pending;
    await settle();
    expect(component().cambios).toBe(0);
    expect(component().borrador.titulo).toBe(CONTENT.titulo);
    expect(root().querySelector('.pendientes')).toBeNull();
  });

  it('cannot go past 30 norms, and 31 norms fail validation without calling the API, focusing the list', async () => {
    await load();
    component().borrador.normas = Array(30).fill('Norma');
    fixture.detectChanges();
    const add = button('Añadir norma');
    expect(add.disabled).toBeTrue();
    add.click();
    component().agregarNorma();
    expect(component().borrador.normas.length).toBe(30);
    component().borrador.normas = Array(31).fill('Norma');
    fixture.detectChanges();
    await component().publicar();
    await component().previsualizar(button('Previsualizar'));
    fixture.detectChanges();
    expect(component().errores['normas']).toBe('No puedes añadir más de 30 normas.');
    const list = root().querySelector<HTMLElement>('ol.normas')!;
    expect(list.getAttribute('aria-invalid')).toBe('true');
    expect(list.getAttribute('aria-describedby')).toBe('error-normas');
    expect(root().querySelector('#error-normas')!.textContent).toContain('No puedes añadir más de 30 normas.');
    expect(document.activeElement).toBe(list);
    expect(confirm).not.toHaveBeenCalled();
    http.expectNone(r => r.method === 'POST');
  });

  it('sends a single POST when publish is clicked twice', async () => {
    await load();
    await edit('titulo', 'Una vez');
    const publish = button('Publicar versión 5');
    publish.click();
    publish.click();
    await settle();
    expect(confirm).toHaveBeenCalledTimes(1);
    const request = http.expectOne(r => r.method === 'POST' && r.url === `${BASE}/TAEKWONDO`);
    request.flush(version(5));
    await settle();
    http.expectOne(`${BASE}/TAEKWONDO`).flush({ activa: version(5), historial: [version(5), version()] });
    await settle();
  });

  it('returns focus to the enabled trigger when publish or restore is cancelled', async () => {
    await load();
    await edit('titulo', 'Sin publicar');
    confirm.and.resolveTo({ confirmado: false, valor: null });
    const publish = button('Publicar versión 5');
    publish.focus();
    await component().publicar();
    expect(publish.disabled).toBeFalse();
    expect(document.activeElement).toBe(publish);
    // El iframe de Karma puede ser más estrecho que 768 px, donde la columna de versiones se oculta.
    root().querySelector<HTMLElement>('.versiones-escritorio')!.style.display = 'block';
    const restore = button('Restaurar versión 3');
    restore.focus();
    expect(document.activeElement).toBe(restore);
    await component().restaurar(component().historial[1]);
    expect(restore.disabled).toBeFalse();
    expect(document.activeElement).toBe(restore);
  });

  it('handles a real beforeunload on window: blocks with changes, not without, nor after destroy', async () => {
    await load();
    // Karma escucha `window.onbeforeunload` para detectar recargas: se aparta mientras dura la prueba.
    const karma = window.onbeforeunload;
    window.onbeforeunload = null;
    const fire = () => {
      const event = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    };
    try {
      expect(fire()).toBeFalse();
      await edit('titulo', 'Cambios');
      expect(fire()).toBeTrue();
      fixture.destroy();
      expect(fire()).toBeFalse();
    } finally { window.onbeforeunload = karma; }
  });

  it('the leave guard asks with changes and lets you leave without them', async () => {
    await load();
    const guard = () => TestBed.runInInjectionContext(() =>
      salirSinGuardarGuard(component(), {} as ActivatedRouteSnapshot, {} as RouterStateSnapshot, {} as RouterStateSnapshot));
    expect(await guard()).toBeTrue();
    expect(confirm).not.toHaveBeenCalled();
    await edit('titulo', 'Cambios');
    confirm.and.resolveTo({ confirmado: false, valor: null });
    expect(await guard()).toBeFalse();
    expect(confirm).toHaveBeenCalledWith(jasmine.objectContaining({ titulo: '¿Descartar los cambios?' }));
    confirm.and.resolveTo({ confirmado: true, valor: null });
    expect(await guard()).toBeTrue();
  });
  describe('empty and loading states', () => {
    const SIN_PLANTILLA = 'aún no tiene plantilla';
    const vacio = () => fixture.debugElement.query(By.directive(BloqueVacioComponent))?.componentInstance as BloqueVacioComponent | undefined;

    it('does not show «sin plantilla» while the response is pending', async () => {
      await settle();
      expect(root().querySelector('pn-esqueleto')).not.toBeNull();
      expect(root().textContent).not.toContain(SIN_PLANTILLA);
      expect(root().querySelector('pn-bloque-vacio')).toBeNull();
      expect(root().querySelector('.sin-plantilla')).toBeNull();
      http.expectOne(`${BASE}/TAEKWONDO`).flush({}, { status: 404, statusText: 'Not found' });
      await settle();
    });

    it('shows the empty block with the invitar pose after a 404 for managers', async () => {
      admin = false;
      http.expectOne(`${BASE}/TAEKWONDO`).flush({}, { status: 404, statusText: 'Not found' });
      await settle();
      expect(vacio()!.pose).toBe('invitar');
      expect(root().querySelector('pn-bloque-vacio')!.textContent).toContain(SIN_PLANTILLA);
      expect(root().querySelectorAll('.sin-plantilla').length).toBe(0);
    });

    it('shows one single message to admins on a 404 (hint above the editor, no empty block)', async () => {
      http.expectOne(`${BASE}/TAEKWONDO`).flush({}, { status: 404, statusText: 'Not found' });
      await settle();
      expect(root().querySelector('pn-bloque-vacio')).toBeNull();
      expect(root().querySelectorAll('.sin-plantilla').length).toBe(1);
    });

    it('shows the editor and no empty state when the discipline has a template', async () => {
      await load();
      expect(root().querySelector('pn-bloque-vacio')).toBeNull();
      expect(root().textContent).not.toContain(SIN_PLANTILLA);
      expect(root().querySelector('#plantilla-cabecera')).not.toBeNull();
    });

    it('the last chosen discipline wins when the first response arrives late', async () => {
      await load();
      const first = component().cambiarDisciplina('KICKBOXING');
      await settle();
      const late = http.expectOne(`${BASE}/KICKBOXING`);
      component().disciplina = 'PILATES';
      const second = component().cargar();
      const latest = http.expectOne(`${BASE}/PILATES`);
      latest.flush({ activa: version(2), historial: [version(2)] });
      await second;
      late.flush({ activa: version(3), historial: [version(3)] });
      await first;
      await settle();
      expect(component().activa!.version).toBe(2);
      expect(component().estado).toBe('ready');
      expect(root().textContent).toContain('Versión 2');
    });

    it('shows the error block, never «sin plantilla», when loading fails', async () => {
      http.expectOne(`${BASE}/TAEKWONDO`).flush({}, { status: 500, statusText: 'Server error' });
      await settle();
      expect(root().querySelector('pn-bloque-error')).not.toBeNull();
      expect(root().querySelector('pn-bloque-vacio')).toBeNull();
      expect(root().textContent).not.toContain(SIN_PLANTILLA);
    });
  });
});

describe('PlantillasPdfComponent with the catalogue still loading', () => {
  it('waits for the catalogue (skeleton, no empty state) and then reads the first discipline', async () => {
    const respuesta = new Subject<unknown>();
    let catalogo!: CatalogoService;
    await TestBed.configureTestingModule({
      imports: [PlantillasPdfComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: CatalogoService, useFactory: () => catalogo },
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => true } },
        { provide: ConfirmacionService, useValue: { confirmar: () => Promise.resolve({ confirmado: true, valor: null }) } },
        { provide: AvisosService, useValue: jasmine.createSpyObj<AvisosService>('avisos', ['exito', 'error']) }],
    }).overrideComponent(PlantillasPdfComponent, {
      remove: { imports: [VisorPdfComponent] }, add: { imports: [ViewerStub] },
    }).compileComponents();
    catalogo = TestBed.runInInjectionContext(() => new CatalogoService({ get: () => respuesta } as unknown as HttpClient,
      { usuarioLogueadoCambio: of(false), emailCambio: of(null) } as unknown as AuthenticationService));
    catalogo.cargar().subscribe({ error: () => undefined });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PlantillasPdfComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('pn-esqueleto')).not.toBeNull();
    expect(root.querySelector('pn-bloque-vacio')).toBeNull();
    expect(root.textContent).not.toContain('aún no tiene plantilla');
    http.expectNone(r => r.url.startsWith(BASE));
    respuesta.next(CATALOGO_TAEMOI);
    respuesta.complete();
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();
    expect(root.querySelector('pn-bloque-vacio')).toBeNull();
    http.expectOne(`${BASE}/TAEKWONDO`).flush({ activa: version(), historial: [version()] });
    for (let i = 0; i < 8; i++) await Promise.resolve();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.textContent).toContain('Versión 4');
    expect(root.querySelector('pn-bloque-vacio')).toBeNull();
    fixture.destroy();
    http.verify();
  });
});
