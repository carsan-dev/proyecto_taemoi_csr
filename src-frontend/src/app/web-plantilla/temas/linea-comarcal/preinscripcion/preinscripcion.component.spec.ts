import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { DATOS_VACIOS, DatosPreinscripcion } from '../../../comun/preinscripcion/preinscripcion';
import { RELOJ_WEB } from '../../../comun/reloj';
import { PLAZAS, grupoDePrueba, sedeDePrueba, sitioConSedes, turnoDePrueba } from '../../../datos/datos-de-prueba';
import { SitioWeb } from '../../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { CLAVE_SEDE_RECORDADA, TextosTema, vistaSalidas } from '../contenido';
import {
  CLAVE_BORRADOR_PREINSCRIPCION,
  CLAVE_BORRADOR_PREINSCRIPCION_PREVIA,
  PreinscripcionLineaComarcalComponent,
} from './preinscripcion.component';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

/** Sábado 7 de noviembre de 2026, 12:00 (hora local). */
const AHORA = new Date(2026, 10, 7, 12, 0);

/** Nacida el 14/3/2021: cumple 5 este año (Peques, 4 a 6) y es menor. */
const FECHA_PEQUES: Partial<DatosPreinscripcion> = { dia: '14', mes: '3', anio: '2021' };

/** Seis sedes; en Estepa (la 2.ª), Peques lunes y miércoles con plazas e Infantil lleno el martes. */
function seisSedes(): SitioWeb {
  const sitio = sitioConSedes(6);
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
 * Preinscripción del tema «La línea comarcal» (W6, boceto `boceto-linea-comarcal-preinscripcion.html`): pasos de
 * pueblo, grupo y datos con la firma dibujada obligatoria (decisión 493), una y seis sedes, parámetros de la dirección,
 * borrador, envío con plaza o en lista de espera, error 400 y web sin publicar.
 */
describe('PreinscripcionLineaComarcalComponent', () => {
  const url = `${environment.apiUrl}/publico/web`;
  const base = `${environment.apiUrl}/preinscripciones`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let tituloAnterior: string;

  beforeEach(async () => {
    tituloAnterior = document.title;
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
    sessionStorage.removeItem(CLAVE_BORRADOR_PREINSCRIPCION);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([...WEB_PLANTILLA_ROUTES, { path: 'login', component: LoginFalsoComponent }]),
        { provide: RELOJ_WEB, useValue: () => AHORA },
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

  function borrador(datos: Partial<DatosPreinscripcion>): void {
    sessionStorage.setItem(CLAVE_BORRADOR_PREINSCRIPCION, JSON.stringify({ ...DATOS_VACIOS, ...datos }));
  }

  async function estable(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function abrir(sitio: SitioWeb, ruta: string): Promise<HTMLElement> {
    await harness.navigateByUrl(ruta);
    http.expectOne((r) => r.url === url).flush(sitio);
    await estable();
    return harness.routeNativeElement!;
  }

  /** Las normas del paso 3 (GET de la configuración de la disciplina). */
  function normas(contenido: unknown = { normas: ['Llega cinco minutos antes.', 'Dobok limpio.'], importes: 'Mensualidad: 25 € al mes.' }): void {
    http.expectOne(`${base}/public/configuracion/TAEKWONDO`).flush({ deporte: 'TAEKWONDO', version: 1, contenido });
  }

  const raiz = () => harness.routeNativeElement!;
  const textos = (selector: string) =>
    Array.from(raiz().querySelectorAll(selector)).map((e) => e.textContent!.replace(/\s+/g, ' ').trim());
  const texto = (selector: string) => textos(selector)[0] ?? null;
  const consulta = () => TestBed.inject(Router).parseUrl(TestBed.inject(Router).url).queryParams;

  async function escribir(id: string, valor: string): Promise<void> {
    const control = raiz().querySelector<HTMLInputElement>(`#${id}`)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    await estable();
  }

  async function marcar(selector: string): Promise<void> {
    raiz().querySelector<HTMLInputElement>(selector)!.click();
    await estable();
  }

  async function enviar(): Promise<void> {
    raiz().querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();
    await estable();
  }

  async function firmar(): Promise<void> {
    const lienzo = raiz().querySelector<HTMLCanvasElement>('canvas.firma')!;
    const r = lienzo.getBoundingClientRect();
    const en = (tipo: string, x: number, y: number) =>
      lienzo.dispatchEvent(new PointerEvent(tipo, { clientX: r.left + x, clientY: r.top + y, pointerId: 1, bubbles: true }));
    en('pointerdown', 20, 40);
    en('pointermove', 80, 60);
    en('pointermove', 140, 30);
    en('pointerup', 140, 30);
    await estable();
  }

  /** Paso 3 de una menor en Peques de Estepa, con todo rellenado salvo la firma. */
  async function pasoDatosRelleno(sitio = seisSedes(), ruta = '/preinscripcion?paso=3&sede=estepa&grupo=70'): Promise<void> {
    borrador({
      ...FECHA_PEQUES,
      nombre: 'Lucía',
      apellidos: 'García Ruiz',
      direccion: 'C/ Ejemplo, 1, Estepa',
      discapacidad: 'no',
      tutorNombre: 'Ana Ruiz Pérez',
      tutorDni: 'X1234567L',
      telefono: '+34 612 345 678',
      email: 'ana@ejemplo.es',
      normas: true,
      firmante: 'Ana Ruiz Pérez',
    });
    await abrir(sitio, ruta);
    normas();
    await estable();
  }

  describe('paso 1: pueblo (varias sedes)', () => {
    it('sin parámetros: «¿En qué pueblo?», las seis sedes, paso 1 de 3 y el resumen sin elegir', async () => {
      await abrir(seisSedes(), '/preinscripcion');
      expect(harness.routeDebugElement!.componentInstance).toBeInstanceOf(PreinscripcionLineaComarcalComponent);
      expect(texto('h1.pregunta-titulo')).toBe('¿En qué pueblo?');
      expect(texto('.progreso-texto')).toBe('Paso 1 de 3');
      expect(textos('.progreso-paso')).toEqual(['Pueblo', 'Grupo', 'Datos']);
      expect(textos('.sel-nombre')).toEqual(['HerreraElegida', 'EstepaElegida', 'CasaricheElegida', 'Lora de EstepaElegida', 'Roda de AndalucíaElegida', 'MollinaElegida']);
      expect(texto('.sel:nth-child(2) .sel-dato')).toBe('Pabellón municipal · 2 grupos · próxima clase lunes a las 17:00');
      expect(textos('.resumen-vacio')).toEqual(['Sin elegir', 'Sin elegir', 'Sin elegir']);
      expect(raiz().querySelector('a.atras')!.getAttribute('href')).toBe('/#linea');
    });

    it('sin elegir: el resumen de errores; al elegir, paso 2 con la sede en la dirección y recordada', async () => {
      await abrir(seisSedes(), '/preinscripcion');
      await enviar();
      expect(texto('.errores h2')).toBe('Revisa este dato');
      expect(textos('.errores li')).toEqual(['Elige el pueblo donde quieres entrenar.']);
      expect(texto('#sede-error')).toBe('Error: Elige el pueblo donde quieres entrenar.');
      expect(document.title.startsWith('Error: ')).toBeTrue();
      await marcar('#s-estepa');
      await enviar();
      expect(consulta()).toEqual({ paso: '2', sede: 'estepa' });
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      expect(localStorage.getItem(CLAVE_SEDE_RECORDADA)).toBe('estepa');
      expect(document.title.startsWith('Error: ')).toBeFalse();
    });

    it('la última sede recordada viene marcada', async () => {
      localStorage.setItem(CLAVE_SEDE_RECORDADA, 'casariche');
      await abrir(seisSedes(), '/preinscripcion');
      expect(raiz().querySelector<HTMLInputElement>('#s-casariche')!.checked).toBeTrue();
    });
  });

  describe('paso 2: grupo', () => {
    it('una sola sede: sin paso de pueblo (paso 1 de 2), sin «Cambiar de pueblo» y Atrás a la portada', async () => {
      await abrir(sitioConSedes(1), '/preinscripcion');
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      expect(texto('.progreso-texto')).toBe('Paso 1 de 2');
      expect(textos('.progreso-paso')).toEqual(['Grupo', 'Datos']);
      expect(textos('.contexto a')).toEqual([]);
      expect(raiz().querySelector('a.atras')!.getAttribute('href')).toBe('/#linea');
      expect(texto('.grupos-espera')).toBe('Escribe su fecha de nacimiento y aquí verás los grupos de Estepa para su edad.');
    });

    it('con la fecha: solo los grupos de su edad; al elegir, el grupo y los días van a la dirección', async () => {
      await abrir(seisSedes(), '/preinscripcion?sede=estepa');
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
      await escribir('dia', '14');
      await escribir('mes', '3');
      await escribir('anio', '2021');
      expect(texto('#edad-estado')).toBe('Este año cumple 5 años: hay 1 grupo de su edad en Estepa.');
      expect(textos('.salida .grupo-nombre')).toEqual(['Peques']);
      expect(texto('.salida .plazas')).toBe('Quedan 4 plazas');
      raiz().querySelector<HTMLButtonElement>('.salida-accion button')!.click();
      await estable();
      await estable();
      expect(consulta()).toEqual({ paso: '2', sede: 'estepa', grupo: '70' });
      expect(textos('.casilla-dia .casilla-nombre')).toEqual(['Lunes', 'Miércoles']);
      expect(textos('.casilla-dia .plazas')).toEqual(['Quedan 2 plazas', 'Quedan 4 plazas']);
      expect(textos('dl .resumen-fila dd:not(.resumen-cambiar)')).toContain('Peques, 4 a 6 años');
      expect(JSON.parse(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION)!).anio).toBe('2021');
    });

    it('prefill de ?sede=&grupo=&dias=: el grupo elegido con solo esos días y el resto avisado', async () => {
      borrador(FECHA_PEQUES);
      await abrir(seisSedes(), '/preinscripcion?sede=estepa&grupo=70&dias=3');
      expect(texto('.salida--elegida .grupo-nombre')).toBe('PequesElegido');
      const casillas = Array.from(raiz().querySelectorAll<HTMLInputElement>('.casilla-dia input'));
      expect(casillas.map((c) => c.checked)).toEqual([false, true]);
      expect(textos('.resumen-fila dd')).toContain('Miércoles, 17:00–17:45Solo ese día; el grupo también entrena el lunes.');
    });

    it('desmarcar todos los días avisa en el campo; sin grupo, «Elige un grupo para seguir.»', async () => {
      borrador(FECHA_PEQUES);
      await abrir(seisSedes(), '/preinscripcion?sede=estepa&grupo=70');
      await marcar('.casilla-dia:nth-child(1) input');
      await marcar('.casilla-dia:nth-child(2) input');
      expect(texto('#dias-error')).toBe('Error: Marca al menos un día. Si no puede venir ninguno, elige otro grupo.');
      await enviar();
      expect(textos('.errores li')).toEqual(['Marca al menos un día. Si no puede venir ninguno, elige otro grupo.']);

      await harness.navigateByUrl('/preinscripcion?sede=estepa&paso=2');
      await estable();
      await enviar();
      expect(textos('.errores li')).toEqual(['Elige un grupo para seguir.']);
    });

    it('plazas por día: cada día con su estado; con algún día con plaza se elige el grupo y solo esos días', async () => {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba(
        'Peques',
        [
          turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.quedan(1)),
          turnoDePrueba('Martes', '17:00', '17:45', PLAZAS.espera),
          turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.quedan(3)),
          turnoDePrueba('Jueves', '17:00', '17:45', PLAZAS.quedan(0)),
        ],
        { id: 70 },
      );
      borrador(FECHA_PEQUES);
      await abrir(sitio, '/preinscripcion?sede=estepa');
      expect(texto('.salida .plazas')).toBe('Quedan 3 plazas');
      expect(texto('.salida-accion button')).toBe('Elegir este grupo');
      expect(raiz().querySelector('.salida-nota')).toBeNull();
      raiz().querySelector<HTMLButtonElement>('.salida-accion button')!.click();
      await estable();
      await estable();
      expect(consulta()).toEqual({ paso: '2', sede: 'estepa', grupo: '70', dias: '1,3' });
      expect(texto('.salida-dias legend')).toBe('¿Qué días vendrá?');
      expect(texto('#dias-pista')).toBe('Vienen marcados los días con plaza. Desmarca los que no pueda venir; tiene que quedar al menos uno.');
      expect(textos('.casilla-dia .casilla-nombre')).toEqual(['Lunes', 'Martes', 'Miércoles', 'Jueves']);
      expect(textos('.casilla-dia .plazas')).toEqual(['Queda 1 plaza', 'Lista de espera', 'Quedan 3 plazas', 'Lleno']);
      expect(Array.from(raiz().querySelectorAll('.casilla-dia .plazas')).map((p) => p.classList.item(1))).toEqual([
        'plazas--quedan',
        'plazas--espera',
        'plazas--quedan',
        'plazas--lleno',
      ]);
      const casillas = Array.from(raiz().querySelectorAll<HTMLInputElement>('.casilla-dia input'));
      expect(casillas.map((c) => c.checked)).toEqual([true, false, true, false]);
      expect(casillas.map((c) => c.disabled)).toEqual([false, true, false, true]);
      expect(textos('.resumen-fila dd')).toContain('Queda 1 plaza');
      await enviar();
      expect(consulta()['paso']).toBe('3');
      expect(raiz().querySelector('.aviso-espera')).toBeNull();
      normas();
    });

    it('la fila del grupo muestra el mejor día: más libres, si no lista de espera, si no lleno', async () => {
      const fila = async (plazas: Array<ReturnType<typeof PLAZAS.quedan>>): Promise<string | null> => {
        const sitio = seisSedes();
        const dias = ['Lunes', 'Martes', 'Miércoles'];
        sitio.sedes[1].grupos[0] = grupoDePrueba(
          'Peques',
          plazas.map((p, i) => turnoDePrueba(dias[i], '17:00', '17:45', p)),
          { id: 70 },
        );
        borrador(FECHA_PEQUES);
        await abrir(sitio, '/preinscripcion?sede=estepa');
        return texto('.salida .plazas');
      };
      expect(await fila([PLAZAS.lleno, PLAZAS.quedan(2), PLAZAS.quedan(5)])).toBe('Quedan 5 plazas');
    });

    it('la fila del grupo: sin plaza en ningún día, lista de espera si alguien espera en uno', async () => {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba(
        'Peques',
        [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.lleno), turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.espera)],
        { id: 70 },
      );
      borrador(FECHA_PEQUES);
      await abrir(sitio, '/preinscripcion?sede=estepa');
      expect(texto('.salida .plazas')).toBe('Lista de espera');
    });

    it('la fila del grupo: todos los días llenos, «Lleno»', async () => {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba(
        'Peques',
        [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.lleno), turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.quedan(0))],
        { id: 70 },
      );
      borrador(FECHA_PEQUES);
      await abrir(sitio, '/preinscripcion?sede=estepa');
      expect(texto('.salida .plazas')).toBe('Lleno');
    });

    it('prefill con un día lleno en un grupo con plaza: ese día no se marca', async () => {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba(
        'Peques',
        [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.quedan(2)), turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.lleno)],
        { id: 70 },
      );
      borrador(FECHA_PEQUES);
      await abrir(sitio, '/preinscripcion?sede=estepa&grupo=70&dias=3');
      const casillas = Array.from(raiz().querySelectorAll<HTMLInputElement>('.casilla-dia input'));
      expect(casillas.map((c) => c.checked)).toEqual([true, false]);
      expect(casillas.map((c) => c.disabled)).toEqual([false, true]);
    });

    /** Peques con el lunes lleno y el miércoles y el jueves con plaza. */
    function lunesLleno(): SitioWeb {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba(
        'Peques',
        [
          turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.lleno),
          turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.quedan(2)),
          turnoDePrueba('Jueves', '17:00', '17:45', PLAZAS.quedan(3)),
        ],
        { id: 70 },
      );
      return sitio;
    }

    it('el ancla d-primero (foco y error) va en el primer día que se puede elegir, no en uno lleno', async () => {
      borrador(FECHA_PEQUES);
      await abrir(lunesLleno(), '/preinscripcion?sede=estepa&grupo=70');
      const casillas = () => Array.from(raiz().querySelectorAll<HTMLInputElement>('.casilla-dia input'));
      expect(casillas().map((c) => c.id)).toEqual(['', 'd-primero', '']);
      expect(raiz().querySelector<HTMLInputElement>('#d-primero')!.disabled).toBeFalse();
      expect(raiz().querySelector<HTMLInputElement>('#d-primero')!.value).toBe('3');
      await marcar('.casilla-dia:nth-child(2) input');
      await marcar('.casilla-dia:nth-child(3) input');
      await enviar();
      expect(textos('.errores li')).toEqual(['Marca al menos un día. Si no puede venir ninguno, elige otro grupo.']);
      expect(raiz().querySelector('.errores a')!.getAttribute('href')).toContain('#d-primero');
      expect(casillas().map((c) => c.getAttribute('aria-invalid'))).toEqual([null, 'true', null]);
    });

    it('?dias= con un día lleno: se queda con los que tienen plaza; si no queda ninguno, todos los que se pueden elegir', async () => {
      borrador(FECHA_PEQUES);
      await abrir(lunesLleno(), '/preinscripcion?sede=estepa&grupo=70&dias=1,4');
      const marcadas = () => Array.from(raiz().querySelectorAll<HTMLInputElement>('.casilla-dia input')).map((c) => c.checked);
      expect(marcadas()).toEqual([false, false, true]);
      expect(textos('.resumen-fila dd')).not.toContain('Lleno');

      await harness.navigateByUrl('/preinscripcion?sede=estepa&grupo=70&dias=1');
      await estable();
      expect(marcadas()).toEqual([false, true, true]);
    });

    it('sin plaza en ningún día: todos se pueden elegir y el ancla sigue en el primero', async () => {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba(
        'Peques',
        [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.lleno), turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.espera)],
        { id: 70 },
      );
      borrador(FECHA_PEQUES);
      await abrir(sitio, '/preinscripcion?sede=estepa&grupo=70&dias=1');
      const casillas = Array.from(raiz().querySelectorAll<HTMLInputElement>('.casilla-dia input'));
      expect(casillas.map((c) => c.id)).toEqual(['d-primero', '']);
      expect(casillas.map((c) => c.checked)).toEqual([true, false]);
      expect(casillas.map((c) => c.disabled)).toEqual([false, false]);
    });

    it('sin plaza en ningún día: «Ahora no hay plaza» y todos los días se eligen para la lista de espera', async () => {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba(
        'Peques',
        [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.lleno), turnoDePrueba('Miércoles', '17:00', '17:45', PLAZAS.espera)],
        { id: 70 },
      );
      borrador(FECHA_PEQUES);
      await abrir(sitio, '/preinscripcion?sede=estepa');
      expect(texto('.salida-accion button')).toBe('Apuntarme a la lista de espera');
      expect(texto('.salida-nota')).toBe(
        'Ahora no hay plaza. Si te apuntas, entras en la lista de espera de los días que elijas y te avisamos cuando se libere una.',
      );
      raiz().querySelector<HTMLButtonElement>('.salida-accion button')!.click();
      await estable();
      await estable();
      expect(texto('.salida-dias legend')).toBe('¿Para qué días te apuntas a la lista?');
      expect(texto('#dias-pista')).toBe('Vienen marcados todos los días del grupo. Desmarca los que no pueda venir; tiene que quedar al menos uno.');
      expect(textos('.casilla-dia .plazas')).toEqual(['Lleno', 'Lista de espera']);
      const casillas = Array.from(raiz().querySelectorAll<HTMLInputElement>('.casilla-dia input'));
      expect(casillas.map((c) => c.checked)).toEqual([true, true]);
      expect(casillas.map((c) => c.disabled)).toEqual([false, false]);
    });

    it('sin grupo de su edad: otras sedes que lo tienen y el teléfono de la sede', async () => {
      borrador({ dia: '1', mes: '1', anio: '1990' });
      await abrir(seisSedes(), '/preinscripcion?sede=estepa');
      expect(texto('.vacio h2')).toBe('En Estepa no hay grupo para 36 años');
      expect(textos('.otra-nombre')).toEqual(['Herrera', 'Casariche', 'Lora de Estepa', 'Roda de Andalucía', 'Mollina']);
      expect(texto('.vacio-contacto')).toBe('¿Prefieres Estepa o tienes dudas? Llama a la sede al 600 000 002 y te orientamos.');
      expect(raiz().querySelector('.continuar')).toBeNull();
    });
  });

  describe('paso 3: datos y firma', () => {
    it('borrador recuperado; menor: responsable legal, DNI opcional y firma del responsable; normas de la disciplina', async () => {
      await pasoDatosRelleno();
      expect(texto('h1.pregunta-titulo')).toBe('Datos y firma');
      expect(raiz().querySelector<HTMLInputElement>('#nombre')!.value).toBe('Lucía');
      expect(raiz().querySelector<HTMLInputElement>('#dia')!.value).toBe('14');
      expect(texto('#b-tutor')).toBe('Responsable legal');
      expect(texto('label[for="dni"]')).toBe('DNI o NIE (opcional para menores)');
      expect(texto('#firma-quien')).toBe('Firma el responsable legal.');
      expect(texto('#firma-etiqueta')).toBe('Firma');
      expect(texto('#firma-pista')).toBe('Con el dedo, el ratón o un lápiz.');
      expect(texto('#firma-estado')).toBe('Sin firmar');
      expect(raiz().querySelector('#declaro')).toBeNull();
      expect(raiz().querySelector('details.firma-mano')).toBeNull();
      expect(texto('#normas-texto summary')).toBe('Leer las normas (2 puntos)');
      expect(textos('.normas li')).toEqual(['Llega cinco minutos antes.', 'Dobok limpio.']);
      expect(texto('.enviar button')).toBe('Enviar preinscripción');
    });

    it('validación: firma obligatoria, normas, DNI/NIE del responsable y teléfono, con el resumen y cada campo', async () => {
      await pasoDatosRelleno();
      await marcar('#normas');
      await escribir('tutorDni', '1234');
      await escribir('telefono', '+33 612 345 678');
      await enviar();
      expect(textos('.errores li')).toEqual([
        'Introduce un DNI/NIE válido del responsable legal.',
        'Introduce un teléfono de nueve dígitos; puedes añadir el prefijo +34.',
        'Marca que aceptas las normas de la actividad.',
        'Firma en el recuadro.',
      ]);
      expect(texto('.errores h2')).toBe('Revisa estos 4 datos');
      expect(texto('#firma-error')).toBe('Error: Firma en el recuadro.');
      expect(raiz().querySelector('#campo-firma')!.classList).toContain('campo--error');
      expect(raiz().querySelector('#tutorDni')!.getAttribute('aria-invalid')).toBe('true');
      http.expectNone(base);
    });

    it('adulto: sin responsable legal y DNI/NIE obligatorio', async () => {
      await pasoDatosRelleno();
      await escribir('anio', '1990');
      expect(raiz().querySelector('#bloque-tutor')).toBeNull();
      expect(texto('#firma-quien')).toBe('Firma el alumno o la alumna.');
      expect(texto('.aviso-edad')).toBe('Con esta fecha cumple 36 años este año y Peques es de 4 a 6 años. Elegir un grupo de su edad');
      await enviar();
      expect(textos('.errores li')).toContain('El DNI/NIE es obligatorio y debe tener un formato válido.');
    });

    it('envío con plaza: la solicitud de Moiskimdo y la confirmación con referencia; el borrador se borra', async () => {
      await pasoDatosRelleno();
      await firmar();
      expect(texto('#firma-estado')).toBe('Firmado');
      await enviar();
      const peticion = http.expectOne(base);
      const cuerpo = peticion.request.body;
      expect(cuerpo.deporte).toBe('TAEKWONDO');
      expect(cuerpo.turnoIds.length).toBe(2);
      expect(cuerpo.fechaNacimiento).toBe('2021-03-14');
      expect(cuerpo.tutorDni).toBe('X1234567L');
      expect(cuerpo.tieneDiscapacidad).toBeFalse();
      expect(cuerpo.aceptacionNormas).toBeTrue();
      expect(cuerpo.firmanteNombre).toBe('Ana Ruiz Pérez');
      expect(cuerpo.firmaBase64).toMatch(/^data:image\/png;base64,/);
      expect(peticion.request.headers.get('Idempotency-Key')).toBeTruthy();
      peticion.flush(
        { referencia: 'TSS-2026-0142', temporada: '2026-2027', tokenDocumento: 'tok', emailEnviado: false, estado: 'PENDIENTE', plazaAsignada: true, posicionListaEspera: null, mensaje: 'ok' },
        { status: 201, statusText: 'Created' },
      );
      await estable();
      expect(texto('h1#fin-titulo')).toBe('Preinscripción enviada');
      expect(texto('.banda-ref strong')).toBe('TSS-2026-0142');
      expect(texto('.banda-estado')).toBe('Tu preinscripción ya cuenta en las plazas de Peques en Estepa.');
      expect(textos('.ruta-nombre')).toEqual(['Te llega un correo', 'El club la revisa', 'Al primer entrenamiento']);
      expect(textos('.ruta-dir')[1]).toBe('Si falta algo, te llaman al +34 612 345 678.');
      expect(textos('.ruta-dir')[2]).toBe('Lunes y miércoles, 17:00–17:45, en Pabellón municipal. Ropa cómoda y agua; el dobok se pide en el club.');
      expect(raiz().querySelector('.fin-acciones a.boton')!.getAttribute('href')).toBe(`${base}/TSS-2026-0142/documento?token=tok`);
      expect(textos('.ficha-fila dt')).toEqual(['Estado', 'Pueblo', 'Grupo', 'Días y hora', 'Alumno', 'Responsable', 'Contacto']);
      expect(raiz().querySelector('.resumen')).toBeNull();
      expect(raiz().querySelector('a.atras')).toBeNull();
      expect(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION)).toBeNull();
      expect(document.title).toBe('Preinscripción enviada · Taekwondo Sierra Sur');
      // emailEnviado llega siempre false (el correo sale tras guardar): la confirmación no habla de un fallo.
      expect(raiz().textContent!).not.toMatch(/no se (pudo|ha podido) enviar|fall/i);
    });

    it('cada campo de texto con el maxlength de la API; un valor pegado más largo da error en el campo, sin enviar', async () => {
      await pasoDatosRelleno();
      const max = (id: string) => raiz().querySelector(`#${id}`)!.getAttribute('maxlength');
      expect(['nombre', 'apellidos', 'dni', 'direccion', 'tutorNombre', 'tutorDni', 'telefono', 'telefono2', 'email', 'firmante', 'observaciones'].map(max)).toEqual([
        '100',
        '160',
        '9',
        '255',
        '180',
        '9',
        '20',
        '20',
        '180',
        '180',
        '1000',
      ]);
      // El valor programático (como un pegado que el navegador no corte) salta el maxlength: lo para la validación.
      await escribir('apellidos', 'x'.repeat(161));
      await firmar();
      await enviar();
      expect(textos('.errores li')).toEqual(['Los apellidos no pueden superar los 160 caracteres.']);
      expect(texto('#apellidos-error')).toBe('Error: Los apellidos no pueden superar los 160 caracteres.');
      expect(raiz().querySelector('#apellidos')!.getAttribute('aria-invalid')).toBe('true');
      http.expectNone(base);
    });

    describe('lienzo de la firma', () => {
      let observadores: { callback: ResizeObserverCallback; desconectado: boolean }[];
      let originalRO: typeof ResizeObserver;
      let originalDpr: PropertyDescriptor | undefined;

      class ResizeObserverFalso {
        private readonly registro: { callback: ResizeObserverCallback; desconectado: boolean };
        constructor(callback: ResizeObserverCallback) {
          this.registro = { callback, desconectado: false };
          observadores.push(this.registro);
        }
        observe(): void {}
        unobserve(): void {}
        disconnect(): void {
          this.registro.desconectado = true;
        }
      }

      beforeEach(() => {
        observadores = [];
        originalRO = window.ResizeObserver;
        originalDpr = Object.getOwnPropertyDescriptor(window, 'devicePixelRatio');
        (window as { ResizeObserver: unknown }).ResizeObserver = ResizeObserverFalso;
      });

      afterEach(() => {
        window.ResizeObserver = originalRO;
        if (originalDpr) {
          Object.defineProperty(window, 'devicePixelRatio', originalDpr);
        } else {
          delete (window as { devicePixelRatio?: number }).devicePixelRatio;
        }
      });

      const densidad = (valor: number) => Object.defineProperty(window, 'devicePixelRatio', { configurable: true, get: () => valor });
      const lienzo = () => raiz().querySelector<HTMLCanvasElement>('canvas.firma')!;
      const redimensionar = async (ancho: string) => {
        lienzo().style.width = ancho;
        const activo = observadores.filter((o) => !o.desconectado).at(-1)!;
        activo.callback([], {} as ResizeObserver);
        await estable();
      };
      /** Píxeles oscuros (de trazo) del mapa de bits. */
      const oscuros = (x0 = 0, y0 = 0, ancho = lienzo().width, alto = lienzo().height) => {
        const datos = lienzo().getContext('2d')!.getImageData(x0, y0, ancho, alto).data;
        let n = 0;
        for (let i = 0; i < datos.length; i += 4) {
          if (datos[i] < 128) {
            n++;
          }
        }
        return n;
      };

      it('la densidad se limita a 2 y el ancho del mapa de bits a 1600 px; el PNG queda bajo el tope de la API', async () => {
        densidad(3);
        await pasoDatosRelleno();
        lienzo().style.width = '400px';
        raiz().querySelector<HTMLButtonElement>('#borrar-firma')!.click();
        await estable();
        let r = lienzo().getBoundingClientRect();
        expect(lienzo().width).toBe(Math.round(r.width * 2));
        expect(lienzo().height).toBe(Math.round(r.height * 2));

        await redimensionar('1200px');
        r = lienzo().getBoundingClientRect();
        expect(r.width).toBe(1200);
        expect(lienzo().width).toBe(1600);
        expect(lienzo().height).toBe(Math.round((r.height * 1600) / 1200));
        await firmar();
        expect(lienzo().toDataURL('image/png').length).toBeLessThan(700000);
      });

      it('al cambiar de ancho, la firma se repinta a escala y sigue firmada; los trazos nuevos caen bajo el puntero', async () => {
        densidad(1);
        await pasoDatosRelleno();
        lienzo().style.width = '600px';
        raiz().querySelector<HTMLButtonElement>('#borrar-firma')!.click();
        await estable();
        await firmar();
        expect(texto('#firma-estado')).toBe('Firmado');

        await redimensionar('300px');
        expect(lienzo().width).toBe(300);
        expect(texto('#firma-estado')).toBe('Firmado');
        // El vértice (80, 60) del trazo, a la mitad de ancho, cae en (40, 60); nada más allá de la mitad del trazo (x > 72).
        expect(oscuros(38, 58, 5, 5)).toBeGreaterThan(0);
        expect(oscuros(73, 0, 227, lienzo().height)).toBe(0);

        // Un trazo nuevo, en píxeles del lienzo nuevo.
        const r = lienzo().getBoundingClientRect();
        const en = (tipo: string, x: number, y: number) =>
          lienzo().dispatchEvent(new PointerEvent(tipo, { clientX: r.left + x, clientY: r.top + y, pointerId: 2, bubbles: true }));
        en('pointerdown', 200, 100);
        en('pointermove', 260, 100);
        en('pointerup', 260, 100);
        await estable();
        expect(oscuros(228, 98, 5, 5)).toBeGreaterThan(0);

        // Y al volver a ensanchar, los dos trazos se repintan.
        await redimensionar('600px');
        expect(lienzo().width).toBe(600);
        expect(oscuros(78, 58, 5, 5)).toBeGreaterThan(0);
        expect(oscuros(458, 98, 5, 5)).toBeGreaterThan(0);
      });

      it('borrar quita los trazos guardados: tras redimensionar sigue en blanco y sin firmar', async () => {
        densidad(1);
        await pasoDatosRelleno();
        await firmar();
        raiz().querySelector<HTMLButtonElement>('#borrar-firma')!.click();
        await estable();
        expect(texto('#firma-estado')).toBe('Sin firmar');
        expect(oscuros()).toBe(0);
        await redimensionar('320px');
        expect(lienzo().width).toBe(320);
        expect(texto('#firma-estado')).toBe('Sin firmar');
        expect(oscuros()).toBe(0);
      });

      it('se deja de observar al destruir el componente', async () => {
        await pasoDatosRelleno();
        expect(observadores.length).toBe(1);
        expect(observadores[0].desconectado).toBeFalse();
        harness.fixture.destroy();
        expect(observadores[0].desconectado).toBeTrue();
      });

      it('sin ResizeObserver en el navegador, la firma funciona igual', async () => {
        (window as { ResizeObserver?: unknown }).ResizeObserver = undefined;
        await pasoDatosRelleno();
        await firmar();
        expect(texto('#firma-estado')).toBe('Firmado');
        expect(oscuros()).toBeGreaterThan(0);
      });
    });

    it('grupo lleno: aviso de lista de espera y, al enviar, la posición en la lista', async () => {
      const sitio = seisSedes();
      sitio.sedes[1].grupos[0] = grupoDePrueba('Peques', [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.espera)], { id: 70 });
      await pasoDatosRelleno(sitio);
      expect(texto('.aviso-espera strong')).toBe('Este grupo está completo: te apuntas a la lista de espera.');
      expect(texto('.enviar button')).toBe('Enviar y apuntarme a la lista de espera');
      await firmar();
      await enviar();
      http.expectOne(base).flush(
        { referencia: 'TSS-2026-0143', temporada: '2026-2027', tokenDocumento: 'tok', emailEnviado: false, estado: 'EN_LISTA_ESPERA', plazaAsignada: false, posicionListaEspera: 3, mensaje: 'ok' },
        { status: 201, statusText: 'Created' },
      );
      await estable();
      expect(texto('h1#fin-titulo')).toBe('Estás en la lista de espera');
      expect(texto('.banda-estado')).toBe('Todavía no tienes plaza. Estás en la posición 3 de la lista de espera de Peques en Estepa, para el lunes.');
      expect(textos('.ficha-fila dd')[0]).toBe('Lista de espera, sin plaza');
    });

    it('enviando: «Enviando…», desactivado y aria-busy; 400: el mensaje junto al botón, que vuelve a su texto', async () => {
      await pasoDatosRelleno();
      await firmar();
      await enviar();
      const boton = () => raiz().querySelector<HTMLButtonElement>('.enviar button')!;
      expect(boton().textContent!.trim()).toBe('Enviando…');
      expect(boton().disabled).toBeTrue();
      expect(boton().getAttribute('aria-busy')).toBe('true');
      http.expectOne(base).flush({ mensaje: 'Todos los turnos deben ser de la misma sede.' }, { status: 400, statusText: 'Bad Request' });
      await estable();
      expect(raiz().querySelector('.errores')).toBeNull();
      expect(raiz().querySelector('#envio-error')!.getAttribute('role')).toBe('alert');
      expect(texto('#envio-error')).toBe('Todos los turnos deben ser de la misma sede.');
      expect(texto('h1.pregunta-titulo')).toBe('Datos y firma');
      expect(boton().textContent!.trim()).toBe('Enviar preinscripción');
      expect(boton().disabled).toBeFalse();
      expect(boton().getAttribute('aria-busy')).toBeNull();
      await enviar();
      expect(texto('#envio-error')).toBe('');
      http.expectOne(base).flush({ mensaje: 'x' }, { status: 400, statusText: 'Bad Request' });
      await estable();
      expect(texto('#envio-error')).toBe('x');
    });

    it('vista previa: no se envía, avisa junto al botón y el borrador no toca la clave real', async () => {
      const sitio = seisSedes();
      sitio.vistaPrevia = true;
      sessionStorage.setItem(CLAVE_BORRADOR_PREINSCRIPCION, JSON.stringify({ ...DATOS_VACIOS, nombre: 'Real' }));
      sessionStorage.setItem(
        CLAVE_BORRADOR_PREINSCRIPCION_PREVIA,
        JSON.stringify({
          ...DATOS_VACIOS,
          ...FECHA_PEQUES,
          nombre: 'Lucía',
          apellidos: 'García Ruiz',
          direccion: 'C/ Ejemplo, 1, Estepa',
          discapacidad: 'no',
          tutorNombre: 'Ana Ruiz Pérez',
          tutorDni: 'X1234567L',
          telefono: '612 345 678',
          email: 'ana@ejemplo.es',
          normas: true,
          firmante: 'Ana Ruiz Pérez',
        }),
      );
      await abrir(sitio, '/preinscripcion?paso=3&sede=estepa&grupo=70&vista-previa=linea-comarcal');
      normas();
      await estable();
      expect(raiz().querySelector<HTMLInputElement>('#nombre')!.value).toBe('Lucía');
      await escribir('nombre', 'Prueba');
      expect(JSON.parse(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION)!).nombre).toBe('Real');
      expect(JSON.parse(sessionStorage.getItem(CLAVE_BORRADOR_PREINSCRIPCION_PREVIA)!).nombre).toBe('Prueba');
      await firmar();
      await enviar();
      http.expectNone(base);
      expect(raiz().querySelector('.errores')).toBeNull();
      expect(texto('#envio-error')).toBe('Es una vista previa: la preinscripción no se envía.');
      expect(texto('h1.pregunta-titulo')).toBe('Datos y firma');
      sessionStorage.removeItem(CLAVE_BORRADOR_PREINSCRIPCION_PREVIA);
    });

    it('sin normas cargadas no se envía', async () => {
      borrador(FECHA_PEQUES);
      await abrir(seisSedes(), '/preinscripcion?paso=3&sede=estepa&grupo=70');
      http.expectOne(`${base}/public/configuracion/TAEKWONDO`).flush(null, { status: 500, statusText: 'Error' });
      await estable();
      await enviar();
      expect(textos('.errores li')).toContain('No puedes enviar la solicitud hasta que se carguen las normas.');
    });

    it('?paso=3 sin fecha válida vuelve al paso 2', async () => {
      await abrir(seisSedes(), '/preinscripcion?paso=3&sede=estepa&grupo=70');
      expect(texto('h1.pregunta-titulo')).toBe('¿Qué grupo?');
    });
  });

  it('enlaces de los horarios: un grupo a dos horas lleva los días de la segunda salida, que abre elegida', async () => {
    const sitio = seisSedes();
    sitio.sedes[1].grupos[0] = grupoDePrueba(
      'Peques',
      [turnoDePrueba('Lunes', '17:00', '17:45'), turnoDePrueba('Viernes', '18:00', '19:00')],
      { id: 70 },
    );
    const consultas = vistaSalidas(sitio.sedes[1], new TextosTema(sitio), AHORA).map((s) => s.accion.consulta);
    expect(consultas).toEqual([
      { sede: 'estepa', grupo: '70' },
      { sede: 'estepa', grupo: '71' },
      { sede: 'estepa', grupo: '70', dias: '5' },
    ]);
    borrador(FECHA_PEQUES);
    await abrir(sitio, '/preinscripcion?sede=estepa&grupo=70&dias=5');
    expect(texto('.salida--elegida .hora-ini')).toBe('18:00');
    expect(texto('.dia-unico')).toBe('Este grupo entrena un día a la semana: el viernes.');
  });

  it('web sin publicar: «Web aún no publicada», sin el tema', async () => {
    await harness.navigateByUrl('/preinscripcion');
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
    await estable();
    http
      .expectOne(`${environment.apiUrl}/publico/escuela`)
      .flush({ nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: null });
    harness.detectChanges();
    expect(raiz().querySelector('.lc')).toBeNull();
    expect(raiz().querySelector('app-web-sin-publicar a.sp-entrar')!.getAttribute('href')).toBe('/login');
  });
});
