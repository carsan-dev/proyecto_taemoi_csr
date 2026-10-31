import { Component, EventEmitter, Input, OnDestroy, Output, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';

import { SedeVisible } from '../../../servicios/authentication/permisos-sesion.service';
import { mensajeDeError } from '../../../servicios/endpoints/caja.service';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { PanelLateralComponent } from '../../panel-lateral/panel-lateral.component';
import { eur, plural } from '../../formato/formato-es';
import { CambioSede, CambioSedeService } from './cambio-sede.service';

/** Un efecto del cambio, en la lista «qué pasa»: el titular y su aclaración. */
export interface EfectoCambioSede {
  clave: 'pendientes' | 'cobrados' | 'grupos';
  titulo: string;
  detalle: string;
}

/**
 * Efectos de una lectura previa, tal como los dice el boceto de E9-1: lo sin cobrar pasa a la sede nueva, lo cobrado se
 * queda en la actual (omitido si son 0 o no hay sede actual) y los grupos no cambian (omitido si son 0).
 */
export function efectosDelCambio(lectura: CambioSede): EfectoCambioSede[] {
  const actual = lectura.sedeActualNombre;
  const nueva = lectura.sedeNuevaNombre;
  const efectos: EfectoCambioSede[] = [];
  const pendientes = lectura.cargosPendientes;
  if (pendientes > 0) {
    efectos.push({
      clave: 'pendientes',
      titulo: `${plural(pendientes, 'cargo sin cobrar', 'cargos sin cobrar')} ${pendientes === 1 ? 'pasa' : 'pasan'} a ${nueva}`,
      detalle: `${eur(lectura.importePendiente)}. ${pendientes === 1 ? 'Se cobrará' : 'Se cobrarán'} en la caja de ${nueva}.`,
    });
  } else {
    efectos.push({ clave: 'pendientes', titulo: 'No tiene cargos sin cobrar', detalle: `Nada pasa a ${nueva}.` });
  }
  const cobrados = lectura.cargosCobrados;
  if (actual && cobrados > 0) {
    efectos.push({
      clave: 'cobrados',
      titulo: `${plural(cobrados, 'cargo cobrado', 'cargos cobrados')} ${cobrados === 1 ? 'se queda' : 'se quedan'} en ${actual}`,
      detalle: `${eur(lectura.importeCobrado)}. La caja de ${actual} no cambia.`,
    });
  }
  const grupos = lectura.gruposEnSedeActual;
  if (actual && grupos > 0) {
    efectos.push({
      clave: 'grupos',
      titulo: `Sigue en ${plural(grupos, 'grupo', 'grupos')} de ${actual}`,
      detalle: 'Los grupos no cambian. Si deja de ir, cámbialo en Deportes.',
    });
  }
  return efectos;
}

/**
 * Panel lateral «Cambiar de sede» de la ficha (E9-1, boceto `fase3-e9-boceto-sede.html`, 3): sede actual → nueva, la
 * lectura previa de lo que pasa con el dinero y, al confirmar, `PUT /api/alumnos/{id}/sede`. Un error del servidor se
 * queda en el panel, que no se cierra; con el envío en vuelo no se cierra ni se puede enviar otra vez.
 */
@Component({
  selector: 'pn-cambiar-sede',
  standalone: true,
  imports: [ReactiveFormsModule, PanelLateralComponent, BotonOcupadoComponent],
  templateUrl: './cambiar-sede-panel.component.html',
  styleUrls: ['./cambiar-sede-panel.component.scss'],
})
export class CambiarSedePanelComponent implements OnDestroy {
  private readonly api = inject(CambioSedeService);

  @Input({ required: true }) alumnoId = 0;
  /** La sede principal del alumno; nula si no tiene. */
  @Input() sedeActualId: number | null = null;
  @Input() sedeActualNombre: string | null = null;
  /** Las sedes de la escuela; solo se ofrecen las activas distintas de la actual. */
  @Input() sedes: SedeVisible[] = [];
  @Output() readonly cerrado = new EventEmitter<void>();
  /** Cambio aplicado: el nombre de la sede nueva. */
  @Output() readonly cambiado = new EventEmitter<string>();

  readonly sede = new FormControl<number | null>(null);
  lectura: CambioSede | null = null;
  leyendo = false;
  errorLectura = '';
  enviando = false;
  errorEnvio = '';
  private peticion?: Subscription;
  private envio?: Subscription;

  get opciones(): SedeVisible[] {
    return this.sedes.filter((s) => s.activa !== false && s.id !== this.sedeActualId);
  }

  get nombreActual(): string {
    return this.sedeActualNombre || 'Sin sede';
  }

  get nombreNueva(): string {
    return this.lectura?.sedeNuevaNombre ?? this.sedes.find((s) => s.id === this.sede.value)?.nombre ?? '';
  }

  get efectos(): EfectoCambioSede[] {
    return this.lectura ? efectosDelCambio(this.lectura) : [];
  }

  elegida(): void {
    this.errorEnvio = '';
    this.leer();
  }

  /** Pide la lectura previa de la sede elegida; una lectura anterior en vuelo se descarta. */
  leer(): void {
    const sedeId = this.sede.value;
    this.peticion?.unsubscribe();
    this.lectura = null;
    this.errorLectura = '';
    if (sedeId === null) {
      this.leyendo = false;
      return;
    }
    this.leyendo = true;
    this.peticion = this.api.lectura(this.alumnoId, sedeId).subscribe({
      next: (lectura) => { this.lectura = lectura; this.leyendo = false; },
      error: (error: unknown) => {
        this.leyendo = false;
        this.errorLectura = mensajeDeError(error) ?? 'No se pudo calcular qué pasa con los cargos.';
      },
    });
  }

  confirmar(): void {
    const sedeId = this.sede.value;
    if (this.enviando || this.leyendo || !this.lectura || this.errorLectura || sedeId === null) return;
    this.enviando = true;
    this.errorEnvio = '';
    this.envio = this.api.aplicar(this.alumnoId, sedeId).subscribe({
      next: (resultado) => {
        this.enviando = false;
        this.cambiado.emit(resultado?.sedeNuevaNombre || this.nombreNueva);
      },
      error: (error: unknown) => {
        this.enviando = false;
        this.errorEnvio = mensajeDeError(error) ?? 'No se pudo cambiar la sede. Inténtalo de nuevo.';
      },
    });
  }

  ngOnDestroy(): void {
    this.peticion?.unsubscribe();
    this.envio?.unsubscribe();
  }
}
