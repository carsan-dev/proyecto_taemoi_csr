import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { MaterialExamenPanelComponent } from './material-examen.component';
import { DisciplinaConBloques, ListadoMaterial } from './material-examen.models';

const API = `${environment.apiUrl}/material-examen`;
const MB = 1024 * 1024;

function disciplina(codigo: string, nombre: string, cambios: Partial<DisciplinaConBloques> = {}): DisciplinaConBloques {
  return {
    codigo,
    nombre,
    bloques: [
      {
        id: 'b01_inicio_a_amarillo',
        nombre: 'Inicio a amarillo',
        grados: [
          { codigo: 'BLANCO', nombre: 'BLANCO' },
          { codigo: 'BLANCO_AMARILLO', nombre: 'BLANCO-AMARILLO' },
        ],
        tieneTemario: true,
        videos: 7,
        bytes: 466 * MB,
      },
      { id: 'b06', nombre: 'Rojo a negro', grados: [{ codigo: 'ROJO', nombre: 'ROJO' }], tieneTemario: false, videos: 0, bytes: 0 },
    ],
    totales: { temarios: 1, videos: 7, bytes: 466 * MB },
    ...cambios,
  };
}

describe('MaterialExamenPanelComponent (pieza R2, lista de bloques)', () => {
  let fixture: ComponentFixture<MaterialExamenPanelComponent>;
  let http: HttpTestingController;
  let consulta: Record<string, string>;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const pestanas = (): HTMLButtonElement[] => Array.from(el().querySelectorAll<HTMLButtonElement>('[role="tab"]'));
  const filas = (): HTMLAnchorElement[] => Array.from(el().querySelectorAll<HTMLAnchorElement>('a.fila'));

  function cargar(listado: Partial<ListadoMaterial>): void {
    fixture = TestBed.createComponent(MaterialExamenPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(API).flush({ disciplinas: [], totales: { temarios: 0, videos: 0, bytes: 0 }, ...listado });
    fixture.detectChanges();
  }

  beforeEach(() => {
    consulta = {};
    TestBed.configureTestingModule({
      imports: [MaterialExamenPanelComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: CatalogoService,
          useValue: {
            grado: (_d: string, codigo: string) => ({ BLANCO: { color1: '#FFFFFF', color2: null }, BLANCO_AMARILLO: { color1: '#FFFFFF', color2: '#FFFF00' } })[codigo],
          },
        },
        { provide: ActivatedRoute, useFactory: () => ({ snapshot: { queryParamMap: convertToParamMap(consulta) } }) },
      ],
    });
  });

  afterEach(() => http.verify());

  it('pinta cada bloque como en el boceto', () => {
    cargar({ disciplinas: [disciplina('TAEKWONDO', 'Taekwondo')] });
    const [primera, segunda] = filas();
    expect(texto(primera.querySelector('.fila__nombre'))).toBe('Inicio a amarillo');
    expect(texto(primera.querySelector('.fila__grados'))).toBe('Alumnos de Blanco a Blanco-amarillo');
    expect(texto(primera.querySelector('.fila__pastilla--bien'))).toBe('Temario');
    expect(texto(primera.querySelector('.fila__pastillas'))).toContain('7 vídeos');
    expect(texto(primera.querySelector('.fila__peso'))).toBe('466 MB');
    expect(primera.getAttribute('href')).toBe('/material-examen/TAEKWONDO/b01_inicio_a_amarillo');
    const cintos = primera.querySelectorAll<HTMLElement>('.fila__cinto');
    expect(cintos.length).toBe(2);
    expect(cintos[1].style.background).toContain('rgb(255, 255, 0)');

    expect(texto(segunda.querySelector('.fila__grados'))).toBe('Alumnos de Rojo');
    expect(segunda.querySelector('.fila__pastilla--bien')).toBeNull();
    expect(texto(segunda.querySelector('.fila__pastillas'))).toBe('Sin vídeos');
  });

  it('resumen de la disciplina activa, sin pestañas si solo hay una', () => {
    cargar({ disciplinas: [disciplina('TAEKWONDO', 'Taekwondo')] });
    expect(pestanas().length).toBe(0);
    expect(texto(el().querySelector('.material__resumen'))).toBe('2 bloques · 7 vídeos · 466 MB');
  });

  it('pestañas por disciplina con sus teclas; solo la activa entra en el Tab', () => {
    cargar({ disciplinas: [disciplina('TAEKWONDO', 'Taekwondo'), disciplina('KICKBOXING', 'Kickboxing', { bloques: [], totales: { temarios: 0, videos: 0, bytes: 0 } })] });
    expect(el().querySelector('[role="tablist"]')?.getAttribute('aria-label')).toBe('Disciplina');
    expect(pestanas().map((p) => p.getAttribute('tabindex'))).toEqual(['0', '-1']);

    pestanas()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(pestanas()[1].getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(pestanas()[1]);
    expect(filas().length).toBe(0);

    pestanas()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(pestanas()[0].getAttribute('aria-selected')).toBe('true');
  });

  it('vuelve a la disciplina que trae la consulta', () => {
    consulta = { disciplina: 'KICKBOXING' };
    cargar({ disciplinas: [disciplina('TAEKWONDO', 'Taekwondo'), disciplina('KICKBOXING', 'Kickboxing')] });
    expect(pestanas()[1].getAttribute('aria-selected')).toBe('true');
  });

  it('error con reintento', () => {
    fixture = TestBed.createComponent(MaterialExamenPanelComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(API).flush({ mensaje: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(el().querySelector('.pn-bloque-error__titulo'))).toBe('No se pudo cargar el material de examen.');

    el().querySelector<HTMLButtonElement>('.pn-bloque-error__reintentar')!.click();
    http.expectOne(API).flush({ disciplinas: [disciplina('TAEKWONDO', 'Taekwondo')], totales: { temarios: 1, videos: 7, bytes: 1 } });
    fixture.detectChanges();
    expect(filas().length).toBe(2);
  });

  it('vacío sin disciplinas con bloques', () => {
    cargar({ disciplinas: [] });
    expect(el().querySelector('.pn-bloque-vacio')).not.toBeNull();
    expect(filas().length).toBe(0);
  });
});
