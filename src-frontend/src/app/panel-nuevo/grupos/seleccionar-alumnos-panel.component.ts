import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription, debounceTime } from 'rxjs';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { fotoDe } from '../alumnos/alumno-vista';
import { AvisosService } from '../avisos/avisos.service';
import { cerrarSweetAlertAbierto } from '../capas/capa-modal';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';

interface FotoAlumnoGrupo { id?: number; nombre?: string; url?: string; }
export interface AlumnoGrupo {
  id: number;
  nombre: string;
  apellidos: string;
  activo?: boolean;
  fotoAlumno?: FotoAlumnoGrupo;
}
export interface GrupoAlumnos { id: number; nombre: string; alumnos: AlumnoGrupo[]; }
interface PaginaAlumnos { content: AlumnoGrupo[]; totalPages: number; totalElements: number; }

@Component({
  selector: 'pn-seleccionar-alumnos',
  standalone: true,
  imports: [FormsModule, PanelLateralComponent, BloqueErrorComponent, BotonOcupadoComponent, EsqueletoComponent],
  templateUrl: './seleccionar-alumnos-panel.component.html',
  styleUrl: './seleccionar-alumnos-panel.component.scss',
})
export class SeleccionarAlumnosPanelComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  @Input({ required: true }) grupo!: GrupoAlumnos;
  @Output() readonly cerrado = new EventEmitter<void>();
  @Output() readonly anadidos = new EventEmitter<void>();
  readonly elegidos = new Set<number>();
  readonly fotosFallidas = new Set<number>();
  readonly esqueletos = Array.from({ length: 10 });
  alumnos: AlumnoGrupo[] = [];
  filtro = '';
  pagina = 1;
  total = 0;
  totalPaginas = 0;
  cargando = true;
  enviando = false;
  errorCarga = '';
  errorEnvio = '';
  private readonly busqueda = new Subject<void>();
  private readonly suscripciones = new Subscription();
  private carga?: Subscription;
  private envio?: Subscription;

  ngOnInit(): void {
    this.suscripciones.add(this.busqueda.pipe(debounceTime(500)).subscribe(() => this.cargarAlumnos()));
    this.cargarAlumnos();
  }

  buscar(valor: string): void {
    this.filtro = valor;
    this.pagina = 1;
    // Cancel immediately, not after the debounce: the previous query is already stale.
    this.carga?.unsubscribe();
    this.cargando = true;
    this.errorCarga = '';
    this.busqueda.next();
  }

  cargarAlumnos(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.errorCarga = '';
    this.carga = this.endpoints.obtenerAlumnos(this.pagina, 10, this.filtro, false).subscribe({
      next: (respuesta: PaginaAlumnos) => {
        this.alumnos = respuesta.content.filter((alumno) => alumno.activo !== false);
        this.total = respuesta.totalElements;
        this.totalPaginas = respuesta.totalPages;
        this.cargando = false;
      },
      error: () => {
        this.cargando = false;
        cerrarSweetAlertAbierto(this.router);
        this.errorCarga = 'No se pudieron cargar los alumnos';
      },
    });
  }

  pertenece(id: number): boolean { return this.grupo.alumnos.some((alumno) => alumno.id === id); }
  fotoPara(alumno: AlumnoGrupo): string | null {
    return this.fotosFallidas.has(alumno.id) ? null : fotoDe(alumno);
  }
  marcarFotoFallida(id: number): void { this.fotosFallidas.add(id); }
  alternar(id: number): void {
    if (this.enviando || this.pertenece(id)) return;
    if (this.elegidos.has(id)) this.elegidos.delete(id);
    else this.elegidos.add(id);
    this.errorEnvio = '';
  }
  quitarSeleccion(): void { if (!this.enviando) this.elegidos.clear(); }
  cambiarPagina(pagina: number): void {
    if (this.cargando || pagina < 1 || pagina > this.totalPaginas || pagina === this.pagina) return;
    this.pagina = pagina;
    this.cargarAlumnos();
  }
  get desde(): number { return this.total ? (this.pagina - 1) * 10 + 1 : 0; }
  get hasta(): number { return Math.min(this.pagina * 10, this.total); }

  cerrar(): void { if (!this.enviando) this.cerrado.emit(); }
  anadir(): void {
    if (this.enviando || !this.elegidos.size) return;
    this.enviando = true;
    this.errorEnvio = '';
    this.envio = this.endpoints.agregarAlumnosAGrupo(this.grupo.id, [...this.elegidos]).subscribe({
      next: () => {
        this.enviando = false;
        this.avisos.exito('Alumnos añadidos al grupo');
        this.anadidos.emit();
      },
      error: () => {
        this.enviando = false;
        cerrarSweetAlertAbierto(this.router);
        this.errorEnvio = 'No se pudieron agregar los alumnos. Inténtalo de nuevo.';
      },
    });
  }
  ngOnDestroy(): void {
    this.suscripciones.unsubscribe();
    this.busqueda.complete();
    this.carga?.unsubscribe();
    this.envio?.unsubscribe();
  }
}
