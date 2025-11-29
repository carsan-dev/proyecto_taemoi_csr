import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { GrupoAlumnosModalComponent } from '../../generales/grupo-alumnos-modal/grupo-alumnos-modal.component';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { finalize } from 'rxjs/operators';

@Component({
  selector: 'app-listado-grupos',
  standalone: true,
  imports: [CommonModule, RouterModule, GrupoAlumnosModalComponent, SkeletonCardComponent],
  templateUrl: './listado-grupos.component.html',
  styleUrl: './listado-grupos.component.scss',
})
export class ListadoGruposComponent implements OnInit {
  grupos: any[] = [];
  cargando: boolean = false; // Local loading state
  gruposMostrar: string[] = [
    'Taekwondo',
    'Taekwondo Competición',
    'Kickboxing',
  ];

  // Variables para controlar el modal
  mostrarModalAlumnos = false;
  grupoNombreModal = '';
  alumnosGrupoModal: any[] = [];

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerGrupos();
    this.endpointsService.obtenerConteoAlumnosPorGrupo();
  }

  obtenerGrupos(): void {
    this.cargando = true;
    this.endpointsService.obtenerTodosLosGrupos()
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (response) => {
          this.grupos = response;
        },
        error: (error) => {
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
  }

  eliminarGrupo(id: number): void {
    Swal.fire({
      title: '¿Estás seguro?',
      text: 'No podrás revertir esto',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, eliminarlo',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarGrupo(id).subscribe({
          next: () => {
            Swal.fire({
              title: '¡Eliminado!',
              text: 'El grupo ha sido eliminado correctamente.',
              icon: 'success',
              timer: 2000,
            });
            this.obtenerGrupos();
          },
          error: (error) => {
            Swal.fire({
              title: 'Error en la petición',
              text: 'No hemos podido eliminar el grupo',
              icon: 'error',
            });
          },
        });
      }
    });
  }

  obtenerConteoAlumnos(grupoNombre: string): { active: number; total: number } {
    const conteo = this.endpointsService.conteoAlumnosPorGrupo[grupoNombre];
    if (!conteo) {
      return { active: 0, total: 0 };
    }

    return {
      active: conteo['active'] || 0,
      total: conteo['total'] || 0
    };
  }

  abrirModalAlumnos(tipo: string) {
    this.endpointsService.obtenerAlumnosPorTipo(tipo).subscribe({
      next: (alumnos) => {
        this.grupoNombreModal = tipo;
        this.alumnosGrupoModal = alumnos;
        this.mostrarModalAlumnos = true;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la obtención',
          text: 'No hemos podido obtener los alumnos del grupo',
          icon: 'error',
        });
      }
    });
  }

  cerrarModalAlumnos() {
    this.mostrarModalAlumnos = false;
  }
}
