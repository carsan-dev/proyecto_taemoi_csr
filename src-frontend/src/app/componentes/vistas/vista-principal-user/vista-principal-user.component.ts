import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import Swal from 'sweetalert2';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';

@Component({
  selector: 'app-vista-principal-user',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './vista-principal-user.component.html',
  styleUrl: './vista-principal-user.component.scss',
})
export class VistaPrincipalUserComponent implements OnInit, OnDestroy {
  alumnos: any[] = [];
  selectedAlumno: any = null;
  grupos: any[] = [];
  private readonly subscriptions: Subscription = new Subscription();

  constructor(
    public endpointsService: EndpointsService,
    private readonly authService: AuthenticationService
  ) {}

  ngOnInit() {
    // Cargar todos los alumnos asociados al email del usuario
    this.authService.obtenerTodosLosAlumnos().subscribe({
      next: (alumnos) => {
        if (alumnos && alumnos.length > 0) {
          this.alumnos = alumnos;
          // Seleccionar el primer alumno por defecto
          this.selectedAlumno = alumnos[0];
          this.cargarGruposDelAlumno(this.selectedAlumno.id);
        } else {
          Swal.fire({
            title: 'Error',
            text: 'No se encontraron alumnos asociados a este usuario.',
            icon: 'error',
          });
        }
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener los alumnos.',
          icon: 'error',
        });
      },
    });
  }

  // Método para cambiar el alumno seleccionado
  seleccionarAlumno(alumno: any): void {
    this.selectedAlumno = alumno;
    this.cargarGruposDelAlumno(alumno.id);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  cargarGruposDelAlumno(alumnoId: number): void {
    // Suscribirse al Observable expuesto por el BehaviorSubject
    const gruposSubscription = this.endpointsService.gruposDelAlumno$.subscribe({
      next: (grupos) => {
        this.grupos = grupos;
      },
      error: (error) => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener los grupos del alumno.',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(gruposSubscription);

    // Llamar al método que inicia la carga de datos
    this.endpointsService.obtenerGruposDelAlumno(alumnoId);
  }

  obtenerClaseGrupo(grupoId: number): string {
    switch (grupoId) {
      case 1:
      case 2:
      case 3:
      case 4:
      case 5:
      case 6:
        return 'taekwondo';
      case 7:
        return 'competicion';
      case 8:
        return 'pilates';
      case 9:
        return 'kickboxing';
      case 10:
        return 'defensa_personal_femenina';
      default:
        return ''; // Clase por defecto
    }
  }

  obtenerEmoticonoCategoria(grupoId: number): string {
    switch (grupoId) {
      case 1:
      case 2:
      case 3:
      case 4:
      case 5:
      case 6:
        return '🥋';
      case 7:
        return '🥋';
      case 8:
        return '🧘‍♀️';
      case 9:
        return '🥊';
      case 10:
        return '🛡️';
      default:
        return '❓';
    }
  }
}