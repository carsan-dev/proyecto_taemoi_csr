import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { AlumnosTipoPanelComponent } from './alumnos-tipo-panel.component';

const GRADOS = [
  { nombre: 'Amarillo', color1: '#FFD700', color2: null },
  { nombre: 'Blanco-Amarillo', color1: '#FFFFFF', color2: '#FFD700' },
];

describe('AlumnosTipoPanelComponent colorGrado', () => {
  let componente: AlumnosTipoPanelComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AlumnosTipoPanelComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: CatalogoService, useValue: { catalogo: () => ({ sistemasGrados: [{ grados: GRADOS }] }), cargar: () => of(null) } },
        { provide: AuthenticationService, useValue: { usuarioLogueadoCambio: of(false), emailCambio: of(null) } },
      ],
    });
    componente = TestBed.createComponent(AlumnosTipoPanelComponent).componentInstance;
  });

  it('grado de un color: ese color', () => {
    expect(componente.colorGrado('amarillo')).toBe('#FFD700');
  });

  it('medio grado: dos mitades en orden color1, color2', () => {
    expect(componente.colorGrado('BLANCO-AMARILLO')).toBe('linear-gradient(to right, #FFFFFF 50%, #FFD700 50%)');
  });

  it('sin grado o desconocido: null', () => {
    expect(componente.colorGrado(null)).toBeNull();
    expect(componente.colorGrado('')).toBeNull();
    expect(componente.colorGrado('NO EXISTE')).toBeNull();
  });
});
