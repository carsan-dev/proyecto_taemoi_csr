import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';

import { MASCOTA_CARGADORES, MascotaComponent } from './mascota.component';
import { CARGADORES_MASCOTA, CargadoresMascota, POSES_MASCOTA, PoseMascota } from './poses';

@Component({
  standalone: true,
  imports: [MascotaComponent],
  template: `<pn-mascota [pose]="pose" [tamano]="tamano" [animacion]="animacion" [decorativa]="decorativa" [etiqueta]="etiqueta" />`,
})
class Anfitrion {
  pose: string = 'saludar';
  tamano: 'sm' | 'md' | 'lg' | undefined = undefined;
  animacion: 'aparecer' | 'saludar' | 'celebrar' | 'dudar' | 'descansar' | 'cumpleanos' | null = null;
  decorativa = true;
  etiqueta = '';
}

/** Cargadores falsos y síncronos: `<svg data-pose="…">`; la pose «rota» rechaza como un chunk que no llega. */
function cargadoresFalsos(): CargadoresMascota {
  const falsos = {} as Record<string, () => Promise<string>>;
  for (const pose of [...POSES_MASCOTA, 'cumpleanos-inicio', 'compacta']) {
    falsos[pose] = () => Promise.resolve(`<svg data-pose="${pose}" viewBox="0 0 320 320"><path d="M0 0"/></svg>`);
  }
  falsos['leer'] = () => Promise.reject(new Error('chunk perdido'));
  return falsos as CargadoresMascota;
}

describe('MascotaComponent (8b-M)', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let anfitrion: Anfitrion;
  let reducido: boolean;

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }
  function mascota(): HTMLElement {
    return el().querySelector('pn-mascota') as HTMLElement;
  }
  function pintar(): void {
    fixture.detectChanges();
    flush();
    fixture.detectChanges();
  }
  function montar(): void {
    fixture = TestBed.createComponent(Anfitrion);
    anfitrion = fixture.componentInstance;
  }

  beforeEach(() => {
    reducido = false;
    spyOn(window, 'matchMedia').and.callFake(
      (consulta: string) => ({ matches: reducido && consulta.includes('prefers-reduced-motion'), media: consulta }) as MediaQueryList,
    );
    TestBed.configureTestingModule({
      imports: [Anfitrion],
      providers: [{ provide: MASCOTA_CARGADORES, useValue: cargadoresFalsos() }],
    });
  });

  it('pinta la pose como SVG inline, no como imagen', fakeAsync(() => {
    montar();
    anfitrion.pose = 'dudar';
    pintar();
    const svg = mascota().querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg!.getAttribute('data-pose')).toBe('dudar');
    expect(mascota().querySelector('img')).toBeNull();
  }));

  it('cambia de pose cuando cambia la entrada', fakeAsync(() => {
    montar();
    pintar();
    anfitrion.pose = 'buscar';
    pintar();
    expect(mascota().querySelector('svg')!.getAttribute('data-pose')).toBe('buscar');
  }));

  it('el tamaño por defecto es md (96 px) y admite sm (32 px) y lg (144 px)', fakeAsync(() => {
    montar();
    pintar();
    expect(mascota().classList).toContain('pn-mascota--md');
    anfitrion.tamano = 'lg';
    pintar();
    expect(mascota().classList).toContain('pn-mascota--lg');
    expect(mascota().classList).not.toContain('pn-mascota--md');
    anfitrion.tamano = 'sm';
    pintar();
    expect(mascota().classList).toContain('pn-mascota--sm');
  }));

  it('con sm usa la versión compacta, sea cual sea la pose', fakeAsync(() => {
    montar();
    anfitrion.pose = 'celebrar';
    anfitrion.tamano = 'sm';
    pintar();
    expect(mascota().querySelector('svg')!.getAttribute('data-pose')).toBe('compacta');
  }));

  it('es decorativa por defecto: aria-hidden y sin role', fakeAsync(() => {
    montar();
    pintar();
    expect(mascota().getAttribute('aria-hidden')).toBe('true');
    expect(mascota().getAttribute('role')).toBeNull();
    expect(mascota().getAttribute('aria-label')).toBeNull();
  }));

  it('con decorativa=false es role=img con el aria-label de la etiqueta', fakeAsync(() => {
    montar();
    anfitrion.decorativa = false;
    anfitrion.etiqueta = 'Mes cobrado entero';
    pintar();
    expect(mascota().getAttribute('role')).toBe('img');
    expect(mascota().getAttribute('aria-label')).toBe('Mes cobrado entero');
    expect(mascota().getAttribute('aria-hidden')).toBeNull();
  }));

  it('guarda el hueco del tamaño final antes de que llegue el SVG', fakeAsync(() => {
    montar();
    fixture.detectChanges(); // sin flush: el SVG aún no ha llegado
    expect(mascota().querySelector('svg')).toBeNull();
    expect(mascota().classList).toContain('pn-mascota--md');
    flush();
    fixture.detectChanges();
    expect(mascota().querySelector('svg')).not.toBeNull();
  }));

  it('una pose inexistente no rompe y deja el hueco vacío', fakeAsync(() => {
    montar();
    anfitrion.pose = 'volar';
    expect(() => pintar()).not.toThrow();
    expect(mascota().querySelector('svg')).toBeNull();
    expect(mascota().classList).toContain('pn-mascota--md');
  }));

  it('si el chunk de la pose no llega, no rompe', fakeAsync(() => {
    montar();
    anfitrion.pose = 'leer';
    expect(() => pintar()).not.toThrow();
    expect(mascota().querySelector('svg')).toBeNull();
  }));

  it('la última pose pedida gana si llegan desordenadas', fakeAsync(() => {
    montar();
    anfitrion.pose = 'esperar';
    fixture.detectChanges();
    anfitrion.pose = 'invitar';
    pintar();
    expect(mascota().querySelector('svg')!.getAttribute('data-pose')).toBe('invitar');
  }));

  it('el CSS de verdad: 96, 32 y 144 px, y el hueco no cambia cuando llega el SVG', fakeAsync(() => {
    montar();
    document.body.appendChild(el());
    fixture.detectChanges();
    const antes = mascota().getBoundingClientRect();
    expect([antes.width, antes.height]).toEqual([96, 96]);
    flush();
    fixture.detectChanges();
    const despues = mascota().getBoundingClientRect();
    expect([despues.width, despues.height]).toEqual([96, 96]);
    anfitrion.tamano = 'sm';
    pintar();
    expect(mascota().getBoundingClientRect().width).toBe(32);
    anfitrion.tamano = 'lg';
    pintar();
    expect(mascota().getBoundingClientRect().width).toBe(144);
    el().remove();
  }));

  it('el CSS de verdad: duraciones y bucle de cada animación, y la cinta toma el acento de la escuela', fakeAsync(() => {
    montar();
    document.body.appendChild(el());
    const esperadas: Record<string, [string, string, string]> = {
      aparecer: ['pn-mascota-aparecer', '0.24s', '1'],
      saludar: ['pn-mascota-saludar', '0.6s', '1'],
      celebrar: ['pn-mascota-celebrar', '0.7s', '1'],
      dudar: ['pn-mascota-dudar', '0.5s', '1'],
      descansar: ['pn-mascota-descansar', '3s', 'infinite'],
    };
    for (const [nombre, [clave, duracion, iteraciones]] of Object.entries(esperadas)) {
      anfitrion.animacion = nombre as typeof anfitrion.animacion;
      pintar();
      const lienzo = el().querySelector<HTMLElement>('.pn-mascota__lienzo')!;
      const estilo = getComputedStyle(lienzo);
      // Angular antepone el atributo de encapsulación al nombre de los keyframes.
      expect(estilo.animationName).withContext(nombre).toContain(clave);
      expect(estilo.animationDuration).withContext(nombre).toBe(duracion);
      expect(estilo.animationIterationCount).withContext(nombre).toBe(iteraciones);
    }
    // Solo el descansar es un bucle.
    anfitrion.animacion = 'celebrar';
    pintar();
    const pieza = el().querySelector<HTMLElement>('.pn-mascota__confeti')!;
    expect(getComputedStyle(pieza).animationDuration).toBe('0.6s');
    expect(getComputedStyle(pieza).backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
    // El acento de la escuela (--pn-acento) llega como --school-accent a la cinta del SVG.
    el().style.setProperty('--pn-acento', 'rgb(70, 99, 150)');
    anfitrion.animacion = null;
    pintar();
    const cinta = mascota().querySelector('svg')!;
    expect(getComputedStyle(mascota()).getPropertyValue('--school-accent').trim()).toBe('rgb(70, 99, 150)');
    expect(cinta).not.toBeNull();
    el().remove();
  }));

  describe('animaciones', () => {
    const nombres = ['aparecer', 'saludar', 'celebrar', 'dudar', 'descansar'] as const;

    for (const nombre of nombres) {
      it(`«${nombre}» pone su clase en el lienzo`, fakeAsync(() => {
        montar();
        anfitrion.animacion = nombre;
        pintar();
        expect(el().querySelector(`.pn-mascota__lienzo--${nombre}`)).not.toBeNull();
      }));
    }

    it('sin animación no hay clase de animación', fakeAsync(() => {
      montar();
      pintar();
      expect(el().querySelector('[class*="pn-mascota__lienzo--"]')).toBeNull();
    }));

    it('celebrar suelta el confeti, aria-hidden', fakeAsync(() => {
      montar();
      anfitrion.animacion = 'celebrar';
      pintar();
      const confeti = el().querySelectorAll('.pn-mascota__confeti');
      expect(confeti.length).toBeGreaterThan(0);
      confeti.forEach((c) => expect(c.getAttribute('aria-hidden')).toBe('true'));
    }));

    it('cumpleaños apila el fotograma inicial y el final', fakeAsync(() => {
      montar();
      anfitrion.pose = 'cumpleanos';
      anfitrion.animacion = 'cumpleanos';
      pintar();
      const capas = el().querySelectorAll('.pn-mascota__lienzo svg');
      expect(capas.length).toBe(2);
      expect(capas[0].getAttribute('data-pose')).toBe('cumpleanos-inicio');
      expect(capas[1].getAttribute('data-pose')).toBe('cumpleanos');
      expect(el().querySelector('.pn-mascota__capa--final')).not.toBeNull();
    }));

    describe('con prefers-reduced-motion: reduce', () => {
      beforeEach(() => {
        reducido = true;
      });

      for (const nombre of [...nombres, 'cumpleanos' as const]) {
        it(`«${nombre}» deja la pose quieta, sin clase de animación`, fakeAsync(() => {
          montar();
          anfitrion.animacion = nombre;
          pintar();
          expect(el().querySelector('[class*="pn-mascota__lienzo--"]')).toBeNull();
          expect(el().querySelector('.pn-mascota__confeti')).toBeNull();
          expect(el().querySelector('svg')).not.toBeNull();
        }));
      }

      it('cumpleaños pinta directamente la pose final', fakeAsync(() => {
        montar();
        anfitrion.pose = 'cumpleanos';
        anfitrion.animacion = 'cumpleanos';
        pintar();
        const capas = el().querySelectorAll('.pn-mascota__lienzo svg');
        expect(capas.length).toBe(1);
        expect(capas[0].getAttribute('data-pose')).toBe('cumpleanos');
      }));
    });
  });
});

describe('Poses de la mascota (8b-M), con los chunks de verdad', () => {
  it('cada pose y la compacta cargan un SVG inline sano', async () => {
    const claves = [...POSES_MASCOTA, 'compacta', 'cumpleanos-inicio'];
    for (const clave of claves) {
      const cargador = (CARGADORES_MASCOTA as Record<string, () => Promise<string>>)[clave];
      expect(cargador).withContext(clave).toBeDefined();
      const svg = await cargador();
      expect(svg.startsWith('<svg')).withContext(clave).toBeTrue();
      expect(svg).withContext(clave).toContain('viewBox="0 0 320 320"');
      expect(svg).withContext(clave).not.toContain('<title');
      expect(svg).withContext(clave).not.toMatch(/<(script|image|foreignObject|style)\b|href=|onload/i);
    }
  });

  it('las poses de mascota-app están todas, incluidos los 8 estados del 23/10', () => {
    const esperadas: PoseMascota[] = [
      'saludar', 'explicar', 'celebrar', 'dudar', 'descansar', 'cumpleanos', 'prepararse', 'animar', 'leer',
      'confirmar', 'buscar', 'esperar', 'invitar', 'agradecer',
    ];
    expect([...POSES_MASCOTA].sort()).toEqual([...esperadas].sort());
  });
});
