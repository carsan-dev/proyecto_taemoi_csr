import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { environment } from '../../../environments/environment';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { salirSinGuardarGuard } from '../productos-alumno/salir-sin-guardar.guard';
import { MiPerfilComponent } from './mi-perfil.component';
import { MiPerfil } from './mi-perfil.service';

const URL = `${environment.apiUrl}/panel/mi-perfil`;

@Component({ standalone: true, template: '<p>Otra</p>' })
class OtraFalsa {}

function perfil(parcial: Partial<MiPerfil> = {}): MiPerfil {
  return { nombrePreferido: null, nombreBase: 'Lucía M.', cumpleDia: null, cumpleMes: null, cumpleFicha: null, ...parcial };
}

describe('MiPerfilComponent (8b-3o)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let avisos: jasmine.SpyObj<AvisosService>;
  let permisos: jasmine.SpyObj<PermisosSesionService>;
  let confirmar: jasmine.Spy;

  beforeEach(async () => {
    avisos = jasmine.createSpyObj('Avisos', ['exito', 'error', 'info']);
    permisos = jasmine.createSpyObj('Permisos', ['refrescar', 'reiniciar']);
    confirmar = jasmine.createSpy('confirmar').and.resolveTo({ confirmado: false, valor: null });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'panel/mi-perfil', component: MiPerfilComponent, canDeactivate: [salirSinGuardarGuard] },
          { path: 'otra', component: OtraFalsa },
        ]),
        { provide: AvisosService, useValue: avisos },
        { provide: PermisosSesionService, useValue: permisos },
        { provide: ConfirmacionService, useValue: { confirmar } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  const el = () => harness.routeNativeElement as HTMLElement;
  const q = <T extends HTMLElement>(selector: string) => el().querySelector<T>(selector)!;

  async function abrir(datos: MiPerfil = perfil()): Promise<MiPerfilComponent> {
    const pantalla = await harness.navigateByUrl('/panel/mi-perfil', MiPerfilComponent);
    http.expectOne(URL).flush(datos);
    harness.detectChanges();
    return pantalla;
  }

  function escribir(selector: string, texto: string): void {
    const campo = q<HTMLInputElement>(selector);
    campo.value = texto;
    campo.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  function elegir(selector: string, valor: string): void {
    const campo = q<HTMLSelectElement>(selector);
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
    harness.detectChanges();
  }

  const barra = () => el().querySelector('.pendientes');
  const boton = (texto: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('.pendientes button')).find((b) => b.textContent?.includes(texto))!;
  const dias = () => Array.from(q<HTMLSelectElement>('#cumple-dia').options).map((o) => o.value).filter(Boolean);

  it('muestra el esqueleto mientras carga', async () => {
    await harness.navigateByUrl('/panel/mi-perfil', MiPerfilComponent);
    expect(el().querySelector('pn-esqueleto')).not.toBeNull();
    http.expectOne(URL).flush(perfil());
  });

  it('título, subtítulo y las dos tarjetas', async () => {
    await abrir();
    expect(q('h1').textContent).toBe('Mi perfil');
    expect(q('.subtitulo').textContent).toBe('Solo cambia lo que ves en tu panel.');
    expect(Array.from(el().querySelectorAll('h2')).map((h) => h.textContent)).toEqual(['Cómo te llamamos', 'Cumpleaños']);
    expect(el().querySelector('a')).toBeNull();
  });

  it('error de lectura: bloque de error y Reintentar vuelve a pedir', async () => {
    await harness.navigateByUrl('/panel/mi-perfil', MiPerfilComponent);
    http.expectOne(URL).flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    harness.detectChanges();
    expect(el().querySelector('pn-bloque-error')).not.toBeNull();
    q<HTMLButtonElement>('.pn-bloque-error__reintentar').click();
    http.expectOne(URL).flush(perfil({ nombrePreferido: 'Luci' }));
    harness.detectChanges();
    expect(el().querySelector('pn-bloque-error')).toBeNull();
    expect(q<HTMLInputElement>('#nombre-panel').value).toBe('Luci');
  });

  describe('nombre', () => {
    it('frase con nombreBase y contador siempre visible, ligado con aria-describedby', async () => {
      await abrir();
      expect(q('#nombre-ayuda').textContent).toBe('Vacío, verás «Lucía M.».');
      expect(q('#nombre-contador').textContent?.trim()).toBe('0 / 40');
      expect(q('#nombre-panel').getAttribute('aria-describedby')).toBe('nombre-ayuda nombre-contador');
      expect(q('#nombre-panel').getAttribute('maxlength')).toBe('40');
      escribir('#nombre-panel', 'Luci');
      expect(q('#nombre-contador').textContent?.trim()).toBe('4 / 40');
    });

    it('sin nombreBase no hay frase, pero sí contador', async () => {
      await abrir(perfil({ nombreBase: null }));
      expect(q('#nombre-ayuda').textContent).toBe('');
      expect(q('#nombre-contador').textContent?.trim()).toBe('0 / 40');
    });

    it('«opcional» en la etiqueta', async () => {
      await abrir();
      expect(q('label[for="nombre-panel"]').textContent).toContain('Nombre para el panel');
      expect(q('label[for="nombre-panel"] .opcional').textContent).toBe('opcional');
    });
  });

  describe('cumpleaños', () => {
    it('sin ficha: dos selects, meses en minúscula, opción vacía y texto exacto', async () => {
      await abrir();
      const meses = Array.from(q<HTMLSelectElement>('#cumple-mes').options).map((o) => o.textContent?.trim());
      expect(meses[0]).toBe('—');
      expect(meses.slice(1)).toEqual([
        'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
      ]);
      expect(q<HTMLSelectElement>('#cumple-dia').options[0].textContent?.trim()).toBe('—');
      expect(q('#cumple-ayuda').textContent).toBe('¡Ese día te espera una sorpresa!');
      expect(el().textContent).toContain('Día y mes');
    });

    it('con ficha: sin campos, la fecha y la explicación', async () => {
      await abrir(perfil({ cumpleFicha: { dia: 14, mes: 3 } }));
      expect(el().querySelector('#cumple-dia')).toBeNull();
      expect(el().querySelector('#cumple-mes')).toBeNull();
      expect(q('.fecha-ficha').textContent).toBe('14 de marzo');
      expect(el().textContent).toContain('Sale de tu ficha de alumno. Se cambia allí.');
    });

    it('el día ofrece solo los días del mes elegido (febrero hasta 29)', async () => {
      await abrir();
      expect(dias().length).toBe(31);
      elegir('#cumple-mes', '2');
      expect(dias().length).toBe(29);
      elegir('#cumple-mes', '4');
      expect(dias().length).toBe(30);
      elegir('#cumple-mes', '1');
      expect(dias().length).toBe(31);
    });

    it('si el día deja de existir al cambiar de mes, se vacía y el error sale al guardar', async () => {
      await abrir();
      elegir('#cumple-dia', '31');
      elegir('#cumple-mes', '1');
      elegir('#cumple-mes', '4');
      expect(q<HTMLSelectElement>('#cumple-dia').value).toBe('');
      expect(q('#cumple-error').textContent).toBe('');
      boton('Guardar').click();
      harness.detectChanges();
      expect(q('#cumple-error').textContent).toBe('Elige día y mes, o deja los dos vacíos.');
    });

    it('solo uno de los dos: error bajo el campo y no llama a la API', async () => {
      await abrir();
      elegir('#cumple-dia', '5');
      boton('Guardar').click();
      harness.detectChanges();
      http.expectNone(URL);
      expect(q('#cumple-error').textContent).toBe('Elige día y mes, o deja los dos vacíos.');
      expect(q('#cumple-dia').getAttribute('aria-invalid')).toBe('true');
      elegir('#cumple-mes', '5');
      expect(q('#cumple-error').textContent).toBe('');
    });
  });

  describe('barra de cambios', () => {
    it('sin cambios no hay barra; cuenta 1 por el nombre y 1 por el cumpleaños', async () => {
      await abrir();
      expect(barra()).toBeNull();
      escribir('#nombre-panel', 'Luci');
      expect(q('.pendientes-aviso').textContent).toBe('1 cambio sin guardar');
      elegir('#cumple-dia', '14');
      elegir('#cumple-mes', '3');
      expect(q('.pendientes-aviso').textContent).toBe('2 cambios sin guardar');
      expect(q('.pendientes-estado').textContent?.trim()).toBe('2 cambios sin guardar');
    });

    it('el nombre se compara recortado y vacío equivale a null', async () => {
      await abrir(perfil({ nombrePreferido: 'Luci' }));
      escribir('#nombre-panel', '  Luci ');
      expect(barra()).toBeNull();
      escribir('#nombre-panel', '   ');
      expect(q('.pendientes-aviso').textContent).toBe('1 cambio sin guardar');
      await harness.navigateByUrl('/otra');
    });

    it('con ficha el cumpleaños nunca cuenta como cambio', async () => {
      await abrir(perfil({ cumpleFicha: { dia: 14, mes: 3 }, cumpleDia: 14, cumpleMes: 3 }));
      expect(barra()).toBeNull();
      escribir('#nombre-panel', 'Luci');
      expect(q('.pendientes-aviso').textContent).toBe('1 cambio sin guardar');
      await harness.navigateByUrl('/otra');
    });

    it('Descartar devuelve los valores del servidor y quita la barra', async () => {
      await abrir(perfil({ nombrePreferido: 'Luci', cumpleDia: 14, cumpleMes: 3 }));
      escribir('#nombre-panel', 'Otro');
      elegir('#cumple-mes', '5');
      expect(barra()).not.toBeNull();
      boton('Descartar').click();
      harness.detectChanges();
      expect(q<HTMLInputElement>('#nombre-panel').value).toBe('Luci');
      expect(q<HTMLSelectElement>('#cumple-mes').value).toBe('3');
      expect(q<HTMLSelectElement>('#cumple-dia').value).toBe('14');
      expect(barra()).toBeNull();
    });
  });

  describe('guardar', () => {
    it('envía el nombre recortado y el cumpleaños; aviso, refresco de sesión y barra fuera', async () => {
      await abrir();
      escribir('#nombre-panel', '  Luci ');
      elegir('#cumple-dia', '14');
      elegir('#cumple-mes', '3');
      boton('Guardar').click();
      harness.detectChanges();
      expect(boton('Guardando').getAttribute('aria-busy')).toBe('true');
      const peticion = http.expectOne(URL);
      expect(peticion.request.method).toBe('PUT');
      expect(peticion.request.body).toEqual({ nombrePreferido: 'Luci', cumpleDia: 14, cumpleMes: 3 });
      peticion.flush(perfil({ nombrePreferido: 'Luci', cumpleDia: 14, cumpleMes: 3 }));
      harness.detectChanges();
      expect(avisos.exito).toHaveBeenCalledOnceWith('Perfil guardado');
      expect(permisos.refrescar).toHaveBeenCalledTimes(1);
      expect(barra()).toBeNull();
      expect(q<HTMLInputElement>('#nombre-panel').value).toBe('Luci');
    });

    it('un nombre vacío se envía como null', async () => {
      await abrir(perfil({ nombrePreferido: 'Luci' }));
      escribir('#nombre-panel', '');
      boton('Guardar').click();
      const peticion = http.expectOne(URL);
      expect(peticion.request.body).toEqual({ nombrePreferido: null, cumpleDia: null, cumpleMes: null });
      peticion.flush(perfil());
    });

    it('con ficha y la cuenta sin cumpleaños envía lo que llegó (null y null)', async () => {
      await abrir(perfil({ cumpleFicha: { dia: 14, mes: 3 } }));
      escribir('#nombre-panel', 'Luci');
      boton('Guardar').click();
      const peticion = http.expectOne(URL);
      expect(peticion.request.body).toEqual({ nombrePreferido: 'Luci', cumpleDia: null, cumpleMes: null });
      peticion.flush(perfil({ nombrePreferido: 'Luci', cumpleFicha: { dia: 14, mes: 3 } }));
    });

    it('con ficha y la cuenta con cumpleaños guardado, el PUT lleva ese valor y no el de la ficha', async () => {
      await abrir(perfil({ cumpleFicha: { dia: 14, mes: 3 }, cumpleDia: 9, cumpleMes: 5 }));
      escribir('#nombre-panel', 'Luci');
      boton('Guardar').click();
      const peticion = http.expectOne(URL);
      expect(peticion.request.body).toEqual({ nombrePreferido: 'Luci', cumpleDia: 9, cumpleMes: 5 });
      peticion.flush(perfil({ nombrePreferido: 'Luci', cumpleFicha: { dia: 14, mes: 3 }, cumpleDia: 9, cumpleMes: 5 }));
    });

    it('error del PUT: el mensaje de la API visible y no se pierde lo escrito', async () => {
      await abrir();
      escribir('#nombre-panel', 'Luci');
      boton('Guardar').click();
      http.expectOne(URL).flush({ mensaje: 'Ese nombre no es válido.' }, { status: 400, statusText: 'Bad Request' });
      harness.detectChanges();
      expect(q('.error-guardado').textContent).toBe('Ese nombre no es válido.');
      expect(q<HTMLInputElement>('#nombre-panel').value).toBe('Luci');
      expect(barra()).not.toBeNull();
      expect(avisos.exito).not.toHaveBeenCalled();
      expect(permisos.refrescar).not.toHaveBeenCalled();
      expect(boton('Guardar').disabled).toBeFalse();
      await harness.navigateByUrl('/otra');
    });
  });

  describe('salir sin guardar', () => {
    it('sin cambios se sale sin preguntar', async () => {
      await abrir();
      await harness.navigateByUrl('/otra');
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('con cambios pregunta y, si se queda, no navega', async () => {
      await abrir();
      escribir('#nombre-panel', 'Luci');
      await harness.navigateByUrl('/otra');
      expect(confirmar).toHaveBeenCalledTimes(1);
      expect(TestBed.inject(Router).url).toBe('/panel/mi-perfil');
    });
  });
});
