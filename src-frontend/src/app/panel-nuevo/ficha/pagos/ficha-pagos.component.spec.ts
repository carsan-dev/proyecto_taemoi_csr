import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import Swal from 'sweetalert2';

import { environment } from '../../../../environments/environment';
import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { AvisosService } from '../../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { ReciboPagoService } from '../../cobro/recibo-pago.service';
import type { FichaAlumnoComponent } from '../ficha-alumno.component';
import { FichaPagosComponent } from './ficha-pagos.component';

const API = environment.apiUrl;

function producto(id: number, cambios: Partial<ProductoAlumnoDTO> = {}): ProductoAlumnoDTO {
  return {
    id,
    concepto: `Concepto ${id}`,
    precio: 30,
    cantidad: 1,
    pagado: true,
    fechaAsignacion: new Date(2026, 9, 1),
    fechaPago: new Date(2026, 9, 3),
    ...cambios,
  } as ProductoAlumnoDTO;
}

describe('FichaPagosComponent: «Revertir» un cobro desde la ficha', () => {
  let fixture: ComponentFixture<FichaPagosComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let esAdmin: boolean;
  let panelSede: boolean;
  let productos: ProductoAlumnoDTO[];
  let ficha: {
    alumno: { id: number };
    panelSede: boolean;
    productosAlumno: ProductoAlumnoDTO[];
    productosPaginados: ProductoAlumnoDTO[];
    paginaActualProductos: number;
    totalPaginasProductos: number;
    calcularTotal: (precio: number, cantidad: number) => number;
    isPagandoProducto: () => boolean;
    pagarProducto: jasmine.Spy;
    cambiarPaginaProductos: jasmine.Spy;
    actualizarProductoEnListas: (p: ProductoAlumnoDTO) => void;
    releerPendiente: jasmine.Spy;
  };

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

  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('tbody tr'));
  const fila = (concepto: string): HTMLElement => filas().find((f) => texto(f.querySelector('.pagos__concepto')) === concepto)!;

  function crear(): void {
    ficha.productosAlumno = productos;
    ficha.productosPaginados = [...productos];
    ficha.panelSede = panelSede;
    fixture = TestBed.createComponent(FichaPagosComponent);
    fixture.componentRef.setInput('ficha', ficha as unknown as FichaAlumnoComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function abrirMenu(concepto: string): HTMLElement {
    botonEn(fila(concepto), `Más acciones de ${concepto}`).click();
    fixture.detectChanges();
    return fila(concepto).querySelector<HTMLElement>('[role="menu"]')!;
  }

  const opciones = (menu: HTMLElement): string[] => Array.from(menu.querySelectorAll('[role="menuitem"]')).map(texto);

  function elegir(menu: HTMLElement, t: string): void {
    Array.from(menu.querySelectorAll<HTMLButtonElement>('[role="menuitem"]')).find((b) => texto(b) === t)!.click();
    pintar();
  }

  function escribir(valor: string): void {
    const c = dialogo()!.querySelector<HTMLTextAreaElement>('#motivo')!;
    c.value = valor;
    c.dispatchEvent(new Event('input'));
  }

  beforeEach(async () => {
    Swal.close();
    quitarTokens = ponerTokensDePrueba();
    esAdmin = true;
    panelSede = false;
    productos = [producto(10), producto(11, { pagado: false, fechaPago: null }), producto(12, { devuelto: true })];
    ficha = {
      alumno: { id: 7 },
      panelSede: false,
      productosAlumno: [],
      productosPaginados: [],
      paginaActualProductos: 1,
      totalPaginasProductos: 1,
      calcularTotal: (precio, cantidad) => precio * cantidad,
      isPagandoProducto: () => false,
      pagarProducto: jasmine.createSpy('pagarProducto'),
      cambiarPaginaProductos: jasmine.createSpy('cambiarPaginaProductos'),
      actualizarProductoEnListas: (p) => {
        ficha.productosAlumno = ficha.productosAlumno.map((x) => (x.id === p.id ? p : x));
        ficha.productosPaginados = ficha.productosPaginados.map((x) => (x.id === p.id ? p : x));
      },
      releerPendiente: jasmine.createSpy('releerPendiente'),
    };
    await TestBed.configureTestingModule({
      imports: [FichaPagosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthenticationService,
          useValue: { tieneRolAdmin: () => esAdmin, tieneAccesoAdmin: () => true, rolesEstanCargados: () => true },
        },
        { provide: ReciboPagoService, useValue: { descargar: jasmine.createSpy('descargar'), descargando: () => false } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    Swal.close();
    quitarTokens();
  });

  it('propietario: la fila pagada tiene «⋯» con «Descargar recibo» y «Revertir», en ese orden', () => {
    crear();
    const mas = botonEn(fila('Concepto 10'), 'Más acciones de Concepto 10');
    expect(mas.getAttribute('aria-haspopup')).toBe('menu');
    expect(opciones(abrirMenu('Concepto 10'))).toEqual(['Descargar recibo', 'Revertir']);
    expect(fila('Concepto 10').querySelector('.pagos__menu-opcion--peligro i')!.classList).toContain('bi-arrow-counterclockwise');
  });

  it('propietario: sin recibo posible (cargo cancelado), el menú solo lleva «Revertir»', () => {
    productos = [producto(10, { canceladoEn: '2026-10-05T10:00:00' })];
    crear();
    expect(opciones(abrirMenu('Concepto 10'))).toEqual(['Revertir']);
  });

  it('propietario: pendiente y devuelto quedan como hoy, sin «⋯»', () => {
    crear();
    expect(fila('Concepto 11').querySelector('[aria-haspopup="menu"]')).toBeNull();
    expect(botonEn(fila('Concepto 11'), 'Cobrar Concepto 11')).toBeTruthy();
    expect(fila('Concepto 12').querySelector('[aria-haspopup="menu"]')).toBeNull();
  });

  it('sin ser propietario: sin menú y el botón «Descargar recibo» de siempre', () => {
    esAdmin = false;
    crear();
    expect(el().querySelector('[aria-haspopup="menu"]')).toBeNull();
    expect(botonEn(fila('Concepto 10'), 'Descargar recibo de Concepto 10')).toBeTruthy();
    expect(el().textContent).not.toContain('Pedir revertir');
  });

  it('«Descargar recibo» del menú hace la misma descarga y cierra el menú', () => {
    crear();
    elegir(abrirMenu('Concepto 10'), 'Descargar recibo');
    expect(TestBed.inject(ReciboPagoService).descargar).toHaveBeenCalledWith(10);
    expect(fila('Concepto 10').querySelector('[role="menu"]')).toBeNull();
  });

  it('Esc cierra el menú y devuelve el foco al «⋯»', () => {
    crear();
    const menu = abrirMenu('Concepto 10');
    menu.querySelector<HTMLElement>('[role="menuitem"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(fila('Concepto 10').querySelector('[role="menu"]')).toBeNull();
    expect(document.activeElement).toBe(botonEn(fila('Concepto 10'), 'Más acciones de Concepto 10'));
  });

  it('«Revertir»: diálogo destructivo con los textos de Tesorería y el foco en «Cancelar»', async () => {
    crear();
    elegir(abrirMenu('Concepto 10'), 'Revertir');
    expect(dialogo()!.classList).toContain('pn-dialogo--peligro');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('Revertir cobro');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('El cobro "Concepto 10" pasará a pendiente.');
    expect(texto(dialogo()!.querySelector('label[for="motivo"]'))).toBe('Motivo (opcional)');
    expect(botonEn(dialogo()!, 'Sí, revertir')).toBeTruthy();
    expect(document.activeElement).toBe(botonEn(dialogo()!, 'Cancelar'));
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
  });

  it('cancelar no envía nada', async () => {
    crear();
    elegir(abrirMenu('Concepto 10'), 'Revertir');
    botonEn(dialogo()!, 'Cancelar').click();
    await esperar();
    http.expectNone((r) => r.method === 'PUT');
    expect(botonEn(fila('Concepto 10'), 'Más acciones de Concepto 10').disabled).toBeFalse();
  });

  it('confirmar con motivo: PUT {pagado:false, motivoCambio}; la fila pasa a Pendiente con «Cobrar» y el resumen cambia', async () => {
    crear();
    expect(texto(el().querySelector('.pagos__resumen'))).toBe('3 cargos, 30,00 € pendiente');
    elegir(abrirMenu('Concepto 10'), 'Revertir');
    escribir('  error de marcación  ');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/10`);
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ pagado: false, motivoCambio: 'error de marcación' });
    r.flush({ ...producto(10), pagado: false, fechaPago: null });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Cobro revertido a pendiente' }]);
    expect(texto(fila('Concepto 10').querySelector('.pagos__pastilla'))).toBe('Pendiente');
    expect(botonEn(fila('Concepto 10'), 'Cobrar Concepto 10')).toBeTruthy();
    expect(fila('Concepto 10').querySelector('[aria-haspopup="menu"]')).toBeNull();
    expect(texto(el().querySelector('.pagos__resumen'))).toBe('3 cargos, 60,00 € pendiente');
    expect(ficha.releerPendiente).toHaveBeenCalled();
  });

  it('confirmar sin motivo envía motivoCambio null', async () => {
    crear();
    elegir(abrirMenu('Concepto 10'), 'Revertir');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/10`);
    expect(r.request.body).toEqual({ pagado: false, motivoCambio: null });
    r.flush({});
    await esperar();
  });

  it('403: el aviso de Tesorería y la fila no cambia', async () => {
    crear();
    elegir(abrirMenu('Concepto 10'), 'Revertir');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    http.expectOne(`${API}/productos-alumno/10`).flush({}, { status: 403, statusText: 'Forbidden' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'Solo administradores pueden revertir cobros' }]);
    expect(texto(fila('Concepto 10').querySelector('.pagos__pastilla'))).toContain('Pagado');
    expect(botonEn(fila('Concepto 10'), 'Más acciones de Concepto 10').disabled).toBeFalse();
    expect(ficha.releerPendiente).not.toHaveBeenCalled();
  });

  it('409 (ya está en una entrega de caja): su aviso y la fila no cambia', async () => {
    crear();
    elegir(abrirMenu('Concepto 10'), 'Revertir');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    http
      .expectOne(`${API}/productos-alumno/10`)
      .flush({ mensaje: 'El cobro ya está incluido en una entrega de caja' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo revertir el cobro' }]);
    expect(texto(fila('Concepto 10').querySelector('.pagos__pastilla'))).toContain('Pagado');
    expect(ficha.releerPendiente).not.toHaveBeenCalled();
  });

  it('en vuelo: el «⋯» de esa fila queda deshabilitado y solo esa', async () => {
    productos = [producto(10), producto(13)];
    crear();
    elegir(abrirMenu('Concepto 10'), 'Revertir');
    botonEn(dialogo()!, 'Sí, revertir').click();
    await esperar();
    const r = http.expectOne(`${API}/productos-alumno/10`);
    expect(botonEn(fila('Concepto 10'), 'Más acciones de Concepto 10').disabled).toBeTrue();
    expect(botonEn(fila('Concepto 13'), 'Más acciones de Concepto 13').disabled).toBeFalse();
    r.flush({});
    await esperar();
  });
});
