import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, Subscription, catchError, debounceTime, of, switchMap } from 'rxjs';

import { TipoFestivo } from '../../interfaces/asistencia';
import { PermisosSesionService, SedeVisible } from '../../servicios/authentication/permisos-sesion.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { ParteMensaje } from '../dialogo/dialogo.component';
import { mensajeDelServidor } from '../mis-grupos/profesor-textos';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import {
  CampoFestivo,
  Festivo,
  FormularioFestivo,
  ImpactoFestivo,
  NOMBRE_MAXIMO,
  TIPOS_FESTIVO,
  cuerpoFestivo,
  diasDeRango,
  diasDelMes,
  fechaDeFila,
  fechaEnFrase,
  formularioDe,
  nombreDeFila,
  sedesEnFrase,
  textoImpacto,
  textoTipo,
  validarFestivo,
} from './festivos-vista';
import { FestivosService } from './festivos.service';

type Estado = 'cargando' | 'listo' | 'error';

/** El panel lateral abierto: `festivo` null es «Nuevo festivo». */
interface PanelFestivo {
  festivo: Festivo | null;
  form: FormularioFestivo;
  errores: Partial<Record<CampoFestivo, string>>;
  enviando: boolean;
  impacto: ImpactoFestivo | null;
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** Orden de los campos para llevar el foco al primer error. */
const ORDEN_CAMPOS: readonly CampoFestivo[] = ['fecha', 'desde', 'hasta', 'dia', 'mes', 'nombre', 'sedes'];

/**
 * Festivos (decisiones 456-458; boceto `boceto-pasar-lista-motivos-festivos.html`, estados `festivos*`), con el modelo de
 * Sedes: página de 880 px, «Nuevo festivo» como único primario, una fila por festivo (toda la fila edita, «Quitar» es su
 * hermano) y el panel lateral de 480 (hoja en el móvil) para alta y edición, con el aviso de las clases a las que
 * afecta. Gestión y responsable de sede; este solo edita los que son solo de sus sedes («Lo gestiona la escuela»).
 */
@Component({
  selector: 'app-festivos-panel',
  standalone: true,
  imports: [NgTemplateOutlet, AcentoEscuelaDirective, BloqueErrorComponent, BotonOcupadoComponent, EsqueletoComponent, PanelLateralComponent],
  templateUrl: './festivos.component.html',
  styleUrls: ['./festivos.component.scss'],
})
export class FestivosComponent implements OnInit {
  private readonly api = inject(FestivosService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destruir = inject(DestroyRef);
  private lectura: Subscription | null = null;
  private readonly impacto$ = new Subject<void>();

  readonly tipos = TIPOS_FESTIVO;
  readonly maximo = NOMBRE_MAXIMO;
  readonly meses = MESES.map((m, i) => ({ valor: String(i + 1), texto: m }));
  readonly fechaDeFila = fechaDeFila;
  readonly nombreDeFila = nombreDeFila;
  readonly textoTipo = textoTipo;

  estado: Estado = 'cargando';
  festivos: Festivo[] = [];
  pasados = false;
  sedeFiltro: number | null = null;
  /** Sedes activas de la sesión: todas para la gestión, las suyas para el responsable. */
  sedes: SedeVisible[] = [];
  responsable = false;
  panel: PanelFestivo | null = null;
  quitando: number | null = null;

  ngOnInit(): void {
    this.permisos.obtener().pipe(takeUntilDestroyed(this.destruir), catchError(() => of(null))).subscribe((p) => {
      this.sedes = (p?.sedes ?? []).filter((s) => s.activa !== false);
      this.responsable = !!p?.capacidades?.includes('PANEL_SEDE');
      this.cdr.markForCheck();
    });
    // El aviso de impacto, 400 ms después del último cambio; solo con el formulario completo.
    this.impacto$.pipe(
      debounceTime(400),
      switchMap(() => {
        const panel = this.panel;
        if (!panel || Object.keys(validarFestivo(panel.form)).length) return of(null);
        return this.api.impacto(cuerpoFestivo(panel.form), panel.festivo?.id ?? null).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(this.destruir),
    ).subscribe((impacto) => {
      if (this.panel) this.panel.impacto = impacto;
      this.cdr.detectChanges();
    });
    this.cargar();
  }

  // ------------------------------------------------------------------ lista

  cargar(silencioso = false): void {
    this.lectura?.unsubscribe();
    if (!silencioso) this.estado = 'cargando';
    this.lectura = this.api.listar(this.pasados, this.sedeFiltro).pipe(takeUntilDestroyed(this.destruir)).subscribe({
      next: (festivos) => {
        this.festivos = festivos;
        this.estado = 'listo';
        this.cdr.detectChanges();
      },
      error: () => {
        if (!silencioso) this.estado = 'error';
        this.cdr.detectChanges();
      },
    });
  }

  get verFiltroSede(): boolean { return this.sedes.length > 1; }
  get textoTodas(): string { return this.responsable ? 'Mis sedes' : 'Todas las sedes'; }

  get textoTotal(): string {
    const n = this.festivos.length;
    return n === 0 ? 'Ningún festivo' : n === 1 ? '1 festivo' : `${n} festivos`;
  }

  get etiquetaLista(): string { return this.pasados ? 'Festivos pasados' : 'Próximos festivos'; }

  elegirSede(valor: string): void {
    this.sedeFiltro = valor === '' ? null : Number(valor);
    this.cargar();
  }

  alternarPasados(): void {
    this.pasados = !this.pasados;
    this.cargar();
  }

  /** «Todas las sedes» o las suyas, como chips. */
  sedesDe(f: Festivo): string[] { return f.todasLasSedes ? [] : f.sedes.map((s) => s.nombre); }

  // ------------------------------------------------------------------ panel «Nuevo festivo» y «Editar festivo»

  abrirNuevo(): void {
    if (this.estado !== 'listo') return;
    const inicial = this.sedes.length === 1 ? [this.sedes[0].id] : this.sedeFiltro !== null ? [this.sedeFiltro] : [];
    this.panel = { festivo: null, form: formularioDe(null, inicial), errores: {}, enviando: false, impacto: null };
  }

  abrirEditar(f: Festivo): void {
    if (!f.editable) return;
    this.panel = { festivo: f, form: formularioDe(f, []), errores: {}, enviando: false, impacto: null };
    this.impacto$.next();
  }

  cerrarPanel(): void {
    if (this.panel?.enviando) return;
    this.panel = null;
  }

  get tituloPanel(): string { return this.panel?.festivo ? 'Editar festivo' : 'Nuevo festivo'; }
  get textoGuardar(): string { return this.panel?.festivo ? 'Guardar cambios' : 'Crear festivo'; }
  get textoGuardando(): string { return this.panel?.festivo ? 'Guardando…' : 'Creando…'; }

  /** Casillas: todas las sedes de la gestión o solo las del responsable (sin «Todas»). */
  get verTodas(): boolean { return !this.responsable && this.sedes.length > 1; }

  get todasMarcadas(): boolean {
    return !!this.panel && this.sedes.length > 0 && this.sedes.every((s) => this.panel!.form.sedeIds.includes(s.id));
  }

  get diasDelRango(): number {
    return this.panel ? diasDeRango(this.panel.form.desde, this.panel.form.hasta) : 0;
  }

  get textoDiasRango(): string {
    const n = this.diasDelRango;
    return n ? `Los dos días cuentan: ${n} ${n === 1 ? 'día' : 'días'}.` : 'Los dos días cuentan.';
  }

  get diasAnual(): number[] {
    const mes = Number(this.panel?.form.mes) || 1;
    return Array.from({ length: this.panel?.form.mes ? diasDelMes(mes) : 31 }, (_, i) => i + 1);
  }

  get notaImpacto(): { fuerte: string; resto: string } | null {
    return this.panel?.impacto ? textoImpacto(this.panel.impacto) : null;
  }

  elegirTipo(tipo: TipoFestivo): void {
    if (!this.panel || this.panel.enviando) return;
    this.panel.form = { ...this.panel.form, tipo };
    this.panel.errores = {};
    this.panel.impacto = null;
    this.impacto$.next();
  }

  /** Flechas (con vuelta), Inicio y Fin mueven la elección y el foco, como un grupo de radios nativo. */
  teclaTipo(evento: KeyboardEvent): void {
    if (!this.panel) return;
    const actual = this.tipos.findIndex((t) => t.valor === this.panel!.form.tipo);
    const n = this.tipos.length;
    const destino: Record<string, number> = { ArrowRight: (actual + 1) % n, ArrowDown: (actual + 1) % n, ArrowLeft: (actual - 1 + n) % n, ArrowUp: (actual - 1 + n) % n, Home: 0, End: n - 1 };
    if (!(evento.key in destino)) return;
    evento.preventDefault();
    const i = destino[evento.key];
    this.elegirTipo(this.tipos[i].valor);
    this.cdr.detectChanges();
    (evento.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('[role="radio"]')[i]?.focus();
  }

  escribir(campo: Exclude<CampoFestivo, 'sedes'>, evento: Event): void {
    if (!this.panel) return;
    const valor = (evento.target as HTMLInputElement | HTMLSelectElement).value;
    this.panel.form = { ...this.panel.form, [campo]: campo === 'nombre' ? valor.slice(0, NOMBRE_MAXIMO) : valor };
    delete this.panel.errores[campo];
    if (campo !== 'nombre') this.impacto$.next();
  }

  alternarSede(id: number): void {
    if (!this.panel) return;
    const ids = this.panel.form.sedeIds;
    this.panel.form = { ...this.panel.form, sedeIds: ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id] };
    delete this.panel.errores.sedes;
    this.impacto$.next();
  }

  alternarTodas(): void {
    if (!this.panel) return;
    this.panel.form = { ...this.panel.form, sedeIds: this.todasMarcadas ? [] : this.sedes.map((s) => s.id) };
    delete this.panel.errores.sedes;
    this.impacto$.next();
  }

  errorDe(campo: string): string | null {
    return this.panel?.errores[campo as CampoFestivo] ?? null;
  }

  marcada(id: number): boolean { return !!this.panel?.form.sedeIds.includes(id); }

  descripcion(campo: CampoFestivo, ayuda: string | null): string | null {
    const ids = [ayuda, this.panel?.errores[campo] ? `fv-error-${campo}` : null].filter((x): x is string => !!x);
    return ids.length ? ids.join(' ') : null;
  }

  guardar(evento?: Event): void {
    evento?.preventDefault();
    const panel = this.panel;
    if (!panel || panel.enviando) return;
    panel.errores = validarFestivo(panel.form);
    const primero = ORDEN_CAMPOS.find((c) => panel.errores[c]);
    if (primero) {
      this.cdr.detectChanges();
      const destino = primero === 'sedes' ? '#fv-sedes input' : `#fv-${primero}`;
      this.host.nativeElement.querySelector<HTMLElement>(destino)?.focus();
      return;
    }
    panel.enviando = true;
    const edicion = panel.festivo;
    const cuerpo = cuerpoFestivo(panel.form);
    const peticion: Observable<Festivo> = edicion ? this.api.editar(edicion.id, cuerpo) : this.api.crear(cuerpo);
    // Sin cancelar al salir: una escritura en vuelo sigue su curso.
    peticion.subscribe({
      next: () => {
        this.panel = null;
        this.avisos.exito(edicion ? 'Cambios guardados.' : 'Festivo creado.');
        this.cargar(true);
      },
      error: (e) => {
        panel.enviando = false;
        this.avisos.error(mensajeDelServidor(e, 'No se ha podido guardar el festivo. Inténtalo de nuevo.'));
        this.cdr.detectChanges();
      },
    });
  }

  // ------------------------------------------------------------------ quitar

  async quitar(f: Festivo): Promise<void> {
    if (!f.editable || this.quitando !== null) return;
    this.quitando = f.id;
    const impacto = await new Promise<ImpactoFestivo | null>((resolver) => {
      this.api.impacto(cuerpoFestivo(formularioDe(f, [])), f.id).pipe(catchError(() => of(null))).subscribe(resolver);
    });
    this.quitando = null;
    const resultado = await this.confirmacion.confirmar<void>({
      titulo: `¿Quitar «${nombreDeFila(f)}»?`,
      mensaje: this.mensajeQuitar(f, impacto),
      accion: 'Quitar festivo',
      accionOcupada: 'Quitando…',
      destructivo: true,
      nombre: 'quitar-festivo',
      ejecutar: () => this.api.quitar(f.id),
    });
    if (!resultado.confirmado) return;
    if (resultado.error) {
      this.avisos.error(mensajeDelServidor(resultado.error, 'No se ha podido quitar el festivo. Inténtalo de nuevo.'));
      this.cdr.detectChanges();
      return;
    }
    this.festivos = this.festivos.filter((x) => x.id !== f.id);
    this.avisos.exito('Festivo quitado.');
    this.cargar(true);
  }

  /** «Del 23 de diciembre al 7 de enero volverá a haber clase en todas las sedes: esas 46 clases pedirán lista otra vez.» */
  private mensajeQuitar(f: Festivo, impacto: ImpactoFestivo | null): ParteMensaje[] {
    const n = impacto ? Math.max(impacto.clases - impacto.conLista, 0) : null;
    const cola = n === null
      ? ': sus clases pedirán lista otra vez.'
      : n === 0
        ? ': ninguna clase cambia.'
        : n === 1
          ? ': esa clase pedirá lista otra vez.'
          : `: esas ${n} clases pedirán lista otra vez.`;
    return [`${fechaEnFrase(f)} volverá a haber clase en `, { fuerte: sedesEnFrase(f) }, `${cola} No se borra ninguna lista.`];
  }
}
