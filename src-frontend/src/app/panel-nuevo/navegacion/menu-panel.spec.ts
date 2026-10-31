import { Capacidad } from '../../servicios/authentication/permisos-sesion.service';
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
    gestion: caso.roles.includes('ROLE_ADMIN') || caso.roles.includes('ROLE_MANAGER'),
    profesor,
    misGrupos: profesor && caso.capacidades.includes('MIS_GRUPOS'),
    plataforma: caso.plataforma,
  };
}

describe('menu-panel: nada se pierde (decisión 232)', () => {
  it('el propietario de Moiskimdo (ADMIN y PROFESOR) sigue viendo «Mi caja», «Mis grupos» y lo solo del propietario', () => {
    const rutas = rutasDeGestion(contextoDe(CASOS[3]));
    for (const ruta of ['/mi-caja', '/mis-grupos', '/auditoriaSistema', '/configuracion-sistema']) {
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
      '/plataformaErrores',
      '/plataformaLogos',
    ]) {
      expect(rutas).withContext(ruta).not.toContain(ruta);
    }
  });
});

describe('menu-panel: estructura (DESIGN.md, Navigation)', () => {
  for (const misGrupos of [true, false]) {
    it(`solo-profesor con MIS_GRUPOS=${misGrupos}: solo enlaces directos de profesor`, () => {
      const contexto: ContextoMenu = { propietario: false, profesor: true, soloProfesor: true, misGrupos, plataforma: false };
      const secciones = menuVisible(contexto);
      expect(secciones.map((s) => s.etiqueta)).toEqual(misGrupos ? ['Mis grupos', 'Mi caja'] : ['Mi caja']);
      expect(secciones.map((s) => s.ruta)).toEqual(misGrupos ? ['/mis-grupos', '/mi-caja'] : ['/mi-caja']);
      expect(secciones.every((s) => s.entradas.length === 0 && !s.contador)).toBeTrue();
      expect(CUENTA_PANEL.filter((e) => !e.condicion || contexto[e.condicion])).toEqual([]);
    });
  }

  for (const propietario of [true, false]) {
    it(`cuenta mixta con propietario=${propietario}: mantiene ambos enlaces en sus desplegables`, () => {
      const secciones = menuVisible({ propietario, profesor: true, soloProfesor: false, misGrupos: true, plataforma: false });
      expect(secciones.length).toBe(6);
      expect(secciones.find((s) => s.id === 'grupos')?.entradas.some((e) => e.ruta === '/mis-grupos')).toBeTrue();
      expect(secciones.find((s) => s.id === 'tesoreria')?.entradas.some((e) => e.ruta === '/mi-caja')).toBeTrue();
    });
  }

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

  it('la cuenta lleva «Mi perfil»', () => {
    expect(CUENTA_PANEL.map((e) => [e.etiqueta, e.ruta, e.icono, e.condicion])).toEqual([
      ['Mi perfil', '/panel/mi-perfil', 'bi-person', 'gestion'],
    ]);
  });

  for (const caso of CASOS) {
    it(`«Mi perfil» se ve para ${caso.nombre}`, () => {
      expect(rutasDeGestion(contextoDe(caso))).toContain('/panel/mi-perfil');
    });
  }

  it('«Mi perfil» no se ve para solo-profesor ni sin contexto de gestión', () => {
    const soloProfesor: ContextoMenu = { propietario: false, profesor: true, soloProfesor: true, gestion: false, misGrupos: true, plataforma: false };
    expect(rutasDeGestion(soloProfesor)).not.toContain('/panel/mi-perfil');
    expect(rutasDeGestion({ propietario: false, profesor: true, misGrupos: false, plataforma: false })).not.toContain('/panel/mi-perfil');
  });

  it('«Mi perfil» no enciende ninguna entrada de la cabecera', () => {
    expect(seccionActiva('/panel/mi-perfil')).toBeNull();
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
