import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../../../environments/environment';
import { sitioConSedes, sitioDePrueba } from '../../../datos/datos-de-prueba';
import { SitioWeb } from '../../../datos/modelos';
import { NoEncontradaLineaComarcalComponent } from './no-encontrada.component';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

/** 404 del tema (boceto `boceto-login-404-sin-publicar.html`, §9 n.º 10): cabecera y pie del tema y dos salidas. */
describe('NoEncontradaLineaComarcalComponent', () => {
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
        provideRouter([
          { path: 'login', component: LoginFalsoComponent },
          { path: '**', data: { webPlantilla: true }, component: NoEncontradaLineaComarcalComponent },
        ]),
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

  async function abrir(respuesta: SitioWeb | 404): Promise<HTMLElement> {
    await harness.navigateByUrl('/no-existe');
    const peticion = http.expectOne(url);
    if (respuesta === 404) {
      peticion.flush(null, { status: 404, statusText: 'Not Found' });
    } else {
      peticion.flush(respuesta);
    }
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  it('cabecera y pie del tema, mensaje y dos salidas; la URL no cambia', async () => {
    const raiz = await abrir(sitioConSedes(6));
    expect(raiz.querySelector('.lc .cabecera')).not.toBeNull();
    expect(raiz.querySelector('.lc .pie')).not.toBeNull();
    expect(raiz.querySelector('h1')!.textContent!.trim()).toBe('No encontramos esta página');
    expect(raiz.querySelector('.error-texto')!.textContent!.trim()).toBe(
      'Puede que el enlace esté mal escrito o que la página ya no exista.',
    );
    expect(raiz.querySelector('.error-codigo')!.textContent!.trim()).toBe('Error 404');
    const salidas = Array.from(raiz.querySelectorAll<HTMLAnchorElement>('.acciones a'));
    expect(salidas.map((a) => [a.textContent!.trim(), a.getAttribute('href')])).toEqual([
      ['Ir al inicio', '/'],
      ['Ver horarios', '/#linea'],
    ]);
    expect(salidas[1].classList).toContain('boton--secundario');
    expect(TestBed.inject(Router).url).toBe('/no-existe');
  });

  it('sin sedes no ofrece «Ver horarios»', async () => {
    const raiz = await abrir(sitioDePrueba());
    expect(Array.from(raiz.querySelectorAll('.acciones a')).map((a) => a.textContent!.trim())).toEqual(['Ir al inicio']);
  });

  it('SEO: título con la escuela y noindex', async () => {
    await abrir(sitioConSedes(1));
    expect(document.title).toBe('No encontramos esta página · Taekwondo Sierra Sur');
    expect(document.head.querySelector('meta[name="robots"][data-web-plantilla]')!.getAttribute('content')).toBe('noindex, nofollow');
  });

  it('web sin publicar: «Web aún no publicada» en la misma URL', async () => {
    const raiz = await abrir(404);
    await harness.fixture.whenStable();
    http
      .expectOne(`${environment.apiUrl}/publico/escuela`)
      .flush({ nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: null });
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/no-existe');
    expect(raiz.querySelector('.lc')).toBeNull();
    expect(raiz.querySelector('app-web-sin-publicar .sp-nombre')?.textContent).toBe('Taekwondo Sierra Sur');
  });
});
