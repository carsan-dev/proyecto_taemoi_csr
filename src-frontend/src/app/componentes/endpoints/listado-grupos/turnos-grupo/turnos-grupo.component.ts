import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-turnos-grupo',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turnos-grupo.component.html',
  styleUrl: './turnos-grupo.component.scss'
})
export class TurnosGrupoComponent implements OnInit {
  grupoId!: number;
  turnos: any[] = [];

  constructor(
    private route: ActivatedRoute,
    private endpointsService: EndpointsService
  ) { }

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
            Swal.fire({
              title: 'No encontrado',
              text: 'No se han encontrado turnos para ese grupo',
              icon: 'error',
            });
          },
        });
    }
  }

  eliminarTurnoDelGrupo(turnoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: "¡No podrás revertir esto!",
      icon: 'warning',
      showCancelButton: true,
      confirmButtonColor: '#3085d6',
      cancelButtonColor: '#d33',
      confirmButtonText: 'Sí, eliminarlo',
      cancelButtonText: 'Cancelar'
    }).then((result) => {
      if (result.isConfirmed) {
        const token = localStorage.getItem('token');

        if (token) {
          this.endpointsService
            .eliminarTurnoDeGrupo(this.grupoId, turnoId, token)
            .subscribe({
              next: (response) => {
                Swal.fire({
                  title: 'Bien',
                  text: '¡Turno eliminado correctamente del grupo!',
                  icon: 'success',
                });
                this.obtenerTurnos();
              },
              error: (error) => {
                Swal.fire({
                  title: 'Error',
                  text: 'No hemos podido eliminar el turno, ' + error,
                  icon: 'error',
                });
              },
            });
        }
      }
    });
  }

}
