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
  visible,
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
      expect(CUENTA_PANEL.filter((e) => visible(e, contexto)).map((e) => e.ruta)).toEqual(['/panel/mi-perfil']);
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
      ['Mi perfil', '/panel/mi-perfil', 'bi-person', 'cuenta'],
    ]);
  });

  for (const caso of CASOS) {
    it(`«Mi perfil» se ve para ${caso.nombre}`, () => {
      expect(rutasDeGestion(contextoDe(caso))).toContain('/panel/mi-perfil');
    });
  }

  it('«Mi perfil» se ve para solo-profesor y no sin rol del panel (decisión 419)', () => {
    const soloProfesor: ContextoMenu = { propietario: false, profesor: true, soloProfesor: true, gestion: false, misGrupos: true, plataforma: false };
    expect(rutasDeGestion(soloProfesor)).toContain('/panel/mi-perfil');
    expect(rutasDeGestion({ propietario: false, profesor: false, misGrupos: false, plataforma: false })).not.toContain('/panel/mi-perfil');
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

describe('menu-panel: Sedes (E9-3)', () => {
  const base: ContextoMenu = { propietario: true, gestion: true, profesor: false, misGrupos: false, plataforma: false };

  it('«Sedes» va en Escuela, antes de Configuración, y solo con la capacidad SEDES', () => {
    const escuela = MENU_PANEL.find((s) => s.id === 'escuela')!;
    const rutas = escuela.entradas.map((e) => e.ruta);
    expect(rutas.indexOf('/sedes')).toBeGreaterThan(-1);
    expect(rutas.indexOf('/sedes')).toBeLessThan(rutas.indexOf('/configuracion-sistema'));
    expect(escuela.entradas.find((e) => e.ruta === '/sedes')).toEqual(
      jasmine.objectContaining({ etiqueta: 'Sedes', icono: 'bi-geo-alt', condicion: 'sedes' })
    );
    expect(rutasDeGestion({ ...base, sedes: true })).toContain('/sedes');
    expect(rutasDeGestion({ ...base, sedes: false })).not.toContain('/sedes');
    expect(rutasDeGestion(base)).not.toContain('/sedes');
  });

  it('el gestor con SEDES también la ve, aunque no sea propietario', () => {
    expect(rutasDeGestion({ ...base, propietario: false, sedes: true })).toContain('/sedes');
  });

  it('Material de examen: en Escuela, solo con gestión y nunca para quien solo es profesor', () => {
    const conGestion = { ...base, gestion: true };
    const escuela = menuVisible(conGestion).find((seccion) => seccion.id === 'escuela')!;
    expect(escuela.entradas.find((e) => e.ruta === '/material-examen')).toEqual(
      jasmine.objectContaining({ etiqueta: 'Material de examen', condicion: 'gestion' })
    );
    expect(rutasDeGestion(conGestion)).toContain('/material-examen');
    expect(rutasDeGestion({ ...base, gestion: false })).not.toContain('/material-examen');
    expect(rutasDeGestion({ ...base, soloProfesor: true, profesor: true, misGrupos: true })).not.toContain('/material-examen');
  });

  it('el responsable de sede no ve Material de examen aunque la condición de gestión le deje pasar', () => {
    expect(rutasDeGestion({ ...base, panelSede: true })).not.toContain('/material-examen');
    expect(rutasDeGestion({ ...base, panelSede: true, profesor: true, misGrupos: true })).not.toContain('/material-examen');
  });

  it('/material-examen y sus bloques encienden la entrada Escuela', () => {
    expect(seccionActiva('/material-examen')).toBe('escuela');
    expect(seccionActiva('/material-examen/TAEKWONDO/b01_inicio_a_amarillo')).toBe('escuela');
  });

  it('/sedes enciende la entrada Escuela', () => {
    expect(seccionActiva('/sedes')).toBe('escuela');
  });
});

describe('menu-panel: Festivos (decisión 458)', () => {
  const base: ContextoMenu = { propietario: true, gestion: true, profesor: false, misGrupos: false, plataforma: false };

  it('«Festivos» va en Grupos, tras «Mis grupos» y antes de las altas, sin condición', () => {
    const grupos = MENU_PANEL.find((s) => s.id === 'grupos')!;
    const rutas = grupos.entradas.map((e) => e.ruta);
    expect(rutas.indexOf('/festivos')).toBe(rutas.indexOf('/mis-grupos') + 1);
    expect(rutas.indexOf('/festivos')).toBeLessThan(rutas.indexOf('/gruposCrear'));
    expect(grupos.entradas.find((e) => e.ruta === '/festivos')).toEqual({ etiqueta: 'Festivos', ruta: '/festivos', icono: 'bi-calendar-x' });
  });

  it('la ven la gestión y el responsable de sede; quien solo es profesor, no', () => {
    expect(rutasDeGestion(base)).toContain('/festivos');
    expect(rutasDeGestion({ ...base, propietario: false })).toContain('/festivos');
    expect(rutasDeGestion({ ...base, propietario: false, gestion: false, panelSede: true })).toContain('/festivos');
    expect(rutasDeGestion({ ...base, propietario: false, gestion: false, soloProfesor: true, profesor: true, misGrupos: true })).not.toContain('/festivos');
  });

  it('/festivos enciende la entrada Grupos', () => {
    expect(seccionActiva('/festivos')).toBe('grupos');
  });
});

describe('menu-panel: Miembros (E9-4)', () => {
  const base: ContextoMenu = { propietario: true, gestion: true, profesor: false, misGrupos: false, plataforma: false };

  it('«Miembros» va en Escuela, tras Sedes, y solo con la capacidad MIEMBROS', () => {
    const escuela = MENU_PANEL.find((s) => s.id === 'escuela')!;
    const rutas = escuela.entradas.map((e) => e.ruta);
    expect(rutas.indexOf('/miembros')).toBe(rutas.indexOf('/sedes') + 1);
    expect(escuela.entradas.find((e) => e.ruta === '/miembros')).toEqual(
      jasmine.objectContaining({ etiqueta: 'Miembros', icono: 'bi-person-gear', condicion: 'miembros' })
    );
    expect(rutasDeGestion({ ...base, miembros: true })).toContain('/miembros');
    expect(rutasDeGestion({ ...base, miembros: false })).not.toContain('/miembros');
    expect(rutasDeGestion(base)).not.toContain('/miembros');
  });

  it('/miembros enciende la entrada Escuela', () => {
    expect(seccionActiva('/miembros')).toBe('escuela');
  });
});

describe('menu-panel: Datos de la escuela (pieza 394)', () => {
  const base: ContextoMenu = { propietario: true, gestion: true, profesor: false, misGrupos: false, plataforma: false };

  it('«Datos de la escuela» va en Escuela, tras Miembros y antes de Configuración, solo para el propietario', () => {
    const escuela = MENU_PANEL.find((s) => s.id === 'escuela')!;
    const rutas = escuela.entradas.map((e) => e.ruta);
    expect(rutas.indexOf('/datos-escuela')).toBe(rutas.indexOf('/miembros') + 1);
    expect(rutas.indexOf('/datos-escuela')).toBe(rutas.indexOf('/configuracion-sistema') - 1);
    expect(escuela.entradas.find((e) => e.ruta === '/datos-escuela')).toEqual(
      jasmine.objectContaining({ etiqueta: 'Datos de la escuela', condicion: 'propietario' })
    );
    expect(rutasDeGestion(base)).toContain('/datos-escuela');
    expect(rutasDeGestion({ ...base, propietario: false })).not.toContain('/datos-escuela');
    expect(rutasDeGestion({ ...base, propietario: false, gestion: false, panelSede: true })).not.toContain('/datos-escuela');
    expect(rutasDeGestion({ propietario: false, profesor: true, soloProfesor: true, misGrupos: false, plataforma: false })).not.toContain(
      '/datos-escuela'
    );
  });

  it('/datos-escuela enciende la entrada Escuela', () => {
    expect(seccionActiva('/datos-escuela')).toBe('escuela');
  });
});

describe('menu-panel: Web · Tema (W7)', () => {
  const base: ContextoMenu = { propietario: true, gestion: true, profesor: false, misGrupos: false, plataforma: false };

  it('«Tema» va en el grupo «Web» de Escuela, tras «Ver la web», con la condición webPlantilla', () => {
    const escuela = MENU_PANEL.find((s) => s.id === 'escuela')!;
    const rutas = escuela.entradas.map((e) => e.ruta);
    expect(rutas.indexOf('/web-tema')).toBe(rutas.indexOf('/') + 1);
    expect(escuela.entradas.find((e) => e.ruta === '/web-tema')).toEqual(
      jasmine.objectContaining({ etiqueta: 'Tema', condicion: 'webPlantilla' })
    );
    expect(escuela.prefijos).toContain('/web-tema');
  });

  it('la ve el propietario de una escuela de la plantilla', () => {
    expect(rutasDeGestion({ ...base, legado: false })).toContain('/web-tema');
  });

  it('no la ve el propietario de Moiskimdo, ni el propietario si aún no se sabe si es Moiskimdo', () => {
    expect(rutasDeGestion({ ...base, legado: true })).not.toContain('/web-tema');
    expect(rutasDeGestion(base)).not.toContain('/web-tema');
  });

  it('no la ven el gestor, el responsable de sede ni quien solo es profesor', () => {
    expect(rutasDeGestion({ ...base, propietario: false, legado: false })).not.toContain('/web-tema');
    expect(rutasDeGestion({ ...base, propietario: false, gestion: false, panelSede: true, legado: false })).not.toContain('/web-tema');
    expect(
      rutasDeGestion({ propietario: false, profesor: true, soloProfesor: true, misGrupos: false, plataforma: false, legado: false })
    ).not.toContain('/web-tema');
  });

  it('/web-tema enciende la entrada Escuela', () => {
    expect(seccionActiva('/web-tema')).toBe('escuela');
  });
});
