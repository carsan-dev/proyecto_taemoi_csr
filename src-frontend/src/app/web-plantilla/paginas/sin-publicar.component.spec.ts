import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../environments/environment';
import { EscuelaPublica } from '../../componentes/vistas/vista-login/marca-escuela-login.service';
import { RELOJ_WEB } from '../comun/reloj';
import { DATOS_NO_ENCONTRADA, WEB_PLANTILLA_ROUTES, noEncontradaDelTema } from '../web-plantilla.routes';

@Component({ standalone: true, template: '<p class="login">login</p>' })
class LoginFalsoComponent {}

const ESCUELA: EscuelaPublica = {
  nombre: 'Taekwondo Sierra Sur',
  logo: 'NINGUNO',
  logoVersion: null,
  tema: { primario: '#B3261E', sobrePrimario: '#FFFFFF' },
};

/**
 * «Web aún no publicada» (boceto `boceto-login-404-sin-publicar.html`, sección 3; §9 n.º 10): con la API en 404, cada
 * ruta de la plantilla enseña la misma página sin tema, con el nombre (o el logo) de la escuela y «Entrar» al login.
 */
describe('SinPublicarComponent · web aún no publicada', () => {
  const web = `${environment.apiUrl}/publico/web`;
  const escuela = `${environment.apiUrl}/publico/escuela`;
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          ...WEB_PLANTILLA_ROUTES,
          { path: 'login', component: LoginFalsoComponent },
          { path: '**', data: DATOS_NO_ENCONTRADA, loadComponent: noEncontradaDelTema },
        ]),
        { provide: RELOJ_WEB, useValue: () => new Date(2026, 10, 2, 16, 0) },
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
  });

  /** Abre la ruta con la web sin publicar; la marca se pide solo la primera vez (se comparte). */
  async function abrir(ruta: string, marca: EscuelaPublica | 'error' | null = ESCUELA): Promise<HTMLElement> {
    await harness.navigateByUrl(ruta);
    for (const peticion of http.match((r) => r.url.startsWith(web))) {
      peticion.flush(null, { status: 404, statusText: 'Not Found' });
    }
    harness.detectChanges();
    await harness.fixture.whenStable();
    if (marca === 'error') {
      http.expectOne(escuela).flush(null, { status: 500, statusText: 'Error' });
    } else if (marca) {
      http.expectOne(escuela).flush(marca);
    }
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  const texto = (raiz: HTMLElement, selector: string) => raiz.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();

  it('en todas las rutas de la plantilla: nombre, aviso y «Entrar» a /login, sin tema y en la misma URL', async () => {
    const rutas = ['/', '/sedes/estepa', '/tarifas', '/preinscripcion', '/eventos', '/eventos/3', '/legales', '/no-existe'];
    for (const [i, ruta] of rutas.entries()) {
      const raiz = await abrir(ruta, i === 0 ? ESCUELA : null);
      expect(raiz.querySelector('.lc')).withContext(ruta).toBeNull();
      expect(raiz.querySelector('main#contenido')).withContext(ruta).toBeNull();
      expect(texto(raiz, 'app-web-sin-publicar .sp-nombre')).withContext(ruta).toBe('Taekwondo Sierra Sur');
      expect(texto(raiz, 'app-web-sin-publicar h1')).withContext(ruta).toBe('Esta web todavía no está publicada');
      expect(raiz.querySelector('app-web-sin-publicar a.sp-entrar')!.getAttribute('href')).withContext(ruta).toBe('/login');
      expect(TestBed.inject(Router).url).withContext(ruta).toBe(ruta);
      expect(document.head.querySelector('meta[name="robots"][data-web-plantilla]')?.getAttribute('content'))
        .withContext(ruta)
        .toBe('noindex, nofollow');
    }
  });

  it('sin logo: iniciales sobre el color de la escuela; pie con el año y el nombre', async () => {
    const raiz = await abrir('/');
    expect(texto(raiz, '.sp-mono')).toBe('TSS');
    expect(raiz.querySelector('.sp')!.getAttribute('data-logo')).toBe('ninguno');
    const estilo = (raiz.querySelector('.sp') as HTMLElement).style;
    expect(estilo.getPropertyValue('--sp-fondo')).toBe('#B3261E');
    expect(estilo.getPropertyValue('--sp-sobre')).toBe('#FFFFFF');
    expect(texto(raiz, '.sp-pie')).toBe('© 2026 Taekwondo Sierra Sur');
  });

  it('con logo subido: el logo con su texto alternativo', async () => {
    const raiz = await abrir('/', { ...ESCUELA, logo: 'SUBIDO', logoVersion: 9 });
    const logo = raiz.querySelector<HTMLImageElement>('.sp-mono img')!;
    expect(logo.getAttribute('src')).toBe(`${escuela}/logo?v=9`);
    expect(logo.alt).toBe('Logo de Taekwondo Sierra Sur');
    expect(raiz.querySelector('.sp')!.getAttribute('data-logo')).toBe('subido');
  });

  it('«Entrar» lleva al login', async () => {
    const raiz = await abrir('/tarifas');
    raiz.querySelector<HTMLAnchorElement>('a.sp-entrar')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/login');
  });

  it('si la marca falla: el aviso y «Entrar» siguen, sin nombre', async () => {
    const raiz = await abrir('/', 'error');
    expect(raiz.querySelector('.sp-marca')).toBeNull();
    expect(raiz.querySelector('.sp-pie')).toBeNull();
    expect(raiz.querySelector('a.sp-entrar')!.getAttribute('href')).toBe('/login');
  });
});
