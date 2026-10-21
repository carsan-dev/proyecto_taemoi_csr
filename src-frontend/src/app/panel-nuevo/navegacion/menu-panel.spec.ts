import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { HeaderComponent } from '../../componentes/vistas/layout/header/header.component';
import { AuthenticationService } from '../../servicios/authentication/authentication.service';
import { Capacidad, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { SolicitudesContadorService } from '../../servicios/endpoints/solicitudes-contador.service';
import { PageScrollService } from '../../servicios/generales/page-scroll.service';
import { ScrollLockService } from '../../servicios/generales/scroll-lock.service';
import {
  BARRA_INFERIOR,
  ContextoMenu,
  CUENTA_PANEL,
  ENLACES_LEGALES,
  MENU_PANEL,
  menuVisible,
  rutasDeGestion,
  seccionActiva,
  entradaActual,
} from './menu-panel';

/** Rutas de la web pública que la cabecera de hoy enlaza desde el menú lateral: no son entradas de gestión. */
const RUTAS_PUBLICAS = new Set([
  '/',
  '/eventos',
  '/horarios',
  '/tarifas',
  '/contacto',
  '/taekwondo',
  '/kickboxing',
  '/pilates',
  '/defensa-personal-femenina',
  '/politica-privacidad',
  '/politica-cookies',
  '/aviso-legal',
]);

interface Caso {
  nombre: string;
  roles: string[];
  capacidades: Capacidad[];
  plataforma: boolean;
}

/** Las sesiones de gestión que importan; la cuarta es la del propietario de Moiskimdo (ADMIN y PROFESOR con grupos). */
const CASOS: Caso[] = [
  { nombre: 'gestor', roles: ['ROLE_MANAGER'], capacidades: [], plataforma: false },
  { nombre: 'propietario', roles: ['ROLE_ADMIN'], capacidades: ['DAR_GESTION'], plataforma: false },
  { nombre: 'propietario y profesor sin grupos', roles: ['ROLE_ADMIN', 'ROLE_PROFESOR'], capacidades: ['MI_CAJA'], plataforma: false },
  {
    nombre: 'propietario y profesor con grupos (Moiskimdo)',
    roles: ['ROLE_ADMIN', 'ROLE_PROFESOR', 'ROLE_USER'],
    capacidades: ['DAR_GESTION', 'MIS_GRUPOS', 'MI_CAJA'],
    plataforma: false,
  },
  { nombre: 'gestor y profesor con grupos', roles: ['ROLE_MANAGER', 'ROLE_PROFESOR'], capacidades: ['MIS_GRUPOS'], plataforma: false },
  { nombre: 'propietario y superadmin', roles: ['ROLE_ADMIN'], capacidades: [], plataforma: true },
];

function contextoDe(caso: Caso): ContextoMenu {
  const profesor = caso.roles.includes('ROLE_PROFESOR');
  return {
    propietario: caso.roles.includes('ROLE_ADMIN'),
    profesor,
    misGrupos: profesor && caso.capacidades.includes('MIS_GRUPOS'),
    plataforma: caso.plataforma,
  };
}

/** Pinta la cabecera de HOY con la sesión del caso y saca todas las rutas de su barra, su menú lateral y su cuenta. */
async function rutasDeLaCabeceraDeHoy(caso: Caso): Promise<Set<string>> {
  const auth = {
    usuarioLogueadoCambio: new BehaviorSubject<boolean>(true),
    usernameCambio: new BehaviorSubject<string | null>('cuenta'),
    rolesCambio: new BehaviorSubject<string[]>(caso.roles),
    comprobarLogueado: () => true,
    getRoles: () => of(caso.roles),
    resolverRolesDisponibles: () => of(caso.roles),
    esAdminPlataforma: () => of(caso.plataforma),
    marcarVistaPreferidaSegunRuta: () => undefined,
    tieneAccesoAdmin: (r: string[]) => r.includes('ROLE_ADMIN') || r.includes('ROLE_MANAGER'),
    tieneAccesoUser: (r: string[]) => r.includes('ROLE_USER'),
    tieneAccesoDual: (r: string[]) =>
      r.includes('ROLE_USER') && (r.includes('ROLE_ADMIN') || r.includes('ROLE_MANAGER')),
  };
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [HeaderComponent],
    providers: [
      provideRouter([]),
      { provide: AuthenticationService, useValue: auth },
      { provide: PageScrollService, useValue: { scrollTop: 0 } },
      { provide: ScrollLockService, useValue: { lock: () => () => undefined } },
      { provide: SolicitudesContadorService, useValue: { total: () => 0, refrescar: () => undefined, reiniciar: () => undefined } },
      { provide: PermisosSesionService, useValue: { capacidades: () => of(caso.capacidades), obtener: () => of({}) } },
    ],
  }).compileComponents();
  const fixture = TestBed.createComponent(HeaderComponent);
  fixture.detectChanges();
  fixture.componentInstance.isAdminAccountMenuOpen = true;
  fixture.detectChanges();
  const raiz = fixture.nativeElement as HTMLElement;
  const enlaces = raiz.querySelectorAll<HTMLAnchorElement>(
    '.admin-top-navbar a[href], #admin-sidebar a[href], .admin-account-menu-panel a[href]'
  );
  expect(enlaces.length).withContext(`la cabecera de hoy se pintó para «${caso.nombre}»`).toBeGreaterThan(10);
  const rutas = new Set<string>();
  enlaces.forEach((a) => rutas.add(a.getAttribute('href')!));
  fixture.destroy();
  return rutas;
}

describe('menu-panel: nada se pierde (decisión 232)', () => {
  for (const caso of CASOS) {
    it(`${caso.nombre}: las mismas rutas de gestión que la cabecera de hoy, con las mismas condiciones`, async () => {
      const hoy = [...(await rutasDeLaCabeceraDeHoy(caso))].filter((r) => !RUTAS_PUBLICAS.has(r)).sort();
      const nuevo = rutasDeGestion(contextoDe(caso))
        .filter((r) => !RUTAS_PUBLICAS.has(r))
        .sort();

      expect(nuevo).toEqual(hoy);
    });
  }

  it('el propietario de Moiskimdo (ADMIN y PROFESOR) sigue viendo «Mi caja», «Mis grupos» y lo solo del propietario', () => {
    const rutas = rutasDeGestion(contextoDe(CASOS[3]));
    for (const ruta of ['/mi-caja', '/mis-grupos', '/auditoriaSistema', '/configuracion-sistema', '/panel/diseno-nuevo']) {
      expect(rutas).withContext(ruta).toContain(ruta);
    }
  });

  it('sin contexto (todo falso) no sale nada condicionado', () => {
    const rutas = rutasDeGestion({ propietario: false, profesor: false, misGrupos: false, plataforma: false });
    for (const ruta of [
      '/mi-caja',
      '/mis-grupos',
      '/auditoriaSistema',
      '/configuracion-sistema',
      '/panel/diseno-nuevo',
      '/plataformaErrores',
      '/plataformaLogos',
    ]) {
      expect(rutas).withContext(ruta).not.toContain(ruta);
    }
  });
});

describe('menu-panel: estructura (DESIGN.md, Navigation)', () => {
  const todo: ContextoMenu = { propietario: true, profesor: true, misGrupos: true, plataforma: true };

  it('seis entradas en orden, con icono y nombre; cuatro con desplegable', () => {
    expect(MENU_PANEL.map((s) => s.etiqueta)).toEqual(['Inicio', 'Alumnos', 'Grupos', 'Tesorería', 'Solicitudes', 'Escuela']);
    MENU_PANEL.forEach((s) => expect(s.icono).withContext(s.etiqueta).toMatch(/^bi-/));
    expect(MENU_PANEL.filter((s) => s.entradas.length > 0).map((s) => s.id)).toEqual([
      'alumnos',
      'grupos',
      'tesoreria',
      'escuela',
    ]);
  });

  it('todo está como mucho a dos clics: o la entrada enlaza, o su desplegable lista la ruta', () => {
    for (const seccion of menuVisible(todo)) {
      if (seccion.entradas.length === 0) {
        expect(seccion.ruta).withContext(seccion.id).toBeTruthy();
      } else {
        expect(seccion.ruta).withContext(`${seccion.id}: el desplegable abre, no navega`).toBeUndefined();
        seccion.entradas.forEach((e) => expect(e.ruta).toMatch(/^\//));
      }
    }
  });

  it('Solicitudes lleva el contador', () => {
    expect(MENU_PANEL.find((s) => s.id === 'solicitudes')?.contador).toBe('solicitudes');
  });

  it('la barra inferior lleva Inicio, Alumnos y Tesorería (más «Más»)', () => {
    expect(BARRA_INFERIOR).toEqual(['inicio', 'alumnos', 'tesoreria']);
  });

  it('la cuenta lleva «Diseño nuevo» solo para el propietario', () => {
    const disenoNuevo = CUENTA_PANEL.find((e) => e.ruta === '/panel/diseno-nuevo');
    expect(disenoNuevo?.etiqueta).toBe('Diseño nuevo');
    expect(disenoNuevo?.condicion).toBe('propietario');
  });

  it('los enlaces legales del pie de hoy (decisión 259)', () => {
    expect(ENLACES_LEGALES.map((e) => e.ruta)).toEqual(['/politica-privacidad', '/politica-cookies', '/aviso-legal']);
  });

  it('seccionActiva por prefijo de ruta', () => {
    expect(seccionActiva('/adminpage')).toBe('inicio');
    expect(seccionActiva('/alumnosEditar/7')).toBe('alumnos');
    expect(seccionActiva('/gestionarAlumnos/3')).toBe('grupos');
    expect(seccionActiva('/mi-caja')).toBe('tesoreria');
    expect(seccionActiva('/solicitudes')).toBe('solicitudes');
    expect(seccionActiva('/configuracion-sistema')).toBe('escuela');
    expect(seccionActiva('/mis-grupos/4/alumnos')).toBe('grupos');
    expect(seccionActiva('/alumnosListar?pagina=2')).toBe('alumnos');
    expect(seccionActiva('/userpage')).toBeNull();
  });

  it('entradaActual: la ruta exacta o una hija, nunca un prefijo de texto', () => {
    expect(entradaActual('/alumnosListar', '/alumnosListar')).toBeTrue();
    expect(entradaActual('/mis-grupos', '/mis-grupos/4')).toBeTrue();
    expect(entradaActual('/alumnos', '/alumnosListar')).toBeFalse();
    expect(entradaActual('/eventosListar', '/eventosListar?x=1')).toBeTrue();
  });
});
