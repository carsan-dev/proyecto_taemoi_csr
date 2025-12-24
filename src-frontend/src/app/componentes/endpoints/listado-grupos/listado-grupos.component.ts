import { Component, OnInit, OnDestroy } from '@angular/core';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { showSuccessToast, showErrorToast } from '../../../utils/toast.util';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { GrupoAlumnosModalComponent } from '../../generales/grupo-alumnos-modal/grupo-alumnos-modal.component';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { finalize, filter, take } from 'rxjs/operators';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Component({
  selector: 'app-listado-grupos',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, GrupoAlumnosModalComponent, SkeletonCardComponent],
  templateUrl: './listado-grupos.component.html',
  styleUrl: './listado-grupos.component.scss',
})
export class ListadoGruposComponent implements OnInit, OnDestroy {
  grupos: any[] = [];
  cargando: boolean = true; // Local loading state
  cargandoConteo: boolean = true; // Loading state for alumn count
  gruposMostrar: string[] = [
    'Taekwondo',
    'Taekwondo Competición',
    'Kickboxing',
  ];

  // Variables para controlar el modal
  mostrarModalAlumnos = false;
  grupoNombreModal = '';
  alumnosGrupoModal: any[] = [];
  cargandoAlumnosModal: boolean = false;

  // Turnos por grupo
  turnosPorGrupo: Map<number, any[]> = new Map();

  // Turnos sin grupo asignado
  turnosSinGrupo: any[] = [];

  private conteoSubscription?: Subscription;

  constructor(private readonly endpointsService: EndpointsService) {}

  ngOnInit(): void {
    this.obtenerGrupos();
    this.cargarConteoAlumnos();
  }

  ngOnDestroy(): void {
    this.conteoSubscription?.unsubscribe();
  }

  private cargarConteoAlumnos(): void {
    this.cargandoConteo = true;
    this.endpointsService.obtenerConteoAlumnosPorGrupo();
    // Subscribe to the conteo observable to detect when data arrives
    this.conteoSubscription = this.endpointsService.conteoAlumnosPorGrupo$
      .pipe(
        filter(conteo => Object.keys(conteo).length > 0),
        take(1)
      )
      .subscribe(() => {
        this.cargandoConteo = false;
      });
  }

  obtenerGrupos(): void {
    this.cargando = true;
    this.endpointsService.obtenerTodosLosGrupos()
      .subscribe({
        next: (response) => {
          this.grupos = response;
          this.cargarTurnosPorGrupo();
        },
        error: () => {
          this.cargando = false;
          Swal.fire({
            title: 'Error en la petición',
            text: 'No hemos podido conectar con el servidor',
            icon: 'error',
          });
        },
      });
  }

  cargarTurnosPorGrupo(): void {
    if (this.grupos.length === 0) {
      this.cargarTurnosSinGrupo();
      return;
    }

    const requests = this.grupos.map(grupo =>
      this.endpointsService.obtenerTurnosDelGrupoPorId(grupo.id).pipe(
        catchError(() => of([]))
      )
    );

    forkJoin(requests)
      .subscribe({
        next: (results) => {
          results.forEach((turnos, index) => {
            this.turnosPorGrupo.set(this.grupos[index].id, turnos || []);
          });
          this.cargarTurnosSinGrupo();
        },
      });
  }

  cargarTurnosSinGrupo(): void {
    this.endpointsService.obtenerTodosLosTurnos()
      .pipe(finalize(() => (this.cargando = false)))
      .subscribe({
        next: (turnos) => {
          // Filter turnos that don't have a grupo assigned
          this.turnosSinGrupo = turnos.filter(t => !t.grupoId && !t.grupoNombre);
        },
        error: () => {
          this.cargando = false;
        }
      });
  }

  asignarTurnoAGrupo(grupoId: number, turnoId: string): void {
    if (!turnoId) return;

    const turnoIdNum = +turnoId;
    this.endpointsService.agregarTurnoAGrupo(grupoId, turnoIdNum).subscribe({
      next: () => {
        showSuccessToast('Turno asignado al grupo');
        // Move turno from unassigned to this grupo's list
        const turno = this.turnosSinGrupo.find(t => t.id === turnoIdNum);
        if (turno) {
          this.turnosSinGrupo = this.turnosSinGrupo.filter(t => t.id !== turnoIdNum);
          const turnosGrupo = this.turnosPorGrupo.get(grupoId) || [];
          turnosGrupo.push(turno);
          this.turnosPorGrupo.set(grupoId, turnosGrupo);
        }
      },
      error: () => {
        showErrorToast('No se pudo asignar el turno al grupo');
      },
    });
  }

  getTurnosDelGrupo(grupoId: number): any[] {
    return this.turnosPorGrupo.get(grupoId) || [];
  }

  eliminarTurnoDelGrupo(grupoId: number, turnoId: number): void {
    Swal.fire({
      title: '¿Eliminar turno del grupo?',
      text: 'El turno seguirá existiendo pero sin grupo asignado',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Sí, eliminar',
      cancelButtonText: 'Cancelar',
    }).then((result) => {
      if (result.isConfirmed) {
        this.endpointsService.eliminarTurnoDeGrupo(grupoId, turnoId).subscribe({
          next: () => {
            showSuccessToast('Turno eliminado del grupo');
            const turnos = this.turnosPorGrupo.get(grupoId) || [];
            this.turnosPorGrupo.set(grupoId, turnos.filter(t => t.id !== turnoId));
          },
          error: () => {
            showErrorToast('No se pudo eliminar el turno');
          },
        });
      }
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
            showSuccessToast('Grupo eliminado correctamente');
            this.obtenerGrupos();
          },
          error: () => {
            showErrorToast('No se pudo eliminar el grupo');
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
    // Show modal immediately with loading state
    this.grupoNombreModal = tipo;
    this.alumnosGrupoModal = [];
    this.cargandoAlumnosModal = true;
    this.mostrarModalAlumnos = true;

    // Fetch alumnos data
    this.endpointsService.obtenerAlumnosPorTipo(tipo)
      .pipe(finalize(() => (this.cargandoAlumnosModal = false)))
      .subscribe({
        next: (alumnos) => {
          this.alumnosGrupoModal = alumnos;
        },
        error: (error) => {
          this.mostrarModalAlumnos = false;
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
