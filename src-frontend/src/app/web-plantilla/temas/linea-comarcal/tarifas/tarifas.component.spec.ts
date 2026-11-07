import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { RELOJ_WEB } from '../../../comun/reloj';
import { sitioConSedes } from '../../../datos/datos-de-prueba';
import { SitioWeb, TarifaWeb } from '../../../datos/modelos';
import { WEB_PLANTILLA_ROUTES } from '../../../web-plantilla.routes';
import { TarifasLineaComarcalComponent } from './tarifas.component';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

/** Lunes 2 de noviembre de 2026, 16:00 (hora local). */
const AHORA = new Date(2026, 10, 2, 16, 0);

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

/** Las del boceto: general, familiar (adulto y menor) y hermanos; licencia y examen desde 20 €. */
function conTarifas(n: number, cambios: Partial<SitioWeb> = {}): SitioWeb {
  return sitioConSedes(n, {
    tarifas: [
      tarifa(1, 'GENERAL', 'Taekwondo', { importe: 25 }),
      tarifa(2, 'FAMILIAR', 'Familiar padres e hijos', { importeAdulto: 22, importeMenor: 18 }),
      tarifa(3, 'HERMANOS', 'Hermanos', { importe: 20 }),
    ],
    licencias: [{ disciplina: 'TAEKWONDO', concepto: 'Licencia federativa 2027', precio: 45 }],
    examenDesde: 20,
    ...cambios,
  });
}

/**
 * Página de tarifas del tema «La línea comarcal» (W5, boceto `boceto-linea-comarcal-tarifas.html`): banda, cuadro,
 * mensualidad por sede con cambios anunciados, licencia y examen («desde»), sin tarifas, una y seis sedes.
 */
describe('TarifasLineaComarcalComponent', () => {
  const url = `${environment.apiUrl}/publico/web`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let tituloAnterior: string;

  beforeEach(async () => {
    tituloAnterior = document.title;
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
    for (const elemento of Array.from(document.head.querySelectorAll('[data-web-plantilla]'))) {
      elemento.remove();
    }
    document.title = tituloAnterior;
  });

  async function abrir(sitio: SitioWeb): Promise<HTMLElement> {
    await harness.navigateByUrl('/tarifas');
    http.expectOne((r) => r.url === url).flush(sitio);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const textos = (raiz: HTMLElement, selector: string) =>
    Array.from(raiz.querySelectorAll(selector)).map((e) => e.textContent!.replace(/\s+/g, ' ').trim());

  it('banda: la mensualidad, igual en las seis sedes, qué incluye, Preinscribirme y Ver horarios', async () => {
    const raiz = await abrir(conTarifas(6));
    expect(harness.routeDebugElement!.componentInstance).toBeInstanceOf(TarifasLineaComarcalComponent);
    expect(textos(raiz, 'h1#tarifas-titulo')).toEqual(['Tarifas']);
    expect(textos(raiz, '.tarifa-importe')).toEqual(['25 €']);
    expect(textos(raiz, '.tarifa-igual')).toEqual(['Una cuota al mes, igual en las seis sedes.']);
    expect(textos(raiz, '.tarifa-incluye li')).toEqual([
      'Mensualidad por alumno',
      'También tarifa familiar y de hermanos',
      'Licencia y exámenes, aparte',
    ]);
    expect(raiz.querySelector('.tarifas a.boton--blanco')!.getAttribute('href')).toBe('/preinscripcion');
    expect(raiz.querySelector('.tarifas a.enlace-flecha')!.getAttribute('href')).toBe('/#linea');
    expect(document.title).toBe('Tarifas · Taekwondo Sierra Sur');
  });

  it('cuadro: una fila por tarifa; la familiar con adulto y menor; hermanos «desde el segundo»', async () => {
    const raiz = await abrir(conTarifas(6));
    expect(textos(raiz, '.cuadro caption')).toEqual(['Mensualidades del curso 2026-2027']);
    expect(textos(raiz, '.cuadro tbody th')).toEqual(['Taekwondo', 'Familiar padres e hijos', 'Hermanos']);
    expect(textos(raiz, '.cuadro .quien')).toEqual(['Cada alumno', 'Cada adulto', 'Cada menor', 'Desde el segundo hermano']);
    expect(textos(raiz, '.cuadro .importe')).toEqual(['25 €al mes', '22 €al mes', '18 €al mes', '20 €al mes']);
    const familiar = raiz.querySelector('tbody.grupo-tarifa th[scope="rowgroup"]')!;
    expect(familiar.getAttribute('rowspan')).toBe('2');
  });

  it('parada a parada: la misma en todas, sin cambios ni leyenda', async () => {
    const raiz = await abrir(conTarifas(6));
    expect(textos(raiz, '.lt-nombre')).toEqual(['Herrera', 'Estepa', 'Casariche', 'Lora de Estepa', 'Roda de Andalucía', 'Mollina']);
    expect(raiz.querySelector('a.lt-nombre')!.getAttribute('href')).toBe('/sedes/herrera');
    expect(textos(raiz, '.lt-importe')).toEqual(Array(6).fill('25 € al mes'));
    expect(raiz.querySelector('.excepcion')).toBeNull();
    expect(raiz.querySelector('.lt-leyenda')).toBeNull();
  });

  it('una sede con cambio anunciado: su rombo «Desde enero de 2027: 28 € al mes» y la leyenda; hoy sigue igual', async () => {
    const raiz = await abrir(
      conTarifas(6, {
        tarifasSede: [{ tarifaId: 1, sedeId: 1, importe: 28, importeAdulto: null, importeMenor: null, vigenteDesde: '2027-01-01' }],
      }),
    );
    expect(textos(raiz, '.lt-importe')[0]).toBe('25 € al mes');
    expect(textos(raiz, '.excepcion')).toEqual(['Desde enero de 2027: 28 € al mes']);
    expect(textos(raiz, '.lt-leyenda')).toEqual(['Cambio de tarifa anunciado en esa sede']);
    expect(textos(raiz, '.tarifa-igual')).toEqual(['Una cuota al mes, igual en las seis sedes.']);
  });

  it('una sede que hoy paga distinto: su importe en su parada y la banda sin «igual en las seis sedes»', async () => {
    const raiz = await abrir(
      conTarifas(6, {
        tarifasSede: [{ tarifaId: 1, sedeId: 1, importe: 28, importeAdulto: null, importeMenor: null, vigenteDesde: '2026-09-01' }],
      }),
    );
    expect(textos(raiz, '.lt-importe')).toEqual(['28 € al mes', ...Array(5).fill('25 € al mes')]);
    expect(raiz.querySelector('.excepcion')).toBeNull();
    expect(textos(raiz, '.tarifa-igual')).toEqual(['Una cuota al mes.']);
  });

  it('aparte: licencia y examen «desde» el menor precio', async () => {
    const raiz = await abrir(conTarifas(6));
    expect(textos(raiz, '.aparte li')).toEqual(['Licencia federativa 202745 €', 'Derecho de examenSegún grado, desde 20 €']);
    expect(raiz.querySelector('.cierre a.boton')!.getAttribute('href')).toBe('/preinscripcion');
  });

  it('sin producto de examen: solo la licencia', async () => {
    const raiz = await abrir(conTarifas(6, { examenDesde: null }));
    expect(textos(raiz, '.aparte-concepto')).toEqual(['Licencia federativa 2027']);
  });

  it('sin licencia ni examen: sin «Aparte» ni la línea de la banda que lo dice', async () => {
    const raiz = await abrir(conTarifas(6, { licencias: [], examenDesde: null }));
    expect(raiz.querySelector('#aparte')).toBeNull();
    expect(textos(raiz, '.tarifa-incluye li')).toEqual(['Mensualidad por alumno', 'También tarifa familiar y de hermanos']);
  });

  it('una sede: «para todos los grupos» y sin parada a parada', async () => {
    const raiz = await abrir(conTarifas(1));
    expect(textos(raiz, '.tarifa-igual')).toEqual(['Una cuota al mes, para todos los grupos.']);
    expect(raiz.querySelector('#por-sede')).toBeNull();
  });

  it('sin tarifas: «Pregunta la cuota en tu sede» con el correo y el teléfono; sin cuadro ni parada a parada', async () => {
    const raiz = await abrir(conTarifas(6, { tarifas: [] }));
    expect(textos(raiz, '.tarifa-vacio')).toEqual(['Pregunta la cuota en tu sede']);
    expect(textos(raiz, '.tarifa-contacto dt')).toEqual(['Correo', 'Teléfono']);
    expect(raiz.querySelector('.tarifa-contacto a')!.getAttribute('href')).toBe('mailto:info@example.com');
    expect(raiz.querySelector('#cuadro')).toBeNull();
    expect(raiz.querySelector('#por-sede')).toBeNull();
    expect(raiz.querySelector('.tarifa-importe')).toBeNull();
  });

  it('cabecera: «Tarifas» lleva a esta página y la marca como actual', async () => {
    const raiz = await abrir(conTarifas(6));
    const enlace = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.nav-lista a')).find((a) => a.textContent!.trim() === 'Tarifas')!;
    expect(enlace.getAttribute('href')).toBe('/tarifas');
    expect(enlace.getAttribute('aria-current')).toBe('page');
  });
});
