import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { EditarAlumnoComponent } from '../../componentes/endpoints/editar-alumno/editar-alumno.component';
import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { LoadingService } from '../../servicios/generales/loading.service';
import { calcularEdad } from '../../utilities/calcular-edad';
import { PendienteAlumno } from '../alumnos/pendientes-alumno.service';
import { FichaAlumnoComponent } from './ficha-alumno.component';

const API = environment.apiUrl;
const NBSP = '\u00a0';
const ANIO = new Date().getFullYear();

// ------------------------------------------------------------------ datos ficticios

const DEPORTES = () => [
  {
    id: 70, deporte: 'TAEKWONDO', grado: 'BLANCO', fechaGrado: '2023-03-22', activo: true, principal: true,
    aptoParaExamen: false, tieneLicencia: true, numeroLicencia: 1234, fechaLicencia: `${ANIO}-02-01`,
    tipoTarifa: 'INFANTIL', cuantiaTarifa: 28, fechaAlta: '2022-10-08', fechaAltaInicial: '2022-10-08',
    antiguedad: '4 años', competidor: false, categoria: 'Infantil', fechaBaja: null,
  },
  {
    id: 71, deporte: 'KICKBOXING', grado: 'NARANJA', fechaGrado: '2023-05-27', activo: true, principal: false,
    aptoParaExamen: true, tieneLicencia: false, fechaLicencia: null, tipoTarifa: 'KICKBOXING', cuantiaTarifa: 30,
    fechaAlta: new Date(2022, 9, 8).getTime(), fechaAltaInicial: new Date(2022, 9, 8).getTime(), antiguedad: '4 años', competidor: false, fechaBaja: null,
  },
  {
    id: 72, deporte: 'PILATES', grado: null, fechaGrado: null, activo: false, principal: false, aptoParaExamen: false,
    tieneLicencia: false, tipoTarifa: 'PILATES', cuantiaTarifa: 30, fechaAlta: '2021-01-01', fechaAltaInicial: '2021-01-01',
    antiguedad: '5 años', competidor: false, fechaBaja: '2024-06-30',
  },
];

const ALUMNO = () => ({
  // Como la API: fechas del alumno en milisegundos y sin alta propia (el carnet toma la más antigua de sus disciplinas).
  id: 7, nombre: 'Rubén', apellidos: 'Prueba Ficticia', numeroExpediente: 501, fechaNacimiento: new Date(2018, 2, 5).getTime(),
  fechaAlta: null, fechaAltaInicial: null, fechaBaja: null, activo: true, email: 'ruben@ejemplo.test',
  telefono: 600000000, telefono2: 611111111, nif: '12345678Z', direccion: 'Calle Inventada 1', autorizacionWeb: true,
  tieneDiscapacidad: false, observaciones: 'Nota inventada', responsableLegalNombre: null, responsableLegalNif: null,
  fotoAlumno: null, tipoTarifa: 'INFANTIL', cuantiaTarifa: 28, rolFamiliar: null, grupoFamiliar: null,
  deportes: DEPORTES(),
});

// El servidor los da del más antiguo al más reciente; la ficha de hoy los invierte (lo más reciente primero).
const PRODUCTOS = () => [
  { id: 801, concepto: 'MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO', precio: 30, cantidad: 1, pagado: false, fechaAsignacion: '2026-09-01T12:00:00' },
  { id: 802, concepto: 'MENSUALIDAD OCTUBRE 2026 - TAEKWONDO', precio: 30, cantidad: 1, pagado: false, fechaAsignacion: '2026-10-01T12:00:00' },
  { id: 803, concepto: 'MENSUALIDAD AGOSTO 2026 - TAEKWONDO', precio: 30, cantidad: 1, pagado: true, fechaPago: '2026-08-03T12:00:00', fechaAsignacion: '2026-08-01T12:00:00' },
  { id: 804, concepto: 'MENSUALIDAD JULIO 2026 - TAEKWONDO', precio: 30, cantidad: 1, pagado: true, fechaPago: '2026-07-02T12:00:00', fechaAsignacion: '2026-07-01T12:00:00' },
  { id: 805, concepto: 'DERECHO A EXAMEN BLANCO-AMARILLO', precio: 25, cantidad: 1, pagado: true, fechaPago: '2026-06-12T12:00:00', fechaAsignacion: '2026-06-10T12:00:00' },
  { id: 806, concepto: 'MENSUALIDAD JUNIO 2026 - TAEKWONDO', precio: 30, cantidad: 1, pagado: true, fechaPago: '2026-06-02T12:00:00', fechaAsignacion: '2026-06-01T12:00:00' },
].reverse();

const DOCUMENTOS = () =>
  Array.from({ length: 6 }, (_, i) => ({ id: 900 + i, nombre: `documento-ficticio-${i + 1}.pdf`, tipo: 'application/pdf' }));

const GRUPOS = () => [
  { id: 1, nombre: 'Taekwondo Lunes y Miércoles', deporte: 'TAEKWONDO' },
  { id: 2, nombre: 'Taekwondo Martes y Jueves', deporte: 'TAEKWONDO' },
  { id: 9, nombre: 'Kickboxing Lunes y Miércoles', deporte: 'KICKBOXING' },
];
const TURNOS = () => [
  { id: 1, diaSemana: 'Lunes', horaInicio: '17:00', horaFin: '18:00', grupoId: 1 },
  { id: 10, diaSemana: 'Miércoles', horaInicio: '17:00', horaFin: '18:00', grupoId: 1 },
  { id: 6, diaSemana: 'Martes', horaInicio: '17:00', horaFin: '18:00', grupoId: 2 },
  { id: 4, diaSemana: 'Lunes', horaInicio: '20:15', horaFin: '21:15', grupoId: 9 },
];

const TRES_CARGOS: PendienteAlumno = {
  alumnoId: 7,
  importe: 85,
  cargos: 3,
  detalle: [
    { productoAlumnoId: 801, concepto: 'MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO', importe: 30, vencidoDesde: '2026-09-06' },
    { productoAlumnoId: 802, concepto: 'MENSUALIDAD OCTUBRE 2026 - TAEKWONDO', importe: 30, vencidoDesde: '2026-10-06' },
    { productoAlumnoId: 807, concepto: 'MENSUALIDAD OCTUBRE 2026 - KICKBOXING', importe: 25, vencidoDesde: '2026-10-06' },
  ],
};
const UN_CARGO: PendienteAlumno = { ...TRES_CARGOS, importe: 30, cargos: 1, detalle: [TRES_CARGOS.detalle[0]] };

interface Datos {
  alumno: any;
  deportes: any[];
  pendientes: PendienteAlumno[];
  ids: number[];
}

function datosPorDefecto(): Datos {
  return { alumno: ALUMNO(), deportes: DEPORTES(), pendientes: [TRES_CARGOS], ids: [3, 7, 9] };
}

/** Respuesta a cada GET de la carga de la ficha; las escrituras responden `{}`. */
function respuesta(req: TestRequest, datos: Datos): unknown {
  const url = req.request.url.replace(API, '');
  if (req.request.method !== 'GET') {
    return {};
  }
  if (url === '/alumnos/ids') return datos.ids;
  if (/^\/alumnos\/\d+$/.test(url)) return { ...datos.alumno, id: Number(url.split('/')[2]) };
  if (/^\/alumnos\/\d+\/documentos$/.test(url)) return DOCUMENTOS();
  if (/^\/productos-alumno\/alumno\/\d+$/.test(url)) return PRODUCTOS();
  if (/^\/convocatorias\/alumnos\/\d+$/.test(url)) return [];
  if (url === '/grupos/resumen') return GRUPOS();
  if (url === '/turnos/dto') return TURNOS();
  if (/^\/alumnos\/\d+\/deportes$/.test(url)) return datos.deportes;
  if (/^\/alumnos\/\d+\/grupos$/.test(url)) return [GRUPOS()[0], GRUPOS()[2]];
  if (/^\/alumnos\/\d+\/turnos$/.test(url)) return [TURNOS()[0], TURNOS()[3]];
  if (url === '/tesoreria/pendientes-por-alumno') return datos.pendientes;
  if (url === '/convocatorias') return [{ id: 60, fechaConvocatoria: '2099-12-10', deporte: req.request.params.get('deporte') }];
  if (/\/descargar|\/documentos\/\d+/.test(url)) return new Blob(['x']);
  return [];
}

// ------------------------------------------------------------------ registro de lo que se hace (para comparar con hoy)

interface Escritura {
  metodo: string;
  url: string;
  cuerpo: unknown;
}

function serializar(cuerpo: unknown): unknown {
  if (cuerpo instanceof FormData) {
    const salida: Record<string, unknown> = {};
    cuerpo.forEach((valor, clave) => {
      if (valor instanceof File) {
        salida[clave] = { nombre: valor.name, tipo: valor.type, bytes: valor.size };
      } else {
        try {
          salida[clave] = JSON.parse(String(valor));
        } catch {
          salida[clave] = valor;
        }
      }
    });
    return salida;
  }
  if (cuerpo && typeof cuerpo === 'object' && !Array.isArray(cuerpo)) {
    // La hora de «ahora» (p. ej. fechaPago al marcar pagado) cambia entre las dos fichas: se compara que sea una fecha.
    return Object.fromEntries(Object.entries(cuerpo).map(([k, v]) => [k, v instanceof Date ? '<fecha de ahora>' : v]));
  }
  return cuerpo ?? null;
}

/** Opciones de un Swal sin funciones (callbacks) ni espacios de maquetación, para comparar textos y botones. */
function opcionesSwal(opciones: any): unknown {
  return JSON.parse(
    JSON.stringify(opciones ?? null, (_clave, valor) => {
      if (typeof valor === 'function') return undefined;
      if (valor instanceof Map) return Array.from(valor.entries());
      if (typeof valor === 'string') return valor.replace(/\s+/g, ' ').trim();
      return valor;
    })
  );
}

@Component({ standalone: true, template: '<p class="otra">otra</p>' })
class OtraPantalla {}

interface Montaje<C> {
  harness: RouterTestingHarness;
  comp: C;
  http: HttpTestingController;
  el: () => HTMLElement;
  escrituras: Escritura[];
  swal: unknown[];
  /** Resultados que devolverá cada `Swal.fire` en orden; después, cancelado. */
  resultados: any[];
  datos: Datos;
  /** Peticiones que no se responden solas (para probar respuestas que llegan tarde). */
  retener: ((req: TestRequest) => boolean) | null;
  /** Las retenidas, para responderlas a mano. */
  retenidas: TestRequest[];
  asentar: () => Promise<void>;
}

let fire: jasmine.Spy;
let actual: Montaje<any> | null = null;

function configurar(): void {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([
        { path: 'alumnosEditar/:id', loadComponent: () => Promise.resolve(componenteRuta) },
        { path: '**', component: OtraPantalla },
      ]),
      proveerCatalogoTesting(CATALOGO_TAEMOI),
      { provide: LoadingService, useValue: jasmine.createSpyObj('LoadingService', ['show', 'hide']) },
    ],
  });
}

let componenteRuta: Type<unknown> = FichaAlumnoComponent;

async function montar<C>(tipo: Type<C>, url = '/alumnosEditar/7', datos: Datos = datosPorDefecto()): Promise<Montaje<C>> {
  componenteRuta = tipo;
  configurar();
  const harness = await RouterTestingHarness.create();
  const http = TestBed.inject(HttpTestingController);
  const m: Montaje<C> = {
    harness,
    comp: null as unknown as C,
    http,
    el: () => harness.routeNativeElement as HTMLElement,
    escrituras: [],
    swal: [],
    resultados: [],
    datos,
    retener: null,
    retenidas: [],
    asentar: async () => {
      for (let vuelta = 0; vuelta < 12; vuelta++) {
        await new Promise((r) => setTimeout(r));
        for (const req of http.match(() => true)) {
          if (req.cancelled) continue;
          if (m.retener?.(req)) {
            m.retenidas.push(req);
            continue;
          }
          if (req.request.method !== 'GET') {
            m.escrituras.push({ metodo: req.request.method, url: req.request.urlWithParams.replace(API, ''), cuerpo: serializar(req.request.body) });
          }
          req.flush(respuesta(req, m.datos) as any);
        }
        harness.detectChanges();
      }
    },
  };
  actual = m;
  const navegacion = harness.navigateByUrl(url);
  await m.asentar();
  m.comp = (await navegacion) as C;
  await m.asentar();
  m.escrituras.length = 0;
  m.swal.length = 0;
  return m;
}

function texto(raiz: ParentNode, selector: string): string {
  // Sin tocar el espacio duro de los importes (es-ES: «85,00 €»).
  return (raiz.querySelector(selector)?.textContent ?? '').replace(/[ \t\r\n]+/g, ' ').trim();
}

function boton(raiz: ParentNode, etiqueta: string): HTMLButtonElement {
  const botones = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button, [role="menuitem"]'));
  const encontrado = botones.find(
    (b) => (b.getAttribute('aria-label') ?? '').trim() === etiqueta || b.textContent!.replace(/\s+/g, ' ').trim() === etiqueta
  );
  if (!encontrado) {
    throw new Error(`No hay botón «${etiqueta}»: ${botones.map((b) => b.textContent!.trim()).join(' | ')}`);
  }
  return encontrado;
}

function bloque(m: Montaje<any>, deporte: string): HTMLElement {
  const b = m.el().querySelector<HTMLElement>(`[data-deporte="${deporte}"]`);
  if (!b) throw new Error(`Sin bloque de ${deporte}`);
  return b;
}

async function pestana(m: Montaje<any>, nombre: string): Promise<void> {
  const tabs = Array.from(m.el().querySelectorAll<HTMLElement>('[role="tab"]'));
  const tab = tabs.find((t) => t.querySelector('.ficha__pestana-texto')?.textContent?.trim() === nombre);
  if (!tab) throw new Error(`No hay pestaña «${nombre}»`);
  tab.click();
  await m.asentar();
}

/**
 * Hace lo mismo en la ficha de hoy (llamando al método que enlaza su plantilla) y en la nueva (pulsando en su
 * plantilla) con las mismas respuestas del usuario, y devuelve lo enviado y los diálogos de cada una.
 */
async function comparar(
  hoy: (m: Montaje<EditarAlumnoComponent>) => void | Promise<void>,
  nueva: (m: Montaje<FichaAlumnoComponent>) => void | Promise<void>,
  resultados: any[] = [],
  datos: () => Datos = datosPorDefecto
): Promise<{ hoy: { escrituras: Escritura[]; swal: unknown[] }; nueva: { escrituras: Escritura[]; swal: unknown[] } }> {
  const a = await montar(EditarAlumnoComponent, '/alumnosEditar/7', datos());
  a.resultados = [...resultados];
  await hoy(a);
  await a.asentar();
  const deHoy = { escrituras: [...a.escrituras], swal: [...a.swal] };
  a.harness.fixture.destroy();
  const b = await montar(FichaAlumnoComponent, '/alumnosEditar/7', datos());
  b.resultados = [...resultados];
  await nueva(b);
  await b.asentar();
  return { hoy: deHoy, nueva: { escrituras: [...b.escrituras], swal: [...b.swal] } };
}

describe('FichaAlumnoComponent (8b-3b, ficha F1 «Carnet»)', () => {
  beforeEach(() => {
    fire = spyOn(Swal, 'fire').and.callFake(((opciones: any) => {
      actual?.swal.push(opcionesSwal(opciones));
      const r = actual?.resultados.shift() ?? { isConfirmed: false, isDenied: false, isDismissed: true };
      return Promise.resolve(r);
    }) as any);
  });

  afterEach(() => {
    actual?.harness.fixture.destroy();
    actual = null;
    document.querySelectorAll('.swal2-container').forEach((n) => n.remove());
  });

  // ---------------------------------------------------------------- carnet

  describe('carnet', () => {
    it('nombre en dos líneas como título, expediente, edad, alta y estado con los ayudantes es-ES', async () => {
      const m = await montar(FichaAlumnoComponent);
      const h1 = m.el().querySelector('h1.ficha__nombre')!;
      expect(texto(h1, '.ficha__nombre-nombre')).toBe('Rubén');
      expect(texto(h1, '.ficha__apellidos')).toBe('Prueba Ficticia');
      expect(texto(m.el(), '[data-dato="expediente"] dd')).toBe('501');
      const edad = calcularEdad(new Date(2018, 2, 5).getTime() as any);
      expect(texto(m.el(), '[data-dato="edad"] dd')).toBe(`${edad} ${edad === 1 ? 'año' : 'años'}`);
      // Sin alta propia: la más antigua de sus disciplinas, también las de baja (Pilates, 01/01/2021).
      expect(texto(m.el(), '[data-dato="alta"] dd')).toBe('01/01/2021');
      expect(texto(m.el(), '[data-dato="estado"] dd')).toBe('Activo');
      // El subtítulo genérico de hoy se quita.
      expect(m.el().textContent).not.toContain('Visualiza y gestiona');
    });

    it('una línea por disciplina activa (la principal primero) con su cinturón, «principal», grado y tiempo', async () => {
      const m = await montar(FichaAlumnoComponent);
      const lineas = Array.from(m.el().querySelectorAll('.ficha__disciplina'));
      expect(lineas.length).toBe(2);
      expect(lineas[0].textContent).toContain('Taekwondo');
      expect(lineas[0].querySelector('.ficha__principal')?.textContent?.trim()).toBe('principal');
      expect(texto(lineas[0], '.ficha__grado-linea')).toMatch(/^BLANCO, \d+ años?( y \d+ meses?)?$/);
      expect(lineas[1].textContent).toContain('Kickboxing');
      expect(lineas[1].querySelector('.ficha__principal')).toBeNull();
      expect(texto(lineas[1], '.ficha__grado-linea')).toMatch(/^NARANJA, /);
    });

    it('franja: un tramo por disciplina con grado y cada cinturón con el borde cinto-borde, que llega a 3:1', async () => {
      const m = await montar(FichaAlumnoComponent);
      // Tokens de DESIGN.md, como los declara panel-nuevo.scss (que el test no carga).
      const tokens = document.createElement('style');
      tokens.textContent = '.pn { --pn-cinto-borde: #7a8290; --pn-superficie: #ffffff; }';
      document.head.appendChild(tokens);
      document.body.appendChild(m.el());
      try {
        const tramos = Array.from(m.el().querySelectorAll<HTMLElement>('.ficha__franja-tramo'));
        expect(tramos.length).toBe(2);
        expect(getComputedStyle(tramos[0]).backgroundColor).toBe('rgb(255, 255, 255)');
        const carnet = m.el().querySelector<HTMLElement>('.ficha__carnet')!;
        const fondo = getComputedStyle(carnet).backgroundColor;
        for (const muestra of [...tramos, ...Array.from(m.el().querySelectorAll<HTMLElement>('.ficha__cinto'))]) {
          const s = getComputedStyle(muestra);
          expect(s.borderTopStyle).withContext('borde visible').toBe('solid');
          expect(parseFloat(s.borderTopWidth)).toBeGreaterThanOrEqual(1);
          expect(s.borderTopColor).toBe('rgb(122, 130, 144)');
          expect(contrasteRgb(s.borderTopColor, fondo)).withContext('borde frente al carnet').toBeGreaterThanOrEqual(3);
        }
      } finally {
        tokens.remove();
        m.el().remove();
      }
    });

    it('pendiente: importe vencido, cargos y el más antiguo; «Cobrar» con varios abre el panel de cada cargo', async () => {
      const m = await montar(FichaAlumnoComponent);
      expect(texto(m.el(), '.ficha__importe')).toBe(`85,00${NBSP}€`);
      expect(texto(m.el(), '.ficha__resumen-cargos')).toBe('3 cargos, el más antiguo vencido desde 06/09/2026');
      const cobrar = m.el().querySelector<HTMLButtonElement>('.ficha__cobrar')!;
      expect(cobrar.getAttribute('aria-haspopup')).toBe('dialog');
      document.body.appendChild(m.el());
      cobrar.focus();
      cobrar.click();
      await m.asentar();
      expect(fire).not.toHaveBeenCalled();
      const panel = m.el().querySelector<HTMLElement>('.ficha__cargos')!;
      expect(panel.getAttribute('role')).toBe('dialog');
      expect(panel.getAttribute('aria-modal')).toBe('true');
      expect(cobrar.getAttribute('aria-expanded')).toBe('true');
      const cargos = panel.querySelectorAll('.ficha__cargo');
      expect(cargos.length).toBe(3);
      expect(cargos[0].textContent).toContain('Vencido desde el 06/09/2026');
      expect(cargos[0].textContent).toContain(`30,00${NBSP}€`);
      expect(document.activeElement).toBe(cargos[0].querySelector('button'));
      panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await m.asentar();
      expect(m.el().querySelector('.ficha__cargos')).toBeNull();
      expect(document.activeElement).toBe(cobrar);
      m.el().remove();
    });

    it('«Cobrar» de un cargo del panel: el diálogo «Confirmar cobro» y el PUT de la tesorería; refresca el pendiente y los pagos', async () => {
      const m = await montar(FichaAlumnoComponent);
      m.el().querySelector<HTMLButtonElement>('.ficha__cobrar')!.click();
      await m.asentar();
      m.resultados = [{ isConfirmed: true, value: 'BIZUM' }];
      const peticiones: string[] = [];
      const espia = spyOn(m.http, 'match').and.callThrough();
      m.el().querySelectorAll<HTMLButtonElement>('.ficha__cargo button')[1].click();
      expect(m.swal[0]).toEqual(jasmine.objectContaining({ title: 'Confirmar cobro', text: 'Marcar como pagado "MENSUALIDAD OCTUBRE 2026 - TAEKWONDO"?', inputLabel: 'Método (opcional)' }));
      m.datos.pendientes = [{ ...TRES_CARGOS, importe: 55, cargos: 2, detalle: [TRES_CARGOS.detalle[0], TRES_CARGOS.detalle[2]] }];
      await m.asentar();
      expect(m.escrituras).toEqual([{ metodo: 'PUT', url: '/productos-alumno/802', cuerpo: { pagado: true, metodoPago: 'BIZUM' } }]);
      espia.calls.all().forEach((c) => peticiones.push(String(c.args[0])));
      expect(texto(m.el(), '.ficha__importe')).toBe(`55,00${NBSP}€`);
      expect(m.el().querySelectorAll('.ficha__cargos .ficha__cargo').length).toBe(2);
    });

    it('«Cobrar» con un cargo: el diálogo de hoy sin panel; cancelado no envía nada y sin método no envía metodoPago', async () => {
      const datos = { ...datosPorDefecto(), pendientes: [UN_CARGO] };
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7', datos);
      const cobrar = m.el().querySelector<HTMLButtonElement>('.ficha__cobrar')!;
      expect(cobrar.getAttribute('aria-haspopup')).toBeNull();
      expect(texto(m.el(), '.ficha__resumen-cargos')).toBe('1 cargo, vencido desde 06/09/2026');
      m.resultados = [{ isConfirmed: false }];
      cobrar.click();
      await m.asentar();
      expect(m.el().querySelector('.ficha__cargos')).toBeNull();
      expect(m.escrituras).toEqual([]);
      m.resultados = [{ isConfirmed: true, value: '' }];
      m.datos.pendientes = [];
      cobrar.click();
      await m.asentar();
      expect(m.escrituras).toEqual([{ metodo: 'PUT', url: '/productos-alumno/801', cuerpo: { pagado: true } }]);
      expect(texto(m.el(), '.ficha__al-dia')).toBe('Al día');
    });

    it('sin nada vencido: «Al día» y sin «Cobrar»; la pestaña Pagos sin insignia', async () => {
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7', { ...datosPorDefecto(), pendientes: [] });
      expect(texto(m.el(), '.ficha__al-dia')).toBe('Al día');
      expect(m.el().querySelector('.ficha__cobrar')).toBeNull();
      expect(m.el().querySelector('[role="tab"] .ficha__insignia')).toBeNull();
    });

    it('licencia federativa de la principal con su pastilla, junto al carnet', async () => {
      const m = await montar(FichaAlumnoComponent);
      expect(texto(m.el(), '.ficha__licencia .ficha__pastilla')).toBe('Con licencia');
      const sin = datosPorDefecto();
      sin.deportes[0].tieneLicencia = false;
      sin.alumno.deportes = sin.deportes;
      const m2 = await montar(FichaAlumnoComponent, '/alumnosEditar/7', sin);
      expect(texto(m2.el(), '.ficha__licencia .ficha__pastilla')).toBe('Sin licencia');
    });

    it('foto: se amplía al pulsar con el diálogo de hoy; «Cambiar foto» abre el selector y «Quitar» va desactivado sin foto', async () => {
      const m = await montar(FichaAlumnoComponent);
      const img = m.el().querySelector<HTMLImageElement>('.ficha__foto img')!;
      expect(img.getAttribute('width')).toBe('240');
      expect(img.getAttribute('height')).toBe('300');
      const abrir = spyOn(m.comp, 'abrirModal');
      m.el().querySelector<HTMLButtonElement>('.ficha__foto')!.click();
      expect(abrir).toHaveBeenCalled();
      expect(m.el().querySelector('dialog#imageModal')).withContext('diálogo de la foto de hoy').not.toBeNull();
      const selector = m.el().querySelector<HTMLInputElement>('input[type="file"][accept="image/*"]')!;
      const clic = spyOn(selector, 'click');
      boton(m.el(), 'Cambiar foto').click();
      expect(clic).toHaveBeenCalled();
      expect(boton(m.el(), 'Quitar').disabled).toBeTrue();
    });
  });

  // ---------------------------------------------------------------- barra

  describe('barra', () => {
    it('«Alumnos» lleva al listado; «1 de N» con anterior y siguiente; «Incluir inactivos» pide los ids con inactivos', async () => {
      const m = await montar(FichaAlumnoComponent);
      expect(m.el().querySelector('a.ficha__volver')!.getAttribute('href')).toBe('/alumnosListar');
      expect(texto(m.el(), '.ficha__posicion')).toBe('2 de 3');
      m.el().querySelector<HTMLButtonElement>('.ficha__siguiente')!.click();
      await m.asentar();
      expect(TestBed.inject(Router).url).toBe('/alumnosEditar/9');
      m.el().querySelector<HTMLButtonElement>('.ficha__anterior')!.click();
      await m.asentar();
      expect(TestBed.inject(Router).url).toBe('/alumnosEditar/7');
      const casilla = m.el().querySelector<HTMLInputElement>('#ficha-inactivos')!;
      expect(casilla.checked).toBeFalse();
      const ids: string[] = [];
      const original = m.http.match.bind(m.http);
      spyOn(m.http, 'match').and.callFake(((f: any) => {
        const lista = original(f);
        lista.filter((r) => r.request.url.endsWith('/alumnos/ids')).forEach((r) => ids.push(r.request.urlWithParams));
        return lista;
      }) as any);
      casilla.click();
      await m.asentar();
      expect(ids.some((u) => u.includes('incluirInactivos=true'))).toBeTrue();
    });

    it('números grandes con miles: «1.044 de 1.200»', async () => {
      const datos = { ...datosPorDefecto(), ids: Array.from({ length: 1200 }, (_, i) => (i === 1043 ? 7 : 10000 + i)) };
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7', datos);
      expect(texto(m.el(), '.ficha__posicion')).toBe('1.044 de 1.200');
    });
  });

  // ---------------------------------------------------------------- pestañas

  describe('pestañas', () => {
    it('tablist con Deportes, Pagos (con insignia de los cargos pendientes), Datos y contacto, Documentos y notas', async () => {
      const m = await montar(FichaAlumnoComponent);
      const lista = m.el().querySelector('[role="tablist"]')!;
      const tabs = Array.from(lista.querySelectorAll<HTMLElement>('[role="tab"]'));
      expect(tabs.map((t) => t.querySelector('.ficha__pestana-texto')!.textContent!.trim())).toEqual([
        'Deportes',
        'Pagos',
        'Datos y contacto',
        'Documentos y notas',
      ]);
      expect(texto(tabs[1], '.ficha__insignia')).toBe('3');
      expect(tabs[1].getAttribute('aria-label')).toBe('Pagos, 3 cargos pendientes');
      expect(tabs[0].getAttribute('aria-selected')).toBe('true');
      expect(tabs[0].tabIndex).toBe(0);
      expect(tabs[1].tabIndex).toBe(-1);
      const panel = m.el().querySelector<HTMLElement>('[role="tabpanel"]')!;
      expect(panel.id).toBe(tabs[0].getAttribute('aria-controls')!);
      expect(panel.getAttribute('aria-labelledby')).toBe(tabs[0].id);
    });

    it('flechas, Inicio y Fin mueven la pestaña y el foco; la pestaña queda en la dirección', async () => {
      const m = await montar(FichaAlumnoComponent);
      document.body.appendChild(m.el());
      const router = TestBed.inject(Router);
      const tabs = () => Array.from(m.el().querySelectorAll<HTMLElement>('[role="tab"]'));
      tabs()[0].focus();
      tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      await m.asentar();
      expect(tabs()[1].getAttribute('aria-selected')).toBe('true');
      expect(document.activeElement).toBe(tabs()[1]);
      expect(router.url).toBe('/alumnosEditar/7?pestana=pagos');
      tabs()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
      await m.asentar();
      expect(document.activeElement).toBe(tabs()[3]);
      expect(router.url).toBe('/alumnosEditar/7?pestana=documentos');
      tabs()[3].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
      await m.asentar();
      expect(document.activeElement).toBe(tabs()[0]);
      expect(router.url).toBe('/alumnosEditar/7');
      tabs()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
      await m.asentar();
      expect(document.activeElement).toBe(tabs()[3]);
      tabs()[3].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
      await m.asentar();
      expect(document.activeElement).toBe(tabs()[0]);
      m.el().remove();
    });

    it('se vuelve a la pestaña de la dirección y se conserva al pasar al alumno siguiente', async () => {
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7?pestana=pagos');
      expect(m.el().querySelector('[role="tab"][aria-selected="true"]')!.id).toBe('ficha-pestana-pagos');
      expect(m.el().querySelector('[role="tabpanel"] app-ficha-pagos')).not.toBeNull();
      m.el().querySelector<HTMLButtonElement>('.ficha__siguiente')!.click();
      await m.asentar();
      expect(TestBed.inject(Router).url).toBe('/alumnosEditar/9?pestana=pagos');
      expect(m.el().querySelector('[role="tab"][aria-selected="true"]')!.id).toBe('ficha-pestana-pagos');
    });

    it('una pestaña desconocida en la dirección abre Deportes', async () => {
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7?pestana=otra');
      expect(m.el().querySelector('[role="tab"][aria-selected="true"]')!.id).toBe('ficha-pestana-deportes');
    });
  });

  // ---------------------------------------------------------------- acciones: lo mismo que hoy

  describe('cada acción, igual que hoy (mismo endpoint, cuerpo y diálogos)', () => {
    async function igual(
      hoy: (m: Montaje<EditarAlumnoComponent>) => void | Promise<void>,
      nueva: (m: Montaje<FichaAlumnoComponent>) => void | Promise<void>,
      resultados: any[],
      esperado: Partial<Escritura>[],
      datos?: () => Datos
    ): Promise<void> {
      const r = await comparar(hoy, nueva, resultados, datos);
      expect(r.nueva.escrituras).withContext('escrituras').toEqual(r.hoy.escrituras);
      expect(r.nueva.swal).withContext('diálogos').toEqual(r.hoy.swal);
      expect(r.hoy.escrituras.length).withContext('el caso escribe algo').toBe(esperado.length);
      esperado.forEach((e, i) => expect(r.nueva.escrituras[i]).toEqual(jasmine.objectContaining(e)));
    }

    it('agregar deporte (diálogo de hoy)', async () => {
      await igual(
        async (m) => {
          m.comp.abrirModalAgregarDeporte();
          await m.asentar();
          m.comp.agregarDeporte();
        },
        async (m) => {
          boton(m.el(), 'Agregar deporte').click();
          await m.asentar();
          const modal = document.querySelector('#agregarDeporteModal.show') ?? m.el().querySelector('#agregarDeporteModal.show');
          expect(modal).withContext('el diálogo de hoy, abierto').not.toBeNull();
          boton(modal!, 'Agregar Deporte').click();
        },
        [],
        [{ metodo: 'POST', url: '/alumnos/7/deportes' }]
      );
    });

    it('marcar principal', async () => {
      await igual(
        (m) => m.comp.marcarDeportePrincipal('KICKBOXING'),
        (m) => boton(bloque(m, 'KICKBOXING'), 'Marcar principal').click(),
        [],
        [{ metodo: 'PUT', url: '/alumnos/7/deportes/KICKBOXING/principal' }]
      );
    });

    it('desactivar deporte', async () => {
      await igual(
        (m) => m.comp.desactivarDeporte('KICKBOXING'),
        (m) => boton(bloque(m, 'KICKBOXING'), 'Desactivar').click(),
        [{ isConfirmed: true }],
        [{ metodo: 'PUT', url: '/alumnos/7/deportes/KICKBOXING/desactivar' }]
      );
    });

    it('activar deporte', async () => {
      await igual(
        (m) => m.comp.activarDeporte('PILATES'),
        (m) => boton(bloque(m, 'PILATES'), 'Activar').click(),
        [{ isConfirmed: true }],
        [{ metodo: 'PUT', url: '/alumnos/7/deportes/PILATES/activar' }]
      );
    });

    it('eliminar deporte (elegir «Eliminar completamente»)', async () => {
      await igual(
        (m) => m.comp.removerDeporte('KICKBOXING'),
        (m) => boton(bloque(m, 'KICKBOXING'), 'Eliminar deporte').click(),
        [{ isDenied: true }],
        [{ metodo: 'DELETE', url: '/alumnos/7/deportes/KICKBOXING' }]
      );
    });

    it('dar de baja desde la cabecera de Deportes y desde «⋯»', async () => {
      await igual(
        (m) => m.comp.eliminarODarDeBajaAlumno(),
        (m) => boton(m.el().querySelector('.deportes__cabecera')!, 'Dar de baja').click(),
        [{ isConfirmed: true }],
        [{ metodo: 'PUT', url: '/alumnos/7/baja' }]
      );
      await igual(
        (m) => m.comp.eliminarODarDeBajaAlumno(),
        async (m) => {
          m.el().querySelector<HTMLButtonElement>('.ficha__mas')!.click();
          await m.asentar();
          boton(m.el().querySelector('.ficha__menu')!, 'Dar de baja o eliminar…').click();
        },
        [{ isConfirmed: true }],
        [{ metodo: 'PUT', url: '/alumnos/7/baja' }]
      );
    });

    it('eliminar alumno desde «⋯» con las dos confirmaciones de hoy', async () => {
      await igual(
        (m) => m.comp.eliminarODarDeBajaAlumno(),
        async (m) => {
          m.el().querySelector<HTMLButtonElement>('.ficha__mas')!.click();
          await m.asentar();
          boton(m.el().querySelector('.ficha__menu')!, 'Dar de baja o eliminar…').click();
        },
        [{ isDenied: true }, { isConfirmed: true }],
        [{ metodo: 'DELETE', url: '/alumnos/7' }]
      );
    });

    it('dar de alta a un alumno de baja', async () => {
      const deBaja = (): Datos => {
        const d = datosPorDefecto();
        d.deportes = d.deportes.map((x) => ({ ...x, activo: false }));
        d.alumno = { ...d.alumno, activo: false, deportes: d.deportes };
        return d;
      };
      await igual(
        (m) => m.comp.eliminarODarDeBajaAlumno(),
        (m) => boton(m.el().querySelector('.deportes__cabecera')!, 'Dar de alta').click(),
        [{ isConfirmed: true }, { isConfirmed: true, value: 'PILATES' }],
        [{ metodo: 'PUT', url: '/alumnos/7/deportes/PILATES/activar' }],
        deBaja
      );
    });

    it('actualizar grado (diálogo de hoy)', async () => {
      await igual(
        async (m) => {
          m.comp.abrirModalActualizarGrado('TAEKWONDO');
          await m.asentar();
          m.comp.nuevoGradoActualizar = 'AMARILLO';
          m.comp.actualizarGradoDeporte();
        },
        async (m) => {
          boton(bloque(m, 'TAEKWONDO'), 'Actualizar grado').click();
          await m.asentar();
          expect(m.el().querySelector('#actualizarGradoModal.show')).not.toBeNull();
          m.comp.nuevoGradoActualizar = 'AMARILLO';
          boton(m.el().querySelector('#actualizarGradoModal')!, 'Actualizar Grado').click();
        },
        [],
        [
          { metodo: 'PUT', url: '/alumnos/7/deportes/TAEKWONDO/grado' },
          { metodo: 'PUT', url: '/alumnos/7/deportes/TAEKWONDO/fecha-grado' },
        ]
      );
    });

    it('apto para examen', async () => {
      await igual(
        (m) => m.comp.actualizarAptoParaExamen('TAEKWONDO', { target: { checked: true } }),
        (m) => bloque(m, 'TAEKWONDO').querySelector<HTMLInputElement>('input[id^="aptoExamen-"]')!.click(),
        [],
        [{ metodo: 'PUT', url: '/alumnos/7/deportes/TAEKWONDO/apto-examen' }]
      );
    });

    it('agregar a convocatoria, pasar por recompensa y añadir derecho de examen', async () => {
      await igual(
        (m) => m.comp.abrirModalConvocatoriasPorDeporte('KICKBOXING'),
        (m) => boton(bloque(m, 'KICKBOXING'), 'Agregar a convocatoria').click(),
        [{ isConfirmed: true, value: '60' }],
        [{ metodo: 'POST' }]
      );
      await igual(
        (m) => m.comp.pasarGradoConDerechoExamen('KICKBOXING'),
        (m) => boton(bloque(m, 'KICKBOXING'), 'Añadir derecho de examen').click(),
        [{ isConfirmed: true }],
        [{ metodo: 'POST' }]
      );
      const aptoTkd = (): Datos => {
        const d = datosPorDefecto();
        d.deportes[0].aptoParaExamen = true;
        return d;
      };
      await igual(
        (m) => m.comp.pasarGradoPorRecompensa('TAEKWONDO'),
        (m) => boton(bloque(m, 'TAEKWONDO'), 'Pasar por recompensa').click(),
        [{ isConfirmed: true }],
        [{ metodo: 'POST' }],
        aptoTkd
      );
    });

    it('fecha de grado, tarifa, licencia y alta con «Aplicar cambios»', async () => {
      const cambiar = (raiz: ParentNode, selector: string, valor: string, evento = 'change') => {
        const campo = raiz.querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
        campo.value = valor;
        campo.dispatchEvent(new Event(evento, { bubbles: true }));
      };
      await igual(
        (m) => {
          m.comp.onFechaGradoChange('TAEKWONDO', { target: { value: '2024-01-15' } } as any);
          m.comp.applyGradoChanges('TAEKWONDO');
        },
        async (m) => {
          cambiar(bloque(m, 'TAEKWONDO'), 'input[id^="fechaGrado-"]', '2024-01-15');
          await m.asentar();
          boton(bloque(m, 'TAEKWONDO').querySelector('[data-caja="grado"]')!, 'Aplicar cambios').click();
        },
        [],
        [{ metodo: 'PUT', url: '/alumnos/7/deportes/TAEKWONDO/fecha-grado' }]
      );
      await igual(
        (m) => {
          m.comp.onTipoTarifaModelChange('TAEKWONDO', 'ADULTO');
          m.comp.onCuantiaTarifaModelChange('TAEKWONDO', 31);
          m.comp.applyTarifaChanges('TAEKWONDO');
        },
        async (m) => {
          cambiar(bloque(m, 'TAEKWONDO'), 'select[id^="tipoTarifa-"]', 'ADULTO');
          await m.asentar();
          cambiar(bloque(m, 'TAEKWONDO'), 'input[id^="cuantiaTarifa-"]', '31', 'input');
          await m.asentar();
          boton(bloque(m, 'TAEKWONDO').querySelector('[data-caja="tarifa"]')!, 'Aplicar cambios').click();
        },
        [],
        [
          { metodo: 'PUT', url: '/alumnos/7/deportes/TAEKWONDO/tipo-tarifa' },
          { metodo: 'PUT' },
        ]
      );
      await igual(
        (m) => {
          m.comp.onNumeroLicenciaChange('TAEKWONDO', { target: { value: '4321' } } as any);
          m.comp.applyLicenciaChanges('TAEKWONDO');
        },
        async (m) => {
          cambiar(bloque(m, 'TAEKWONDO'), 'input[id^="numeroLicencia-"]', '4321', 'input');
          await m.asentar();
          boton(bloque(m, 'TAEKWONDO').querySelector('[data-caja="licencia"]')!, 'Aplicar cambios').click();
        },
        [],
        [{ metodo: 'PUT' }]
      );
      await igual(
        (m) => {
          m.comp.onFechaAltaChange('KICKBOXING', { target: { value: '2022-11-01' } } as any);
          m.comp.applyEstadisticasChanges('KICKBOXING');
        },
        async (m) => {
          cambiar(bloque(m, 'KICKBOXING'), 'input[id^="fechaAlta-"]', '2022-11-01');
          await m.asentar();
          boton(bloque(m, 'KICKBOXING').querySelector('[data-caja="alta"]')!, 'Aplicar cambios').click();
        },
        [],
        [{ metodo: 'PUT' }]
      );
    });

    it('competidor (con licencia) y cancelar cambios sin enviar nada', async () => {
      await igual(
        (m) => {
          m.comp.onCompetidorChange('TAEKWONDO', { target: { checked: true } } as any);
          m.comp.applyCompetidorChanges('TAEKWONDO');
        },
        async (m) => {
          bloque(m, 'TAEKWONDO').querySelector<HTMLInputElement>('input[id^="competidor-"]')!.click();
          await m.asentar();
          boton(bloque(m, 'TAEKWONDO').querySelector('[data-caja="competidor"]')!, 'Aplicar cambios').click();
        },
        [],
        [{ metodo: 'PUT' }]
      );
      const m = await montar(FichaAlumnoComponent);
      const campo = bloque(m, 'TAEKWONDO').querySelector<HTMLInputElement>('input[id^="fechaGrado-"]')!;
      campo.value = '2024-01-15';
      campo.dispatchEvent(new Event('change'));
      await m.asentar();
      boton(bloque(m, 'TAEKWONDO').querySelector('[data-caja="grado"]')!, 'Cancelar').click();
      await m.asentar();
      expect(m.escrituras).toEqual([]);
      expect(m.comp.hasPendingGradoChanges('TAEKWONDO')).toBeFalse();
    });

    it('grupos y turnos: quitar y añadir', async () => {
      await igual(
        (m) => m.comp.eliminarGrupoAlumno(1),
        (m) => boton(bloque(m, 'TAEKWONDO'), 'Quitar el grupo Taekwondo Lunes y Miércoles').click(),
        [],
        [{ metodo: 'DELETE', url: '/alumnos/7/grupos/1' }]
      );
      await igual(
        (m) => m.comp.agregarGrupoAlumno(2),
        async (m) => {
          const s = bloque(m, 'TAEKWONDO').querySelector<HTMLSelectElement>('select.deportes__anadir-grupo')!;
          s.value = '2';
          s.dispatchEvent(new Event('change'));
        },
        [],
        [{ metodo: 'POST', url: '/alumnos/7/grupos/2' }]
      );
      await igual(
        (m) => m.comp.eliminarTurnoAlumno(1),
        (m) => boton(bloque(m, 'TAEKWONDO'), 'Quitar el turno Lunes 17:00 - 18:00').click(),
        [],
        [{ metodo: 'DELETE', url: '/alumnos/7/turnos/1' }]
      );
      await igual(
        (m) => m.comp.agregarTurnoAlumno(10),
        async (m) => {
          const s = bloque(m, 'TAEKWONDO').querySelector<HTMLSelectElement>('select.deportes__anadir-turno')!;
          s.value = '10';
          s.dispatchEvent(new Event('change'));
        },
        [],
        [{ metodo: 'POST', url: '/alumnos/7/turnos/10' }]
      );
    });

    it('foto: quitar y cambiar', async () => {
      const conFoto = (): Datos => {
        const d = datosPorDefecto();
        d.alumno = { ...d.alumno, fotoAlumno: { id: 5, nombre: 'f.webp', url: `${API}/alumnos/7/imagen` } };
        return d;
      };
      await igual(
        (m) => m.comp.eliminarFoto(7),
        (m) => boton(m.el(), 'Quitar').click(),
        [],
        [{ metodo: 'DELETE', url: '/alumnos/7/imagen' }],
        conFoto
      );
      const png = () => new File([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], 'foto-ficticia.png', { type: 'image/png' });
      const elegir = async (input: HTMLInputElement, m: Montaje<any>) => {
        const dt = new DataTransfer();
        dt.items.add(png());
        input.files = dt.files;
        input.dispatchEvent(new Event('change'));
        await new Promise((r) => setTimeout(r, 30));
        await m.asentar();
      };
      await igual(
        (m) => elegir(m.el().querySelector<HTMLInputElement>('#inputFile')!, m),
        (m) => elegir(m.el().querySelector<HTMLInputElement>('input[type="file"][accept="image/*"]')!, m),
        [],
        [{ metodo: 'PUT', url: '/alumnos/7' }]
      );
    });

    it('documentos: subir, eliminar, descargar, ver y ver más', async () => {
      const pdf = () => new File(['%PDF-1.4 ficticio'], 'documento-ficticio.pdf', { type: 'application/pdf' });
      const subir = async (input: HTMLInputElement, m: Montaje<any>) => {
        const dt = new DataTransfer();
        dt.items.add(pdf());
        input.files = dt.files;
        input.dispatchEvent(new Event('change'));
        await new Promise((r) => setTimeout(r, 30));
        await m.asentar();
      };
      await igual(
        (m) => subir(m.el().querySelector<HTMLInputElement>('#docFile')!, m),
        async (m) => {
          await pestana(m, 'Documentos y notas');
          await subir(m.el().querySelector<HTMLInputElement>('input[type="file"]:not([accept="image/*"])')!, m);
        },
        [],
        [{ metodo: 'POST', url: '/alumnos/7/documentos' }]
      );
      await igual(
        (m) => m.comp.eliminarDocumento(900),
        async (m) => {
          await pestana(m, 'Documentos y notas');
          boton(m.el(), 'Eliminar documento-ficticio-1.pdf').click();
        },
        [{ isConfirmed: true }],
        [{ metodo: 'DELETE', url: '/alumnos/7/documentos/900' }]
      );
      const m = await montar(FichaAlumnoComponent);
      await pestana(m, 'Documentos y notas');
      const descargar = spyOn(m.comp, 'descargarDocumento');
      const abrir = spyOn(m.comp, 'abrirDocumento');
      boton(m.el(), 'Descargar documento-ficticio-1.pdf').click();
      boton(m.el(), 'Ver documento-ficticio-1.pdf').click();
      expect(descargar).toHaveBeenCalledWith(jasmine.objectContaining({ id: 900 }));
      expect(abrir).toHaveBeenCalledWith(jasmine.objectContaining({ id: 900 }));
      expect(m.el().querySelectorAll('.documentos__documento').length).toBe(5);
      boton(m.el(), 'Ver 1 más').click();
      await m.asentar();
      expect(m.el().querySelectorAll('.documentos__documento').length).toBe(6);
    });

    it('WhatsApp desde el carnet y desde Documentos: el selector de teléfono y el diálogo de hoy', async () => {
      await igual(
        (m) => m.comp.abrirWhatsApp(),
        (m) => m.el().querySelector<HTMLButtonElement>('.ficha__accion--whatsapp')!.click(),
        [{ isConfirmed: true }],
        []
      );
      const m = await montar(FichaAlumnoComponent);
      m.resultados = [{ isConfirmed: true }];
      await pestana(m, 'Documentos y notas');
      boton(m.el().querySelector('app-ficha-documentos')!, 'WhatsApp').click();
      await m.asentar();
      expect(m.el().querySelector('#whatsappModal.show')).withContext('diálogo de WhatsApp de hoy').not.toBeNull();
    });

    it('información adicional: guardar observaciones', async () => {
      await igual(
        (m) => {
          m.comp.observacionesDraft = 'Otra nota inventada';
          m.comp.guardarObservaciones();
        },
        async (m) => {
          await pestana(m, 'Documentos y notas');
          const area = m.el().querySelector<HTMLTextAreaElement>('#observaciones')!;
          area.value = 'Otra nota inventada';
          area.dispatchEvent(new Event('input'));
          await m.asentar();
          boton(m.el(), 'Guardar observaciones').click();
        },
        [],
        [{ metodo: 'PUT', url: '/alumnos/7/observaciones', cuerpo: { observaciones: 'Otra nota inventada' } }]
      );
    });

    it('datos y contacto: «Editar datos» abre la edición; guardar envía lo mismo que hoy y cancelar no envía nada', async () => {
      await igual(
        (m) => {
          m.comp.toggleEditBasicInfo();
          m.comp.onBasicInfoChange('nombre', { target: { type: 'text', value: 'Rubén Ficticio' } } as any);
          m.comp.onBasicInfoChange('telefono2', { target: { type: 'tel', value: '622222222' } } as any);
          m.comp.applyBasicInfoChanges();
        },
        async (m) => {
          m.el().querySelector<HTMLButtonElement>('.ficha__accion--editar')!.click();
          await m.asentar();
          expect(m.el().querySelector('[role="tab"][aria-selected="true"]')!.id).toBe('ficha-pestana-datos');
          for (const [id, valor] of [['nombre', 'Rubén Ficticio'], ['telefono2', '622222222']]) {
            const campo = m.el().querySelector<HTMLInputElement>(`#${id}`)!;
            campo.value = valor;
            campo.dispatchEvent(new Event('input'));
          }
          await m.asentar();
          boton(m.el().querySelector('.ficha__guardar')!, 'Guardar cambios').click();
        },
        [],
        [{ metodo: 'PUT', url: '/alumnos/7' }]
      );
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7?pestana=datos');
      expect(texto(m.el(), '[data-scroll-anchor="alumno-informacion-personal"]')).toContain('05/03/2018');
      boton(m.el(), 'Editar').click();
      await m.asentar();
      // La fecha llega en milisegundos y el campo la muestra como hoy («yyyy-MM-dd»).
      expect(m.el().querySelector<HTMLInputElement>('#fechaNacimiento')!.value).toBe('2018-03-05');
      const campo = m.el().querySelector<HTMLInputElement>('#email')!;
      campo.value = 'no-es-un-correo';
      campo.dispatchEvent(new Event('input'));
      await m.asentar();
      expect(texto(m.el(), '.ficha__guardar')).toContain('Corrige los errores antes de guardar');
      expect(boton(m.el().querySelector('.ficha__guardar')!, 'Guardar cambios').disabled).toBeTrue();
      boton(m.el().querySelector('.ficha__guardar')!, 'Cancelar').click();
      await m.asentar();
      expect(m.escrituras).toEqual([]);
      expect(m.comp.editingBasicInfo).toBeFalse();
      expect(m.el().querySelector('#email')).toBeNull();
    });

    it('pagos: «Cobrar» de la tabla con el diálogo y el PUT de la ficha de hoy, paginación, gestionar productos y reservar plaza', async () => {
      await igual(
        (m) => m.comp.pagarProducto(m.comp.productosPaginados.find((p) => p.id === 801)!),
        async (m) => {
          await pestana(m, 'Pagos');
          boton(m.el(), 'Cobrar MENSUALIDAD SEPTIEMBRE 2026 - TAEKWONDO').click();
        },
        [{ isConfirmed: true }],
        [{ metodo: 'PUT', url: '/productos-alumno/801' }]
      );
      await igual(
        (m) => m.comp.reservarPlaza(7),
        async (m) => {
          await pestana(m, 'Pagos');
          boton(m.el(), 'Reservar plaza').click();
        },
        [{ isConfirmed: true, value: 'TAEKWONDO' }, { isConfirmed: false }],
        []
      );
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7?pestana=pagos');
      const filas = () => m.el().querySelectorAll('.pagos__fila');
      expect(filas().length).toBe(4);
      expect(texto(m.el(), '.pagos__resumen')).toBe(`6 cargos, 60,00${NBSP}€ pendiente`);
      expect(filas()[0].textContent).toContain(`30,00${NBSP}€`);
      expect(filas()[0].textContent).toContain('Pendiente');
      expect(filas()[2].textContent).toContain('Pagado 03/08/2026');
      boton(m.el(), 'Página siguiente de pagos').click();
      await m.asentar();
      expect(filas().length).toBe(2);
      expect(texto(m.el(), '.pagos__posicion')).toBe('2 de 2');
      boton(m.el(), 'Gestionar productos').click();
      await m.asentar();
      expect(TestBed.inject(Router).url).toBe('/alumnos/7/productos');
    });

    it('«Vista de familia» lleva a la vista de la familia con el alumno', async () => {
      const m = await montar(FichaAlumnoComponent);
      expect(m.el().querySelector('a.ficha__accion--familia')!.getAttribute('href')).toBe('/userpage?alumnoId=7');
    });
  });

  // ---------------------------------------------------------------- revisión de la ronda 2

  describe('el pendiente es siempre del alumno que se ve', () => {
    const esPendiente = (r: TestRequest) => r.request.url.endsWith('/tesoreria/pendientes-por-alumno');

    it('al pasar de 7 a 9 sin respuesta del pendiente de 9: sin importe, «Cobrar», panel ni insignia; la del 7 que llega tarde se ignora', async () => {
      const m = await montar(FichaAlumnoComponent);
      m.el().querySelector<HTMLButtonElement>('.ficha__cobrar')!.click();
      await m.asentar();
      expect(m.el().querySelector('.ficha__cargos')).withContext('panel abierto en el 7').not.toBeNull();
      m.retener = esPendiente;
      m.el().querySelector<HTMLButtonElement>('.ficha__siguiente')!.click();
      await m.asentar();
      expect(TestBed.inject(Router).url).toBe('/alumnosEditar/9');
      expect(m.comp.alumnoId).toBe(9);
      expect(m.el().querySelector('.ficha__importe')).toBeNull();
      expect(m.el().querySelector('.ficha__cobrar')).toBeNull();
      expect(m.el().querySelector('.ficha__cargos')).toBeNull();
      expect(m.el().querySelector('[role="tab"] .ficha__insignia')).toBeNull();
      const pendientes = m.retenidas.filter((r) => !r.cancelled);
      expect(pendientes.map((r) => r.request.params.get('alumnoIds'))).toEqual(['9']);
      // Llega una respuesta que no es del alumno que se ve (la del 7): no se pinta ni se puede cobrar.
      pendientes[0].flush([TRES_CARGOS]);
      m.harness.detectChanges();
      expect(m.el().querySelector('.ficha__cobrar')).toBeNull();
      expect(m.el().querySelector('.ficha__importe')).toBeNull();
      expect(texto(m.el(), '.ficha__al-dia')).toBe('Al día');
    });

    it('la respuesta del alumno anterior que llega cuando ya se ve otro no lo pinta', async () => {
      const datos = datosPorDefecto();
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7', datos);
      m.retener = esPendiente;
      m.comp.releerPendiente();
      const [del7] = m.http.match((r) => r.url.endsWith('/tesoreria/pendientes-por-alumno')).filter((r) => !r.cancelled);
      expect(del7.request.params.get('alumnoIds')).toBe('7');
      m.el().querySelector<HTMLButtonElement>('.ficha__siguiente')!.click();
      await m.asentar();
      expect(m.comp.alumnoId).toBe(9);
      if (!del7.cancelled) {
        del7.flush([TRES_CARGOS]);
        m.harness.detectChanges();
      }
      expect(m.el().querySelector('.ficha__cobrar')).toBeNull();
      expect(m.el().querySelector('.ficha__importe')).toBeNull();
    });
  });

  describe('buscador en los desplegables de Deportes (el de hoy)', () => {
    it('tarifa, rol familiar, categoría, añadir turno y añadir grupo llevan el buscador de hoy, con su lista en .pn', async () => {
      const datos = datosPorDefecto();
      datos.deportes[0] = { ...datos.deportes[0], tipoTarifa: 'PADRES_HIJOS', competidor: true };
      datos.alumno.deportes = datos.deportes;
      const m = await montar(FichaAlumnoComponent, '/alumnosEditar/7', datos);
      const tkd = bloque(m, 'TAEKWONDO');
      for (const selector of [
        'select[id^="tipoTarifa-"]',
        'select[id^="rolFamiliar-"]',
        'select[id^="categoria-"]',
        'select.deportes__anadir-turno',
        'select.deportes__anadir-grupo',
      ]) {
        const select = tkd.querySelector<HTMLSelectElement>(selector);
        expect(select).withContext(selector).not.toBeNull();
        expect(select!.closest('.admin-select-combobox')).withContext(`${selector} con buscador`).not.toBeNull();
      }
      const listaId = tkd.querySelector('select[id^="tipoTarifa-"]')!.closest('.admin-select-combobox')!
        .querySelector('input.admin-select-input')!.getAttribute('aria-controls')!;
      const lista = document.getElementById(listaId)!;
      expect(lista.classList).withContext('la lista flotante usa los tokens del panel').toContain('pn');
    });

    it('filtra las tarifas al escribir', async () => {
      const m = await montar(FichaAlumnoComponent);
      const combo = bloque(m, 'TAEKWONDO').querySelector('select[id^="tipoTarifa-"]')!.closest('.admin-select-combobox')!;
      const entrada = combo.querySelector<HTMLInputElement>('input.admin-select-input')!;
      const lista = document.getElementById(entrada.getAttribute('aria-controls')!)!;
      const visibles = () =>
        Array.from(lista.querySelectorAll<HTMLElement>('.admin-select-option'))
          .filter((o) => o.style.display !== 'none')
          .map((o) => o.textContent!.trim());
      entrada.value = 'grupo';
      entrada.dispatchEvent(new Event('input'));
      await new Promise((r) => setTimeout(r, 300));
      expect(visibles()).toEqual(['Adulto Grupo', 'Infantil Grupo']);
    });
  });

  // ---------------------------------------------------------------- cinturón (sin grados)

  it('disciplina sin grados: sin caja Grado ni Licencia, como hoy; los diálogos quedan fuera de .pn', async () => {
    const m = await montar(FichaAlumnoComponent);
    const pilates = bloque(m, 'PILATES');
    expect(pilates.querySelector('[data-caja="grado"]')).toBeNull();
    expect(pilates.querySelector('[data-caja="licencia"]')).toBeNull();
    expect(pilates.querySelector('[data-caja="tarifa"]')).not.toBeNull();
    expect(pilates.querySelector('[data-caja="alta"]')).not.toBeNull();
    expect(texto(pilates, '.deportes__marca')).toBe('Inactivo');
    const dialogos = m.el().querySelector('app-dialogos-alumno')!;
    expect(dialogos.closest('.pn')).withContext('los diálogos de hoy conservan su tipografía').toBeNull();
  });
});

function canales(rgb: string): number[] {
  return (rgb.match(/\d+(\.\d+)?/g) ?? []).slice(0, 3).map(Number);
}

function contrasteRgb(a: string, b: string): number {
  const lum = (c: number[]) => {
    const [r, g, bl] = c.map((v) => {
      const s = v / 255;
      return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
  };
  const la = lum(canales(a));
  const lb = lum(canales(b));
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}
