import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal, { SweetAlertOptions, SweetAlertResult } from 'sweetalert2';

import { PermisosSesionService } from '../../../servicios/authentication/permisos-sesion.service';
import { Toast } from '../../../utils/toast.util';
import { AvisosService } from '../../avisos/avisos.service';
import { CONSULTA_HOJA } from '../../capas/capa-modal';
import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { estilosPanelGuard } from '../../estilos-panel.guard';
import { EstilosPanelService } from '../../estilos-panel.service';
import { DialogosFichaPanel, textoDeAviso } from './dialogos-ficha-panel';
import { itSinReloj } from '../../../testing/reloj-prueba';

@Component({ standalone: true, template: '<p>Pantalla nueva</p>' })
class PantallaNueva {}

/** El html de hoy de «Dar de baja o eliminar» (`eliminarODarDeBajaAlumno`). */
const BAJA_O_ELIMINAR: SweetAlertOptions = {
  title: '¿Qué deseas hacer?',
  html: `
          <p>Selecciona una opción para <strong>Rubén Prueba Ficticia</strong>:</p>
          <ul style="text-align: left; margin-top: 1rem;">
            <li><strong>Dar de baja:</strong> Desactiva todos los deportes activos y mantiene todos los datos (historial, grupos, productos, etc.)</li>
            <li><strong>Eliminar completamente:</strong> Elimina permanentemente al alumno y toda su información (irreversible)</li>
          </ul>
        `,
  icon: 'warning',
  showCancelButton: true,
  showDenyButton: true,
  confirmButtonText: 'Dar de baja',
  denyButtonText: 'Eliminar completamente',
  cancelButtonText: 'Cancelar',
  confirmButtonColor: '#ffa500',
  denyButtonColor: '#d33',
  cancelButtonColor: '#6c757d',
};

describe('DialogosFichaPanel: los diálogos de la ficha con las piezas del panel nuevo (8b-D2b)', () => {
  let panel: DialogosFichaPanel;
  let avisos: AvisosService;
  let swal: jasmine.Spy;
  let toast: jasmine.Spy;
  let disparador: HTMLButtonElement;
  let quitarTokens: () => void;

  const dialogo = () => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const esperar = () => new Promise<void>((r) => setTimeout(r));

  function boton(texto: string): HTMLButtonElement {
    const lista = Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('button'));
    const b = lista.find((x) => (x.getAttribute('aria-label') ?? x.textContent ?? '').replace(/\s+/g, ' ').trim() === texto);
    if (!b) throw new Error(`Sin botón «${texto}»: ${lista.map((x) => x.textContent?.trim()).join(' | ')}`);
    return b;
  }

  function abrir(opciones: SweetAlertOptions, id?: string): Promise<SweetAlertResult> {
    disparador.focus();
    const p = panel.fire(opciones, id);
    pintarCapas();
    return p;
  }

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

  beforeEach(() => {
    quitarTokens = ponerTokensDePrueba();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'alumnosEditar/:id', canMatch: [estilosPanelGuard], component: PantallaNueva }]),
        { provide: PermisosSesionService, useValue: { obtener: () => of({}) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
        DialogosFichaPanel,
      ],
    });
    panel = TestBed.inject(DialogosFichaPanel);
    avisos = TestBed.inject(AvisosService);
    // `Toast` (toast.util) hereda `fire` de Swal: se espía antes, para que tenga el suyo.
    toast = spyOn(Toast, 'fire').and.callThrough();
    swal = spyOn(Swal, 'fire').and.callThrough();
    disparador = document.createElement('button');
    disparador.textContent = 'Disparador';
    document.body.appendChild(disparador);
  });

  afterEach(() => {
    expect(swal).withContext('nunca SweetAlert2 en el panel nuevo').not.toHaveBeenCalled();
    expect(toast).withContext('nunca los toasts de SweetAlert2 en el panel nuevo').not.toHaveBeenCalled();
    avisos.cerrarTodos();
    quitarTokens();
    disparador.remove();
    document.querySelectorAll('pn-dialogo, pn-avisos').forEach((e) => e.remove());
  });

  describe('avisos', () => {
    itSinReloj('éxito con temporizador: aviso de éxito con la frase; se resuelve como aceptado sin esperar', async () => {
      const r = await panel.fire({ title: '¡Deporte agregado!', text: 'El deporte Kickboxing ha sido agregado exitosamente', icon: 'success', timer: 2000 });
      expect(avisos.lista().map((a) => [a.tipo, a.texto])).toEqual([['exito', 'El deporte Kickboxing ha sido agregado exitosamente']]);
      expect(r).toEqual(jasmine.objectContaining({ isConfirmed: true, isDenied: false, isDismissed: false }));
      expect(dialogo()).toBeNull();
    });

    itSinReloj('información: aviso de información', async () => {
      await panel.fire({ title: 'Información', text: 'El alumno ya está activo (tiene deportes activos)', icon: 'info' });
      expect(avisos.lista().map((a) => [a.tipo, a.texto])).toEqual([['info', 'El alumno ya está activo (tiene deportes activos)']]);
    });

    itSinReloj('error: aviso de error (role=alert, sin cierre solo); un título que no es «Error» va delante de la frase', async () => {
      await panel.fire({ title: 'Error', text: 'No se pudieron cargar las convocatorias', icon: 'error' });
      await panel.fire({ title: 'No se pudo subir el documento', text: 'El archivo supera 10 MB.', icon: 'error' });
      expect(avisos.lista().map((a) => [a.tipo, a.texto])).toEqual([
        ['error', 'No se pudieron cargar las convocatorias'],
        ['error', 'No se pudo subir el documento. El archivo supera 10 MB.'],
      ]);
    });

    itSinReloj('aviso sin cancelar con icono de advertencia: error, que no se cierra solo', async () => {
      await panel.fire({ title: 'No se puede eliminar', text: 'El alumno debe tener al menos un deporte asignado', icon: 'warning' });
      expect(avisos.lista().map((a) => [a.tipo, a.texto])).toEqual([
        ['error', 'No se puede eliminar. El alumno debe tener al menos un deporte asignado'],
      ]);
    });

    it('toasts de hoy (toastExito y toastError): avisos', () => {
      panel.toastExito('Grupo añadido');
      panel.toastError('No se pudo añadir al grupo');
      expect(avisos.lista().map((a) => [a.tipo, a.texto])).toEqual([
        ['exito', 'Grupo añadido'],
        ['error', 'No se pudo añadir al grupo'],
      ]);
    });

    it('textoDeAviso: frase sola, título solo o «Título. Frase»', () => {
      expect(textoDeAviso({ title: '¡Bien!', text: '¡Alumno actualizado correctamente!', icon: 'success' })).toBe('¡Alumno actualizado correctamente!');
      expect(textoDeAviso({ title: 'Reservas parciales', text: 'Completadas: 1, Errores: 1', icon: 'warning' })).toBe('Reservas parciales. Completadas: 1, Errores: 1');
      expect(textoDeAviso({ title: 'Sin telefono', icon: 'warning' })).toBe('Sin telefono');
      expect(textoDeAviso({ title: 'Error', text: '', icon: 'error' })).toBe('Error');
    });
  });

  describe('confirmaciones', () => {
    const ACTUALIZAR: SweetAlertOptions = {
      title: '¿Estás seguro?',
      text: 'El alumno se actualizará con la información introducida',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, actualizarlo',
      cancelButtonText: 'Cancelar',
    };

    itSinReloj('título, frase y botones de hoy; foco en la acción; aceptar da isConfirmed', async () => {
      anchoDeMovil(false);
      const p = abrir(ACTUALIZAR);
      const d = dialogo()!;
      expect(d.querySelector('h2')!.textContent!.trim()).toBe('¿Estás seguro?');
      expect(d.textContent).toContain('El alumno se actualizará con la información introducida');
      expect(Array.from(d.querySelectorAll('.pn-dialogo__pie button')).map((b) => b.textContent!.trim())).toEqual(['Cancelar', 'Sí, actualizarlo']);
      expect(d.classList).not.toContain('pn-dialogo--peligro');
      expect(document.activeElement).toBe(boton('Sí, actualizarlo'));
      boton('Sí, actualizarlo').click();
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: true, isDenied: false, isDismissed: false }));
      expect(document.activeElement).withContext('foco de vuelta al disparador').toBe(disparador);
    });

    itSinReloj('cancelar, Esc o la X: isDismissed con dismiss «cancel»', async () => {
      let p = abrir(ACTUALIZAR);
      boton('Cancelar').click();
      expect(await p).toEqual({ isConfirmed: false, isDenied: false, isDismissed: true, dismiss: 'cancel' as any });
      p = abrir(ACTUALIZAR);
      dialogo()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      expect((await p).isDismissed).toBeTrue();
    });

    itSinReloj('destructivo (eliminar, o rojo): acción en peligro y foco en «Cancelar»', async () => {
      const p = abrir({
        title: '¿Estás seguro?',
        text: 'Eliminarás este documento permanentemente',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminar',
        cancelButtonText: 'Cancelar',
      });
      expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
      expect(document.activeElement).toBe(boton('Cancelar'));
      expect(getComputedStyle(boton('Sí, eliminar')).backgroundColor).toBe('rgb(180, 35, 24)');
      boton('Cancelar').click();
      await p;
      const q = abrir({ title: '¿Eliminar deporte?', text: 'x', icon: 'warning', showCancelButton: true, confirmButtonText: 'Sí', confirmButtonColor: '#d33' });
      expect(dialogo()!.classList).withContext('rojo de hoy').toContain('pn-dialogo--peligro');
      boton('Cancelar').click();
      await q;
    });

    itSinReloj('desactivar (naranja, se puede deshacer) no es destructivo', async () => {
      const p = abrir({ title: '¿Desactivar deporte?', text: 'x', icon: 'question', showCancelButton: true, confirmButtonText: 'Sí, desactivar', confirmButtonColor: '#ffa500' });
      expect(dialogo()!.classList).not.toContain('pn-dialogo--peligro');
      boton('Cancelar').click();
      await p;
    });

    itSinReloj('«denegar» es la segunda acción del diálogo; html con negrita y lista; da isDenied', async () => {
      anchoDeMovil(false);
      const p = abrir(BAJA_O_ELIMINAR, 'baja-o-eliminar');
      const d = dialogo()!;
      expect(d.getAttribute('data-dialogo')).toBe('baja-o-eliminar');
      // La segunda acción a la izquierda; «Cancelar» y la acción, juntas a la derecha.
      expect(Array.from(d.querySelectorAll('.pn-dialogo__pie button')).map((b) => b.textContent!.trim())).toEqual([
        'Eliminar completamente',
        'Cancelar',
        'Dar de baja',
      ]);
      expect(d.querySelector('.pn-dialogo__mensaje strong')!.textContent).toBe('Rubén Prueba Ficticia');
      const items = Array.from(d.querySelectorAll('.pn-dialogo__lista li')).map((li) => li.textContent!.replace(/\s+/g, ' ').trim());
      expect(items.length).toBe(2);
      expect(items[0]).toMatch(/^Dar de baja: Desactiva todos los deportes/);
      expect(d.innerHTML).not.toContain('text-align');
      expect(document.activeElement).withContext('destructivo: foco en «Cancelar»').toBe(boton('Cancelar'));
      const negar = boton('Eliminar completamente');
      expect(getComputedStyle(negar).color).withContext('segunda acción en peligro').toBe('rgb(180, 35, 24)');
      negar.click();
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: false, isDenied: true, isDismissed: false }));
    });

    itSinReloj('«denegar» no destructivo (teléfono secundario): botón secundario', async () => {
      const p = abrir({
        title: 'Seleccionar telefono',
        text: 'A qué número deseas enviar el mensaje de WhatsApp?',
        icon: 'question',
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Principal: 600000000',
        denyButtonText: 'Secundario: 611111111',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#25D366',
        denyButtonColor: '#128C7E',
      });
      expect(document.activeElement).toBe(boton('Principal: 600000000'));
      expect(getComputedStyle(boton('Secundario: 611111111')).color).not.toBe('rgb(180, 35, 24)');
      boton('Secundario: 611111111').click();
      expect((await p).isDenied).toBeTrue();
    });
  });

  describe('campos', () => {
    const PARA_QUE_DEPORTE: SweetAlertOptions = {
      title: '¿Para qué deporte?',
      text: 'Temporada: 2026/2027',
      icon: 'question',
      input: 'select',
      inputOptions: { TODOS: 'Todos los deportes', TAEKWONDO: 'Taekwondo', KICKBOXING: 'Kickboxing' },
      inputPlaceholder: 'Selecciona un deporte',
      showCancelButton: true,
      confirmButtonText: 'Continuar',
      cancelButtonText: 'Cancelar',
      inputValidator: (value) => (value ? null : 'Debes seleccionar un deporte'),
    };

    itSinReloj('select de hasta 6 opciones: pastillas (radiogroup); la validación sale en línea bajo el campo, no en un aviso', async () => {
      const p = abrir(PARA_QUE_DEPORTE);
      const grupo = dialogo()!.querySelector<HTMLElement>('[role="radiogroup"]')!;
      expect(document.getElementById(grupo.getAttribute('aria-labelledby')!)!.textContent!.trim()).toBe('Selecciona un deporte');
      const radios = Array.from(grupo.querySelectorAll<HTMLButtonElement>('[role="radio"]'));
      expect(radios.map((r) => r.textContent!.trim())).toEqual(['Todos los deportes', 'Taekwondo', 'Kickboxing']);
      expect(radios.map((r) => r.getAttribute('aria-checked'))).toEqual(['false', 'false', 'false']);
      boton('Continuar').click();
      await esperar();
      pintarCapas();
      expect(dialogo()).withContext('sigue abierto').not.toBeNull();
      const error = document.getElementById(grupo.getAttribute('aria-describedby')!)!;
      expect(error.textContent!.trim()).toBe('Debes seleccionar un deporte');
      expect(grupo.getAttribute('aria-invalid')).toBe('true');
      expect(avisos.lista()).withContext('sin aviso').toEqual([]);
      radios[2].click();
      pintarCapas();
      boton('Continuar').click();
      const r = await p;
      expect(r).toEqual(jasmine.objectContaining({ isConfirmed: true, value: 'KICKBOXING' }));
    });

    itSinReloj('select de más de 6 opciones: un desplegable con el valor de hoy elegido; «Crear nueva» (denegar) da isDenied', async () => {
      const opciones: Record<string, string> = {};
      for (let i = 1; i <= 8; i++) opciones[String(i)] = `2026-0${i}-10`;
      let p = abrir({
        title: 'Selecciona convocatoria (Taekwondo)',
        input: 'select',
        inputOptions: opciones,
        inputPlaceholder: 'Selecciona una convocatoria',
        inputValue: '3',
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Añadir',
        denyButtonText: 'Crear nueva',
        cancelButtonText: 'Cancelar',
        inputValidator: (v) => (v ? null : 'Debes seleccionar una convocatoria'),
      });
      const select = dialogo()!.querySelector<HTMLSelectElement>('select')!;
      expect(select.value).toBe('3');
      expect(select.options[0].textContent!.trim()).toBe('Selecciona una convocatoria');
      expect(select.options.length).toBe(9);
      expect(document.querySelector(`label[for="${select.id}"]`)!.textContent!.trim()).toBe('Selecciona una convocatoria');
      select.value = '5';
      select.dispatchEvent(new Event('change'));
      boton('Añadir').click();
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: true, value: '5' }));
      p = abrir({ title: 'x', input: 'select', inputOptions: opciones, showCancelButton: true, showDenyButton: true, denyButtonText: 'Crear nueva' });
      boton('Crear nueva').click();
      expect((await p).isDenied).toBeTrue();
    });

    itSinReloj('radio con valor de hoy: pastilla elegida; aceptar da su valor', async () => {
      const p = abrir({
        title: 'Selecciona cinturon rojo',
        text: 'El alumno pasara a rojo en Taekwondo. Selecciona el tipo de cinturon.',
        input: 'radio',
        inputOptions: { normal: 'Rojo (sin bordar)', bordado: 'Rojo bordado' },
        inputValue: 'normal',
        showCancelButton: true,
        confirmButtonText: 'Continuar',
        cancelButtonText: 'Cancelar',
        inputValidator: (v) => (v ? null : 'Debes seleccionar una opcion'),
      });
      const radios = Array.from(dialogo()!.querySelectorAll('[role="radio"]'));
      expect(radios.map((r) => r.getAttribute('aria-checked'))).toEqual(['true', 'false']);
      boton('Continuar').click();
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: true, value: 'normal' }));
    });

    itSinReloj('fecha: campo con su etiqueta y el valor de hoy; vacío da el error en línea con el foco en el campo', async () => {
      const p = abrir({
        title: 'Nueva convocatoria (Taekwondo)',
        input: 'date',
        inputLabel: 'Fecha de la convocatoria',
        inputValue: '2026-10-22',
        showCancelButton: true,
        confirmButtonText: 'Crear y añadir',
        cancelButtonText: 'Cancelar',
        inputValidator: (value) => (value ? null : 'Debes seleccionar una fecha'),
      });
      const campo = dialogo()!.querySelector<HTMLInputElement>('input[type="date"]')!;
      expect(document.querySelector(`label[for="${campo.id}"]`)!.textContent!.trim()).toBe('Fecha de la convocatoria');
      expect(campo.value).toBe('2026-10-22');
      campo.value = '';
      campo.dispatchEvent(new Event('input'));
      boton('Crear y añadir').click();
      await esperar();
      pintarCapas();
      expect(campo.getAttribute('aria-invalid')).toBe('true');
      expect(document.getElementById(campo.getAttribute('aria-describedby')!)!.textContent!.trim()).toBe('Debes seleccionar una fecha');
      expect(document.activeElement).toBe(campo);
      campo.value = '2026-12-10';
      campo.dispatchEvent(new Event('input'));
      pintarCapas();
      expect(campo.getAttribute('aria-invalid')).withContext('al escribir se quita el error').toBeNull();
      boton('Crear y añadir').click();
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: true, value: '2026-12-10' }));
    });

    itSinReloj('textarea con sus atributos y validación', async () => {
      const p = abrir({
        title: 'Cancelar cargo',
        text: '«MATRÍCULA». No se borra.',
        icon: 'warning',
        input: 'textarea',
        inputLabel: 'Motivo',
        inputPlaceholder: 'Por qué se cancela este cargo',
        inputAttributes: { maxlength: '500' },
        showCancelButton: true,
        confirmButtonText: 'Cancelar cargo',
        cancelButtonText: 'Volver',
        inputValidator: (v: string) => ((v ?? '').trim() ? null : 'Indica el motivo de la cancelación'),
      });
      const area = dialogo()!.querySelector<HTMLTextAreaElement>('textarea')!;
      expect(area.getAttribute('maxlength')).toBe('500');
      expect(area.placeholder).toBe('Por qué se cancela este cargo');
      area.value = 'Baja médica';
      area.dispatchEvent(new Event('input'));
      boton('Cancelar cargo').click();
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: true, value: 'Baja médica' }));
    });

    itSinReloj('formulario html por id (dar de alta): pastillas con los mismos valores; preConfirm lee el DOM; el aviso de validación sale en línea', async () => {
      const opciones: SweetAlertOptions = {
        title: 'Selecciona el deporte a activar',
        html: `
        <p>El alumno tiene 2 deportes inactivos.</p>
        <p>¿Qué deporte deseas activar?</p>
        <select id="deporte-select" class="swal2-input" style="width: 80%;">
          <option value="">Selecciona un deporte...</option>
          <option value="TODOS">✓ Todos los deportes</option>
          <option value="TAEKWONDO">Taekwondo</option><option value="KICKBOXING">Kickboxing</option>
        </select>
      `,
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: 'Activar',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#28a745',
        cancelButtonColor: '#6c757d',
        didOpen: () => {
          throw new Error('didOpen es de SweetAlert2');
        },
        preConfirm: () => {
          const deporteSelect = document.getElementById('deporte-select') as HTMLSelectElement;
          if (!deporteSelect.value) {
            panel.showValidationMessage('Debes seleccionar un deporte');
            return false;
          }
          return deporteSelect.value;
        },
      };
      const p = abrir(opciones, 'alta-deporte');
      const d = dialogo()!;
      expect(d.querySelector('.pn-dialogo__mensaje')!.textContent!.trim()).toBe('El alumno tiene 2 deportes inactivos.');
      const grupo = d.querySelector<HTMLElement>('[role="radiogroup"]')!;
      expect(document.getElementById(grupo.getAttribute('aria-labelledby')!)!.textContent!.trim()).toBe('¿Qué deporte deseas activar?');
      const radios = Array.from(grupo.querySelectorAll<HTMLButtonElement>('[role="radio"]'));
      expect(radios.map((r) => r.textContent!.trim())).toEqual(['Todos los deportes', 'Taekwondo', 'Kickboxing']);
      boton('Activar').click();
      await esperar();
      pintarCapas();
      expect(dialogo()).not.toBeNull();
      expect(document.getElementById(grupo.getAttribute('aria-describedby')!)!.textContent!.trim()).toBe('Debes seleccionar un deporte');
      radios[0].click();
      pintarCapas();
      boton('Activar').click();
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: true, value: 'TODOS' }));
    });

    itSinReloj('acción larga (preConfirm con promesa): botón ocupado y Esc sin efecto hasta que termina', async () => {
      let soltar!: (v: string) => void;
      const p = abrir({
        title: 'Guardar',
        text: 'Se guarda.',
        showCancelButton: true,
        confirmButtonText: 'Guardar',
        showLoaderOnConfirm: true,
        preConfirm: () => new Promise<string>((r) => (soltar = r)),
      });
      boton('Guardar').click();
      pintarCapas();
      const accion = dialogo()!.querySelector<HTMLButtonElement>('.pn-dialogo__accion')!;
      expect(accion.getAttribute('aria-busy')).toBe('true');
      dialogo()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      dialogo()!.dispatchEvent(new Event('cancel', { cancelable: true }));
      boton('Cancelar').click();
      pintarCapas();
      expect(dialogo()).withContext('sigue abierto').not.toBeNull();
      soltar('hecho');
      expect(await p).toEqual(jasmine.objectContaining({ isConfirmed: true, value: 'hecho' }));
    });
  });

  describe('lo que no se puede traducir', () => {
    itSinReloj('un tipo de campo desconocido (file): confirmación genérica con el mismo título y frase, sin campo y sin SweetAlert2', async () => {
      const p = abrir({ title: 'Sube un fichero', text: 'Elige el documento.', input: 'file', showCancelButton: true, confirmButtonText: 'Subir' });
      const d = dialogo()!;
      expect(d.querySelector('h2')!.textContent!.trim()).toBe('Sube un fichero');
      expect(d.textContent).toContain('Elige el documento.');
      expect(d.querySelector('input, select, textarea')).toBeNull();
      expect(d.getAttribute('data-generico')).toBe('true');
      boton('Subir').click();
      expect((await p).isConfirmed).toBeTrue();
    });

    itSinReloj('«OK» de SweetAlert2 pasa a «Aceptar»', async () => {
      const p = abrir({ title: 'Seguro', input: 'range', showCancelButton: true });
      expect(boton('Aceptar')).toBeTruthy();
      boton('Aceptar').click();
      await p;
    });
  });
});
