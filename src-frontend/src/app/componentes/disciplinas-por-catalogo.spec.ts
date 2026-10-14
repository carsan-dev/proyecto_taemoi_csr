// Disciplinas en los componentes: las listas y las reglas salen del catálogo (Taemoi y una segunda escuela).
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';

import { Catalogo } from '../features/catalogo/catalogo.models';
import { InformePdfService } from '../servicios/generales/informe-pdf.service';
import { LoadingService } from '../servicios/generales/loading.service';
import { proveerCatalogoTesting } from '../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../features/catalogo/testing/catalogo.fixtures';
import { EditarAlumnoComponent } from './endpoints/editar-alumno/editar-alumno.component';
import { ListadoAlumnosComponent } from './endpoints/listado-alumnos/listado-alumnos.component';
import { ListadoConvocatoriasComponent } from './endpoints/listado-convocatorias/listado-convocatorias.component';
import { PreinscripcionesAdminComponent } from './endpoints/preinscripciones-admin/preinscripciones-admin.component';
import { TesoreriaCobrosComponent } from './endpoints/tesoreria-cobros/tesoreria-cobros.component';
import { VistaPrincipalAdminComponent } from './vistas/vista-principal-admin/vista-principal-admin.component';
import { MaterialesExamenUserComponent } from './vistas/vista-principal-user/materiales-examen-user/materiales-examen-user.component';
import { VistaPrincipalUserComponent } from './vistas/vista-principal-user/vista-principal-user.component';

/** Construye un componente en un contexto de inyección con el catálogo indicado ya cargado. */
function construir<T>(catalogo: Catalogo, fabrica: () => T): T {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      proveerCatalogoTesting(catalogo),
      InformePdfService,
      { provide: LoadingService, useValue: { show: () => undefined, hide: () => undefined } },
    ],
  });
  return TestBed.runInInjectionContext(fabrica);
}

const sinDependencias = {} as any;

describe('vista-principal-admin: etiqueta, emoji y color por disciplina (D2, D12)', () => {
  const admin = (catalogo: Catalogo) =>
    construir(catalogo, () => new VistaPrincipalAdminComponent(sinDependencias, sinDependencias, sinDependencias));

  it('Taemoi: nombre del catálogo y emoji/color de Taemoi por código', () => {
    const c = admin(CATALOGO_TAEMOI);
    const esperado: [string, string, string, string][] = [
      ['TAEKWONDO', 'Taekwondo', '🥋', '#4a90d9'],
      ['KICKBOXING', 'Kickboxing', '🥊', '#e74c3c'],
      ['PILATES', 'Pilates', '🧘', '#50c878'],
      // D2: «Defensa Personal Femenina» (antes «Defensa Personal» en este panel).
      ['DEFENSA_PERSONAL_FEMENINA', 'Defensa Personal Femenina', '🛡️', '#f5a623'],
    ];
    esperado.forEach(([codigo, nombre, emoji, color]) => {
      expect(c.getDeporteLabel(codigo)).toBe(nombre);
      expect(c.getDeporteIcon(codigo)).toBe(emoji);
      expect(c.getDeporteColor(codigo)).toBe(color);
    });
  });

  it('Taemoi: un código desconocido conserva el código y los valores neutros', () => {
    const c = admin(CATALOGO_TAEMOI);
    expect(c.getDeporteLabel('OTRO')).toBe('OTRO');
    expect(c.getDeporteIcon('OTRO')).toBe('⭐');
    expect(c.getDeporteColor('OTRO')).toBe('#6c757d');
  });

  it('segunda escuela: nombre del catálogo y emoji/color neutros (sin los de Taemoi)', () => {
    const c = admin(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.getDeporteLabel('JUDO')).toBe('Judo');
    expect(c.getDeporteIcon('JUDO')).toBe('⭐');
    expect(c.getDeporteColor('JUDO')).toBe('#6c757d');
    // Un código de Taemoi no se etiqueta con valores de Taemoi en otra escuela (D15).
    expect(c.getDeporteLabel('TAEKWONDO')).toBe('TAEKWONDO');
    expect(c.getDeporteIcon('TAEKWONDO')).toBe('⭐');
    expect(c.getDeporteColor('TAEKWONDO')).toBe('#6c757d');
  });
});

describe('tesorería: filtro de deporte', () => {
  const tesoreria = (catalogo: Catalogo) =>
    construir(catalogo, () => new TesoreriaCobrosComponent(sinDependencias, sinDependencias, sinDependencias, sinDependencias));

  it('Taemoi: TODOS y las cuatro disciplinas con su nombre', () => {
    const c = tesoreria(CATALOGO_TAEMOI);
    expect(c.deportes).toEqual([
      { value: 'TODOS', label: 'Todos los deportes' },
      { value: 'TAEKWONDO', label: 'Taekwondo' },
      { value: 'KICKBOXING', label: 'Kickboxing' },
      { value: 'PILATES', label: 'Pilates' },
      { value: 'DEFENSA_PERSONAL_FEMENINA', label: 'Defensa Personal Femenina' },
    ]);
  });

  it('segunda escuela: TODOS y JUDO (la inactiva no se ofrece, pero sí se etiqueta)', () => {
    const c = tesoreria(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.deportes).toEqual([
      { value: 'TODOS', label: 'Todos los deportes' },
      { value: 'JUDO', label: 'Judo' },
    ]);
    expect(c.getDeporteLabel('YOGA')).toBe('Yoga');
    expect(c.getDeporteLabel('GENERAL')).toBe('General');
    expect(c.getDeporteLabel('TODOS')).toBe('Todos los deportes');
  });
});

describe('convocatorias: deportes con grados', () => {
  const convocatorias = (catalogo: Catalogo) =>
    construir(catalogo, () => new ListadoConvocatoriasComponent(sinDependencias, sinDependencias, sinDependencias));

  it('Taemoi: Taekwondo y Kickboxing (las únicas con grados); empieza en la primera', () => {
    const c = convocatorias(CATALOGO_TAEMOI);
    expect(c.deportes).toEqual(['TAEKWONDO', 'KICKBOXING']);
    expect(c.deporteSeleccionado).toBe('TAEKWONDO');
    expect(c.claseCssDisciplina('DEFENSA_PERSONAL_FEMENINA')).toBe('defensa-personal-femenina');
  });

  it('segunda escuela: JUDO, sin valores de Taemoi', () => {
    const c = convocatorias(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.deportes).toEqual(['JUDO']);
    expect(c.deporteSeleccionado).toBe('JUDO');
  });
});

describe('preinscripciones-admin: deportes y grado', () => {
  const admin = (catalogo: Catalogo) =>
    construir(
      catalogo,
      () => new PreinscripcionesAdminComponent(sinDependencias, sinDependencias, sinDependencias, sinDependencias)
    );

  it('Taemoi: las cuatro disciplinas; la plantilla empieza en la primera; grado solo con sistema de grados', () => {
    const c = admin(CATALOGO_TAEMOI);
    expect(c.deportes).toEqual(['TAEKWONDO', 'KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA']);
    expect(c.deportePlantilla).toBe('TAEKWONDO');
    const requiereGrado = (deporte: string) => {
      c.seleccion = { deporte };
      return c.requiereGrado;
    };
    expect(requiereGrado('TAEKWONDO')).toBeTrue();
    expect(requiereGrado('KICKBOXING')).toBeTrue();
    expect(requiereGrado('PILATES')).toBeFalse();
    expect(requiereGrado('DEFENSA_PERSONAL_FEMENINA')).toBeFalse();
  });

  it('segunda escuela: JUDO aparece y exige grado', () => {
    const c = admin(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.deportes).toEqual(['JUDO']);
    expect(c.deportePlantilla).toBe('JUDO');
    c.seleccion = { deporte: 'JUDO' };
    expect(c.requiereGrado).toBeTrue();
  });
});

describe('editar-alumno: disciplinas y reglas', () => {
  const editar = (catalogo: Catalogo) =>
    construir(
      catalogo,
      () =>
        new EditarAlumnoComponent(
          sinDependencias,
          sinDependencias,
          new FormBuilder(),
          sinDependencias,
          sinDependencias,
          sinDependencias,
          sinDependencias
        )
    );

  it('Taemoi: ofrece añadir las disciplinas activas que el alumno no tiene', () => {
    const c = editar(CATALOGO_TAEMOI);
    c.deportesDelAlumno = [{ deporte: 'TAEKWONDO' } as any];
    expect(c.getDeportesDisponiblesParaAgregar()).toEqual(['KICKBOXING', 'PILATES', 'DEFENSA_PERSONAL_FEMENINA']);
    expect(c.deportes).toEqual(['TAEKWONDO', 'KICKBOXING']);
  });

  it('Taemoi: sin grado solo Pilates y defensa personal', () => {
    const c = editar(CATALOGO_TAEMOI);
    expect(c.esDeporteSinGrado('PILATES')).toBeTrue();
    expect(c.esDeporteSinGrado('DEFENSA_PERSONAL_FEMENINA')).toBeTrue();
    expect(c.esDeporteSinGrado('TAEKWONDO')).toBeFalse();
    expect(c.esDeporteSinGrado('KICKBOXING')).toBeFalse();
    expect(c.esDeporteSinGrado(null)).toBeFalse();
    expect(c.requiereGradoNuevoDeporte()).toBeTrue(); // sin deporte nuevo elegido
  });

  it('Taemoi (D10): «derecho de examen» solo en Kickboxing; «recompensa» en el resto', () => {
    const c = editar(CATALOGO_TAEMOI);
    expect(c.ofreceDerechoExamen('KICKBOXING')).toBeTrue();
    expect(c.ofreceRecompensaGrado('KICKBOXING')).toBeFalse();
    expect(c.ofreceDerechoExamen('TAEKWONDO')).toBeFalse();
    expect(c.ofreceRecompensaGrado('TAEKWONDO')).toBeTrue();
  });

  it('segunda escuela: ofrece JUDO, tiene grado y siempre «recompensa»', () => {
    const c = editar(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.getDeportesDisponiblesParaAgregar()).toEqual(['JUDO']);
    c.deportesDelAlumno = [{ deporte: 'JUDO' } as any];
    expect(c.getDeportesDisponiblesParaAgregar()).toEqual([]);
    expect(c.deportes).toEqual(['JUDO']);
    expect(c.esDeporteSinGrado('JUDO')).toBeFalse();
    expect(c.esDeporteSinGrado('YOGA')).toBeTrue();
    expect(c.ofreceDerechoExamen('JUDO')).toBeFalse();
    expect(c.ofreceRecompensaGrado('JUDO')).toBeTrue();
    expect(c.claseCssDisciplina('JUDO')).toBe('judo');
  });

  it('la sección de competidor depende de permiteCompeticion', () => {
    const taemoi = editar(CATALOGO_TAEMOI);
    expect(taemoi.catalogo.permiteCompeticion('TAEKWONDO')).toBeTrue();
    expect(taemoi.catalogo.permiteCompeticion('PILATES')).toBeFalse();
    const otra = editar(CATALOGO_SEGUNDA_ESCUELA);
    expect(otra.catalogo.permiteCompeticion('JUDO')).toBeTrue();
  });
});

describe('listado-alumnos: grado y licencia por disciplina', () => {
  const listado = (catalogo: Catalogo) =>
    construir(
      catalogo,
      () =>
        new ListadoAlumnosComponent(
          sinDependencias,
          sinDependencias,
          { show: () => undefined, hide: () => undefined } as any,
          sinDependencias,
          sinDependencias
        )
    );

  function conPrincipal(c: ListadoAlumnosComponent, deporte: string): void {
    c.deportesPorAlumno.set(1, [{ id: 1, deporte, principal: true, activo: true, tieneLicencia: false } as any]);
  }

  it('Taemoi: grado salvo Pilates/defensa personal; licencia solo Taekwondo y Kickboxing', () => {
    const c = listado(CATALOGO_TAEMOI);
    expect(c.deporteRequiereGrado('TAEKWONDO')).toBeTrue();
    expect(c.deporteRequiereGrado('KICKBOXING')).toBeTrue();
    expect(c.deporteRequiereGrado('PILATES')).toBeFalse();
    expect(c.deporteRequiereGrado('DEFENSA_PERSONAL_FEMENINA')).toBeFalse();

    ['TAEKWONDO', 'KICKBOXING'].forEach((deporte) => {
      conPrincipal(c, deporte);
      expect(c.alumnoRequiereLicencia(1)).toBeTrue();
      expect(c.getResumenAlumno(1).licenciaTexto).toBe('Sin licencia');
    });
    ['PILATES', 'DEFENSA_PERSONAL_FEMENINA'].forEach((deporte) => {
      conPrincipal(c, deporte);
      expect(c.alumnoRequiereLicencia(1)).toBeFalse();
      expect(c.getResumenAlumno(1).licenciaTexto).toBe('N/A');
    });
  });

  it('segunda escuela: JUDO tiene grado y no exige licencia; sus disciplinas se ofrecen en las listas', () => {
    const c = listado(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.deporteRequiereGrado('JUDO')).toBeTrue();
    conPrincipal(c, 'JUDO');
    expect(c.alumnoRequiereLicencia(1)).toBeFalse();
    expect(c.getResumenAlumno(1).licenciaTexto).toBe('N/A');
    expect(c.catalogo.disciplinasActivas().map((d) => d.codigo)).toEqual(['JUDO']);
    expect(c.catalogo.disciplinasActivas().filter((d) => d.requiereLicencia)).toEqual([]);
  });
});

describe('vista de usuario: estado de examen y retos por disciplina', () => {
  const usuario = (catalogo: Catalogo) =>
    construir(
      catalogo,
      () =>
        new VistaPrincipalUserComponent(
          sinDependencias,
          sinDependencias,
          sinDependencias,
          sinDependencias,
          sinDependencias,
          sinDependencias
        )
    ) as any;

  it('Taemoi: estado de examen salvo Pilates/defensa personal; retos propios de Taemoi', () => {
    const c = usuario(CATALOGO_TAEMOI);
    expect(c.deporteUsaEstadoExamen('TAEKWONDO')).toBeTrue();
    expect(c.deporteUsaEstadoExamen('kickboxing')).toBeTrue();
    expect(c.deporteUsaEstadoExamen('PILATES')).toBeFalse();
    expect(c.deporteUsaEstadoExamen('DEFENSA_PERSONAL_FEMENINA')).toBeFalse();
    expect(c.deporteSinGrado('PILATES')).toBeTrue();

    c.deportesDelAlumno = [{ deporte: 'TAEKWONDO', activo: true }];
    expect(c.obtenerRetosDiariosPorDeporteActivo().length).toBeGreaterThan(0);
  });

  it('segunda escuela: JUDO usa estado de examen y no hereda los retos de Taemoi', () => {
    const c = usuario(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.deporteUsaEstadoExamen('JUDO')).toBeTrue();
    expect(c.deporteSinGrado('YOGA')).toBeTrue();
    c.deportesDelAlumno = [{ deporte: 'JUDO', activo: true }];
    expect(c.obtenerRetosDiariosPorDeporteActivo()).toEqual([]);
  });
});

describe('materiales de examen: solo disciplinas con material', () => {
  const materiales = (catalogo: Catalogo) =>
    construir(catalogo, () => new MaterialesExamenUserComponent(sinDependencias)) as any;

  const alumnoCon = (...deportes: string[]) =>
    deportes.map((deporte, i) => ({ id: i + 1, deporte, grado: 'BLANCO', activo: true }));

  it('Taemoi: Taekwondo y Kickboxing', () => {
    const c = materiales(CATALOGO_TAEMOI);
    c.deportes = alumnoCon('PILATES', 'KICKBOXING', 'TAEKWONDO', 'DEFENSA_PERSONAL_FEMENINA');
    expect(c.obtenerDeportesConMaterial().map((d: any) => d.deporte)).toEqual(['KICKBOXING', 'TAEKWONDO']);
  });

  it('segunda escuela: JUDO no tiene material de examen', () => {
    const c = materiales(CATALOGO_SEGUNDA_ESCUELA);
    c.deportes = alumnoCon('JUDO');
    expect(c.obtenerDeportesConMaterial()).toEqual([]);
  });

  it('una disciplina con material (catálogo modificado) se ofrece aunque no sea de Taemoi', () => {
    const conMaterial: Catalogo = {
      ...CATALOGO_SEGUNDA_ESCUELA,
      disciplinas: CATALOGO_SEGUNDA_ESCUELA.disciplinas.map((d) =>
        d.codigo === 'JUDO' ? { ...d, tieneMaterialExamen: true } : d
      ),
    };
    const c = materiales(conMaterial);
    c.deportes = alumnoCon('JUDO');
    expect(c.obtenerDeportesConMaterial().map((d: any) => d.deporte)).toEqual(['JUDO']);
  });
});
