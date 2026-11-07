import { of, throwError } from 'rxjs';
import Swal from 'sweetalert2';
import { VistaLoginComponent } from './vista-login.component';

describe('VistaLoginComponent', () => {
  it('abre el registro y prellena el correo desde la URL', () => {
    const auth = { comprobarLogueado: () => false };
    const router = { navigate: jasmine.createSpy('navigate') };
    const route = { queryParams: of({modo: 'registro', email: ' Familia@Example.COM '}) };
    const component = new VistaLoginComponent(auth as any, router as any, route as any);

    component.ngOnInit();

    expect(component).toBeTruthy();
    expect(component.mostrarRegistro).toBeTrue();
    expect(component.registro.email).toBe('familia@example.com');
  });

  it('muestra "Demasiados intentos" cuando nginx responde 429 con cuerpo HTML', () => {
    const auth = { comprobarLogueado: () => false };
    const router = { navigate: jasmine.createSpy('navigate') };
    const route = { queryParams: of({}) };
    const component = new VistaLoginComponent(auth as any, router as any, route as any);

    const resultado = (component as any).obtenerErrorLogin({
      status: 429,
      error: '<html><body><h1>429 Too Many Requests</h1></body></html>',
    });

    expect(resultado).toEqual({
      title: 'Demasiados intentos',
      message: 'Espera un minuto y vuelve a intentarlo.',
    });
  });
  describe('destino tras entrar (selector de escuela)', () => {
    const route = { queryParams: of({}) };

    async function esperar(condicion: () => boolean): Promise<void> {
      for (let i = 0; i < 100 && !condicion(); i++) {
        await new Promise((resolver) => setTimeout(resolver, 10));
      }
    }

    function auth(logueado = false) {
      return {
        comprobarLogueado: () => logueado,
        rolesEstanCargados: () => true,
        getRolesActuales: () => ['ROLE_ADMIN'],
        login: () => of(['ROLE_ADMIN']),
        obtenerNombreUsuario: () => of('ana'),
        resolverRutaInicio: jasmine.createSpy('resolverRutaInicio').and.returnValue(of('/adminpage')),
      };
    }

    beforeEach(() => spyOn(Swal, 'fire').and.resolveTo({} as never));

    it('con contraseña, el selector decide si pasa por «Tras entrar»', async () => {
      const router = { navigate: jasmine.createSpy('navigate') };
      const selector = { destinoTrasEntrar: jasmine.createSpy('destinoTrasEntrar').and.returnValue(of('/elegir-escuela')) };
      const injector = { get: () => selector };
      const component = new VistaLoginComponent(auth() as any, router as any, route as any, injector as any);

      component.login();
      await esperar(() => router.navigate.calls.count() > 0);

      expect(selector.destinoTrasEntrar).toHaveBeenCalledOnceWith('/adminpage');
      expect(router.navigate).toHaveBeenCalledOnceWith(['/elegir-escuela']);
    });

    it('si el selector falla, entra en el destino de siempre', async () => {
      const router = { navigate: jasmine.createSpy('navigate') };
      const selector = { destinoTrasEntrar: () => throwError(() => new Error('sin red')) };
      const injector = { get: () => selector };
      const component = new VistaLoginComponent(auth() as any, router as any, route as any, injector as any);

      component.login();
      await esperar(() => router.navigate.calls.count() > 0);

      expect(router.navigate).toHaveBeenCalledOnceWith(['/adminpage']);
    });

    it('volver a /login con la sesión abierta no pasa por «Tras entrar»', async () => {
      const router = { navigate: jasmine.createSpy('navigate') };
      const selector = { destinoTrasEntrar: jasmine.createSpy('destinoTrasEntrar') };
      const injector = { get: () => selector };
      const component = new VistaLoginComponent(auth(true) as any, router as any, route as any, injector as any);

      component.ngOnInit();
      await esperar(() => router.navigate.calls.count() > 0);

      expect(router.navigate).toHaveBeenCalledOnceWith(['/adminpage']);
      expect(selector.destinoTrasEntrar).not.toHaveBeenCalled();
    });
  });
});
