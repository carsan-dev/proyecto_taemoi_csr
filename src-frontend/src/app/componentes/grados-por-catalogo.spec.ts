// Grados en los componentes: listas, nombres (D3) y colores (D4, D5) salen del catálogo. Taemoi y una segunda escuela.
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';

import { Catalogo } from '../features/catalogo/catalogo.models';
import { InformePdfService } from '../servicios/generales/informe-pdf.service';
import { LoadingService } from '../servicios/generales/loading.service';
import { proveerCatalogoTesting } from '../features/catalogo/testing/catalogo-service.testing';
import { CATALOGO_SEGUNDA_ESCUELA, CATALOGO_TAEMOI } from '../features/catalogo/testing/catalogo.fixtures';
import { fijarCatalogoActual, restablecerCatalogoActual } from '../features/catalogo/testing/catalogo-actual.testing';
import { getGradoNombreParaDeporte, getGradoTextStyle, getGradoTextStylePorNombre } from '../utilities/grado-colors';
import { EditarAlumnoComponent } from './endpoints/editar-alumno/editar-alumno.component';
import { ListadoAlumnosComponent } from './endpoints/listado-alumnos/listado-alumnos.component';
import { ListadoConvocatoriasComponent } from './endpoints/listado-convocatorias/listado-convocatorias.component';
import { PreinscripcionesAdminComponent } from './endpoints/preinscripciones-admin/preinscripciones-admin.component';
import { GrupoAlumnosModalComponent } from './generales/grupo-alumnos-modal/grupo-alumnos-modal.component';
import { MaterialesExamenUserComponent } from './vistas/vista-principal-user/materiales-examen-user/materiales-examen-user.component';
import { VistaPrincipalUserComponent } from './vistas/vista-principal-user/vista-principal-user.component';

const sinDependencias = {} as any;

/** Segunda escuela con los colores de los grados sin rellenar (`color_1/color_2` nulos): paleta de respaldo. */
const CATALOGO_SEGUNDA_ESCUELA_SIN_COLORES: Catalogo = {
  ...CATALOGO_SEGUNDA_ESCUELA,
  sistemasGrados: CATALOGO_SEGUNDA_ESCUELA.sistemasGrados.map((sistema) => ({
    ...sistema,
    grados: sistema.grados.map((grado) => ({ ...grado, color1: null, color2: null })),
  })),
};

const ESTILO_ROJO = 'color: #FF0000; font-weight: bold;';
const ESTILO_MARRON = 'color: #8B4513; font-weight: bold;';

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

describe('grado-colors con disciplina: colores y nombres del catálogo (D3, D4, D5)', () => {
  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: KB ROJO es marrón y «MARRÓN» (D4); TKD ROJO sigue rojo', () => {
    fijarCatalogoActual(CATALOGO_TAEMOI);
    expect(getGradoTextStyle('ROJO', 'KICKBOXING')).toBe(ESTILO_MARRON);
    expect(getGradoTextStyle('ROJO', 'TAEKWONDO')).toBe(ESTILO_ROJO);
    expect(getGradoNombreParaDeporte('ROJO', 'KICKBOXING')).toBe('MARRÓN');
    expect(getGradoNombreParaDeporte('ROJO', 'TAEKWONDO')).toBe('ROJO');
  });

  it('Taemoi: sin disciplina conserva la paleta por nombre (rojo, también en KB)', () => {
    fijarCatalogoActual(CATALOGO_TAEMOI);
    expect(getGradoTextStyle('ROJO')).toBe(ESTILO_ROJO);
  });

  it('segunda escuela con colores: usa los suyos y no la paleta de Taemoi (color1 inferior, color2 superior)', () => {
    fijarCatalogoActual(CATALOGO_SEGUNDA_ESCUELA);
    expect(getGradoTextStyle('BLANCO_JUDO', 'JUDO')).toContain('color: #FAFAFA');
    const marron = getGradoTextStyle('MARRON_JUDO', 'JUDO');
    // Dos colores: degradado con color2 arriba y color1 abajo.
    expect(marron).toContain('linear-gradient(to bottom, #FAFAFA 50%, #8B4513 50%)');
    expect(getGradoNombreParaDeporte('MARRON_JUDO', 'JUDO')).toBe('Marrón judo');
  });

  it('segunda escuela sin colores: paleta de respaldo por nombre de color', () => {
    fijarCatalogoActual(CATALOGO_SEGUNDA_ESCUELA_SIN_COLORES);
    expect(getGradoTextStyle('BLANCO_JUDO', 'JUDO')).toContain('color: #FFFFFF');
    // El nombre sigue siendo el del catálogo aunque no tenga colores.
    expect(getGradoNombreParaDeporte('MARRON_JUDO', 'JUDO')).toBe('Marrón judo');
  });

  describe('getGradoTextStylePorNombre (listados cortos: solo llega el nombre)', () => {
    it('Taemoi: «MARRÓN» es marrón; «ROJO» rojo; «BLANCO-AMARILLO» degradado amarillo/blanco', () => {
      fijarCatalogoActual(CATALOGO_TAEMOI);
      expect(getGradoTextStylePorNombre('MARRÓN')).toBe(ESTILO_MARRON);
      expect(getGradoTextStylePorNombre('ROJO')).toBe(ESTILO_ROJO);
      expect(getGradoTextStylePorNombre('BLANCO-AMARILLO')).toContain(
        'linear-gradient(to bottom, #FFFF00 50%, #FFFFFF 50%)'
      );
      expect(getGradoTextStylePorNombre(' marrón ')).toBe(ESTILO_MARRON);
    });

    it('segunda escuela: busca el nombre en su catálogo', () => {
      fijarCatalogoActual(CATALOGO_SEGUNDA_ESCUELA);
      expect(getGradoTextStylePorNombre('Marrón judo')).toContain('linear-gradient(to bottom, #FAFAFA 50%, #8B4513 50%)');
    });

    it('sin catálogo o nombre desconocido: paleta por nombre; vacío no falla', () => {
      restablecerCatalogoActual();
      expect(getGradoTextStylePorNombre('ROJO')).toBe(ESTILO_ROJO);
      fijarCatalogoActual(CATALOGO_TAEMOI);
      expect(getGradoTextStylePorNombre('XYZ')).toBe(getGradoTextStyle('XYZ'));
      expect(getGradoTextStylePorNombre(null)).toBe(getGradoTextStyle(''));
    });
  });
});

describe('grupo-alumnos-modal: color del grado por nombre (D5)', () => {
  // El componente no inyecta el catálogo: lo lee por el puente de las utilidades (cargado por el resolver).
  const modal = (catalogo: Catalogo) => {
    fijarCatalogoActual(catalogo);
    return new GrupoAlumnosModalComponent(sinDependencias);
  };

  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: «MARRÓN» sale marrón (antes salía gris al derivar el color del nombre)', () => {
    const c = modal(CATALOGO_TAEMOI);
    expect(c.getGradoStyle('MARRÓN')).toBe(ESTILO_MARRON);
    expect(c.getGradoStyle('NEGRO 1º DAN')).toContain('color: #000000');
  });
});

describe('listado-alumnos: nombre del grado (D3) y color por disciplina', () => {
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

  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: getGradoNombrePartes separa el sufijo con los nombres del catálogo', () => {
    const c = listado(CATALOGO_TAEMOI);
    expect(c.getGradoNombrePartes('ROJO_NEGRO_1_PUM', 'TAEKWONDO')).toEqual({ texto: 'ROJO NEGRO', sufijo: '1 PUM' });
    expect(c.getGradoNombrePartes('NEGRO_1_DAN', 'TAEKWONDO')).toEqual({ texto: 'NEGRO', sufijo: '1 DAN' });
    expect(c.getGradoNombrePartes('BLANCO_AMARILLO', 'TAEKWONDO')).toEqual({ texto: 'BLANCO AMARILLO', sufijo: null });
    expect(c.getGradoNombrePartes('ROJO', 'KICKBOXING')).toEqual({ texto: 'MARRÓN', sufijo: null });
    expect(c.getGradoNombrePartes(null, 'TAEKWONDO')).toEqual({ texto: 'Sin grado', sufijo: null });
  });

  it('Taemoi: getGradoStyle con disciplina aplica el marrón de KB ROJO (D4)', () => {
    const c = listado(CATALOGO_TAEMOI);
    expect(c.getGradoStyle('ROJO', 'KICKBOXING')).toBe(ESTILO_MARRON);
    expect(c.getGradoStyle('ROJO', 'TAEKWONDO')).toBe(ESTILO_ROJO);
  });

  it('segunda escuela: nombre y color del catálogo', () => {
    const c = listado(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.getGradoNombre('BLANCO_JUDO', 'JUDO')).toBe('Blanco judo');
    expect(c.getGradoNombrePartes('MARRON_JUDO', 'JUDO')).toEqual({ texto: 'Marrón judo', sufijo: null });
    expect(c.getGradoStyle('BLANCO_JUDO', 'JUDO')).toContain('color: #FAFAFA');
  });
});

describe('listado-convocatorias: nombre y color con la disciplina de la convocatoria', () => {
  const convocatorias = (catalogo: Catalogo) =>
    construir(catalogo, () => new ListadoConvocatoriasComponent(sinDependencias, sinDependencias, sinDependencias));

  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: KB ROJO marrón y «MARRÓN» (D3, D4); TKD «AMARILLO-NARANJA» en lugar del código', () => {
    const c = convocatorias(CATALOGO_TAEMOI);
    c.convocatoriaSeleccionada = { deporte: 'KICKBOXING' };
    expect(c.getGradoStyle('ROJO')).toBe(ESTILO_MARRON);
    expect(c.getGradoNombre('ROJO')).toBe('MARRÓN');
    c.convocatoriaSeleccionada = { deporte: 'TAEKWONDO' };
    expect(c.getGradoStyle('ROJO')).toBe(ESTILO_ROJO);
    expect(c.getGradoNombre('AMARILLO_NARANJA')).toBe('AMARILLO-NARANJA');
  });

  it('sin convocatoria abierta usa la disciplina del filtro', () => {
    const c = convocatorias(CATALOGO_TAEMOI);
    c.deporteSeleccionado = 'KICKBOXING';
    expect(c.getGradoNombre('ROJO')).toBe('MARRÓN');
  });

  it('segunda escuela: sus grados y colores', () => {
    const c = convocatorias(CATALOGO_SEGUNDA_ESCUELA);
    c.convocatoriaSeleccionada = { deporte: 'JUDO' };
    expect(c.getGradoNombre('MARRON_JUDO')).toBe('Marrón judo');
    expect(c.getGradoStyle('MARRON_JUDO')).toContain('#8B4513');
  });
});

describe('editar-alumno: grados que ofrecen los modales y grado inicial por disciplina', () => {
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

  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: TKD 19 grados, KB los 11 de su sistema con «MARRÓN», sin grados en Pilates', () => {
    const c = editar(CATALOGO_TAEMOI);
    const tkd = c.gradosDeLaDisciplina('TAEKWONDO');
    expect(tkd.length).toBe(19);
    expect(tkd[0]).toEqual(jasmine.objectContaining({ codigo: 'BLANCO', nombre: 'BLANCO' }));
    expect(tkd[11]).toEqual(jasmine.objectContaining({ codigo: 'ROJO_NEGRO_1_PUM', nombre: 'ROJO-NEGRO 1º PUM' }));
    const kb = c.gradosDeLaDisciplina('KICKBOXING');
    expect(kb.map((g) => g.codigo)).toEqual([
      'BLANCO', 'AMARILLO', 'NARANJA', 'VERDE', 'AZUL', 'ROJO',
      'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN',
    ]);
    expect(kb[5].nombre).toBe('MARRÓN');
    expect(c.gradosDeLaDisciplina('PILATES')).toEqual([]);
    expect(c.gradosDeLaDisciplina('')).toEqual([]);
  });

  it('Taemoi: el grado inicial de un deporte nuevo es BLANCO (TKD y KB) y se corrige al cambiar de disciplina', () => {
    const c = editar(CATALOGO_TAEMOI);
    c.nuevoDeporte = 'KICKBOXING';
    c.onNuevoDeporteChange('KICKBOXING');
    expect(c.gradoInicialDeporte).toBe('BLANCO');
    c.gradoInicialDeporte = 'AZUL_ROJO'; // grado de TKD que KB no ofrece
    c.onNuevoDeporteChange('KICKBOXING');
    expect(c.gradoInicialDeporte).toBe('BLANCO');
    c.gradoInicialDeporte = 'VERDE';
    c.onNuevoDeporteChange('KICKBOXING');
    expect(c.gradoInicialDeporte).toBe('VERDE'); // uno válido se respeta
  });

  it('Taemoi: sin grado (Pilates) no hay grado inicial', () => {
    const c = editar(CATALOGO_TAEMOI);
    c.gradoInicialDeporte = 'BLANCO';
    c.onNuevoDeporteChange('PILATES');
    expect(c.gradoInicialDeporte).toBe('');
  });

  it('segunda escuela: sus grados y su primer grado como inicial', () => {
    const c = editar(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.gradosDeLaDisciplina('JUDO').map((g) => g.codigo)).toEqual(['BLANCO_JUDO', 'MARRON_JUDO']);
    c.nuevoDeporte = 'JUDO';
    c.onNuevoDeporteChange('JUDO');
    expect(c.gradoInicialDeporte).toBe('BLANCO_JUDO');
  });

  it('abrir el modal de cambio de grado sin grado actual propone el primero de la disciplina', () => {
    const c = editar(CATALOGO_SEGUNDA_ESCUELA);
    c.deportesDelAlumno = [{ deporte: 'JUDO', grado: null } as any];
    c.abrirModalActualizarGrado('JUDO');
    expect(c.nuevoGradoActualizar).toBe('BLANCO_JUDO');
    c.deportesDelAlumno = [{ deporte: 'JUDO', grado: 'MARRON_JUDO' } as any];
    c.abrirModalActualizarGrado('JUDO');
    expect(c.nuevoGradoActualizar).toBe('MARRON_JUDO');
  });

  it('getGradoStyle con la disciplina del deporte (KB ROJO marrón, D4)', () => {
    const c = editar(CATALOGO_TAEMOI);
    expect(c.getGradoStyle('ROJO', 'KICKBOXING')).toBe(ESTILO_MARRON);
    expect(c.getGradoStyle('ROJO', 'TAEKWONDO')).toBe(ESTILO_ROJO);
  });
});

describe('preinscripciones-admin: grados de la disciplina de la solicitud', () => {
  const admin = (catalogo: Catalogo) =>
    construir(
      catalogo,
      () => new PreinscripcionesAdminComponent(sinDependencias, sinDependencias, sinDependencias, sinDependencias)
    );

  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: TKD los 19; KB los 11 (con «MARRÓN»); Pilates ninguno', () => {
    const c = admin(CATALOGO_TAEMOI);
    c.seleccion = { deporte: 'TAEKWONDO' };
    expect(c.gradosDisponibles.map((g) => g.codigo)).toEqual([
      'BLANCO', 'BLANCO_AMARILLO', 'AMARILLO', 'AMARILLO_NARANJA', 'NARANJA', 'NARANJA_VERDE', 'VERDE',
      'VERDE_AZUL', 'AZUL', 'AZUL_ROJO', 'ROJO', 'ROJO_NEGRO_1_PUM', 'ROJO_NEGRO_2_PUM', 'ROJO_NEGRO_3_PUM',
      'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN',
    ]);
    c.seleccion = { deporte: 'KICKBOXING' };
    expect(c.gradosDisponibles.map((g) => g.codigo)).toEqual([
      'BLANCO', 'AMARILLO', 'NARANJA', 'VERDE', 'AZUL', 'ROJO',
      'NEGRO_1_DAN', 'NEGRO_2_DAN', 'NEGRO_3_DAN', 'NEGRO_4_DAN', 'NEGRO_5_DAN',
    ]);
    expect(c.gradosDisponibles.find((g) => g.codigo === 'ROJO')?.nombre).toBe('MARRÓN');
    c.seleccion = { deporte: 'PILATES' };
    expect(c.gradosDisponibles).toEqual([]);
  });

  it('segunda escuela: sus grados; el grado inicial es el primero', () => {
    const c = admin(CATALOGO_SEGUNDA_ESCUELA);
    c.seleccion = { deporte: 'JUDO' };
    expect(c.gradosDisponibles.map((g) => g.codigo)).toEqual(['BLANCO_JUDO', 'MARRON_JUDO']);
    (c as any).prepararTarifa();
    expect(c.datosAlta.grado).toBe('BLANCO_JUDO');
  });
});

describe('materiales de examen: etiqueta del grado siguiente = grado.nombre (D3, D4)', () => {
  const materiales = (catalogo: Catalogo) =>
    construir(catalogo, () => new MaterialesExamenUserComponent(sinDependencias)) as any;

  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: nombres del catálogo; ROJO_NEGRO_n_PUM conserva «ROJO-NEGRO» (antes «NEGRO nº PUM», D3)', () => {
    const c = materiales(CATALOGO_TAEMOI);
    expect(c.obtenerEtiquetaCinturonObjetivo('AMARILLO_NARANJA', 'TAEKWONDO')).toBe('AMARILLO-NARANJA');
    expect(c.obtenerEtiquetaCinturonObjetivo('ROJO_NEGRO_1_PUM', 'TAEKWONDO')).toBe('ROJO-NEGRO 1º PUM');
    expect(c.obtenerEtiquetaCinturonObjetivo('NEGRO_3_DAN', 'KICKBOXING')).toBe('NEGRO 3º DAN');
    // D4: KB ROJO es «MARRÓN»
    expect(c.obtenerEtiquetaCinturonObjetivo('ROJO', 'KICKBOXING')).toBe('MARRÓN');
    expect(c.obtenerEtiquetaCinturonObjetivo('ROJO', 'TAEKWONDO')).toBe('ROJO');
  });

  it('desconocidos, vacíos o sin disciplina: null', () => {
    const c = materiales(CATALOGO_TAEMOI);
    expect(c.obtenerEtiquetaCinturonObjetivo('DESCONOCIDO', 'TAEKWONDO')).toBeNull();
    expect(c.obtenerEtiquetaCinturonObjetivo('', 'TAEKWONDO')).toBeNull();
    expect(c.obtenerEtiquetaCinturonObjetivo(null, 'TAEKWONDO')).toBeNull();
    expect(c.obtenerEtiquetaCinturonObjetivo('ROJO', null)).toBeNull();
  });

  it('el título del temario usa el nombre (KB ROJO: «Marrón»)', () => {
    const c = materiales(CATALOGO_TAEMOI);
    expect(c.construirTituloTemarioParaGrado('ROJO', 'KICKBOXING')).toBe('Temario para cinturón Marrón');
    expect(c.construirTituloTemarioParaGrado('ROJO_NEGRO_2_PUM', 'TAEKWONDO')).toBe(
      'Temario para cinturón Rojo-Negro 2º Pum'
    );
    expect(c.construirTituloTemarioParaGrado(null, 'TAEKWONDO')).toBe('Temario');
  });

  it('nombre del grado actual del material', () => {
    const c = materiales(CATALOGO_TAEMOI);
    expect(c.nombreGradoActual({ deporte: 'KICKBOXING', gradoActual: 'ROJO' })).toBe('MARRÓN');
    expect(c.nombreGradoActual({ deporte: 'TAEKWONDO', gradoActual: 'NO_EXISTE' })).toBe('NO_EXISTE');
    expect(c.nombreGradoActual({ deporte: 'TAEKWONDO', gradoActual: null })).toBeNull();
  });

  it('segunda escuela: nombre de su catálogo', () => {
    const c = materiales(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.obtenerEtiquetaCinturonObjetivo('MARRON_JUDO', 'JUDO')).toBe('Marrón judo');
    expect(c.obtenerEtiquetaCinturonObjetivo('ROJO', 'JUDO')).toBeNull();
  });
});

describe('vista de usuario: cinturón con colores y nombres del catálogo', () => {
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

  afterEach(() => restablecerCatalogoActual());

  it('Taemoi: KB ROJO marrón y «MARRÓN» (D3, D4); TKD ROJO rojo', () => {
    const c = usuario(CATALOGO_TAEMOI);
    expect(c.getBeltVisual('ROJO', 'KICKBOXING')).toEqual(
      jasmine.objectContaining({ topColor: '#8B4513', bottomColor: '#8B4513', isSplit: false, label: 'MARRÓN' })
    );
    expect(c.getBeltVisual('ROJO', 'TAEKWONDO')).toEqual(
      jasmine.objectContaining({ topColor: '#FF0000', bottomColor: '#FF0000', isSplit: false, label: 'ROJO' })
    );
  });

  it('Taemoi: split (color1 inferior, color2 superior) y franjas derivadas del código', () => {
    const c = usuario(CATALOGO_TAEMOI);
    expect(c.getBeltVisual('ROJO_NEGRO_3_PUM', 'TAEKWONDO')).toEqual({
      topColor: '#000000',
      bottomColor: '#FF0000',
      isSplit: true,
      stripeOffsets: [3, 9, 15],
      label: 'ROJO-NEGRO 3º PUM',
    });
    expect(c.getBeltVisual('NEGRO_2_DAN', 'KICKBOXING')).toEqual({
      topColor: '#000000',
      bottomColor: '#000000',
      isSplit: false,
      stripeOffsets: [3, 9],
      label: 'NEGRO 2º DAN',
    });
    expect(c.getBeltVisual('AMARILLO_NARANJA', 'TAEKWONDO')).toEqual({
      topColor: '#FFA500',
      bottomColor: '#FFFF00',
      isSplit: true,
      stripeOffsets: [],
      label: 'AMARILLO-NARANJA',
    });
  });

  it('segunda escuela: sus colores y nombres, sin la paleta de Taemoi', () => {
    const c = usuario(CATALOGO_SEGUNDA_ESCUELA);
    expect(c.getBeltVisual('MARRON_JUDO', 'JUDO')).toEqual({
      topColor: '#FAFAFA',
      bottomColor: '#8B4513',
      isSplit: true,
      stripeOffsets: [],
      label: 'Marrón judo',
    });
    expect(c.getBeltVisual('BLANCO_JUDO', 'judo')).toEqual(
      jasmine.objectContaining({ topColor: '#FAFAFA', bottomColor: '#FAFAFA', isSplit: false, label: 'Blanco judo' })
    );
  });

  it('segunda escuela sin colores: paleta de respaldo por nombre de color y nombre del catálogo', () => {
    const c = usuario(CATALOGO_SEGUNDA_ESCUELA_SIN_COLORES);
    expect(c.getBeltVisual('BLANCO_JUDO', 'JUDO')).toEqual(
      jasmine.objectContaining({ topColor: '#FFFFFF', bottomColor: '#FFFFFF', label: 'Blanco judo' })
    );
  });

  it('la caché distingue disciplinas y devuelve la misma referencia', () => {
    const c = usuario(CATALOGO_TAEMOI);
    const kb = c.getBeltVisual('ROJO', 'KICKBOXING');
    const tkd = c.getBeltVisual('ROJO', 'TAEKWONDO');
    expect(kb).not.toBe(tkd);
    expect(c.getBeltVisual('ROJO', 'KICKBOXING')).toBe(kb);
    expect(c.getBeltVisual('rojo', 'kickboxing')).toBe(kb);
  });

  it('la caché se vacía si el catálogo cambia (sin catálogo no queda el marrón anterior)', () => {
    const c = usuario(CATALOGO_TAEMOI);
    expect(c.getBeltVisual('ROJO', 'KICKBOXING').bottomColor).toBe('#8B4513');
    c.catalogo.invalidar();
    const sin = c.getBeltVisual('ROJO', 'KICKBOXING');
    // Sin catálogo (D15): sin nombre ni colores del catálogo; paleta por nombre de color y código.
    expect(sin.bottomColor).toBe('#FF0000');
    expect(sin.label).toBe('ROJO');
  });

  it('grado desconocido o vacío', () => {
    const c = usuario(CATALOGO_TAEMOI);
    expect(c.getBeltVisual('DESCONOCIDO', 'TAEKWONDO')).toEqual({
      topColor: '#CCCCCC',
      bottomColor: '#CCCCCC',
      isSplit: false,
      stripeOffsets: [],
      label: 'DESCONOCIDO',
    });
    expect(c.getBeltVisual('', 'TAEKWONDO').label).toBe('SIN GRADO');
  });
});
