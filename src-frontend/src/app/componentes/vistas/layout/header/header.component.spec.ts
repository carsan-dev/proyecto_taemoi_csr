import { HeaderComponent } from './header.component';
import { NgZone } from '@angular/core';
import { of, Subject } from 'rxjs';

describe('HeaderComponent', () => {
  function createComponent(): HeaderComponent {
    const authServiceMock = {
      usuarioLogueadoCambio: new Subject<boolean>(),
      usernameCambio: new Subject<string | null>(),
      rolesCambio: new Subject<string[]>(),
      comprobarLogueado: () => false,
      getRoles: () => of([]),
      tieneAccesoAdmin: (roles: string[]) => roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER'),
      tieneAccesoUser: (roles: string[]) => roles.includes('ROLE_USER'),
      tieneAccesoDual: (roles: string[]) => roles.includes('ROLE_USER') && (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER')),
    };

    const routerMock = {
      url: '/adminpage',
      events: of(),
    };

    return new HeaderComponent(authServiceMock as any, routerMock as any, new NgZone({}));
  }

  let component: HeaderComponent;

  beforeEach(() => {
    component = createComponent();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('oculta accesos rapidos solo-admin para manager', () => {
    component.isAdminOnly = false;

    const routes = component.visibleAdminQuickLinks.map((item) => item.route);

    expect(routes).not.toContain('/auditoriaSistema');
    expect(routes).not.toContain('/configuracion-sistema');
  });

  it('muestra accesos rapidos solo-admin para admin', () => {
    component.isAdminOnly = true;

    const routes = component.visibleAdminQuickLinks.map((item) => item.route);

    expect(routes).toContain('/auditoriaSistema');
    expect(routes).toContain('/configuracion-sistema');
  });
});
