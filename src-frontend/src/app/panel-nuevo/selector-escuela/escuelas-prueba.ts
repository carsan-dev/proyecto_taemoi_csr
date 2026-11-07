import { EscuelaUsuario } from './selector-escuela.service';

/** Escuela de la cuenta para los tests del selector (datos ficticios). */
export function escuela(parcial: Partial<EscuelaUsuario> = {}): EscuelaUsuario {
  return {
    id: 1,
    slug: 'escuela-norte',
    nombre: 'Escuela Ficticia Norte',
    roles: ['ROLE_ADMIN'],
    activa: false,
    logo: 'NINGUNO',
    logoVersion: null,
    colorPrimario: '#0B5CAD',
    numeroSedes: 1,
    ...parcial,
  };
}
