import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-selector-mes-ano',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="selector-mes-ano">
      <select [id]="inputId" class="admin-form-control form-select" aria-label="Mes"
        [ngModel]="mes" (ngModelChange)="mes = $event; actualizar()" [ngModelOptions]="{standalone: true}">
        <option value="">Selecciona el mes</option>
        @for (nombre of meses; track $index) {
          <option [value]="($index + 1).toString().padStart(2, '0')">{{ nombre }}</option>
        }
      </select>
      <select [id]="inputId + '-ano'" class="admin-form-control form-select" aria-label="Año"
        [ngModel]="ano" (ngModelChange)="ano = $event; actualizar()" [ngModelOptions]="{standalone: true}">
        @for (opcion of anos; track opcion) { <option [value]="opcion">{{ opcion }}</option> }
      </select>
    </div>
  `,
  styles: `
    :host { display: block; }
    .selector-mes-ano { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: .75rem; }
    select { min-width: 0; width: 100%; }
  `,
})
export class SelectorMesAnoComponent implements OnChanges {
  @Input({ required: true }) inputId = '';
  @Input() valor: string | undefined = '';
  @Output() valorChange = new EventEmitter<string>();
  readonly meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
  mes = '';
  ano = String(new Date().getFullYear());
  anos: string[] = [];

  ngOnChanges(): void {
    const partes = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(this.valor ?? '');
    this.ano = partes?.[1] ?? this.ano;
    this.mes = partes?.[2] ?? '';
    // Keep a preselected year available even when it falls outside the usual range.
    const inicio = Math.min(1900, Number(this.ano));
    const fin = Math.max(2100, Number(this.ano));
    this.anos = Array.from({ length: fin - inicio + 1 }, (_, i) => String(inicio + i).padStart(4, '0'));
  }

  actualizar(): void {
    this.valorChange.emit(this.mes ? `${this.ano}-${this.mes}` : '');
  }
}
