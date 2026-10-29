// Disciplinas en los componentes: las listas y las reglas salen del catálogo (Taemoi y una segunda escuela).
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';

import { Catalogo } from '../features/catalogo/catalogo.models';
import { InformePdfService } from '../servicios/generales/informe-pdf.service';
import { LoadingService } from '../servicios/generales/loading.service';
import { PlataformaService } from '../servicios/plataforma/plataforma.service';
import { proveerCatalogoTesting } from '../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../features/catalogo/testing/catalogo.fixtures';
import { FichaAlumnoBase } from '../panel-nuevo/ficha/ficha-alumno-base';
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
      { provide: PlataformaService, useValue: {} },
    ],
  });
  return TestBed.runInInjectionContext(fabrica);
}

const sinDependencias = {} as any;

describe('editar-alumno: disciplinas y reglas', () => {
  const editar = (catalogo: Catalogo) =>
    construir(
      catalogo,
      () =>
        new FichaAlumnoBase(
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
