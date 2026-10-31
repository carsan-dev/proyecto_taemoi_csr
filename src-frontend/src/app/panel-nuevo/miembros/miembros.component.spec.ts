import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { Capacidad, PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { FichaCompleta, Miembro, PaginaDeMiembros, RolDeMiembro, RolMembresia } from '../../servicios/endpoints/miembros.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { MiembroFichaComponent } from './miembro-ficha.component';
import { MiembrosPanelComponent } from './miembros.component';

const API = `${environment.apiUrl}/miembros`;
const GRUPOS = `${environment.apiUrl}/grupos/resumen`;

function rol(r: RolMembresia, activa = true, cargo: string | null = null): RolDeMiembro {
  return { rol: r, activa, cargo };
}

function miembro(cambios: Partial<Miembro> = {}): Miembro {
  return {
    usuarioId: 1,
    nombre: 'Lucía',
    apellidos: 'Martín Gil',
    email: 'lucia@ejemplo.test',
    invitacionPendiente: false,
    roles: [rol('PROFESOR')],
    sedes: [],
    numeroGrupos: 0,
    ...cambios,
  };
}

function ficha(cambios: Partial<FichaCompleta> = {}): FichaCompleta {
  return { ...miembro(), grupos: [], ...cambios };
}

function pagina(contenido: Miembro[], extra: Partial<PaginaDeMiembros> = {}): PaginaDeMiembros {
  return { contenido, pagina: 0, tamano: 20, total: contenido.length, totalPaginas: contenido.length ? 1 : 0, ...extra };
}

const SEDES = [
  { id: 1, nombre: 'Casariche', activa: true },
  { id: 2, nombre: 'Mollina', activa: true },
  { id: 3, nombre: 'Herrera', activa: false },
];

function permisos(capacidades: Capacidad[], variasSedes = true): PermisosSesion {
  return { roles: [], esAdminPlataforma: false, sedes: SEDES, variasSedes, tieneGrupos: false, capacidades };
}

const PROPIETARIA: Capacidad[] = ['MIEMBROS', 'SEDES', 'DAR_GESTION'];
const GESTORA: Capacidad[] = ['MIEMBROS', 'SEDES'];

describe('MiembrosPanelComponent (E9-4, pantalla Miembros del panel nuevo)', () => {
  let fixture: ComponentFixture<MiembrosPanelComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let permisosActuales: PermisosSesion;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/[\s  ]+/g, ' ').trim();
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const panel = (): HTMLElement | null => el().querySelector<HTMLElement>('dialog.pn-panel');
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.miembros__fila'));
  const listados = (): TestRequest[] => http.match((r) => r.method === 'GET' && r.url === API);

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

  function avisos(): { tipo: string; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : a.classList.contains('pn-aviso--info') ? 'info' : 'exito',
      texto: texto(a.querySelector('.pn-aviso__texto')),
    }));
  }

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find((x) => (x.getAttribute('aria-label') ?? '').trim() === t || texto(x) === t);
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function cargar(pag: PaginaDeMiembros = pagina([miembro()]), caps: Capacidad[] = PROPIETARIA): void {
    permisosActuales = permisos(caps);
    fixture = TestBed.createComponent(MiembrosPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    listados().forEach((r) => r.flush(pag));
    fixture.detectChanges();
  }

  /** Abre la ficha de una fila y responde la ficha completa (y el catálogo de grupos si es profesor). */
  async function abrirFicha(f: FichaCompleta, id = f.usuarioId): Promise<void> {
    el().querySelector<HTMLButtonElement>(`.miembros__fila[data-id="${id}"]`)!.click();
    pintar();
    http.expectOne((r) => r.method === 'GET' && r.url === `${API}/${id}`).flush(f);
    await esperar();
    const catalogo = http.match((r) => r.url === GRUPOS);
    catalogo.forEach((r) => r.flush([{ id: 10, nombre: 'Infantil martes', numAlumnos: 3, turnos: [] }, { id: 11, nombre: 'Adultos', numAlumnos: 5, turnos: [] }]));
    await esperar();
  }

  const casilla = (r: string): HTMLInputElement => panel()!.querySelector<HTMLInputElement>(`[data-rol="${r}"] input[type="checkbox"]`)!;
  const lineaRol = (r: string): HTMLElement => panel()!.querySelector<HTMLElement>(`[data-rol="${r}"]`)!;
  const titulos = (): string[] => Array.from(panel()!.querySelectorAll('.miembros__seccion-titulo')).map((t) => texto(t));
  const subtitulos = (): string[] => Array.from(panel()!.querySelectorAll('.miembros__subbloque-titulo')).map((t) => texto(t));

  function marcar(c: HTMLInputElement, valor: boolean): void {
    c.checked = valor;
    c.dispatchEvent(new Event('change'));
    pintar();
  }

  async function confirmar(): Promise<void> {
    (dialogo()!.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
    await esperar();
  }

  beforeEach(async () => {
    jasmine.clock().install();
    // debounceTime de RxJS compara con Date.now(): sin fecha simulada no dispara con tick().
    jasmine.clock().mockDate();
    quitarTokens = ponerTokensDePrueba();
    await TestBed.configureTestingModule({
      imports: [MiembrosPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of(permisosActuales), reiniciar: () => undefined } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    quitarTokens();
    jasmine.clock().uninstall();
  });

  // ------------------------------------------------------------------ lista

  it('pide la primera página (page 0, size 20) con ERROR_HANDLED_LOCALLY y pinta una fila por miembro con sus roles activos', () => {
    permisosActuales = permisos(PROPIETARIA);
    fixture = TestBed.createComponent(MiembrosPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const [r] = listados();
    expect(r.request.params.get('page')).toBe('0');
    expect(r.request.params.get('size')).toBe('20');
    expect(r.request.params.has('q')).toBeFalse();
    expect(r.request.params.has('incluirFamilias')).toBeFalse();
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    r.flush(
      pagina([
        miembro({ roles: [rol('PROPIETARIO'), rol('ADMINISTRADOR', false), rol('PROFESOR')], numeroGrupos: 2 }),
        miembro({
          usuarioId: 2,
          nombre: 'Carlos',
          apellidos: 'Díaz',
          email: 'carlos@ejemplo.test',
          invitacionPendiente: true,
          roles: [rol('RESPONSABLE_SEDE')],
          sedes: [{ id: 1, nombre: 'Casariche' }],
        }),
      ])
    );
    fixture.detectChanges();

    expect(texto(el().querySelector('h1'))).toBe('Miembros');
    expect(texto(el().querySelector('.miembros__cabecera .miembros__boton-primario'))).toBe('Añadir miembro');
    expect(filas().length).toBe(2);
    expect(texto(filas()[0].querySelector('.miembros__nombre'))).toBe('Abrir la ficha de Lucía Martín Gil');
    expect(texto(filas()[0].querySelector('.miembros__email'))).toBe('lucia@ejemplo.test');
    expect(Array.from(filas()[0].querySelectorAll('.miembros__etiqueta')).map((e) => texto(e))).toEqual(['Propietario', 'Profesor']);
    expect(texto(filas()[0].querySelector('.miembros__linea'))).toBe('2 grupos');
    expect(Array.from(filas()[1].querySelectorAll('.miembros__etiqueta')).map((e) => texto(e))).toEqual(['Responsable de sede', 'Invitación pendiente']);
    expect(texto(filas()[1].querySelector('.miembros__linea'))).toBe('Casariche');
    expect(filas().every((f) => f.tagName === 'BUTTON')).toBeTrue();
  });

  // Última página con una sola fila, ya abierta: `total` y `totalPaginas` antes de borrarla.
  function enLaUltimaPagina(numeroDePagina: number): void {
    const hasta = { total: (numeroDePagina - 1) * 20 + 1, totalPaginas: numeroDePagina };
    cargar(pagina([miembro()], hasta));
    fixture.componentInstance.irAPagina(numeroDePagina);
    listados()[0].flush(pagina([miembro({ usuarioId: 99 })], { ...hasta, pagina: numeroDePagina - 1 }));
    fixture.detectChanges();
  }

  const paginaPedida = (r: TestRequest): string | null => r.request.params.get('page');

  it('recarga silenciosa de una página que se quedó vacía: va a la última válida, sin pasar por «Aún no hay miembros»', () => {
    enLaUltimaPagina(3);
    fixture.componentInstance.refrescarTrasCierre();
    const [vacia] = listados();
    expect(paginaPedida(vacia)).toBe('2');
    vacia.flush(pagina([], { pagina: 2, total: 40, totalPaginas: 2 }));
    fixture.detectChanges();

    expect(el().textContent).not.toContain('Aún no hay miembros');
    expect(fixture.componentInstance.pagina).toBe(2);
    const [valida] = listados();
    expect(paginaPedida(valida)).toBe('1');
    valida.flush(pagina([miembro({ usuarioId: 7 })], { pagina: 1, total: 40, totalPaginas: 2 }));
    fixture.detectChanges();

    expect(filas().map((f) => f.getAttribute('data-id'))).toEqual(['7']);
    expect(el().querySelector('pn-esqueleto')).toBeNull();
    expect(el().textContent).not.toContain('Aún no hay miembros');
    expect(fixture.componentInstance.totalPaginas).toBe(2);
  });

  it('carga normal de una página vacía: sigue el esqueleto hasta tener la última válida', () => {
    enLaUltimaPagina(2);
    fixture.componentInstance.cargar();
    listados()[0].flush(pagina([], { pagina: 1, total: 20, totalPaginas: 1 }));
    fixture.detectChanges();

    expect(el().querySelector('pn-esqueleto')).not.toBeNull();
    expect(el().textContent).not.toContain('Aún no hay miembros');
    const [valida] = listados();
    expect(paginaPedida(valida)).toBe('0');
    valida.flush(pagina([miembro({ usuarioId: 7 })], { total: 20, totalPaginas: 1 }));
    fixture.detectChanges();
    expect(filas().length).toBe(1);
  });

  it('sin nadie en ninguna página: acaba en la primera y entonces sí dice «Aún no hay miembros»', () => {
    enLaUltimaPagina(3);
    fixture.componentInstance.refrescarTrasCierre();
    listados()[0].flush(pagina([], { pagina: 2, total: 0, totalPaginas: 0 }));
    fixture.detectChanges();
    const [primera] = listados();
    expect(paginaPedida(primera)).toBe('0');
    primera.flush(pagina([]));
    fixture.detectChanges();

    expect(fixture.componentInstance.pagina).toBe(1);
    expect(el().textContent).toContain('Aún no hay miembros');
    expect(listados().length).toBe(0);
  });

  it('una respuesta vacía que aún cuenta esa página no entra en bucle: retrocede una cada vez', () => {
    enLaUltimaPagina(3);
    fixture.componentInstance.refrescarTrasCierre();
    listados()[0].flush(pagina([], { pagina: 2, total: 41, totalPaginas: 3 }));
    fixture.detectChanges();
    const [segunda] = listados();
    expect(paginaPedida(segunda)).toBe('1');
    segunda.flush(pagina([], { pagina: 1, total: 41, totalPaginas: 3 }));
    fixture.detectChanges();
    const [primera] = listados();
    expect(paginaPedida(primera)).toBe('0');
    primera.flush(pagina([], { total: 0, totalPaginas: 0 }));
    fixture.detectChanges();
    expect(listados().length).toBe(0);
  });

  it('cargando: esqueleto; error: bloque con «Reintentar» que vuelve a pedir', () => {
    permisosActuales = permisos(PROPIETARIA);
    fixture = TestBed.createComponent(MiembrosPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    jasmine.clock().tick(300);
    fixture.detectChanges();
    expect(texto(el().querySelector('pn-esqueleto [role="status"]'))).toBe('Cargando miembros');
    listados()[0].flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('pn-bloque-error'))).toContain('No se pudieron cargar los miembros');
    boton(el(), 'Reintentar').click();
    fixture.detectChanges();
    listados()[0].flush(pagina([miembro()]));
    fixture.detectChanges();
    expect(el().querySelector('pn-bloque-error')).toBeNull();
    expect(filas().length).toBe(1);
  });

  it('buscar espera 300 ms, vuelve a la página 1 y manda q por la API, no por la dirección', () => {
    cargar(pagina([miembro()], { totalPaginas: 3, total: 50 }));
    const buscador = el().querySelector<HTMLInputElement>('#miembros-buscar')!;
    buscador.value = 'lucia@';
    buscador.dispatchEvent(new Event('input'));
    jasmine.clock().tick(299);
    expect(listados().length).toBe(0);
    jasmine.clock().tick(1);
    const [r] = listados();
    expect(r.request.params.get('q')).toBe('lucia@');
    expect(r.request.params.get('page')).toBe('0');
    expect(location.href).not.toContain('lucia');
    r.flush(pagina([miembro()]));
  });

  it('el selector de rol y «Incluir familias» filtran en el servidor', () => {
    cargar();
    const opciones = Array.from(el().querySelectorAll<HTMLOptionElement>('#miembros-rol option')).map((o) => texto(o));
    expect(opciones).toEqual(['Todos los roles', 'Propietario', 'Gestor', 'Responsable de sede', 'Profesor']);
    const select = el().querySelector<HTMLSelectElement>('#miembros-rol')!;
    select.value = 'ADMINISTRADOR';
    select.dispatchEvent(new Event('change'));
    let [r] = listados();
    expect(r.request.params.get('rol')).toBe('ADMINISTRADOR');
    r.flush(pagina([miembro()]));
    fixture.detectChanges();
    const familias = el().querySelector<HTMLInputElement>('#miembros-familias')!;
    familias.checked = true;
    familias.dispatchEvent(new Event('change'));
    [r] = listados();
    expect(r.request.params.get('incluirFamilias')).toBe('true');
    expect(r.request.params.get('rol')).toBe('ADMINISTRADOR');
    r.flush(pagina([miembro()]));
  });

  it('paginación de servidor: rango, páginas y «Siguiente» piden page+1', () => {
    cargar(pagina([miembro()], { total: 45, totalPaginas: 3 }));
    expect(texto(el().querySelector('.miembros__rango'))).toBe('1–20 de 45');
    expect(el().querySelector('.miembros__pagina-num[aria-current="page"]')!.textContent!.trim()).toBe('1');
    expect((boton(el(), 'Anterior') as HTMLButtonElement).disabled).toBeTrue();
    boton(el(), 'Siguiente').click();
    const [r] = listados();
    expect(r.request.params.get('page')).toBe('1');
    r.flush(pagina([miembro()], { pagina: 1, total: 45, totalPaginas: 3 }));
    fixture.detectChanges();
    expect(texto(el().querySelector('.miembros__rango'))).toBe('21–40 de 45');
    boton(el(), 'Página 3').click();
    expect(listados()[0].request.params.get('page')).toBe('2');
  });

  it('búsqueda sin resultados: «Sin resultados» y «Quitar filtros» limpia', () => {
    cargar();
    const buscador = el().querySelector<HTMLInputElement>('#miembros-buscar')!;
    buscador.value = 'zzz';
    buscador.dispatchEvent(new Event('input'));
    jasmine.clock().tick(300);
    listados()[0].flush(pagina([]));
    fixture.detectChanges();
    expect(texto(el().querySelector('pn-bloque-vacio'))).toContain('Sin resultados');
    boton(el(), 'Quitar filtros').click();
    const [r] = listados();
    expect(r.request.params.has('q')).toBeFalse();
    r.flush(pagina([miembro()]));
    fixture.detectChanges();
    expect(filas().length).toBe(1);
    expect(buscador.value).toBe('');
  });

  // ------------------------------------------------------------------ ficha: datos y roles

  it('la ficha abre con el nombre por título y las secciones Datos y Roles; Sedes y Grupos solo con sus roles activos', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('ADMINISTRADOR')] }));
    expect(texto(panel()!.querySelector('.pn-panel__titulo'))).toBe('Lucía Martín Gil');
    expect(titulos()).toEqual(['Datos', 'Roles']);
    expect(texto(panel()!.querySelector('.miembros__dato-valor'))).toBe('lucia@ejemplo.test');
    expect(panel()!.textContent).not.toContain('Reenviar invitación');
  });

  it('sesión propietaria: Propietario y Gestor se pueden cambiar; sesión gestora: deshabilitados con la ayuda', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha());
    expect(casilla('PROPIETARIO').disabled).toBeFalse();
    expect(casilla('ADMINISTRADOR').disabled).toBeFalse();
    fixture.destroy();

    cargar(pagina([miembro()]), GESTORA);
    await abrirFicha(ficha());
    expect(casilla('PROPIETARIO').disabled).toBeTrue();
    expect(casilla('ADMINISTRADOR').disabled).toBeTrue();
    expect(texto(lineaRol('PROPIETARIO').querySelector('.miembros__ayuda'))).toBe('Solo el propietario puede cambiar este rol.');
    expect(casilla('PROFESOR').disabled).toBeFalse();
    expect(casilla('RESPONSABLE_SEDE').disabled).toBeFalse();
  });

  it('dar un rol: PUT /roles/{rol} sin cuerpo de cargo y la ficha devuelta refresca la casilla y la lista', async () => {
    cargar();
    await abrirFicha(ficha());
    expect(casilla('ADMINISTRADOR').checked).toBeFalse();
    marcar(casilla('ADMINISTRADOR'), true);
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/roles/ADMINISTRADOR`);
    expect(put.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    put.flush(ficha({ roles: [rol('PROFESOR'), rol('ADMINISTRADOR')] }));
    await esperar();
    expect(casilla('ADMINISTRADOR').checked).toBeTrue();
    expect(lineaRol('ADMINISTRADOR').querySelector('input[type="text"]')).not.toBeNull();
    expect(Array.from(filas()[0].querySelectorAll('.miembros__etiqueta')).map((e) => texto(e))).toEqual(['Profesor', 'Gestor']);
    listados().forEach((r) => r.flush(pagina([miembro({ roles: [rol('PROFESOR'), rol('ADMINISTRADOR')] })])));
  });

  it('error al quitar un rol: el mensaje del servidor junto a esa línea y la casilla sigue marcada', async () => {
    cargar(pagina([miembro()]), GESTORA);
    await abrirFicha(ficha());
    marcar(casilla('PROFESOR'), false);
    await esperar();
    expect(dialogo()).not.toBeNull();
    await confirmar();
    http.expectOne((r) => r.method === 'DELETE').flush({ mensaje: 'La cuenta no tiene ese rol en esta escuela.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(lineaRol('PROFESOR').querySelector('.miembros__error'))).toBe('La cuenta no tiene ese rol en esta escuela.');
    expect(casilla('PROFESOR').checked).toBeTrue();
  });

  it('quitar un rol pide confirmación con su texto y llama a DELETE al confirmar', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('PROFESOR'), rol('ADMINISTRADOR')] }));
    marcar(casilla('ADMINISTRADOR'), false);
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('¿Quitar el rol de gestor a Lucía Martín Gil?');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('Podrás devolvérselo después.');
    // La casilla no cambia hasta que la API responde.
    expect(casilla('ADMINISTRADOR').checked).toBeTrue();
    await confirmar();
    http.expectOne((r) => r.method === 'DELETE' && r.url === `${API}/1/roles/ADMINISTRADOR`).flush(ficha({ roles: [rol('PROFESOR'), rol('ADMINISTRADOR', false)] }));
    await esperar();
    expect(casilla('ADMINISTRADOR').checked).toBeFalse();
    expect(lineaRol('ADMINISTRADOR').querySelector('input[type="text"]')).toBeNull();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('cancelar la confirmación no llama a la API y deja la casilla marcada', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('PROFESOR'), rol('ADMINISTRADOR')] }));
    marcar(casilla('ADMINISTRADOR'), false);
    await esperar();
    (dialogo()!.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
    await esperar();
    expect(http.match((r) => r.method === 'DELETE').length).toBe(0);
    expect(casilla('ADMINISTRADOR').checked).toBeTrue();
  });

  it('el 409 de último propietario se ve junto a la línea de Propietario', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('PROPIETARIO')] }));
    marcar(casilla('PROPIETARIO'), false);
    await esperar();
    await confirmar();
    http.expectOne((r) => r.method === 'DELETE' && r.url === `${API}/1/roles/PROPIETARIO`).flush({ mensaje: 'La escuela se quedaría sin propietario.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(lineaRol('PROPIETARIO').querySelector('.miembros__error'))).toBe('La escuela se quedaría sin propietario.');
    expect(lineaRol('ADMINISTRADOR').querySelector('.miembros__error')).toBeNull();
    expect(casilla('PROPIETARIO').checked).toBeTrue();
  });

  it('el cargo se guarda con su botón: PUT con el cargo; en blanco lo borra', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('PROFESOR', true, 'Entrenador')] }));
    const campo = lineaRol('PROFESOR').querySelector<HTMLInputElement>('input[type="text"]')!;
    expect(campo.value).toBe('Entrenador');
    expect(campo.maxLength).toBe(100);
    expect(lineaRol('PROFESOR').textContent).not.toContain('Guardar cargo');
    campo.value = '  ';
    campo.dispatchEvent(new Event('input'));
    pintar();
    boton(lineaRol('PROFESOR'), 'Guardar cargo').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/roles/PROFESOR`);
    expect(put.request.body).toEqual({ cargo: '' });
    put.flush(ficha({ roles: [rol('PROFESOR', true, null)] }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Cargo guardado.' }]);
    expect(lineaRol('PROFESOR').querySelector<HTMLInputElement>('input[type="text"]')!.value).toBe('');
    expect(lineaRol('PROFESOR').textContent).not.toContain('Guardar cargo');
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  // ------------------------------------------------------------------ ficha: familia (decisión 396)

  const AYUDA_FAMILIA = 'Solo podrá entrar si tiene algún alumno vinculado.';

  it('propietario sin la membresía: Familia es una casilla más tras Profesor, sin marcar, sin formulario de cargo y con la ayuda', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha({ roles: [rol('PROFESOR')] }));
    const orden = Array.from(panel()!.querySelectorAll('[data-rol]')).map((n) => n.getAttribute('data-rol'));
    expect(orden).toEqual(['PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE', 'PROFESOR', 'FAMILIA']);
    expect(casilla('FAMILIA').checked).toBeFalse();
    expect(casilla('FAMILIA').disabled).toBeFalse();
    expect(texto(lineaRol('FAMILIA').querySelector('.miembros__ayuda'))).toBe(AYUDA_FAMILIA);
    expect(lineaRol('FAMILIA').querySelector('input[type="text"]')).toBeNull();
    expect(lineaRol('FAMILIA').textContent).not.toContain('Cargo');
    expect(panel()!.textContent).not.toContain('Se vincula desde la ficha del alumno.');
  });

  it('propietario con la membresía activa: casilla marcada y todavía sin formulario de cargo', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha({ roles: [rol('PROFESOR'), rol('FAMILIA')] }));
    expect(casilla('FAMILIA').checked).toBeTrue();
    expect(lineaRol('FAMILIA').querySelector('input[type="text"]')).toBeNull();
    expect(lineaRol('FAMILIA').textContent).not.toContain('Guardar cargo');
  });

  it('propietario: la ayuda de Familia sale con Profesor o sin roles, y aria-describedby apunta a ella', async () => {
    for (const roles of [[rol('PROFESOR')], []]) {
      cargar(pagina([miembro()]), PROPIETARIA);
      await abrirFicha(ficha({ roles }));
      expect(texto(lineaRol('FAMILIA').querySelector('.miembros__ayuda'))).withContext(`${roles.length} roles`).toBe(AYUDA_FAMILIA);
      expect(casilla('FAMILIA').getAttribute('aria-describedby')).toBe('miembro-ayuda-FAMILIA');
      fixture.destroy();
    }
  });

  it('propietario: con Propietario, Gestor o Responsable de sede activos no sale la ayuda ni aria-describedby', async () => {
    for (const activo of ['PROPIETARIO', 'ADMINISTRADOR', 'RESPONSABLE_SEDE'] as const) {
      cargar(pagina([miembro()]), PROPIETARIA);
      await abrirFicha(ficha({ roles: [rol(activo), rol('PROFESOR')] }));
      expect(lineaRol('FAMILIA').querySelector('.miembros__ayuda')).withContext(activo).toBeNull();
      expect(casilla('FAMILIA').hasAttribute('aria-describedby')).withContext(activo).toBeFalse();
      fixture.destroy();
    }
  });

  it('propietario: el rol inactivo no cuenta, y un error de Familia solo describe lo que existe', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha({ roles: [rol('ADMINISTRADOR', false), rol('PROFESOR')] }));
    expect(texto(lineaRol('FAMILIA').querySelector('.miembros__ayuda'))).toBe(AYUDA_FAMILIA);
    fixture.destroy();

    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha({ roles: [rol('ADMINISTRADOR')] }));
    marcar(casilla('FAMILIA'), true);
    http
      .expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/roles/FAMILIA`)
      .flush({ mensaje: 'No se pudo.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    const descrita = casilla('FAMILIA').getAttribute('aria-describedby');
    expect(descrita).toBe('miembro-error-FAMILIA');
    expect(panel()!.querySelector(`#${descrita}`)).not.toBeNull();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('gestor con la membresía: fila de texto con la ayuda de solo propietario; sin ella, ni fila', async () => {
    cargar(pagina([miembro()]), GESTORA);
    await abrirFicha(ficha({ roles: [rol('PROFESOR'), rol('FAMILIA')] }));
    const familia = lineaRol('FAMILIA');
    expect(texto(familia.querySelector('.miembros__rol-texto'))).toBe('Familia');
    expect(familia.querySelector('input')).toBeNull();
    expect(texto(familia.querySelector('.miembros__ayuda'))).toBe('Solo el propietario puede cambiar este rol.');
    expect(panel()!.textContent).not.toContain('Se vincula desde la ficha del alumno.');
    fixture.destroy();

    cargar(pagina([miembro()]), GESTORA);
    await abrirFicha(ficha({ roles: [rol('PROFESOR')] }));
    expect(panel()!.querySelector('[data-rol="FAMILIA"]')).toBeNull();
  });

  it('gestor: Familia ya quitada no se enseña', async () => {
    cargar(pagina([miembro()]), GESTORA);
    await abrirFicha(ficha({ roles: [rol('PROFESOR'), rol('FAMILIA', false)] }));
    expect(panel()!.querySelector('[data-rol="FAMILIA"]')).toBeNull();
  });

  it('dar Familia: PUT /roles/FAMILIA directo, sin cuerpo ni diálogo, y refresca la casilla', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha());
    marcar(casilla('FAMILIA'), true);
    expect(dialogo()).toBeNull();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/roles/FAMILIA`);
    expect(put.request.body).toBeNull();
    expect(put.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    put.flush(ficha({ roles: [rol('PROFESOR'), rol('FAMILIA')] }));
    await esperar();
    expect(casilla('FAMILIA').checked).toBeTrue();
    expect(lineaRol('FAMILIA').querySelector('input[type="text"]')).toBeNull();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('quitar Familia: diálogo con el texto exacto, «Quitar rol» llama a DELETE y «Volver» no', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha({ roles: [rol('PROFESOR'), rol('FAMILIA')] }));
    marcar(casilla('FAMILIA'), false);
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('¿Quitar el rol de familia a Lucía Martín Gil?');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Sus alumnos siguen vinculados. Si no tiene otro rol, no podrá entrar. Podrás devolvérselo después.'
    );
    expect(texto(dialogo()!.querySelector('.pn-dialogo__accion'))).toBe('Quitar rol');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__cancelar'))).toBe('Volver');
    (dialogo()!.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
    await esperar();
    expect(http.match((r) => r.method === 'DELETE').length).toBe(0);
    expect(casilla('FAMILIA').checked).toBeTrue();

    marcar(casilla('FAMILIA'), false);
    await esperar();
    await confirmar();
    http.expectOne((r) => r.method === 'DELETE' && r.url === `${API}/1/roles/FAMILIA`).flush(ficha({ roles: [rol('PROFESOR'), rol('FAMILIA', false)] }));
    await esperar();
    expect(casilla('FAMILIA').checked).toBeFalse();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  // ------------------------------------------------------------------ ficha: restablecer contraseña (9-8)

  const nueva = (): HTMLInputElement => panel()!.querySelector<HTMLInputElement>('#miembro-contrasena-nueva')!;
  const repite = (): HTMLInputElement => panel()!.querySelector<HTMLInputElement>('#miembro-contrasena-confirmar')!;
  const bloqueContrasena = (): HTMLElement | null => panel()!.querySelector<HTMLElement>('#miembro-form-contrasena');
  const restablecer = (): HTMLButtonElement | null => panel()!.querySelector<HTMLButtonElement>('#miembro-restablecer');
  const PUT_CONTRASENA = `${API}/1/contrasena`;

  function escribir(c: HTMLInputElement, valor: string): void {
    c.value = valor;
    c.dispatchEvent(new Event('input'));
    pintar();
  }

  async function conBloque(caps: Capacidad[] = PROPIETARIA): Promise<void> {
    cargar(pagina([miembro()]), caps);
    await abrirFicha(ficha({ contrasena: 'RESTABLECIBLE' }));
    restablecer()!.click();
    pintar();
  }

  const FRASE_GOOGLE = 'Esta cuenta usa Google. Su contraseña no se cambia aquí.';
  const FRASE_OTRA = 'Esta cuenta también pertenece a otra escuela. Su contraseña no se cambia desde aquí.';

  it('visibilidad según quién abre la ficha y el estado de la contraseña (los cuatro valores)', async () => {
    const casos: [FichaCompleta['contrasena'], boolean, boolean, string][] = [
      ['RESTABLECIBLE', true, false, ''],
      ['GOOGLE', false, false, FRASE_GOOGLE],
      ['OTRA_ESCUELA', false, false, FRASE_OTRA],
      ['INVITACION_PENDIENTE', false, false, ''],
    ];
    for (const [estado, boton_, , frase] of casos) {
      cargar(pagina([miembro()]), PROPIETARIA);
      await abrirFicha(ficha({ contrasena: estado }));
      expect(restablecer() !== null).withContext(`propietario ${estado}`).toBe(boton_);
      expect(texto(panel()!.querySelector('.miembros__aviso'))).withContext(`propietario ${estado}`).toBe(frase);
      fixture.destroy();

      // El gestor no ve nada de la parte de contraseña, aunque la API la trajera.
      cargar(pagina([miembro()]), GESTORA);
      await abrirFicha(ficha({ contrasena: estado }));
      expect(restablecer()).withContext(`gestor ${estado}`).toBeNull();
      expect(panel()!.querySelector('.miembros__aviso')).withContext(`gestor ${estado}`).toBeNull();
      expect(bloqueContrasena()).toBeNull();
      fixture.destroy();
    }
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha());
    expect(restablecer()).toBeNull();
  });

  it('el botón es secundario, va tras el email y desplegarlo enseña el bloque con sus campos, foco en el primero', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha({ contrasena: 'RESTABLECIBLE' }));
    document.body.appendChild(el());
    expect(restablecer()!.classList).toContain('miembros__boton-secundario');
    expect(texto(restablecer())).toBe('Restablecer contraseña');
    restablecer()!.click();
    pintar();
    const bloque = bloqueContrasena()!;
    expect(texto(bloque.querySelector('h4'))).toBe('Restablecer contraseña');
    expect(texto(bloque.querySelector('label[for="miembro-contrasena-nueva"]'))).toBe('Nueva contraseña (obligatorio)');
    expect(texto(bloque.querySelector('label[for="miembro-contrasena-confirmar"]'))).toBe('Repite la contraseña (obligatorio)');
    expect(nueva().getAttribute('autocomplete')).toBe('new-password');
    expect(repite().getAttribute('autocomplete')).toBe('new-password');
    expect(nueva().type).toBe('password');
    expect(texto(bloque.querySelector('.miembros__casilla'))).toBe('Mostrar las contraseñas');
    expect(Array.from(bloque.querySelectorAll('.miembros__regla')).map((r) => texto(r).replace(/: (cumple|falta)$/, ''))).toEqual([
      'Mínimo 8 caracteres', 'Al menos 1 mayúscula', 'Al menos 1 minúscula', 'Al menos 1 número', 'Coinciden',
    ]);
    expect(Array.from(bloque.querySelectorAll('.miembros__plegado-acciones button')).map((b) => texto(b))).toEqual(['Cancelar', 'Guardar contraseña']);
    // Se despliega en la propia ficha: no se abre otro panel lateral.
    expect(document.querySelectorAll('dialog.pn-panel').length).toBe(1);
    expect(document.activeElement).toBe(nueva());
    // «Mostrar las contraseñas» cambia los dos campos.
    const mostrar = bloque.querySelector<HTMLInputElement>('.miembros__casilla input')!;
    mostrar.click();
    pintar();
    expect(nueva().type).toBe('text');
    expect(repite().type).toBe('text');
    el().remove();
  });

  it('cancelar pliega, vacía los campos y devuelve el foco al botón; al volver a desplegar está vacío', async () => {
    cargar(pagina([miembro()]), PROPIETARIA);
    await abrirFicha(ficha({ contrasena: 'RESTABLECIBLE' }));
    document.body.appendChild(el());
    restablecer()!.click();
    pintar();
    escribir(nueva(), 'Abcdefg1');
    escribir(repite(), 'Abcdefg1');
    boton(bloqueContrasena()!, 'Cancelar').click();
    pintar();
    expect(bloqueContrasena()).toBeNull();
    expect(document.activeElement).toBe(restablecer());
    expect(http.match((r) => r.method === 'PUT').length).toBe(0);
    restablecer()!.click();
    pintar();
    expect(nueva().value).toBe('');
    expect(repite().value).toBe('');
    el().remove();
  });

  it('los requisitos se marcan en vivo, y «Coinciden» solo con las dos iguales', async () => {
    await conBloque();
    const estado = (): string[] => Array.from(panel()!.querySelectorAll('.miembros__regla')).map((r) => r.getAttribute('data-cumple') ?? '');
    expect(estado()).toEqual(['false', 'false', 'false', 'false', 'false']);
    escribir(nueva(), 'abc');
    expect(estado()).toEqual(['false', 'false', 'true', 'false', 'false']);
    escribir(nueva(), 'Abcdefg1');
    expect(estado()).toEqual(['true', 'true', 'true', 'true', 'false']);
    escribir(repite(), 'Abcdefg1');
    expect(estado()).toEqual(['true', 'true', 'true', 'true', 'true']);
    escribir(repite(), 'Abcdefg2');
    expect(estado()[4]).toBe('false');
  });

  it('guardar: PUT /contrasena con ERROR_HANDLED_LOCALLY, aviso de éxito, bloque plegado y vacío, y la lista se pone al día', async () => {
    await conBloque();
    document.body.appendChild(el());
    escribir(nueva(), 'Abcdefg1');
    escribir(repite(), 'Abcdefg1');
    boton(bloqueContrasena()!, 'Guardar contraseña').click();
    pintar();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === PUT_CONTRASENA);
    expect(put.request.body).toEqual({ nuevaContrasena: 'Abcdefg1' });
    expect(put.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    put.flush(ficha({ contrasena: 'RESTABLECIBLE' }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Contraseña actualizada correctamente.' }]);
    expect(bloqueContrasena()).toBeNull();
    expect(document.activeElement).toBe(restablecer());
    restablecer()!.click();
    pintar();
    expect(nueva().value).toBe('');
    listados().forEach((r) => r.flush(pagina([miembro()])));
    el().remove();
  });

  it('una contraseña que no cumple las reglas o no coincide no se envía y el mensaje sale en el bloque', async () => {
    await conBloque();
    for (const mala of ['abcdefgh1', 'ABCDEFGH1', 'Abcdefgh', 'Abc1']) {
      escribir(nueva(), mala);
      escribir(repite(), mala);
      boton(bloqueContrasena()!, 'Guardar contraseña').click();
      pintar();
      const error = bloqueContrasena()!.querySelector('[role="alert"]');
      expect(texto(error)).toBe('La contraseña debe tener mayúsculas, minúsculas y números (mínimo 8).');
      expect(http.match((r) => r.method === 'PUT').length).toBe(0);
    }
    escribir(nueva(), 'Abcdefg1');
    escribir(repite(), 'Abcdefg2');
    boton(bloqueContrasena()!, 'Guardar contraseña').click();
    pintar();
    expect(texto(bloqueContrasena()!.querySelector('[role="alert"]'))).toBe('Las contraseñas no coinciden');
    expect(nueva().getAttribute('aria-invalid')).toBe('true');
    expect(http.match((r) => r.method === 'PUT').length).toBe(0);
  });

  it('un error de la API se ve dentro del bloque con role=alert y no pierde lo escrito', async () => {
    await conBloque();
    escribir(nueva(), 'Abcdefg1');
    escribir(repite(), 'Abcdefg1');
    boton(bloqueContrasena()!, 'Guardar contraseña').click();
    pintar();
    http.expectOne((r) => r.method === 'PUT' && r.url === PUT_CONTRASENA).flush({ mensaje: 'La contraseña es demasiado común.' }, { status: 400, statusText: 'Bad Request' });
    await esperar();
    const alerta = bloqueContrasena()!.querySelector('[role="alert"]')!;
    expect(texto(alerta)).toBe('La contraseña es demasiado común.');
    expect(nueva().value).toBe('Abcdefg1');
    expect(repite().value).toBe('Abcdefg1');
    expect(nueva().disabled).toBeFalse();
    expect(avisos()).toEqual([]);
    // Al escribir de nuevo el error desaparece.
    escribir(nueva(), 'Abcdefg12');
    escribir(repite(), 'Abcdefg12');
    expect(bloqueContrasena()!.querySelector('[role="alert"]')).toBeNull();
    // Un error sin mensaje de la API: el genérico.
    boton(bloqueContrasena()!, 'Guardar contraseña').click();
    pintar();
    http.expectOne((r) => r.method === 'PUT' && r.url === PUT_CONTRASENA).flush(null, { status: 500, statusText: 'Error' });
    await esperar();
    expect(texto(bloqueContrasena()!.querySelector('[role="alert"]'))).toBe('No se pudo actualizar la contraseña.');
  });

  it('mientras se envía el panel queda bloqueado y un segundo envío no repite el PUT', async () => {
    await conBloque();
    escribir(nueva(), 'Abcdefg1');
    escribir(repite(), 'Abcdefg1');
    const guardar = boton(bloqueContrasena()!, 'Guardar contraseña');
    guardar.click();
    pintar();
    expect(guardar.disabled || guardar.getAttribute('aria-busy') === 'true' || texto(guardar) === 'Guardando…').toBeTrue();
    expect(nueva().disabled).toBeTrue();
    expect(boton(bloqueContrasena()!, 'Cancelar').disabled).toBeTrue();
    bloqueContrasena()!.dispatchEvent(new Event('submit'));
    guardar.click();
    pintar();
    expect(http.match((r) => r.method === 'PUT' && r.url === PUT_CONTRASENA).length).toBe(1);
    // El panel no se cierra con la escritura en vuelo.
    boton(panel()!, 'Cerrar').click();
    pintar();
    expect(panel()).not.toBeNull();
  });

  it('cerrar la ficha vacía los campos: al reabrirla el bloque está plegado y vacío', async () => {
    await conBloque();
    escribir(nueva(), 'Abcdefg1');
    escribir(repite(), 'Abcdefg1');
    const ficha_ = fixture.debugElement.query(By.directive(MiembroFichaComponent)).componentInstance as MiembroFichaComponent;
    expect(ficha_.contrasena?.nueva).toBe('Abcdefg1');
    boton(panel()!, 'Cerrar').click();
    pintar();
    expect(ficha_.contrasena).toBeNull();
    await abrirFicha(ficha({ contrasena: 'RESTABLECIBLE' }));
    expect(bloqueContrasena()).toBeNull();
    restablecer()!.click();
    pintar();
    expect(nueva().value).toBe('');
  });

  it('destruir el componente vacía los campos y no guarda ni escribe la contraseña en la consola', async () => {
    const consola = [spyOn(console, 'log'), spyOn(console, 'warn'), spyOn(console, 'error'), spyOn(console, 'info')];
    await conBloque();
    escribir(nueva(), 'Abcdefg1');
    escribir(repite(), 'Abcdefg1');
    const ficha_ = fixture.debugElement.query(By.directive(MiembroFichaComponent)).componentInstance as MiembroFichaComponent;
    const form = ficha_.contrasena!;
    fixture.componentInstance.abierto = null;
    pintar();
    expect(form.nueva).toBe('');
    expect(form.confirmar).toBe('');
    expect(ficha_.contrasena).toBeNull();
    consola.forEach((c) => expect(c).not.toHaveBeenCalled());
  });

  // ------------------------------------------------------------------ ficha: invitación

  it('invitación pendiente: etiqueta y «Reenviar invitación», que usa la ruta de reenvío y avisa', async () => {
    cargar(pagina([miembro({ invitacionPendiente: true })]));
    await abrirFicha(ficha({ invitacionPendiente: true }));
    expect(texto(panel()!.querySelector('.miembros__invitacion .miembros__etiqueta'))).toBe('Invitación pendiente');
    boton(panel()!, 'Reenviar invitación').click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === `${API}/1/invitacion`);
    expect(post.request.body).toBeNull();
    post.flush(ficha({ invitacionPendiente: true }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Invitación reenviada.' }]);
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('error al reenviar: el mensaje en la sección Datos', async () => {
    cargar(pagina([miembro({ invitacionPendiente: true })]));
    await abrirFicha(ficha({ invitacionPendiente: true }));
    boton(panel()!, 'Reenviar invitación').click();
    http.expectOne((r) => r.url === `${API}/1/invitacion`).flush({ mensaje: 'La invitación ya se aceptó.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(panel()!.querySelector('#miembro-datos-error'))).toBe('La invitación ya se aceptó.');
  });

  // ------------------------------------------------------------------ ficha: cancelar invitación (decisión 389)

  const TITULO_CANCELAR = '¿Cancelar la invitación de Lucía Martín Gil?';
  const TEXTO_CANCELAR = 'El enlace dejará de valer y le avisaremos por correo. Si no tiene nada más en ninguna escuela, su cuenta se borrará.';
  const YA_ACEPTADA = 'La invitación ya se aceptó.';
  const SOLO_PROPIETARIO = 'Solo el propietario da o quita los roles de propietario y gestor.';
  const NO_MIEMBRO = 'La cuenta no es miembro de esta escuela.';
  const pendiente = (cambios: Partial<FichaCompleta> = {}): FichaCompleta => ficha({ invitacionPendiente: true, ...cambios });
  const tieneCancelar = (): boolean => Array.from(panel()!.querySelectorAll('button')).some((b) => texto(b) === 'Cancelar invitación');
  const fichasPedidas = (id = 1): TestRequest[] => http.match((r) => r.method === 'GET' && r.url === `${API}/${id}`);
  const borrados = (id = 1): TestRequest[] => http.match((r) => r.method === 'DELETE' && r.url === `${API}/${id}/invitacion`);
  const instanciaFicha = (): MiembroFichaComponent => fixture.debugElement.query(By.directive(MiembroFichaComponent)).componentInstance;

  async function abrirPendiente(f: FichaCompleta = pendiente(), caps: Capacidad[] = PROPIETARIA): Promise<void> {
    cargar(pagina([miembro({ invitacionPendiente: true, roles: f.roles })]), caps);
    await abrirFicha(f);
  }

  async function pedirCancelar(): Promise<void> {
    boton(panel()!, 'Cancelar invitación').click();
    await esperar();
  }

  it('«Cancelar invitación» va en su fila con «Reenviar invitación», bajo la pastilla, como botón de texto en peligro', async () => {
    await abrirPendiente();
    const bloque = panel()!.querySelector('.miembros__invitacion')!;
    expect(bloque.querySelector(':scope > .miembros__etiqueta')).not.toBeNull();
    const acciones = Array.from(bloque.querySelectorAll('.miembros__invitacion-acciones button'));
    expect(acciones.map((b) => texto(b))).toEqual(['Reenviar invitación', 'Cancelar invitación']);
    expect(acciones[1].classList.contains('miembros__boton-texto')).toBeTrue();
    expect(acciones[1].classList.contains('miembros__boton-texto--peligro')).toBeTrue();
  });

  it('la sesión propietaria lo ve también con roles reservados', async () => {
    await abrirPendiente(pendiente({ roles: [rol('PROPIETARIO'), rol('ADMINISTRADOR'), rol('PROFESOR')] }));
    expect(tieneCancelar()).toBeTrue();
  });

  it('la sesión gestora lo ve si ningún rol activo es reservado, aunque tenga uno reservado desactivado', async () => {
    await abrirPendiente(pendiente({ roles: [rol('PROFESOR'), rol('ADMINISTRADOR', false)] }), GESTORA);
    expect(tieneCancelar()).toBeTrue();
  });

  it('la sesión gestora lo ve con Familia ya quitada, y la propietaria con Familia activa', async () => {
    await abrirPendiente(pendiente({ roles: [rol('PROFESOR'), rol('FAMILIA', false)] }), GESTORA);
    expect(tieneCancelar()).toBeTrue();
    fixture.destroy();

    await abrirPendiente(pendiente({ roles: [rol('PROFESOR'), rol('FAMILIA')] }), PROPIETARIA);
    expect(tieneCancelar()).toBeTrue();
  });

  for (const roles of [[rol('PROPIETARIO')], [rol('ADMINISTRADOR')], [rol('ADMINISTRADOR'), rol('PROFESOR')], [rol('FAMILIA')], [rol('PROFESOR'), rol('FAMILIA')]]) {
    it(`la sesión gestora no lo ve con ${roles.map((r) => r.rol).join(' y ')} activos; «Reenviar» sigue`, async () => {
      await abrirPendiente(pendiente({ roles }), GESTORA);
      expect(tieneCancelar()).toBeFalse();
      expect(boton(panel()!, 'Reenviar invitación')).toBeTruthy();
    });
  }

  it('no aparece sin ningún rol activo en esta escuela', async () => {
    await abrirPendiente(pendiente({ roles: [rol('PROFESOR', false)] }));
    expect(tieneCancelar()).toBeFalse();
  });

  it('sin rol activo en esta escuela (cuenta conservada): ni pastilla ni «Reenviar invitación», en la fila ni en la ficha', async () => {
    await abrirPendiente(pendiente({ roles: [rol('PROFESOR', false)] }));
    expect(filas()[0].querySelector('.miembros__etiqueta--atencion')).toBeNull();
    expect(panel()!.querySelector('.miembros__invitacion')).toBeNull();
    expect(Array.from(panel()!.querySelectorAll('button')).some((b) => texto(b) === 'Reenviar invitación')).toBeFalse();
  });

  it('solo con familia activa sigue pendiente aquí: pastilla y «Reenviar invitación»', async () => {
    await abrirPendiente(pendiente({ roles: [rol('PROFESOR', false), rol('FAMILIA')] }));
    expect(texto(filas()[0].querySelector('.miembros__etiqueta--atencion'))).toBe('Invitación pendiente');
    expect(boton(panel()!, 'Reenviar invitación')).toBeTruthy();
  });

  it('al quitar el último rol desaparecen pastilla y botones; al devolverlo vuelven', async () => {
    await abrirPendiente();
    marcar(casilla('PROFESOR'), false);
    await esperar();
    await confirmar();
    http.expectOne((r) => r.method === 'DELETE' && r.url === `${API}/1/roles/PROFESOR`).flush(pendiente({ roles: [rol('PROFESOR', false)] }));
    await esperar();
    listados().forEach((r) => r.flush(pagina([miembro({ invitacionPendiente: true, roles: [rol('PROFESOR', false)] })])));
    pintar();
    expect(panel()!.querySelector('.miembros__invitacion')).toBeNull();
    expect(filas()[0].querySelector('.miembros__etiqueta--atencion')).toBeNull();

    marcar(casilla('PROFESOR'), true);
    http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/roles/PROFESOR`).flush(pendiente());
    await esperar();
    http.match((r) => r.url === GRUPOS).forEach((r) => r.flush([]));
    listados().forEach((r) => r.flush(pagina([miembro({ invitacionPendiente: true })])));
    pintar();
    expect(boton(panel()!, 'Reenviar invitación')).toBeTruthy();
    expect(filas()[0].querySelector('.miembros__etiqueta--atencion')).not.toBeNull();
  });

  it('no aparece sin invitación pendiente', async () => {
    cargar();
    await abrirFicha(ficha());
    expect(tieneCancelar()).toBeFalse();
  });

  it('pide confirmación con los textos exactos, en peligro y con el foco en «Volver»; «Volver» no llama a la API', async () => {
    await abrirPendiente();
    await pedirCancelar();
    const d = dialogo()!;
    expect(d.classList.contains('pn-dialogo--peligro')).toBeTrue();
    expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe(TITULO_CANCELAR);
    expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe(TEXTO_CANCELAR);
    expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Cancelar invitación');
    const volver = d.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement;
    expect(texto(volver)).toBe('Volver');
    expect(document.activeElement).toBe(volver);
    volver.click();
    await esperar();
    expect(dialogo()).toBeNull();
    expect(borrados().length).toBe(0);
    expect(panel()).not.toBeNull();
    expect(avisos()).toEqual([]);
  });

  for (const cuentaBorrada of [true, false]) {
    it(`éxito con cuentaBorrada=${cuentaBorrada}: DELETE sin cuerpo, cierra la ficha, recarga la lista, avisa y no vuelve a pedir la ficha`, async () => {
      await abrirPendiente();
      await pedirCancelar();
      await confirmar();
      const [peticion] = borrados();
      expect(peticion.request.body).toBeNull();
      expect(peticion.request.withCredentials).toBeTrue();
      expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      peticion.flush({ cuentaBorrada });
      await esperar();

      expect(panel()).toBeNull();
      expect(dialogo()).toBeNull();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Invitación cancelada.' }]);
      const recarga = listados();
      expect(recarga.length).toBe(1);
      recarga[0].flush(pagina([]));
      await esperar();
      expect(fichasPedidas().length).toBe(0);
    });
  }

  it('mientras se envía: «Cancelando…», panel bloqueado y sin doble envío', async () => {
    await abrirPendiente();
    await pedirCancelar();
    const accion = dialogo()!.querySelector('.pn-dialogo__accion') as HTMLButtonElement;
    accion.click();
    await esperar();
    const [peticion] = borrados();
    expect(texto(accion)).toBe('Cancelando…');
    expect(instanciaFicha().ocupado).toBeTrue();
    const enLaFicha = panel()!.querySelector('.miembros__boton-texto--peligro') as HTMLButtonElement;
    expect(texto(enLaFicha)).toBe('Cancelando…');
    expect(enLaFicha.getAttribute('aria-busy')).toBe('true');
    expect((panel()!.querySelector('.miembros__pie button') as HTMLButtonElement).disabled).toBeTrue();

    accion.click();
    enLaFicha.click();
    void instanciaFicha().cancelarInvitacion();
    await esperar();
    expect(borrados().length).toBe(0);
    expect(document.querySelectorAll('dialog.pn-dialogo').length).toBe(1);
    peticion.flush({ cuentaBorrada: true });
    await esperar();
    listados().forEach((r) => r.flush(pagina([])));
  });

  it('dos pulsaciones seguidas abren un solo diálogo', async () => {
    await abrirPendiente();
    const b = boton(panel()!, 'Cancelar invitación');
    b.click();
    b.click();
    await esperar();
    expect(document.querySelectorAll('dialog.pn-dialogo').length).toBe(1);
  });

  it('con un reenvío en vuelo no abre el diálogo', async () => {
    await abrirPendiente();
    boton(panel()!, 'Reenviar invitación').click();
    pintar();
    const reenvio = http.expectOne((r) => r.method === 'POST' && r.url === `${API}/1/invitacion`);
    void instanciaFicha().cancelarInvitacion();
    await esperar();
    expect(dialogo()).toBeNull();
    reenvio.flush(pendiente());
    await esperar();
    listados().forEach((r) => r.flush(pagina([])));
  });

  it('409: el mensaje en Datos; recarga la ficha sin esqueleto, refleja el estado real y la fila se pone al día', async () => {
    await abrirPendiente();
    await pedirCancelar();
    await confirmar();
    borrados()[0].flush({ mensaje: YA_ACEPTADA }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(panel()).not.toBeNull();
    expect(texto(panel()!.querySelector('#miembro-datos-error'))).toBe(YA_ACEPTADA);
    expect(avisos()).toEqual([]);

    const [recarga] = fichasPedidas();
    expect(panel()!.querySelector('pn-esqueleto')).toBeNull();
    recarga.flush(ficha({ invitacionPendiente: false }));
    await esperar();
    expect(panel()!.querySelector('.miembros__invitacion')).toBeNull();
    expect(texto(panel()!.querySelector('#miembro-datos-error'))).toBe(YA_ACEPTADA);
    expect(fixture.componentInstance.miembros[0].invitacionPendiente).toBeFalse();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('403: el mensaje en Datos, sin recargar la ficha', async () => {
    await abrirPendiente();
    await pedirCancelar();
    await confirmar();
    borrados()[0].flush({ mensaje: SOLO_PROPIETARIO }, { status: 403, statusText: 'Forbidden' });
    await esperar();
    expect(texto(panel()!.querySelector('#miembro-datos-error'))).toBe(SOLO_PROPIETARIO);
    expect(fichasPedidas().length).toBe(0);
    expect(instanciaFicha().ocupado).toBeFalse();
    expect(tieneCancelar()).toBeTrue();
  });

  it('404: la cuenta ya no existe: cierra la ficha, recarga la lista y avisa con el mensaje de la API, sin pedir la ficha', async () => {
    await abrirPendiente();
    await pedirCancelar();
    await confirmar();
    borrados()[0].flush({ mensaje: NO_MIEMBRO }, { status: 404, statusText: 'Not Found' });
    await esperar();

    expect(panel()).toBeNull();
    expect(dialogo()).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'error', texto: NO_MIEMBRO }]);
    const recarga = listados();
    expect(recarga.length).toBe(1);
    recarga[0].flush(pagina([]));
    await esperar();
    expect(fichasPedidas().length).toBe(0);
  });

  it('404 con la ficha ya cerrada: también avisa y la lista se refresca', async () => {
    await abrirPendiente();
    await pedirCancelar();
    await confirmar();
    const [peticion] = borrados();
    fixture.componentInstance.abierto = null;
    pintar();
    peticion.flush({ mensaje: NO_MIEMBRO }, { status: 404, statusText: 'Not Found' });
    await esperar();

    expect(avisos()).toEqual([{ tipo: 'error', texto: NO_MIEMBRO }]);
    const recarga = listados();
    expect(recarga.length).toBe(1);
    recarga[0].flush(pagina([]));
    expect(fichasPedidas().length).toBe(0);
  });

  it('ficha cerrada con la cancelación en vuelo: un 409 no avisa ni recarga nada', async () => {
    await abrirPendiente();
    await pedirCancelar();
    await confirmar();
    const [peticion] = borrados();
    fixture.componentInstance.abierto = null;
    pintar();
    peticion.flush({ mensaje: YA_ACEPTADA }, { status: 409, statusText: 'Conflict' });
    await esperar();

    expect(avisos()).toEqual([]);
    expect(listados().length).toBe(0);
    expect(fichasPedidas().length).toBe(0);
  });

  it('ficha cerrada con la cancelación en vuelo: el éxito se avisa y la lista se refresca, sin pedir la ficha', async () => {
    await abrirPendiente();
    await pedirCancelar();
    await confirmar();
    const [peticion] = borrados();
    fixture.componentInstance.abierto = null;
    pintar();
    expect(el().querySelector('pn-miembro-ficha')).toBeNull();
    peticion.flush({ cuentaBorrada: true });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Invitación cancelada.' }]);
    const recarga = listados();
    expect(recarga.length).toBe(1);
    recarga[0].flush(pagina([]));
    expect(fichasPedidas().length).toBe(0);
  });

  // ------------------------------------------------------------------ ficha: sedes

  it('Sedes solo con Responsable de sede activo; guarda con PUT /sedes y exige al menos una', async () => {
    cargar();
    await abrirFicha(ficha());
    expect(subtitulos()).not.toContain('Sedes');
    fixture.destroy();

    cargar(pagina([miembro({ roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 1, nombre: 'Casariche' }] })]));
    await abrirFicha(ficha({ roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 1, nombre: 'Casariche' }] }));
    expect(titulos()).toEqual(['Datos', 'Roles']);
    expect(subtitulos()).toEqual(['Sedes']);
    const bloque = lineaRol('RESPONSABLE_SEDE').querySelector('#miembro-sedes');
    expect(bloque).not.toBeNull();
    expect(bloque!.closest('[role="group"]')!.getAttribute('aria-labelledby')).toBe('miembro-sedes');
    expect(lineaRol('PROFESOR')?.querySelector('#miembro-sedes') ?? null).toBeNull();
    const cajas = Array.from(panel()!.querySelectorAll<HTMLInputElement>('.miembros__sedes input'));
    // Solo las activas: Herrera, inactiva y sin guardar, no sale.
    expect(cajas.map((c) => texto(c.parentElement))).toEqual(['Casariche', 'Mollina']);
    expect(cajas.map((c) => c.checked)).toEqual([true, false]);
    expect(boton(panel()!, 'Guardar sedes').disabled).toBeTrue();

    cajas[0].checked = false;
    cajas[0].dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Guardar sedes').click();
    pintar();
    expect(http.match((r) => r.method === 'PUT').length).toBe(0);
    expect(texto(panel()!.querySelector('#miembro-sedes-error'))).toBe('Indica al menos una sede.');

    cajas[1].checked = true;
    cajas[1].dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Guardar sedes').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/sedes`);
    expect(put.request.body).toEqual({ sedeIds: [2] });
    put.flush(ficha({ roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 2, nombre: 'Mollina' }] }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Sedes guardadas.' }]);
    expect(Array.from(panel()!.querySelectorAll<HTMLInputElement>('.miembros__sedes input')).map((c) => c.checked)).toEqual([false, true]);
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('error de sedes: el mensaje en su propia sección', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 1, nombre: 'Casariche' }] }));
    const cajas = Array.from(panel()!.querySelectorAll<HTMLInputElement>('.miembros__sedes input'));
    cajas[1].checked = true;
    cajas[1].dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Guardar sedes').click();
    http.expectOne((r) => r.url === `${API}/1/sedes`).flush({ mensaje: 'La sede no existe en la escuela.' }, { status: 400, statusText: 'Bad Request' });
    await esperar();
    expect(texto(panel()!.querySelector('#miembro-sedes-error'))).toBe('La sede no existe en la escuela.');
    expect(panel()!.querySelector('#miembro-grupos-error')).toBeNull();
  });

  it('marcar Responsable de sede sin sedes guardadas pide las sedes antes y da el rol con ellas', async () => {
    cargar();
    await abrirFicha(ficha());
    marcar(casilla('RESPONSABLE_SEDE'), true);
    expect(http.match((r) => r.method === 'PUT').length).toBe(0);
    expect(subtitulos()).toContain('Sedes');
    expect(lineaRol('RESPONSABLE_SEDE').querySelector('#miembro-sedes')).not.toBeNull();
    expect(lineaRol('RESPONSABLE_SEDE').querySelector('.miembros__sedes')).not.toBeNull();
    const cajas = Array.from(panel()!.querySelectorAll<HTMLInputElement>('.miembros__sedes input'));
    cajas[1].checked = true;
    cajas[1].dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Dar el rol con estas sedes').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/roles/RESPONSABLE_SEDE`);
    expect(put.request.body).toEqual({ sedeIds: [2] });
    put.flush(ficha({ roles: [rol('PROFESOR'), rol('RESPONSABLE_SEDE')], sedes: [{ id: 2, nombre: 'Mollina' }] }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Rol dado.' }]);
    expect(casilla('RESPONSABLE_SEDE').checked).toBeTrue();
    expect(panel()!.textContent).toContain('Guardar sedes');
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  // ------------------------------------------------------------------ ficha: grupos

  it('Grupos solo con Profesor activo: lista con su rol, cambia, quita, añade y guarda con PUT /grupos', async () => {
    cargar(pagina([miembro({ roles: [rol('ADMINISTRADOR')] })]));
    await abrirFicha(ficha({ roles: [rol('ADMINISTRADOR')] }));
    expect(subtitulos()).not.toContain('Grupos');
    fixture.destroy();

    cargar(pagina([miembro({ numeroGrupos: 1 })]));
    await abrirFicha(ficha({ numeroGrupos: 1, grupos: [{ grupoId: 10, nombre: 'Infantil martes', sedeId: 2, rol: 'TITULAR' }] }));
    expect(titulos()).toEqual(['Datos', 'Roles']);
    expect(subtitulos()).toEqual(['Grupos']);
    expect(lineaRol('PROFESOR').querySelector('#miembro-grupos')).not.toBeNull();
    expect(lineaRol('PROFESOR').querySelector('.miembros__grupos')).not.toBeNull();
    const fila = panel()!.querySelector<HTMLElement>('.miembros__grupo[data-grupo="10"]')!;
    expect(texto(fila.querySelector('.miembros__grupo-nombre'))).toBe('Infantil martes · Mollina');
    expect(fila.querySelector<HTMLSelectElement>('select')!.value).toBe('TITULAR');
    expect(boton(panel()!, 'Guardar grupos').disabled).toBeTrue();
    // Ya tiene «Infantil martes»: el selector de añadir solo ofrece «Adultos».
    const nuevo = panel()!.querySelector<HTMLSelectElement>('#miembro-grupo-nuevo')!;
    expect(Array.from(nuevo.options).map((o) => texto(o))).toEqual(['Añadir un grupo…', 'Adultos']);

    const rolFila = fila.querySelector<HTMLSelectElement>('select')!;
    rolFila.value = 'AYUDANTE';
    rolFila.dispatchEvent(new Event('change'));
    pintar();
    nuevo.value = '11';
    nuevo.dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Añadir').click();
    pintar();
    expect(panel()!.querySelectorAll('.miembros__grupo').length).toBe(2);

    boton(panel()!, 'Guardar grupos').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/grupos`);
    expect(put.request.body).toEqual({
      asignaciones: [
        { grupoId: 10, rol: 'AYUDANTE' },
        { grupoId: 11, rol: 'TITULAR' },
      ],
    });
    put.flush(
      ficha({
        numeroGrupos: 2,
        grupos: [
          { grupoId: 10, nombre: 'Infantil martes', sedeId: 2, rol: 'AYUDANTE' },
          { grupoId: 11, nombre: 'Adultos', sedeId: 1, rol: 'TITULAR' },
        ],
      })
    );
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Grupos guardados.' }]);
    expect(boton(panel()!, 'Guardar grupos').disabled).toBeTrue();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('quitar un grupo lo saca del borrador hasta guardar; un error de grupos se ve en su sección', async () => {
    cargar();
    await abrirFicha(ficha({ grupos: [{ grupoId: 10, nombre: 'Infantil martes', sedeId: 1, rol: 'TITULAR' }] }));
    boton(panel()!, 'Quitar Infantil martes').click();
    pintar();
    expect(panel()!.querySelectorAll('.miembros__grupo').length).toBe(0);
    boton(panel()!, 'Guardar grupos').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/grupos`);
    expect(put.request.body).toEqual({ asignaciones: [] });
    put.flush(ficha({ grupos: [] }));
    await esperar();
    expect(panel()!.textContent).toContain('No tiene grupos asignados.');
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('error de grupos: el mensaje en la sección Grupos', async () => {
    cargar();
    await abrirFicha(ficha({ grupos: [{ grupoId: 10, nombre: 'Infantil martes', sedeId: 1, rol: 'TITULAR' }] }));
    boton(panel()!, 'Quitar Infantil martes').click();
    pintar();
    boton(panel()!, 'Guardar grupos').click();
    http.expectOne((r) => r.url === `${API}/1/grupos`).flush({ mensaje: 'La cuenta no tiene el rol de profesor activo en esta escuela.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(panel()!.querySelector('#miembro-grupos-error'))).toBe('La cuenta no tiene el rol de profesor activo en esta escuela.');
  });

  // ------------------------------------------------------------------ alta

  function abrirAlta(caps: Capacidad[] = PROPIETARIA): void {
    cargar(pagina([miembro()]), caps);
    boton(el().querySelector('.miembros__cabecera')!, 'Añadir miembro').click();
    pintar();
  }

  function rellenar(id: string, valor: string): void {
    const c = panel()!.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    c.value = valor;
    c.dispatchEvent(new Event(c.tagName === 'SELECT' ? 'change' : 'input'));
    pintar();
  }

  it('«Añadir miembro»: email y rol obligatorios, y solo los roles que la sesión puede dar', () => {
    abrirAlta(GESTORA);
    expect(texto(panel()!.querySelector('.pn-panel__titulo'))).toBe('Añadir miembro');
    expect(texto(panel()!.querySelector('label[for="miembro-alta-email"]'))).toBe('Email (obligatorio)');
    expect(texto(panel()!.querySelector('label[for="miembro-alta-rol"]'))).toBe('Rol (obligatorio)');
    expect(Array.from(panel()!.querySelectorAll('#miembro-alta-rol option')).map((o) => texto(o))).toEqual(['Elige un rol', 'Responsable de sede', 'Profesor']);
    boton(panel()!, 'Añadir miembro').click();
    pintar();
    expect(texto(panel()!.querySelector('#miembro-alta-error-email'))).toBe('Indica el email.');
    expect(texto(panel()!.querySelector('#miembro-alta-error-rol'))).toBe('Elige un rol.');
    expect(http.match((r) => r.method === 'POST').length).toBe(0);
  });

  it('la sesión propietaria ve los cuatro roles', () => {
    abrirAlta(PROPIETARIA);
    expect(Array.from(panel()!.querySelectorAll('#miembro-alta-rol option')).map((o) => texto(o))).toEqual([
      'Elige un rol',
      'Propietario',
      'Gestor',
      'Responsable de sede',
      'Profesor',
    ]);
  });

  it('alta con cuenta: POST sin nombre; cierra, avisa, recarga la lista y abre la ficha del nuevo', async () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'ana@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    boton(panel()!, 'Añadir miembro').click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === API);
    expect(post.request.body).toEqual({ email: 'ana@ejemplo.test', rol: 'PROFESOR' });
    expect(post.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    post.flush(ficha({ usuarioId: 7, nombre: 'Ana', apellidos: 'Ruiz', email: 'ana@ejemplo.test' }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Miembro añadido.' }]);
    expect(el().querySelector('pn-miembro-alta')).toBeNull();
    // Se abre la ficha del nuevo y se recarga la lista.
    http.expectOne((r) => r.method === 'GET' && r.url === `${API}/7`).flush(ficha({ usuarioId: 7, nombre: 'Ana', apellidos: 'Ruiz', email: 'ana@ejemplo.test' }));
    await esperar();
    expect(texto(panel()!.querySelector('.pn-panel__titulo'))).toBe('Ana Ruiz');
    expect(listados().length).toBe(1);
    http.match((r) => r.url === GRUPOS).forEach((r) => r.flush([]));
  });

  it('responsable de sede: casillas de sedes activas, al menos una, y las manda en el alta', async () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'ana@ejemplo.test');
    rellenar('miembro-alta-rol', 'RESPONSABLE_SEDE');
    const cajas = Array.from(panel()!.querySelectorAll<HTMLInputElement>('#miembro-alta-sedes-grupo input'));
    expect(cajas.map((c) => texto(c.parentElement))).toEqual(['Casariche', 'Mollina']);
    boton(panel()!, 'Añadir miembro').click();
    pintar();
    expect(texto(panel()!.querySelector('#miembro-alta-error-sedes'))).toBe('Indica al menos una sede.');
    cajas[1].checked = true;
    cajas[1].dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Añadir miembro').click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === API);
    expect(post.request.body).toEqual({ email: 'ana@ejemplo.test', rol: 'RESPONSABLE_SEDE', sedeIds: [2] });
    post.flush(ficha({ usuarioId: 7, roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 2, nombre: 'Mollina' }] }));
    await esperar();
    http.match((r) => r.url === `${API}/7`).forEach((r) => r.flush(ficha({ usuarioId: 7, roles: [rol('RESPONSABLE_SEDE')] })));
  });

  it('alta con invitación: el 404 pide nombre y apellidos con el aviso y el botón pasa a «Enviar invitación»', async () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'nueva@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    expect(panel()!.querySelector('#miembro-alta-nombre')).toBeNull();
    boton(panel()!, 'Añadir miembro').click();
    http.expectOne((r) => r.method === 'POST' && r.url === API).flush({ mensaje: 'No hay ninguna cuenta con ese email.' }, { status: 404, statusText: 'Not Found' });
    await esperar();
    expect(texto(panel()!.querySelector('#miembro-alta-sin-cuenta'))).toBe('No hay ninguna cuenta registrada con ese email. Le enviaremos una invitación para crear su contraseña.');
    expect(texto(panel()!.querySelector('label[for="miembro-alta-nombre"]'))).toBe('Nombre (obligatorio)');
    expect(texto(panel()!.querySelector('label[for="miembro-alta-apellidos"]'))).toBe('Apellidos (obligatorio)');
    expect(panel()!.querySelector('#miembro-alta-error')).toBeNull();

    boton(panel()!, 'Enviar invitación').click();
    pintar();
    expect(texto(panel()!.querySelector('#miembro-alta-error-nombre'))).toBe('Indica el nombre.');
    expect(http.match((r) => r.method === 'POST').length).toBe(0);

    rellenar('miembro-alta-nombre', 'Nora');
    rellenar('miembro-alta-apellidos', 'Sanz');
    boton(panel()!, 'Enviar invitación').click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === API);
    expect(post.request.body).toEqual({ email: 'nueva@ejemplo.test', rol: 'PROFESOR', nombre: 'Nora', apellidos: 'Sanz' });
    post.flush(ficha({ usuarioId: 8, nombre: 'Nora', apellidos: 'Sanz', email: 'nueva@ejemplo.test', invitacionPendiente: true }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Invitación enviada.' }]);
    http.match((r) => r.url === `${API}/8`).forEach((r) => r.flush(ficha({ usuarioId: 8, nombre: 'Nora', apellidos: 'Sanz', invitacionPendiente: true })));
  });

  it('cambiar el email tras el 404 vuelve al camino normal (sin nombre ni apellidos)', async () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'nueva@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    boton(panel()!, 'Añadir miembro').click();
    http.expectOne((r) => r.method === 'POST').flush({ mensaje: 'No hay ninguna cuenta con ese email.' }, { status: 404, statusText: 'Not Found' });
    await esperar();
    rellenar('miembro-alta-email', 'otra@ejemplo.test');
    expect(panel()!.querySelector('#miembro-alta-nombre')).toBeNull();
    expect(texto(panel()!.querySelector('.miembros__pie .miembros__boton-primario'))).toBe('Añadir miembro');
  });

  it('rol desactivado (409): muestra el mensaje y ofrece «Reactivar», que manda reactivar:true', async () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'ana@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    boton(panel()!, 'Añadir miembro').click();
    http.expectOne((r) => r.method === 'POST').flush({ mensaje: 'La cuenta tiene el rol de profesor desactivado en esta escuela.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(panel()!.querySelector('#miembro-alta-error'))).toBe('La cuenta tiene el rol de profesor desactivado en esta escuela.');
    boton(panel()!, 'Reactivar').click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === API);
    expect(post.request.body).toEqual({ email: 'ana@ejemplo.test', rol: 'PROFESOR', reactivar: true });
    post.flush(ficha({ usuarioId: 7 }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Miembro añadido.' }]);
    http.match((r) => r.url === `${API}/7`).forEach((r) => r.flush(ficha({ usuarioId: 7 })));
  });

  it('otro error del alta (403): el mensaje del servidor en el panel, que sigue abierto', async () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'ana@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    boton(panel()!, 'Añadir miembro').click();
    http.expectOne((r) => r.method === 'POST').flush({ mensaje: 'Solo el propietario da o quita los roles de propietario y gestor.' }, { status: 403, statusText: 'Forbidden' });
    await esperar();
    expect(texto(panel()!.querySelector('#miembro-alta-error'))).toBe('Solo el propietario da o quita los roles de propietario y gestor.');
    expect(panel()!.textContent).not.toContain('Reactivar');
  });

  it('no escribe emails en la consola', async () => {
    const consola = spyOn(console, 'log');
    const aviso = spyOn(console, 'warn');
    const error = spyOn(console, 'error');
    abrirAlta();
    rellenar('miembro-alta-email', 'ana@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    boton(panel()!, 'Añadir miembro').click();
    http.expectOne((r) => r.method === 'POST').flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    await esperar();
    expect(consola).not.toHaveBeenCalled();
    expect(aviso).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------ lecturas, doble envío y cierres con escritura en vuelo

  it('una respuesta vieja de la lista se descarta: la petición anterior se cancela y manda la última', () => {
    cargar();
    const comp = fixture.componentInstance;
    comp.cargar();
    comp.cargar();
    const [vieja, ultima] = listados();
    expect(vieja.cancelled).toBeTrue();
    expect(ultima.cancelled).toBeFalse();
    ultima.flush(pagina([miembro({ usuarioId: 5, nombre: 'Berta', apellidos: 'Sol', email: 'berta@ejemplo.test' })]));
    fixture.detectChanges();
    expect(filas().map((f) => f.dataset['id'])).toEqual(['5']);
  });

  it('una recarga silenciosa que cancela una carga normal y falla deja el bloque de error, no el esqueleto', () => {
    cargar();
    const comp = fixture.componentInstance;
    comp.cargar();
    fixture.detectChanges();
    expect(comp.estado).toBe('cargando');
    comp.cargar(true);
    const [vieja, silenciosa] = listados();
    expect(vieja.cancelled).toBeTrue();
    silenciosa.flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(comp.estado).toBe('error');
    expect(el().querySelector('pn-bloque-error')).not.toBeNull();
    expect(el().querySelector('pn-esqueleto')).toBeNull();
  });

  it('una recarga silenciosa que falla con la lista ya a la vista no la tira', () => {
    cargar();
    fixture.componentInstance.cargar(true);
    listados()[0].flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.componentInstance.estado).toBe('listo');
    expect(filas().length).toBe(1);
  });

  it('al destruir el componente no queda viva la lectura de la lista', () => {
    permisosActuales = permisos(PROPIETARIA);
    fixture = TestBed.createComponent(MiembrosPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const [lectura] = listados();
    expect(lectura.cancelled).toBeFalse();
    fixture.destroy();
    expect(lectura.cancelled).toBeTrue();
  });

  it('doble envío del alta: un segundo envío mientras el primero va en vuelo no manda otro POST', () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'ana@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    boton(panel()!, 'Añadir miembro').click();
    panel()!.querySelector<HTMLFormElement>('#miembro-alta-form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    pintar();
    const posts = http.match((r) => r.method === 'POST' && r.url === API);
    expect(posts.length).toBe(1);
    posts[0].flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
  });

  it('doble envío en la ficha: un segundo «Guardar cargo» en vuelo no repite el PUT', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('PROFESOR', true, 'Jefe')] }));
    const caja = lineaRol('PROFESOR').querySelector<HTMLInputElement>('input[type="text"]')!;
    caja.value = 'Coordinador';
    caja.dispatchEvent(new Event('input'));
    pintar();
    const guardar = boton(lineaRol('PROFESOR'), 'Guardar cargo');
    guardar.click();
    guardar.click();
    const puts = http.match((r) => r.method === 'PUT' && r.url === `${API}/1/roles/PROFESOR`);
    expect(puts.length).toBe(1);
    puts[0].flush(ficha({ roles: [rol('PROFESOR', true, 'Coordinador')] }));
    await esperar();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('PUT /sedes conserva la sede inactiva ya guardada y la etiqueta como inactiva', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 3, nombre: 'Herrera' }, { id: 1, nombre: 'Casariche' }] }));
    const cajas = Array.from(panel()!.querySelectorAll<HTMLInputElement>('.miembros__sedes input'));
    expect(cajas.map((c) => texto(c.parentElement))).toEqual(['Casariche', 'Mollina', 'Herrera (inactiva)']);
    cajas[1].checked = true;
    cajas[1].dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Guardar sedes').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/sedes`);
    expect(put.request.body).toEqual({ sedeIds: [3, 1, 2] });
    put.flush(ficha({ roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 3, nombre: 'Herrera' }, { id: 1, nombre: 'Casariche' }, { id: 2, nombre: 'Mollina' }] }));
    await esperar();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('sin datos de sedes (permisos que fallan o tardan) las sedes guardadas se enseñan sin «(inactiva)»', async () => {
    cargar();
    fixture.componentInstance.contexto = { ...fixture.componentInstance.contexto, sedes: [] };
    await abrirFicha(ficha({ roles: [rol('RESPONSABLE_SEDE')], sedes: [{ id: 1, nombre: 'Casariche' }] }));
    const cajas = Array.from(panel()!.querySelectorAll<HTMLInputElement>('.miembros__sedes input'));
    expect(cajas.map((c) => texto(c.parentElement))).toEqual(['Casariche']);
    expect(cajas[0].checked).toBeTrue();
  });

  it('PUT /grupos conserva un grupo que no está en el catálogo', async () => {
    cargar();
    await abrirFicha(ficha({ numeroGrupos: 1, grupos: [{ grupoId: 99, nombre: 'Antiguo', sedeId: null, rol: 'TITULAR' }] }));
    expect(panel()!.querySelector('.miembros__grupo[data-grupo="99"]')).not.toBeNull();
    const nuevo = panel()!.querySelector<HTMLSelectElement>('#miembro-grupo-nuevo')!;
    nuevo.value = '11';
    nuevo.dispatchEvent(new Event('change'));
    pintar();
    boton(panel()!, 'Añadir').click();
    pintar();
    boton(panel()!, 'Guardar grupos').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/grupos`);
    expect(put.request.body).toEqual({
      asignaciones: [
        { grupoId: 99, rol: 'TITULAR' },
        { grupoId: 11, rol: 'TITULAR' },
      ],
    });
    put.flush(ficha());
    await esperar();
    listados().forEach((r) => r.flush(pagina([miembro()])));
  });

  it('al cerrar la ficha no quedan lecturas vivas (la ficha ni el catálogo de grupos)', async () => {
    cargar();
    el().querySelector<HTMLButtonElement>('.miembros__fila[data-id="1"]')!.click();
    pintar();
    const lectura = http.expectOne((r) => r.method === 'GET' && r.url === `${API}/1`);
    expect(lectura.cancelled).toBeFalse();
    fixture.componentInstance.abierto = null;
    pintar();
    expect(lectura.cancelled).toBeTrue();

    el().querySelector<HTMLButtonElement>('.miembros__fila[data-id="1"]')!.click();
    pintar();
    http.expectOne((r) => r.method === 'GET' && r.url === `${API}/1`).flush(ficha());
    await esperar();
    const catalogo = http.match((r) => r.url === GRUPOS);
    expect(catalogo.length).toBe(1);
    expect(catalogo[0].cancelled).toBeFalse();
    fixture.componentInstance.abierto = null;
    pintar();
    expect(catalogo[0].cancelled).toBeTrue();
  });

  it('ficha cerrada con un guardado en vuelo: el éxito se avisa y la lista se refresca igualmente', async () => {
    cargar();
    await abrirFicha(ficha({ roles: [rol('PROFESOR', true, 'Jefe')] }));
    const caja = lineaRol('PROFESOR').querySelector<HTMLInputElement>('input[type="text"]')!;
    caja.value = 'Coordinador';
    caja.dispatchEvent(new Event('input'));
    pintar();
    boton(lineaRol('PROFESOR'), 'Guardar cargo').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/roles/PROFESOR`);
    fixture.componentInstance.abierto = null;
    pintar();
    expect(el().querySelector('pn-miembro-ficha')).toBeNull();
    expect(listados().length).toBe(0);
    put.flush(ficha({ roles: [rol('PROFESOR', true, 'Coordinador')] }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Cargo guardado.' }]);
    const recarga = listados();
    expect(recarga.length).toBe(1);
    recarga[0].flush(pagina([miembro()]));
  });

  it('alta cerrada con el POST en vuelo: el éxito se avisa y la lista se refresca, sin abrir la ficha', async () => {
    abrirAlta();
    rellenar('miembro-alta-email', 'ana@ejemplo.test');
    rellenar('miembro-alta-rol', 'PROFESOR');
    boton(panel()!, 'Añadir miembro').click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === API);
    fixture.componentInstance.altaAbierta = false;
    pintar();
    post.flush(ficha({ usuarioId: 7, nombre: 'Ana', apellidos: 'Ruiz', email: 'ana@ejemplo.test' }));
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Miembro añadido.' }]);
    expect(el().querySelector('pn-miembro-ficha')).toBeNull();
    const recarga = listados();
    expect(recarga.length).toBe(1);
    recarga[0].flush(pagina([miembro()]));
  });
});
