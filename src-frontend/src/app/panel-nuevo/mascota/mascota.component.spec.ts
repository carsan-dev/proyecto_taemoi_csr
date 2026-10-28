import { Component } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, flush } from '@angular/core/testing';

import { MASCOTA_CARGADORES, MascotaComponent } from './mascota.component';
import { CARGADORES_MASCOTA, CargadoresMascota, POSES_MASCOTA } from './poses';

@Component({
  standalone: true,
  imports: [MascotaComponent],
  template: `<pn-mascota [pose]="pose" [tamano]="tamano" [animacion]="animacion" [decorativa]="decorativa" [etiqueta]="etiqueta" />@if (segunda) {<pn-mascota [pose]="pose" />}`,
})
class Anfitrion {
  segunda = false;
  pose: string = 'saludar';
  tamano: 'sm' | 'md' | 'lg' | undefined = undefined;
  animacion: 'aparecer' | 'saludar' | 'celebrar' | 'dudar' | 'descansar' | 'cumpleanos' | null = null;
  decorativa = true;
  etiqueta = '';
}

/** Cargadores falsos y síncronos: `<svg data-pose="…">`; la pose «rota» rechaza como un chunk que no llega. */
function cargadoresFalsos(): CargadoresMascota {
  const falsos = {} as Record<string, () => Promise<string>>;
  for (const pose of [...POSES_MASCOTA, 'compacta']) {
    falsos[pose] = () => Promise.resolve(`<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" class="prueba-mascota modo-vivo" data-pose="${pose}" viewBox="0 0 320 320" aria-labelledby="titulo"><title id="titulo">Mascota</title><style>.prueba-mascota.modo-vivo{--gota-idle-count:infinite}.prueba-mascota path{animation:prueba-mascota 1s infinite}@keyframes prueba-mascota{to{opacity:.5}}</style><defs><linearGradient id="tinta"/><path id="trazo" d="M0 0"/></defs><path fill="url(#tinta)" d="M0 0"/><use href="#trazo"/><use xlink:href="#trazo"/></svg>`);
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

  it('aparecer conserva su clase y la entrada de 240 ms', fakeAsync(() => {
    montar();
    document.body.appendChild(el());
    anfitrion.animacion = 'aparecer';
    pintar();
    const lienzo = mascota().querySelector<HTMLElement>('.pn-mascota__lienzo--aparecer')!;
    expect(lienzo).not.toBeNull();
    expect(getComputedStyle(lienzo).animationName).toContain('pn-mascota-aparecer');
    expect(getComputedStyle(lienzo).animationDuration).toBe('0.24s');
    el().remove();
  }));

  it('limita el reposo aunque la raíz del SVG pida un bucle', fakeAsync(() => {
    montar();
    document.body.appendChild(el());
    el().style.setProperty('--pn-acento', 'rgb(70, 99, 150)');
    pintar();
    const svg = mascota().querySelector('svg')!;
    expect(getComputedStyle(svg).getPropertyValue('--gota-idle-count').trim()).toBe('1');
    expect(getComputedStyle(svg).getPropertyValue('--school-accent').trim()).toBe('rgb(70, 99, 150)');
    el().remove();
  }));

  it('dos instancias no comparten IDs y todas sus referencias son locales', fakeAsync(() => {
    montar();
    anfitrion.segunda = true;
    pintar();
    const dibujos = Array.from(el().querySelectorAll('pn-mascota svg'));
    expect(dibujos.length).toBe(2);
    const todos = new Set<string>();
    for (const dibujo of dibujos) {
      const ids = new Set(Array.from(dibujo.querySelectorAll('[id]'), (nodo) => nodo.id));
      expect(ids.size).toBe(3);
      ids.forEach((id) => {
        expect(todos.has(id)).toBeFalse();
        todos.add(id);
      });
      const referencias: string[] = [];
      dibujo.querySelectorAll('*').forEach((nodo) => {
        for (const atributo of Array.from(nodo.attributes)) {
          for (const coincidencia of atributo.value.matchAll(/url\(#([^)]+)\)/g)) {
            referencias.push(coincidencia[1]);
          }
          if (['href', 'xlink:href'].includes(atributo.name)) {
            referencias.push(atributo.value.slice(1));
          }
        }
      });
      referencias.push(dibujo.getAttribute('aria-labelledby')!);
      expect(referencias.length).toBe(4);
      referencias.forEach((id) => expect(ids.has(id)).withContext(id).toBeTrue());
    }
  }));

  describe('animaciones compatibles', () => {
    const nombres = ['saludar', 'celebrar', 'dudar', 'descansar', 'cumpleanos'] as const;
    for (const nombre of nombres) {
      it(nombre + ' no agrega clase, confeti ni capas dobles', fakeAsync(() => {
        montar();
        anfitrion.pose = nombre;
        anfitrion.animacion = nombre;
        pintar();
        expect(el().querySelector('[class*="pn-mascota__lienzo--"]')).toBeNull();
        expect(el().querySelector('.pn-mascota__confeti')).toBeNull();
        expect(mascota().querySelectorAll('svg').length).toBe(1);
      }));
    }
    it('sin animación no hay clase de animación', fakeAsync(() => {
      montar();
      pintar();
      expect(el().querySelector('[class*="pn-mascota__lienzo--"]')).toBeNull();
    }));
    for (const nombre of ['aparecer', ...nombres] as const) {
      it(nombre + ' con menos movimiento queda sin clase ni animaciones SVG', fakeAsync(() => {
        reducido = true;
        montar();
        document.body.appendChild(el());
        anfitrion.animacion = nombre;
        pintar();
        expect(el().querySelector('[class*="pn-mascota__lienzo--"]')).toBeNull();
        expect(mascota().classList).toContain('pn-mascota--quieta');
        mascota().querySelectorAll('svg path').forEach((nodo) =>
          expect(getComputedStyle(nodo).animationName).toBe('none'));
        el().remove();
      }));
    }
  });
});

describe('Poses animadas de la mascota (8b-M2), con los chunks de verdad', () => {
  it('cada pose y la compacta cargan un SVG inline propio', async () => {
    for (const clave of [...POSES_MASCOTA, 'compacta'] as const) {
      const svg = await CARGADORES_MASCOTA[clave]();
      expect(svg.startsWith('<svg')).withContext(clave).toBeTrue();
      expect(svg).withContext(clave).toContain('viewBox="0 0 320 320"');
      expect(svg).withContext(clave).not.toMatch(/<(script|image|foreignObject)\b|\bon\w+=/i);
      const documento = new DOMParser().parseFromString(svg, 'image/svg+xml');
      expect(documento.querySelector('parsererror')).withContext(clave).toBeNull();
      const ids = new Set(Array.from(documento.querySelectorAll('[id]'), (nodo) => nodo.id));
      for (const referencia of svg.matchAll(/url\(#([^)]+)\)|(?:href|xlink:href)="#([^"]+)"/g)) {
        expect(ids.has(referencia[1] || referencia[2])).withContext(clave).toBeTrue();
      }
      if (clave === 'compacta') {
        expect(svg).not.toContain('@keyframes');
      } else {
        expect(svg).withContext(clave).toContain('<style>');
        expect(svg).withContext(clave).toContain('@keyframes');
        expect(svg).withContext(clave).toContain('prefers-reduced-motion');
        expect(svg).withContext(clave).toContain('--gota-idle-count');
      }
    }
  });
  it('mantiene las 14 poses y solo el compacto adicional', () => {
    expect(POSES_MASCOTA.length).toBe(14);
    expect(Object.keys(CARGADORES_MASCOTA).sort()).toEqual([...POSES_MASCOTA, 'compacta'].sort());
  });
});
