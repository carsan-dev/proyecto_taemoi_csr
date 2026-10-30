import { Location } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { EndpointsService } from '../../servicios/endpoints/endpoints.service';
import {
  SUBIDA_DOCUMENTO,
  SUBIDA_IMAGEN,
  mensajeErrorSubida,
  prepararFicheroParaSubida,
  reiniciarSelectorArchivo,
} from '../../utils/subida-archivos.util';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { LecturasPanelService } from '../carga/lecturas-panel.service';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { numero } from '../formato/formato-es';
import { EventoFotosComponent, ResultadoFotos } from './evento-fotos.component';
import {
  ANCHO_FORMULARIO,
  CampoConError,
  DocumentoEvento,
  EventoPanel,
  IMAGEN_POR_DEFECTO,
  cuerpoCrear,
  cuerpoEditar,
  estadoFecha,
  etiquetaEstado,
  fechaCorta,
  fechaParaCampo,
  mensajesError,
  nombreDescarga,
  tamanoArchivo,
  urlImagen,
} from './eventos-vista';

type Estado = 'cargando' | 'listo' | 'error';

interface ErrorResumen {
  campo: CampoConError;
  id: string;
  mensaje: string;
}

const IDS: Record<CampoConError, string> = { titulo: 'evento-titulo', descripcion: 'evento-descripcion' };

/**
 * Crear y editar evento del panel nuevo (8b-3f, P2 «vista previa de la web al lado»): un formulario en una columna con
 * las secciones Información, Imagen y Documentos, etiquetas «obligatorio» u «opcional», resumen de errores arriba con
 * enlaces a cada campo y barra fija abajo con «Cancelar» y la acción (desactivada con errores, «Guardando…»). Al lado
 * (desde 1280 px; al final del formulario por debajo), la tarjeta pública tal como se verá, que cambia al escribir y dice
 * si el evento está visible u oculto. Las MISMAS peticiones que `CrearEventoComponent` y `EditarEventoComponent`
 * (método, ruta, multipart y orden), sin SweetAlert2 ni cargador global. Va en su propio chunk detrás de
 * `estilosPanelGuard` (`eventosCrear` y `eventosEditar/:id`).
 */
@Component({
  selector: 'app-evento-formulario',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BotonOcupadoComponent,
    EventoFotosComponent,
  ],
  templateUrl: './evento-formulario.component.html',
  styleUrls: ['./evento-formulario.component.scss'],
})
export class EventoFormularioComponent implements OnInit, OnDestroy {
  private readonly endpoints = inject(EndpointsService);
  private readonly lecturas = inject(LecturasPanelService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Los validadores de hoy. */
  readonly form = inject(FormBuilder).nonNullable.group({
    titulo: ['', [Validators.required, Validators.maxLength(100)]],
    descripcion: ['', [Validators.required, Validators.maxLength(500)]],
    fechaEvento: [''],
    visible: [true],
  });

  readonly ids = IDS;
  readonly tamanoArchivo = tamanoArchivo;
  readonly imagenPorDefecto = IMAGEN_POR_DEFECTO;

  modo: 'crear' | 'editar' = 'crear';
  eventoId: number | null = null;
  estado: Estado = 'listo';
  evento: EventoPanel | null = null;

  /** La imagen elegida y aún sin guardar, con su URL local para verla. */
  imagen: File | null = null;
  imagenLocal: string | null = null;
  errorImagen: string | null = null;

  documentosGuardados: DocumentoEvento[] = [];
  documentosNuevos: File[] = [];
  errorDocumentos: string | null = null;
  subiendoDocumentos = false;

  guardando = false;
  eliminandoImagen = false;
  /** Se pulsó la acción con errores: el resumen se ve y cada error, bajo su campo. */
  intentado = false;

  /** El bloque «Fotos del evento» (E8c · F2): lleva sus fotos; el formulario le pide subirlas al guardar. */
  private readonly fotos = viewChild(EventoFotosComponent);

  private lectura?: Subscription;
  private readonly destroy$ = new Subject<void>();
  /** Las escrituras no se cancelan al salir (acaban y suben los documentos); la navegación, no. */
  private destruido = false;

  get titulo(): string {
    return this.modo === 'crear' ? 'Crear nuevo evento' : 'Editar evento';
  }

  get subtitulo(): string {
    return this.modo === 'crear'
      ? 'Crea un evento para mostrar en la página web pública'
      : 'Modifica la información del evento publicado en la web';
  }

  /** «N cambios sin guardar» de la barra: solo las fotos pendientes y el cambio de orden. */
  get cambiosFotos(): number {
    return this.fotos()?.cambios ?? 0;
  }

  get textoCambiosFotos(): string {
    const n = this.cambiosFotos;
    return n === 1 ? '1 cambio sin guardar' : `${numero(n)} cambios sin guardar`;
  }

  get accion(): string {
    return this.modo === 'crear' ? 'Crear evento' : 'Guardar cambios';
  }

  ngOnInit(): void {
    this.ruta.params.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const id = Number(params['id']);
      if (params['id'] !== undefined && !Number.isNaN(id)) {
        this.modo = 'editar';
        this.eventoId = id;
        this.cargar();
      } else {
        this.modo = 'crear';
        this.eventoId = null;
        this.estado = 'listo';
      }
    });
  }

  ngOnDestroy(): void {
    this.lectura?.unsubscribe();
    this.soltarImagenLocal();
    this.destruido = true;
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ------------------------------------------------------------------ lectura (editar)

  /** `GET /eventos/{id}` de hoy. */
  cargar(): void {
    if (this.eventoId === null) {
      return;
    }
    this.lectura?.unsubscribe();
    this.estado = 'cargando';
    this.lectura = this.lecturas.evento(this.eventoId).subscribe({
      next: (evento: EventoPanel) => {
        this.evento = evento;
        this.form.patchValue({
          titulo: evento.titulo ?? '',
          descripcion: evento.descripcion ?? '',
          fechaEvento: fechaParaCampo(evento.fechaEvento),
        });
        this.documentosGuardados = [...(evento.documentos ?? [])];
        this.estado = 'listo';
        this.cdr.markForCheck();
      },
      error: () => {
        this.estado = 'error';
        this.cdr.markForCheck();
      },
    });
  }

  // ------------------------------------------------------------------ errores

  private visibleError(campo: CampoConError): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || this.intentado);
  }

  erroresDe(campo: CampoConError): string[] {
    return this.visibleError(campo) ? mensajesError(campo, this.form.controls[campo].errors) : [];
  }

  get errores(): ErrorResumen[] {
    const lista: ErrorResumen[] = [];
    for (const campo of ['titulo', 'descripcion'] as CampoConError[]) {
      for (const mensaje of mensajesError(campo, this.form.controls[campo].errors)) {
        lista.push({ campo, id: IDS[campo], mensaje });
      }
    }
    return lista;
  }

  get tituloResumen(): string {
    const n = this.errores.length;
    return n === 1 ? 'Revisa 1 campo antes de guardar' : `Revisa ${numero(n)} campos antes de guardar`;
  }

  describirCampo(campo: CampoConError): string {
    const ids = [`${IDS[campo]}-ayuda`];
    if (this.erroresDe(campo).length) {
      ids.push(`${IDS[campo]}-error`);
    }
    return ids.join(' ');
  }

  irACampo(evento: Event, id: string): void {
    evento.preventDefault();
    const campo = this.host.nativeElement.querySelector<HTMLElement>(`#${id}`);
    campo?.focus();
    campo?.scrollIntoView?.({ block: 'center' });
  }

  // ------------------------------------------------------------------ vista previa

  get valorTitulo(): string {
    return this.form.controls.titulo.value;
  }

  get valorDescripcion(): string {
    return this.form.controls.descripcion.value;
  }

  get visibleEnLaWeb(): boolean {
    return this.modo === 'crear' ? this.form.controls.visible.value : this.evento?.visible !== false;
  }

  /** La imagen que se ve en el campo: la elegida o la guardada. */
  get imagenActual(): string | null {
    if (this.imagenLocal) {
      return this.imagenLocal;
    }
    return this.evento?.fotoEvento?.url ? urlImagen(this.evento, ANCHO_FORMULARIO) : null;
  }

  get imagenPrevia(): string {
    return this.imagenActual ?? IMAGEN_POR_DEFECTO;
  }

  /** «Próximo · 20 nov 2026», la etiqueta de fecha de la tarjeta pública. */
  get fechaPrevia(): string | null {
    const valor = this.form.controls.fechaEvento.value;
    const corta = fechaCorta(valor);
    return corta ? `${etiquetaEstado(estadoFecha(valor, new Date()))} · ${corta}` : null;
  }

  get estadoPrevia(): string {
    return estadoFecha(this.form.controls.fechaEvento.value, new Date());
  }

  alternarVisible(): void {
    const control = this.form.controls.visible;
    control.setValue(!control.value);
  }

  // ------------------------------------------------------------------ imagen

  /** La validación de hoy (`prepararFicheroParaSubida` con `SUBIDA_IMAGEN`); el motivo, en línea. */
  async elegirImagen(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const elegido = entrada?.files?.[0];
    if (!elegido) {
      return;
    }
    const preparado = await prepararFicheroParaSubida(elegido, SUBIDA_IMAGEN);
    reiniciarSelectorArchivo(entrada);
    if (!preparado.ok) {
      this.errorImagen = `No se pudo usar la imagen. ${preparado.motivo}`;
      this.cdr.markForCheck();
      return;
    }
    this.errorImagen = null;
    this.soltarImagenLocal();
    this.imagen = preparado.file;
    this.imagenLocal = URL.createObjectURL(preparado.file);
    this.cdr.markForCheck();
  }

  /** «Quitar»: deja de usar la imagen elegida (no envía nada). */
  quitarImagen(): void {
    this.soltarImagenLocal();
    this.imagen = null;
    this.errorImagen = null;
  }

  private soltarImagenLocal(): void {
    if (this.imagenLocal) {
      URL.revokeObjectURL(this.imagenLocal);
      this.imagenLocal = null;
    }
  }

  /** «Eliminar imagen» (editar): confirmación destructiva y el `DELETE /eventos/{id}/imagen` de hoy. */
  async eliminarImagen(): Promise<void> {
    const id = this.eventoId;
    if (id === null || this.eliminandoImagen) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Eliminar imagen',
      mensaje: 'La imagen se borra ahora mismo y deja de verse en la web, aunque no guardes el resto de cambios.',
      accion: 'Sí, eliminar',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-imagen',
    });
    if (!r.confirmado || this.eliminandoImagen) {
      return;
    }
    this.eliminandoImagen = true;
    this.endpoints
      .eliminarImagenEvento(id)
      .subscribe({
        next: () => {
          this.eliminandoImagen = false;
          if (this.evento) {
            this.evento = { ...this.evento, fotoEvento: null };
          }
          this.avisos.exito('Imagen eliminada.');
          this.cdr.markForCheck();
        },
        error: () => {
          this.eliminandoImagen = false;
          this.avisos.error('No se ha podido eliminar la imagen.');
          this.cdr.markForCheck();
        },
      });
  }

  // ------------------------------------------------------------------ documentos

  /** La validación de hoy (`SUBIDA_DOCUMENTO`): los que no valen no se añaden y se dice por qué. */
  async elegirDocumentos(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const elegidos = Array.from(entrada?.files ?? []);
    const rechazados: string[] = [];
    for (const elegido of elegidos) {
      const preparado = await prepararFicheroParaSubida(elegido, SUBIDA_DOCUMENTO);
      if (preparado.ok) {
        this.documentosNuevos = [...this.documentosNuevos, preparado.file];
      } else {
        rechazados.push(`«${elegido.name}»: ${preparado.motivo}`);
      }
    }
    reiniciarSelectorArchivo(entrada);
    this.errorDocumentos = rechazados.length
      ? `${rechazados.length === 1 ? 'No se pudo añadir el documento.' : 'Algunos documentos no se añadieron.'} ${rechazados.join(' ')}`
      : null;
    this.cdr.markForCheck();
  }

  quitarDocumento(indice: number): void {
    this.documentosNuevos = this.documentosNuevos.filter((_, i) => i !== indice);
  }

  get textoSubir(): string {
    const n = this.documentosNuevos.length;
    return n === 1 ? 'Subir 1 documento' : `Subir ${numero(n)} documentos`;
  }

  /** «Subir N documentos» (editar): un `POST /eventos/{id}/documentos` por documento, a la vez, como hoy. */
  subirDocumentos(): void {
    const id = this.eventoId;
    if (id === null || this.subiendoDocumentos || this.documentosNuevos.length === 0) {
      return;
    }
    const lote = [...this.documentosNuevos];
    this.subiendoDocumentos = true;
    this.enviarDocumentos(id, lote, (subidos, errores, motivos, guardados) => {
      this.subiendoDocumentos = false;
      this.documentosNuevos = [];
      this.documentosGuardados = [...this.documentosGuardados, ...guardados];
      if (errores > 0) {
        this.avisos.error(
          [`Subida parcial: ${frase(subidos, 'documento subido', 'documentos subidos')}, ${frase(errores, 'error', 'errores')}.`, ...motivos].join(' ')
        );
      } else {
        this.avisos.exito(subidos === 1 ? '1 documento subido correctamente.' : `${numero(subidos)} documentos subidos correctamente.`);
      }
      this.cdr.markForCheck();
    });
  }

  /** Lo de hoy: todos a la vez y, cuando han vuelto todos, el resumen (subidos, errores y motivos del servidor). */
  private enviarDocumentos(
    eventoId: number,
    lote: File[],
    alTerminar: (subidos: number, errores: number, motivos: string[], guardados: DocumentoEvento[], fallidos: File[]) => void
  ): void {
    let subidos = 0;
    let errores = 0;
    const motivos: string[] = [];
    const guardados: (DocumentoEvento | null)[] = lote.map(() => null);
    const comprobar = () => {
      if (subidos + errores === lote.length) {
        alTerminar(
          subidos,
          errores,
          motivos,
          guardados.filter((g): g is DocumentoEvento => !!g),
          lote.filter((_, i) => !guardados[i])
        );
      }
    };
    lote.forEach((archivo, i) => {
      this.endpoints.subirDocumentoEvento(eventoId, archivo).subscribe({
        next: (documento: DocumentoEvento) => {
          subidos++;
          guardados[i] = documento;
          comprobar();
        },
        error: (error) => {
          errores++;
          const motivo = mensajeErrorSubida(error, '');
          if (motivo && !motivos.includes(motivo)) {
            motivos.push(motivo);
          }
          comprobar();
        },
      });
    });
  }

  /** «Descargar»: lo de hoy (en iOS, la URL directa; si no, el blob con el nombre de hoy). */
  descargar(documento: DocumentoEvento): void {
    const id = this.eventoId;
    if (!documento?.id || id === null) {
      this.avisos.error('No se pudo descargar el documento seleccionado.');
      return;
    }
    if (esIOS()) {
      globalThis.window?.open(this.endpoints.obtenerUrlDescargaDocumentoEvento(id, documento.id, true), '_self');
      return;
    }
    this.endpoints
      .descargarDocumentoEvento(id, documento.id, true)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (blob) => {
          const url = globalThis.URL.createObjectURL(blob);
          const enlace = document.createElement('a');
          enlace.href = url;
          enlace.download = nombreDescarga(documento.nombre, documento.tipo);
          enlace.click();
          globalThis.URL.revokeObjectURL(url);
        },
        error: () => this.avisos.error('No se pudo descargar el documento.'),
      });
  }

  /** «Eliminar» un documento guardado: confirmación destructiva y el `DELETE` de hoy. */
  async eliminarDocumento(documento: DocumentoEvento): Promise<void> {
    const id = this.eventoId;
    if (id === null) {
      return;
    }
    const r = await this.confirmacion.confirmar({
      titulo: 'Eliminar documento',
      mensaje: `¿Estás seguro de eliminar «${documento.nombre}»?`,
      accion: 'Sí, eliminar',
      cancelar: 'Cancelar',
      destructivo: true,
      focoEnCancelar: true,
      nombre: 'eliminar-documento',
    });
    if (!r.confirmado) {
      return;
    }
    this.endpoints
      .eliminarDocumentoEvento(id, documento.id)
      .subscribe({
        next: () => {
          this.documentosGuardados = this.documentosGuardados.filter((d) => d.id !== documento.id);
          this.avisos.exito('El documento ha sido eliminado.');
          this.cdr.markForCheck();
        },
        error: () => {
          this.avisos.error('No se pudo eliminar el documento.');
          this.cdr.markForCheck();
        },
      });
  }

  // ------------------------------------------------------------------ guardar y cancelar

  guardar(): void {
    if (this.guardando) {
      return;
    }
    if (this.form.invalid) {
      this.intentado = true;
      this.form.markAllAsTouched();
      this.cdr.detectChanges();
      this.host.nativeElement.querySelector<HTMLElement>('.evento-form__resumen')?.focus();
      return;
    }
    if (this.modo === 'crear') {
      this.crear();
    } else {
      this.actualizar();
    }
  }

  /** `POST /eventos/crear` de hoy («nuevo» y «file» con su nombre); después, cada documento y las fotos; luego, a Eventos. */
  private crear(): void {
    const v = this.form.getRawValue();
    const documentos = [...this.documentosNuevos];
    this.guardando = true;
    this.endpoints
      .crearEvento(cuerpoCrear(v), this.imagen)
      .subscribe({
        next: (creado: EventoPanel) => {
          const conFotos = (errores: number, motivos: string[], guardados: DocumentoEvento[], fallidos: File[]) => {
            const fotos = this.fotos();
            if (!fotos || fotos.cambios === 0) {
              this.terminarCreado(errores, motivos);
              return;
            }
            void fotos
              .sincronizar(creado.id)
              .then((resultado) => this.terminarCreado(errores, motivos, { creado, guardados, fallidos, resultado }));
          };
          if (documentos.length === 0) {
            conFotos(0, [], [], []);
            return;
          }
          this.enviarDocumentos(creado.id, documentos, (_subidos, errores, motivos, guardados, fallidos) =>
            conFotos(errores, motivos, guardados, fallidos)
          );
        },
        error: (error) => {
          this.guardando = false;
          this.avisos.error(mensajeErrorSubida(error, 'No hemos podido crear el evento.'));
          this.cdr.markForCheck();
        },
      });
  }

  /**
   * Tras crear: si todo fue bien (o solo fallaron documentos, como hoy), a Eventos. Si alguna foto no subió, el usuario
   * se queda en el formulario, que pasa a Editar con el evento ya creado para poder reintentar sin duplicarlo.
   */
  private terminarCreado(
    errores: number,
    motivos: string[],
    fotos?: { creado: EventoPanel; guardados: DocumentoEvento[]; fallidos: File[]; resultado: ResultadoFotos }
  ): void {
    this.guardando = false;
    const fallosFotos = fotos ? fotos.resultado.fallos : 0;
    const errorOrden = fotos?.resultado.errorOrden ?? false;
    const partes: string[] = [];
    if (errores > 0) {
      partes.push(errores === 1 ? '1 documento no se pudo subir.' : `${numero(errores)} documentos no se pudieron subir.`);
    }
    if (fallosFotos > 0) {
      partes.push(fallosFotos === 1 ? '1 foto no se pudo subir.' : `${numero(fallosFotos)} fotos no se pudieron subir.`);
    }
    if (errorOrden) {
      partes.push('No se pudo guardar el orden de las fotos.');
    }
    if (fotos && (fallosFotos > 0 || errorOrden)) {
      this.pasarAEditar(fotos.creado, fotos.guardados, fotos.fallidos);
      this.avisos.error(
        [
          `El evento se ha creado, pero ${partes.join(' ')}`,
          ...fotos.resultado.motivos,
          ...motivos,
          'Reintenta cada foto o pulsa «Guardar cambios».',
        ].join(' ')
      );
      this.cdr.markForCheck();
      return;
    }
    if (errores > 0) {
      this.avisos.error([`El evento se ha creado, pero ${partes.join(' ')}`, ...motivos].join(' '));
    } else {
      this.avisos.exito('Has creado un nuevo evento.');
    }
    if (!this.destruido) {
      void this.router.navigate(['/eventosListar']);
    }
  }

  /** El evento ya existe: el formulario sigue abierto como Editar, con lo que falta por subir. */
  private pasarAEditar(creado: EventoPanel, documentosGuardados: DocumentoEvento[], documentosFallidos: File[]): void {
    this.modo = 'editar';
    this.eventoId = creado.id;
    this.evento = creado;
    this.documentosGuardados = documentosGuardados;
    this.documentosNuevos = documentosFallidos;
    this.quitarImagen();
    this.location.replaceState(`/eventosEditar/${creado.id}`);
  }

  /** `PUT /eventos/{id}` de hoy: «eventoEditado» y «file» (el archivo o "null"). */
  private actualizar(): void {
    const id = this.eventoId;
    if (id === null) {
      return;
    }
    const datos = new FormData();
    datos.append('eventoEditado', JSON.stringify(cuerpoEditar(this.form.getRawValue())));
    if (this.imagen) {
      datos.append('file', this.imagen);
    } else {
      datos.append('file', 'null');
    }
    this.guardando = true;
    this.endpoints
      .actualizarEvento(id, datos)
      .subscribe({
        next: () => {
          const fotos = this.fotos();
          if (!fotos || fotos.cambios === 0) {
            this.terminarActualizado();
            return;
          }
          void fotos.sincronizar(id).then((resultado) => {
            if (resultado.fallos === 0 && !resultado.errorOrden) {
              this.terminarActualizado();
              return;
            }
            this.guardando = false;
            const partes: string[] = [];
            if (resultado.fallos > 0) {
              partes.push(resultado.fallos === 1 ? '1 foto no se pudo subir.' : `${numero(resultado.fallos)} fotos no se pudieron subir.`);
            }
            if (resultado.errorOrden) {
              partes.push('No se pudo guardar el orden de las fotos.');
            }
            this.avisos.error(
              [`El evento se ha guardado, pero ${partes.join(' ')}`, ...resultado.motivos, 'Reintenta cada foto o pulsa «Guardar cambios».'].join(' ')
            );
            this.cdr.markForCheck();
          });
        },
        error: (error) => {
          this.guardando = false;
          this.avisos.error(mensajeErrorSubida(error, 'No se ha podido actualizar el evento'));
          this.cdr.markForCheck();
        },
      });
  }

  private terminarActualizado(): void {
    this.guardando = false;
    this.avisos.exito('El evento ha sido actualizado correctamente.');
    if (!this.destruido) {
      void this.router.navigate(['/eventosListar']);
    }
  }

  /** «Cancelar»: no envía nada; como hoy, crear vuelve a Eventos y editar, atrás. */
  cancelar(): void {
    if (this.guardando) {
      return;
    }
    if (this.modo === 'crear') {
      void this.router.navigate(['/eventosListar']);
    } else {
      this.location.back();
    }
  }
}

function frase(n: number, singular: string, plural: string): string {
  return `${numero(n)} ${n === 1 ? singular : plural}`;
}

/** `esDispositivoIOS` de hoy. */
function esIOS(): boolean {
  const nav = globalThis.navigator;
  if (!nav) {
    return false;
  }
  const agente = nav.userAgent ?? '';
  return /iPad|iPhone|iPod/.test(agente) || (nav.platform === 'MacIntel' && nav.maxTouchPoints > 1);
}
