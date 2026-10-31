import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { CatalogoDisciplina } from '../../features/catalogo/catalogo.models';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { CrearGrupoPanelComponent } from './crear-grupo-panel.component';

const DISCIPLINAS: CatalogoDisciplina[] = [
  { codigo: 'KICKBOXING', nombre: 'Kickboxing', orden: 2, activa: true, edadAdulto: 14, permiteCompeticion: true, requiereLicencia: true, sistemaGradosId: null, tieneMaterialExamen: false },
  { codigo: 'INACTIVA', nombre: 'Inactiva', orden: 0, activa: false, edadAdulto: null, permiteCompeticion: false, requiereLicencia: false, sistemaGradosId: null, tieneMaterialExamen: false },
  { codigo: 'TAEKWONDO', nombre: 'Taekwondo', orden: 1, activa: true, edadAdulto: 14, permiteCompeticion: true, requiereLicencia: true, sistemaGradosId: 1, tieneMaterialExamen: true },
];

const PERMISOS_UNA: PermisosSesion = { roles: ['ROLE_ADMIN'], esAdminPlataforma: false, sedes: [{ id: 1, nombre: 'Central', activa: true }], variasSedes: false, tieneGrupos: true, capacidades: [] };
const PERMISOS_VARIAS: PermisosSesion = { ...PERMISOS_UNA, variasSedes: true, sedes: [
  { id: 1, nombre: 'Mollina', activa: true }, { id: 2, nombre: 'Estepa', activa: true }, { id: 3, nombre: 'Cerrada', activa: false }] };

describe('CrearGrupoPanelComponent (3m-1b)', () => {
  let permisos: PermisosSesion;
  let fixture: ComponentFixture<CrearGrupoPanelComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;

  beforeEach(async () => {
    permisos = PERMISOS_UNA;
    avisos = jasmine.createSpyObj('AvisosService', ['exito', 'error']);
    spyOn(Swal, 'fire');
    await TestBed.configureTestingModule({
      imports: [CrearGrupoPanelComponent],
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([]), { provide: AvisosService, useValue: avisos }, { provide: PermisosSesionService, useValue: { obtener: () => of(permisos) } }, { provide: AuthenticationService, useValue: { usuarioLogueadoCambio: of(false), emailCambio: of(null) } }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    cargarPanel();
  });

  function cargarPanel(disciplinas = DISCIPLINAS): void {
    const catalogo = TestBed.inject(CatalogoService);
    catalogo.invalidar();
    catalogo.cargar().subscribe();
    http.expectOne(`${environment.apiUrl}/catalogo`).flush({
      organizacion: { id: 1, comportamientoLegadoTaemoi: false }, disciplinas, sistemasGrados: [], categorias: [], tarifas: [],
    });
    fixture = TestBed.createComponent(CrearGrupoPanelComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  }

  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  function nombre(valor: string): HTMLInputElement {
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    return input;
  }

  function deporte(valor: string): HTMLSelectElement {
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    select.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    return select;
  }

  it('sitúa el error antes de la fila de ayuda completa y contador', () => {
    nombre('');
    const host = fixture.nativeElement as HTMLElement;
    const error = host.querySelector('#crear-grupo-error')!;
    const ayuda = host.querySelector('#crear-grupo-ayuda')!;
    const contador = host.querySelector('#crear-grupo-contador')!;
    expect(ayuda.textContent).toBe('Elige un nombre descriptivo para identificar el grupo fácilmente. Ejemplo: Infantiles cinturón blanco.');
    expect(error.nextElementSibling).toBe(ayuda.parentElement);
    expect(ayuda.parentElement).toBe(contador.parentElement);
    expect(getComputedStyle(ayuda.parentElement!).display).toBe('flex');
    expect(getComputedStyle(ayuda.parentElement!).gap).toBe('12px');
    expect(getComputedStyle(contador).whiteSpace).toBe('nowrap');
  });

  it('muestra solo disciplinas activas en el orden del catálogo y exige deporte para habilitar crear', () => {
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    const boton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(select.required).toBeTrue();
    expect(fixture.nativeElement.querySelector('label[for="crear-grupo-deporte"]').textContent).toContain('Deporte (obligatorio)');
    expect(Array.from(select.options).map((option) => option.value)).toEqual(['', 'TAEKWONDO', 'KICKBOXING']);
    expect(select.options[0].textContent).toBe('Selecciona un deporte…');
    expect(select.options[0].disabled).toBeTrue();
    expect(select.value).toBe('');
    expect(boton.disabled).toBeTrue();
    nombre('Infantiles');
    expect(boton.disabled).toBeTrue();
    fixture.componentInstance.crear();
    fixture.detectChanges();
    http.expectNone(`${environment.apiUrl}/grupos`);
    expect(fixture.nativeElement.querySelector('#crear-grupo-deporte-error').textContent).toContain('Elige el deporte del grupo');
    expect(select.getAttribute('aria-invalid')).toBe('true');
    expect(select.getAttribute('aria-describedby')).toBe('crear-grupo-deporte-error');
    deporte('KICKBOXING');
    expect(boton.disabled).toBeFalse();
    expect(fixture.nativeElement.querySelector('#crear-grupo-deporte-error')).toBeNull();
    nombre('');
    expect(boton.disabled).toBeTrue();
  });

  it('preselecciona la única disciplina activa sin depender de su código', () => {
    fixture.destroy();
    cargarPanel([{ ...DISCIPLINAS[0], codigo: 'JUDO', nombre: 'Judo' }, DISCIPLINAS[1]]);
    expect(fixture.componentInstance.deporte.value).toBe('JUDO');
    expect(fixture.nativeElement.querySelector('select').value).toBe('JUDO');
    nombre('Infantiles');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeFalse();
  });

  it('sin disciplinas no permite crear un grupo', () => {
    fixture.destroy();
    cargarPanel([]);
    nombre('Infantiles');
    fixture.componentInstance.crear();
    expect(fixture.componentInstance.deporte.value).toBe('');
    expect(fixture.nativeElement.querySelector('button[type="submit"]').disabled).toBeTrue();
    http.expectNone(`${environment.apiUrl}/grupos`);
  });

  it('enfoca el campo y conecta etiqueta, ayuda, contador y error; valida obligatorio y 50 caracteres', () => {
    const input = nombre('');
    expect(document.activeElement).toBe(input);
    expect(fixture.nativeElement.querySelector('label').htmlFor).toBe(input.id);
    expect(input.maxLength).toBe(50);
    expect(fixture.nativeElement.textContent).toContain('El nombre del grupo es obligatorio');
    expect(input.getAttribute('aria-describedby')).toContain('crear-grupo-error');
    nombre('a'.repeat(50));
    expect(fixture.componentInstance.nombre.valid).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('50 / 50');
    nombre('a'.repeat(51));
    expect(fixture.componentInstance.nombre.invalid).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Máximo 50 caracteres');
    fixture.componentInstance.crear();
    http.expectNone(`${environment.apiUrl}/grupos`);
  });

  it('envía nombre y deporte con la llamada de hoy, avisa y emite el grupo creado', () => {
    const creado = spyOn(fixture.componentInstance.creado, 'emit');
    nombre('Infantiles');
    deporte('TAEKWONDO');
    fixture.componentInstance.crear();
    const peticion = http.expectOne(`${environment.apiUrl}/grupos`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(peticion.request.body).toEqual({ nombre: 'Infantiles', deporte: 'TAEKWONDO' });
    peticion.flush({ id: 81, nombre: 'Infantiles' });
    expect(avisos.exito).toHaveBeenCalledWith('Grupo creado');
    expect(creado).toHaveBeenCalledWith(81);
  });

  it('atiende el submit nativo, evita navegar y envía la petición desde el DOM', () => {
    nombre('Infantiles');
    deporte('TAEKWONDO');
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    const submit = new Event('submit', { bubbles: true, cancelable: true });
    form.dispatchEvent(submit);
    expect(submit.defaultPrevented).toBeTrue();
    const peticion = http.expectOne(`${environment.apiUrl}/grupos`);
    expect(peticion.request.body).toEqual({ nombre: 'Infantiles', deporte: 'TAEKWONDO' });
    peticion.flush({ id: 81, nombre: 'Infantiles' });
  });

  for (const cuerpo of ['Nombre ya utilizado', { mensaje: 'No puedes crear grupos en esta sede' }, { message: 'Disciplina no disponible' }]) {
    it(`muestra el texto real del servidor (${JSON.stringify(cuerpo)}) sin cerrar el panel`, () => {
      nombre('Infantiles');
      deporte('TAEKWONDO');
      fixture.componentInstance.crear();
      http.expectOne(`${environment.apiUrl}/grupos`).flush(cuerpo, { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();
      const texto = typeof cuerpo === 'string' ? cuerpo : cuerpo.mensaje ?? cuerpo.message;
      expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(texto);
      expect(fixture.nativeElement.querySelectorAll('[role="alert"]').length).toBe(1);
      expect(avisos.error).not.toHaveBeenCalled();
      expect(Swal.fire).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('dialog').open).toBeTrue();
    });
  }

  it('un fallo 500 queda solo en el campo, sin aviso ni modal global', () => {
    nombre('Infantiles');
    deporte('TAEKWONDO');
    fixture.componentInstance.crear();
    http.expectOne(`${environment.apiUrl}/grupos`).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('No se pudo crear el grupo. Inténtalo de nuevo.');
    expect(avisos.error).not.toHaveBeenCalled();
    expect(Swal.fire).not.toHaveBeenCalled();
  });

  it('la llamada heredada sin contexto conserva el aviso global del interceptor', () => {
    const error = jasmine.createSpy('error');
    TestBed.inject(EndpointsService).crearGrupo({ nombre: 'Infantiles' }).subscribe({ error });
    const peticion = http.expectOne(`${environment.apiUrl}/grupos`);
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeFalse();
    peticion.flush({}, { status: 500, statusText: 'Error' });
    expect(error).toHaveBeenCalled();
    expect(Swal.fire).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Error del Servidor' }));
  });

  describe('sede (E9-1)', () => {
    function conVariasSedes(): void {
      permisos = PERMISOS_VARIAS;
      fixture.destroy();
      cargarPanel();
    }
    function selectSede(): HTMLSelectElement | null {
      return fixture.nativeElement.querySelector('#crear-grupo-sede');
    }
    function elegirSede(valor: string): void {
      const select = selectSede()!;
      select.selectedIndex = Array.from(select.options).findIndex((o) => o.textContent === valor);
      select.dispatchEvent(new Event('change'));
      select.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
    }

    it('con una sola sede no hay campo y el cuerpo es el de hoy', () => {
      expect(selectSede()).toBeNull();
      nombre('Infantiles');
      deporte('TAEKWONDO');
      fixture.componentInstance.crear();
      const peticion = http.expectOne(`${environment.apiUrl}/grupos`);
      expect(peticion.request.body).toEqual({ nombre: 'Infantiles', deporte: 'TAEKWONDO' });
      peticion.flush({ id: 9 });
    });

    it('con varias sedes es el tercer campo, ofrece solo las activas y manda sedeId', () => {
      conVariasSedes();
      const select = selectSede()!;
      expect(fixture.nativeElement.querySelector('label[for="crear-grupo-sede"]').textContent).toContain('Sede (obligatorio)');
      expect(Array.from(fixture.nativeElement.querySelectorAll('select')).indexOf(select)).toBe(1);
      expect(Array.from(select.options).map((o) => o.textContent)).toEqual(['Elige una sede', 'Mollina', 'Estepa']);
      nombre('Infantiles');
      deporte('TAEKWONDO');
      elegirSede('Estepa');
      fixture.componentInstance.crear();
      const peticion = http.expectOne(`${environment.apiUrl}/grupos`);
      expect(peticion.request.body).toEqual({ nombre: 'Infantiles', deporte: 'TAEKWONDO', sedeId: 2 });
      peticion.flush({ id: 9 });
    });

    it('sin elegir sede avisa y no crea', () => {
      conVariasSedes();
      nombre('Infantiles');
      deporte('TAEKWONDO');
      const boton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
      expect(boton.disabled).toBeFalse();
      fixture.componentInstance.crear();
      fixture.detectChanges();
      http.expectNone(`${environment.apiUrl}/grupos`);
      expect(fixture.nativeElement.querySelector('#crear-grupo-sede-error').textContent).toContain('Elige la sede del grupo.');
      expect(selectSede()!.getAttribute('aria-invalid')).toBe('true');
      elegirSede('Mollina');
      expect(fixture.nativeElement.querySelector('#crear-grupo-sede-error')).toBeNull();
      expect(boton.disabled).toBeFalse();
    });

    it('sin sede el botón de crear sigue activo y el clic real muestra el error sin petición', () => {
      conVariasSedes();
      nombre('Infantiles');
      deporte('TAEKWONDO');
      const boton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
      expect(boton.disabled).toBeFalse();
      boton.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('#crear-grupo-sede-error').textContent).toContain('Elige la sede del grupo.');
      expect(selectSede()!.getAttribute('aria-invalid')).toBe('true');
      http.expectNone(`${environment.apiUrl}/grupos`);
    });

    it('un 400 del servidor sobre la sede se muestra en su campo', () => {
      conVariasSedes();
      nombre('Infantiles');
      deporte('TAEKWONDO');
      elegirSede('Mollina');
      fixture.componentInstance.crear();
      http.expectOne(`${environment.apiUrl}/grupos`).flush({ mensaje: 'Indica la sede.' }, { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('#crear-grupo-sede-error').textContent).toContain('Indica la sede.');
      expect(fixture.nativeElement.querySelectorAll('[role="alert"]').length).toBe(1);
    });
  });
});
