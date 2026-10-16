// Cancelación con motivo: el modal envía lo escrito (o vacío, y el backend aplica el motivo por defecto),
// «Volver» no llama a la API y la ficha de una solicitud cancelada muestra el motivo guardado.
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';
import {
  MOTIVO_CANCELACION_MAX,
  MOTIVO_CANCELACION_POR_DEFECTO,
  PreinscripcionService,
} from '../../../features/preinscripcion/preinscripcion.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';

function solicitud(extra: Record<string, unknown> = {}) {
  return {
    referencia: 'PRE-1', nombre: 'Ana', apellidos: 'García López', dni: '12345678Z', deporte: 'TAEKWONDO',
    estado: 'PENDIENTE', email: 'familia@example.com', telefono: '612345678', grupo: 'Lunes 18:00',
    grupoOriginal: 'Lunes 18:00', turnosSolicitados: [], alumnosCoincidentes: [],
    estadoEmailCambioTurnos: 'NO_ENVIADO', emailCambioTurnosIntentos: 0,
    estadoEmailFinalizacion: 'NO_ENVIADO', emailFinalizacionIntentos: 0,
    ...extra,
  };
}

describe('PreinscripcionesAdminComponent · cancelación con motivo', () => {
  let api: any;

  beforeEach(() => {
    api = {
      cancelar: jasmine.createSpy('cancelar').and.returnValue(of(void 0)),
      listar: jasmine.createSpy('listar').and.returnValue(of({ content: [], page: 0, totalElements: 0, totalPages: 0 })),
      firma: jasmine.createSpy('firma').and.returnValue(of(new Blob())),
    };
    TestBed.configureTestingModule({
      imports: [PreinscripcionesAdminComponent],
      providers: [
        proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: PreinscripcionService, useValue: api },
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      ],
    });
  });

  function crear() {
    const fixture = TestBed.createComponent(PreinscripcionesAdminComponent);
    fixture.detectChanges();
    return fixture;
  }

  describe('modal', () => {
    it('pide el motivo con el texto, la etiqueta, el placeholder y los botones aprobados', async () => {
      const alerta = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false } as any);
      const component = crear().componentInstance;
      component.seleccion = solicitud();

      await component.cancelar();

      const opciones = alerta.calls.mostRecent().args[0] as any;
      expect(opciones.title).toBe('¿Cancelar preinscripción?');
      expect(opciones.text).toBe('Se cancelará la solicitud PRE-1 y se avisará a la familia por correo.');
      expect(opciones.input).toBe('textarea');
      expect(opciones.inputLabel).toBe('Motivo (opcional)');
      expect(opciones.inputPlaceholder).toBe('No ha sido posible confirmar tu plaza en esta ocasión.');
      expect(opciones.inputPlaceholder).toBe(MOTIVO_CANCELACION_POR_DEFECTO);
      expect(opciones.inputAttributes.maxlength).toBe(String(MOTIVO_CANCELACION_MAX));
      expect(MOTIVO_CANCELACION_MAX).toBe(500);
      expect(opciones.confirmButtonText).toBe('Cancelar y avisar');
      expect(opciones.cancelButtonText).toBe('Volver');
      expect(opciones.confirmButtonColor).toBe('#a52229');
      expect(opciones.showCancelButton).toBeTrue();
    });

    it('envía el motivo escrito tal cual', async () => {
      spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true, value: '  El grupo se ha cerrado.  ' } as any);
      const component = crear().componentInstance;
      component.seleccion = solicitud();

      await component.cancelar();

      expect(api.cancelar).toHaveBeenCalledOnceWith('PRE-1', '  El grupo se ha cerrado.  ');
    });

    it('envía vacío si no se escribe motivo (el backend aplica el de por defecto)', async () => {
      spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true, value: '' } as any);
      const component = crear().componentInstance;
      component.seleccion = solicitud();

      await component.cancelar();

      expect(api.cancelar).toHaveBeenCalledOnceWith('PRE-1', '');
    });

    it('«Volver» no llama a la API', async () => {
      spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false, isDismissed: true, dismiss: 'cancel' } as any);
      const component = crear().componentInstance;
      component.seleccion = solicitud();

      await component.cancelar();

      expect(api.cancelar).not.toHaveBeenCalled();
    });

    it('añade la nota del motivo por defecto con el correo de destino y un contador', async () => {
      const alerta = spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false } as any);
      const component = crear().componentInstance;
      component.seleccion = solicitud({ email: '<b>familia@example.com</b>' });
      await component.cancelar();
      const opciones = alerta.calls.mostRecent().args[0] as any;
      const popup = document.createElement('div');
      const textarea = document.createElement('textarea');
      textarea.className = 'swal2-textarea';
      popup.appendChild(textarea);

      opciones.didOpen(popup);

      const nota = popup.querySelector('.swal2-motivo-ayuda p') as HTMLElement;
      const contador = popup.querySelector('.swal2-motivo-ayuda span') as HTMLElement;
      expect(nota.textContent).toContain('Si lo dejas vacío se enviará el motivo por defecto.');
      expect(nota.textContent).toContain('<b>familia@example.com</b>');
      expect(nota.querySelector('b')).toBeNull();
      expect(textarea.getAttribute('aria-describedby')).toBe(nota.id);
      expect(contador.textContent).toBe('0/500');
      textarea.value = 'Grupo cerrado';
      textarea.dispatchEvent(new Event('input'));
      expect(contador.textContent).toBe('13/500');
    });
  });

  describe('ficha', () => {
    function ficha(seleccion: Record<string, unknown>) {
      const fixture = crear();
      fixture.componentInstance.seleccion = solicitud(seleccion);
      fixture.detectChanges();
      const dl = fixture.nativeElement.querySelector('.detail-panel dl') as HTMLElement;
      const pares = new Map<string, string>();
      dl.querySelectorAll('dt').forEach((dt) => pares.set(dt.textContent!.trim(), dt.nextElementSibling!.textContent!.trim()));
      return pares;
    }

    it('muestra el motivo y el aviso de una solicitud cancelada', () => {
      const pares = ficha({
        estado: 'CANCELADA', motivoCancelacion: 'El grupo se ha cerrado.',
        estadoEmailCancelacion: 'ERROR', emailCancelacionIntentos: 1, emailCancelacionUltimoError: 'SMTP no disponible',
      });

      expect(pares.get('Motivo de cancelación')).toBe('El grupo se ha cerrado.');
      expect(pares.get('Aviso de cancelación')).toBe('ERROR · 1 intento(s)');
      expect(pares.get('Error del aviso de cancelación')).toBe('SMTP no disponible');
    });

    it('no muestra motivo ni aviso de cancelación en solicitudes no canceladas', () => {
      const pares = ficha({ estado: 'PENDIENTE', motivoCancelacion: 'resto antiguo', estadoEmailCancelacion: 'NO_ENVIADO' });

      expect(pares.has('Motivo de cancelación')).toBeFalse();
      expect(pares.has('Aviso de cancelación')).toBeFalse();
    });
  });
});
