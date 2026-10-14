import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { PreinscripcionesAdminComponent } from './preinscripciones-admin.component';
import { PreinscripcionService } from '../../../features/preinscripcion/preinscripcion.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { proveerCatalogoTesting } from '../../../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../../../features/catalogo/testing/catalogo.fixtures';
import { Catalogo } from '../../../features/catalogo/catalogo.models';

describe('PreinscripcionesAdminComponent: tarifas del catálogo', () => {
  const api = {
    listar: () => of({ content: [], page: 0, totalElements: 0, totalPages: 0 }),
    firma: () => of(new Blob()),
  };
  const auth = { tieneRolAdmin: () => true };
  const route = { snapshot: { queryParamMap: { get: () => null } } };
  const router = { navigate: () => Promise.resolve(true) };

  function crear(catalogo: Catalogo): PreinscripcionesAdminComponent {
    TestBed.configureTestingModule({ providers: [proveerCatalogoTesting(catalogo)] });
    return TestBed.runInInjectionContext(
      () => new PreinscripcionesAdminComponent(api as any, auth as any, route as any, router as any)
    );
  }

  function solicitud(deporte: string, fechaNacimiento = '2014-04-12'): any {
    return { referencia: 'PRE-1', deporte, fechaNacimiento, alumnosCoincidentes: [] };
  }

  const opciones = (c: PreinscripcionesAdminComponent) => c.tarifasDisponibles.map((t) => [t.codigo, t.nombre]);

  describe('D7/D8 lista por disciplina (Taemoi)', () => {
    it('Taekwondo: sus 7 tarifas en orden del catálogo, con nombre', () => {
      const c = crear(CATALOGO_TAEMOI);
      c.seleccion = solicitud('TAEKWONDO');
      expect(opciones(c)).toEqual([
        ['ADULTO', 'Adulto'], ['ADULTO_GRUPO', 'Adulto Grupo'], ['FAMILIAR', 'Familiar'], ['INFANTIL', 'Infantil'],
        ['INFANTIL_GRUPO', 'Infantil Grupo'], ['HERMANOS', 'Hermanos'], ['PADRES_HIJOS', 'Padres e Hijos'],
      ]);
    });

    it('Kickboxing, Pilates y Defensa Personal Femenina: solo su tarifa', () => {
      const c = crear(CATALOGO_TAEMOI);
      c.seleccion = solicitud('KICKBOXING');
      expect(opciones(c)).toEqual([['KICKBOXING', 'Kickboxing']]);
      c.seleccion = solicitud('PILATES');
      expect(opciones(c)).toEqual([['PILATES', 'Pilates']]);
      c.seleccion = solicitud('DEFENSA_PERSONAL_FEMENINA');
      expect(opciones(c)).toEqual([['DEFENSA_PERSONAL_FEMENINA', 'Defensa Personal Femenina']]);
    });

    it('segunda escuela: Judo ofrece la general y la propia', () => {
      const c = crear(CATALOGO_SEGUNDA_ESCUELA);
      c.seleccion = solicitud('JUDO');
      expect(opciones(c)).toEqual([['BONO', 'Bono'], ['JUDO_MES', 'Judo mensual']]);
    });
  });

  describe('D9 tarifa inicial e importe', () => {
    beforeEach(() => {
      jasmine.clock().install();
      jasmine.clock().mockDate(new Date(2026, 5, 15, 12, 0, 0));
    });
    afterEach(() => jasmine.clock().uninstall());

    it('Taemoi Taekwondo: menor de 18 -> INFANTIL 28; adulto -> ADULTO 30', () => {
      const c = crear(CATALOGO_TAEMOI);
      c.seleccion = solicitud('TAEKWONDO', '2008-06-16T12:00:00');
      c.crearNuevaFicha();
      expect(c.datosAlta.tipoTarifa).toBe('INFANTIL');
      expect(c.datosAlta.cuantiaTarifa).toBe(28);
      c.seleccion = solicitud('TAEKWONDO', '2008-06-15T12:00:00');
      c.crearNuevaFicha();
      expect(c.datosAlta.tipoTarifa).toBe('ADULTO');
      expect(c.datosAlta.cuantiaTarifa).toBe(30);
    });

    it('Taemoi otras disciplinas: su primera tarifa', () => {
      const c = crear(CATALOGO_TAEMOI);
      const esperados: [string, string, number][] = [
        ['KICKBOXING', 'KICKBOXING', 30], ['PILATES', 'PILATES', 30], ['DEFENSA_PERSONAL_FEMENINA', 'DEFENSA_PERSONAL_FEMENINA', 25],
      ];
      esperados.forEach(([deporte, tarifa, importe]) => {
        c.seleccion = solicitud(deporte);
        c.crearNuevaFicha();
        expect([c.datosAlta.tipoTarifa, c.datosAlta.cuantiaTarifa]).withContext(deporte).toEqual([tarifa, importe]);
      });
    });

    it('segunda escuela: sin preselección e importe 0 hasta elegir; al elegir, importe del catálogo', () => {
      const c = crear(CATALOGO_SEGUNDA_ESCUELA);
      c.seleccion = solicitud('JUDO');
      c.crearNuevaFicha();
      expect(c.datosAlta.tipoTarifa).toBe('');
      expect(c.datosAlta.cuantiaTarifa).toBe(0);
      expect(c.finalizacionValida).toBeFalse();
      c.datosAlta.tipoTarifa = 'JUDO_MES';
      c.cambiarTarifa();
      expect(c.datosAlta.cuantiaTarifa).toBe(35);
    });

    it('importe al cambiar de tarifa: el de antes, sin rol (PADRES_HIJOS -> 0)', () => {
      const c = crear(CATALOGO_TAEMOI);
      c.seleccion = solicitud('TAEKWONDO');
      c.crearNuevaFicha();
      const esperados: [string, number][] = [
        ['ADULTO', 30], ['ADULTO_GRUPO', 20], ['FAMILIAR', 0], ['INFANTIL', 28],
        ['INFANTIL_GRUPO', 20], ['HERMANOS', 26], ['PADRES_HIJOS', 0],
      ];
      esperados.forEach(([codigo, importe]) => {
        c.datosAlta.tipoTarifa = codigo;
        c.datosAlta.rolFamiliar = 'PADRE';
        c.cambiarTarifa();
        expect(c.datosAlta.cuantiaTarifa).withContext(codigo).toBe(importe);
      });
    });
  });

  describe('rol y grupo por banderas', () => {
    it('Taemoi: PADRES_HIJOS exige rol; HERMANOS exige grupo; el resto los vacía', () => {
      const c = crear(CATALOGO_TAEMOI);
      c.seleccion = { ...solicitud('TAEKWONDO'), tieneDiscapacidad: false };
      c.crearNuevaFicha();

      c.datosAlta.tipoTarifa = 'PADRES_HIJOS';
      c.cambiarTarifa();
      expect(c.requiereRolFamiliar).toBeTrue();
      expect(c.requiereGrupoFamiliar).toBeFalse();
      expect(c.finalizacionValida).toBeFalse();
      c.datosAlta.rolFamiliar = 'MADRE';
      expect(c.finalizacionValida).toBeTrue();

      c.datosAlta.tipoTarifa = 'HERMANOS';
      c.cambiarTarifa();
      expect(c.datosAlta.rolFamiliar).toBe('NINGUNO');
      expect(c.requiereGrupoFamiliar).toBeTrue();
      expect(c.finalizacionValida).toBeFalse();
      c.datosAlta.grupoFamiliar = 'GARCIA';
      expect(c.finalizacionValida).toBeTrue();

      c.datosAlta.tipoTarifa = 'ADULTO';
      c.cambiarTarifa();
      expect(c.datosAlta.grupoFamiliar).toBe('');
      expect(c.requiereRolFamiliar).toBeFalse();
      expect(c.requiereGrupoFamiliar).toBeFalse();
    });

    it('segunda escuela: las banderas de sus tarifas, no los códigos', () => {
      const conBanderas: Catalogo = {
        ...CATALOGO_SEGUNDA_ESCUELA,
        tarifas: CATALOGO_SEGUNDA_ESCUELA.tarifas.map((t) => (t.codigo === 'BONO' ? { ...t, requiereGrupoFamiliar: true } : t)),
      };
      const c = crear(conBanderas);
      c.seleccion = { ...solicitud('JUDO'), tieneDiscapacidad: false };
      c.crearNuevaFicha();
      c.datosAlta.tipoTarifa = 'BONO';
      c.cambiarTarifa();
      expect(c.requiereGrupoFamiliar).toBeTrue();
      expect(c.finalizacionValida).toBeFalse();
    });
  });

  describe('sin tarifa preseleccionada (otras escuelas, D9)', () => {
    async function pintar(catalogo: Catalogo, deporte: string) {
      await TestBed.configureTestingModule({
        imports: [PreinscripcionesAdminComponent],
        providers: [
          proveerCatalogoTesting(catalogo),
          { provide: PreinscripcionService, useValue: api },
          { provide: AuthenticationService, useValue: auth },
          { provide: ActivatedRoute, useValue: route },
          { provide: Router, useValue: router },
        ],
      }).compileComponents();
      const fixture = TestBed.createComponent(PreinscripcionesAdminComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();
      c.seleccion = { ...solicitud(deporte), tieneDiscapacidad: false, turnosSolicitados: [] };
      c.crearNuevaFicha();
      c.mostrandoFinalizacion = true;
      fixture.detectChanges();
      const select = (fixture.nativeElement as HTMLElement).querySelector('section.sport-data select') as HTMLSelectElement;
      return { c, fixture, select };
    }

    it('segunda escuela: primera opción deshabilitada «Selecciona una tarifa» y finalización inválida hasta elegir una real', async () => {
      const { c, fixture, select } = await pintar(CATALOGO_SEGUNDA_ESCUELA, 'JUDO');
      expect(c.datosAlta.tipoTarifa).toBe('');
      const primera = select.querySelectorAll('option')[0];
      expect(primera.textContent?.trim()).toBe('Selecciona una tarifa');
      expect(primera.disabled).toBeTrue();
      expect(c.finalizacionValida).toBeFalse();

      c.datosAlta.tipoTarifa = 'BONO';
      c.cambiarTarifa();
      fixture.detectChanges();
      expect(c.finalizacionValida).toBeTrue();
      const textos = Array.from(select.querySelectorAll('option')).map((o) => o.textContent?.trim());
      expect(textos).not.toContain('Selecciona una tarifa');
    });

    it('Taemoi: con tarifa preseleccionada no hay marcador', async () => {
      const { select } = await pintar(CATALOGO_TAEMOI, 'TAEKWONDO');
      const textos = Array.from(select.querySelectorAll('option')).map((o) => o.textContent?.trim());
      expect(textos).not.toContain('Selecciona una tarifa');
    });
  });

  describe('plantilla', () => {
    it('D8: las opciones muestran el nombre y envían el código; el rol aparece solo con la bandera', async () => {
      await TestBed.configureTestingModule({
        imports: [PreinscripcionesAdminComponent],
        providers: [
          proveerCatalogoTesting(CATALOGO_TAEMOI),
          { provide: PreinscripcionService, useValue: api },
          { provide: AuthenticationService, useValue: auth },
          { provide: ActivatedRoute, useValue: route },
          { provide: Router, useValue: router },
        ],
      }).compileComponents();
      const fixture = TestBed.createComponent(PreinscripcionesAdminComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();
      c.seleccion = { ...solicitud('TAEKWONDO'), tieneDiscapacidad: false, turnosSolicitados: [] };
      c.crearNuevaFicha();
      c.mostrandoFinalizacion = true;
      fixture.detectChanges();

      const raiz = fixture.nativeElement as HTMLElement;
      const seccion = raiz.querySelector('section.sport-data') as HTMLElement;
      expect(seccion).withContext('sección de tarifa').not.toBeNull();
      const opcionesHtml = Array.from(seccion.querySelectorAll('select')[0].querySelectorAll('option'))
        .map((o) => [o.value, o.textContent?.trim()]);
      expect(opcionesHtml).toEqual([
        ['ADULTO', 'Adulto'], ['ADULTO_GRUPO', 'Adulto Grupo'], ['FAMILIAR', 'Familiar'], ['INFANTIL', 'Infantil'],
        ['INFANTIL_GRUPO', 'Infantil Grupo'], ['HERMANOS', 'Hermanos'], ['PADRES_HIJOS', 'Padres e Hijos'],
      ]);
      expect(seccion.textContent).not.toContain('Rol familiar');

      c.datosAlta.tipoTarifa = 'PADRES_HIJOS';
      c.cambiarTarifa();
      fixture.detectChanges();
      expect(seccion.textContent).toContain('Rol familiar');
      expect(seccion.textContent).not.toContain('Grupo familiar');

      c.datosAlta.tipoTarifa = 'HERMANOS';
      c.cambiarTarifa();
      fixture.detectChanges();
      expect(seccion.textContent).toContain('Grupo familiar');
      expect(seccion.textContent).not.toContain('Rol familiar');
    });
  });
});
