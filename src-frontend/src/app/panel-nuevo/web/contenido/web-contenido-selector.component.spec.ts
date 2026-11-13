import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, UrlTree, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { CANAL_EDITOR_WEB } from '../../../web-plantilla/editor/puente-editor';
import { EstadoWebEscuela } from '../tema/web-tema-vista';
import { ContenidoWeb, ORIGEN_EDITOR, VISOR_VISUAL } from './web-contenido-vista';
import { SALIDA_HOJA_MS, deslizarAsa } from '../../hoja/asa-hoja-prueba';
import { WebContenidoComponent } from './web-contenido.component';

const URL = `${environment.apiUrl}/escuela/web`;
const URL_PUBLICA = `${environment.apiUrl}/publico/web`;
const ORIGEN = 'about:blank#';
const PREVIA = '?vista-previa=linea-comarcal&editor=1';

const ESTADO: EstadoWebEscuela = { tema: 'linea-comarcal', publicada: true, publicadaEn: '2026-11-01T10:00:00Z', temasDisponibles: ['linea-comarcal'] };
const CONTENIDO: ContenidoWeb = { version: 1, bloques: [] };
const SEDES = [
  { nombre: 'Herrera', slug: 'herrera' },
  { nombre: 'Lora de Estepa', slug: null },
  { nombre: 'Estepa', slug: 'estepa' },
];
const EVENTOS = {
  proximos: [
    { id: 4, titulo: 'Examen de cinturones', fecha: '2026-11-14' },
    { id: 7, titulo: 'Torneo comarcal', fecha: '2026-11-29' },
  ],
  anteriores: [],
};

describe('WebContenidoComponent: selector de página y «Ver el caso» (W12c-bis)', () => {
  let fixture: ComponentFixture<WebContenidoComponent>;
  let http: HttpTestingController;
  let navegar: jasmine.Spy;

  function configurar(consulta: Record<string, string> = {}): void {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ORIGEN_EDITOR, useValue: ORIGEN }, { provide: VISOR_VISUAL, useValue: null }],
    });
    spyOnProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap').and.returnValue(convertToParamMap(consulta));
    http = TestBed.inject(HttpTestingController);
    navegar = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  }

  afterEach(() => {
    fixture?.destroy();
    http.verify();
  });

  const el = () => fixture.nativeElement as HTMLElement;
  const q = <T extends HTMLElement = HTMLElement>(selector: string) => el().querySelector<T>(selector);
  const todos = <T extends HTMLElement = HTMLElement>(selector: string) => Array.from(el().querySelectorAll<T>(selector));
  const texto = (selector: string) => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const iframe = () => q<HTMLIFrameElement>('iframe');
  const src = () => iframe()?.getAttribute('src') ?? null;
  const anuncio = () => texto('p.oculto[role="status"]');
  const pagina = () => q<HTMLSelectElement>('#web-contenido-pagina')!;
  const sub = () => q<HTMLSelectElement>('#web-contenido-sub');

  function abrir(sedes: unknown = SEDES, eventos: unknown = EVENTOS, fallan = false): void {
    fixture = TestBed.createComponent(WebContenidoComponent);
    fixture.detectChanges();
    http.expectOne(URL).flush(ESTADO);
    http.expectOne(`${URL}/contenido`).flush(CONTENIDO);
    const sitio = http.expectOne((r) => r.url === URL_PUBLICA);
    expect(sitio.request.params.get('vistaPrevia')).toBe('true');
    expect(sitio.request.params.get('tema')).toBe('linea-comarcal');
    expect(sitio.request.method).toBe('GET');
    const ev = http.expectOne((r) => r.url === `${URL_PUBLICA}/eventos`);
    if (fallan) {
      sitio.flush(null, { status: 500, statusText: 'Error' });
    } else {
      sitio.flush({ sedes });
      ev.flush(eventos as object);
    }
    fixture.detectChanges();
  }

  function elegir(select: HTMLSelectElement, valor: string): void {
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function llegar(data: unknown): void {
    window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: iframe()!.contentWindow }));
    fixture.detectChanges();
  }

  /** La consulta `pagina` de la última navegación (la página va en la dirección, como el boceto). */
  const paginaEnLaDireccion = () => (navegar.calls.mostRecent().args[0] as UrlTree).queryParams['pagina'];

  describe('selector «Página» (d519)', () => {
    beforeEach(() => configurar());

    it('las siete páginas con su nombre, sin «· N cambios»; empieza en Inicio', () => {
      abrir();
      expect(texto('label[for="web-contenido-pagina"]')).toBe('Página');
      expect(todos<HTMLOptionElement>('#web-contenido-pagina option').map((o) => o.textContent!.trim())).toEqual([
        'Inicio, con cabecera y pie',
        'Una sede',
        'Tarifas',
        'Eventos',
        'Un evento',
        'Legales',
        'Preinscripción',
      ]);
      expect(pagina().value).toBe('inicio');
      expect(sub()).toBeNull();
      expect(src()).toBe(`${ORIGEN}/${PREVIA}`);
    });

    for (const [valor, ruta, nombre] of [
      ['tarifas', '/tarifas', 'Tarifas'],
      ['eventos', '/eventos', 'Eventos'],
      ['legales', '/legales', 'Legales'],
      ['preinscripcion', '/preinscripcion', 'Preinscripción'],
    ] as const) {
      it(`${nombre}: el iframe pasa a ${ruta} en modo editor, el rótulo y la voz lo dicen y va en la dirección`, () => {
        abrir();
        elegir(pagina(), valor);
        expect(src()).toBe(`${ORIGEN}${ruta}${PREVIA}`);
        // W12c-ter: en Preinscripción el rótulo lleva el paso (boceto `pintarSelector`, l.2415).
        expect(texto('#marco-t')).toBe(`Vista previa de tu web · ${nombre}${valor === 'preinscripcion' ? ' · 1 · Pueblo' : ''}`);
        expect(anuncio()).toBe(`Vista previa de ${nombre}.`);
        expect(paginaEnLaDireccion()).toBe(valor);
        // Nada de esto llega a la API.
        http.expectNone(() => true);
      });
    }

    it('Una sede: segundo selector «Sede» con la primera con página; las sin página, desactivadas y nombradas en la nota', () => {
      abrir();
      elegir(pagina(), 'sede');
      expect(texto('label[for="web-contenido-sub"]')).toBe('Sede');
      const opciones = todos<HTMLOptionElement>('#web-contenido-sub option');
      expect(opciones.map((o) => [o.textContent!.trim(), o.disabled])).toEqual([
        ['Herrera', false],
        ['Lora de Estepa (sin página)', true],
        ['Estepa', false],
      ]);
      expect(src()).toBe(`${ORIGEN}/sedes/herrera${PREVIA}`);
      expect(texto('#marco-t')).toBe('Vista previa de tu web · Una sede · Herrera');
      expect(texto('.nota-pagina')).toBe(
        'Sus textos valen para todas las sedes. Una sede sin dirección de página (Lora de Estepa) no tiene página: se la pones en Sedes.',
      );
      elegir(sub()!, 'estepa');
      expect(src()).toBe(`${ORIGEN}/sedes/estepa${PREVIA}`);
      expect(texto('#marco-t')).toBe('Vista previa de tu web · Una sede · Estepa');
      http.expectNone(() => true);
    });

    it('Un evento: segundo selector «Evento» con «Título (día mes)» y su nota', () => {
      abrir();
      elegir(pagina(), 'evento');
      expect(texto('label[for="web-contenido-sub"]')).toBe('Evento');
      expect(todos<HTMLOptionElement>('#web-contenido-sub option').map((o) => o.textContent!.trim())).toEqual([
        'Examen de cinturones (14 nov)',
        'Torneo comarcal (29 nov)',
      ]);
      expect(src()).toBe(`${ORIGEN}/eventos/4${PREVIA}`);
      expect(texto('.nota-pagina')).toBe(
        'Sus textos valen para todos los eventos. El nombre, la fecha, las fotos y los documentos se cambian en Eventos.',
      );
      elegir(sub()!, '7');
      expect(src()).toBe(`${ORIGEN}/eventos/7${PREVIA}`);
      expect(texto('#marco-t')).toBe('Vista previa de tu web · Un evento · Torneo comarcal (29 nov)');
    });

    it('sin sede con página ni eventos (o si no se pueden leer), «Una sede» y «Un evento» no se eligen', () => {
      abrir([{ nombre: 'Lora', slug: null }], { proximos: [], anteriores: [] });
      const op = (v: string) => q<HTMLOptionElement>(`#web-contenido-pagina option[value="${v}"]`)!;
      expect(op('sede').disabled).toBeTrue();
      expect(op('evento').disabled).toBeTrue();
      expect(op('tarifas').disabled).toBeFalse();
      fixture.destroy();
      http.verify();
      abrir(undefined, undefined, true);
      expect(op('sede').disabled).toBeTrue();
      expect(op('evento').disabled).toBeTrue();
    });

    it('al cambiar de página se quita el caso', () => {
      abrir();
      fixture.componentInstance.alternarMenuCasos();
      fixture.detectChanges();
      q<HTMLButtonElement>('[data-caso="lleno"]')!.click();
      fixture.detectChanges();
      expect(q('.caso-activo')).not.toBeNull();
      elegir(pagina(), 'tarifas');
      expect(q('.caso-activo')).toBeNull();
      expect(fixture.componentInstance.caso()).toBeNull();
    });
  });

  describe('la página de la dirección', () => {
    it('?pagina=eventos abre Eventos', () => {
      configurar({ pagina: 'eventos' });
      abrir();
      expect(pagina().value).toBe('eventos');
      expect(src()).toBe(`${ORIGEN}/eventos${PREVIA}`);
    });

    it('?pagina=sede sin sedes con página vuelve a Inicio; una página desconocida, Inicio', () => {
      configurar({ pagina: 'sede' });
      abrir([{ nombre: 'Lora', slug: null }]);
      expect(pagina().value).toBe('inicio');
      expect(src()).toBe(`${ORIGEN}/${PREVIA}`);
    });
  });

  describe('«Ver el caso» (d528)', () => {
    beforeEach(() => configurar());

    const boton = () => q<HTMLButtonElement>('.ver-caso')!;
    const abrirMenu = () => {
      boton().click();
      fixture.detectChanges();
    };
    const opciones = () => todos<HTMLButtonElement>('.flot--casos [role="menuitemradio"]');

    it('menú con «Datos reales» y los casos de la página; marcado el que se ve', async () => {
      abrir();
      expect(texto('.ver-caso')).toBe('Ver el caso');
      expect(boton().getAttribute('aria-haspopup')).toBe('menu');
      expect(boton().getAttribute('aria-expanded')).toBe('false');
      abrirMenu();
      expect(boton().getAttribute('aria-expanded')).toBe('true');
      expect(texto('.flot__titulo')).toBe('Ver un caso de Inicio');
      expect(texto('.flot__ayuda--casos')).toBe('Textos que solo salen en casos poco frecuentes. Elige uno para verlo y cambiar sus textos.');
      expect(opciones().map((o) => o.textContent!.replace(/\s+/g, ' ').trim())).toEqual([
        'Datos realesTu web como está ahora',
        'Grupo llenoUn grupo sin plazas libres',
        'Lista de esperaUn grupo que ya tiene lista de espera',
      ]);
      expect(opciones().map((o) => o.getAttribute('aria-checked'))).toEqual(['true', 'false', 'false']);
      await fixture.whenStable();
      expect(document.activeElement).toBe(opciones()[0]);
    });

    it('cada página, sus casos', () => {
      abrir();
      const casosDe = (p: string) => {
        elegir(pagina(), p);
        abrirMenu();
        const lista = opciones().slice(1).map((o) => o.querySelector('span:last-child')!.firstChild!.textContent!.trim());
        q<HTMLButtonElement>('.ver-caso')!.click();
        fixture.detectChanges();
        return lista;
      };
      expect(casosDe('tarifas')).toEqual(['Sin tarifas publicadas']);
      expect(casosDe('eventos')).toEqual(['Sin eventos próximos', 'Sin eventos', 'Un solo evento', 'Sin eventos anteriores']);
      expect(casosDe('evento')).toEqual(['Una sola foto']);
      expect(casosDe('sede')).toEqual(['Grupo lleno', 'Lista de espera', 'Sin clases esta semana']);
      expect(casosDe('legales')).toEqual([]);
      abrirMenu();
      expect(texto('.flot__ayuda--casos')).toBe('Esta página no tiene textos de casos poco frecuentes.');
      expect(q('.flot__sep')).toBeNull();
    });

    it('elegir un caso: va por el puente al iframe (propio origen), se dice encima de la vista previa y se vuelve a los datos reales', () => {
      abrir();
      elegir(pagina(), 'eventos');
      const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
      abrirMenu();
      q<HTMLButtonElement>('[data-caso="sin-proximos"]')!.click();
      fixture.detectChanges();
      expect(enviar).toHaveBeenCalledOnceWith({ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'sin-proximos' }, location.origin as never);
      expect(q('.flot--casos')).toBeNull();
      expect(document.activeElement).toBe(boton());
      expect(boton().classList).toContain('es-activo');
      expect(texto('#caso-voz')).toBe('Viendo: Sin eventos próximos');
      expect(texto('.caso-activo__texto')).toBe(
        'Viendo: Sin eventos próximosHay eventos pasados, ninguno por venir. Solo para ver y cambiar sus textos: no es un cambio y no se guarda.',
      );
      expect(anuncio()).toBe('Viendo: Sin eventos próximos. No es un cambio y no se guarda.');
      abrirMenu();
      expect(q('[data-caso="sin-proximos"]')!.getAttribute('aria-checked')).toBe('true');
      boton().click();
      fixture.detectChanges();

      q<HTMLButtonElement>('.caso-activo__volver')!.click();
      fixture.detectChanges();
      expect(texto('.caso-activo__volver')).toBe('');
      expect(q('.caso-activo')).toBeNull();
      expect(enviar.calls.mostRecent().args).toEqual([{ canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: null } as never, location.origin as never]);
      expect(anuncio()).toBe('Vuelves a los datos reales.');
      expect(document.activeElement).toBe(boton());
      expect(texto('#caso-voz')).toBe('');
      // Nada de esto llega a la API.
      http.expectNone(() => true);
    });

    it('con otra sede (el iframe se recarga), la capa nueva recibe el caso al estar lista', () => {
      abrir();
      elegir(pagina(), 'sede');
      abrirMenu();
      q<HTMLButtonElement>('[data-caso="espera"]')!.click();
      fixture.detectChanges();
      elegir(sub()!, 'estepa');
      expect(q('.caso-activo')).not.toBeNull();
      const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'listo' });
      const tipos = enviar.calls
        .allArgs()
        .map(([m]) => (m as { tipo: string; caso?: string }).tipo + (m as { caso?: string }).caso)
        .filter((t) => !t.startsWith('estado-bloques') && !t.startsWith('profesores'));
      expect(tipos).toEqual(['borradorundefined', 'casoespera']);
    });

    it('al elegir el caso, enseña su parte cuando la capa dice dónde está; sin caso elegido, no', () => {
      abrir();
      const desplazar = spyOn(window, 'scrollTo');
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 5000 });
      expect(desplazar).not.toHaveBeenCalled();
      abrirMenu();
      q<HTMLButtonElement>('[data-caso="lleno"]')!.click();
      fixture.detectChanges();
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 5000 });
      expect(desplazar).toHaveBeenCalledTimes(1);
      llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 5000 });
      expect(desplazar).toHaveBeenCalledTimes(1);
    });

    it('al enseñar la parte, descuenta el alto real de la barra superior del panel (56 px en móvil), no 64 fijo', () => {
      const cabecera = document.createElement('header');
      cabecera.className = 'pn-cab';
      cabecera.style.cssText = 'position:fixed;top:0;left:0;right:0;height:56px';
      document.body.appendChild(cabecera);
      try {
        abrir();
        const desplazar = spyOn(window, 'scrollTo');
        abrirMenu();
        q<HTMLButtonElement>('[data-caso="lleno"]')!.click();
        fixture.detectChanges();
        llegar({ canal: CANAL_EDITOR_WEB, tipo: 'mostrar', arriba: 5000 });
        expect(desplazar).toHaveBeenCalledTimes(1);
        const y = iframe()!.getBoundingClientRect().top + window.scrollY + 5000 * fixture.componentInstance.escala();
        const tapa = 56 + q('.caso-activo')!.offsetHeight + 16;
        const destino = desplazar.calls.mostRecent().args[0] as unknown as ScrollToOptions;
        expect(destino.top).toBeCloseTo(Math.max(0, y - tapa), 3);
      } finally {
        cabecera.remove();
      }
    });

    it('teclado: flechas, Inicio y Fin entre opciones; Escape cierra y devuelve el foco; pulsar fuera lo cierra', async () => {
      abrir();
      abrirMenu();
      await fixture.whenStable();
      const tecla = (key: string) => {
        (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
        fixture.detectChanges();
      };
      tecla('ArrowDown');
      expect(document.activeElement).toBe(opciones()[1]);
      tecla('End');
      expect(document.activeElement).toBe(opciones()[2]);
      tecla('ArrowDown');
      expect(document.activeElement).toBe(opciones()[0]);
      tecla('ArrowUp');
      expect(document.activeElement).toBe(opciones()[2]);
      tecla('Home');
      expect(document.activeElement).toBe(opciones()[0]);
      tecla('Escape');
      expect(q('.flot--casos')).toBeNull();
      expect(document.activeElement).toBe(boton());

      abrirMenu();
      document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
      fixture.detectChanges();
      expect(q('.flot--casos')).toBeNull();
      expect(boton().getAttribute('aria-expanded')).toBe('false');
    });

    it('d546: deslizar el asa de la hoja hacia abajo cierra el menú sin elegir ni mover el foco, como pulsar fuera', async () => {
      abrir();
      const enviar = spyOn(iframe()!.contentWindow!, 'postMessage');
      abrirMenu();
      deslizarAsa(q('.flot--casos .flot__asa-m')!);
      expect(q<HTMLElement>('.flot--casos')!.style.transform).toBe('translateY(100%)');
      await new Promise((resuelve) => setTimeout(resuelve, SALIDA_HOJA_MS));
      fixture.detectChanges();
      expect(q('.flot--casos')).toBeNull();
      expect(boton().getAttribute('aria-expanded')).toBe('false');
      expect(enviar.calls.allArgs().filter(([m]) => (m as { tipo: string }).tipo === 'caso')).toEqual([]);
      expect(q('.caso-activo')).toBeNull();
    });

    it('en < 768 px el menú es una hoja inferior, como los demás menús', () => {
      abrir();
      abrirMenu();
      const reglas: CSSStyleRule[] = [];
      for (const hoja of Array.from(document.styleSheets)) {
        let lista: CSSRuleList;
        try {
          lista = hoja.cssRules;
        } catch {
          continue;
        }
        for (const regla of Array.from(lista)) {
          if (regla instanceof CSSMediaRule && regla.conditionText.includes('767.98px')) {
            reglas.push(...(Array.from(regla.cssRules) as CSSStyleRule[]));
          }
        }
      }
      const hoja = reglas.find((r) => /^\.flot\[/.test(r.selectorText ?? '') && r.style.position === 'fixed');
      expect(hoja).withContext('regla .flot de la hoja inferior').toBeDefined();
      expect(hoja!.style.bottom).toBe('0px');
      expect(hoja!.style.left).toBe('0px');
      // Por encima de la barra inferior del panel (navegacion/barra-inferior.component.scss: z-index 1001), que si no
      // tapa sus últimas opciones (precedente: la hoja de cargos de Alumnos, z-index 1020).
      expect(Number(hoja!.style.zIndex)).toBeGreaterThan(1001);
      expect(q('.flot__asa')).not.toBeNull();
    });
  });
});
