import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule, Location } from '@angular/common';
import { Subscription } from 'rxjs/internal/Subscription';

@Component({
  selector: 'app-turnos-grupo',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turnos-grupo.component.html',
  styleUrl: './turnos-grupo.component.scss',
})
export class TurnosGrupoComponent implements OnInit, OnDestroy {
  grupoId!: number;
  turnos: any[] = [];
  private readonly subscriptions: Subscription = new Subscription();

  constructor(
    private readonly route: ActivatedRoute,
    public endpointsService: EndpointsService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    this.obtenerGrupoId();
    this.obtenerTurnos();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
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
    // Suscribirse al Observable expuesto por el BehaviorSubject
    const turnosSubscription = this.endpointsService.turnosDelGrupo$.subscribe({
      next: (turnos) => {
        this.turnos = turnos;
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

    this.subscriptions.add(turnosSubscription);

    // Llamar al método que inicia la carga de datos
    this.endpointsService.obtenerTurnosDelGrupo(this.grupoId);
  }

  eliminarTurnoDelGrupo(turnoId: number) {
    Swal.fire({
      title: '¿Estás seguro?',
      text: '¡No podrás revertir esto!',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminarlo',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarTurnoDeGrupo(this.grupoId, turnoId).subscribe({
          next: () => {
            Swal.fire({
              title: 'Bien',
              text: '¡Turno eliminado correctamente del grupo!',
              icon: 'success',
            }).then(() => {
              // Llamar a actualizarTurnos para recargar los datos
              this.actualizarTurnos();
            });
          },
          error: () => {
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
  

  actualizarTurnos() {
    // Suscribirse al Observable expuesto por el BehaviorSubject
    const turnosSubscription = this.endpointsService.turnosDelGrupo$.subscribe({
      next: (turnos) => {
        this.turnos = turnos;
        if (this.turnos.length === 0) {
          Swal.fire({
            title: 'Advertencia',
            text: 'No quedan más turnos en el grupo.',
            icon: 'warning',
          });
        }
      },
      error: (error) => {
        // Aunque es poco probable que el Observable emita un error aquí,
        // puedes manejarlo si es necesario
      },
    });

    this.subscriptions.add(turnosSubscription);

    // Llamar al método para iniciar la carga de datos
    this.endpointsService.obtenerTurnosDelGrupo(this.grupoId);
  }

  volver() {
    this.location.back();
  }
}
