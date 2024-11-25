import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-productos-alumno-notas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './productos-alumno-notas.component.html',
  styleUrl: './productos-alumno-notas.component.scss'
})
export class ProductosAlumnoNotasComponent {
  @Input() productoAlumno!: ProductoAlumnoDTO;
  @Output() cerrar = new EventEmitter<void>();
  @Output() guardar = new EventEmitter<ProductoAlumnoDTO>();

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
    }, 300);
  }

  guardarNotas() {
    this.guardar.emit(this.productoAlumno);
  }
}
