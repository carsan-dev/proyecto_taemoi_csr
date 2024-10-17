import { Component, OnInit } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-listado-grupos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './listado-grupos.component.html',
  styleUrl: './listado-grupos.component.scss',
})
export class ListadoGruposComponent implements OnInit {
  grupos: any[] = [];

  constructor(private endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerGrupos();
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
            Swal.fire('Eliminado!', 'El grupo ha sido eliminado.', 'success');
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
}
