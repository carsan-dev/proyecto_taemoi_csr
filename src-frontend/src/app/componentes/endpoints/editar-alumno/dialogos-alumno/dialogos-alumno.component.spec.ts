import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';

import { proveerCatalogoTesting } from '../../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../../features/catalogo/testing/catalogo.fixtures';
import { EditarAlumnoComponent } from '../editar-alumno.component';
import { FotoAmpliadaComponent } from '../foto-ampliada/foto-ampliada.component';
import { DialogosAlumnoComponent } from './dialogos-alumno.component';

/**
 * Los diálogos de la ficha de hoy (convocatorias, WhatsApp, agregar deporte, actualizar grado y la foto ampliada) salen
 * a piezas compartidas para que la ficha nueva (8b-3b) los use sin copiarlos. Con la misma ficha detrás, el marcado es
 * el de hoy y cada botón llama al mismo método.
 */
describe('Diálogos de la ficha del alumno (piezas compartidas, 8b-3b)', () => {
  let ficha: EditarAlumnoComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DialogosAlumnoComponent, FotoAmpliadaComponent],
      providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)],
    });
    ficha = TestBed.runInInjectionContext(
      () => new EditarAlumnoComponent({} as any, {} as any, new FormBuilder(), {} as any, {} as any, {} as any, {} as any)
    );
    ficha.alumno = { id: 7, nombre: 'Rubén', apellidos: 'Prueba Ficticia', telefono: 600000000 };
    ficha.deportesDisponibles = ['PILATES'];
  });

  function montar(): ComponentFixture<DialogosAlumnoComponent> {
    const fixture = TestBed.createComponent(DialogosAlumnoComponent);
    fixture.componentRef.setInput('ficha', ficha);
    fixture.detectChanges();
    return fixture;
  }

  it('pinta los cinco diálogos de hoy con sus ids, cerrados', () => {
    const el = montar().nativeElement as HTMLElement;
    for (const id of ['convocatoriaModal', 'eliminarConvocatoriaModal', 'whatsappModal', 'agregarDeporteModal', 'actualizarGradoModal']) {
      const modal = el.querySelector(`#${id}.modal`);
      expect(modal).withContext(id).not.toBeNull();
      expect(modal!.classList.contains('show')).withContext(id).toBeFalse();
    }
  });

  it('sin alumno no pinta ninguno, como hoy', () => {
    ficha.alumno = null;
    const el = montar().nativeElement as HTMLElement;
    expect(el.querySelector('.modal')).toBeNull();
  });

  it('agregar deporte: se abre con la ficha y sus botones llaman a los métodos de la ficha', () => {
    const fixture = montar();
    const el = fixture.nativeElement as HTMLElement;
    ficha.abrirModalAgregarDeporte();
    fixture.detectChanges();
    expect(el.querySelector('#agregarDeporteModal')!.classList.contains('show')).toBeTrue();
    const agregar = spyOn(ficha, 'agregarDeporte');
    const botones = Array.from(el.querySelectorAll<HTMLButtonElement>('#agregarDeporteModal .modal-footer button'));
    expect(botones.map((b) => b.textContent!.trim())).toEqual(['Cancelar', 'Agregar Deporte']);
    botones[1].click();
    expect(agregar).toHaveBeenCalled();
    botones[0].click();
    fixture.detectChanges();
    expect(el.querySelector('#agregarDeporteModal')!.classList.contains('show')).toBeFalse();
  });

  it('actualizar grado y WhatsApp: mismos botones que hoy', () => {
    const fixture = montar();
    const el = fixture.nativeElement as HTMLElement;
    const actualizar = spyOn(ficha, 'actualizarGradoDeporte');
    ficha.deporteParaActualizarGrado = 'TAEKWONDO';
    ficha.nuevoGradoActualizar = 'AMARILLO';
    ficha.fechaGradoActualizar = '2026-10-22';
    ficha.mostrarModalActualizarGrado = true;
    fixture.detectChanges();
    el.querySelectorAll<HTMLButtonElement>('#actualizarGradoModal .modal-footer button')[1].click();
    expect(actualizar).toHaveBeenCalled();
    const enviar = spyOn(ficha, 'enviarWhatsappConDocumentos');
    ficha.whatsappTelefonoSeleccionado = 600000000;
    ficha.mostrarModalWhatsapp = true;
    fixture.detectChanges();
    expect(el.querySelector('#whatsappModal')!.classList.contains('show')).toBeTrue();
    el.querySelectorAll<HTMLButtonElement>('#whatsappModal .modal-footer button')[1].click();
    expect(enviar).toHaveBeenCalled();
  });

  it('foto ampliada: el <dialog> de hoy con su id y se cierra con la ficha', () => {
    const fixture = TestBed.createComponent(FotoAmpliadaComponent);
    fixture.componentRef.setInput('ficha', ficha);
    fixture.detectChanges();
    const dialogo = (fixture.nativeElement as HTMLElement).querySelector('dialog#imageModal.modal')!;
    expect(dialogo).not.toBeNull();
    expect(dialogo.querySelector('img#imgAmpliada')).not.toBeNull();
    const cerrar = spyOn(ficha, 'cerrarModal');
    dialogo.querySelector<HTMLButtonElement>('button.close')!.click();
    expect(cerrar).toHaveBeenCalled();
  });
});
