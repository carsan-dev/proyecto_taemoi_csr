import { Component, EventEmitter, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { GrupoAlumnosModalComponent } from '../../componentes/generales/grupo-alumnos-modal/grupo-alumnos-modal.component';
import { gradosDeDisciplina } from '../../features/catalogo/catalogo-reglas';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { iniciales } from '../alumnos/alumno-vista';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { frasePlural, numero } from '../formato/formato-es';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { CinturonComponent } from '../cinturon/cinturon.component';
import { DistintivoExamenComponent } from '../cinturon/distintivo-examen.component';
import { mediasVisibles, textoOcultoRallas } from '../cinturon/rallas-textos';

/**
 * «Alumnos por tipo de grupo» de Grupos (8b-3c) en el panel lateral (hoja inferior por debajo de 768 px). Hereda la
 * lógica de `GrupoAlumnosModalComponent` (buscador con la espera de 300 ms, «solo activos» por defecto, 8 por página y
 * paso a la ficha del alumno) y solo cambia la plantilla, la carga (la misma petición, con su fallo dentro del panel en
 * vez del SweetAlert2 de hoy) y el cierre (sin la espera de la animación del modal de hoy).
 */
@Component({
  selector: 'pn-alumnos-tipo',
  standalone: true,
  imports: [CinturonComponent, DistintivoExamenComponent, PanelLateralComponent, EsqueletoComponent, BloqueErrorComponent, BloqueVacioComponent],
  templateUrl: './alumnos-tipo-panel.component.html',
  styleUrls: ['./alumnos-tipo-panel.component.scss'],
})
export class AlumnosTipoPanelComponent extends GrupoAlumnosModalComponent implements OnInit, OnDestroy {
  private readonly lecturas = inject(LecturasPanelService);
  private readonly catalogo = inject(CatalogoService);

  @Output() readonly cerrado = new EventEmitter<void>();

  error = false;
  readonly iniciales = iniciales;
  readonly textoOcultoRallas = textoOcultoRallas;
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

  /**
   * Colores del cinturón de un alumno del listado (`AlumnoCortoDTO`): búsqueda exacta por `codigoGrado` en el sistema de
   * grados de su disciplina (`deporte`, la de la inscripción del tipo). Sin grado, disciplina sin grados o código que no
   * está en el catálogo: null.
   */
  cinturon(alumno: { codigoGrado?: string | null; deporte?: string | null } | null | undefined): { color1: string; color2: string | null } | null {
    const codigo = alumno?.codigoGrado;
    const catalogo = this.catalogo.catalogo();
    if (!codigo || !alumno?.deporte || !catalogo) {
      return null;
    }
    const disciplina = (catalogo.disciplinas ?? []).find((d) => d.codigo === alumno.deporte);
    const encontrado = gradosDeDisciplina(catalogo.sistemasGrados, disciplina).find((g) => g.codigo === codigo);
    return encontrado?.color1 ? { color1: encontrado.color1, color2: encontrado.color2 ?? null } : null;
  }

  /** Medias rallas hacia el examen del alumno; null si no se usan. */
  medias(alumno: { mediasRallas?: number | null; aplicaRallas?: boolean | null } | null | undefined): number | null {
    return mediasVisibles(alumno);
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
