import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-grupo-alumnos-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './grupo-alumnos-modal.component.html',
  styleUrl: './grupo-alumnos-modal.component.scss'
})
export class GrupoAlumnosModalComponent {
  @Input() grupoNombre!: string;
  @Input() alumnos!: any[];
  @Output() cerrar = new EventEmitter<void>();

  modalVisible = false;

  ngOnInit(): void {
    setTimeout(() => {
      this.modalVisible = true;
    }, 0);
  }

  cerrarModal() {
    this.modalVisible = false;
    setTimeout(() => {
      this.cerrar.emit();
    }, 300); // Puedes ajustar el tiempo si tienes animaciones
  }
}
