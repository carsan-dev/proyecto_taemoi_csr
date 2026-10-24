import { throwError, of } from 'rxjs';
import Swal from 'sweetalert2';
import { ConfiguracionSistemaComponent } from './configuracion-sistema.component';
import { itSinReloj } from '../../../testing/reloj-prueba';

describe('ConfiguracionSistemaComponent roles', () => {
  const component = new ConfiguracionSistemaComponent({} as any);

  it('muestra nombres de los roles nuevos', () => {
    expect(component.getRoleDisplayName('ROLE_RESPONSABLE_SEDE')).toBe('Responsable de sede');
    expect(component.getRoleDisplayName('ROLE_PROFESOR')).toBe('Profesor');
  });

  it('usa clase neutra existente para los roles nuevos', () => {
    expect(component.getRoleBadgeClass('ROLE_RESPONSABLE_SEDE')).toBe('badge-user');
    expect(component.getRoleBadgeClass('ROLE_PROFESOR')).toBe('badge-user');
  });

  it('mantiene los roles existentes', () => {
    expect(component.getRoleDisplayName('ROLE_ADMIN')).toBe('Administrador');
    expect(component.getRoleBadgeClass('ROLE_MANAGER')).toBe('badge-manager');
  });
});

describe('ConfiguracionSistemaComponent guardar roles', () => {
  const usuario: any = { id: 7, nombre: 'Ana', apellidos: 'Pérez', rol: '[ROLE_ADMIN, ROLE_USER]' };

  function preparar(respuesta: any) {
    const servicio: any = {
      actualizarRolesUsuario: jasmine.createSpy().and.returnValue(respuesta),
      obtenerUsuarios: jasmine.createSpy().and.returnValue(of([])),
    };
    const component = new ConfiguracionSistemaComponent(servicio);
    component.rolesSeleccionados.set(7, component.parseRoles(usuario.rol));
    component.rolesSeleccionados.get(7)!.ROLE_ADMIN = false;
    spyOn(Swal, 'fire').and.callFake(((o: any) =>
      Promise.resolve(o?.showCancelButton ? { isConfirmed: true } : {})) as any);
    return component;
  }

  const avisos = () =>
    (Swal.fire as jasmine.Spy).calls.allArgs().map((a) => JSON.stringify(a)).join('|');

  const espera = () => new Promise((r) => setTimeout(r, 50));

  itSinReloj('409 con mensaje: muestra el mensaje y restaura las casillas', async () => {
    const c = preparar(
      throwError(() => ({ status: 409, error: { mensaje: 'La escuela se quedaría sin propietario.' } }))
    );
    c.guardarCambios(usuario);
    await espera();
    expect(avisos()).toContain('La escuela se quedaría sin propietario.');
    expect(c.rolesSeleccionados.get(7)!.ROLE_ADMIN).toBeTrue();
  });

  itSinReloj('error sin mensaje: aviso genérico y casillas restauradas', async () => {
    const c = preparar(throwError(() => ({ status: 500 })));
    c.guardarCambios(usuario);
    await espera();
    expect(avisos()).toContain('No se pudieron actualizar los roles del usuario.');
    expect(c.rolesSeleccionados.get(7)!.ROLE_ADMIN).toBeTrue();
  });

  itSinReloj('éxito: recarga usuarios sin restaurar', async () => {
    const c = preparar(of({}));
    c.guardarCambios(usuario);
    await espera();
    expect(c.rolesSeleccionados.get(7)!.ROLE_ADMIN).toBeFalse();
  });
});
