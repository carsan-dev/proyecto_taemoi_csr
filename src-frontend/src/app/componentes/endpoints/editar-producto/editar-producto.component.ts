import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { ActivatedRoute, Router } from '@angular/router';
import Swal from 'sweetalert2';
import { CommonModule, Location } from '@angular/common';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-editar-producto',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, SkeletonCardComponent],
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
        error: (error) => {
          this.cargando = false;
          Swal.fire({
            title: 'Error',
            text: 'No se pudo cargar el producto',
            icon: 'error',
          });
          this.router.navigate(['/productosListar']);
        },
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

    const productoActualizado = this.productoForm.value;

    this.endpointsService.actualizarProducto(this.productoId, productoActualizado).subscribe({
      next: (producto) => {
        Swal.fire({
          title: '¡Producto actualizado!',
          text: 'El producto se ha actualizado exitosamente',
          icon: 'success',
          timer: 2000,
        }).then(() => {
          this.router.navigate(['/productosListar']);
        });
      },
      error: (error) => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo actualizar el producto',
          icon: 'error',
        });
      },
    });
  }

  volver() {
    this.location.back();
  }
}
