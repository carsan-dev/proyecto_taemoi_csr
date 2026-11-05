import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AltaEscuela, AltaEscuelaService } from './alta-escuela.service';

describe('AltaEscuelaService', () => {
  let servicio: AltaEscuelaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(AltaEscuelaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('pide las disciplinas de la plantilla con credenciales', () => {
    servicio.disciplinas().subscribe((lista) => expect(lista.length).toBe(1));
    const peticion = http.expectOne(`${environment.apiUrl}/plataforma/plantilla/disciplinas`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    peticion.flush([{ codigo: 'TKD', nombre: 'Taekwondo', conGrados: true, tarifas: 2, productos: 1 }]);
  });

  it('crea la escuela con POST y el cuerpo tal cual', () => {
    const alta: AltaEscuela = {
      nombre: 'Escuela Norte',
      emailContacto: 'norte@correo.es',
      sede: { nombre: 'Casariche' },
      propietario: { nombre: 'Ana', apellidos: 'Gil', email: 'ana@correo.es' },
      disciplinas: ['TKD'],
    };
    servicio.crear(alta).subscribe((r) => expect(r.id).toBe(9));
    const peticion = http.expectOne(`${environment.apiUrl}/plataforma/escuelas`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.body).toEqual(alta);
    peticion.flush({ id: 9, nombre: 'Escuela Norte', invitacionEnviadaA: 'ana@correo.es' });
  });
});
