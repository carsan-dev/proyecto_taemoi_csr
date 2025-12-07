import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface InformeOption {
  value: string;
  label: string;
  icon?: string;
  description?: string;
}

interface InformeCategory {
  title: string;
  icon: string;
  color: string;
  opciones: InformeOption[];
}

@Component({
  selector: 'app-informe-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './informe-modal.component.html',
  styleUrl: './informe-modal.component.scss'
})
export class InformeModalComponent implements OnInit {
  @Input() title: string = 'Generar Informe';
  @Input() opcionesInforme: Array<{ value: string, label: string }> = [];

  modalVisible = false;
  selectedInforme: string = '';
  categoriasInforme: InformeCategory[] = [];

  @Output() cerrar = new EventEmitter<void>();
  @Output() informeSeleccionado = new EventEmitter<string>();

  ngOnInit(): void {
    this.organizarInformesPorCategoria();
    setTimeout(() => {
      this.modalVisible = true;
    }, 0);
  }

  /**
   * Organize reports into categories
   */
  organizarInformesPorCategoria(): void {
    const categorias: InformeCategory[] = [
      {
        title: 'Informes por Grado',
        icon: 'bi-trophy-fill',
        color: '#6366f1',
        opciones: []
      },
      {
        title: 'Informes de Promoción',
        icon: 'bi-arrow-up-circle-fill',
        color: '#10b981',
        opciones: []
      },
      {
        title: 'Informes Financieros',
        icon: 'bi-cash-stack',
        color: '#f59e0b',
        opciones: []
      },
      {
        title: 'Otros Informes',
        icon: 'bi-file-earmark-text-fill',
        color: '#8b5cf6',
        opciones: []
      }
    ];

    // Classify each report into its category
    this.opcionesInforme.forEach(opcion => {
      const value = opcion.value.toLowerCase();

      if (value.includes('grado')) {
        categorias[0].opciones.push({
          ...opcion,
          icon: 'bi-award',
          description: this.getDescription(opcion.value)
        });
      } else if (value.includes('infantiles') || value.includes('adultos')) {
        categorias[1].opciones.push({
          ...opcion,
          icon: 'bi-people',
          description: this.getDescription(opcion.value)
        });
      } else if (value.includes('deuda') || value.includes('mensualidad')) {
        categorias[2].opciones.push({
          ...opcion,
          icon: 'bi-currency-euro',
          description: this.getDescription(opcion.value)
        });
      } else {
        categorias[3].opciones.push({
          ...opcion,
          icon: 'bi-file-text',
          description: this.getDescription(opcion.value)
        });
      }
    });

    // Only include categories that have reports
    this.categoriasInforme = categorias.filter(cat => cat.opciones.length > 0);
  }

  /**
   * Get description for each report type
   */
  getDescription(value: string): string {
    const descriptions: { [key: string]: string } = {
      'general': 'Listado completo de alumnos organizados por cinturón',
      'taekwondo': 'Alumnos de Taekwondo por nivel de grado',
      'kickboxing': 'Alumnos de Kickboxing por nivel de grado',
      'licencias': 'Estado actual de todas las licencias deportivas',
      'infantiles': 'Alumnos menores aptos para promoción de grado',
      'adultos': 'Alumnos adultos aptos para promoción de grado',
      'deudas': 'Resumen de pagos pendientes y deudas',
      'mensualidades': 'Estado de mensualidades de todos los alumnos',
      'mensualidades-taekwondo': 'Mensualidades específicas de Taekwondo',
      'mensualidades-kickboxing': 'Mensualidades específicas de Kickboxing'
    };
    return descriptions[value] || '';
  }

  cerrarModal(): void {
    this.modalVisible = false;
    setTimeout(() => {
      this.cerrar.emit();
    }, 300);
  }

  generarInforme(): void {
    this.informeSeleccionado.emit(this.selectedInforme);
    this.cerrarModal();
  }

  /**
   * Select a report
   */
  seleccionarInforme(value: string): void {
    this.selectedInforme = value;
  }
}
