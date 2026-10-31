import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { Sede } from './sedes-vista';
import { SedesPanelComponent } from './sedes.component';

const API = `${environment.apiUrl}/sedes`;

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
    await TestBed.configureTestingModule({
      imports: [SedesPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => ({ subscribe: () => undefined }), reiniciar } },
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

  it('«Nueva sede»: siete campos con las etiquetas exactas, sin aforo, y sin sección Estado', () => {
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
    ]);
    expect(campo('nombre').getAttribute('maxlength')).toBe('100');
    expect(campo('direccion').getAttribute('maxlength')).toBe('255');
    expect(campo('codigoPostal').getAttribute('maxlength')).toBe('10');
    expect(campo('telefono').getAttribute('maxlength')).toBe('20');
    expect(campo('email').getAttribute('maxlength')).toBe('180');
    expect(campo('codigoClubFederacion').getAttribute('maxlength')).toBe('40');
    expect(panel()!.textContent).not.toMatch(/aforo/i);
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
});
