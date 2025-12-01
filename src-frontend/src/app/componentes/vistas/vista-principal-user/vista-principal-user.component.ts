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

  private normalizarDeporte(grupo: any): string {
    return (grupo?.deporte || grupo?.tipoGrupo || '').toString().toLowerCase().trim();
  }

  obtenerEtiquetaColor(grupo: any): string {
    const deporte = this.normalizarDeporte(grupo);
    if (deporte.includes('competici')) return 'Taekwondo Competición';
    if (deporte.includes('taekwondo')) return 'Taekwondo';
    if (deporte.includes('kickboxing')) return 'Kickboxing';
    if (deporte.includes('pilates')) return 'Pilates';
    if (deporte.includes('defensa personal')) return 'Defensa Personal Femenina';
    return 'Otro';
  }

  obtenerClaseGrupo(grupo: any): string {
    const etiqueta = this.obtenerEtiquetaColor(grupo);
    return etiqueta.toLowerCase().replace(/[^a-z0-9_ ]/gi, '').replace(/\s+/g, '_');
  }

  obtenerIconoDeporte(grupo: any): string {
    const etiqueta = this.obtenerEtiquetaColor(grupo).toLowerCase();
    if (etiqueta.includes('competición')) return 'bi bi-trophy-fill';
    if (etiqueta.includes('taekwondo')) return 'bi bi-shield-shaded';
    if (etiqueta.includes('kickboxing')) return 'bi bi-lightning-charge-fill';
    if (etiqueta.includes('pilates')) return 'bi bi-peace-fill';
    if (etiqueta.includes('defensa personal')) return 'bi bi-shield-lock-fill';
    return 'bi bi-star-fill';
  }

  obtenerNombreDeporte(grupo: any): string {
    const etiqueta = this.obtenerEtiquetaColor(grupo);
    if (etiqueta === 'Defensa Personal Femenina') return 'D.P. Femenina';
    return etiqueta;
  }
}
