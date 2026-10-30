import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, finalize, forkJoin, from } from 'rxjs';

import { ClaseAsistencia, ClasesDelGrupo } from '../../interfaces/asistencia';
import { AlumnoDeGrupo, DeporteEnLista, GrupoProfesor } from '../../interfaces/profesor';
import { etiquetaRol, mensajeDelServidor, mesesAsistencia, nombreCompleto } from './profesor-textos';
import { AsistenciaService } from '../../servicios/endpoints/asistencia.service';
import { ProfesorService, mensajeDeErrorBlob } from '../../servicios/endpoints/profesor.service';
import type { DocumentoInforme } from '../../servicios/generales/informe-pdf.service';
import { gradoFila, gradoParaFrase, iniciales } from '../alumnos/alumno-vista';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EstilosPanelService } from '../estilos-panel.service';
import { diaSemana, fechaClase, franja, tieneLista } from '../asistencia/asistencia-textos';
import { numero } from '../formato/formato-es';
import { PluralPipe } from '../formato/plural.pipe';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { VisorPdfComponent } from '../visor-pdf/visor-pdf.component';
import { FichaProfesorPanelComponent } from './ficha-profesor-panel.component';

const FILTROS = { todos: 'todos', aptos: 'aptos' } as const;
type Filtro = typeof FILTROS[keyof typeof FILTROS];

/** Desde este ancho (px) la ficha del alumno se abre en el panel lateral; por debajo, en su pantalla. */
export const ANCHO_ESCRITORIO = 1024;
/** Ancho del panel lateral de la ficha (boceto R6: 420 px). */
export const ANCHO_PANEL_FICHA = 420;

function esApto(alumno: AlumnoDeGrupo): boolean { return alumno.deportes.some(d => d.apto); }

@Component({
  selector: 'pn-grupo-profesor',
  standalone: true,
  imports: [FormsModule, NgTemplateOutlet, RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, BloqueVacioComponent,
    EsqueletoComponent, BotonOcupadoComponent, PluralPipe, VisorPdfComponent, PanelLateralComponent, FichaProfesorPanelComponent],
  templateUrl: './grupo.component.html',
  styleUrl: './grupo.component.scss',
})
export class GrupoPanelComponent implements OnInit {
  private readonly profesor = inject(ProfesorService);
  private readonly asistencia = inject(AsistenciaService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly estilos = inject(EstilosPanelService);
  private readonly destruir = inject(DestroyRef);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private carga?: Subscription;
  private cargaHoja?: Subscription;
  private cargaClases?: Subscription;
  private cargaResumen?: Subscription;

  readonly etiquetaRol = etiquetaRol;
  readonly nombreCompleto = nombreCompleto;
  readonly iniciales = iniciales;
  readonly numero = numero;
  readonly meses = mesesAsistencia();
  readonly esqueletos = [0, 1, 2, 3, 4];
  grupoId = 0;
  grupo: GrupoProfesor | null = null;
  alumnos: AlumnoDeGrupo[] = [];
  cargando = true;
  error: string | null = null;
  filtro: Filtro = FILTROS.todos;
  mes = this.meses[0].valor;
  descargando = false;
  errorAsistencia: string | null = null;
  documentos: DocumentoInforme[] = [];
  focoRetorno: HTMLElement | null = null;
  readonly anchoPanelFicha = ANCHO_PANEL_FICHA;
  readonly franja = franja;
  /** Clases de hoy y recientes (`GET /clases`); `null` mientras carga o si falla. */
  clases: ClasesDelGrupo | null = null;
  cargandoClases = true;
  errorClases = false;
  /** Asistencia del mes elegido por alumno; `null` sin datos o si el resumen falla (las filas quedan sin cifra). */
  resumen: Map<number, { presentes: number; clases: number }> | null = null;
  /** Alumno de `?alumno=` (la ficha abierta en el panel lateral); 0 si no hay. */
  alumnoEnUrl = 0;

  ngOnInit(): void {
    this.ruta.paramMap.pipe(takeUntilDestroyed(this.destruir)).subscribe(parametros => {
      this.cargaHoja?.unsubscribe();
      this.cerrarVisor();
      this.errorAsistencia = null;
      this.cargaClases?.unsubscribe();
      this.cargaResumen?.unsubscribe();
      this.grupoId = Number(parametros.get('grupoId'));
      this.filtro = FILTROS.todos;
      this.cargar();
      this.cargarClases();
      this.cargarResumen();
    });
    this.ruta.queryParamMap.pipe(takeUntilDestroyed(this.destruir)).subscribe(parametros => {
      const id = Number(parametros.get('alumno'));
      this.alumnoEnUrl = Number.isInteger(id) && id > 0 ? id : 0;
    });
  }

  /** ¿Pantalla de escritorio (≥ 1024 px)? Separado para poder probarlo. */
  esEscritorio(): boolean {
    return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      && window.matchMedia(`(min-width: ${ANCHO_ESCRITORIO}px)`).matches;
  }

  /** El alumno cuya ficha está abierta en el panel lateral: el de `?alumno=` si es de este grupo y hay escritorio. */
  get alumnoAbierto(): AlumnoDeGrupo | null {
    if (!this.alumnoEnUrl || this.cargando || !this.esEscritorio()) return null;
    return this.alumnos.find(a => a.id === this.alumnoEnUrl) ?? null;
  }

  /** Cierra la ficha y devuelve el foco a la fila del alumno que estaba abierto (puede no ser la que la abrió). */
  cerrarAlumno(): void {
    const id = this.alumnoEnUrl;
    void this.router.navigate([], { relativeTo: this.ruta, queryParams: { alumno: null }, queryParamsHandling: 'merge' })
      .then(() => setTimeout(() => this.anfitrion.nativeElement.querySelector<HTMLElement>(`[data-alumno="${id}"]`)?.focus()));
  }

  esAptoAlumno(alumno: AlumnoDeGrupo): boolean { return esApto(alumno); }

  /** Nombres de los deportes en que es apto, para el lector de pantalla cuando tiene más de uno. */
  deportesAptos(alumno: AlumnoDeGrupo): string {
    return alumno.deportes.filter(d => d.apto).map(d => d.deporteNombre).join(', ');
  }

  /** Grado con el nombre del catálogo («Blanco-amarillo») y su muestra de color; nulo si no tiene grado. */
  gradoDe(deporte: DeporteEnLista): { texto: string; fondo: string } | null {
    if (!deporte.grado) return null;
    const grado = gradoFila(deporte.grado.codigo, deporte.deporte);
    return { texto: gradoParaFrase(grado), fondo: grado.fondo };
  }

  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.error = null;
    this.grupo = null;
    this.alumnos = [];
    this.carga = forkJoin({ grupos: this.profesor.misGrupos(), alumnos: this.profesor.alumnosDelGrupo(this.grupoId) }).pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.cargando = false)),
    ).subscribe({
      next: ({ grupos, alumnos }) => {
        this.grupo = grupos.find(g => g.id === this.grupoId) ?? null;
        this.alumnos = alumnos;
        if (!this.grupo) this.error = 'No se ha encontrado el grupo entre tus grupos asignados.';
      },
      error: e => (this.error = mensajeDelServidor(e, 'No se ha podido cargar el grupo.')),
    });
  }

  /** La tarjeta de hoy: se pide aparte para que su fallo no tumbe el resto de la pantalla. */
  cargarClases(): void {
    this.cargaClases?.unsubscribe();
    this.cargandoClases = true;
    this.errorClases = false;
    this.clases = null;
    this.cargaClases = this.asistencia.clases('profesor', this.grupoId).pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.cargandoClases = false)),
    ).subscribe({
      next: (clases) => (this.clases = clases),
      error: () => (this.errorClases = true),
    });
  }

  /** Asistencia del mes elegido para la columna de cada fila. */
  cargarResumen(): void {
    this.cargaResumen?.unsubscribe();
    this.resumen = null;
    const mes = this.meses.find(m => m.valor === this.mes) ?? this.meses[0];
    this.cargaResumen = this.asistencia.resumen('profesor', this.grupoId, mes.year, mes.month).pipe(
      takeUntilDestroyed(this.destruir),
    ).subscribe({
      next: (lista) => (this.resumen = new Map(lista.map(r => [r.alumnoId, { presentes: r.presentes, clases: r.clases }]))),
      error: () => (this.resumen = null),
    });
  }

  cambiarMes(valor: string): void {
    this.mes = valor;
    this.cargarResumen();
  }

  get nombreMesElegido(): string {
    return (this.meses.find(m => m.valor === this.mes) ?? this.meses[0]).etiqueta.split(' ')[0].toLowerCase();
  }

  /** «3 de 4» para la fila, o null sin lista ese mes (se pinta «—»). Los alumnos que ya no están en el grupo no salen. */
  asistenciaDe(alumno: AlumnoDeGrupo): string | null {
    const r = this.resumen?.get(alumno.id);
    return r && r.clases > 0 ? `${r.presentes} de ${r.clases}` : null;
  }

  // ------------------------------------------------------------------ tarjeta de hoy
  get clasesHoy(): ClaseAsistencia[] { return this.clases?.hoy ?? []; }
  get puedePasarOtra(): boolean { return (this.clases?.recientes.length ?? 0) > 0; }
  tieneLista(clase: ClaseAsistencia): boolean { return tieneLista(clase); }
  tituloHoy(clase: ClaseAsistencia): string { return `Hoy, ${diaSemana(clase.fecha)} · ${franja(clase)}`; }
  get textoProxima(): string {
    const p = this.clases?.proxima;
    return p ? `Próxima: ${fechaClase(p.fecha)}, ${p.horaInicio}` : '';
  }
  consultaClase(clase: ClaseAsistencia): { turno: number; fecha: string } { return { turno: clase.turnoId, fecha: clase.fecha }; }

  /** El apto cambiado desde la ficha del panel lateral: la fila y el contador «Aptos» se actualizan sin recargar. */
  alCambiarApto(cambio: { alumnoId: number; deporte: string; apto: boolean }): void {
    this.alumnos = this.alumnos.map(a => a.id !== cambio.alumnoId ? a : {
      ...a,
      deportes: a.deportes.map(d => d.deporte === cambio.deporte ? { ...d, apto: cambio.apto } : d),
    });
  }

  get numeroAptos(): number { return this.alumnos.filter(esApto).length; }
  get visibles(): AlumnoDeGrupo[] { return this.filtro === FILTROS.aptos ? this.alumnos.filter(esApto) : this.alumnos; }

  teclaFiltro(evento: KeyboardEvent): void {
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(evento.key)) return;
    evento.preventDefault();
    this.filtro = evento.key === 'Home' ? FILTROS.todos : evento.key === 'End' ? FILTROS.aptos
      : this.filtro === FILTROS.todos ? FILTROS.aptos : FILTROS.todos;
    const contenedor = evento.currentTarget as HTMLElement;
    contenedor.querySelector<HTMLButtonElement>(`[data-filtro="${this.filtro}"]`)?.focus();
  }

  enlaceAlumno(alumno: AlumnoDeGrupo): string {
    return this.router.serializeUrl(this.router.createUrlTree(['/mis-grupos/alumnos', alumno.id], { queryParams: { grupo: this.grupoId } }));
  }

  /**
   * Único punto de apertura. En escritorio, la ficha va en el panel lateral y el alumno queda en la URL (`?alumno=`),
   * así que atrás y recargar funcionan; por debajo de 1024 px, pantalla propia.
   */
  abrirAlumno(evento: MouseEvent, alumno: AlumnoDeGrupo): void {
    if (evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey || evento.button !== 0) return;
    evento.preventDefault();
    if (this.esEscritorio()) {
      void this.router.navigate([], { relativeTo: this.ruta, queryParams: { alumno: alumno.id }, queryParamsHandling: 'merge' });
      return;
    }
    void this.router.navigate(['/mis-grupos/alumnos', alumno.id], { queryParams: { grupo: this.grupoId } });
  }

  descargarAsistencia(evento?: Event): void {
    if (this.descargando) return;
    const mes = this.meses.find(m => m.valor === this.mes) ?? this.meses[0];
    if (evento?.currentTarget instanceof HTMLElement) this.focoRetorno = evento.currentTarget;
    this.descargando = true;
    this.errorAsistencia = null;
    this.cargaHoja = forkJoin({
      hoja: this.profesor.hojaAsistencia(this.grupoId, mes.year, mes.month),
      estilos: from(this.estilos.cargar()),
    }).pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.descargando = false)),
    ).subscribe({
      next: ({ hoja }) => {
        this.documentos = [{ blob: hoja.blob, nombreArchivo: hoja.nombre, titulo: 'Hoja de asistencia' }];
      },
      error: async e => {
        const grupoId = this.grupoId;
        const mensaje = (await mensajeDeErrorBlob(e)) ?? mensajeDelServidor(e, 'No se ha podido descargar la hoja de asistencia.');
        if (!this.destruir.destroyed && this.grupoId === grupoId) this.errorAsistencia = mensaje;
      },
    });
  }

  cerrarVisor(): void {
    this.documentos = [];
    this.focoRetorno = null;
  }
}
