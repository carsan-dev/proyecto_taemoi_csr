/**
 * Menú del panel nuevo (8b-N, fase3-e8b-plan.md §8; DESIGN.md, Navigation): las entradas de gestión de la cabecera de
 * hoy, agrupadas en seis. Ninguna se pierde: `menu-panel.spec.ts` compara, sesión a sesión, estas rutas con las que
 * pinta la cabecera de hoy (barra de accesos, menú lateral y cuenta), con las mismas condiciones de rol y capacidad.
 * Todo queda como mucho a dos clics: la entrada (o «Más» en el móvil) y la opción.
 */

export type IdSeccion = 'inicio' | 'alumnos' | 'grupos' | 'tesoreria' | 'solicitudes' | 'escuela';

/**
 * Condiciones de hoy, una a una:
 * - `propietario`: `isAdminOnly` de la cabecera de hoy (ROLE_ADMIN; Registro de acciones, Configuración).
 * - `profesor`: `tieneRolProfesor` (ROLE_PROFESOR; Mi caja).
 * - `misGrupos`: `veMisGrupos` (ROLE_PROFESOR y la capacidad MIS_GRUPOS de `GET /api/auth/permisos`).
 * - `plataforma`: `esAdminPlataforma` (la sesión administradora de plataforma).
 * - `gestion`: ROLE_ADMIN o ROLE_MANAGER (`/api/panel/**`); falla cerrado si el contexto no lo trae.
 */
export type Condicion = 'propietario' | 'profesor' | 'misGrupos' | 'plataforma' | 'gestion';

export type ContextoMenu = Readonly<
  Record<Exclude<Condicion, 'gestion'>, boolean> & { soloProfesor?: boolean; gestion?: boolean }
>;

export interface EntradaMenu {
  etiqueta: string;
  ruta: string;
  icono: string;
  condicion?: Condicion;
  /** Empieza un grupo con nombre dentro del desplegable (separado de lo anterior). */
  grupo?: string;
  /** Enlace a la portada pública: navega con `state.verWeb` para que la portada no vuelva al panel. */
  verWeb?: boolean;
}

export interface SeccionMenu {
  id: IdSeccion;
  etiqueta: string;
  icono: string;
  /** Solo las entradas sin desplegable: navegan con un clic. */
  ruta?: string;
  /** Las del desplegable; vacío en las que navegan directamente. */
  entradas: readonly EntradaMenu[];
  /** Rutas (y sus hijas) en las que la entrada es la actual. */
  prefijos: readonly string[];
  contador?: 'solicitudes';
}

export const MENU_PANEL: readonly SeccionMenu[] = [
  {
    id: 'inicio',
    etiqueta: 'Inicio',
    icono: 'bi-house',
    ruta: '/adminpage',
    entradas: [],
    prefijos: ['/adminpage'],
  },
  {
    id: 'alumnos',
    etiqueta: 'Alumnos',
    icono: 'bi-people',
    entradas: [
      { etiqueta: 'Listado de alumnos', ruta: '/alumnosListar', icono: 'bi-list-ul' },
      { etiqueta: 'Preinscripciones', ruta: '/preinscripciones', icono: 'bi-clipboard2-check' },
      { etiqueta: 'Convocatorias', ruta: '/convocatoriasListar', icono: 'bi-clipboard-check' },
      { etiqueta: 'Crear alumno', ruta: '/alumnosCrear', icono: 'bi-person-plus', grupo: 'Fichas' },
      { etiqueta: 'Eliminar alumno', ruta: '/alumnosEliminar', icono: 'bi-trash' },
    ],
    prefijos: [
      '/alumnosListar',
      '/alumnosEditar',
      '/alumnosCrear',
      '/alumnosEliminar',
      '/alumnos',
      '/preinscripciones',
      '/convocatoriasListar',
    ],
  },
  {
    id: 'grupos',
    etiqueta: 'Grupos',
    icono: 'bi-collection',
    entradas: [
      { etiqueta: 'Grupos y turnos', ruta: '/gruposListar', icono: 'bi-list-ul' },
      { etiqueta: 'Mis grupos', ruta: '/mis-grupos', icono: 'bi-person-badge', condicion: 'misGrupos' },
      { etiqueta: 'Crear grupo', ruta: '/gruposCrear', icono: 'bi-plus-square', grupo: 'Altas' },
      { etiqueta: 'Crear turno', ruta: '/turnosCrear', icono: 'bi-calendar-plus' },
    ],
    prefijos: [
      '/gruposListar',
      '/gruposCrear',
      '/gruposEditar',
      '/gestionarAlumnos',
      '/seleccionarAlumnos',
      '/gestionarTurnosAlumno',
      '/turnosCrear',
      '/turnosEditar',
      '/mis-grupos',
    ],
  },
  {
    id: 'tesoreria',
    etiqueta: 'Tesorería',
    icono: 'bi-cash-stack',
    entradas: [
      { etiqueta: 'Cobros', ruta: '/tesoreriaCobros', icono: 'bi-cash-stack' },
      { etiqueta: 'Caja de sede', ruta: '/cajaSede', icono: 'bi-safe2' },
      { etiqueta: 'Mi caja', ruta: '/mi-caja', icono: 'bi-wallet2', condicion: 'profesor' },
      { etiqueta: 'Productos', ruta: '/productosListar', icono: 'bi-box-seam', grupo: 'Catálogo' },
      { etiqueta: 'Crear producto', ruta: '/productosCrear', icono: 'bi-plus-square' },
    ],
    prefijos: ['/tesoreriaCobros', '/cajaSede', '/mi-caja', '/productosListar', '/productosCrear', '/productosEditar'],
  },
  {
    id: 'solicitudes',
    etiqueta: 'Solicitudes',
    icono: 'bi-inbox',
    ruta: '/solicitudes',
    entradas: [],
    prefijos: ['/solicitudes'],
    contador: 'solicitudes',
  },
  {
    id: 'escuela',
    etiqueta: 'Escuela',
    icono: 'bi-building',
    entradas: [
      { etiqueta: 'Eventos', ruta: '/eventosListar', icono: 'bi-calendar-check' },
      { etiqueta: 'Crear evento', ruta: '/eventosCrear', icono: 'bi-calendar-plus' },
      { etiqueta: 'Configuración', ruta: '/configuracion-sistema', icono: 'bi-sliders', condicion: 'propietario' },
      { etiqueta: 'Registro de acciones', ruta: '/auditoriaSistema', icono: 'bi-journal-text', condicion: 'propietario' },
      { etiqueta: 'Ver la web', ruta: '/', icono: 'bi-globe2', grupo: 'Web', verWeb: true },
      {
        etiqueta: 'Errores de plataforma',
        ruta: '/plataformaErrores',
        icono: 'bi-exclamation-octagon',
        condicion: 'plataforma',
        grupo: 'Plataforma',
      },
      { etiqueta: 'Logo y color', ruta: '/plataformaLogos', icono: 'bi-palette', condicion: 'plataforma' },
    ],
    prefijos: [
      '/eventosListar',
      '/eventosCrear',
      '/eventosEditar',
      '/configuracion-sistema',
      '/auditoriaSistema',
      '/plataformaErrores',
      '/plataformaLogos',
    ],
  },
];

/** La barra inferior del móvil (por debajo de 1024 px), además de «Más». */
export const BARRA_INFERIOR: readonly IdSeccion[] = ['inicio', 'alumnos', 'tesoreria'];

/** Menú de cuenta: «Mi perfil» para propietario y gestor (una sesión solo-profesor no usa `/api/panel/**`). */
export const CUENTA_PANEL: readonly EntradaMenu[] = [
  { etiqueta: 'Mi perfil', ruta: '/panel/mi-perfil', icono: 'bi-person', condicion: 'gestion' },
];

/** Los enlaces legales del pie público (decisión 259): en «Más» y en la cuenta, junto a «Configurar cookies». */
export const ENLACES_LEGALES: readonly EntradaMenu[] = [
  { etiqueta: 'Política de privacidad', ruta: '/politica-privacidad', icono: 'bi-shield-lock' },
  { etiqueta: 'Política de cookies', ruta: '/politica-cookies', icono: 'bi-cookie' },
  { etiqueta: 'Aviso legal', ruta: '/aviso-legal', icono: 'bi-file-earmark-text' },
];

export function visible(entrada: EntradaMenu, contexto: ContextoMenu): boolean {
  return !entrada.condicion || contexto[entrada.condicion] === true;
}

/** Las entradas visibles: dos accesos directos para solo-profesor o las seis secciones de gestión. */
export function menuVisible(contexto: ContextoMenu): SeccionMenu[] {
  // Decisión 326 (R6): el profesor sin gestión navega directamente, sin menús administrativos.
  if (contexto.soloProfesor) {
    return [
      ...(contexto.misGrupos ? [{
        id: 'grupos' as const, etiqueta: 'Mis grupos', icono: 'bi-people',
        ruta: '/mis-grupos', entradas: [], prefijos: ['/mis-grupos'],
      }] : []),
      {
        id: 'tesoreria', etiqueta: 'Mi caja', icono: 'bi-wallet2',
        ruta: '/mi-caja', entradas: [], prefijos: ['/mi-caja'],
      },
    ];
  }
  return MENU_PANEL.map((seccion) => ({
    ...seccion,
    entradas: seccion.entradas.filter((e) => visible(e, contexto)),
  }));
}

/** Todas las rutas a las que llega la sesión desde la navegación nueva (entradas, opciones y cuenta). */
export function rutasDeGestion(contexto: ContextoMenu): string[] {
  const rutas = new Set<string>();
  for (const seccion of menuVisible(contexto)) {
    if (seccion.ruta) {
      rutas.add(seccion.ruta);
    }
    seccion.entradas.forEach((e) => rutas.add(e.ruta));
  }
  CUENTA_PANEL.filter((e) => visible(e, contexto)).forEach((e) => rutas.add(e.ruta));
  return [...rutas];
}

function sinConsulta(url: string): string {
  return (url || '').split('?')[0].split('#')[0];
}

/** La ruta del enlace es la actual: la misma o una hija (`/mis-grupos/4`). */
export function entradaActual(ruta: string, url: string): boolean {
  const actual = sinConsulta(url);
  return actual === ruta || actual.startsWith(`${ruta}/`);
}

/** La entrada de la cabecera que contiene la ruta actual, o null fuera de la gestión. */
export function seccionActiva(url: string): IdSeccion | null {
  const seccion = MENU_PANEL.find((s) => s.prefijos.some((p) => entradaActual(p, url)));
  return seccion?.id ?? null;
}
