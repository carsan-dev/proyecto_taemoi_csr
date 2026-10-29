import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { DeporteDelAlumno, FichaAlumnoProfesor, PaseCreado, SaludAlumno } from '../../interfaces/profesor';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { ProfesorService } from '../../servicios/endpoints/profesor.service';
import { AvisosService } from '../avisos/avisos.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { eur } from '../formato/formato-es';
import { OpcionesConfirmacion, ResultadoConfirmacion, ValoresDialogo } from '../dialogo/dialogo.component';
import { FichaProfesorPanelComponent, NOTA_PASE, NOTA_PASE_GESTION } from './ficha-profesor-panel.component';

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

const PASE: PaseCreado = { deporte: deporte(), cargo: { id: 9, concepto: 'Pase a Naranja (Taekwondo)', importe: 1234.5 } };

describe('FichaProfesorPanelComponent', () => {
  let fixture: ComponentFixture<FichaProfesorPanelComponent>;
  let componente: FichaProfesorPanelComponent;
  let profesor: jasmine.SpyObj<ProfesorService>;
  let avisos: jasmine.SpyObj<AvisosService>;
  let opcionesDialogo: OpcionesConfirmacion | null;
  let valoresDialogo: ValoresDialogo;
  let resultadoDialogo: ResultadoConfirmacion | null;

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = (selector: string) => el().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const boton = (t: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.replace(/\s+/g, ' ').trim() === t);
  const bloques = () => Array.from(el().querySelectorAll('.ficha__bloque'));
  const bloqueDe = (nombre: string) => bloques().find((b) => b.querySelector('h2')?.textContent?.trim() === nombre) as HTMLElement;

  function crear(datos = ficha(), query: Record<string, string> = { grupo: '4' }, roles: string[] = ['ROLE_PROFESOR']): void {
    profesor = jasmine.createSpyObj<ProfesorService>('ProfesorService', [
      'ficha', 'salud', 'actualizarApto', 'actualizarFechaGrado', 'pasarGrado',
    ]);
    profesor.ficha.and.returnValue(of(datos));
    avisos = jasmine.createSpyObj<AvisosService>('AvisosService', ['exito', 'error']);
    opcionesDialogo = null;
    valoresDialogo = {};
    resultadoDialogo = null;
    // El diálogo del panel: ejecuta `comprobar` como lo haría al pulsar «Crear cargo» y confirma si no hay error.
    const confirmacion = {
      confirmar: async (opciones: OpcionesConfirmacion): Promise<ResultadoConfirmacion> => {
        opcionesDialogo = opciones;
        const error = await opciones.comprobar?.(valoresDialogo);
        resultadoDialogo = error ? { confirmado: false, valor: null } : { confirmado: true, valor: null, valores: valoresDialogo };
        return resultadoDialogo;
      },
    };
    TestBed.configureTestingModule({
      imports: [FichaProfesorPanelComponent],
      providers: [
        provideRouter([]),
        { provide: ProfesorService, useValue: profesor },
        { provide: AvisosService, useValue: avisos },
        { provide: ConfirmacionService, useValue: confirmacion },
        { provide: AuthenticationService, useValue: { getRolesActuales: () => roles } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ alumnoId: '3' }), queryParamMap: convertToParamMap(query) } },
        },
      ],
    });
    fixture = TestBed.createComponent(FichaProfesorPanelComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('pantalla propia: nombre, edad, grupos, contacto con «Llamar» y la miga al grupo; sin navegación del profesor', () => {
    crear();
    expect(texto('h1')).toBe('Ana Prueba Ficticia');
    expect(texto('.ficha__cabecera .ficha__secundario')).toBe('9 años · Infantil lunes y miércoles');
    const contactos = el().querySelectorAll('.ficha__contacto');
    expect(contactos[0].querySelector('.ficha__contacto-nombre')?.textContent?.trim()).toBe('María Ficticia');
    expect(contactos[0].querySelector('.ficha__secundario')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Madre · 600 000 000');
    expect(contactos[0].querySelector('a.ficha__boton')?.getAttribute('href')).toBe('tel:600000000');
    expect(contactos[1].querySelector('.ficha__contacto-nombre')?.textContent?.trim()).toBe('Ana Prueba Ficticia');
    const miga = el().querySelector('nav.ficha__migas a');
    expect(miga?.textContent?.trim()).toBe('Infantil lunes y miércoles');
    expect(miga?.getAttribute('href')).toBe('/mis-grupos/4');
    expect(el().textContent).not.toContain('Volver');
    expect(el().querySelector('app-navegacion-profesor')).toBeNull();
    expect(el().textContent).not.toContain('Cerrar sesión');
  });

  it('la miga cae en «Mis grupos» si no llega el grupo de origen', () => {
    crear(ficha(), {});
    expect(el().querySelector('nav.ficha__migas a')?.textContent?.trim()).toBe('Mis grupos');
    expect(el().querySelector('nav.ficha__migas a')?.getAttribute('href')).toBe('/mis-grupos');
  });

  it('no enseña NIF, email ni dirección (lo justo para el profesor)', () => {
    crear();
    expect(el().textContent).not.toMatch(/NIF|DNI|email|correo|dirección/i);
  });

  it('un bloque por deporte: los de sus grupos, luego Salud y al final los de solo lectura, sin controles', () => {
    crear(ficha([
      deporte({ deporte: 'KICKBOXING', deporteNombre: 'Kickboxing', deMisGrupos: false, apto: true, grado: { codigo: 'BLANCO', nombre: 'Blanco' }, fechaGrado: '2026-09-15' }),
      deporte(),
    ]));
    expect(bloques().map((b) => b.querySelector('h2')?.textContent?.trim())).toEqual(['Contacto', 'Taekwondo', 'Salud', 'Kickboxing']);
    const taekwondo = bloqueDe('Taekwondo');
    const kick = bloqueDe('Kickboxing');
    expect(taekwondo.textContent).not.toContain('Solo lectura');
    expect(taekwondo.textContent).toContain('Amarillo');
    expect(taekwondo.querySelector<HTMLInputElement>('input[type="date"]')!.value).toBe('2026-03-12');
    expect(kick.querySelector('.ficha__bloque-cabeza')?.lastElementChild?.textContent?.trim()).toBe('Solo lectura');
    expect(kick.querySelector('[role="switch"], button, input')).toBeNull();
    expect(kick.textContent).toContain('Blanco');
    expect(kick.textContent).toContain('15/09/2026');
    expect(kick.textContent?.replace(/\s+/g, ' ')).toContain('Apto para examen Sí');
  });

  it('el grado sale formateado y con la muestra de color del cinturón', () => {
    crear(ficha([deporte({ grado: { codigo: 'BLANCO_AMARILLO', nombre: 'BLANCO-AMARILLO' } })]));
    const cinto = bloqueDe('Taekwondo').querySelector<HTMLElement>('.ficha__cinto')!;
    expect(cinto).not.toBeNull();
    expect(cinto.style.background).not.toBe('');
    expect(bloqueDe('Taekwondo').textContent).not.toContain('BLANCO-AMARILLO');
  });

  describe('apto', () => {
    it('el interruptor, a la izquierda de la etiqueta, es role=switch y envía el cambio; al fallar avisa con un aviso de error', () => {
      crear();
      const interruptor = () => el().querySelector<HTMLButtonElement>('[role="switch"]')!;
      expect(interruptor().firstElementChild?.classList).toContain('ficha__interruptor');
      expect(interruptor().getAttribute('aria-checked')).toBe('true');
      profesor.actualizarApto.and.returnValue(of(deporte({ apto: false })));
      interruptor().click();
      fixture.detectChanges();
      expect(profesor.actualizarApto).toHaveBeenCalledOnceWith(3, 'TAEKWONDO', false);
      expect(interruptor().getAttribute('aria-checked')).toBe('false');

      profesor.actualizarApto.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      interruptor().click();
      expect(avisos.error).toHaveBeenCalledWith('No se ha podido cambiar el apto.');
    });
  });

  describe('fecha de grado', () => {
    const campo = () => el().querySelector<HTMLInputElement>('input[type="date"]')!;
    const escribir = (valor: string) => {
      campo().value = valor;
      campo().dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };

    it('el campo está a la vista con «obligatorio»; Guardar y Cancelar solo aparecen al cambiarla', () => {
      crear();
      expect(texto('.ficha__etiqueta')).toBe('Fecha de grado obligatorio');
      expect(campo().value).toBe('2026-03-12');
      expect(boton('Guardar')).toBeUndefined();
      escribir('2026-05-01');
      expect(boton('Guardar')).toBeDefined();
      expect(boton('Cancelar')).toBeDefined();
      boton('Cancelar')!.click();
      fixture.detectChanges();
      expect(boton('Guardar')).toBeUndefined();
      expect(campo().value).toBe('2026-03-12');
    });

    it('guarda con la fecha elegida con la misma llamada de hoy', () => {
      crear();
      profesor.actualizarFechaGrado.and.returnValue(of(deporte({ fechaGrado: '2026-05-01' })));
      escribir('2026-05-01');
      boton('Guardar')!.click();
      fixture.detectChanges();
      expect(profesor.actualizarFechaGrado).toHaveBeenCalledOnceWith(3, 'TAEKWONDO', '2026-05-01');
      expect(boton('Guardar')).toBeUndefined();
      expect(campo().value).toBe('2026-05-01');
    });

    it('no se guarda vacía y el error sale en línea; un fallo del servidor también', () => {
      crear();
      escribir('');
      boton('Guardar')!.click();
      fixture.detectChanges();
      expect(profesor.actualizarFechaGrado).not.toHaveBeenCalled();
      expect(texto('.ficha__error')).toBe('Elige una fecha.');

      profesor.actualizarFechaGrado.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { mensaje: 'Fecha futura' } }))
      );
      escribir('2026-05-01');
      boton('Guardar')!.click();
      fixture.detectChanges();
      expect(texto('.ficha__error')).toBe('Fecha futura');
      expect(avisos.error).not.toHaveBeenCalled();
    });
  });

  describe('pase (decisión 207)', () => {
    it('un solo botón «Proponer pase» y el diálogo pide el tipo con radios; sin tipo no se envía nada', async () => {
      crear();
      expect(boton('Proponer pase')).toBeDefined();
      expect(boton('Pase con derecho de examen')).toBeUndefined();
      boton('Proponer pase')!.click();
      await fixture.whenStable();
      expect(opcionesDialogo!.titulo).toBe('Crear cargo del pase');
      expect(opcionesDialogo!.mensaje).toEqual([{ fuerte: 'Ana Prueba Ficticia' }, ' · Taekwondo']);
      const tipo = opcionesDialogo!.campos![0];
      expect(tipo.tipo).toBe('pastillas');
      expect(tipo.opciones!.map((o) => o.texto)).toEqual(['Con derecho de examen', 'Por recompensa']);
      expect(opcionesDialogo!.destacado).toBeUndefined();
      expect(profesor.pasarGrado).not.toHaveBeenCalled();
      expect(resultadoDialogo!.confirmado).toBeFalse();
    });

    it('con el tipo elegido la petición sale con el cuerpo de la ficha de hoy y avisa con el cargo', async () => {
      crear();
      profesor.pasarGrado.and.returnValue(of(PASE));
      valoresDialogo = { 'tipo-pase': 'examen' };
      boton('Proponer pase')!.click();
      await fixture.whenStable();
      expect(opcionesDialogo!.accion).toBe('Crear cargo');
      expect(opcionesDialogo!.bloques).toContain({ tipo: 'parrafo', partes: [NOTA_PASE] });
      // Misma llamada que la ficha de hoy: (alumno, deporte, tipo, pideRojoBordado && rojoBordado).
      expect(profesor.pasarGrado).toHaveBeenCalledOnceWith(3, 'TAEKWONDO', 'examen', false);
      const [mensaje, accion] = avisos.exito.calls.mostRecent().args;
      expect(mensaje).toBe(`Cargo creado · Pase a Naranja (Taekwondo) · ${eur(1234.5)}`);
      expect(mensaje).toMatch(/1\.234,50/);
      expect(accion!.texto).toBe('Ir a Mi caja');
    });

    it('con la confirmación pendiente, pulsar «Proponer pase» varias veces abre un solo diálogo y no envía nada', async () => {
      crear();
      const confirmar = spyOn(TestBed.inject(ConfirmacionService), 'confirmar').and.returnValue(new Promise(() => undefined));
      boton('Proponer pase')!.click();
      boton('Proponer pase')!.click();
      void componente.proponerPase(componente.ficha!.deportes[0]);
      await fixture.whenStable();
      expect(confirmar).toHaveBeenCalledTimes(1);
      expect(profesor.pasarGrado).not.toHaveBeenCalled();
    });

    it('«Por recompensa» usa el mismo endpoint con el tipo recompensa', async () => {
      crear();
      profesor.pasarGrado.and.returnValue(of(PASE));
      valoresDialogo = { 'tipo-pase': 'recompensa' };
      boton('Proponer pase')!.click();
      await fixture.whenStable();
      expect(profesor.pasarGrado).toHaveBeenCalledOnceWith(3, 'TAEKWONDO', 'recompensa', false);
    });

    it('la gestión lee «tesorería» y el aviso lleva a tesorería', async () => {
      crear(ficha(), { grupo: '4' }, ['ROLE_MANAGER']);
      profesor.pasarGrado.and.returnValue(of(PASE));
      valoresDialogo = { 'tipo-pase': 'examen' };
      const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      boton('Proponer pase')!.click();
      await fixture.whenStable();
      expect(opcionesDialogo!.bloques).toContain({ tipo: 'parrafo', partes: [NOTA_PASE_GESTION] });
      const accion = avisos.exito.calls.mostRecent().args[1]!;
      expect(accion.texto).toBe('Ir a tesorería');
      accion.alPulsar();
      expect(navegar).toHaveBeenCalledWith(['/tesoreriaCobros']);
    });

    it('con Rojo / Rojo bordado pide también el grado y rojoBordado=true solo si se elige', async () => {
      crear(ficha([deporte({ pideRojoBordado: true, siguienteGrado: { codigo: 'ROJO', nombre: 'Rojo' } })]));
      profesor.pasarGrado.and.returnValue(of(PASE));
      valoresDialogo = { 'tipo-pase': 'examen' };
      boton('Proponer pase')!.click();
      await fixture.whenStable();
      const grado = opcionesDialogo!.campos!.find((c) => c.id === 'grado-pase')!;
      expect(grado.opciones!.map((o) => o.texto)).toEqual(['Rojo', 'Rojo bordado']);
      expect(profesor.pasarGrado).toHaveBeenCalledWith(3, 'TAEKWONDO', 'examen', false);

      valoresDialogo = { 'tipo-pase': 'recompensa', 'grado-pase': 'rojo-bordado' };
      boton('Proponer pase')!.click();
      await fixture.whenStable();
      expect(profesor.pasarGrado.calls.mostRecent().args).toEqual([3, 'TAEKWONDO', 'recompensa', true]);
    });

    it('un fallo deja el diálogo con el error del servidor en línea y no avisa', async () => {
      crear();
      profesor.pasarGrado.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 409, error: { mensaje: 'Ya hay un pase pendiente de cobro para este deporte' } }))
      );
      valoresDialogo = { 'tipo-pase': 'recompensa' };
      boton('Proponer pase')!.click();
      await fixture.whenStable();
      expect(resultadoDialogo!.confirmado).toBeFalse();
      expect(avisos.exito).not.toHaveBeenCalled();
      const error = await opcionesDialogo!.comprobar!(valoresDialogo);
      expect(error).toEqual({ mensaje: 'Ya hay un pase pendiente de cobro para este deporte' });
    });

    it('sin apto no hay botón de pase y se explica por qué', () => {
      crear(ficha([deporte({ apto: false })]));
      expect(boton('Proponer pase')).toBeUndefined();
      expect(el().textContent).toContain('Marca «Apto para examen» para poder hacer un pase.');
    });
  });

  describe('salud (D7-3)', () => {
    it('bloque propio con la nota; no se pide al cargar, se pide al desplegar, se descarta al plegar y se pide de nuevo', () => {
      crear();
      expect(profesor.salud).not.toHaveBeenCalled();
      expect(texto('#ficha-titulo-salud')).toBe('Salud');
      expect(bloqueDe('Salud').textContent).toContain('Tu consulta queda registrada.');
      expect(texto('#ficha-boton-salud')).toBe('Ver salud');

      profesor.salud.and.returnValue(of(SALUD));
      const disparador = () => el().querySelector<HTMLButtonElement>('#ficha-boton-salud')!;
      disparador().click();
      fixture.detectChanges();
      expect(profesor.salud).toHaveBeenCalledOnceWith(3);
      expect(disparador().getAttribute('aria-expanded')).toBe('true');
      expect(texto('#ficha-panel-salud')).toContain('Asma leve');

      disparador().click();
      fixture.detectChanges();
      expect(componente.salud).toBeNull();
      disparador().click();
      expect(profesor.salud).toHaveBeenCalledTimes(2);
    });

    it('al salir se cancela la petición en curso', () => {
      crear();
      const respuesta = new Subject<SaludAlumno>();
      profesor.salud.and.returnValue(respuesta.asObservable());
      el().querySelector<HTMLButtonElement>('#ficha-boton-salud')!.click();
      expect(respuesta.observed).toBeTrue();
      fixture.destroy();
      expect(respuesta.observed).toBeFalse();
    });

    it('sin datos registrados lo dice', () => {
      crear();
      profesor.salud.and.returnValue(of({ ...SALUD, registrado: false, lesionEnfermedad: null, descripcion: null }));
      el().querySelector<HTMLButtonElement>('#ficha-boton-salud')!.click();
      fixture.detectChanges();
      expect(texto('#ficha-panel-salud')).toBe('Sin datos de salud registrados.');
    });
  });

  it('embebida: carga el alumno de la entrada, sin miga ni título propio, y recarga al cambiar de alumno', () => {
    crear();
    fixture.destroy();
    fixture = TestBed.createComponent(FichaProfesorPanelComponent);
    fixture.componentRef.setInput('embebida', true);
    fixture.componentRef.setInput('alumnoId', 5);
    fixture.detectChanges();
    expect(profesor.ficha).toHaveBeenCalledWith(5);
    expect(el().querySelector('a.ficha__migas')).toBeNull();
    expect(el().querySelector('h1')).toBeNull();
    expect(texto('.ficha__secundario--suelto')).toBe('9 años · Infantil lunes y miércoles');
    fixture.componentRef.setInput('alumnoId', 6);
    fixture.detectChanges();
    expect(profesor.ficha).toHaveBeenCalledWith(6);
  });
});
