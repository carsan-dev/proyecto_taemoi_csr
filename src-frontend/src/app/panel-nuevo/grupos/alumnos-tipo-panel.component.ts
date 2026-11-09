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

/**
 * «Alumnos por tipo de grupo» de Grupos (8b-3c) en el panel lateral (hoja inferior por debajo de 768 px). Hereda la
 * lógica de `GrupoAlumnosModalComponent` (buscador con la espera de 300 ms, «solo activos» por defecto, 8 por página y
 * paso a la ficha del alumno) y solo cambia la plantilla, la carga (la misma petición, con su fallo dentro del panel en
 * vez del SweetAlert2 de hoy) y el cierre (sin la espera de la animación del modal de hoy).
 */
@Component({
  selector: 'pn-alumnos-tipo',
  standalone: true,
  imports: [CinturonComponent, PanelLateralComponent, EsqueletoComponent, BloqueErrorComponent, BloqueVacioComponent],
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

  /**
   * Colores del cinturón de `alumno.grado`. El API de este listado (`AlumnoCortoDTO`) solo da el NOMBRE del grado, no su
   * código ni la disciplina. La disciplina se toma del tipo de grupo abierto (su código o nombre dentro del nombre del
   * tipo, como hace el API) y en su sistema de grados se busca el grado por código o por nombre. Si el tipo no deja una
   * disciplina con grados, se busca en todos los sistemas, como antes.
   */
  cinturon(grado: string | null | undefined): { color1: string; color2: string | null } | null {
    const buscado = (grado ?? '').trim().toUpperCase();
    const catalogo = this.catalogo.catalogo();
    if (!buscado || !catalogo) {
      return null;
    }
    const tipo = (this.grupoNombre ?? '').toUpperCase();
    const disciplinas = (catalogo.disciplinas ?? []).filter(
      (d) =>
        d.sistemaGradosId !== null &&
        (tipo.includes(d.codigo.toUpperCase().replace(/_/g, ' ')) || tipo.includes(d.codigo.toUpperCase()) || tipo.includes(d.nombre.toUpperCase()))
    );
    const delTipo = disciplinas.flatMap((d) => gradosDeDisciplina(catalogo.sistemasGrados, d));
    const candidatos = delTipo.length ? delTipo : catalogo.sistemasGrados.flatMap((sistema) => sistema.grados);
    const encontrado = candidatos.find(
      (g) => g.color1 && (g.codigo.trim().toUpperCase() === buscado || g.nombre.trim().toUpperCase() === buscado)
    );
    return encontrado ? { color1: encontrado.color1 as string, color2: encontrado.color2 ?? null } : null;
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
