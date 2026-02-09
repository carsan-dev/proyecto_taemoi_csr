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
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

interface BeltVisualData {
  topColor: string;
  bottomColor: string;
  isSplit: boolean;
  stripeOffsets: number[];
  label: string;
}

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
  private readonly beltWidthPx = 84;
  private readonly beltVisualCache = new Map<string, BeltVisualData>();

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

  private isKickboxing(deporte: string | null | undefined): boolean {
    return (deporte || '').toUpperCase() === 'KICKBOXING';
  }

  private getBeltColorByName(colorName: string, isKickboxing: boolean): string {
    const normalized = (colorName || '').toUpperCase().trim();
    if (normalized === 'ROJO' && isKickboxing) {
      return '#8B4513';
    }

    switch (normalized) {
      case 'BLANCO':
        return '#FFFFFF';
      case 'AMARILLO':
        return '#FFFF00';
      case 'NARANJA':
        return '#FFA500';
      case 'VERDE':
        return '#008000';
      case 'AZUL':
        return '#0000FF';
      case 'ROJO':
        return '#FF0000';
      case 'NEGRO':
        return '#000000';
      default:
        return '#CCCCCC';
    }
  }

  private getStripeOffsets(stripeCount: number): number[] {
    const safeCount = Number.isFinite(stripeCount) ? Math.max(0, stripeCount) : 0;
    const stripeWidth = Math.max(2, Math.floor(this.beltWidthPx / 15));
    const gap = 1;
    const initialMargin = 3;
    return Array.from({ length: safeCount }, (_, index) => initialMargin + index * (stripeWidth + gap));
  }

  private getBeltLabel(tipoGrado: string, deporte: string | null | undefined): string {
    const upper = (tipoGrado || '').toUpperCase().trim();
    if (!upper) {
      return '';
    }

    const isKickboxing = this.isKickboxing(deporte);
    const adaptPart = (part: string): string => {
      if (isKickboxing && part === 'ROJO') {
        return 'MARRON';
      }
      return part;
    };

    if (upper.startsWith('NEGRO_') && upper.includes('_DAN')) {
      const parts = upper.split('_');
      const dan = parts.length >= 2 ? parts[1] : '';
      return dan ? `NEGRO ${dan} DAN` : 'NEGRO DAN';
    }

    if (upper.startsWith('ROJO_NEGRO_')) {
      const parts = upper.split('_');
      const pum = parts.length >= 3 ? parts[2] : '';
      const rojoName = isKickboxing ? 'MARRON' : 'ROJO';
      return pum ? `${rojoName}/NEGRO ${pum} PUM` : `${rojoName}/NEGRO`;
    }

    if (upper.includes('_')) {
      const parts = upper
        .split('_')
        .filter((part) => part !== 'DAN' && part !== 'PUM' && !/^\d+$/.test(part));
      if (parts.length > 0) {
        return parts.map(adaptPart).join('/');
      }
    }

    return adaptPart(upper);
  }

  getBeltVisual(tipoGrado: string | null | undefined, deporte: string | null | undefined): BeltVisualData {
    const grado = (tipoGrado || '').toUpperCase().trim();
    const sport = (deporte || '').toUpperCase().trim();
    const cacheKey = `${sport}|${grado}`;
    const cached = this.beltVisualCache.get(cacheKey);
    if (cached) {
      return cached;
    }

    const isKickboxing = this.isKickboxing(sport);
    const defaultVisual: BeltVisualData = {
      topColor: '#CCCCCC',
      bottomColor: '#CCCCCC',
      isSplit: false,
      stripeOffsets: [],
      label: 'SIN GRADO'
    };

    if (!grado) {
      this.beltVisualCache.set(cacheKey, defaultVisual);
      return defaultVisual;
    }

    let topColor = '#CCCCCC';
    let bottomColor = '#CCCCCC';
    let isSplit = false;
    let stripeOffsets: number[] = [];

    if (grado.startsWith('ROJO_NEGRO_')) {
      const parts = grado.split('_');
      const stripeCount = Number.parseInt(parts[2] || '0', 10);
      topColor = this.getBeltColorByName(parts[1], isKickboxing);
      bottomColor = this.getBeltColorByName(parts[0], isKickboxing);
      isSplit = true;
      stripeOffsets = this.getStripeOffsets(stripeCount);
    } else if (grado.includes('DAN') || (grado.includes('PUM') && !grado.includes('ROJO_NEGRO'))) {
      const parts = grado.split('_');
      const stripeCount = grado.includes('DAN') ? Number.parseInt(parts[1] || '0', 10) : 0;
      topColor = '#000000';
      bottomColor = '#000000';
      stripeOffsets = this.getStripeOffsets(stripeCount);
    } else if (grado.includes('_')) {
      const parts = grado.split('_');
      topColor = this.getBeltColorByName(parts[1], isKickboxing);
      bottomColor = this.getBeltColorByName(parts[0], isKickboxing);
      isSplit = true;
    } else {
      topColor = this.getBeltColorByName(grado, isKickboxing);
      bottomColor = topColor;
    }

    const visual: BeltVisualData = {
      topColor,
      bottomColor,
      isSplit,
      stripeOffsets,
      label: this.getBeltLabel(grado, sport)
    };

    this.beltVisualCache.set(cacheKey, visual);
    return visual;
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
