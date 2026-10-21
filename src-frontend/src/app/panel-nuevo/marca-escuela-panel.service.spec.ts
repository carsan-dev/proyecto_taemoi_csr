import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../core/http-context';
import { AuthenticationService } from '../servicios/authentication/authentication.service';
import { MarcaEscuelaPanelService, MarcaPanel } from './marca-escuela-panel.service';

const URL_MARCA = `${environment.apiUrl}/escuela/marca`;

const MARCA: MarcaPanel = {
  nombre: 'Escuela Ficticia Norte',
  logo: 'SUBIDO',
  logoVersion: 42,
  tema: {
    primario: '#3E9B4F',
    sobrePrimario: '#1A2230',
    sobrePrimario2: '#1A2230',
    primarioTinta: '#24853A',
    primarioSuave: '#ECF5ED',
    ajustado: false,
  },
};

const VARIABLES = [
  '--pn-marca-primario',
  '--pn-marca-sobre-primario',
  '--pn-marca-sobre-primario-2',
  '--pn-marca-primario-tinta',
  '--pn-marca-primario-suave',
];

describe('MarcaEscuelaPanelService (8b-T)', () => {
  let servicio: MarcaEscuelaPanelService;
  let http: HttpTestingController;
  let email: BehaviorSubject<string | null>;
  let documento: Document;
  let pn: HTMLElement;

  beforeEach(() => {
    email = new BehaviorSubject<string | null>('gestor@example.test');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthenticationService, useValue: { emailCambio: email.asObservable() } },
      ],
    });
    servicio = TestBed.inject(MarcaEscuelaPanelService);
    http = TestBed.inject(HttpTestingController);
    documento = TestBed.inject(DOCUMENT);
    pn = documento.createElement('div');
    pn.className = 'pn';
    documento.body.appendChild(pn);
  });

  afterEach(() => {
    http.verify();
    pn.remove();
    for (const variable of VARIABLES) {
      documento.documentElement.style.removeProperty(variable);
    }
  });

  it('pide la marca una vez por sesión, con la cookie y errores tratados por quien la usa', async () => {
    const primera = firstValueFrom(servicio.marca());
    const segunda = firstValueFrom(servicio.marca());
    const peticion = http.expectOne(URL_MARCA);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush(MARCA);
    expect(await primera).toEqual(MARCA);
    expect(await segunda).toEqual(MARCA);
    expect(await firstValueFrom(servicio.marca())).toEqual(MARCA);
    http.expectNone(URL_MARCA);
  });

  it('un error no se guarda: la siguiente llamada vuelve a pedir', async () => {
    const fallida = firstValueFrom(servicio.marca()).catch((e) => e);
    http.expectOne(URL_MARCA).flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    expect((await fallida).status).toBe(500);
    const otra = firstValueFrom(servicio.marca());
    http.expectOne(URL_MARCA).flush(MARCA);
    expect(await otra).toEqual(MARCA);
  });

  it('al cambiar de sesión (cierre o entrada de otra cuenta) se vuelve a pedir', async () => {
    const antes = firstValueFrom(servicio.marca());
    http.expectOne(URL_MARCA).flush(MARCA);
    await antes;
    email.next(null);
    email.next('otra@example.test');
    const despues = firstValueFrom(servicio.marca());
    http.expectOne(URL_MARCA).flush({ ...MARCA, nombre: 'Escuela Ficticia Sur' });
    expect((await despues).nombre).toBe('Escuela Ficticia Sur');
  });

  it('escribe las variables de la marca en el elemento .pn, nunca en documentElement', () => {
    servicio.aplicarEn(pn, MARCA.tema);
    expect(pn.style.getPropertyValue('--pn-marca-primario')).toBe('#3E9B4F');
    expect(pn.style.getPropertyValue('--pn-marca-sobre-primario')).toBe('#1A2230');
    expect(pn.style.getPropertyValue('--pn-marca-sobre-primario-2')).toBe('#1A2230');
    expect(pn.style.getPropertyValue('--pn-marca-primario-tinta')).toBe('#24853A');
    expect(pn.style.getPropertyValue('--pn-marca-primario-suave')).toBe('#ECF5ED');
    for (const variable of VARIABLES) {
      expect(documento.documentElement.style.getPropertyValue(variable)).withContext(variable).toBe('');
      expect(documento.body.style.getPropertyValue(variable)).withContext(variable).toBe('');
    }
  });

  it('se niega a escribir en documentElement, body o un elemento sin la clase .pn', () => {
    const suelto = documento.createElement('div');
    for (const elemento of [documento.documentElement, documento.body, suelto]) {
      expect(() => servicio.aplicarEn(elemento, MARCA.tema)).toThrowError(/\.pn/);
    }
    for (const variable of VARIABLES) {
      expect(documento.documentElement.style.getPropertyValue(variable)).toBe('');
      expect(suelto.style.getPropertyValue(variable)).toBe('');
    }
  });

  it('pintar: pide la marca (una vez) y la aplica al .pn; con error deja los colores por defecto', () => {
    servicio.pintar(pn).subscribe();
    http.expectOne(URL_MARCA).flush(MARCA);
    expect(pn.style.getPropertyValue('--pn-marca-primario')).toBe('#3E9B4F');

    const otro = documento.createElement('div');
    otro.className = 'pn';
    servicio.pintar(otro).subscribe();
    http.expectNone(URL_MARCA);
    expect(otro.style.getPropertyValue('--pn-marca-primario')).toBe('#3E9B4F');

    email.next('otra@example.test');
    const tercero = documento.createElement('div');
    tercero.className = 'pn';
    let completado = false;
    servicio.pintar(tercero).subscribe({ complete: () => (completado = true) });
    http.expectOne(URL_MARCA).flush({}, { status: 403, statusText: 'Forbidden' });
    expect(completado).toBeTrue();
    expect(tercero.style.getPropertyValue('--pn-marca-primario')).toBe('');
  });

  it('URL del logo: subido con su versión, el SVG de siempre en LEGADO y ninguna sin logo', () => {
    expect(servicio.urlLogo(MARCA)).toBe(`${environment.apiUrl}/escuela/logo?v=42`);
    expect(servicio.urlLogo({ ...MARCA, logo: 'LEGADO', logoVersion: null })).toBe('assets/media/Logo_escuela.svg');
    expect(servicio.urlLogo({ ...MARCA, logo: 'NINGUNO', logoVersion: null })).toBeNull();
  });
});
