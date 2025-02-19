import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-informe-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './informe-modal.component.html',
  styleUrl: './informe-modal.component.scss'
})
export class InformeModalComponent {
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
