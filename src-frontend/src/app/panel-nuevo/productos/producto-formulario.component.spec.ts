import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { CrearProductoComponent } from '../../componentes/endpoints/crear-producto/crear-producto.component';
import { EditarProductoComponent } from '../../componentes/endpoints/editar-producto/editar-producto.component';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { LoadingService } from '../../servicios/generales/loading.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { ProductoFormularioComponent } from './producto-formulario.component';

const API = environment.apiUrl;

const PRODUCTO = { id: 7, concepto: 'Viaje al campeonato', precio: 1010.5, vendibleProfesor: true };

/** Lo que se compara de una escritura: método, ruta, credenciales y cuerpo (JSON tal como viaja). */
function forma(r: TestRequest) {
  return {
    metodo: r.request.method,
    url: r.request.urlWithParams,
    credenciales: r.request.withCredentials,
    cuerpo: JSON.stringify(r.request.body),
  };
}

describe('ProductoFormularioComponent (8b-3g, crear y editar producto del panel nuevo)', () => {
  let fixture: ComponentFixture<ProductoFormularioComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  /** El id de la ruta (sin él, crear): lo leen las dos pantallas, la de hoy por `snapshot` y la nueva por `params`. */
  let idRuta: string | undefined;
  let navegar: jasmine.Spy;
  let atras: jasmine.Spy;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();

  async function esperar(): Promise<void> {
    for (let i = 0; i < 10; i++) {
      await Promise.resolve();
    }
    pintarCapas();
    fixture.detectChanges();
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

  function escribirEn(raiz: HTMLElement, selector: string, valor: string, salir = true): void {
    const campo = raiz.querySelector<HTMLInputElement>(selector)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    if (salir) {
      campo.dispatchEvent(new Event('blur'));
    }
  }

  function escribir(id: string, valor: string, salir = true): void {
    escribirEn(el(), `#${id}`, valor, salir);
    fixture.detectChanges();
  }

  const guardar = (): HTMLButtonElement => el().querySelector<HTMLButtonElement>('.producto-form__guardar')!;
  const vendible = (): HTMLInputElement => el().querySelector<HTMLInputElement>('#producto-vendible')!;
  const precio = (): HTMLInputElement => el().querySelector<HTMLInputElement>('#producto-precio')!;

  function crear(id?: number): void {
    idRuta = id === undefined ? undefined : String(id);
    fixture = TestBed.createComponent(ProductoFormularioComponent);
    fixture.detectChanges();
  }

  function editar(producto: object = PRODUCTO): void {
    crear(7);
    http.expectOne(`${API}/productos/7`).flush(producto);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    quitarTokens = ponerTokensDePrueba();
    await TestBed.configureTestingModule({
      imports: [ProductoFormularioComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            get params() {
              return of(idRuta === undefined ? {} : { id: idRuta });
            },
            get snapshot() {
              return { params: idRuta === undefined ? {} : { id: idRuta } };
            },
          },
        },
        { provide: LoadingService, useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']) },
        { provide: PermisosSesionService, useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
    idRuta = undefined;
    http = TestBed.inject(HttpTestingController);
    navegar = spyOn(TestBed.inject(Router), 'navigate').and.returnValue(Promise.resolve(true));
    atras = spyOn(TestBed.inject(Location), 'back');
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    quitarTokens();
  });

  // ------------------------------------------------------------------ crear

  it('crear: título, enlace «Productos», concepto y precio obligatorios, «Lo puede vender el profesor» con la ayuda de hoy', () => {
    crear();
    expect(texto(el().querySelector('h1'))).toBe('Crear producto');
    expect(el().querySelector('.producto-form__miga a')!.getAttribute('href')).toBe('/productosListar');
    expect(texto(el().querySelector('label[for="producto-concepto"]'))).toBe('Concepto obligatorio');
    expect(texto(el().querySelector('label[for="producto-precio"]'))).toBe('Precio obligatorio');
    expect(precio().type).toBe('text');
    expect(precio().getAttribute('inputmode')).toBe('decimal');
    expect(texto(el().querySelector('#producto-precio-ayuda'))).toBe('En euros, con coma para los decimales: 25 o 1.010,50.');
    expect(vendible().type).toBe('checkbox');
    expect(vendible().checked).toBeFalse();
    expect(texto(el().querySelector('label[for="producto-vendible"]'))).toBe('Lo puede vender el profesor');
    expect(texto(el().querySelector('#producto-vendible-ayuda'))).toBe(
      'Aparecerá en Mi caja para que el profesor lo cobre en efectivo. Las mensualidades, licencias, exámenes, reservas y productos de grado nunca se venden desde ahí.'
    );
    expect(texto(guardar())).toBe('Crear producto');
  });

  /**
   * Crea con la pantalla de hoy y con la nueva y devuelve las dos peticiones. La de hoy va primero: `createComponent`
   * saca del documento las raíces anteriores (la de hoy solo necesita sus eventos; la nueva, el envío del formulario).
   */
  function crearConLasDos(concepto: string, precioHoy: string, precioNuevo: string, marcar: boolean): [TestRequest, TestRequest] {
    const hoy = TestBed.createComponent(CrearProductoComponent);
    hoy.detectChanges();
    const raizHoy = hoy.nativeElement as HTMLElement;
    escribirEn(raizHoy, '#concepto', concepto);
    escribirEn(raizHoy, '#precio', precioHoy);
    if (marcar) {
      raizHoy.querySelector<HTMLInputElement>('#vendibleProfesor')!.click();
    }
    hoy.detectChanges();
    hoy.componentInstance.onSubmit();
    const deHoy = http.expectOne(`${API}/productos`);

    crear();
    escribir('producto-concepto', concepto);
    escribir('producto-precio', precioNuevo);
    if (marcar) {
      vendible().click();
      fixture.detectChanges();
    }
    guardar().click();
    fixture.detectChanges();
    const nueva = http.expectOne(`${API}/productos`);
    hoy.destroy();
    return [deHoy, nueva];
  }

  it('crear: el MISMO POST /productos de hoy, campo a campo (precio es-ES «1.010,50» = hoy «1010.50»)', () => {
    const casos: [string, string, string, boolean][] = [
      ['Viaje al campeonato', '1010.50', '1.010,50', false],
      ['Camiseta del club', '12.5', '12,5', true],
      ['Cuota', '25', '25', false],
      ['Seguro', '1.5', '1.5', true],
      ['Gratis', '0', '0', false],
    ];
    for (const [concepto, hoy, nuevo, marcar] of casos) {
      const [deHoy, nueva] = crearConLasDos(concepto, hoy, nuevo, marcar);
      expect(forma(nueva)).withContext(nuevo).toEqual(forma(deHoy));
      expect(Object.keys(nueva.request.body)).toEqual(['concepto', 'precio', 'vendibleProfesor']);
      deHoy.flush({ id: 1 });
      nueva.flush({ id: 2 });
      fixture.destroy();
    }
  });

  it('crear: tras guardar, aviso y a Productos (como hoy)', async () => {
    crear();
    escribir('producto-concepto', 'Camiseta del club');
    escribir('producto-precio', '12,50');
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne(`${API}/productos`);
    expect(r.request.body).toEqual({ concepto: 'Camiseta del club', precio: 12.5, vendibleProfesor: false });
    expect(guardar().getAttribute('aria-busy')).toBe('true');
    expect(texto(guardar())).toBe('Guardando…');
    // Sin doble envío.
    guardar().click();
    fixture.detectChanges();
    http.expectNone(`${API}/productos`);
    r.flush({ id: 30 });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'otro', texto: 'Producto creado correctamente.' }]);
    expect(navegar).toHaveBeenCalledOnceWith(['/productosListar']);
  });

  it('crear: un error avisa (mensaje del servidor o el de hoy) y deja el formulario como estaba', async () => {
    crear();
    escribir('producto-concepto', 'Camiseta');
    escribir('producto-precio', '12,50');
    guardar().click();
    fixture.detectChanges();
    http.expectOne(`${API}/productos`).flush({}, { status: 500, statusText: 'Error' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo crear el producto.' }]);
    expect(navegar).not.toHaveBeenCalled();
    expect(precio().value).toBe('12,50');
    expect(guardar().getAttribute('aria-busy')).toBeNull();
  });

  it('con errores: la acción va con aria-disabled; pulsarla no envía y enseña el resumen con enlaces a cada campo', () => {
    crear();
    expect(guardar().getAttribute('aria-disabled')).toBe('true');
    guardar().click();
    fixture.detectChanges();
    http.expectNone(() => true);
    const resumen = el().querySelector<HTMLElement>('.producto-form__resumen')!;
    expect(resumen.getAttribute('role')).toBe('alert');
    expect(document.activeElement).toBe(resumen);
    expect(texto(resumen.querySelector('h2'))).toBe('Revisa 2 campos antes de guardar');
    const enlaces = Array.from(resumen.querySelectorAll<HTMLAnchorElement>('a'));
    expect(enlaces.map((a) => [a.getAttribute('href'), texto(a)])).toEqual([
      ['#producto-concepto', 'El concepto es obligatorio'],
      ['#producto-precio', 'El precio es obligatorio'],
    ]);
    enlaces[1].click();
    fixture.detectChanges();
    expect(document.activeElement).toBe(precio());
    expect(precio().getAttribute('aria-invalid')).toBe('true');
    expect(precio().getAttribute('aria-describedby')).toBe('producto-precio-ayuda producto-precio-error');
  });

  it('precio: lo que hoy no valía sigue sin enviarse (vacío, texto, negativo) y lo ambiguo se pregunta; error bajo el campo', () => {
    crear();
    escribir('producto-concepto', 'Camiseta');
    const casos: [string, string][] = [
      ['', 'El precio es obligatorio'],
      ['abc', 'Escribe el precio con cifras, por ejemplo 25 o 1.010,50'],
      ['-5', 'El precio debe ser mayor o igual a 0'],
      ['1.234', '«1.234» puede ser 1234 € o 1,234 €: escríbelo sin punto (1234) o con coma (1,234)'],
      ['1,010.50', 'Escribe el precio con cifras, por ejemplo 25 o 1.010,50'],
    ];
    for (const [escrito, mensaje] of casos) {
      escribir('producto-precio', escrito);
      expect(texto(el().querySelector('#producto-precio-error'))).withContext(escrito).toBe(mensaje);
      expect(guardar().getAttribute('aria-disabled')).toBe('true');
      guardar().click();
      fixture.detectChanges();
      http.expectNone(`${API}/productos`);
    }
  });

  it('precio: al salir del campo se escribe en es-ES («1010.5» pasa a «1.010,50»); lo que no se lee se deja como está', () => {
    crear();
    escribir('producto-precio', '1010.5');
    expect(precio().value).toBe('1.010,50');
    escribir('producto-precio', '25');
    expect(precio().value).toBe('25,00');
    escribir('producto-precio', '1.234');
    expect(precio().value).toBe('1.234');
    // Mientras se escribe, no se toca.
    escribir('producto-precio', '7', false);
    expect(precio().value).toBe('7');
  });

  it('pulsar la acción con el foco en un campo no le quita el foco antes del clic (el error del campo movería el botón)', () => {
    crear();
    precio().focus();
    for (const boton of [guardar(), botonEn(el(), 'Cancelar')]) {
      const pulsar = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
      boton.dispatchEvent(pulsar);
      expect(pulsar.defaultPrevented).withContext(texto(boton)).toBeTrue();
    }
  });

  it('cancelar: no envía nada y vuelve atrás, como hoy', () => {
    crear();
    escribir('producto-concepto', 'Camiseta');
    escribir('producto-precio', '12');
    botonEn(el(), 'Cancelar').click();
    fixture.detectChanges();
    http.expectNone(() => true);
    expect(atras).toHaveBeenCalledTimes(1);
  });

  // ------------------------------------------------------------------ editar

  it('editar: el GET /productos/{id} de hoy con ERROR_HANDLED_LOCALLY; campos con el precio en es-ES', () => {
    crear(7);
    const r = http.expectOne(`${API}/productos/7`);
    expect(r.request.method).toBe('GET');
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    r.flush(PRODUCTO);
    fixture.detectChanges();
    expect(texto(el().querySelector('h1'))).toBe('Editar producto');
    expect(el().querySelector<HTMLInputElement>('#producto-concepto')!.value).toBe('Viaje al campeonato');
    expect(precio().value).toBe('1.010,50');
    expect(vendible().checked).toBeTrue();
    expect(texto(guardar())).toBe('Guardar cambios');
    expect(guardar().getAttribute('aria-disabled')).toBeNull();
  });

  it('editar: un producto sin el campo queda desmarcado (como hoy)', () => {
    editar({ id: 7, concepto: 'Cuota', precio: 30 });
    expect(vendible().checked).toBeFalse();
  });

  /** Edita con la pantalla de hoy y con la nueva el mismo producto y devuelve las dos peticiones. */
  function editarConLasDos(cambios: { concepto?: string; precioHoy?: string; precioNuevo?: string; alternar?: boolean }): [TestRequest, TestRequest] {
    const hoy = TestBed.createComponent(EditarProductoComponent);
    hoy.detectChanges();
    http.expectOne(`${API}/productos/7`).flush(PRODUCTO);
    hoy.detectChanges();
    const raizHoy = hoy.nativeElement as HTMLElement;
    if (cambios.concepto !== undefined) {
      escribirEn(raizHoy, '#concepto', cambios.concepto);
    }
    if (cambios.precioHoy !== undefined) {
      escribirEn(raizHoy, '#precio', cambios.precioHoy);
    }
    if (cambios.alternar) {
      raizHoy.querySelector<HTMLInputElement>('#vendibleProfesor')!.click();
    }
    hoy.detectChanges();
    hoy.componentInstance.onSubmit();
    const deHoy = http.expectOne(`${API}/productos/7`);

    editar();
    if (cambios.concepto !== undefined) {
      escribir('producto-concepto', cambios.concepto);
    }
    if (cambios.precioNuevo !== undefined) {
      escribir('producto-precio', cambios.precioNuevo);
    }
    if (cambios.alternar) {
      vendible().click();
      fixture.detectChanges();
    }
    guardar().click();
    fixture.detectChanges();
    const nueva = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/productos/7`);
    hoy.destroy();
    return [deHoy, nueva];
  }

  it('editar: el MISMO PUT /productos/{id} de hoy, campo a campo, sin tocar nada y cambiando cada campo', () => {
    const casos = [
      {},
      { concepto: 'Viaje al nacional' },
      { precioHoy: '999.99', precioNuevo: '999,99' },
      { precioHoy: '1234567.89', precioNuevo: '1.234.567,89' },
      { alternar: true },
      { concepto: 'Viaje', precioHoy: '0', precioNuevo: '0', alternar: true },
    ];
    for (const cambios of casos) {
      idRuta = '7';
      const [deHoy, nueva] = editarConLasDos(cambios);
      expect(deHoy.request.method).toBe('PUT');
      expect(forma(nueva)).withContext(JSON.stringify(cambios)).toEqual(forma(deHoy));
      expect(Object.keys(nueva.request.body)).toEqual(['concepto', 'precio', 'vendibleProfesor']);
      deHoy.flush({});
      nueva.flush({});
      fixture.destroy();
    }
  });

  it('editar: tras guardar, aviso y a Productos; el error del servidor (renombrar con cargos) se enseña tal cual', async () => {
    editar();
    escribir('producto-concepto', 'Viaje al nacional');
    guardar().click();
    fixture.detectChanges();
    const mensaje = 'No se puede cambiar el nombre de «Viaje al campeonato»: ya tiene cargos y su nombre identifica los cobros de cada periodo (se duplicarían). Cree un producto nuevo.';
    http.expectOne(`${API}/productos/7`).flush({ mensaje }, { status: 400, statusText: 'Bad' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: mensaje }]);
    expect(navegar).not.toHaveBeenCalled();
    guardar().click();
    fixture.detectChanges();
    http.expectOne(`${API}/productos/7`).flush({});
    await esperar();
    expect(avisos()[1]).toEqual({ tipo: 'otro', texto: 'Producto actualizado correctamente.' });
    expect(navegar).toHaveBeenCalledOnceWith(['/productosListar']);
  });

  it('editar: esqueleto y, si la lectura falla, bloque de error con «Reintentar» (no SweetAlert2)', () => {
    crear(7);
    expect(el().querySelector('pn-esqueleto')).not.toBeNull();
    http.expectOne(`${API}/productos/7`).flush({}, { status: 404, statusText: 'No' });
    fixture.detectChanges();
    const error = el().querySelector('pn-bloque-error')!;
    expect(texto(error)).toContain('No se pudo cargar el producto');
    expect(navegar).not.toHaveBeenCalled();
    botonEn(error, 'Reintentar').click();
    fixture.detectChanges();
    http.expectOne(`${API}/productos/7`).flush(PRODUCTO);
    fixture.detectChanges();
    expect(precio().value).toBe('1.010,50');
  });

  it('guardar no se cancela al salir: la petición sigue y no navega tras destruir', async () => {
    editar();
    guardar().click();
    fixture.detectChanges();
    const r = http.expectOne((x) => x.method === 'PUT');
    fixture.destroy();
    expect(r.cancelled).toBeFalse();
    r.flush({});
    await esperar();
    expect(navegar).not.toHaveBeenCalled();
  });
});
