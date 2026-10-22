import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';

const MESES = [
  'Enero',
  'Febrero',
  'Marzo',
  'Abril',
  'Mayo',
  'Junio',
  'Julio',
  'Agosto',
  'Septiembre',
  'Octubre',
  'Noviembre',
  'Diciembre',
];

/**
 * Mes y año con su etiqueta visible cada uno (boceto `InformesPanel.dc.html`), con el mismo valor «AAAA-MM» y los mismos
 * años que `SelectorMesAnoComponent` de hoy (vacío mientras no se elige el mes). Solo en el panel nuevo.
 */
@Component({
  selector: 'pn-campo-mes-ano',
  standalone: true,
  template: `
    <div class="pn-mes-ano">
      <div class="pn-mes-ano__campo">
        <label class="pn-mes-ano__etiqueta" [for]="campoId">Mes</label>
        <select class="pn-mes-ano__control" [id]="campoId" (change)="cambiarMes($any($event.target).value)">
          <option value="" [selected]="mes === ''">Selecciona el mes</option>
          @for (nombre of meses; track $index) {
          <option [value]="clave($index)" [selected]="mes === clave($index)">{{ nombre }}</option>
          }
        </select>
      </div>
      <div class="pn-mes-ano__campo">
        <label class="pn-mes-ano__etiqueta" [for]="campoId + '-ano'">Año</label>
        <select class="pn-mes-ano__control" [id]="campoId + '-ano'" (change)="cambiarAno($any($event.target).value)">
          @for (opcion of anos; track opcion) {
          <option [value]="opcion" [selected]="opcion === ano">{{ opcion }}</option>
          }
        </select>
      </div>
    </div>
  `,
  styleUrls: ['./campos-panel.scss'],
})
export class CampoMesAnoComponent implements OnChanges {
  @Input({ required: true }) campoId = '';
  @Input() valor: string | undefined = '';
  @Output() readonly valorChange = new EventEmitter<string>();

  readonly meses = MESES;
  mes = '';
  ano = String(new Date().getFullYear());
  anos: string[] = rangoAnos(this.ano);

  clave(indice: number): string {
    return String(indice + 1).padStart(2, '0');
  }

  ngOnChanges(): void {
    const partes = /^(\d{4})-(0[1-9]|1[0-2])$/.exec(this.valor ?? '');
    this.ano = partes?.[1] ?? this.ano;
    this.mes = partes?.[2] ?? '';
    this.anos = rangoAnos(this.ano);
  }

  cambiarMes(mes: string): void {
    this.mes = mes;
    this.emitir();
  }

  cambiarAno(ano: string): void {
    this.ano = ano;
    this.emitir();
  }

  private emitir(): void {
    this.valorChange.emit(this.mes ? `${this.ano}-${this.mes}` : '');
  }
}

/** De 1900 a 2100, como hoy, y el año elegido aunque quede fuera. */
function rangoAnos(ano: string): string[] {
  const inicio = Math.min(1900, Number(ano));
  const fin = Math.max(2100, Number(ano));
  return Array.from({ length: fin - inicio + 1 }, (_, i) => String(inicio + i).padStart(4, '0'));
}
