import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { Subscription, forkJoin } from 'rxjs';
import Swal from 'sweetalert2';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

interface DashboardStats {
  totalAlumnos: number;
  alumnosActivos: number;
  alumnosInactivos: number;
  totalGrupos: number;
  totalEventos: number;
  eventosVisibles: number;
  totalTurnos: number;
  alumnosPorDeporte: { [key: string]: number };
  alumnosAptos: number;
  proximosEventos: any[];
}

@Component({
  selector: 'app-vista-principal-admin',
  standalone: true,
  imports: [CommonModule, RouterLink, SkeletonCardComponent],
  templateUrl: './vista-principal-admin.component.html',
  styleUrl: './vista-principal-admin.component.scss',
})
export class VistaPrincipalAdminComponent implements OnInit, OnDestroy {
  nombreUsuario: string | null = '';
  usuarioLogueado: boolean = false;
  cargandoEstadisticas: boolean = true;
  cargandoEventos: boolean = true;
  fechaActual: Date = new Date();
  private subscriptions: Subscription = new Subscription();

  stats: DashboardStats = {
    totalAlumnos: 0,
    alumnosActivos: 0,
    alumnosInactivos: 0,
    totalGrupos: 0,
    totalEventos: 0,
    eventosVisibles: 0,
    totalTurnos: 0,
    alumnosPorDeporte: {},
    alumnosAptos: 0,
    proximosEventos: []
  };

  // Navigation sections for quick access
  seccionesAdmin = [
    {
      titulo: 'Alumnos',
      descripcion: 'Gestiona información de los alumnos',
      ruta: '/alumnosListar',
      icono: 'bi bi-people',
      color: '#4a90d9'
    },
    {
      titulo: 'Grupos y Turnos',
      descripcion: 'Organiza grupos y gestiona horarios',
      ruta: '/gruposListar',
      icono: 'bi bi-people-fill',
      color: '#50c878'
    },
    {
      titulo: 'Eventos',
      descripcion: 'Crea y administra eventos especiales',
      ruta: '/eventosListar',
      icono: 'bi bi-calendar-check',
      color: '#f5a623'
    },
    {
      titulo: 'Productos',
      descripcion: 'Gestión de productos y servicios',
      ruta: '/productosListar',
      icono: 'bi bi-box-seam',
      color: '#9b59b6'
    },
    {
      titulo: 'Convocatorias',
      descripcion: 'Publica y gestiona convocatorias',
      ruta: '/convocatoriasListar',
      icono: 'bi bi-clipboard-check',
      color: '#e74c3c'
    },
  ];

  constructor(
    private readonly authService: AuthenticationService,
    private readonly route: ActivatedRoute,
    private readonly endpointsService: EndpointsService
  ) {}

  ngOnInit(): void {
    this.authService.obtenerNombreUsuario().subscribe((nombre) => {
      this.nombreUsuario = nombre ?? '';

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

    this.cargarEstadisticas();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  cargarEstadisticas(): void {
    this.cargandoEstadisticas = true;

    // Fetch all data in parallel
    const sub = forkJoin({
      alumnos: this.endpointsService.obtenerTodosLosAlumnosSinPaginar(true),
      grupos: this.endpointsService.obtenerTodosLosGrupos(),
      turnos: this.endpointsService.obtenerTodosLosTurnos(),
      alumnosAptos: this.endpointsService.obtenerAlumnosAptosParaExamen()
    }).subscribe({
      next: (data) => {
        // Process alumnos
        const alumnos = data.alumnos || [];
        this.stats.totalAlumnos = alumnos.length;
        this.stats.alumnosActivos = alumnos.filter((a: any) => a.activo !== false).length;
        this.stats.alumnosInactivos = alumnos.filter((a: any) => a.activo === false).length;

        // Count by sport using deportes array
        const deporteCount: { [key: string]: number } = {};
        alumnos.forEach((alumno: any) => {
          if (alumno.deportes && Array.isArray(alumno.deportes)) {
            alumno.deportes.forEach((deporte: any) => {
              if (deporte.activo !== false) {
                const deporteNombre = deporte.deporte || 'OTRO';
                deporteCount[deporteNombre] = (deporteCount[deporteNombre] || 0) + 1;
              }
            });
          }
        });
        this.stats.alumnosPorDeporte = deporteCount;

        // Process grupos
        this.stats.totalGrupos = (data.grupos || []).length;

        // Process turnos
        this.stats.totalTurnos = (data.turnos || []).length;

        // Process alumnos aptos
        this.stats.alumnosAptos = (data.alumnosAptos || []).length;

        this.cargandoEstadisticas = false;
      },
      error: (err) => {
        console.error('Error loading statistics:', err);
        this.cargandoEstadisticas = false;
      }
    });

    this.subscriptions.add(sub);

    // Load events separately (uses BehaviorSubject pattern)
    this.endpointsService.obtenerTodosLosEventos();
    const eventosSub = this.endpointsService.eventos$.subscribe(eventos => {
      if (eventos) {
        this.stats.totalEventos = eventos.length;
        this.stats.eventosVisibles = eventos.filter((e: any) => e.visible).length;

        // Get upcoming events (next 5 events sorted by date)
        const now = new Date();
        this.stats.proximosEventos = eventos
          .filter((e: any) => new Date(e.fecha) >= now && e.visible)
          .sort((a: any, b: any) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())
          .slice(0, 5);

        this.cargandoEventos = false;
      }
    });
    this.subscriptions.add(eventosSub);
  }

  getDeporteLabel(deporte: string): string {
    const labels: { [key: string]: string } = {
      'TAEKWONDO': 'Taekwondo',
      'KICKBOXING': 'Kickboxing',
      'PILATES': 'Pilates',
      'DEFENSA_PERSONAL_FEMENINA': 'Defensa Personal'
    };
    return labels[deporte] || deporte;
  }

  getDeporteIcon(deporte: string): string {
    const icons: { [key: string]: string } = {
      'TAEKWONDO': '🥋',
      'KICKBOXING': '🥊',
      'PILATES': '🧘',
      'DEFENSA_PERSONAL_FEMENINA': '🛡️'
    };
    return icons[deporte] || '⭐';
  }

  getDeporteColor(deporte: string): string {
    const colors: { [key: string]: string } = {
      'TAEKWONDO': '#4a90d9',
      'KICKBOXING': '#e74c3c',
      'PILATES': '#50c878',
      'DEFENSA_PERSONAL_FEMENINA': '#f5a623'
    };
    return colors[deporte] || '#6c757d';
  }

  getTotalDeportes(): number {
    return Object.values(this.stats.alumnosPorDeporte).reduce((a, b) => a + b, 0);
  }

  getDeportePercentage(count: number): number {
    const total = this.getTotalDeportes();
    return total > 0 ? (count / total) * 100 : 0;
  }

  formatDate(fecha: string | Date): string {
    const date = fecha instanceof Date ? fecha : new Date(fecha);
    return date.toLocaleDateString('es-ES', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  }

  getObjectKeys(obj: any): string[] {
    return Object.keys(obj || {});
  }
}
