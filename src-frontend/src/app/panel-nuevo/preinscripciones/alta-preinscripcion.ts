import { cuantiaEstandar } from '../../features/catalogo/catalogo-reglas';
import { CatalogoTarifa } from '../../features/catalogo/catalogo.models';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { tarifaInicialPreinscripcion } from '../../features/catalogo/legado-taemoi';
import { diaApi } from '../../utils/fecha-api';
import { Solicitud } from './preinscripciones-vista';

export type AccionAlumno = '' | 'CREAR_NUEVO' | 'VINCULAR_EXISTENTE';
export type DecisionEmail = '' | 'CONSERVAR_FICHA' | 'USAR_PREINSCRIPCION';

export interface CampoComparable {
  clave: string;
  etiqueta: string;
  pre: string;
  alumno: string;
  soloMenor?: boolean;
}

export interface DatosAlta {
  tipoTarifa: string;
  cuantiaTarifa: number | null;
  rolFamiliar: string;
  grupoFamiliar: string;
  grado: string;
  fechaGrado: string;
}

/** El cuerpo de `POST /preinscripciones/{ref}/finalizar` (`PreinscripcionService.finalizar`). */
export interface CuerpoFinalizar {
  accionAlumno: string;
  alumnoId?: number;
  camposActualizar: string[];
  discapacidadHistorica: boolean | null;
  decisionEmail?: string;
  datosDeporte: any;
}

/** Los mismos campos, etiquetas y orden que la pantalla de hoy. */
export const CAMPOS_COMPARABLES: CampoComparable[] = [
  { clave: 'NOMBRE', etiqueta: 'Nombre', pre: 'nombre', alumno: 'nombre' },
  { clave: 'APELLIDOS', etiqueta: 'Apellidos', pre: 'apellidos', alumno: 'apellidos' },
  { clave: 'FECHA_NACIMIENTO', etiqueta: 'Fecha de nacimiento', pre: 'fechaNacimiento', alumno: 'fechaNacimiento' },
  { clave: 'DIRECCION', etiqueta: 'Dirección', pre: 'direccion', alumno: 'direccion' },
  { clave: 'TELEFONO', etiqueta: 'Teléfono', pre: 'telefono', alumno: 'telefono' },
  { clave: 'TELEFONO2', etiqueta: 'Teléfono secundario', pre: 'telefono2', alumno: 'telefono2' },
  { clave: 'NIF', etiqueta: 'DNI/NIE', pre: 'dni', alumno: 'nif' },
  { clave: 'TIENE_DISCAPACIDAD', etiqueta: 'Discapacidad', pre: 'tieneDiscapacidad', alumno: 'tieneDiscapacidad' },
  { clave: 'RESPONSABLE_LEGAL_NOMBRE', etiqueta: 'Responsable legal', pre: 'tutorNombre', alumno: 'responsableLegalNombre', soloMenor: true },
  { clave: 'RESPONSABLE_LEGAL_NIF', etiqueta: 'DNI/NIE del responsable', pre: 'tutorDni', alumno: 'responsableLegalNif', soloMenor: true },
  { clave: 'EMAIL', etiqueta: 'Correo electrónico', pre: 'email', alumno: 'email' },
];

/**
 * «Revisar y finalizar alta» de Preinscripciones del panel nuevo (8b-3h): el estado y las reglas del diálogo de hoy
 * (`PreinscripcionesAdminComponent`, de `abrirFinalizacion` a `finalizar`), copiados sin cambiar nada: destino de la
 * ficha (nueva o existente, con la vinculación sola por DNI exacto), los datos distintos que se actualizan, el correo
 * definitivo de acceso, la discapacidad de las solicitudes antiguas, la tarifa (con su importe del catálogo) y el grado.
 * `alta-preinscripcion.spec.ts` lo compara paso a paso con la pantalla de hoy, cuerpo enviado incluido.
 */
export class AltaPreinscripcion {
  accionAlumno: AccionAlumno = '';
  alumnoSeleccionado: any;
  discapacidadHistorica: boolean | null = null;
  decisionEmail: DecisionEmail = '';
  datosAlta: DatosAlta = { tipoTarifa: '', cuantiaTarifa: null, rolFamiliar: 'NINGUNO', grupoFamiliar: '', grado: '', fechaGrado: '' };
  readonly camposActualizar = new Set<string>();

  constructor(
    readonly solicitud: Solicitud,
    private readonly catalogo: CatalogoService
  ) {
    // Como `abrirFinalizacion`: una sola ficha con el mismo DNI se vincula sola; sin fichas, ficha nueva; si no, a elegir.
    this.discapacidadHistorica = solicitud['tieneDiscapacidad'] ?? null;
    this.prepararTarifa();
    const candidatos = solicitud['alumnosCoincidentes'] || [];
    if (solicitud['dni'] && candidatos.length === 1 && candidatos[0].nif?.toUpperCase() === solicitud['dni'].toUpperCase()) {
      this.elegirAlumno(candidatos[0]);
    } else if (candidatos.length === 0) {
      this.crearNuevaFicha();
    } else {
      this.accionAlumno = '';
      this.alumnoSeleccionado = undefined;
      this.camposActualizar.clear();
    }
  }

  get candidatos(): any[] {
    return this.solicitud['alumnosCoincidentes'] || [];
  }

  elegirAlumno(alumno: any): void {
    this.accionAlumno = 'VINCULAR_EXISTENTE';
    this.alumnoSeleccionado = alumno;
    this.decisionEmail = '';
    this.prepararTarifa(alumno);
    this.camposActualizar.clear();
    this.camposAplicables.filter((c) => this.hayDiferencia(c)).forEach((c) => this.camposActualizar.add(c.clave));
  }

  crearNuevaFicha(): void {
    if (this.tieneCoincidenciaDniExacta) {
      return;
    }
    this.accionAlumno = 'CREAR_NUEVO';
    this.alumnoSeleccionado = undefined;
    this.decisionEmail = '';
    this.prepararTarifa();
    this.camposActualizar.clear();
  }

  confirmarDiscapacidadHistorica(valor: boolean): void {
    this.discapacidadHistorica = valor;
    if (this.accionAlumno !== 'VINCULAR_EXISTENTE' || !this.alumnoSeleccionado) {
      return;
    }
    const campo = this.camposAplicables.find((c) => c.clave === 'TIENE_DISCAPACIDAD');
    if (campo && this.hayDiferencia(campo)) {
      this.camposActualizar.add(campo.clave);
    } else {
      this.camposActualizar.delete('TIENE_DISCAPACIDAD');
    }
  }

  alternarCampo(clave: string, marcado: boolean): void {
    if (marcado) {
      this.camposActualizar.add(clave);
    } else {
      this.camposActualizar.delete(clave);
    }
  }

  get camposAplicables(): CampoComparable[] {
    return CAMPOS_COMPARABLES.filter(
      (c) =>
        c.clave !== 'EMAIL' &&
        (!c.soloMenor || this.esMenor) &&
        (c.clave !== 'NIF' || tieneValor(this.solicitud['dni'])) &&
        (c.clave !== 'TELEFONO2' || tieneValor(this.solicitud['telefono2']))
    );
  }

  /** Los campos que difieren entre la ficha elegida y la solicitud (los que se pueden actualizar). */
  get camposDistintos(): CampoComparable[] {
    return this.camposAplicables.filter((c) => this.hayDiferencia(c));
  }

  hayDiferencia(c: CampoComparable): boolean {
    const actual = valorComparable(this.alumnoSeleccionado?.[c.alumno], c.clave);
    const recibido = valorComparable(this.valorPreinscripcion(c), c.clave);
    return actual !== recibido;
  }

  valorPreinscripcion(c: CampoComparable): any {
    return c.clave === 'TIENE_DISCAPACIDAD' ? (this.solicitud['tieneDiscapacidad'] ?? this.discapacidadHistorica) : this.solicitud[c.pre];
  }

  mostrarValor(v: any, campo?: string): string {
    if (campo === 'FECHA_NACIMIENTO' && v != null) {
      const dia = diaApi(v);
      if (dia) {
        return dia.split('-').reverse().join('/');
      }
    }
    if (v === true) {
      return 'Sí';
    }
    if (v === false) {
      return 'No';
    }
    return v ?? 'Sin dato';
  }

  get requiereDatosDeporte(): boolean {
    return this.accionAlumno === 'CREAR_NUEVO' || (this.accionAlumno === 'VINCULAR_EXISTENTE' && !!this.alumnoSeleccionado?.requiereDatosDeporte);
  }

  /** Edad por año, mes y día de `diaApi` (la API envía [a, m, d]). */
  get esMenor(): boolean {
    const dia = diaApi(this.solicitud['fechaNacimiento']);
    if (!dia) {
      return false;
    }
    const [a, m, d] = dia.split('-').map(Number);
    const hoy = new Date();
    let edad = hoy.getFullYear() - a;
    if (hoy.getMonth() + 1 < m || (hoy.getMonth() + 1 === m && hoy.getDate() < d)) {
      edad--;
    }
    return edad < 18;
  }

  get requiereGrado(): boolean {
    return this.catalogo.tieneGrados(this.solicitud['deporte']);
  }

  get tarifasDisponibles(): CatalogoTarifa[] {
    return this.catalogo.tarifasDe(this.solicitud['deporte']);
  }

  get requiereRolFamiliar(): boolean {
    return this.catalogo.tarifa(this.datosAlta.tipoTarifa)?.requiereRolFamiliar === true;
  }

  get requiereGrupoFamiliar(): boolean {
    return this.catalogo.tarifa(this.datosAlta.tipoTarifa)?.requiereGrupoFamiliar === true;
  }

  get gradosDisponibles(): { codigo: string; nombre: string }[] {
    return this.catalogo.gradosSeleccionables(this.solicitud['deporte']);
  }

  /** Al cambiar de tarifa: su importe del catálogo y fuera el rol o el grupo que ya no pida. */
  cambiarTarifa(): void {
    this.datosAlta.cuantiaTarifa = this.cuantia(this.datosAlta.tipoTarifa);
    if (!this.requiereRolFamiliar) {
      this.datosAlta.rolFamiliar = 'NINGUNO';
    }
    if (!this.requiereGrupoFamiliar) {
      this.datosAlta.grupoFamiliar = '';
    }
  }

  get correosDiferentes(): boolean {
    return (
      this.accionAlumno === 'VINCULAR_EXISTENTE' &&
      !!this.alumnoSeleccionado &&
      valorComparable(this.alumnoSeleccionado.email, 'EMAIL') !== valorComparable(this.solicitud['email'], 'EMAIL')
    );
  }

  get discapacidadEfectiva(): boolean | null {
    return this.solicitud['tieneDiscapacidad'] ?? this.discapacidadHistorica;
  }

  get tieneCoincidenciaDniExacta(): boolean {
    const dni = this.solicitud['dni']?.trim().toUpperCase();
    return Boolean(dni && this.candidatos.some((a: any) => a.nif?.trim().toUpperCase() === dni));
  }

  get finalizacionValida(): boolean {
    if (!this.accionAlumno || this.discapacidadEfectiva == null || (this.accionAlumno === 'VINCULAR_EXISTENTE' && !this.alumnoSeleccionado)) {
      return false;
    }
    if (this.correosDiferentes && !this.decisionEmail) {
      return false;
    }
    const d = this.datosAlta;
    if (!d.tipoTarifa || d.cuantiaTarifa == null || d.cuantiaTarifa < 0) {
      return false;
    }
    if (this.requiereDatosDeporte && this.requiereGrado && (!d.grado || !d.fechaGrado)) {
      return false;
    }
    if (this.requiereRolFamiliar && d.rolFamiliar === 'NINGUNO') {
      return false;
    }
    if (this.requiereGrupoFamiliar && !d.grupoFamiliar.trim()) {
      return false;
    }
    return true;
  }

  /** El cuerpo de `finalizar`, idéntico al de hoy. */
  cuerpo(): CuerpoFinalizar {
    const conGrado = this.requiereDatosDeporte && this.requiereGrado;
    const datosDeporte = { ...this.datosAlta, grado: conGrado ? this.datosAlta.grado : null, fechaGrado: conGrado ? this.datosAlta.fechaGrado : null };
    return {
      accionAlumno: this.accionAlumno,
      alumnoId: this.accionAlumno === 'VINCULAR_EXISTENTE' ? this.alumnoSeleccionado.id : undefined,
      camposActualizar: [...this.camposActualizar],
      discapacidadHistorica: this.solicitud['tieneDiscapacidad'] == null ? this.discapacidadHistorica : null,
      decisionEmail: this.decisionEmail || undefined,
      datosDeporte,
    };
  }

  /** D9: preselección de Taemoi (legado-taemoi); otras escuelas, ninguna. */
  private tarifaInicial(): string {
    const deporte = this.solicitud['deporte'];
    return (
      tarifaInicialPreinscripcion(
        this.catalogo.esLegadoTaemoi(),
        deporte,
        diaApi(this.solicitud['fechaNacimiento']),
        this.catalogo.tarifasDe(deporte)
      ) ?? ''
    );
  }

  private prepararTarifa(alumno?: any): void {
    const hoy = new Date().toISOString().slice(0, 10);
    const tipo = alumno?.tipoTarifa || this.tarifaInicial();
    this.datosAlta = {
      tipoTarifa: tipo,
      cuantiaTarifa: alumno?.cuantiaTarifa ?? this.cuantia(tipo),
      rolFamiliar: alumno?.rolFamiliar || 'NINGUNO',
      grupoFamiliar: alumno?.grupoFamiliar || '',
      grado: this.gradosDisponibles[0]?.codigo ?? '',
      fechaGrado: hoy,
    };
  }

  /** Importe estándar sin rol, como en la pantalla de hoy. Desconocida: 0. */
  private cuantia(tarifa: string): number {
    return cuantiaEstandar(this.catalogo.tarifa(tarifa));
  }
}

function tieneValor(valor: any): boolean {
  return valor !== null && valor !== undefined && String(valor).trim() !== '';
}

/** FECHA_NACIMIENTO llega como [a, m, d] (preinscripción) o en ms (ficha): se compara el día, sin hora ni zona. */
function valorComparable(v: any, campo: string): string {
  if (v == null) {
    return '';
  }
  if (campo === 'FECHA_NACIMIENTO') {
    return diaApi(v) ?? String(v);
  }
  if (v instanceof Date) {
    return v.toISOString().slice(0, 10);
  }
  if (typeof v === 'boolean') {
    return v ? 'true' : 'false';
  }
  const texto = String(v).trim().toLowerCase();
  if (['TELEFONO', 'TELEFONO2'].includes(campo)) {
    const digitos = texto.replace(/\D/g, '');
    if (digitos.length === 13 && digitos.startsWith('0034')) {
      return digitos.slice(4);
    }
    return digitos.length === 11 && digitos.startsWith('34') ? digitos.slice(2) : digitos;
  }
  return /^\d{4}-\d{2}-\d{2}/.test(texto) ? texto.slice(0, 10) : texto.replace(/\s+/g, ' ');
}
