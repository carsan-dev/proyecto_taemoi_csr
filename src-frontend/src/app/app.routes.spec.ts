import { adminOnlyGuard } from './guards/admin-only.guard';
import { roleGuard } from './guards/role.guard';
import { routes } from './app.routes';

describe('app routes', () => {
  it('protege auditoría y configuración con guard de admin', () => {
    const auditoriaRoute = routes.find((route) => route.path === 'auditoriaSistema');
    const configuracionRoute = routes.find((route) => route.path === 'configuracion-sistema');

    expect(auditoriaRoute?.canActivate).toEqual([roleGuard, adminOnlyGuard]);
    expect(configuracionRoute?.canActivate).toEqual([roleGuard, adminOnlyGuard]);
  });
});
