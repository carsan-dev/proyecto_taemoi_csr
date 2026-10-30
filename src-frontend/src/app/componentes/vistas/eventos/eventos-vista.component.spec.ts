import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EventosVistaComponent } from './eventos-vista.component';

function evento(id: number, extra: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id,
    titulo: `Evento ${id}`,
    descripcion: 'Descripción',
    fechaEvento: '2026-06-14',
    fotoEvento: { id: id * 10, nombre: `cartel-${id}.webp`, tipo: 'image/webp', ruta: null, url: `http://api.test/api/eventos/${id}/imagen` },
    ...extra,
  };
}

describe('EventosVistaComponent · etiqueta de fotos en la cartelera', () => {
  let fixture: ComponentFixture<EventosVistaComponent>;

  // Los cinco primeros van a destacados; las casillas de la cuadrícula empiezan en el sexto.
  function montar(secundarios: Record<string, unknown>[], previewMode = false): void {
    TestBed.configureTestingModule({ imports: [EventosVistaComponent] });
    fixture = TestBed.createComponent(EventosVistaComponent);
    const destacados = [1, 2, 3, 4, 5].map((id) => evento(id));
    fixture.componentRef.setInput('eventos', [...destacados, ...secundarios]);
    fixture.componentRef.setInput('previewMode', previewMode);
    fixture.detectChanges();
  }

  const etiquetas = (): string[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.event-tile .event-photos-badge')).map((e) =>
      (e.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );

  it('muestra «24 fotos» con su icono en la tarjeta con fotos', () => {
    montar([evento(6, { numeroFotos: 24 })]);

    expect(etiquetas()).toEqual(['24 fotos']);
    expect((fixture.nativeElement as HTMLElement).querySelector('.event-tile .event-photos-badge .bi-images')).not.toBeNull();
  });

  it('usa el singular con una sola foto', () => {
    montar([evento(6, { numeroFotos: 1 })]);

    expect(etiquetas()).toEqual(['1 foto']);
  });

  it('sin numeroFotos, con 0 o con un valor no numérico no pinta etiqueta', () => {
    montar([evento(6), evento(7, { numeroFotos: 0 }), evento(8, { numeroFotos: null }), evento(9, { numeroFotos: 'x' })]);

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.event-tile').length).toBe(4);
    expect(etiquetas()).toEqual([]);
  });

  it('la vista previa (previewMode) no se rompe con ni sin fotos', () => {
    montar([evento(6, { numeroFotos: 3 }), evento(7)], true);

    expect(etiquetas()).toEqual(['3 fotos']);
  });
});
