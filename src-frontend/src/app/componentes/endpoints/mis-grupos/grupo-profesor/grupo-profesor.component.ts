import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, finalize, forkJoin, from, takeUntil } from 'rxjs';

import { AlumnoDeGrupo, GrupoProfesor } from '../../../../interfaces/profesor';
import { ProfesorService, mensajeDeErrorBlob } from '../../../../servicios/endpoints/profesor.service';
import type { DocumentoInforme } from '../../../../servicios/generales/informe-pdf.service';
import { AcentoEscuelaDirective } from '../../../../panel-nuevo/acento-escuela.directive';
import { EstilosPanelService } from '../../../../panel-nuevo/estilos-panel.service';
import { VisorPdfComponent } from '../../../../panel-nuevo/visor-pdf/visor-pdf.component';
import { showErrorToast } from '../../../../utils/toast.util';
import { NavegacionProfesorComponent } from '../navegacion-profesor/navegacion-profesor.component';
import { MesAsistencia, etiquetaRol, mensajeDelServidor, mesesAsistencia, nombreCompleto } from '../profesor-textos';

export type FiltroAlumnos = 'todos' | 'aptos';

/** Un alumno está «apto» en la lista si lo está en alguno de sus deportes. */
export function esApto(alumno: AlumnoDeGrupo): boolean {
  return alumno.deportes.some((d) => d.apto);
}

/** Un grupo (§5.2): hoja de asistencia del mes, filtro Todos/Aptos y sus alumnos activos. Sin datos de salud. */
@Component({
  selector: 'app-grupo-profesor',
  standalone: true,
  imports: [FormsModule, RouterLink, NavegacionProfesorComponent, AcentoEscuelaDirective, VisorPdfComponent],
  templateUrl: './grupo-profesor.component.html',
  styleUrl: './grupo-profesor.component.scss',
})
export class GrupoProfesorComponent implements OnInit, OnDestroy {
  private readonly profesor = inject(ProfesorService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly estilos = inject(EstilosPanelService);
  private readonly destroy$ = new Subject<void>();

  readonly etiquetaRol = etiquetaRol;
  readonly nombreCompleto = nombreCompleto;
  readonly meses: MesAsistencia[] = mesesAsistencia();

  grupoId = 0;
  grupo: GrupoProfesor | null = null;
  alumnos: AlumnoDeGrupo[] = [];
  cargando = false;
  cargado = false;
  error: string | null = null;

  filtro: FiltroAlumnos = 'todos';
  mes = this.meses[0].valor;
  descargando = false;
  documentos: DocumentoInforme[] = [];
  focoRetorno: HTMLElement | null = null;

  ngOnInit(): void {
    this.grupoId = Number(this.ruta.snapshot.paramMap.get('grupoId'));
    this.cargar();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  cargar(): void {
    this.cargando = true;
    this.error = null;
    forkJoin({ grupos: this.profesor.misGrupos(), alumnos: this.profesor.alumnosDelGrupo(this.grupoId) })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.cargando = false))
      )
      .subscribe({
        next: ({ grupos, alumnos }) => {
          this.grupo = grupos.find((g) => g.id === this.grupoId) ?? null;
          this.alumnos = alumnos;
          this.cargado = true;
        },
        error: (e) => (this.error = mensajeDelServidor(e, 'No se ha podido cargar el grupo.')),
      });
  }

  get numeroAptos(): number {
    return this.alumnos.filter(esApto).length;
  }

  get visibles(): AlumnoDeGrupo[] {
    return this.filtro === 'aptos' ? this.alumnos.filter(esApto) : this.alumnos;
  }

  elegirFiltro(filtro: FiltroAlumnos): void {
    this.filtro = filtro;
  }

  descargarAsistencia(boton: HTMLElement): void {
    if (this.descargando) { return; }
    const mes = this.meses.find((m) => m.valor === this.mes) ?? this.meses[0];
    this.focoRetorno = boton;
    this.descargando = true;
    forkJoin({
      hoja: this.profesor.hojaAsistencia(this.grupoId, mes.year, mes.month),
      estilos: from(this.estilos.cargar()),
    })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.descargando = false))
      )
      .subscribe({
        next: ({ hoja: { blob, nombre } }) => {
          this.documentos = [{ blob, nombreArchivo: nombre, titulo: 'Hoja de asistencia' }];
        },
        error: async (e) => {
          const mensaje = (await mensajeDeErrorBlob(e)) ?? mensajeDelServidor(e, 'No se ha podido descargar la hoja de asistencia.');
          showErrorToast(mensaje);
        },
      });
  }

  cerrarVisor(): void {
    this.documentos = [];
    this.focoRetorno = null;
  }
}
