// Caracterización del comportamiento de Taemoi. Si cambia, debe ser una decisión explícita.
// La tabla de etiquetas ya no está en el componente: sale de `grado.nombre` del catálogo (D3).
import { TestBed } from '@angular/core/testing';
import { MaterialesExamenUserComponent } from './materiales-examen-user.component';
import { proveerCatalogoTesting } from '../../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../../features/catalogo/testing/catalogo.fixtures';

describe('materiales-examen-user etiquetas del grado siguiente (caracterización)', () => {
  // Se instancia sin render: solo se ejecutan los inicializadores de campos (en un contexto de inyección).
  let componente: {
    obtenerEtiquetaCinturonObjetivo(g: string | null | undefined, deporte: string | null | undefined): string | null;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(CATALOGO_TAEMOI)] });
    componente = TestBed.runInInjectionContext(
      () => new MaterialesExamenUserComponent({} as never)
    ) as unknown as typeof componente;
  });

  it('Taekwondo: etiqueta de cada grado (igual que la tabla anterior salvo D3)', () => {
    const esperado: [string, string][] = [
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
      // D3: antes «NEGRO nº PUM»; ahora el nombre del catálogo, «ROJO-NEGRO nº PUM».
      ['ROJO_NEGRO_1_PUM', 'ROJO-NEGRO 1º PUM'],
      ['ROJO_NEGRO_2_PUM', 'ROJO-NEGRO 2º PUM'],
      ['ROJO_NEGRO_3_PUM', 'ROJO-NEGRO 3º PUM'],
      ['NEGRO_1_DAN', 'NEGRO 1º DAN'],
      ['NEGRO_2_DAN', 'NEGRO 2º DAN'],
      ['NEGRO_3_DAN', 'NEGRO 3º DAN'],
      ['NEGRO_4_DAN', 'NEGRO 4º DAN'],
      ['NEGRO_5_DAN', 'NEGRO 5º DAN'],
    ];
    esperado.forEach(([grado, etiqueta]) => {
      expect(componente.obtenerEtiquetaCinturonObjetivo(grado, 'TAEKWONDO')).withContext(grado).toBe(etiqueta);
    });
  });

  it('Kickboxing: D4, ROJO es «MARRÓN»', () => {
    expect(componente.obtenerEtiquetaCinturonObjetivo('ROJO', 'KICKBOXING')).toBe('MARRÓN');
    expect(componente.obtenerEtiquetaCinturonObjetivo('NEGRO_2_DAN', 'KICKBOXING')).toBe('NEGRO 2º DAN');
  });

  it('obtenerEtiquetaCinturonObjetivo: desconocidos y vacíos', () => {
    expect(componente.obtenerEtiquetaCinturonObjetivo('DESCONOCIDO', 'TAEKWONDO')).toBeNull();
    expect(componente.obtenerEtiquetaCinturonObjetivo('', 'TAEKWONDO')).toBeNull();
    expect(componente.obtenerEtiquetaCinturonObjetivo(null, 'TAEKWONDO')).toBeNull();
    expect(componente.obtenerEtiquetaCinturonObjetivo(undefined, 'TAEKWONDO')).toBeNull();
  });
});
