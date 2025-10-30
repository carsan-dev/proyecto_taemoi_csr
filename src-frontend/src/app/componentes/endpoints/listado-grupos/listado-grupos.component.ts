import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { GrupoAlumnosModalComponent } from '../../generales/grupo-alumnos-modal/grupo-alumnos-modal.component';

@Component({
  selector: 'app-listado-grupos',
  standalone: true,
  imports: [CommonModule, RouterModule, GrupoAlumnosModalComponent],
  templateUrl: './listado-grupos.component.html',
  styleUrl: './listado-grupos.component.scss',
})
export class ListadoGruposComponent implements OnInit {
  grupos: any[] = [];
  gruposMostrar: string[] = [
    'Taekwondo',
    'Taekwondo Competición',
    'Pilates',
    'Kickboxing',
    'Defensa Personal Femenina',
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
    this.endpointsService.obtenerTodosLosGrupos().subscribe({
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

  obtenerConteoAlumnos(grupoNombre: string): number {
    return this.endpointsService.conteoAlumnosPorGrupo[grupoNombre] || 0;
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
