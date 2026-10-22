import { Component, EventEmitter, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { GrupoAlumnosModalComponent } from '../../componentes/generales/grupo-alumnos-modal/grupo-alumnos-modal.component';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { iniciales } from '../alumnos/alumno-vista';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { frasePlural, numero } from '../formato/formato-es';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';

/**
 * «Alumnos por tipo de grupo» de Grupos (8b-3c) en el panel lateral (hoja inferior por debajo de 768 px). Hereda la
 * lógica de `GrupoAlumnosModalComponent` (buscador con la espera de 300 ms, «solo activos» por defecto, 8 por página y
 * paso a la ficha del alumno) y solo cambia la plantilla, la carga (la misma petición, con su fallo dentro del panel en
 * vez del SweetAlert2 de hoy) y el cierre (sin la espera de la animación del modal de hoy).
 */
@Component({
  selector: 'pn-alumnos-tipo',
  standalone: true,
  imports: [PanelLateralComponent, EsqueletoComponent, BloqueErrorComponent, BloqueVacioComponent],
  templateUrl: './alumnos-tipo-panel.component.html',
  styleUrls: ['./alumnos-tipo-panel.component.scss'],
})
export class AlumnosTipoPanelComponent extends GrupoAlumnosModalComponent implements OnInit, OnDestroy {
  private readonly lecturas = inject(LecturasPanelService);
  private readonly catalogo = inject(CatalogoService);

  @Output() readonly cerrado = new EventEmitter<void>();

  error = false;
  readonly iniciales = iniciales;
  private carga?: Subscription;

  constructor() {
    super(inject(Router));
  }

  get contador(): string {
    const total = (this.alumnos ?? []).length;
    const vistos = this.alumnosFiltrados.length;
    return vistos === total
      ? frasePlural(total, 'Mostrando {n} alumno', 'Mostrando {n} alumnos')
      : `Mostrando ${numero(vistos)} de ${frasePlural(total, '{n} alumno', '{n} alumnos')}`;
  }

  override ngOnInit(): void {
    super.ngOnInit();
    this.cargar();
  }

  override ngOnDestroy(): void {
    super.ngOnDestroy();
    this.carga?.unsubscribe();
  }

  cargar(): void {
    this.carga?.unsubscribe();
    this.error = false;
    this.cargando = true;
    this.alumnos = [];
    this.filtrarAlumnos();
    this.carga = this.lecturas.alumnosPorTipo(this.grupoNombre).subscribe({
      next: (alumnos) => {
        this.cargando = false;
        this.alumnos = alumnos ?? [];
        this.filtrarAlumnos();
      },
      error: () => {
        this.cargando = false;
        this.error = true;
      },
    });
  }

  buscar(valor: string): void {
    this.nombreFiltro = valor;
    this.onSearchChange();
  }

  alternarActivos(): void {
    this.mostrarSoloActivos = !this.mostrarSoloActivos;
    this.onActivoFilterChange();
  }

  /** Sin la espera de 300 ms del modal de hoy: el panel se quita al momento. */
  override cerrarModal(): void {
    this.cerrado.emit();
  }

  /** Color del cinturón por el nombre del grado (`alumno.grado` llega como nombre, como hoy). */
  colorGrado(nombre: string | null | undefined): string | null {
    const buscado = (nombre ?? '').trim().toUpperCase();
    if (!buscado) {
      return null;
    }
    const grado = this.catalogo
      .catalogo()
      ?.sistemasGrados.flatMap((s) => s.grados)
      .find((g) => g.color1 && g.nombre.trim().toUpperCase() === buscado);
    return grado?.color1 ?? null;
  }

  foto(alumno: any): string | null {
    const url: string | undefined = alumno?.fotoAlumno?.url;
    if (!url) {
      return null;
    }
    const version = encodeURIComponent(String(alumno.fotoAlumno.id ?? alumno.fotoAlumno.nombre ?? '0'));
    return `${url}${url.includes('?') ? '&' : '?'}w=180&v=${version}`;
  }
}
