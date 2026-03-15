import { CommonModule, Location } from '@angular/common';
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { Router } from '@angular/router';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';

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
      showErrorToast('Formulario inválido. Completa los campos requeridos.');
      return;
    }

    const nuevoProducto = this.productoForm.value;

    this.endpointsService.crearProducto(nuevoProducto).subscribe({
      next: () => {
        showSuccessToast('Producto creado correctamente');
        this.router.navigate(['/productosListar']);
      },
      error: () => {
        showErrorToast('No se pudo crear el producto');
      },
    });
  }


  volver() {
    this.location.back();
  }
}
