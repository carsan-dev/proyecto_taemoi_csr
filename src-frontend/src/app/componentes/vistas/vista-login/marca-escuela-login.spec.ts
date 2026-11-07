import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom, lastValueFrom, of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../../core/http-context';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EscuelaPublica, MarcaEscuelaLoginService, inicialesEscuela } from './marca-escuela-login.service';
import { ESPERA_MARCA_MS, VistaLoginComponent } from './vista-login.component';

const URL_ESCUELA = `${environment.apiUrl}/publico/escuela`;
const SVG_LEGADO = 'assets/media/Logo_escuela.svg';
const TEMA = { primario: '#0B5CAD', sobrePrimario: '#FFFFFF' };

const SIERRA_SUR_SIN_LOGO: EscuelaPublica = { nombre: 'Taekwondo Sierra Sur', logo: 'NINGUNO', logoVersion: null, tema: TEMA };
const SIERRA_SUR_CON_LOGO: EscuelaPublica = { nombre: 'Taekwondo Sierra Sur', logo: 'SUBIDO', logoVersion: 42, tema: TEMA };
const MOISKIMDO: EscuelaPublica = {
  nombre: 'Moiskimdo',
  logo: 'LEGADO',
  logoVersion: null,
  tema: { primario: '#1B2B2E', sobrePrimario: '#FFFFFF' },
};

describe('Marca de la escuela en el login (fase 4, pieza 5)', () => {
  let fixture: ComponentFixture<VistaLoginComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VistaLoginComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthenticationService, useValue: { comprobarLogueado: () => false } },
        { provide: Router, useValue: { navigate: jasmine.createSpy('navigate') } },
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VistaLoginComponent);
  });

  afterEach(() => http.verify());

  const pagina = (): HTMLElement => fixture.nativeElement.querySelector('.login-page');
  const logoIzquierda = (): HTMLImageElement | null => fixture.nativeElement.querySelector('.login-left img.brand-logo');
  const filaMovil = (): HTMLElement | null => fixture.nativeElement.querySelector('.marca-movil');

  function responder(escuela: EscuelaPublica): void {
    fixture.detectChanges();
    const peticion = http.expectOne(URL_ESCUELA);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush(escuela);
    fixture.detectChanges();
  }

  it('mientras llega la escuela: el hueco del SVG de siempre, oculto, y sin fila móvil', () => {
    fixture.detectChanges();

    expect(pagina().getAttribute('data-logo')).toBe('pendiente');
    expect(logoIzquierda()!.getAttribute('src')).toContain(SVG_LEGADO);
    expect(getComputedStyle(logoIzquierda()!).visibility).toBe('hidden');
    expect(filaMovil()).toBeNull();
    http.expectOne(URL_ESCUELA);
  });

  it('LEGADO (Moiskimdo): el SVG de siempre, visible, con el degradado de siempre y sin fila móvil', () => {
    responder(MOISKIMDO);

    expect(pagina().getAttribute('data-logo')).toBe('legado');
    expect(logoIzquierda()!.getAttribute('src')).toBe('../../../../assets/media/Logo_escuela.svg');
    expect(logoIzquierda()!.alt).toBe('Logo de la escuela');
    expect(getComputedStyle(logoIzquierda()!).visibility).toBe('visible');
    expect(pagina().style.getPropertyValue('--marca-fondo')).toBe('');
    expect(fixture.nativeElement.querySelector('.marca-texto')).toBeNull();
    expect(filaMovil()).toBeNull();
  });

  it('SUBIDO: el PNG público con su versión en la URL, sobre el color de la escuela', () => {
    responder(SIERRA_SUR_CON_LOGO);

    expect(pagina().getAttribute('data-logo')).toBe('subido');
    expect(logoIzquierda()!.getAttribute('src')).toBe(`${URL_ESCUELA}/logo?v=42`);
    expect(logoIzquierda()!.alt).toBe('Logo de Taekwondo Sierra Sur');
    expect(pagina().style.getPropertyValue('--marca-fondo')).toBe('#0B5CAD');
    expect(pagina().style.getPropertyValue('--marca-sobre')).toBe('#FFFFFF');
    expect(fixture.nativeElement.querySelector(`img[src*="${SVG_LEGADO}"]`)).toBeNull();
    expect(filaMovil()!.querySelector('img')!.getAttribute('src')).toBe(`${URL_ESCUELA}/logo?v=42`);
    expect(filaMovil()!.textContent).toContain('Taekwondo Sierra Sur');
  });

  it('SUBIDO que no carga: el nombre en texto, nunca el logo de Moiskimdo', () => {
    responder(SIERRA_SUR_CON_LOGO);

    logoIzquierda()!.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(pagina().getAttribute('data-logo')).toBe('ninguno');
    expect(fixture.nativeElement.querySelector('.monograma').textContent.trim()).toBe('TSS');
    expect(fixture.nativeElement.querySelector(`img[src*="${SVG_LEGADO}"]`)).toBeNull();
  });

  it('NINGUNO: placa con las iniciales y el nombre en texto, sobre el color de la escuela', () => {
    responder(SIERRA_SUR_SIN_LOGO);

    expect(pagina().getAttribute('data-logo')).toBe('ninguno');
    expect(logoIzquierda()).toBeNull();
    const monograma: HTMLElement = fixture.nativeElement.querySelector('.login-left .monograma');
    expect(monograma.textContent!.trim()).toBe('TSS');
    expect(monograma.getAttribute('aria-hidden')).toBe('true');
    expect(fixture.nativeElement.querySelector('.login-left .marca-texto-nombre').textContent.trim()).toBe('Taekwondo Sierra Sur');
    expect(pagina().style.getPropertyValue('--marca-fondo')).toBe('#0B5CAD');
    expect(filaMovil()!.querySelector('.marca-movil-logo')!.textContent!.trim()).toBe('TSS');
    expect(filaMovil()!.querySelector('.marca-movil-nombre')!.textContent!.trim()).toBe('Taekwondo Sierra Sur');
  });

  it('si la petición falla: el SVG de siempre', () => {
    fixture.detectChanges();
    http.expectOne(URL_ESCUELA).flush('error', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(pagina().getAttribute('data-logo')).toBe('legado');
    expect(logoIzquierda()!.getAttribute('src')).toContain(SVG_LEGADO);
    expect(filaMovil()).toBeNull();
  });

  it('si la petición tarda: el SVG de siempre al vencer la espera; una respuesta tardía aún se aplica', fakeAsync(() => {
    fixture.detectChanges();
    const peticion = http.expectOne(URL_ESCUELA);

    tick(ESPERA_MARCA_MS - 1);
    fixture.detectChanges();
    expect(pagina().getAttribute('data-logo')).toBe('pendiente');

    tick(1);
    fixture.detectChanges();
    expect(pagina().getAttribute('data-logo')).toBe('legado');
    expect(getComputedStyle(logoIzquierda()!).visibility).toBe('visible');

    peticion.flush(SIERRA_SUR_SIN_LOGO);
    fixture.detectChanges();
    expect(pagina().getAttribute('data-logo')).toBe('ninguno');
  }));

  it('un color que no es #RRGGBB no se escribe', () => {
    responder({ ...SIERRA_SUR_SIN_LOGO, tema: { primario: 'red;background:url(x)', sobrePrimario: '#FFFFFF' } });

    expect(pagina().style.getPropertyValue('--marca-fondo')).toBe('');
    expect(pagina().style.getPropertyValue('--marca-sobre')).toBe('#FFFFFF');
  });
});

describe('MarcaEscuelaLoginService', () => {
  let servicio: MarcaEscuelaLoginService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(MarcaEscuelaLoginService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide una vez y comparte la respuesta', async () => {
    const primera = lastValueFrom(servicio.escuela());
    http.expectOne(URL_ESCUELA).flush(MOISKIMDO);
    expect(await primera).toEqual(MOISKIMDO);

    expect(await firstValueFrom(servicio.escuela())).toEqual(MOISKIMDO);
    http.expectNone(URL_ESCUELA);
  });

  it('desuscribirse antes de la respuesta cancela la petición en vuelo', () => {
    const suscripcion = servicio.escuela().subscribe();
    const peticion = http.expectOne(URL_ESCUELA);

    suscripcion.unsubscribe();

    expect(peticion.cancelled).toBeTrue();
  });

  it('un error no se guarda: la siguiente llamada vuelve a pedir', async () => {
    const primera = firstValueFrom(servicio.escuela());
    http.expectOne(URL_ESCUELA).flush('error', { status: 503, statusText: 'Unavailable' });
    await expectAsync(primera).toBeRejected();

    const segunda = firstValueFrom(servicio.escuela());
    http.expectOne(URL_ESCUELA).flush(SIERRA_SUR_SIN_LOGO);
    expect(await segunda).toEqual(SIERRA_SUR_SIN_LOGO);
  });

  it('respuestas raras: el SVG de siempre', () => {
    expect(servicio.marcaDe(null).tipo).toBe('legado');
    expect(servicio.marcaDe({ ...SIERRA_SUR_CON_LOGO, logoVersion: null }).tipo).toBe('legado');
    expect(servicio.marcaDe({ ...SIERRA_SUR_SIN_LOGO, nombre: '  ' }).tipo).toBe('legado');
    expect(servicio.marcaDe({ ...MOISKIMDO, logo: 'OTRO' as any }).tipo).toBe('legado');
  });

  it('iniciales: hasta tres, saltando las palabras en minúscula', () => {
    expect(inicialesEscuela('Taekwondo Sierra Sur')).toBe('TSS');
    expect(inicialesEscuela('Club de Taekwondo Estepa')).toBe('CTE');
    expect(inicialesEscuela('Escuela Ficticia Norte de Prueba')).toBe('EFN');
    expect(inicialesEscuela('moiskimdo')).toBe('M');
  });
});
