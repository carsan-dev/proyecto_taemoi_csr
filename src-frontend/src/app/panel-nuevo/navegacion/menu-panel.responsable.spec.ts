import { routes } from '../../app.routes';
import { ContextoMenu, menuVisible, rutasDeGestion, visible, CUENTA_PANEL } from './menu-panel';

/** Lo que la decisión 370 da al responsable, con «Mi perfil» de la cuenta. */
const RUTAS_370 = [
  '/adminpage',
  '/alumnosListar',
  '/preinscripciones',
  '/convocatoriasListar',
  '/alumnosCrear',
  '/gruposListar',
  '/gruposCrear',
  '/turnosCrear',
  '/tesoreriaCobros',
  '/cajaSede',
  '/solicitudes',
  '/panel/mi-perfil',
];

const NO_VE = [
  '/alumnosEliminar',
  '/eventosListar',
  '/eventosCrear',
  '/productosListar',
  '/productosCrear',
  '/sedes',
  '/miembros',
  '/configuracion-sistema',
  '/auditoriaSistema',
];

const BASE: ContextoMenu = { propietario: false, profesor: false, misGrupos: false, plataforma: false, panelSede: true };

describe('menu-panel: responsable de sede (decisión 370)', () => {
  it('R solo ve lo de la 370 y nada más', () => {
    expect(rutasDeGestion(BASE).sort()).toEqual([...RUTAS_370].sort());
  });

  it('R + Pr suma Mi caja y Mis grupos, sin la vista de solo profesor (371)', () => {
    const contexto: ContextoMenu = { ...BASE, profesor: true, misGrupos: true, soloProfesor: false };
    expect(rutasDeGestion(contexto).sort()).toEqual([...RUTAS_370, '/mi-caja', '/mis-grupos'].sort());
    expect(menuVisible(contexto).map((s) => s.id)).toEqual(['inicio', 'alumnos', 'grupos', 'tesoreria', 'solicitudes']);
  });

  it('ninguna entrada lleva a lo que no ve, y Productos o Eventos no dejan título vacío', () => {
    const rutas = rutasDeGestion(BASE);
    NO_VE.forEach((ruta) => expect(rutas).withContext(ruta).not.toContain(ruta));
    expect(menuVisible(BASE).map((s) => s.id)).toEqual(['inicio', 'alumnos', 'grupos', 'tesoreria', 'solicitudes']);
    expect(menuVisible(BASE).find((s) => s.id === 'tesoreria')?.entradas.map((e) => e.grupo ?? null)).toEqual([null, null]);
  });

  it('un responsable que además es de plataforma conserva sus entradas de plataforma', () => {
    const rutas = rutasDeGestion({ ...BASE, plataforma: true });
    expect(rutas).toContain('/plataformaErrores');
    expect(rutas).toContain('/plataformaLogos');
  });

  it('«Mi perfil» sale con gestión y con responsable; no con solo profesor', () => {
    const perfil = CUENTA_PANEL[0];
    expect(visible(perfil, BASE)).toBeTrue();
    expect(visible(perfil, { ...BASE, panelSede: false, gestion: true })).toBeTrue();
    expect(visible(perfil, { ...BASE, panelSede: false })).toBeFalse();
  });

  it('toda ruta del menú de R, salvo Mi caja y Mis grupos, deja pasar al responsable', () => {
    const contexto: ContextoMenu = { ...BASE, profesor: true, misGrupos: true };
    for (const ruta of rutasDeGestion(contexto).filter((r) => r !== '/mi-caja' && r !== '/mis-grupos')) {
      const definicion = routes.find((r) => `/${r.path}` === ruta);
      expect((definicion?.data?.['requiredRoles'] as string[] | undefined) ?? []).withContext(ruta).toContain('ROLE_RESPONSABLE_SEDE');
    }
  });

  it('las pantallas que R no ve siguen cerradas, también sus subrutas', () => {
    const cerradas = routes.filter((r) =>
      /^(alumnosEliminar|eventos(Listar|Crear|Editar)|productos(Listar|Crear|Editar)|sedes|miembros|configuracion-sistema|auditoriaSistema)/.test(
        r.path ?? ''
      )
    );
    expect(cerradas.length).toBe(11);
    cerradas.forEach((r) =>
      expect((r.data?.['requiredRoles'] as string[]) ?? []).withContext(String(r.path)).not.toContain('ROLE_RESPONSABLE_SEDE')
    );
  });

  it('las subrutas de las pantallas de la 370 dejan pasar a R', () => {
    for (const path of [
      'alumnosEditar/:id',
      'alumnos/:id/productos',
      'gruposListar/:grupoId/lista',
      'gruposEditar/:id',
      'gestionarAlumnos/:id',
      'seleccionarAlumnos/:id',
      'gestionarTurnosAlumno/:alumnoId',
      'turnosEditar/:id',
    ]) {
      const definicion = routes.find((r) => r.path === path);
      expect(definicion?.data?.['requiredRoles'] as string[]).withContext(path).toContain('ROLE_RESPONSABLE_SEDE');
    }
  });
});
