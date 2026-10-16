// Las fechas de la ficha y del historial de plantillas llegan de la API como número en SEGUNDOS (Instant con
// @EnableWebMvc en WebConfig). Con el pipe date a secas salían en enero de 1970.
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { PreinscripcionService } from '../../../features/preinscripcion/preinscripcion.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';

const SEGUNDOS = 1760000000; // 2025-10-09T08:53:20Z

describe('PreinscripcionesAdminComponent · fechas de la API en segundos', () => {
  let api: any;

  beforeEach(() => {
    api = {
      listar: jasmine.createSpy('listar').and.returnValue(of({ content: [], page: 0, totalElements: 0, totalPages: 0 })),
      plantillas: jasmine.createSpy('plantillas').and.returnValue(of({
        activa: { version: 2, contenido: { normas: ['Norma'] }, instrucciones: '' },
        historial: [{ id: 1, version: 2, activa: true, creadaEn: SEGUNDOS }],
      })),
    };
    TestBed.configureTestingModule({
      imports: [PreinscripcionesAdminComponent],
      providers: [
        proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: PreinscripcionService, useValue: api },
        { provide: AuthenticationService, useValue: { tieneRolAdmin: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      ],
    });
  });

  function pares(seleccion: Record<string, unknown>) {
    const fixture = TestBed.createComponent(PreinscripcionesAdminComponent);
    fixture.detectChanges();
    fixture.componentInstance.seleccion = {
      referencia: 'PRE-1', nombre: 'Ana', apellidos: 'García', estado: 'CANCELADA', turnosSolicitados: [],
      estadoEmailCambioTurnos: 'ENVIADO', emailCambioTurnosIntentos: 1,
      estadoEmailFinalizacion: 'ENVIADO', emailFinalizacionIntentos: 1,
      estadoEmailCancelacion: 'ENVIADO', emailCancelacionIntentos: 1,
      ...seleccion,
    };
    fixture.detectChanges();
    const mapa = new Map<string, string>();
    (fixture.nativeElement as HTMLElement).querySelectorAll('.detail-panel dt').forEach((dt) =>
      mapa.set(dt.textContent!.trim(), dt.nextElementSibling!.textContent!.trim()));
    return mapa;
  }

  it('muestra en su año real todas las fechas de la ficha', () => {
    const ficha = pares({
      turnosModificadosEn: SEGUNDOS, emailCambioTurnosEnviadoEn: SEGUNDOS,
      emailFinalizacionEnviadoEn: SEGUNDOS, emailCancelacionEnviadoEn: SEGUNDOS,
    });

    for (const etiqueta of ['Turnos modificados', 'Aviso enviado', 'Confirmación enviada', 'Aviso de cancelación enviado']) {
      expect(ficha.get(etiqueta)).withContext(etiqueta).toContain('2025');
      expect(ficha.get(etiqueta)).withContext(etiqueta).not.toContain('1970');
    }
  });

  it('sigue aceptando fechas ISO', () => {
    const ficha = pares({ emailFinalizacionEnviadoEn: '2026-10-16T12:00:00Z' });

    expect(ficha.get('Confirmación enviada')).toContain('2026');
  });

  describe('modal de finalización: fecha de nacimiento', () => {
    // La preinscripción la envía como LocalDate [año, mes, día]; la ficha del alumno como java.util.Date en ms,
    // medianoche del huso del servidor (UTC en producción, Europe/Madrid en pruebas).
    const MEDIANOCHE_UTC_12_ABR = 1397260800000; // 2014-04-12T00:00:00Z
    const MEDIANOCHE_MADRID_12_ABR = 1397253600000; // 2014-04-11T22:00:00Z (CEST)
    const MEDIANOCHE_MADRID_13_ABR = 1397340000000; // 2014-04-12T22:00:00Z (CEST)
    const MEDIANOCHE_MADRID_15_ENE = 1389740400000; // 2014-01-14T23:00:00Z (CET)
    const campo = { clave: 'FECHA_NACIMIENTO', etiqueta: 'Fecha de nacimiento', pre: 'fechaNacimiento', alumno: 'fechaNacimiento' };

    function comparar(pre: unknown, alumno: unknown) {
      const component = TestBed.createComponent(PreinscripcionesAdminComponent).componentInstance;
      component.seleccion = { fechaNacimiento: pre };
      component.alumnoSeleccionado = { fechaNacimiento: alumno };
      return component;
    }

    it('la misma fecha en [a,m,d] y en ms a medianoche UTC no se marca distinta', () => {
      expect(comparar([2014, 4, 12], MEDIANOCHE_UTC_12_ABR).hayDiferencia(campo)).toBeFalse();
    });

    it('la misma fecha en ms a medianoche de Madrid (día anterior en UTC) no se marca distinta', () => {
      expect(comparar([2014, 4, 12], MEDIANOCHE_MADRID_12_ABR).hayDiferencia(campo)).toBeFalse();
      expect(comparar([2014, 1, 15], MEDIANOCHE_MADRID_15_ENE).hayDiferencia(campo)).toBeFalse();
    });

    it('también con cadenas yyyy-MM-dd', () => {
      expect(comparar('2014-04-12', MEDIANOCHE_MADRID_12_ABR).hayDiferencia(campo)).toBeFalse();
      expect(comparar('2014-04-12', '2014-04-12').hayDiferencia(campo)).toBeFalse();
    });

    it('una fecha realmente distinta sí se marca, incluido el día siguiente a medianoche de Madrid', () => {
      expect(comparar([2014, 4, 12], MEDIANOCHE_MADRID_13_ABR).hayDiferencia(campo)).toBeTrue();
      expect(comparar([2014, 4, 13], MEDIANOCHE_MADRID_12_ABR).hayDiferencia(campo)).toBeTrue();
      expect(comparar([2015, 4, 12], MEDIANOCHE_UTC_12_ABR).hayDiferencia(campo)).toBeTrue();
    });

    it('al abrir la finalización no propone actualizar una fecha que solo cambia de formato', () => {
      const component = TestBed.createComponent(PreinscripcionesAdminComponent).componentInstance;
      component.seleccion = {
        referencia: 'PRE-1', deporte: 'TAEKWONDO', nombre: 'Ana', apellidos: 'García López', dni: '12345678Z',
        fechaNacimiento: [2014, 4, 12], direccion: 'Calle Mayor 1', telefono: '612345678', email: 'ana@example.com',
        tieneDiscapacidad: false, turnosSolicitados: [],
        alumnosCoincidentes: [{
          id: 3, nif: '12345678Z', nombre: 'Ana', apellidos: 'García López', fechaNacimiento: MEDIANOCHE_MADRID_12_ABR,
          direccion: 'Calle Mayor 1', telefono: '612345678', email: 'ana@example.com', tieneDiscapacidad: false,
        }],
      };

      component.abrirFinalizacion();

      expect(component.camposActualizar.has('FECHA_NACIMIENTO')).toBeFalse();
    });

    it('compara y muestra bien fechas en ms entre 1966 y 1973 (no son segundos)', () => {
      const component = comparar([1970, 6, 15], 14256000000);

      expect(component.hayDiferencia(campo)).toBeFalse();
      expect(comparar([1972, 12, 31], 94608000000).hayDiferencia(campo)).toBeFalse();
      expect(component.mostrarValor(14256000000, 'FECHA_NACIMIENTO')).toBe('15/06/1970');
      expect(component.mostrarValor(94608000000, 'FECHA_NACIMIENTO')).toBe('31/12/1972');
    });

    it('muestra la fecha como dd/MM/yyyy en los dos formatos', () => {
      const component = comparar([2014, 4, 12], MEDIANOCHE_MADRID_12_ABR);

      expect(component.mostrarValor([2014, 4, 12], 'FECHA_NACIMIENTO')).toBe('12/04/2014');
      expect(component.mostrarValor(MEDIANOCHE_MADRID_12_ABR, 'FECHA_NACIMIENTO')).toBe('12/04/2014');
      expect(component.mostrarValor(null, 'FECHA_NACIMIENTO')).toBe('Sin dato');
    });
  });

  describe('edad con la fecha de nacimiento [a,m,d] de la API (sin new Date(array))', () => {
    beforeEach(() => {
      jasmine.clock().install();
      jasmine.clock().mockDate(new Date(2026, 5, 15, 12, 0, 0)); // 15/06/2026
    });
    afterEach(() => jasmine.clock().uninstall());

    function componente(fechaNacimiento: unknown) {
      const component = TestBed.createComponent(PreinscripcionesAdminComponent).componentInstance;
      component.seleccion = {
        referencia: 'PRE-1', deporte: 'TAEKWONDO', nombre: 'Ana', apellidos: 'García', fechaNacimiento,
        tieneDiscapacidad: false, turnosSolicitados: [], alumnosCoincidentes: [],
      };
      return component;
    }

    it('esMenorSeleccionado: cumple 18 hoy, mañana y ayer', () => {
      expect(componente([2008, 6, 15]).esMenorSeleccionado).withContext('hoy').toBeFalse();
      expect(componente([2008, 6, 16]).esMenorSeleccionado).withContext('mañana').toBeTrue();
      expect(componente([2008, 6, 14]).esMenorSeleccionado).withContext('ayer').toBeFalse();
      expect(componente(null).esMenorSeleccionado).toBeFalse();
    });

    it('tarifa inicial de Taekwondo: adulto -> ADULTO, 17 años -> INFANTIL', () => {
      const adulto = componente([1990, 1, 1]);
      adulto.abrirFinalizacion();
      expect(adulto.datosAlta.tipoTarifa).toBe('ADULTO');

      const dieciochoHoy = componente([2008, 6, 15]);
      dieciochoHoy.abrirFinalizacion();
      expect(dieciochoHoy.datosAlta.tipoTarifa).toBe('ADULTO');

      const menor = componente([2008, 6, 16]);
      menor.abrirFinalizacion();
      expect(menor.datosAlta.tipoTarifa).toBe('INFANTIL');
    });
  });

  it('muestra en su año real la fecha del historial de plantillas', async () => {
    const fixture = TestBed.createComponent(PreinscripcionesAdminComponent);
    fixture.detectChanges();
    await fixture.componentInstance.cambiarTab('plantillas');
    fixture.detectChanges();

    const fecha = (fixture.nativeElement as HTMLElement).querySelector('.history small')!.textContent!;
    expect(fecha).toContain('2025');
    expect(fecha).not.toContain('1970');
  });
});
