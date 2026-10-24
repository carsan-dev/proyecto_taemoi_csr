import { Location } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { GrupoResumen, TurnoDeGrupoResumen } from '../../interfaces/grupo-resumen';
import { TurnoResumen } from '../../interfaces/turno-resumen';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { cerrarSweetAlertAbierto } from '../capas/capa-modal';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ErrorCampo, ValoresDialogo } from '../dialogo/dialogo.component';
import { frasePlural, numero } from '../formato/formato-es';
import { AlumnosTipoPanelComponent } from './alumnos-tipo-panel.component';
import { CrearGrupoPanelComponent } from './crear-grupo-panel.component';
import { SeccionDisciplina, rangoEdad, resumenHorario, seccionesPorDisciplina, textoTurno } from './grupos-vista';

type Estado = 'cargando' | 'listo' | 'error';

/** Los tipos de grupo de hoy (los de «Alumnos por tipo de grupo» de la pantalla de hoy). */
export const TIPOS_GRUPO: readonly string[] = ['Taekwondo', 'Taekwondo Competición', 'Kickboxing'];

/**
 * Grupos del panel nuevo (8b-3c): estructura de C (alumnos por tipo de grupo, filtro por disciplina con su número y los
 * grupos por disciplina con su total) con cada grupo como tarjeta de A (alumnos, edad y horario a la vista; turnos
 * desplegables con editar, quitar y «Añadir turno»; «Alumnos», «Editar», «Rango de edad» y eliminar al pie) y los turnos
 * sin grupo al final. Las mismas lecturas y escrituras que la pantalla de hoy (`ListadoGruposComponent`), con el diálogo,
 * los avisos, el panel lateral y los estados de carga del panel nuevo, sin SweetAlert2 ni cargador global. Va en su
 * propio chunk detrás de `disenoNuevoGuard`: con el interruptor apagado sigue la pantalla de hoy.
 */
@Component({
  selector: 'app-grupos',
  standalone: true,
  imports: [
    RouterLink,
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    AlumnosTipoPanelComponent,
    CrearGrupoPanelComponent,
  ],
  templateUrl: './grupos.component.html',
  styleUrls: ['./grupos.component.scss'],
})
export class GruposComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly catalogo = inject(CatalogoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly tipos = TIPOS_GRUPO;
  readonly numero = numero;
  readonly rangoEdad = rangoEdad;
  readonly resumenHorario = resumenHorario;
  readonly textoTurno = textoTurno;

  estadoGrupos: Estado = 'cargando';
  estadoTurnos: Estado = 'cargando';
  estadoConteo: Estado = 'cargando';
  grupos: GrupoResumen[] = [];
  /** Todos los turnos de la escuela (para el total de la cabecera). */
  private turnos: TurnoResumen[] = [];
  turnosSinGrupo: TurnoResumen[] = [];
  private conteo: Record<string, { active?: number; total?: number }> = {};

  /** Disciplina elegida en el filtro; vacío, todas. */
  filtro: string | null = null;
  /** Tarjetas con los turnos desplegados. */
  readonly abiertas = new Set<number>();
  /** Turno elegido en «Añadir turno» de cada grupo. */
  readonly eleccion = new Map<number, number>();
  /** Grupos con un «Añadir turno» en vuelo. */
  readonly anadiendo = new Set<number>();
  /** Tipo con el panel de alumnos abierto. */
  tipoAbierto: string | null = null;
  crearAbierto = false;
  private crearDesdeRuta = false;
  grupoResaltado: number | null = null;
  private finResaltado?: ReturnType<typeof setTimeout>;
  private disparadorCrear: HTMLElement | null = null;

  private readonly cargas = new Subscription();
  private cargaGrupos?: Subscription;
  private cargaTurnos?: Subscription;
  private cargaConteo?: Subscription;

  get subtitulo(): string {
    const partes: string[] = [];
    if (this.estadoGrupos === 'listo') {
      partes.push(frasePlural(this.grupos.length, '{n} grupo', '{n} grupos'));
    }
    if (this.estadoTurnos === 'listo') {
      partes.push(frasePlural(this.turnos.length, '{n} turno', '{n} turnos'));
    }
    return partes.join(' · ');
  }

  /** Todas las secciones (las del catálogo aunque no tengan grupos). */
  get secciones(): SeccionDisciplina[] {
    return seccionesPorDisciplina(this.grupos, this.catalogo.disciplinasActivas(), (c) => this.catalogo.etiquetaDisciplina(c));
  }

  /** Opciones del filtro: «Todas» y cada sección con su número de grupos. */
  get opcionesFiltro(): { codigo: string | null; nombre: string; cuantos: number }[] {
    return [
      { codigo: null, nombre: 'Todas', cuantos: this.grupos.length },
      ...this.secciones.map((s) => ({ codigo: s.codigo, nombre: s.nombre, cuantos: s.grupos.length })),
    ];
  }

  /** Las secciones que se ven: con «Todas», las que tienen grupos; con una disciplina, solo esa. */
  get seccionesVisibles(): SeccionDisciplina[] {
    const todas = this.secciones;
    return this.filtro === null ? todas.filter((s) => s.grupos.length > 0) : todas.filter((s) => s.codigo === this.filtro);
  }

  get nombreFiltro(): string {
    return this.opcionesFiltro.find((o) => o.codigo === this.filtro)?.nombre ?? '';
  }

  ngOnInit(): void {
    this.cargas.add(this.route.data.subscribe((data) => {
      this.crearDesdeRuta = !!data['crearGrupo'];
      if (this.crearDesdeRuta) this.crearAbierto = true;
    }));
    const estado = this.location.getState() as Record<string, unknown> | null;
    const resaltarId = estado?.['grupoCreadoId'];
    if (typeof resaltarId === 'number') {
      const { grupoCreadoId, ...resto } = estado ?? {};
      // Consumir el estado antes de cargar evita repetir el resaltado al recargar la página.
      this.location.replaceState(this.location.path(), '', resto);
    }
    this.cargarGrupos(false, typeof resaltarId === 'number' ? resaltarId : undefined);
    this.cargarTurnos();
    this.cargarConteo();
  }

  ngOnDestroy(): void {
    clearTimeout(this.finResaltado);
    this.cargas.unsubscribe();
    this.cargaGrupos?.unsubscribe();
    this.cargaTurnos?.unsubscribe();
    this.cargaConteo?.unsubscribe();
  }

  // ------------------------------------------------------------------ lecturas

  /** `GET /api/grupos/resumen`. Con `recargar`, sin esqueleto (tras eliminar un grupo, como hoy, se vuelve a leer). */
  cargarGrupos(recargar = false, resaltarId?: number): void {
    this.cargaGrupos?.unsubscribe();
    if (!recargar) {
      this.estadoGrupos = 'cargando';
    }
    this.cargaGrupos = this.lecturas.resumenGrupos().subscribe({
      next: (grupos) => {
        this.grupos = (grupos ?? []).map((g) => ({ ...g, turnos: [...(g.turnos ?? [])] }));
        this.estadoGrupos = 'listo';
        if (resaltarId !== undefined) {
          clearTimeout(this.finResaltado);
          this.grupoResaltado = resaltarId;
          this.finResaltado = setTimeout(() => {
            this.grupoResaltado = null;
            this.cdr.markForCheck();
          }, 5000);
        }
        if (this.filtro !== null && !this.opcionesFiltro.some((o) => o.codigo === this.filtro)) {
          this.filtro = null;
        }
      },
      error: () => {
        this.estadoGrupos = 'error';
      },
    });
  }

  /**
   * `GET /api/turnos/resumen`. Sin grupo = sin `grupoId`. La pantalla de hoy pide además `grupoNombre` vacío, pero la
   * API manda «Sin grupo» en esos turnos, así que hoy nunca ofrece ninguno (spec «con los datos reales… no ofrece turnos
   * sin grupo, como hasta ahora»); aquí sí, para que «Añadir turno» y «Turnos sin grupo» funcionen.
   */
  cargarTurnos(recargar = false): void {
    this.cargaTurnos?.unsubscribe();
    if (!recargar) {
      this.estadoTurnos = 'cargando';
    }
    this.cargaTurnos = this.lecturas.resumenTurnos().subscribe({
      next: (turnos) => {
        this.turnos = turnos ?? [];
        this.turnosSinGrupo = this.turnos.filter((t) => !t.grupoId);
        this.estadoTurnos = 'listo';
      },
      error: () => {
        this.estadoTurnos = 'error';
      },
    });
  }

  /** `GET /api/grupos/conteo-alumnos`: los activos de cada tipo. */
  cargarConteo(): void {
    this.cargaConteo?.unsubscribe();
    this.estadoConteo = 'cargando';
    this.cargaConteo = this.lecturas.conteoAlumnosPorGrupo().subscribe({
      next: (conteo) => {
        this.conteo = conteo ?? {};
        this.estadoConteo = 'listo';
      },
      error: () => {
        this.estadoConteo = 'error';
      },
    });
  }

  activosDe(tipo: string): number {
    return this.conteo[tipo]?.active ?? 0;
  }

  abrirCrear(evento: Event): void {
    this.disparadorCrear = evento.currentTarget as HTMLElement;
    this.disparadorCrear.focus();
    this.crearAbierto = true;
  }

  cerrarCrear(): void {
    this.crearAbierto = false;
    if (this.crearDesdeRuta) {
      void this.router.navigate(['/gruposListar'], { replaceUrl: true });
      return;
    }
    this.cdr.detectChanges();
    const destino = this.disparadorCrear?.isConnected
      ? this.disparadorCrear
      : this.host.nativeElement.querySelector<HTMLElement>('.grupos__boton-primario');
    destino?.focus();
  }

  grupoCreado(id: number): void {
    if (this.crearDesdeRuta) {
      this.crearAbierto = false;
      void this.router.navigate(['/gruposListar'], { replaceUrl: true, state: { grupoCreadoId: id } });
      return;
    }
    this.cerrarCrear();
    this.filtro = null;
    this.cargarGrupos(true, id);
  }

  // ------------------------------------------------------------------ alumnos por tipo

  abrirTipo(tipo: string): void {
    this.tipoAbierto = tipo;
  }

  cerrarTipo(): void {
    this.tipoAbierto = null;
  }

  // ------------------------------------------------------------------ filtro (radiogroup con tabindex itinerante)

  elegirFiltro(codigo: string | null): void {
    this.filtro = codigo;
  }

  teclaFiltro(evento: KeyboardEvent): void {
    const opciones = this.opcionesFiltro;
    const actual = Math.max(
      0,
      opciones.findIndex((o) => o.codigo === this.filtro)
    );
    let siguiente: number;
    switch (evento.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        siguiente = (actual + 1) % opciones.length;
        break;
      case 'ArrowLeft':
      case 'ArrowUp':
        siguiente = (actual - 1 + opciones.length) % opciones.length;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = opciones.length - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.filtro = opciones[siguiente].codigo;
    this.cdr.detectChanges();
    this.host.nativeElement.querySelectorAll<HTMLElement>('.grupos__filtro [role="radio"]')[siguiente]?.focus();
  }

  // ------------------------------------------------------------------ tarjeta

  /** El aviso de error del panel nuevo sustituye al SweetAlert2 del interceptor, como en 8b-D1. */
  private avisoError(texto: string): void {
    cerrarSweetAlertAbierto(this.router);
    this.avisos.error(texto);
  }

  idTurnos(grupo: GrupoResumen): string {
    return `grupo-${grupo.id}-turnos`;
  }

  alternarTurnos(grupo: GrupoResumen): void {
    if (this.abiertas.has(grupo.id)) {
      this.abiertas.delete(grupo.id);
    } else {
      this.abiertas.add(grupo.id);
    }
  }

  elegirTurno(grupo: GrupoResumen, valor: string): void {
    if (valor) {
      this.eleccion.set(grupo.id, Number(valor));
    } else {
      this.eleccion.delete(grupo.id);
    }
  }

  /** `POST /api/grupos/{id}/turnos/{turnoId}` (el de hoy): el turno pasa de los sin grupo a la tarjeta. */
  anadirTurno(grupo: GrupoResumen): void {
    const turnoId = this.eleccion.get(grupo.id);
    if (turnoId === undefined || this.anadiendo.has(grupo.id)) {
      return;
    }
    this.anadiendo.add(grupo.id);
    this.endpoints.agregarTurnoAGrupo(grupo.id, turnoId).subscribe({
      next: () => {
        this.anadiendo.delete(grupo.id);
        this.eleccion.forEach((v, k) => {
          if (v === turnoId) {
            this.eleccion.delete(k);
          }
        });
        const turno = this.turnosSinGrupo.find((t) => t.id === turnoId);
        if (turno) {
          this.turnosSinGrupo = this.turnosSinGrupo.filter((t) => t.id !== turnoId);
          grupo.turnos.push(turno);
          this.turnos = this.turnos.map((t) => (t.id === turnoId ? { ...t, grupoId: grupo.id, grupoNombre: grupo.nombre } : t));
        }
        this.avisos.exito('Turno asignado al grupo');
        this.cdr.markForCheck();
      },
      error: () => {
        this.anadiendo.delete(grupo.id);
        this.avisoError('No se pudo asignar el turno al grupo');
        this.cdr.markForCheck();
      },
    });
  }

  /** `DELETE /api/grupos/{id}/turnos/{turnoId}` tras la confirmación de hoy (destructiva, foco en «Cancelar»). */
  async quitarTurno(grupo: GrupoResumen, turno: TurnoDeGrupoResumen): Promise<void> {
    const { confirmado } = await this.confirmacion.confirmar({
      titulo: '¿Eliminar turno del grupo?',
      mensaje: 'El turno seguirá existiendo pero sin grupo asignado',
      accion: 'Sí, eliminar',
      cancelar: 'Cancelar',
      destructivo: true,
      nombre: 'quitar-turno',
    });
    if (!confirmado) {
      return;
    }
    this.endpoints.eliminarTurnoDeGrupo(grupo.id, turno.id).subscribe({
      next: () => {
        grupo.turnos = grupo.turnos.filter((t) => t.id !== turno.id);
        const sinGrupo: TurnoResumen = { ...turno, grupoId: null, grupoNombre: 'Sin grupo', tipoGrupo: null };
        this.turnosSinGrupo = [...this.turnosSinGrupo.filter((t) => t.id !== turno.id), sinGrupo];
        this.turnos = this.turnos.map((t) => (t.id === turno.id ? sinGrupo : t));
        this.avisos.exito('Turno eliminado del grupo');
        this.cdr.markForCheck();
      },
      error: () => {
        this.avisoError('No se pudo eliminar el turno');
      },
    });
  }

  /** Rango de edad: los dos campos y la validación de hoy; `PUT /api/grupos/{id}` con el cuerpo de hoy. */
  async editarRango(grupo: GrupoResumen): Promise<void> {
    const resultado = await this.confirmacion.confirmar({
      titulo: 'Rango de edad del grupo',
      accion: 'Guardar',
      cancelar: 'Cancelar',
      nombre: 'rango-edad',
      campos: [
        { id: 'rangoEdadMin', tipo: 'number', etiqueta: 'Edad mínima', valor: valorCampo(grupo.rangoEdadMin), atributos: { min: '0' } },
        { id: 'rangoEdadMax', tipo: 'number', etiqueta: 'Edad máxima', valor: valorCampo(grupo.rangoEdadMax), atributos: { min: '0' } },
      ],
      comprobar: comprobarRango,
    });
    if (!resultado.confirmado || !resultado.valores) {
      return;
    }
    const min = edad(resultado.valores['rangoEdadMin']);
    const max = edad(resultado.valores['rangoEdadMax']);
    const cuerpo: any = { id: grupo.id, nombre: grupo.nombre, deporte: grupo.deporte, rangoEdadMin: min, rangoEdadMax: max };
    this.endpoints.actualizarGrupo(grupo.id, cuerpo).subscribe({
      next: () => {
        grupo.rangoEdadMin = min;
        grupo.rangoEdadMax = max;
        this.avisos.exito('Rango de edad actualizado');
        this.cdr.markForCheck();
      },
      error: () => {
        this.avisoError('No se pudo actualizar el rango de edad');
      },
    });
  }

  /** `DELETE /api/grupos/{id}` tras la confirmación de hoy; después vuelve a leer grupos y turnos, como hoy. */
  async eliminarGrupo(grupo: GrupoResumen): Promise<void> {
    const { confirmado } = await this.confirmacion.confirmar({
      titulo: '¿Estás seguro?',
      mensaje: 'No podrás revertir esto',
      accion: 'Sí, eliminarlo',
      cancelar: 'Cancelar',
      destructivo: true,
      nombre: 'eliminar-grupo',
    });
    if (!confirmado) {
      return;
    }
    this.endpoints.eliminarGrupo(grupo.id).subscribe({
      next: () => {
        this.avisos.exito('Grupo eliminado correctamente');
        this.abiertas.delete(grupo.id);
        this.cargarGrupos(true);
        this.cargarTurnos(true);
      },
      error: () => {
        this.avisoError('No se pudo eliminar el grupo');
      },
    });
  }
}

function valorCampo(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '' : String(valor);
}

function edad(valor: string | undefined): number | null {
  return valor === undefined || valor.trim() === '' ? null : Number(valor);
}

/** La validación de hoy (`editarRangoEdad`), con sus mensajes en línea bajo el campo que falla. */
export function comprobarRango(valores: ValoresDialogo): ErrorCampo | null {
  const min = edad(valores['rangoEdadMin']);
  const max = edad(valores['rangoEdadMax']);
  if (min !== null && (Number.isNaN(min) || min < 0)) {
    return { campo: 'rangoEdadMin', mensaje: 'La edad mínima debe ser un número válido.' };
  }
  if (max !== null && (Number.isNaN(max) || max < 0)) {
    return { campo: 'rangoEdadMax', mensaje: 'La edad máxima debe ser un número válido.' };
  }
  if (min !== null && max !== null && min > max) {
    return { campo: 'rangoEdadMin', mensaje: 'La edad mínima no puede ser mayor que la máxima.' };
  }
  return null;
}
