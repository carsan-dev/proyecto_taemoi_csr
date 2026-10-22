import { AccionInforme } from '../../../servicios/generales/informe-pdf.service';
import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InformeCategory, categoriasDeInforme, descripcionInforme } from './categorias-informe';

@Component({
  selector: 'app-informe-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './informe-modal.component.html',
  styleUrl: './informe-modal.component.scss'
})
export class InformeModalComponent implements OnInit, OnChanges {
  @Input() generando = false;
  @Input() title: string = 'Generar Informe';
  @Input() opcionesInforme: Array<{ value: string, label: string }> = [];
  @Input() temporadasReservasPlaza: string[] = [];

  modalVisible = false;
  selectedInforme: string = '';
  soloActivos: boolean = true;
  categoriasInforme: InformeCategory[] = [];
  temporadaSeleccionada: string = '';
  opcionesTemporadaReservas: Array<{ value: string, label: string }> = [];

  @Output() cerrar = new EventEmitter<void>();
  @Output() informeSeleccionado = new EventEmitter<{ tipo: string, soloActivos: boolean, temporada?: string, accion: AccionInforme }>();

  ngOnInit(): void {
    this.organizarInformesPorCategoria();
    this.actualizarOpcionesTemporadaReservas();
    setTimeout(() => {
      this.modalVisible = true;
    }, 0);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['opcionesInforme'] && !changes['opcionesInforme'].firstChange) {
      this.organizarInformesPorCategoria();
    }
    if (changes['temporadasReservasPlaza']) {
      this.actualizarOpcionesTemporadaReservas();
    }
  }

  /**
   * Organize reports into categories
   */
  organizarInformesPorCategoria(): void {
    this.categoriasInforme = categoriasDeInforme(this.opcionesInforme);
  }

  /**
   * Get description for each report type
   */
  getDescription(value: string): string {
    return descripcionInforme(value);
  }

  private actualizarOpcionesTemporadaReservas(): void {
    const temporadas = [...new Set(this.temporadasReservasPlaza)].sort((a, b) => b.localeCompare(a));
    const anoActual = new Date().getFullYear();
    const temporadaActual = `${anoActual}/${anoActual + 1}`;
    this.opcionesTemporadaReservas = temporadas.map(temporada => ({
      value: temporada,
      label: temporada === temporadaActual ? `Actual (${temporada})` : temporada,
    }));

    if (!this.temporadaSeleccionada || !temporadas.includes(this.temporadaSeleccionada)) {
      this.temporadaSeleccionada = temporadas.includes(temporadaActual) ? temporadaActual : (temporadas[0] || '');
    }
  }

  cerrarModal(): void {
    this.modalVisible = false;
    setTimeout(() => {
      this.cerrar.emit();
    }, 300);
  }

  generarInforme(accion: AccionInforme = 'descargar'): void {
    if (!this.selectedInforme || this.generando) {
      return;
    }
    this.informeSeleccionado.emit({
      accion,
      tipo: this.selectedInforme,
      soloActivos: this.soloActivos,
      temporada: this.esInformeReservasPlaza() ? this.temporadaSeleccionada : undefined,
    });

  }

  /**
   * Select a report
   */
  seleccionarInforme(value: string): void {
    this.selectedInforme = value;
  }

  esInformeReservasPlaza(): boolean {
    return this.selectedInforme === 'reservas-plaza';
  }

  puedeGenerarInforme(): boolean {
    return !!this.selectedInforme && !this.generando;
  }
}
