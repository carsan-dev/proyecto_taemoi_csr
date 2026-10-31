import { HttpParams, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { MASCOTA_CARGADORES } from '../mascota/mascota.component';
import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { MOTIVO_CANCELACION_POR_DEFECTO, PreinscripcionService } from '../../features/preinscripcion/preinscripcion.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { GESTION_UNA_SEDE, GESTION_VARIAS_SEDES, RESPONSABLE_VARIAS_SEDES } from '../filtro-sede/filtro-sede-prueba';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { PreinscripcionesPanelComponent } from './preinscripciones.component';

const BASE = `${environment.apiUrl}/preinscripciones`;

/** Un Instant de la API: segundos desde 1970. */
const segundos = (d: Date): number => d.getTime() / 1000;

function solicitud(extra: Record<string, unknown> = {}): any {
  return {
    referencia: 'PRE-1',
    temporada: '2026-2027',
    deporte: 'TAEKWONDO',
    estado: 'PENDIENTE',
    posicionListaEspera: null,
    nombre: 'Ana',
    apellidos: 'García López',
    dni: '12345678Z',
    fechaNacimiento: [2014, 4, 12],
    direccion: 'Calle Mayor 1',
    telefono: '612345678',
    telefono2: null,
    email: 'familia@example.com',
    observaciones: null,
    tieneDiscapacidad: false,
    tutorNombre: 'María García',
    tutorDni: '87654321X',
    grupoOriginal: 'Infantil · Lunes 18:00',
    grupo: 'Infantil · Lunes 18:00',
    turnosSolicitados: [{ id: 11, diaSemana: 'Lunes', horaInicio: '18:00', horaFin: '19:00', grupo: 'Infantil' }],
    creadaEn: segundos(new Date(2026, 9, 20, 18, 0)),
    emailEnviado: true,
    emailIntentos: 1,
    estadoEmailCambioTurnos: 'NO_ENVIADO',
    emailCambioTurnosIntentos: 0,
    turnosModificadosEn: null,
    estadoEmailFinalizacion: 'NO_ENVIADO',
    emailFinalizacionIntentos: 0,
    ...extra,
  };
}

const LISTA = (): any[] => [
  solicitud(),
  solicitud({
    referencia: 'PRE-2',
    nombre: 'Lucía',
    apellidos: 'Pérez',
    dni: null,
    deporte: 'KICKBOXING',
    estado: 'EN_LISTA_ESPERA',
    posicionListaEspera: 3,
    observaciones: 'Solo puede los martes',
    creadaEn: segundos(new Date(2026, 9, 23, 10, 30)),
  }),
  solicitud({ referencia: 'PRE-3', nombre: 'Mario', apellidos: 'Sanz', dni: '22222222J', estado: 'CANCELADA' }),
  solicitud({ referencia: 'PRE-4', nombre: 'Elena', apellidos: 'Ruiz', dni: '33333333P', estado: 'FINALIZADA' }),
];

const CANDIDATA = {
  id: 3,
  nif: '12345678Z',
  nombre: 'Ana',
  apellidos: 'García López',
  fechaNacimiento: [2014, 4, 12],
  direccion: 'Calle Mayor 1',
  telefono: '612345678',
  email: 'familia@example.com',
  tieneDiscapacidad: false,
  responsableLegalNombre: 'María García',
  responsableLegalNif: '87654321X',
  activo: true,
  requiereDatosDeporte: false,
  tipoTarifa: 'INFANTIL',
  cuantiaTarifa: 30,
};

describe('PreinscripcionesPanelComponent (8b-3h, Preinscripciones del panel nuevo, P1 lista con detalle al lado)', () => {
  let fixture: ComponentFixture<PreinscripcionesPanelComponent>;
  let http: HttpTestingController;
  let quitarTokens: () => void;
  let esAdmin: boolean;
  let guardarEnStorage: jasmine.Spy;

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

  function boton(raiz: ParentNode, t: string): HTMLButtonElement {
    const b = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button')).find(
      (x) => (x.getAttribute('aria-label') ?? '').replace(/\s+/g, ' ').trim() === t || texto(x) === t
    );
    if (!b) {
      throw new Error(`Sin botón «${t}»`);
    }
    return b;
  }

  const hayBoton = (raiz: ParentNode, t: string): boolean => {
    try {
      boton(raiz, t);
      return true;
    } catch {
      return false;
    }
  };

  function avisos(): { tipo: string; texto: string }[] {
    return Array.from(document.querySelectorAll<HTMLElement>('.pn-aviso')).map((a) => ({
      tipo: a.classList.contains('pn-aviso--error') ? 'error' : 'otro',
      texto: a.querySelector('.pn-aviso__texto')!.textContent!.replace(/\s+/g, ' ').trim(),
    }));
  }

  const lecturasLista = (): TestRequest[] => http.match((r) => r.method === 'GET' && r.url === BASE);
  const filas = (): HTMLButtonElement[] => Array.from(el().querySelectorAll<HTMLButtonElement>('.preins__fila'));
  const fila = (ref: string): HTMLButtonElement => filas().find((f) => texto(f).includes(ref))!;
  const detalle = (): HTMLElement => el().querySelector<HTMLElement>('.preins__detalle')!;
  /** Los pares etiqueta y valor de «Datos» del detalle. */
  const datos = (): Map<string, string> =>
    new Map(Array.from(detalle().querySelectorAll('.preins__datos dt')).map((dt) => [texto(dt), texto(dt.nextElementSibling)]));

  function responderLista(content: any[] = LISTA(), extra: Record<string, unknown> = {}): TestRequest {
    const [r] = lecturasLista();
    r.flush({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1, first: true, last: true, ...extra });
    fixture.detectChanges();
    return r;
  }

  function crear(): void {
    fixture = TestBed.createComponent(PreinscripcionesPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  }

  function cargar(content?: any[]): void {
    crear();
    responderLista(content);
  }

  /** Abre una fila y atiende sus lecturas: el detalle (solo pendientes) y la firma. */
  function abrir(ref: string, coincidentes: any[] = []): void {
    fila(ref).click();
    fixture.detectChanges();
    const item = LISTA().find((x) => x.referencia === ref) ?? solicitud({ referencia: ref });
    http.match(`${BASE}/${ref}`).forEach((r) => r.flush({ ...item, alumnosCoincidentes: coincidentes }));
    http.match(`${BASE}/${ref}/firma`).forEach((r) => r.flush(new Blob(['png'], { type: 'image/png' })));
    fixture.detectChanges();
  }

  function abrirMas(): HTMLElement {
    boton(detalle(), 'Más acciones').click();
    fixture.detectChanges();
    return el().querySelector<HTMLElement>('.preins__menu')!;
  }

  beforeEach(async () => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2026, 9, 23, 12, 0));
    quitarTokens = ponerTokensDePrueba();
    esAdmin = true;
    guardarEnStorage = spyOn(Storage.prototype, 'setItem').and.callThrough();
    await TestBed.configureTestingModule({
      imports: [PreinscripcionesPanelComponent],
      providers: [
        { provide: MASCOTA_CARGADORES, useValue: {
          explicar: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
          compacta: () => Promise.resolve('<svg viewBox="0 0 320 320"></svg>'),
        } },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => esAdmin } },
        { provide: PermisosSesionService, useValue: { tiene: () => false, obtener: () => of({}) } },
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
    quitarTokens();
    jasmine.clock().uninstall();
  });

  // ------------------------------------------------------------------ lectura y lista

  it('carga: la petición de hoy (GET /preinscripciones con page=0 y size=25), con ERROR_HANDLED_LOCALLY', () => {
    crear();
    const hoy = TestBed.inject(PreinscripcionService);
    let urlDeHoy = '';
    hoy.listar({ temporada: '', deporte: '', estado: '', q: '' }, 0, 25).subscribe();
    const [nueva, deHoy] = lecturasLista();
    urlDeHoy = deHoy.request.urlWithParams;
    expect(nueva.request.urlWithParams).toBe(urlDeHoy);
    expect(nueva.request.urlWithParams).toBe(`${BASE}?page=0&size=25`);
    expect(nueva.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    deHoy.flush({ content: [] });
    nueva.flush({ content: LISTA(), page: 0, totalElements: 4, totalPages: 1 });
    fixture.detectChanges();
    expect(filas().length).toBe(4);
    http.verify();
  });

  it('cabecera: título, pestañas Solicitudes y Plantillas PDF, «Actualizar» y los filtros como hoy', () => {
    cargar();
    expect(texto(el().querySelector('h1'))).toBe('Preinscripciones');
    const pestanas = Array.from(el().querySelectorAll('[role="tab"]')).map((p) => [texto(p), p.getAttribute('aria-selected')]);
    expect(pestanas).toEqual([
      ['Solicitudes', 'true'],
      ['Plantillas PDF', 'false'],
    ]);
    expect(hayBoton(el(), 'Actualizar')).toBeTrue();
    const opciones = (id: string) => Array.from(el().querySelectorAll<HTMLOptionElement>(`#${id} option`)).map((o) => [o.value, texto(o)]);
    expect(opciones('preins-estado')).toEqual([
      ['', 'Todos los estados'],
      ['PENDIENTE', 'Pendiente'],
      ['EN_LISTA_ESPERA', 'En lista de espera'],
      ['FINALIZADA', 'Finalizada'],
      ['CANCELADA', 'Cancelada'],
    ]);
    expect(opciones('preins-deporte')[0]).toEqual(['', 'Todos los deportes']);
    expect(opciones('preins-deporte').map((o) => o[0])).toContain('TAEKWONDO');
    expect(opciones('preins-temporada').slice(0, 3)).toEqual([
      ['', 'Todas las temporadas'],
      ['2026-2027', '2026-2027'],
      ['2025-2026', '2025-2026'],
    ]);
    expect(el().querySelector<HTMLInputElement>('#preins-buscar')!.placeholder).toBe('DNI, referencia o nombre');
    expect(hayBoton(el(), 'Filtrar')).toBeTrue();
  });

  it('fila: nombre, pastilla con el puesto en la lista de espera, referencia y DNI o «Sin DNI», disciplina, llegada y «Con observación»', () => {
    cargar();
    const f = fila('PRE-2');
    expect(texto(f.querySelector('.preins__nombre'))).toBe('Lucía Pérez');
    expect(f.querySelector('.preins__nombre')!.getAttribute('title')).toBe('Lucía Pérez');
    expect(texto(f.querySelector('.preins__pastilla'))).toBe('En lista de espera · puesto 3');
    expect(texto(f.querySelector('.preins__ref'))).toBe('PRE-2');
    expect(texto(f.querySelector('.preins__dni'))).toBe('Sin DNI');
    expect(texto(f.querySelector('.preins__disciplina'))).toBe('Kickboxing');
    expect(texto(f.querySelector('.preins__llegada'))).toBe('Hace 1 h');
    expect(f.querySelector('.preins__llegada')!.getAttribute('title')).toBe('Llegó el 23/10/2026, 10:30');
    expect(texto(f.querySelector('.preins__observacion-marca'))).toBe('Con observación');
    expect(fila('PRE-1').querySelector('.preins__observacion-marca')).toBeNull();
    expect(texto(fila('PRE-1').querySelector('.preins__pastilla'))).toBe('Pendiente');
    // El orden de la API (la más reciente primero, por `creadaEn`): la pantalla no reordena.
    expect(filas().map((x) => texto(x.querySelector('.preins__ref')))).toEqual(['PRE-1', 'PRE-2', 'PRE-3', 'PRE-4']);
  });

  it('paginación bajo la lista: «1–25 de 60» y «Siguiente» pide la página 1 con los mismos filtros', () => {
    crear();
    responderLista(LISTA(), { totalElements: 60, totalPages: 3 });
    expect(texto(el().querySelector('.preins__rango'))).toBe('1–25 de 60 preinscripciones');
    expect(boton(el(), 'Página anterior').disabled).toBeTrue();
    boton(el(), 'Página siguiente').click();
    fixture.detectChanges();
    const [r] = lecturasLista();
    expect(r.request.params.get('page')).toBe('1');
    expect(r.request.params.get('size')).toBe('25');
    r.flush({ content: LISTA(), page: 1, totalElements: 60, totalPages: 3 });
    fixture.detectChanges();
    expect(texto(el().querySelector('.preins__rango'))).toBe('26–50 de 60 preinscripciones');
  });

  it('Filtrar envía los filtros de hoy (temporada, deporte, estado, q) desde la página 0; elegir en un desplegable no lee', () => {
    cargar();
    const elegir = (id: string, v: string) => {
      const s = el().querySelector<HTMLSelectElement>(`#${id}`)!;
      s.value = v;
      s.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    elegir('preins-temporada', '2025-2026');
    elegir('preins-deporte', 'TAEKWONDO');
    elegir('preins-estado', 'EN_LISTA_ESPERA');
    const q = el().querySelector<HTMLInputElement>('#preins-buscar')!;
    q.value = ' 1234 ';
    q.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(lecturasLista().length).toBe(0);
    boton(el(), 'Filtrar').click();
    fixture.detectChanges();
    const [r] = lecturasLista();
    let esperado = new HttpParams().set('page', 0).set('size', 25);
    for (const [k, v] of Object.entries({ temporada: '2025-2026', deporte: 'TAEKWONDO', estado: 'EN_LISTA_ESPERA', q: ' 1234 ' })) {
      esperado = esperado.set(k, v);
    }
    expect(r.request.params.toString()).toBe(esperado.toString());
    r.flush({ content: [], page: 0, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(texto(el().querySelector('pn-bloque-vacio'))).toContain('No hay solicitudes con estos filtros');
  });

  it('Intro en el buscador filtra; «Actualizar» vuelve a leer la misma página', () => {
    cargar();
    const q = el().querySelector<HTMLInputElement>('#preins-buscar')!;
    q.value = 'PRE-2';
    q.dispatchEvent(new Event('input'));
    q.form!.dispatchEvent(new Event('submit', { cancelable: true }));
    fixture.detectChanges();
    const [r] = lecturasLista();
    expect(r.request.params.get('q')).toBe('PRE-2');
    r.flush({ content: LISTA(), page: 0, totalElements: 4, totalPages: 1 });
    fixture.detectChanges();
    boton(el(), 'Actualizar').click();
    fixture.detectChanges();
    expect(lecturasLista().length).toBe(1);
  });

  it('error de la lista: bloque con «Reintentar»; vacío sin filtros con su texto', () => {
    crear();
    lecturasLista()[0].flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('pn-bloque-error'))).toContain('No se pudieron cargar las preinscripciones');
    boton(el(), 'Reintentar').click();
    fixture.detectChanges();
    responderLista([]);
    expect(texto(el().querySelector('pn-bloque-vacio'))).toContain('Todavía no ha llegado ninguna preinscripción');
  });

  it('cargando: esqueleto a los 300 ms', () => {
    crear();
    jasmine.clock().tick(301);
    fixture.detectChanges();
    expect(el().querySelector('pn-esqueleto')).not.toBeNull();
  });

  // ------------------------------------------------------------------ detalle

  it('sin elegir: el detalle invita a elegir una solicitud (no se pide ninguna firma)', () => {
    cargar();
    expect(texto(el().querySelector('.preins__detalle-vacio'))).toContain('Elige una solicitud');
    http.expectNone(() => true);
  });

  it('shows explicar above the unselected request detail', () => {
    cargar();
    const mascot = el().querySelector('.preins__detalle-vacio pn-mascota')!;
    expect(mascot.getAttribute('pose')).toBe('explicar');
    expect(mascot.getAttribute('aria-hidden')).toBe('true');
    expect(mascot.nextElementSibling?.classList).toContain('preins__detalle-vacio-titulo');
  });

  it('al abrir una pendiente: pide el detalle (fichas compatibles) y la firma, como hoy; datos, horario y avisos', () => {
    cargar();
    fila('PRE-1').click();
    fixture.detectChanges();
    expect(fila('PRE-1').getAttribute('aria-current')).toBe('true');
    const det = http.expectOne(`${BASE}/PRE-1`);
    expect(det.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    det.flush({ ...solicitud(), alumnosCoincidentes: [CANDIDATA] });
    const firma = http.expectOne(`${BASE}/PRE-1/firma`);
    expect(firma.request.responseType).toBe('blob');
    expect(firma.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    firma.flush(new Blob(['png'], { type: 'image/png' }));
    fixture.detectChanges();
    const d = detalle();
    expect(texto(d.querySelector('h2'))).toBe('Ana García López');
    const pares = datos();
    expect(pares.get('DNI/NIE')).toBe('12345678Z');
    expect(pares.get('Teléfono')).toBe('612345678');
    expect(pares.get('Teléfono secundario')).toBe('No facilitado');
    expect(pares.get('Discapacidad')).toBe('No');
    expect(pares.get('Deporte')).toBe('Taekwondo');
    expect(pares.get('Temporada')).toBe('2026-2027');
    expect(texto(d.querySelector('.preins__horario'))).toContain('Horario solicitado originalmente');
    expect(texto(d.querySelector('.preins__horario'))).toContain('Asignación vigente');
    expect(texto(d.querySelector('.preins__observacion'))).toContain('Sin observaciones.');
    const avisosCorreo = texto(d.querySelector('.preins__avisos'));
    expect(avisosCorreo).toContain('Email inicial');
    expect(avisosCorreo).toContain('Enviado · 1 intento');
    expect(avisosCorreo).toContain('Aviso de cambio');
    expect(avisosCorreo).toContain('Confirmación final');
    expect(d.querySelector<HTMLImageElement>('.preins__firma img')!.src).toMatch(/^blob:/);
    expect(texto(d.querySelector('.preins__fichas'))).toContain('1 ficha compatible');
  });

  it('observación resaltada y, en lista de espera, su puesto; cancelada, su motivo y el aviso de cancelación', () => {
    cargar();
    abrir('PRE-2');
    expect(detalle().querySelector('.preins__observacion--con')).not.toBeNull();
    expect(texto(detalle().querySelector('.preins__observacion'))).toContain('Solo puede los martes');
    expect(datos().get('Puesto en la lista de espera')).toBe('3');
    // En lista de espera no se buscan fichas compatibles (como hoy): solo la firma.
    http.expectNone(`${BASE}/PRE-2`);
    fixture.componentInstance.seleccionar(
      solicitud({ referencia: 'PRE-9', estado: 'CANCELADA', motivoCancelacion: 'Grupo cerrado', estadoEmailCancelacion: 'ERROR', emailCancelacionIntentos: 2, emailCancelacionUltimoError: 'SMTP caído' })
    );
    http.match(`${BASE}/PRE-9/firma`).forEach((r) => r.flush(new Blob()));
    fixture.detectChanges();
    expect(datos().get('Motivo de cancelación')).toBe('Grupo cerrado');
    const avisosCorreo = texto(detalle().querySelector('.preins__avisos'));
    expect(avisosCorreo).toContain('Aviso de cancelación');
    expect(avisosCorreo).toContain('Con error · 2 intentos');
    expect(avisosCorreo).toContain('SMTP caído');
  });

  it('la firma que no carga enseña su error con «Reintentar», que la vuelve a pedir', () => {
    cargar();
    fila('PRE-2').click();
    fixture.detectChanges();
    http.expectOne(`${BASE}/PRE-2/firma`).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const firma = detalle().querySelector<HTMLElement>('.preins__firma')!;
    expect(texto(firma)).toContain('No se pudo cargar la firma.');
    boton(firma, 'Reintentar').click();
    fixture.detectChanges();
    http.expectOne(`${BASE}/PRE-2/firma`).flush(new Blob(['png']));
    fixture.detectChanges();
    expect(firma.querySelector('img')).not.toBeNull();
  });

  it('las fichas compatibles que no cargan: error con «Reintentar» y «Revisar y finalizar alta» deshabilitado', () => {
    cargar();
    fila('PRE-1').click();
    fixture.detectChanges();
    http.expectOne(`${BASE}/PRE-1`).flush(null, { status: 500, statusText: 'Error' });
    http.expectOne(`${BASE}/PRE-1/firma`).flush(new Blob());
    fixture.detectChanges();
    expect(boton(detalle(), 'Revisar y finalizar alta').disabled).toBeTrue();
    boton(detalle().querySelector('.preins__fichas')!, 'Reintentar').click();
    fixture.detectChanges();
    http.expectOne(`${BASE}/PRE-1`).flush({ ...solicitud(), alumnosCoincidentes: [] });
    fixture.detectChanges();
    expect(boton(detalle(), 'Revisar y finalizar alta').disabled).toBeFalse();
    expect(texto(detalle().querySelector('.preins__fichas'))).toContain('No se ha encontrado una ficha compatible.');
  });

  it('«Descargar solicitud firmada»: GET …/documento-firmado en blob y la descarga de hoy, sin guardarla', () => {
    cargar();
    abrir('PRE-1');
    const crearUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:pdf');
    const revocar = spyOn(URL, 'revokeObjectURL');
    const clic = spyOn(HTMLAnchorElement.prototype, 'click');
    boton(detalle(), 'Descargar solicitud firmada').click();
    const r = http.expectOne(`${BASE}/PRE-1/documento-firmado`);
    expect(r.request.method).toBe('GET');
    expect(r.request.responseType).toBe('blob');
    r.flush(new Blob(['%PDF']));
    expect(crearUrl).toHaveBeenCalled();
    expect(clic).toHaveBeenCalledTimes(1);
    const enlace = clic.calls.mostRecent().object as HTMLAnchorElement;
    expect(enlace.download).toBe('solicitud-original-PRE-1.pdf');
    expect(revocar).toHaveBeenCalledWith('blob:pdf');
  });

  it('móvil: «Volver a la lista» cierra el detalle y devuelve el foco a la fila', () => {
    cargar();
    abrir('PRE-2');
    boton(el(), 'Volver a la lista').click();
    fixture.detectChanges();
    expect(el().querySelector('.preins__detalle-vacio')).not.toBeNull();
    expect(document.activeElement).toBe(fila('PRE-2'));
  });

  it('ningún dato personal en la URL ni en el almacenamiento del navegador', async () => {
    cargar();
    const router = TestBed.inject(Router);
    abrir('PRE-1', [CANDIDATA]);
    const q = el().querySelector<HTMLInputElement>('#preins-buscar')!;
    q.value = '12345678Z';
    q.dispatchEvent(new Event('input'));
    boton(el(), 'Filtrar').click();
    responderLista();
    await esperar();
    for (const dato of ['PRE-1', '12345678Z', 'Ana', 'García', 'familia@example.com', '612345678']) {
      expect(router.url).not.toContain(dato);
      expect(decodeURIComponent(location.href)).not.toContain(dato);
    }
    expect(guardarEnStorage).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------------ acciones

  it('«Reenviar solicitud original»: POST …/reenviar con {} sin confirmación; aviso de éxito; un 409 muestra su mensaje', () => {
    cargar();
    abrir('PRE-1');
    boton(detalle(), 'Reenviar solicitud original').click();
    fixture.detectChanges();
    const r = http.expectOne(`${BASE}/PRE-1/reenviar`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({});
    expect(dialogo()).toBeNull();
    r.flush(null);
    pintar();
    expect(avisos()).toContain({ tipo: 'otro', texto: 'Solicitud original reenviada correctamente.' });
    boton(detalle(), 'Reenviar solicitud original').click();
    http.expectOne(`${BASE}/PRE-1/reenviar`).flush({ mensaje: 'Espera un minuto antes de reenviar.' }, { status: 409, statusText: 'Conflict' });
    pintar();
    expect(avisos()).toContain({ tipo: 'error', texto: 'Espera un minuto antes de reenviar.' });
  });

  it('sin doble envío: con un reenvío en vuelo, otro clic no manda nada', () => {
    cargar();
    abrir('PRE-1');
    boton(detalle(), 'Reenviar solicitud original').click();
    fixture.detectChanges();
    boton(detalle(), 'Reenviando…').click();
    fixture.detectChanges();
    expect(http.match(`${BASE}/PRE-1/reenviar`).length).toBe(1);
  });

  it('«Reenviar aviso de cambio»: solo pendiente con turnos modificados; POST …/reenviar-cambio-turnos con {} y relee la lista', () => {
    const lista = [solicitud({ turnosModificadosEn: segundos(new Date(2026, 9, 21)) }), ...LISTA().slice(1)];
    crear();
    responderLista(lista);
    fila('PRE-1').click();
    http.match(`${BASE}/PRE-1`).forEach((r) => r.flush({ ...lista[0], alumnosCoincidentes: [] }));
    http.match(`${BASE}/PRE-1/firma`).forEach((r) => r.flush(new Blob()));
    fixture.detectChanges();
    boton(detalle().querySelector('.preins__avisos')!, 'Reenviar aviso de cambio').click();
    const r = http.expectOne(`${BASE}/PRE-1/reenviar-cambio-turnos`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({});
    r.flush(null);
    pintar();
    expect(avisos()).toContain({ tipo: 'otro', texto: 'Reenvío del aviso de cambio solicitado.' });
    expect(lecturasLista().length).toBe(1);
  });

  it('sin turnos modificados no hay «Reenviar aviso de cambio»', () => {
    cargar();
    abrir('PRE-1');
    expect(hayBoton(detalle(), 'Reenviar aviso de cambio')).toBeFalse();
  });

  it('«Reenviar confirmación» de una finalizada: POST …/reenviar-finalizacion con {}', () => {
    cargar();
    abrir('PRE-4');
    expect(hayBoton(detalle(), 'Revisar y finalizar alta')).toBeFalse();
    boton(detalle(), 'Reenviar confirmación').click();
    const r = http.expectOne(`${BASE}/PRE-4/reenviar-finalizacion`);
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual({});
    r.flush(null);
    pintar();
    expect(avisos()).toContain({ tipo: 'otro', texto: 'Reenvío de confirmación solicitado.' });
  });

  describe('«Cancelar solicitud» (menú «Más»)', () => {
    async function abrirCancelar(ref = 'PRE-1', opcion = 'Cancelar solicitud'): Promise<HTMLDialogElement> {
      cargar();
      abrir(ref);
      boton(abrirMas(), opcion).click();
      await esperar();
      return dialogo()!;
    }

    it('el diálogo de hoy: título, frase, motivo opcional con su marcador y máximo, nota con el correo y «Cancelar y avisar» / «Volver»', async () => {
      const d = await abrirCancelar();
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('¿Cancelar preinscripción?');
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe('Se cancelará la solicitud PRE-1 y se avisará a la familia por correo.');
      const motivo = d.querySelector<HTMLTextAreaElement>('textarea')!;
      expect(texto(d.querySelector(`label[for="${motivo.id}"]`))).toBe('Motivo (opcional)');
      expect(motivo.placeholder).toBe(MOTIVO_CANCELACION_POR_DEFECTO);
      expect(motivo.maxLength).toBe(500);
      expect(texto(d.querySelector('.pn-dialogo__explicacion'))).toBe(
        'Si lo dejas vacío se enviará el motivo por defecto. El aviso se enviará a familia@example.com.'
      );
      expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Cancelar y avisar');
      expect(texto(d.querySelector('.pn-dialogo__cancelar'))).toBe('Volver');
      expect(document.activeElement).toBe(d.querySelector('.pn-dialogo__cancelar'));
    });

    it('«Volver» no llama a la API', async () => {
      const d = await abrirCancelar();
      (d.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
      await esperar();
      http.expectNone(`${BASE}/PRE-1/cancelar`);
    });

    it('envía el motivo tal cual (sin recortar) y, vacío, {motivo: ""}; después relee', async () => {
      let d = await abrirCancelar();
      const motivo = d.querySelector<HTMLTextAreaElement>('textarea')!;
      motivo.value = '  El grupo se ha cerrado.  ';
      motivo.dispatchEvent(new Event('input'));
      (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
      await esperar();
      const r = http.expectOne(`${BASE}/PRE-1/cancelar`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toEqual({ motivo: '  El grupo se ha cerrado.  ' });
      r.flush(null);
      await esperar();
      expect(avisos()).toContain({ tipo: 'otro', texto: 'Preinscripción cancelada.' });
      responderLista();
      boton(abrirMas(), 'Cancelar solicitud').click();
      await esperar();
      d = dialogo()!;
      (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
      await esperar();
      expect(http.expectOne(`${BASE}/PRE-1/cancelar`).request.body).toEqual({ motivo: '' });
    });

    it('en lista de espera la opción es «Cancelar lista de espera», con la misma llamada; un 409 enseña su mensaje', async () => {
      const d = await abrirCancelar('PRE-2', 'Cancelar lista de espera');
      (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
      await esperar();
      http.expectOne(`${BASE}/PRE-2/cancelar`).flush({ mensaje: 'La solicitud ya está finalizada.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'La solicitud ya está finalizada.' });
    });
  });

  describe('«Eliminar definitivamente» (menú «Más»)', () => {
    async function abrirEliminar(): Promise<HTMLDialogElement> {
      cargar();
      abrir('PRE-3');
      boton(abrirMas(), 'Eliminar definitivamente').click();
      await esperar();
      return dialogo()!;
    }

    it('solo en canceladas y solo para Administración', () => {
      cargar();
      abrir('PRE-1');
      expect(hayBoton(abrirMas(), 'Eliminar definitivamente')).toBeFalse();
      esAdmin = false;
      abrir('PRE-3');
      expect(el().querySelector('.preins__mas')).toBeNull();
    });

    it('confirmación explícita: dice que no se puede deshacer, pide la referencia y el foco empieza en «Volver»', async () => {
      const d = await abrirEliminar();
      expect(d.classList).toContain('pn-dialogo--peligro');
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Eliminar definitivamente');
      expect(texto(d)).toContain('Se eliminarán la solicitud PRE-3, sus datos personales, la firma y el PDF.');
      expect(texto(d)).toContain('Esta acción no se puede deshacer.');
      expect(texto(d.querySelector('label'))).toBe('Escribe la referencia para confirmar');
      expect(d.querySelector<HTMLInputElement>('input[type="text"]')!.placeholder).toBe('PRE-3');
      expect(document.activeElement).toBe(d.querySelector('.pn-dialogo__cancelar'));
      expect(texto(document.activeElement)).toBe('Volver');
    });

    it('sin acción por defecto: Intro en el campo o en el foco inicial no elimina', async () => {
      const d = await abrirEliminar();
      const campo = d.querySelector<HTMLInputElement>('input[type="text"]')!;
      campo.value = 'PRE-3';
      campo.dispatchEvent(new Event('input'));
      campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
      d.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
      await esperar();
      http.expectNone((r) => r.method === 'DELETE');
      expect(d.closest('form')).toBeNull();
    });

    it('la referencia que no coincide no envía nada: «La referencia no coincide.»', async () => {
      const d = await abrirEliminar();
      const campo = d.querySelector<HTMLInputElement>('input[type="text"]')!;
      campo.value = 'PRE-4';
      campo.dispatchEvent(new Event('input'));
      (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
      await esperar();
      expect(texto(d.querySelector('.pn-dialogo__error'))).toBe('La referencia no coincide.');
      http.expectNone((r) => r.method === 'DELETE');
    });

    it('con la referencia (sin mayúsculas ni espacios, como hoy): DELETE /preinscripciones/{ref}, aviso y la fila deja el detalle', async () => {
      const d = await abrirEliminar();
      const campo = d.querySelector<HTMLInputElement>('input[type="text"]')!;
      campo.value = ' pre-3 ';
      campo.dispatchEvent(new Event('input'));
      (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
      await esperar();
      const r = http.expectOne(`${BASE}/PRE-3`);
      expect(r.request.method).toBe('DELETE');
      r.flush(null);
      await esperar();
      expect(avisos()).toContain({ tipo: 'otro', texto: 'Preinscripción eliminada definitivamente.' });
      responderLista(LISTA().filter((x) => x.referencia !== 'PRE-3'));
      expect(el().querySelector('.preins__detalle-vacio')).not.toBeNull();
    });

    it('un error al eliminar enseña el mensaje del servidor', async () => {
      const d = await abrirEliminar();
      const campo = d.querySelector<HTMLInputElement>('input[type="text"]')!;
      campo.value = 'PRE-3';
      campo.dispatchEvent(new Event('input'));
      (d.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
      await esperar();
      http.expectOne(`${BASE}/PRE-3`).flush({ mensaje: 'No se puede eliminar todavía.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'No se puede eliminar todavía.' });
    });
  });

  describe('«Cambiar la asignación»', () => {
    const TURNOS = [
      { id: 11, diaSemana: 'Lunes', horaInicio: '18:00', horaFin: '19:00', grupo: 'Infantil', rangoEdadMin: 5, rangoEdadMax: 12, completo: false },
      { id: 12, diaSemana: 'Lunes', horaInicio: '19:00', horaFin: '20:00', grupo: 'Infantil', rangoEdadMin: 5, rangoEdadMax: 12, completo: false },
      { id: 21, diaSemana: 'Viernes', horaInicio: '20:00', horaFin: '21:00', grupo: 'Mayores', rangoEdadMin: 16, rangoEdadMax: 99, completo: true },
    ];

    function abrirEditor(): HTMLElement {
      cargar();
      abrir('PRE-1');
      boton(detalle(), 'Cambiar la asignación').click();
      fixture.detectChanges();
      const r = http.expectOne(`${environment.apiUrl}/preinscripciones/public/turnos/TAEKWONDO`);
      r.flush(TURNOS);
      fixture.detectChanges();
      return detalle().querySelector<HTMLElement>('.preins__asignacion')!;
    }

    const casilla = (editor: HTMLElement, id: number) => editor.querySelector<HTMLInputElement>(`input[data-turno="${id}"]`)!;

    it('turnos de la disciplina con sus avisos; un turno por día; guarda con PUT …/turnos {turnoIds} y relee', () => {
      const editor = abrirEditor();
      expect(texto(editor)).toContain('Puede ignorar edad y aforo. Solo se admite un turno por día.');
      expect(casilla(editor, 11).checked).toBeTrue();
      expect(texto(casilla(editor, 21).closest('label'))).toContain('Fuera de edad');
      expect(texto(casilla(editor, 21).closest('label'))).toContain('Sin plaza');
      expect(boton(editor, 'Guardar turnos').disabled).toBeTrue();
      casilla(editor, 12).click();
      fixture.detectChanges();
      expect(casilla(editor, 11).checked).toBeFalse();
      boton(editor, 'Guardar turnos').click();
      fixture.detectChanges();
      const r = http.expectOne(`${BASE}/PRE-1/turnos`);
      expect(r.request.method).toBe('PUT');
      expect(r.request.body).toEqual({ turnoIds: [12] });
      r.flush(null);
      pintar();
      expect(avisos()).toContain({ tipo: 'otro', texto: 'Turnos actualizados. El aviso al cliente se ha puesto en cola.' });
      expect(lecturasLista().length).toBe(1);
    });

    it('«Cancelar» cierra el editor sin enviar nada; sin ningún turno no se puede guardar', () => {
      const editor = abrirEditor();
      casilla(editor, 11).click();
      fixture.detectChanges();
      expect(texto(editor)).toContain('Selecciona al menos un turno.');
      expect(boton(editor, 'Guardar turnos').disabled).toBeTrue();
      boton(editor, 'Cancelar').click();
      fixture.detectChanges();
      expect(detalle().querySelector('.preins__asignacion')).toBeNull();
      http.expectNone(`${BASE}/PRE-1/turnos`);
    });

    it('un turno nuevo sin plaza o fuera de edad pide «Forzar asignación de turnos»; «Volver» no envía', async () => {
      const editor = abrirEditor();
      casilla(editor, 21).click();
      fixture.detectChanges();
      boton(editor, 'Guardar turnos').click();
      await esperar();
      const d = dialogo()!;
      expect(texto(d.querySelector('.pn-dialogo__titulo'))).toBe('Forzar asignación de turnos');
      expect(texto(d.querySelector('.pn-dialogo__mensaje'))).toBe(
        'La selección contiene turnos nuevos fuera de la edad recomendada o sin plaza disponible. La solicitud quedará con plaza asignada aunque supere el aforo.'
      );
      expect(texto(d.querySelector('.pn-dialogo__accion'))).toBe('Sí, forzar asignación');
      (d.querySelector('.pn-dialogo__cancelar') as HTMLButtonElement).click();
      await esperar();
      http.expectNone(`${BASE}/PRE-1/turnos`);
      boton(editor, 'Guardar turnos').click();
      await esperar();
      (dialogo()!.querySelector('.pn-dialogo__accion') as HTMLButtonElement).click();
      await esperar();
      expect(http.expectOne(`${BASE}/PRE-1/turnos`).request.body).toEqual({ turnoIds: [11, 21] });
    });

    it('un error al guardar enseña el mensaje del servidor y deja el editor abierto', () => {
      const editor = abrirEditor();
      casilla(editor, 12).click();
      fixture.detectChanges();
      boton(editor, 'Guardar turnos').click();
      http.expectOne(`${BASE}/PRE-1/turnos`).flush({ mensaje: 'El turno ya no existe.' }, { status: 409, statusText: 'Conflict' });
      pintar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'El turno ya no existe.' });
      expect(detalle().querySelector('.preins__asignacion')).not.toBeNull();
    });
  });

  describe('«Revisar y finalizar alta»', () => {
    const panel = (): HTMLElement | null => el().querySelector<HTMLElement>('dialog.pn-panel');

    async function abrirAlta(coincidentes: any[]): Promise<HTMLElement> {
      cargar();
      abrir('PRE-1', coincidentes);
      boton(detalle(), 'Revisar y finalizar alta').click();
      await esperar();
      return panel()!;
    }

    it('una ficha con el mismo DNI: solo «Usar …», sin «Crear una ficha nueva», y el cuerpo de hoy', async () => {
      const p = await abrirAlta([CANDIDATA]);
      expect(texto(p.querySelector('.pn-panel__titulo'))).toBe('Finalizar preinscripción');
      const radios = Array.from(p.querySelectorAll<HTMLInputElement>('input[name="preins-destino"]'));
      expect(radios.map((r) => texto(r.closest('label')))).toEqual([jasmine.stringContaining('Usar Ana García López')]);
      expect(radios[0].checked).toBeTrue();
      expect(texto(p)).toContain('Ya existe una ficha con este DNI/NIE, por lo que debe reutilizarse.');
      boton(p, 'Aceptar y completar alta').click();
      await esperar();
      const r = http.expectOne(`${BASE}/PRE-1/finalizar`);
      expect(r.request.method).toBe('POST');
      expect(r.request.body).toEqual({
        accionAlumno: 'VINCULAR_EXISTENTE',
        alumnoId: 3,
        camposActualizar: [],
        discapacidadHistorica: null,
        decisionEmail: undefined,
        datosDeporte: { tipoTarifa: 'INFANTIL', cuantiaTarifa: 30, rolFamiliar: 'NINGUNO', grupoFamiliar: '', grado: null, fechaGrado: null },
      });
      r.flush(null);
      await esperar();
      expect(avisos()).toContain({ tipo: 'otro', texto: 'Preinscripción finalizada: tarifa, grupos y turnos actualizados.' });
      expect(panel()).toBeNull();
      expect(lecturasLista().length).toBe(1);
    });

    it('sin fichas: crea una ficha nueva con la tarifa y el grado iniciales', async () => {
      const p = await abrirAlta([]);
      const radios = Array.from(p.querySelectorAll<HTMLInputElement>('input[name="preins-destino"]'));
      expect(radios.length).toBe(1);
      expect(texto(radios[0].closest('label'))).toContain('Crear una ficha nueva');
      expect(radios[0].checked).toBeTrue();
      expect(texto(p)).toContain('Se crearán ficha activa, número de expediente y fechas de alta usando los datos recibidos.');
      boton(p, 'Aceptar y completar alta').click();
      await esperar();
      const cuerpo = http.expectOne(`${BASE}/PRE-1/finalizar`).request.body;
      expect(cuerpo.accionAlumno).toBe('CREAR_NUEVO');
      expect(cuerpo.alumnoId).toBeUndefined();
      expect(cuerpo.datosDeporte.tipoTarifa).toBe('INFANTIL');
      expect(cuerpo.datosDeporte.grado).toBe('BLANCO');
      expect(cuerpo.datosDeporte.fechaGrado).toBe('2026-10-23');
    });

    it('otra ficha compatible sin el mismo DNI: hay que elegir (ficha existente o nueva) antes de poder aceptar; correo definitivo de acceso', async () => {
      const otra = { ...CANDIDATA, id: 8, nif: '99999999R', email: 'otro@example.com' };
      const p = await abrirAlta([otra]);
      const aceptar = boton(p, 'Aceptar y completar alta');
      expect(aceptar.disabled).toBeTrue();
      const radios = Array.from(p.querySelectorAll<HTMLInputElement>('input[name="preins-destino"]'));
      expect(radios.map((r) => r.checked)).toEqual([false, false]);
      radios[0].click();
      fixture.detectChanges();
      expect(texto(p)).toContain('Correo definitivo de acceso');
      expect(aceptar.disabled).toBeTrue();
      p.querySelector<HTMLInputElement>('input[value="USAR_PREINSCRIPCION"]')!.click();
      fixture.detectChanges();
      expect(aceptar.disabled).toBeFalse();
      aceptar.click();
      await esperar();
      const cuerpo = http.expectOne(`${BASE}/PRE-1/finalizar`).request.body;
      expect(cuerpo.accionAlumno).toBe('VINCULAR_EXISTENTE');
      expect(cuerpo.alumnoId).toBe(8);
      expect(cuerpo.decisionEmail).toBe('USAR_PREINSCRIPCION');
      expect(cuerpo.camposActualizar).toEqual(['NIF']);
    });

    it('«Volver» cierra sin enviar; un error del servidor deja el panel abierto con su mensaje', async () => {
      let p = await abrirAlta([]);
      boton(p, 'Volver').click();
      await esperar();
      expect(panel()).toBeNull();
      http.expectNone(`${BASE}/PRE-1/finalizar`);
      boton(detalle(), 'Revisar y finalizar alta').click();
      await esperar();
      p = panel()!;
      boton(p, 'Aceptar y completar alta').click();
      await esperar();
      http.expectOne(`${BASE}/PRE-1/finalizar`).flush({ mensaje: 'La ficha está bloqueada.' }, { status: 409, statusText: 'Conflict' });
      await esperar();
      expect(avisos()).toContain({ tipo: 'error', texto: 'La ficha está bloqueada.' });
      expect(panel()).not.toBeNull();
    });
  });

  // ------------------------------------------------------------------ Plantillas PDF

  it('Plantillas PDF: editor del panel nuevo sin volver a leer solicitudes, con ?tab=plantillas', async () => {
    cargar();
    boton(el(), 'Plantillas PDF').click();
    await esperar();
    const hoy = el().querySelector('pn-plantillas-pdf');
    expect(hoy).not.toBeNull();
    const r = http.expectOne(`${BASE}/plantillas/TAEKWONDO`);
    r.flush({
      activa: { version: 4, contenido: { cabecera: 'C', contacto: 'C', titulo: 'T', consentimiento: 'C', normas: ['N'], importes: 'I' }, instrucciones: 'I' },
      historial: [{ id: 4, version: 4, activa: true, creadaEn: 1760000000 }],
    });
    await esperar();
    expect(texto(hoy)).toContain('Versión 4');
    expect(hoy!.querySelector('#plantilla-cabecera')).not.toBeNull();
    expect(hoy!.querySelector('nav.tabs')).toBeNull();
    expect(lecturasLista().length).toBe(0);
    expect(TestBed.inject(Router).url).toBe('/?tab=plantillas');
    expect(el().querySelector('[role="tab"][aria-selected="true"]')!.textContent).toContain('Plantillas PDF');
  });

  it('con cambios sin publicar, volver a Solicitudes pide confirmación del panel', async () => {
    cargar();
    boton(el(), 'Plantillas PDF').click();
    await esperar();
    http.expectOne(`${BASE}/plantillas/TAEKWONDO`).flush({
      activa: { version: 1, contenido: { normas: ['N'] }, instrucciones: '' },
      historial: [],
    });
    await esperar();
    const alerta = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: false, valor: null });
    fixture.componentInstance.plantillas!.borrador.cabecera = 'Cambio';
    boton(el(), 'Solicitudes').click();
    await esperar();
    expect(alerta).toHaveBeenCalled();
    expect(alerta.calls.mostRecent().args[0].titulo).toBe('¿Descartar los cambios?');
    expect(el().querySelector('pn-plantillas-pdf')).not.toBeNull();
  });

  it('salir de la pantalla (canDeactivate) pregunta al editor de plantillas y deja salir desde Solicitudes', async () => {
    cargar();
    expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
    boton(el(), 'Plantillas PDF').click();
    await esperar();
    http.expectOne(`${BASE}/plantillas/TAEKWONDO`).flush({
      activa: { version: 1, contenido: { normas: ['N'] }, instrucciones: '' },
      historial: [],
    });
    await esperar();
    const alerta = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.resolveTo({ confirmado: false, valor: null });
    expect(await fixture.componentInstance.puedeSalir()).toBeTrue();
    expect(alerta).not.toHaveBeenCalled();
    fixture.componentInstance.plantillas!.borrador.cabecera = 'Cambio';
    expect(await fixture.componentInstance.puedeSalir()).toBeFalse();
    expect(alerta.calls.mostRecent().args[0].titulo).toBe('¿Descartar los cambios?');
  });

  // ------------------------------------------------------------------ a 375 px

  it('a 375 px: sin desplazamiento horizontal con un nombre largo, que se recorta con su title', () => {
    const largo = 'Maximiliana-Remedios-Concepción de la Santísima Trinidad Fernández-Villaverde y Ortega';
    crear();
    fixture.nativeElement.style.display = 'block';
    fixture.nativeElement.style.width = '375px';
    document.body.appendChild(fixture.nativeElement);
    responderLista([solicitud({ nombre: largo, apellidos: '' })]);
    const f = filas()[0];
    expect(f.querySelector('.preins__nombre')!.getAttribute('title')).toBe(largo);
    const raiz = el().querySelector<HTMLElement>('.preins')!;
    expect(raiz.scrollWidth).toBeLessThanOrEqual(raiz.clientWidth);
    expect(f.scrollWidth).toBeLessThanOrEqual(f.clientWidth);
    fixture.nativeElement.remove();
  });

  it('responsable de sede: sin pestaña ni carga de Plantillas PDF, ni con ?tab=plantillas', () => {
    const permisos = TestBed.inject(PermisosSesionService) as unknown as { tiene: (c: string) => boolean };
    permisos.tiene = (c: string) => c === 'PANEL_SEDE';
    cargar();
    pintar();
    const raiz = fixture.nativeElement as HTMLElement;
    expect(raiz.querySelector('[role="tablist"]')).toBeNull();
    expect(raiz.textContent).not.toContain('Plantillas PDF');
    expect(fixture.componentInstance.pestana).toBe('solicitudes');
  });

  describe('filtro por sede (9-6, decisión 376)', () => {
    function conPermisos(permisos: PermisosSesion): void {
      (TestBed.inject(PermisosSesionService) as unknown as { obtener: () => unknown }).obtener = () => of(permisos);
    }

    function elegirSede(valor: string): void {
      const select = el().querySelector<HTMLSelectElement>('#preins-sede')!;
      select.value = valor;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    const delListado = () => http.match((r) => r.url === BASE).filter((r) => !r.cancelled);

    it('con una sede no hay selector y la petición es la de hoy', () => {
      conPermisos(GESTION_UNA_SEDE);
      crear();
      expect(el().querySelector('#preins-sede')).toBeNull();
      expect(delListado()[0].request.urlWithParams).toBe(`${BASE}?page=0&size=25`);
    });

    it('el responsable no ve el selector', () => {
      conPermisos(RESPONSABLE_VARIAS_SEDES);
      crear();
      expect(el().querySelector('#preins-sede')).toBeNull();
    });

    it('con dos sedes y gestión: como el resto de la barra, va con «Filtrar», desde la primera página', () => {
      conPermisos(GESTION_VARIAS_SEDES);
      crear();
      delListado()[0].flush({ content: LISTA(), page: 2, totalElements: 80, totalPages: 4 });
      fixture.detectChanges();
      elegirSede('2');
      expect(delListado().length).toBe(0);
      fixture.componentInstance.filtrar();
      fixture.detectChanges();
      const [lista] = delListado();
      expect(lista.request.params.get('sedeId')).toBe('2');
      expect(lista.request.params.get('page')).toBe('0');
      expect(fixture.componentInstance.hayFiltros).toBeTrue();
    });

    it('«Quitar filtros» también quita la sede', () => {
      conPermisos(GESTION_VARIAS_SEDES);
      crear();
      delListado()[0].flush({ content: [], page: 0, totalElements: 0, totalPages: 0 });
      fixture.detectChanges();
      elegirSede('2');
      fixture.componentInstance.quitarFiltros();
      fixture.detectChanges();
      expect(delListado()[0].request.params.has('sedeId')).toBeFalse();
      expect(el().querySelector<HTMLSelectElement>('#preins-sede')!.value).toBe('');
    });
  });
});
