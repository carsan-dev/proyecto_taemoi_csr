import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { interval, Subscription, forkJoin, of } from 'rxjs';
import { catchError, skip } from 'rxjs/operators';
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
  cobrosPendientesMes: number;
  importePendienteMes: number;
  erroresAuditoriaRecientes: number;
  proximosEventos: any[];
}

interface DashboardQuickAction {
  titulo: string;
  descripcion: string;
  ruta: string;
  icono: string;
  color: string;
  queryParams?: Record<string, string>;
}

interface DashboardAlert {
  id: string;
  tipo: 'critical' | 'warning' | 'info' | 'success';
  titulo: string;
  descripcion: string;
  accionTexto: string;
  ruta: string;
  icono: string;
  queryParams?: Record<string, string>;
}

@Component({
  selector: 'app-vista-principal-admin',
  standalone: true,
  imports: [CommonModule, RouterLink, SkeletonCardComponent],
  templateUrl: './vista-principal-admin.component.html',
  styleUrl: './vista-principal-admin.component.scss',
})
export class VistaPrincipalAdminComponent implements OnInit, OnDestroy {
  private readonly ventanaErroresAuditoriaDias: number = 7;
  private readonly autoRefreshIntervalMs: number = 30000;
  private eventosSuscripcionInicializada = false;
  nombreUsuario: string | null = '';
  usuarioLogueado: boolean = false;
  cargandoEstadisticas: boolean = true;
  cargandoDistribucion: boolean = true;
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
    cobrosPendientesMes: 0,
    importePendienteMes: 0,
    erroresAuditoriaRecientes: 0,
    proximosEventos: []
  };

  alertasOperativas: DashboardAlert[] = [];

  operacionesPrioritarias: DashboardQuickAction[] = [
    {
      titulo: 'Revisar Aptos para Examen',
      descripcion: 'Alumnos listos para promocionar en la siguiente convocatoria.',
      ruta: '/alumnosListar',
      queryParams: { aptoParaExamen: 'true' },
      icono: 'bi bi-award-fill',
      color: '#b83280',
    },
    {
      titulo: 'Gestionar Bajas y Altas',
      descripcion: 'Control rapido de alumnos inactivos y eliminaciones.',
      ruta: '/alumnosEliminar',
      icono: 'bi bi-person-x-fill',
      color: '#dd6b20',
    },
    {
      titulo: 'Preparar Convocatorias',
      descripcion: 'Crear convocatorias, pagos e informe de examen.',
      ruta: '/convocatoriasListar',
      icono: 'bi bi-clipboard-check-fill',
      color: '#3182ce',
    },
    {
      titulo: 'Tesorería y Cobros',
      descripcion: 'Revisión de deudas, pendientes y cobros por mes.',
      ruta: '/tesoreriaCobros',
      icono: 'bi bi-cash-stack',
      color: '#2f855a',
    },
    {
      titulo: 'Auditoria de API',
      descripcion: 'Consulta errores recientes y operaciones globales.',
      ruta: '/auditoriaSistema',
      icono: 'bi bi-bug-fill',
      color: '#0f766e',
    },
    {
      titulo: 'Roles y Configuracion',
      descripcion: 'Permisos de usuarios y limite global de turno.',
      ruta: '/configuracion-sistema',
      icono: 'bi bi-gear-fill',
      color: '#2f855a',
    },
  ];

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
    {
      titulo: 'Tesorería',
      descripcion: 'Control mensual de cobros y deudas',
      ruta: '/tesoreriaCobros',
      icono: 'bi bi-cash-coin',
      color: '#16a085'
    },
    {
      titulo: 'Auditoria',
      descripcion: 'Revisa trazas del sistema y errores recientes de la API',
      ruta: '/auditoriaSistema',
      icono: 'bi bi-journal-text',
      color: '#0f766e'
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

    this.inicializarSuscripcionEventos();
    this.cargarEstadisticas();
    this.iniciarAutoRefresh();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  get dashboardCargando(): boolean {
    return this.cargandoEstadisticas || this.cargandoDistribucion || this.cargandoEventos;
  }

  actualizarDashboardAhora(): void {
    if (this.dashboardCargando) {
      return;
    }

    this.fechaActual = new Date();
    this.cargarEstadisticas();
  }

  cargarEstadisticas(): void {
    this.cargandoEstadisticas = true;
    this.cargandoDistribucion = true;
    this.cargandoEventos = true;
    this.endpointsService.obtenerTodosLosEventos();
    const mesActual = this.fechaActual.getMonth() + 1;
    const anoActual = this.fechaActual.getFullYear();
    const fechaHastaErrores = this.formatearFechaLocal(new Date());
    const fechaDesdeErrores = this.formatearFechaLocal(this.obtenerFechaDiasAtras(this.ventanaErroresAuditoriaDias));

    // Fetch all data in parallel
    const sub = forkJoin({
      alumnosTotales: this.endpointsService.obtenerAlumnos(1, 1, '', true, false),
      alumnosActivos: this.endpointsService.obtenerAlumnos(1, 1, '', false, false),
      grupos: this.endpointsService.obtenerTodosLosGrupos(),
      turnos: this.endpointsService.obtenerTodosLosTurnos(),
      alumnosAptos: this.endpointsService.obtenerAlumnosAptosParaExamen(),
      auditoriaErroresRecientes: this.endpointsService.obtenerAuditoriaEventos(
        {
          desde: fechaDesdeErrores,
          hasta: fechaHastaErrores,
          resultado: 'ERROR',
        },
        1,
        1
      ).pipe(
        catchError((error) => {
          console.warn('No se pudieron cargar errores recientes de auditoria para el dashboard:', error);
          return of(null);
        })
      ),
      tesoreriaResumen: this.endpointsService.obtenerTesoreriaResumen(mesActual, anoActual, 'TODOS').pipe(
        catchError((error) => {
          console.warn('No se pudo cargar el resumen de tesorería para el dashboard:', error);
          return of(null);
        })
      )
    }).subscribe({
      next: (data) => {
        const totalAlumnos = data.alumnosTotales?.totalElements ?? 0;
        const alumnosActivos = data.alumnosActivos?.totalElements ?? 0;
        this.stats.totalAlumnos = totalAlumnos;
        this.stats.alumnosActivos = alumnosActivos;
        this.stats.alumnosInactivos = Math.max(totalAlumnos - alumnosActivos, 0);
        this.stats.alumnosPorDeporte = {};

        // Process grupos
        this.stats.totalGrupos = (data.grupos || []).length;

        // Process turnos
        this.stats.totalTurnos = (data.turnos || []).length;

        // Process alumnos aptos
        this.stats.alumnosAptos = (data.alumnosAptos || []).length;
        this.stats.cobrosPendientesMes = data.tesoreriaResumen?.totalPendientes || 0;
        this.stats.importePendienteMes = data.tesoreriaResumen?.importePendiente || 0;
        this.stats.erroresAuditoriaRecientes = data.auditoriaErroresRecientes?.totalElements || 0;

        this.cargandoEstadisticas = false;
        this.actualizarAlertasOperativas();
        this.cargarDistribucionPorDeporte();
      },
      error: (err) => {
        console.error('Error loading statistics:', err);
        this.cargandoEstadisticas = false;
        this.cargandoDistribucion = false;
        this.actualizarAlertasOperativas();
      }
    });

    this.subscriptions.add(sub);
  }

  private cargarDistribucionPorDeporte(): void {
    const sub = this.endpointsService.obtenerDistribucionAlumnosPorDeporte().subscribe({
      next: (distribucion) => {
        const deporteCount: { [key: string]: number } = {};
        Object.entries(distribucion ?? {}).forEach(([deporte, total]) => {
          const totalNormalizado = Number(total);
          if (Number.isFinite(totalNormalizado) && totalNormalizado > 0) {
            deporteCount[deporte] = Math.floor(totalNormalizado);
          }
        });
        this.stats.alumnosPorDeporte = deporteCount;
        this.cargandoDistribucion = false;
      },
      error: () => {
        this.stats.alumnosPorDeporte = {};
        this.cargandoDistribucion = false;
      },
    });
    this.subscriptions.add(sub);
  }

  private inicializarSuscripcionEventos(): void {
    if (this.eventosSuscripcionInicializada) {
      return;
    }

    const eventosSub = this.endpointsService.eventos$
      .pipe(skip(1))
      .subscribe((eventos) => {
        this.stats.totalEventos = eventos.length;
        this.stats.eventosVisibles = eventos.filter((evento: any) => evento.visible).length;
        this.stats.proximosEventos = eventos
          .filter((evento: any) => evento.visible)
          .slice(0, 4);
        this.cargandoEventos = false;
        this.actualizarAlertasOperativas();
      });

    this.subscriptions.add(eventosSub);
    this.eventosSuscripcionInicializada = true;
  }

  private iniciarAutoRefresh(): void {
    const autoRefreshSub = interval(this.autoRefreshIntervalMs).subscribe(() => {
      if (this.cargandoEstadisticas || this.cargandoDistribucion || this.cargandoEventos) {
        return;
      }
      this.fechaActual = new Date();
      this.cargarEstadisticas();
    });
    this.subscriptions.add(autoRefreshSub);
  }

  get eventosOcultos(): number {
    return Math.max(this.stats.totalEventos - this.stats.eventosVisibles, 0);
  }

  mostrarAlertasPrimero(): boolean {
    return this.alertasOperativas.some((alerta) => this.esAlertaUrgente(alerta.tipo));
  }

  private actualizarAlertasOperativas(): void {
    if (this.cargandoEstadisticas || this.cargandoEventos) {
      return;
    }

    const alertas: DashboardAlert[] = [];

    if (this.stats.alumnosInactivos > 0) {
      alertas.push({
        id: 'alumnos-inactivos',
        tipo: 'warning',
        titulo: 'Alumnos inactivos pendientes',
        descripcion: `${this.stats.alumnosInactivos} alumnos requieren revision de alta o baja.`,
        accionTexto: 'Ir a bajas y altas',
        ruta: '/alumnosEliminar',
        icono: 'bi bi-person-x-fill',
      });
    }

    if (this.eventosOcultos > 0) {
      alertas.push({
        id: 'eventos-ocultos',
        tipo: 'info',
        titulo: 'Eventos sin publicar',
        descripcion: `${this.eventosOcultos} eventos estan ocultos en la web publica.`,
        accionTexto: 'Revisar visibilidad',
        ruta: '/eventosListar',
        icono: 'bi bi-eye-slash-fill',
      });
    }

    if (this.stats.alumnosAptos > 0) {
      alertas.push({
        id: 'aptos-examen',
        tipo: 'info',
        titulo: 'Alumnos listos para convocatoria',
        descripcion: `${this.stats.alumnosAptos} alumnos figuran aptos para examen.`,
        accionTexto: 'Abrir listado aptos',
        ruta: '/alumnosListar',
        queryParams: { aptoParaExamen: 'true' },
        icono: 'bi bi-award-fill',
      });
    }

    if (this.stats.cobrosPendientesMes > 0) {
      const mes = this.fechaActual.getMonth() + 1;
      const ano = this.fechaActual.getFullYear();
      alertas.push({
        id: 'cobros-pendientes-mes',
        tipo: 'warning',
        titulo: 'Cobros pendientes del mes',
        descripcion: `${this.stats.cobrosPendientesMes} cobros pendientes (${this.stats.importePendienteMes.toFixed(2)} €) en ${mes}/${ano}.`,
        accionTexto: 'Revisar tesorería',
        ruta: '/tesoreriaCobros',
        queryParams: {
          estado: 'PENDIENTES',
          mes: mes.toString(),
          ano: ano.toString(),
          deporte: 'TODOS',
        },
        icono: 'bi bi-cash-stack',
      });
    }

    if (this.stats.erroresAuditoriaRecientes > 0) {
      alertas.push({
        id: 'auditoria-errores-recientes',
        tipo: 'critical',
        titulo: 'Errores recientes en API',
        descripcion: `${this.stats.erroresAuditoriaRecientes} errores en los ultimos ${this.ventanaErroresAuditoriaDias} dias.`,
        accionTexto: 'Abrir auditoria',
        ruta: '/auditoriaSistema',
        icono: 'bi bi-bug-fill',
      });
    }

    if (this.stats.totalTurnos === 0) {
      alertas.push({
        id: 'sin-turnos',
        tipo: 'critical',
        titulo: 'No hay turnos creados',
        descripcion: 'Crea turnos para poder asignar alumnos y mantener horario activo.',
        accionTexto: 'Crear turno',
        ruta: '/turnosCrear',
        icono: 'bi bi-calendar-plus-fill',
      });
    }

    if (this.stats.totalGrupos === 0) {
      alertas.push({
        id: 'sin-grupos',
        tipo: 'critical',
        titulo: 'No hay grupos configurados',
        descripcion: 'Crea grupos para organizar alumnado y asignaciones de turnos.',
        accionTexto: 'Crear grupo',
        ruta: '/gruposCrear',
        icono: 'bi bi-people-fill',
      });
    }

    if (alertas.length === 0) {
      alertas.push({
        id: 'operacion-estable',
        tipo: 'success',
        titulo: 'Operacion estable',
        descripcion: 'No hay alertas operativas prioritarias en este momento.',
        accionTexto: 'Ver dashboard',
        ruta: '/adminpage',
        icono: 'bi bi-check2-circle',
      });
    }

    this.alertasOperativas = this.ordenarAlertasOperativas(alertas);
  }

  private ordenarAlertasOperativas(alertas: DashboardAlert[]): DashboardAlert[] {
    return [...alertas].sort((a, b) => {
      const prioridad = this.obtenerPrioridadAlerta(a.tipo) - this.obtenerPrioridadAlerta(b.tipo);
      if (prioridad !== 0) {
        return prioridad;
      }
      return a.titulo.localeCompare(b.titulo, 'es', { sensitivity: 'base' });
    });
  }

  private obtenerPrioridadAlerta(tipo: DashboardAlert['tipo']): number {
    if (tipo === 'critical') return 0;
    if (tipo === 'warning') return 1;
    if (tipo === 'info') return 2;
    return 3;
  }

  private esAlertaUrgente(tipo: DashboardAlert['tipo']): boolean {
    return tipo === 'critical' || tipo === 'warning';
  }

  private obtenerFechaDiasAtras(dias: number): Date {
    const fecha = new Date();
    fecha.setDate(fecha.getDate() - dias);
    return fecha;
  }

  private formatearFechaLocal(fecha: Date): string {
    const ano = fecha.getFullYear();
    const mes = String(fecha.getMonth() + 1).padStart(2, '0');
    const dia = String(fecha.getDate()).padStart(2, '0');
    return `${ano}-${mes}-${dia}`;
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

  getEventoMiniaturaUrl(evento: any): string {
    const fallback = '../../../../assets/media/default.webp';
    const rawUrl = evento?.fotoEvento?.url;
    if (!rawUrl) {
      return fallback;
    }

    const version = String(evento?.fotoEvento?.id ?? evento?.fotoEvento?.nombre ?? '0');
    let url = this.actualizarParametroUrl(rawUrl, 'w', '260');
    url = this.actualizarParametroUrl(url, 'v', version);
    return url;
  }

  private actualizarParametroUrl(url: string, key: string, value: string): string {
    const valueSeguro = encodeURIComponent(value);
    const regex = new RegExp(`([?&])${key}=[^&]*`);
    if (regex.test(url)) {
      return url.replace(regex, `$1${key}=${valueSeguro}`);
    }
    const separador = url.includes('?') ? '&' : '?';
    return `${url}${separador}${key}=${valueSeguro}`;
  }
}

