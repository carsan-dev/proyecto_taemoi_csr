/**
 * Modelos de `GET /api/catalogo` y `GET /api/catalogo/publico`.
 * Reflejan exactamente `CatalogoDTO` y `CatalogoPublicoDTO` del backend
 * (docs/multi-escuela/catalogo-frontend.md §1). Los `BigDecimal` llegan como número JSON.
 */

export type AplicaGrado = 'TODOS' | 'MENORES' | 'ADULTOS' | 'NINGUNO';

export interface CatalogoOrganizacion {
  id: number;
  comportamientoLegadoTaemoi: boolean;
}

export interface CatalogoDisciplina {
  codigo: string;
  nombre: string;
  orden: number | null;
  activa: boolean;
  edadAdulto: number | null;
  permiteCompeticion: boolean;
  requiereLicencia: boolean;
  /** Nulo: la disciplina no tiene grados (Pilates, defensa personal). */
  sistemaGradosId: number | null;
  tieneMaterialExamen: boolean;
}

export interface CatalogoGrado {
  codigo: string;
  nombre: string;
  orden: number;
  aplicaA: AplicaGrado;
  /** Mitad inferior (color base). Nulo: sin color definido. */
  color1: string | null;
  /** Mitad superior. Nulo: cinturón de un solo color. */
  color2: string | null;
  requiereExamen: boolean;
}

export interface CatalogoSistemaGrados {
  id: number;
  nombre: string;
  /** Por `orden, id`. */
  grados: CatalogoGrado[];
}

export interface CatalogoCategoria {
  /** Puede ser nulo (las categorías de Taemoi sembradas solo lo tienen si ya lo tenían). No usar como clave. */
  codigo: string | null;
  nombre: string;
  /** Código de la disciplina, o nulo si es general. */
  disciplina: string | null;
  edadMin: number | null;
  edadMax: number | null;
  orden: number | null;
}

export interface CatalogoTarifa {
  codigo: string;
  nombre: string;
  /** Única disciplina que admite la tarifa, o nulo si admite cualquiera. */
  disciplina: string | null;
  importe: number;
  importeAdulto: number | null;
  importeMenor: number | null;
  orden: number | null;
  activa: boolean;
  requiereRolFamiliar: boolean;
  requiereGrupoFamiliar: boolean;
}

export interface Catalogo {
  organizacion: CatalogoOrganizacion;
  disciplinas: CatalogoDisciplina[];
  sistemasGrados: CatalogoSistemaGrados[];
  categorias: CatalogoCategoria[];
  /** Vacía para quien no es gestor (D14). */
  tarifas: CatalogoTarifa[];
}

export interface CatalogoPublicoDisciplina {
  codigo: string;
  nombre: string;
  orden: number | null;
}

export interface CatalogoPublico {
  disciplinas: CatalogoPublicoDisciplina[];
}

export type EstadoCatalogo =
  | { estado: 'vacio' }
  | { estado: 'cargando' }
  | { estado: 'listo'; datos: Catalogo }
  | { estado: 'error'; error: unknown };
