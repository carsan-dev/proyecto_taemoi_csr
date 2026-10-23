import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { LoadingService } from '../../servicios/generales/loading.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { EventosPanelComponent } from './eventos.component';

const API = environment.apiUrl;

interface EventoPrueba {
  id: number;
  titulo: string;
  descripcion: string;
  fechaEvento: string | null;
  visible: boolean;
  fotoEvento: { id: number; nombre: string; url: string; tipo: string; ruta: string } | null;
}

function evento(id: number, cambios: Partial<EventoPrueba> = {}): EventoPrueba {
  return {
    id,
    titulo: `Evento ${id}`,
    descripcion: `Descripción del evento ${id}`,
    fechaEvento: null,
    visible: true,
    fotoEvento: { id: 900 + id, nombre: `f${id}.webp`, url: `https://img.test/f${id}.webp`, tipo: 'image/webp', ruta: '' },
    ...cambios,
  };
}

const LISTA = (): EventoPrueba[] => [
  evento(1, { titulo: 'Campeonato regional', fechaEvento: '2026-11-14' }),
  evento(2, { titulo: 'Exhibición', fechaEvento: '2026-10-22' }),
  evento(3, { titulo: 'Seminario', fechaEvento: '2026-09-27', visible: false }),
  evento(4, { titulo: 'Día del padre', fotoEvento: null }),
];

describe('EventosPanelComponent (8b-3f, listado de Eventos del panel nuevo)', () => {
  let fixture: ComponentFixture<EventosPanelComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let loading: jasmine.SpyObj<LoadingService>;
  let anunciar: jasmine.Spy;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();

  function pintar(): void {
    pintarCapas();
    fixture.detectChanges();
  }

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintar();
  }

  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const capa = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.eventos-capa');

  function botonEn(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').replace(/\s+/g, ' ').trim() === t || texto(x) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function avisos(): { tipo: string; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : 'otro',
      texto: a.querySelector('.pn-aviso__texto')!.textContent!.replace(/\s+/g, ' ').trim(),
    }));
  }

  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.eventos__fila'));
  const fila = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.eventos__fila[data-evento="${id}"]`)!;
  const orden = (): number[] => filas().map((f) => Number(f.dataset['evento']));
  const asa = (id: number): HTMLButtonElement => fila(id).querySelector<HTMLButtonElement>('.eventos__asa')!;
  const interruptor = (id: number): HTMLButtonElement => fila(id).querySelector<HTMLButtonElement>('[role="switch"]')!;
  const tecla = (destino: HTMLElement, key: string): void => {
    destino.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  function crear(): void {
    fixture = TestBed.createComponent(EventosPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function responderLista(lista: EventoPrueba[] = LISTA()): void {
    http.expectOne(`${API}/eventos/admin/todos`).flush(lista);
    fixture.detectChanges();
  }

  function cargar(lista?: EventoPrueba[]): void {
    crear();
    responderLista(lista);
  }

  function guardarOrden(): TestRequest {
    botonEn(el(), 'Guardar orden').click();
    fixture.detectChanges();
    return http.expectOne(`${API}/eventos/orden`);
  }

  beforeEach(async () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 22, 10, 0));
    quitarTokens = ponerTokensDePrueba();
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    await TestBed.configureTestingModule({
      imports: [EventosPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LoadingService, useValue: loading },
        { provide: PermisosSesionService, useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
    anunciar = spyOn(TestBed.inject(LiveAnnouncer), 'announce').and.returnValue(Promise.resolve());
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    quitarTokens();
    jasmine.clock().uninstall();
  });

  // ------------------------------------------------------------------ lectura y cabecera

  it('carga: GET /eventos/admin/todos como hoy (con credenciales y X-Skip-Loading), con ERROR_HANDLED_LOCALLY y sin cargador global', () => {
    crear();
    const r = http.expectOne(`${API}/eventos/admin/todos`);
    expect(r.request.method).toBe('GET');
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.headers.get('X-Skip-Loading')).toBe('true');
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    r.flush(LISTA());
    fixture.detectChanges();
    expect(filas().length).toBe(4);
    expect(loading.show).not.toHaveBeenCalled();
  });

  it('cabecera: «Eventos» con total y visibles, «Ver cómo queda en la web» y «Crear nuevo evento» como único primario', () => {
    cargar();
    expect(texto(el().querySelector('h1'))).toBe('Eventos');
    expect(texto(el().querySelector('.eventos__resumen'))).toBe('4 eventos, 3 visibles en la web');
    const primarios = el().querySelectorAll('.eventos__boton-primario');
    expect(primarios.length).toBe(1);
    expect(texto(primarios[0])).toBe('Crear nuevo evento');
    expect(primarios[0].getAttribute('href')).toBe('/eventosCrear');
    expect(texto(el().querySelector('.eventos__ver-web'))).toBe('Ver cómo queda en la web');
  });

  it('filas: miniatura (la de hoy, w=480), título, descripción, fecha con su estado, interruptor y «Editar» a la vista', () => {
    cargar();
    const f1 = fila(1);
    expect(f1.querySelector<HTMLImageElement>('.eventos__miniatura img')!.getAttribute('src')).toBe('https://img.test/f1.webp?w=480&v=901');
    expect(texto(f1.querySelector('.eventos__titulo'))).toBe('Campeonato regional');
    expect(texto(f1.querySelector('.eventos__descripcion'))).toBe('Descripción del evento 1');
    expect(texto(f1.querySelector('.eventos__fecha'))).toBe('14 nov 2026 Próximo');
    expect(f1.querySelector('.eventos__fecha')!.getAttribute('title')).toBe('Próximo: Sábado, 14 de noviembre de 2026');
    expect(texto(fila(2).querySelector('.eventos__estado'))).toBe('Hoy');
    expect(texto(fila(3).querySelector('.eventos__estado'))).toBe('Realizado');
    expect(texto(fila(4).querySelector('.eventos__fecha'))).toBe('Sin fecha');
    // Sin foto: la imagen por defecto, que no se amplía.
    expect(fila(4).querySelector('.eventos__miniatura button')).toBeNull();
    expect(fila(4).querySelector<HTMLImageElement>('.eventos__miniatura img')!.getAttribute('src')).toBe('assets/media/default.webp');
    expect(interruptor(1).getAttribute('aria-checked')).toBe('true');
    expect(interruptor(3).getAttribute('aria-checked')).toBe('false');
    expect(texto(interruptor(1))).toBe('Visible en la web');
    expect(interruptor(1).getAttribute('aria-label')).toBe('Visible en la web: Campeonato regional');
    const editar = fila(1).querySelector<HTMLAnchorElement>('.eventos__editar')!;
    expect(texto(editar)).toBe('Editar');
    expect(editar.getAttribute('href')).toBe('/eventosEditar/1');
    // «Eliminar» no está a la vista: va en «⋯».
    expect(fila(1).querySelector('.eventos__menu')).toBeNull();
    expect(botonEn(fila(1), 'Más acciones de Campeonato regional').getAttribute('aria-haspopup')).toBe('menu');
  });

  // ------------------------------------------------------------------ orden

  it('aviso bajo el título: explica cómo ordenar y, al reordenar, pasa a «Guardar orden»', () => {
    cargar();
    expect(texto(el().querySelector('.eventos__aviso'))).toContain('aparecerá «Guardar orden»');
    expect(el().querySelector('.eventos__aviso--pendiente')).toBeNull();
    botonEn(fila(1), 'Bajar Campeonato regional').click();
    fixture.detectChanges();
    expect(orden()).toEqual([2, 1, 3, 4]);
    const aviso = el().querySelector('.eventos__aviso--pendiente')!;
    expect(aviso).not.toBeNull();
    expect(texto(aviso)).toContain('El orden ha cambiado');
    expect(botonEn(aviso, 'Guardar orden')).toBeTruthy();
  });

  it('subir y bajar: la primera no sube y la última no baja; con flechas, el MISMO PUT /eventos/orden de hoy con los ids', async () => {
    cargar();
    expect(botonEn(fila(1), 'Subir Campeonato regional').disabled).toBeTrue();
    expect(botonEn(fila(4), 'Bajar Día del padre').disabled).toBeTrue();
    botonEn(fila(4), 'Subir Día del padre').click();
    fixture.detectChanges();
    expect(orden()).toEqual([1, 2, 4, 3]);
    const r = guardarOrden();
    expect(r.request.method).toBe('PUT');
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.body).toEqual([1, 2, 4, 3]);
    r.flush(null);
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Orden guardado.' }]);
    // Como hoy, vuelve a leer la lista.
    responderLista([evento(1), evento(2), evento(4), evento(3)]);
    expect(el().querySelector('.eventos__aviso--pendiente')).toBeNull();
  });

  it('teclado: flechas con el foco en el asa mueven la fila, el foco sigue en su asa y se anuncia con LiveAnnouncer', () => {
    cargar();
    asa(1).focus();
    tecla(asa(1), 'ArrowDown');
    expect(orden()).toEqual([2, 1, 3, 4]);
    expect(document.activeElement).toBe(asa(1));
    expect(anunciar).toHaveBeenCalledWith('Campeonato regional, posición 2 de 4.');
    tecla(asa(1), 'ArrowDown');
    tecla(asa(1), 'ArrowUp');
    expect(orden()).toEqual([2, 1, 3, 4]);
    tecla(asa(2), 'ArrowUp');
    expect(orden()).toEqual([2, 1, 3, 4]);
    expect(anunciar).toHaveBeenCalledWith('Exhibición ya es la primera.');
    expect(asa(1).getAttribute('aria-label')).toBe('Mover Campeonato regional');
    expect(asa(1).getAttribute('aria-describedby')).toBe('eventos-ayuda-orden');
    expect(texto(el().querySelector('#eventos-ayuda-orden'))).toContain('flechas arriba y abajo');
  });

  it('teclado: flechas con el foco en los botones de subir y bajar también mueven, y el foco sigue en su botón', () => {
    cargar();
    const bajar = botonEn(fila(2), 'Bajar Exhibición');
    bajar.focus();
    tecla(bajar, 'ArrowDown');
    expect(orden()).toEqual([1, 3, 2, 4]);
    expect(document.activeElement).toBe(botonEn(fila(2), 'Bajar Exhibición'));
    botonEn(fila(2), 'Subir Exhibición').click();
    fixture.detectChanges();
    expect(document.activeElement).toBe(botonEn(fila(2), 'Subir Exhibición'));
    expect(orden()).toEqual([1, 2, 3, 4]);
  });

  it('el orden con teclado y con arrastre mandan la MISMA petición', async () => {
    cargar();
    asa(1).focus();
    tecla(asa(1), 'ArrowDown');
    tecla(asa(1), 'ArrowDown');
    const conTeclado = guardarOrden();
    const cuerpoTeclado = conTeclado.request.body;
    conTeclado.flush(null);
    await esperar();
    responderLista();

    // Arrastre: el `cdkDropListDropped` de la lista, de la posición 0 a la 2.
    fixture.componentInstance.soltar({ previousIndex: 0, currentIndex: 2 } as never);
    fixture.detectChanges();
    expect(anunciar).toHaveBeenCalledWith('Campeonato regional, posición 3 de 4.');
    const conArrastre = guardarOrden();
    expect(conArrastre.request.method).toBe('PUT');
    expect(conArrastre.request.body).toEqual(cuerpoTeclado);
    expect(cuerpoTeclado).toEqual([2, 3, 1, 4]);
    conArrastre.flush(null);
    await esperar();
    responderLista();
  });

  it('el arrastre usa el asa como tirador', () => {
    cargar();
    expect(asa(1).hasAttribute('cdkdraghandle') || asa(1).classList.contains('cdk-drag-handle')).toBeTrue();
    expect(fila(1).classList).toContain('cdk-drag');
  });

  it('«Guardar orden»: sin doble envío; un error avisa con el texto de hoy y el orden sigue pendiente', async () => {
    cargar();
    botonEn(fila(1), 'Bajar Campeonato regional').click();
    fixture.detectChanges();
    const boton = botonEn(el(), 'Guardar orden');
    boton.click();
    fixture.detectChanges();
    expect(boton.getAttribute('aria-busy')).toBe('true');
    expect(texto(boton)).toContain('Guardando…');
    boton.click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/orden`);
    r.flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudieron guardar los cambios.' }]);
    expect(el().querySelector('.eventos__aviso--pendiente')).not.toBeNull();
    http.expectNone(`${API}/eventos/admin/todos`);
  });

  // ------------------------------------------------------------------ visible en la web

  it('interruptor: la MISMA llamada de hoy (PUT /eventos/{id}/toggle-visibilidad sin cuerpo), sin doble envío; cambia el resumen', async () => {
    cargar();
    interruptor(1).click();
    fixture.detectChanges();
    expect(interruptor(1).getAttribute('aria-busy')).toBe('true');
    interruptor(1).click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/eventos/1/toggle-visibilidad`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toBeNull();
    expect(r.request.withCredentials).toBeTrue();
    r.flush({});
    await esperar();
    expect(interruptor(1).getAttribute('aria-checked')).toBe('false');
    expect(texto(el().querySelector('.eventos__resumen'))).toBe('4 eventos, 2 visibles en la web');
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Campeonato regional ya no se ve en la web.' }]);
    // Publicar el oculto: la misma llamada.
    interruptor(3).click();
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/3/toggle-visibilidad`).flush({});
    await esperar();
    expect(interruptor(3).getAttribute('aria-checked')).toBe('true');
    expect(avisos().pop()).toEqual({ tipo: 'otro', texto: 'Seminario ya se ve en la web.' });
  });

  it('interruptor: no toca el orden pendiente; un error deja el estado de antes y avisa', async () => {
    cargar();
    botonEn(fila(1), 'Bajar Campeonato regional').click();
    fixture.detectChanges();
    interruptor(2).click();
    fixture.detectChanges();
    http.expectOne(`${API}/eventos/2/toggle-visibilidad`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(interruptor(2).getAttribute('aria-checked')).toBe('true');
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo cambiar la visibilidad del evento.' }]);
    expect(orden()).toEqual([2, 1, 3, 4]);
    expect(el().querySelector('.eventos__aviso--pendiente')).not.toBeNull();
  });

  // ------------------------------------------------------------------ «⋯» y eliminar

  it('«⋯»: botón de menú con «Eliminar»; flechas lo abren, Esc lo cierra y devuelve el foco', () => {
    cargar();
    const mas = botonEn(fila(1), 'Más acciones de Campeonato regional');
    expect(mas.getAttribute('aria-expanded')).toBe('false');
    mas.focus();
    tecla(mas, 'ArrowDown');
    const menu = fila(1).querySelector<HTMLElement>('[role="menu"]')!;
    expect(menu).not.toBeNull();
    expect(mas.getAttribute('aria-expanded')).toBe('true');
    expect(mas.getAttribute('aria-controls')).toBe(menu.id);
    expect(Array.from(menu.querySelectorAll('[role="menuitem"]')).map(texto)).toEqual(['Eliminar']);
    expect(document.activeElement).toBe(menu.querySelector('[role="menuitem"]'));
    tecla(document.activeElement as HTMLElement, 'Escape');
    expect(fila(1).querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(mas);
  });

  it('eliminar: confirmación destructiva con el foco en «Cancelar»; cancelar no envía; confirmar, el DELETE /eventos/{id} de hoy', async () => {
    cargar();
    botonEn(fila(3), 'Más acciones de Seminario').click();
    fixture.detectChanges();
    botonEn(fila(3), 'Eliminar').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar evento');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('Vas a eliminar «Seminario». No podrás revertir esto.');
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(texto(document.activeElement)).toBe('Cancelar');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'DELETE');

    botonEn(fila(3), 'Más acciones de Seminario').click();
    fixture.detectChanges();
    botonEn(fila(3), 'Eliminar').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminarlo').click();
    await esperar();
    const r = http.expectOne(`${API}/eventos/3`);
    expect(r.request.method).toBe('DELETE');
    expect(r.request.withCredentials).toBeTrue();
    r.flush(null);
    await esperar();
    expect(orden()).toEqual([1, 2, 4]);
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Evento eliminado correctamente.' }]);
    expect(texto(el().querySelector('.eventos__resumen'))).toBe('3 eventos, 3 visibles en la web');
  });

  it('eliminar: un error avisa y la fila sigue', async () => {
    cargar();
    botonEn(fila(1), 'Más acciones de Campeonato regional').click();
    fixture.detectChanges();
    botonEn(fila(1), 'Eliminar').click();
    pintar();
    botonEn(dialogo()!, 'Sí, eliminarlo').click();
    await esperar();
    http.expectOne(`${API}/eventos/1`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(orden()).toEqual([1, 2, 3, 4]);
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo eliminar el evento.' }]);
  });

  // ------------------------------------------------------------------ capas: web y miniatura

  it('«Ver cómo queda en la web»: capa modal con la vista pública (solo visibles, en el orden actual), foco contenido y Esc', () => {
    cargar();
    botonEn(fila(1), 'Bajar Campeonato regional').click();
    fixture.detectChanges();
    const boton = el().querySelector<HTMLButtonElement>('.eventos__ver-web')!;
    boton.focus();
    boton.click();
    pintar();
    const c = capa()!;
    expect(c.open).toBeTrue();
    expect(texto(c.querySelector('.eventos-capa__titulo'))).toBe('Cómo queda en la web');
    const vista = fixture.componentInstance.eventosWeb.map((e) => e.id);
    expect(vista).toEqual([2, 1, 4]);
    expect(c.querySelector('app-eventos-vista')).not.toBeNull();
    // Foco dentro y contenido.
    expect(c.contains(document.activeElement)).toBeTrue();
    const cerrar = c.querySelector<HTMLButtonElement>('.eventos-capa__cerrar')!;
    cerrar.focus();
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    cerrar.dispatchEvent(tab);
    expect(c.contains(document.activeElement)).toBeTrue();
    c.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    pintar();
    expect(capa()).toBeNull();
    expect(document.activeElement).toBe(boton);
  });

  it('miniatura: se amplía en una capa modal con la imagen sin «w» y se cierra con Esc', () => {
    cargar();
    const mini = botonEn(fila(1), 'Ampliar la imagen de Campeonato regional');
    mini.focus();
    mini.click();
    pintar();
    const c = capa()!;
    expect(c.querySelector<HTMLImageElement>('img')!.getAttribute('src')).toBe('https://img.test/f1.webp?v=901');
    expect(c.querySelector<HTMLImageElement>('img')!.getAttribute('alt')).toBe('Vista ampliada de Campeonato regional');
    c.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    pintar();
    expect(capa()).toBeNull();
    expect(document.activeElement).toBe(mini);
  });

  // ------------------------------------------------------------------ estados

  it('esqueleto a los 300 ms, y nada antes', () => {
    crear();
    jasmine.clock().tick(299);
    fixture.detectChanges();
    expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
    jasmine.clock().tick(1);
    fixture.detectChanges();
    expect(el().querySelectorAll('.eventos__esqueleto-fila').length).toBeGreaterThanOrEqual(3);
    expect(el().querySelector('pn-esqueleto [role="status"]')!.textContent).toContain('Cargando eventos');
    responderLista();
    expect(el().querySelector('pn-esqueleto')).toBeNull();
  });

  it('error: bloque de error con «Reintentar» (sin SweetAlert2) que vuelve a pedir la lista', () => {
    crear();
    http.expectOne(`${API}/eventos/admin/todos`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('pn-bloque-error'))).toContain('No se pudieron obtener los eventos');
    expect(Swal.isVisible()).toBeFalse();
    botonEn(el(), 'Reintentar').click();
    fixture.detectChanges();
    responderLista();
    expect(filas().length).toBe(4);
  });

  it('vacío: «No hay eventos disponibles» con «Crear nuevo evento»', () => {
    cargar([]);
    expect(texto(el().querySelector('pn-bloque-vacio'))).toContain('No hay eventos disponibles');
    expect(texto(el().querySelector('.eventos__resumen'))).toBe('Ningún evento');
    expect(el().querySelector('pn-bloque-vacio a')!.getAttribute('href')).toBe('/eventosCrear');
  });

  // ------------------------------------------------------------------ móvil

  it('a 375 px: ni la página ni las filas se desplazan en horizontal, con un título de 80 caracteres sin espacios', () => {
    const largo = 'Campeonatointerautonómicodeformasycombateparatodaslascategoríasinfantilesyadult';
    cargar([evento(1, { titulo: largo.slice(0, 80), fechaEvento: '2026-11-14' }), ...LISTA().slice(1)]);
    const host = el();
    host.style.display = 'block';
    host.style.width = '375px';
    fixture.detectChanges();
    const pagina = host.querySelector<HTMLElement>('.eventos')!;
    expect(pagina.scrollWidth).toBe(pagina.clientWidth);
    for (const f of filas()) {
      expect(f.scrollWidth).withContext(`fila ${f.dataset['evento']}`).toBe(f.clientWidth);
      expect(f.getBoundingClientRect().right).toBeLessThanOrEqual(host.getBoundingClientRect().right + 0.5);
    }
    // Los mandos de la fila caben enteros dentro de ella.
    const caja = fila(1).getBoundingClientRect();
    for (const b of Array.from(fila(1).querySelectorAll<HTMLElement>('button, a'))) {
      const r = b.getBoundingClientRect();
      expect(r.left).toBeGreaterThanOrEqual(caja.left - 0.5);
      expect(r.right).toBeLessThanOrEqual(caja.right + 0.5);
    }
  });
});
