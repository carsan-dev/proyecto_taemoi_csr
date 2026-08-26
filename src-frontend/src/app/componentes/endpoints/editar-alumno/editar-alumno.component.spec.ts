import { FormBuilder } from '@angular/forms';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { PageScrollSnapshot } from '../../../servicios/generales/page-scroll.service';
import { EditarAlumnoComponent } from './editar-alumno.component';

describe('EditarAlumnoComponent scroll continuity', () => {
  afterEach(() => Swal.close());

  it('reloads a saved student in the background and restores the active section', () => {
    const endpoints = {
      actualizarAlumno: jasmine.createSpy('actualizarAlumno').and.returnValue(of({})),
    };
    const snapshot: PageScrollSnapshot = {
      position: [0, 1450],
      anchor: { key: 'alumno-informacion-personal', viewportOffset: 90 },
    };
    const pageScroll = jasmine.createSpyObj('PageScrollService', [
      'captureSnapshot',
      'restoreSnapshotAfterNextRender',
    ]);
    pageScroll.captureSnapshot.and.returnValue(snapshot);
    pageScroll.restoreSnapshotAfterNextRender.and.returnValue(Promise.resolve(true));
    const component = new EditarAlumnoComponent(
      endpoints as any,
      {} as any,
      new FormBuilder(),
      {} as any,
      {} as any,
      {} as any,
      pageScroll
    );
    component.alumnoId = 7;
    component.alumno = {
      id: 7,
      nombre: 'Ana',
      apellidos: 'Díaz',
      direccion: 'Calle Uno',
      fechaNacimiento: '2000-01-01',
      nif: '12345678Z',
      email: 'ana@example.test',
      telefono: 600000000,
      telefono2: null,
      responsableLegalNombre: null,
      responsableLegalNif: null,
      tieneDiscapacidad: false,
      autorizacionWeb: true,
      fechaBaja: null,
      observaciones: '',
      tipoTarifa: 'GENERAL',
      fechaAlta: '2024-01-01',
      cuantiaTarifa: 30,
      rolFamiliar: null,
      grupoFamiliar: null,
    };
    component.pendingBasicInfoChanges = { nombre: 'Ana María' };
    component.editingBasicInfo = true;
    spyOn(component, 'cargarAlumno').and.callFake((_id, afterLoad) => afterLoad?.());

    component.applyBasicInfoChanges();

    expect(pageScroll.captureSnapshot).toHaveBeenCalledOnceWith('alumno-informacion-personal');
    expect(component.cargarAlumno).toHaveBeenCalled();
    expect(pageScroll.restoreSnapshotAfterNextRender).toHaveBeenCalledOnceWith(snapshot, 'auto');
    expect(component.editingBasicInfo).toBeFalse();
  });
});
