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

  const chipPrincipal = (): HTMLElement | null =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.showcase-main-chip--fotos');
  const etiquetasMiniatura = (): string[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.thumb-media .event-photos-badge')).map((e) =>
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

  describe('destacados', () => {
    function montarDestacados(extras: Record<number, number | null>): void {
      TestBed.configureTestingModule({ imports: [EventosVistaComponent] });
      fixture = TestBed.createComponent(EventosVistaComponent);
      const destacados = [1, 2, 3].map((id) => evento(id, extras[id] !== undefined ? { numeroFotos: extras[id] } : {}));
      fixture.componentRef.setInput('eventos', destacados);
      fixture.detectChanges();
    }

    it('el destacado principal con fotos lleva la pastilla «N fotos» con icono junto a «Evento destacado»', () => {
      montarDestacados({ 1: 12 });

      const chip = chipPrincipal()!;
      expect(chip).not.toBeNull();
      expect((chip.textContent ?? '').replace(/\s+/g, ' ').trim()).toBe('12 fotos');
      expect(chip.querySelector('.bi-images')).not.toBeNull();
      expect(chip.classList).toContain('showcase-main-chip');
      expect(chip.previousElementSibling?.textContent?.trim()).toBe('Evento destacado');
      expect(chip.parentElement?.classList).toContain('showcase-main-chips');
    });

    it('la pastilla del principal tiene la misma altura que «Evento destacado»', () => {
      montarDestacados({ 1: 3 });

      const chip = chipPrincipal()!;
      const evento = chip.previousElementSibling as HTMLElement;
      expect(Math.abs(chip.getBoundingClientRect().height - evento.getBoundingClientRect().height)).toBeLessThan(1);
    });

    it('las miniaturas de destacados con fotos llevan la etiqueta y las demás no', () => {
      montarDestacados({ 1: 1, 2: 0, 3: 7 });

      expect(etiquetasMiniatura()).toEqual(['1 foto', '7 fotos']);
      const insignia = (fixture.nativeElement as HTMLElement).querySelector('.thumb-media .event-photos-badge')!;
      expect(insignia.classList).toContain('event-photos-badge--mini');
      expect(insignia.querySelector('.bi-images')).not.toBeNull();
      expect(insignia.closest('.thumb-item')?.querySelector('.thumb-title')?.contains(insignia)).toBeFalse();
    });

    it('sin fotos (ausente, 0 o nulo) los destacados no pintan ninguna etiqueta', () => {
      montarDestacados({ 2: 0, 3: null });

      expect(chipPrincipal()).toBeNull();
      expect(etiquetasMiniatura()).toEqual([]);
    });

    it('el principal cambia de pastilla al pasar al siguiente destacado', () => {
      montarDestacados({ 1: 4 });
      expect(chipPrincipal()).not.toBeNull();

      fixture.componentInstance.mostrarDestacadoSiguiente(new Event('click'));
      fixture.detectChanges();

      expect(chipPrincipal()).toBeNull();
    });
  });
});
