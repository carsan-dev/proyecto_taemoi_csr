import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import Swal from 'sweetalert2';

import { environment } from '../../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../../core/http-context';
import { errorInterceptor } from '../../core/interceptors/error.interceptor';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AvisosService } from '../avisos/avisos.service';
import { CrearGrupoPanelComponent } from './crear-grupo-panel.component';

describe('CrearGrupoPanelComponent (3m-1)', () => {
  let fixture: ComponentFixture<CrearGrupoPanelComponent>;
  let http: HttpTestingController;
  let avisos: jasmine.SpyObj<AvisosService>;

  beforeEach(async () => {
    avisos = jasmine.createSpyObj('AvisosService', ['exito', 'error']);
    spyOn(Swal, 'fire');
    await TestBed.configureTestingModule({
      imports: [CrearGrupoPanelComponent],
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting(), provideRouter([]), { provide: AvisosService, useValue: avisos }],
    }).compileComponents();
    fixture = TestBed.createComponent(CrearGrupoPanelComponent);
    http = TestBed.inject(HttpTestingController);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

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

  it('envía solo nombre con la llamada de hoy, avisa y emite el grupo creado', () => {
    const creado = spyOn(fixture.componentInstance.creado, 'emit');
    nombre('Infantiles');
    fixture.componentInstance.crear();
    const peticion = http.expectOne(`${environment.apiUrl}/grupos`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    expect(peticion.request.body).toEqual({ nombre: 'Infantiles' });
    peticion.flush({ id: 81, nombre: 'Infantiles' });
    expect(avisos.exito).toHaveBeenCalledWith('Grupo creado');
    expect(creado).toHaveBeenCalledWith(81);
  });

  it('atiende el submit nativo, evita navegar y envía la petición desde el DOM', () => {
    nombre('Infantiles');
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    const submit = new Event('submit', { bubbles: true, cancelable: true });
    form.dispatchEvent(submit);
    expect(submit.defaultPrevented).toBeTrue();
    const peticion = http.expectOne(`${environment.apiUrl}/grupos`);
    expect(peticion.request.body).toEqual({ nombre: 'Infantiles' });
    peticion.flush({ id: 81, nombre: 'Infantiles' });
  });

  for (const cuerpo of ['Nombre ya utilizado', { mensaje: 'No puedes crear grupos en esta sede' }]) {
    it(`muestra el texto real del servidor (${JSON.stringify(cuerpo)}) sin cerrar el panel`, () => {
      nombre('Infantiles');
      fixture.componentInstance.crear();
      http.expectOne(`${environment.apiUrl}/grupos`).flush(cuerpo, { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();
      const texto = typeof cuerpo === 'string' ? cuerpo : cuerpo.mensaje;
      expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(texto);
      expect(fixture.nativeElement.querySelectorAll('[role="alert"]').length).toBe(1);
      expect(avisos.error).not.toHaveBeenCalled();
      expect(Swal.fire).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('dialog').open).toBeTrue();
    });
  }

  it('un fallo 500 queda solo en el campo, sin aviso ni modal global', () => {
    nombre('Infantiles');
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
});
