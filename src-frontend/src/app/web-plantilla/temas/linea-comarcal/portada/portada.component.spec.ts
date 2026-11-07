import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { derivarColores } from '../../../comun/colores';
import { RELOJ_WEB } from '../../../comun/reloj';
import {
  PLAZAS,
  grupoDePrueba,
  sedeDePrueba,
  sitioConSedes,
  turnoDePrueba,
} from '../../../datos/datos-de-prueba';
import { BloqueWeb, EventoWeb, SitioWeb } from '../../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { CLAVE_SEDE_RECORDADA } from './linea-sedes.component';
import { PortadaLineaComarcalComponent } from './portada.component';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

/** Lunes 2 de noviembre de 2026, 16:00 (hora local). */
const AHORA = new Date(2026, 10, 2, 16, 0);

const bloque = (clave: string, texto: string | null, orden = 0, imagenUrl: string | null = null): BloqueWeb => ({
  clave,
  texto,
  imagenUrl,
  orden,
});

function evento(id: number, titulo: string, fecha: string, cambios: Partial<EventoWeb> = {}): EventoWeb {
  return { id, titulo, fecha, conImagen: false, hora: null, lugar: null, sedeId: null, sedeNombre: null, ...cambios };
}

/**
 * Portada del tema «La línea comarcal» (W5, boceto `boceto-linea-comarcal.html`): datos reales de `GET /api/publico/web`,
 * variantes de una y seis sedes, plazas (§4.3), bloques por defecto y vacíos (§9 n.º 11), orden, sin publicar → login.
 */
describe('PortadaLineaComarcalComponent', () => {
  const url = `${environment.apiUrl}/publico/web`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let tituloAnterior: string;

  beforeEach(async () => {
    tituloAnterior = document.title;
    localStorage.removeItem(CLAVE_SEDE_RECORDADA);
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
    document.body.classList.remove('lc-menu-abierto');
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
    document.title = tituloAnterior;
  });

  async function abrir(sitio: SitioWeb | 'no-publicada' | 'error', ruta = '/'): Promise<HTMLElement> {
    await harness.navigateByUrl(ruta);
    const peticion = http.expectOne((r) => r.url === url);
    if (sitio === 'no-publicada') {
      peticion.flush(null, { status: 404, statusText: 'Not Found' });
    } else if (sitio === 'error') {
      peticion.flush(null, { status: 500, statusText: 'Error' });
    } else {
      peticion.flush(sitio);
    }
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const textos = (raiz: HTMLElement, selector: string) =>
    Array.from(raiz.querySelectorAll(selector)).map((e) => e.textContent!.replace(/\s+/g, ' ').trim());

  it('la ruta de la portada carga el tema (chunk perezoso) y mientras carga no pinta nada', async () => {
    await harness.navigateByUrl('/');
    expect(harness.routeDebugElement!.componentInstance).toBeInstanceOf(PortadaLineaComarcalComponent);
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('.lc')).toBeNull();
    http.expectOne(url).flush(sitioConSedes(6));
  });

  describe('seis sedes', () => {
    it('portada: rótulo, titular y frase del boceto con los datos; botón «Busca tu pueblo» y «Ver tarifas»', async () => {
      const raiz = await abrir(sitioConSedes(6));
      expect(textos(raiz, '.portada-texto .rotulo')).toEqual(['Escuela de taekwondo · 6 sedes']);
      expect(textos(raiz, 'h1.titular')).toEqual(['Taekwondo en seis pueblos']);
      expect(textos(raiz, '.entrada')).toEqual([
        'Grupos por edad desde los 4 años, en el pabellón o el colegio de cada pueblo. Busca el tuyo y preinscríbete.',
      ]);
      expect(textos(raiz, '.acciones .boton')).toEqual(['Busca tu pueblo']);
      expect(textos(raiz, '.acciones .enlace-flecha')).toEqual(['Ver tarifas']);
    });

    it('línea: seis paradas con su próxima clase; ninguna abierta en móvil; al pulsar se abre y se recuerda', async () => {
      const raiz = await abrir(sitioConSedes(6));
      const paradas = Array.from(raiz.querySelectorAll<HTMLButtonElement>('button.parada-boton'));
      expect(paradas.length).toBe(6);
      expect(textos(raiz, '.parada-nombre')).toEqual(['Herrera', 'Estepa', 'Casariche', 'Lora de Estepa', 'Roda de Andalucía', 'Mollina']);
      expect(textos(raiz, '.parada-cuando')[0]).toBe('HOY17:00');
      expect(raiz.querySelector('.red-pista')).not.toBeNull();
      const escritorio = window.matchMedia('(min-width: 900px)').matches;
      expect(raiz.querySelectorAll('.horario.abierto').length).toBe(escritorio ? 1 : 0);

      paradas[2].click();
      harness.detectChanges();
      expect(paradas[2].getAttribute('aria-expanded')).toBe('true');
      expect(raiz.querySelector('#horario-casariche')!.classList).toContain('abierto');
      expect(raiz.querySelectorAll('.horario.abierto').length).toBe(1);
      expect(localStorage.getItem(CLAVE_SEDE_RECORDADA)).toBe('casariche');

      paradas[2].click();
      harness.detectChanges();
      expect(raiz.querySelectorAll('.horario.abierto').length).toBe(0);
    });

    it('abre la sede recordada', async () => {
      localStorage.setItem(CLAVE_SEDE_RECORDADA, 'mollina');
      const raiz = await abrir(sitioConSedes(6));
      expect(raiz.querySelector('#parada-mollina')!.getAttribute('aria-expanded')).toBe('true');
      expect(raiz.querySelectorAll('.horario.abierto').length).toBe(1);
    });

    it('presentación y cifras; tarifa «Igual en todas las sedes»; contacto con las seis sedes', async () => {
      const raiz = await abrir(sitioConSedes(6));
      expect(textos(raiz, '#presentacion .rotulo')).toEqual(['02 · El club']);
      expect(textos(raiz, '#presentacion-titulo')).toEqual(['Clases cerca de casa']);
      expect(textos(raiz, '#presentacion .cuerpo p')[0]).toContain('Damos clase en seis pueblos de la comarca');
      expect(textos(raiz, '.dato')).toEqual(['6sedes', '4años, edad de inicio', '25 €al mes', '3grupos por sede']);
      expect(textos(raiz, '.tarifa-importe')).toEqual(['25 €']);
      expect(textos(raiz, '.tarifa-igual')).toEqual(['Igual en todas las sedes']);
      expect(textos(raiz, '.tarifa-incluye li')).toEqual(['Mensualidad por alumno', 'Licencia y exámenes aparte, se informan en la sede']);
      expect(textos(raiz, '.ruta-nombre').length).toBe(6);
      expect(raiz.querySelector<HTMLAnchorElement>('a.ruta-nombre')!.getAttribute('href')).toBe('/sedes/herrera');
      expect(textos(raiz, '.contacto-datos dd')).toEqual(['info@example.com', '600 000 000']);
      expect(raiz.querySelector<HTMLAnchorElement>('.contacto-datos a.cifras')!.getAttribute('href')).toBe('tel:+34600000000');
    });

    it('numeración y orden del boceto sin eventos ni profesores ni galería (no se pintan)', async () => {
      const raiz = await abrir(sitioConSedes(6));
      expect(raiz.querySelector('#eventos')).toBeNull();
      expect(raiz.querySelector('#profesores')).toBeNull();
      expect(raiz.querySelector('#galeria')).toBeNull();
      expect(Array.from(raiz.querySelectorAll('main > section')).map((s) => s.id)).toEqual([
        'portada',
        'presentacion',
        'disciplinas',
        'tarifas',
        'contacto',
      ]);
      expect(textos(raiz, '#linea-titulo')).toEqual(['01 · Horarios']);
      expect(textos(raiz, '#disciplinas .rotulo')).toEqual(['03 · Disciplinas']);
      expect(textos(raiz, '#tarifas-titulo')).toEqual(['04 · Tarifas']);
      expect(textos(raiz, '.contacto-principal .rotulo')).toEqual(['05 · Contacto']);
      // El menú no enlaza a una sección que no está.
      expect(textos(raiz, '.nav-lista a')).toEqual(['Horarios', 'Sedes', 'Tarifas', 'Contacto']);
    });

    const tarifaSede = { tarifaId: 1, sedeId: 2, importe: 30, importeAdulto: null, importeMenor: null, vigenteDesde: '2027-01-01' };

    it('una tarifa de sede futura deja «Igual en todas las sedes»', async () => {
      const raiz = await abrir(sitioConSedes(6, { tarifasSede: [tarifaSede] }));
      expect(textos(raiz, '.tarifa-igual')).toEqual(['Igual en todas las sedes']);
    });

    it('una tarifa distinta hoy en una sede quita «Igual en todas las sedes»', async () => {
      const raiz = await abrir(sitioConSedes(6, { tarifasSede: [{ ...tarifaSede, vigenteDesde: '2026-09-01' }] }));
      expect(raiz.querySelector('.tarifa-igual')).toBeNull();
      expect(textos(raiz, '.tarifa-importe')).toEqual(['25 €']);
    });
  });

  describe('una sede', () => {
    it('parada fija y su horario abierto, sin pista; textos de una sede', async () => {
      const raiz = await abrir(sitioConSedes(1));
      expect(raiz.querySelector('button.parada-boton')).toBeNull();
      expect(raiz.querySelector('.parada-fija#parada-estepa')).not.toBeNull();
      expect(raiz.querySelector('#horario-estepa')!.classList).toContain('abierto');
      expect(raiz.querySelector('.red--una')).not.toBeNull();
      expect(raiz.querySelector('.paradas--una')).not.toBeNull();
      expect(raiz.querySelector('.red-pista')).toBeNull();
      expect(textos(raiz, '.portada-texto .rotulo')).toEqual(['Escuela de taekwondo · 1 sede']);
      expect(textos(raiz, 'h1.titular')).toEqual(['Taekwondo en Estepa']);
      expect(textos(raiz, '.entrada')).toEqual(['Grupos por edad desde los 4 años, en Estepa. Mira el horario, elige grupo y preinscríbete.']);
      expect(textos(raiz, '.acciones .boton')).toEqual(['Ver horarios']);
      expect(textos(raiz, '.tarifa-igual')).toEqual(['Para todos los grupos']);
      expect(textos(raiz, '.dato')[0]).toBe('1sede');
      expect(textos(raiz, '#presentacion .cuerpo p')[0]).toContain('Damos clase en Estepa, en el pabellón');
    });

    it('panel de la sede: lugar, dirección, ver la sede, cómo llegar y salidas por hora', async () => {
      const raiz = await abrir(sitioConSedes(1));
      expect(textos(raiz, '.tablero-lugar')).toEqual(['Pabellón municipal C/ del Deporte, 2, Estepa']);
      const enlaces = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.tablero-enlaces a'));
      expect(enlaces[0].getAttribute('href')).toBe('/sedes/estepa');
      expect(enlaces[1].getAttribute('href')).toContain('https://www.google.com/maps/search/?api=1&query=');
      expect(enlaces[1].getAttribute('target')).toBe('_blank');
      expect(textos(raiz, '.salida .grupo-nombre')).toEqual(['Peques', 'Infantil', 'Jóvenes y adultos']);
      expect(textos(raiz, '.salida .grupo-edades')).toEqual(['4 a 6 años', '7 a 11 años', 'Desde 12 años']);
      expect(textos(raiz, '.salida .hora-ini')).toEqual(['17:00', '18:00', '19:30']);
      expect(textos(raiz, '.salida:first-child .dia--si')).toEqual(['L', 'X']);
      expect(textos(raiz, '.salida:first-child .dias-p .oculto')).toEqual(['lunes y miércoles']);
    });
  });

  it('plazas (§4.3): 3, 2, 1, 0 y lista de espera, con su forma y su acción', async () => {
    const sede = sedeDePrueba(1, 'Estepa', {
      grupos: [
        grupoDePrueba('Tres', [turnoDePrueba('Lunes', '16:00', '17:00', PLAZAS.quedan(3))]),
        grupoDePrueba('Dos', [turnoDePrueba('Lunes', '17:00', '18:00', PLAZAS.quedan(2))]),
        grupoDePrueba('Una', [turnoDePrueba('Lunes', '18:00', '19:00', PLAZAS.quedan(1))]),
        grupoDePrueba('Cero', [turnoDePrueba('Lunes', '19:00', '20:00', PLAZAS.lleno)]),
        grupoDePrueba('Espera', [turnoDePrueba('Lunes', '20:00', '21:00', PLAZAS.espera)]),
        grupoDePrueba('Mezcla', [turnoDePrueba('Martes', '21:00', '22:00', PLAZAS.quedan(5)), turnoDePrueba('Jueves', '21:00', '22:00', PLAZAS.quedan(0))]),
      ],
    });
    const raiz = await abrir(sitioConSedes(1, { sedes: [sede] }));
    expect(textos(raiz, '.plazas')).toEqual(['Quedan 3 plazas', 'Quedan 2 plazas', 'Queda 1 plaza', 'Lleno', 'Lista de espera', 'Lleno']);
    expect(Array.from(raiz.querySelectorAll('.plazas')).map((p) => p.className)).toEqual([
      'plazas plazas--quedan',
      'plazas plazas--quedan',
      'plazas plazas--quedan',
      'plazas plazas--lleno',
      'plazas plazas--espera',
      'plazas plazas--lleno',
    ]);
    const acciones = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.salida-accion a'));
    expect(acciones.map((a) => a.textContent!.trim())).toEqual([
      'Preinscribirme',
      'Preinscribirme',
      'Preinscribirme',
      'Apuntarme a la lista de espera',
      'Apuntarme a la lista de espera',
      'Apuntarme a la lista de espera',
    ]);
    expect(acciones.map((a) => a.classList.contains('boton--secundario'))).toEqual([false, false, false, true, true, true]);
    const grupoTres = sede.grupos[0].id;
    expect(acciones[0].getAttribute('href')).toBe(`/preinscripcion?sede=estepa&grupo=${grupoTres}`);
    expect(acciones[0].getAttribute('aria-label')).toBe('Preinscribirme: Tres, 4 a 6 años, Estepa');
  });

  it('textos de horarios propios de la escuela (lleno, espera, botones)', async () => {
    const sede = sedeDePrueba(1, 'Estepa', {
      grupos: [grupoDePrueba('Cero', [turnoDePrueba('Lunes', '19:00', '20:00', PLAZAS.lleno)])],
    });
    const raiz = await abrir(
      sitioConSedes(1, {
        sedes: [sede],
        bloques: [bloque('horarios.lleno', 'Completo'), bloque('horarios.boton_espera', 'Lista de espera, por favor')],
      }),
    );
    expect(textos(raiz, '.plazas')).toEqual(['Completo']);
    expect(textos(raiz, '.salida-accion a')).toEqual(['Lista de espera, por favor']);
  });

  describe('bloques (§9 n.º 4 y 11)', () => {
    it('los que faltan llevan el texto del boceto; los propios mandan', async () => {
      const raiz = await abrir(
        sitioConSedes(6, {
          bloques: [
            bloque('portada.titular', 'Taekwondo en la Sierra Sur'),
            bloque('presentacion.titulo', 'Un club, seis pueblos'),
            bloque('menu.horarios', 'Clases'),
            bloque('cabecera.boton', 'Apúntate'),
          ],
        }),
      );
      expect(textos(raiz, 'h1.titular')).toEqual(['Taekwondo en la Sierra Sur']);
      expect(textos(raiz, '#presentacion-titulo')).toEqual(['Un club, seis pueblos']);
      expect(textos(raiz, '.nav-lista a')[0]).toBe('Clases');
      expect(textos(raiz, '.cabecera-cta')).toEqual(['Apúntate']);
      expect(textos(raiz, '#contacto-titulo')).toEqual(['¿Hablamos?']);
      expect(textos(raiz, '.pie-legal a')).toEqual(['Aviso legal', 'Privacidad', 'Cookies']);
    });

    it('un bloque vacío no se pinta', async () => {
      const raiz = await abrir(
        sitioConSedes(6, {
          bloques: [bloque('portada.frase', ''), bloque('contacto.titulo', '  '), bloque('tarifas.punto2', null), bloque('pie.cookies', '')],
        }),
      );
      expect(raiz.querySelector('.entrada')).toBeNull();
      expect(raiz.querySelector('#contacto-titulo')).toBeNull();
      expect(textos(raiz, '.tarifa-incluye li')).toEqual(['Mensualidad por alumno']);
      expect(textos(raiz, '.pie-legal a')).toEqual(['Aviso legal', 'Privacidad']);
    });

    it('profesores, galería y foto de portada desde los bloques; sin foto, monograma', async () => {
      const raiz = await abrir(
        sitioConSedes(6, {
          bloques: [
            bloque('portada.foto', null, 0, 'https://cdn/portada.jpg'),
            bloque('portada.alt', 'Niños en fila en el tatami'),
            bloque('profesor.1.nombre', 'Antonio Ruiz Carmona'),
            bloque('profesor.1.cargo', 'Cinturón negro 5.º dan'),
            bloque('profesor.1.foto', null, 0, 'https://cdn/antonio.jpg'),
            bloque('profesor.2.nombre', 'Manuel Ortega Pozo'),
            bloque('galeria.1', 'Clase', 0, 'https://cdn/g1.jpg'),
            bloque('galeria.2', 'Patada', 0, 'https://cdn/g2.jpg'),
          ],
        }),
      );
      const foto = raiz.querySelector<HTMLImageElement>('.portada-foto img')!;
      expect(foto.getAttribute('src')).toBe('https://cdn/portada.jpg');
      expect(foto.getAttribute('alt')).toBe('Niños en fila en el tatami');
      expect(textos(raiz, '.profe-nombre')).toEqual(['Antonio Ruiz Carmona', 'Manuel Ortega Pozo']);
      expect(textos(raiz, '.profe-grado')).toEqual(['Cinturón negro 5.º dan']);
      expect(textos(raiz, '.monograma')).toEqual(['MO']);
      expect(textos(raiz, '.galeria-n')).toEqual(['01', '02']);
      expect(raiz.querySelector('.galeria li')!.className).toBe('g-grande');
      expect(textos(raiz, '#profesores .rotulo')).toEqual(['04 · Profesores']);
      expect(textos(raiz, '#galeria-titulo')).toEqual(['05 · Galería']);
    });

    it('orden: las movibles cambian entre sí por `orden`; las fijas siguen en su sitio', async () => {
      const raiz = await abrir(
        sitioConSedes(6, {
          bloques: [
            bloque('galeria.1', 'Clase', 1, 'https://cdn/g1.jpg'),
            bloque('profesor.1.nombre', 'Antonio Ruiz', 2),
            bloque('presentacion.texto', 'Texto propio.', 3),
          ],
        }),
      );
      expect(Array.from(raiz.querySelectorAll('main > section')).map((s) => s.id)).toEqual([
        'portada',
        'galeria',
        'disciplinas',
        'profesores',
        'presentacion',
        'tarifas',
        'contacto',
      ]);
      expect(textos(raiz, '#presentacion .cuerpo p')).toEqual(['Texto propio.']);
    });
  });

  it('eventos: los 3 próximos con fecha, día de la semana, lugar y hora si los hay', async () => {
    const raiz = await abrir(
      sitioConSedes(6, {
        proximosEventos: [
          evento(1, 'Examen de cinturones', '2026-11-14', { lugar: 'Pabellón cubierto municipal', sedeNombre: 'Estepa', hora: '10:00' }),
          evento(2, 'Torneo comarcal infantil', '2026-11-29'),
          evento(3, 'Exhibición de Navidad', '2026-12-12', { hora: '18:00' }),
          evento(4, 'Cuarto', '2026-12-20'),
        ],
      }),
    );
    expect(textos(raiz, '.evento-titulo')).toEqual(['Examen de cinturones', 'Torneo comarcal infantil', 'Exhibición de Navidad']);
    expect(textos(raiz, '.evento-dia')).toEqual(['14 nov', '29 nov', '12 dic']);
    expect(textos(raiz, '.evento-semana')).toEqual(['sábado', 'domingo', 'sábado']);
    expect(textos(raiz, '.evento-lugar')).toEqual(['Pabellón cubierto municipal, Estepa · 10:00', '18:00']);
    expect(raiz.querySelector('.evento a')!.getAttribute('href')).toBe('/eventos/1');
    expect(textos(raiz, '.nav-lista a')).toContain('Eventos');
  });

  it('marco: color de la escuela con contraste AA e iniciales sin logo', async () => {
    const raiz = await abrir(sitioConSedes(6, { escuela: { nombre: 'Taekwondo Sierra Sur', colorPrimario: '#F2C230', logoVersion: null } }));
    const lc = raiz.querySelector<HTMLElement>('.lc')!;
    const c = derivarColores('#F2C230');
    expect(lc.style.getPropertyValue('--boton-fondo')).toBe(c.botonFondo);
    expect(lc.style.getPropertyValue('--boton-tinta')).toBe(c.botonTinta);
    expect(lc.style.getPropertyValue('--escuela-trazo')).toBe(c.trazo);
    expect(textos(raiz, '.logo')).toEqual(['TSS']);
    expect(textos(raiz, '.marca-nombre span')).toEqual(['Taekwondo', 'Sierra Sur']);
    expect(textos(raiz, '.pie-fila p')[0]).toBe('© 2026 Taekwondo Sierra Sur');
  });

  it('marco: con logo, la imagen de la API', async () => {
    const raiz = await abrir(sitioConSedes(6));
    expect(raiz.querySelector('.logo img')!.getAttribute('src')).toBe(`${environment.apiUrl}/publico/escuela/logo?v=42`);
    expect(raiz.querySelector('.logo img')!.getAttribute('alt')).toBe('Logo de Taekwondo Sierra Sur');
  });

  it('menú en móvil: abre, cambia el texto, bloquea el fondo y Escape lo cierra con el foco en el botón', async () => {
    const raiz = await abrir(sitioConSedes(6));
    const boton = raiz.querySelector<HTMLButtonElement>('.menu-boton')!;
    boton.click();
    harness.detectChanges();
    expect(boton.getAttribute('aria-expanded')).toBe('true');
    expect(boton.textContent!.trim()).toBe('Cerrar');
    expect(raiz.querySelector('.nav')!.classList).toContain('abierta');
    expect(document.body.classList).toContain('lc-menu-abierto');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    harness.detectChanges();
    expect(boton.getAttribute('aria-expanded')).toBe('false');
    expect(document.body.classList).not.toContain('lc-menu-abierto');
    expect(document.activeElement).toBe(boton);
  });

  it('SEO de la portada (canonical en la raíz y título de la escuela)', async () => {
    await abrir(sitioConSedes(6));
    expect(document.title).toBe('Taekwondo Sierra Sur');
    const canonical = document.head.querySelector('link[rel="canonical"][data-web-plantilla]')!.getAttribute('href');
    expect(canonical).toBe(`${location.origin}/`);
    const datos = Array.from(document.head.querySelectorAll('script[type="application/ld+json"][data-web-plantilla]'));
    expect(datos.length).toBe(6);
  });

  it('ancla en la URL (/#contacto): se lleva la sección a la vista al tener datos', async () => {
    const llevar = spyOn(Element.prototype, 'scrollIntoView');
    const raiz = await abrir(sitioConSedes(6), '/#contacto');
    await harness.fixture.whenStable();
    expect(llevar).toHaveBeenCalled();
    expect(llevar.calls.mostRecent().object as unknown).toBe(raiz.querySelector('#contacto'));
  });

  it('web sin publicar (404 de la API): va al login sin dejar la portada en el historial', async () => {
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigateByUrl').and.callThrough();
    await abrir('no-publicada');
    await harness.fixture.whenStable();
    expect(navegar).toHaveBeenCalledWith('/login', { replaceUrl: true });
    expect(router.url).toBe('/login');
    expect(harness.routeNativeElement!.querySelector('.login')).not.toBeNull();
  });

  it('error de la API: no pinta nada', async () => {
    const raiz = await abrir('error');
    expect(raiz.querySelector('.lc')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/');
  });
});
