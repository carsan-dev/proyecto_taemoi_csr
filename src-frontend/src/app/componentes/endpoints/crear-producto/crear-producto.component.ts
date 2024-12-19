import { CommonModule, Location } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { Router } from '@angular/router';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-crear-producto',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './crear-producto.component.html',
  styleUrl: './crear-producto.component.scss'
})
export class CrearProductoComponent {
  productoForm: FormGroup;

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly router: Router,
    private readonly location: Location
  ) {
    this.productoForm = this.fb.group({
      concepto: ['', Validators.required],
      precio: ['', [Validators.required, Validators.min(0)]],
    });
  }

  onSubmit(): void {
    if (this.productoForm.invalid) {
      Swal.fire({
        title: 'Formulario inválido',
        text: 'Por favor, complete todos los campos requeridos correctamente',
        icon: 'error',
      });
      return;
    }

    const nuevoProducto = this.productoForm.value;

    this.endpointsService.crearProducto(nuevoProducto).subscribe({
      next: (productoCreado) => {
        Swal.fire({
          title: '¡Producto creado!',
          text: 'El producto se ha creado exitosamente',
          icon: 'success',
          timer: 2000,
        }).then(() => {
          this.router.navigate(['/productosListar']);
        });
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo crear el producto',
          icon: 'error',
        });
      },
    });
  }

  volver() {
    this.location.back();
  }
}
