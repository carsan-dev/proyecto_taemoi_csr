import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { ERROR_HANDLED_LOCALLY } from '../core/http-context';
import { DisenoPanelService } from './diseno-panel.service';

const API = `${environment.apiUrl}/panel`;

describe('DisenoPanelService (API del interruptor, 8b-I)', () => {
  let servicio: DisenoPanelService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(DisenoPanelService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lee el estado de la gestión con la cookie y errores tratados en la pantalla', async () => {
    const lectura = firstValueFrom(servicio.listar());
    const peticion = http.expectOne(`${API}/diseno-nuevo`);
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.withCredentials).toBeTrue();
    expect(peticion.request.context.get(ERROR_HANDLED_LOCALLY)).toBeTrue();
    peticion.flush({ porDefecto: false, usuarios: [{ usuarioId: 3, permitido: true, activo: true }] });
    expect((await lectura).usuarios[0].usuarioId).toBe(3);
  });

  it('da o quita el permiso con {permitido}', () => {
    servicio.cambiarPermiso(7, true).subscribe();
    const peticion = http.expectOne(`${API}/diseno-nuevo/7`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ permitido: true });
    peticion.flush({ usuarioId: 7, permitido: true, activo: true });
  });

  it('el valor para todo el equipo con {nuevo}', () => {
    servicio.cambiarPorDefecto(false).subscribe();
    const peticion = http.expectOne(`${API}/diseno-nuevo/por-defecto`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nuevo: false });
    peticion.flush({ porDefecto: false });
  });

  it('el propio con {activo}', () => {
    servicio.cambiarMiDiseno(false).subscribe();
    const peticion = http.expectOne(`${API}/mi-diseno`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ activo: false });
    peticion.flush({ activo: false, permitido: true });
  });

  it('los nombres salen de la API de miembros: propietarios y gestores, sin familias', () => {
    servicio.gestionDeLaEscuela().subscribe();
    const propietarios = http.expectOne(
      (r) => r.url === `${environment.apiUrl}/miembros` && r.params.get('rol') === 'PROPIETARIO'
    );
    const gestores = http.expectOne(
      (r) => r.url === `${environment.apiUrl}/miembros` && r.params.get('rol') === 'ADMINISTRADOR'
    );
    expect(propietarios.request.params.get('size')).toBe('100');
    expect(propietarios.request.params.has('incluirFamilias')).toBeFalse();
    propietarios.flush({ contenido: [], pagina: 0, tamano: 100, total: 0, totalPaginas: 0 });
    gestores.flush({ contenido: [], pagina: 0, tamano: 100, total: 0, totalPaginas: 0 });
  });
});
