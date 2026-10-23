import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { Subject, Subscription, forkJoin, of } from 'rxjs';
import { catchError, map, takeUntil } from 'rxjs/operators';

import { SearchableSelectDirective } from '../../directives/searchable-select.directive';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { mensajeDeError } from '../../servicios/endpoints/caja.service';
import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import { AccionInforme, InformePdfService } from '../../servicios/generales/informe-pdf.service';
import { esSiguienteGradoRojo } from '../../utilities/grado-progresion';
import { AcentoEscuelaActual, AcentoEscuelaDirective } from '../acento-escuela.directive';
import { GradoFila, gradoFila } from '../alumnos/alumno-vista';
import { InformePdfPanelService } from '../alumnos/herramientas/informe-pdf-panel.service';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { fecha, fechaLarga, numero } from '../formato/formato-es';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import { variablesDelTema } from '../tema-escuela';
import { VisorPdfComponent } from '../visor-pdf/visor-pdf.component';
import {
  AlumnoConvocatoria,
  Cifras,
  ClaveCifra,
  ConvocatoriaPanel,
  POR_PAGINA,
  PestanaDisciplina,
  TAMANO_PAGINA_ALUMNOS,
  cifrasDe,
  cuantiaDeCampo,
  cuantiaParaCampo,
  diaParaCampo,
  fechaDeCampo,
  iniciales,
  nombreInforme,
  paginar,
  paginasVisibles,
  pestanasDisciplina,
  textoCifra,
  totalPaginas,
} from './convocatorias-vista';

type Estado = 'cargando' | 'listo' | 'error';

/** Un alumno apto tal como llega de `GET /alumnos/aptos/convocatoria`. */
interface AlumnoApto {
  id: number;
  nombre: string;
  apellidos: string;
  grado: string;
  fechaNacimiento?: string | Date | null;
}

/** Las cifras de un bloque: se leen con los alumnos de su convocatoria. */
interface CifrasBloque {
  estado: Estado;
  cifras?: Cifras;
}

/** La clave de hoy en `sessionStorage`: página y disciplina se recuerdan igual que en la pantalla de hoy. */
const CLAVE_ESTADO = 'listadoConvocatoriasEstado';

/**
 * Convocatorias del panel nuevo (8b-3e, Convocatorias C): un bloque por convocatoria con sus cifras (inscritos, pagados
 * y pendientes), pestañas por disciplina con su número en lugar del filtro por deporte, alumnos en lista con su grado
 * actual y el siguiente, cuantía editable, conmutador Pagado | Pendiente y cruz para quitar; al pie, el alumno apto, «PDF»
 * en un menú y «Actualizar grados de todos». Las MISMAS escrituras que la pantalla de hoy (`ListadoConvocatoriasComponent`):
 * endpoint, método, cuerpo y nombre del PDF; con el diálogo, los avisos, el visor de PDF y los estados de carga del panel
 * nuevo, sin SweetAlert2 ni cargador global. Va en su propio chunk detrás de `disenoNuevoGuard`.
 */
@Component({
  selector: 'app-convocatorias',
  standalone: true,
  imports: [
    AcentoEscuelaDirective,
    SearchableSelectDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    VisorPdfComponent,
  ],
  providers: [{ provide: InformePdfService, useClass: InformePdfPanelService }],
  templateUrl: './convocatorias.component.html',
  styleUrls: ['./convocatorias.component.scss'],
})
export class ConvocatoriasComponent implements OnInit, OnDestroy {
  readonly informes = inject(InformePdfService);
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  readonly catalogo = inject(CatalogoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly temaActual = inject(AcentoEscuelaActual);

  /** La lista flotante del buscador va en el body, fuera del `.pn` de la pantalla: lleva copiado el tema de la escuela. */
  readonly variablesDesplegable = computed(() => variablesDelTema(this.temaActual.aplicado()));

  readonly porPagina = POR_PAGINA;
  readonly numero = numero;
  readonly fecha = fecha;
  readonly textoCifra = textoCifra;
  readonly iniciales = iniciales;
  readonly claves: ClaveCifra[] = ['inscritos', 'pagados', 'pendientes'];

  estado: Estado = 'cargando';
  deporte = this.catalogo.disciplinasConGrados()[0]?.codigo ?? '';
  /** Las convocatorias de cada disciplina con grados, por código. */
  listas: Record<string, ConvocatoriaPanel[]> = {};
  pagina = 1;
  /** Cifras de cada bloque, por id de convocatoria. */
  readonly cifras = new Map<number, CifrasBloque>();

  /** La convocatoria abierta («Ver alumnos»): una a la vez, como hoy. */
  abierta: ConvocatoriaPanel | null = null;
  estadoAlumnos: Estado = 'listo';
  alumnos: AlumnoConvocatoria[] = [];
  aptos: AlumnoApto[] = [];
  aptoElegido: number | null = null;
  /** «Cargar más alumnos» (lo de hoy: pide la página siguiente del listado y vuelve a leer los aptos). */
  paginaAlumnos = 1;
  alumnosCargadosCompletamente = false;
  /** Inscripciones con una escritura en vuelo: sin doble envío. */
  readonly enProceso = new Set<number>();
  agregando = false;
  /** Las preguntas de «Agregar alumno» están abiertas: no se abren otras encima. */
  private preguntando = false;
  menuPdf = false;

  private carga?: Subscription;
  private lecturaBloque?: Subscription;
  /** La lectura de cifras en curso de cada bloque: una nueva cancela la anterior. */
  private readonly lecturasCifras = new Map<number, Subscription>();
  /** Generación de las cifras de cada bloque: la respuesta de una lectura más vieja que la última escrita se descarta. */
  private readonly generacionCifras = new Map<number, number>();
  private contadorGeneracion = 0;
  /** Disciplinas cuya lista no se pudo leer: su pestaña va sin número. */
  readonly sinNumero = new Set<string>();
  private readonly destroy$ = new Subject<void>();
  private escuchaFuera = false;

  get disciplinas(): { codigo: string; nombre: string }[] {
    return this.catalogo.disciplinasConGrados().map((d) => ({ codigo: d.codigo, nombre: d.nombre }));
  }

  get pestanas(): PestanaDisciplina[] {
    const totales: Record<string, number> = {};
    for (const [codigo, lista] of Object.entries(this.listas)) {
      totales[codigo] = lista.length;
    }
    return pestanasDisciplina(
      this.disciplinas,
      totales,
      this.catalogo.disciplinasActivas().map((d) => d.codigo)
    );
  }

  get pestanaActual(): PestanaDisciplina | undefined {
    return this.pestanas.find((p) => p.codigo === this.deporte);
  }

  get convocatorias(): ConvocatoriaPanel[] {
    return this.listas[this.deporte] ?? [];
  }

  get totalPaginas(): number {
    return totalPaginas(this.convocatorias.length, POR_PAGINA);
  }

  get visibles(): ConvocatoriaPanel[] {
    return paginar(this.convocatorias, this.pagina, POR_PAGINA);
  }

  get paginas(): (number | null)[] {
    return paginasVisibles(this.pagina, this.totalPaginas);
  }

  ngOnInit(): void {
    this.restaurarEstado();
    this.cargar();
  }

  ngOnDestroy(): void {
    this.carga?.unsubscribe();
    this.lecturaBloque?.unsubscribe();
    this.lecturasCifras.forEach((l) => l.unsubscribe());
    this.quitarEscucha();
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ------------------------------------------------------------------ lecturas

  /** `GET /convocatorias?deporte=` de cada disciplina con grados (hoy, la de la disciplina elegida): da los números. */
  cargar(): void {
    this.carga?.unsubscribe();
    this.estado = 'cargando';
    const disciplinas = this.disciplinas;
    if (disciplinas.length === 0) {
      this.listas = {};
      this.estado = 'listo';
      return;
    }
    if (!disciplinas.some((d) => d.codigo === this.deporte)) {
      this.deporte = disciplinas[0].codigo;
    }
    this.sinNumero.clear();
    const lecturas: Record<string, ReturnType<LecturasPanelService['convocatorias']>> = {};
    for (const d of disciplinas) {
      // Si falla una disciplina, la pantalla sigue y su pestaña va sin número.
      lecturas[d.codigo] = this.lecturas.convocatorias(d.codigo).pipe(
        catchError(() => {
          this.sinNumero.add(d.codigo);
          return of([] as any[]);
        })
      );
    }
    this.carga = forkJoin(lecturas).subscribe({
      next: (listas) => {
        if (this.sinNumero.size === disciplinas.length) {
          // Ninguna disciplina se pudo leer: no hay nada que enseñar.
          this.estado = 'error';
          return;
        }
        this.listas = Object.fromEntries(
          Object.entries(listas).map(([codigo, lista]) => [codigo, (lista ?? []) as ConvocatoriaPanel[]])
        );
        this.cifras.clear();
        this.estado = 'listo';
        this.ajustarPagina();
        this.guardarEstado();
        this.leerCifrasVisibles();
      },
      error: () => {
        this.estado = 'error';
      },
    });
  }

  /** «Reintentar» en la pestaña de una disciplina que no se pudo leer: relee solo esa lista. */
  reintentarDisciplina(codigo: string): void {
    this.lecturas
      .convocatorias(codigo)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (lista) => {
          this.sinNumero.delete(codigo);
          this.listas = { ...this.listas, [codigo]: (lista ?? []) as ConvocatoriaPanel[] };
          this.ajustarPagina();
          this.guardarEstado();
          this.leerCifrasVisibles();
          this.cdr.markForCheck();
        },
        error: () => {
          this.avisos.error('No se pudieron obtener las convocatorias.');
        },
      });
  }

  /** Las cifras de los bloques a la vista que aún no se han leído (los alumnos de cada convocatoria). */
  private leerCifrasVisibles(): void {
    for (const c of this.visibles) {
      if (!this.cifras.has(c.id)) {
        this.leerCifras(c.id);
      }
    }
  }

  leerCifras(id: number): void {
    this.lecturasCifras.get(id)?.unsubscribe();
    this.cifras.set(id, { estado: 'cargando' });
    const generacion = this.nuevaGeneracion(id);
    this.lecturasCifras.set(id, this.lecturas
      .alumnosDeConvocatoria(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (alumnos) => {
          if (this.generacionCifras.get(id) !== generacion) {
            return;
          }
          this.cifras.set(id, { estado: 'listo', cifras: cifrasDe((alumnos ?? []) as AlumnoConvocatoria[]) });
          this.cdr.markForCheck();
        },
        error: () => {
          if (this.generacionCifras.get(id) !== generacion) {
            return;
          }
          this.cifras.set(id, { estado: 'error' });
          this.cdr.markForCheck();
        },
      }));
  }

  private nuevaGeneracion(id: number): number {
    const generacion = ++this.contadorGeneracion;
    this.generacionCifras.set(id, generacion);
    return generacion;
  }

  cifrasDe(id: number): CifrasBloque {
    return this.cifras.get(id) ?? { estado: 'cargando' };
  }

  // ------------------------------------------------------------------ pestañas (sustituyen al filtro por deporte)

  elegirDisciplina(codigo: string): void {
    if (codigo === this.deporte) {
      return;
    }
    this.cerrarBloque();
    this.deporte = codigo;
    this.pagina = 1;
    this.guardarEstado();
    this.leerCifrasVisibles();
  }

  teclaPestanas(evento: KeyboardEvent): void {
    const lista = this.pestanas;
    const n = lista.length;
    if (n === 0) {
      return;
    }
    const actual = Math.max(0, lista.findIndex((p) => p.codigo === this.deporte));
    let siguiente: number;
    switch (evento.key) {
      case 'ArrowRight':
        siguiente = (actual + 1) % n;
        break;
      case 'ArrowLeft':
        siguiente = (actual - 1 + n) % n;
        break;
      case 'Home':
        siguiente = 0;
        break;
      case 'End':
        siguiente = n - 1;
        break;
      default:
        return;
    }
    evento.preventDefault();
    this.elegirDisciplina(lista[siguiente].codigo);
    this.cdr.detectChanges();
    this.host.nativeElement.querySelectorAll<HTMLElement>('[role="tab"]')[siguiente]?.focus();
  }

  irAPagina(pagina: number): void {
    if (this.estado !== 'listo' || pagina === this.pagina || pagina < 1 || pagina > this.totalPaginas) {
      return;
    }
    this.cerrarBloque();
    this.pagina = pagina;
    this.guardarEstado();
    this.leerCifrasVisibles();
  }

  // ------------------------------------------------------------------ bloque

  fechaLarga(c: ConvocatoriaPanel): string {
    return fechaLarga(c.fechaConvocatoria);
  }

  etiquetaDisciplina(codigo: string): string {
    return this.catalogo.etiquetaDisciplina(codigo);
  }

  colorDisciplina(codigo: string): string {
    return this.pestanas.find((p) => p.codigo === codigo)?.color ?? 'otra';
  }

  estaAbierta(c: ConvocatoriaPanel): boolean {
    return this.abierta?.id === c.id;
  }

  /** «Ver alumnos»: abre la lista del bloque (y cierra la que hubiera) u «Ocultar alumnos» la cierra, como hoy. */
  alternar(c: ConvocatoriaPanel): void {
    if (this.estaAbierta(c)) {
      this.cerrarBloque();
      return;
    }
    this.cerrarBloque();
    this.abierta = c;
    this.aptoElegido = null;
    this.leerBloque(false);
  }

  private cerrarBloque(): void {
    this.lecturaBloque?.unsubscribe();
    this.abierta = null;
    this.alumnos = [];
    this.aptos = [];
    this.aptoElegido = null;
    this.estadoAlumnos = 'listo';
    this.cerrarMenuPdf(false);
  }

  /**
   * Los alumnos del bloque abierto y, después, los aptos de su disciplina (`seleccionarConvocatoria` y `filtrarAlumnos`
   * de hoy). `silencioso` (tras una escritura) relee sin esqueleto y con la lista abierta (hoy se cerraba el bloque).
   */
  leerBloque(silencioso = true): void {
    const c = this.abierta;
    if (!c) {
      return;
    }
    this.lecturaBloque?.unsubscribe();
    if (!silencioso) {
      this.estadoAlumnos = 'cargando';
    }
    this.lecturaBloque = this.lecturas
      .alumnosDeConvocatoria(c.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (datos) => {
          this.alumnos = (datos ?? []) as AlumnoConvocatoria[];
          // Estas cifras son las más nuevas: una lectura de cifras anterior que llegue ahora no las pisa.
          this.nuevaGeneracion(c.id);
          this.cifras.set(c.id, { estado: 'listo', cifras: cifrasDe(this.alumnos) });
          this.estadoAlumnos = 'listo';
          this.cdr.markForCheck();
          this.leerAptos();
        },
        error: () => {
          this.estadoAlumnos = 'error';
          this.cdr.markForCheck();
        },
      });
  }

  /** Los aptos de la disciplina sin los ya inscritos, como hoy. */
  private leerAptos(): void {
    const c = this.abierta;
    if (!c) {
      return;
    }
    this.lecturas
      .aptosParaConvocatoria(c.deporte)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (aptos) => {
          if (this.abierta?.id !== c.id) {
            return;
          }
          this.aptos = ((aptos ?? []) as AlumnoApto[]).filter((a) => !this.alumnos.some((i) => i.alumnoId === a.id));
          if (this.aptoElegido !== null && !this.aptos.some((a) => a.id === this.aptoElegido)) {
            this.aptoElegido = null;
          }
          this.cdr.markForCheck();
        },
        error: () => {
          this.avisos.error('No se pudieron cargar los alumnos elegibles.');
        },
      });
  }

  // ------------------------------------------------------------------ fila del alumno

  nombreCompleto(a: { nombre?: string | null; apellidos?: string | null }): string {
    return `${a.nombre ?? ''} ${a.apellidos ?? ''}`.trim();
  }

  grado(codigo: string): GradoFila {
    return gradoFila(codigo ?? '', this.abierta?.deporte ?? this.deporte);
  }

  gradoApto(a: AlumnoApto): string {
    return gradoFila(a.grado ?? '', this.abierta?.deporte ?? this.deporte).nombreDetalle;
  }

  cuantiaParaCampo(a: AlumnoConvocatoria): string {
    return cuantiaParaCampo(a.cuantiaExamen);
  }

  ocupado(a: AlumnoConvocatoria): boolean {
    return this.enProceso.has(a.id);
  }

  /** Conmutador Pagado | Pendiente: la MISMA llamada que la casilla de hoy (`{cuantiaExamen, pagado}`). */
  marcarPago(a: AlumnoConvocatoria, pagado: boolean): void {
    if (this.ocupado(a) || a.pagado === pagado) {
      return;
    }
    this.guardarAlumno(a, { cuantiaExamen: a.cuantiaExamen, pagado });
  }

  /** Cuantía: al cambiar el campo, el mismo PUT de hoy; lo que no es un número no se envía y el campo vuelve atrás. */
  cambiarCuantia(a: AlumnoConvocatoria, evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const texto = campo.value;
    const valor = cuantiaDeCampo(texto);
    if (this.ocupado(a) || (valor === null && texto.trim() !== '')) {
      campo.value = cuantiaParaCampo(a.cuantiaExamen);
      return;
    }
    if (valor === a.cuantiaExamen) {
      campo.value = cuantiaParaCampo(a.cuantiaExamen);
      return;
    }
    this.guardarAlumno(a, { cuantiaExamen: valor, pagado: a.pagado });
  }

  /** `actualizarAlumnoConvocatoria` de hoy: aviso y relectura; un error dice el del servidor y vuelve a lo guardado. */
  private guardarAlumno(a: AlumnoConvocatoria, cuerpo: { cuantiaExamen: number | null; pagado: boolean }): void {
    this.enProceso.add(a.id);
    this.endpoints
      .actualizarAlumnoConvocatoria(a.id, cuerpo)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.enProceso.delete(a.id);
          // Lo guardado pasa a ser lo que hay hasta que llegue la relectura: el siguiente envío parte de ahí.
          a.cuantiaExamen = cuerpo.cuantiaExamen;
          a.pagado = cuerpo.pagado;
          this.avisos.exito('Datos del alumno actualizados correctamente.');
          this.leerBloque();
        },
        error: (e) => {
          this.enProceso.delete(a.id);
          this.avisos.error(mensajeDeError(e) ?? 'No se pudo actualizar el alumno.');
          this.leerBloque();
        },
      });
  }

  /** La cruz: confirmación destructiva y el `DELETE /alumnos/{convocatoria}/alumno/{alumno}` de hoy. */
  async quitar(a: AlumnoConvocatoria): Promise<void> {
    const c = this.abierta;
    if (!c || this.ocupado(a)) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Quitar alumno',
      mensaje: `Vas a eliminar al alumno ${a.nombre} de la convocatoria.`,
      accion: 'Sí, eliminar',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'quitar-alumno',
    });
    if (!r.confirmado || this.ocupado(a)) {
      return;
    }
    this.enProceso.add(a.id);
    this.endpoints
      .eliminarAlumnoDeConvocatoria(a.alumnoId, c.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.enProceso.delete(a.id);
          this.avisos.exito('El alumno ha sido eliminado de la convocatoria.');
          this.leerBloque();
        },
        error: (e) => {
          this.enProceso.delete(a.id);
          this.avisos.error(mensajeDeError(e) ?? 'No se pudo eliminar al alumno.');
        },
      });
  }

  // ------------------------------------------------------------------ agregar alumno

  elegirApto(valor: string): void {
    const id = Number(valor);
    this.aptoElegido = valor === '' || Number.isNaN(id) ? null : id;
  }

  /**
   * «Agregar alumno»: «Selecciona el tipo de producto» con elección explícita (nada elegido al abrir, la acción
   * deshabilitada hasta elegir) y un «Cancelar» que no decide nada; si el siguiente grado es el rojo, la pregunta de
   * hoy (sin bordar por defecto). Después, el `POST` de hoy con `{porRecompensa, rojoBordado}`.
   */
  async agregar(): Promise<void> {
    const c = this.abierta;
    const id = this.aptoElegido;
    if (!c || id === null || this.agregando || this.preguntando) {
      return;
    }
    const apto = this.aptos.find((a) => a.id === id);
    this.preguntando = true;
    const eleccion = await this.preguntarTipo(c, apto).finally(() => (this.preguntando = false));
    if (!eleccion || this.agregando) {
      return;
    }
    this.agregando = true;
    this.enviarAgregar(c, id, eleccion.porRecompensa, eleccion.rojoBordado);
  }

  /** Las dos preguntas de hoy; `null` si se cancela cualquiera de ellas. */
  private async preguntarTipo(
    c: ConvocatoriaPanel,
    apto: AlumnoApto | undefined
  ): Promise<{ porRecompensa: boolean; rojoBordado: boolean } | null> {
    const tipo = await this.confirmacion.confirmar({
      titulo: 'Selecciona el tipo de producto',
      mensaje: '¿Asignar el precio por antigüedad o por recompensa?',
      campo: {
        etiqueta: 'Tipo de producto',
        opciones: [
          { valor: 'recompensa', texto: 'Por recompensa' },
          { valor: 'antiguedad', texto: 'Por antigüedad' },
        ],
        valor: null,
        requerido: true,
      },
      accion: 'Agregar',
      cancelar: 'Cancelar',
      nombre: 'tipo-producto',
    });
    if (!tipo.confirmado || (tipo.valor !== 'recompensa' && tipo.valor !== 'antiguedad')) {
      return null;
    }
    const porRecompensa = tipo.valor === 'recompensa';
    if (!apto || !esSiguienteGradoRojo(c.deporte, apto.grado, apto.fechaNacimiento ?? null)) {
      return { porRecompensa, rojoBordado: false };
    }
    const rojo = await this.confirmacion.confirmar({
      titulo: 'Selecciona el grado rojo',
      mensaje: `El alumno pasará a rojo en ${this.etiquetaDisciplina(c.deporte)}. Selecciona el tipo de grado rojo.`,
      campo: {
        etiqueta: 'Tipo de grado rojo',
        opciones: [
          { valor: 'normal', texto: 'Rojo (sin bordar)' },
          { valor: 'bordado', texto: 'Rojo bordado' },
        ],
        valor: 'normal',
        requerido: true,
      },
      accion: 'Continuar',
      cancelar: 'Cancelar',
      nombre: 'grado-rojo',
    });
    return rojo.confirmado ? { porRecompensa, rojoBordado: rojo.valor === 'bordado' } : null;
  }

  private enviarAgregar(c: ConvocatoriaPanel, alumnoId: number, porRecompensa: boolean, rojoBordado: boolean): void {
    this.endpoints
      .agregarAlumnoAConvocatoria(alumnoId, c.id, porRecompensa, rojoBordado)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.agregando = false;
          this.aptoElegido = null;
          this.avisos.exito('El alumno ha sido asignado correctamente.');
          this.leerBloque();
        },
        error: () => {
          this.agregando = false;
          this.avisos.error('No se pudo agregar al alumno.');
        },
      });
  }

  /** «Cargar más alumnos», como hoy: la página siguiente del listado (10) y, después, los aptos otra vez. */
  cargarMasAlumnos(): void {
    if (this.alumnosCargadosCompletamente) {
      return;
    }
    this.lecturas
      .alumnos(this.paginaAlumnos, TAMANO_PAGINA_ALUMNOS)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (respuesta) => {
          this.paginaAlumnos++;
          if (respuesta?.totalPages === this.paginaAlumnos) {
            this.alumnosCargadosCompletamente = true;
          }
          this.leerAptos();
          this.cdr.markForCheck();
        },
        error: () => {
          this.avisos.error('No se pudo cargar los alumnos paginados.');
        },
      });
  }

  // ------------------------------------------------------------------ menú «PDF»

  private get menuPdfRef(): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>('#convocatorias-menu-pdf');
  }

  private get botonPdf(): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>('#convocatorias-pdf');
  }

  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    const zona = this.botonPdf?.closest('.convocatorias__menu-zona');
    if (destino && zona?.contains(destino)) {
      return;
    }
    this.menuPdf = false;
    this.quitarEscucha();
    this.cdr.markForCheck();
  };

  private quitarEscucha(): void {
    if (this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      this.escuchaFuera = false;
    }
  }

  alternarMenuPdf(evento?: KeyboardEvent): void {
    this.menuPdf = !this.menuPdf;
    if (this.menuPdf) {
      if (!this.escuchaFuera) {
        document.addEventListener('pointerdown', this.alPulsarFuera, true);
        this.escuchaFuera = true;
      }
      this.cdr.detectChanges();
      enfocarOpcion(this.menuPdfRef, evento?.key === 'ArrowUp' ? 'ultima' : 'primera');
    } else {
      this.quitarEscucha();
    }
  }

  teclaBotonPdf(evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !this.menuPdf) {
      evento.preventDefault();
      this.alternarMenuPdf(evento);
    }
  }

  teclaMenuPdf(evento: KeyboardEvent): void {
    const menu = this.menuPdfRef;
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenuPdf(true);
    } else if (accion === 'salir') {
      this.cerrarMenuPdf(false);
    }
  }

  cerrarMenuPdf(devolverFoco: boolean): void {
    this.menuPdf = false;
    this.quitarEscucha();
    if (devolverFoco) {
      this.cdr.detectChanges();
      this.botonPdf?.focus();
    }
  }

  alSalirDelMenu(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      this.cerrarMenuPdf(false);
    }
  }

  /** «Ver PDF» y «Descargar PDF»: `generarReporte` de hoy (petición, nombre con la fecha y título). */
  pdf(accion: AccionInforme): void {
    const c = this.abierta;
    this.cerrarMenuPdf(true);
    if (!c || this.informes.ocupado || this.informes.destruido) {
      return;
    }
    const { nombreArchivo, titulo } = nombreInforme(c);
    this.informes.generar(
      this.endpoints.descargarInformePDFConvocatoria(c.id).pipe(map((blob) => ({ documentos: [{ blob, nombreArchivo, titulo }] }))),
      accion,
      'No se pudo generar el informe.'
    );
  }

  // ------------------------------------------------------------------ actualizar grados

  /**
   * «Actualizar grados de todos»: confirmación siempre (con el aviso de hoy si alguien no ha pagado) y el
   * `PUT /convocatorias/{id}/actualizar-grados` de hoy; el diálogo sigue ocupado mientras va, sin doble envío.
   */
  async actualizarGrados(): Promise<void> {
    const c = this.abierta;
    if (!c || this.alumnos.length === 0) {
      return;
    }
    const hayNoPagados = this.alumnos.some((a) => !a.pagado);
    const n = this.alumnos.length;
    const r = await this.confirmacion.confirmar<void>({
      titulo: 'Actualizar grados',
      mensaje: hayNoPagados
        ? 'Hay alumnos que no han pagado la convocatoria. ¿Deseas continuar?'
        : n === 1
          ? 'Se actualizará el grado de 1 alumno inscrito.'
          : `Se actualizará el grado de ${numero(n)} alumnos inscritos.`,
      accion: 'Sí, actualizar',
      accionOcupada: 'Actualizando grados…',
      cancelar: 'Cancelar',
      nombre: 'actualizar-grados',
      ejecutar: () => this.endpoints.actualizarGradosDeConvocatoria(c.id),
    });
    if (r.error) {
      this.avisos.error('No se pudieron actualizar los grados.');
      return;
    }
    if (!r.confirmado) {
      return;
    }
    this.avisos.exito('Grados actualizados correctamente.');
    if (this.abierta?.id === c.id) {
      this.leerBloque();
    }
  }

  // ------------------------------------------------------------------ crear y eliminar convocatoria

  /** «Crear convocatoria»: disciplina y fecha, la confirmación de hoy y el `POST /convocatorias` de hoy. */
  async crearConvocatoria(): Promise<void> {
    const datos = await this.confirmacion.confirmar({
      titulo: 'Crear convocatoria',
      accion: 'Crear',
      cancelar: 'Cancelar',
      nombre: 'crear-convocatoria',
      campos: [
        {
          id: 'convocatoria-deporte',
          tipo: 'select',
          etiqueta: 'Disciplina',
          opciones: this.disciplinas.map((d) => ({ valor: d.codigo, texto: d.nombre })),
          valor: this.deporte,
        },
        { id: 'convocatoria-fecha', tipo: 'date', etiqueta: 'Fecha de la convocatoria', valor: diaParaCampo(new Date()) },
      ],
      comprobar: (v) =>
        v['convocatoria-fecha'] ? null : { campo: 'convocatoria-fecha', mensaje: 'Por favor, selecciona una fecha' },
    });
    const deporte = datos.valores?.['convocatoria-deporte'];
    const dia = datos.valores?.['convocatoria-fecha'];
    if (!datos.confirmado || !deporte || !dia) {
      return;
    }
    const fechaConvocatoria = fechaDeCampo(dia);
    const ok = await this.confirmacion.confirmar({
      titulo: 'Crear convocatoria',
      mensaje: [
        'Vas a crear una convocatoria de ',
        { fuerte: this.etiquetaDisciplina(deporte) },
        ' para ',
        { fuerte: fecha(fechaConvocatoria) },
        '.',
      ],
      accion: 'Sí, crear',
      cancelar: 'Cancelar',
      nombre: 'confirmar-crear',
    });
    if (!ok.confirmado) {
      return;
    }
    this.endpoints
      .crearConvocatoria({ fechaConvocatoria, deporte })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (nueva: ConvocatoriaPanel) => {
          this.avisos.exito('La convocatoria ha sido creada correctamente.');
          if (!nueva?.id) {
            return;
          }
          const codigo = nueva.deporte || deporte;
          if (this.sinNumero.has(codigo)) {
            // Su lista no se pudo leer: se relee entera en vez de añadir la nueva a una lista incompleta.
            this.reintentarDisciplina(codigo);
            return;
          }
          // Como hoy, se añade al final de su lista sin volver a pedirla; empieza sin alumnos.
          this.listas = { ...this.listas, [codigo]: [...(this.listas[codigo] ?? []), nueva] };
          this.cifras.set(nueva.id, { estado: 'listo', cifras: { inscritos: 0, pagados: 0, pendientes: 0 } });
          this.leerCifrasVisibles();
          this.cdr.markForCheck();
        },
        error: () => {
          this.avisos.error('No se pudo crear la convocatoria.');
        },
      });
  }

  /** La papelera: confirmación destructiva y el `DELETE /convocatorias/{id}` de hoy. */
  async eliminarConvocatoria(c: ConvocatoriaPanel): Promise<void> {
    const r = await this.confirmacion.confirmar({
      titulo: 'Eliminar convocatoria',
      mensaje: `Vas a eliminar la convocatoria del ${fecha(c.fechaConvocatoria)}. Esta acción no se puede deshacer.`,
      accion: 'Sí, eliminar',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-convocatoria',
    });
    if (!r.confirmado) {
      return;
    }
    this.endpoints
      .eliminarConvocatoria(c.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.avisos.exito('La convocatoria ha sido eliminada.');
          if (this.abierta?.id === c.id) {
            this.cerrarBloque();
          }
          this.listas = { ...this.listas, [c.deporte]: (this.listas[c.deporte] ?? []).filter((x) => x.id !== c.id) };
          this.cifras.delete(c.id);
          this.ajustarPagina();
          this.guardarEstado();
          this.leerCifrasVisibles();
          this.cdr.markForCheck();
        },
        error: () => {
          this.avisos.error('No se pudo eliminar la convocatoria.');
        },
      });
  }

  // ------------------------------------------------------------------ como hoy

  private ajustarPagina(): void {
    const total = this.totalPaginas;
    if (total === 0) {
      this.pagina = 1;
    } else if (this.pagina > total) {
      this.pagina = total;
    }
  }

  private guardarEstado(): void {
    try {
      sessionStorage.setItem(
        CLAVE_ESTADO,
        JSON.stringify({ paginaActualConvocatorias: this.pagina, tamanoPaginaConvocatorias: POR_PAGINA, deporteSeleccionado: this.deporte })
      );
    } catch {
      // Sin almacenamiento de sesión, no se recuerda.
    }
  }

  private restaurarEstado(): void {
    try {
      const guardado = sessionStorage.getItem(CLAVE_ESTADO);
      if (!guardado) {
        return;
      }
      const estado = JSON.parse(guardado);
      this.pagina = Number(estado?.paginaActualConvocatorias) || 1;
      if (typeof estado?.deporteSeleccionado === 'string' && estado.deporteSeleccionado) {
        this.deporte = estado.deporteSeleccionado;
      }
    } catch {
      // Un estado ilegible se ignora, como hoy.
    }
  }
}
