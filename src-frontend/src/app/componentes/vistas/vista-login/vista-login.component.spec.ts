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
});
