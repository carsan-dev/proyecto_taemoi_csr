import { Location } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription, combineLatest, debounceTime } from 'rxjs';
import { MascotaComponent } from '../mascota/mascota.component';
import { environment } from '../../../environments/environment';
import type { TurnoResumen } from '../../interfaces/turno-resumen';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { cerrarSweetAlertAbierto } from '../capas/capa-modal';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { fotoDe } from '../alumnos/alumno-vista';
import { SeleccionarAlumnosPanelComponent, type AlumnoGrupo, type GrupoAlumnos } from './seleccionar-alumnos-panel.component';

const DIAS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
const INICIALES_DIAS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

@Component({
  selector: 'pn-gestionar-alumnos',
  standalone: true,
  imports: [MascotaComponent, FormsModule, RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, EsqueletoComponent, SeleccionarAlumnosPanelComponent],
  templateUrl: './gestionar-alumnos.component.html',
  styleUrl: './gestionar-alumnos.component.scss',
})
export class GestionarAlumnosComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly endpoints = inject(EndpointsService);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly busqueda = new Subject<void>();
  private carga?: Subscription;
  private cargaTurnos = new Subscription();
  readonly turnosPorAlumno = new Map<number, string>();
  private disparador: HTMLElement | null = null;
  private desdeRuta = false;
  private rutaConsumida = false;
  private enfocarAlCargar = false;
  private revisarFocoTrasRecarga = false;
  grupoId = 0;
  grupo: GrupoAlumnos | null = null;
  alumnos: AlumnoGrupo[] = [];
  filtrados: AlumnoGrupo[] = [];
  filtro = '';
  pagina = 1;
  cargando = true;
  errorCarga = '';
  panelAbierto = false;
  readonly quitandos = new Set<number>();
  readonly fotosFallidas = new Set<number>();
  readonly esqueletos = Array.from({ length: 10 });

  fotoPara(alumno: AlumnoGrupo): string | null {
    return this.fotosFallidas.has(alumno.id) ? null : fotoDe(alumno);
  }
  marcarFotoFallida(id: number): void { this.fotosFallidas.add(id); }

  ngOnInit(): void {
    const estado = this.location.getState() as Record<string, unknown> | null;
    this.enfocarAlCargar = estado?.['enfocarAnadirAlumnos'] === true;
    if (this.enfocarAlCargar) {
      const { enfocarAnadirAlumnos, ...resto } = estado ?? {};
      this.location.replaceState(this.location.path(), '', resto);
    }
    this.busqueda.pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.filtrar());
    combineLatest([this.route.params, this.route.data]).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(([params, data]) => {
      const id = Number(params['id']);
      if (id !== this.grupoId || !this.carga) {
        this.grupoId = id;
        this.grupo = null;
        this.alumnos = [];
        this.filtro = '';
        this.pagina = 1;
        this.rutaConsumida = false;
        this.panelAbierto = false;
        this.cargarGrupo();
      }
      this.desdeRuta = !!data['seleccionarAlumnos'];
      if (this.desdeRuta && !this.rutaConsumida) {
        this.panelAbierto = true;
        this.rutaConsumida = true;
      }
    });
  }

  cargarGrupo(): void {
    this.carga?.unsubscribe();
    this.cargaTurnos.unsubscribe();
    this.cargaTurnos = new Subscription();
    this.turnosPorAlumno.clear();
    this.cargando = !this.grupo;
    this.errorCarga = '';
    if (!Number.isSafeInteger(this.grupoId) || this.grupoId <= 0) {
      this.cargando = false; this.errorCarga = 'No se pudo cargar el grupo'; return;
    }
    this.carga = this.endpoints.obtenerGrupoPorId(this.grupoId, contextoErrorLocal()).subscribe({
      next: (grupo) => {
        this.grupo = grupo;
        this.alumnos = (grupo.alumnos ?? []).filter((alumno) => alumno.activo !== false);
        this.cargando = false;
        this.filtrar(false);
        if (this.enfocarAlCargar || this.revisarFocoTrasRecarga) {
          this.cdr.detectChanges();
          const documento = this.host.nativeElement.ownerDocument;
          if (this.enfocarAlCargar || (!this.disparador?.isConnected && documento.activeElement === documento.body)) {
            this.host.nativeElement.querySelector<HTMLElement>('.alumnos-grupo__primario')?.focus();
          }
          this.enfocarAlCargar = false;
          this.revisarFocoTrasRecarga = false;
        }
      },
      error: () => { this.cargando = false; this.errorCarga = 'No se pudo cargar el grupo'; },
    });
  }

  buscar(valor: string): void { this.filtro = valor; this.busqueda.next(); }
  private filtrar(reiniciar = true): void {
    const filtro = this.filtro.trim().toLowerCase();
    this.filtrados = this.alumnos.filter((alumno) => `${alumno.nombre} ${alumno.apellidos}`.toLowerCase().includes(filtro));
    this.pagina = reiniciar ? 1 : Math.min(this.pagina, this.totalPaginas);
    this.cargarTurnosVisibles();
  }
  private cargarTurnosVisibles(): void {
    const grupoId = this.grupoId;
    for (const alumno of this.visibles) {
      if (this.turnosPorAlumno.has(alumno.id)) continue;
      this.turnosPorAlumno.set(alumno.id, '');
      this.cargaTurnos.add(this.http.get<TurnoResumen[]>(`${environment.apiUrl}/grupos/${grupoId}/alumnos/${alumno.id}/turnos`, {
        withCredentials: true, context: contextoErrorLocal(),
      }).subscribe({
        next: (turnos) => {
          const porHora = new Map<string, string[]>();
          for (const turno of turnos.filter(t => t.grupoId === grupoId).sort((a, b) => DIAS.indexOf(a.diaSemana.toLowerCase()) - DIAS.indexOf(b.diaSemana.toLowerCase()))) {
            const hora = turno.horaInicio.slice(0, 5);
            const dias = porHora.get(hora) ?? [];
            const dia = INICIALES_DIAS[DIAS.indexOf(turno.diaSemana.toLowerCase())] ?? turno.diaSemana;
            if (!dias.includes(dia)) dias.push(dia);
            porHora.set(hora, dias);
          }
          this.turnosPorAlumno.set(alumno.id, [...porHora].map(([hora, dias]) => `${new Intl.ListFormat('es').format(dias)} ${hora}`).join(' · '));
        },
        // Missing schedules (including the endpoint's 404) must not hide the pupil list.
        error: () => this.turnosPorAlumno.set(alumno.id, ''),
      }));
    }
  }
  get totalPaginas(): number { return Math.max(1, Math.ceil(this.filtrados.length / 10)); }
  get visibles(): AlumnoGrupo[] { return this.filtrados.slice((this.pagina - 1) * 10, this.pagina * 10); }
  get desde(): number { return this.filtrados.length ? (this.pagina - 1) * 10 + 1 : 0; }
  get hasta(): number { return Math.min(this.pagina * 10, this.filtrados.length); }
  cambiarPagina(pagina: number): void {
    if (pagina >= 1 && pagina <= this.totalPaginas) { this.pagina = pagina; this.cargarTurnosVisibles(); }
  }

  abrirPanel(disparador: HTMLElement): void {
    this.disparador = disparador;
    disparador.focus();
    this.panelAbierto = true;
  }
  cerrarPanel(): void {
    this.panelAbierto = false;
    this.cdr.detectChanges();
    if (this.desdeRuta) {
      void this.router.navigate(['/gestionarAlumnos', this.grupoId], { replaceUrl: true, state: { enfocarAnadirAlumnos: true } });
      return;
    }
    const destino = this.disparador?.isConnected ? this.disparador
      : this.host.nativeElement.querySelector<HTMLElement>('.alumnos-grupo__primario');
    destino?.focus();
    this.revisarFocoTrasRecarga = true;
    this.cargarGrupo();
  }
  alumnosAnadidos(): void { this.cerrarPanel(); }

  async quitar(alumno: AlumnoGrupo): Promise<void> {
    if (this.quitandos.has(alumno.id)) return;
    const grupoId = this.grupoId;
    this.quitandos.add(alumno.id);
    const { confirmado } = await this.confirmacion.confirmar({
      titulo: `¿Quitar a ${alumno.nombre} ${alumno.apellidos} del grupo?`,
      mensaje: 'Sigue siendo alumno de la escuela y puedes volver a añadirlo.',
      accion: 'Quitar del grupo', cancelar: 'Cancelar', destructivo: false,
    });
    if (!confirmado || this.destroyRef.destroyed || grupoId !== this.grupoId) {
      this.quitandos.delete(alumno.id);
      return;
    }
    this.endpoints.eliminarAlumnoDeGrupo(grupoId, alumno.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.quitandos.delete(alumno.id);
        this.avisos.exito('Alumno quitado del grupo');
        if (grupoId === this.grupoId) this.cargarGrupo();
      },
      error: () => {
        this.quitandos.delete(alumno.id);
        cerrarSweetAlertAbierto(this.router);
        this.avisos.error('No se pudo quitar al alumno del grupo');
      },
    });
  }
  ngOnDestroy(): void { this.carga?.unsubscribe(); this.cargaTurnos.unsubscribe(); this.busqueda.complete(); }
}
