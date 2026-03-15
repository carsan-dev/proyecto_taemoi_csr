import { adminOnlyGuard } from './guards/admin-only.guard';
import { roleGuard } from './guards/role.guard';
import { resolverPortalContexto, routes } from './app.routes';

describe('app routes', () => {
  it('declara requiredRoles en todas las rutas protegidas por roleGuard', () => {
    const protectedRoutes = routes.filter((route) => route.canActivate?.includes(roleGuard));

    expect(protectedRoutes.length).toBeGreaterThan(0);
    expect(protectedRoutes.every((route) => Array.isArray(route.data?.['requiredRoles']) && route.data?.['requiredRoles'].length > 0)).toBeTrue();
  });

  it('protege auditoría y configuración con guard de admin', () => {
    const auditoriaRoute = routes.find((route) => route.path === 'auditoriaSistema');
    const configuracionRoute = routes.find((route) => route.path === 'configuracion-sistema');

    expect(auditoriaRoute?.canActivate).toEqual([roleGuard, adminOnlyGuard]);
    expect(configuracionRoute?.canActivate).toEqual([roleGuard, adminOnlyGuard]);
    expect(auditoriaRoute?.data?.['requiredRoles']).toEqual(['ROLE_ADMIN']);
    expect(configuracionRoute?.data?.['requiredRoles']).toEqual(['ROLE_ADMIN']);
  });

  it('mantiene el portal user accesible para cuentas duales y usuarios finales', () => {
    const userRoute = routes.find((route) => route.path === 'userpage');

    expect(userRoute?.data?.['requiredRoles']).toEqual(['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_USER']);
  });

  it('resuelve el contexto visual del portal a partir de metadata de ruta', () => {
    expect(resolverPortalContexto('/userpage/turnos')).toBe('user');
    expect(resolverPortalContexto('/alumnosEditar/10')).toBe('admin');
    expect(resolverPortalContexto('/contacto')).toBe('public');
  });
});
