import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import Swal from 'sweetalert2';

import { AltaEscuelaService, DisciplinaPlantilla, EscuelaCreada } from '../../../servicios/plataforma/alta-escuela.service';
import { PlataformaNuevaEscuelaComponent } from './plataforma-nueva-escuela.component';

describe('PlataformaNuevaEscuelaComponent', () => {
  let fixture: ComponentFixture<PlataformaNuevaEscuelaComponent>;
  let servicio: jasmine.SpyObj<AltaEscuelaService>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;

  const LISTA: DisciplinaPlantilla[] = [
    { codigo: 'TKD', nombre: 'Taekwondo', conGrados: true, tarifas: 3, productos: 1 },
    { codigo: 'PIL', nombre: 'Pilates', conGrados: false, tarifas: 1, productos: 2 },
  ];

  const el = (): HTMLElement => fixture.nativeElement;
  const texto = (): string => el().textContent!.replace(/\s+/g, ' ');
  const campo = (id: string): HTMLInputElement => el().querySelector<HTMLInputElement>(`#${id}`)!;
  const casilla = (codigo: string): HTMLInputElement => el().querySelector<HTMLInputElement>(`[data-codigo="${codigo}"] input`)!;
  const boton = (etiqueta: string): HTMLButtonElement =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent!.includes(etiqueta)) as HTMLButtonElement;

  function escribir(id: string, valor: string): void {
    const entrada = campo(id);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function marcar(codigo: string, marcada = true): void {
    const c = casilla(codigo);
    c.checked = marcada;
    c.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function rellenar(): void {
    escribir('ne-nombre', '  Escuela Norte ');
    escribir('ne-emailContacto', 'norte@correo.es');
    escribir('ne-sede', 'Casariche');
    escribir('ne-propietarioNombre', 'Ana');
    escribir('ne-propietarioApellidos', 'Gil');
    escribir('ne-propietarioEmail', 'ana@correo.es');
    marcar('PIL');
    marcar('TKD');
  }

  async function enviar(): Promise<void> {
    el().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    servicio = jasmine.createSpyObj<AltaEscuelaService>('AltaEscuelaService', ['disciplinas', 'crear']);
    servicio.disciplinas.and.returnValue(of(LISTA));
    location = jasmine.createSpyObj<Location>('Location', ['back']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    router.navigate.and.resolveTo(true);

    await TestBed.configureTestingModule({
      imports: [PlataformaNuevaEscuelaComponent],
      providers: [
        { provide: AltaEscuelaService, useValue: servicio },
        { provide: Location, useValue: location },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PlataformaNuevaEscuelaComponent);
    fixture.detectChanges();
  });

  it('pinta cabecera, tarjetas y una fila por disciplina con singular y plural', () => {
    expect(texto()).toContain('Plataforma');
    expect(el().querySelector('h1')!.textContent).toContain('Nueva escuela');
    const filas = el().querySelectorAll('.disciplina');
    expect(filas.length).toBe(2);
    expect(filas[0].textContent!.replace(/\s+/g, ' ')).toContain('Taekwondo Con grados · 3 tarifas base · 1 producto');
    expect(filas[1].textContent!.replace(/\s+/g, ' ')).toContain('Pilates Sin grados · 1 tarifa base · 2 productos');
    expect(texto()).toContain('Las tarifas base llegan desactivadas');
    expect(texto()).toContain('0 / 150');
    expect(texto()).toContain('0 / 100');
  });

  it('muestra el esqueleto mientras carga las disciplinas', () => {
    const pendiente = new Subject<DisciplinaPlantilla[]>();
    servicio.disciplinas.and.returnValue(pendiente);
    const otro = TestBed.createComponent(PlataformaNuevaEscuelaComponent);
    otro.detectChanges();
    expect(otro.nativeElement.querySelector('app-skeleton-card')).not.toBeNull();
    expect(otro.nativeElement.querySelector('.disciplina')).toBeNull();
  });

  it('error de carga: aviso con Reintentar que vuelve a pedir la lista', () => {
    servicio.disciplinas.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture.componentInstance.cargar();
    fixture.detectChanges();
    expect(el().querySelector('[role=alert]')!.textContent).toContain('No se pudo cargar la lista de disciplinas');
    servicio.disciplinas.and.returnValue(of(LISTA));
    boton('Reintentar').click();
    fixture.detectChanges();
    expect(el().querySelectorAll('.disciplina').length).toBe(2);
  });

  it('contadores y resumen siguen lo escrito', () => {
    escribir('ne-nombre', 'Norte');
    expect(texto()).toContain('5 / 150');
    expect(el().querySelector('.barra-resumen')!.textContent!.trim()).toBe('');
    marcar('TKD');
    expect(el().querySelector('.barra-resumen')!.textContent!.trim()).toBe('1 disciplina');
    escribir('ne-propietarioEmail', 'ana@correo.es');
    marcar('PIL');
    expect(el().querySelector('.barra-resumen')!.textContent!.trim()).toBe('2 disciplinas · se invitará a ana@correo.es');
  });

  it('enviar vacío: errores en línea por campo, descritos con aria-describedby, sin llamar al servicio', async () => {
    await enviar();
    expect(servicio.crear).not.toHaveBeenCalled();
    expect(el().querySelector('#ne-nombre-error')!.textContent).toContain('Escribe el nombre de la escuela');
    expect(el().querySelector('#ne-emailContacto-error')!.textContent).toContain('correo de contacto');
    expect(el().querySelector('#ne-sede-error')!.textContent).toContain('primera sede');
    expect(el().querySelector('#ne-propietarioEmail-error')!.textContent).toContain('email del propietario');
    expect(el().querySelector('#ne-disciplinas-error')!.textContent).toContain('Marca al menos una disciplina');
    expect(campo('ne-nombre').getAttribute('aria-invalid')).toBe('true');
    expect(campo('ne-nombre').getAttribute('aria-describedby')).toContain('ne-nombre-error');
    expect(document.activeElement === campo('ne-nombre') || el().contains(document.activeElement)).toBeTrue();
  });

  it('rechaza emails mal escritos', async () => {
    rellenar();
    escribir('ne-emailContacto', 'sin-arroba');
    escribir('ne-propietarioEmail', 'otro@');
    await enviar();
    expect(servicio.crear).not.toHaveBeenCalled();
    expect(el().querySelector('#ne-emailContacto-error')!.textContent).toContain('correo válido');
    expect(el().querySelector('#ne-propietarioEmail-error')!.textContent).toContain('email válido');
  });

  it('el error de un campo desaparece al volver a escribir en él', async () => {
    await enviar();
    escribir('ne-nombre', 'N');
    expect(el().querySelector('#ne-nombre-error')!.hasAttribute('hidden')).toBeTrue();
    expect(campo('ne-nombre').getAttribute('aria-invalid')).toBeNull();
  });

  it('envía el cuerpo esperado, recortado y con las disciplinas en el orden de la plantilla', async () => {
    const respuesta: EscuelaCreada = { id: 7, nombre: 'Escuela Norte', invitacionEnviadaA: 'ana@correo.es' };
    servicio.crear.and.returnValue(of(respuesta));
    rellenar();
    await enviar();
    expect(servicio.crear).toHaveBeenCalledOnceWith({
      nombre: 'Escuela Norte',
      emailContacto: 'norte@correo.es',
      sede: { nombre: 'Casariche' },
      propietario: { nombre: 'Ana', apellidos: 'Gil', email: 'ana@correo.es' },
      disciplinas: ['TKD', 'PIL'],
    });
  });

  it('mientras envía bloquea el formulario y deja el botón ocupado', async () => {
    servicio.crear.and.returnValue(new Subject<EscuelaCreada>());
    rellenar();
    await enviar();
    expect(boton('Creando').disabled).toBeTrue();
    expect(boton('Creando').getAttribute('aria-busy')).toBe('true');
    expect(el().querySelector('fieldset.bloque')!.hasAttribute('disabled')).toBeTrue();
    expect(boton('Cancelar').disabled).toBeTrue();
  });

  it('409: el mensaje de la API sale bajo el nombre de la escuela', async () => {
    servicio.crear.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya existe una escuela con ese nombre.' } }))
    );
    rellenar();
    await enviar();
    expect(el().querySelector('#ne-nombre-error')!.textContent).toContain('Ya existe una escuela con ese nombre.');
    expect(el().querySelector('.error-general')).toBeNull();
    expect(campo('ne-nombre').getAttribute('aria-invalid')).toBe('true');
    expect(boton('Crear escuela').disabled).toBeFalse();
  });

  it('otro error: alerta de página con el mensaje y el formulario intacto', async () => {
    servicio.crear.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Sede no válida.' } })));
    rellenar();
    await enviar();
    expect(el().querySelector('.error-general[role=alert]')!.textContent).toContain('Sede no válida.');
    expect(campo('ne-nombre').value).toContain('Escuela Norte');
  });

  it('éxito: sustituye el formulario por la confirmación con el correo invitado', async () => {
    servicio.crear.and.returnValue(of({ id: 7, nombre: 'Escuela Norte', invitacionEnviadaA: 'ana@correo.es' }));
    rellenar();
    await enviar();
    expect(el().querySelector('form')).toBeNull();
    expect(el().querySelector('h2')!.textContent).toContain('Escuela creada');
    expect(texto()).toContain('Hemos enviado la invitación a ana@correo.es.');
    boton('Ir a Logo y color').click();
    expect(router.navigate).toHaveBeenCalledWith(['/plataformaLogos']);
  });

  it('«Crear otra» vuelve al formulario vacío', async () => {
    servicio.crear.and.returnValue(of({ id: 7, nombre: 'Escuela Norte', invitacionEnviadaA: 'ana@correo.es' }));
    rellenar();
    await enviar();
    boton('Crear otra').click();
    fixture.detectChanges();
    expect(el().querySelector('form')).not.toBeNull();
    expect(campo('ne-nombre').value).toBe('');
    expect(campo('ne-propietarioEmail').value).toBe('');
    expect(casilla('TKD').checked).toBeFalse();
    expect(el().querySelector('.barra-resumen')!.textContent!.trim()).toBe('');
  });

  describe('Cancelar', () => {
    it('con el formulario limpio vuelve atrás sin preguntar', async () => {
      const aviso = spyOn(Swal, 'fire');
      await fixture.componentInstance.cancelar();
      expect(aviso).not.toHaveBeenCalled();
      expect(location.back).toHaveBeenCalled();
    });

    it('con cambios pregunta; si se sigue editando no sale', async () => {
      escribir('ne-nombre', 'Norte');
      spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: false } as never);
      await fixture.componentInstance.cancelar();
      expect(location.back).not.toHaveBeenCalled();
    });

    it('con cambios y confirmando, vuelve atrás', async () => {
      escribir('ne-nombre', 'Norte');
      spyOn(Swal, 'fire').and.resolveTo({ isConfirmed: true } as never);
      await fixture.componentInstance.cancelar();
      expect(location.back).toHaveBeenCalled();
    });
  });
});
