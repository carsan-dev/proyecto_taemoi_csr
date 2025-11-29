import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EndpointsService } from '../../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { CommonModule, Location } from '@angular/common';
import { Subscription } from 'rxjs/internal/Subscription';
import { skip } from 'rxjs/operators';
import { SkeletonCardComponent } from '../../../generales/skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-turnos-grupo',
  standalone: true,
  imports: [CommonModule, SkeletonCardComponent],
  templateUrl: './turnos-grupo.component.html',
  styleUrl: './turnos-grupo.component.scss',
})
export class TurnosGrupoComponent implements OnInit, OnDestroy {
  grupoId!: number;
  turnos: any[] = [];
  cargando: boolean = true; // Loading state
  private readonly subscriptions: Subscription = new Subscription();

  constructor(
    private readonly route: ActivatedRoute,
    public endpointsService: EndpointsService,
    private readonly location: Location
  ) {}

  ngOnInit(): void {
    this.obtenerGrupoId();
    this.setupSubscription();
    this.obtenerTurnos();
  }

  setupSubscription(): void {
    // Subscribe once and skip the initial cached value
    const turnosSubscription = this.endpointsService.turnosDelGrupo$
      .pipe(skip(1))
      .subscribe({
        next: (turnos) => {
          this.turnos = turnos;
          this.cargando = false;
        },
        error: (error) => {
          this.cargando = false;
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
    this.cargando = true;
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
        this.endpointsService
          .eliminarTurnoDeGrupo(this.grupoId, turnoId)
          .subscribe({
            next: () => {
              Swal.fire({
                title: 'Bien',
                text: '¡Turno eliminado correctamente del grupo!',
                icon: 'success',
                timer: 2000,
              }).then(() => {
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
    this.endpointsService.obtenerTurnosDelGrupo(this.grupoId);
  }

  volver() {
    this.location.back();
  }
}
