import { HttpEventType } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Observable, Subscription, firstValueFrom } from 'rxjs';

import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { catalogoEsLegadoTaemoi } from '../../features/catalogo/legado-taemoi';
import { prepararFicheroParaSubida, reiniciarSelectorArchivo } from '../../utils/subida-archivos.util';

import { PermisosSesionService } from '../../servicios/authentication/permisos-sesion.service';
import { AcentoEscuelaDirective } from '../acento-escuela.directive';
import { AvisosService } from '../avisos/avisos.service';
import { BloqueErrorComponent } from '../carga/bloque-error.component';
import { BloqueVacioComponent } from '../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../carga/esqueleto.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { PanelLateralComponent } from '../panel-lateral/panel-lateral.component';
import {
  AVISO_AFORO_BAJO,
  AVISO_SEGUNDA_SEDE,
  AYUDA_SLUG,
  AYUDA_SLUG_EDITAR,
  CAMPOS_SEDE,
  CamposSede,
  CamposWebSede,
  ClaveCampo,
  ClaveCampoWeb,
  DefinicionCampo,
  FORMATOS_FOTO,
  FalloFoto,
  MAXIMOS_WEB,
  NumeroFoto,
  SUBIDA_FOTO_SEDE,
  Sede,
  SedeEditada,
  TEXTOS_FOTO,
  TEXTO_DESACTIVAR,
  TEXTO_UNICA_ACTIVA,
  activarCreaSegunda,
  altPorDefecto,
  ayudaAlt,
  campoWebDeError,
  camposDe,
  camposWebDe,
  cuerpoSede,
  cuerpoSedeWeb,
  esUnicaActiva,
  estadoHttp,
  falloAlElegir,
  lineaSecundaria,
  mensajeError,
  pesoFoto,
  sedeConSlug,
  seraSegundaSede,
  textoSlugRepetido,
  validarCampos,
  validarCamposWeb,
} from './sedes-vista';
import { SedesService, UrlsFotosSede } from './sedes.service';

type Estado = 'cargando' | 'listo' | 'error';

/** Orden de los campos de la web en el formulario (el foco va al primero con error). */
const CLAVES_WEB: readonly ClaveCampoWeb[] = ['slug', 'lugar', 'indicacionesAcceso', 'fotoAlt', 'foto2Alt'];

/** ¿Hay alguna foto elegida o quitada que enviar al guardar? */
function hayFotosPorEnviar(panel: { fotos: Record<NumeroFoto, { archivo: File | null; quitar: boolean }> }): boolean {
  return [panel.fotos[1], panel.fotos[2]].some((f) => !!f.archivo || f.quitar);
}

/** Una de las dos fotos de la sede en el formulario (W12g). Lo elegido o quitado se envía al guardar. */
interface FotoFormulario {
  /** La foto guardada (id de imagen de la sede), o null. */
  guardada: number | null;
  /** La foto elegida y aún sin subir, con su URL local para verla. */
  archivo: File | null;
  local: string | null;
  /** «Quitar» sobre la guardada: al guardar, `DELETE`. */
  quitar: boolean;
  fallo: FalloFoto | null;
  /** Porcentaje de la subida en curso, o null. */
  progreso: number | null;
}

/** El panel lateral abierto: `sede` null es «Nueva sede». */
interface PanelSede {
  sede: Sede | null;
  campos: CamposSede;
  errores: Partial<Record<ClaveCampo, string>>;
  /** W12g: el grupo «En la web» (solo escuelas con web por plantilla). */
  web: CamposWebSede;
  erroresWeb: Partial<Record<ClaveCampoWeb, string>>;
  fotos: Record<NumeroFoto, FotoFormulario>;
  /** Error que no es de un campo: se ve en el propio panel (el SweetAlert2 quedaría detrás del `<dialog>`). */
  error: string | null;
  enviando: boolean;
  cambiandoEstado: boolean;
}

/**
 * Sedes del panel nuevo (E9, pieza 9-3). Una fila por sede (nombre, localidad y teléfono, estado y editar); «Nueva sede» y
 * «Editar sede» en el panel lateral, y «Estado» (activar y desactivar) al final del panel al editar. `PUT /api/sedes/{id}`
 * reemplaza todos los datos editables: al editar se envía también el aforo por turno del formulario (pieza 9-7).
 * Tras un alta, una edición o un cambio de estado se descartan los permisos en caché (`variasSedes` y `sedes` de
 * `GET /api/auth/permisos`) para que el resto de pantallas se enteren. Solo propietario y gestión.
 */
@Component({
  selector: 'app-sedes-panel',
  standalone: true,
  imports: [
    AcentoEscuelaDirective,
    EsqueletoComponent,
    BloqueErrorComponent,
    BloqueVacioComponent,
    BotonOcupadoComponent,
    PanelLateralComponent,
  ],
  templateUrl: './sedes.component.html',
  styleUrls: ['./sedes.component.scss'],
})
export class SedesPanelComponent implements OnInit, OnDestroy {
  private readonly api = inject(SedesService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly catalogo = inject(CatalogoService);
  private lectura: Subscription | null = null;
  private lecturaFotos: Subscription | null = null;
  private destruido = false;

  readonly campos = CAMPOS_SEDE;
  readonly avisoSegundaSede = AVISO_SEGUNDA_SEDE;
  readonly textoUnicaActiva = TEXTO_UNICA_ACTIVA;
  readonly lineaSecundaria = lineaSecundaria;
  readonly maximosWeb = MAXIMOS_WEB;
  readonly textosFoto = TEXTOS_FOTO;
  readonly formatosFoto = FORMATOS_FOTO;
  readonly numerosFoto: readonly NumeroFoto[] = [1, 2];
  readonly ayudaSlug = AYUDA_SLUG;
  readonly ayudaSlugEditar = AYUDA_SLUG_EDITAR;
  readonly altPorDefecto = altPorDefecto;
  readonly ayudaAlt = ayudaAlt;
  /** «taekwondosierrasur.es/sedes/»: la web de la escuela se sirve en el mismo Host que el panel. */
  readonly prefijoSlug = `${globalThis.location?.host ?? ''}/sedes/`;
  /** URL de las fotos guardadas, para la miniatura. */
  urlsFotos: UrlsFotosSede = new Map();

  estado: Estado = 'cargando';
  sedes: Sede[] = [];
  panel: PanelSede | null = null;

  ngOnInit(): void {
    this.cargar();
    this.cargarUrlsFotos();
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.lectura?.unsubscribe();
    this.lecturaFotos?.unsubscribe();
    this.soltarLocales(this.panel);
  }

  /**
   * W12g: el grupo «En la web» solo sale en escuelas con web por plantilla. Con `legado` (Moiskimdo) o sin catálogo, el
   * formulario y el cuerpo enviado quedan como siempre (falla cerrado).
   */
  get webPlantilla(): boolean {
    const catalogo = this.catalogo.catalogo();
    return catalogo !== null && !catalogoEsLegadoTaemoi(catalogo);
  }

  private cargarUrlsFotos(): void {
    if (!this.webPlantilla) {
      return;
    }
    this.lecturaFotos?.unsubscribe();
    this.lecturaFotos = this.api.urlsFotos().subscribe((urls) => {
      this.urlsFotos = urls;
      this.cdr.detectChanges();
    });
  }

  // ------------------------------------------------------------------ lista

  /** `silencioso`: tras un cambio, la lista se refresca sin volver al esqueleto (y un fallo no la tira). */
  cargar(silencioso = false): void {
    this.lectura?.unsubscribe();
    if (!silencioso) {
      this.estado = 'cargando';
    }
    this.lectura = this.api.listar().subscribe({
      next: (sedes) => {
        this.sedes = sedes ?? [];
        this.estado = 'listo';
        this.cdr.detectChanges();
      },
      error: () => {
        if (!silencioso) {
          this.estado = 'error';
        }
        this.cdr.detectChanges();
      },
    });
  }

  // ------------------------------------------------------------------ panel «Nueva sede» y «Editar sede»

  abrirNueva(): void {
    // Con la lista en carga o en error no hay sedes que contar: no se sabría si la nueva es la segunda.
    if (this.estado !== 'listo') {
      return;
    }
    this.panel = this.panelNuevo(null);
  }

  abrirEditar(sede: Sede): void {
    this.panel = this.panelNuevo(sede);
  }

  private panelNuevo(sede: Sede | null): PanelSede {
    const foto = (guardada: number | null | undefined): FotoFormulario => ({
      guardada: guardada ?? null,
      archivo: null,
      local: null,
      quitar: false,
      fallo: null,
      progreso: null,
    });
    return {
      sede,
      campos: camposDe(sede),
      errores: {},
      web: camposWebDe(sede),
      erroresWeb: {},
      fotos: { 1: foto(sede?.fotoImagenId), 2: foto(sede?.foto2ImagenId) },
      error: null,
      enviando: false,
      cambiandoEstado: false,
    };
  }

  cerrarPanel(): void {
    if (this.panel?.enviando || this.panel?.cambiandoEstado) {
      return;
    }
    this.soltarLocales(this.panel);
    this.panel = null;
  }

  /** `aria-describedby` del control: su ayuda y su error, si los tiene. */
  descripcionCampo(campo: DefinicionCampo, panel: PanelSede): string | null {
    const ids = [
      campo.ayuda ? `sedes-ayuda-${campo.clave}` : '',
      panel.errores[campo.clave] ? `sedes-error-${campo.clave}` : '',
    ].filter((id) => id !== '');
    return ids.length ? ids.join(' ') : null;
  }

  /** Crear con exactamente una sede activa la convierte en la segunda. */
  get segundaSede(): boolean {
    return !!this.panel && !this.panel.sede && seraSegundaSede(this.sedes);
  }

  get textoGuardar(): string {
    if (this.panel?.sede) {
      return 'Guardar cambios';
    }
    return this.segundaSede ? 'Crear segunda sede' : 'Crear sede';
  }

  get textoGuardando(): string {
    return this.panel?.sede ? 'Guardando…' : 'Creando…';
  }

  /** La sede abierta tal como está en la lista (el estado se actualiza sin cerrar el panel). */
  get sedeAbierta(): Sede | null {
    const sede = this.panel?.sede;
    return sede ? (this.sedes.find((s) => s.id === sede.id) ?? sede) : null;
  }

  get unicaActiva(): boolean {
    const sede = this.sedeAbierta;
    return !!sede && esUnicaActiva(this.sedes, sede);
  }

  escribir(clave: ClaveCampo, evento: Event): void {
    if (!this.panel) {
      return;
    }
    this.panel.campos[clave] = (evento.target as HTMLInputElement).value;
    delete this.panel.errores[clave];
    this.panel.error = null;
  }

  // ------------------------------------------------------------------ W12g: grupo «En la web»

  escribirWeb(clave: ClaveCampoWeb, evento: Event): void {
    if (!this.panel) {
      return;
    }
    this.panel.web[clave] = (evento.target as HTMLInputElement | HTMLTextAreaElement).value;
    delete this.panel.erroresWeb[clave];
    this.panel.error = null;
  }

  /** `aria-describedby` de un campo de la web: su error (si lo tiene) y su ayuda. */
  descripcionWeb(clave: ClaveCampoWeb, panel: PanelSede): string {
    return [panel.erroresWeb[clave] ? `sedes-error-${clave}` : '', `sedes-ayuda-${clave}`].filter((id) => id !== '').join(' ');
  }

  longitud(valor: string): number {
    return [...valor].length;
  }

  /** La foto que se ve en el hueco: la elegida o la guardada (si no se ha quitado). */
  tieneFoto(foto: FotoFormulario): boolean {
    return !!foto.archivo || (foto.guardada !== null && !foto.quitar);
  }

  /** La miniatura: la elegida o la URL de la guardada (si la vista previa de la web la trae). */
  miniatura(numero: NumeroFoto, foto: FotoFormulario, panel: PanelSede): string | null {
    if (foto.local) {
      return foto.local;
    }
    const id = panel.sede?.id;
    return id === undefined ? null : (this.urlsFotos.get(id)?.[numero - 1] ?? null);
  }

  notaFoto(foto: FotoFormulario, panel: PanelSede): string {
    if (foto.progreso !== null) {
      return `Subiendo la foto… ${foto.progreso} %`;
    }
    if (foto.archivo) {
      return `${pesoFoto(foto.archivo.size)} · ${panel.sede ? 'se subirá al guardar los cambios' : 'se subirá al crear la sede'}`;
    }
    return 'Guardada';
  }

  elegirFoto(numero: NumeroFoto): void {
    this.host.nativeElement.querySelector<HTMLInputElement>(`#sedes-foto-${numero}`)?.click();
  }

  /** La foto elegida se valida aquí (JPEG, PNG o WebP de hasta 50 MB) y se sube al guardar. */
  async fotoElegida(numero: NumeroFoto, evento: Event): Promise<void> {
    const panel = this.panel;
    const entrada = evento.target as HTMLInputElement;
    const elegido = entrada?.files?.[0];
    if (!panel || !elegido) {
      return;
    }
    const preparado = await prepararFicheroParaSubida(elegido, SUBIDA_FOTO_SEDE);
    reiniciarSelectorArchivo(entrada);
    if (this.panel !== panel || this.destruido) {
      return;
    }
    const foto = panel.fotos[numero];
    if (!preparado.ok) {
      foto.fallo = falloAlElegir(preparado.motivo, elegido);
      this.cdr.detectChanges();
      return;
    }
    this.soltarLocal(foto);
    foto.fallo = null;
    foto.archivo = preparado.file;
    foto.local = URL.createObjectURL(preparado.file);
    this.cdr.detectChanges();
  }

  /** «Quitar»: el hueco se queda sin foto; si había una guardada, se borra al guardar. */
  quitarFoto(numero: NumeroFoto): void {
    const foto = this.panel?.fotos[numero];
    if (!foto) {
      return;
    }
    this.soltarLocal(foto);
    foto.archivo = null;
    foto.quitar = foto.guardada !== null;
    foto.fallo = null;
  }

  cerrarFallo(numero: NumeroFoto): void {
    const foto = this.panel?.fotos[numero];
    if (foto) {
      foto.fallo = null;
    }
  }

  private soltarLocal(foto: FotoFormulario): void {
    if (foto.local) {
      URL.revokeObjectURL(foto.local);
      foto.local = null;
    }
  }

  private soltarLocales(panel: PanelSede | null): void {
    if (panel) {
      this.soltarLocal(panel.fotos[1]);
      this.soltarLocal(panel.fotos[2]);
    }
  }

  guardar(evento?: Event): void {
    evento?.preventDefault();
    const panel = this.panel;
    if (!panel || panel.enviando || panel.cambiandoEstado) {
      return;
    }
    const web = this.webPlantilla;
    const edicion = this.sedeAbierta;
    panel.errores = validarCampos(panel.campos);
    panel.erroresWeb = web ? validarCamposWeb(panel.web, this.sedes, edicion?.id ?? null) : {};
    const primero = this.campos.find((c) => panel.errores[c.clave]);
    if (primero) {
      this.enfocar(primero.clave);
      return;
    }
    const primeroWeb = CLAVES_WEB.find((c) => panel.erroresWeb[c]);
    if (primeroWeb) {
      this.enfocar(primeroWeb);
      return;
    }
    panel.enviando = true;
    panel.error = null;
    // Con `legado`, el cuerpo de siempre: sin los campos de la web (el servidor los conserva).
    const cuerpo = web ? cuerpoSedeWeb(panel.campos, panel.web) : cuerpoSede(panel.campos);
    const peticion: Observable<SedeEditada> = edicion ? this.api.editar(edicion.id, cuerpo) : this.api.crear(cuerpo);
    // Sin cancelar al salir: una escritura en vuelo sigue su curso.
    peticion.subscribe({
      next: ({ turnosSobreAforo, ...respuesta }) => {
        // La sede cambió el nombre o apareció: los permisos en caché (`sedes`, `variasSedes`) ya no valen.
        this.permisos.reiniciar();
        if (this.destruido) {
          return;
        }
        const terminar = (sede: Sede, fallos: number): void => {
          this.incorporar(sede);
          if (fallos > 0) {
            this.fotosFallidas(panel, sede, !edicion, fallos);
            return;
          }
          this.soltarLocales(panel);
          this.panel = null;
          this.avisos.exito(edicion ? 'Cambios guardados.' : 'Sede creada.');
          if (turnosSobreAforo) {
            this.avisos.info(AVISO_AFORO_BAJO);
          }
          this.cargar(true);
          if (web) {
            this.cargarUrlsFotos();
          }
        };
        if (!web || !hayFotosPorEnviar(panel)) {
          terminar(respuesta, 0);
          return;
        }
        void this.enviarFotos(panel, respuesta).then(({ sede, fallos }) => {
          if (!this.destruido) {
            terminar(sede, fallos);
          }
        });
      },
      error: (error) => {
        if (this.destruido) {
          return;
        }
        panel.enviando = false;
        const estado = estadoHttp(error);
        const campoWeb = web && (estado === 400 || estado === 409) ? campoWebDeError(mensajeError(error, '')) : null;
        if (campoWeb) {
          panel.erroresWeb = {
            [campoWeb]:
              campoWeb === 'slug' && estado === 409
                ? textoSlugRepetido(sedeConSlug(this.sedes, panel.web.slug, edicion?.id ?? null))
                : mensajeError(error, ''),
          };
          this.enfocar(campoWeb);
          return;
        }
        if (estadoHttp(error) === 409) {
          panel.errores = { nombre: mensajeError(error, 'Ya hay una sede con ese nombre.') };
          this.cdr.detectChanges();
          this.enfocar('nombre');
          return;
        }
        panel.error = mensajeError(error, 'No se pudo guardar la sede.');
        this.cdr.detectChanges();
      },
    });
  }

  /** La sede que devolvió el servidor entra en la lista ya, sin esperar a la recarga: si esta falla, no queda vieja. */
  private incorporar(sede: Sede): void {
    const existe = this.sedes.some((s) => s.id === sede.id);
    this.sedes = existe ? this.sedes.map((s) => (s.id === sede.id ? sede : s)) : [...this.sedes, sede];
  }

  /**
   * W12g: tras guardar los datos, las fotos de una en una (subir la elegida o borrar la quitada). Cada fallo se queda en
   * su hueco con la foto elegida, para reintentarlo con «Guardar cambios».
   */
  private async enviarFotos(panel: PanelSede, sede: Sede): Promise<{ sede: Sede; fallos: number }> {
    let actual = sede;
    let fallos = 0;
    for (const numero of this.numerosFoto) {
      const foto = panel.fotos[numero];
      if (!foto.archivo && !(foto.quitar && foto.guardada !== null)) {
        continue;
      }
      foto.fallo = null;
      try {
        if (foto.archivo) {
          actual = await this.subir(actual.id, numero, foto);
          this.soltarLocal(foto);
          foto.archivo = null;
        } else {
          actual = await firstValueFrom(this.api.quitarFoto(actual.id, numero));
          foto.quitar = false;
        }
        foto.guardada = (numero === 1 ? actual.fotoImagenId : actual.foto2ImagenId) ?? null;
      } catch (error) {
        fallos++;
        foto.progreso = null;
        foto.fallo = {
          titulo: foto.archivo ? 'No se pudo subir la foto' : 'No se pudo quitar la foto',
          texto: mensajeError(error, 'Vuelve a intentarlo con «Guardar cambios».'),
        };
      }
      this.cdr.detectChanges();
    }
    return { sede: actual, fallos };
  }

  private subir(id: number, numero: NumeroFoto, foto: FotoFormulario): Promise<Sede> {
    foto.progreso = 0;
    this.cdr.detectChanges();
    return new Promise<Sede>((resolver, rechazar) => {
      this.api.subirFoto(id, numero, foto.archivo as File).subscribe({
        next: (evento) => {
          if (evento.type === HttpEventType.UploadProgress && evento.total) {
            foto.progreso = Math.min(100, Math.round((evento.loaded / evento.total) * 100));
            this.cdr.detectChanges();
          } else if (evento.type === HttpEventType.Response) {
            foto.progreso = null;
            if (evento.body) {
              resolver(evento.body);
            } else {
              rechazar(null);
            }
          }
        },
        error: rechazar,
      });
    });
  }

  /** Alguna foto no se pudo enviar: el panel sigue abierto, ya como «Editar sede», para reintentarlo sin duplicar la sede. */
  private fotosFallidas(panel: PanelSede, sede: Sede, creada: boolean, fallos: number): void {
    panel.sede = sede;
    panel.enviando = false;
    const fotos = fallos === 1 ? 'una foto no se pudo guardar' : 'las fotos no se pudieron guardar';
    this.avisos.error(creada ? `La sede se ha creado, pero ${fotos}.` : `Los cambios se han guardado, pero ${fotos}.`);
    this.cargar(true);
    this.cdr.detectChanges();
  }

  private enfocar(clave: ClaveCampo | ClaveCampoWeb): void {
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`#sedes-campo-${clave}`)?.focus();
  }

  // ------------------------------------------------------------------ activar y desactivar

  async desactivar(): Promise<void> {
    const sede = this.sedeAbierta;
    const panel = this.panel;
    if (!sede || !panel || !sede.activa || esUnicaActiva(this.sedes, sede) || panel.cambiandoEstado) {
      return;
    }
    const resultado = await this.confirmacion.confirmar<Sede>({
      titulo: `¿Desactivar ${sede.nombre}?`,
      mensaje: TEXTO_DESACTIVAR,
      accion: 'Desactivar',
      accionOcupada: 'Desactivando…',
      nombre: 'desactivar-sede',
      ejecutar: () => this.api.cambiarActiva(sede.id, false),
    });
    if (!resultado.confirmado) {
      return;
    }
    if (this.destruido) {
      this.permisos.reiniciar();
      return;
    }
    if (resultado.error) {
      this.avisos.error(mensajeError(resultado.error, 'No se pudo desactivar la sede.'));
      this.cdr.detectChanges();
      return;
    }
    this.estadoCambiado(resultado.respuesta ?? { ...sede, activa: false }, 'Sede desactivada.');
  }

  async activar(): Promise<void> {
    const sede = this.sedeAbierta;
    const panel = this.panel;
    if (!sede || !panel || sede.activa || panel.cambiandoEstado) {
      return;
    }
    if (activarCreaSegunda(this.sedes, sede)) {
      const resultado = await this.confirmacion.confirmar<Sede>({
        titulo: `¿Activar ${sede.nombre}?`,
        mensaje: AVISO_SEGUNDA_SEDE,
        accion: 'Activar',
        accionOcupada: 'Activando…',
        nombre: 'activar-segunda-sede',
        ejecutar: () => this.api.cambiarActiva(sede.id, true),
      });
      if (!resultado.confirmado) {
        return;
      }
      if (this.destruido) {
        this.permisos.reiniciar();
        return;
      }
      if (resultado.error) {
        this.avisos.error(mensajeError(resultado.error, 'No se pudo activar la sede.'));
        this.cdr.detectChanges();
        return;
      }
      this.estadoCambiado(resultado.respuesta ?? { ...sede, activa: true }, 'Sede activada.');
      return;
    }
    panel.cambiandoEstado = true;
    this.api.cambiarActiva(sede.id, true).subscribe({
      next: (respuesta) => {
        panel.cambiandoEstado = false;
        if (this.destruido) {
          this.permisos.reiniciar();
          return;
        }
        this.estadoCambiado(respuesta, 'Sede activada.');
      },
      error: (error) => {
        if (this.destruido) {
          return;
        }
        panel.cambiandoEstado = false;
        this.avisos.error(mensajeError(error, 'No se pudo activar la sede.'));
        this.cdr.detectChanges();
      },
    });
  }

  /** Alta o cambio de estado hecho: la lista se pone al día, los permisos en caché se descartan y se avisa. */
  private estadoCambiado(sede: Sede, aviso: string): void {
    this.sedes = this.sedes.map((s) => (s.id === sede.id ? { ...s, ...sede } : s));
    this.permisos.reiniciar();
    this.avisos.exito(aviso);
    this.cdr.detectChanges();
    this.cargar(true);
  }
}
