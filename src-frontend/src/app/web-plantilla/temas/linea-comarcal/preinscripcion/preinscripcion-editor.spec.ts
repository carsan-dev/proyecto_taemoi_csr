import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { CARGAR_CAPA_EDITOR, EditorWebService } from '../../../comun/editor-web';
import { RELOJ_WEB } from '../../../comun/reloj';
import { PLAZAS, grupoDePrueba, sedeDePrueba, sitioConSedes, turnoDePrueba } from '../../../datos/datos-de-prueba';
import { SitioWeb } from '../../../datos/modelos';
import type { OpcionesCapaEditor } from '../../../editor/capa-editor';
import { simuladorDeLaVista } from '../../../editor/casos-vista';
import { CANAL_EDITOR_WEB, CasoVista, PasoPreinscripcion } from '../../../editor/puente-editor';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { CLAVE_SEDE_RECORDADA } from '../contenido';
import { CLAVE_BORRADOR_PREINSCRIPCION, CLAVE_BORRADOR_PREINSCRIPCION_PREVIA } from './preinscripcion.component';

/** Sábado 7 de noviembre de 2026, 12:00 (hora local). */
const AHORA = new Date(2026, 10, 7, 12, 0);
const EDITOR = '/preinscripcion?vista-previa=linea-comarcal&editor=1';

/** Seis sedes; en Estepa (la 2.ª), Peques lunes y miércoles con plazas e Infantil lleno el martes. */
function seisSedes(vistaPrevia = false): SitioWeb {
  const sitio = sitioConSedes(6, { vistaPrevia });
  sitio.sedes[0] = { ...sitio.sedes[0], telefono: '955000001' };
  sitio.sedes[1] = sedeDePrueba(2, 'Estepa', {
    telefono: '600000002',
    grupos: [
      grupoDePrueba('Peques', [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.quedan(2)), turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.quedan(4))], { id: 70 }),
      grupoDePrueba('Infantil', [turnoDePrueba('Martes', '18:00', '19:00', PLAZAS.lleno)], { id: 71, rangoEdadMin: 7, rangoEdadMax: 11 }),
    ],
  });
  return sitio;
}

/**
 * W12c-ter: la preinscripción del tema con el editor de la web (pasos 1-4 y los tres casos del boceto, en memoria) y la
 * invariante de siempre: sin el editor activo, nada de eso se alcanza por la dirección ni por mensajes, y el
 * almacenamiento y las llamadas son los de siempre. Los specs de `preinscripcion.component.spec.ts` no cambian.
 */
describe('PreinscripcionLineaComarcalComponent con el editor de la web (W12c-ter)', () => {
  const url = `${environment.apiUrl}/publico/web`;
  const base = `${environment.apiUrl}/preinscripciones`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let cargar: jasmine.Spy;
  let opciones: OpcionesCapaEditor | null;
  let tituloAnterior: string;

  beforeEach(async () => {
    tituloAnterior = document.title;
    opciones = null;
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
    sessionStorage.removeItem(CLAVE_BORRADOR_PREINSCRIPCION);
    sessionStorage.removeItem(CLAVE_BORRADOR_PREINSCRIPCION_PREVIA);
    cargar = jasmine.createSpy('cargar').and.callFake(() =>
      Promise.resolve({
        activarCapaEditor: (o: OpcionesCapaEditor) => {
          opciones = o;
          return () => undefined;
        },
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(WEB_PLANTILLA_ROUTES),
        { provide: RELOJ_WEB, useValue: () => AHORA },
        { provide: CARGAR_CAPA_EDITOR, useValue: cargar },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
    sessionStorage.removeItem(CLAVE_BORRADOR_PREINSCRIPCION);
    sessionStorage.removeItem(CLAVE_BORRADOR_PREINSCRIPCION_PREVIA);
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
    document.title = tituloAnterior;
  });

  async function estable(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function abrir(sitio: SitioWeb, ruta: string): Promise<void> {
    await harness.navigateByUrl(ruta);
    http.expectOne((r) => r.url === url).flush(sitio);
    await estable();
    await estable();
  }

  /** Lo que manda la capa al recibir el paso y el caso del panel. */
  async function ver(caso: CasoVista | null, paso: PasoPreinscripcion | null): Promise<void> {
    expect(opciones).withContext('la capa de edición no se activó').not.toBeNull();
    opciones!.alCaso!(simuladorDeLaVista(caso, paso));
    await estable();
  }

  const normas = () =>
    http.expectOne(`${base}/public/configuracion/TAEKWONDO`).flush({ deporte: 'TAEKWONDO', version: 1, contenido: { normas: ['Respeto.'] } });

  const raiz = () => harness.routeNativeElement!;
  const texto = (selector: string) => raiz().querySelector(selector)?.textContent!.replace(/\s+/g, ' ').trim() ?? null;
  const textos = (selector: string) => Array.from(raiz().querySelectorAll(selector)).map((e) => e.textContent!.replace(/\s+/g, ' ').trim());
  const escrituras = (spy: jasmine.Spy) => spy.calls.all().filter((c) => c.object === sessionStorage).length;

  describe('sin el editor activo (visitante)', () => {
    it('?paso=4 no da la confirmación: el paso que toca con lo que hay (sin fecha, el 2)', async () => {
      await abrir(seisSedes(), '/preinscripcion?paso=4&sede=estepa&grupo=70');
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      expect(raiz().querySelector('.banda')).toBeNull();
      expect(texto('.progreso-texto')).toBe('Paso 2 de 3');
    });

    it('?paso=3 con sede y grupo pero sin fecha escrita sigue en el paso 2; sin la capa no hay ejemplo', async () => {
      await abrir(seisSedes(), '/preinscripcion?paso=3&sede=estepa&grupo=70&dias=1,3');
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      expect(TestBed.inject(EditorWebService).preinscripcion(seisSedes(), AHORA)).toBeNull();
      expect(cargar).not.toHaveBeenCalled();
    });

    it('un mensaje del puente (paso 4, «En la lista de espera») no cambia nada: no hay capa que lo escuche', async () => {
      await abrir(seisSedes(), '/preinscripcion');
      const antes = raiz().innerHTML;
      for (const data of [
        { canal: CANAL_EDITOR_WEB, tipo: 'paso', paso: 4 },
        { canal: CANAL_EDITOR_WEB, tipo: 'caso', caso: 'fin-espera' },
      ]) {
        window.dispatchEvent(new MessageEvent('message', { data, origin: location.origin, source: window.parent }));
      }
      await estable();
      expect(raiz().innerHTML).toBe(antes);
      expect(texto('h1.pregunta-titulo')).toBe('¿En qué pueblo?');
      expect(cargar).not.toHaveBeenCalled();
    });

    it('con ?editor=1 pero sin la confirmación de la API no se activa: lo escrito se guarda como siempre (borrador de la vista previa)', async () => {
      await abrir(seisSedes(false), `${EDITOR}&paso=2&sede=estepa`);
      expect(cargar).not.toHaveBeenCalled();
      const control = raiz().querySelector<HTMLInputElement>('#dia')!;
      control.value = '14';
      control.dispatchEvent(new Event('input'));
      await estable();
      expect(JSON.parse(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION_PREVIA)!).dia).toBe('14');
    });

    it('el visitante sigue guardando su borrador al escribir', async () => {
      await abrir(seisSedes(), '/preinscripcion?paso=2&sede=estepa');
      const control = raiz().querySelector<HTMLInputElement>('#dia')!;
      control.value = '14';
      control.dispatchEvent(new Event('input'));
      await estable();
      expect(JSON.parse(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION)!).dia).toBe('14');
    });
  });

  describe('con el editor activo', () => {
    it('se activa solo con editor=1, la vista previa confirmada por la API y dentro de un iframe', async () => {
      await abrir(seisSedes(true), EDITOR);
      expect(cargar).toHaveBeenCalledTimes(1);
      expect(TestBed.inject(EditorWebService).enEditor()).toBeTrue();
    });

    it('pasos 1 a 4 con datos de ejemplo de la escuela, sin dirección, almacenamiento ni foco', async () => {
      const setItem = spyOn(Storage.prototype, 'setItem').and.callThrough();
      const removeItem = spyOn(Storage.prototype, 'removeItem').and.callThrough();
      await abrir(seisSedes(true), EDITOR);
      const direccion = TestBed.inject(Router).url;

      await ver(null, 1);
      expect(texto('h1.pregunta-titulo')).toBe('¿En qué pueblo?');
      expect(texto('.progreso-texto')).toBe('Paso 1 de 3');

      await ver(null, 2);
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      expect(texto('.contexto > span')).toBe('En Herrera · Pabellón municipal');
      expect(texto('.grupos-espera')).toBe('Escribe su fecha de nacimiento y aquí verás los grupos de Herrera para su edad.');

      await ver(null, 3);
      normas();
      await estable();
      expect(texto('h1.pregunta-titulo')).toBe('Datos y firma');
      expect(texto('.progreso-texto')).toBe('Paso 3 de 3');
      expect(texto('.eleccion > span')).toBe('Peques, 4 a 6 años · Lunes y miércoles, 17:00–17:45');
      expect(raiz().querySelector('.aviso-espera')).toBeNull();
      expect(texto('.enviar button')).toBe('Enviar preinscripción');

      await ver(null, 4);
      expect(texto('#fin-titulo')).toBe('Preinscripción enviada');
      expect(texto('.banda-ref strong')).toBe('TSS-2026-0142');
      expect(texto('.banda-estado')).toBe('Tu preinscripción ya cuenta en las plazas de Peques en Herrera.');
      expect(raiz().querySelector('.progreso')).toBeNull();
      // Decisión del propietario (11/11): teléfono del solicitante de ejemplo, no el de la sede, y la ficha entera.
      expect(raiz().textContent).toContain('Si falta algo, te llaman al 600 000 000.');
      expect(raiz().textContent).not.toContain('955000001');
      expect(textos('.ficha-fila dt')).toEqual(jasmine.arrayContaining(['Grupo', 'Alumno', 'Responsable', 'Contacto']));
      expect(textos('.ficha-fila dd')).toEqual(jasmine.arrayContaining(['Lucía García Ruiz', 'Ana Ruiz Pérez', 'ana@ejemplo.es']));

      await ver(null, 2);
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      expect(raiz().querySelector('.banda')).toBeNull();

      expect(escrituras(setItem)).toBe(0);
      expect(escrituras(removeItem)).toBe(0);
      expect(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION_PREVIA)).toBeNull();
      expect(TestBed.inject(Router).url).toBe(direccion);
      expect(document.activeElement?.classList.contains('pregunta-titulo')).toBeFalse();
      expect(document.activeElement?.id).not.toBe('fin-titulo');
    });

    it('«Sin grupo para su edad» (paso 2): el vacío de su sede para una edad sin grupo', async () => {
      await abrir(seisSedes(true), EDITOR);
      await ver('sin-grupo', 2);
      expect(texto('.vacio h2')).toBe('En Herrera no hay grupo para 3 años');
      expect(raiz().querySelector('.continuar')).toBeNull();
    });

    it('«Grupo completo» (paso 3): el aviso de la lista de espera y su botón', async () => {
      await abrir(seisSedes(true), EDITOR);
      await ver('espera', 3);
      normas();
      await estable();
      expect(texto('.aviso-espera strong')).toBe('Este grupo está completo: te apuntas a la lista de espera.');
      expect(texto('.enviar button')).toBe('Enviar y apuntarme a la lista de espera');
    });

    it('«En la lista de espera» (paso 4): la confirmación de la lista de espera con la posición del boceto', async () => {
      await abrir(seisSedes(true), EDITOR);
      await ver('fin-espera', 4);
      expect(texto('#fin-titulo')).toBe('Estás en la lista de espera');
      expect(texto('.banda-estado')).toContain('3');
      // La ficha entera, con el solicitante de ejemplo (decisión del propietario, 11/11).
      expect(textos('.ficha-fila dt')).toEqual(jasmine.arrayContaining(['Grupo', 'Alumno', 'Responsable', 'Contacto']));
    });

    it('lo que se escriba o se envíe no se guarda ni sale: ni sessionStorage ni POST', async () => {
      const setItem = spyOn(Storage.prototype, 'setItem').and.callThrough();
      await abrir(seisSedes(true), EDITOR);
      await ver(null, 3);
      normas();
      await estable();
      const nombre = raiz().querySelector<HTMLInputElement>('#nombre')!;
      nombre.value = 'Lucía';
      nombre.dispatchEvent(new Event('input'));
      await estable();
      raiz().querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();
      await estable();
      http.expectNone((r) => r.method !== 'GET');
      expect(escrituras(setItem)).toBe(0);
      expect(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION_PREVIA)).toBeNull();
      expect(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION)).toBeNull();
    });

    it('el formulario válido (fecha, campos, normas y firma) no sale: ni POST ni borrador, y avisa de que es vista previa', async () => {
      const setItem = spyOn(Storage.prototype, 'setItem').and.callThrough();
      await abrir(seisSedes(true), EDITOR);
      await ver(null, 3);
      normas();
      await estable();
      // La capa inerte del editor impide escribir y firmar con el ratón: se rellena y se envía llamando al componente directamente.
      const componente = harness.routeDebugElement!.componentInstance as {
        datos: { set(valor: unknown): void };
        firmado: { set(valor: boolean): void };
        enviarDatos(evento: Event): void;
      };
      componente.datos.set({
        nombre: 'Lucía',
        apellidos: 'Pérez Gil',
        dia: '14',
        mes: '3',
        anio: '2021',
        dni: '12345678Z',
        direccion: 'Calle Real 1',
        discapacidad: 'no',
        tutorNombre: 'Lucía Pérez Gil',
        tutorDni: '12345678Z',
        telefono: '600000000',
        telefono2: '',
        email: 'lucia@ejemplo.es',
        observaciones: '',
        normas: true,
        fotos: false,
        firmante: 'Lucía Pérez Gil',
      });
      componente.firmado.set(true);
      await estable();
      componente.enviarDatos(new Event('submit'));
      await estable();
      http.expectNone((r) => r.method !== 'GET');
      expect(texto('#envio-error')).toBe('Es una vista previa: la preinscripción no se envía.');
      expect(escrituras(setItem)).toBe(0);
    });

    it('la sede del ejemplo no pisa la sede recordada del gestor, ni al verla ni al pasar por el paso 1', async () => {
      localStorage.setItem(CLAVE_SEDE_RECORDADA, 'casariche');
      await abrir(seisSedes(true), EDITOR);
      await ver(null, 2);
      expect(texto('.contexto > span')).toBe('En Herrera · Pabellón municipal');
      expect(localStorage.getItem(CLAVE_SEDE_RECORDADA)).toBe('casariche');
      await ver(null, 1);
      raiz().querySelector<HTMLButtonElement>('form button[type="submit"]')?.click();
      await estable();
      expect(localStorage.getItem(CLAVE_SEDE_RECORDADA)).toBe('casariche');
    });

    it('sin paso ni caso (null), vuelve a mandar la dirección', async () => {
      await abrir(seisSedes(true), EDITOR);
      await ver(null, 2);
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      await ver(null, null);
      expect(texto('h1.pregunta-titulo')).toBe('¿En qué pueblo?');
    });
  });
});
