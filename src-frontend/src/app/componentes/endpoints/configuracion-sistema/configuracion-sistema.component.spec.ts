import { ConfiguracionSistemaComponent } from './configuracion-sistema.component';

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
