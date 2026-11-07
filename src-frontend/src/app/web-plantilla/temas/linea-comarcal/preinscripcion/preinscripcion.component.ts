import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
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
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';

import { paginaWeb } from '../../../comun/pagina-web';
import { borrarBorrador, guardarBorrador, guardarValor, leerBorrador, leerValor } from '../../../comun/preinscripcion/almacen';
import { enlaceDocumento, envioPreinscripcion, normasPreinscripcion } from '../../../comun/preinscripcion/envio';
import {
  DatosPreinscripcion,
  ErrorCampo,
  MAX_OBSERVACIONES,
  MENSAJES,
  diasDeParametro,
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
  private readonly consulta = toSignal(this.activa.queryParamMap, { requireSync: true });
  private readonly lienzo = viewChild<ElementRef<HTMLCanvasElement>>('lienzo');
  private readonly envio = envioPreinscripcion();
  private readonly cargaNormas = normasPreinscripcion();

  protected readonly t = TEXTOS_PREINSCRIPCION_LINEA_COMARCAL;
  protected readonly rellenar = rellenar;
  protected readonly maxObservaciones = MAX_OBSERVACIONES;
  protected readonly pagina = paginaWeb('preinscripcion', () => '/preinscripcion');

  protected readonly datos = signal<DatosPreinscripcion>(leerBorrador(this.almacen('sessionStorage'), CLAVE_BORRADOR_PREINSCRIPCION));
  protected readonly paso = signal<Paso>(1);
  protected readonly errores = signal<ErrorCampo[]>([]);
  /** Paso 2: ningún día marcado (aviso en el campo, sin resumen, como el boceto). */
  protected readonly sinDias = signal(false);
  protected readonly sedeMarcada = signal<string | null>(null);
  protected readonly diasMarcados = signal<number[]>([]);
  protected readonly firmado = signal(false);
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
    return salida ? casillasDia(salida, this.reloj()) : [];
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
    // El paso, al llegar y cada vez que cambia la dirección (como el boceto: se comprueba lo que falta).
    effect(() => {
      const sitio = this.pagina.sitio();
      const consulta = this.consulta();
      if (!sitio) {
        return;
      }
      untracked(() => this.resolverPaso(consulta.get('paso')));
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
    // La sede vista se recuerda (como la portada y la página de sede).
    effect(() => {
      const sede = this.sede();
      if (sede) {
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
          this.mostrarErrores([{ id: '', campo: 'envio', msg: estado.mensaje }]);
        }
      });
    });
  }

  // ------------------------------------------------------------------------------------------------------------
  // Navegación
  // ------------------------------------------------------------------------------------------------------------

  private resolverPaso(pedido: string | null): void {
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
    guardarValor(this.almacen('localStorage'), CLAVE_SEDE_RECORDADA, marcada.clave);
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
    this.diasMarcados.set([...fila.salida.dias]);
    this.ir(2, { grupo: String(fila.salida.grupo.id), dias: parametroDias(sede, fila.salida, fila.salida.dias) }, true);
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
    const dias = salida.dias.filter((d) => (d === numero ? marcado : this.diasMarcados().includes(d)));
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
    guardarBorrador(this.almacen('sessionStorage'), CLAVE_BORRADOR_PREINSCRIPCION, this.datos());
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

  private prepararLienzo(lienzo: HTMLCanvasElement): void {
    const r = lienzo.getBoundingClientRect();
    const dpr = this.documento.defaultView?.devicePixelRatio || 1;
    lienzo.width = Math.max(1, Math.round(r.width * dpr));
    lienzo.height = Math.max(1, Math.round(r.height * dpr));
    const ctx = lienzo.getContext('2d');
    if (ctx) {
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(0, 0, lienzo.width, lienzo.height);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.lineWidth = 2.25;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.strokeStyle = '#101418';
    }
    this.firmado.set(false);
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
    borrarBorrador(this.almacen('sessionStorage'), CLAVE_BORRADOR_PREINSCRIPCION);
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

  private limpiarErrores(): void {
    this.sinDias.set(false);
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

/** Si la salida es de esa edad. */
function avisoEdadVacio(salida: Salida, edad: number): boolean {
  return avisoEdad(salida, edad) === null;
}
