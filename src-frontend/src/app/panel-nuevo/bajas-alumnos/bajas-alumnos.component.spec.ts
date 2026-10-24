import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { itSinReloj } from '../../testing/reloj-prueba';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { BajasAlumnosComponent } from './bajas-alumnos.component';
import { CLAVE_ESTADO } from './bajas-alumnos-vista';

const API = environment.apiUrl;
const setTimeoutReal = window.setTimeout.bind(window);

const ALUMNOS = [
  { id: 1, nombre: 'Lucía', apellidos: 'Ortega Ramos', numeroExpediente: 1042, nif: '12345678Z', activo: true },
  { id: 2, nombre: 'Hugo', apellidos: 'Martín Sanz', numeroExpediente: 1038, nif: '23456789D', activo: true },
  { id: 3, nombre: 'Carmen', apellidos: 'Díaz Ruiz', numeroExpediente: 1051, nif: null, activo: false },
];

function pagina(content = ALUMNOS, totalElements = 23, totalPages = 3) {
  return { content, totalElements, totalPages };
}

describe('BajasAlumnosComponent (8b-3l, «Bajas y eliminaciones» del panel nuevo)', () => {
  let fixture: ComponentFixture<BajasAlumnosComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let avisos: AvisosService;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const textosAvisos = (): string[] => avisos.lista().map((a) => a.texto.replace(/ /g, ' '));
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');

  async function esperar(veces = 4): Promise<void> {
    for (let i = 0; i < veces; i++) {
      await new Promise((r) => setTimeoutReal(r, 5));
    }
    pintarCapas();
    fixture.detectChanges();
  }

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => texto(x) === t || (x.getAttribute('aria-label') ?? '') === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function listado(): TestRequest {
    return http.expectOne((r) => r.url === `${API}/alumnos` && r.method === 'GET');
  }

  /** 8b-3l bis: el resumen de cargos de la página que pide el propietario, con los pagos de cada alumno. */
  function resumenPagina(pagos: Record<number, number> = {}): void {
    http.match((r) => r.url === `${API}/alumnos/cargos-resumen`).forEach((r) => {
      const ids = (r.request.params.get('alumnoIds') ?? '').split(',').filter(Boolean).map(Number);
      r.flush(
        ids.map((alumnoId) => ({
          alumnoId,
          cargos: 0,
          pagados: 0,
          pendientes: 0,
          importePagado: 0,
          importePendiente: 0,
          pagos: pagos[alumnoId] ?? 0,
        }))
      );
    });
    fixture.detectChanges();
  }

  function crear(propietario: boolean, respuesta = pagina(), pagos: Record<number, number> = {}): TestRequest {
    TestBed.overrideProvider(AuthenticationService, {
      useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => propietario, obtenerRoles: () => of([]) },
    });
    fixture = TestBed.createComponent(BajasAlumnosComponent);
    http = TestBed.inject(HttpTestingController);
    avisos = TestBed.inject(AvisosService);
    fixture.detectChanges();
    const req = listado();
    req.flush(respuesta);
    fixture.detectChanges();
    resumenPagina(pagos);
    return req;
  }

  function elegir(id: number): void {
    const casilla = el().querySelector<HTMLInputElement>(`#bajas-casilla-fila-${id}`)!;
    casilla.click();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    sessionStorage.removeItem(CLAVE_ESTADO);
    quitarTokens = ponerTokensDePrueba();
    await TestBed.configureTestingModule({
      imports: [BajasAlumnosComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: AuthenticationService, useValue: {} },
        { provide: PermisosSesionService, useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo, pn-lote-alumnos').forEach((e) => e.remove());
    sessionStorage.removeItem(CLAVE_ESTADO);
    quitarTokens();
  });

  it('la llamada de hoy (10 por página, solo activos) y filas de dos líneas con enlace a la ficha y la pastilla de estado', () => {
    const req = crear(true);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('incluirInactivos')).toBe('false');
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(texto(el().querySelector('h1'))).toBe('Bajas y eliminaciones');
    expect(texto(el().querySelector('.bajas__aviso'))).toBe(
      'Aquí se dan de baja o se eliminan alumnos. Eliminar borra al alumno para siempre.'
    );
    const filas = el().querySelectorAll('.bajas__filas .bajas__fila');
    expect(filas.length).toBe(3);
    const nombre = filas[0].querySelector<HTMLAnchorElement>('.bajas__nombre')!;
    expect(texto(nombre)).toBe('Lucía Ortega Ramos');
    expect(nombre.getAttribute('href')).toBe('/alumnosEditar/1');
    expect(texto(filas[0].querySelector('.bajas__linea2'))).toBe('Exp. 1042 · DNI 12345678Z');
    expect(texto(filas[0].querySelector('.bajas__pastilla'))).toBe('Activo');
    expect(texto(filas[2].querySelector('.bajas__pastilla'))).toBe('De baja');
    expect(texto(filas[0].querySelector('.bajas__acciones .bajas__boton-texto'))).toBe('Dar de baja');
    expect(texto(filas[2].querySelector('.bajas__acciones .bajas__boton-texto'))).toBe('Dar de alta');
    expect(filas[0].querySelector('[aria-label="Más acciones de Lucía Ortega Ramos"]')).not.toBeNull();
    expect(texto(el().querySelector('.bajas__rango'))).toBe('1-10 de 23');
    // La tabla de 1280 lleva las columnas de hoy.
    expect(Array.from(el().querySelectorAll('.bajas__tabla thead th')).map(texto)).toEqual([
      '', 'Foto', 'Nombre', 'Apellidos', 'Expediente', 'DNI', 'Estado', 'Acciones',
    ]);
    expect(JSON.parse(sessionStorage.getItem(CLAVE_ESTADO)!)).toEqual({
      paginaActual: 1,
      tamanoPagina: 10,
      nombreFiltro: '',
      mostrarInactivos: false,
    });
  });

  it('P-4: el gestor no ve «⋯» ni «Eliminar»; el aviso lo dice', () => {
    crear(false);
    expect(el().querySelector('[aria-label^="Más acciones"]')).toBeNull();
    expect(texto(el().querySelector('.bajas__aviso'))).toBe(
      'Aquí se dan de baja alumnos. Eliminarlos para siempre solo puede hacerlo el propietario.'
    );
    elegir(1);
    const barra = el().querySelector('.bajas__seleccion')!;
    expect(texto(barra)).toContain('1 elegido');
    expect(Array.from(barra.querySelectorAll('button')).map(texto)).toEqual(['Quitar selección', 'Dar de baja']);
  });

  it('selección: la barra «N elegidos · Quitar selección · Dar de baja · Eliminar» y «Seleccionar los de esta página»', () => {
    crear(true);
    expect(el().querySelector('.bajas__seleccion')).toBeNull();
    boton(el(), 'Seleccionar los de esta página').click();
    fixture.detectChanges();
    const barra = el().querySelector('.bajas__seleccion')!;
    expect(texto(barra.querySelector('.bajas__seleccion-cuenta'))).toBe('3 elegidos');
    expect(Array.from(barra.querySelectorAll('button')).map(texto)).toEqual(['Quitar selección', 'Dar de baja', 'Eliminar']);
    expect(texto(boton(el(), 'Quitar los de esta página'))).toBe('Quitar los de esta página');
    boton(barra, 'Quitar selección').click();
    fixture.detectChanges();
    expect(el().querySelector('.bajas__seleccion')).toBeNull();
  });

  it('eliminar en lote: nombres, pagos y cargos (P-3), foco en «Cancelar», progreso sin poder cerrar y resumen de los fallos', async () => {
    crear(true);
    elegir(1);
    elegir(2);
    boton(el().querySelector('.bajas__seleccion')!, 'Eliminar').click();
    await esperar();
    const d = dialogo()!;
    expect(d.getAttribute('role')).toBe('alertdialog');
    expect(d.classList).toContain('pn-dialogo--peligro');
    expect(texto(d.querySelector('h2'))).toBe('¿Eliminar a 2 alumnos?');
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('Se borran para siempre. No se puede deshacer.');
    expect(Array.from(d.querySelectorAll('.lote__nombre')).map(texto)).toEqual(['Lucía Ortega Ramos', 'Hugo Martín Sanz']);
    expect(document.activeElement).toBe(boton(d, 'Cancelar'));
    const cargos = http.expectOne((r) => r.url === `${API}/alumnos/cargos-resumen`);
    expect(cargos.request.params.get('alumnoIds')).toBe('1,2');
    expect(cargos.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(texto(d.querySelector('.lote__cargos'))).toBe('Comprobando sus pagos y cargos…');
    cargos.flush([
      { alumnoId: 1, cargos: 3, pagados: 2, pendientes: 1, importePagado: 60, importePendiente: 30 },
      { alumnoId: 2, cargos: 0, pagados: 0, pendientes: 0, importePagado: 0, importePendiente: 0 },
    ]);
    await esperar();
    expect(texto(d.querySelector('.lote__cargos')).replace(/ /g, ' ')).toBe(
      'Entre los 2 tienen 3 cargos: 2 pagados (60,00 €) y 1 pendiente (30,00 €). Se borran con ellos.'
    );
    expect(Array.from(d.querySelectorAll('.lote__dato')).map(texto)).toEqual(['se elimina · 3 cargos', 'se elimina · Sin cargos']);

    boton(d, 'Eliminar 2 alumnos').click();
    await esperar();
    const primero = http.expectOne(`${API}/alumnos/1`);
    expect(primero.request.method).toBe('DELETE');
    expect(primero.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Eliminando 1 de 2…');
    expect(texto(d.querySelector('h2'))).toBe('Eliminando alumnos');
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('No cierres esta ventana hasta que termine.');
    expect(Array.from(d.querySelectorAll('.lote__estado')).map(texto)).toEqual(['En curso', 'Pendiente']);
    expect(boton(d, 'Cancelar').disabled).toBeTrue();
    d.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await esperar();
    expect(dialogo()).not.toBeNull();
    primero.flush(null);
    await esperar();
    http
      .expectOne(`${API}/alumnos/2`)
      .flush({ mensaje: 'No se puede borrar: tiene una devolución pendiente.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(d.querySelector('h2'))).toBe('No se pudo eliminar a 1 de 2');
    expect(Array.from(d.querySelectorAll('.lote__estado')).map(texto)).toEqual(['Eliminado', 'No se eliminó']);
    expect(texto(d.querySelector('.lote__motivo'))).toBe('No se puede borrar: tiene una devolución pendiente.');
    http.expectNone((r) => r.url === `${API}/alumnos`);
    boton(d, 'Cerrar').click();
    await esperar();
    expect(document.querySelector('pn-lote-alumnos')).toBeNull();
    listado().flush(pagina());
  });

  it('eliminar uno desde «⋯»: «¿Eliminar a …?» y, si todo va bien, aviso de hoy y recarga', async () => {
    crear(true);
    boton(el().querySelector('.bajas__filas')!, 'Más acciones de Lucía Ortega Ramos').click();
    fixture.detectChanges();
    const opcion = el().querySelector<HTMLButtonElement>('#bajas-menu-fila-1 [role="menuitem"]')!;
    expect(texto(opcion)).toBe('Eliminar definitivamente');
    expect(document.activeElement).toBe(opcion);
    opcion.click();
    await esperar();
    const d = dialogo()!;
    expect(texto(d.querySelector('h2'))).toBe('¿Eliminar a Lucía Ortega Ramos?');
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('Se borra para siempre. No se puede deshacer.');
    http.expectOne((r) => r.url === `${API}/alumnos/cargos-resumen`).flush([
      { alumnoId: 1, cargos: 0, pagados: 0, pendientes: 0, importePagado: 0, importePendiente: 0 },
    ]);
    await esperar();
    expect(texto(d.querySelector('.lote__cargos'))).toBe('No tiene pagos ni cargos.');
    boton(d, 'Eliminar definitivamente').click();
    await esperar();
    http.expectOne(`${API}/alumnos/1`).flush(null);
    await esperar();
    expect(dialogo()).toBeNull();
    expect(textosAvisos()).toContain('El alumno ha sido eliminado correctamente.');
    listado().flush(pagina());
  });

  it('si el servidor rechaza el borrado (403, P-4), el diálogo enseña su motivo; sin resumen de cargos, lo dice', async () => {
    crear(true);
    fixture.componentInstance.eliminar(fixture.componentInstance.filas[0]);
    fixture.detectChanges();
    await esperar();
    http.expectOne((r) => r.url === `${API}/alumnos/cargos-resumen`).flush(null, { status: 403, statusText: 'Forbidden' });
    await esperar();
    const d = dialogo()!;
    expect(texto(d.querySelector('.lote__cargos'))).toBe(
      'No se pudieron leer sus pagos y cargos. Si tiene pagos, no se elimina y verás el motivo.'
    );
    boton(d, 'Eliminar definitivamente').click();
    await esperar();
    http
      .expectOne(`${API}/alumnos/1`)
      .flush({ mensaje: 'Solo el propietario puede eliminar alumnos definitivamente.' }, { status: 403, statusText: 'Forbidden' });
    await esperar();
    expect(texto(d.querySelector('h2'))).toBe('No se pudo eliminar al alumno');
    expect(texto(d.querySelector('.lote__motivo'))).toBe('Solo el propietario puede eliminar alumnos definitivamente.');
  });

  // ------------------------------------------------------------------ 8b-3l bis (decisión 287): eliminar anonimizando

  it('8b-3l bis: el propietario pide el resumen de cargos de la página; el gestor, no', () => {
    TestBed.overrideProvider(AuthenticationService, {
      useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => false, obtenerRoles: () => of([]) },
    });
    fixture = TestBed.createComponent(BajasAlumnosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    listado().flush(pagina());
    fixture.detectChanges();
    http.expectNone((r) => r.url.includes('cargos-resumen'));
  });

  itSinReloj('con pagos, «⋯» ofrece «Eliminar anonimizando»: diálogo destructivo, foco en «Cancelar» y el POST', async () => {
    crear(true, pagina(), { 1: 3 });
    boton(el().querySelector('.bajas__filas')!, 'Más acciones de Lucía Ortega Ramos').click();
    fixture.detectChanges();
    expect(texto(el().querySelector('#bajas-menu-fila-1 [role="menuitem"]'))).toBe('Eliminar anonimizando');
    boton(el().querySelector('.bajas__filas')!, 'Más acciones de Hugo Martín Sanz').click();
    fixture.detectChanges();
    expect(texto(el().querySelector('#bajas-menu-fila-2 [role="menuitem"]'))).toBe('Eliminar definitivamente');
    fixture.componentInstance.eliminar(fixture.componentInstance.filas[0]);
    fixture.detectChanges();
    await esperar();
    const d = dialogo()!;
    expect(d.getAttribute('role')).toBe('alertdialog');
    expect(d.classList).toContain('pn-dialogo--peligro');
    expect(texto(d.querySelector('h2'))).toBe('¿Eliminar anonimizando a Lucía Ortega Ramos?');
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Sus datos personales se borran para siempre. Sus 3 pagos se quedan en Tesorería como «Alumno eliminado». No se puede deshacer.'
    );
    expect(document.activeElement).toBe(boton(d, 'Cancelar'));
    http.expectOne((r) => r.url === `${API}/alumnos/cargos-resumen`).flush([
      { alumnoId: 1, cargos: 4, pagados: 3, pendientes: 1, importePagado: 90, importePendiente: 30, pagos: 3 },
    ]);
    await esperar();
    expect(texto(d.querySelector('.lote__cargos')).replace(/[  ]/g, ' ')).toBe(
      'Tiene 4 cargos: 3 pagados (90,00 €) y 1 pendiente (30,00 €). Lo pagado se conserva. ' +
        'Tiene 1 cargo sin pagar (30,00 €): se cancela.'
    );
    boton(d, 'Eliminar anonimizando').click();
    await esperar();
    http.expectNone((r) => r.method === 'DELETE');
    const post = http.expectOne(`${API}/alumnos/1/anonimizar`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({});
    expect(post.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    post.flush(null);
    await esperar();
    expect(dialogo()).toBeNull();
    expect(textosAvisos()).toContain('El alumno ha sido eliminado anonimizando. Sus pagos siguen en Tesorería.');
    listado().flush(pagina());
  });

  itSinReloj('un pago en singular: «Su pago se queda en Tesorería…»; «Cancelar» no hace nada', async () => {
    crear(true, pagina(), { 2: 1 });
    fixture.componentInstance.eliminar(fixture.componentInstance.filas[1]);
    fixture.detectChanges();
    await esperar();
    const d = dialogo()!;
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Sus datos personales se borran para siempre. Su pago se queda en Tesorería como «Alumno eliminado». No se puede deshacer.'
    );
    boton(d, 'Cancelar').click();
    await esperar();
    expect(dialogo()).toBeNull();
    http.expectNone((r) => r.url.includes('/anonimizar') || r.method === 'DELETE');
  });

  itSinReloj('en lote con y sin pagos: «se anonimiza (tiene pagos)» o «se elimina», un botón y uno detrás de otro', async () => {
    crear(true, pagina(), { 1: 2 });
    elegir(1);
    elegir(2);
    boton(el().querySelector('.bajas__seleccion')!, 'Eliminar').click();
    await esperar();
    const d = dialogo()!;
    expect(texto(d.querySelector('h2'))).toBe('¿Eliminar a 2 alumnos?');
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Los que no tienen pagos se borran para siempre. De los que tienen pagos se borran sus datos personales y sus pagos ' +
        'se quedan en Tesorería como «Alumno eliminado». No se puede deshacer.'
    );
    expect(document.activeElement).toBe(boton(d, 'Cancelar'));
    http.expectOne((r) => r.url === `${API}/alumnos/cargos-resumen`).flush([
      { alumnoId: 1, cargos: 3, pagados: 2, pendientes: 1, importePagado: 60, importePendiente: 30, pagos: 2 },
      { alumnoId: 2, cargos: 1, pagados: 0, pendientes: 1, importePagado: 0, importePendiente: 20, pagos: 0 },
    ]);
    await esperar();
    expect(Array.from(d.querySelectorAll('.lote__dato')).map((n) => texto(n).replace(/[  ]/g, ' '))).toEqual([
      'se anonimiza (tiene pagos) · 3 cargos · 1 sin pagar (30,00 €): se cancela',
      'se elimina · 1 cargo',
    ]);
    expect(texto(d.querySelector('.lote__cargos')).replace(/[  ]/g, ' ')).toBe(
      'Entre los 2 tienen 4 cargos: 2 pagados (60,00 €) y 2 pendientes (50,00 €). ' +
        'De los alumnos con pagos, lo pagado se conserva y lo sin pagar se cancela; los demás se borran con ellos.'
    );
    expect(Array.from(d.querySelectorAll('.pn-dialogo__accion')).map(texto)).toEqual(['Eliminar 2 alumnos']);
    boton(d, 'Eliminar 2 alumnos').click();
    await esperar();
    const primero = http.expectOne(`${API}/alumnos/1/anonimizar`);
    expect(primero.request.method).toBe('POST');
    http.expectNone(`${API}/alumnos/2`);
    primero.flush(null);
    await esperar();
    const segundo = http.expectOne(`${API}/alumnos/2`);
    expect(segundo.request.method).toBe('DELETE');
    segundo.flush(
      { mensaje: 'Este alumno tiene pagos. Dale de baja o elimínalo anonimizando.' },
      { status: 409, statusText: 'Conflict' }
    );
    await esperar();
    expect(texto(d.querySelector('h2'))).toBe('No se pudo eliminar a 1 de 2');
    expect(Array.from(d.querySelectorAll('.lote__estado')).map(texto)).toEqual(['Anonimizado', 'No se eliminó']);
    expect(texto(d.querySelector('.lote__motivo'))).toBe('Este alumno tiene pagos. Dale de baja o elimínalo anonimizando.');
    boton(d, 'Cerrar').click();
    await esperar();
    listado().flush(pagina());
  });

  it('dar de baja en lote: diálogo normal con el recuento, un PUT por alumno en orden y el aviso de hoy', async () => {
    crear(true);
    elegir(1);
    elegir(2);
    boton(el().querySelector('.bajas__seleccion')!, 'Dar de baja').click();
    await esperar();
    const d = dialogo()!;
    expect(d.classList).not.toContain('pn-dialogo--peligro');
    expect(texto(d.querySelector('h2'))).toBe('¿Dar de baja a 2 alumnos?');
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('Los alumnos seleccionados serán dados de baja.');
    http.expectNone((r) => r.url.includes('cargos-resumen'));
    boton(d, 'Dar de baja a 2 alumnos').click();
    await esperar();
    const uno = http.expectOne(`${API}/alumnos/1/baja`);
    expect(uno.request.method).toBe('PUT');
    expect(uno.request.body).toEqual({});
    uno.flush(null);
    await esperar();
    http.expectOne(`${API}/alumnos/2/baja`).flush(null);
    await esperar();
    expect(dialogo()).toBeNull();
    expect(textosAvisos()).toContain('Los alumnos seleccionados han sido dados de baja correctamente.');
    listado().flush(pagina());
  });

  it('dar de baja uno: la frase de hoy en el diálogo del panel y el PUT', async () => {
    crear(true);
    boton(el().querySelector('.bajas__filas')!, 'Dar de baja a Lucía Ortega Ramos').click();
    await esperar();
    const d = dialogo()!;
    expect(texto(d.querySelector('h2'))).toBe('¿Dar de baja a Lucía Ortega Ramos?');
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('El alumno será dado de baja.');
    boton(d, 'Dar de baja').click();
    await esperar();
    http.expectOne(`${API}/alumnos/1/baja`).flush(null);
    await esperar();
    expect(textosAvisos()).toContain('El alumno ha sido dado de baja correctamente.');
    listado().flush(pagina());
  });

  it('dar de alta con varios deportes de baja: «Selecciona el deporte a activar» con «Todos los deportes»', async () => {
    crear(true);
    boton(el().querySelector('.bajas__filas')!, 'Dar de alta a Carmen Díaz Ruiz').click();
    http.expectOne(`${API}/alumnos/3/deportes`).flush([
      { deporte: 'TAEKWONDO', activo: false },
      { deporte: 'PILATES', activo: false },
    ]);
    await esperar();
    const d = dialogo()!;
    expect(texto(d.querySelector('h2'))).toBe('Selecciona el deporte a activar');
    expect(Array.from(d.querySelectorAll('[role="radio"]')).map(texto)).toEqual(['Todos los deportes', 'Taekwondo', 'Pilates']);
    boton(d, 'Dar de alta').click();
    await esperar();
    expect(texto(d.querySelector('.pn-dialogo__error'))).toBe('Por favor selecciona un deporte');
    boton(d, 'Todos los deportes').click();
    boton(d, 'Dar de alta').click();
    await esperar();
    http.expectOne(`${API}/alumnos/3/deportes/TAEKWONDO/activar`).flush('ok');
    await esperar();
    http.expectOne(`${API}/alumnos/3/deportes/PILATES/activar`).flush('ok');
    await esperar();
    expect(textosAvisos()).toContain('El alumno ha sido dado de alta en todos sus deportes: Taekwondo, Pilates.');
    listado().flush(pagina());
  });

  it('dar de alta sin deportes de baja: lo dice', async () => {
    crear(true);
    boton(el().querySelector('.bajas__filas')!, 'Dar de alta a Carmen Díaz Ruiz').click();
    http.expectOne(`${API}/alumnos/3/deportes`).flush([{ deporte: 'TAEKWONDO', activo: true }]);
    await esperar();
    expect(textosAvisos()).toContain('El alumno ya tiene todos sus deportes activos.');
  });

  it('«Todos» pide también los de baja; la página fuera de rango vuelve a la última; el estado se recuerda', () => {
    sessionStorage.setItem(CLAVE_ESTADO, JSON.stringify({ paginaActual: 9, tamanoPagina: 10, nombreFiltro: 'ana', mostrarInactivos: true }));
    TestBed.overrideProvider(AuthenticationService, {
      useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => true, obtenerRoles: () => of([]) },
    });
    fixture = TestBed.createComponent(BajasAlumnosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const primera = listado();
    expect(primera.request.params.get('page')).toBe('9');
    expect(primera.request.params.get('nombre')).toBe('ana');
    expect(primera.request.params.get('incluirInactivos')).toBe('true');
    primera.flush(pagina(ALUMNOS, 23, 3));
    const segunda = listado();
    expect(segunda.request.params.get('page')).toBe('3');
    segunda.flush(pagina(ALUMNOS, 23, 3));
    fixture.detectChanges();
    expect(JSON.parse(sessionStorage.getItem(CLAVE_ESTADO)!).paginaActual).toBe(3);
    expect(el().querySelector<HTMLInputElement>('#bajas-buscar')!.value).toBe('ana');
    boton(el(), 'Activos').click();
    const tercera = listado();
    expect(tercera.request.params.get('incluirInactivos')).toBe('false');
    expect(tercera.request.params.get('page')).toBe('1');
  });

  it('error: bloque con «Reintentar»; vacío con el texto de hoy y «Quitar filtros»', () => {
    TestBed.overrideProvider(AuthenticationService, {
      useValue: { rolesEstanCargados: () => true, tieneRolAdmin: () => true, obtenerRoles: () => of([]) },
    });
    fixture = TestBed.createComponent(BajasAlumnosComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    listado().flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('.pn-bloque-error__titulo'))).toBe('No hemos podido conectar con el servidor');
    boton(el(), 'Reintentar').click();
    fixture.detectChanges();
    listado().flush(pagina([], 0, 0));
    fixture.detectChanges();
    expect(texto(el().querySelector('.pn-bloque-vacio__titulo'))).toBe(
      'No se encontraron alumnos que coincidan con los criterios de búsqueda.'
    );
    expect(el().querySelector('.pn-bloque-vacio__accion')).toBeNull();
    boton(el(), 'Todos').click();
    listado().flush(pagina([], 0, 0));
    fixture.detectChanges();
    boton(el(), 'Quitar filtros').click();
    const req = listado();
    expect(req.request.params.get('incluirInactivos')).toBe('false');
  });
});
