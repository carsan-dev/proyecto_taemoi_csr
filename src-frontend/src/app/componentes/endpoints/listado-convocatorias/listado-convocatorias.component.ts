import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { formatDate } from '../../../utilities/formatear-fecha';

@Component({
  selector: 'app-listado-convocatorias',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './listado-convocatorias.component.html',
  styleUrl: './listado-convocatorias.component.scss',
})
export class ListadoConvocatoriasComponent implements OnInit {
  convocatorias: any[] = [];
  convocatoriaSeleccionada: any;
  alumnosInscritos: any[] = [];
  deportes = ['TAEKWONDO', 'KICKBOXING'];
  deporteSeleccionado = 'TAEKWONDO';

  constructor(
    private readonly endpointsService: EndpointsService,
  ) {}

  ngOnInit(): void {
    this.obtenerConvocatorias();
  }

  obtenerConvocatorias(): void {
    this.endpointsService
      .obtenerConvocatorias(this.deporteSeleccionado)
      .subscribe({
        next: (data) => {
          this.convocatorias = data;
        },
        error: (error) => {
          console.error('Error al obtener las convocatorias:', error);
        },
      });
  }

  crearConvocatoria(): void {
    const nuevaConvocatoria = {
      fechaConvocatoria: new Date(),
      deporte: this.deporteSeleccionado,
    };
    this.endpointsService.crearConvocatoria(nuevaConvocatoria).subscribe({
      next: (data) => {
        this.convocatorias.push(data);
      },
      error: (error) => {
        console.error('Error al crear la convocatoria:', error);
      },
    });
  }

  seleccionarConvocatoria(convocatoria: any): void {
    this.convocatoriaSeleccionada = convocatoria;

    this.endpointsService
      .obtenerAlumnosDeConvocatoria(convocatoria.id)
      .subscribe({
        next: (data) => {
          this.alumnosInscritos = data;
        },
        error: (error) => {
          console.error('Error al obtener alumnos de la convocatoria:', error);
        },
      });
  }

  eliminarConvocatoria(convocatoria: any): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: `Vas a eliminar la convocatoria del ${formatDate(
        convocatoria.fechaConvocatoria
      )}. Esta acción no se puede deshacer.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#d33',
      cancelButtonColor: '#3085d6',
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarConvocatoria(convocatoria.id).subscribe({
          next: () => {
            Swal.fire(
              '¡Eliminado!',
              `La convocatoria del ${formatDate(
                convocatoria.fechaConvocatoria
              )} ha sido eliminada.`,
              'success'
            );
            this.convocatorias = this.convocatorias.filter(
              (c) => c.id !== convocatoria.id
            );

            if (this.convocatoriaSeleccionada?.id === convocatoria.id) {
              this.convocatoriaSeleccionada = null;
              this.alumnosInscritos = [];
            }
          },
          error: (error) => {
            console.error('Error al eliminar la convocatoria:', error);
            Swal.fire(
              'Error',
              'Hubo un problema al eliminar la convocatoria. Por favor, intenta de nuevo.',
              'error'
            );
          },
        });
      }
    });
  }
}
