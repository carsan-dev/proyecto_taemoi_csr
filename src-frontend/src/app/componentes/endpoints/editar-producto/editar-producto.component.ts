import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { ActivatedRoute, Router } from '@angular/router';
import { showErrorToast, showSuccessToast } from '../../../utils/toast.util';
import { CommonModule, Location } from '@angular/common';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-editar-producto',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './editar-producto.component.html',
  styleUrl: './editar-producto.component.scss'
})
export class EditarProductoComponent implements OnInit {
  productoForm: FormGroup;
  productoId: number;
  cargando: boolean = true;

  constructor(
    private readonly fb: FormBuilder,
    private readonly endpointsService: EndpointsService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly location: Location
  ) {
    this.productoForm = this.fb.group({
      concepto: ['', Validators.required],
      precio: ['', [Validators.required, Validators.min(0)]],
      // Agrega otros campos si es necesario
    });
    this.productoId = +this.route.snapshot.params['id'];
  }

  ngOnInit(): void {
    this.cargarProducto();
  }

  cargarProducto(): void {
    this.cargando = true;
    this.endpointsService.obtenerProductoPorId(this.productoId)
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (producto) => {
          this.productoForm.patchValue(producto);
        },
        error: () => {
          this.cargando = false;
          showErrorToast('No se pudo cargar el producto');
          this.router.navigate(['/productosListar']);
        },
      });
  }

  onSubmit(): void {
    if (this.productoForm.invalid) {
      showErrorToast('Formulario inválido. Completa los campos requeridos.');
      return;
    }

    const productoActualizado = this.productoForm.value;

    this.endpointsService.actualizarProducto(this.productoId, productoActualizado).subscribe({
      next: () => {
        showSuccessToast('Producto actualizado correctamente');
        this.router.navigate(['/productosListar']);
      },
      error: () => {
        showErrorToast('No se pudo actualizar el producto');
      },
    });
  }


  volver() {
    this.location.back();
  }
}
