import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  ViewEncapsulation,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Params, Router, RouterLink, convertToParamMap } from '@angular/router';

import { EditorWebService } from '../../../comun/editor-web';
import { paginaWeb } from '../../../comun/pagina-web';
import { borrarBorrador, guardarBorrador, guardarValor, leerBorrador, leerValor } from '../../../comun/preinscripcion/almacen';
import { enlaceDocumento, envioPreinscripcion, normasPreinscripcion } from '../../../comun/preinscripcion/envio';
import {
  DATOS_VACIOS,
  DatosPreinscripcion,
  ErrorCampo,
  MAX_APELLIDOS,
  MAX_DIRECCION,
  MAX_EMAIL,
  MAX_FIRMANTE,
  MAX_NOMBRE,
  MAX_OBSERVACIONES,
  MAX_TELEFONO,
  MAX_TUTOR_NOMBRE,
  MENSAJES,
  diasDeParametro,
  diasElegibles,
  diasElegidos,
  edadTemporada,
  errorFecha,
  esMenor,
  fechaNacimiento,
  inicioTemporada,
  salidaDeParametros,
  sedeDeParametro,
  sinPlaza,
  solicitudDe,
  turnosElegidos,
  validarDatos,
} from '../../../comun/preinscripcion/preinscripcion';
import { RELOJ_WEB } from '../../../comun/reloj';
import { Salida } from '../../../comun/horario';
import type { EjemploPreinscripcion } from '../../../editor/casos-vista';
import { SinPublicarComponent } from '../../../paginas/sin-publicar.component';
import { CLAVE_SEDE_RECORDADA, claveSede } from '../contenido';
import { MarcoLineaComarcalComponent } from '../marco/marco.component';
import { TEXTOS_PREINSCRIPCION_LINEA_COMARCAL } from '../textos';
import {
  FilaGrupo,
  VistaFin,
  avisoEdad,
  casillasDia,
  claveSalida,
  estadoEdad,
  filasGrupo,
  opcionesSede,
  parametroDias,
  parametroSede,
  rellenar,
  textoDiasLista,
  textoGrupo,
  textoHorario,
  textoPlaza,
  textoResto,
  textoTarifa,
  vistaFin,
  vistaVacio,
} from './preinscripcion';

/** Borrador de la preinscripción en `sessionStorage` (la clave del boceto). */
export const CLAVE_BORRADOR_PREINSCRIPCION = 'linea-comarcal:preinscripcion';
/** El de la vista previa del panel: lo que se prueba ahí nunca pisa el borrador real. */
export const CLAVE_BORRADOR_PREINSCRIPCION_PREVIA = 'linea-comarcal:preinscripcion:vista-previa';

type Paso = 1 | 2 | 3 | 4;
type CampoTexto = Exclude<keyof DatosPreinscripcion, 'normas' | 'fotos' | 'discapacidad'>;

/**
 * Preinscripción del tema «La línea comarcal» (`/preinscripcion?sede=&grupo=&dias=`, boceto
 * `fase4-web/boceto-linea-comarcal-preinscripcion.html`): pueblo, grupo y datos con firma, y la confirmación. Como en el
 * boceto, el paso y lo elegido van en la dirección (`?paso=`) y lo escrito, en el borrador de `sessionStorage`; la
 * última sede, en `localStorage`. Con una sola sede no hay paso de pueblo.
 *
 * Los datos de sedes, grupos, turnos y plazas son los de `GET /api/publico/web` (los mismos turnos y la misma regla de
 * visibilidad que los públicos de preinscripción); las normas, las de la disciplina del grupo
 * (`/public/configuracion/{deporte}`, como Moiskimdo). La firma es dibujada y obligatoria (decisión 493).
 */
@Component({
  selector: 'app-lc-preinscripcion',
  standalone: true,
  imports: [MarcoLineaComarcalComponent, NgTemplateOutlet, RouterLink, SinPublicarComponent],
  templateUrl: './preinscripcion.component.html',
  styleUrl: './preinscripcion.component.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreinscripcionLineaComarcalComponent {
  private readonly reloj = inject(RELOJ_WEB);
  private readonly documento = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly titulo = inject(Title);
  private readonly activa = inject(ActivatedRoute);
  private readonly editor = inject(EditorWebService);
  private readonly direccion = toSignal(this.activa.queryParamMap, { requireSync: true });
  private readonly lienzo = viewChild<ElementRef<HTMLCanvasElement>>('lienzo');
  private readonly envio = envioPreinscripcion();
  private readonly cargaNormas = normasPreinscripcion();

  protected readonly t = TEXTOS_PREINSCRIPCION_LINEA_COMARCAL;
  protected readonly rellenar = rellenar;
  protected readonly maxObservaciones = MAX_OBSERVACIONES;
  /** El `maxlength` de cada campo de texto: las longitudes de la API. */
  protected readonly max = {
    nombre: MAX_NOMBRE,
    apellidos: MAX_APELLIDOS,
    direccion: MAX_DIRECCION,
    tutorNombre: MAX_TUTOR_NOMBRE,
    telefono: MAX_TELEFONO,
    email: MAX_EMAIL,
    firmante: MAX_FIRMANTE,
  } as const;
  protected readonly pagina = paginaWeb('preinscripcion', () => '/preinscripcion');

  /**
   * W12c-ter: en el editor de la web («Web · Contenido»), el paso de ejemplo que da la capa de edición, en memoria;
   * fuera del editor, siempre null y manda la dirección.
   */
  private readonly ejemplo = computed(() => {
    const sitio = this.pagina.sitio();
    return sitio ? this.editor.preinscripcion(sitio, this.reloj()) : null;
  });
  /** Los parámetros de la dirección o, en el editor, los del ejemplo (con la vista previa de la dirección). */
  private readonly consulta = computed(() => {
    const ejemplo = this.ejemplo();
    const direccion = this.direccion();
    if (!ejemplo) {
      return direccion;
    }
    const previa = direccion.get('vista-previa');
    return convertToParamMap(previa === null ? ejemplo.consulta : { ...ejemplo.consulta, 'vista-previa': previa });
  });

  /** Vista previa del panel (`?vista-previa=`, o la API la confirma): se usa el formulario, pero no se envía. */
  private readonly enPrevia = this.direccion().get('vista-previa') !== null;
  private readonly claveBorrador = this.enPrevia ? CLAVE_BORRADOR_PREINSCRIPCION_PREVIA : CLAVE_BORRADOR_PREINSCRIPCION;
  protected readonly vistaPrevia = computed(() => this.enPrevia || this.pagina.sitio()?.vistaPrevia === true);

  protected readonly datos = signal<DatosPreinscripcion>(leerBorrador(this.almacen('sessionStorage'), this.claveBorrador));
  protected readonly paso = signal<Paso>(1);
  protected readonly errores = signal<ErrorCampo[]>([]);
  /** Paso 2: ningún día marcado (aviso en el campo, sin resumen, como el boceto). */
  protected readonly sinDias = signal(false);
  protected readonly sedeMarcada = signal<string | null>(null);
  protected readonly diasMarcados = signal<number[]>([]);
  protected readonly firmado = signal(false);
  /** Error general del envío (400 de la API o vista previa): junto al botón, no en el resumen de los campos. */
  protected readonly errorEnvio = signal<string | null>(null);
  protected readonly fin = signal<(VistaFin & { documento: string }) | null>(null);

  protected readonly una = computed(() => this.pagina.sitio()?.sedes.length === 1);
  protected readonly sede = computed(() => {
    const sitio = this.pagina.sitio();
    if (!sitio) {
      return null;
    }
    return this.una() ? sitio.sedes[0] : sedeDeParametro(sitio.sedes, this.consulta().get('sede'));
  });
  private readonly salidaUrl = computed(() => {
    const sede = this.sede();
    return sede ? salidaDeParametros(sede, this.consulta().get('grupo'), diasDeParametro(this.consulta().get('dias'))) : null;
  });
  private readonly inicio = computed(() => inicioTemporada(this.pagina.sitio()?.temporada));
  protected readonly edad = computed(() => edadTemporada(this.datos(), this.inicio(), this.reloj()));
  protected readonly menor = computed(() => esMenor(this.datos(), this.reloj()));
  /** En el paso 2 un grupo que ya no es de su edad se quita (boceto); en el 3 se avisa. */
  protected readonly salida = computed(() => {
    const salida = this.salidaUrl();
    const edad = this.edad();
    if (!salida || (this.paso() === 2 && edad !== null && !avisoEdadVacio(salida, edad))) {
      return null;
    }
    return salida;
  });
  protected readonly dias = computed(() => (this.salida() ? this.diasMarcados() : []));
  protected readonly turnos = computed(() => {
    const salida = this.salida();
    return salida ? turnosElegidos(salida, this.dias()) : [];
  });
  protected readonly espera = computed(() => sinPlaza(this.turnos()));

  protected readonly pasos = computed(() => {
    const lista: [Paso, string][] = this.una()
      ? [[2, this.t.pasoGrupo], [3, this.t.pasoDatos]]
      : [[1, this.t.pasoPueblo], [2, this.t.pasoGrupo], [3, this.t.pasoDatos]];
    const indice = lista.findIndex(([p]) => p === this.paso());
    return {
      texto: rellenar(this.t.pasoNdeM, { n: indice + 1, total: lista.length }),
      estilo: { '--n': String(lista.length), '--avance': String(indice / (lista.length - 1)) },
      lista: lista.map(([, nombre], i) => ({ nombre, hecho: i < indice, ahora: i === indice })),
    };
  });
  protected readonly atras = computed(() => {
    const paso = this.paso();
    const consulta = this.previa();
    if (paso === 1 || (this.una() && paso === 2)) {
      return { ruta: '/', consulta, fragmento: 'linea' };
    }
    return { ruta: '/preinscripcion', consulta: this.consultaPara((paso - 1) as Paso), fragmento: undefined };
  });

  // Paso 1
  protected readonly opciones = computed(() => {
    const sitio = this.pagina.sitio();
    return sitio ? opcionesSede(sitio, this.reloj()) : [];
  });

  // Paso 2
  protected readonly filas = computed((): FilaGrupo[] | null => {
    const sitio = this.pagina.sitio();
    const sede = this.sede();
    const edad = this.edad();
    return sitio && sede && edad !== null ? filasGrupo(sitio, sede, edad, this.reloj()) : null;
  });
  protected readonly estadoEdad = computed(() => {
    const sede = this.sede();
    const edad = this.edad();
    const filas = this.filas();
    return sede && edad !== null && filas ? estadoEdad(sede, edad, filas.length) : null;
  });
  protected readonly vacio = computed(() => {
    const sitio = this.pagina.sitio();
    const sede = this.sede();
    const edad = this.edad();
    return sitio && sede && edad !== null && this.filas()?.length === 0 ? vistaVacio(sitio, sede, edad) : null;
  });
  protected readonly casillas = computed(() => {
    const salida = this.salida();
    const sitio = this.pagina.sitio();
    return sitio && salida ? casillasDia(sitio, salida, this.reloj()) : [];
  });
  /** El día que lleva el ancla `d-primero` (foco y error del campo): el primero que se puede elegir. */
  protected readonly primeraElegible = computed(() => this.casillas().find((c) => c.elegible)?.numero ?? null);
  /** Algún día de la salida elegida no se puede elegir (está lleno y otros tienen plaza). */
  protected readonly diasParciales = computed(() => {
    const salida = this.salida();
    return !!salida && diasElegibles(salida).length < salida.dias.length;
  });

  // Paso 3
  protected readonly avisoEdad = computed(() => (this.paso() === 3 ? avisoEdad(this.salidaUrl(), this.edad()) : null));
  protected readonly normas = this.cargaNormas.estado;
  protected readonly enviando = computed(() => this.envio.estado().estado === 'enviando');

  // Resumen lateral
  protected readonly resumen = computed(() => {
    const sitio = this.pagina.sitio();
    const salida = this.salida();
    const dias = this.dias();
    const edad = this.edad();
    return {
      edad: edad !== null ? rellenar(this.t.cumpleAnios, { n: edad }) : null,
      grupo: salida ? textoGrupo(salida) : null,
      horario: salida && dias.length ? textoHorario(salida, dias) : null,
      resto: salida ? textoResto(salida, dias) : '',
      plaza: sitio && salida ? textoPlaza(sitio, { sede: this.sede(), salida, dias }) : null,
      tarifa: sitio ? textoTarifa(sitio, this.reloj()) : null,
    };
  });

  private primeraVez = true;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.dejarDeObservar());
    // El paso, al llegar y cada vez que cambia la dirección (como el boceto: se comprueba lo que falta).
    effect(() => {
      const sitio = this.pagina.sitio();
      const consulta = this.consulta();
      const ejemplo = this.ejemplo();
      if (!sitio) {
        return;
      }
      untracked(() => (ejemplo ? this.verEjemplo(ejemplo) : this.resolverPaso(consulta.get('paso'))));
    });
    // Los días marcados, los de la dirección.
    effect(() => {
      const salida = this.salidaUrl();
      const pedidos = diasDeParametro(this.consulta().get('dias'));
      untracked(() => {
        this.diasMarcados.set(salida ? diasElegidos(salida, pedidos) : []);
        this.sinDias.set(false);
      });
    });
    // La sede vista se recuerda (como la portada y la página de sede); en el editor de la web no se toca el navegador.
    effect(() => {
      const sede = this.sede();
      if (sede && !this.editor.enEditor()) {
        untracked(() => guardarValor(this.almacen('localStorage'), CLAVE_SEDE_RECORDADA, claveSede(sede)));
      }
    });
    // Normas de la disciplina del grupo.
    effect(() => {
      const salida = this.salidaUrl();
      if (this.paso() === 3 && salida) {
        untracked(() => this.cargaNormas.cargar(salida.grupo.disciplina));
      }
    });
    // El lienzo de la firma, al aparecer.
    effect(() => {
      const lienzo = this.lienzo();
      if (lienzo) {
        untracked(() => afterNextRender(() => this.prepararLienzo(lienzo.nativeElement), { injector: this.injector }));
      }
    });
    // Resultado del envío.
    effect(() => {
      const estado = this.envio.estado();
      untracked(() => {
        if (estado.estado === 'hecho') {
          this.terminar(estado.respuesta);
        } else if (estado.estado === 'error') {
          this.mostrarErrorEnvio(estado.mensaje);
        }
      });
    });
  }

  // ------------------------------------------------------------------------------------------------------------
  // Navegación
  // ------------------------------------------------------------------------------------------------------------

  private resolverPaso(pedido: string | null): void {
    const sede = this.sede();
    const paso = this.pasoPara(pedido);
    const cambia = paso !== this.paso() || this.fin() !== null;
    this.paso.set(paso);
    if (paso === 1) {
      this.sedeMarcada.set(sede ? claveSede(sede) : leerValor(this.almacen('localStorage'), CLAVE_SEDE_RECORDADA));
    }
    if (cambia || this.primeraVez) {
      this.fin.set(null);
      this.envio.reiniciar();
      this.limpiarErrores();
      const enfocar = !this.primeraVez || !!pedido || !!this.consulta().get('sede');
      this.primeraVez = false;
      if (enfocar) {
        this.trasPintar(() => {
          this.documento.defaultView?.scrollTo?.(0, 0);
          this.enfocar('.p-preinscripcion .pregunta-titulo');
        });
      }
    }
  }

  /** El paso pedido, o el que toca con lo que falta (sede, grupo y fecha), como el boceto. */
  private pasoPara(pedido: string | null): Paso {
    const sede = this.sede();
    const una = this.una();
    let paso = Number(pedido) as Paso;
    if (!(paso >= 1 && paso <= 3)) {
      paso = sede && !una ? 2 : 1;
    }
    if (una && paso === 1) {
      paso = 2;
    }
    if (paso >= 2 && !sede) {
      paso = 1;
    }
    if (paso >= 3 && !this.salidaUrl()) {
      paso = 2;
    }
    if (paso === 3 && !fechaNacimiento(this.datos(), this.reloj())) {
      paso = 2;
    }
    return paso;
  }

  /**
   * W12c-ter: el paso de ejemplo del editor de la web, en memoria: sin tocar la dirección, sin guardar en el navegador,
   * sin mover el foco y sin enviar. El paso 4 pinta la confirmación con la respuesta simulada que da la capa.
   */
  private verEjemplo(ejemplo: EjemploPreinscripcion): void {
    this.datos.set({ ...DATOS_VACIOS, ...ejemplo.datos });
    const paso = this.pasoPara(String(Math.min(ejemplo.paso, 3)));
    this.paso.set(paso);
    this.fin.set(null);
    this.limpiarErrores();
    const sede = this.sede();
    const salida = this.salida();
    if (ejemplo.respuesta && paso === 3 && sede && salida) {
      const dias = diasElegidos(salida, diasDeParametro(ejemplo.consulta.dias));
      this.fin.set({
        ...vistaFin(ejemplo.respuesta, { sede, salida, dias }, this.datos(), this.menor()),
        documento: enlaceDocumento(ejemplo.respuesta),
      });
      this.paso.set(4);
    }
  }

  /** Los parámetros de la dirección para ir a un paso (con la vista previa, si la hay). */
  protected consultaPara(paso: Paso, cambios: { sede?: string | null; grupo?: string | null; dias?: string | null } = {}): Params {
    const sede = this.sede();
    const salida = this.salida();
    const actual: { sede: string | null; grupo: string | null; dias: string | null } = {
      sede: sede && !this.una() ? parametroSede(sede) : null,
      grupo: salida ? String(salida.grupo.id) : null,
      dias: sede && salida && this.dias().length ? parametroDias(sede, salida, this.dias()) : null,
    };
    const valores: Record<string, string | number | null> = {
      paso,
      ...actual,
      ...cambios,
      'vista-previa': this.consulta().get('vista-previa'),
    };
    if (this.una()) {
      valores['sede'] = null;
    }
    return Object.fromEntries(Object.entries(valores).filter(([, v]) => v !== null && v !== undefined));
  }

  private previa(): Params {
    const previa = this.consulta().get('vista-previa');
    return previa !== null ? { 'vista-previa': previa } : {};
  }

  private ir(paso: Paso, cambios: Parameters<PreinscripcionLineaComarcalComponent['consultaPara']>[1] = {}, reemplazar = false): void {
    void this.router.navigate(['/preinscripcion'], { queryParams: this.consultaPara(paso, cambios), replaceUrl: reemplazar });
  }

  // ------------------------------------------------------------------------------------------------------------
  // Paso 1: pueblo
  // ------------------------------------------------------------------------------------------------------------

  protected enviarPueblo(evento: Event): void {
    evento.preventDefault();
    const opciones = this.opciones();
    const marcada = opciones.find((o) => o.clave === this.sedeMarcada());
    if (!marcada) {
      this.mostrarErrores(opciones.length ? [{ id: `s-${opciones[0].clave}`, campo: 'sede', msg: MENSAJES.elegirSede }] : []);
      return;
    }
    if (!this.editor.enEditor()) {
      guardarValor(this.almacen('localStorage'), CLAVE_SEDE_RECORDADA, marcada.clave);
    }
    this.ir(2, { sede: marcada.parametro, grupo: null, dias: null });
  }

  // ------------------------------------------------------------------------------------------------------------
  // Paso 2: fecha, grupo y días
  // ------------------------------------------------------------------------------------------------------------

  protected elegir(fila: FilaGrupo): void {
    const sede = this.sede();
    if (!sede) {
      return;
    }
    this.limpiarErrores();
    const dias = diasElegibles(fila.salida);
    this.diasMarcados.set(dias);
    this.ir(2, { grupo: String(fila.salida.grupo.id), dias: parametroDias(sede, fila.salida, dias) }, true);
    this.trasPintar(() => this.enfocar('#campo-dias'));
  }

  protected esElegida(fila: FilaGrupo): boolean {
    const salida = this.salida();
    return !!salida && claveSalida(salida) === fila.clave;
  }

  protected marcarDia(numero: number, marcado: boolean): void {
    const salida = this.salida();
    const sede = this.sede();
    if (!salida || !sede) {
      return;
    }
    const dias = diasElegibles(salida).filter((d) => (d === numero ? marcado : this.diasMarcados().includes(d)));
    this.diasMarcados.set(dias);
    this.sinDias.set(!dias.length);
    if (dias.length) {
      this.ir(2, { dias: parametroDias(sede, salida, dias) }, true);
    }
  }

  protected verGrupos(): void {
    if (!this.comprobarFecha()) {
      return;
    }
    this.trasPintar(() => {
      const estado = this.documento.getElementById('edad-estado');
      if (typeof estado?.scrollIntoView === 'function') {
        estado.scrollIntoView({ block: 'start', behavior: this.reducido() ? 'auto' : 'smooth' });
      }
    });
  }

  private comprobarFecha(): boolean {
    const error = errorFecha(this.datos(), this.reloj());
    if (error) {
      this.mostrarErrores([error]);
      return false;
    }
    if (this.errores().some((e) => e.campo === 'fecha')) {
      this.limpiarErrores();
    }
    return true;
  }

  protected enviarGrupo(evento: Event): void {
    evento.preventDefault();
    const fecha = errorFecha(this.datos(), this.reloj());
    if (fecha) {
      this.mostrarErrores([fecha]);
    } else if (!this.salida()) {
      const primera = this.filas()?.find((f) => !this.esElegida(f));
      this.mostrarErrores([{ id: primera ? `g-${primera.clave}` : 'dia', campo: 'grupos', msg: MENSAJES.elegirGrupo }]);
    } else if (!this.dias().length) {
      this.mostrarErrores([{ id: 'd-primero', campo: 'dias', msg: MENSAJES.marcarDia }]);
    } else {
      this.ir(3);
    }
  }

  protected revisarFecha(evento: Event): void {
    evento.preventDefault();
    this.documento.getElementById('campo-fecha')?.scrollIntoView?.({ block: 'start' });
    this.documento.getElementById('dia')?.focus({ preventScroll: true });
  }

  // ------------------------------------------------------------------------------------------------------------
  // Paso 3: datos y firma
  // ------------------------------------------------------------------------------------------------------------

  protected valor(campo: CampoTexto): string {
    return this.datos()[campo];
  }

  protected actualizar<K extends keyof DatosPreinscripcion>(campo: K, valor: DatosPreinscripcion[K]): void {
    this.datos.update((d) => ({ ...d, [campo]: valor }));
    // W12c-ter: en el editor de la web no se guarda nada en el navegador.
    if (!this.editor.enEditor()) {
      guardarBorrador(this.almacen('sessionStorage'), this.claveBorrador, this.datos());
    }
  }

  protected escribir(campo: CampoTexto, evento: Event): void {
    this.actualizar(campo, (evento.target as HTMLInputElement).value);
  }

  protected enviarDatos(evento: Event): void {
    evento.preventDefault();
    if (this.enviando()) {
      return;
    }
    const errores = validarDatos(this.datos(), {
      ahora: this.reloj(),
      firmado: this.firmado(),
      normasListas: this.normas().estado === 'listo',
    });
    if (errores.length) {
      this.mostrarErrores(errores);
      return;
    }
    if (this.avisoEdad()) {
      this.limpiarErrores();
      this.documento.getElementById('campo-fecha')?.scrollIntoView?.({ block: 'start' });
      this.documento.querySelector<HTMLElement>('.p-preinscripcion .aviso-edad a')?.focus({ preventScroll: true });
      return;
    }
    const salida = this.salida();
    const lienzo = this.lienzo()?.nativeElement;
    if (!salida || !lienzo) {
      return;
    }
    this.limpiarErrores();
    if (this.vistaPrevia()) {
      this.mostrarErrorEnvio(this.t.vistaPreviaNoEnvia);
      return;
    }
    this.envio.enviar(
      solicitudDe(this.datos(), {
        deporte: salida.grupo.disciplina,
        turnoIds: this.turnos().map((t) => t.id),
        firmaBase64: lienzo.toDataURL('image/png'),
        ahora: this.reloj(),
      }),
    );
  }

  protected consultaElegirOtro(): Params {
    return this.consultaPara(2, { grupo: null, dias: null });
  }

  // Firma: lienzo con fondo blanco, como Moiskimdo (la API la pone en el PDF).
  private trazando = false;

  /** Los trazos de la firma, con los puntos en proporción del tamaño CSS del lienzo (0 a 1), para repintarlos. */
  private trazos: [number, number][][] = [];
  /** El ancho CSS con el que se dimensionó el lienzo (si no cambia, no se repinta). */
  private anchoLienzo = 0;
  private observador: ResizeObserver | null = null;
  private lienzoObservado: HTMLCanvasElement | null = null;

  /** Lienzo limpio (al aparecer y al borrar): sin trazos y sin firmar. */
  private prepararLienzo(lienzo: HTMLCanvasElement): void {
    this.trazos = [];
    this.trazando = false;
    this.dimensionarLienzo(lienzo);
    this.observar(lienzo);
  }

  /**
   * El mapa de bits a la medida del lienzo, con fondo blanco y el estilo del trazo, y los trazos guardados repintados a
   * esa medida. La densidad se limita a {@link DENSIDAD_MAX_FIRMA} y el ancho a {@link ANCHO_MAX_FIRMA} para que el PNG
   * quede muy por debajo del tope de la API (`firmaBase64`, 700.000 caracteres) también en tabletas anchas.
   */
  private dimensionarLienzo(lienzo: HTMLCanvasElement): void {
    const r = lienzo.getBoundingClientRect();
    const dpr = this.documento.defaultView?.devicePixelRatio || 1;
    const escala = Math.min(dpr, DENSIDAD_MAX_FIRMA, r.width > 0 ? ANCHO_MAX_FIRMA / r.width : DENSIDAD_MAX_FIRMA);
    lienzo.width = Math.max(1, Math.round(r.width * escala));
    lienzo.height = Math.max(1, Math.round(r.height * escala));
    this.anchoLienzo = r.width;
    const ctx = lienzo.getContext('2d');
    if (ctx) {
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(0, 0, lienzo.width, lienzo.height);
      // Los puntos van en píxeles CSS: la transformación lleva al mapa de bits (redondeado) en cada eje.
      const sx = r.width > 0 ? lienzo.width / r.width : escala;
      const sy = r.height > 0 ? lienzo.height / r.height : escala;
      ctx.setTransform(sx, 0, 0, sy, 0, 0);
      ctx.lineWidth = 2.25;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.strokeStyle = '#101418';
      for (const trazo of this.trazos) {
        if (trazo.length < 2) {
          continue;
        }
        ctx.beginPath();
        ctx.moveTo(trazo[0][0] * r.width, trazo[0][1] * r.height);
        for (const [x, y] of trazo.slice(1)) {
          ctx.lineTo(x * r.width, y * r.height);
        }
        ctx.stroke();
      }
      // Un trazo en curso sigue desde su último punto.
      const ultimo = this.trazando ? this.trazos.at(-1)?.at(-1) : undefined;
      if (ultimo) {
        ctx.beginPath();
        ctx.moveTo(ultimo[0] * r.width, ultimo[1] * r.height);
      }
    }
    this.firmado.set(this.trazos.some((t) => t.length > 1));
  }

  /** Al girar la tableta o cambiar el ancho de la ventana, el lienzo se redimensiona y la firma se repinta a escala. */
  private observar(lienzo: HTMLCanvasElement): void {
    if (this.lienzoObservado === lienzo) {
      return;
    }
    this.dejarDeObservar();
    const Observador = this.documento.defaultView?.ResizeObserver;
    if (typeof Observador !== 'function') {
      return;
    }
    this.lienzoObservado = lienzo;
    this.observador = new Observador(() => {
      const ancho = lienzo.getBoundingClientRect().width;
      if (lienzo.isConnected && ancho > 0 && ancho !== this.anchoLienzo) {
        this.dimensionarLienzo(lienzo);
      }
    });
    this.observador.observe(lienzo);
  }

  private dejarDeObservar(): void {
    this.observador?.disconnect();
    this.observador = null;
    this.lienzoObservado = null;
  }

  protected borrarFirma(): void {
    const lienzo = this.lienzo()?.nativeElement;
    if (lienzo) {
      this.prepararLienzo(lienzo);
    }
  }

  protected empezarTrazo(evento: PointerEvent): void {
    const lienzo = evento.currentTarget as HTMLCanvasElement;
    this.trazando = true;
    try {
      lienzo.setPointerCapture(evento.pointerId);
    } catch {
      /* puntero ya suelto */
    }
    const ctx = lienzo.getContext('2d');
    const [x, y] = this.punto(lienzo, evento);
    this.trazos.push([this.relativo(lienzo, x, y)]);
    ctx?.beginPath();
    ctx?.moveTo(x, y);
  }

  protected seguirTrazo(evento: PointerEvent): void {
    if (!this.trazando) {
      return;
    }
    const lienzo = evento.currentTarget as HTMLCanvasElement;
    const ctx = lienzo.getContext('2d');
    const [x, y] = this.punto(lienzo, evento);
    this.trazos.at(-1)?.push(this.relativo(lienzo, x, y));
    ctx?.lineTo(x, y);
    ctx?.stroke();
    if (!this.firmado()) {
      this.firmado.set(true);
    }
  }

  protected terminarTrazo(): void {
    this.trazando = false;
  }

  private punto(lienzo: HTMLCanvasElement, evento: PointerEvent): [number, number] {
    const r = lienzo.getBoundingClientRect();
    return [evento.clientX - r.left, evento.clientY - r.top];
  }

  /** El punto en proporción del tamaño CSS del lienzo. */
  private relativo(lienzo: HTMLCanvasElement, x: number, y: number): [number, number] {
    const r = lienzo.getBoundingClientRect();
    return [r.width > 0 ? x / r.width : 0, r.height > 0 ? y / r.height : 0];
  }

  // ------------------------------------------------------------------------------------------------------------
  // Paso 4: confirmación
  // ------------------------------------------------------------------------------------------------------------

  private terminar(respuesta: Parameters<typeof vistaFin>[0] & { tokenDocumento: string }): void {
    const sede = this.sede();
    const salida = this.salida();
    if (!sede || !salida) {
      return;
    }
    this.fin.set({
      ...vistaFin(respuesta, { sede, salida, dias: this.dias() }, this.datos(), this.menor()),
      documento: enlaceDocumento({ referencia: respuesta.referencia, tokenDocumento: respuesta.tokenDocumento }),
    });
    this.paso.set(4);
    borrarBorrador(this.almacen('sessionStorage'), this.claveBorrador);
    const escuela = this.pagina.sitio()?.escuela.nombre.trim();
    this.titulo.setTitle(escuela ? `${this.fin()!.titulo} · ${escuela}` : this.fin()!.titulo);
    this.trasPintar(() => {
      this.documento.defaultView?.scrollTo?.(0, 0);
      this.enfocar('#fin-titulo');
    });
  }

  // ------------------------------------------------------------------------------------------------------------
  // Errores: resumen arriba y mensaje en el campo
  // ------------------------------------------------------------------------------------------------------------

  protected error(campo: string): ErrorCampo | null {
    const error = this.errores().find((e) => e.campo === campo) ?? null;
    if (!error && campo === 'dias' && this.sinDias()) {
      return { id: 'd-primero', campo, msg: MENSAJES.marcarDia };
    }
    return error;
  }

  protected invalido(id: string): true | null {
    return this.errores().some((e) => e.id === id) ? true : null;
  }

  protected tituloErrores(): string {
    const n = this.errores().length;
    return n === 1 ? this.t.revisaUno : rellenar(this.t.revisaN, { n });
  }

  protected irAlCampo(evento: Event, id: string): void {
    const destino = this.documento.getElementById(id);
    if (!destino) {
      return;
    }
    evento.preventDefault();
    const caja = destino.closest('.campo, fieldset') ?? destino;
    caja.scrollIntoView?.({ block: 'start' });
    destino.focus({ preventScroll: true });
  }

  private mostrarErrores(lista: ErrorCampo[]): void {
    this.errores.set(lista);
    const actual = this.titulo.getTitle().replace(/^Error: /, '');
    this.titulo.setTitle(`${this.t.error}${actual}`);
    this.trasPintar(() => {
      const caja = this.documento.querySelector<HTMLElement>('.p-preinscripcion .errores');
      caja?.scrollIntoView?.({ block: 'start' });
      caja?.focus({ preventScroll: true });
    });
  }

  /** El error general del envío, junto al botón (`role="alert"`, se anuncia solo). */
  private mostrarErrorEnvio(mensaje: string): void {
    this.errorEnvio.set(mensaje);
    this.trasPintar(() => this.documento.getElementById('envio-error')?.scrollIntoView?.({ block: 'nearest' }));
  }

  private limpiarErrores(): void {
    this.sinDias.set(false);
    this.errorEnvio.set(null);
    if (this.errores().length) {
      this.errores.set([]);
    }
    const titulo = this.titulo.getTitle();
    if (titulo.startsWith(this.t.error)) {
      this.titulo.setTitle(titulo.slice(this.t.error.length));
    }
  }

  // ------------------------------------------------------------------------------------------------------------

  protected textoDias(dias: readonly number[]): string {
    return textoDiasLista(dias);
  }

  /** «Volver al inicio»: la portada (con la vista previa, si la hay). */
  protected consultaVolver(): Params {
    return this.previa();
  }

  private trasPintar(accion: () => void): void {
    afterNextRender(accion, { injector: this.injector });
  }

  private enfocar(selector: string): void {
    this.documento.querySelector<HTMLElement>(selector)?.focus({ preventScroll: true });
  }

  private reducido(): boolean {
    return this.documento.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? true;
  }

  private almacen(tipo: 'sessionStorage' | 'localStorage'): Storage | null {
    try {
      return this.documento.defaultView?.[tipo] ?? null;
    } catch {
      return null;
    }
  }
}

/** Densidad máxima del mapa de bits de la firma (más no se nota y engorda el PNG). */
const DENSIDAD_MAX_FIRMA = 2;
/** Ancho máximo del mapa de bits de la firma, en píxeles. */
const ANCHO_MAX_FIRMA = 1600;

/** Si la salida es de esa edad. */
function avisoEdadVacio(salida: Salida, edad: number): boolean {
  return avisoEdad(salida, edad) === null;
}
