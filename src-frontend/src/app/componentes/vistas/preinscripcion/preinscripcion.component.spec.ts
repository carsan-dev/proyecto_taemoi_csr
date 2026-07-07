import { of, throwError } from 'rxjs';
import { PreinscripcionComponent } from './preinscripcion.component';

describe('PreinscripcionComponent normas', () => {
  function crear(configuracion: unknown) {
    const api = {
      temporada: () => of({ temporada: '2026/2027' }),
      grupos: () => of([]),
      configuracion: () => configuracion instanceof Error ? throwError(() => configuracion) : of(configuracion)
    };
    const route = { snapshot: { queryParamMap: { get: () => null } } };
    return new PreinscripcionComponent(api as any, route as any);
  }

  it('muestra normas recibidas como objeto y habilita su aceptación', () => {
    const component = crear({ deporte: 'PILATES', version: 1, contenido: { normas: ['Norma uno'], importes: 'Pago presencial' } });
    component.form.controls.deporte.setValue('PILATES');
    component.cambiarDeporte();
    expect(component.configuracion?.contenido.normas).toEqual(['Norma uno']);
    expect(component.form.controls.aceptacionNormas.enabled).toBeTrue();
  });

  it('normaliza contenido JSON serializado', () => {
    const component = crear({ deporte: 'TAEKWONDO', version: 1, contenido: '{"normas":["Norma uno"]}' });
    component.form.controls.deporte.setValue('TAEKWONDO');
    component.cambiarDeporte();
    expect(component.normasDisponibles).toBeTrue();
  });

  it('mantiene aceptación y envío bloqueados cuando falla la carga', () => {
    const component = crear(new Error('fallo'));
    component.form.controls.deporte.setValue('KICKBOXING');
    component.cambiarDeporte();
    expect(component.errorNormas).toContain('No se pudieron cargar');
    expect(component.form.controls.aceptacionNormas.disabled).toBeTrue();
    expect(component.normasDisponibles).toBeFalse();
  });
});
