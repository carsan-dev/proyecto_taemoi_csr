// Caracterización del comportamiento actual (Taemoi). Si cambia, debe ser una decisión explícita.
import { TestBed } from '@angular/core/testing';
import { MaterialesExamenUserComponent } from './materiales-examen-user.component';
import { proveerCatalogoTesting } from '../../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../../features/catalogo/testing/catalogo.fixtures';

describe('materiales-examen-user etiquetasSiguienteGrado (caracterización)', () => {
  // Se instancia sin render: solo se ejecutan los inicializadores de campos (en un contexto de inyección).
  let componente: {
    etiquetasSiguienteGrado: Record<string, string>;
    obtenerEtiquetaCinturonObjetivo(g: string | null | undefined): string | null;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    componente = TestBed.runInInjectionContext(
      () => new MaterialesExamenUserComponent({} as never)
    ) as unknown as typeof componente;
  });

  it('tabla exacta (claves en orden y valores)', () => {
    expect(Object.entries(componente.etiquetasSiguienteGrado)).toEqual([
      ['BLANCO_AMARILLO', 'BLANCO-AMARILLO'],
      ['AMARILLO', 'AMARILLO'],
      ['AMARILLO_NARANJA', 'AMARILLO-NARANJA'],
      ['NARANJA', 'NARANJA'],
      ['NARANJA_VERDE', 'NARANJA-VERDE'],
      ['VERDE', 'VERDE'],
      ['VERDE_AZUL', 'VERDE-AZUL'],
      ['AZUL', 'AZUL'],
      ['AZUL_ROJO', 'AZUL-ROJO'],
      ['ROJO', 'ROJO'],
      ['ROJO_NEGRO_1_PUM', 'NEGRO 1º PUM'],
      ['ROJO_NEGRO_2_PUM', 'NEGRO 2º PUM'],
      ['ROJO_NEGRO_3_PUM', 'NEGRO 3º PUM'],
      ['NEGRO_1_DAN', 'NEGRO 1º DAN'],
      ['NEGRO_2_DAN', 'NEGRO 2º DAN'],
      ['NEGRO_3_DAN', 'NEGRO 3º DAN'],
      ['NEGRO_4_DAN', 'NEGRO 4º DAN'],
      ['NEGRO_5_DAN', 'NEGRO 5º DAN'],
    ]);
  });

  it('obtenerEtiquetaCinturonObjetivo: conocidos, desconocidos y vacíos', () => {
    expect(componente.obtenerEtiquetaCinturonObjetivo('AZUL_ROJO')).toBe('AZUL-ROJO');
    expect(componente.obtenerEtiquetaCinturonObjetivo('NEGRO_2_DAN')).toBe('NEGRO 2º DAN');
    expect(componente.obtenerEtiquetaCinturonObjetivo('BLANCO')).toBeNull(); // no hay etiqueta para BLANCO
    expect(componente.obtenerEtiquetaCinturonObjetivo('DESCONOCIDO')).toBeNull();
    expect(componente.obtenerEtiquetaCinturonObjetivo('')).toBeNull();
    expect(componente.obtenerEtiquetaCinturonObjetivo(null)).toBeNull();
    expect(componente.obtenerEtiquetaCinturonObjetivo(undefined)).toBeNull();
  });
});
