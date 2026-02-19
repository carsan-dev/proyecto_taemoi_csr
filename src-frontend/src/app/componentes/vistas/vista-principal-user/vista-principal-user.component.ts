import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';
import Swal from 'sweetalert2';

import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { ConvocatoriaDTO } from '../../../interfaces/convocatoria-dto';
import { Documento } from '../../../interfaces/documento';
import { RetoDiarioEstado } from '../../../interfaces/reto-diario-estado';
import { RetoDiarioRankingSemanal } from '../../../interfaces/reto-diario-ranking-semanal';
import { Turno } from '../../../interfaces/turno';
import { getDeporteLabel } from '../../../enums/deporte';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';
import { MaterialesExamenUserComponent } from './materiales-examen-user/materiales-examen-user.component';

interface BeltVisualData {
  topColor: string;
  bottomColor: string;
  isSplit: boolean;
  stripeOffsets: number[];
  label: string;
}

interface ProximaClaseData {
  turno: Turno;
  inicio: Date;
  fin: Date;
  enCurso: boolean;
  etiquetaDia: string;
  horario: string;
  cuentaAtras: string;
  deporte: string;
}

interface ProximaClasePorDeporte {
  sportKey: string;
  sportLabel: string;
  sportIcon: string;
  clase: ProximaClaseData;
}

interface EstadoDeporteSnapshot {
  deporteId: number | null;
  deporte: string;
  grado: string | null;
  aptoParaExamen: boolean;
}

@Component({
  selector: 'app-vista-principal-user',
  standalone: true,
  imports: [CommonModule, RouterModule, SkeletonCardComponent, MaterialesExamenUserComponent],
  templateUrl: './vista-principal-user.component.html',
  styleUrl: './vista-principal-user.component.scss',
})
export class VistaPrincipalUserComponent implements OnInit, OnDestroy {
  alumnos: any[] = [];
  selectedAlumno: any = null;
  grupos: any[] = [];
  turnosAlumno: Turno[] = [];
  cargandoTurnos: boolean = false;
  proximaClase: ProximaClaseData | null = null;
  proximasClasesPorDeporte: ProximaClasePorDeporte[] = [];
  deportesDelAlumno: AlumnoDeporteDTO[] = [];
  cargandoDeportes: boolean = false;
  documentosAlumno: Documento[] = [];
  cargandoDocumentos: boolean = false;
  cargandoConvocatorias: boolean = false;
  eventosRecientes: any[] = [];
  novedadesDocumentos: number = 0;
  novedadesEventos: number = 0;
  novedadesEstadoDeportes: number = 0;
  detallesNovedadesEstado: string[] = [];
  convocatoriasAlumno: ConvocatoriaDTO[] = [];
  proximaConvocatoriaAlumno: ConvocatoriaDTO | null = null;
  proximasConvocatoriasPorDeporte: ConvocatoriaDTO[] = [];
  convocatoriasSecundarias: ConvocatoriaDTO[] = [];
  mostrarConvocatoriasSecundarias: boolean = false;
  retoDiarioActual: string = '';
  rachaRetoDiario: number = 0;
  retoCompletadoHoy: boolean = false;
  retoCountdownTexto: string = '';
  retoCountdownUrgente: boolean = false;
  recordatorioRachaEmailHabilitado: boolean = false;
  cargandoRecordatorioRachaEmail: boolean = false;
  guardandoRecordatorioRachaEmail: boolean = false;
  rankingSemanal: RetoDiarioRankingSemanal | null = null;
  cargandoRankingSemanal: boolean = false;
  errorRankingSemanal: string | null = null;
  deporteRankingSeleccionado: string | null = null;
  deportesRankingDisponibles: string[] = [];
  documentosVisiblesCount: number = 0;
  private readonly subscriptions: Subscription = new Subscription();
  private readonly beltWidthPx = 84;
  private readonly beltVisualCache = new Map<string, BeltVisualData>();
  private readonly gruposCache = new Map<number, any[]>();
  private readonly turnosCache = new Map<number, Turno[]>();
  private readonly deportesCache = new Map<number, AlumnoDeporteDTO[]>();
  private readonly documentosCache = new Map<number, Documento[]>();
  private readonly convocatoriasCache = new Map<number, ConvocatoriaDTO[]>();
  private readonly documentosPageSize = 8;
  private readonly probabilidadRetoDeporte = 0.75;
  private readonly retosDiariosGenerales: string[] = [
    'Movilidad de cadera y tobillo: 5 minutos',
    'Plancha frontal: 3 series de 30 segundos',
    'Plancha lateral: 3 series de 20 segundos por lado',
    'Sentadillas controladas: 20 repeticiones',
    'Respiración nasal y postura: 2 minutos',
    'Puente de glúteo: 3 series de 10 repeticiones',
    'Estiramientos de piernas: 5 minutos',
    'Elevaciones de gemelos: 3 series de 12 repeticiones',
    'Zancadas alternas: 2 series de 12 repeticiones',
    'Guardia y desplazamientos suaves: 3 minutos',
    'Rotaciones torácicas: 3 series de 12 repeticiones por lado',
    'Sentadilla isométrica en pared: 2 series de 45 segundos',
    'Movilidad de hombros y escápulas: 4 minutos',
    'Bird-dog: 3 series de 12 repeticiones por lado',
    'Peso muerto a una pierna sin carga: 2 series de 12 repeticiones por lado',
    'Activación de tobillos y pies: 3 minutos',
    'Flexiones inclinadas: 2 series de 10 repeticiones',
    'Estiramiento de cadena posterior: 4 minutos',
  ];
  private readonly retosDiariosPorDeporte: Record<string, string[]> = {
    TAEKWONDO: [
      'Ap chagi: 4 series de 12 repeticiones por pierna',
      'Yop chagi: 4 series de 10 repeticiones por pierna',
      'Dollyo chagi: 3 series de 12 repeticiones por pierna',
      'Paso adelante + ap chagi: 3 series de 8 repeticiones por lado',
      'Paso atrás + contraataque: 3 series de 8 repeticiones por lado',
      'Guardia de taekwondo con rebote suave: 5 minutos',
      'Rodilla arriba + extensión controlada: 3 series de 10 repeticiones por pierna',
      'Equilibrio en pierna de apoyo: 4 series de 20 segundos por lado',
      'Combinación ap chagi + dollyo chagi: 3 series de 8 repeticiones por lado',
      'Bloqueos medios + altos: 3 series de 10 repeticiones por lado',
      'Desplazamientos de combate con cambios de dirección: 2 series de 90 segundos',
      'Elevaciones de rodilla explosiva: 3 series de 12 repeticiones por lado',
      'Giro de cadera y pivote para técnica circular: 3 series de 8 repeticiones por lado',
      'Trabajo de distancia sin impacto: 4 series de 30 segundos',
      'Chagi a altura media con control: 3 series de 10 repeticiones por pierna',
      'Sombra técnica de taekwondo: 2 series de 2 minutos',
      'Combinación de 1-2 patadas: 3 series de 6 repeticiones por lado',
      'Saltos cortos con pies juntos: 3 series de 12 repeticiones',
      'Cámara de patada mantenida 2 segundos: 3 series de 10 repeticiones por lado',
      'Movilidad específica de cadera para patada: 4 minutos',
      'Colocarte el cinturón: 1 repetición',
      'Combinación de patadas sin bajar la pierna: 3 repeticiones',
      'Apertura de piernas: 3 series de 30 segundos',
      'Piernas juntas, tocar el suelo con las palmas sin doblar rodillas: 3 series de 12 repeticiones'
    ],
    KICKBOXING: [
      'Jab en sombra con técnica limpia: 4 series de 20 segundos',
      'Jab-cross en sombra: 4 series de 20 segundos',
      'Low kick técnica: 3 series de 15 repeticiones por pierna',
      'Front kick: 3 series de 12 repeticiones por pierna',
      'Teep + paso atrás: 3 series de 10 repeticiones por lado',
      'Sombra con guardia alta: 2 series de 2 minutos',
      'Desplazamiento lateral en guardia: 4 series de 30 segundos',
      'Slip izquierda + slip derecha: 3 series de 10 repeticiones',
      'Bloqueo de low kick: 3 series de 10 repeticiones por lado',
      'Rodillazos al aire: 4 series de 12 repeticiones por lado',
      'Combinación jab-cross-low kick: 3 series de 8 repeticiones por lado',
      'Combinación jab-cross-hook: 3 series de 8 repeticiones por lado',
      'Defensa activa sin bajar manos: 2 series de 90 segundos',
      'Uppercut al aire: 3 series de 12 repeticiones por lado',
      'Paso adelante + 1-2: 3 series de 10 repeticiones por lado',
      'Paso atrás + contra 1-2: 3 series de 10 repeticiones por lado',
      'Sombra a ritmo alto: 4 series de 20 segundos',
      'Chequeo de distancia con jab: 3 series de 10 repeticiones',
      'Condición física específica (cuerda o rebote): 2 series de 2 minutos',
      'Sentadilla + low kick técnica: 3 series de 12 repeticiones por lado',
    ],
    PILATES: [
      'Hundred modificado con control respiratorio: 3 series de 10 repeticiones',
      'Puente articulado de columna: 3 series de 12 repeticiones',
      'Roll down segmentado: 3 series de 10 repeticiones',
      'Dead bug controlado: 3 series de 10 repeticiones por lado',
      'Clam shell: 3 series de 12 repeticiones por lado',
      'Side kick en decúbito lateral: 3 series de 10 repeticiones por lado',
      'Swimming en colchoneta: 3 series de 8 repeticiones',
      'Cat-cow lento con respiración: 3 series de 10 repeticiones',
      'Movilización de columna torácica: 3 series de 10 repeticiones',
      'Single leg stretch: 3 series de 10 repeticiones por lado',
      'Double leg stretch controlado: 3 series de 8 repeticiones',
      'Plancha de antebrazos con alineación: 2 series de 60 segundos',
      'Elevación de pelvis unilateral: 3 series de 10 repeticiones por lado',
      'Apertura de cadera en cuadrupedia: 3 series de 10 repeticiones por lado',
      'Respiración costal lateral: 5 minutos',
      'Elevaciones escapulares tumbado: 3 series de 12 repeticiones',
      'Articulación de hombros sin dolor: 3 series de 10 repeticiones',
      'Equilibrio en un pie con centro activo: 3 series de 8 repeticiones por lado',
      'Movilidad suave de columna y cadera: 4 minutos',
      'Control del core en posición neutra: 3 series de 10 repeticiones',
    ],
    DEFENSA_PERSONAL_FEMENINA: [
      'Postura base + manos preparadas: 3 series de 10 repeticiones',
      'Desplazamiento lateral y salida de línea: 3 series de 12 repeticiones',
      'Palm strike al aire: 3 series de 10 repeticiones por lado',
      'Golpe de codo corto: 3 series de 10 repeticiones por lado',
      'Rodillazo frontal: 3 series de 10 repeticiones por lado',
      'Secuencia voz fuerte + paso atrás + guardia: 3 series de 8 repeticiones',
      'Práctica de distancia de seguridad: 2 series de 2 minutos',
      'Liberación de agarre de muñeca: 3 series de 8 repeticiones por lado',
      'Salida de agarre de ropa: 3 series de 8 repeticiones por lado',
      'Empuje y escape a espacio seguro: 3 series de 10 repeticiones',
      'Giro de cadera para salir de agarre: 3 series de 8 repeticiones por lado',
      'Combinación palm strike + escape: 3 series de 10 repeticiones',
      'Combinación rodillazo + salida lateral: 3 series de 10 repeticiones',
      'Simulación de reacción rápida: 2 series de 90 segundos',
      'Trabajo de base y equilibrio: 3 series de 12 repeticiones',
      'Defensa ante agarre frontal sin fuerza bruta: 3 series de 8 repeticiones',
      'Defensa ante aproximación con antebrazos: 3 series de 8 repeticiones',
      'Técnica corta de choque y separación: 3 series de 10 repeticiones',
      'Respiración para bajar estrés post-esfuerzo: 4 minutos',
      'Secuencia simple de bloqueo, golpe y salida: 3 series de 8 repeticiones',
    ],
  };
  private countdownIntervalId: number | null = null;
  private retoCountdownIntervalId: number | null = null;
  private nextRetoResetAtEpochMs: number | null = null;
  private recargandoRetoTrasReset: boolean = false;
  private primeraEmisionEventosRecibida: boolean = false;
  private documentosSeccionVisible: boolean = false;
  private documentosSectionObserver: IntersectionObserver | null = null;
  private documentosSentinelObserver: IntersectionObserver | null = null;
  private cargaDocumentosDiferidaId: number | null = null;

  constructor(
    public endpointsService: EndpointsService,
    private readonly authService: AuthenticationService,
    private readonly alumnoService: AlumnoService
  ) {}

  @ViewChild('misDocumentosSection')
  set misDocumentosSectionRef(sectionRef: ElementRef<HTMLElement> | undefined) {
    this.configurarObservadorSeccionDocumentos(sectionRef?.nativeElement ?? null);
  }

  @ViewChild('documentosSentinel')
  set documentosSentinelRef(sentinelRef: ElementRef<HTMLElement> | undefined) {
    this.configurarObservadorSentinelDocumentos(sentinelRef?.nativeElement ?? null);
  }

  ngOnInit(): void {
    this.inicializarRetoDiario();
    this.cargarPreferenciaRecordatorioRachaEmail();

    const nombreSubscription = this.authService.obtenerNombreUsuario().subscribe((nombre) => {
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
    this.subscriptions.add(nombreSubscription);

    const alumnosSubscription = this.authService.obtenerTodosLosAlumnos().subscribe({
      next: (alumnos) => {
        if (alumnos && alumnos.length > 0) {
          this.alumnos = alumnos;
          this.seleccionarAlumno(alumnos[0]);
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
    this.subscriptions.add(alumnosSubscription);

    this.inicializarNovedadesEventos();
    this.iniciarActualizacionProximaClase();
  }

  seleccionarAlumno(alumno: any): void {
    const alumnoId = Number(alumno?.id);
    if (!Number.isInteger(alumnoId) || alumnoId <= 0) {
      return;
    }
    if (Number(this.selectedAlumno?.id) === alumnoId) {
      return;
    }

    this.selectedAlumno = alumno;
    this.mostrarConvocatoriasSecundarias = false;
    this.cargarDatosAlumno(alumnoId);
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

  irADocumentos(): void {
    const alumnoId = Number(this.selectedAlumno?.id);
    if (Number.isInteger(alumnoId) && alumnoId > 0) {
      this.programarCargaDocumentos(alumnoId, true);
    }
    this.scrollToSection('mis-documentos');
    this.marcarDocumentosComoVistos();
  }

  ngOnDestroy(): void {
    this.cancelarCargaDiferidaDocumentos();
    this.detenerActualizacionProximaClase();
    this.detenerCountdownRetoDiario();
    this.desconectarObservadoresDocumentos();
    this.subscriptions.unsubscribe();
  }

  private cargarDatosAlumno(alumnoId: number): void {
    this.cancelarCargaDiferidaDocumentos();
    this.resetearRankingSemanalRetoDiario();
    this.cargarGruposDelAlumno(alumnoId);
    this.cargarTurnosDelAlumno(alumnoId);
    this.cargarDeportesDelAlumno(alumnoId);
    this.programarCargaDocumentos(alumnoId);
    this.cargarConvocatoriasDelAlumno(alumnoId);
    this.cargarEstadoRetoDiario(alumnoId);
  }

  private esAlumnoSeleccionado(alumnoId: number): boolean {
    return Number(this.selectedAlumno?.id) === alumnoId;
  }

  private cancelarCargaDiferidaDocumentos(): void {
    if (this.cargaDocumentosDiferidaId === null) {
      return;
    }
    globalThis.window?.clearTimeout(this.cargaDocumentosDiferidaId);
    this.cargaDocumentosDiferidaId = null;
  }

  private programarCargaDocumentos(alumnoId: number, inmediata: boolean = false): void {
    if (!this.esAlumnoSeleccionado(alumnoId)) {
      return;
    }

    if (this.documentosCache.has(alumnoId)) {
      this.cargarDocumentosDelAlumno(alumnoId);
      return;
    }

    this.cancelarCargaDiferidaDocumentos();
    if (inmediata || !globalThis.window?.setTimeout) {
      this.cargarDocumentosDelAlumno(alumnoId);
      return;
    }

    this.cargaDocumentosDiferidaId = globalThis.window.setTimeout(() => {
      this.cargaDocumentosDiferidaId = null;
      this.cargarDocumentosDelAlumno(alumnoId);
    }, 350);
  }

  cargarGruposDelAlumno(alumnoId: number): void {
    const gruposCache = this.gruposCache.get(alumnoId);
    if (gruposCache) {
      this.grupos = gruposCache;
      return;
    }

    this.endpointsService.obtenerGruposDelAlumnoObservable(alumnoId).subscribe({
      next: (grupos) => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }
        const gruposNormalizados = Array.isArray(grupos) ? grupos : [];
        this.grupos = gruposNormalizados;
        this.gruposCache.set(alumnoId, gruposNormalizados);
      },
      error: () => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }
        this.grupos = [];
        Swal.fire({
          title: 'Error en la peticion',
          text: 'Error al obtener los grupos del alumno.',
          icon: 'error',
        });
      },
    });
  }

  cargarTurnosDelAlumno(alumnoId: number): void {
    const turnosCache = this.turnosCache.get(alumnoId);
    if (turnosCache) {
      this.turnosAlumno = turnosCache;
      this.cargandoTurnos = false;
      this.actualizarProximaClase();
      return;
    }

    this.cargandoTurnos = true;
    this.endpointsService.obtenerTurnosDelAlumnoObservable(alumnoId).subscribe({
      next: (turnos: any[]) => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }
        const turnosNormalizados = this.normalizarTurnos(turnos);
        this.turnosAlumno = turnosNormalizados;
        this.turnosCache.set(alumnoId, turnosNormalizados);
        this.cargandoTurnos = false;
        this.actualizarProximaClase();
      },
      error: () => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }
        this.turnosAlumno = [];
        this.proximaClase = null;
        this.proximasClasesPorDeporte = [];
        this.cargandoTurnos = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los horarios del alumno',
          icon: 'error',
        });
      },
    });
  }

  private inicializarNovedadesEventos(): void {
    const eventosSubscription = this.endpointsService.eventos$.subscribe({
      next: (eventos) => {
        const listaEventos = Array.isArray(eventos) ? eventos : [];
        if (!this.primeraEmisionEventosRecibida) {
          this.primeraEmisionEventosRecibida = true;
          // Ignora la emision inicial vacia del BehaviorSubject para no
          // pisar el snapshot de "vistos" al recargar o iniciar sesion.
          if (listaEventos.length === 0) {
            return;
          }
        }
        this.eventosRecientes = listaEventos.slice(0, 3);
        this.actualizarNovedadesEventos(listaEventos);
      },
      error: () => {
        this.eventosRecientes = [];
        this.novedadesEventos = 0;
      },
    });

    this.subscriptions.add(eventosSubscription);
    this.endpointsService.obtenerEventos();
  }

  private actualizarNovedadesEventos(eventos: any[]): void {
    const idsActuales = this.obtenerIdsNumericos(eventos.map((evento) => evento?.id));
    const storageKey = 'dashboard-user-vistos-eventos';
    const idsVistos = this.obtenerIdsVistos(storageKey);

    if (this.esCargaVaciaTransitoria(idsActuales, idsVistos)) {
      this.novedadesEventos = 0;
      return;
    }

    if (idsVistos === null) {
      this.novedadesEventos = 0;
      this.guardarIdsVistos(storageKey, idsActuales);
      return;
    }

    this.novedadesEventos = idsActuales.filter((id) => !idsVistos.has(id)).length;
    this.guardarIdsVistos(storageKey, idsActuales);
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
    const deportesCache = this.deportesCache.get(alumnoId);
    if (deportesCache) {
      this.deportesDelAlumno = deportesCache;
      this.retoDiarioActual = this.getRetoDiarioSegunFecha(new Date(), alumnoId);
      this.actualizarNovedadesEstadoDeportes(alumnoId, this.deportesDelAlumno);
      this.prepararRankingSemanalDesdeDeportes(alumnoId);
      this.cargandoDeportes = false;
      return;
    }

    this.cargandoDeportes = true;
    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }

        const deportesNormalizados = deportes ?? [];
        this.deportesDelAlumno = deportesNormalizados;
        this.deportesCache.set(alumnoId, deportesNormalizados);
        this.retoDiarioActual = this.getRetoDiarioSegunFecha(new Date(), alumnoId);
        this.actualizarNovedadesEstadoDeportes(alumnoId, this.deportesDelAlumno);
        this.prepararRankingSemanalDesdeDeportes(alumnoId);
        this.cargandoDeportes = false;
      },
      error: () => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }

        this.deportesDelAlumno = [];
        this.retoDiarioActual = this.getRetoDiarioSegunFecha(new Date(), alumnoId);
        this.novedadesEstadoDeportes = 0;
        this.detallesNovedadesEstado = [];
        this.resetearRankingSemanalRetoDiario();
        this.cargandoDeportes = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los deportes del alumno',
          icon: 'error',
        });
      },
    });
  }

  cargarDocumentosDelAlumno(alumnoId: number): void {
    const documentosCache = this.documentosCache.get(alumnoId);
    if (documentosCache) {
      this.documentosAlumno = documentosCache;
      this.actualizarNovedadesDocumentos(alumnoId, this.documentosAlumno);
      this.reiniciarPaginacionDocumentos();
      if (this.documentosSeccionVisible) {
        this.marcarDocumentosComoVistos();
      }
      this.cargandoDocumentos = false;
      return;
    }

    this.cargandoDocumentos = true;
    this.endpointsService.obtenerDocumentosDeAlumno(alumnoId).subscribe({
      next: (documentos: Documento[]) => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }

        const documentosNormalizados = documentos ?? [];
        this.documentosAlumno = documentosNormalizados;
        this.documentosCache.set(alumnoId, documentosNormalizados);
        this.actualizarNovedadesDocumentos(alumnoId, this.documentosAlumno);
        this.reiniciarPaginacionDocumentos();
        if (this.documentosSeccionVisible) {
          this.marcarDocumentosComoVistos();
        }
        this.cargandoDocumentos = false;
      },
      error: () => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }

        this.documentosAlumno = [];
        this.novedadesDocumentos = 0;
        this.reiniciarPaginacionDocumentos();
        this.cargandoDocumentos = false;
        Swal.fire({
          title: 'Error',
          text: 'No se pudieron cargar los documentos del alumno',
          icon: 'error',
        });
      },
    });
  }

  abrirDocumento(documento: Documento): void {
    if (!this.esDocumentoPrevisualizable(documento?.tipo)) {
      this.descargarDocumento(documento);
      return;
    }
    this.descargarDocumento(documento, true);
  }

  private esDispositivoIOS(): boolean {
    const navigatorRef = globalThis.navigator;
    if (!navigatorRef) {
      return false;
    }

    const userAgent = navigatorRef.userAgent ?? '';
    const esIOSClasico = /iPad|iPhone|iPod/.test(userAgent);
    const esIPadOS = navigatorRef.platform === 'MacIntel' && navigatorRef.maxTouchPoints > 1;
    return esIOSClasico || esIPadOS;
  }

  descargarDocumento(documento: Documento, abrirEnNuevaPestana: boolean = false): void {
    const alumnoId = this.selectedAlumno?.id;
    if (!alumnoId || !documento?.id) {
      Swal.fire({
        title: 'Error',
        text: 'No se pudo procesar el documento seleccionado',
        icon: 'error',
      });
      return;
    }

    const forzarDescarga = !abrirEnNuevaPestana;
    if (this.esDispositivoIOS()) {
      const downloadUrl = this.endpointsService.obtenerUrlDescargaDocumentoAlumno(
        alumnoId,
        documento.id,
        forzarDescarga
      );
      if (abrirEnNuevaPestana) {
        globalThis.window?.open(downloadUrl, '_blank', 'noopener');
      } else {
        globalThis.window?.location.assign(downloadUrl);
      }
      return;
    }

    this.endpointsService.descargarDocumentoAlumno(alumnoId, documento.id, forzarDescarga).subscribe({
      next: (blob) => {
        const url = globalThis.URL.createObjectURL(blob);

        if (abrirEnNuevaPestana) {
          globalThis.window?.open(url, '_blank', 'noopener');
          setTimeout(() => globalThis.URL.revokeObjectURL(url), 60_000);
          return;
        }

        const link = globalThis.document?.createElement('a');
        if (!link) {
          globalThis.URL.revokeObjectURL(url);
          return;
        }

        link.href = url;
        link.download = this.obtenerNombreDescarga(documento.nombre, documento.tipo);
        link.click();
        globalThis.URL.revokeObjectURL(url);
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo descargar el documento',
          icon: 'error',
        });
      },
    });
  }

  private obtenerNombreDescarga(
    nombre: string | null | undefined,
    mimeType: string | null | undefined
  ): string {
    const base = (nombre ?? '').trim() || 'documento';
    if (base.includes('.')) {
      return base;
    }
    const extension = this.obtenerExtensionDesdeMime(mimeType);
    return extension ? `${base}.${extension}` : base;
  }

  esDocumentoPrevisualizable(mimeType: string | null | undefined): boolean {
    const mime = (mimeType ?? '').split(';')[0].trim().toLowerCase();
    if (!mime) {
      return false;
    }

    if (mime.startsWith('image/')) {
      return true;
    }

    switch (mime) {
      case 'application/pdf':
      case 'text/plain':
      case 'text/csv':
      case 'application/json':
      case 'text/xml':
      case 'application/xml':
        return true;
      default:
        return false;
    }
  }

  private obtenerExtensionDesdeMime(mimeType: string | null | undefined): string | null {
    const mime = (mimeType ?? '').split(';')[0].trim().toLowerCase();
    switch (mime) {
      case 'application/pdf':
        return 'pdf';
      case 'text/csv':
        return 'csv';
      case 'text/plain':
        return 'txt';
      case 'image/jpeg':
        return 'jpg';
      case 'image/png':
        return 'png';
      case 'image/webp':
        return 'webp';
      case 'image/gif':
        return 'gif';
      case 'application/msword':
        return 'doc';
      case 'application/vnd.openxmlformats-officedocument.wordprocessingml.document':
        return 'docx';
      case 'application/vnd.ms-excel':
        return 'xls';
      case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
        return 'xlsx';
      default:
        return null;
    }
  }

  getTotalNovedades(): number {
    return this.novedadesDocumentos + this.novedadesEventos + this.novedadesEstadoDeportes;
  }

  completarRetoDiario(): void {
    if (this.retoCompletadoHoy) {
      return;
    }

    const alumnoId = Number(this.selectedAlumno?.id);
    if (!Number.isInteger(alumnoId) || alumnoId <= 0) {
      return;
    }

    this.endpointsService.completarRetoDiario(alumnoId).subscribe({
      next: (estado: RetoDiarioEstado) => {
        this.aplicarEstadoRetoDiario(estado);
        if (this.deporteRankingSeleccionado) {
          this.cargarRankingSemanalRetoDiario(alumnoId, this.deporteRankingSeleccionado);
        }
      },
      error: () => {
        Swal.fire({
          title: 'Error',
          text: 'No se pudo registrar el reto diario',
          icon: 'error',
        });
      },
    });
  }

  onToggleRecordatorioRachaEmail(event: Event): void {
    if (this.guardandoRecordatorioRachaEmail || this.cargandoRecordatorioRachaEmail) {
      return;
    }

    const target = event.target;
    if (!(target instanceof HTMLInputElement)) {
      return;
    }

    const siguienteEstado = target.checked;
    const estadoPrevio = this.recordatorioRachaEmailHabilitado;
    if (siguienteEstado === estadoPrevio) {
      return;
    }

    this.recordatorioRachaEmailHabilitado = siguienteEstado;
    this.guardandoRecordatorioRachaEmail = true;

    this.authService.actualizarRecordatorioRachaEmail(siguienteEstado).subscribe({
      next: (response) => {
        this.recordatorioRachaEmailHabilitado = !!response?.habilitado;
        Swal.fire({
          title: 'Recordatorio actualizado',
          text: this.recordatorioRachaEmailHabilitado
            ? 'Te avisaremos por email cuando queden menos de 3 horas.'
            : 'Has desactivado los recordatorios por email.',
          icon: 'success',
          timer: 1800,
          showConfirmButton: false,
        });
      },
      error: () => {
        this.recordatorioRachaEmailHabilitado = estadoPrevio;
        Swal.fire({
          title: 'Error',
          text: 'No se pudo actualizar el recordatorio de racha.',
          icon: 'error',
        });
      },
      complete: () => {
        this.guardandoRecordatorioRachaEmail = false;
      },
    });
  }

  onSeleccionarDeporteRanking(deporte: string): void {
    const deporteNormalizado = (deporte || '').trim().toUpperCase();
    if (!deporteNormalizado || this.deporteRankingSeleccionado === deporteNormalizado) {
      return;
    }
    if (!this.deportesRankingDisponibles.includes(deporteNormalizado)) {
      return;
    }

    const alumnoId = Number(this.selectedAlumno?.id);
    if (!Number.isInteger(alumnoId) || alumnoId <= 0) {
      return;
    }

    this.deporteRankingSeleccionado = deporteNormalizado;
    this.cargarRankingSemanalRetoDiario(alumnoId, deporteNormalizado);
  }

  getTextoDiasParaSuperarSiguiente(): string {
    const dias = this.rankingSemanal?.miPosicion?.diasParaSuperarSiguiente;
    if (dias === null || dias === undefined) {
      return '';
    }
    return `Te faltan ${dias} día(s) para superar al siguiente.`;
  }

  private prepararRankingSemanalDesdeDeportes(alumnoId: number): void {
    const deportesActivos = this.obtenerDeportesActivosParaRanking();
    this.deportesRankingDisponibles = deportesActivos;

    if (deportesActivos.length === 0) {
      this.rankingSemanal = null;
      this.errorRankingSemanal = null;
      this.cargandoRankingSemanal = false;
      this.deporteRankingSeleccionado = null;
      return;
    }

    if (!this.deporteRankingSeleccionado || !deportesActivos.includes(this.deporteRankingSeleccionado)) {
      const deportePrincipal = this.deportesDelAlumno
        .find((deporteItem) => deporteItem?.activo !== false && deporteItem?.principal === true)?.deporte;
      const deportePrincipalNormalizado = (deportePrincipal || '').trim().toUpperCase();
      this.deporteRankingSeleccionado = deportesActivos.includes(deportePrincipalNormalizado)
        ? deportePrincipalNormalizado
        : deportesActivos[0];
    }

    this.cargarRankingSemanalRetoDiario(alumnoId, this.deporteRankingSeleccionado);
  }

  private obtenerDeportesActivosParaRanking(): string[] {
    const deportesActivosOrdenados = (this.deportesDelAlumno ?? [])
      .filter((deporteItem) => deporteItem?.activo !== false && typeof deporteItem?.deporte === 'string')
      .sort((a, b) => {
        if (!!a?.principal === !!b?.principal) {
          return 0;
        }
        return a?.principal ? -1 : 1;
      })
      .map((deporteItem) => String(deporteItem.deporte).trim().toUpperCase())
      .filter((deporte) => deporte.length > 0);

    return Array.from(new Set(deportesActivosOrdenados));
  }

  private cargarRankingSemanalRetoDiario(alumnoId: number, deporte: string): void {
    const deporteNormalizado = (deporte || '').trim().toUpperCase();
    if (!deporteNormalizado) {
      return;
    }

    this.cargandoRankingSemanal = true;
    this.errorRankingSemanal = null;

    this.endpointsService.obtenerRankingRetoDiarioSemanal(alumnoId, deporteNormalizado, 5).subscribe({
      next: (ranking) => {
        if (!this.esAlumnoSeleccionado(alumnoId) || this.deporteRankingSeleccionado !== deporteNormalizado) {
          return;
        }
        if (!ranking) {
          this.rankingSemanal = null;
          this.errorRankingSemanal = 'No hay datos de ranking disponibles.';
          this.cargandoRankingSemanal = false;
          return;
        }

        const top = Array.isArray(ranking?.top) ? ranking.top : [];
        this.rankingSemanal = {
          ...ranking,
          deporte: ranking?.deporte || deporteNormalizado,
          top,
        };
        this.cargandoRankingSemanal = false;
      },
      error: () => {
        if (!this.esAlumnoSeleccionado(alumnoId) || this.deporteRankingSeleccionado !== deporteNormalizado) {
          return;
        }
        this.rankingSemanal = null;
        this.errorRankingSemanal = 'No se pudo cargar el ranking semanal.';
        this.cargandoRankingSemanal = false;
      },
    });
  }

  private resetearRankingSemanalRetoDiario(): void {
    this.rankingSemanal = null;
    this.cargandoRankingSemanal = false;
    this.errorRankingSemanal = null;
    this.deporteRankingSeleccionado = null;
    this.deportesRankingDisponibles = [];
  }

  getResumenEstadoExamen(): string {
    const aptos = this.getCantidadDeportesAptosExaminables();
    const total = this.getCantidadDeportesExaminables();
    if (total === 0) {
      return 'Sin deportes examinables';
    }
    return `Apto en ${aptos}/${total} deportes`;
  }

  getEstadoConvocatoriaTexto(): string {
    if (this.cargandoConvocatorias) {
      return 'Cargando convocatoria...';
    }
    if (!this.proximaConvocatoriaAlumno) {
      return 'No apuntado a convocatoria';
    }
    const fecha = this.formatearFecha(this.proximaConvocatoriaAlumno.fechaConvocatoria);
    return `Apuntado - ${fecha}`;
  }

  getProximaConvocatoriaDeporteLabel(): string {
    if (!this.proximaConvocatoriaAlumno?.deporte) {
      return '';
    }
    return this.getDeporteLabel(this.proximaConvocatoriaAlumno.deporte);
  }

  toggleConvocatoriasSecundarias(): void {
    if (this.convocatoriasSecundarias.length === 0) {
      return;
    }
    this.mostrarConvocatoriasSecundarias = !this.mostrarConvocatoriasSecundarias;
  }

  getDocumentosVisibles(): Documento[] {
    return this.documentosAlumno.slice(0, this.documentosVisiblesCount);
  }

  hayMasDocumentosPorCargar(): boolean {
    return this.documentosVisiblesCount < this.documentosAlumno.length;
  }

  cargarMasDocumentos(): void {
    if (!this.hayMasDocumentosPorCargar()) {
      return;
    }
    this.documentosVisiblesCount = Math.min(
      this.documentosVisiblesCount + this.documentosPageSize,
      this.documentosAlumno.length
    );
  }

  private actualizarNovedadesDocumentos(alumnoId: number, documentos: Documento[]): void {
    const storageKey = this.getStorageKeyDocumentos(alumnoId);
    const idsActuales = this.obtenerIdsNumericos(documentos.map((documento) => documento?.id));
    const idsVistos = this.obtenerIdsVistos(storageKey);

    if (this.esCargaVaciaTransitoria(idsActuales, idsVistos)) {
      this.novedadesDocumentos = 0;
      return;
    }

    if (idsVistos === null) {
      this.novedadesDocumentos = 0;
      this.guardarIdsVistos(storageKey, idsActuales);
      return;
    }

    this.novedadesDocumentos = idsActuales.filter((id) => !idsVistos.has(id)).length;
    this.guardarIdsVistos(storageKey, idsActuales);
  }

  private actualizarNovedadesEstadoDeportes(alumnoId: number, deportes: AlumnoDeporteDTO[]): void {
    const storageKey = `dashboard-user-estado-deportes-${alumnoId}`;
    const estadoActual = this.generarSnapshotEstadoDeportes(deportes);
    const estadoPrevio = this.obtenerEstadoDeportesVisto(storageKey);

    if (estadoPrevio !== null && estadoPrevio.length > 0 && estadoActual.length === 0) {
      this.novedadesEstadoDeportes = 0;
      this.detallesNovedadesEstado = [];
      return;
    }

    if (estadoPrevio === null) {
      this.novedadesEstadoDeportes = 0;
      this.detallesNovedadesEstado = [];
      this.guardarEstadoDeportesVisto(storageKey, estadoActual);
      return;
    }

    const comparacion = this.compararEstadoDeportes(estadoPrevio, estadoActual);
    this.novedadesEstadoDeportes = comparacion.total;
    this.detallesNovedadesEstado = comparacion.detalles.slice(0, 3);
    this.guardarEstadoDeportesVisto(storageKey, estadoActual);
  }

  private generarSnapshotEstadoDeportes(deportes: AlumnoDeporteDTO[]): EstadoDeporteSnapshot[] {
    return deportes.map((deporte) => ({
      deporteId: Number.isInteger(Number(deporte.id)) && Number(deporte.id) > 0 ? Number(deporte.id) : null,
      deporte: (deporte.deporte || '').toString().toUpperCase(),
      grado: deporte.grado ?? null,
      aptoParaExamen: !!deporte.aptoParaExamen,
    }));
  }

  private compararEstadoDeportes(
    estadoPrevio: EstadoDeporteSnapshot[],
    estadoActual: EstadoDeporteSnapshot[]
  ): { total: number; detalles: string[] } {
    const previoMap = new Map(
      estadoPrevio.map((item) => [this.getClaveEstadoDeporte(item), item])
    );
    const actualMap = new Map(
      estadoActual.map((item) => [this.getClaveEstadoDeporte(item), item])
    );

    let total = 0;
    const detalles: string[] = [];

    estadoActual.forEach((actual) => {
      const clave = this.getClaveEstadoDeporte(actual);
      const previo = previoMap.get(clave);
      const deporteLabel = this.getDeporteLabel(actual.deporte || 'Deporte');

      if (!previo) {
        total += 1;
        detalles.push(`${deporteLabel}: nuevo deporte asignado`);
        return;
      }

      if ((previo.grado || '') !== (actual.grado || '')) {
        total += 1;
        detalles.push(`${deporteLabel}: grado actualizado a ${this.formatearGradoNovedad(actual.grado, actual.deporte)}`);
      }

      if (
        this.deporteUsaEstadoExamen(actual.deporte) &&
        previo.aptoParaExamen !== actual.aptoParaExamen
      ) {
        total += 1;
        detalles.push(
          `${deporteLabel}: ${actual.aptoParaExamen ? 'ahora apto para examen' : 'ya no apto para examen'}`
        );
      }
    });

    estadoPrevio.forEach((previo) => {
      const clave = this.getClaveEstadoDeporte(previo);
      if (actualMap.has(clave)) {
        return;
      }
      total += 1;
      detalles.push(`${this.getDeporteLabel(previo.deporte || 'Deporte')}: deporte desasignado`);
    });

    return { total, detalles };
  }

  private getClaveEstadoDeporte(item: EstadoDeporteSnapshot): string {
    if (item.deporteId && item.deporteId > 0) {
      return `id:${item.deporteId}`;
    }
    return `deporte:${this.normalizarTexto(item.deporte)}`;
  }

  private deporteUsaEstadoExamen(deporte: string): boolean {
    const deporteNormalizado = (deporte || '').toString().toUpperCase().trim();
    return deporteNormalizado !== 'PILATES' && deporteNormalizado !== 'DEFENSA_PERSONAL_FEMENINA';
  }

  private formatearGradoNovedad(grado: string | null, deporte: string): string {
    if (!grado) {
      return 'sin grado';
    }
    const label = this.getBeltLabel(grado, deporte);
    return (label || grado).toLowerCase();
  }

  private obtenerEstadoDeportesVisto(storageKey: string): EstadoDeporteSnapshot[] | null {
    const storage = this.obtenerStorageSeguro();
    if (!storage) {
      return null;
    }

    const rawValue = storage.getItem(storageKey);
    if (!rawValue) {
      return null;
    }

    try {
      const parsed = JSON.parse(rawValue);
      if (!Array.isArray(parsed)) {
        return null;
      }

      return parsed.map((item: any) => ({
        deporteId:
          Number.isInteger(Number(item?.deporteId)) && Number(item?.deporteId) > 0
            ? Number(item.deporteId)
            : null,
        deporte: (item?.deporte || '').toString().toUpperCase(),
        grado: item?.grado ? String(item.grado) : null,
        aptoParaExamen: !!item?.aptoParaExamen,
      }));
    } catch {
      return null;
    }
  }

  private guardarEstadoDeportesVisto(storageKey: string, estado: EstadoDeporteSnapshot[]): void {
    const storage = this.obtenerStorageSeguro();
    if (!storage) {
      return;
    }
    storage.setItem(storageKey, JSON.stringify(estado));
  }

  private obtenerStorageSeguro(): Storage | null {
    return globalThis.window?.localStorage ?? null;
  }

  private obtenerIdsVistos(storageKey: string): Set<number> | null {
    const storage = this.obtenerStorageSeguro();
    if (!storage) {
      return null;
    }

    const rawValue = storage.getItem(storageKey);
    if (!rawValue) {
      return null;
    }

    try {
      const parsed = JSON.parse(rawValue);
      if (!Array.isArray(parsed)) {
        return null;
      }
      return new Set(this.obtenerIdsNumericos(parsed));
    } catch {
      return null;
    }
  }

  private guardarIdsVistos(storageKey: string, ids: number[]): void {
    const storage = this.obtenerStorageSeguro();
    if (!storage) {
      return;
    }
    storage.setItem(storageKey, JSON.stringify(ids));
  }

  private getStorageKeyDocumentos(alumnoId: number): string {
    return `dashboard-user-vistos-documentos-${alumnoId}`;
  }

  private cargarConvocatoriasDelAlumno(alumnoId: number): void {
    const convocatoriasCache = this.convocatoriasCache.get(alumnoId);
    if (convocatoriasCache) {
      this.convocatoriasAlumno = convocatoriasCache;
      this.proximasConvocatoriasPorDeporte = this.obtenerProximasConvocatoriasPorDeporte(this.convocatoriasAlumno);
      this.proximaConvocatoriaAlumno = this.obtenerConvocatoriaProxima(this.proximasConvocatoriasPorDeporte);
      this.convocatoriasSecundarias = this.proximasConvocatoriasPorDeporte.slice(1);
      this.mostrarConvocatoriasSecundarias = false;
      this.cargandoConvocatorias = false;
      return;
    }

    this.cargandoConvocatorias = true;
    this.endpointsService.obtenerConvocatoriasDeAlumno(alumnoId).subscribe({
      next: (convocatorias) => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }

        const convocatoriasNormalizadas = Array.isArray(convocatorias) ? convocatorias : [];
        this.convocatoriasAlumno = convocatoriasNormalizadas;
        this.convocatoriasCache.set(alumnoId, convocatoriasNormalizadas);
        this.proximasConvocatoriasPorDeporte = this.obtenerProximasConvocatoriasPorDeporte(this.convocatoriasAlumno);
        this.proximaConvocatoriaAlumno = this.obtenerConvocatoriaProxima(this.proximasConvocatoriasPorDeporte);
        this.convocatoriasSecundarias = this.proximasConvocatoriasPorDeporte.slice(1);
        this.mostrarConvocatoriasSecundarias = false;
        this.cargandoConvocatorias = false;
      },
      error: () => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }

        this.convocatoriasAlumno = [];
        this.proximaConvocatoriaAlumno = null;
        this.proximasConvocatoriasPorDeporte = [];
        this.convocatoriasSecundarias = [];
        this.mostrarConvocatoriasSecundarias = false;
        this.cargandoConvocatorias = false;
      },
    });
  }

  private obtenerConvocatoriaProxima(convocatorias: ConvocatoriaDTO[]): ConvocatoriaDTO | null {
    if (!Array.isArray(convocatorias) || convocatorias.length === 0) {
      return null;
    }
    return convocatorias[0];
  }

  private obtenerProximasConvocatoriasPorDeporte(convocatorias: ConvocatoriaDTO[]): ConvocatoriaDTO[] {
    if (!Array.isArray(convocatorias) || convocatorias.length === 0) {
      return [];
    }

    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);

    const futurasOrdenadas = convocatorias
      .map((convocatoria) => ({
        convocatoria,
        fecha: this.parseFechaConvocatoria(convocatoria?.fechaConvocatoria),
      }))
      .filter((item) => item.fecha !== null && item.fecha.getTime() >= hoy.getTime())
      .sort((a, b) => a.fecha!.getTime() - b.fecha!.getTime());

    const porDeporte = new Map<string, ConvocatoriaDTO>();
    for (const item of futurasOrdenadas) {
      const deporteKey = this.normalizarTexto(item.convocatoria?.deporte || 'sin-deporte');
      if (!porDeporte.has(deporteKey)) {
        porDeporte.set(deporteKey, item.convocatoria);
      }
    }

    return Array.from(porDeporte.values());
  }

  private parseFechaConvocatoria(fecha: string | null | undefined): Date | null {
    if (!fecha) {
      return null;
    }

    const parsed = new Date(fecha);
    if (Number.isNaN(parsed.getTime())) {
      return null;
    }
    return parsed;
  }

  private inicializarRetoDiario(): void {
    this.retoDiarioActual = this.getRetoDiarioSegunFecha(new Date());
  }

  private cargarEstadoRetoDiario(alumnoId: number): void {
    this.retoDiarioActual = this.getRetoDiarioSegunFecha(new Date(), alumnoId);
    this.rachaRetoDiario = 0;
    this.retoCompletadoHoy = false;
    this.resetearCountdownRetoDiario();
    this.endpointsService.obtenerEstadoRetoDiario(alumnoId).subscribe({
      next: (estado: RetoDiarioEstado) => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }
        this.aplicarEstadoRetoDiario(estado);
        this.recargandoRetoTrasReset = false;
      },
      error: () => {
        if (!this.esAlumnoSeleccionado(alumnoId)) {
          return;
        }
        this.rachaRetoDiario = 0;
        this.retoCompletadoHoy = false;
        this.recargandoRetoTrasReset = false;
        this.resetearCountdownRetoDiario();
      },
    });
  }

  private cargarPreferenciaRecordatorioRachaEmail(): void {
    this.cargandoRecordatorioRachaEmail = true;
    this.authService.obtenerRecordatorioRachaEmail().subscribe({
      next: (response) => {
        this.recordatorioRachaEmailHabilitado = !!response?.habilitado;
        this.cargandoRecordatorioRachaEmail = false;
      },
      error: () => {
        this.recordatorioRachaEmailHabilitado = false;
        this.cargandoRecordatorioRachaEmail = false;
      },
    });
  }

  private getRetoDiarioSegunFecha(fecha: Date, alumnoId?: number): string {
    const retosGenerales = [...this.retosDiariosGenerales];
    const retosDeporte = this.obtenerRetosDiariosPorDeporteActivo();
    if (retosGenerales.length === 0 && retosDeporte.length === 0) {
      return '5 min de movilidad suave';
    }

    const alumnoIdNormalizado = this.normalizarAlumnoIdReto(alumnoId);
    const usaRetoDeporte = this.debeUsarRetoDeporte(fecha, alumnoIdNormalizado, retosDeporte.length > 0);
    const retosCandidatos = usaRetoDeporte
      ? retosDeporte
      : (retosGenerales.length > 0 ? retosGenerales : retosDeporte);
    const index = this.obtenerIndiceDeterminista(fecha, alumnoIdNormalizado, retosCandidatos.length, 'item');
    return retosCandidatos[index];
  }

  private debeUsarRetoDeporte(fecha: Date, alumnoId: number, hayRetosDeporte: boolean): boolean {
    if (!hayRetosDeporte) {
      return false;
    }
    const valorDeterminista = this.obtenerValorDeterminista(fecha, alumnoId, 'pool');
    return valorDeterminista < this.probabilidadRetoDeporte;
  }

  private obtenerRetosDiariosPorDeporteActivo(): string[] {
    const retos: string[] = [];
    const deportesActivos = Array.from(new Set(
      (this.deportesDelAlumno ?? [])
        .filter((deporteItem) => deporteItem?.activo !== false)
        .map((deporteItem) => this.normalizarClaveDeporteReto(deporteItem?.deporte))
        .filter((deporte) => deporte.length > 0)
    )).sort();

    deportesActivos.forEach((deporte) => {
      const retosDeporte = this.retosDiariosPorDeporte[deporte];
      if (Array.isArray(retosDeporte) && retosDeporte.length > 0) {
        retos.push(...retosDeporte);
      }
    });

    return retos;
  }

  private normalizarClaveDeporteReto(deporte: unknown): string {
    return String(deporte ?? '').trim().toUpperCase();
  }

  private normalizarAlumnoIdReto(alumnoId?: number): number {
    const candidato = Number(alumnoId ?? this.selectedAlumno?.id ?? 0);
    return Number.isInteger(candidato) && candidato > 0 ? candidato : 0;
  }

  private obtenerIndiceDeterminista(fecha: Date, alumnoId: number, longitud: number, canal: string): number {
    if (!Number.isFinite(longitud) || longitud <= 0) {
      return 0;
    }
    const valor = this.obtenerValorDeterminista(fecha, alumnoId, canal);
    return Math.min(longitud - 1, Math.floor(valor * longitud));
  }

  private obtenerValorDeterminista(fecha: Date, alumnoId: number, canal: string): number {
    const inicioDiaLocal = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
    const clave = `${alumnoId}|${inicioDiaLocal.getTime()}|${canal}`;
    const hash = this.hashDeterminista(clave);
    return hash / 4_294_967_296;
  }

  private hashDeterminista(valor: string): number {
    let hash = 2_169_136_261;
    for (let i = 0; i < valor.length; i += 1) {
      hash ^= valor.charCodeAt(i);
      hash = Math.imul(hash, 16_777_619);
    }
    return hash >>> 0;
  }

  private aplicarEstadoRetoDiario(estado: RetoDiarioEstado | null | undefined): void {
    const racha = Number(estado?.racha ?? 0);
    this.rachaRetoDiario = Number.isFinite(racha) ? Math.max(0, Math.floor(racha)) : 0;
    this.retoCompletadoHoy = !!estado?.completadoHoy;

    const nextResetRaw = Number(estado?.nextResetAtEpochMs);
    if (Number.isFinite(nextResetRaw) && nextResetRaw > 0) {
      this.nextRetoResetAtEpochMs = Math.floor(nextResetRaw);
    } else {
      this.nextRetoResetAtEpochMs = this.calcularProximoResetLocalEpochMs();
    }

    this.iniciarCountdownRetoDiario();
  }

  private iniciarCountdownRetoDiario(): void {
    if (!globalThis.window?.setInterval) {
      return;
    }

    this.detenerCountdownRetoDiario();
    this.actualizarTextoCountdownRetoDiario();
    this.retoCountdownIntervalId = globalThis.window.setInterval(() => {
      this.actualizarTextoCountdownRetoDiario();
    }, 1_000);
  }

  private detenerCountdownRetoDiario(): void {
    if (this.retoCountdownIntervalId !== null) {
      globalThis.window?.clearInterval(this.retoCountdownIntervalId);
      this.retoCountdownIntervalId = null;
    }
  }

  private resetearCountdownRetoDiario(): void {
    this.detenerCountdownRetoDiario();
    this.nextRetoResetAtEpochMs = null;
    this.retoCountdownTexto = '';
    this.retoCountdownUrgente = false;
  }

  private actualizarTextoCountdownRetoDiario(): void {
    if (!this.nextRetoResetAtEpochMs || this.nextRetoResetAtEpochMs <= 0) {
      this.retoCountdownTexto = '';
      this.retoCountdownUrgente = false;
      return;
    }

    const ahora = Date.now();
    const restanteMs = this.nextRetoResetAtEpochMs - ahora;

    if (restanteMs <= 0) {
      this.retoCountdownTexto = this.retoCompletadoHoy
        ? 'Cargando próximo reto...'
        : 'Actualizando estado de racha...';
      this.retoCountdownUrgente = false;
      this.refrescarRetoTrasCambioDia();
      return;
    }

    this.retoCountdownUrgente = restanteMs <= 3 * 60 * 60 * 1_000;
    const restanteFormateado = this.formatearDuracionCountdown(restanteMs);
    this.retoCountdownTexto = this.retoCompletadoHoy
      ? `Próximo reto en ${restanteFormateado}`
      : `Te quedan ${restanteFormateado} para mantener la racha`;
  }

  private refrescarRetoTrasCambioDia(): void {
    if (this.recargandoRetoTrasReset) {
      return;
    }

    const alumnoId = Number(this.selectedAlumno?.id);
    if (!Number.isInteger(alumnoId) || alumnoId <= 0) {
      return;
    }

    this.recargandoRetoTrasReset = true;
    this.retoDiarioActual = this.getRetoDiarioSegunFecha(new Date(), alumnoId);
    this.cargarEstadoRetoDiario(alumnoId);
  }

  private formatearDuracionCountdown(restanteMs: number): string {
    const totalSegundos = Math.max(0, Math.floor(restanteMs / 1_000));
    const dias = Math.floor(totalSegundos / 86_400);
    const horas = Math.floor((totalSegundos % 86_400) / 3_600);
    const minutos = Math.floor((totalSegundos % 3_600) / 60);
    const segundos = totalSegundos % 60;

    if (dias > 0) {
      return `${dias}d ${horas}h ${minutos}m`;
    }
    if (horas > 0) {
      return `${horas}h ${minutos}m ${segundos}s`;
    }
    if (minutos > 0) {
      return `${minutos}m ${segundos}s`;
    }
    return `${segundos}s`;
  }

  private calcularProximoResetLocalEpochMs(): number {
    const ahora = new Date();
    const proximoReset = new Date(ahora);
    proximoReset.setHours(24, 0, 0, 0);
    return proximoReset.getTime();
  }

  private marcarDocumentosComoVistos(): void {
    const alumnoId = Number(this.selectedAlumno?.id);
    if (!Number.isInteger(alumnoId) || alumnoId <= 0) {
      return;
    }

    const idsActuales = this.obtenerIdsNumericos(this.documentosAlumno.map((documento) => documento?.id));
    this.guardarIdsVistos(this.getStorageKeyDocumentos(alumnoId), idsActuales);
    this.novedadesDocumentos = 0;
  }

  private reiniciarPaginacionDocumentos(): void {
    this.documentosVisiblesCount = Math.min(this.documentosPageSize, this.documentosAlumno.length);
  }

  private configurarObservadorSeccionDocumentos(element: HTMLElement | null): void {
    if (this.documentosSectionObserver) {
      this.documentosSectionObserver.disconnect();
      this.documentosSectionObserver = null;
    }

    if (!element || !globalThis.window?.IntersectionObserver) {
      this.documentosSeccionVisible = false;
      return;
    }

    this.documentosSectionObserver = new globalThis.window.IntersectionObserver(
      (entradas) => {
        const entradaActiva = entradas[0];
        this.documentosSeccionVisible = !!entradaActiva?.isIntersecting;
        if (this.documentosSeccionVisible) {
          const alumnoId = Number(this.selectedAlumno?.id);
          if (Number.isInteger(alumnoId) && alumnoId > 0) {
            this.programarCargaDocumentos(alumnoId, true);
          }
          this.marcarDocumentosComoVistos();
        }
      },
      {
        threshold: 0.35,
      }
    );

    this.documentosSectionObserver.observe(element);
  }

  private configurarObservadorSentinelDocumentos(element: HTMLElement | null): void {
    if (this.documentosSentinelObserver) {
      this.documentosSentinelObserver.disconnect();
      this.documentosSentinelObserver = null;
    }

    if (!element || !globalThis.window?.IntersectionObserver) {
      return;
    }

    this.documentosSentinelObserver = new globalThis.window.IntersectionObserver(
      (entradas) => {
        if (entradas.some((entrada) => entrada.isIntersecting)) {
          this.cargarMasDocumentos();
        }
      },
      {
        rootMargin: '0px 0px 280px 0px',
        threshold: 0.01,
      }
    );

    this.documentosSentinelObserver.observe(element);
  }

  private desconectarObservadoresDocumentos(): void {
    if (this.documentosSectionObserver) {
      this.documentosSectionObserver.disconnect();
      this.documentosSectionObserver = null;
    }
    if (this.documentosSentinelObserver) {
      this.documentosSentinelObserver.disconnect();
      this.documentosSentinelObserver = null;
    }
  }

  private obtenerIdsNumericos(values: any[]): number[] {
    return values
      .map((value) => Number(value))
      .filter((value) => Number.isInteger(value) && value > 0);
  }

  private esCargaVaciaTransitoria(idsActuales: number[], idsVistos: Set<number> | null): boolean {
    return idsActuales.length === 0 && idsVistos !== null && idsVistos.size > 0;
  }

  private iniciarActualizacionProximaClase(): void {
    if (!globalThis.window?.setInterval) {
      return;
    }

    this.detenerActualizacionProximaClase();
    this.countdownIntervalId = globalThis.window.setInterval(() => {
      if (this.turnosAlumno.length > 0) {
        this.actualizarProximaClase();
      }
    }, 15_000);
  }

  private detenerActualizacionProximaClase(): void {
    if (this.countdownIntervalId !== null) {
      globalThis.window?.clearInterval(this.countdownIntervalId);
      this.countdownIntervalId = null;
    }
  }

  private normalizarTurnos(turnos: any[]): Turno[] {
    if (!Array.isArray(turnos)) {
      return [];
    }

    return turnos
      .filter((turno) => turno?.diaSemana && turno?.horaInicio && turno?.horaFin)
      .map((turno) => ({
        id: Number(turno.id ?? 0),
        diaSemana: String(turno.diaSemana),
        horaInicio: String(turno.horaInicio),
        horaFin: String(turno.horaFin),
        tipoGrupo: String(turno.tipoGrupo ?? turno.deporte ?? 'Clase'),
      }));
  }

  private actualizarProximaClase(): void {
    if (!this.turnosAlumno.length) {
      this.proximaClase = null;
      this.proximasClasesPorDeporte = [];
      return;
    }

    const ahora = new Date();
    const candidatos = this.turnosAlumno
      .map((turno) => this.construirProximaClase(turno, ahora))
      .filter((candidato): candidato is ProximaClaseData => candidato !== null)
      .sort((a, b) => {
        if (a.enCurso !== b.enCurso) {
          return a.enCurso ? -1 : 1;
        }
        return a.inicio.getTime() - b.inicio.getTime();
      });

    this.proximaClase = candidatos[0] ?? null;
    this.proximasClasesPorDeporte = this.construirProximasClasesPorDeporte(
      candidatos,
      this.proximaClase
    );
  }

  private construirProximasClasesPorDeporte(
    candidatos: ProximaClaseData[],
    claseGlobal: ProximaClaseData | null
  ): ProximaClasePorDeporte[] {
    const porDeporte = new Map<string, ProximaClaseData[]>();

    candidatos.forEach((candidato) => {
      const sportKey = this.getSportKeyFromTurno(candidato.turno);
      const acumuladas = porDeporte.get(sportKey) ?? [];
      acumuladas.push(candidato);
      porDeporte.set(sportKey, acumuladas);
    });

    const filas: ProximaClasePorDeporte[] = [];

    porDeporte.forEach((clases, sportKey) => {
      const siguiente = clases.find((clase) => !this.esMismaClase(clase, claseGlobal));
      if (!siguiente) {
        return;
      }

      filas.push({
        sportKey,
        sportLabel: this.getSportLabelByKey(sportKey),
        sportIcon: this.getSportIconByKey(sportKey),
        clase: siguiente,
      });
    });

    return filas.sort((a, b) => a.clase.inicio.getTime() - b.clase.inicio.getTime());
  }

  private esMismaClase(a: ProximaClaseData, b: ProximaClaseData | null): boolean {
    if (!b) {
      return false;
    }
    if (a === b) {
      return true;
    }

    const mismoHorario =
      a.inicio.getTime() === b.inicio.getTime() &&
      a.fin.getTime() === b.fin.getTime();

    const idA = Number(a.turno?.id ?? 0);
    const idB = Number(b.turno?.id ?? 0);
    if (idA > 0 && idB > 0) {
      return idA === idB && mismoHorario;
    }

    return (
      mismoHorario &&
      this.getSportKeyFromTurno(a.turno) === this.getSportKeyFromTurno(b.turno)
    );
  }

  private getSportKeyFromTurno(turno: Turno): string {
    return this.getClaveDeporteGrupo({
      deporte: turno.tipoGrupo,
      tipoGrupo: turno.tipoGrupo,
    });
  }

  private getSportLabelByKey(sportKey: string): string {
    if (sportKey === 'competicion') return 'Tkd. Competicion';
    if (sportKey === 'taekwondo') return 'Taekwondo';
    if (sportKey === 'kickboxing') return 'Kickboxing';
    if (sportKey === 'pilates') return 'Pilates';
    if (sportKey === 'defensa') return 'D.P. Femenina';
    return 'Otros';
  }

  private getSportIconByKey(sportKey: string): string {
    if (sportKey === 'competicion') return 'bi-trophy-fill';
    if (sportKey === 'taekwondo') return 'bi-lightning-charge-fill';
    if (sportKey === 'kickboxing') return 'bi-fire';
    if (sportKey === 'pilates') return 'bi-heart-pulse-fill';
    if (sportKey === 'defensa') return 'bi-shield-fill-check';
    return 'bi-star-fill';
  }

  private construirProximaClase(turno: Turno, ahora: Date): ProximaClaseData | null {
    const diaNumero = this.obtenerNumeroDia(turno.diaSemana);
    const horaInicio = this.obtenerPartesHora(turno.horaInicio);
    const horaFin = this.obtenerPartesHora(turno.horaFin);

    if (diaNumero === null || !horaInicio || !horaFin) {
      return null;
    }

    const hoyMismoDia = ahora.getDay() === diaNumero;
    const inicioHoy = new Date(ahora);
    inicioHoy.setHours(horaInicio.hora, horaInicio.minuto, 0, 0);
    const finHoy = new Date(ahora);
    finHoy.setHours(horaFin.hora, horaFin.minuto, 0, 0);
    if (finHoy <= inicioHoy) {
      finHoy.setDate(finHoy.getDate() + 1);
    }

    let inicio = inicioHoy;
    let fin = finHoy;
    let enCurso = false;

    if (hoyMismoDia && ahora >= inicioHoy && ahora < finHoy) {
      enCurso = true;
    } else {
      const diasHastaTurno = this.calcularDiasHastaTurno(diaNumero, ahora);
      inicio = new Date(ahora);
      inicio.setDate(ahora.getDate() + diasHastaTurno);
      inicio.setHours(horaInicio.hora, horaInicio.minuto, 0, 0);

      if (inicio <= ahora) {
        inicio.setDate(inicio.getDate() + 7);
      }

      fin = new Date(inicio);
      fin.setHours(horaFin.hora, horaFin.minuto, 0, 0);
      if (fin <= inicio) {
        fin.setDate(fin.getDate() + 1);
      }
    }

    const deporte = this.obtenerNombreDeporte({
      deporte: turno.tipoGrupo,
      tipoGrupo: turno.tipoGrupo,
    });

    return {
      turno,
      inicio,
      fin,
      enCurso,
      etiquetaDia: this.formatearEtiquetaDia(inicio, ahora, enCurso),
      horario: `${turno.horaInicio} - ${turno.horaFin}`,
      cuentaAtras: this.formatearCuentaAtras(inicio, fin, ahora, enCurso),
      deporte,
    };
  }

  private obtenerNumeroDia(diaSemana: string): number | null {
    const dia = this.normalizarTexto(diaSemana);

    if (dia === 'domingo') return 0;
    if (dia === 'lunes') return 1;
    if (dia === 'martes') return 2;
    if (dia === 'miercoles') return 3;
    if (dia === 'jueves') return 4;
    if (dia === 'viernes') return 5;
    if (dia === 'sabado') return 6;

    return null;
  }

  private obtenerPartesHora(hora: string): { hora: number; minuto: number } | null {
    if (!hora || !hora.includes(':')) {
      return null;
    }

    const [horaRaw, minutoRaw] = hora.split(':');
    const horaNumero = Number(horaRaw);
    const minutoNumero = Number(minutoRaw);

    if (!Number.isInteger(horaNumero) || !Number.isInteger(minutoNumero)) {
      return null;
    }
    if (horaNumero < 0 || horaNumero > 23 || minutoNumero < 0 || minutoNumero > 59) {
      return null;
    }

    return { hora: horaNumero, minuto: minutoNumero };
  }

  private calcularDiasHastaTurno(diaObjetivo: number, ahora: Date): number {
    const diaActual = ahora.getDay();
    return (diaObjetivo - diaActual + 7) % 7;
  }

  private formatearEtiquetaDia(fecha: Date, ahora: Date, enCurso: boolean): string {
    if (enCurso) {
      return 'Ahora mismo';
    }

    const inicioDiaActual = new Date(ahora);
    inicioDiaActual.setHours(0, 0, 0, 0);
    const inicioManana = new Date(inicioDiaActual);
    inicioManana.setDate(inicioDiaActual.getDate() + 1);

    const fechaNormalizada = new Date(fecha);
    fechaNormalizada.setHours(0, 0, 0, 0);

    if (fechaNormalizada.getTime() === inicioDiaActual.getTime()) {
      return 'Hoy';
    }
    if (fechaNormalizada.getTime() === inicioManana.getTime()) {
      return 'Mañana';
    }

    return fecha.toLocaleDateString('es-ES', { weekday: 'long' });
  }

  private formatearCuentaAtras(inicio: Date, fin: Date, ahora: Date, enCurso: boolean): string {
    const objetivo = enCurso ? fin.getTime() : inicio.getTime();
    const diferenciaMs = Math.max(0, objetivo - ahora.getTime());
    const diferenciaMinutos = Math.ceil(diferenciaMs / 60_000);

    if (diferenciaMinutos === 0) {
      return enCurso ? 'Finalizando' : 'Comienza en breve';
    }

    const dias = Math.floor(diferenciaMinutos / (60 * 24));
    const horas = Math.floor((diferenciaMinutos - dias * 60 * 24) / 60);
    const minutos = diferenciaMinutos % 60;

    let duracion = '';
    if (dias > 0) {
      duracion = `${dias}d ${horas}h`;
    } else if (horas > 0) {
      duracion = `${horas}h ${minutos}m`;
    } else {
      duracion = `${minutos}m`;
    }

    return enCurso ? `Termina en ${duracion}` : `Empieza en ${duracion}`;
  }

  private normalizarTexto(valor: string): string {
    return (valor || '')
      .toString()
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
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

  getCantidadDeportesAptosExaminables(): number {
    return this.deportesDelAlumno.filter(
      (deporte) => this.deporteUsaEstadoExamen(deporte.deporte) && deporte.aptoParaExamen
    ).length;
  }

  getCantidadDeportesExaminables(): number {
    return this.deportesDelAlumno.filter((deporte) => this.deporteUsaEstadoExamen(deporte.deporte)).length;
  }
}
