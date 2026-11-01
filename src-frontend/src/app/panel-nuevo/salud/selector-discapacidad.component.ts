import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ORDEN_DISCAPACIDAD, TIPOS_DISCAPACIDAD, TipoDiscapacidad } from '../../interfaces/tipo-discapacidad';

@Component({
  selector: 'pn-selector-discapacidad',
  standalone: true,
  templateUrl: './selector-discapacidad.component.html',
  styleUrls: ['./selector-discapacidad.component.scss'],
})
export class SelectorDiscapacidadComponent {
  @Input({ required: true }) id = '';
  @Input() tipos: TipoDiscapacidad[] = [];
  @Input() otra = '';
  @Input() error = false;
  @Input() deshabilitado = false;
  @Output() readonly tiposChange = new EventEmitter<TipoDiscapacidad[]>();
  @Output() readonly otraChange = new EventEmitter<string>();
  readonly orden = ORDEN_DISCAPACIDAD;
  readonly etiquetas = TIPOS_DISCAPACIDAD;

  alternar(tipo: TipoDiscapacidad): void {
    if (this.deshabilitado) return;
    const elegidos = this.tipos.includes(tipo) ? this.tipos.filter(t => t !== tipo) : [...this.tipos, tipo];
    this.tiposChange.emit(this.orden.filter(t => elegidos.includes(t)));
    if (tipo === 'OTRA' && !elegidos.includes('OTRA')) this.otraChange.emit('');
  }
}
