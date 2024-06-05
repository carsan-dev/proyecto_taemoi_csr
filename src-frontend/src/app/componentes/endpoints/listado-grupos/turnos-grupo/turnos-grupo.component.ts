import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule, Location } from '@angular/common';

@Component({
  selector: 'app-turnos-grupo',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turnos-grupo.component.html',
  styleUrl: './turnos-grupo.component.scss',
})
export class TurnosGrupoComponent implements OnInit {
  grupoId!: number;
  turnos: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private endpointsService: EndpointsService,
    private location: Location
  ) {}

  ngOnInit(): void {
    if (typeof localStorage !== 'undefined') {
      this.obtenerGrupoId();
      this.obtenerTurnos();
    }
  }

  obtenerGrupoId() {
    const id = this.route.snapshot.paramMap.get('id');

    if (id) {
      this.grupoId = +id;
    } else {
      this.grupoId = 0;
    }
  }

  obtenerTurnos() {
    const token = localStorage.getItem('token');

    if (token) {
      this.endpointsService
        .obtenerTurnosDelGrupo(this.grupoId, token)
        .subscribe({
          next: (response) => {
            this.turnos = response;
          },
          error: (error) => {
            let errorMessage = '';
            if (error.status === 404) {
              errorMessage += 'Este grupo no tiene turnos asociados.';
            } else {
              errorMessage += 'Ha ocurrido un error.';
            }
            Swal.fire({
              title: 'Error',
              text: errorMessage,
              icon: 'error',
            });
          },
        });
    }
  }

  eliminarTurnoDelGrupo(turnoId: number) {
    const token = localStorage.getItem('token');
    if (token) {
      Swal.fire({
        title: '¿Estás seguro?',
        text: '¡No podrás revertir esto!',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminarlo',
        cancelButtonText: 'Cancelar',
      }).then((result) => {
        if (result.isConfirmed) {
          this.endpointsService
            .eliminarTurnoDeGrupo(this.grupoId, turnoId, token)
            .subscribe({
              next: (response) => {
                Swal.fire({
                  title: 'Bien',
                  text: '¡Turno eliminado correctamente del grupo!',
                  icon: 'success',
                }).then(() => {
                  this.actualizarTurnos();
                });
              },
              error: (error) => {
                Swal.fire({
                  title: 'Error',
                  text: 'No hemos podido eliminar el turno.',
                  icon: 'error',
                });
              },
            });
        }
      });
    }
  }

  actualizarTurnos() {
    const token = localStorage.getItem('token');
    if (token) {
      this.endpointsService
        .obtenerTurnosDelGrupo(this.grupoId, token)
        .subscribe({
          next: (response) => {
            this.turnos = response;
            if (this.turnos.length === 0) {
              Swal.fire({
                title: 'Advertencia',
                text: 'No quedan más turnos en el grupo.',
                icon: 'warning',
              });
            }
          },
          error: (error) => {
            if (error.status === 404) {
              this.turnos = [];
              Swal.fire({
                title: 'Advertencia',
                text: 'No quedan más turnos en el grupo.',
                icon: 'warning',
              });
            } else {
              Swal.fire({
                title: 'Error',
                text: 'Ha ocurrido un error al obtener los turnos.',
                icon: 'error',
              });
            }
          },
        });
    }
  }

  volver() {
    this.location.back();
  }
}
