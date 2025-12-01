import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';
import Swal from 'sweetalert2';

import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';

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
    this.authService.obtenerNombreUsuario().subscribe((nombre) => {
      if (nombre && !sessionStorage.getItem('welcomeShown')) {
        Swal.fire({
          title: 'Inicio de sesión exitoso',
          text: `¡Bienvenido/a, ${nombre}!`,
          icon: 'success',
          timer: 2000,
        });
        sessionStorage.setItem('welcomeShown', 'true');
      }
    });

    this.authService.obtenerTodosLosAlumnos().subscribe({
      next: (alumnos) => {
        if (alumnos && alumnos.length > 0) {
          this.alumnos = alumnos;
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
      error: () => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener los alumnos.',
          icon: 'error',
        });
      },
    });
  }

  seleccionarAlumno(alumno: any): void {
    this.selectedAlumno = alumno;
    this.cargarGruposDelAlumno(alumno.id);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  cargarGruposDelAlumno(alumnoId: number): void {
    const gruposSubscription = this.endpointsService.gruposDelAlumno$.subscribe({
      next: (grupos) => {
        this.grupos = grupos;
      },
      error: () => {
        Swal.fire({
          title: 'Error en la petición',
          text: 'Error al obtener los grupos del alumno.',
          icon: 'error',
        });
      },
    });

    this.subscriptions.add(gruposSubscription);
    this.endpointsService.obtenerGruposDelAlumno(alumnoId);
  }

  obtenerClaseGrupo(grupo: any): string {
    const deporte = (grupo?.deporte || '').toUpperCase();
    switch (deporte) {
      case 'TAEKWONDO':
        return 'taekwondo';
      case 'KICKBOXING':
        return 'kickboxing';
      case 'PILATES':
        return 'pilates';
      case 'DEFENSA_PERSONAL_FEMENINA':
        return 'defensa_personal_femenina';
      case 'COMPETICION':
        return 'competicion';
      default:
        return 'otro';
    }
  }

  obtenerIconoDeporte(grupo: any): string {
    const deporte = (grupo?.deporte || '').toUpperCase();
    switch (deporte) {
      case 'TAEKWONDO':
        return 'bi bi-shield-shaded';
      case 'KICKBOXING':
        return 'bi bi-lightning-charge-fill';
      case 'PILATES':
        return 'bi bi-peace-fill';
      case 'DEFENSA_PERSONAL_FEMENINA':
        return 'bi bi-shield-lock-fill';
      case 'COMPETICION':
        return 'bi bi-trophy-fill';
      default:
        return 'bi bi-star-fill';
    }
  }

  obtenerNombreDeporte(grupo: any): string {
    const deporte = (grupo?.deporte || '').toUpperCase();
    switch (deporte) {
      case 'TAEKWONDO':
        return 'Taekwondo';
      case 'KICKBOXING':
        return 'Kickboxing';
      case 'PILATES':
        return 'Pilates';
      case 'DEFENSA_PERSONAL_FEMENINA':
        return 'Defensa Personal Femenina';
      case 'COMPETICION':
        return 'Competición';
      default:
        return 'Otro';
    }
  }
}
