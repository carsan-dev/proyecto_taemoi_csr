import { LiveAnnouncer } from '@angular/cdk/a11y';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { ChangeDetectorRef, Component, ElementRef, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription, firstValueFrom } from 'rxjs';

import { SUBIDA_IMAGEN, mensajeErrorSubida, prepararFicheroParaSubida, reiniciarSelectorArchivo } from '../../utils/subida-archivos.util';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { numero } from '../formato/formato-es';
import { EventoFotosApiService } from './evento-fotos-api.service';
import { enfocarOpcion, teclaEnMenu } from '../navegacion/teclado-menu';
import { moverEn, urlImagen } from './eventos-vista';

/** Tope de fotos por evento (el de la API) y de subidas simultáneas al guardar (dos). */
export const MAX_FOTOS = 40;
export const SUBIDAS_A_LA_VEZ = 2;
/** Ancho de las miniaturas pedidas a la API (`?w=`, hasta 640). */
export const ANCHO_FOTO = 640;
/** Arrastre: inmediato con ratón y por pulsación larga en táctil (el dedo que se desliza antes sigue desplazando la página). */
export const RETARDO_ARRASTRE = { touch: 350, mouse: 0 };
/** Medidas del menú «⋯» de la miniatura (ancho fijo y alto aproximado) para colocarlo dentro de la pantalla. */
const MENU_ANCHO = 200;
const MENU_ALTO = 144;
const MENU_MARGEN = 8;

export type EstadoFoto = 'guardada' | 'pendiente' | 'subiendo' | 'error';
/** El mando de orden con el foco: tras mover, el foco vuelve al mismo de la miniatura movida. */
type MandoFoto = 'asa' | 'antes' | 'despues';

export interface FotoItem {
  /** Identidad estable en la rejilla (no es el id del servidor, que las pendientes aún no tienen). */
  clave: number;
  estado: EstadoFoto;
  id: number | null;
  /** Lo que se ve: la vista previa local o la miniatura del servidor. */
  vista: string;
  archivo: File | null;
  /** URL local (`createObjectURL`) que hay que revocar al quitar la foto y al destruir el bloque. */
  urlLocal: string | null;
  motivo: string | null;
}

export interface ResultadoFotos {
  /** Fotos que no se pudieron subir (quedan marcadas con «Reintentar»). */
  fallos: number;
  /** El `PUT .../orden` falló: el orden sigue sin guardar. */
  errorOrden: boolean;
  motivos: string[];
}

/**
 * «Fotos del evento» del formulario de eventos (E8c · F2; boceto `fase3-e8c-boceto-fotos.html`, marco 1 y panel móvil
 * del marco 3). Rejilla fluida de miniaturas cuadradas con cuatro estados (guardada, pendiente, subiendo, error con
 * «Reintentar»), selección múltiple y soltar ficheros sobre el bloque, orden por arrastre (`cdk/drag-drop`) y por botones
 * «antes» y «después» con teclado, anunciado con `LiveAnnouncer`. Subir y reordenar esperan a «Guardar cambios» (lo pide
 * el formulario con `sincronizar`); borrar una foto guardada es al momento y con confirmación.
 */
@Component({
  selector: 'pn-fotos-evento',
  standalone: true,
  imports: [CdkDropList, CdkDrag, CdkDragHandle, EsqueletoComponent, BloqueErrorComponent],
  templateUrl: './evento-fotos.component.html',
  styleUrls: ['./evento-fotos.component.scss'],
})
export class EventoFotosComponent implements OnInit, OnDestroy {
  private readonly api = inject(EventoFotosApiService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly anunciador = inject(LiveAnnouncer);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** El evento (nulo mientras se crea; el formulario lo fija al crearlo). */
  @Input() eventoId: number | null = null;
  /** El formulario está guardando: el bloque no admite cambios. */
  @Input() guardando = false;

  readonly max = MAX_FOTOS;
  readonly retardoArrastre = RETARDO_ARRASTRE;

  carga: 'cargando' | 'listo' | 'error' = 'listo';
  fotos: FotoItem[] = [];
  /** Motivos de las fotos rechazadas y aviso de las descartadas por el tope, en línea. */
  mensajes: string[] = [];
  arrastrando = false;
  /** Clave de la miniatura con el menú «⋯» abierto (táctil). */
  menuAbierto: number | null = null;
  menuPosicion = { top: 0, left: 0 };
  eliminando = false;
  private ordenCambiado = false;
  /** Las que no se subieron deben colocarse cuando se suban: hará falta otro `PUT .../orden`. */
  private reordenarTrasSubir = false;
  private contador = 0;
  private lectura?: Subscription;
  private escuchaFuera = false;

  ngOnInit(): void {
    if (this.eventoId !== null) {
      this.cargar();
    }
  }

  ngOnDestroy(): void {
    this.lectura?.unsubscribe();
    this.quitarEscucha();
    for (const f of this.fotos) {
      this.soltarUrl(f);
    }
  }

  // ------------------------------------------------------------------ estado

  /** Cambios sin guardar: las fotos pendientes (con las fallidas) y, si lo hay, el cambio de orden. */
  get cambios(): number {
    return this.fotos.filter((f) => f.estado !== 'guardada').length + (this.ordenCambiado ? 1 : 0);
  }

  get ocupado(): boolean {
    return this.guardando || this.eliminando || this.fotos.some((f) => f.estado === 'subiendo');
  }

  get completo(): boolean {
    return this.fotos.length >= MAX_FOTOS;
  }

  get textoContador(): string {
    return this.carga === 'listo' ? `${numero(this.fotos.length)} / ${MAX_FOTOS}` : `– / ${MAX_FOTOS}`;
  }

  descripcion(f: FotoItem, indice: number): string {
    const estado: Record<EstadoFoto, string> = {
      guardada: 'guardada',
      pendiente: 'pendiente de subir',
      subiendo: 'subiendo',
      error: 'no se pudo subir',
    };
    return `Foto ${indice + 1} de ${this.fotos.length}, ${estado[f.estado]}`;
  }

  // ------------------------------------------------------------------ lectura (editar)

  /** `GET /eventos/{id}/fotos`: un fallo aquí queda en este bloque y no bloquea el resto del formulario. */
  cargar(): void {
    const id = this.eventoId;
    if (id === null) {
      return;
    }
    this.lectura?.unsubscribe();
    this.carga = 'cargando';
    this.lectura = this.api.listar(id).subscribe({
      next: (lista) => {
        this.fotos = [...lista]
          .sort((a, b) => a.orden - b.orden)
          .map((f) => ({
            clave: ++this.contador,
            estado: 'guardada' as const,
            id: f.id,
            vista: urlImagen({ fotoEvento: { id: f.id, url: f.url } }, ANCHO_FOTO) ?? f.url,
            archivo: null,
            urlLocal: null,
            motivo: null,
          }));
        this.carga = 'listo';
        this.cdr.markForCheck();
      },
      error: () => {
        this.carga = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ añadir

  async elegir(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const elegidos = Array.from(entrada?.files ?? []);
    await this.agregar(elegidos);
    reiniciarSelectorArchivo(entrada);
  }

  abrirSelector(entrada: HTMLInputElement): void {
    if (!this.ocupado && !this.completo) {
      entrada.click();
    }
  }

  alArrastrar(evento: DragEvent, encima: boolean): void {
    if (!evento.dataTransfer?.types?.includes('Files')) {
      return;
    }
    evento.preventDefault();
    if (!encima && evento.relatedTarget instanceof Node && this.host.nativeElement.contains(evento.relatedTarget)) {
      return;
    }
    this.arrastrando = encima && this.carga === 'listo';
  }

  async alSoltar(evento: DragEvent): Promise<void> {
    if (!evento.dataTransfer?.types?.includes('Files')) {
      return;
    }
    evento.preventDefault();
    this.arrastrando = false;
    await this.agregar(Array.from(evento.dataTransfer.files ?? []));
  }

  /**
   * La validación de hoy (`prepararFicheroParaSubida` con `SUBIDA_IMAGEN`), foto a foto: las que no valen no se añaden y
   * se dice por qué. Si la selección supera el tope, entran las que quepan y se avisa de las descartadas.
   */
  private async agregar(archivos: File[]): Promise<void> {
    if (this.carga !== 'listo' || this.ocupado || archivos.length === 0) {
      return;
    }
    const rechazos: string[] = [];
    let descartadas = 0;
    let anadidas = 0;
    for (const archivo of archivos) {
      if (this.fotos.length >= MAX_FOTOS) {
        descartadas++;
        continue;
      }
      const preparado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
      if (!preparado.ok) {
        rechazos.push(`«${archivo.name}»: ${preparado.motivo}`);
        continue;
      }
      if (this.fotos.length >= MAX_FOTOS) {
        descartadas++;
        continue;
      }
      const urlLocal = URL.createObjectURL(preparado.file);
      this.fotos = [
        ...this.fotos,
        { clave: ++this.contador, estado: 'pendiente', id: null, vista: urlLocal, archivo: preparado.file, urlLocal, motivo: null },
      ];
      anadidas++;
    }
    const mensajes: string[] = [];
    if (rechazos.length) {
      mensajes.push(`${rechazos.length === 1 ? 'No se pudo añadir una foto.' : 'Algunas fotos no se añadieron.'} ${rechazos.join(' ')}`);
    }
    if (descartadas) {
      mensajes.push(
        `${descartadas === 1 ? 'Se ha descartado 1 foto' : `Se han descartado ${numero(descartadas)} fotos`}: el máximo es ${MAX_FOTOS} por evento.`
      );
    }
    this.mensajes = mensajes;
    if (anadidas) {
      this.anunciar(anadidas === 1 ? '1 foto añadida.' : `${numero(anadidas)} fotos añadidas.`);
    }
    this.cdr.markForCheck();
  }

  // ------------------------------------------------------------------ quitar

  /** Una pendiente se quita al momento; una guardada, con confirmación y el `DELETE` (también al momento). */
  async quitar(f: FotoItem): Promise<void> {
    if (this.ocupado) {
      return;
    }
    const indice = this.fotos.indexOf(f);
    if (f.estado !== 'guardada') {
      this.eliminarDeLaLista(f, indice);
      return;
    }
    const id = this.eventoId;
    if (id === null || f.id === null) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Eliminar foto',
      mensaje: 'La foto se borra ahora mismo y deja de verse en la web, aunque no guardes el resto de cambios.',
      accion: 'Sí, eliminar',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-foto',
    });
    if (!r.confirmado || this.ocupado) {
      return;
    }
    this.eliminando = true;
    this.api.eliminar(id, f.id).subscribe({
      next: () => {
        this.eliminando = false;
        this.eliminarDeLaLista(f, this.fotos.indexOf(f));
        this.avisos.exito('Foto eliminada.');
      },
      error: (error) => {
        this.eliminando = false;
        this.avisos.error(mensajeErrorSubida(error, 'No se ha podido eliminar la foto.'));
        this.cdr.markForCheck();
      },
    });
  }

  private eliminarDeLaLista(f: FotoItem, indice: number): void {
    this.soltarUrl(f);
    this.fotos = this.fotos.filter((x) => x !== f);
    if (this.fotos.length === 0) {
      this.ordenCambiado = false;
      this.reordenarTrasSubir = false;
    }
    this.anunciar(`Foto ${indice + 1} quitada. Quedan ${numero(this.fotos.length)}.`);
    this.cdr.detectChanges();
    this.enfocarTras(indice);
  }

  private enfocarTras(indice: number): void {
    const celdas = this.host.nativeElement.querySelectorAll<HTMLElement>('.fotos__celda--foto');
    const celda = celdas[Math.min(indice, celdas.length - 1)];
    // En táctil «Eliminar» vive en el menú: el foco va al «⋯» de la miniatura que ocupa el sitio.
    const candidatos = [celda?.querySelector<HTMLElement>('.fotos__quitar'), celda?.querySelector<HTMLElement>('.fotos__mas')];
    for (const c of candidatos) {
      c?.focus();
      if (c && document.activeElement === c) {
        return;
      }
    }
    this.host.nativeElement.querySelector<HTMLElement>('.fotos__anadir')?.focus();
  }

  private soltarUrl(f: FotoItem): void {
    if (f.urlLocal) {
      URL.revokeObjectURL(f.urlLocal);
      f.urlLocal = null;
    }
  }

  // ------------------------------------------------------------------ «⋯» (táctil)

  private menuRef(clave: number): HTMLElement | null {
    return this.host.nativeElement.querySelector<HTMLElement>(`#fotos-menu-${clave}`);
  }

  private botonMenu(clave: number): HTMLButtonElement | null {
    return this.host.nativeElement.querySelector<HTMLButtonElement>(`#fotos-mas-${clave}`);
  }

  private readonly alPulsarFuera = (evento: Event): void => {
    const destino = evento.target as Node | null;
    const zona = this.menuAbierto === null ? null : this.botonMenu(this.menuAbierto)?.closest('.fotos__menu-zona');
    if (destino && zona?.contains(destino)) {
      return;
    }
    this.menuAbierto = null;
    this.quitarEscucha();
    this.cdr.markForCheck();
  };

  /** El menú va con `position: fixed` (la miniatura recorta): se cierra al desplazar o girar para no quedar suelto. */
  private readonly alMoverPantalla = (): void => {
    this.cerrarMenu(false);
    this.cdr.markForCheck();
  };

  private quitarEscucha(): void {
    if (this.escuchaFuera) {
      document.removeEventListener('pointerdown', this.alPulsarFuera, true);
      window.removeEventListener('scroll', this.alMoverPantalla, true);
      window.removeEventListener('resize', this.alMoverPantalla);
      this.escuchaFuera = false;
    }
  }

  alternarMenu(f: FotoItem, evento?: Event): void {
    if (this.menuAbierto === f.clave) {
      this.cerrarMenu(false);
      return;
    }
    const boton = this.botonMenu(f.clave);
    if (boton) {
      const caja = boton.getBoundingClientRect();
      const izquierda = Math.min(Math.max(caja.right - MENU_ANCHO, MENU_MARGEN), Math.max(window.innerWidth - MENU_ANCHO - MENU_MARGEN, MENU_MARGEN));
      const abajo = caja.bottom + 4;
      const arriba = caja.top - MENU_ALTO - 4;
      this.menuPosicion = { top: abajo + MENU_ALTO > window.innerHeight && arriba >= MENU_MARGEN ? arriba : abajo, left: izquierda };
    }
    this.menuAbierto = f.clave;
    if (!this.escuchaFuera) {
      document.addEventListener('pointerdown', this.alPulsarFuera, true);
      window.addEventListener('scroll', this.alMoverPantalla, true);
      window.addEventListener('resize', this.alMoverPantalla);
      this.escuchaFuera = true;
    }
    this.cdr.detectChanges();
    enfocarOpcion(this.menuRef(f.clave), (evento as KeyboardEvent | undefined)?.key === 'ArrowUp' ? 'ultima' : 'primera');
  }

  teclaBotonMenu(f: FotoItem, evento: KeyboardEvent): void {
    if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && this.menuAbierto !== f.clave) {
      evento.preventDefault();
      this.alternarMenu(f, evento);
    }
  }

  teclaMenu(f: FotoItem, evento: KeyboardEvent): void {
    const menu = this.menuRef(f.clave);
    if (!menu) {
      return;
    }
    const accion = teclaEnMenu(evento, menu);
    if (accion === 'cerrar') {
      this.cerrarMenu(true);
    } else if (accion === 'salir') {
      this.cerrarMenu(false);
    }
  }

  cerrarMenu(devolverFoco: boolean): void {
    const clave = this.menuAbierto;
    this.menuAbierto = null;
    this.quitarEscucha();
    if (devolverFoco && clave !== null) {
      this.cdr.detectChanges();
      this.botonMenu(clave)?.focus();
    }
  }

  alSalirDelMenu(evento: FocusEvent): void {
    const zona = evento.currentTarget as HTMLElement | null;
    const destino = evento.relatedTarget as Node | null;
    if (zona && destino && !zona.contains(destino)) {
      this.cerrarMenu(false);
    }
  }

  /** «Mover antes» / «Mover después» del menú: el foco vuelve al «⋯» de la misma miniatura, esté donde esté ahora. */
  moverDesdeMenu(f: FotoItem, delta: number): void {
    const indice = this.fotos.indexOf(f);
    this.cerrarMenu(false);
    this.mover(indice, delta);
    this.cdr.detectChanges();
    this.botonMenu(f.clave)?.focus();
  }

  quitarDesdeMenu(f: FotoItem): void {
    this.cerrarMenu(false);
    void this.quitar(f);
  }

  // ------------------------------------------------------------------ orden

  /** «Antes» y «después» (botones y teclado). */
  mover(indice: number, delta: number, mando?: MandoFoto): void {
    const f = this.fotos[indice];
    if (!f || this.ocupado) {
      return;
    }
    const destino = indice + delta;
    if (destino < 0 || destino >= this.fotos.length) {
      this.anunciar(`La foto ${indice + 1} ya es la ${destino < 0 ? 'primera' : 'última'}.`);
      return;
    }
    this.aplicarOrden(indice, destino);
    if (mando) {
      this.cdr.detectChanges();
      this.enfocarMando(f.clave, mando);
    }
  }

  /** Flechas con el foco en el asa o en los botones de mover. */
  teclaOrden(evento: KeyboardEvent, indice: number, mando: MandoFoto): void {
    const delta = ({ ArrowLeft: -1, ArrowUp: -1, ArrowRight: 1, ArrowDown: 1 } as Record<string, number>)[evento.key];
    if (!delta) {
      return;
    }
    evento.preventDefault();
    this.mover(indice, delta, mando);
  }

  /** El arrastre del asa (`cdkDropListDropped`). */
  soltar(evento: CdkDragDrop<FotoItem[]>): void {
    if (evento.previousIndex === evento.currentIndex || this.ocupado) {
      return;
    }
    this.aplicarOrden(evento.previousIndex, evento.currentIndex);
  }

  private aplicarOrden(desde: number, hasta: number): void {
    this.fotos = moverEn(this.fotos, desde, hasta);
    this.ordenCambiado = true;
    this.anunciar(`Foto movida a la posición ${hasta + 1} de ${this.fotos.length}.`);
    this.cdr.markForCheck();
  }

  private enfocarMando(clave: number, mando: MandoFoto): void {
    const celda = this.host.nativeElement.querySelector<HTMLElement>(`.fotos__celda--foto[data-foto="${clave}"]`);
    const objetivo = celda?.querySelector<HTMLButtonElement>(`.fotos__${mando}`);
    if (objetivo && !objetivo.disabled) {
      objetivo.focus();
    } else {
      // En el extremo, el botón queda deshabilitado: el foco pasa al asa de la misma miniatura.
      celda?.querySelector<HTMLElement>('.fotos__asa')?.focus();
    }
  }

  private anunciar(texto: string): void {
    void this.anunciador.announce(texto);
  }

  // ------------------------------------------------------------------ subir al guardar

  /** «Reintentar» en una miniatura fallida: la sube ahora, con el evento ya creado. */
  async reintentar(f: FotoItem): Promise<void> {
    const id = this.eventoId;
    if (id === null || this.ocupado || f.estado !== 'error') {
      return;
    }
    const r = await this.sincronizar(id, f);
    if (r.fallos === 0 && !r.errorOrden) {
      this.avisos.exito('Foto subida.');
    } else {
      const base = r.fallos > 0 ? 'No se pudo subir la foto.' : 'No se pudo guardar el orden de las fotos.';
      this.avisos.error([base, ...r.motivos].join(' '));
    }
  }

  /**
   * Lo que pide «Guardar cambios» tras guardar el evento: sube las pendientes (y las fallidas) con `SUBIDAS_A_LA_VEZ` a
   * la vez como máximo y, si cambió el orden (o se subieron varias a la vez, que llegan en cualquier orden), envía la
   * secuencia final. Las fallidas quedan marcadas con «Reintentar»; las demás no se pierden.
   */
  async sincronizar(eventoId: number, solo?: FotoItem): Promise<ResultadoFotos> {
    const lote = this.fotos.filter((f) => (f.estado === 'pendiente' || f.estado === 'error') && (!solo || f === solo) && f.archivo);
    for (const f of lote) {
      f.estado = 'subiendo';
      f.motivo = null;
    }
    this.cdr.markForCheck();
    let subidas = 0;
    let fallos = 0;
    const motivos: string[] = [];
    await this.enLotes(lote, async (f) => {
      try {
        const r = await firstValueFrom(this.api.subir(eventoId, f.archivo as File));
        f.id = r.id;
        f.estado = 'guardada';
        f.archivo = null;
        subidas++;
      } catch (error) {
        f.estado = 'error';
        const delServidor = mensajeErrorSubida(error, '');
        f.motivo = delServidor || 'No se pudo subir la foto.';
        fallos++;
        if (delServidor && !motivos.includes(delServidor)) {
          motivos.push(delServidor);
        }
      }
      this.cdr.markForCheck();
    });

    let errorOrden = false;
    const sinSubir = this.fotos.some((f) => f.estado !== 'guardada');
    const ids = this.fotos.filter((f) => f.id !== null).map((f) => f.id as number);
    if ((this.ordenCambiado || this.reordenarTrasSubir || subidas >= 2) && ids.length >= 2) {
      try {
        await firstValueFrom(this.api.ordenar(eventoId, ids));
        this.ordenCambiado = false;
        this.reordenarTrasSubir = sinSubir;
      } catch (error) {
        errorOrden = true;
        this.reordenarTrasSubir = true;
        const delServidor = mensajeErrorSubida(error, '');
        if (delServidor) {
          motivos.push(delServidor);
        }
      }
    } else {
      this.ordenCambiado = false;
      this.reordenarTrasSubir = sinSubir;
    }
    this.cdr.markForCheck();
    return { fallos, errorOrden, motivos };
  }

  private async enLotes<T>(lista: T[], tarea: (elemento: T) => Promise<void>): Promise<void> {
    let siguiente = 0;
    const trabajador = async (): Promise<void> => {
      while (siguiente < lista.length) {
        await tarea(lista[siguiente++]);
      }
    };
    await Promise.all(Array.from({ length: Math.min(SUBIDAS_A_LA_VEZ, lista.length) }, trabajador));
  }
}
