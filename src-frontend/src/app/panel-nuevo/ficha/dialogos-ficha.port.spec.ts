import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { Toast } from '../../utils/toast.util';
import { DIALOGOS_FICHA, DIALOGOS_SWEETALERT, DialogosFichaPort } from './dialogos-ficha.port';
import { FichaAlumnoBase } from './ficha-alumno-base';

/**
 * 8b-D2b: los diálogos de la ficha pasan por un puerto (`DIALOGOS_FICHA`) cuya implementación por defecto es
 * SweetAlert2 tal cual: la ficha de hoy abre los mismos SweetAlert2, con el mismo objeto de opciones, que antes.
 */
describe('DIALOGOS_FICHA: por defecto, SweetAlert2 con las mismas opciones (8b-D2b)', () => {
  let swal: jasmine.Spy;
  let toast: jasmine.Spy;
  let puerto: DialogosFichaPort;
  let fire: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    // `Toast` (toast.util) hereda `fire` de Swal: se espía antes, para que tenga el suyo.
    toast = spyOn(Toast, 'fire');
    swal = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: false, isDenied: false, isDismissed: true }) as any);
    puerto = TestBed.inject(DIALOGOS_FICHA);
    fire = spyOn(puerto, 'fire').and.callThrough();
  });

  /** El objeto que recibió el puerto es el mismo (no una copia) que recibe `Swal.fire`, y solo ese argumento. */
  function mismoObjeto(): any {
    expect(fire).toHaveBeenCalledTimes(1);
    expect(swal).toHaveBeenCalledTimes(1);
    expect(swal.calls.mostRecent().args.length).withContext('Swal.fire con un solo argumento').toBe(1);
    expect(swal.calls.mostRecent().args[0]).toBe(fire.calls.mostRecent().args[0]);
    return swal.calls.mostRecent().args[0];
  }

  function ficha(): any {
    const endpoints = jasmine.createSpyObj('EndpointsService', ['eliminarDocumentoAlumno', 'reservarPlazaPorDeporte']);
    const alumnos = jasmine.createSpyObj('AlumnoService', ['establecerDeportePrincipal']);
    alumnos.establecerDeportePrincipal.and.returnValue(of({}));
    const c = TestBed.runInInjectionContext(
      () => new FichaAlumnoBase(endpoints, alumnos, new FormBuilder(), {} as any, {} as any, {} as any, {} as any)
    ) as any;
    c.alumnoId = 7;
    c.alumno = { id: 7, nombre: 'Rubén', apellidos: 'Prueba Ficticia' };
    return c;
  }

  it('la implementación por defecto es la de SweetAlert2 y es la misma en toda la aplicación', () => {
    expect(puerto).toBe(DIALOGOS_SWEETALERT);
  });

  it('fire: llama a Swal.fire con el objeto idéntico e ignora el id del diálogo', async () => {
    const opciones = { title: 'Hola', text: 'Mundo', icon: 'info' as const };
    const resultado = await puerto.fire(opciones, 'un-id');
    expect(swal).toHaveBeenCalledOnceWith(opciones);
    expect(swal.calls.mostRecent().args[0]).toBe(opciones);
    expect(resultado).toEqual({ isConfirmed: false, isDenied: false, isDismissed: true });
  });

  it('close y showValidationMessage: los de SweetAlert2', () => {
    const cerrar = spyOn(Swal, 'close');
    const validar = spyOn(Swal, 'showValidationMessage');
    puerto.close();
    puerto.showValidationMessage('Debes seleccionar un deporte');
    expect(cerrar).toHaveBeenCalledTimes(1);
    expect(validar).toHaveBeenCalledOnceWith('Debes seleccionar un deporte');
  });

  it('toastExito y toastError: los toasts de hoy (toast.util)', () => {
    puerto.toastExito('Grupo añadido');
    puerto.toastError('No se pudo añadir al grupo');
    expect(toast.calls.allArgs() as unknown[]).toEqual([
      [{ icon: 'success', title: 'Grupo añadido' }],
      [{ icon: 'error', title: 'No se pudo añadir al grupo' }],
    ]);
  });

  describe('la ficha de hoy, por categoría de diálogo', () => {
    it('aviso informativo (sin deportes que agregar)', () => {
      const c = ficha();
      spyOn(c, 'getDeportesDisponiblesParaAgregar').and.returnValue([]);
      c.abrirModalAgregarDeporte();
      expect(mismoObjeto()).toEqual({
        title: 'No hay deportes disponibles',
        text: 'El alumno ya está inscrito en todos los deportes disponibles',
        icon: 'info',
      });
    });

    it('error (descarga sin datos)', () => {
      const c = ficha();
      c.alumno = null;
      c.alumnoId = null;
      c.descargarDocumento({});
      expect(mismoObjeto()).toEqual({
        icon: 'error',
        title: 'No se pudo descargar',
        text: 'Falta la información necesaria del documento o del alumno.',
      });
    });

    it('confirmación (eliminar documento)', () => {
      ficha().eliminarDocumento(900);
      expect(mismoObjeto()).toEqual({
        title: '¿Estás seguro?',
        text: 'Eliminarás este documento permanentemente',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminar',
        cancelButtonText: 'Cancelar',
      });
    });

    it('confirmación con «denegar» y html (dar de baja o eliminar): el id va al puerto, no a SweetAlert2', () => {
      const c = ficha();
      c.deportesDelAlumno = [{ deporte: 'TAEKWONDO', activo: true }];
      c.eliminarODarDeBajaAlumno();
      expect(fire.calls.mostRecent().args[1]).toBe('baja-o-eliminar');
      const o = mismoObjeto();
      expect({ ...o, html: undefined }).toEqual({
        title: '¿Qué deseas hacer?',
        html: undefined,
        icon: 'warning',
        showCancelButton: true,
        showDenyButton: true,
        confirmButtonText: 'Dar de baja',
        denyButtonText: 'Eliminar completamente',
        cancelButtonText: 'Cancelar',
        confirmButtonColor: '#ffa500',
        denyButtonColor: '#d33',
        cancelButtonColor: '#6c757d',
      });
      expect(o.html).toContain('<strong>Rubén Prueba Ficticia</strong>');
    });

    it('selector (¿para qué deporte?) con su validación', () => {
      const c = ficha();
      c.deportesDelAlumno = [{ deporte: 'TAEKWONDO', activo: true }, { deporte: 'KICKBOXING', activo: true }];
      c.reservarPlaza(7);
      const anio = new Date().getFullYear();
      const o = mismoObjeto();
      expect(o).toEqual({
        title: '¿Para qué deporte?',
        text: `Temporada: ${anio}/${anio + 1}`,
        icon: 'question',
        input: 'select',
        inputOptions: { TODOS: 'Todos los deportes', TAEKWONDO: 'Taekwondo', KICKBOXING: 'Kickboxing' },
        inputPlaceholder: 'Selecciona un deporte',
        showCancelButton: true,
        confirmButtonText: 'Continuar',
        cancelButtonText: 'Cancelar',
        inputValidator: jasmine.any(Function),
      });
      expect(o.inputValidator('')).toBe('Debes seleccionar un deporte');
      expect(o.inputValidator('TODOS')).toBeNull();
    });

    it('fecha (nueva convocatoria)', () => {
      const c = ficha();
      c.abrirSwalCrearConvocatoria('TAEKWONDO');
      const o = mismoObjeto();
      expect(o).toEqual({
        title: 'Nueva convocatoria (Taekwondo)',
        input: 'date',
        inputLabel: 'Fecha de la convocatoria',
        inputValue: c.formatDateForInput(new Date()),
        showCancelButton: true,
        confirmButtonText: 'Crear y añadir',
        cancelButtonText: 'Cancelar',
        inputValidator: jasmine.any(Function),
      });
      expect(o.inputValidator('')).toBe('Debes seleccionar una fecha');
    });

    it('formulario html (dar de alta): preConfirm lee el DOM y avisa con showValidationMessage de SweetAlert2', () => {
      const c = ficha();
      c.deportesDelAlumno = [{ deporte: 'TAEKWONDO', activo: false }, { deporte: 'KICKBOXING', activo: false }];
      c.darDeAltaAlumno();
      expect(fire.calls.mostRecent().args[1]).toBe('alta-deporte');
      const o = mismoObjeto();
      expect(o.html).toContain('<select id="deporte-select" class="swal2-input" style="width: 80%;">');
      expect(o.confirmButtonText).toBe('Activar');
      const validar = spyOn(Swal, 'showValidationMessage');
      const select = document.createElement('select');
      select.id = 'deporte-select';
      select.innerHTML = '<option value="">Selecciona un deporte...</option><option value="TODOS">Todos</option>';
      document.body.appendChild(select);
      try {
        expect(o.preConfirm()).toBeFalse();
        expect(validar).toHaveBeenCalledOnceWith('Debes seleccionar un deporte');
        select.value = 'TODOS';
        expect(o.preConfirm()).toBe('TODOS');
      } finally {
        select.remove();
      }
    });

    it('toast (marcar principal): el toast de hoy', async () => {
      const c = ficha();
      spyOn(c, 'cargarDeportesDelAlumno').and.stub();
      c.deportesDelAlumno = [{ deporte: 'TAEKWONDO', activo: true, principal: true }, { deporte: 'KICKBOXING', activo: true }];
      c.marcarDeportePrincipal('KICKBOXING');
      expect(toast).toHaveBeenCalledOnceWith({ icon: 'success', title: 'Deporte principal actualizado a Kickboxing' } as any);
      expect(swal).not.toHaveBeenCalled();
    });
  });

  describe('nadie llama a SweetAlert2 directamente salvo el puerto por defecto', () => {
    // El código emitido de cada clase (también en el build de los specs): una llamada directa a SweetAlert2 o a los
    // toasts de toast.util dejaría el nombre del módulo importado («sweetalert2», «toast_util») o «Swal.».
    const DIRECTO = /sweetalert2|\bSwal\.|toast_util|showSuccessToast|showErrorToast/;

    it('FichaAlumnoBase', () => {
      expect(FichaAlumnoBase.toString()).not.toMatch(DIRECTO);
    });

    it('el puerto por defecto sí llama a SweetAlert2', () => {
      expect(DIALOGOS_SWEETALERT.fire.toString() + DIALOGOS_SWEETALERT.close.toString()).toMatch(/sweetalert2|Swal/);
    });
  });
});
