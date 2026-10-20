import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { FichaProfesorComponent, NOTA_PASE } from './ficha-profesor.component';
import { DeporteDelAlumno, FichaAlumnoProfesor, PaseCreado, SaludAlumno } from '../../../../interfaces/profesor';
import { ProfesorService } from '../../../../servicios/endpoints/profesor.service';

function deporte(cambios: Partial<DeporteDelAlumno> = {}): DeporteDelAlumno {
  return {
    deporte: 'TAEKWONDO', deporteNombre: 'Taekwondo', grado: { codigo: 'AMARILLO', nombre: 'Amarillo' }, fechaGrado: '2026-03-12',
    apto: true, deMisGrupos: true, admitePases: true, siguienteGrado: { codigo: 'NARANJA', nombre: 'Naranja' }, pideRojoBordado: false,
    ...cambios,
  };
}

function ficha(deportes: DeporteDelAlumno[] = [deporte()]): FichaAlumnoProfesor {
  return {
    id: 3, nombre: 'Ana', apellidos: 'Prueba Ficticia', edad: 9,
    grupos: [{ id: 4, nombre: 'Infantil lunes y miércoles', sede: null }],
    contacto: [
      { tipo: 'TUTOR', nombre: 'María', apellidos: 'Ficticia', parentesco: 'MADRE', telefono: '600 000 000' },
      { tipo: 'ALUMNO', nombre: null, apellidos: null, parentesco: null, telefono: '600 000 001' },
    ],
    deportes,
  };
}

const SALUD: SaludAlumno = {
  alumnoId: 3, registrado: true, lesionEnfermedad: true, descripcion: 'Asma leve', impideActividad: false, impideDetalle: null, alergias: null,
};

describe('FichaProfesorComponent', () => {
  let fixture: ComponentFixture<FichaProfesorComponent>;
  let componente: FichaProfesorComponent;
  let profesor: jasmine.SpyObj<ProfesorService>;

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const boton = (texto: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim() === texto);

  function crear(datos = ficha(), query: Record<string, string> = { grupo: '4' }): void {
    profesor = jasmine.createSpyObj<ProfesorService>('ProfesorService', [
      'ficha', 'salud', 'actualizarApto', 'actualizarFechaGrado', 'pasarGrado',
    ]);
    profesor.ficha.and.returnValue(of(datos));
    TestBed.configureTestingModule({
      imports: [FichaProfesorComponent],
      providers: [
        provideRouter([]),
        { provide: ProfesorService, useValue: profesor },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ alumnoId: '3' }), queryParamMap: convertToParamMap(query) } },
        },
      ],
    });
    fixture = TestBed.createComponent(FichaProfesorComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('pinta nombre, edad, grupos compartidos, contacto con «Llamar» y volver al grupo', () => {
    crear();
    expect(texto('h1')).toBe('Ana Prueba Ficticia');
    expect(texto('.subtitulo')).toBe('9 años · Infantil lunes y miércoles');
    const contactos = el().querySelectorAll('.contacto');
    expect(contactos[0].querySelector('.contacto-nombre')?.textContent?.trim()).toBe('María Ficticia');
    expect(contactos[0].querySelector('.contacto-meta')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Madre · 600 000 000');
    expect(contactos[0].querySelector('a.btn-llamar')?.getAttribute('href')).toBe('tel:600000000');
    expect(contactos[1].querySelector('.contacto-nombre')?.textContent?.trim()).toBe('Ana Prueba Ficticia');
    expect(contactos[1].querySelector('.contacto-quien')?.textContent?.trim()).toBe('Alumno');
    expect(el().querySelector('a.volver')?.getAttribute('href')).toBe('/mis-grupos/4');
    expect(texto('.grado-desde span')).toBe('desde 12/03/2026');
  });

  it('no hay «Cambiar grado» directo', () => {
    crear();
    expect(el().textContent).not.toContain('Cambiar grado');
  });

  describe('salud (D7-3)', () => {
    it('no se pide al cargar la ficha; solo al desplegar la sección', () => {
      crear();
      expect(profesor.salud).not.toHaveBeenCalled();
      expect(texto('.salud-nota')).toBe('Tu consulta queda registrada');

      profesor.salud.and.returnValue(of(SALUD));
      (el().querySelector('#botonSalud') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(profesor.salud).toHaveBeenCalledOnceWith(3);
      expect(el().querySelector('#botonSalud')?.getAttribute('aria-expanded')).toBe('true');
      expect(texto('.salud-datos')).toContain('Asma leve');
    });

    it('al plegarla se descarta y al volver a abrirla se pide de nuevo (sin caché)', () => {
      crear();
      profesor.salud.and.returnValue(of(SALUD));
      const botonSalud = () => el().querySelector('#botonSalud') as HTMLButtonElement;
      botonSalud().click();
      fixture.detectChanges();
      botonSalud().click();
      fixture.detectChanges();
      expect(componente.salud).toBeNull();
      expect(el().querySelector('.salud-datos')).toBeNull();
      botonSalud().click();
      fixture.detectChanges();
      expect(profesor.salud).toHaveBeenCalledTimes(2);
    });

    it('al salir de la ficha se descarta la salud y se cancela la petición en curso', () => {
      crear();
      const respuesta = new Subject<SaludAlumno>();
      profesor.salud.and.returnValue(respuesta.asObservable());
      (el().querySelector('#botonSalud') as HTMLButtonElement).click();
      expect(respuesta.observed).toBeTrue();
      fixture.destroy();
      expect(respuesta.observed).toBeFalse();
      expect(componente.salud).toBeNull();
      expect(componente.cargandoSalud).toBeFalse();
    });

    it('sin datos registrados lo dice', () => {
      crear();
      profesor.salud.and.returnValue(of({ ...SALUD, registrado: false, lesionEnfermedad: null, descripcion: null }));
      (el().querySelector('#botonSalud') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(texto('.salud-panel')).toBe('Sin datos de salud registrados.');
    });
  });

  describe('pases (decisión 207)', () => {
    it('confirmación con el texto del boceto; el botón se desactiva durante la petición y crea el cargo', () => {
      crear();
      const respuesta = new Subject<PaseCreado>();
      profesor.pasarGrado.and.returnValue(respuesta.asObservable());

      boton('Pase con derecho de examen')!.click();
      fixture.detectChanges();
      expect(texto('.hoja h2')).toBe('Pase con derecho de examen');
      expect(texto('.hoja-detalle')).toContain('Taekwondo: Amarillo');
      expect(texto('.hoja-detalle')).toContain('Naranja');
      expect(texto('.hoja-nota')).toBe(NOTA_PASE);
      expect(el().querySelector('.opciones')).toBeNull();

      boton('Crear cargo')!.click();
      fixture.detectChanges();
      expect(profesor.pasarGrado).toHaveBeenCalledOnceWith(3, 'TAEKWONDO', 'examen', false);
      expect(boton('Crear cargo')!.disabled).toBeTrue();
      boton('Crear cargo')!.click();
      expect(profesor.pasarGrado).toHaveBeenCalledTimes(1);

      respuesta.next({ deporte: deporte(), cargo: { id: 9, concepto: 'Pase a Naranja (Taekwondo)', importe: 25 } });
      respuesta.complete();
      fixture.detectChanges();
      expect(el().querySelector('.hoja')).toBeNull();
      expect(texto('.aviso')).toContain('Cargo creado');
      expect(texto('.aviso')).toContain('Pase a Naranja (Taekwondo)');
      expect(texto('.aviso')).toMatch(/25,00\s€/);
      expect(el().querySelector('.aviso a')?.getAttribute('href')).toBe('/mi-caja');
    });

    it('el 409 muestra el mensaje del backend y vuelve a activar el botón', () => {
      crear();
      profesor.pasarGrado.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya hay un pase pendiente de cobro para este deporte' } }))
      );
      boton('Pase por recompensa')!.click();
      fixture.detectChanges();
      boton('Crear cargo')!.click();
      fixture.detectChanges();
      expect(profesor.pasarGrado).toHaveBeenCalledWith(3, 'TAEKWONDO', 'recompensa', false);
      expect(texto('.hoja-error')).toBe('Ya hay un pase pendiente de cobro para este deporte');
      expect(boton('Crear cargo')!.disabled).toBeFalse();
    });

    it('un 400 del servicio también muestra su mensaje', () => {
      crear();
      profesor.pasarGrado.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'El alumno no está apto para examen' } }))
      );
      boton('Pase con derecho de examen')!.click();
      fixture.detectChanges();
      boton('Crear cargo')!.click();
      fixture.detectChanges();
      expect(texto('.hoja-error')).toBe('El alumno no está apto para examen');
    });

    it('si pide rojo bordado, se elige dentro de la confirmación', () => {
      crear(ficha([deporte({ siguienteGrado: { codigo: 'ROJO', nombre: 'Rojo' }, pideRojoBordado: true })]));
      profesor.pasarGrado.and.returnValue(of({ deporte: deporte(), cargo: { id: 9, concepto: 'Pase', importe: 30 } }));
      boton('Pase con derecho de examen')!.click();
      fixture.detectChanges();
      const opciones = el().querySelectorAll<HTMLInputElement>('.opciones input[type=radio]');
      expect(opciones.length).toBe(2);
      expect(texto('.opciones')).toContain('Rojo bordado');

      componente.hojaPase!.rojoBordado = true;
      fixture.detectChanges();
      expect(texto('.hoja-detalle strong')).toBe('Rojo bordado');
      boton('Crear cargo')!.click();
      expect(profesor.pasarGrado).toHaveBeenCalledWith(3, 'TAEKWONDO', 'examen', true);
    });

    it('Escape cierra la hoja del pase', () => {
      crear();
      boton('Pase por recompensa')!.click();
      fixture.detectChanges();
      expect(el().querySelector('.hoja')).not.toBeNull();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      fixture.detectChanges();
      expect(el().querySelector('.hoja')).toBeNull();
    });

    it('sin apto no hay botones de pase y se explica por qué', () => {
      crear(ficha([deporte({ apto: false })]));
      expect(boton('Pase con derecho de examen')).toBeUndefined();
      expect(texto('.pista')).toContain('Apto para examen');
    });
  });

  it('apto conmutable con PUT y el deporte devuelto', () => {
    crear(ficha([deporte({ apto: false })]));
    profesor.actualizarApto.and.returnValue(of(deporte({ apto: true })));
    const interruptor = el().querySelector<HTMLButtonElement>('button[role=switch]')!;
    expect(interruptor.getAttribute('aria-checked')).toBe('false');
    interruptor.click();
    fixture.detectChanges();
    expect(profesor.actualizarApto).toHaveBeenCalledWith(3, 'TAEKWONDO', true);
    expect(el().querySelector('button[role=switch]')!.getAttribute('aria-checked')).toBe('true');
    expect(boton('Pase con derecho de examen')).toBeDefined();
  });

  it('la fecha de grado se edita y se guarda con PUT', () => {
    crear();
    profesor.actualizarFechaGrado.and.returnValue(of(deporte({ fechaGrado: '2026-05-02' })));
    boton('Cambiar')!.click();
    fixture.detectChanges();
    componente.ediciones.get('TAEKWONDO')!.valor = '2026-05-02';
    componente.guardarFecha(componente.ficha!.deportes[0]);
    fixture.detectChanges();
    expect(profesor.actualizarFechaGrado).toHaveBeenCalledWith(3, 'TAEKWONDO', '2026-05-02');
    expect(texto('.grado-desde span')).toBe('desde 02/05/2026');
  });

  it('un deporte que no es de sus grupos es de solo lectura', () => {
    crear(ficha([deporte({ deMisGrupos: false })]));
    expect(texto('.chip-lectura')).toBe('Solo lectura');
    expect(el().querySelector('button[role=switch]')).toBeNull();
    expect(boton('Cambiar')).toBeUndefined();
    expect(boton('Pase con derecho de examen')).toBeUndefined();
  });

  it('sin grupo de origen, «Volver» lleva a Mis grupos', () => {
    crear(ficha(), {});
    expect(el().querySelector('a.volver')?.getAttribute('href')).toBe('/mis-grupos');
  });
});
