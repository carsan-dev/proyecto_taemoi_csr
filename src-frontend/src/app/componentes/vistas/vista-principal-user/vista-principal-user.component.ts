import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs/internal/Subscription';
import Swal from 'sweetalert2';

import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { EndpointsService } from '../../../servicios/endpoints/endpoints.service';
import { AlumnoService } from '../../../features/alumno/services/alumno.service';
import { AlumnoDeporteDTO } from '../../../interfaces/alumno-deporte-dto';
import { Documento } from '../../../interfaces/documento';
import { Turno } from '../../../interfaces/turno';
import { getDeporteLabel } from '../../../enums/deporte';
import { SkeletonCardComponent } from '../../generales/skeleton-card/skeleton-card.component';

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
  imports: [CommonModule, RouterModule, SkeletonCardComponent],
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
  eventosRecientes: any[] = [];
  novedadesDocumentos: number = 0;
  novedadesEventos: number = 0;
  novedadesEstadoDeportes: number = 0;
  detallesNovedadesEstado: string[] = [];
  private readonly subscriptions: Subscription = new Subscription();
  private readonly beltWidthPx = 84;
  private readonly beltVisualCache = new Map<string, BeltVisualData>();
  private countdownIntervalId: number | null = null;

  constructor(
    public endpointsService: EndpointsService,
    private readonly authService: AuthenticationService,
    private readonly alumnoService: AlumnoService
  ) {}

  ngOnInit(): void {
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
          this.selectedAlumno = alumnos[0];
          this.cargarGruposDelAlumno(this.selectedAlumno.id);
          this.cargarTurnosDelAlumno(this.selectedAlumno.id);
          this.cargarDeportesDelAlumno(this.selectedAlumno.id);
          this.cargarDocumentosDelAlumno(this.selectedAlumno.id);
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
    this.selectedAlumno = alumno;
    this.cargarGruposDelAlumno(alumno.id);
    this.cargarTurnosDelAlumno(alumno.id);
    this.cargarDeportesDelAlumno(alumno.id);
    this.cargarDocumentosDelAlumno(alumno.id);
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
    this.detenerActualizacionProximaClase();
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

  cargarTurnosDelAlumno(alumnoId: number): void {
    this.cargandoTurnos = true;
    this.endpointsService.obtenerTurnosDelAlumnoObservable(alumnoId).subscribe({
      next: (turnos: any[]) => {
        this.turnosAlumno = this.normalizarTurnos(turnos);
        this.cargandoTurnos = false;
        this.actualizarProximaClase();
      },
      error: () => {
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
    this.cargandoDeportes = true;
    this.alumnoService.obtenerDeportesDelAlumno(alumnoId).subscribe({
      next: (deportes: AlumnoDeporteDTO[]) => {
        this.deportesDelAlumno = deportes ?? [];
        this.actualizarNovedadesEstadoDeportes(alumnoId, this.deportesDelAlumno);
        this.cargandoDeportes = false;
      },
      error: () => {
        this.deportesDelAlumno = [];
        this.novedadesEstadoDeportes = 0;
        this.detallesNovedadesEstado = [];
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
    this.cargandoDocumentos = true;
    this.endpointsService.obtenerDocumentosDeAlumno(alumnoId).subscribe({
      next: (documentos: Documento[]) => {
        this.documentosAlumno = documentos ?? [];
        this.actualizarNovedadesDocumentos(alumnoId, this.documentosAlumno);
        this.cargandoDocumentos = false;
      },
      error: () => {
        this.documentosAlumno = [];
        this.novedadesDocumentos = 0;
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
    this.descargarDocumento(documento, true);
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

    this.endpointsService.descargarDocumentoAlumno(alumnoId, documento.id).subscribe({
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
        link.download = documento.nombre || 'documento';
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

  getTotalNovedades(): number {
    return this.novedadesDocumentos + this.novedadesEventos + this.novedadesEstadoDeportes;
  }

  private actualizarNovedadesDocumentos(alumnoId: number, documentos: Documento[]): void {
    const storageKey = `dashboard-user-vistos-documentos-${alumnoId}`;
    const idsActuales = this.obtenerIdsNumericos(documentos.map((documento) => documento?.id));
    const idsVistos = this.obtenerIdsVistos(storageKey);

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

  private obtenerIdsNumericos(values: any[]): number[] {
    return values
      .map((value) => Number(value))
      .filter((value) => Number.isInteger(value) && value > 0);
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
}
