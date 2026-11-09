import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AlumnosTipoPanelComponent } from './alumnos-tipo-panel.component';

const GRADOS = [
  { codigo: 'AMARILLO', nombre: 'Amarillo', color1: '#FFD700', color2: null },
  { codigo: 'BLANCO_AMARILLO', nombre: 'Blanco-Amarillo', color1: '#FFFFFF', color2: '#FFD700' },
];
const GRADOS_KB = [{ codigo: 'AMARILLO', nombre: 'Amarillo', color1: '#AAAAAA', color2: null }];
const CATALOGO = {
  disciplinas: [
    { codigo: 'TAEKWONDO', nombre: 'Taekwondo', sistemaGradosId: 1 },
    { codigo: 'KICKBOXING', nombre: 'Kickboxing', sistemaGradosId: 2 },
    { codigo: 'PILATES', nombre: 'Pilates', sistemaGradosId: null },
  ],
  sistemasGrados: [{ id: 1, grados: GRADOS }, { id: 2, grados: GRADOS_KB }],
};

describe('AlumnosTipoPanelComponent cinturon', () => {
  let componente: AlumnosTipoPanelComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AlumnosTipoPanelComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: CatalogoService, useValue: { catalogo: () => CATALOGO, cargar: () => of(null) } },
        { provide: AuthenticationService, useValue: { usuarioLogueadoCambio: of(false), emailCambio: of(null) } },
      ],
    });
    componente = TestBed.createComponent(AlumnosTipoPanelComponent).componentInstance;
  });

  it('grado de un color: color1 y sin color2, por código y disciplina exactos', () => {
    expect(componente.cinturon({ codigoGrado: 'AMARILLO', deporte: 'TAEKWONDO' })).toEqual({ color1: '#FFD700', color2: null });
  });

  it('medio grado: color1 y color2 (arriba)', () => {
    expect(componente.cinturon({ codigoGrado: 'BLANCO_AMARILLO', deporte: 'TAEKWONDO' })).toEqual({ color1: '#FFFFFF', color2: '#FFD700' });
  });

  it('el mismo código en otra disciplina: el de la disciplina de la inscripción, no la del nombre del tipo', () => {
    componente.grupoNombre = 'Taekwondo adultos';
    expect(componente.cinturon({ codigoGrado: 'AMARILLO', deporte: 'KICKBOXING' })).toEqual({ color1: '#AAAAAA', color2: null });
    expect(componente.cinturon({ codigoGrado: 'AMARILLO', deporte: 'TAEKWONDO' })).toEqual({ color1: '#FFD700', color2: null });
  });

  it('ya no se deduce por el nombre del grado ni del tipo de grupo', () => {
    componente.grupoNombre = 'Taekwondo infantil';
    expect(componente.cinturon({ codigoGrado: null, deporte: null })).toBeNull();
    expect(componente.cinturon({ codigoGrado: 'Amarillo', deporte: 'TAEKWONDO' })).toBeNull();
  });

  it('sin grado, desconocido o disciplina sin grados: null', () => {
    expect(componente.cinturon({ codigoGrado: null, deporte: 'TAEKWONDO' })).toBeNull();
    expect(componente.cinturon({ codigoGrado: 'NO_EXISTE', deporte: 'TAEKWONDO' })).toBeNull();
    expect(componente.cinturon({ codigoGrado: 'AMARILLO', deporte: 'PILATES' })).toBeNull();
    expect(componente.cinturon(null)).toBeNull();
  });

  it('rallas: las medias solo si se usan', () => {
    expect(componente.medias({ mediasRallas: 5, aplicaRallas: true })).toBe(5);
    expect(componente.medias({ mediasRallas: 5, aplicaRallas: false })).toBeNull();
    expect(componente.medias({ mediasRallas: null, aplicaRallas: false })).toBeNull();
  });
});

describe('AlumnosTipoPanelComponent género en «Lista/Listo para examen»', () => {
  it('FEMENINO da «Lista para examen» y sin género «Listo para examen»', () => {
    const alumno = (id: number, genero: string | null) => ({
      id, nombre: 'A' + id, apellidos: 'B', genero, grado: 'Amarillo', codigoGrado: 'AMARILLO', deporte: 'TAEKWONDO',
      mediasRallas: 10, aplicaRallas: true, activo: true,
    });
    TestBed.configureTestingModule({
      imports: [AlumnosTipoPanelComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: LecturasPanelService, useValue: { alumnosPorTipo: () => of([alumno(1, 'FEMENINO'), alumno(2, null)]) } },
        { provide: CatalogoService, useValue: { catalogo: () => CATALOGO, cargar: () => of(null) } },
        { provide: AuthenticationService, useValue: { usuarioLogueadoCambio: of(false), emailCambio: of(null) } },
      ],
    });
    const fixture = TestBed.createComponent(AlumnosTipoPanelComponent);
    fixture.detectChanges();
    const textos = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[data-listo]')).map((e) => e.textContent?.trim());
    expect(textos).toEqual(['Lista para examen', 'Listo para examen']);
  });
});
