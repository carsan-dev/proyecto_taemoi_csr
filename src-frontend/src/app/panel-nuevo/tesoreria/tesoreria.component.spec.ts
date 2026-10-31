import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { TesoreriaMovimiento } from '../../interfaces/tesoreria-movimiento';
import { TesoreriaResumen } from '../../interfaces/tesoreria-resumen';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { GESTION_UNA_SEDE, GESTION_VARIAS_SEDES, RESPONSABLE_VARIAS_SEDES } from '../filtro-sede/filtro-sede-prueba';
import { LoadingService } from '../../servicios/generales/loading.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { ReciboPagoService } from '../cobro/recibo-pago.service';
import { EstilosPanelService } from '../estilos-panel.service';
import { TesoreriaComponent } from './tesoreria.component';

const API = environment.apiUrl;

function mov(id: number, cambios: Partial<TesoreriaMovimiento> = {}): TesoreriaMovimiento {
  return {
    productoAlumnoId: id,
    alumnoId: 300 + (id % 10),
    alumnoNombreCompleto: `Alumna${id} Ficticia`,
    deporte: 'KICKBOXING',
    concepto: `Mensualidad octubre ${id}`,
    categoria: 'Mensualidad',
    fechaAsignacion: '2026-10-01T10:00:00',
    pagado: false,
    fechaPago: null,
    precio: 35,
    notas: null,
    alumnoActivo: true,
    devuelto: false,
    ...cambios,
  };
}

const PENDIENTE = mov(1000, { notas: 'Paga en dos plazos', precio: 1250 });
const PAGADO = mov(1001, { pagado: true, fechaPago: '2026-10-03T12:00:00', concepto: 'Derecho de examen', categoria: 'Examen', deporte: 'TAEKWONDO' });
const DEVUELTO = mov(1002, { pagado: true, fechaPago: '2026-10-04T12:00:00', devuelto: true });

const RESUMEN: TesoreriaResumen = {
  mes: 10,
  ano: 2026,
  deporte: 'TODOS',
  totalMovimientos: 62,
  totalPagados: 41,
  totalPendientes: 21,
  importeTotal: 18342.5,
  importePagado: 12107.5,
  importePendiente: 6235,
  alumnosConPendientes: 1,
  importeDevuelto: 70,
};

function pagina(content: TesoreriaMovimiento[], totalElements = content.length, totalPages = totalElements ? Math.ceil(totalElements / 25) : 0) {
  return { content, totalElements, totalPages, number: 0, size: 25 };
}

describe('TesoreriaComponent (8b-3d, Tesorería del panel nuevo)', () => {
  let fixture: ComponentFixture<TesoreriaComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let loading: jasmine.SpyObj<LoadingService>;
  let esAdmin: boolean;
  let esGestion: boolean;
  let parametros: Record<string, string>;

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

  const fila = (id: number): HTMLElement => el().querySelector<HTMLElement>(`.tesoreria__fila[data-movimiento="${id}"]`)!;
  const pestanas = (): HTMLButtonElement[] => Array.from(el().querySelectorAll<HTMLButtonElement>('[role="tab"]'));

  const esResumen = (r: TestRequest) => r.request.url === `${API}/tesoreria/resumen`;
  const esMovimientos = (r: TestRequest) => r.request.url === `${API}/tesoreria/movimientos`;

  function pendientes(): { resumen: TestRequest[]; movimientos: TestRequest[]; anios: TestRequest[] } {
    return {
      resumen: http.match((r) => r.url === `${API}/tesoreria/resumen`).filter((r) => !r.cancelled),
      movimientos: http.match((r) => r.url === `${API}/tesoreria/movimientos`).filter((r) => !r.cancelled),
      anios: http.match((r) => r.url === `${API}/tesoreria/anios-disponibles`).filter((r) => !r.cancelled),
    };
  }

  function crear(): void {
    fixture = TestBed.createComponent(TesoreriaComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  /** Responde la carga en vuelo (resumen si lo hay, movimientos y años la primera vez). */
  function responder(contenido = [PENDIENTE], total = contenido.length, resumen: TesoreriaResumen = RESUMEN): { resumen?: TestRequest; movimientos: TestRequest } {
    const p = pendientes();
    p.anios.forEach((r) => r.flush([2025, 2026]));
    expect(p.movimientos.length).withContext('movimientos').toBe(1);
    expect(p.resumen.length).toBeLessThanOrEqual(1);
    p.resumen[0]?.flush(resumen);
    p.movimientos[0].flush(pagina(contenido, total));
    fixture.detectChanges();
    return { resumen: p.resumen[0], movimientos: p.movimientos[0] };
  }

  function params(r: TestRequest): Record<string, string> {
    const salida: Record<string, string> = {};
    r.request.params.keys().forEach((k) => (salida[k] = r.request.params.get(k)!));
    return salida;
  }

  function abrirMenuFila(id: number): HTMLElement {
    botonEn(fila(id), 'Más acciones').click();
    fixture.detectChanges();
    return fila(id).querySelector<HTMLElement>('[role="menu"]')!;
  }

  function opciones(menu: HTMLElement): string[] {
    return Array.from(menu.querySelectorAll('[role="menuitem"]')).map(texto);
  }

  function elegirMenu(menu: HTMLElement, t: string): void {
    Array.from(menu.querySelectorAll<HTMLButtonElement>('[role="menuitem"]')).find((b) => texto(b) === t)!.click();
    pintar();
  }

  function escribir(selector: string, valor: string): void {
    const c = dialogo()!.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    c.value = valor;
    c.dispatchEvent(new Event('input'));
  }

  beforeEach(async () => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 22, 10, 0));
    quitarTokens = ponerTokensDePrueba();
    loading = jasmine.createSpyObj('LoadingService', ['show', 'hide']);
    esAdmin = true;
    esGestion = false;
    parametros = {};
    await TestBed.configureTestingModule({
      imports: [TesoreriaComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useFactory: () => ({ snapshot: { queryParamMap: convertToParamMap(parametros) } }) },
        { provide: LoadingService, useValue: loading },
        {
          provide: AuthenticationService,
          useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => esAdmin, tieneAccesoAdmin: () => esGestion, obtenerRoles: () => of([]) },
        },
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of({}) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
        {
          provide: CatalogoService,
          useValue: {
            disciplinasActivas: () => [
              { codigo: 'TAEKWONDO', nombre: 'Taekwondo' },
              { codigo: 'KICKBOXING', nombre: 'Kickboxing' },
              { codigo: 'PILATES', nombre: 'Pilates' },
            ],
            etiquetaDisciplina: (c: string) => ({ TAEKWONDO: 'Taekwondo', KICKBOXING: 'Kickboxing', PILATES: 'Pilates' } as Record<string, string>)[c] ?? c,
          },
        },
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
    quitarTokens();
    jasmine.clock().uninstall();
  });

  // ------------------------------------------------------------------ lecturas y cabecera

  it('carga: resumen, movimientos (Pendientes) y años con los parámetros de hoy, ERROR_HANDLED_LOCALLY y sin cargador global', () => {
    crear();
    const p = pendientes();
    expect(p.resumen.length).toBe(1);
    expect(p.movimientos.length).toBe(1);
    expect(p.anios.length).toBe(1);
    expect(params(p.resumen[0])).toEqual({ ano: '2026', mes: '10', soloActivos: 'true' });
    expect(params(p.movimientos[0])).toEqual({ ano: '2026', mes: '10', pagado: 'false', soloActivos: 'true', page: '1', size: '25' });
    for (const r of [p.resumen[0], p.movimientos[0], p.anios[0]]) {
      expect(r.request.method).toBe('GET');
      expect(r.request.withCredentials).toBeTrue();
      expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    }
    p.anios[0].flush([2025, 2026]);
    p.resumen[0].flush(RESUMEN);
    p.movimientos[0].flush(pagina([PENDIENTE]));
    expect(loading.show).not.toHaveBeenCalled();
  });

  it('cabecera: «Tesorería» y el subtitulo de hoy con «Actualizado» fecha y hora', () => {
    crear();
    responder();
    expect(texto(el().querySelector('h1'))).toBe('Tesorería');
    expect(texto(el().querySelector('.tesoreria__subtitulo'))).toBe(
      'Control de deudas, pagos pendientes y estado mensual por deporte. Actualizado 22/10/2026 10:00'
    );
  });

  it('parámetros de la dirección (enlaces de hoy): mes, año, estado, deporte y soloActivos', () => {
    parametros = { mes: '3', ano: '2025', estado: 'PAGADOS', deporte: 'KICKBOXING', soloActivos: 'false' };
    crear();
    const p = pendientes();
    expect(params(p.movimientos[0])).toEqual({
      ano: '2025', mes: '3', deporte: 'KICKBOXING', pagado: 'true', soloActivos: 'false', page: '1', size: '25',
    });
    p.anios[0].flush([2025, 2026]);
    p.resumen[0].flush(RESUMEN);
    p.movimientos[0].flush(pagina([PAGADO]));
    fixture.detectChanges();
    expect(pestanas()[1].getAttribute('aria-selected')).toBe('true');
  });

  // ------------------------------------------------------------------ pestañas

  it('pestañas: tablist con Pendientes, Cobrados y Todos; Pendientes elegida y con tabindex itinerante', () => {
    crear();
    responder();
    expect(el().querySelector('[role="tablist"]')).not.toBeNull();
    expect(pestanas().map(texto)).toEqual(['Pendientes', 'Cobrados', 'Todos']);
    expect(pestanas().map((t) => t.getAttribute('aria-selected'))).toEqual(['true', 'false', 'false']);
    expect(pestanas().map((t) => t.tabIndex)).toEqual([0, -1, -1]);
    const panel = el().querySelector('[role="tabpanel"]')!;
    expect(panel.getAttribute('aria-labelledby')).toBe(pestanas()[0].id);
  });

  it('pestañas: Cobrados → pagado=true y Todos → sin pagado, en la página 1 con resumen, como «Aplicar»', () => {
    crear();
    responder();
    pestanas()[1].click();
    fixture.detectChanges();
    let p = pendientes();
    expect(p.resumen.length).toBe(1);
    expect(params(p.movimientos[0])).toEqual({ ano: '2026', mes: '10', pagado: 'true', soloActivos: 'true', page: '1', size: '25' });
    p.resumen[0].flush(RESUMEN);
    p.movimientos[0].flush(pagina([PAGADO]));
    fixture.detectChanges();
    pestanas()[2].click();
    fixture.detectChanges();
    p = pendientes();
    expect(params(p.movimientos[0])).toEqual({ ano: '2026', mes: '10', soloActivos: 'true', page: '1', size: '25' });
  });

  it('pestañas: flechas, Inicio y Fin cambian de pestaña y de foco', () => {
    crear();
    responder();
    const lista = el().querySelector<HTMLElement>('[role="tablist"]')!;
    pestanas()[0].focus();
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[1]);
    expect(pestanas()[1].getAttribute('aria-selected')).toBe('true');
    responder([PAGADO]);
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[2]);
    responder([PAGADO]);
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[0]);
    responder();
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[2]);
    responder();
    lista.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(pestanas()[0]);
  });

  // ------------------------------------------------------------------ filtros

  it('filtros: una barra con Mes, Año (con «Todos los años»), Deporte, «Solo alumnos activos», buscar, «Aplicar» y «Restablecer»', () => {
    crear();
    responder();
    const barra = el().querySelector('.tesoreria__filtros')!;
    expect(texto(barra.querySelector('label[for="tesoreria-mes"]'))).toBe('Mes');
    expect(texto(barra.querySelector('label[for="tesoreria-ano"]'))).toBe('Año');
    expect(texto(barra.querySelector('label[for="tesoreria-deporte"]'))).toBe('Deporte');
    expect(texto(barra.querySelector('label[for="tesoreria-activos"]'))).toBe('Solo alumnos activos');
    const anos = Array.from(barra.querySelectorAll<HTMLOptionElement>('#tesoreria-ano option')).map(texto);
    expect(anos).toEqual(['Todos los años (totales)', '2025', '2026']);
    const deportes = Array.from(barra.querySelectorAll<HTMLOptionElement>('#tesoreria-deporte option')).map(texto);
    expect(deportes).toEqual(['Todos los deportes', 'Taekwondo', 'Kickboxing', 'Pilates']);
    expect(barra.querySelector<HTMLInputElement>('#tesoreria-buscar')!.placeholder).toBe('Alumno, concepto o categoría');
    expect(botonEn(barra, 'Aplicar')).toBeTruthy();
    expect(botonEn(barra, 'Restablecer')).toBeTruthy();
  });

  it('«Aplicar» envía los filtros elegidos, en la página 1 y con resumen; nada se pide antes de pulsarlo', () => {
    crear();
    responder();
    const barra = el().querySelector('.tesoreria__filtros')!;
    const mes = barra.querySelector<HTMLSelectElement>('#tesoreria-mes')!;
    mes.value = mes.options[4].value; // Abril
    mes.dispatchEvent(new Event('change'));
    const ano = barra.querySelector<HTMLSelectElement>('#tesoreria-ano')!;
    ano.value = ano.options[1].value; // 2025
    ano.dispatchEvent(new Event('change'));
    const dep = barra.querySelector<HTMLSelectElement>('#tesoreria-deporte')!;
    dep.value = 'PILATES';
    dep.dispatchEvent(new Event('change'));
    barra.querySelector<HTMLInputElement>('#tesoreria-activos')!.click();
    fixture.detectChanges();
    expect(pendientes().movimientos.length).toBe(0);
    botonEn(barra, 'Aplicar').click();
    fixture.detectChanges();
    const p = pendientes();
    expect(params(p.resumen[0])).toEqual({ ano: '2025', mes: '4', deporte: 'PILATES', soloActivos: 'false' });
    expect(params(p.movimientos[0])).toEqual({ ano: '2025', mes: '4', deporte: 'PILATES', pagado: 'false', soloActivos: 'false', page: '1', size: '25' });
  });

  it('«Todos los años» deshabilita el mes y no envía ni mes ni año', () => {
    crear();
    responder();
    const ano = el().querySelector<HTMLSelectElement>('#tesoreria-ano')!;
    ano.value = ano.options[0].value;
    ano.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el().querySelector<HTMLSelectElement>('#tesoreria-mes')!.disabled).toBeTrue();
    botonEn(el(), 'Aplicar').click();
    fixture.detectChanges();
    const p = pendientes();
    expect(params(p.resumen[0])).toEqual({ soloActivos: 'true' });
    expect(params(p.movimientos[0])).toEqual({ pagado: 'false', soloActivos: 'true', page: '1', size: '25' });
  });

  it('buscar: como hoy, a los 300 ms pide solo los movimientos con el texto, en la página 1', () => {
    crear();
    responder();
    const campo = el().querySelector<HTMLInputElement>('#tesoreria-buscar')!;
    campo.value = 'Ana';
    campo.dispatchEvent(new Event('input'));
    jasmine.clock().tick(299);
    expect(pendientes().movimientos.length).toBe(0);
    jasmine.clock().tick(1);
    const p = pendientes();
    expect(p.resumen.length).toBe(0);
    expect(params(p.movimientos[0])).toEqual({ ano: '2026', mes: '10', pagado: 'false', texto: 'Ana', soloActivos: 'true', page: '1', size: '25' });
    p.movimientos[0].flush(pagina([]));
  });

  it('«Restablecer» vuelve a los valores de hoy (mes y año actuales, todos los deportes, Pendientes, sin texto, solo activos)', () => {
    crear();
    responder();
    pestanas()[2].click();
    fixture.detectChanges();
    responder();
    const dep = el().querySelector<HTMLSelectElement>('#tesoreria-deporte')!;
    dep.value = 'PILATES';
    dep.dispatchEvent(new Event('change'));
    el().querySelector<HTMLInputElement>('#tesoreria-activos')!.click();
    fixture.detectChanges();
    botonEn(el().querySelector('.tesoreria__filtros')!, 'Restablecer').click();
    fixture.detectChanges();
    const p = pendientes();
    expect(params(p.resumen[0])).toEqual({ ano: '2026', mes: '10', soloActivos: 'true' });
    expect(params(p.movimientos[0])).toEqual({ ano: '2026', mes: '10', pagado: 'false', soloActivos: 'true', page: '1', size: '25' });
    p.resumen[0].flush(RESUMEN);
    p.movimientos[0].flush(pagina([PENDIENTE]));
    fixture.detectChanges();
    expect(pestanas()[0].getAttribute('aria-selected')).toBe('true');
    expect(el().querySelector<HTMLSelectElement>('#tesoreria-deporte')!.value).toBe('TODOS');
    expect(el().querySelector<HTMLInputElement>('#tesoreria-activos')!.checked).toBeTrue();
  });

  // ------------------------------------------------------------------ resumen

  it('resumen: cuatro cifras con importes es-ES, plurales y los devueltos', () => {
    crear();
    responder();
    const cifras = Array.from(el().querySelectorAll('.tesoreria__cifra')).map((c) => ({
      titulo: texto(c.querySelector('.tesoreria__cifra-titulo')),
      valor: texto(c.querySelector('.tesoreria__cifra-valor')),
      detalle: Array.from(c.querySelectorAll('.tesoreria__cifra-detalle')).map(texto),
    }));
    expect(cifras).toEqual([
      { titulo: 'Pendiente', valor: '6.235,00 €', detalle: ['21 cobros pendientes'] },
      { titulo: 'Cobrado', valor: '12.107,50 €', detalle: ['41 cobros pagados', 'De ello, 70,00 € devueltos'] },
      { titulo: 'Total generado', valor: '18.342,50 €', detalle: ['62 movimientos'] },
      { titulo: 'Alumnos con deuda', valor: '1', detalle: ['Octubre 2026'] },
    ]);
    expect(el().querySelector('.tesoreria__cifra-valor')!.hasAttribute('data-cifras')).toBeTrue();
  });

  it('resumen: singular con 1 y sin devueltos', () => {
    crear();
    responder([PENDIENTE], 1, { ...RESUMEN, totalPendientes: 1, totalPagados: 1, totalMovimientos: 1, importeDevuelto: 0 });
    const detalles = Array.from(el().querySelectorAll('.tesoreria__cifra-detalle')).map(texto);
    expect(detalles).toEqual(['1 cobro pendiente', '1 cobro pagado', '1 movimiento', 'Octubre 2026']);
  });

  it('barra cobrado/pendiente: proporciones, texto equivalente (role=img) y leyenda con nombre e importe, no solo color', () => {
    crear();
    responder();
    const barra = el().querySelector<HTMLElement>('.tesoreria__barra')!;
    expect(barra.getAttribute('role')).toBe('img');
    expect(barra.getAttribute('aria-label')!.replace(/\s+/g, ' ')).toBe('Cobrado 12.107,50 € (66 %), pendiente 6.235,00 € (34 %)');
    expect(barra.querySelector<HTMLElement>('.tesoreria__barra-cobrado')!.style.width).toBe('66%');
    expect(barra.querySelector<HTMLElement>('.tesoreria__barra-pendiente')!.style.width).toBe('34%');
    expect(Array.from(el().querySelectorAll('.tesoreria__leyenda li')).map(texto)).toEqual([
      'Cobrado 12.107,50 € · 66 %',
      'Pendiente 6.235,00 € · 34 %',
    ]);
    expect(el().querySelector('.tesoreria__sedes')).toBeNull();
  });

  // ------------------------------------------------------------------ lista

  it('lista: cabecera con título, deporte y periodo; filas de dos líneas con alumno (ficha), concepto, importe, línea 2, notas y pastilla', () => {
    crear();
    responder([PENDIENTE, PAGADO, DEVUELTO], 3);
    expect(texto(el().querySelector('.tesoreria__lista-titulo'))).toBe('Movimientos de cobro');
    expect(texto(el().querySelector('.tesoreria__lista-sub'))).toBe('Todos los deportes · Octubre 2026');
    const f = fila(1000);
    const alumno = f.querySelector<HTMLAnchorElement>('a.tesoreria__alumno')!;
    expect(texto(alumno)).toBe('Alumna1000 Ficticia');
    expect(alumno.getAttribute('href')).toBe('/alumnosEditar/300');
    expect(texto(f.querySelector('.tesoreria__concepto'))).toBe('Mensualidad octubre 1000');
    expect(texto(f.querySelector('.tesoreria__importe'))).toBe('1.250,00 €');
    expect(texto(f.querySelector('.tesoreria__meta'))).toBe('Kickboxing · Mensualidad · Cargado 01/10/2026');
    expect(texto(f.querySelector('.tesoreria__notas-texto'))).toBe('Paga en dos plazos');
    expect(texto(f.querySelector('.tesoreria__pastilla'))).toBe('Pendiente');
    expect(f.querySelector('.tesoreria__pastilla--pendiente')).not.toBeNull();
    expect(botonEn(f, 'Cobrar 1.250,00 € de Mensualidad octubre 1000 a Alumna1000 Ficticia')).toBeTruthy();
    const p = fila(1001);
    expect(texto(p.querySelector('.tesoreria__meta'))).toBe('Taekwondo · Examen · Cargado 01/10/2026');
    expect(texto(p.querySelector('.tesoreria__pastilla'))).toBe('Pagado el 03/10/2026');
    expect(p.querySelector('.tesoreria__cobrar')).toBeNull();
    expect(p.querySelector('.tesoreria__notas')).toBeNull();
    expect(Array.from(fila(1002).querySelectorAll('.tesoreria__pastilla')).map(texto)).toEqual(['Pagado el 04/10/2026', 'Devuelto']);
  });

  it('un alumno sin id va sin enlace, como hoy', () => {
    crear();
    responder([mov(1003, { alumnoId: null })]);
    expect(fila(1003).querySelector('a.tesoreria__alumno')).toBeNull();
    expect(texto(fila(1003).querySelector('.tesoreria__alumno'))).toBe('Alumna1003 Ficticia');
  });

  it('pie: «Mostrando X de Y movimientos» y paginación de 25 que pide solo los movimientos de esa página', () => {
    crear();
    const veinticinco = Array.from({ length: 25 }, (_, i) => mov(2000 + i));
    responder(veinticinco, 62);
    expect(texto(el().querySelector('.tesoreria__mostrando'))).toBe('Mostrando 25 de 62 movimientos');
    const nav = el().querySelector('.tesoreria__paginacion')!;
    expect(nav.getAttribute('aria-label')).toBe('Páginas de movimientos');
    botonEn(nav, 'Página 2').click();
    fixture.detectChanges();
    const p = pendientes();
    expect(p.resumen.length).toBe(0);
    expect(params(p.movimientos[0])).toEqual({ ano: '2026', mes: '10', pagado: 'false', soloActivos: 'true', page: '2', size: '25' });
    p.movimientos[0].flush(pagina([mov(3000)], 62));
    fixture.detectChanges();
    expect(botonEn(el().querySelector('.tesoreria__paginacion')!, 'Página 2').getAttribute('aria-current')).toBe('page');
    expect(texto(el().querySelector('.tesoreria__mostrando'))).toBe('Mostrando 1 de 62 movimientos');
  });

  it('una sola página: sin paginación', () => {
    crear();
    responder([PENDIENTE], 1);
    expect(el().querySelector('.tesoreria__paginas')).toBeNull();
    expect(texto(el().querySelector('.tesoreria__mostrando'))).toBe('Mostrando 1 de 1 movimientos');
  });

  // ------------------------------------------------------------------ estados

  it('esqueleto con forma de filas a los 300 ms, y nada antes', () => {
    crear();
    jasmine.clock().tick(299);
    fixture.detectChanges();
    expect(el().querySelector('.pn-esqueleto__forma')).toBeNull();
    jasmine.clock().tick(1);
    fixture.detectChanges();
    expect(el().querySelectorAll('.tesoreria__esqueleto-fila').length).toBeGreaterThanOrEqual(5);
    expect(el().querySelector('pn-esqueleto [role="status"]')!.textContent).toContain('Cargando la tesorería');
    responder();
    expect(el().querySelector('pn-esqueleto')).toBeNull();
  });

  it('error de carga: bloque de error con «Reintentar» (sin SweetAlert2) que vuelve a pedir lo mismo', () => {
    crear();
    const p = pendientes();
    p.anios[0].flush([2026]);
    p.resumen[0].flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const error = el().querySelector('pn-bloque-error')!;
    expect(texto(error)).toContain('No se pudo cargar la información de tesorería');
    expect(document.querySelector('.swal2-container')).toBeNull();
    botonEn(error, 'Reintentar').click();
    fixture.detectChanges();
    responder();
    expect(fila(1000)).not.toBeNull();
  });

  it('vacío: el texto de hoy en pn-bloque-vacio con «Restablecer»', () => {
    crear();
    responder([], 0);
    const vacio = el().querySelector('pn-bloque-vacio')!;
    expect(texto(vacio)).toContain('No hay cobros para los filtros seleccionados.');
    botonEn(vacio, 'Restablecer').click();
    fixture.detectChanges();
    expect(pendientes().resumen.length).toBe(1);
  });

  // ------------------------------------------------------------------ cobrar

  it('«Cobrar»: el diálogo de CobroCargoService y el PUT de hoy con el método; relee resumen y movimientos', async () => {
    crear();
    responder();
    botonEn(fila(1000), 'Cobrar 1.250,00 € de Mensualidad octubre 1000 a Alumna1000 Ficticia').click();
    pintar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Cobrar cargo');
    Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('[role="radio"]')).find((b) => texto(b) === 'Efectivo')!.click();
    botonEn(dialogo()!, 'Marcar como pagado').click();
    pintar();
    const r = http.expectOne(`${API}/productos-alumno/1000`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ pagado: true, metodoPago: 'EFECTIVO' });
    expect(fila(1000).querySelector<HTMLButtonElement>('.tesoreria__cobrar')!.disabled).toBeTrue();
    r.flush({});
    await esperar();
    expect(avisos()[0].texto).toBe('Cobro registrado: 1.250,00 € · Alumna1000 Ficticia');
    const p = pendientes();
    expect(p.resumen.length).toBe(1);
    expect(p.movimientos.length).toBe(1);
    expect(loading.show).not.toHaveBeenCalled();
  });

  it('8b-M: el cobro que deja el mes sin nada pendiente lo anuncia con la gota de 32 px celebrando, y el texto dice lo mismo', async () => {
    crear();
    responder([PENDIENTE], 1, { ...RESUMEN, importePendiente: 1250, totalPendientes: 1 });
    expect(el().querySelector('.tesoreria__cierre')).toBeNull();
    botonEn(fila(1000), 'Cobrar 1.250,00 € de Mensualidad octubre 1000 a Alumna1000 Ficticia').click();
    pintar();
    Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('[role="radio"]')).find((b) => texto(b) === 'Efectivo')!.click();
    botonEn(dialogo()!, 'Marcar como pagado').click();
    pintar();
    http.expectOne(`${API}/productos-alumno/1000`).flush({});
    await esperar();
    const p = pendientes();
    p.resumen[0].flush({ ...RESUMEN, importePendiente: 0, totalPendientes: 0, alumnosConPendientes: 0 });
    p.movimientos[0].flush(pagina([]));
    await esperar();
    const cierre = el().querySelector<HTMLElement>('.tesoreria__cierre')!;
    expect(cierre.getAttribute('role')).toBe('status');
    expect(texto(cierre)).toBe('Octubre 2026 está cobrado entero.');
    const mascota = cierre.querySelector<HTMLElement>('pn-mascota')!;
    expect(mascota.classList).toContain('pn-mascota--sm');
    expect(mascota.getAttribute('aria-hidden')).toBe('true');
  });

  it('8b-M: un mes que se abre ya sin nada pendiente no celebra (solo el cobro que lo cierra)', () => {
    crear();
    responder([], 0, { ...RESUMEN, importePendiente: 0, totalPendientes: 0, alumnosConPendientes: 0 });
    expect(el().querySelector('.tesoreria__cierre')).toBeNull();
    expect(el().querySelector('pn-mascota')).toBeNull();
  });

  it('8b-M: la tesorería no lleva mascota si el mes sigue con cobros pendientes', async () => {
    crear();
    responder();
    expect(el().querySelector('pn-mascota')).toBeNull();
  });

  it('«Cobrar» cancelado no envía nada', async () => {
    crear();
    responder();
    botonEn(fila(1000), 'Cobrar 1.250,00 € de Mensualidad octubre 1000 a Alumna1000 Ficticia').click();
    pintar();
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'PUT');
  });

  // ------------------------------------------------------------------ menú «⋯»

  it('«⋯»: botón de menú (aria-haspopup, aria-expanded, aria-controls) con las opciones según estado y rol', () => {
    crear();
    responder([PENDIENTE, PAGADO, DEVUELTO], 3);
    const boton = botonEn(fila(1001), 'Más acciones');
    expect(boton.getAttribute('aria-haspopup')).toBe('menu');
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(opciones(abrirMenuFila(1000))).toEqual(['Notas']);
    const menu = abrirMenuFila(1001);
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(boton.getAttribute('aria-controls')).toBe(menu.id);
    expect(opciones(menu)).toEqual(['Notas', 'Cambiar fecha de pago', 'Revertir']);
    expect(fila(1000).querySelector('[role="menu"]')).toBeNull();
    expect(opciones(abrirMenuFila(1002))).toEqual(['Notas', 'Cambiar fecha de pago']);
  });

  it('«⋯» sin ser propietario: «Pedir revertir» en lugar de «Revertir»', () => {
    esAdmin = false;
    crear();
    responder([PAGADO]);
    expect(opciones(abrirMenuFila(1001))).toEqual(['Notas', 'Cambiar fecha de pago', 'Pedir revertir']);
  });

  it('«⋯» recibo (pieza 279): «Descargar recibo» solo para gestión y solo en cobrados', () => {
    esGestion = true;
    crear();
    responder([PENDIENTE, PAGADO, DEVUELTO], 3);
    expect(opciones(abrirMenuFila(1000))).toEqual(['Notas']);
    expect(opciones(abrirMenuFila(1001))).toEqual(['Notas', 'Cambiar fecha de pago', 'Descargar recibo', 'Revertir']);
    expect(opciones(abrirMenuFila(1002))).not.toContain('Descargar recibo');
  });

  it('«⋯» recibo: el responsable de sede lo tiene, sin «Revertir»', () => {
    (TestBed.inject(PermisosSesionService) as unknown as { tiene: (c: string) => boolean }).tiene = (c: string) => c === 'PANEL_SEDE';
    esGestion = false;
    esAdmin = false;
    crear();
    responder([PAGADO]);
    const lista = opciones(abrirMenuFila(1001));
    expect(lista).toContain('Descargar recibo');
    expect(lista).not.toContain('Revertir');
  });

  it('«⋯» recibo: sin gestión (solo profesor o familias) no hay «Descargar recibo»', () => {
    esGestion = false;
    esAdmin = false;
    crear();
    responder([PAGADO]);
    expect(opciones(abrirMenuFila(1001))).not.toContain('Descargar recibo');
  });

  it('«Descargar recibo» pide el recibo del cargo de esa fila', () => {
    esGestion = true;
    crear();
    responder([PENDIENTE, PAGADO], 2);
    const descargar = spyOn(TestBed.inject(ReciboPagoService), 'descargar').and.resolveTo();
    elegirMenu(abrirMenuFila(1001), 'Descargar recibo');
    expect(descargar).toHaveBeenCalledOnceWith(1001);
  });

  it('«⋯» con teclado: foco en la primera, flechas con vuelta, Inicio, Fin y Esc cierra devolviendo el foco', () => {
    crear();
    responder([PAGADO]);
    const boton = botonEn(fila(1001), 'Más acciones');
    boton.focus();
    boton.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    fixture.detectChanges();
    const menu = fila(1001).querySelector<HTMLElement>('[role="menu"]')!;
    const items = Array.from(menu.querySelectorAll<HTMLElement>('[role="menuitem"]'));
    expect(document.activeElement).toBe(items[0]);
    const tecla = (key: string) => {
      (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
      fixture.detectChanges();
    };
    tecla('ArrowUp');
    expect(document.activeElement).toBe(items[2]);
    tecla('ArrowDown');
    expect(document.activeElement).toBe(items[0]);
    tecla('End');
    expect(document.activeElement).toBe(items[2]);
    tecla('Home');
    expect(document.activeElement).toBe(items[0]);
    tecla('Escape');
    expect(fila(1001).querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(boton);
  });

  // ------------------------------------------------------------------ notas, fecha, revertir

  it('«Notas»: diálogo de hoy con el texto; «Guardar» hace el PUT {notas}; aviso y relectura', async () => {
    crear();
    responder();
    elegirMenu(abrirMenuFila(1000), 'Notas');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Editar notas');
    const area = dialogo()!.querySelector<HTMLTextAreaElement>('textarea#notas')!;
    expect(area.value).toBe('Paga en dos plazos');
    expect(area.placeholder).toBe('Escribe una nota...');
    expect(area.maxLength).toBe(255);
    escribir('#notas', 'Pagará el viernes');
    botonEn(dialogo()!, 'Guardar').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/1000`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ notas: 'Pagará el viernes' });
    r.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Notas actualizadas correctamente' }]);
    expect(pendientes().resumen.length).toBe(1);
  });

  it('«Notas» cancelado no envía nada; un error avisa con el texto de hoy', async () => {
    crear();
    responder();
    elegirMenu(abrirMenuFila(1000), 'Notas');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'PUT');
    elegirMenu(abrirMenuFila(1000), 'Notas');
    botonEn(dialogo()!, 'Guardar').click();
    await esperar();
    http.expectOne(`${API}/productos-alumno/1000`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudieron actualizar las notas' }]);
  });

  it('«Cambiar fecha de pago»: diálogo con la fecha de hoy; «Guardar» hace el PUT {fechaPago} a mediodía', async () => {
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Cambiar fecha de pago');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Editar fecha de pago');
    const campo = dialogo()!.querySelector<HTMLInputElement>('input#fechaPago')!;
    expect(campo.type).toBe('date');
    expect(campo.value).toBe(new Date('2026-10-03T12:00:00').toISOString().slice(0, 10));
    escribir('#fechaPago', '2026-10-05');
    botonEn(dialogo()!, 'Guardar').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/1001`);
    expect(r.request.method).toBe('PUT');
    expect(Object.keys(r.request.body)).toEqual(['fechaPago']);
    expect(r.request.body.fechaPago).toEqual(new Date('2026-10-05T12:00:00'));
    r.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Fecha de pago actualizada' }]);
  });

  it('«Cambiar fecha de pago» vacía: el error de hoy en línea y nada enviado', async () => {
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Cambiar fecha de pago');
    escribir('#fechaPago', '');
    botonEn(dialogo()!, 'Guardar').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('#fechaPago-error'))).toBe('Debes indicar una fecha de pago');
    http.expectNone((r) => r.method === 'PUT');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
  });

  it('«Revertir» (propietario): destructivo con el foco en «Cancelar», el texto de hoy y el PUT {pagado:false, motivoCambio}', async () => {
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Revertir');
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Revertir cobro');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('El cobro "Derecho de examen" pasará a pendiente.');
    expect(texto(dialogo()!.querySelector('label[for="motivo"]'))).toBe('Motivo (opcional)');
    expect(dialogo()!.querySelector<HTMLTextAreaElement>('#motivo')!.placeholder).toBe('Ejemplo: error de marcación');
    expect(document.activeElement).toBe(botonEn(dialogo()!, 'Cancelar'));
    escribir('#motivo', '  error de marcación  ');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/1001`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ pagado: false, motivoCambio: 'error de marcación' });
    r.flush({});
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Cobro revertido a pendiente' }]);
  });

  it('«Revertir» sin motivo envía null; cancelar no envía; un 403 dice quién puede', async () => {
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Revertir');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'PUT');
    elegirMenu(abrirMenuFila(1001), 'Revertir');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/1001`);
    expect(r.request.body).toEqual({ pagado: false, motivoCambio: null });
    r.flush({}, { status: 403, statusText: 'Forbidden' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'Solo administradores pueden revertir cobros' }]);
  });

  it('sin doble envío: con una escritura en vuelo, el «⋯» de esa fila queda deshabilitado', async () => {
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Revertir');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/1001`);
    expect(botonEn(fila(1001), 'Más acciones').disabled).toBeTrue();
    r.flush({});
    await esperar();
  });

  it('«Pedir revertir» (gestor): la solicitud de la 6c con datos, nota y motivo obligatorio; POST /solicitudes y aviso', async () => {
    esAdmin = false;
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Pedir revertir');
    expect(dialogo()!.classList).not.toContain('pn-dialogo--peligro');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Pedir al propietario que revierta este cobro');
    expect(Array.from(dialogo()!.querySelectorAll('.pn-dialogo__lista li')).map(texto)).toEqual([
      'Alumno: Alumna1001 Ficticia',
      'Concepto: Derecho de examen',
      'Importe: 35,00 €',
      'Fecha de pago: 3/10/2026',
    ]);
    expect(texto(dialogo()!.querySelector('.pn-dialogo__explicacion'))).toContain('Mientras tanto el pago sigue marcado.');
    const area = dialogo()!.querySelector<HTMLTextAreaElement>('textarea#motivo')!;
    expect(area.maxLength).toBe(500);
    expect(area.placeholder).toBe('Qué ha pasado y por qué hay que cambiarlo');
    botonEn(dialogo()!, 'Enviar solicitud').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('#motivo-error'))).toBe('Escribe el motivo: el propietario lo verá al decidir');
    http.expectNone(`${API}/solicitudes`);
    escribir('#motivo', '  Lo cobré dos veces  ');
    botonEn(dialogo()!, 'Enviar solicitud').click();
    await esperar();
    const r = http.expectOne(`${API}/solicitudes`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({ tipo: 'REVERTIR_COBRO', productoAlumnoId: 1001, motivo: 'Lo cobré dos veces' });
    r.flush({ id: 9, tipo: 'REVERTIR_COBRO', estado: 'PENDIENTE' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Solicitud enviada al propietario' }]);
  });

  it('«Pedir revertir»: un 409 enseña el mensaje del servidor; cancelar no envía', async () => {
    esAdmin = false;
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Pedir revertir');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone(`${API}/solicitudes`);
    elegirMenu(abrirMenuFila(1001), 'Pedir revertir');
    escribir('#motivo', 'Duplicado');
    botonEn(dialogo()!, 'Enviar solicitud').click();
    await esperar();
    http.expectOne(`${API}/solicitudes`).flush({ mensaje: 'Ya hay una solicitud pendiente sobre este recurso' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'Ya hay una solicitud pendiente sobre este recurso' }]);
  });

  // ------------------------------------------------------------------ exportar y certificados

  describe('exportar', () => {
    let descargas: string[];

    beforeEach(() => {
      descargas = [];
      spyOn(URL, 'createObjectURL').and.returnValue('blob:prueba');
      spyOn(URL, 'revokeObjectURL');
      spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
        descargas.push(this.download);
      });
    });

    function abrirExportar(): HTMLElement {
      el().querySelector<HTMLButtonElement>('#tesoreria-exportar')!.click();
      fixture.detectChanges();
      return el().querySelector<HTMLElement>('#tesoreria-menu-exportar')!;
    }

    it('menú «Exportar» con las cuatro opciones y teclado de menú', () => {
      crear();
      responder();
      const boton = el().querySelector<HTMLButtonElement>('#tesoreria-exportar')!;
      expect(boton.getAttribute('aria-haspopup')).toBe('menu');
      const menu = abrirExportar();
      expect(menu.getAttribute('role')).toBe('menu');
      expect(opciones(menu)).toEqual(['Ver PDF', 'Exportar PDF', 'Exportar CSV', 'Certificados…']);
      expect(document.activeElement).toBe(menu.querySelector('[role="menuitem"]'));
      (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();
      expect(el().querySelector('#tesoreria-menu-exportar')).toBeNull();
      expect(document.activeElement).toBe(boton);
    });

    it('«Exportar CSV» y «Exportar PDF»: las peticiones y nombres de hoy, con la pestaña como estado', async () => {
      crear();
      responder();
      elegirMenu(abrirExportar(), 'Exportar CSV');
      const csv = http.expectOne((r) => r.url === `${API}/tesoreria/export/csv`);
      expect(params(csv)).toEqual({ ano: '2026', mes: '10', pagado: 'false', soloActivos: 'true' });
      expect(csv.request.responseType).toBe('blob');
      csv.flush(new Blob(['a;b']));
      await esperar();
      pestanas()[1].click();
      fixture.detectChanges();
      responder([PAGADO]);
      elegirMenu(abrirExportar(), 'Exportar PDF');
      const pdf = http.expectOne((r) => r.url === `${API}/tesoreria/export/pdf`);
      expect(params(pdf)).toEqual({ ano: '2026', mes: '10', pagado: 'true', soloActivos: 'true' });
      pdf.flush(new Blob(['%PDF']));
      await esperar();
      expect(descargas).toEqual(['tesoreria_2026_10_todos_pendientes_activos.csv', 'tesoreria_2026_10_todos_pagados_activos.pdf']);
      expect(loading.show).not.toHaveBeenCalled();
    });

    it('«Ver PDF» abre el visor del panel nuevo; un fallo avisa con el texto de hoy, sin SweetAlert2', async () => {
      crear();
      responder();
      elegirMenu(abrirExportar(), 'Ver PDF');
      http.expectOne((r) => r.url === `${API}/tesoreria/export/pdf`).flush(new Blob(['%PDF']));
      await esperar();
      expect(el().querySelector('pn-visor-pdf')).not.toBeNull();
      expect(descargas).toEqual([]);
      fixture.componentInstance.informes.cerrar();
      fixture.detectChanges();
      elegirMenu(abrirExportar(), 'Exportar CSV');
      http.expectOne((r) => r.url === `${API}/tesoreria/export/csv`).flush(new Blob([]), { status: 500, statusText: 'Error' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo generar el informe CSV de tesoreria' }]);
      expect(Swal.isVisible()).toBeFalse();
    });

    it('«Certificados…»: panel lateral con alumnos y año; POST de hoy y el nombre .zip con varios, .pdf con uno', async () => {
      crear();
      responder();
      elegirMenu(abrirExportar(), 'Certificados…');
      const panel = () => document.querySelector<HTMLElement>('dialog.pn-panel')!;
      expect(texto(panel().querySelector('.pn-panel__titulo'))).toBe('Certificados de cobros');
      const alumnos = http.expectOne((r) => r.url === `${API}/alumnos`);
      expect(params(alumnos)).toEqual({ incluirInactivos: 'false' });
      expect(alumnos.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      alumnos.flush([
        { id: 2, nombre: 'Zoe', apellidos: 'Ficticia' },
        { id: 1, nombre: 'Ana', apellidos: 'Prueba' },
        { id: 3, nombre: 'Luis', apellidos: 'Ejemplo' },
      ]);
      pintar();
      expect(Array.from(panel().querySelectorAll('.cert__alumno')).map(texto)).toEqual(['Ana Prueba', 'Luis Ejemplo', 'Zoe Ficticia']);
      expect(panel().querySelector<HTMLSelectElement>('#cert-ano')!.value).toBe('2026');
      const generar = botonEn(panel(), 'Generar');
      expect(generar.getAttribute('aria-disabled') === 'true' || generar.disabled).toBeTrue();
      const buscar = panel().querySelector<HTMLInputElement>('#cert-buscar')!;
      buscar.value = 'e';
      buscar.dispatchEvent(new Event('input'));
      pintar();
      botonEn(panel(), 'Seleccionar visibles').click();
      pintar();
      expect(texto(panel().querySelector('.cert__contador'))).toBe('3 seleccionados');
      botonEn(panel(), 'Generar').click();
      pintar();
      const r = http.expectOne(`${API}/tesoreria/certificados-cobros`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toEqual({ alumnoIds: [1, 3, 2], ano: 2026 });
      r.flush(new Blob(['zip']));
      await esperar();
      expect(descargas).toEqual(['certificados_cobros_2026.zip']);
      expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Certificados generados correctamente' }]);
      expect(document.querySelector('dialog.pn-panel')).toBeNull();
      expect(loading.show).not.toHaveBeenCalled();

      elegirMenu(abrirExportar(), 'Certificados…');
      http.expectNone((x) => x.url === `${API}/alumnos`);
      panel().querySelectorAll<HTMLInputElement>('.cert__alumno input')[0].click();
      pintar();
      botonEn(panel(), 'Generar').click();
      pintar();
      http.expectOne(`${API}/tesoreria/certificados-cobros`).flush(new Blob(['pdf']));
      await esperar();
      expect(descargas[1]).toBe('certificado_cobros_2026.pdf');
    });

    it('«Certificados…»: un fallo de los alumnos enseña el bloque de error; un fallo al generar avisa con el texto de hoy', async () => {
      crear();
      responder();
      elegirMenu(abrirExportar(), 'Certificados…');
      const panel = () => document.querySelector<HTMLElement>('dialog.pn-panel')!;
      http.expectOne((r) => r.url === `${API}/alumnos`).flush({}, { status: 500, statusText: 'Error' });
      pintar();
      expect(texto(panel().querySelector('pn-bloque-error'))).toContain('No se pudieron cargar los alumnos');
      botonEn(panel(), 'Reintentar').click();
      pintar();
      http.expectOne((r) => r.url === `${API}/alumnos`).flush([{ id: 1, nombre: 'Ana', apellidos: 'Prueba' }]);
      pintar();
      panel().querySelector<HTMLInputElement>('.cert__alumno input')!.click();
      pintar();
      botonEn(panel(), 'Generar').click();
      pintar();
      http.expectOne(`${API}/tesoreria/certificados-cobros`).flush(new Blob([]), { status: 500, statusText: 'Error' });
      await esperar();
      expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudieron generar los certificados' }]);
      expect(document.querySelector('dialog.pn-panel')).not.toBeNull();
    });

    it('«Certificados…» con «Todos los años» y sin el año actual en la lista: el selector enseña el año que se enviará', async () => {
      crear();
      const p = pendientes();
      p.anios.forEach((r) => r.flush([2023, 2024]));
      p.resumen[0].flush(RESUMEN);
      p.movimientos[0].flush(pagina([PENDIENTE]));
      fixture.detectChanges();
      const ano = el().querySelector<HTMLSelectElement>('#tesoreria-ano')!;
      ano.value = '';
      ano.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      elegirMenu(abrirExportar(), 'Certificados…');
      const panel = () => document.querySelector<HTMLElement>('dialog.pn-panel')!;
      http.expectOne((r) => r.url === `${API}/alumnos`).flush([{ id: 1, nombre: 'Ana', apellidos: 'Prueba' }]);
      pintar();
      const select = panel().querySelector<HTMLSelectElement>('#cert-ano')!;
      expect(Array.from(select.options).map((o) => o.value)).toEqual(['2026', '2024', '2023']);
      expect(select.value).toBe('2026');
      panel().querySelector<HTMLInputElement>('.cert__alumno input')!.click();
      pintar();
      botonEn(panel(), 'Generar').click();
      pintar();
      const r = http.expectOne(`${API}/tesoreria/certificados-cobros`);
      expect(r.request.body).toEqual({ alumnoIds: [1], ano: 2026 });
      r.flush(new Blob(['pdf']));
      await esperar();
      expect(descargas).toEqual(['certificado_cobros_2026.pdf']);
    });

    it('clic fuera en una zona sin foco cierra «Exportar» sin quitar el foco de donde está; dentro no lo cierra', () => {
      crear();
      responder();
      const menu = abrirExportar();
      menu.dispatchEvent(new Event('pointerdown', { bubbles: true }));
      fixture.detectChanges();
      expect(el().querySelector('#tesoreria-menu-exportar')).not.toBeNull();
      document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
      fixture.detectChanges();
      expect(el().querySelector('#tesoreria-menu-exportar')).toBeNull();
      expect(document.activeElement).not.toBe(el().querySelector('#tesoreria-exportar'));
    });
  });

  it('clic fuera en una zona sin foco cierra el «⋯»; dentro del menú no', () => {
    crear();
    responder();
    const menu = abrirMenuFila(1000);
    menu.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(fila(1000).querySelector('[role="menu"]')).not.toBeNull();
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(fila(1000).querySelector('[role="menu"]')).toBeNull();
  });

  it('una relectura silenciosa que cancela una carga de página y falla deja la lista en error, no en esqueleto', () => {
    crear();
    responder();
    const c = fixture.componentInstance;
    c.cargar(false);
    expect(c.estadoLista).toBe('cargando');
    c.cargar(true, true);
    const p = pendientes();
    p.resumen[0].flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(c.estadoLista).toBe('error');
    expect(c.cargando).toBeFalse();
  });

  it('al destruir la pantalla antes de la respuesta no hay aviso ni relectura (escritura y solicitud)', async () => {
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Revertir');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    const put = http.expectOne(`${API}/productos-alumno/1001`);
    fixture.destroy();
    expect(put.cancelled).toBeTrue();
    expect(avisos()).toEqual([]);
    expect(pendientes().movimientos.length).toBe(0);
  });

  it('al destruir antes de la respuesta de «Pedir revertir» no hay aviso', async () => {
    esAdmin = false;
    crear();
    responder([PAGADO]);
    elegirMenu(abrirMenuFila(1001), 'Pedir revertir');
    escribir('#motivo', 'Duplicado');
    botonEn(dialogo()!, 'Enviar solicitud').click();
    await esperar();
    const r = http.expectOne(`${API}/solicitudes`);
    fixture.destroy();
    expect(r.cancelled).toBeTrue();
    expect(avisos()).toEqual([]);
  });

  it('nunca abre SweetAlert2 ni el cargador global en el flujo normal', () => {
    crear();
    responder([PENDIENTE, PAGADO], 2);
    expect(Swal.isVisible()).toBeFalse();
    expect(loading.show).not.toHaveBeenCalled();
    expect(esResumen).toBeDefined();
    expect(esMovimientos).toBeDefined();
  });

  describe('filtro por sede (9-6, decisión 376)', () => {
    function conPermisos(permisos: PermisosSesion): void {
      (TestBed.inject(PermisosSesionService) as unknown as { obtener: () => unknown }).obtener = () => of(permisos);
    }

    function elegirSede(valor: string): void {
      const select = el().querySelector<HTMLSelectElement>('#tesoreria-sede')!;
      select.value = valor;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    it('con una sede no hay selector y las peticiones son las de hoy', () => {
      conPermisos(GESTION_UNA_SEDE);
      crear();
      expect(el().querySelector('#tesoreria-sede')).toBeNull();
      const p = pendientes();
      expect(params(p.resumen[0])).toEqual({ ano: '2026', mes: '10', soloActivos: 'true' });
      expect(params(p.movimientos[0])).toEqual({ ano: '2026', mes: '10', pagado: 'false', soloActivos: 'true', page: '1', size: '25' });
    });

    it('el responsable no ve el selector', () => {
      conPermisos(RESPONSABLE_VARIAS_SEDES);
      crear();
      expect(el().querySelector('#tesoreria-sede')).toBeNull();
    });

    it('con dos sedes y gestión: como el resto de la barra, va con «Aplicar», en resumen y movimientos, desde la página 1', () => {
      conPermisos(GESTION_VARIAS_SEDES);
      crear();
      responder([PENDIENTE], 60);
      fixture.componentInstance.irAPagina(2);
      fixture.detectChanges();
      responder([PENDIENTE], 60);
      elegirSede('2');
      expect(pendientes().movimientos.length).withContext('no se aplica al elegir').toBe(0);
      fixture.componentInstance.aplicar();
      fixture.detectChanges();
      const p = pendientes();
      expect(params(p.resumen[0])['sedeId']).toBe('2');
      expect(params(p.movimientos[0])['sedeId']).toBe('2');
      expect(params(p.movimientos[0])['page']).toBe('1');
    });

    it('paginar y buscar usan la sede ya aplicada, no la elegida sin «Aplicar»', () => {
      conPermisos(GESTION_VARIAS_SEDES);
      crear();
      responder([PENDIENTE], 60);
      elegirSede('2');
      fixture.componentInstance.irAPagina(2);
      fixture.detectChanges();
      let r = responder([PENDIENTE], 60);
      expect(r.resumen).withContext('paginar no relee el resumen').toBeUndefined();
      expect(params(r.movimientos)['sedeId']).withContext('paginar').toBeUndefined();
      fixture.componentInstance.cargar(false);
      r = responder([PENDIENTE], 60);
      expect(params(r.movimientos)['sedeId']).withContext('buscar').toBeUndefined();
      fixture.componentInstance.aplicar();
      fixture.detectChanges();
      r = responder([PENDIENTE], 60);
      expect(params(r.resumen!)['sedeId']).withContext('aplicar, resumen').toBe('2');
      expect(params(r.movimientos)['sedeId']).withContext('aplicar').toBe('2');
      fixture.componentInstance.irAPagina(2);
      r = responder([PENDIENTE], 60);
      expect(params(r.movimientos)['sedeId']).withContext('paginar tras aplicar').toBe('2');
    });

    it('las exportaciones respetan la sede y «Restablecer» la quita', () => {
      conPermisos(GESTION_VARIAS_SEDES);
      crear();
      responder();
      elegirSede('2');
      fixture.componentInstance.exportar('pdf');
      expect(params(http.expectOne((r) => r.url === `${API}/tesoreria/export/pdf`))['sedeId']).toBe('2');
      fixture.componentInstance.informes.cerrar();
      fixture.componentInstance.restablecer();
      fixture.detectChanges();
      const p = pendientes();
      expect(params(p.movimientos[0])['sedeId']).toBeUndefined();
      expect(el().querySelector<HTMLSelectElement>('#tesoreria-sede')!.value).toBe('');
    });

    it('la exportación sin sede es la de hoy', () => {
      conPermisos(GESTION_UNA_SEDE);
      crear();
      responder();
      fixture.componentInstance.exportar('csv');
      expect(params(http.expectOne((r) => r.url === `${API}/tesoreria/export/csv`))).toEqual({ ano: '2026', mes: '10', pagado: 'false', soloActivos: 'true' });
    });
  });
});
