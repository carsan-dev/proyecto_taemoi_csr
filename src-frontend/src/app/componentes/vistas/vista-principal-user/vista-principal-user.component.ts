import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';
import Swal from 'sweetalert2';

import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { getDeporteLabel } from '../../../enums/deporte';
import { getGradoTextStyle } from '../../../utilities/grado-colors';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-vista-principal-user',
  standalone: true,
  imports: [CommonModule, RouterModule, SkeletonCardComponent],
  templateUrl: './vista-principal-user.component.html',
  styleUrl: './vista-principal-user.component.scss',
})
export class VistaPrincipalUserComponent implements OnInit, OnDestroy {
  alumnos: any[] = [];
  selectedAlumno: any = null;
  grupos: any[] = [];
  deportesDelAlumno: AlumnoDeporteDTO[] = [];
  cargandoDeportes: boolean = false;
  private readonly subscriptions: Subscription = new Subscription();

  constructor(
    public endpointsService: EndpointsService,
    private readonly authService: AuthenticationService,
    private readonly alumnoService: AlumnoService
  ) {}

  ngOnInit(): void {
    this.authService.obtenerNombreUsuario().subscribe((nombre) => {
      if (nombre && !sessionStorage.getItem('welcomeShown')) {
        Swal.fire({
          title: 'Inicio de sesion exitoso',
          text: `Bienvenido/a, ${nombre}`,
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
          this.cargarDeportesDelAlumno(this.selectedAlumno.id);
          return;
        }

        Swal.fire({
          title: 'Error',
          text: 'No se encontraron alumnos asociados a este usuario.',
          icon: 'error',
        });
      },
      error: () => {
        Swal.fire({
          title: 'Error en la peticion',
          text: 'Error al obtener los alumnos.',
          icon: 'error',
        });
      },
    });
  }

  seleccionarAlumno(alumno: any): void {
    this.selectedAlumno = alumno;
    this.cargarGruposDelAlumno(alumno.id);
    this.cargarDeportesDelAlumno(alumno.id);
  }

  scrollToSection(sectionId: string): void {
    if (typeof document === 'undefined') {
      return;
    }
    const target = document.getElementById(sectionId);
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
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
          title: 'Error en la peticion',
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
    if (deporte.includes('competici')) return 'Taekwondo Competicion';
    if (deporte.includes('taekwondo')) return 'Taekwondo';
    if (deporte.includes('kickboxing')) return 'Kickboxing';
    if (deporte.includes('pilates')) return 'Pilates';
    if (deporte.includes('defensa personal')) return 'Defensa Personal Femenina';
    return 'Otro';
  }

  getClaveDeporteGrupo(grupo: any): string {
    const deporte = this.normalizarDeporte(grupo);
    if (deporte.includes('competici')) return 'competicion';
    if (deporte.includes('taekwondo')) return 'taekwondo';
    if (deporte.includes('kickboxing')) return 'kickboxing';
    if (deporte.includes('pilates')) return 'pilates';
    if (deporte.includes('defensa')) return 'defensa';
    return 'otro';
  }

  obtenerClaseGrupo(grupo: any): string {
    return this.getClaveDeporteGrupo(grupo);
  }

  getIconoDeporteGrupo(grupo: any): string {
    const deporteKey = this.getClaveDeporteGrupo(grupo);
    if (deporteKey === 'competicion') return 'bi-trophy-fill';
    if (deporteKey === 'taekwondo') return 'bi-lightning-charge-fill';
    if (deporteKey === 'kickboxing') return 'bi-fire';
    if (deporteKey === 'pilates') return 'bi-heart-pulse-fill';
    if (deporteKey === 'defensa') return 'bi-shield-fill-check';
    return 'bi-star-fill';
  }

  obtenerIconoDeporte(grupo: any): string {
    return this.getIconoDeporteGrupo(grupo);
  }

  obtenerNombreDeporte(grupo: any): string {
    const etiqueta = this.obtenerEtiquetaColor(grupo);
    if (etiqueta === 'Defensa Personal Femenina') return 'D.P. Femenina';
    return etiqueta;
  }

  obtenerColorDeporte(grupo: any): string {
    const etiqueta = this.obtenerEtiquetaColor(grupo).toLowerCase();
    if (etiqueta.includes('competici')) return '#d7b2e7';
    if (etiqueta.includes('taekwondo')) return '#b8c8ea';
    if (etiqueta.includes('kickboxing')) return '#ffd2b4';
    if (etiqueta.includes('pilates')) return '#c4e3e5';
    if (etiqueta.includes('defensa personal')) return '#f6cde0';
    return '#dee2e6';
  }

  cargarDeportesDelAlumno(alumnoId: number): void {
    this.cargandoDeportes = true;
    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        this.deportesDelAlumno = deportes;
        this.cargandoDeportes = false;
      },
      error: () => {
        this.deportesDelAlumno = [];
        this.cargandoDeportes = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los deportes del alumno',
          icon: 'error',
        });
      },
    });
  }

  getDeporteLabel(deporte: string): string {
    return getDeporteLabel(deporte);
  }

  getGradoStyle(tipoGrado: string): string {
    return getGradoTextStyle(tipoGrado);
  }

  formatearFecha(fecha: string | Date | null): string {
    if (!fecha) return 'Sin fecha';
    const date = fecha instanceof Date ? fecha : new Date(fecha);
    return date.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  getIconoDeporteTarjeta(deporte: string): string {
    const deporteLower = (deporte || '').toLowerCase();
    if (deporteLower.includes('competici')) return 'bi-trophy-fill';
    if (deporteLower.includes('taekwondo')) return 'bi-lightning-charge-fill';
    if (deporteLower.includes('kickboxing')) return 'bi-fire';
    if (deporteLower.includes('pilates')) return 'bi-heart-pulse-fill';
    if (deporteLower.includes('defensa')) return 'bi-shield-fill-check';
    return 'bi-star-fill';
  }

  getIconoDeportePorNombre(deporte: string): string {
    return this.getIconoDeporteTarjeta(deporte);
  }

  getCantidadDeportesAptos(): number {
    return this.deportesDelAlumno.filter((deporte) => deporte.aptoParaExamen).length;
  }
}
