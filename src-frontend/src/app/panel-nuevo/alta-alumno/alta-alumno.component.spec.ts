import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { fijarCatalogoActual, restablecerCatalogoActual } from '../../features/catalogo/testing/catalogo-actual.testing';
import { CATALOGO_TAEMOI } from '../../features/catalogo/testing/catalogo.fixtures';
import { proveerCatalogoTesting } from '../../features/catalogo/testing/catalogo-service.testing';
import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AvisosService } from '../avisos/avisos.service';
import { pintarCapas, ponerTokensDePrueba } from '../capas/tokens-prueba';
import { EstilosPanelService } from '../estilos-panel.service';
import { AltaAlumnoComponent } from './alta-alumno.component';
import { fechaHoy } from './alta-alumno-vista';

const API = environment.apiUrl;
const setTimeoutReal = window.setTimeout.bind(window);

const GRADOS = [
  { id: 1, tipoGrado: 'BLANCO' },
  { id: 2, tipoGrado: 'AMARILLO' },
];

function permisos(variasSedes: boolean) {
  return {
    obtener: () =>
      of({
        roles: ['ROLE_ADMIN'],
        esAdminPlataforma: false,
        sedes: variasSedes
          ? [
              { id: 7, nombre: 'Utrera', activa: true },
              { id: 8, nombre: 'Los Palacios', activa: true },
              { id: 9, nombre: 'Antigua', activa: false },
            ]
          : [{ id: 7, nombre: 'Utrera', activa: true }],
        variasSedes,
        tieneGrupos: false,
        capacidades: [],
        disenoNuevo: { activo: true, permitido: true },
      }),
  };
}

describe('AltaAlumnoComponent (8b-3l, alta de alumno del panel nuevo)', () => {
  let fixture: ComponentFixture<AltaAlumnoComponent>;
  let http: HttpTestingController;
  let navegar: jasmine.Spy;
  let quitarTokens: () => void;
  let avisos: AvisosService;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const texto = (n: Element | null | undefined): string => (n?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const comp = (): AltaAlumnoComponent => fixture.componentInstance;
  const darDeAlta = (): HTMLButtonElement => el().querySelector<HTMLButtonElement>('#alta-dar-de-alta')!;
  const dialogo = (): HTMLDialogElement | null => document.querySelector<HTMLDialogElement>('dialog.pn-dialogo');
  const textosAvisos = (): string[] => avisos.lista().map((a) => a.texto);

  async function esperar(veces = 4): Promise<void> {
    for (let i = 0; i < veces; i++) {
      await new Promise((r) => setTimeoutReal(r, 5));
    }
    pintarCapas();
    fixture.detectChanges();
  }

  function crear(variasSedes = false): void {
    TestBed.overrideProvider(PermisosSesionService, { useValue: permisos(variasSedes) });
    fixture = TestBed.createComponent(AltaAlumnoComponent);
    http = TestBed.inject(HttpTestingController);
    avisos = TestBed.inject(AvisosService);
    navegar = spyOn(TestBed.inject(Router), 'navigate').and.returnValue(Promise.resolve(true));
    fixture.detectChanges();
    const grados = http.expectOne(`${API}/grados`);
    expect(grados.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    grados.flush(GRADOS);
    fixture.detectChanges();
  }

  /** Un alta válida: datos personales y contacto, y taekwondo con grado y tarifa. */
  function rellenar(): void {
    comp().form.patchValue({
      nombre: 'Lucía',
      apellidos: 'Ortega Ramos',
      fechaNacimiento: '2017-03-14',
      nif: '12345678z',
      direccion: 'Calle Mayor 14',
      telefono: '611234567',
      email: 'marta@correo.es',
    });
    // Como al escribir: el control queda tocado (un disable() posterior recalcula el pristine del grupo con los hijos).
    comp().form.get('nombre')!.markAsDirty();
    http.match((r) => r.url.startsWith(`${API}/grados/disponibles/`)).forEach((r) => r.flush(['BLANCO', 'AMARILLO']));
    const d = comp().deportes.at(0);
    d.patchValue({ deporte: 'TAEKWONDO' });
    comp().alCambiarDeporte(0);
    d.patchValue({ grado: 'AMARILLO', tipoTarifa: 'INFANTIL' });
    comp().alCambiarTarifa(0);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    quitarTokens = ponerTokensDePrueba();
    fijarCatalogoActual(CATALOGO_TAEMOI);
    await TestBed.configureTestingModule({
      imports: [AltaAlumnoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        proveerCatalogoTesting(CATALOGO_TAEMOI),
        { provide: PermisosSesionService, useValue: permisos(false) },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    TestBed.inject(AvisosService).cerrarTodos();
    pintarCapas();
    document.querySelectorAll('pn-dialogo').forEach((e) => e.remove());
    restablecerCatalogoActual();
    quitarTokens();
  });

  it('mantiene la autorización web como un segmento compacto y conserva el valor booleano', () => {
    crear();
    const segmento = el().querySelector<HTMLElement>('.alta__segmento')!;
    expect(texto(segmento.closest('fieldset')?.querySelector('legend'))).toBe('Autorización para publicar imágenes en la web obligatorio');
    expect(segmento.getBoundingClientRect().width).toBeGreaterThan(0);
    expect(segmento.getBoundingClientRect().width).toBeLessThan(120);
    expect(segmento.querySelectorAll('input[type="radio"]').length).toBe(2);
    const no = segmento.querySelector<HTMLInputElement>('#alta-autorizacion-no')!;
    no.click(); fixture.detectChanges();
    expect(comp().form.get('autorizacionWeb')!.value).toBeFalse();
    expect(no.checked).toBeTrue();
  });

  it('bloques, título y etiquetas «obligatorio» u «opcional»; con una sede no se pide «Sede»', () => {
    crear();
    expect(texto(el().querySelector('h1'))).toBe('Dar de alta un alumno');
    expect(Array.from(el().querySelectorAll('.alta__bloque h2')).map(texto)).toEqual([
      'Datos personales',
      'Contacto',
      'Responsable legal',
      'Deportes',
      'Foto y permisos',
    ]);
    const etiqueta = (id: string): string => texto(el().querySelector(`label[for="${id}"]`));
    expect(etiqueta('alta-nombre')).toBe('Nombre obligatorio');
    expect(etiqueta('alta-nif')).toBe('NIF / DNI opcional');
    expect(etiqueta('alta-telefono2')).toBe('Teléfono secundario opcional');
    // El buscador de hoy pasa el «for» de la etiqueta a su campo: se busca por texto.
    expect(texto(el().querySelector('.alta__deporte .alta__etiqueta'))).toBe('Deporte obligatorio');
    expect(el().querySelector('#alta-sede')).toBeNull();
    expect(texto(el().querySelector('.alta__resumen h2'))).toBe('Resumen del alta');
    expect(Array.from(el().querySelectorAll('.alta__nota')).map(texto)).toContain(
      'Nota: Puedes gestionar los deportes del alumno en la página de edición.'
    );
    expect(texto(el().querySelector('label[for="alta-foto"]'))).toBe('elige un archivo');
    expect(texto(el().querySelector('#alta-foto-ayuda'))).toBe('JPG, PNG, GIF, WebP (máx. 50MB)');
  });

  it('«Dar de alta» sigue activo con errores: no envía, enseña «Faltan N datos» con enlaces y los mensajes bajo cada campo', () => {
    crear();
    const boton = darDeAlta();
    expect(boton.disabled).toBeFalse();
    expect(boton.getAttribute('aria-disabled')).toBeNull();
    boton.click();
    fixture.detectChanges();
    http.expectNone((r) => r.url.endsWith('/alumnos/crear'));
    const resumen = el().querySelector<HTMLElement>('.alta__resumen-errores')!;
    expect(resumen.getAttribute('role')).toBe('alert');
    expect(document.activeElement).toBe(resumen);
    // nombre, apellidos, fecha, dirección, teléfono, email, deporte y tarifa.
    expect(texto(resumen.querySelector('h2'))).toBe('Faltan 8 datos');
    const enlaces = Array.from(resumen.querySelectorAll<HTMLAnchorElement>('a'));
    expect(enlaces.map((a) => a.getAttribute('href'))).toContain('#alta-direccion');
    expect(enlaces.map(texto)).toContain('Teléfono principal');
    const direccion = enlaces.find((a) => a.getAttribute('href') === '#alta-direccion')!;
    direccion.click();
    expect(document.activeElement).toBe(el().querySelector('#alta-direccion'));
    expect(texto(el().querySelector('#alta-direccion-error'))).toBe('La dirección es obligatoria');
    expect(el().querySelector('#alta-direccion')!.getAttribute('aria-describedby')).toContain('alta-direccion-error');
    expect(texto(el().querySelector('#alta-nombre-error'))).toBe('El nombre del alumno es obligatorio');
  });

  it('edad y categoría junto a la fecha de nacimiento; con menos de 18, la nota del responsable legal (sigue opcional)', () => {
    crear();
    comp().form.patchValue({ fechaNacimiento: '2017-03-14' });
    http.match((r) => r.url.startsWith(`${API}/grados/disponibles/2017-03-14`)).forEach((r) => r.flush([]));
    fixture.detectChanges();
    expect(texto(el().querySelector('.alta__edad'))).toMatch(/^\d+ años · categoría (infantil|precadete)$/);
    expect(texto(el().querySelector('#alta-nota-menor'))).toBe('Recomendado: es menor de edad. Sigue siendo opcional.');
    expect(texto(el().querySelector('label[for="alta-responsableLegalNombre"]'))).toBe('Nombre del responsable opcional');
    comp().form.patchValue({ fechaNacimiento: '1990-01-01' });
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();
    expect(el().querySelector('#alta-nota-menor')).toBeNull();
  });

  it('P-2: fecha de alta anterior a la de nacimiento no se envía; el mensaje sale bajo el campo y en el resumen', () => {
    crear();
    rellenar();
    comp().deportes.at(0).patchValue({ fechaAlta: '2017-03-13' });
    darDeAlta().click();
    fixture.detectChanges();
    http.expectNone((r) => r.url.endsWith('/alumnos/crear'));
    expect(texto(el().querySelector('#alta-fechaAlta-0-error'))).toBe('La fecha de alta no puede ser anterior a la fecha de nacimiento.');
    expect(texto(el().querySelector('.alta__resumen-errores h2'))).toBe('Falta 1 dato');
    // Al cambiar la fecha de nacimiento se vuelve a comprobar.
    comp().form.patchValue({ fechaNacimiento: '2016-01-01' });
    http.match(() => true).forEach((r) => r.flush([]));
    fixture.detectChanges();
    expect(comp().form.valid).toBeTrue();
  });

  it('envía el MISMO multipart de hoy y salta a la ficha con «Alumno creado con 1 deporte(s)»', async () => {
    crear();
    rellenar();
    darDeAlta().click();
    fixture.detectChanges();
    const req: TestRequest = http.expectOne(`${API}/alumnos/crear`);
    expect(req.request.method).toBe('POST');
    expect(req.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    const datos = req.request.body as FormData;
    expect(datos.get('file')).toBeNull();
    const nuevo = JSON.parse(datos.get('nuevo') as string);
    expect(nuevo.nombre).toBe('Lucía');
    expect(nuevo.nif).toBe('12345678Z');
    expect(nuevo.responsableLegalNif).toBeNull();
    expect(nuevo.aptoParaExamen).toBeFalse();
    expect(nuevo.rolFamiliar).toBe('NINGUNO');
    expect(nuevo.autorizacionWeb).toBeTrue();
    expect('sede' in nuevo).toBeFalse();
    expect('sedePrincipalId' in nuevo).toBeFalse();
    expect(nuevo.deportesInicial.length).toBe(1);
    const d = nuevo.deportesInicial[0];
    expect(d.deporte).toBe('TAEKWONDO');
    expect(d.grado).toBe('AMARILLO');
    expect(d.tipoTarifa).toBe('INFANTIL');
    expect(d.cuantiaTarifa).toBe(28);
    expect(d.fechaAlta).toBe(fechaHoy());
    expect(d.fechaAltaInicial).toBe(fechaHoy());
    expect(d.competidor).toBeFalse();
    expect(d.fechaAltaCompeticion).toBeNull();
    // Ocupado mientras va: un segundo clic no envía otra vez.
    expect(darDeAlta().getAttribute('aria-busy')).toBe('true');
    darDeAlta().click();
    http.expectNone((r) => r.url.endsWith('/alumnos/crear'));
    req.flush({ id: 55 });
    await esperar();
    expect(navegar).toHaveBeenCalledWith(['/alumnosEditar', 55]);
    expect(textosAvisos()).toContain('Alumno creado con 1 deporte(s)');
  });

  it('con la foto: va como «file» con su nombre; sin id en la respuesta, al listado', async () => {
    crear();
    rellenar();
    const foto = new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4])], 'foto.jpg', { type: 'image/jpeg' });
    const entrada = el().querySelector<HTMLInputElement>('#alta-foto')!;
    const dt = new DataTransfer();
    dt.items.add(foto);
    entrada.files = dt.files;
    entrada.dispatchEvent(new Event('change'));
    await esperar(8);
    expect(comp().imagen).not.toBeNull();
    expect(texto(el().querySelector('.alta__soltar-titulo'))).toBe('Imagen del alumno seleccionada');
    darDeAlta().click();
    const req = http.expectOne(`${API}/alumnos/crear`);
    const archivo = (req.request.body as FormData).get('file') as File;
    expect(archivo.name).toBe('foto.jpg');
    req.flush({});
    await esperar();
    expect(navegar).toHaveBeenCalledWith(['/alumnosListar']);
  });

  it('P-1: con varias sedes, «Sede» es obligatoria («Indica la sede.») y va como sedePrincipalId; las inactivas no salen', () => {
    crear(true);
    const sede = el().querySelector<HTMLSelectElement>('#alta-sede')!;
    expect(sede).not.toBeNull();
    expect(Array.from(sede.options).map((o) => o.textContent!.trim())).toEqual(['Elige una sede', 'Utrera', 'Los Palacios']);
    rellenar();
    darDeAlta().click();
    fixture.detectChanges();
    http.expectNone((r) => r.url.endsWith('/alumnos/crear'));
    expect(texto(el().querySelector('#alta-sede-error'))).toBe('Indica la sede.');
    comp().form.patchValue({ sede: '8' });
    darDeAlta().click();
    const nuevo = JSON.parse((http.expectOne(`${API}/alumnos/crear`).request.body as FormData).get('nuevo') as string);
    expect(nuevo.sedePrincipalId).toBe(8);
    expect('sede' in nuevo).toBeFalse();
  });

  it('NIF duplicado (409): el texto del servidor bajo «NIF / DNI», en el resumen y en el aviso de error', async () => {
    crear();
    rellenar();
    darDeAlta().click();
    http.expectOne(`${API}/alumnos/crear`).flush('El alumno con NIF 12345678Z ya existe.', { status: 409, statusText: 'Conflict' });
    await esperar();
    expect(texto(el().querySelector('#alta-nif-error'))).toBe('El alumno con NIF 12345678Z ya existe.');
    expect(texto(el().querySelector('.alta__resumen-errores'))).toContain('NIF / DNI');
    expect(textosAvisos()).toContain('El alumno con NIF 12345678Z ya existe.');
    expect(navegar).not.toHaveBeenCalled();
    // Al corregirlo, el error del servidor se va.
    const nif = el().querySelector<HTMLInputElement>('#alta-nif')!;
    nif.value = '87654321X';
    nif.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el().querySelector('#alta-nif-error')).toBeNull();
  });

  it('error del servidor sin campo: en el resumen y en el aviso, con el texto de hoy si no trae ninguno', async () => {
    crear();
    rellenar();
    darDeAlta().click();
    http.expectOne(`${API}/alumnos/crear`).flush(null, { status: 500, statusText: 'Error' });
    await esperar();
    expect(texto(el().querySelector('.alta__resumen-errores'))).toContain('No has completado todos los campos requeridos');
    expect(textosAvisos()).toContain('No has completado todos los campos requeridos');
  });

  it('deportes: «Añadir otro deporte (1 de 4)» hasta 4; «Quitar» con uno solo avisa y no quita', () => {
    crear();
    const anadir = (): HTMLButtonElement =>
      Array.from(el().querySelectorAll<HTMLButtonElement>('button')).find((b) => texto(b).startsWith('Añadir otro deporte'))!;
    expect(texto(anadir())).toBe('Añadir otro deporte (1 de 4)');
    const quitar = el().querySelector<HTMLButtonElement>('.alta__deporte-cabecera button')!;
    expect(quitar.getAttribute('aria-disabled')).toBe('true');
    quitar.click();
    expect(comp().deportes.length).toBe(1);
    expect(textosAvisos()).toContain('Un alumno debe tener al menos 1 deporte');
    anadir().click();
    anadir().click();
    anadir().click();
    fixture.detectChanges();
    expect(comp().deportes.length).toBe(4);
    expect(texto(anadir())).toBe('Añadir otro deporte (4 de 4)');
    expect(anadir().disabled).toBeTrue();
    expect(texto(el().querySelectorAll('.alta__deporte-cabecera h3')[1])).toBe('Deporte 2');
  });

  it('licencia y competición: sin licencia avisa; con licencia y competidor, la categoría por edad y las fechas por defecto', () => {
    crear();
    rellenar();
    expect(texto(el().querySelector('.alta__deporte .alta__ayuda--bloque'))).toBe(
      'El alumno debe tener una licencia federativa activa para poder ser marcado como competidor.'
    );
    comp().alternarLicencia(0);
    fixture.detectChanges();
    comp().alternarCompetidor(0);
    fixture.detectChanges();
    const d = comp().deportes.at(0);
    expect(d.get('categoria')!.value).toBe('Infantil');
    expect(d.get('fechaAltaCompeticion')!.value).toBe(fechaHoy());
    // Apagar la licencia limpia la competición.
    comp().alternarLicencia(0);
    expect(d.get('competidor')!.value).toBeFalse();
    expect(d.get('categoria')!.value).toBe('');
  });

  it('cancelar: sin datos, al listado; con datos, «¿Salir sin dar de alta?» con «Seguir editando» y «Salir sin guardar»', async () => {
    crear();
    await comp().cancelar();
    expect(navegar).toHaveBeenCalledWith(['/alumnosListar']);
    navegar.calls.reset();

    rellenar();
    const promesa = comp().cancelar();
    await esperar();
    const d = dialogo()!;
    expect(texto(d.querySelector('h2'))).toBe('¿Salir sin dar de alta?');
    const botones = Array.from(d.querySelectorAll<HTMLButtonElement>('.pn-dialogo__pie button')).map(texto);
    expect(botones).toContain('Seguir editando');
    expect(botones).toContain('Salir sin guardar');
    Array.from(d.querySelectorAll<HTMLButtonElement>('button')).find((b) => texto(b) === 'Seguir editando')!.click();
    await promesa;
    expect(navegar).not.toHaveBeenCalled();

    const otra = comp().cancelar();
    await esperar();
    Array.from(dialogo()!.querySelectorAll<HTMLButtonElement>('button')).find((b) => texto(b) === 'Salir sin guardar')!.click();
    await otra;
    expect(navegar).toHaveBeenCalledWith(['/alumnosListar']);
  });

  it('si fallan los grados, el aviso de error con el texto de hoy', () => {
    TestBed.overrideProvider(PermisosSesionService, { useValue: permisos(false) });
    fixture = TestBed.createComponent(AltaAlumnoComponent);
    http = TestBed.inject(HttpTestingController);
    avisos = TestBed.inject(AvisosService);
    fixture.detectChanges();
    http.expectOne(`${API}/grados`).flush(null, { status: 500, statusText: 'Error' });
    expect(textosAvisos()).toContain('No se pudieron cargar los grados');
  });

  it('sin permisos legibles no pide la sede (el servidor decide, como hoy)', () => {
    TestBed.overrideProvider(PermisosSesionService, { useValue: { obtener: () => throwError(() => new Error('x')) } });
    fixture = TestBed.createComponent(AltaAlumnoComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${API}/grados`).flush(GRADOS);
    fixture.detectChanges();
    expect(el().querySelector('#alta-sede')).toBeNull();
  });
});
