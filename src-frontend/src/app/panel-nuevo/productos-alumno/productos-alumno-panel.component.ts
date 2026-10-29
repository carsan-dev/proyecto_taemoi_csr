import { ChangeDetectorRef, Component, DestroyRef, ElementRef, HostListener, OnInit, afterEveryRender, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { AcentoEscuelaActual, AcentoEscuelaDirective } from '../acento-escuela.directive';
import { SearchableSelectDirective } from '../../directives/searchable-select.directive';
import { variablesDelTema } from '../tema-escuela';
import { fotoDe, iniciales } from '../alumnos/alumno-vista';
import { ReciboPagoService } from '../cobro/recibo-pago.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import type { FilaCargo } from './productos-alumno.estado';
import { eur, fecha } from '../formato/formato-es';
import { ProductosAlumnoEstado } from './productos-alumno.estado';
import { ConCambiosSinGuardar } from './salir-sin-guardar.guard';
import { TEXTOS_PRODUCTOS_ALUMNO, etiquetaProducto, leerPrecio, precioParaCampo } from './productos-alumno-vista';

/**
 * «Productos y cargos» de un alumno (8b-3n) en `/alumnos/:id/productos`. Toda la
 * lógica está en `ProductosAlumnoEstado` (`estado`), uno por pantalla. Esta capa solo gestiona la vista P1.
 */
@Component({
  selector: 'pn-productos-alumno',
  standalone: true,
  imports: [RouterLink, BloqueErrorComponent, EsqueletoComponent, BotonOcupadoComponent, AcentoEscuelaDirective, SearchableSelectDirective],
  providers: [ProductosAlumnoEstado],
  templateUrl: './productos-alumno-panel.component.html',
  styleUrl: './productos-alumno-panel.component.scss',
})
export class ProductosAlumnoPanelComponent implements OnInit, ConCambiosSinGuardar {
  readonly estado = inject(ProductosAlumnoEstado);
  private readonly ruta = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly recibo = inject(ReciboPagoService);
  private readonly detectorCambios = inject(ChangeDetectorRef);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly temaActual = inject(AcentoEscuelaActual);
  readonly variablesDesplegable = computed(() => variablesDelTema(this.temaActual.aplicado()));
  readonly hayCargosPagados = computed(() => this.estado.filasPagina().some((fila) => fila.cargo.pagado === true));
  readonly hayNotaCobrar = computed(() => this.estado.filas().some((fila) => fila.muestraCobrar && fila.notaCobrar !== null));
  readonly iniciales = iniciales;
  readonly desplegadas = new Set<number>();
  menuAbierto: number | null = null;
  private botonMenu: HTMLButtonElement | null = null;
  readonly textosEstado = { pagado: 'Pagado', pendiente: 'Pendiente', cancelado: 'Cancelado', devuelto: 'Devuelto' };
  readonly fecha = fecha;
  readonly precioParaCampo = precioParaCampo;

  constructor() {
    afterEveryRender(() => this.sincronizarBuscador());
  }

  private sincronizarBuscador(): void {
    const campo = this.elemento.nativeElement.querySelector<HTMLInputElement>('.producto [role="combobox"]');
    if (!campo) return;
    campo.setAttribute('aria-label', 'Producto');
    campo.setAttribute('aria-required', 'true');
    campo.setAttribute('aria-describedby', 'asignar-producto-error');
    campo.setAttribute('aria-invalid', String(this.estado.errorAsignacion() === 'producto'));
    campo.disabled = this.estado.guardando();
  }

  buscarProducto(evento: Event): void {
    if ((evento.target as HTMLElement).getAttribute('role') === 'combobox') {
      const texto = this.valor(evento);
      if (texto === '') this.estado.elegirProducto(null);
      this.estado.buscarProducto(texto);
    }
  }

  terminarBusquedaProducto(): void {
    this.estado.buscarProducto('');
  }

  marcarBorrar(id: number): void {
    this.cerrarMenu(false);
    if (!this.estado.marcarBorrar(id)) return;
    this.detectorCambios.detectChanges();
    this.elemento.nativeElement.querySelector<HTMLButtonElement>(`[data-cargo="${id}"] [data-deshacer]`)?.focus();
  }

  /**
   * «Deshacer» de la fila: el del borrado marcado o, si no, el de sus cambios. El botón desaparece al deshacer: el foco
   * pasa al «Detalle» de la misma fila para no perderse.
   */
  deshacer(fila: FilaCargo): void {
    if (fila.puedeDeshacerBorrar) this.estado.deshacerBorrar(fila.id);
    else this.estado.deshacerFila(fila.id);
    this.detectorCambios.detectChanges();
    this.elemento.nativeElement.querySelector<HTMLButtonElement>(`[data-cargo="${fila.id}"] [data-expandir]`)?.focus();
  }

  precioResumen(texto: string): string {
    const lectura = leerPrecio(texto);
    return lectura.valor === null ? '—' : eur(lectura.valor);
  }

  get foto(): string | null {
    const foto = this.estado.alumno()?.fotoAlumno;
    if (!foto || typeof foto !== 'object') return null;
    const datos = foto as { url?: unknown; id?: unknown; nombre?: unknown };
    return typeof datos.url === 'string' ? fotoDe({ fotoAlumno: {
      url: datos.url, nombre: typeof datos.nombre === 'string' ? datos.nombre : null,
      id: typeof datos.id === 'number' ? datos.id : null,
    } }) : null;
  }

  alternarDetalle(id: number): void {
    if (!this.desplegadas.delete(id)) this.desplegadas.add(id);
  }

  alternarMenu(id: number, disparador: HTMLButtonElement, ultima = false): void {
    if (this.menuAbierto === id) { this.cerrarMenu(); return; }
    this.menuAbierto = id;
    this.botonMenu = disparador;
    this.detectorCambios.detectChanges();
    enfocarOpcion(disparador.parentElement?.querySelector('[role="menu"]') ?? null, ultima ? 'ultima' : 'primera');
  }

  cerrarMenu(devolverFoco = true): void {
    const disparador = this.menuAbierto !== null ? this.botonMenu : null;
    this.menuAbierto = null;
    this.botonMenu = null;
    if (devolverFoco) disparador?.focus();
  }

  teclaMenu(evento: KeyboardEvent, menu: HTMLElement): void {
    const accion = teclaEnMenu(evento, menu);
    if (accion) this.cerrarMenu(accion === 'cerrar');
  }

  @HostListener('document:click', ['$event'])
  clicFueraMenu(evento: MouseEvent): void {
    if (this.menuAbierto !== null && !this.botonMenu?.parentElement?.contains(evento.target as Node)) this.cerrarMenu(false);
  }

  async editarNotas(fila: FilaCargo): Promise<void> {
    this.cerrarMenu();
    if (!fila.puedeEditarNotas) return;
    const resultado = await this.confirmacion.confirmar({
      titulo: 'Editar notas', mensaje: fila.concepto, accion: 'Aplicar', cancelar: 'Cancelar',
      campos: [{ id: 'cargo-notas', tipo: 'textarea', etiqueta: 'Notas', valor: fila.notas }],
    });
    if (resultado.confirmado) this.estado.cambiarNotas(fila.id, resultado.valores?.['cargo-notas'] ?? fila.notas);
  }

  /** «Descargar recibo» (gestión, cargo pagado): `GET /productos-alumno/{id}/recibo`. */
  descargarRecibo(fila: FilaCargo): void {
    this.cerrarMenu();
    if (fila.puedeDescargarRecibo) void this.recibo.descargar(fila.id);
  }

  readonly textos = TEXTOS_PRODUCTOS_ALUMNO;
  readonly eur = eur;
  readonly etiquetaProducto = etiquetaProducto;

  ngOnInit(): void {
    this.ruta.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((parametros) => {
      const id = Number(parametros.get('id'));
      if (this.estado.estado() !== 'cargando' && id === this.estado.alumnoId()) {
        return;
      }
      this.estado.iniciar(id);
    });
  }

  /** `canDeactivate` (`salirSinGuardarGuard`): con cambios, «¿Salir sin guardar?». */
  puedeSalir(): boolean | Promise<boolean> {
    return this.estado.confirmarSalida();
  }

  /** Recargar o cerrar la pestaña con cambios: el aviso del navegador. */
  @HostListener('window:beforeunload', ['$event'])
  antesDeDescargar(evento: BeforeUnloadEvent): void {
    if (this.estado.hayCambios() || this.estado.guardando()) {
      evento.preventDefault();
      evento.returnValue = '';
    }
  }

  valor(evento: Event): string {
    const destino = evento.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement | null;
    return destino?.value ?? '';
  }

  elegirProducto(evento: Event): void {
    const valor = this.valor(evento);
    this.estado.elegirProducto(valor ? Number(valor) : null);
    this.terminarBusquedaProducto();
  }
}
