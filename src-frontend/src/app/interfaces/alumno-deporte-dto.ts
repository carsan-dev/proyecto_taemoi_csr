/**
 * DTO for AlumnoDeporte - represents a student's participation in a specific sport
 * IMPORTANT: Property names must match backend AlumnoDeporteDTO.java
 * All per-sport fields are included here (tarifa, grado, licencia, competidor, etc.)
 */
export interface AlumnoDeporteDTO {
  id: number;
  deporte: string; // TAEKWONDO, KICKBOXING, PILATES, DEFENSA_PERSONAL_FEMENINA

  // Grade data (per-sport)
  grado: string | null; // TipoGrado name (e.g., "BLANCO", "AMARILLO")
  fechaGrado: Date | string | null;
  aptoParaExamen: boolean;

  // Status and dates
  activo: boolean;
  fechaAlta: Date | string;
  fechaAltaInicial: Date | string | null; // For seniority calculation
  fechaBaja: Date | string | null;
  antiguedad: string | null; // e.g., "3 años, 2 meses" - calculated from fechaAltaInicial

  // Tarifa (per-sport)
  tipoTarifa: string | null; // TipoTarifa enum value
  cuantiaTarifa: number | null;
  rolFamiliar: string | null; // For PADRES_HIJOS tarifa
  grupoFamiliar: string | null; // For HERMANOS tarifa

  // Competitor data (per-sport)
  competidor: boolean;
  fechaAltaCompeticion: Date | string | null; // Date when became competitor
  fechaAltaCompetidorInicial: Date | string | null; // Initial competitor registration date (for seniority)
  antiguedadCompetidor: string | null; // e.g., "1 año, 3 meses" - calculated from fechaAltaCompetidorInicial
  categoria: string | null; // Category for competitors (per-sport, only for Taekwondo)
  peso: number | null;
  fechaPeso: Date | string | null;

  // License data (per-sport)
  tieneLicencia: boolean;
  numeroLicencia: number | null;
  fechaLicencia: Date | string | null;
}

/**
 * Alumno completo con todos sus deportes
 */
export interface AlumnoConDeportesDTO {
  // Datos básicos del alumno
  id: number;
  nombre: string;
  apellidos: string;
  nif: string | null;
  fechaNacimiento: Date | string;
  edad: number;
  sexo: string | null;
  email: string | null;
  telefono: string | null;
  telefonoFamiliar: string | null;
  rolFamiliar: string | null;
  grupoFamiliar: string | null;
  numeroExpediente: string | null;
  numeroLicencia: string | null;
  fechaLicencia: Date | string | null;
  peso: number | null;
  altura: number | null;
  talla: string | null;
  activo: boolean;
  fechaAlta: Date | string | null;
  fechaBaja: Date | string | null;
  observaciones: string | null;

  // Datos de fotografía
  fotoAlumno: {
    id: number;
    nombre: string;
    tipo: string;
    datos: string;
  } | null;

  // Datos de tarifa
  tarifa: string | null;
  cuantiaTarifa: number;
  tarifaId: number | null;

  // Datos de categoría
  esCompetidor: boolean;
  categoria: {
    id: number;
    nombre: string;
  } | null;

  // Datos deprecados (single-sport legacy)
  /** @deprecated Use deportes array instead */
  deporte: string | null;
  /** @deprecated Use deportes array instead */
  grado: {
    id: number;
    tipoGrado: string;
  } | null;
  /** @deprecated Use deportes array instead */
  fechaGrado: Date | string | null;
  /** @deprecated Use deportes array instead */
  aptoParaExamen: boolean;

  // Datos multi-deporte (NUEVO)
  deportes: AlumnoDeporteDTO[];

  // Relaciones
  grupos: any[]; // Array de grupos
  turnos: any[]; // Array de turnos
  documentos: any[]; // Array de documentos
}

/**
 * Request para agregar un deporte a un alumno
 */
export interface AgregarDeporteRequest {
  deporte: string;
  gradoInicial: string; // TipoGrado enum value
}

/**
 * Request para actualizar grado de un deporte
 */
export interface ActualizarGradoDeporteRequest {
  nuevoGrado: string; // TipoGrado enum value
}
