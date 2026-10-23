import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { AuditoriaEvento } from '../../../interfaces/auditoria-evento';
import { AuditoriaEventoDetalle } from '../../../interfaces/auditoria-evento-detalle';
import { PermisosSesionService } from '../../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../../capas/tokens-prueba';
import { EstilosPanelService } from '../../estilos-panel.service';
import { RegistroAccionesComponent } from './registro.component';
import { eventoVista } from './registro-vista.spec';

const API = environment.apiUrl;
const EVENTOS = `${API}/admin/auditoria/eventos`;
const MODULOS = `${API}/admin/auditoria/modulos`;

const EVENTOS_DE_PRUEBA: AuditoriaEvento[] = [
  eventoVista({ id: 1, fechaEvento: '2026-10-23T10:42:00', accion: 'UPDATE', resumen: 'Cambio en la ficha de un alumno', usuarioId: 12 }),
  eventoVista({ id: 2, fechaEvento: '2026-10-23T08:05:00', accion: 'READ', modulo: 'salud', resumen: 'Consulta de la salud de un alumno', usuarioId: 3 }),
  eventoVista({ id: 3, fechaEvento: '2026-10-22T19:03:00', accion: 'CREATE', modulo: 'grupos', resumen: 'Alta de un grupo', usuarioId: null }),
];

function detalleVista(cambios: Partial<AuditoriaEventoDetalle> = {}): AuditoriaEventoDetalle {
  return {
    ...eventoVista({ id: 1 }),
    ipCliente: '10.0.0.9',
    userAgent: 'NavegadorDePrueba/1.0',
    queryParamsJson: '{"page":"1"}',
    payloadJson: '{"nombre":"[REDACTADO]","activo":true}',
    ...cambios,
  };
}

describe('RegistroAccionesComponent (8b-3k, Registro de acciones del panel nuevo, P2 cronología por días)', () => {
  let fixture: ComponentFixture<RegistroAccionesComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/[\s  ]+/g, ' ').trim();
  const peticiones = (url: string): TestRequest[] => http.match((r) => r.method === 'GET' && r.url === url);
  const accion = (id: number): HTMLButtonElement => el().querySelector<HTMLButtonElement>(`.reg__accion[data-id="${id}"]`)!;
  const panel = (): HTMLElement | null => el().querySelector<HTMLElement>('dialog.pn-panel');

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
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : 'otro',
      texto: texto(a.querySelector('.pn-aviso__texto')),
    }));
  }

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').trim() === t || texto(x) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  function responderEventos(eventos = EVENTOS_DE_PRUEBA, total = eventos.length, paginas = 1): void {
    peticiones(EVENTOS).forEach((r) => r.flush({ content: eventos, totalElements: total, totalPages: paginas, number: 0, size: 25 }));
    fixture.detectChanges();
  }

  /** Comprueba la consulta pendiente (una sola) y la responde. */
  function esperarConsulta(url: string, eventos = EVENTOS_DE_PRUEBA, total = eventos.length, paginas = 1): void {
    const pendientes = peticiones(EVENTOS);
    expect(pendientes.length).toBe(1);
    expect(pendientes[0]?.request.urlWithParams).toBe(url);
    pendientes.forEach((r) => r.flush({ content: eventos, totalElements: total, totalPages: paginas, number: 0, size: 25 }));
    fixture.detectChanges();
  }

  function cargar(eventos = EVENTOS_DE_PRUEBA, total = eventos.length, paginas = 1): void {
    fixture = TestBed.createComponent(RegistroAccionesComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    peticiones(MODULOS).forEach((r) => r.flush(['alumnos', 'grupos', 'salud']));
    responderEventos(eventos, total, paginas);
  }

  function abrirFiltros(): HTMLFormElement {
    el().querySelector<HTMLButtonElement>('#reg-filtros')!.click();
    fixture.detectChanges();
    return el().querySelector<HTMLFormElement>('#reg-panel-filtros')!;
  }

  function poner(control: HTMLInputElement | HTMLSelectElement, valor: string): void {
    control.value = valor;
    control.dispatchEvent(new Event(control instanceof HTMLSelectElement ? 'change' : 'input'));
    fixture.detectChanges();
  }

  async function verDetalle(id = 1, detalle: AuditoriaEventoDetalle = detalleVista()): Promise<HTMLElement> {
    accion(id).click();
    fixture.detectChanges();
    const [r] = peticiones(`${EVENTOS}/${id}`);
    expect(r.request.withCredentials).toBeTrue();
    expect(r.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    r.flush(detalle);
    await esperar();
    return panel()!;
  }

  beforeEach(async () => {
    Swal.close();
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 23, 12, 0));
    quitarTokens = ponerTokensDePrueba();
    await TestBed.configureTestingModule({
      imports: [RegistroAccionesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PermisosSesionService, useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) } },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    Swal.close();
    quitarTokens();
    jasmine.clock().uninstall();
  });

  it('al entrar pide los módulos y la primera página con la consulta de hoy (resultado EXITO), con ERROR_HANDLED_LOCALLY', () => {
    fixture = TestBed.createComponent(RegistroAccionesComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const [m] = peticiones(MODULOS);
    expect(m.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    const [e] = peticiones(EVENTOS);
    expect(e.request.urlWithParams).toBe(`${EVENTOS}?page=1&size=25&resultado=EXITO`);
    expect(e.request.withCredentials).toBeTrue();
    expect(e.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    m.flush([]);
    responderEventos();
    http.verify();
  });

  it('título, subtítulo con el total y la cronología por días con hora, pastilla, qué, módulo y «Cuenta N»', () => {
    cargar(EVENTOS_DE_PRUEBA, 128, 6);
    expect(texto(el().querySelector('h1'))).toBe('Registro de acciones');
    expect(texto(el().querySelector('.reg__subtitulo'))).toBe('Lo que se ha hecho en tu escuela: 128 acciones.');
    const dias = Array.from(el().querySelectorAll('.reg__dia')).map((d) => texto(d.querySelector('.reg__dia-titulo')));
    expect(dias).toEqual(['Hoy', 'Ayer']);
    const fila = accion(1);
    expect(texto(fila.querySelector('.reg__hora'))).toBe('10:42');
    expect(fila.querySelector('time')!.getAttribute('datetime')).toBe('2026-10-23T10:42:00');
    expect(texto(fila.querySelector('.reg__pastilla'))).toBe('Cambio');
    expect(fila.querySelector('.reg__pastilla')!.getAttribute('data-tono')).toBe('cambio');
    expect(texto(fila.querySelector('.reg__que'))).toBe('Cambio en la ficha de un alumno');
    expect(texto(fila.querySelector('.reg__modulo'))).toBe('alumnos');
    expect(texto(fila.querySelector('.reg__cuenta'))).toBe('Cuenta 12');
    expect(texto(accion(2).querySelector('.reg__pastilla'))).toBe('Salud');
    expect(texto(accion(3).querySelector('.reg__cuenta'))).toBe('Sin cuenta');
    expect(fila.getAttribute('aria-label')).toBe('Ver el detalle: Cambio en la ficha de un alumno (Cambio, 10:42, alumnos, Cuenta 12)');
    // Ni el nombre ni el email de quien lo hizo.
    expect(texto(el())).not.toContain('Persona Ficticia');
    expect(texto(el())).not.toContain('persona@ejemplo.test');
  });

  it('una sola acción: «1 acción»; sin acciones, el aviso de hoy', () => {
    cargar([EVENTOS_DE_PRUEBA[0]]);
    expect(texto(el().querySelector('.reg__subtitulo'))).toBe('Lo que se ha hecho en tu escuela: 1 acción.');
    fixture.destroy();
    cargar([]);
    expect(texto(el().querySelector('.pn-bloque-vacio'))).toContain('Todavía no hay acciones en este periodo');
  });

  it('los filtros se pliegan en «Filtros»: desde, hasta, acción, módulo, persona y buscar; aplicar manda la consulta de hoy', () => {
    cargar();
    const disparador = el().querySelector<HTMLButtonElement>('#reg-filtros')!;
    expect(disparador.getAttribute('aria-expanded')).toBe('false');
    expect(el().querySelector('#reg-panel-filtros')).toBeNull();
    const form = abrirFiltros();
    expect(disparador.getAttribute('aria-expanded')).toBe('true');
    expect(disparador.getAttribute('aria-controls')).toBe('reg-panel-filtros');
    const etiquetas = Array.from(form.querySelectorAll('label')).map((l) => texto(l));
    expect(etiquetas).toEqual(['Desde', 'Hasta', 'Acción', 'Módulo', 'Persona', 'Buscar en el resumen']);
    const opciones = Array.from(form.querySelectorAll<HTMLOptionElement>('#reg-accion option')).map((o) => texto(o));
    expect(opciones).toEqual(['Todas', 'Altas', 'Cambios', 'Bajas', 'Consultas de salud']);
    expect(Array.from(form.querySelectorAll<HTMLOptionElement>('#reg-modulo option')).map((o) => texto(o))).toEqual([
      'Todos', 'alumnos', 'grupos', 'salud',
    ]);
    poner(form.querySelector<HTMLInputElement>('#reg-desde')!, '2026-10-01');
    poner(form.querySelector<HTMLInputElement>('#reg-hasta')!, '2026-10-23');
    poner(form.querySelector<HTMLSelectElement>('#reg-modulo')!, 'grupos');
    poner(form.querySelector<HTMLSelectElement>('#reg-accion')!, 'CREATE');
    poner(form.querySelector<HTMLInputElement>('#reg-persona')!, ' ana ');
    poner(form.querySelector<HTMLInputElement>('#reg-texto')!, 'grupo');
    boton(form, 'Aplicar filtros').click();
    fixture.detectChanges();
    esperarConsulta(
      `${EVENTOS}?page=1&size=25&desde=2026-10-01&hasta=2026-10-23&resultado=EXITO&accion=CREATE&modulo=grupos&usuario=ana&texto=grupo`
    );
    expect(texto(disparador)).toBe('Filtros 6');
  });

  it('con «Consultas de salud» el módulo queda fijo en salud con su ayuda; restablecer limpia y vuelve a pedir', () => {
    cargar();
    const form = abrirFiltros();
    poner(form.querySelector<HTMLSelectElement>('#reg-accion')!, 'READ');
    const modulo = form.querySelector<HTMLSelectElement>('#reg-modulo')!;
    expect(modulo.disabled).toBeTrue();
    expect(texto(modulo.selectedOptions[0])).toBe('salud');
    expect(modulo.getAttribute('aria-describedby')).toBe('reg-ayuda-salud');
    expect(texto(form.querySelector('#reg-ayuda-salud'))).toBe('Fijo al filtrar consultas de salud');
    boton(form, 'Aplicar filtros').click();
    fixture.detectChanges();
    esperarConsulta(`${EVENTOS}?page=1&size=25&resultado=EXITO&accion=READ&modulo=salud`);
    boton(el().querySelector('#reg-panel-filtros')!, 'Restablecer').click();
    fixture.detectChanges();
    esperarConsulta(`${EVENTOS}?page=1&size=25&resultado=EXITO`);
    expect(el().querySelector<HTMLSelectElement>('#reg-accion')!.value).toBe('TODOS');
  });

  it('el detalle se abre en un panel al lado: qué, módulo, ficha afectada, fecha y persona (nombre o email, como hoy)', async () => {
    cargar();
    const p = await verDetalle();
    expect(texto(p.querySelector('.pn-panel__titulo'))).toBe('Detalle de la acción');
    const campos = Array.from(p.querySelectorAll('.reg__detalle > div')).map((d) => [texto(d.querySelector('dt')), texto(d.querySelector('dd'))]);
    expect(campos).toEqual([
      ['Qué', 'Cambio en la ficha de un alumno'],
      ['Módulo', 'alumnos'],
      ['Ficha afectada', '7'],
      ['Fecha', '23/10/2026 10:42:00'],
      ['Persona', 'Persona Ficticia'],
    ]);
    expect(accion(1).getAttribute('aria-current')).toBe('true');
  });

  it('la persona del detalle sin nombre es el email; sin cuenta, «Sin cuenta»', async () => {
    cargar();
    const persona = (p: HTMLElement) => texto(Array.from(p.querySelectorAll('.reg__detalle > div')).find((d) => texto(d.querySelector('dt')) === 'Persona')!.querySelector('dd'));
    let p = await verDetalle(1, detalleVista({ usuarioNombre: null }));
    expect(persona(p)).toBe('persona@ejemplo.test');
    p = await verDetalle(2, detalleVista({ usuarioId: null, usuarioNombre: null, usuarioEmail: null }));
    expect(persona(p)).toBe('Sin cuenta');
  });

  it('datos técnicos desplegables: método, estado, endpoint, query params y payload tal como llegan; nada más', async () => {
    cargar();
    const p = await verDetalle();
    const tecnicos = p.querySelector<HTMLDetailsElement>('details.reg__tecnicos')!;
    expect(tecnicos.open).toBeFalse();
    expect(texto(tecnicos.querySelector('summary'))).toBe('Datos técnicos');
    const campos = Array.from(tecnicos.querySelectorAll('dl > div')).map((d) => [texto(d.querySelector('dt')), texto(d.querySelector('dd'))]);
    expect(campos).toEqual([['Método', 'PUT'], ['Estado', '200'], ['Endpoint', '/api/alumnos/7']]);
    const bloques = Array.from(tecnicos.querySelectorAll('.reg__json')).map((b) => [texto(b.querySelector('h3')), b.querySelector('pre')!.textContent]);
    expect(bloques).toEqual([
      ['Query params', '{\n  "page": "1"\n}'],
      ['Payload', '{\n  "nombre": "[REDACTADO]",\n  "activo": true\n}'],
    ]);
    const todo = texto(p);
    for (const oculto of ['10.0.0.9', 'NavegadorDePrueba', 'persona@ejemplo.test']) {
      expect(todo).not.toContain(oculto);
    }
  });

  it('sin query params ni payload no hay datos técnicos (como hoy)', async () => {
    cargar();
    const p = await verDetalle(1, detalleVista({ queryParamsJson: null, payloadJson: null }));
    expect(p.querySelector('details.reg__tecnicos')).toBeNull();
  });

  it('abrir y cerrar el detalle no pone nada en la URL', async () => {
    cargar();
    const router = TestBed.inject(Router);
    const url = router.url;
    const push = spyOn(history, 'pushState').and.callThrough();
    const replace = spyOn(history, 'replaceState').and.callThrough();
    const p = await verDetalle();
    expect(router.url).toBe(url);
    boton(p, 'Cerrar').click();
    fixture.detectChanges();
    expect(panel()).toBeNull();
    expect(router.url).toBe(url);
    expect(push).not.toHaveBeenCalled();
    expect(replace).not.toHaveBeenCalled();
  });

  it('si el detalle no se puede leer, lo avisa y no abre el panel', async () => {
    cargar();
    accion(1).click();
    fixture.detectChanges();
    peticiones(`${EVENTOS}/1`)[0].flush(null, { status: 500, statusText: 'Error' });
    await esperar();
    expect(panel()).toBeNull();
    expect(avisos()).toEqual([{ tipo: 'error', texto: 'No se pudo cargar el detalle de la acción.' }]);
  });

  it('«Actualizar ahora» vuelve a pedir la misma página sin cerrar el detalle; cada 30 s, también', async () => {
    cargar(EVENTOS_DE_PRUEBA, 60, 3);
    boton(el().querySelector('.reg__paginacion')!, 'Página 2').click();
    fixture.detectChanges();
    esperarConsulta(`${EVENTOS}?page=2&size=25&resultado=EXITO`, EVENTOS_DE_PRUEBA, 60, 3);
    await verDetalle();
    boton(el(), 'Actualizar ahora').click();
    fixture.detectChanges();
    esperarConsulta(`${EVENTOS}?page=2&size=25&resultado=EXITO`, EVENTOS_DE_PRUEBA, 60, 3);
    expect(panel()).not.toBeNull();
    jasmine.clock().tick(30000);
    fixture.detectChanges();
    esperarConsulta(`${EVENTOS}?page=2&size=25&resultado=EXITO`, EVENTOS_DE_PRUEBA, 60, 3);
    http.verify();
  });

  it('si la lista no se puede leer: bloque de error con «Reintentar»', () => {
    fixture = TestBed.createComponent(RegistroAccionesComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    peticiones(MODULOS)[0].flush([]);
    peticiones(EVENTOS)[0].flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('.pn-bloque-error'))).toContain('No se ha podido cargar el registro de acciones.');
    boton(el(), 'Reintentar').click();
    fixture.detectChanges();
    responderEventos();
    expect(accion(1)).not.toBeNull();
  });

  it('a 375 sin desplazamiento horizontal', async () => {
    cargar();
    const host = el();
    document.body.appendChild(host);
    host.style.display = 'block';
    host.style.width = '375px';
    abrirFiltros();
    const pagina = host.querySelector<HTMLElement>('.reg__pagina')!;
    expect(pagina.scrollWidth).toBeLessThanOrEqual(pagina.clientWidth);
  });
});
