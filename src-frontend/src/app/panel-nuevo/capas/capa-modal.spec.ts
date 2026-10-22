import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { disenoNuevoGuard } from '../diseno-nuevo.guard';
import { EstilosPanelService } from '../estilos-panel.service';
import { CLASE_ERROR_HTTP, cerrarSweetAlertAbierto } from './capa-modal';

@Component({ standalone: true, template: '<p>Pantalla nueva</p>' })
class PantallaNueva {}

@Component({ standalone: true, template: '<p>Pantalla de hoy</p>' })
class PantallaDeHoy {}

/**
 * `cerrarSweetAlertAbierto` (8b-D1, ronda 2): el aviso o el diálogo del panel nuevo que responden a un error cierran el
 * SweetAlert2 del interceptor de errores, y SOLO ese (con la clase `swal-error-http`) y SOLO en una pantalla nueva (la
 * entrada con `disenoNuevoGuard`). Una respuesta tardía que llega en una pantalla de hoy no cierra su SweetAlert2, ni el
 * de «Sesión expirada» (que lleva a /login), ni una confirmación.
 */
describe('cerrarSweetAlertAbierto (8b-D1, solo el SweetAlert2 del error HTTP y solo en el panel nuevo)', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'alumnosListar', canMatch: [disenoNuevoGuard], component: PantallaNueva },
          { path: 'alumnosListar', component: PantallaDeHoy },
          { path: 'productos', component: PantallaDeHoy },
        ]),
        {
          provide: PermisosSesionService,
          useValue: { obtener: () => of({ disenoNuevo: { activo: true, permitido: true } }) },
        },
        { provide: EstilosPanelService, useValue: { cargar: () => Promise.resolve() } },
      ],
    });
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    Swal.close();
    document.querySelectorAll('.swal2-container').forEach((e) => e.remove());
  });

  async function ir(url: string): Promise<void> {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
  }

  function abrirSwal(clase?: string): void {
    void Swal.fire({
      title: 'Error',
      text: 'No se pudo guardar',
      showClass: { popup: '' },
      hideClass: { popup: '' },
      ...(clase ? { customClass: { popup: clase } } : {}),
    });
  }

  it('la clase del interceptor es «swal-error-http»', () => {
    expect(CLASE_ERROR_HTTP).toBe('swal-error-http');
  });

  it('en una pantalla nueva cierra el SweetAlert2 del error HTTP', async () => {
    await ir('/alumnosListar');
    expect(router.url).toBe('/alumnosListar');
    abrirSwal(CLASE_ERROR_HTTP);
    expect(Swal.isVisible()).toBeTrue();
    cerrarSweetAlertAbierto(router);
    expect(Swal.isVisible()).toBeFalse();
  });

  it('en una pantalla nueva NO cierra otro SweetAlert2 (una confirmación, «Sesión expirada»…)', async () => {
    await ir('/alumnosListar');
    abrirSwal();
    cerrarSweetAlertAbierto(router);
    expect(Swal.isVisible()).toBeTrue();
    abrirSwal('swal-sesion-expirada');
    cerrarSweetAlertAbierto(router);
    expect(Swal.isVisible()).toBeTrue();
  });

  it('en una pantalla de hoy NO cierra nada, ni siquiera el del error HTTP (respuesta tardía tras cambiar de pantalla)', async () => {
    await ir('/productos');
    abrirSwal(CLASE_ERROR_HTTP);
    cerrarSweetAlertAbierto(router);
    expect(Swal.isVisible()).toBeTrue();
  });

  it('sin SweetAlert2 abierto no hace nada', async () => {
    await ir('/alumnosListar');
    const cerrar = spyOn(Swal, 'close');
    cerrarSweetAlertAbierto(router);
    expect(cerrar).not.toHaveBeenCalled();
  });
});
