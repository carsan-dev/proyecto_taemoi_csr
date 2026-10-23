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
import { ProductosComponent } from './productos.component';

const API = environment.apiUrl;
const URL_LISTA = `${API}/productos`;

type Tipo = 'MENSUALIDAD' | 'LICENCIA' | 'RESERVA_PLAZA' | 'COMPETIDOR' | 'EXAMEN' | 'RECOMPENSA' | 'OTRO';
const TIPOS: Tipo[] = ['MENSUALIDAD', 'LICENCIA', 'RESERVA_PLAZA', 'COMPETIDOR', 'EXAMEN', 'RECOMPENSA', 'OTRO'];

interface ProductoPrueba {
  id: number;
  concepto: string;
  precio: number;
  vendibleProfesor: boolean;
  tipo: Tipo;
}

const P = (id: number, concepto: string, precio: number, tipo: Tipo, vendibleProfesor = false): ProductoPrueba => ({
  id,
  concepto,
  precio,
  tipo,
  vendibleProfesor,
});

const LISTA = (): ProductoPrueba[] => [
  P(1, 'Mensualidad taekwondo', 30, 'MENSUALIDAD'),
  P(2, 'Mensualidad kickboxing', 28, 'MENSUALIDAD'),
  P(5, 'Licencia federativa', 45.5, 'LICENCIA'),
  P(9, 'Derecho de examen: blanco a amarillo', 25, 'EXAMEN'),
  P(10, 'Derecho de examen: amarillo a naranja', 25, 'EXAMEN'),
  P(14, 'Derecho de examen: 1.er dan', 120, 'EXAMEN'),
  P(17, 'Camiseta del club', 12.5, 'OTRO', true),
  P(19, 'Viaje al campeonato', 1010.5, 'OTRO'),
];

describe('ProductosComponent (8b-3g, listado de Productos del panel nuevo, P2 agrupado por tipo)', () => {
  let fixture: ComponentFixture<ProductosComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let loading: jasmine.SpyObj<LoadingService>;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');

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

  const grupos = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.productos__grupo'));
  const grupo = (tipo: Tipo): HTMLElement | null => el().querySelector<HTMLElement>(`.productos__grupo[data-tipo="${tipo}"]`);
  const fila = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.productos__fila[data-producto="${id}"]`)!;
  const idsDe = (tipo: Tipo): number[] =>
    Array.from(grupo(tipo)!.querySelectorAll<HTMLElement>('.productos__fila')).map((f) => Number(f.dataset['producto']));
  const casilla = (id: number): HTMLInputElement => fila(id).querySelector<HTMLInputElement>('.productos__casilla')!;
  const interruptor = (id: number): HTMLButtonElement => fila(id).querySelector<HTMLButtonElement>('[role="switch"]')!;
  const barra = (): HTMLElement | null => el().querySelector<HTMLElement>('.productos__seleccion');

  const pendientesLista = (): TestRequest[] => http.match((r) => r.method === 'GET' && r.url === URL_LISTA);

  /** Responde cada lectura pendiente como la API: filtra por tipo, concepto y Mi caja y pagina. */
  function responderLecturas(lista: ProductoPrueba[] = LISTA(), pendientes: TestRequest[] = pendientesLista()): TestRequest[] {
    for (const r of pendientes) {
      const p = r.request.params;
      let l = lista;
      const tipo = p.get('tipo');
      const concepto = p.get('concepto');
      const v = p.get('vendibleProfesor');
      if (tipo) {
        l = l.filter((x) => x.tipo === tipo);
      }
      if (concepto) {
        l = l.filter((x) => x.concepto.toLowerCase().includes(concepto.toLowerCase()));
      }
      if (v !== null) {
        l = l.filter((x) => x.vendibleProfesor === (v === 'true'));
      }
      const size = Number(p.get('size'));
      const page = Number(p.get('page'));
      const content = l.slice((page - 1) * size, page * size).map(({ tipo: _t, ...resto }) => resto);
      r.flush({ content, totalElements: l.length, totalPages: Math.ceil(l.length / size), number: page - 1, size });
    }
    fixture.detectChanges();
    return pendientes;
  }

  function crear(): void {
    retenidas.length = 0;
    fixture = TestBed.createComponent(ProductosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function cargar(lista?: ProductoPrueba[]): void {
    crear();
    responderLecturas(lista);
  }

  function buscar(valor: string): void {
    const campo = el().querySelector<HTMLInputElement>('#productos-buscar')!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function elegir(id: string, valor: string): void {
    const sel = el().querySelector<HTMLSelectElement>(`#${id}`)!;
    sel.value = valor;
    sel.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function marcar(id: number): void {
    casilla(id).click();
    fixture.detectChanges();
  }

  const tecla = (destino: HTMLElement, key: string): void => {
    destino.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  };

  beforeEach(async () => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    sessionStorage.removeItem('panelProductosEstado');
    sessionStorage.removeItem('listadoProductosEstado');
    jasmine.clock().install();
    // La búsqueda espera 300 ms con `debounceTime`, que mira `Date.now()`: el reloj de prueba también lo mueve.
    jasmine.clock().mockDate(new Date(2026, 9, 22, 10, 0));
    quitarTokens = ponerTokensDePrueba();
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    await TestBed.configureTestingModule({
      imports: [ProductosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LoadingService, useValue: loading },
        { provide: PermisosSesionService, useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    sessionStorage.removeItem('panelProductosEstado');
    quitarTokens();
    jasmine.clock().uninstall();
  });

  // ------------------------------------------------------------------ lectura

  it('carga: una lectura por tipo con la petición de hoy (GET /productos con página, tamaño, orden y tipo), ERROR_HANDLED_LOCALLY y sin cargador global', () => {
    crear();
    const pendientes = pendientesLista();
    expect(pendientes.map((r) => r.request.params.get('tipo'))).toEqual(TIPOS);
    for (const r of pendientes) {
      expect(r.request.withCredentials).toBeTrue();
      expect(r.request.headers.get('X-Skip-Loading')).toBe('true');
      expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      expect(r.request.params.keys()).toEqual(['page', 'size', 'orderBy', 'order', 'tipo']);
      expect(r.request.params.get('page')).toBe('1');
      expect(r.request.params.get('size')).toBe('200');
      // Por id (único): un orden por concepto (repetible) podría repetir u omitir filas entre páginas.
      expect(r.request.params.get('orderBy')).toBe('id');
      expect(r.request.params.get('order')).toBe('asc');
    }
    responderLecturas(LISTA(), pendientes);
    expect(el().querySelectorAll('.productos__fila').length).toBe(8);
    expect(el().querySelector('.productos__pie')).toBeNull();
    expect(loading.show).not.toHaveBeenCalled();
    http.verify();
  });

  // ------------------------------------------------------------------ «Ver los N restantes»

  const examenes = (n: number): ProductoPrueba[] => Array.from({ length: n }, (_, i) => P(100 + i, `Examen ${i}`, 25, 'EXAMEN'));
  const pie = (tipo: Tipo): HTMLButtonElement | null => grupo(tipo)!.querySelector<HTMLButtonElement>('.productos__pie button');
  /** `http.match` retira lo que devuelve: lo pendiente se guarda aquí para poder mirarlo más de una vez. */
  const retenidas: TestRequest[] = [];
  const nombre = (r: TestRequest): string => `${r.request.params.get('tipo')}:${r.request.params.get('page')}`;
  const paginasPendientes = (): string[] => {
    retenidas.push(...pendientesLista());
    return retenidas.map(nombre);
  };

  /** Atiende la lectura pendiente de esa página y tipo (solo debe haber una) y deja las demás. */
  function responderPagina(lista: ProductoPrueba[], pagina: string, tipo: Tipo = 'EXAMEN'): void {
    retenidas.push(...pendientesLista());
    const hay = retenidas.filter((r) => nombre(r) === `${tipo}:${pagina}`);
    expect(hay.length).toBe(1);
    hay.forEach((r) => retenidas.splice(retenidas.indexOf(r), 1));
    responderLecturas(lista, hay);
  }

  it('al abrir: solo la primera página de cada grupo; nada de las siguientes aunque haya más', () => {
    const muchos = examenes(450);
    crear();
    responderLecturas(muchos);
    http.expectNone(() => true);
    expect(idsDe('EXAMEN').length).toBe(50);
  });

  it('50 filas por grupo y «Ver los N restantes» con el número real (totalElements); cabecera y total también', () => {
    cargar(examenes(450));
    expect(idsDe('EXAMEN').length).toBe(50);
    expect(texto(pie('EXAMEN'))).toBe('Ver los 400 restantes');
    expect(pie('EXAMEN')!.getAttribute('aria-label')).toBe('Ver los 400 restantes de Derechos de examen');
    expect(texto(grupo('EXAMEN')!.querySelector('.productos__numero'))).toBe('450');
    expect(grupo('EXAMEN')!.querySelector('.productos__numero')!.getAttribute('aria-label')).toBe('450 productos');
    expect(texto(el().querySelector('.productos__total'))).toBe('450 productos');
    // Dentro de la tarjeta del grupo, justo bajo la lista, y solo en el grupo que lo necesita.
    expect(grupo('EXAMEN')!.querySelector('.productos__lista')!.nextElementSibling!.classList).toContain('productos__pie');
  });

  it('el total suma el de cada grupo (totalElements), no las filas leídas', () => {
    cargar([...examenes(450), P(1, 'Mensualidad taekwondo', 30, 'MENSUALIDAD')]);
    expect(texto(el().querySelector('.productos__total'))).toBe('451 productos');
  });

  it('«Ver el restante» en singular cuando falta una', () => {
    cargar(examenes(51));
    expect(texto(pie('EXAMEN'))).toBe('Ver el restante');
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    expect(idsDe('EXAMEN').length).toBe(51);
    expect(pie('EXAMEN')).toBeNull();
    http.expectNone(() => true);
  });

  it('pulsarlo con todo ya leído enseña el grupo entero sin pedir nada y deja el foco en la primera fila nueva', () => {
    cargar(examenes(120));
    expect(texto(pie('EXAMEN'))).toBe('Ver los 70 restantes');
    pie('EXAMEN')!.focus();
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    http.expectNone(() => true);
    expect(idsDe('EXAMEN').length).toBe(120);
    expect(pie('EXAMEN')).toBeNull();
    // La fila 51 del orden por concepto («Examen 50»).
    expect(document.activeElement).toBe(casilla(150));
  });

  it('pide las páginas 2..n de ESE grupo una tras otra: la siguiente solo cuando acaba la anterior; botón ocupado sin doble clic', () => {
    const muchos = [...examenes(450), P(1, 'Mensualidad taekwondo', 30, 'MENSUALIDAD')];
    cargar(muchos);
    const boton = pie('EXAMEN')!;
    boton.focus();
    boton.click();
    fixture.detectChanges();
    // Se ven ya las 200 leídas y solo se pide la página 2 de EXAMEN.
    expect(idsDe('EXAMEN').length).toBe(200);
    expect(paginasPendientes()).toEqual(['EXAMEN:2']);
    expect(pie('EXAMEN')!.getAttribute('aria-busy')).toBe('true');
    expect(texto(pie('EXAMEN'))).toBe('Cargando…');
    // Un segundo clic no manda otra.
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    expect(paginasPendientes()).toEqual(['EXAMEN:2']);
    responderPagina(muchos, '2');
    expect(idsDe('EXAMEN').length).toBe(400);
    expect(paginasPendientes()).toEqual(['EXAMEN:3']);
    expect(document.activeElement).toBe(casilla(150));
    responderPagina(muchos, '3');
    expect(idsDe('EXAMEN').length).toBe(450);
    expect(pie('EXAMEN')).toBeNull();
    http.expectNone(() => true);
    expect(idsDe('MENSUALIDAD')).toEqual([1]);
  });

  it('un error deja lo leído, avisa y el botón sirve para reintentar desde la página que faltaba', async () => {
    const muchos = examenes(450);
    cargar(muchos);
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    const r = http.expectOne((x) => x.url === URL_LISTA);
    expect(r.request.params.get('page')).toBe('2');
    r.flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudieron cargar los productos restantes.' }]);
    expect(idsDe('EXAMEN').length).toBe(200);
    expect(texto(pie('EXAMEN'))).toBe('Ver los 250 restantes');
    expect(pie('EXAMEN')!.getAttribute('aria-busy')).toBeNull();
    http.expectNone(() => true);
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    expect(paginasPendientes()).toEqual(['EXAMEN:2']);
    responderPagina(muchos, '2');
    responderPagina(muchos, '3');
    expect(idsDe('EXAMEN').length).toBe(450);
  });

  it('una carga nueva cancela la lectura en vuelo y vuelve a 50 filas por grupo', () => {
    const muchos = examenes(450);
    cargar(muchos);
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    const enVuelo = http.expectOne((x) => x.url === URL_LISTA);
    elegir('productos-mi-caja', 'false');
    expect(enVuelo.cancelled).toBeTrue();
    responderLecturas(muchos);
    expect(idsDe('EXAMEN').length).toBe(50);
    expect(texto(pie('EXAMEN'))).toBe('Ver los 400 restantes');
  });

  it('destruir cancela la lectura en vuelo', () => {
    cargar(examenes(450));
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    const enVuelo = http.expectOne((x) => x.url === URL_LISTA);
    fixture.destroy();
    expect(enVuelo.cancelled).toBeTrue();
  });

  it('«seleccionar todos» de un grupo solo marca las filas que se ven; el estado se calcula sobre ellas', () => {
    cargar(examenes(120));
    const todos = grupo('EXAMEN')!.querySelector<HTMLInputElement>('.productos__casilla-grupo')!;
    expect(todos.getAttribute('aria-label')).toBe('Seleccionar los 50 de Derechos de examen');
    todos.click();
    fixture.detectChanges();
    expect(texto(barra()!.querySelector('.productos__seleccion-texto'))).toBe('50 productos seleccionados');
    expect(todos.checked).toBeTrue();
    // Al enseñar el resto, ya no están todas marcadas; las 50 siguen seleccionadas.
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    expect(todos.checked).toBeFalse();
    expect(todos.indeterminate).toBeTrue();
    expect(texto(barra()!.querySelector('.productos__seleccion-texto'))).toBe('50 productos seleccionados');
    expect(todos.getAttribute('aria-label')).toBe('Seleccionar los 120 de Derechos de examen');
  });

  it('relectura silenciosa con la página 2 en vuelo: cancela la vieja, vuelve a pedir la 1 y la 2 solo después', () => {
    const lista = examenes(450);
    cargar(lista);
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    const vieja = http.expectOne((x) => x.url === URL_LISTA);
    expect(vieja.request.params.get('page')).toBe('2');
    fixture.componentInstance.cargar(true);
    fixture.detectChanges();
    expect(vieja.cancelled).toBeTrue();
    expect(paginasPendientes()).toEqual(TIPOS.map((t) => `${t}:1`));
    responderLecturas(lista, retenidas.splice(0));
    expect(paginasPendientes()).toEqual(['EXAMEN:2']);
    responderPagina(lista, '2');
    expect(paginasPendientes()).toEqual(['EXAMEN:3']);
    responderPagina(lista, '3');
    expect(idsDe('EXAMEN').length).toBe(450);
    expect(pie('EXAMEN')).toBeNull();
  });

  it('pulsar el pie durante una relectura silenciosa no pide nada más y el botón sigue ocupado', () => {
    const lista = examenes(450);
    cargar(lista);
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    http.expectOne((x) => x.url === URL_LISTA);
    fixture.componentInstance.cargar(true);
    fixture.detectChanges();
    const primeras = pendientesLista();
    expect(primeras.length).toBe(7);
    expect(pie('EXAMEN')!.getAttribute('aria-busy')).toBe('true');
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    http.expectNone(() => true);
    responderLecturas(lista, primeras);
    expect(paginasPendientes()).toEqual(['EXAMEN:2']);
  });

  it('relectura silenciosa: el grupo abierto relee sus páginas en serie y sigue abierto; el cerrado, solo la primera', () => {
    const lista = [...examenes(250), ...Array.from({ length: 300 }, (_, i) => P(1000 + i, `Otro ${i}`, 5, 'OTRO'))];
    cargar(lista);
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    responderPagina(lista, '2');
    expect(idsDe('EXAMEN').length).toBe(250);
    const c = fixture.componentInstance;
    c.cargar(true);
    fixture.detectChanges();
    // Sin esqueleto y las siete primeras páginas a la vez; las siguientes, solo tras la primera de su tipo.
    expect(el().querySelector('pn-esqueleto')).toBeNull();
    expect(paginasPendientes()).toEqual(TIPOS.map((t) => `${t}:1`));
    responderLecturas(lista, retenidas.splice(0));
    expect(paginasPendientes()).toEqual(['EXAMEN:2']);
    responderPagina(lista, '2');
    expect(idsDe('EXAMEN').length).toBe(250);
    expect(pie('EXAMEN')).toBeNull();
    expect(idsDe('OTRO').length).toBe(50);
    expect(texto(pie('OTRO'))).toBe('Ver los 250 restantes');
    http.expectNone(() => true);
  });

  it('cabecera: «Productos» con para qué sirven y «Crear producto» como único primario', () => {
    cargar();
    expect(texto(el().querySelector('h1'))).toBe('Productos');
    expect(texto(el().querySelector('.productos__subtitulo'))).toBe(
      'Lo que se cobra a los alumnos y lo que el profesorado puede vender desde Mi caja.'
    );
    const primarios = el().querySelectorAll('.productos__boton-primario');
    expect(primarios.length).toBe(1);
    expect(texto(primarios[0])).toBe('Crear producto');
    expect(primarios[0].getAttribute('href')).toBe('/productosCrear');
  });

  it('grupos por tipo en su orden, cada uno con su número; los vacíos no salen; total junto a los filtros', () => {
    cargar();
    expect(grupos().map((g) => g.dataset['tipo'])).toEqual(['MENSUALIDAD', 'LICENCIA', 'EXAMEN', 'OTRO']);
    expect(grupos().map((g) => texto(g.querySelector('h2')))).toEqual([
      'Mensualidades 2',
      'Licencias 1',
      'Derechos de examen 3',
      'Otros productos 2',
    ]);
    expect(grupo('EXAMEN')!.querySelector('.productos__numero')!.getAttribute('aria-label')).toBe('3 productos');
    expect(texto(el().querySelector('.productos__total'))).toBe('8 productos');
    // Sin páginas.
    expect(el().querySelector('app-paginacion, .paginacion')).toBeNull();
  });

  it('fila corta: casilla, concepto (enlace a editar), «nº», precio es-ES a la derecha, interruptor «Mi caja» y «⋯»', () => {
    cargar();
    const f = fila(19);
    expect(casilla(19).type).toBe('checkbox');
    expect(casilla(19).getAttribute('aria-label')).toBe('Seleccionar Viaje al campeonato');
    const enlace = f.querySelector<HTMLAnchorElement>('.productos__concepto')!;
    expect(texto(enlace)).toBe('Viaje al campeonato');
    expect(enlace.getAttribute('href')).toBe('/productosEditar/19');
    expect(texto(f.querySelector('.productos__id'))).toBe('nº 19');
    expect(texto(f.querySelector('.productos__precio'))).toBe('1.010,50 €');
    expect(interruptor(19).getAttribute('aria-checked')).toBe('false');
    expect(interruptor(17).getAttribute('aria-checked')).toBe('true');
    expect(texto(interruptor(17))).toBe('Mi caja');
    expect(interruptor(17).getAttribute('aria-label')).toBe('Mi caja: Camiseta del club');
    expect(f.querySelector('.productos__menu')).toBeNull();
    expect(botonEn(f, 'Más acciones de Viaje al campeonato').getAttribute('aria-haspopup')).toBe('menu');
  });

  it('dentro de cada grupo, por concepto de la A a la Z (el orden por defecto de hoy)', () => {
    cargar();
    expect(idsDe('EXAMEN')).toEqual([14, 10, 9]);
    expect(idsDe('MENSUALIDAD')).toEqual([2, 1]);
  });

  // ------------------------------------------------------------------ menús

  it('«⋯»: menú con «Editar» (a la ficha) y «Eliminar»; flechas lo abren, Esc lo cierra y devuelve el foco', () => {
    cargar();
    const mas = botonEn(fila(5), 'Más acciones de Licencia federativa');
    mas.focus();
    tecla(mas, 'ArrowDown');
    const menu = fila(5).querySelector<HTMLElement>('.productos__menu')!;
    expect(menu.getAttribute('role')).toBe('menu');
    expect(mas.getAttribute('aria-expanded')).toBe('true');
    const opciones = Array.from(menu.querySelectorAll<HTMLElement>('[role="menuitem"]'));
    expect(opciones.map(texto)).toEqual(['Editar', 'Eliminar']);
    expect(opciones[0].getAttribute('href')).toBe('/productosEditar/5');
    expect(document.activeElement).toBe(opciones[0]);
    tecla(opciones[0], 'ArrowDown');
    expect(document.activeElement).toBe(opciones[1]);
    tecla(opciones[1], 'Escape');
    expect(fila(5).querySelector('.productos__menu')).toBeNull();
    expect(document.activeElement).toBe(mas);
  });

  it('ordenar: un menú en cada grupo (ID, concepto o precio); cambia solo ese grupo, sin volver a pedir nada', () => {
    cargar();
    const boton = grupo('EXAMEN')!.querySelector<HTMLButtonElement>('.productos__ordenar')!;
    expect(texto(boton)).toBe('Concepto, de la A a la Z');
    expect(boton.getAttribute('aria-label')).toBe('Ordenar Derechos de examen: Concepto, de la A a la Z');
    boton.focus();
    tecla(boton, 'ArrowDown');
    const menu = grupo('EXAMEN')!.querySelector<HTMLElement>('.productos__menu')!;
    const opciones = Array.from(menu.querySelectorAll<HTMLElement>('[role="menuitem"]'));
    expect(opciones.length).toBe(6);
    expect(opciones[0].getAttribute('aria-current')).toBe('true');
    tecla(opciones[0], 'End');
    expect(document.activeElement).toBe(opciones[5]);
    botonEn(menu, 'Precio, de mayor a menor').click();
    fixture.detectChanges();
    expect(idsDe('EXAMEN')).toEqual([14, 9, 10]);
    expect(texto(boton)).toBe('Precio, de mayor a menor');
    expect(document.activeElement).toBe(boton);
    // Los demás grupos siguen igual y no hay lecturas nuevas.
    expect(idsDe('MENSUALIDAD')).toEqual([2, 1]);
    http.expectNone(() => true);
    boton.click();
    fixture.detectChanges();
    botonEn(grupo('EXAMEN')!, 'Nº, de menor a mayor').click();
    fixture.detectChanges();
    expect(idsDe('EXAMEN')).toEqual([9, 10, 14]);
  });

  // ------------------------------------------------------------------ filtros

  it('buscador: a los 300 ms vuelve a pedir cada tipo con el concepto de hoy; chip y total', () => {
    cargar();
    buscar('examen');
    jasmine.clock().tick(299);
    expect(pendientesLista().length).toBe(0);
    jasmine.clock().tick(1);
    const pendientes = pendientesLista();
    expect(pendientes.length).toBe(7);
    expect(pendientes.every((r) => r.request.params.get('concepto') === 'examen')).toBeTrue();
    expect(pendientes[0].request.params.keys()).toEqual(['page', 'size', 'orderBy', 'order', 'concepto', 'tipo']);
    responderLecturas(LISTA(), pendientes);
    expect(grupos().map((g) => g.dataset['tipo'])).toEqual(['EXAMEN']);
    expect(texto(el().querySelector('.productos__total'))).toBe('3 productos');
    expect(Array.from(el().querySelectorAll('.productos__chip')).map(texto)).toEqual(['«examen»']);
  });

  it('desplegable «Tipo»: pide solo ese tipo; «Mi caja»: vendibleProfesor como hoy; chips con quitar y «Limpiar filtros»', () => {
    cargar();
    const limpiar = botonEn(el(), 'Limpiar filtros');
    expect(limpiar.disabled).toBeTrue();
    elegir('productos-tipo', 'OTRO');
    let pendientes = pendientesLista();
    expect(pendientes.map((r) => r.request.params.get('tipo'))).toEqual(['OTRO']);
    responderLecturas(LISTA(), pendientes);
    elegir('productos-mi-caja', 'false');
    pendientes = pendientesLista();
    expect(pendientes.length).toBe(1);
    expect(pendientes[0].request.params.get('vendibleProfesor')).toBe('false');
    expect(pendientes[0].request.params.keys()).toEqual(['page', 'size', 'orderBy', 'order', 'tipo', 'vendibleProfesor']);
    responderLecturas(LISTA(), pendientes);
    expect(grupos().map((g) => g.dataset['tipo'])).toEqual(['OTRO']);
    expect(idsDe('OTRO')).toEqual([19]);
    expect(Array.from(el().querySelectorAll('.productos__chip')).map(texto)).toEqual(['Tipo: Otro', 'Mi caja: No']);
    botonEn(el(), 'Quitar filtro Tipo: Otro').click();
    fixture.detectChanges();
    pendientes = pendientesLista();
    expect(pendientes.length).toBe(7);
    expect(pendientes.every((r) => r.request.params.get('vendibleProfesor') === 'false')).toBeTrue();
    responderLecturas(LISTA(), pendientes);
    expect(botonEn(el(), 'Limpiar filtros').disabled).toBeFalse();
    botonEn(el(), 'Limpiar filtros').click();
    fixture.detectChanges();
    pendientes = pendientesLista();
    expect(pendientes.length).toBe(7);
    expect(pendientes.every((r) => !r.request.params.has('vendibleProfesor') && !r.request.params.has('concepto'))).toBeTrue();
    responderLecturas(LISTA(), pendientes);
    expect(el().querySelectorAll('.productos__chip').length).toBe(0);
    expect(el().querySelector<HTMLSelectElement>('#productos-tipo')!.value).toBe('');
  });

  it('recuerda filtros y orden de cada grupo en la sesión (clave propia, no la de la pantalla de hoy)', () => {
    cargar();
    elegir('productos-mi-caja', 'true');
    responderLecturas();
    grupo('OTRO')!.querySelector<HTMLButtonElement>('.productos__ordenar')!.click();
    fixture.detectChanges();
    botonEn(grupo('OTRO')!, 'Precio, de mayor a menor').click();
    fixture.detectChanges();
    expect(sessionStorage.getItem('listadoProductosEstado')).toBeNull();
    fixture.destroy();
    crear();
    const pendientes = pendientesLista();
    expect(pendientes.every((r) => r.request.params.get('vendibleProfesor') === 'true')).toBeTrue();
    responderLecturas([...LISTA(), P(18, 'Protecciones', 85.9, 'OTRO', true)], pendientes);
    expect(el().querySelector<HTMLSelectElement>('#productos-mi-caja')!.value).toBe('true');
    expect(idsDe('OTRO')).toEqual([18, 17]);
  });

  // ------------------------------------------------------------------ estados

  it('vacío sin filtros: «No hay productos disponibles» con «Crear producto»', () => {
    cargar([]);
    const vacio = el().querySelector('pn-bloque-vacio')!;
    expect(texto(vacio.querySelector('.pn-bloque-vacio__titulo'))).toBe('No hay productos disponibles');
    expect(vacio.querySelector('a')!.getAttribute('href')).toBe('/productosCrear');
    expect(texto(el().querySelector('.productos__total'))).toBe('0 productos');
  });

  it('vacío con filtros: «Ningún producto coincide con los filtros» con «Limpiar filtros»', () => {
    cargar();
    buscar('zzz');
    jasmine.clock().tick(300);
    responderLecturas();
    const vacio = el().querySelector('pn-bloque-vacio')!;
    expect(texto(vacio.querySelector('.pn-bloque-vacio__titulo'))).toBe('Ningún producto coincide con los filtros');
    botonEn(vacio, 'Limpiar filtros').click();
    fixture.detectChanges();
    expect(pendientesLista().length).toBe(7);
  });

  it('esqueleto a los 300 ms (no antes); error con «Reintentar» que vuelve a pedir; sin SweetAlert2', () => {
    crear();
    expect(el().querySelector('pn-esqueleto')).not.toBeNull();
    jasmine.clock().tick(299);
    fixture.detectChanges();
    expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
    jasmine.clock().tick(1);
    fixture.detectChanges();
    expect(el().querySelector('.pn-esqueleto__forma .productos__esqueleto-grupo')).not.toBeNull();
    const pendientes = pendientesLista();
    pendientes[0].flush({}, { status: 500, statusText: 'Error' });
    for (const r of pendientes.slice(1)) {
      if (!r.cancelled) {
        r.flush({ content: [], totalElements: 0, totalPages: 0 });
      }
    }
    fixture.detectChanges();
    const error = el().querySelector('pn-bloque-error')!;
    expect(error).not.toBeNull();
    expect(texto(error)).toContain('No se pudieron cargar los productos');
    expect(document.querySelector('.swal2-container')).toBeNull();
    botonEn(error, 'Reintentar').click();
    fixture.detectChanges();
    expect(pendientesLista().length).toBe(7);
  });

  // ------------------------------------------------------------------ Mi caja

  it('interruptor «Mi caja»: al momento, el PATCH /productos/{id}/vendible-profesor de hoy; sin doble envío; aviso', async () => {
    cargar();
    interruptor(19).click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/productos/19/vendible-profesor`);
    expect(r.request.method).toBe('PATCH');
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.body).toEqual({ vendibleProfesor: true });
    expect(interruptor(19).getAttribute('aria-busy')).toBe('true');
    interruptor(19).click();
    fixture.detectChanges();
    http.expectNone(`${API}/productos/19/vendible-profesor`);
    r.flush({ id: 19, concepto: 'Viaje al campeonato', precio: 1010.5, vendibleProfesor: true });
    await esperar();
    expect(interruptor(19).getAttribute('aria-checked')).toBe('true');
    expect(interruptor(19).getAttribute('aria-busy')).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: '«Viaje al campeonato»: ya se puede vender desde Mi caja.' }]);
    interruptor(17).click();
    fixture.detectChanges();
    const quitar = http.expectOne(`${API}/productos/17/vendible-profesor`);
    expect(quitar.request.body).toEqual({ vendibleProfesor: false });
    quitar.flush({ id: 17, vendibleProfesor: false });
    await esperar();
    expect(interruptor(17).getAttribute('aria-checked')).toBe('false');
    expect(avisos()[1]).toEqual({ tipo: 'otro', texto: '«Camiseta del club»: ya no sale en Mi caja.' });
  });

  it('interruptor «Mi caja»: un error deja el valor de antes y avisa con el mensaje del servidor (o el de hoy)', async () => {
    cargar();
    interruptor(17).click();
    fixture.detectChanges();
    http.expectOne(`${API}/productos/17/vendible-profesor`).flush({ mensaje: 'Producto no encontrado con id 17' }, { status: 404, statusText: 'No' });
    await esperar();
    expect(interruptor(17).getAttribute('aria-checked')).toBe('true');
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'Producto no encontrado con id 17' }]);
    interruptor(19).click();
    fixture.detectChanges();
    http.expectOne(`${API}/productos/19/vendible-profesor`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()[1]).toEqual({ tipo: 'error', texto: 'No se pudo cambiar «Mi caja» de «Viaje al campeonato».' });
  });

  // ------------------------------------------------------------------ selección y barra al pie

  it('selección: la barra fija al pie sale con los seleccionados; «Seleccionar todos» de un grupo y «Quitar selección»', () => {
    cargar();
    expect(barra()).toBeNull();
    marcar(17);
    expect(barra()).not.toBeNull();
    expect(barra()!.getAttribute('role')).toBe('region');
    expect(texto(barra()!.querySelector('.productos__seleccion-texto'))).toBe('1 producto seleccionado');
    const todos = grupo('EXAMEN')!.querySelector<HTMLInputElement>('.productos__casilla-grupo')!;
    expect(todos.getAttribute('aria-label')).toBe('Seleccionar los 3 de Derechos de examen');
    todos.click();
    fixture.detectChanges();
    expect([9, 10, 14].every((id) => casilla(id).checked)).toBeTrue();
    expect(texto(barra()!.querySelector('.productos__seleccion-texto'))).toBe('4 productos seleccionados');
    expect(Array.from(barra()!.querySelectorAll('button')).map(texto)).toEqual([
      'Quitar selección',
      'Dar a Mi caja',
      'Quitar de Mi caja',
      'Eliminar',
    ]);
    marcar(10);
    expect(todos.checked).toBeFalse();
    expect(todos.indeterminate).toBeTrue();
    botonEn(barra()!, 'Quitar selección').click();
    fixture.detectChanges();
    expect(barra()).toBeNull();
    expect(casilla(17).checked).toBeFalse();
  });

  it('«Dar a Mi caja» y «Quitar de Mi caja»: el PATCH /productos/vendible-profesor de hoy con los ids; sin doble envío; deselecciona', async () => {
    cargar();
    marcar(19);
    marcar(9);
    botonEn(barra()!, 'Dar a Mi caja').click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/productos/vendible-profesor`);
    expect(r.request.method).toBe('PATCH');
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.body).toEqual({ ids: [9, 19], vendibleProfesor: true });
    expect(botonEn(barra()!, 'Guardando…').getAttribute('aria-busy')).toBe('true');
    expect(botonEn(barra()!, 'Quitar de Mi caja').disabled).toBeTrue();
    expect(botonEn(barra()!, 'Eliminar').disabled).toBeTrue();
    botonEn(barra()!, 'Quitar de Mi caja').click();
    botonEn(barra()!, 'Guardando…').click();
    fixture.detectChanges();
    http.expectNone(`${API}/productos/vendible-profesor`);
    r.flush({ actualizados: 2 });
    await esperar();
    expect(interruptor(19).getAttribute('aria-checked')).toBe('true');
    expect(interruptor(9).getAttribute('aria-checked')).toBe('true');
    expect(barra()).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: '2 productos ahora en Mi caja.' }]);
    marcar(17);
    botonEn(barra()!, 'Quitar de Mi caja').click();
    fixture.detectChanges();
    const quitar = http.expectOne(`${API}/productos/vendible-profesor`);
    expect(quitar.request.body).toEqual({ ids: [17], vendibleProfesor: false });
    quitar.flush({ actualizados: 1 });
    await esperar();
    expect(interruptor(17).getAttribute('aria-checked')).toBe('false');
    expect(avisos()[1]).toEqual({ tipo: 'otro', texto: '1 producto quitado de Mi caja.' });
  });

  it('Mi caja en lote: más de 200 seleccionados no envía nada y avisa; 200 sí (todo o nada de la API)', async () => {
    crear();
    responderLecturas(examenes(201));
    pie('EXAMEN')!.click();
    fixture.detectChanges();
    responderLecturas(examenes(201), pendientesLista());
    grupo('EXAMEN')!.querySelector<HTMLInputElement>('.productos__casilla-grupo')!.click();
    fixture.detectChanges();
    expect(texto(barra()!.querySelector('.productos__seleccion-texto'))).toBe('201 productos seleccionados');
    botonEn(barra()!, 'Dar a Mi caja').click();
    await esperar();
    http.expectNone(`${API}/productos/vendible-profesor`);
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'Selecciona como máximo 200 productos a la vez.' }]);
    expect(barra()).not.toBeNull();
    expect(botonEn(barra()!, 'Dar a Mi caja').disabled).toBeFalse();

    casilla(100).click();
    fixture.detectChanges();
    botonEn(barra()!, 'Quitar de Mi caja').click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/productos/vendible-profesor`);
    expect((r.request.body as { ids: number[] }).ids.length).toBe(200);
    r.flush({ actualizados: 200 });
    await esperar();
  });

  it('Mi caja en lote: un error no cambia nada, la selección sigue y avisa con el mensaje del servidor', async () => {
    cargar();
    marcar(19);
    botonEn(barra()!, 'Dar a Mi caja').click();
    fixture.detectChanges();
    http.expectOne(`${API}/productos/vendible-profesor`).flush({ mensaje: 'Como máximo 200 productos a la vez.' }, { status: 400, statusText: 'Bad' });
    await esperar();
    expect(interruptor(19).getAttribute('aria-checked')).toBe('false');
    expect(casilla(19).checked).toBeTrue();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'Como máximo 200 productos a la vez.' }]);
  });

  // ------------------------------------------------------------------ eliminar

  it('eliminar uno: destructivo con el foco en «Cancelar»; cancelar no envía; confirmar, el DELETE /productos/{id} de hoy y relee', async () => {
    cargar();
    botonEn(fila(19), 'Más acciones de Viaje al campeonato').click();
    fixture.detectChanges();
    botonEn(fila(19), 'Eliminar').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar producto');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('Vas a eliminar «Viaje al campeonato». Esta acción no se puede deshacer.');
    expect(document.activeElement).toBe(botonEn(dialogo()!, 'Cancelar'));
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone(() => true);

    botonEn(fila(19), 'Más acciones de Viaje al campeonato').click();
    fixture.detectChanges();
    botonEn(fila(19), 'Eliminar').click();
    await esperar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    pintar();
    const r = http.expectOne(`${API}/productos/19`);
    expect(r.request.method).toBe('DELETE');
    expect(r.request.withCredentials).toBeTrue();
    // Ocupado mientras va: un segundo clic no manda otro.
    botonEn(dialogo()!, 'Eliminando…').click();
    http.expectNone(`${API}/productos/19`);
    r.flush(null, { status: 204, statusText: 'No Content' });
    await esperar();
    expect(dialogo()).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Producto eliminado correctamente.' }]);
    // Relee, como hoy, sin esqueleto.
    responderLecturas(LISTA().filter((p) => p.id !== 19));
    expect(el().querySelector('[data-producto="19"]')).toBeNull();
  });

  it('eliminar uno: un error avisa y la fila sigue', async () => {
    cargar();
    botonEn(fila(5), 'Más acciones de Licencia federativa').click();
    fixture.detectChanges();
    botonEn(fila(5), 'Eliminar').click();
    await esperar();
    botonEn(dialogo()!, 'Sí, eliminar').click();
    pintar();
    http.expectOne(`${API}/productos/5`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo eliminar el producto.' }]);
    expect(fila(5)).not.toBeNull();
    expect(pendientesLista().length).toBe(0);
  });

  it('eliminar en lote: confirmación destructiva; un DELETE tras otro (como hoy, del último al primero); los fallos no paran el resto', async () => {
    cargar();
    marcar(9);
    marcar(17);
    marcar(19);
    botonEn(barra()!, 'Eliminar').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar 3 productos');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Los productos seleccionados serán eliminados permanentemente. Esta acción no se puede deshacer.'
    );
    expect(document.activeElement).toBe(botonEn(dialogo()!, 'Cancelar'));
    botonEn(dialogo()!, 'Sí, eliminarlos').click();
    pintar();
    // Uno cada vez.
    const primero = http.expectOne(() => true);
    expect([primero.request.method, primero.request.url]).toEqual(['DELETE', `${API}/productos/19`]);
    primero.flush(null);
    const segundo = http.expectOne(() => true);
    expect(segundo.request.url).toBe(`${API}/productos/17`);
    segundo.flush({}, { status: 500, statusText: 'Error' });
    const tercero = http.expectOne(() => true);
    expect(tercero.request.url).toBe(`${API}/productos/9`);
    tercero.flush(null);
    await esperar();
    expect(dialogo()).toBeNull();
    expect(avisos()).toEqual([
      { tipo: 'otro', texto: '2 productos eliminados.' },
      { tipo: 'error', texto: 'No se pudo eliminar el producto «Camiseta del club».' },
    ]);
    expect(barra()).toBeNull();
    expect(pendientesLista().length).toBe(7);
  });

  it('eliminar en lote: cancelar no envía nada y la selección sigue', async () => {
    cargar();
    marcar(9);
    botonEn(barra()!, 'Eliminar').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar 1 producto');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone(() => true);
    expect(casilla(9).checked).toBeTrue();
  });

  it('las escrituras no se cancelan al salir de la pantalla (las lecturas sí)', async () => {
    cargar();
    interruptor(19).click();
    marcar(9);
    botonEn(barra()!, 'Dar a Mi caja').click();
    fixture.detectChanges();
    const uno = http.expectOne(`${API}/productos/19/vendible-profesor`);
    const lote = http.expectOne(`${API}/productos/vendible-profesor`);
    buscar('cami');
    jasmine.clock().tick(300);
    const lecturas = pendientesLista();
    fixture.destroy();
    expect(uno.cancelled).toBeFalse();
    expect(lote.cancelled).toBeFalse();
    expect(lecturas.every((r) => r.cancelled)).toBeTrue();
    uno.flush({ id: 19, vendibleProfesor: true });
    lote.flush({ actualizados: 1 });
    await esperar();
  });

  // ------------------------------------------------------------------ móvil

  it('a 375 px: sin desplazamiento horizontal en la página ni en las filas, con un concepto de 80 caracteres sin espacios', () => {
    const largo = 'Equipacióncompletadecompeticiónoficialhomologadaparatodaslascategoríasinfantiles'.slice(0, 80);
    cargar([...LISTA(), P(30, largo, 1234567.89, 'OTRO', true)]);
    marcar(30);
    const host = el();
    host.style.display = 'block';
    host.style.width = '375px';
    fixture.detectChanges();
    const pagina = host.querySelector<HTMLElement>('.productos')!;
    expect(pagina.scrollWidth).toBe(pagina.clientWidth);
    for (const f of Array.from(host.querySelectorAll<HTMLElement>('.productos__fila'))) {
      expect(f.scrollWidth).withContext(`fila ${f.dataset['producto']}`).toBe(f.clientWidth);
      expect(f.getBoundingClientRect().right).toBeLessThanOrEqual(host.getBoundingClientRect().right + 0.5);
    }
    expect(barra()!.scrollWidth).toBe(barra()!.clientWidth);
  });
});
