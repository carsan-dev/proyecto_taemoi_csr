import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, finalize, forkJoin, map, of } from 'rxjs';

import { AlumnoDeLista, ClaseAsistencia, ClasesDelGrupo, PrefijoAsistencia } from '../../interfaces/asistencia';
import { DeporteEnLista } from '../../interfaces/profesor';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AsistenciaService } from '../../servicios/endpoints/asistencia.service';
import { ProfesorService } from '../../servicios/endpoints/profesor.service';
import { gradoFila, gradoParaFrase } from '../alumnos/alumno-vista';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { contextoErrorLocal } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import { mensajeDelServidor, nombreCompleto } from '../mis-grupos/profesor-textos';
import { ConCambiosSinGuardar } from '../productos-alumno/salir-sin-guardar.guard';
import { textoSalirSinGuardar } from '../productos-alumno/productos-alumno-vista';
import { cuenta, estadoClase, fechaClaseCapital, franja, mismaClase, tieneLista } from './asistencia-textos';

const FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/;

export const TEXTOS_PASAR_LISTA = {
  errorCarga: 'No se ha podido cargar la lista.',
  errorGuardar: 'No se ha podido guardar la lista. Inténtalo de nuevo.',
  sinAlumnos: 'Este turno no tiene alumnos asignados.',
  sinClases: 'No hay clases en los últimos 14 días.',
} as const;

/**
 * Pasar lista (decisión 324; boceto `Lista.dc.html`): una clase es un turno y una fecha. Una sola pantalla con dos rutas
 * (`data.prefijoAsistencia`: profesor o gestión). Sin marcar cuenta como falta; guardar reemplaza la lista de esa
 * clase. Todo contador usa el tamaño de la lista de la clase, nunca el del grupo.
 */
@Component({
  selector: 'pn-pasar-lista',
  standalone: true,
  imports: [RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, BloqueVacioComponent, BotonOcupadoComponent, EsqueletoComponent, PanelLateralComponent],
  templateUrl: './pasar-lista.component.html',
  styleUrl: './pasar-lista.component.scss',
})
export class PasarListaComponent implements OnInit, ConCambiosSinGuardar {
  private readonly asistencia = inject(AsistenciaService);
  private readonly profesor = inject(ProfesorService);
  private readonly endpoints = inject(EndpointsService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly destruir = inject(DestroyRef);
  private carga?: Subscription;
  private cargaLista?: Subscription;

  readonly textos = TEXTOS_PASAR_LISTA;
  readonly esqueletos = [0, 1, 2, 3, 4, 5];
  readonly fechaClaseCapital = fechaClaseCapital;
  readonly franja = franja;
  readonly estadoClase = estadoClase;
  readonly nombreCompleto = nombreCompleto;

  prefijo: PrefijoAsistencia = 'profesor';
  grupoId = 0;
  nombreGrupo = '';
  clases: ClasesDelGrupo | null = null;
  clase: ClaseAsistencia | null = null;
  alumnos: AlumnoDeLista[] = [];
  /** Marcas actuales y las de partida (servidor o último guardado): su diferencia son los cambios sin guardar. */
  marcados = new Set<number>();
  private base = new Set<number>();
  cargando = true;
  cargandoLista = false;
  error: string | null = null;
  guardando = false;
  guardada = false;
  hojaAbierta = false;
  private reintento: () => void = () => this.cargar();

  ngOnInit(): void {
    const datos = this.ruta.snapshot.data;
    this.prefijo = datos['prefijoAsistencia'] === 'gestion' ? 'gestion' : 'profesor';
    this.grupoId = Number(this.ruta.snapshot.paramMap.get('grupoId'));
    this.cargar();
  }

  // ------------------------------------------------------------------ enlaces
  get esGestion(): boolean { return this.prefijo === 'gestion'; }
  get enlaceListado(): string[] { return [this.esGestion ? '/gruposListar' : '/mis-grupos']; }
  get textoListado(): string { return this.esGestion ? 'Grupos' : 'Mis grupos'; }
  get enlaceGrupo(): (string | number)[] { return this.esGestion ? ['/gruposEditar', this.grupoId] : ['/mis-grupos', this.grupoId]; }

  // ------------------------------------------------------------------ carga
  cargar(): void {
    this.carga?.unsubscribe();
    this.cargando = true;
    this.error = null;
    this.reintento = () => this.cargar();
    this.carga = forkJoin({ clases: this.asistencia.clases(this.prefijo, this.grupoId), nombre: this.nombreDelGrupo() }).pipe(
      takeUntilDestroyed(this.destruir),
    ).subscribe({
      next: ({ clases, nombre }) => {
        this.clases = clases;
        this.nombreGrupo = nombre;
        this.cargando = false;
        this.elegirInicial(clases);
      },
      error: (e) => {
        this.cargando = false;
        this.error = mensajeDelServidor(e, this.textos.errorCarga);
      },
    });
  }

  private nombreDelGrupo(): Observable<string> {
    const nombre$ = this.esGestion
      ? this.endpoints.obtenerGrupoPorId(this.grupoId, contextoErrorLocal()).pipe(map((g) => g.nombre))
      : this.profesor.misGrupos().pipe(map((gs) => gs.find((g) => g.id === this.grupoId)?.nombre ?? ''));
    // El nombre solo adorna las migas: si falla, la lista sigue.
    return nombre$.pipe(catchError(() => of('')));
  }

  /** `?turno&fecha`; si no, la clase de hoy (la primera sin lista) o, sin hoy, la más reciente con la hoja abierta. */
  private elegirInicial(clases: ClasesDelGrupo): void {
    const consulta = this.ruta.snapshot.queryParamMap;
    const turno = Number(consulta.get('turno'));
    const fecha = consulta.get('fecha') ?? '';
    if (Number.isInteger(turno) && turno > 0 && FECHA_ISO.test(fecha)) {
      const conocida = [...clases.hoy, ...clases.recientes].find((c) => c.turnoId === turno && c.fecha === fecha);
      this.cargarLista(conocida ?? { turnoId: turno, fecha, horaInicio: '', horaFin: '', pasada: false, presentes: null, total: null, alumnos: 0 }, false);
      return;
    }
    const hoy = clases.hoy.find((c) => !tieneLista(c)) ?? clases.hoy[0];
    if (hoy) {
      this.cargarLista(hoy, false);
    } else if (clases.recientes.length) {
      this.cargarLista(clases.recientes[0], false);
      this.hojaAbierta = true;
    }
  }

  /** Pide la lista de una clase. `escribirUrl`: deja `turno` y `fecha` en la URL (sin añadir historial). */
  private cargarLista(clase: ClaseAsistencia, escribirUrl: boolean): void {
    this.cargaLista?.unsubscribe();
    this.cargandoLista = true;
    this.error = null;
    this.guardada = false;
    this.reintento = () => this.cargarLista(clase, escribirUrl);
    if (escribirUrl) {
      void this.router.navigate([], { relativeTo: this.ruta, queryParams: { turno: clase.turnoId, fecha: clase.fecha }, replaceUrl: true });
    }
    this.cargaLista = this.asistencia.lista(this.prefijo, this.grupoId, clase.turnoId, clase.fecha).pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.cargandoLista = false)),
    ).subscribe({
      next: (lista) => {
        this.clase = lista.clase;
        this.alumnos = lista.alumnos;
        // Lista sin guardar: ninguna marcada, vengan como vengan.
        const inicial = new Set(tieneLista(lista.clase) ? lista.alumnos.filter((a) => a.presente).map((a) => a.id) : []);
        this.base = inicial;
        this.marcados = new Set(inicial);
      },
      error: (e) => {
        this.clase = null;
        this.alumnos = [];
        this.error = mensajeDelServidor(e, this.textos.errorCarga);
      },
    });
  }

  reintentar(): void { this.reintento(); }

  // ------------------------------------------------------------------ marcas
  get total(): number { return this.alumnos.length; }
  get sinAlumnos(): boolean { return !!this.clase && this.alumnos.length === 0; }
  get cuenta(): string { return cuenta(this.marcados.size, this.total); }
  get todosMarcados(): boolean { return this.total > 0 && this.marcados.size === this.total; }
  get hayCambios(): boolean { return this.cambiosPendientes() > 0; }
  get titulo(): string { return this.clase ? `${fechaClaseCapital(this.clase.fecha)} · ${franja(this.clase)}` : ''; }

  estaMarcado(alumno: AlumnoDeLista): boolean { return this.marcados.has(alumno.id); }

  alternar(alumno: AlumnoDeLista): void {
    if (this.guardando) return;
    const siguiente = new Set(this.marcados);
    if (!siguiente.delete(alumno.id)) siguiente.add(alumno.id);
    this.marcados = siguiente;
    this.guardada = false;
  }

  alternarTodos(): void {
    if (this.guardando) return;
    this.marcados = this.todosMarcados ? new Set() : new Set(this.alumnos.map((a) => a.id));
    this.guardada = false;
  }

  /** El deporte del grupo (con varias disciplinas) o, si no, el primero. */
  deporteDe(alumno: AlumnoDeLista): DeporteEnLista | null {
    return alumno.deportes.find((d) => d.delGrupo) ?? alumno.deportes[0] ?? null;
  }

  gradoDe(alumno: AlumnoDeLista): { texto: string; fondo: string } {
    const deporte = this.deporteDe(alumno);
    if (!deporte?.grado) return { texto: 'Sin grado', fondo: '' };
    const grado = gradoFila(deporte.grado.codigo, deporte.deporte);
    return { texto: gradoParaFrase(grado), fondo: grado.fondo };
  }

  // ------------------------------------------------------------------ guardar
  guardar(): void {
    const clase = this.clase;
    if (!clase || this.guardando) return;
    const marcados = this.alumnos.filter((a) => this.marcados.has(a.id)).map((a) => a.id);
    this.guardando = true;
    this.asistencia.guardar(this.prefijo, this.grupoId, clase.turnoId, clase.fecha, marcados).pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.guardando = false)),
    ).subscribe({
      next: (guardada) => {
        this.base = new Set(this.marcados);
        this.guardada = true;
        this.clase = guardada;
        this.actualizarReciente(guardada);
      },
      // Las marcas se conservan: solo se avisa.
      error: (e) => this.avisos.error(mensajeDelServidor(e, this.textos.errorGuardar)),
    });
  }

  private actualizarReciente(guardada: ClaseAsistencia): void {
    if (!this.clases) return;
    const poner = (lista: ClaseAsistencia[]) =>
      lista.map((c) => (mismaClase(c, guardada) ? { ...c, presentes: guardada.presentes, total: guardada.total, alumnos: guardada.alumnos } : c));
    this.clases = { ...this.clases, hoy: poner(this.clases.hoy), recientes: poner(this.clases.recientes) };
  }

  // ------------------------------------------------------------------ elegir clase
  get recientes(): ClaseAsistencia[] { return this.clases?.recientes ?? []; }

  abrirHoja(): void { this.hojaAbierta = true; }
  cerrarHoja(): void { this.hojaAbierta = false; }

  esElegida(clase: ClaseAsistencia): boolean { return !!this.clase && mismaClase(this.clase, clase); }

  /** Dos clases el mismo día: la hora distingue las filas. */
  fechaDeFila(clase: ClaseAsistencia): string {
    const repetida = this.recientes.filter((c) => c.fecha === clase.fecha).length > 1;
    return repetida ? `${fechaClaseCapital(clase.fecha)} · ${clase.horaInicio}` : fechaClaseCapital(clase.fecha);
  }

  async elegirClase(clase: ClaseAsistencia): Promise<void> {
    if (this.esElegida(clase) && !this.error) {
      this.hojaAbierta = false;
      return;
    }
    if (this.hayCambios && !(await this.confirmarSalida('¿Cambiar de clase sin guardar?', 'Cambiar de clase'))) return;
    this.marcados = new Set(this.base);
    this.hojaAbierta = false;
    this.cargarLista(clase, true);
  }

  // ------------------------------------------------------------------ cambios sin guardar
  private async confirmarSalida(titulo: string, accion: string): Promise<boolean> {
    const r = await this.confirmacion.confirmar({
      titulo,
      mensaje: textoSalirSinGuardar(this.cambiosPendientes()),
      accion,
      cancelar: 'Seguir pasando lista',
      destructivo: true,
      nombre: 'salir-sin-guardar',
    });
    return r.confirmado;
  }

  /** Alumnos cuya marca difiere de la de partida. */
  private cambiosPendientes(): number {
    let n = 0;
    for (const id of this.marcados) if (!this.base.has(id)) n++;
    for (const id of this.base) if (!this.marcados.has(id)) n++;
    return n;
  }

  /** `canDeactivate` (`salirSinGuardarGuard`). */
  async puedeSalir(): Promise<boolean> {
    if (!this.hayCambios) return true;
    return this.confirmarSalida('¿Salir sin guardar?', 'Salir sin guardar');
  }

  @HostListener('window:beforeunload', ['$event'])
  avisarAlCerrar(evento: BeforeUnloadEvent): void {
    if (this.hayCambios) evento.preventDefault();
  }
}
