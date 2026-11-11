import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { Sede } from './sedes-vista';
import { SedesPanelComponent } from './sedes.component';

const API = `${environment.apiUrl}/sedes`;
const WEB = `${environment.apiUrl}/publico/web`;

/** Catálogo de prueba: Moiskimdo (`legado`) o una escuela con web por plantilla. */
const CATALOGO_LEGADO = { organizacion: { comportamientoLegadoTaemoi: true } };
const CATALOGO_PLANTILLA = { organizacion: { comportamientoLegadoTaemoi: false } };

function sede(cambios: Partial<Sede> = {}): Sede {
  return {
    id: 1,
    nombre: 'Casariche',
    direccion: null,
    localidad: null,
    codigoPostal: null,
    telefono: null,
    email: null,
    codigoClubFederacion: null,
    aforoTurno: null,
    activa: true,
    ...cambios,
  };
}

const UNA_ACTIVA: Sede[] = [
  sede({ id: 1, nombre: 'Casariche', localidad: 'Casariche', telefono: '600 111 222', aforoTurno: 25, email: 'casariche@ejemplo.test' }),
  sede({ id: 2, nombre: 'Mollina', activa: false }),
];
const DOS_ACTIVAS: Sede[] = [sede({ id: 1 }), sede({ id: 2, nombre: 'Roda de Andalucía' }), sede({ id: 3, nombre: 'Herrera', activa: false })];

describe('SedesPanelComponent (E9-3, pantalla Sedes del panel nuevo)', () => {
  let fixture: ComponentFixture<SedesPanelComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let reiniciar: jasmine.Spy;
  // Por defecto, Moiskimdo: las pruebas de siempre comprueban que el cuerpo enviado no cambia (W12g).
  const catalogo = signal<object | null>(CATALOGO_LEGADO);

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/[\s  ]+/g, ' ').trim();
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const panel = (): HTMLElement | null => el().querySelector<HTMLElement>('dialog.pn-panel');
  const listados = (): TestRequest[] => http.match((r) => r.method === 'GET' && r.url === API);
  const campo = (clave: string): HTMLInputElement => el().querySelector<HTMLInputElement>(`#sedes-campo-${clave}`)!;
  const filas = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('.sedes__fila'));

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

  function cargar(sedes: Sede[] = UNA_ACTIVA): void {
    fixture = TestBed.createComponent(SedesPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    listados().forEach((r) => r.flush(sedes));
    fixture.detectChanges();
  }

  function escribir(clave: string, valor: string): void {
    const c = campo(clave);
    c.value = valor;
    c.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function abrirNueva(): void {
    boton(el(), 'Nueva sede').click();
    pintar();
  }

  function abrirEditar(id: number): void {
    el().querySelector<HTMLButtonElement>(`.sedes__fila[data-id="${id}"]`)!.click();
    pintar();
  }

  async function confirmar(): Promise<void> {
    (dialogo()!.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
    await esperar();
  }

  beforeEach(async () => {
    jasmine.clock().install();
    quitarTokens = ponerTokensDePrueba();
    reiniciar = jasmine.createSpy('reiniciar');
    catalogo.set(CATALOGO_LEGADO);
    await TestBed.configureTestingModule({
      imports: [SedesPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => ({ subscribe: () => undefined }), reiniciar } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
        { provide: CatalogoService, useValue: { catalogo } },
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

  // ------------------------------------------------------------------ lista y estados

  it('pide las sedes con ERROR_HANDLED_LOCALLY y pinta una fila por sede en el orden de la API', () => {
    fixture = TestBed.createComponent(SedesPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const [r] = listados();
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    r.flush(UNA_ACTIVA);
    fixture.detectChanges();

    expect(texto(el().querySelector('h1'))).toBe('Sedes');
    expect(filas().length).toBe(2);
    expect(texto(filas()[0].querySelector('.sedes__nombre'))).toBe('Editar Casariche');
    expect(texto(filas()[0].querySelector('.sedes__linea'))).toBe('Casariche · 600 111 222');
    expect(texto(filas()[0].querySelector('.sedes__estado'))).toBe('Activa');
    expect(filas()[1].querySelector('.sedes__linea')).toBeNull();
    expect(texto(filas()[1].querySelector('.sedes__estado'))).toBe('Inactiva');
    expect(filas()[1].classList).toContain('sedes__fila--inactiva');
    expect(filas()[0].classList).not.toContain('sedes__fila--inactiva');
  });

  it('cada fila es un botón entero: el teclado la abre', () => {
    cargar();
    expect(filas().every((f) => f.tagName === 'BUTTON' && f.getAttribute('type') === 'button')).toBeTrue();
  });

  it('con una sola sede funciona igual', () => {
    cargar([sede()]);
    expect(filas().length).toBe(1);
    abrirEditar(1);
    expect(texto(panel()!.querySelector('.pn-panel__titulo'))).toBe('Editar sede');
  });

  it('cargando: esqueleto tras 300 ms y sin filas', () => {
    fixture = TestBed.createComponent(SedesPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    jasmine.clock().tick(300);
    fixture.detectChanges();
    expect(el().querySelector('pn-esqueleto')).not.toBeNull();
    expect(texto(el().querySelector('pn-esqueleto [role="status"]'))).toBe('Cargando sedes');
    expect(filas().length).toBe(0);
    listados().forEach((r) => r.flush([]));
  });

  it('error: bloque con «Reintentar», que vuelve a pedir la lista', () => {
    fixture = TestBed.createComponent(SedesPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    listados()[0].flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('pn-bloque-error'))).toContain('No se pudieron cargar las sedes');
    boton(el(), 'Reintentar').click();
    fixture.detectChanges();
    const [otra] = listados();
    expect(otra).toBeDefined();
    otra.flush(UNA_ACTIVA);
    fixture.detectChanges();
    expect(el().querySelector('pn-bloque-error')).toBeNull();
    expect(filas().length).toBe(2);
  });

  it('vacío: bloque con «Nueva sede»', () => {
    cargar([]);
    expect(texto(el().querySelector('pn-bloque-vacio'))).toContain('Aún no hay sedes');
    el().querySelector<HTMLButtonElement>('.pn-bloque-vacio__accion')!.click();
    pintar();
    expect(panel()).not.toBeNull();
  });

  it('con la lista en error, «Nueva sede» está desactivado y abrirNueva() no abre el panel', () => {
    fixture = TestBed.createComponent(SedesPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    listados()[0].flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(boton(el(), 'Nueva sede').disabled).toBeTrue();
    fixture.componentInstance.abrirNueva();
    pintar();
    expect(panel()).toBeNull();
  });

  // ------------------------------------------------------------------ crear

  it('«Nueva sede»: ocho campos con las etiquetas exactas y sin sección Estado', () => {
    cargar([sede({ id: 1 }), sede({ id: 2 })]);
    abrirNueva();
    const etiquetas = Array.from(panel()!.querySelectorAll('label.sedes__etiqueta')).map((l) => texto(l));
    expect(etiquetas).toEqual([
      'Nombre (obligatorio)',
      'Dirección',
      'Localidad',
      'Código postal',
      'Teléfono',
      'Email',
      'Código del club en la federación',
      'Aforo por turno (opcional)',
    ]);
    expect(campo('nombre').getAttribute('maxlength')).toBe('100');
    expect(campo('direccion').getAttribute('maxlength')).toBe('255');
    expect(campo('codigoPostal').getAttribute('maxlength')).toBe('10');
    expect(campo('telefono').getAttribute('maxlength')).toBe('20');
    expect(campo('email').getAttribute('maxlength')).toBe('180');
    expect(campo('codigoClubFederacion').getAttribute('maxlength')).toBe('40');
    expect(campo('aforoTurno').getAttribute('maxlength')).toBe('6');
    expect(panel()!.querySelector('.sedes__estado-bloque')).toBeNull();
    expect(texto(boton(panel()!, 'Cancelar'))).toBe('Cancelar');
    expect(texto(panel()!.querySelector('.sedes__pie .sedes__boton-primario'))).toBe('Crear sede');
  });

  it('crear: POST con vacíos como null y aforo null, descarta los permisos, recarga y avisa', async () => {
    cargar([sede({ id: 1 }), sede({ id: 2 })]);
    abrirNueva();
    escribir('nombre', '  Lora de Estepa ');
    escribir('telefono', '600');
    boton(panel()!, 'Crear sede').click();
    await esperar();

    const [post] = http.match((r) => r.method === 'POST' && r.url === API);
    expect(post.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(post.request.body).toEqual({
      nombre: 'Lora de Estepa',
      direccion: null,
      localidad: null,
      codigoPostal: null,
      telefono: '600',
      email: null,
      codigoClubFederacion: null,
      aforoTurno: null,
    });
    expect(reiniciar).not.toHaveBeenCalled();
    post.flush(sede({ id: 9, nombre: 'Lora de Estepa' }), { status: 201, statusText: 'Created' });
    await esperar();

    expect(reiniciar).toHaveBeenCalledTimes(1);
    expect(panel()).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Sede creada.' }]);
    const [recarga] = listados();
    expect(recarga).toBeDefined();
    recarga.flush([sede({ id: 1 }), sede({ id: 2 }), sede({ id: 9, nombre: 'Lora de Estepa' })]);
    fixture.detectChanges();
    expect(filas().length).toBe(3);
  });

  it('crear con la recarga fallida: la sede nueva entra en la lista igualmente', async () => {
    cargar([sede({ id: 1 }), sede({ id: 2 })]);
    abrirNueva();
    escribir('nombre', 'Lora de Estepa');
    boton(panel()!, 'Crear sede').click();
    await esperar();
    http.expectOne((r) => r.method === 'POST' && r.url === API).flush(sede({ id: 9, nombre: 'Lora de Estepa' }), { status: 201, statusText: 'Created' });
    await esperar();
    const [recarga] = listados();
    expect(recarga).toBeDefined();
    recarga.flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(el().querySelector('pn-bloque-error')).toBeNull();
    expect(filas().length).toBe(3);
    expect(texto(el().querySelector('.sedes__fila[data-id="9"] .sedes__nombre'))).toBe('Editar Lora de Estepa');
  });

  it('crear sin nombre o con email no válido no envía nada y enfoca el campo', async () => {
    cargar([sede({ id: 1 }), sede({ id: 2 })]);
    abrirNueva();
    boton(panel()!, 'Crear sede').click();
    await esperar();
    expect(http.match((r) => r.method === 'POST').length).toBe(0);
    expect(texto(panel()!.querySelector('#sedes-error-nombre'))).toBe('Indica el nombre de la sede.');
    expect(campo('nombre').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campo('nombre'));

    escribir('nombre', 'Estepa');
    escribir('email', 'esto-no-es-un-email');
    boton(panel()!, 'Crear sede').click();
    await esperar();
    expect(http.match((r) => r.method === 'POST').length).toBe(0);
    expect(texto(panel()!.querySelector('#sedes-error-email'))).toBe('El email no es válido.');
  });

  it('bloquea el doble envío: dos pulsaciones, una petición', async () => {
    cargar([sede({ id: 1 }), sede({ id: 2 })]);
    abrirNueva();
    escribir('nombre', 'Estepa');
    const crear = boton(panel()!, 'Crear sede');
    crear.click();
    crear.click();
    const form = panel()!.querySelector('form')!;
    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await esperar();
    const posts = http.match((r) => r.method === 'POST' && r.url === API);
    expect(posts.length).toBe(1);
    expect(panel()!.querySelector('.sedes__pie .sedes__boton-primario')!.getAttribute('aria-busy')).toBe('true');
    posts[0].flush(sede({ id: 9, nombre: 'Estepa' }), { status: 201, statusText: 'Created' });
    await esperar();
    listados().forEach((r) => r.flush([]));
  });

  it('409 de nombre repetido: el mensaje del servidor sale en el campo Nombre y el panel sigue abierto', async () => {
    cargar([sede({ id: 1 }), sede({ id: 2 })]);
    abrirNueva();
    escribir('nombre', 'Casariche');
    boton(panel()!, 'Crear sede').click();
    await esperar();
    http.expectOne((r) => r.method === 'POST' && r.url === API).flush({ mensaje: 'Ya hay una sede con ese nombre.' }, { status: 409, statusText: 'Conflict' });
    await esperar();

    expect(panel()).not.toBeNull();
    expect(texto(panel()!.querySelector('#sedes-error-nombre'))).toBe('Ya hay una sede con ese nombre.');
    expect(campo('nombre').getAttribute('aria-invalid')).toBe('true');
    expect(campo('nombre').getAttribute('aria-describedby')).toBe('sedes-error-nombre');
    expect(panel()!.querySelector('#sedes-error-general')).toBeNull();
    expect(avisos()).toEqual([]);
    expect(reiniciar).not.toHaveBeenCalled();
    // Escribir de nuevo quita el error y se puede reintentar.
    escribir('nombre', 'Casariche 2');
    expect(panel()!.querySelector('#sedes-error-nombre')).toBeNull();
    expect(texto(panel()!.querySelector('.sedes__pie .sedes__boton-primario'))).toBe('Crear sede');
  });

  it('otro error: se ve en el panel con role=alert y el panel sigue abierto', async () => {
    cargar([sede({ id: 1 }), sede({ id: 2 })]);
    abrirNueva();
    escribir('nombre', 'Estepa');
    boton(panel()!, 'Crear sede').click();
    await esperar();
    http.expectOne((r) => r.method === 'POST').flush({ mensaje: 'El email de la sede no es válido.' }, { status: 400, statusText: 'Bad Request' });
    await esperar();
    const alerta = panel()!.querySelector('#sedes-error-general')!;
    expect(alerta.getAttribute('role')).toBe('alert');
    expect(texto(alerta)).toBe('El email de la sede no es válido.');
    expect(reiniciar).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------ segunda sede

  it('con exactamente una sede activa, crear muestra el aviso fijo y «Crear segunda sede»', () => {
    cargar(UNA_ACTIVA);
    abrirNueva();
    const nota = panel()!.querySelector('#sedes-segunda')!;
    expect(texto(nota)).toBe(
      'Será la segunda sede de la escuela. Desde entonces, las altas de alumnos y grupos pedirán sede y la caja se llevará por sede.'
    );
    expect(texto(panel()!.querySelector('.sedes__pie .sedes__boton-primario'))).toBe('Crear segunda sede');
    expect(dialogo()).toBeNull();
  });

  it('crear la segunda sede envía sin diálogo adicional', async () => {
    cargar(UNA_ACTIVA);
    abrirNueva();
    escribir('nombre', 'Roda de Andalucía');
    boton(panel()!, 'Crear segunda sede').click();
    await esperar();
    expect(dialogo()).toBeNull();
    http.expectOne((r) => r.method === 'POST' && r.url === API).flush(sede({ id: 7 }), { status: 201, statusText: 'Created' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Sede creada.' }]);
    listados().forEach((r) => r.flush([]));
  });

  it('con dos o más activas, o al editar, no hay aviso y el botón es «Crear sede»', () => {
    cargar(DOS_ACTIVAS);
    abrirNueva();
    expect(panel()!.querySelector('#sedes-segunda')).toBeNull();
    expect(texto(panel()!.querySelector('.sedes__pie .sedes__boton-primario'))).toBe('Crear sede');
  });

  it('editar con una sola activa no muestra el aviso de segunda sede', () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    expect(panel()!.querySelector('#sedes-segunda')).toBeNull();
    expect(texto(panel()!.querySelector('.sedes__pie .sedes__boton-primario'))).toBe('Guardar cambios');
  });

  // ------------------------------------------------------------------ editar

  it('editar: rellena los campos y «Guardar cambios» envía el aforoTurno que devolvió la API (el PUT reemplaza todo)', async () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    expect(texto(panel()!.querySelector('.pn-panel__titulo'))).toBe('Editar sede');
    expect(campo('nombre').value).toBe('Casariche');
    expect(campo('email').value).toBe('casariche@ejemplo.test');
    escribir('nombre', 'Casariche Centro');
    escribir('telefono', '');
    boton(panel()!, 'Guardar cambios').click();
    await esperar();

    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`);
    expect(put.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(put.request.body).toEqual({
      nombre: 'Casariche Centro',
      direccion: null,
      localidad: 'Casariche',
      codigoPostal: null,
      telefono: null,
      email: 'casariche@ejemplo.test',
      codigoClubFederacion: null,
      aforoTurno: 25,
    });
    put.flush({ ...UNA_ACTIVA[0], nombre: 'Casariche Centro', telefono: null });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Cambios guardados.' }]);
    expect(reiniciar).toHaveBeenCalledTimes(1);
    expect(panel()).toBeNull();
    listados().forEach((r) => r.flush(UNA_ACTIVA));
  });

  it('9-7: el aforo se ve con su ayuda, y vaciarlo envía null', async () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    expect(texto(panel()!.querySelector('label[for="sedes-campo-aforoTurno"]'))).toBe('Aforo por turno (opcional)');
    expect(campo('aforoTurno').value).toBe('25');
    expect(campo('aforoTurno').getAttribute('inputmode')).toBe('numeric');
    expect(texto(panel()!.querySelector('#sedes-ayuda-aforoTurno'))).toBe('Si lo dejas vacío, se usa el límite común de la escuela.');
    expect(campo('aforoTurno').getAttribute('aria-describedby')).toBe('sedes-ayuda-aforoTurno');
    expect(campo('nombre').getAttribute('aria-describedby')).toBeNull();
    escribir('aforoTurno', '');
    boton(panel()!, 'Guardar cambios').click();
    await esperar();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`);
    expect((put.request.body as { aforoTurno: number | null }).aforoTurno).toBeNull();
    put.flush({ ...UNA_ACTIVA[0], aforoTurno: null, turnosSobreAforo: false });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Cambios guardados.' }]);
    listados().forEach((r) => r.flush(UNA_ACTIVA));
  });

  it('9-7: un aforo no válido no se envía: error en el campo y foco', async () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    escribir('aforoTurno', '0');
    boton(panel()!, 'Guardar cambios').click();
    await esperar();
    http.expectNone((r) => r.method === 'PUT');
    expect(texto(panel()!.querySelector('#sedes-error-aforoTurno'))).toBe('Escribe un número entero mayor que 0 o déjalo vacío.');
    expect(campo('aforoTurno').getAttribute('aria-invalid')).toBe('true');
    expect(campo('aforoTurno').getAttribute('aria-describedby')).toBe('sedes-ayuda-aforoTurno sedes-error-aforoTurno');
    expect(document.activeElement).toBe(campo('aforoTurno'));
  });

  it('9-7: alta con aforo: lo envía como número', async () => {
    cargar(UNA_ACTIVA);
    boton(el(), 'Nueva sede').click();
    pintar();
    escribir('nombre', 'Herrera');
    escribir('aforoTurno', '30');
    boton(panel()!, 'Crear segunda sede').click();
    await esperar();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === API);
    expect((post.request.body as { aforoTurno: number | null }).aforoTurno).toBe(30);
    post.flush(sede({ id: 9, nombre: 'Herrera', aforoTurno: 30 }));
    await esperar();
    listados().forEach((r) => r.flush(UNA_ACTIVA));
  });

  it('9-7: un aforo por debajo de la ocupación se guarda y avisa, sin quedarse en la lista', async () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    escribir('aforoTurno', '5');
    boton(panel()!, 'Guardar cambios').click();
    await esperar();
    http
      .expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`)
      .flush({ ...UNA_ACTIVA[0], aforoTurno: 5, turnosSobreAforo: true });
    await esperar();
    expect(avisos()).toEqual([
      { tipo: 'exito', texto: 'Cambios guardados.' },
      { tipo: 'info', texto: 'Hay turnos con más inscritos que este aforo: no se da de baja a nadie, pero no entrarán nuevas plazas.' },
    ]);
    expect(panel()).toBeNull();
    expect(Object.keys(fixture.componentInstance.sedes[0])).not.toContain('turnosSobreAforo');
    listados().forEach((r) => r.flush(UNA_ACTIVA));
  });

  it('editar con la recarga fallida: la sede muestra el nombre nuevo', async () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    escribir('nombre', 'Casariche Centro');
    boton(panel()!, 'Guardar cambios').click();
    await esperar();
    http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`).flush({ ...UNA_ACTIVA[0], nombre: 'Casariche Centro' });
    await esperar();
    const [recarga] = listados();
    expect(recarga).toBeDefined();
    recarga.flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('.sedes__fila[data-id="1"] .sedes__nombre'))).toBe('Editar Casariche Centro');
  });

  it('una sede sin aforo se edita con aforoTurno null (no se inventa ni se pierde nada)', async () => {
    cargar([sede({ id: 1 }), sede({ id: 2, aforoTurno: null })]);
    abrirEditar(2);
    boton(panel()!, 'Guardar cambios').click();
    await esperar();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/2`);
    expect((put.request.body as { aforoTurno: number | null }).aforoTurno).toBeNull();
    put.flush(sede({ id: 2 }));
    await esperar();
    listados().forEach((r) => r.flush([]));
  });

  it('409 al editar el nombre: sale en el campo Nombre', async () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    escribir('nombre', 'Mollina');
    boton(panel()!, 'Guardar cambios').click();
    await esperar();
    http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`).flush({ mensaje: 'Ya hay una sede con ese nombre.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(panel()!.querySelector('#sedes-error-nombre'))).toBe('Ya hay una sede con ese nombre.');
  });

  it('Cancelar cierra el panel sin enviar', () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    boton(panel()!, 'Cancelar').click();
    pintar();
    expect(panel()).toBeNull();
    expect(http.match(() => true).length).toBe(0);
  });

  // ------------------------------------------------------------------ activar y desactivar

  it('sede activa (con otra activa): «Desactivar sede» abre el diálogo con su título y su texto', async () => {
    cargar(DOS_ACTIVAS);
    abrirEditar(2);
    expect(texto(panel()!.querySelector('.sedes__estado-titulo'))).toBe('Estado');
    expect(panel()!.textContent).not.toContain('Activar sede');
    boton(panel()!, 'Desactivar sede').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('¿Desactivar Roda de Andalucía?');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe('No admitirá grupos, turnos ni cargos nuevos. Lo ya guardado se conserva.');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__accion'))).toBe('Desactivar');
  });

  it('confirmar desactivar: PUT /activa {activa:false}, descarta los permisos, actualiza la lista y avisa', async () => {
    cargar(DOS_ACTIVAS);
    abrirEditar(2);
    boton(panel()!, 'Desactivar sede').click();
    await esperar();
    await confirmar();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/2/activa`);
    expect(put.request.body).toEqual({ activa: false });
    expect(put.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    put.flush({ ...DOS_ACTIVAS[1], activa: false });
    await esperar();

    expect(reiniciar).toHaveBeenCalledTimes(1);
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Sede desactivada.' }]);
    expect(texto(el().querySelector('.sedes__fila[data-id="2"] .sedes__estado'))).toBe('Inactiva');
    // El panel sigue abierto y ahora ofrece «Activar sede».
    expect(texto(panel()!.querySelector('.sedes__estado-bloque .sedes__estado'))).toBe('Inactiva');
    expect(boton(panel()!, 'Activar sede')).toBeDefined();
    expect(listados().length).toBe(1);
  });

  it('cancelar el diálogo de desactivar no llama a la API ni toca los permisos', async () => {
    cargar(DOS_ACTIVAS);
    abrirEditar(2);
    boton(panel()!, 'Desactivar sede').click();
    await esperar();
    (dialogo()!.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
    await esperar();
    expect(http.match((r) => r.method === 'PUT').length).toBe(0);
    expect(reiniciar).not.toHaveBeenCalled();
    expect(avisos()).toEqual([]);
  });

  it('error al desactivar: aviso de error con el mensaje del servidor y sin tocar los permisos', async () => {
    cargar(DOS_ACTIVAS);
    abrirEditar(2);
    boton(panel()!, 'Desactivar sede').click();
    await esperar();
    await confirmar();
    http.expectOne((r) => r.url === `${API}/2/activa`).flush({ mensaje: 'La escuela se quedaría sin sedes activas.' }, { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'La escuela se quedaría sin sedes activas.' }]);
    expect(reiniciar).not.toHaveBeenCalled();
  });

  it('la única sede activa no se puede desactivar: sin botón y con su texto', () => {
    cargar(UNA_ACTIVA);
    abrirEditar(1);
    const bloque = panel()!.querySelector('.sedes__estado-bloque')!;
    expect(Array.from(bloque.querySelectorAll('button')).length).toBe(0);
    expect(texto(bloque.querySelector('.sedes__estado-texto'))).toBe('Es la única sede activa de la escuela.');
  });

  it('inactiva con otra activa: «Activar sede» pide confirmación con el aviso de segunda sede', async () => {
    cargar(UNA_ACTIVA);
    abrirEditar(2);
    expect(panel()!.textContent).not.toContain('Desactivar sede');
    boton(panel()!, 'Activar sede').click();
    await esperar();
    expect(texto(dialogo()!.querySelector('.pn-dialogo__titulo'))).toBe('¿Activar Mollina?');
    expect(texto(dialogo()!.querySelector('.pn-dialogo__mensaje'))).toBe(
      'Será la segunda sede de la escuela. Desde entonces, las altas de alumnos y grupos pedirán sede y la caja se llevará por sede.'
    );
    expect(http.match((r) => r.method === 'PUT').length).toBe(0);
    await confirmar();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/2/activa`);
    expect(put.request.body).toEqual({ activa: true });
    put.flush({ ...UNA_ACTIVA[1], activa: true });
    await esperar();
    expect(reiniciar).toHaveBeenCalledTimes(1);
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Sede activada.' }]);
    expect(texto(el().querySelector('.sedes__fila[data-id="2"] .sedes__estado'))).toBe('Activa');
  });

  it('inactiva cuando ya hay dos o más activas: se activa sin diálogo', async () => {
    cargar(DOS_ACTIVAS);
    abrirEditar(3);
    boton(panel()!, 'Activar sede').click();
    await esperar();
    expect(dialogo()).toBeNull();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/3/activa`);
    expect(put.request.body).toEqual({ activa: true });
    put.flush({ ...DOS_ACTIVAS[2], activa: true });
    await esperar();
    expect(reiniciar).toHaveBeenCalledTimes(1);
    expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Sede activada.' }]);
  });

  it('activar no se envía dos veces mientras está en vuelo', async () => {
    cargar(DOS_ACTIVAS);
    abrirEditar(3);
    const activar = boton(panel()!, 'Activar sede');
    activar.click();
    activar.click();
    await esperar();
    expect(http.match((r) => r.url === `${API}/3/activa`).length).toBe(1);
  });

  // ------------------------------------------------------------------ W12g: grupo «En la web» (d523, d524)

  describe('W12g: grupo «En la web»', () => {
    const HERRERA = sede({
      id: 1,
      nombre: 'Herrera',
      slug: 'herrera',
      lugar: 'Pabellón municipal',
      indicacionesAcceso: 'Entrada por la puerta lateral.',
      fotoImagenId: 11,
      foto2ImagenId: 12,
      fotoAlt: 'Pabellón con el tatami montado',
      foto2Alt: null,
    });
    const ESTEPA = sede({ id: 2, nombre: 'Estepa', slug: 'estepa' });
    const URL_FOTO_1 = 'https://ejemplo.test/imagenes/org-2/web/sedes/a.jpg';

    const web = (): HTMLElement | null => el().querySelector<HTMLElement>('.sedes__web');
    /** El texto de cada hijo, separado por un espacio (la plantilla no deja espacios entre elementos). */
    const partes = (selector: string): string => Array.from(el().querySelector(selector)!.children).map((h) => texto(h)).filter((t) => t !== '').join(' ');
    const foto = (n: number): HTMLElement => el().querySelector<HTMLElement>(`.sedes__foto[data-foto="${n}"]`)!;
    const jpeg = (nombre = 'pabellon-herrera.jpg'): File => new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3])], nombre, { type: 'image/jpeg' });
    const elegir = (archivo: File): Event => ({ target: { files: [archivo], value: 'x' } }) as unknown as Event;

    function cargarPlantilla(sedes: Sede[] = [HERRERA, ESTEPA]): void {
      catalogo.set(CATALOGO_PLANTILLA);
      cargar(sedes);
      const vista = http.match((r) => r.method === 'GET' && r.url === WEB);
      expect(vista.length).toBe(1);
      expect(vista[0].request.params.get('vistaPrevia')).toBe('true');
      vista[0].flush({ sedes: [{ id: 1, fotoUrl: URL_FOTO_1, foto2Url: null }] });
      fixture.detectChanges();
    }

    it('Moiskimdo (legado): sin el grupo, sin pedir la vista previa y el PUT con el cuerpo de siempre', async () => {
      cargar([HERRERA, ESTEPA]);
      abrirEditar(1);
      expect(web()).toBeNull();
      expect(el().textContent).not.toContain('En la web');
      expect(http.match((r) => r.url === WEB).length).toBe(0);

      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`);
      expect(Object.keys(put.request.body as object).sort()).toEqual(
        ['aforoTurno', 'codigoClubFederacion', 'codigoPostal', 'direccion', 'email', 'localidad', 'nombre', 'telefono'].sort()
      );
    });

    it('sin catálogo no hay grupo (falla cerrado)', () => {
      catalogo.set(null);
      cargar([HERRERA]);
      abrirEditar(1);
      expect(web()).toBeNull();
      expect(http.match((r) => r.url === WEB).length).toBe(0);
    });

    it('editar: textos, orden y valores del boceto', () => {
      cargarPlantilla();
      abrirEditar(1);
      const g = web()!;
      expect(texto(g.querySelector('h3'))).toBe('En la web');
      expect(texto(g.querySelector('.sedes__web-texto'))).toBe('Así sale esta sede en tu web. Todo es opcional.');
      const etiquetas = Array.from(g.querySelectorAll(':scope > .sedes__campo > label, .sedes__foto label')).map((l) => texto(l));
      expect(etiquetas).toEqual([
        'Dirección de su página(opcional)',
        'Lugar(opcional)',
        'Indicaciones de acceso(opcional)',
        'Descripción de la foto(opcional)',
        'Descripción de la foto(opcional)',
      ]);
      expect(texto(g.querySelector('.sedes__prefijo-fijo'))).toBe(`${location.host}/sedes/`);
      expect(campo('slug').value).toBe('herrera');
      expect(partes('#sedes-ayuda-slug')).toBe(
        'Minúsculas, números y guiones. Sin ella, la sede no tiene página propia en la web. Si la cambias, los enlaces que ya hayas compartido dejan de funcionar. 7 / 60'
      );
      expect(campo('lugar').value).toBe('Pabellón municipal');
      expect(partes('#sedes-ayuda-lugar')).toBe('El edificio donde se da la clase. Sale encima de la dirección. 18 / 100');
      expect(partes('#sedes-ayuda-indicacionesAcceso')).toBe('Cómo encontrar la entrada. Sale en «Cómo llegar». 30 / 300');
      expect(partes('.sedes__foto[data-foto="1"] .sedes__foto-cabecera')).toBe('Foto principal Sale arriba en su página y en «Fotos de las sedes».');
      expect(partes('.sedes__foto[data-foto="2"] .sedes__foto-cabecera')).toBe('Segunda foto Sale en «Cómo llegar», en su página.');
      expect(foto(1).querySelector('img')!.getAttribute('src')).toBe(URL_FOTO_1);
      expect(texto(foto(1).querySelector('.sedes__foto-nota'))).toBe('Guardada');
      expect(campo('fotoAlt').value).toBe('Pabellón con el tatami montado');
      expect(campo('foto2Alt').placeholder).toBe('Foto de la sede de Herrera');
      expect(partes('#sedes-ayuda-foto2Alt')).toBe(
        'Qué se ve, para quien no puede verla. Si la dejas vacía, la web dice «Foto de la sede de Herrera». Sin nombres de alumnos. 0 / 150'
      );
      expect(g.textContent).not.toContain('Las fotos se suben al crear la sede.');
      // «Estado» sigue después del grupo.
      expect(g.compareDocumentPosition(el().querySelector('.sedes__estado-bloque')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('nueva: grupo vacío, «Añadir foto» con formatos y la nota de las fotos', () => {
      cargarPlantilla();
      abrirNueva();
      expect(campo('slug').value).toBe('');
      expect(partes('#sedes-ayuda-slug')).toBe('Minúsculas, números y guiones. Sin ella, la sede no tiene página propia en la web. 0 / 60');
      expect(partes('.sedes__foto[data-foto="1"] .sedes__anadir-foto')).toBe('Añadir foto JPG, PNG o WebP, hasta 50 MB');
      expect(texto(web()!.querySelector('.sedes__nota'))).toBe('Las fotos se suben al crear la sede.');
    });

    it('el contador sigue lo escrito', () => {
      cargarPlantilla();
      abrirEditar(1);
      escribir('lugar', 'Gimnasio');
      expect(texto(el().querySelector('#sedes-ayuda-lugar .sedes__contador'))).toBe('8 / 100');
    });

    it('PUT con los campos sin tocar: se envían tal como llegaron', async () => {
      cargarPlantilla();
      abrirEditar(1);
      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`);
      expect(put.request.body).toEqual(
        jasmine.objectContaining({
          nombre: 'Herrera',
          slug: 'herrera',
          lugar: 'Pabellón municipal',
          indicacionesAcceso: 'Entrada por la puerta lateral.',
          fotoAlt: 'Pabellón con el tatami montado',
          foto2Alt: null,
        })
      );
      put.flush(HERRERA);
      await esperar();
      expect(panel()).toBeNull();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Cambios guardados.' }]);
      // Sin fotos que enviar, ninguna petición de fotos.
      expect(http.match((r) => r.url.includes('/fotos/')).length).toBe(0);
    });

    it('PUT con los campos cambiados: recortados, vacíos como null y la dirección en minúsculas', async () => {
      cargarPlantilla();
      abrirEditar(1);
      escribir('slug', ' Herrera-Centro ');
      escribir('lugar', '   ');
      escribir('indicacionesAcceso', ' Por el patio. ');
      escribir('foto2Alt', 'Entrada del pabellón');
      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`);
      expect(put.request.body).toEqual({
        nombre: 'Herrera',
        direccion: null,
        localidad: null,
        codigoPostal: null,
        telefono: null,
        email: null,
        codigoClubFederacion: null,
        aforoTurno: null,
        slug: 'herrera-centro',
        lugar: null,
        indicacionesAcceso: 'Por el patio.',
        fotoAlt: 'Pabellón con el tatami montado',
        foto2Alt: 'Entrada del pabellón',
      });
    });

    it('crear: POST con los campos de la web vacíos como null', async () => {
      cargarPlantilla();
      abrirNueva();
      escribir('nombre', 'Lora de Estepa');
      escribir('slug', 'lora-de-estepa');
      boton(panel()!, 'Crear sede').click();
      await esperar();
      const post = http.expectOne((r) => r.method === 'POST' && r.url === API);
      expect(post.request.body).toEqual(
        jasmine.objectContaining({ nombre: 'Lora de Estepa', slug: 'lora-de-estepa', lugar: null, indicacionesAcceso: null, fotoAlt: null, foto2Alt: null })
      );
    });

    it('dirección que ya usa otra sede de la lista: error en el campo, foco y nada enviado', async () => {
      cargarPlantilla();
      abrirEditar(1);
      escribir('slug', 'Estepa');
      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      expect(http.match((r) => r.method === 'PUT').length).toBe(0);
      expect(texto(el().querySelector('#sedes-error-slug'))).toBe('Ya la usa la sede de Estepa. Elige otra.');
      expect(campo('slug').getAttribute('aria-invalid')).toBe('true');
      expect(campo('slug').getAttribute('aria-describedby')).toBe('sedes-error-slug sedes-ayuda-slug');
      expect(el().querySelector('.sedes__prefijo')!.getAttribute('aria-invalid')).toBe('true');
      expect(document.activeElement).toBe(campo('slug'));
      // Escribir quita el error.
      escribir('slug', 'herrera');
      expect(el().querySelector('#sedes-error-slug')).toBeNull();
    });

    it('dirección no válida: el error del servidor en el campo, sin enviar', async () => {
      cargarPlantilla();
      abrirEditar(1);
      escribir('slug', 'roda de andalucía');
      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      expect(http.match((r) => r.method === 'PUT').length).toBe(0);
      expect(texto(el().querySelector('#sedes-error-slug'))).toBe(
        'La dirección web de la sede solo admite minúsculas sin tildes, cifras y guiones entre ellas (hasta 60 caracteres).'
      );
    });

    it('409 de dirección repetida del servidor: el error va al campo (no a Nombre) y el panel sigue abierto', async () => {
      cargarPlantilla();
      abrirEditar(1);
      escribir('slug', 'casariche');
      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      http
        .expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`)
        .flush({ mensaje: 'Ya hay una sede con esa dirección web.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(panel()).not.toBeNull();
      expect(texto(el().querySelector('#sedes-error-slug'))).toBe('Ya hay una sede con esa dirección web.');
      expect(el().querySelector('#sedes-error-nombre')).toBeNull();
      expect(document.activeElement).toBe(campo('slug'));
    });

    it('400 de dirección repetida o no válida y de lugar: cada uno en su campo', async () => {
      cargarPlantilla();
      abrirEditar(1);
      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      http
        .expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`)
        .flush({ mensaje: 'Ya hay una sede con esa dirección web.' }, { status: 400, statusText: 'Bad Request' });
      await esperar();
      expect(texto(el().querySelector('#sedes-error-slug'))).toBe('Ya hay una sede con esa dirección web.');

      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      http
        .expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`)
        .flush({ mensaje: 'El lugar de la sede no puede pasar de 120 caracteres.' }, { status: 400, statusText: 'Bad Request' });
      await esperar();
      expect(texto(el().querySelector('#sedes-error-lugar'))).toBe('El lugar de la sede no puede pasar de 120 caracteres.');
      expect(document.activeElement).toBe(campo('lugar'));
      expect(el().querySelector('#sedes-error-general')).toBeNull();
    });

    it('editar: foto 1 nueva con descripción y foto 2 quitada se envían al guardar, con el progreso', async () => {
      cargarPlantilla();
      abrirEditar(1);
      await fixture.componentInstance.fotoElegida(1, elegir(jpeg('entrenamiento-herrera.jpg')));
      pintar();
      expect(texto(foto(1).querySelector('.sedes__foto-nombre'))).toBe('entrenamiento-herrera.jpg');
      expect(texto(foto(1).querySelector('.sedes__foto-nota'))).toBe('1 KB · se subirá al guardar los cambios');
      expect(foto(1).querySelector('img')!.getAttribute('src')).toMatch(/^blob:/);
      escribir('fotoAlt', 'Clase de los peques');
      boton(foto(2), 'Quitar').click();
      pintar();
      expect(texto(foto(2).querySelector('.sedes__anadir-foto'))).toContain('Añadir foto');

      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1`);
      expect((put.request.body as { fotoAlt: string }).fotoAlt).toBe('Clase de los peques');
      put.flush(HERRERA);
      await esperar();

      const subida = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/1/fotos/1`);
      expect(subida.request.reportProgress).toBeTrue();
      expect(subida.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
      const datos = subida.request.body as FormData;
      expect((datos.get('archivo') as File).name).toBe('entrenamiento-herrera.jpg');
      expect(panel()).not.toBeNull();
      subida.event({ type: HttpEventType.UploadProgress, loaded: 62, total: 100 });
      pintar();
      expect(texto(foto(1).querySelector('.sedes__foto-nota'))).toBe('Subiendo la foto… 62 %');
      expect(foto(1).querySelector('[role="progressbar"]')!.getAttribute('aria-valuenow')).toBe('62');
      expect(texto(boton(panel()!, 'Guardando…'))).toBe('Guardando…');
      subida.flush({ ...HERRERA, fotoImagenId: 21 });
      await esperar();

      const borrado = http.expectOne((r) => r.method === 'DELETE' && r.url === `${API}/1/fotos/2`);
      borrado.flush({ ...HERRERA, fotoImagenId: 21, foto2ImagenId: null });
      await esperar();
      expect(panel()).toBeNull();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Cambios guardados.' }]);
      // Las miniaturas se vuelven a pedir.
      expect(http.match((r) => r.method === 'GET' && r.url === WEB).length).toBe(1);
    });

    it('crear con foto: la nota dice «se subirá al crear la sede» y la foto va a la sede nueva', async () => {
      cargarPlantilla();
      abrirNueva();
      escribir('nombre', 'Lora de Estepa');
      await fixture.componentInstance.fotoElegida(2, elegir(jpeg('pabellon-lora.jpg')));
      pintar();
      expect(texto(foto(2).querySelector('.sedes__foto-nota'))).toBe('1 KB · se subirá al crear la sede');
      boton(panel()!, 'Crear sede').click();
      await esperar();
      http.expectOne((r) => r.method === 'POST' && r.url === API).flush(sede({ id: 9, nombre: 'Lora de Estepa' }), { status: 201, statusText: 'Created' });
      await esperar();
      const subida = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/9/fotos/2`);
      subida.flush(sede({ id: 9, nombre: 'Lora de Estepa', foto2ImagenId: 30 }));
      await esperar();
      expect(panel()).toBeNull();
      expect(avisos()).toEqual([{ tipo: 'exito', texto: 'Sede creada.' }]);
    });

    it('la foto no sube: el panel sigue abierto como «Editar sede», con el fallo en la foto y sin duplicar la sede', async () => {
      cargarPlantilla();
      abrirNueva();
      escribir('nombre', 'Lora de Estepa');
      await fixture.componentInstance.fotoElegida(1, elegir(jpeg('pabellon-lora.jpg')));
      boton(panel()!, 'Crear sede').click();
      await esperar();
      http.expectOne((r) => r.method === 'POST' && r.url === API).flush(sede({ id: 9, nombre: 'Lora de Estepa' }), { status: 201, statusText: 'Created' });
      await esperar();
      http
        .expectOne((r) => r.method === 'PUT' && r.url === `${API}/9/fotos/1`)
        .flush({ mensaje: 'La imagen no se puede leer.' }, { status: 400, statusText: 'Bad Request' });
      await esperar();

      expect(panel()).not.toBeNull();
      expect(texto(panel()!.querySelector('h2'))).toBe('Editar sede');
      expect(texto(foto(1).querySelector('.sedes__foto-fallo-titulo'))).toBe('No se pudo subir la foto');
      expect(texto(foto(1).querySelector('.sedes__foto-fallo'))).toContain('La imagen no se puede leer.');
      expect(foto(1).querySelector('.sedes__foto-fallo')!.getAttribute('role')).toBe('alert');
      expect(avisos()).toEqual([{ tipo: 'error', texto: 'La sede se ha creado, pero una foto no se pudo guardar.' }]);

      // Reintento: PUT de la sede 9 (no otro POST) y la foto otra vez.
      boton(panel()!, 'Guardar cambios').click();
      await esperar();
      expect(http.match((r) => r.method === 'POST').length).toBe(0);
      http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/9`).flush(sede({ id: 9, nombre: 'Lora de Estepa' }));
      await esperar();
      http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/9/fotos/1`).flush(sede({ id: 9, nombre: 'Lora de Estepa', fotoImagenId: 40 }));
      await esperar();
      expect(panel()).toBeNull();
    });

    it('una foto demasiado grande no se acepta: «Demasiado grande» y la de antes sigue puesta', async () => {
      cargarPlantilla();
      abrirEditar(1);
      const grande = { name: 'entrada-pabellon.jpg', type: 'image/jpeg', size: 64 * 1024 * 1024 } as File;
      await fixture.componentInstance.fotoElegida(1, elegir(grande));
      pintar();
      const fallo = foto(1).querySelector('.sedes__foto-fallo')!;
      expect(texto(fallo.querySelector('.sedes__foto-fallo-titulo'))).toBe('Demasiado grande');
      expect(texto(fallo)).toContain('«entrada-pabellon.jpg» pesa 64 MB. Usa una foto de menos de 50 MB.');
      expect(texto(fallo)).toContain('La foto de antes sigue puesta.');
      boton(fallo, 'Cerrar').click();
      pintar();
      expect(foto(1).querySelector('.sedes__foto-fallo')).toBeNull();
      expect(texto(foto(1).querySelector('.sedes__foto-nota'))).toBe('Guardada');
    });

    it('una foto HEIC: «Formato no admitido» con el motivo', async () => {
      cargarPlantilla();
      abrirEditar(2);
      await fixture.componentInstance.fotoElegida(1, elegir(new File([new Uint8Array([1])], 'grupo.heic', { type: 'image/heic' })));
      pintar();
      expect(texto(foto(1).querySelector('.sedes__foto-fallo'))).toContain('Formato no admitido');
      expect(texto(foto(1).querySelector('.sedes__foto-fallo'))).toContain('«grupo.heic»: convierte la foto a JPG o desactiva HEIF en la cámara.');
      expect(texto(foto(1).querySelector('.sedes__foto-fallo'))).not.toContain('La foto de antes sigue puesta.');
    });
  });
});
