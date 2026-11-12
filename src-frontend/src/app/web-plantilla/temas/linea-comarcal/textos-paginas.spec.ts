import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../environments/environment';
import { salidasDeSede } from '../../comun/horario';
import { DATOS_VACIOS } from '../../comun/preinscripcion/preinscripcion';
import { RELOJ_WEB } from '../../comun/reloj';
import {
  LEGAL_DE_PRUEBA,
  PLAZAS,
  eventoDetalleDePrueba,
  eventoListaDePrueba,
  grupoDePrueba,
  sedeDePrueba,
  sitioConSedes,
  turnoDePrueba,
} from '../../datos/datos-de-prueba';
import { BloqueWeb, EventosWeb, Legal, SitioWeb, TarifaWeb } from '../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../web-plantilla.routes';
import { CLAVE_SEDE_RECORDADA, TextosTema, rellenarHuecos } from './contenido';
import { CLAVE_BORRADOR_PREINSCRIPCION } from './preinscripcion/preinscripcion.component';
import { textoTarifa, textosPreinscripcion, vistaFin } from './preinscripcion/preinscripcion';
import { TEXTOS_LINEA_COMARCAL } from './textos';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

/** Lunes 2 de noviembre de 2026, 16:00 (hora local). */
const AHORA = new Date(2026, 10, 2, 16, 0);

const bloque = (clave: string, texto: string): BloqueWeb => ({ clave, texto, imagenUrl: null, orden: 0 });

const tarifa = (id: number, codigo: string, nombre: string, cambios: Partial<TarifaWeb> = {}): TarifaWeb => ({
  id,
  codigo,
  nombre,
  disciplina: null,
  importe: null,
  importeAdulto: null,
  importeMenor: null,
  ...cambios,
});

/** Seis sedes con tarifa general, familiar y de hermanos, licencia, examen y un cambio anunciado en Herrera. */
function sitio(cambios: Partial<SitioWeb> = {}): SitioWeb {
  const s = sitioConSedes(6, {
    tarifas: [
      tarifa(1, 'GENERAL', 'Taekwondo', { importe: 25 }),
      tarifa(2, 'FAMILIAR', 'Familiar padres e hijos', { importeAdulto: 22, importeMenor: 18 }),
      tarifa(3, 'HERMANOS', 'Hermanos', { importe: 20 }),
    ],
    licencias: [{ disciplina: 'TAEKWONDO', concepto: null, precio: 45 }],
    examenDesde: 20,
    tarifasSede: [{ tarifaId: 1, sedeId: 1, importe: 28, importeAdulto: null, importeMenor: null, vigenteDesde: '2027-01-01' }],
    ...cambios,
  });
  s.sedes[1] = sedeDePrueba(2, 'Estepa', {
    telefono: '600000002',
    grupos: [
      grupoDePrueba('Peques', [turnoDePrueba('Lunes', '17:00', '17:45', PLAZAS.quedan(2))], { id: 70 }),
      grupoDePrueba('Infantil', [turnoDePrueba('Martes', '18:00', '19:00', PLAZAS.lleno)], { id: 71, rangoEdadMin: 7, rangoEdadMax: 11 }),
    ],
  });
  return s;
}

/** Con una sola sede (Estepa) y sin temporada. */
function unaSede(cambios: Partial<SitioWeb> = {}): SitioWeb {
  const s = sitio({ temporada: null, tarifasSede: [], ...cambios });
  s.sedes = [s.sedes[1]];
  return s;
}

/**
 * W12d-bis: el tema lee de `web_bloque` los textos de las otras seis páginas (una sede, tarifas, eventos, un evento,
 * legales y preinscripción) y el botón «Menú». Sin fila, el texto de siempre; con fila, el de la escuela en todas las
 * páginas que comparten la clave; fila vacía, no se pinta; huecos sustituidos después de leer; variantes por datos con
 * una sola clave; leyenda «Lleno» enlazada; legales solo en lo que coincide con el boceto.
 */
describe('Textos de las páginas del tema «La línea comarcal» (W12d-bis)', () => {
  describe('rellenarHuecos', () => {
    it('con todos los datos no toca el texto (tampoco espacios ni puntuación)', () => {
      expect(rellenarHuecos('Una cuota al mes, igual en las {x} sedes.', { x: 'seis' })).toBe('Una cuota al mes, igual en las seis sedes.');
      expect(rellenarHuecos('Hola  ,  {x} .', { x: 'mundo' })).toBe('Hola  ,  mundo .');
      expect(rellenarHuecos('Sin huecos', {})).toBe('Sin huecos');
    });

    it('un dato con «$&» o «$1» va literal', () => {
      expect(rellenarHuecos('Sede {x}.', { x: 'A $& $1' })).toBe('Sede A $& $1.');
    });

    it('hueco sin dato: vacío y limpieza mínima, siempre igual', () => {
      expect(rellenarHuecos('Una cuota al mes, igual en las {x} sedes.', { x: null })).toBe('Una cuota al mes, igual en las sedes.');
      expect(rellenarHuecos('Si falta algo, te llaman al {x}.', {})).toBe('Si falta algo, te llaman al.');
      expect(rellenarHuecos('{x}: puedes apuntarte a la lista de espera', { x: '' })).toBe('puedes apuntarte a la lista de espera');
      expect(rellenarHuecos('Mensualidades (curso {x})', { x: undefined })).toBe('Mensualidades (curso)');
      expect(rellenarHuecos('Estás en la posición {n} de la lista de {grupo}, para {dias}.', { n: null, grupo: 'Peques', dias: '' })).toBe(
        'Estás en la posición de la lista de Peques, para.',
      );
      expect(rellenarHuecos('«{x}» y ¿{x}?', {})).toBe('«» y ¿?');
    });
  });

  describe('textos por defecto', () => {
    it('sin sitio, TextosTema da los del tema', () => {
      const textos = new TextosTema(null);
      expect(textos.t('sede.ver_grupos')).toBe('Ver grupos y horas');
      expect(textos.t('cabecera.menu')).toBe('Menú');
      expect(textos.variante('tarifas.cuota_igual', 'Otra')).toBe('Otra');
    });

    it('las claves legales sin sitio en el tema (o más largas en él) no están entre las que lee', () => {
      const claves = Object.keys(TEXTOS_LINEA_COMARCAL);
      for (const clave of ['legal.aviso.p3', 'legal.priv.h1', 'legal.priv.p1', 'legal.priv.p2', 'legal.cookies.p0', 'legal.normas.p0']) {
        expect(claves).withContext(clave).not.toContain(clave);
      }
      expect(claves.filter((c) => c.startsWith('legal.')).length).toBe(20);
      expect(claves.filter((c) => c.startsWith('pre.')).length).toBe(59);
      expect(claves.filter((c) => c.startsWith('sede.')).length).toBe(23);
      expect(claves.filter((c) => /^tarifas\.(cuota_igual|tambien|incluye_aparte|ver_horarios|rotulo_|titulo_|leyenda_|col_|cada_|hermanos|pie_|licencia|examen|nota_|vacio_)/.test(c)).length).toBe(25);
      expect(claves.filter((c) => c.startsWith('eventos.') && !['eventos.rotulo', 'eventos.titulo', 'eventos.enlace_ver'].includes(c)).length).toBe(10);
      expect(claves.filter((c) => c.startsWith('evento.')).length).toBe(5);
    });
  });

  describe('preinscripción (lógica pura)', () => {
    const sede = () => sitio().sedes[1];
    const datos = { nombre: 'Lucía', apellidos: 'García', telefono: '600 000 000', email: 'a@b.es', tutorNombre: 'Ana' };

    it('sin filas, los textos de siempre; con fila, el de la escuela', () => {
      const porDefecto = textosPreinscripcion(new TextosTema(sitio()));
      expect(porDefecto.tituloPueblo).toBe('¿En qué pueblo?');
      expect(porDefecto.aceptoNormas).toBe('He leído y acepto las normas de la actividad');
      expect(porDefecto.cambiarPueblo).toBe('el pueblo');
      const propia = textosPreinscripcion(new TextosTema(sitio({ bloques: [bloque('pre.acepto', 'Acepto las normas')] })));
      expect(propia.aceptoNormas).toBe('Acepto las normas');
    });

    it('estado de la lista de espera: una clave para las dos variantes; sin posición, el hueco sale vacío', () => {
      const s = sede();
      const salida = salidasDeSede(s).find((x) => x.grupo.id === 71)!;
      const eleccion = { sede: s, salida, dias: salida.dias };
      const respuesta = (posicion: number | null) => ({ referencia: 'R', plazaAsignada: false, posicionListaEspera: posicion });
      const porDefecto = textosPreinscripcion(new TextosTema(sitio()));
      expect(vistaFin(respuesta(3), eleccion, datos, false, porDefecto).estado).toBe(
        'Todavía no tienes plaza. Estás en la posición 3 de la lista de espera de Infantil en Estepa, para el martes.',
      );
      expect(vistaFin(respuesta(null), eleccion, datos, false, porDefecto).estado).toBe(
        'Todavía no tienes plaza. Estás en la lista de espera de Infantil en Estepa, para el martes.',
      );
      const propia = textosPreinscripcion(new TextosTema(sitio({ bloques: [bloque('pre.estado_espera', 'Vas el {n} en {grupo}.')] })));
      expect(vistaFin(respuesta(3), eleccion, datos, false, propia).estado).toBe('Vas el 3 en Infantil.');
      expect(vistaFin(respuesta(null), eleccion, datos, false, propia).estado).toBe('Vas el en Infantil.');
    });

    it('confirmación: título, pasos y texto del correo de la escuela', () => {
      const s = sede();
      const salida = salidasDeSede(s).find((x) => x.grupo.id === 70)!;
      const t = textosPreinscripcion(
        new TextosTema(
          sitio({
            bloques: [
              bloque('pre.fin_plaza', '¡Recibida!'),
              bloque('pre.paso_correo', 'Mira tu correo'),
              bloque('pre.paso_correo_texto', ''),
              bloque('pre.paso_revisa_texto', 'Te llamamos al {x} si hace falta.'),
            ],
          }),
        ),
      );
      const fin = vistaFin({ referencia: 'R', plazaAsignada: true, posicionListaEspera: null }, { sede: s, salida, dias: salida.dias }, datos, false, t);
      expect(fin.titulo).toBe('¡Recibida!');
      expect(fin.pasos[0]).toEqual({ titulo: 'Mira tu correo', texto: '' });
      expect(fin.pasos[1].texto).toBe('Te llamamos al 600 000 000 si hace falta.');
    });

    it('tarifa del resumen: una clave para una y varias sedes; vacía, no se pinta', () => {
      expect(textoTarifa(sitio({ tarifasSede: [] }), AHORA)).toBe('25 € al mes, igual en todas las sedes.');
      expect(textoTarifa(unaSede(), AHORA)).toBe('25 € al mes, para todos los grupos.');
      const fila = [bloque('pre.tarifa_igual', '{x} cada mes.')];
      expect(textoTarifa(sitio({ tarifasSede: [], bloques: fila }), AHORA)).toBe('25 € cada mes.');
      expect(textoTarifa(unaSede({ bloques: fila }), AHORA)).toBe('25 € cada mes.');
      expect(textoTarifa(sitio({ tarifasSede: [], bloques: [bloque('pre.tarifa_igual', '')] }), AHORA)).toBe('');
    });
  });

  describe('páginas', () => {
    const url = `${environment.apiUrl}/publico/web`;
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
      for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
        elemento.remove();
      }
      document.title = tituloAnterior;
    });

    async function abrir(ruta: string, s: SitioWeb, extra?: { ruta: string; datos: object }): Promise<HTMLElement> {
      await harness.navigateByUrl(ruta);
      http.expectOne((r) => r.url === url).flush(s);
      if (extra) {
        http.expectOne((r) => r.url === extra.ruta).flush(extra.datos);
      }
      harness.detectChanges();
      await harness.fixture.whenStable();
      harness.detectChanges();
      return harness.routeNativeElement!;
    }

    const limpio = (e: Element | null) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();
    const textos = (raiz: Element, selector: string) => Array.from(raiz.querySelectorAll(selector)).map(limpio);

    // Una página por test: el sitio de `GET /api/publico/web` se guarda en caché tras la primera petición.
    describe('marco', () => {
      it('«Menú» por defecto', async () => {
        expect(limpio((await abrir('/sedes/estepa', sitio())).querySelector('.menu-texto'))).toBe('Menú');
      });

      it('con `cabecera.menu`, el de la escuela (también fuera de Inicio)', async () => {
        const raiz = await abrir('/tarifas', sitio({ bloques: [bloque('cabecera.menu', 'Secciones')] }));
        expect(limpio(raiz.querySelector('.menu-texto'))).toBe('Secciones');
      });
    });

    describe('una sede', () => {
      it('sin filas, los textos de siempre (cabeceras, leyenda, cómo llegar, ruta y aria-label)', async () => {
        const raiz = await abrir('/sedes/estepa', sitio());
        expect(textos(raiz, '.salidas-cabeza span').slice(0, 3)).toEqual(['Hora', 'Grupo', 'Días y plazas']);
        expect(textos(raiz, '#horarios .rotulo')).toEqual(['Horarios · curso 2026-2027']);
        expect(textos(raiz, '#horarios-titulo')).toEqual(['Grupos y horas']);
        expect(textos(raiz, '.leyenda li')).toEqual(['Quedan plazas', 'Lleno: puedes apuntarte a la lista de espera', 'Ya hay lista de espera']);
        expect(textos(raiz, '.ficha dt')).toEqual(['Lugar', 'Teléfono']);
        expect(textos(raiz, '.ruta-aqui')).toEqual(['Estás aquí']);
        expect(textos(raiz, '.tramo-sentido')).toEqual(['Anterior', 'Siguiente']);
        const boton = raiz.querySelector('.sede-texto .acciones a.boton')!;
        expect(limpio(boton)).toBe('Preinscribirme');
        expect(boton.getAttribute('aria-label')).toBe('Preinscribirme en Estepa');
      });

      it('con filas: el texto de la escuela en su sitio', async () => {
        const raiz = await abrir(
          '/sedes/estepa',
          sitio({
            bloques: [
              bloque('sede.anterior', 'Antes'),
              bloque('sede.ver_grupos', 'Ver horarios'),
              bloque('sede.titulo_horarios', 'Nuestros grupos'),
              bloque('sede.cabeza_hora', 'Horario'),
              bloque('sede.ficha_lugar', 'Sitio'),
              bloque('sede.abrir_mapa', 'Mapa'),
              bloque('sede.rotulo_tarifa', 'Cuota'),
              bloque('sede.estas_aqui', 'Aquí'),
            ],
          }),
        );
        expect(textos(raiz, '.tramo-sentido')).toEqual(['Antes', 'Siguiente']);
        expect(limpio(raiz.querySelector('.sede-texto .acciones a.enlace-flecha'))).toBe('Ver horarios');
        expect(textos(raiz, '#horarios-titulo')).toEqual(['Nuestros grupos']);
        expect(textos(raiz, '.salidas-cabeza span')[0]).toBe('Horario');
        expect(textos(raiz, '.ficha dt')).toEqual(['Sitio', 'Teléfono']);
        expect(limpio(raiz.querySelector('.llegar-acciones a'))).toBe('Mapa (abre en otra pestaña)');
        expect(textos(raiz, '#tarifas-titulo')).toEqual(['Cuota']);
        expect(textos(raiz, '.ruta-aqui')).toEqual(['Aquí']);
      });

      it('sin `sede.preinscribirme_en`, el aria-label de siempre: «<horarios.boton> en <sede>»', async () => {
        const raiz = await abrir('/sedes/estepa', sitio({ bloques: [bloque('horarios.boton', 'Apúntate')] }));
        expect(raiz.querySelector('.sede-texto .acciones a.boton')!.getAttribute('aria-label')).toBe('Apúntate en Estepa');
      });

      it('`sede.preinscribirme_en` da el aria-label; el texto visible sigue siendo `horarios.boton`', async () => {
        const raiz = await abrir(
          '/sedes/estepa',
          sitio({ bloques: [bloque('horarios.boton', 'Apúntate'), bloque('sede.preinscribirme_en', 'Quiero entrenar en {x}')] }),
        );
        const boton = raiz.querySelector('.sede-texto .acciones a.boton')!;
        expect(limpio(boton)).toBe('Apúntate');
        expect(boton.getAttribute('aria-label')).toBe('Quiero entrenar en Estepa');
      });

      const lleno = bloque('horarios.lleno', 'Completo');
      for (const [caso, filas, esperado] of [
        ['sin fila, enlazada a `horarios.lleno`', [lleno], 'Completo: puedes apuntarte a la lista de espera'],
        ['con fila y `{x}`', [lleno, bloque('sede.leyenda_lleno', '{x}, pero hay lista de espera')], 'Completo, pero hay lista de espera'],
        ['con fila sin `{x}`', [lleno, bloque('sede.leyenda_lleno', 'Sin plazas: lista de espera')], 'Sin plazas: lista de espera'],
      ] as [string, BloqueWeb[], string][]) {
        it(`leyenda «Lleno» ${caso}`, async () => {
          expect(textos(await abrir('/sedes/estepa', sitio({ bloques: filas })), '.leyenda li')[1]).toBe(esperado);
        });
      }

      const rotulo = [bloque('sede.rotulo_horarios', 'Horarios del curso {x}')];
      for (const [caso, cambios, esperado] of [
        ['con temporada y fila', { bloques: rotulo }, 'Horarios del curso 2026-2027'],
        ['sin temporada ni fila (variante)', { temporada: null }, 'Horarios'],
        ['sin temporada y con fila: el hueco sale vacío', { temporada: null, bloques: rotulo }, 'Horarios del curso'],
      ] as [string, Partial<SitioWeb>, string][]) {
        it(`rótulo de horarios ${caso}`, async () => {
          expect(textos(await abrir('/sedes/estepa', sitio(cambios)), '#horarios .rotulo')).toEqual([esperado]);
        });
      }

      it('sin clases esta semana: el texto de la escuela', async () => {
        const s = sitio({ bloques: [bloque('sede.sin_clases', 'Vacaciones')] });
        s.sedes[1] = { ...s.sedes[1], grupos: [] };
        expect(limpio((await abrir('/sedes/estepa', s)).querySelector('.proxima'))).toBe('Vacaciones');
      });
    });

    describe('tarifas', () => {
      it('sin filas, los textos de siempre', async () => {
        const raiz = await abrir('/tarifas', sitio());
        expect(limpio(raiz.querySelector('.tarifa-igual'))).toBe('Una cuota al mes, igual en las seis sedes.');
        expect(textos(raiz, '.tarifa-incluye li')).toEqual(['Mensualidad por alumno', 'También tarifa familiar y de hermanos', 'Licencia y exámenes, aparte']);
        expect(textos(raiz, '.cuadro thead th')).toEqual(['Tarifa', 'Quién', 'Importe']);
        expect(textos(raiz, '.cuadro caption')).toEqual(['Mensualidades del curso 2026-2027']);
        expect(textos(raiz, '.cuadro .quien')).toEqual(['Cada alumno', 'Cada adulto', 'Cada menor', 'Desde el segundo hermano']);
        expect(textos(raiz, '.cuadro-pie')).toEqual(['Se paga una mensualidad por alumno, la misma en todos los grupos.']);
        expect(textos(raiz, '.aparte li')).toEqual(['Licencia de taekwondo45 €', 'Derecho de examenSegún grado, desde 20 €']);
        expect(textos(raiz, '.aparte-nota')).toEqual(['No van en la mensualidad: se pagan cuando tocan.']);
        expect(textos(raiz, '.tarifa-mes')).toEqual(['al mes']);
        expect(textos(raiz, '.cuadro small, .lt-importe small').every((x) => x === 'al mes')).toBeTrue();
      });

      it('`tarifas.al_mes` se comparte: banda, cuadro y «Por sede» de Tarifas', async () => {
        const raiz = await abrir('/tarifas', sitio({ bloques: [bloque('tarifas.al_mes', '/mes')] }));
        expect(textos(raiz, '.tarifa-mes')).toEqual(['/mes']);
        const pequenos = textos(raiz, '.cuadro small, .lt-importe small');
        expect(pequenos.length).toBe(10);
        expect(pequenos.every((x) => x === '/mes')).toBeTrue();
      });

      it('`tarifas.al_mes` se comparte: también en la página de la sede', async () => {
        expect(textos(await abrir('/sedes/estepa', sitio({ bloques: [bloque('tarifas.al_mes', '/mes')] })), '.tarifa-mes')).toEqual(['/mes']);
      });

      it('con filas: el texto de la escuela y los huecos sustituidos', async () => {
        const raiz = await abrir(
          '/tarifas',
          sitio({
            bloques: [
              bloque('tarifas.cuota_igual', 'La misma cuota en las {x} sedes.'),
              bloque('tarifas.tambien', 'Además, {x}'),
              bloque('tarifas.col_quien', 'Para'),
              bloque('tarifas.leyenda_cuadro', 'Cuotas {x}'),
              bloque('tarifas.examen_desde', 'desde {x}'),
              bloque('tarifas.licencia', 'Ficha'),
              bloque('tarifas.titulo_sede', 'Sede a sede'),
              bloque('tarifas.ver_horarios', 'Horarios'),
            ],
          }),
        );
        expect(limpio(raiz.querySelector('.tarifa-igual'))).toBe('La misma cuota en las seis sedes.');
        expect(textos(raiz, '.tarifa-incluye li')[1]).toBe('Además, tarifa familiar y de hermanos');
        expect(textos(raiz, '.cuadro thead th')[1]).toBe('Para');
        expect(textos(raiz, '.cuadro caption')).toEqual(['Cuotas 2026-2027']);
        expect(textos(raiz, '.aparte li')).toEqual(['Ficha de taekwondo45 €', 'Derecho de examendesde 20 €']);
        expect(textos(raiz, '#por-sede-titulo')).toEqual(['Sede a sede']);
        expect(textos(raiz, '.tarifas-acciones .enlace-flecha')).toEqual(['Horarios']);
      });

      it('una sede sin filas: la variante de siempre', async () => {
        expect(limpio((await abrir('/tarifas', unaSede())).querySelector('.tarifa-igual'))).toBe('Una cuota al mes, para todos los grupos.');
      });

      it('variantes con una sola clave: cuota con una sede (hueco vacío) y «Cada adulto»/«Cada menor»', async () => {
        const raiz = await abrir(
          '/tarifas',
          unaSede({ bloques: [bloque('tarifas.cuota_igual', 'La misma cuota en las {x} sedes.'), bloque('tarifas.cada_alumno', 'Por persona')] }),
        );
        expect(limpio(raiz.querySelector('.tarifa-igual'))).toBe('La misma cuota en las sedes.');
        expect(textos(raiz, '.cuadro .quien')).toEqual(['Por persona', 'Por persona', 'Por persona', 'Desde el segundo hermano']);
        expect(textos(raiz, '.cuadro caption')).toEqual(['Mensualidades']);
      });

      const vacio = [bloque('tarifas.vacio_texto', 'Pregúntanos.'), bloque('tarifas.vacio_correo', 'Email')];
      it('sin tarifas, con contacto: el texto y el rótulo de la escuela', async () => {
        const raiz = await abrir('/tarifas', sitioConSedes(6, { tarifas: [], bloques: vacio }));
        expect(limpio(raiz.querySelector('.tarifa-lado .tarifa-igual'))).toBe('Pregúntanos.');
        expect(textos(raiz, '.tarifa-contacto dt')).toEqual(['Email', 'Teléfono']);
      });

      it('sin tarifas ni contacto: el texto de la escuela vale también para esta variante', async () => {
        const raiz = await abrir('/tarifas', sitioConSedes(6, { tarifas: [], contacto: { email: null, telefono: null }, bloques: vacio }));
        expect(limpio(raiz.querySelector('.tarifa-lado .tarifa-igual'))).toBe('Pregúntanos.');
      });

      it('sin tarifas ni contacto y sin fila: la variante de siempre', async () => {
        const raiz = await abrir('/tarifas', sitioConSedes(6, { tarifas: [], contacto: { email: null, telefono: null } }));
        expect(limpio(raiz.querySelector('.tarifa-lado .tarifa-igual'))).toBe('Ahora no hay tarifas publicadas. Te la dicen en la sede.');
      });

      it('fila vacía: la nota del cuadro y la de «Aparte» no se pintan', async () => {
        const raiz = await abrir('/tarifas', sitio({ bloques: [bloque('tarifas.pie_cuadro', ''), bloque('tarifas.nota_aparte', '  ')] }));
        expect(raiz.querySelector('.cuadro-pie')).toBeNull();
        expect(raiz.querySelector('.aparte-nota')).toBeNull();
        expect(raiz.querySelector('.cuadro')).not.toBeNull();
      });
    });

    describe('eventos', () => {
      const varios = (): EventosWeb => ({
        proximos: [eventoListaDePrueba(), eventoListaDePrueba({ id: 8, titulo: 'Torneo', fecha: '2026-12-05' })],
        anteriores: [eventoListaDePrueba({ id: 5, titulo: 'Antiguo', fecha: '2025-05-01' })],
      });
      const abrirEventos = (datos: EventosWeb, bloques: BloqueWeb[] = []) =>
        abrir('/eventos', sitioConSedes(6, { bloques }), { ruta: `${url}/eventos`, datos });

      it('sin filas, los textos de siempre', async () => {
        const raiz = await abrirEventos(varios());
        expect(textos(raiz, '.pagina-titular')).toEqual(['Eventos']);
        expect(textos(raiz, '.pagina-entrada')).toEqual(['Exámenes, torneos y exhibiciones del club, con sus circulares y sus fotos.']);
        expect(textos(raiz, '.bloque-cuenta')).toEqual(['2 eventos']);
        expect(textos(raiz, '#anteriores-titulo')).toEqual(['Anteriores']);
      });

      it('con filas, los de la escuela y `{n}` sustituido', async () => {
        const raiz = await abrirEventos(varios(), [
          bloque('eventos.pag_titulo', 'Agenda'),
          bloque('eventos.n_eventos', '{n} citas'),
          bloque('eventos.proximos', 'Lo que viene'),
          bloque('eventos.anteriores', 'Lo que pasó'),
        ]);
        expect(textos(raiz, '.pagina-titular')).toEqual(['Agenda']);
        expect(textos(raiz, '.bloque-cuenta')).toEqual(['2 citas']);
        expect(textos(raiz, '#proximos-titulo')).toEqual(['Lo que viene']);
        expect(textos(raiz, '#anteriores-titulo')).toEqual(['Lo que pasó']);
      });

      it('un solo evento y sin anteriores: los textos de la escuela', async () => {
        const raiz = await abrirEventos({ proximos: [eventoListaDePrueba()], anteriores: [] }, [
          bloque('eventos.un_evento', 'Una cita'),
          bloque('eventos.sin_anteriores', 'Aún nada.'),
        ]);
        expect(textos(raiz, '.bloque-cuenta')).toEqual(['Una cita']);
        expect(textos(raiz, 'p.ev-vacio')).toEqual(['Aún nada.']);
      });

      it('sin próximos: los textos de la escuela; fila vacía no se pinta', async () => {
        const raiz = await abrirEventos({ proximos: [], anteriores: varios().anteriores }, [
          bloque('eventos.sin_proximos_titular', 'Nada a la vista'),
          bloque('eventos.sin_proximos_texto', ''),
          bloque('eventos.ver_anteriores', 'Ver los pasados'),
          bloque('eventos.pag_entrada', ''),
        ]);
        expect(textos(raiz, '.ev-vacio h3')).toEqual(['Nada a la vista']);
        expect(raiz.querySelector('div.ev-vacio p')).toBeNull();
        expect(textos(raiz, '.ev-vacio a')).toEqual(['Ver los pasados']);
        expect(raiz.querySelector('.pagina-entrada')).toBeNull();
      });
    });

    describe('un evento', () => {
      it('volver, documentos, galería y cuenta de la escuela; el visor de fotos sigue fijo', async () => {
        const raiz = await abrir(
          '/eventos/7',
          sitioConSedes(6, {
            bloques: [
              bloque('evento.volver', 'Atrás'),
              bloque('evento.documentos', 'Archivos'),
              bloque('evento.galeria', 'Fotos'),
              bloque('evento.n_fotos', '{n} imágenes'),
              bloque('sede.anterior', 'Antes'),
              bloque('sede.siguiente', 'Después'),
            ],
          }),
          { ruta: `${url}/eventos/7`, datos: eventoDetalleDePrueba() },
        );
        expect(textos(raiz, 'a.atras')).toEqual(['Atrás', 'Atrás']);
        expect(textos(raiz, '#docs-titulo')).toEqual(['Archivos']);
        expect(textos(raiz, '#galeria-titulo')).toEqual(['Fotos']);
        expect(textos(raiz, '.galeria-cuenta')).toEqual(['3 imágenes']);
        expect(textos(raiz, '.visor-pasos button')).toEqual(['Anterior', 'Siguiente']);
      });

      const unaFoto = () => eventoDetalleDePrueba({ fotos: [{ id: 51 }] });
      it('una sola foto sin filas: «1 foto»', async () => {
        const raiz = await abrir('/eventos/7', sitioConSedes(6), { ruta: `${url}/eventos/7`, datos: unaFoto() });
        expect(textos(raiz, '.galeria-cuenta')).toEqual(['1 foto']);
      });

      it('una sola foto: `evento.una_foto`', async () => {
        const raiz = await abrir('/eventos/7', sitioConSedes(6, { bloques: [bloque('evento.una_foto', 'Una imagen')] }), {
          ruta: `${url}/eventos/7`,
          datos: unaFoto(),
        });
        expect(textos(raiz, '.galeria-cuenta')).toEqual(['Una imagen']);
      });
    });

    describe('legales', () => {
      const legal = (): Legal => ({
        ...LEGAL_DE_PRUEBA,
        normas: [{ disciplina: 'TAEKWONDO', disciplinaNombre: 'Taekwondo', version: 1, contenido: { normas: ['Una'] }, instrucciones: null }],
      });
      const abrirLegales = (bloques: BloqueWeb[] = []) => abrir('/legales', sitioConSedes(6, { bloques }), { ruta: `${url}/legal`, datos: legal() });

      it('sin filas, el texto legal de siempre (títulos, ficha y párrafos)', async () => {
        const raiz = await abrirLegales();
        expect(textos(raiz, '.legal-titular')).toEqual(['Información legal']);
        expect(textos(raiz, '.legal-h2')).toEqual(['Aviso legal', 'Privacidad', 'Cookies', 'Normas del club']);
        expect(textos(raiz, '.indice-lista a')).toEqual(['Aviso legal', 'Privacidad', 'Cookies', 'Normas del club']);
        expect(textos(raiz, '#aviso-legal .ficha dt')).toEqual(['Titular', 'CIF', 'Domicilio', 'Correo', 'Teléfono']);
        expect(textos(raiz, '#aviso-legal .legal-h3')).toEqual(['Para qué sirve esta web', 'Propiedad intelectual', 'Responsabilidad', 'Legislación']);
        expect(textos(raiz, '#aviso-legal > p')[2]).toBe(
          'Los textos, fotos, logotipos y diseño de esta web son de Club Taekwondo Sierra Sur o de sus autores. No se pueden copiar ni usar con otros fines sin permiso.',
        );
        expect(textos(raiz, '#privacidad .legal-h3').at(-1)).toBe('Tus derechos');
      });

      it('con filas: títulos, entrada, ficha y párrafos iguales al boceto; `{x}` del titular sustituido', async () => {
        const raiz = await abrirLegales([
          bloque('legal.titulo', 'Lo legal'),
          bloque('legal.entrada', 'Lo que debes saber.'),
          bloque('legal.actualizada', 'Revisado:'),
          bloque('legal.en_esta_pagina', 'Índice'),
          bloque('legal.aviso', 'Aviso'),
          bloque('legal.aviso.p0', 'Datos del titular.'),
          bloque('legal.aviso.h1', 'Para qué'),
          bloque('legal.aviso.p2', 'Todo es de {x}.'),
          bloque('legal.priv.h2', 'Derechos'),
          bloque('legal.ficha_titular', 'Responsable'),
          bloque('legal.ficha_correo', 'Email'),
          bloque('legal.normas', 'Normas'),
        ]);
        expect(textos(raiz, '.legal-titular')).toEqual(['Lo legal']);
        expect(textos(raiz, '.legal-entrada')).toEqual(['Lo que debes saber.']);
        expect(limpio(raiz.querySelector('.actualizado'))).toBe('Revisado: 1 de noviembre de 2026');
        expect(textos(raiz, '.indice-titulo')).toEqual(['Índice']);
        expect(textos(raiz, '.legal-h2')).toEqual(['Aviso', 'Privacidad', 'Cookies', 'Normas']);
        expect(textos(raiz, '.indice-lista a')).toEqual(['Aviso', 'Privacidad', 'Cookies', 'Normas']);
        expect(textos(raiz, '#aviso-legal > p')[0]).toBe('Datos del titular.');
        expect(textos(raiz, '#aviso-legal > p')[2]).toBe('Todo es de Club Taekwondo Sierra Sur.');
        expect(textos(raiz, '#aviso-legal .legal-h3')[0]).toBe('Para qué');
        expect(textos(raiz, '#privacidad .legal-h3').at(-1)).toBe('Derechos');
        expect(textos(raiz, '#aviso-legal .ficha dt')).toEqual(['Responsable', 'CIF', 'Domicilio', 'Email', 'Teléfono']);
      });

      it('las claves sin sitio en el tema, o más largas en él, no cambian nada: el texto legal fijo sigue intacto', async () => {
        const raiz = await abrirLegales([
          bloque('legal.aviso.p3', 'OTRO P3'),
          bloque('legal.priv.h1', 'OTRO H1'),
          bloque('legal.priv.p1', 'OTRO P1'),
          bloque('legal.priv.p2', 'OTRO P2'),
          bloque('legal.cookies.p0', 'OTRO COOKIES'),
          bloque('legal.normas.p0', 'OTRO NORMAS'),
        ]);
        const despues = limpio(raiz.querySelector('.legal-texto'));
        expect(despues).not.toContain('OTRO');
        expect(textos(raiz, '#aviso-legal > p')[3]).toBe(
          'Procuramos que la información esté al día, pero los horarios y las plazas pueden cambiar durante el curso. Si tienes ' +
            'dudas, confírmalo en tu sede. No respondemos del contenido de las webs de otros a las que enlazamos.',
        );
        expect(textos(raiz, '#privacidad .legal-h3')).toEqual([
          'Quién es el responsable',
          'Qué datos tratamos y para qué',
          'Con qué base legal',
          'Menores',
          'Cuánto tiempo los guardamos',
          'A quién se comunican',
          'Tus derechos',
        ]);
        expect(despues).toContain(
          'Puedes pedir el acceso, la rectificación o la supresión de tus datos, oponerte a su tratamiento, limitarlo y pedir su ' +
            'portabilidad escribiendo a info@example.com.',
        );
        expect(textos(raiz, '#cookies > p')[0]).toBe(
          'Esta web solo usa cookies técnicas y el almacenamiento del navegador imprescindible para funcionar. No usa cookies ' +
            'de analítica ni de publicidad, por eso no te pide consentimiento ni muestra un aviso de cookies (artículo 22.2 de la LSSI).',
        );
        expect(raiz.querySelector('.tabla-cookies')).not.toBeNull();
        expect(limpio(raiz.querySelector('#normas > p'))).toBe('Son las normas de la actividad que se aceptan al preinscribirse. Las cuotas están en Tarifas.');
      });
    });

    describe('preinscripción', () => {
      it('paso 1: título, pista, botón y «Paso n de m» de la escuela; el resumen comparte `pre.sin_elegir`', async () => {
        const raiz = await abrir(
          '/preinscripcion',
          sitio({
            bloques: [
              bloque('pre.titulo_pueblo', '¿Dónde?'),
              bloque('pre.pista_pueblo', ''),
              bloque('pre.continuar', 'Seguir'),
              bloque('pre.paso_n', 'Etapa {n}/{total}'),
              bloque('pre.paso_pueblo', 'Sede'),
              bloque('pre.sin_elegir', '—'),
              bloque('pre.resumen_titulo', 'Resumen'),
            ],
          }),
        );
        expect(textos(raiz, 'h1.pregunta-titulo')).toEqual(['¿Dónde?']);
        expect(textos(raiz, '#sede-pista')).toEqual(['']);
        expect(textos(raiz, 'button.continuar')).toEqual(['Seguir']);
        expect(textos(raiz, '.progreso-texto')).toEqual(['Etapa 1/3']);
        expect(textos(raiz, '.progreso-paso')).toEqual(['Sede', 'Grupo', 'Datos']);
        expect(textos(raiz, '#resumen-titulo')).toEqual(['Resumen']);
        expect(textos(raiz, '.resumen-vacio')).toEqual(['—', '—', '—']);
      });

      it('paso 2: leyenda, etiquetas de la fecha y espera de la escuela, con `{x}` sustituido', async () => {
        const raiz = await abrir(
          '/preinscripcion?paso=2&sede=estepa',
          sitio({
            bloques: [
              bloque('pre.fecha_leyenda', 'Nacimiento'),
              bloque('pre.dia', 'D'),
              bloque('pre.mes', 'M'),
              bloque('pre.anio', 'A'),
              bloque('pre.espera_fecha', 'Pon la fecha y verás los grupos de {x}.'),
              bloque('pre.cambiar_pueblo', 'Otro pueblo'),
            ],
          }),
        );
        expect(textos(raiz, '#campo-fecha legend')).toEqual(['Nacimiento']);
        expect(textos(raiz, '.fecha label')).toEqual(['D', 'M', 'A']);
        expect(textos(raiz, '.grupos-espera')).toEqual(['Pon la fecha y verás los grupos de Estepa.']);
        expect(textos(raiz, '.contexto a')).toEqual(['Otro pueblo']);
      });

      it('paso 2 sin grupo para su edad: titular y frase del teléfono de la escuela', async () => {
        sessionStorage.setItem(
          CLAVE_BORRADOR_PREINSCRIPCION,
          JSON.stringify({ ...DATOS_VACIOS, dia: '1', mes: '1', anio: '1990' }),
        );
        const raiz = await abrir(
          '/preinscripcion?paso=2&sede=estepa',
          sitio({
            bloques: [
              bloque('pre.vacio_titulo', 'Sin grupo de {n} años en {x}'),
              bloque('pre.vacio_llamar', 'Llama a {x} al'),
              bloque('pre.vacio_llamar_fin', 'y lo vemos.'),
              bloque('pre.revisar_fecha', 'Cambiar la fecha'),
            ],
          }),
        );
        expect(textos(raiz, '.vacio h2')).toEqual(['Sin grupo de 36 años en Estepa']);
        expect(textos(raiz, '.vacio-contacto')[0]).toBe('Llama a Estepa al 600 000 002 y lo vemos.');
        expect(textos(raiz, '.vacio-contacto a')[1]).toBe('Cambiar la fecha');
      });

      it('paso 3: títulos de bloque, etiquetas y botón de la escuela; la pista del correo vacía no se pinta', async () => {
        sessionStorage.setItem(CLAVE_BORRADOR_PREINSCRIPCION, JSON.stringify({ ...DATOS_VACIOS, dia: '14', mes: '3', anio: '2021' }));
        const raiz = await abrir(
          '/preinscripcion?paso=3&sede=estepa&grupo=70',
          sitio({
            bloques: [
              bloque('pre.titulo_datos', 'Tus datos'),
              bloque('pre.bloque_alumno', 'Quién entrena'),
              bloque('pre.nombre', 'Nombre de pila'),
              bloque('pre.email', 'Correo'),
              bloque('pre.email_pista', ''),
              bloque('pre.acepto', 'Acepto las normas'),
              bloque('pre.enviar', 'Enviar'),
            ],
          }),
        );
        http
          .expectOne(`${environment.apiUrl}/preinscripciones/public/configuracion/TAEKWONDO`)
          .flush({ deporte: 'TAEKWONDO', version: 1, contenido: { normas: ['Una.'] } });
        harness.detectChanges();
        await harness.fixture.whenStable();
        harness.detectChanges();
        expect(textos(raiz, 'h1.pregunta-titulo')).toEqual(['Tus datos']);
        expect(textos(raiz, '#b-alumno')).toEqual(['Quién entrena']);
        expect(textos(raiz, 'label[for="nombre"]')).toEqual(['Nombre de pila']);
        expect(textos(raiz, 'label[for="email"]')).toEqual(['Correo']);
        expect(raiz.querySelector('#email-pista')).toBeNull();
        expect(raiz.querySelector('#email')!.getAttribute('aria-describedby')).toBe('email-error');
        expect(textos(raiz, 'label.marca-casilla')[0]).toBe('Acepto las normas');
        expect(textos(raiz, '.enviar button')).toEqual(['Enviar']);
      });
    });
  });
});
