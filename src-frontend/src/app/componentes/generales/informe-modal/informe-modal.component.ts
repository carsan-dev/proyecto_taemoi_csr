import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-informe-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './informe-modal.component.html',
  styleUrl: './informe-modal.component.scss'
})
export class InformeModalComponent {
  @Input() title: string = 'Generar Informe';
  @Input() opcionesInforme: Array<{ value: string, label: string }> = [
    { value: 'general', label: 'Informe General' },
    { value: 'taekwondo', label: 'Informe Taekwondo por Grado' },
    { value: 'kickboxing', label: 'Informe Kickboxing por Grado' },
    { value: 'licencias', label: 'Informe Estado de Licencias' },
    { value: 'infantiles', label: 'Informe Alumnos Infantiles Promoción' }
  ];

  modalVisible = false;
  selectedInforme: string = '';

  @Output() cerrar = new EventEmitter<void>();
  @Output() informeSeleccionado = new EventEmitter<string>();

  ngOnInit(): void {
    setTimeout(() => {
      this.modalVisible = true;
    }, 0);
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
}
