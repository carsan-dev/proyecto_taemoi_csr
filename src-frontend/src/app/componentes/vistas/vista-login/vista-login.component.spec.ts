import { of } from 'rxjs';
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
});
