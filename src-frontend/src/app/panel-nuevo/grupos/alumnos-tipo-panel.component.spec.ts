import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

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

  it('grado de un color: color1 y sin color2', () => {
    componente.grupoNombre = 'Taekwondo infantil';
    expect(componente.cinturon('amarillo')).toEqual({ color1: '#FFD700', color2: null });
  });

  it('medio grado: color1 y color2 (arriba)', () => {
    componente.grupoNombre = 'Taekwondo infantil';
    expect(componente.cinturon('BLANCO-AMARILLO')).toEqual({ color1: '#FFFFFF', color2: '#FFD700' });
  });

  it('por código del grado además de por nombre', () => {
    componente.grupoNombre = 'Taekwondo infantil';
    expect(componente.cinturon('BLANCO_AMARILLO')).toEqual({ color1: '#FFFFFF', color2: '#FFD700' });
  });

  it('el mismo grado en otra disciplina: el de la disciplina del tipo de grupo', () => {
    componente.grupoNombre = 'Kickboxing adultos';
    expect(componente.cinturon('Amarillo')).toEqual({ color1: '#AAAAAA', color2: null });
    componente.grupoNombre = 'Taekwondo adultos';
    expect(componente.cinturon('Amarillo')).toEqual({ color1: '#FFD700', color2: null });
  });

  it('tipo sin disciplina con grados: se busca en todos los sistemas', () => {
    componente.grupoNombre = 'Pilates';
    expect(componente.cinturon('Amarillo')).toEqual({ color1: '#FFD700', color2: null });
  });

  it('sin grado o desconocido: null', () => {
    expect(componente.cinturon(null)).toBeNull();
    expect(componente.cinturon('')).toBeNull();
    expect(componente.cinturon('NO EXISTE')).toBeNull();
  });
});
