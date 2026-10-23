import { ChangeDetectorRef, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { PreinscripcionService } from '../../features/preinscripcion/preinscripcion.service';
import { AvisosService } from '../avisos/avisos.service';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { AltaPreinscripcion } from './alta-preinscripcion';
import { nombreCompleto } from './preinscripciones-vista';

/**
 * «Revisar y finalizar alta» de Preinscripciones del panel nuevo (8b-3h): el diálogo completo de hoy («Finalizar
 * preinscripción») en el panel lateral del panel nuevo (decisión 270): destino de la ficha (una compatible o una nueva),
 * los datos que se actualizan en la ficha existente, el correo definitivo de acceso, la discapacidad de una solicitud
 * antigua y la tarifa, el importe y el grado. «Aceptar y completar alta» envía `POST …/finalizar` con el cuerpo de hoy
 * (`AltaPreinscripcion.cuerpo`); mientras va, el panel no se cierra y el botón queda ocupado (sin doble envío). Si falla,
 * sigue abierto con el mensaje del servidor en un aviso.
 */
@Component({
  selector: 'pn-finalizar-alta',
  standalone: true,
  imports: [FormsModule, PanelLateralComponent, BotonOcupadoComponent],
  templateUrl: './finalizar-alta.component.html',
  styleUrls: ['./finalizar-alta.component.scss'],
})
export class FinalizarAltaComponent {
  private readonly api = inject(PreinscripcionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);

  @Input({ required: true }) alta!: AltaPreinscripcion;
  @Output() readonly cerrar = new EventEmitter<void>();
  @Output() readonly finalizada = new EventEmitter<void>();

  enviando = false;
  readonly roles = ['PADRE', 'MADRE', 'HIJO', 'HIJA'];

  get s(): Record<string, any> {
    return this.alta.solicitud;
  }

  get nombre(): string {
    return nombreCompleto(this.s);
  }

  get numeroTurnos(): number {
    return this.s['turnosSolicitados']?.length || 1;
  }

  esElegida(candidato: any): boolean {
    return this.alta.accionAlumno === 'VINCULAR_EXISTENTE' && this.alta.alumnoSeleccionado?.id === candidato.id;
  }

  volver(): void {
    if (!this.enviando) {
      this.cerrar.emit();
    }
  }

  aceptar(): void {
    if (this.enviando || !this.alta.finalizacionValida) {
      return;
    }
    this.enviando = true;
    this.api.finalizar(this.s['referencia'], this.alta.cuerpo()).subscribe({
      next: () => {
        this.enviando = false;
        this.finalizada.emit();
      },
      error: (e) => {
        this.enviando = false;
        const mensaje = e?.error?.mensaje;
        this.avisos.error(typeof mensaje === 'string' && mensaje.trim() ? mensaje : 'No se pudo finalizar la preinscripción.');
        this.cdr.markForCheck();
      },
    });
  }
}
