import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  HerramientasAlumnosComponent,
  SeccionHerramientas,
} from '../../../componentes/endpoints/listado-alumnos/herramientas-alumnos/herramientas-alumnos.component';
import { SearchableSelectDirective } from '../../../directives/searchable-select.directive';
import { AccionInforme } from '../../../servicios/generales/informe-pdf.service';
import { AvisosService } from '../../avisos/avisos.service';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { ParteMensaje } from '../../dialogo/dialogo.component';
import { frasePlural } from '../../formato/formato-es';
import { PanelLateralComponent } from '../../panel-lateral/panel-lateral.component';
import { CampoMesAnoComponent } from './campo-mes-ano.component';

export type HerramientaAlumnos = SeccionHerramientas;

/** Las cuatro cargas de «Gestión de mensualidades y licencias», con los nombres de las pestañas de hoy. */
type Carga = 'mensualidades-general' | 'mensualidades-individual' | 'licencias-general' | 'licencias-individual';

const CARGAS: ReadonlyArray<{ valor: Carga; texto: string }> = [
  { valor: 'mensualidades-general', texto: 'Mensualidades generales' },
  { valor: 'mensualidades-individual', texto: 'Mensualidades individuales' },
  { valor: 'licencias-general', texto: 'Licencias generales' },
  { valor: 'licencias-individual', texto: 'Licencias individuales' },
];

const TITULOS: Record<HerramientaAlumnos, { titulo: string; descripcion: string }> = {
  mensualidades: {
    titulo: 'Gestión de mensualidades y licencias',
    descripcion: 'Aplica mensualidades y registra licencias federativas, a todos los alumnos o a uno.',
  },
  asistencia: {
    titulo: 'Listado de asistencia',
    descripcion:
      'Genera un PDF por cada día elegido, con todos los turnos de ese día y un hueco para marcar la asistencia de cada alumno.',
  },
  'listado-mensualidades': {
    titulo: 'Listado de mensualidades',
    descripcion:
      'Genera un PDF con las mensualidades de un mes: alumnos activos de Taekwondo y Kickboxing, con nombre, deporte, descuento, cuantía, grado y fecha de pago.',
  },
};

/**
 * Las tres herramientas del pie del listado de hoy, cada una en el panel lateral del listado nuevo (8b-D1, decisión
 * 270; boceto `InformesPanel.dc.html`). Hereda de `HerramientasAlumnosComponent` (la pantalla de hoy lo sigue usando
 * igual): mismos campos, mismas validaciones, mismas peticiones y mismos nombres de PDF. Solo cambian la plantilla y,
 * por sus métodos `protected`, los avisos, los errores y las confirmaciones del 409, que van con los del panel nuevo
 * (un SweetAlert2 quedaría detrás del panel), y el cargador global, que cede al botón ocupado.
 */
@Component({
  selector: 'pn-herramienta-alumnos',
  standalone: true,
  imports: [FormsModule, SearchableSelectDirective, PanelLateralComponent, BotonOcupadoComponent, CampoMesAnoComponent],
  templateUrl: './herramienta-panel.component.html',
  styleUrls: ['./herramienta-panel.component.scss', './campos-panel.scss'],
})
export class HerramientaPanelComponent extends HerramientasAlumnosComponent {
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);

  @Input({ required: true }) herramienta: HerramientaAlumnos = 'asistencia';
  @Output() readonly cerrar = new EventEmitter<void>();

  readonly cargas = CARGAS;
  readonly etiquetaDia = (dia: string) => dia.charAt(0).toUpperCase() + dia.slice(1);
  /** Botón de PDF que se pulsó: es el que se pone ocupado. */
  pdfPulsado: AccionInforme | null = null;

  get textos(): { titulo: string; descripcion: string } {
    return TITULOS[this.herramienta];
  }

  get carga(): Carga {
    return `${this.mensualidadesTabActiva ? 'mensualidades' : 'licencias'}-${this.mensualidadesTabActiva ? this.mensualidadesTab : this.licenciasTab}` as Carga;
  }

  /** Qué se carga: hoy son dos tarjetas con dos pestañas cada una; aquí, un grupo de cuatro pastillas. */
  private mensualidadesTabActiva = true;

  elegirCarga(carga: Carga): void {
    const [tipo, alcance] = carga.split('-') as ['mensualidades' | 'licencias', 'general' | 'individual'];
    this.mensualidadesTabActiva = tipo === 'mensualidades';
    if (tipo === 'mensualidades') {
      this.cambiarMensualidadesTab(alcance);
    } else {
      this.cambiarLicenciasTab(alcance);
    }
  }

  /** Flechas, Inicio y Fin en el grupo de cargas, como un grupo de radios. */
  teclaCargas(evento: KeyboardEvent): void {
    const actual = CARGAS.findIndex((c) => c.valor === this.carga);
    let siguiente: number;
    switch (evento.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        siguiente = (actual + 1) % CARGAS.length;
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        siguiente = (actual - 1 + CARGAS.length) % CARGAS.length;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = CARGAS.length - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.elegirCarga(CARGAS[siguiente].valor);
    (evento.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('[role="radio"]')[siguiente]?.focus();
  }

  get ocupadoGestion(): boolean {
    switch (this.carga) {
      case 'mensualidades-general':
        return this.procesandoMensualidadesGenerales;
      case 'mensualidades-individual':
        return this.procesandoMensualidadIndividual;
      case 'licencias-general':
        return this.procesandoLicenciasGenerales;
      default:
        return this.procesandoLicenciaIndividual;
    }
  }

  /**
   * Algún envío en vuelo (cualquiera de las cuatro cargas o un PDF): el panel no se cierra por ninguna vía. Hoy el
   * cargador global tapa la pantalla mientras tanto; sin este bloqueo, cerrar y reabrir daría una instancia nueva sin
   * `procesando*` que dejaría lanzar el mismo `POST` a la vez, y el `asignado` de la instancia quitada no llegaría.
   */
  get enVuelo(): boolean {
    return (
      this.procesandoMensualidadesGenerales ||
      this.procesandoMensualidadIndividual ||
      this.procesandoLicenciasGenerales ||
      this.procesandoLicenciaIndividual ||
      this.informes.ocupado
    );
  }

  /** Lo mismo que deshabilita hoy cada botón (y las validaciones de hoy para los que hoy no se deshabilitan). */
  get gestionIncompleta(): boolean {
    switch (this.carga) {
      case 'mensualidades-general':
        return !this.mesAnoSeleccionado;
      case 'mensualidades-individual':
        return !this.alumnoSeleccionado || !this.mesAnoSeleccionadoIndividual;
      case 'licencias-general':
        return !this.anoLicenciaSeleccionado;
      default:
        return !this.alumnoSeleccionado || !this.anoLicenciaSeleccionadoIndividual;
    }
  }

  get accionGestion(): { texto: string; ocupado: string } {
    switch (this.carga) {
      case 'mensualidades-general':
        return { texto: 'Cargar mensualidades', ocupado: 'Cargando…' };
      case 'mensualidades-individual':
        return { texto: 'Cargar mensualidad', ocupado: 'Procesando…' };
      case 'licencias-general':
        return { texto: 'Cargar licencias', ocupado: 'Cargando…' };
      default:
        return { texto: 'Cargar licencia', ocupado: 'Procesando…' };
    }
  }

  ejecutarGestion(): void {
    switch (this.carga) {
      case 'mensualidades-general':
        this.cargarMensualidadesGenerales();
        break;
      case 'mensualidades-individual':
        this.cargarMensualidadIndividual();
        break;
      case 'licencias-general':
        this.cargarLicenciasGenerales();
        break;
      default:
        this.cargarLicenciaIndividual();
    }
  }

  // ------------------------------------------------------------------ PDF

  get diasElegidos(): string {
    const n = this.gruposSeleccionados.length;
    return n === 0 ? 'Elige al menos un día.' : frasePlural(n, 'Se genera {n} PDF.', 'Se generan {n} PDF, uno por día.');
  }

  get pdfIncompleto(): boolean {
    return this.herramienta === 'asistencia'
      ? !this.mesAnoAsistencia || this.gruposSeleccionados.length === 0
      : !this.mesAnoMensualidad;
  }

  generarPdf(accion: AccionInforme): void {
    if (this.informes.ocupado) {
      return;
    }
    this.pdfPulsado = accion;
    if (this.herramienta === 'asistencia') {
      this.generarListadoAsistencia(accion);
    } else {
      this.generarListadoMensualidadMensual(accion);
    }
  }

  // ------------------------------------------------------------------ avisos, confirmaciones y carga del panel nuevo

  protected override avisarExito(texto: string): void {
    // 8b-M: «mensualidades generadas» cierra una tarea larga, y es de las pocas veces que la mascota celebra. Las
    // mensualidades sueltas y las licencias llevan el aviso de siempre.
    if (texto.startsWith('Mensualidades asignadas a ')) {
      this.avisos.exito(texto, undefined, { mascota: 'celebrar' });
      return;
    }
    this.avisos.exito(texto);
  }

  protected override avisarError(texto: string): void {
    this.avisos.error(texto);
  }

  protected override alertarError(texto: string): void {
    this.avisos.error(texto);
  }

  protected override confirmarCarga(texto: string): Promise<boolean> {
    return this.confirmacion
      .confirmar({ titulo: 'Atención', mensaje: texto, accion: 'Sí, cargar', cancelar: 'No, cancelar' })
      .then((r) => r.confirmado);
  }

  protected override confirmarCompletarMensualidad(payload: unknown): Promise<boolean> {
    const r = this.resumenMensualidadIncompleta(payload);
    const partes: ParteMensaje[] = [];
    if (r.mes) {
      partes.push({ fuerte: 'Mes:' }, ` ${r.mes}. `);
    }
    const lista = (mensualidad: string, competidor: string) =>
      [mensualidad ? `mensualidad: ${mensualidad}` : '', competidor ? `cuota competidor: ${competidor}` : '']
        .filter(Boolean)
        .join('; ');
    const tiene = lista(r.existentesMensualidad, r.existentesCompetidor);
    const falta = lista(r.faltantesMensualidad, r.faltantesCompetidor);
    if (tiene) {
      partes.push({ fuerte: 'Ya tiene:' }, ` ${tiene}. `);
    }
    if (falta) {
      partes.push({ fuerte: 'Falta:' }, ` ${falta}. `);
    }
    partes.push('¿Quieres aplicar lo que falta?');
    return this.confirmacion
      .confirmar({ titulo: 'Atención', mensaje: partes, accion: 'Sí, aplicar', cancelar: 'No, cancelar' })
      .then((res) => res.confirmado);
  }

  /** Sin el cargador global: el botón de la acción se pone ocupado. */
  protected override mostrarCarga(): void {}

  protected override ocultarCarga(): void {}
}
