import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
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
            grado: (d: string, codigo: string) => {
              const id = CATALOGO_TAEMOI.disciplinas.find((disciplina) => disciplina.codigo === d)?.sistemaGradosId;
              return CATALOGO_TAEMOI.sistemasGrados.find((sistema) => sistema.id === id)?.grados.find((grado) => grado.codigo === codigo);
            },
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

  for (const codigo of ['TAEKWONDO', 'KICKBOXING']) {
    it(codigo + ': renders the API names as received, with origin and destination belts', () => {
      const datos = disciplina(codigo, codigo, { bloques: [
        { id: 'b01_inicio_a_amarillo', nombre: 'Inicio a amarillo', grados: [{ codigo: 'BLANCO', nombre: 'BLANCO' }], tieneTemario: false, videos: 0, bytes: 0 },
        { id: 'b05_azul_a_rojo', nombre: 'Azul a rojo', grados: [{ codigo: 'AZUL', nombre: 'AZUL' }], tieneTemario: false, videos: 0, bytes: 0 },
        { id: 'b06_rojo_a_negro_1', nombre: 'Rojo a negro', grados: [{ codigo: 'ROJO', nombre: codigo === 'KICKBOXING' ? 'MARRÓN' : 'ROJO' }], tieneTemario: false, videos: 0, bytes: 0 },
        ...[1, 2, 3, 4, 5].map((dan) => ({
          id: dan === 5 ? 'b11_negro_5_a_maximo' : 'b' + String(dan + 6).padStart(2, '0') + '_negro_' + dan + '_a_negro_' + (dan + 1),
          nombre: 'Negro ' + dan,
          grados: [
            ...(dan <= 3 ? [{ codigo: 'ROJO_NEGRO_' + dan + '_PUM', nombre: 'ROJO-NEGRO PUM' }] : []),
            { codigo: 'NEGRO_' + dan + '_DAN', nombre: 'NEGRO DAN' },
          ],
          tieneTemario: false, videos: 0, bytes: 0,
        })),
      ] });
      cargar({ disciplinas: [datos] });
      const nombres = filas().map((fila) => texto(fila.querySelector('.fila__nombre')));
      expect(nombres.slice(0, 3)).toEqual(['Inicio a amarillo', 'Azul a rojo', 'Rojo a negro']);
      const colorRojo = codigo === 'KICKBOXING' ? 'rgb(139, 69, 19)' : 'rgb(255, 0, 0)';
      const esperados = [
        ['rgb(255, 255, 255)', 'rgb(255, 255, 0)'],
        ['rgb(0, 0, 255)', colorRojo],
        [colorRojo, 'rgb(0, 0, 0)'],
        ...Array.from({ length: 5 }, () => ['rgb(0, 0, 0)', 'rgb(0, 0, 0)']),
      ];
      filas().forEach((fila, i) => {
        expect(Array.from(fila.querySelectorAll<HTMLElement>('.fila__cinto')).map((cinto) => cinto.style.background)).toEqual(esperados[i]);
        expect(fila.getAttribute('href')).toBe('/material-examen/' + codigo + '/' + datos.bloques[i].id);
      });
      expect(datos.bloques[0].nombre).toBe('Inicio a amarillo');
      expect(datos.bloques[2].grados[0].codigo).toBe('ROJO');
    });
  }

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
