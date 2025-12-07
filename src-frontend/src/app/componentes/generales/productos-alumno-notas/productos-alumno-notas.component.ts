import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { ProductoAlumnoDTO } from '../../../interfaces/producto-alumno-dto';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-productos-alumno-notas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './productos-alumno-notas.component.html',
  styleUrls: ['./productos-alumno-notas.component.scss']
})
export class ProductosAlumnoNotasComponent implements OnInit{
  @Input() productoAlumno!: ProductoAlumnoDTO;
  @Output() cerrar = new EventEmitter<void>();
  @Output() guardar = new EventEmitter<ProductoAlumnoDTO>();

  modalVisible = false;
  modoEdicion = false;

  ngOnInit(): void {
    setTimeout(() => {
      this.modalVisible = true;
    }, 0);
  }

  cerrarModal() {
    this.modalVisible = false;
    setTimeout(() => {
      this.cerrar.emit();
      this.modoEdicion = false;
    }, 300);
  }

  habilitarEdicion() {
    this.modoEdicion = true;
  }

  guardarNotas() {
    this.guardar.emit(this.productoAlumno);
    this.modoEdicion = false;
    this.cerrarModal();
  }
}
