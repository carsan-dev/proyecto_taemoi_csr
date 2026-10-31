import { PermisosSesion } from '../../servicios/authentication/permisos-sesion.service';

const CASARICHE = { id: 1, nombre: 'Casariche', activa: true };
const ESTEPA = { id: 2, nombre: 'Estepa', activa: true };
const LORA = { id: 3, nombre: 'Lora de Estepa', activa: false };

function permisos(parcial: Partial<PermisosSesion>): PermisosSesion {
  return { roles: ['ADMINISTRADOR'], esAdminPlataforma: false, sedes: [], variasSedes: false, tieneGrupos: false, capacidades: [], ...parcial };
}

/** Gestión con dos sedes activas y una inactiva: el selector ofrece las dos activas. */
export const GESTION_VARIAS_SEDES = permisos({ sedes: [CASARICHE, ESTEPA, LORA], variasSedes: true });

/** Moiskimdo: una sede. */
export const GESTION_UNA_SEDE = permisos({ sedes: [CASARICHE] });

/** Responsable de dos sedes en una escuela con varias: ya va limitado a las suyas. */
export const RESPONSABLE_VARIAS_SEDES = permisos({
  roles: ['RESPONSABLE_SEDE'],
  sedes: [CASARICHE, ESTEPA],
  variasSedes: true,
  capacidades: ['PANEL_SEDE'],
});
