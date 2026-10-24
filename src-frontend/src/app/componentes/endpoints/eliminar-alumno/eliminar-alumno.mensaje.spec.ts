import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { EliminarAlumnoComponent, mensajeDelServidor } from './eliminar-alumno.component';

/**
 * 8b-3l (P-4, decisión 285): la pantalla de hoy sigue igual, pero si el servidor rechaza el borrado (el gestor ya no
 * puede eliminar) enseña su motivo en lugar del texto genérico.
 */
describe('EliminarAlumnoComponent: motivo del servidor al eliminar (8b-3l)', () => {
  const MOTIVO = 'Solo el propietario puede eliminar alumnos definitivamente.';
  let endpoints: jasmine.SpyObj<EndpointsService>;
  let fire: jasmine.Spy;

  beforeEach(() => {
    endpoints = jasmine.createSpyObj('EndpointsService', ['obtenerAlumnos', 'eliminarAlumnos', 'darDeBajaAlumno']);
    endpoints.obtenerAlumnos.and.returnValue(of({ content: [], totalPages: 0 }));
    fire = spyOn(Swal, 'fire').and.returnValue(Promise.resolve({ isConfirmed: true } as any));
    TestBed.configureTestingModule({
      imports: [EliminarAlumnoComponent],
      providers: [
        { provide: EndpointsService, useValue: endpoints },
        { provide: AlumnoService, useValue: {} },
      ],
    });
  });

  afterEach(() => sessionStorage.removeItem('eliminarAlumnoEstado'));

  async function tras(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('eliminar uno: el texto del servidor si lo da; si no, el de hoy', async () => {
    const componente = TestBed.createComponent(EliminarAlumnoComponent).componentInstance;
    endpoints.eliminarAlumnos.and.returnValue(throwError(() => ({ status: 403, error: { mensaje: MOTIVO } })));
    componente.eliminarAlumno(7);
    await tras();
    expect(fire.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ title: 'Error al eliminar alumno', text: MOTIVO }));

    endpoints.eliminarAlumnos.and.returnValue(throwError(() => ({ status: 500, error: null })));
    componente.eliminarAlumno(7);
    await tras();
    expect(fire.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({ text: 'Ha ocurrido un error al intentar eliminar al alumno.' })
    );
  });

  it('eliminar en lote: el texto de hoy con el nombre y, detrás, el motivo del servidor', () => {
    const componente = TestBed.createComponent(EliminarAlumnoComponent).componentInstance;
    endpoints.eliminarAlumnos.and.returnValue(throwError(() => ({ status: 403, error: { mensaje: MOTIVO } })));
    componente.eliminarAlumnoSecuencial([{ id: 7, nombre: 'Ana', apellidos: 'Pérez' }]);
    const textos = fire.calls.allArgs().map((a) => (a[0] as any).text);
    expect(textos).toContain(`Ha ocurrido un error al intentar eliminar al alumno Ana Pérez. ${MOTIVO}`);
  });

  it('mensajeDelServidor: texto plano, «mensaje», «message» o nada', () => {
    expect(mensajeDelServidor({ error: ' Texto ' })).toBe('Texto');
    expect(mensajeDelServidor({ error: { mensaje: MOTIVO } })).toBe(MOTIVO);
    expect(mensajeDelServidor({ error: { message: 'Otro' } })).toBe('Otro');
    expect(mensajeDelServidor({ error: {} })).toBeNull();
    expect(mensajeDelServidor(null)).toBeNull();
  });
});
