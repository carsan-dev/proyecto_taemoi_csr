import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { numero } from '../formato/formato-es';

/** «AAAA-MM-DD» como día local (sin desplazarlo por la zona horaria); nulo si no es una fecha válida. */
export function aFecha(valor: string | null | undefined): Date | null {
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor ?? '');
  if (!iso) {
    return null;
  }
  const fecha = new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3]));
  return Number.isNaN(fecha.getTime()) ? null : fecha;
}

/**
 * Lógica pura del alta de alumno del panel nuevo (8b-3l, P1 de la decisión 285): mensajes de error de cada campo (los de
 * hoy, más los añadidos), edad y categoría junto a la fecha de nacimiento, la validación «fecha de alta ≥ fecha de
 * nacimiento» y el cuerpo del `POST /api/alumnos/crear`, el MISMO que monta `CrearAlumnoComponent.onSubmit` (más la
 * sede cuando la escuela tiene varias).
 */

/** Campos de datos personales con mensaje propio. */
export type CampoPersona =
  | 'nombre'
  | 'apellidos'
  | 'fechaNacimiento'
  | 'nif'
  | 'sede'
  | 'direccion'
  | 'telefono'
  | 'telefono2'
  | 'email'
  | 'responsableLegalNombre'
  | 'responsableLegalNif';

/** Campos de cada deporte con mensaje propio. */
export type CampoDeporte = 'deporte' | 'grado' | 'fechaGrado' | 'fechaAlta' | 'tipoTarifa' | 'rolFamiliar' | 'grupoFamiliar';

/** Error del validador «fecha de alta ≥ fecha de nacimiento» (P-2). */
export const ERROR_ALTA_ANTERIOR = 'altaAnteriorANacimiento';

export const MENSAJE_ALTA_ANTERIOR = 'La fecha de alta no puede ser anterior a la fecha de nacimiento.';

/** El máximo de deportes del alta, como hoy. */
export const MAX_DEPORTES = 4;

/** Etiqueta de cada campo en el resumen de errores. */
export const ETIQUETAS_PERSONA: Record<CampoPersona, string> = {
  nombre: 'Nombre',
  apellidos: 'Apellidos',
  fechaNacimiento: 'Fecha de nacimiento',
  nif: 'NIF / DNI',
  sede: 'Sede',
  direccion: 'Dirección',
  telefono: 'Teléfono principal',
  telefono2: 'Teléfono secundario',
  email: 'Email',
  responsableLegalNombre: 'Nombre del responsable',
  responsableLegalNif: 'DNI/NIE del responsable',
};

export const ETIQUETAS_DEPORTE: Record<CampoDeporte, string> = {
  deporte: 'Deporte',
  grado: 'Grado inicial',
  fechaGrado: 'Fecha de grado',
  fechaAlta: 'Fecha de alta en el deporte',
  tipoTarifa: 'Tipo de tarifa',
  rolFamiliar: 'Rol familiar',
  grupoFamiliar: 'Grupo familiar',
};

export const ORDEN_PERSONA: readonly CampoPersona[] = [
  'nombre',
  'apellidos',
  'fechaNacimiento',
  'nif',
  'sede',
  'direccion',
  'telefono',
  'telefono2',
  'email',
  'responsableLegalNombre',
  'responsableLegalNif',
];

export const ORDEN_DEPORTE: readonly CampoDeporte[] = [
  'deporte',
  'grado',
  'fechaGrado',
  'fechaAlta',
  'tipoTarifa',
  'rolFamiliar',
  'grupoFamiliar',
];

/** Los mensajes de hoy (`crear-alumno.component.html`) y los añadidos (grado, sede, fechas, tarifa, familia). */
export function mensajesPersona(campo: CampoPersona, errores: ValidationErrors | null | undefined): string[] {
  if (!errores) {
    return [];
  }
  const m: string[] = [];
  switch (campo) {
    case 'nombre':
      if (errores['required']) m.push('El nombre del alumno es obligatorio');
      break;
    case 'apellidos':
      if (errores['required']) m.push('Los apellidos del alumno son obligatorios');
      break;
    case 'fechaNacimiento':
      if (errores['required']) m.push('La fecha de nacimiento es obligatoria');
      break;
    case 'nif':
      if (errores['pattern']) m.push('Formato inválido (8 números y una letra)');
      break;
    case 'sede':
      if (errores['required']) m.push('Indica la sede.');
      break;
    case 'direccion':
      if (errores['required']) m.push('La dirección es obligatoria');
      break;
    case 'telefono':
      if (errores['required']) m.push('El teléfono es obligatorio');
      if (errores['pattern']) m.push('Solo números permitidos');
      if (errores['maxlength']) m.push('Máximo 9 caracteres');
      break;
    case 'telefono2':
      if (errores['pattern']) m.push('Solo números permitidos');
      if (errores['maxlength']) m.push('Máximo 9 caracteres');
      break;
    case 'email':
      if (errores['required']) m.push('El email es obligatorio');
      if (errores['email']) m.push('Formato de email inválido');
      break;
    case 'responsableLegalNombre':
      if (errores['maxlength']) m.push('Máximo 180 caracteres');
      break;
    case 'responsableLegalNif':
      if (errores['pattern'] || errores['maxlength']) m.push('Introduce un DNI/NIE válido.');
      break;
  }
  if (errores['servidor']) {
    m.push(String(errores['servidor']));
  }
  return m;
}

export function mensajesDeporte(campo: CampoDeporte, errores: ValidationErrors | null | undefined): string[] {
  if (!errores) {
    return [];
  }
  const m: string[] = [];
  switch (campo) {
    case 'deporte':
      if (errores['required']) m.push('Selecciona el deporte');
      break;
    case 'grado':
      if (errores['required']) m.push('Selecciona el grado');
      break;
    case 'fechaGrado':
      if (errores['required']) m.push('La fecha de grado es obligatoria');
      break;
    case 'fechaAlta':
      if (errores['required']) m.push('La fecha de alta es obligatoria');
      if (errores[ERROR_ALTA_ANTERIOR]) m.push(MENSAJE_ALTA_ANTERIOR);
      break;
    case 'tipoTarifa':
      if (errores['required']) m.push('Selecciona una tarifa');
      break;
    case 'rolFamiliar':
      if (errores['required']) m.push('Selecciona un rol');
      break;
    case 'grupoFamiliar':
      if (errores['required']) m.push('Indica el grupo familiar');
      break;
  }
  if (errores['servidor']) {
    m.push(String(errores['servidor']));
  }
  return m;
}

/** «AAAA-MM-DD» de hoy en hora local (el `toISOString` de hoy daba el día anterior pasada la medianoche UTC). */
export function fechaHoy(hoy: Date = new Date()): string {
  const mm = String(hoy.getMonth() + 1).padStart(2, '0');
  const dd = String(hoy.getDate()).padStart(2, '0');
  return `${hoy.getFullYear()}-${mm}-${dd}`;
}

/** Años cumplidos a `hoy`; nulo sin fecha válida o futura. */
export function edadEnAnios(fechaNacimiento: string | null | undefined, hoy: Date = new Date()): number | null {
  const nacimiento = fechaNacimiento ? aFecha(fechaNacimiento) : null;
  if (!nacimiento || nacimiento.getTime() > hoy.getTime()) {
    return null;
  }
  let edad = hoy.getFullYear() - nacimiento.getFullYear();
  const mes = hoy.getMonth() - nacimiento.getMonth();
  if (mes < 0 || (mes === 0 && hoy.getDate() < nacimiento.getDate())) {
    edad--;
  }
  return edad;
}

/** «9 años · categoría infantil» (sin categoría, solo la edad). Vacío sin fecha válida. */
export function textoEdad(edad: number | null, categoria: string | null | undefined): string {
  if (edad === null) {
    return '';
  }
  const anios = `${numero(edad)} ${edad === 1 ? 'año' : 'años'}`;
  const cat = (categoria ?? '').trim();
  return cat ? `${anios} · categoría ${cat.toLocaleLowerCase('es-ES')}` : anios;
}

/** Menor de 18: el bloque del responsable legal lo recomienda (sigue opcional). */
export function esMenorDeEdad(edad: number | null): boolean {
  return edad !== null && edad < 18;
}

/**
 * P-2 (decisión 285): «fecha de alta ≥ fecha de nacimiento», sobre el control de la fecha de alta de un deporte. Lee la
 * fecha de nacimiento del formulario raíz; sin alguna de las dos, no valida (de eso se ocupa `required`). La ficha de hoy
 * define el mismo criterio (`fechaNacimientoPosteriorAFechaAltaValidator`), aunque no lo engancha.
 */
export function fechaAltaNoAnteriorANacimiento(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const alta = aFecha(control.value || null);
    const nacimiento = aFecha(control.root?.get('fechaNacimiento')?.value || null);
    if (!alta || !nacimiento) {
      return null;
    }
    return alta.getTime() < nacimiento.getTime() ? { [ERROR_ALTA_ANTERIOR]: true } : null;
  };
}

/** «Falta 1 dato» / «Faltan 3 datos». */
export function tituloFaltan(n: number): string {
  return n === 1 ? 'Falta 1 dato' : `Faltan ${numero(n)} datos`;
}

/** «Añadir otro deporte (1 de 4)». */
export function textoAnadirDeporte(n: number): string {
  return `Añadir otro deporte (${numero(n)} de ${numero(MAX_DEPORTES)})`;
}

/** Lo que hace falta del catálogo para montar el cuerpo, como en `onSubmit` de hoy. */
export interface ReglasCuerpo {
  permiteCompeticion(deporte: string): boolean;
  requiereLicencia(deporte: string): boolean;
  primeraDisciplinaActiva(): string;
  categoriaPorEdad(fechaNacimiento: string | null, deporte: string): string;
  hoy: string;
}

/**
 * El cuerpo «nuevo» del `POST /api/alumnos/crear`: el `getRawValue()` del formulario con las mismas transformaciones que
 * `CrearAlumnoComponent.onSubmit` (NIF y DNI/NIE del responsable recortados y en mayúsculas, vacío = null; antigüedad =
 * alta si falta; competición limpia si no aplica; categoría por edad; `aptoParaExamen` false). La sede va como
 * `sedePrincipalId` solo si se pidió (varias sedes); si no, el cuerpo es el de hoy.
 */
export function cuerpoAlta(crudo: Record<string, any>, reglas: ReglasCuerpo, pedirSede: boolean): Record<string, any> {
  const datos: Record<string, any> = { ...crudo };
  const sede = datos['sede'];
  delete datos['sede'];
  const nif = typeof datos['nif'] === 'string' ? datos['nif'].trim().toUpperCase() : '';
  datos['nif'] = nif === '' ? null : nif;
  const responsableNif =
    typeof datos['responsableLegalNif'] === 'string' ? datos['responsableLegalNif'].trim().toUpperCase() : '';
  datos['responsableLegalNif'] = responsableNif === '' ? null : responsableNif;

  datos['deportesInicial'] = (datos['deportesInicial'] ?? []).map((original: Record<string, any>) => {
    const d = { ...original };
    if (!d['fechaAltaInicial']) {
      d['fechaAltaInicial'] = d['fechaAlta'];
    }
    if (!reglas.permiteCompeticion(d['deporte']) || (reglas.requiereLicencia(d['deporte']) && !d['tieneLicencia'])) {
      d['competidor'] = false;
    }
    if (d['competidor']) {
      if (!d['fechaAltaCompeticion']) {
        d['fechaAltaCompeticion'] = d['fechaAlta'] || reglas.hoy;
      }
      if (!d['fechaAltaCompetidorInicial']) {
        d['fechaAltaCompetidorInicial'] = d['fechaAltaInicial'] || d['fechaAlta'] || reglas.hoy;
      }
      if (!d['categoria']) {
        d['categoria'] = reglas.categoriaPorEdad(datos['fechaNacimiento'] || null, d['deporte'] || reglas.primeraDisciplinaActiva());
      }
      d['peso'] = d['peso'] === '' || d['peso'] === null || d['peso'] === undefined ? null : Number(d['peso']);
    } else {
      d['categoria'] = '';
      d['fechaAltaCompeticion'] = null;
      d['fechaAltaCompetidorInicial'] = null;
      d['peso'] = null;
    }
    return d;
  });
  delete datos['deporte'];
  delete datos['grado'];
  datos['aptoParaExamen'] = false;
  if (pedirSede && sede !== null && sede !== undefined && sede !== '') {
    datos['sedePrincipalId'] = Number(sede);
  }
  return datos;
}

/** El texto de un error del alta: el del servidor (texto plano o «mensaje») o el de hoy. */
export function textoErrorAlta(error: any): string {
  if (typeof error?.error === 'string' && error.error.trim()) {
    return error.error.trim();
  }
  const mensaje = error?.error?.mensaje;
  return typeof mensaje === 'string' && mensaje.trim() ? mensaje.trim() : 'No has completado todos los campos requeridos';
}

/** Dónde se enseña el error del servidor bajo un campo: NIF duplicado (409), la sede o un deporte. */
export type DestinoErrorServidor = { tipo: 'persona'; campo: CampoPersona } | { tipo: 'deporte'; indice: number } | null;

export function destinoErrorServidor(
  estado: number | undefined,
  texto: string,
  deportes: ReadonlyArray<{ codigo: string; nombre: string }>
): DestinoErrorServidor {
  if (estado === 409) {
    return { tipo: 'persona', campo: 'nif' };
  }
  const t = texto.toLocaleLowerCase('es-ES');
  if (t.includes('sede')) {
    return { tipo: 'persona', campo: 'sede' };
  }
  if (t.includes('deporte') || t.includes('disciplina')) {
    const indice = deportes.findIndex(
      (d) => (d.codigo && t.includes(d.codigo.toLocaleLowerCase('es-ES'))) || (d.nombre && t.includes(d.nombre.toLocaleLowerCase('es-ES')))
    );
    return { tipo: 'deporte', indice: indice >= 0 ? indice : 0 };
  }
  if (t.includes('nif')) {
    return { tipo: 'persona', campo: 'nif' };
  }
  return null;
}
