import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AcentoEscuelaActual } from '../acento-escuela.directive';
import { ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { SIERRA_PRUEBA } from '../tema-prueba';
import { DisenoNuevoComponent } from './diseno-nuevo.component';
import { itSinReloj } from '../../testing/reloj-prueba';

const API = environment.apiUrl;

/** Datos ficticios: dos gestores y la propietaria, con nombres inventados. */
function miembro(usuarioId: number, nombre: string, apellidos: string, rol: string) {
  return {
    usuarioId,
    nombre,
    apellidos,
    email: `cuenta${usuarioId}@ejemplo.test`,
    authProvider: 'LOCAL',
    invitacionPendiente: false,
    roles: [{ rol, activa: true, cargo: null }],
    sedes: [],
    numeroGrupos: 0,
  };
}

function pagina(contenido: unknown[]) {
  return { contenido, pagina: 0, tamano: 100, total: contenido.length, totalPaginas: 1 };
}

describe('DisenoNuevoComponent (pantalla del propietario, 8b-I)', () => {
  let fixture: ComponentFixture<DisenoNuevoComponent>;
  let http: HttpTestingController;
  let resolverHoja: () => void;
  let reiniciar: jasmine.Spy;

  beforeEach(async () => {
    reiniciar = jasmine.createSpy('reiniciar');
    await TestBed.configureTestingModule({
      imports: [DisenoNuevoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: EstilosPanelService,
          useValue: { cargar: () => new Promise<void>((r) => (resolverHoja = r)) },
        },
        { provide: PermisosSesionService, useValue: { reiniciar } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DisenoNuevoComponent);
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;

  async function cargarHoja(): Promise<void> {
    fixture.detectChanges();
    resolverHoja();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function responderCarga(): void {
    http.expectOne(`${API}/panel/diseno-nuevo`).flush({
      porDefecto: false,
      usuarios: [
        { usuarioId: 4, permitido: true, activo: true },
        { usuarioId: 8, permitido: false, activo: false },
        { usuarioId: 9, permitido: false, activo: false },
      ],
    });
    http
      .expectOne((r) => r.url === `${API}/miembros` && r.params.get('rol') === 'PROPIETARIO')
      .flush(pagina([miembro(4, 'Lucía', 'Ferrer Prado', 'PROPIETARIO')]));
    http
      .expectOne((r) => r.url === `${API}/miembros` && r.params.get('rol') === 'ADMINISTRADOR')
      .flush(pagina([miembro(8, 'Tomás', 'Ibarra Gil', 'ADMINISTRADOR')]));
    fixture.detectChanges();
  }

  async function listo(): Promise<void> {
    await cargarHoja();
    responderCarga();
  }

  const filas = () => Array.from(el().querySelectorAll<HTMLElement>('[data-usuario]'));
  const fila = (id: number) => el().querySelector<HTMLElement>(`[data-usuario="${id}"]`)!;
  const boton = (id: number) => fila(id).querySelector<HTMLButtonElement>('button')!;

  itSinReloj('espera a la hoja del panel nuevo y su raíz lleva la clase pn', async () => {
    fixture.detectChanges();
    expect(el().querySelector('.pn')).not.toBeNull();
    expect(el().querySelector('[data-usuario]')).toBeNull();
    http.expectNone(`${API}/panel/diseno-nuevo`);
    await cargarHoja();
    responderCarga();
    expect(filas().length).toBe(3);
  });

  itSinReloj('8b-C: «Diseño nuevo» sigue en neutros aunque haya marca aplicada (se abre también con el panel de hoy)', async () => {
    const quitarTokens = ponerTokensDePrueba();
    TestBed.inject(AcentoEscuelaActual).poner(SIERRA_PRUEBA);
    await listo();
    const raiz = el().querySelector<HTMLElement>('.pn')!;
    for (const variable of ['--pn-marca-primario-tinta', '--pn-marca-primario-suave', '--pn-acento', '--pn-acento-borde']) {
      expect(raiz.style.getPropertyValue(variable)).withContext(variable).toBe('');
    }
    const secundario = getComputedStyle(el().querySelector('.diseno__boton--secundario')!);
    expect(secundario.color).withContext('texto en la tinta neutra').toBe('rgb(26, 34, 48)');
    expect(secundario.borderTopColor).withContext('borde en la tinta neutra').toBe('rgb(26, 34, 48)');
    const fila = getComputedStyle(boton(4));
    expect(fila.color).withContext('botón de fila').toBe('rgb(26, 34, 48)');
    quitarTokens();
  });

  itSinReloj('cruza el estado con los miembros: nombre, rol visible y estado; sin nombre, la cuenta por número', async () => {
    await listo();
    expect(filas().map((f) => f.dataset['usuario'])).toEqual(['4', '8', '9']);
    expect(fila(4).textContent).toContain('Lucía Ferrer Prado');
    expect(fila(4).textContent).toContain('Administración');
    expect(fila(4).textContent).toContain('Usa el diseño nuevo');
    expect(fila(8).textContent).toContain('Tomás Ibarra Gil');
    expect(fila(8).textContent).toContain('Gestión');
    expect(fila(8).textContent).toContain('Sin permiso');
    expect(fila(9).textContent).toContain('Cuenta n.º 9');
    expect(boton(4).textContent?.trim()).toBe('Quitar permiso');
    expect(boton(8).textContent?.trim()).toBe('Dar permiso');
  });

  itSinReloj('el estado de cada fila deja claro que con permiso no hay que volver a darlo', async () => {
    await listo();
    const base = { usuarioId: 1, nombre: 'X', rol: '' };
    const texto = (permitido: boolean, activo: boolean) =>
      fixture.componentInstance.textoEstado({ ...base, permitido, activo });
    expect(texto(true, true)).toBe('Usa el diseño nuevo');
    expect(texto(true, false)).toBe('Con permiso · usa el diseño anterior');
    expect(texto(false, false)).toBe('Sin permiso');
  });

  itSinReloj('un clic, una petición; al terminar, la fila cambia y los permisos se vuelven a pedir', async () => {
    await listo();
    boton(8).click();
    boton(8).click();
    fixture.detectChanges();
    expect(boton(8).disabled).toBeTrue();
    expect(boton(8).getAttribute('aria-busy')).toBe('true');

    const peticion: TestRequest = http.expectOne(`${API}/panel/diseno-nuevo/8`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ permitido: true });
    peticion.flush({ usuarioId: 8, permitido: true, activo: true });
    fixture.detectChanges();

    expect(fila(8).textContent).toContain('Usa el diseño nuevo');
    expect(boton(8).textContent?.trim()).toBe('Quitar permiso');
    expect(boton(8).disabled).toBeFalse();
    expect(reiniciar).toHaveBeenCalledTimes(1);
  });

  itSinReloj('con una petición en vuelo, los botones de TODAS las filas quedan desactivados', async () => {
    await listo();
    boton(8).click();
    fixture.detectChanges();
    expect([4, 8, 9].map((id) => boton(id).disabled)).toEqual([true, true, true]);
    expect(boton(4).getAttribute('aria-busy')).toBeNull();
    boton(4).click();
    http.expectNone(`${API}/panel/diseno-nuevo/4`);

    http.expectOne(`${API}/panel/diseno-nuevo/8`).flush({ usuarioId: 8, permitido: true, activo: true });
    fixture.detectChanges();
    expect([4, 8, 9].map((id) => boton(id).disabled)).toEqual([false, false, false]);
  });

  itSinReloj('quitar el permiso manda {permitido: false}', async () => {
    await listo();
    boton(4).click();
    const peticion = http.expectOne(`${API}/panel/diseno-nuevo/4`);
    expect(peticion.request.body).toEqual({ permitido: false });
    peticion.flush({ usuarioId: 4, permitido: false, activo: false });
    fixture.detectChanges();
    expect(fila(4).textContent).toContain('Sin permiso');
  });

  itSinReloj('un error de la API muestra su mensaje y deja reintentar', async () => {
    await listo();
    boton(9).click();
    http
      .expectOne(`${API}/panel/diseno-nuevo/9`)
      .flush({ mensaje: 'Solo el propietario y los gestores de la escuela pueden usar el diseño nuevo.' },
        { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const alerta = el().querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('Solo el propietario y los gestores de la escuela pueden usar el diseño nuevo.');
    expect(boton(9).disabled).toBeFalse();
    expect(fila(9).textContent).toContain('Sin permiso');
    expect(reiniciar).not.toHaveBeenCalled();
  });

  itSinReloj('un error sin mensaje dice qué pasó y qué hacer', async () => {
    await listo();
    boton(8).click();
    http.expectOne(`${API}/panel/diseno-nuevo/8`).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('No se pudo guardar el cambio');
  });

  itSinReloj('si la carga falla, muestra el mensaje y «Reintentar» vuelve a pedir', async () => {
    await cargarHoja();
    http.expectOne(`${API}/panel/diseno-nuevo`).flush({ mensaje: 'Acceso denegado.' }, { status: 403, statusText: 'No' });
    http.match((r) => r.url === `${API}/miembros`).filter((r) => !r.cancelled).forEach((r) => r.flush(pagina([])));
    fixture.detectChanges();

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Acceso denegado.');
    const reintentar = Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Reintentar'));
    expect(reintentar).toBeDefined();
    reintentar!.click();
    responderCarga();
    expect(filas().length).toBe(3);
  });

  itSinReloj('«Para todo el equipo» está construido pero oculto (decisión 258)', async () => {
    await listo();
    expect(el().textContent).not.toContain('Para todo el equipo');
    expect(fixture.componentInstance.mostrarParaTodo).toBeFalse();
    http.expectNone(`${API}/panel/diseno-nuevo/por-defecto`);
  });
});
