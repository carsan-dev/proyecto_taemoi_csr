import { ChangeDetectorRef, Component, DestroyRef, ElementRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, Subscription, catchError, finalize, forkJoin, map, of, switchMap } from 'rxjs';

import { AlumnoDeLista, ClaseAsistencia, ClasesDelGrupo, FestivoDeClase, MotivoFalta, PrefijoAsistencia, SedeDeClase } from '../../interfaces/asistencia';
import { DeporteEnLista } from '../../interfaces/profesor';
import { PermisosSesion, PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
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

/**
 * Los 409 que piden recargar la clase. La API no manda un código estable (solo `{ mensaje }`), así que se reconocen por
 * su texto; si cambia allí, cambia aquí:
 * - concurrencia: GlobalExceptionHandler.MENSAJE_CONCURRENCIA (GlobalExceptionHandler.java:126).
 * - clase festiva: AsistenciaService.FESTIVO_EN «Ese día es festivo en %s.» (AsistenciaService.java:101).
 */
const MENSAJE_CONCURRENCIA = 'Otra persona ha cambiado este dato a la vez; vuelve a intentarlo';
const PREFIJO_FESTIVO = 'Ese día es festivo';
import { ConCambiosSinGuardar } from '../productos-alumno/salir-sin-guardar.guard';
import { textoSalirSinGuardar } from '../productos-alumno/productos-alumno-vista';
import { ImpactoFestivo, textoFestivoDeClase } from '../festivos/festivos-vista';
import { FestivosService } from '../festivos/festivos.service';
import {
  MOTIVOS_FALTA, cuenta, estadoClase, fechaClase, fechaClaseCapital, franja, mismaClase, textoFaltas, textoMotivo, tieneLista,
} from './asistencia-textos';
import { DialogoFestivoComponent } from './dialogo-festivo.component';

const FECHA_ISO = /^\d{4}-\d{2}-\d{2}$/;
/** Menú de motivo: aire sobre la barra fija, solape con su fila (el `6px` del CSS) y lo que tapa la cabecera fija. */
const MARGEN_MENU = 8;
const SOLAPE_MENU = 6;
const TECHO_MENU = 64 + MARGEN_MENU;

export const TEXTOS_PASAR_LISTA = {
  errorCarga: 'No se ha podido cargar la lista.',
  errorGuardar: 'No se ha podido guardar la lista. Inténtalo de nuevo.',
  sinAlumnos: 'Este turno no tiene alumnos asignados.',
  sinClases: 'No hay clases en los últimos 14 días.',
  errorImpacto: 'No se han podido consultar las clases de ese día. Inténtalo de nuevo.',
  errorMarcarFestivo: 'No se ha podido marcar el festivo. Inténtalo de nuevo.',
  errorQuitarFestivo: 'No se ha podido quitar el festivo. Inténtalo de nuevo.',
  profesorFestivo: 'Si ese día hay clase, pide a la escuela que quite el festivo.',
  errorBorrarLista: 'No se ha podido borrar la lista. Inténtalo de nuevo.',
} as const;

/**
 * Pasar lista (decisión 324; boceto `Lista.dc.html`): una clase es un turno y una fecha. Una sola pantalla con dos rutas
 * (`data.prefijoAsistencia`: profesor o gestión). Sin marcar cuenta como falta; guardar reemplaza la lista de esa
 * clase. Todo contador usa el tamaño de la lista de la clase, nunca el del grupo.
 *
 * Motivo de la falta y festivos (decisiones 455-460; boceto `boceto-pasar-lista-motivos-festivos.html`): cada fila sin
 * marcar lleva «Motivo», un menú con «Sin motivo» y los cuatro motivos; marcar presente lo borra. Desde la ruta de
 * gestión, «Marcar festivo» crea un festivo de un día en la sede del grupo. Una clase festiva sin lista enseña el
 * bloque del festivo en lugar de la lista.
 *
 * Festivo sobre una lista guardada (decisiones 495-496): las acciones de festivo son de quien gestiona festivos
 * (`gestionaFestivos`), también desde «Mis grupos». Marcar festivo en una clase con lista avisa en rojo y, al confirmar,
 * borra esa lista; una clase con lista que ya cae en festivo (`festivoConLista`) lo dice encima de la lista con
 * «Borrar lista».
 */
@Component({
  selector: 'pn-pasar-lista',
  standalone: true,
  imports: [
    RouterLink, AcentoEscuelaDirective, BloqueErrorComponent, BloqueVacioComponent, BotonOcupadoComponent, DialogoFestivoComponent,
    EsqueletoComponent, PanelLateralComponent,
  ],
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
  private readonly festivos = inject(FestivosService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private carga?: Subscription;
  private cargaLista?: Subscription;

  readonly textos = TEXTOS_PASAR_LISTA;
  readonly esqueletos = [0, 1, 2, 3, 4, 5];
  readonly fechaClaseCapital = fechaClaseCapital;
  readonly franja = franja;
  readonly estadoClase = estadoClase;
  readonly tieneLista = tieneLista;
  readonly nombreCompleto = nombreCompleto;
  readonly motivosFalta = MOTIVOS_FALTA;
  readonly textoMotivo = textoMotivo;

  prefijo: PrefijoAsistencia = 'profesor';
  grupoId = 0;
  nombreGrupo = '';
  clases: ClasesDelGrupo | null = null;
  clase: ClaseAsistencia | null = null;
  alumnos: AlumnoDeLista[] = [];
  /** Marcas actuales y las de partida (servidor o último guardado): su diferencia son los cambios sin guardar. */
  marcados = new Set<number>();
  private base = new Set<number>();
  /** Motivos de las faltas, actuales y de partida (como las marcas). */
  motivos = new Map<number, MotivoFalta>();
  private baseMotivos = new Map<number, MotivoFalta>();
  /** Alumno con el menú de motivo abierto. */
  menuAbierto: number | null = null;
  /** El menú se abre hacia arriba cuando hacia abajo lo taparía la barra fija. */
  menuArriba = false;
  sede: SedeDeClase | null = null;
  /** Permisos de la sesión; null hasta que llegan o si fallan (las acciones de festivo fallan cerradas). */
  private permisosSesion: PermisosSesion | null = null;
  /** La sede se nombra en la línea de la clase solo con dos o más sedes (regla 9-1). */
  variasSedes = false;
  consultandoImpacto = false;
  dialogoFestivo: ImpactoFestivo | null = null;
  marcandoFestivo = false;
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
    // Falla cerrado: sin permisos, la línea no nombra la sede.
    this.permisos.obtener().pipe(takeUntilDestroyed(this.destruir), catchError(() => of(null))).subscribe((p) => {
      this.variasSedes = !!p?.variasSedes;
      this.permisosSesion = p;
    });
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
      this.cargarLista(conocida ?? { turnoId: turno, fecha, horaInicio: '', horaFin: '', pasada: false, presentes: null, total: null, alumnos: 0, festivo: null }, false);
      return;
    }
    const hoy = clases.hoy.find((c) => !tieneLista(c) && !c.festivo) ?? clases.hoy.find((c) => !c.festivo) ?? clases.hoy[0];
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
        this.sede = lista.sede ?? null;
        this.menuAbierto = null;
        // Lista sin guardar: ninguna marcada ni con motivo, vengan como vengan.
        const guardada = tieneLista(lista.clase);
        const inicial = new Set(guardada ? lista.alumnos.filter((a) => a.presente).map((a) => a.id) : []);
        this.base = inicial;
        this.marcados = new Set(inicial);
        const motivos = new Map<number, MotivoFalta>();
        if (guardada) {
          for (const a of lista.alumnos) if (!a.presente && a.motivo) motivos.set(a.id, a.motivo);
        }
        this.baseMotivos = motivos;
        this.motivos = new Map(motivos);
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
  get titulo(): string {
    if (!this.clase) return '';
    const sede = this.variasSedes && this.sede ? ` · ${this.sede.nombre}` : '';
    return `${fechaClaseCapital(this.clase.fecha)} · ${franja(this.clase)}${sede}`;
  }
  get textoPresentes(): string { return this.marcados.size === 1 ? 'presente' : 'presentes'; }
  get faltas(): string {
    const ausentes = this.alumnos.filter((a) => !this.marcados.has(a.id));
    return textoFaltas(ausentes.length, ausentes.filter((a) => this.motivos.has(a.id)).length);
  }

  estaMarcado(alumno: AlumnoDeLista): boolean { return this.marcados.has(alumno.id); }

  alternar(alumno: AlumnoDeLista): void {
    if (this.guardando) return;
    const siguiente = new Set(this.marcados);
    if (!siguiente.delete(alumno.id)) {
      siguiente.add(alumno.id);
      // Presente: la falta y su motivo se van.
      this.quitarMotivo(alumno.id);
    }
    this.marcados = siguiente;
    this.menuAbierto = null;
    this.guardada = false;
  }

  alternarTodos(): void {
    if (this.guardando) return;
    const marcar = !this.todosMarcados;
    this.marcados = marcar ? new Set(this.alumnos.map((a) => a.id)) : new Set();
    if (marcar) this.motivos = new Map();
    this.menuAbierto = null;
    this.guardada = false;
  }

  // ------------------------------------------------------------------ motivo de la falta
  motivoDe(alumno: AlumnoDeLista): MotivoFalta | null { return this.motivos.get(alumno.id) ?? null; }

  etiquetaMotivo(alumno: AlumnoDeLista): string {
    const motivo = this.motivoDe(alumno);
    return `Motivo de la falta de ${nombreCompleto(alumno.nombre, alumno.apellidos)}: ${motivo ? textoMotivo(motivo) : 'sin motivo'}`;
  }

  alternarMenu(alumno: AlumnoDeLista): void {
    if (this.guardando) return;
    if (this.menuAbierto === alumno.id) {
      this.cerrarMenu(true);
      return;
    }
    this.menuAbierto = alumno.id;
    this.menuArriba = false;
    this.cdr.detectChanges();
    const menu = this.host.nativeElement.querySelector<HTMLElement>('.pasar-lista__menu');
    if (menu) this.colocarMenu(menu);
    (menu?.querySelector<HTMLElement>('[aria-checked="true"]') ?? menu?.querySelector<HTMLElement>('[role="menuitemradio"]'))?.focus({ preventScroll: true });
  }

  /**
   * El menú baja desde su fila; si la barra fija de guardar lo taparía (la última fila), sube. Si tampoco cabe arriba
   * (pantalla baja), se queda abajo y la página se desplaza lo que falte para verlo entero.
   */
  private colocarMenu(menu: HTMLElement): void {
    const fila = menu.closest('.pasar-lista__item');
    if (!fila) return;
    const vista = this.host.nativeElement.ownerDocument.defaultView;
    const barra = this.host.nativeElement.querySelector('.pasar-lista__barra');
    const limite = (barra ? barra.getBoundingClientRect().top : (vista?.innerHeight ?? Infinity)) - MARGEN_MENU;
    const caja = menu.getBoundingClientRect();
    if (caja.bottom <= limite) return;
    if (fila.getBoundingClientRect().top + SOLAPE_MENU - caja.height >= TECHO_MENU) {
      this.menuArriba = true;
      this.cdr.detectChanges();
      return;
    }
    vista?.scrollBy({ top: Math.ceil(caja.bottom - limite) });
  }

  elegirMotivo(alumno: AlumnoDeLista, motivo: MotivoFalta | null): void {
    if (motivo) {
      this.motivos = new Map(this.motivos).set(alumno.id, motivo);
    } else {
      this.quitarMotivo(alumno.id);
    }
    this.guardada = false;
    this.cerrarMenu(true);
  }

  private quitarMotivo(id: number): void {
    if (!this.motivos.has(id)) return;
    const siguiente = new Map(this.motivos);
    siguiente.delete(id);
    this.motivos = siguiente;
  }

  /** Cierra el menú; con `enfocar`, el foco vuelve a su botón. */
  private cerrarMenu(enfocar: boolean): void {
    const id = this.menuAbierto;
    this.menuAbierto = null;
    if (!enfocar || id === null) return;
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`[data-motivo="${id}"]`)?.focus();
  }

  /** Flechas (con vuelta), Inicio, Fin y Esc, como en el boceto. */
  teclaMenu(evento: KeyboardEvent): void {
    const opciones = Array.from((evento.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('[role="menuitemradio"]'));
    const i = opciones.indexOf(this.host.nativeElement.ownerDocument.activeElement as HTMLElement);
    let destino: number;
    switch (evento.key) {
      case 'Escape':
        evento.preventDefault();
        evento.stopPropagation();
        this.cerrarMenu(true);
        return;
      case 'Tab':
        this.menuAbierto = null;
        return;
      case 'ArrowDown': destino = (i + 1) % opciones.length; break;
      case 'ArrowUp': destino = (i - 1 + opciones.length) % opciones.length; break;
      case 'Home': destino = 0; break;
      case 'End': destino = opciones.length - 1; break;
      default: return;
    }
    evento.preventDefault();
    opciones[destino]?.focus();
  }

  @HostListener('document:click', ['$event'])
  alClicFuera(evento: MouseEvent): void {
    if (this.menuAbierto === null) return;
    const objetivo = evento.target as HTMLElement | null;
    if (objetivo?.closest('.pasar-lista__menu, .pasar-lista__motivo')) return;
    this.menuAbierto = null;
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
    const motivos: Record<number, MotivoFalta> = {};
    for (const a of this.alumnos) {
      const motivo = this.motivos.get(a.id);
      if (motivo && !this.marcados.has(a.id)) motivos[a.id] = motivo;
    }
    this.guardando = true;
    this.menuAbierto = null;
    this.asistencia.guardar(this.prefijo, this.grupoId, clase.turnoId, clase.fecha, marcados, motivos).pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.guardando = false)),
    ).subscribe({
      next: (guardada) => {
        this.base = new Set(this.marcados);
        this.baseMotivos = new Map(this.motivos);
        this.guardada = true;
        this.clase = guardada;
        this.actualizarReciente(guardada);
      },
      // Las marcas se conservan: solo se avisa.
      error: (e) => {
        const mensaje = mensajeDelServidor(e, this.textos.errorGuardar);
        this.avisos.error(mensaje);
        // Recargan el 409 de concurrencia y el de «ese día es festivo» (la clase se ve como festiva); cualquier otro
        // 409 (p. ej. lista de otro grupo) conserva lo marcado y solo avisa.
        if (e?.status === 409 && (mensaje === MENSAJE_CONCURRENCIA || mensaje.startsWith(PREFIJO_FESTIVO))) {
          this.recargarTrasFestivo(clase);
        }
      },
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
    if (this.guardando) return;
    if (this.esElegida(clase) && !this.error) {
      this.hojaAbierta = false;
      return;
    }
    if (this.hayCambios && !(await this.confirmarSalida('¿Cambiar de clase sin guardar?', 'Cambiar de clase'))) return;
    this.marcados = new Set(this.base);
    this.motivos = new Map(this.baseMotivos);
    this.hojaAbierta = false;
    this.cargarLista(clase, true);
  }

  // ------------------------------------------------------------------ festivo
  get festivo(): FestivoDeClase | null { return this.clase?.festivo ?? null; }

  /** Festivo que cubre una clase que ya tiene lista guardada (caso B). */
  get festivoConLista(): FestivoDeClase | null { return this.festivo ? null : this.clase?.festivoConLista ?? null; }

  /**
   * Quién marca, quita festivos y borra la lista de un festivo (decisiones 495-496): la ruta de gestión o, desde «Mis
   * grupos», propietario, administrador o el responsable de la sede del grupo (roles de membresía de
   * `/api/auth/permisos`, no `ROLE_*`). Sin permisos (o si fallan), no.
   */
  get gestionaFestivos(): boolean {
    if (this.esGestion) return true;
    const p = this.permisosSesion;
    const roles = p?.roles ?? [];
    if (roles.includes('PROPIETARIO') || roles.includes('ADMINISTRADOR')) return true;
    const sede = this.sede;
    return !!sede && roles.includes('RESPONSABLE_SEDE') && (p?.sedes ?? []).some((s) => s.id === sede.id);
  }

  /** Con la sede del grupo conocida y una clase que aún no es festiva (ni festiva con lista). */
  get puedeMarcarFestivo(): boolean {
    return this.gestionaFestivos && !!this.sede && !!this.clase && !this.festivo && !this.festivoConLista && !this.cargandoLista && !this.error;
  }

  /** Caso B: nota encima de la lista con «Borrar lista». */
  get puedeBorrarLista(): boolean {
    return this.gestionaFestivos && !!this.sede && !!this.festivoConLista;
  }

  get textoFestivoConLista(): string {
    const f = this.festivoConLista;
    if (!f || !this.sede) return '';
    const nombre = f.nombre ? ` (${f.nombre})` : '';
    return `Ese día es festivo en ${this.sede.nombre}${nombre}, pero esta clase tiene lista guardada.`;
  }

  /** Caso A: la lista de esta clase, que se borra al marcar el festivo; null si no tiene. */
  get listaQueSeBorra(): { presentes: number; total: number } | null {
    const c = this.clase;
    if (!c || c.festivo || c.presentes === null) return null;
    return { presentes: c.presentes, total: c.total ?? c.alumnos };
  }

  get tituloFestivo(): string {
    const f = this.festivo;
    if (!f) return '';
    const base = this.sede ? `Festivo en ${this.sede.nombre}` : 'Festivo';
    return f.nombre ? `${base} · ${f.nombre}` : base;
  }

  get textoFestivo(): string {
    const f = this.festivo;
    if (!this.clase || !f) return '';
    return textoFestivoDeClase(f, this.clase.fecha, this.sede?.nombre ?? null);
  }

  get fechaEnFrase(): string { return this.clase ? `el ${fechaClase(this.clase.fecha)}` : ''; }

  /** Consulta las clases de esa sede ese día y abre la confirmación. */
  marcarFestivo(): void {
    const clase = this.clase;
    const sede = this.sede;
    if (!clase || !sede || this.consultandoImpacto || this.guardando) return;
    this.consultandoImpacto = true;
    this.festivos.impacto(this.cuerpoFestivo(clase, sede, '')).pipe(
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.consultandoImpacto = false)),
    ).subscribe({
      next: (impacto) => (this.dialogoFestivo = impacto),
      error: (e) => this.avisos.error(mensajeDelServidor(e, this.textos.errorImpacto)),
    });
  }

  cerrarDialogoFestivo(): void {
    if (!this.marcandoFestivo) this.dialogoFestivo = null;
  }

  confirmarFestivo(nombre: string): void {
    const clase = this.clase;
    const sede = this.sede;
    if (!clase || !sede || this.marcandoFestivo) return;
    // Caso A: con lista en esta clase, tras crear el festivo se borra esa lista. Si el borrado falla, el festivo ya está
    // creado: se avisa y se recarga igual (la clase pasa a «festivo con lista»).
    const borrarLista = !!this.listaQueSeBorra;
    this.marcandoFestivo = true;
    this.festivos.crear(this.cuerpoFestivo(clase, sede, nombre)).pipe(
      switchMap(() => !borrarLista
        ? of({ errorBorrar: null })
        : this.asistencia.borrarLista(this.grupoId, clase.turnoId, clase.fecha).pipe(
          map(() => ({ errorBorrar: null })),
          catchError((e: unknown) => of({ errorBorrar: e ?? {} })),
        )),
      takeUntilDestroyed(this.destruir),
      finalize(() => (this.marcandoFestivo = false)),
    ).subscribe({
      next: ({ errorBorrar }) => {
        this.dialogoFestivo = null;
        if (errorBorrar) {
          this.avisos.error(mensajeDelServidor(errorBorrar, this.textos.errorBorrarLista));
        } else {
          this.avisos.exito('Festivo marcado.');
        }
        this.recargarTrasFestivo(clase);
      },
      // El diálogo sigue abierto con lo escrito.
      error: (e) => this.avisos.error(mensajeDelServidor(e, this.textos.errorMarcarFestivo)),
    });
  }

  /** Caso B: borra la lista guardada de una clase festiva; al acabar, la clase se ve como festivo. */
  async borrarListaFestiva(): Promise<void> {
    const clase = this.clase;
    if (!clase || !this.puedeBorrarLista || this.guardando) return;
    const n = clase.total ?? clase.alumnos;
    const r = await this.confirmacion.confirmar<void>({
      titulo: `¿Borrar la lista del ${fechaClase(clase.fecha)}?`,
      mensaje: `La clase quedará como festivo y nadie tendrá falta. Se borran las marcas de ${n} ${n === 1 ? 'alumno' : 'alumnos'}. No se puede deshacer.`,
      accion: 'Borrar lista',
      destructivo: true,
      nombre: 'borrar-lista-festivo',
      ejecutar: () => this.asistencia.borrarLista(this.grupoId, clase.turnoId, clase.fecha),
    });
    if (!r.confirmado) return;
    if (r.error) {
      this.avisos.error(mensajeDelServidor(r.error, this.textos.errorBorrarLista));
      // Un 404/409 puede venir de otra persona de gestión: se recarga para no dejar la pantalla desfasada.
      this.recargarTrasFestivo(clase);
      return;
    }
    this.recargarTrasFestivo(clase);
  }

  async quitarFestivo(): Promise<void> {
    const clase = this.clase;
    const festivo = this.festivo;
    if (!clase || !festivo) return;
    const donde = this.sede ? ` en ${this.sede.nombre}` : '';
    const r = await this.confirmacion.confirmar<void>({
      titulo: `¿Quitar el festivo del ${fechaClase(clase.fecha)}${donde}?`,
      mensaje: `Ese día volverá a haber clase${donde}: sus clases pedirán lista otra vez. No se borra ninguna lista.`,
      accion: 'Quitar festivo',
      accionOcupada: 'Quitando…',
      destructivo: true,
      nombre: 'quitar-festivo',
      ejecutar: () => this.festivos.quitar(festivo.id),
    });
    if (!r.confirmado) return;
    if (r.error) {
      this.avisos.error(mensajeDelServidor(r.error, this.textos.errorQuitarFestivo));
      return;
    }
    this.avisos.exito('Festivo quitado.');
    this.recargarTrasFestivo(clase);
  }

  private cuerpoFestivo(clase: ClaseAsistencia, sede: SedeDeClase, nombre: string) {
    return { tipo: 'DIA' as const, fechaDesde: clase.fecha, fechaHasta: clase.fecha, sedeIds: [sede.id], ...(nombre ? { nombre } : {}) };
  }

  /** La lista y la hoja «Elegir clase» se ponen al día; si la hoja falla, se queda como estaba. */
  private recargarTrasFestivo(clase: ClaseAsistencia): void {
    this.asistencia.clases(this.prefijo, this.grupoId).pipe(takeUntilDestroyed(this.destruir)).subscribe({
      next: (clases) => (this.clases = clases),
      error: () => undefined,
    });
    this.cargarLista(clase, false);
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

  /** Alumnos cuya marca o motivo difiere de la de partida. */
  private cambiosPendientes(): number {
    let n = 0;
    const ids = new Set([...this.marcados, ...this.base, ...this.motivos.keys(), ...this.baseMotivos.keys()]);
    for (const id of ids) {
      const marca = this.marcados.has(id) !== this.base.has(id);
      const motivo = (this.motivos.get(id) ?? null) !== (this.baseMotivos.get(id) ?? null);
      if (marca || motivo) n++;
    }
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
