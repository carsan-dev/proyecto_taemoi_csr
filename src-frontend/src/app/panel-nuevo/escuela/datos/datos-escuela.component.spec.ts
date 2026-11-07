import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { AvisosService } from '../../avisos/avisos.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { MarcaEscuelaPanelService } from '../../marca-escuela-panel.service';
import { salirSinGuardarGuard } from '../../productos-alumno/salir-sin-guardar.guard';
import { DatosEscuela } from './datos-escuela-vista';
import { DatosEscuelaComponent } from './datos-escuela.component';

const URL = `${environment.apiUrl}/escuela/datos`;

@Component({ standalone: true, template: '<p>Otra</p>' })
class OtraFalsa {}

function datos(parcial: Partial<DatosEscuela> = {}): DatosEscuela {
  return {
    nombreLegal: 'Club Deportivo de Ejemplo',
    cif: 'G00000000',
    direccion: 'Calle Ejemplo 1, 41570 Casariche (Sevilla)',
    emailContacto: 'secretaria@club.example',
    telefono: '600 000 000',
    firmanteNombre: 'María Ruiz Ejemplo',
    firmanteCargo: 'Presidenta',
    firmanteNif: '00000000T',
    diaLimiteMensualidad: 5,
    ...parcial,
  };
}

describe('DatosEscuelaComponent (pieza 394)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let avisos: jasmine.SpyObj<AvisosService>;
  let confirmar: jasmine.Spy;

  beforeEach(async () => {
    avisos = jasmine.createSpyObj('Avisos', ['exito', 'error', 'info']);
    confirmar = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: false, valor: null });
    const marca = {
      nombre: 'Club de Ejemplo',
      logo: 'NINGUNO',
      logoVersion: null,
      tema: { primario: '#0b5cad', sobrePrimario: '#fff', sobrePrimario2: '#c4d8eb', primarioTinta: '#0b5cad', primarioSuave: '#e7eff7', ajustado: false },
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'datos-escuela', component: DatosEscuelaComponent, canDeactivate: [salirSinGuardarGuard] },
          { path: 'productosListar', component: OtraFalsa },
          { path: 'otra', component: OtraFalsa },
        ]),
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
        { provide: MarcaEscuelaPanelService, useValue: { marca: () => of(marca), urlLogo: () => null } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const el = () => harness.routeNativeElement as HTMLElement;
  const q = <T extends HTMLElement>(selector: string) => el().querySelector<T>(selector)!;
  const campo = (clave: string) => q<HTMLInputElement>(`#dato-${clave}`);
  const texto = (selector: string) => (q(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const barra = () => el().querySelector<HTMLElement>('.pendientes');

  async function abrir(respuesta: DatosEscuela = datos()): Promise<DatosEscuelaComponent> {
    const pantalla = await harness.navigateByUrl('/datos-escuela', DatosEscuelaComponent);
    http.expectOne(URL).flush(respuesta);
    harness.detectChanges();
    return pantalla;
  }

  function escribir(clave: string, valor: string): void {
    const entrada = campo(clave);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  function salir(clave: string): void {
    campo(clave).dispatchEvent(new Event('blur'));
    harness.detectChanges();
  }

  const pulsarGuardar = () => {
    el().querySelector<HTMLButtonElement>('.pendientes .primario')!.click();
    harness.detectChanges();
  };

  it('carga los datos en los campos, con las etiquetas «(obligatorio)», el contador y sin barra de cambios', async () => {
    await abrir();
    expect(campo('nombreLegal').value).toBe('Club Deportivo de Ejemplo');
    expect(campo('cif').value).toBe('G00000000');
    expect(campo('diaLimiteMensualidad').value).toBe('5');
    expect(texto('label[for="dato-nombreLegal"]')).toBe('Nombre legal (obligatorio)');
    expect(texto('label[for="dato-telefono"]')).toBe('Teléfono (opcional)');
    expect(texto('#dato-nombreLegal-ayuda .contador')).toBe('25 / 150');
    expect(texto('.frase')).toContain('día 6');
    expect(barra()).toBeNull();
    expect(el().querySelector('.primera')).toBeNull();
    expect(texto('.marca-ro')).toContain('los cambia el equipo de Taemoi');
  });

  it('una pantalla, cuatro tarjetas: fiscales, contacto, firmante y cobros, con el enlace a Productos', async () => {
    await abrir();
    expect(Array.from(el().querySelectorAll('.bloque__titulo')).map((t) => t.textContent)).toEqual([
      'Datos fiscales',
      'Contacto',
      'Firmante de los documentos',
      'Cobros',
    ]);
    const enlace = q<HTMLAnchorElement>('.enlace');
    expect(enlace.textContent).toContain('El precio de la licencia de competidor se cambia en Productos');
    expect(enlace.getAttribute('href')).toBe('/productosListar');
  });

  it('error al leer: bloque de error con reintentar', async () => {
    await harness.navigateByUrl('/datos-escuela', DatosEscuelaComponent);
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Error' });
    harness.detectChanges();
    expect(el().textContent).toContain('No se han podido leer los datos de la escuela.');
    expect(el().querySelector('form')).toBeNull();
  });

  it('Moiskimdo: firmante vacío carga sin errores ni barra y sin el aviso de escuela nueva', async () => {
    await abrir(datos({ firmanteNombre: null, firmanteCargo: null, firmanteNif: null }));
    expect(campo('firmanteNombre').value).toBe('');
    expect(el().querySelectorAll('.fv-error').length).toBe(0);
    expect(barra()).toBeNull();
    expect(el().querySelector('.primera')).toBeNull();
  });

  it('el firmante vacío se exige al guardar: la barra pide corregir 3 campos y no llama a la API', async () => {
    await abrir(datos({ firmanteNombre: null, firmanteCargo: null, firmanteNif: null }));
    escribir('telefono', '600 000 001');
    expect(texto('.pendientes-aviso')).toBe('1 cambio sin guardar');
    pulsarGuardar();
    http.expectNone(URL);
    expect(texto('.pendientes-aviso')).toBe('Corrige 3 campos para guardar.');
    expect(texto('#dato-firmanteNombre-ayuda')).toContain('El nombre del firmante es obligatorio.');
    expect(campo('firmanteNombre').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(campo('firmanteNombre'));
  });

  it('escuela nueva: aviso de «Rellena estos datos», día 5 por defecto; desaparece al guardar', async () => {
    const vacios = datos({ nombreLegal: null, cif: null, direccion: null, emailContacto: null, telefono: null, firmanteNombre: null, firmanteCargo: null, firmanteNif: null });
    await abrir(vacios);
    expect(texto('.primera')).toBe('Rellena estos datos antes de cobrar o abrir la preinscripción.');
    expect(campo('diaLimiteMensualidad').value).toBe('5');
    escribir('nombreLegal', 'Club Deportivo de Ejemplo');
    escribir('cif', 'g00000000');
    expect(campo('cif').value).toBe('G00000000');
    escribir('direccion', 'Calle Ejemplo 1');
    escribir('emailContacto', 'secretaria@club.example');
    escribir('firmanteNombre', 'María Ruiz Ejemplo');
    escribir('firmanteCargo', 'Presidenta');
    escribir('firmanteNif', '00000000t');
    pulsarGuardar();
    const peticion = http.expectOne(URL);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(datos({ direccion: 'Calle Ejemplo 1', telefono: null }));
    peticion.flush(datos({ direccion: 'Calle Ejemplo 1', telefono: null }));
    harness.detectChanges();
    expect(el().querySelector('.primera')).toBeNull();
  });

  it('un error de formato sale al salir del campo, con el mensaje de la API; sin tocar, no', async () => {
    await abrir();
    escribir('cif', 'G0000000');
    expect(el().querySelectorAll('.fv-error').length).toBe(0);
    salir('cif');
    expect(texto('#dato-cif-ayuda')).toContain('El CIF no tiene un formato válido.');
    escribir('emailContacto', 'secretaria@club');
    salir('emailContacto');
    expect(texto('#dato-emailContacto-ayuda')).toContain('El email no tiene un formato válido.');
    escribir('diaLimiteMensualidad', '29');
    salir('diaLimiteMensualidad');
    expect(texto('#dato-diaLimiteMensualidad-ayuda')).toContain('Escribe un día del 1 al 28.');
    expect(el().querySelector('.frase')?.hasAttribute('hidden')).toBeTrue();
  });

  it('la barra cuenta los cambios y «Descartar» vuelve a lo guardado', async () => {
    await abrir();
    escribir('telefono', '600 000 001');
    escribir('diaLimiteMensualidad', '10');
    expect(texto('.pendientes-aviso')).toBe('2 cambios sin guardar');
    expect(texto('.frase')).toContain('día 11');
    el().querySelector<HTMLButtonElement>('.pendientes .texto')!.click();
    harness.detectChanges();
    expect(barra()).toBeNull();
    expect(campo('telefono').value).toBe('600 000 000');
    expect(campo('diaLimiteMensualidad').value).toBe('5');
  });

  it('guardar ok: PUT con el formulario completo, aviso de éxito y barra fuera', async () => {
    await abrir();
    escribir('telefono', ' 600 000 001 ');
    escribir('diaLimiteMensualidad', '10');
    pulsarGuardar();
    const peticion = http.expectOne(URL);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual(datos({ telefono: '600 000 001', diaLimiteMensualidad: 10 }));
    // Guardando: campos de solo lectura y botón ocupado
    expect(campo('telefono').readOnly).toBeTrue();
    expect(q('.pendientes .primario').getAttribute('aria-busy')).toBe('true');
    peticion.flush(datos({ telefono: '600 000 001', diaLimiteMensualidad: 10 }));
    harness.detectChanges();
    expect(avisos.exito).toHaveBeenCalledWith('Datos de la escuela guardados.');
    expect(barra()).toBeNull();
    expect(campo('telefono').readOnly).toBeFalse();
    expect(campo('telefono').value).toBe('600 000 001');
  });

  it('un 400 con errores por campo los pone en su campo y no lanza el aviso; al editar el campo se quita', async () => {
    await abrir();
    escribir('cif', 'B1234567J');
    pulsarGuardar();
    http.expectOne(URL).flush(
      { mensaje: 'El CIF no tiene un formato válido.', errores: { cif: 'El CIF no tiene un formato válido.' } },
      { status: 400, statusText: 'Bad Request' }
    );
    harness.detectChanges();
    expect(texto('#dato-cif-ayuda')).toContain('El CIF no tiene un formato válido.');
    expect(campo('cif').getAttribute('aria-invalid')).toBe('true');
    expect(avisos.error).not.toHaveBeenCalled();
    expect(barra()).not.toBeNull();
    escribir('cif', 'B1234567I');
    expect(campo('cif').getAttribute('aria-invalid')).toBe('false');
  });

  it('un fallo sin errores por campo: aviso de error con el mensaje de la API y los cambios siguen ahí', async () => {
    await abrir();
    escribir('telefono', '600 000 001');
    pulsarGuardar();
    http.expectOne(URL).flush({ mensaje: 'Faltan los datos de la escuela o no son válidos.' }, { status: 400, statusText: 'Bad Request' });
    harness.detectChanges();
    expect(avisos.error).toHaveBeenCalledWith('No se han podido guardar los cambios. Faltan los datos de la escuela o no son válidos.');
    expect(avisos.exito).not.toHaveBeenCalled();
    expect(campo('telefono').value).toBe('600 000 001');
    expect(texto('.pendientes-aviso')).toBe('1 cambio sin guardar');
    expect(campo('telefono').readOnly).toBeFalse();
  });

  it('un fallo de red: aviso con el texto del boceto', async () => {
    await abrir();
    escribir('telefono', '600 000 001');
    pulsarGuardar();
    http.expectOne(URL).error(new ProgressEvent('error'), { status: 0 });
    harness.detectChanges();
    expect(avisos.error).toHaveBeenCalledWith(
      'No se han podido guardar los cambios. Siguen aquí. Comprueba la conexión y vuelve a pulsar «Guardar cambios».'
    );
  });

  it('con cambios, salir pregunta «¿Salir sin guardar?»; sin cambios, sale', async () => {
    await abrir();
    await harness.navigateByUrl('/otra');
    expect(confirmar).not.toHaveBeenCalled();
    await harness.navigateByUrl('/datos-escuela', DatosEscuelaComponent);
    http.expectOne(URL).flush(datos());
    harness.detectChanges();
    escribir('telefono', '600 000 001');
    await harness.navigateByUrl('/otra');
    expect(confirmar).toHaveBeenCalledTimes(1);
    expect(confirmar.calls.mostRecent().args[0].titulo).toBe('¿Salir sin guardar?');
    expect(harness.routeNativeElement?.textContent).not.toContain('Otra');
  });
});
