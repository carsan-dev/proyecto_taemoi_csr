import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { SearchableSelectDirective } from '../../directives/searchable-select.directive';
import { RolFamiliar } from '../../enums/rol-familiar';
import { cuantiaEstandar } from '../../features/catalogo/catalogo-reglas';
import { CatalogoTarifa } from '../../features/catalogo/catalogo.models';
import { CatalogoService } from '../../features/catalogo/catalogo.service';
import { PermisosSesionService, SedeVisible } from '../../servicios/authentication/permisos-sesion.service';
import { calcularCategoriaPorEdad } from '../../utilities/categoria-por-edad';
import { getGradoNombreParaDeporte } from '../../utilities/grado-colors';
import { SUBIDA_IMAGEN, prepararFicheroParaSubida, reiniciarSelectorArchivo } from '../../utils/subida-archivos.util';
import { AcentoEscuelaActual, AcentoEscuelaDirective } from '../acento-escuela.directive';
import { iniciales } from '../alumnos/alumno-vista';
import { AvisosService } from '../avisos/avisos.service';
import { BotonOcupadoComponent } from '../carga/boton-ocupado.component';
import { ConfirmacionService } from '../dialogo/confirmacion.service';
import { eur } from '../formato/formato-es';
import { variablesDelTema } from '../tema-escuela';
import { AltaAlumnoApiService } from './alta-alumno-api.service';
import {
  CampoDeporte,
  CampoPersona,
  ETIQUETAS_DEPORTE,
  ETIQUETAS_PERSONA,
  MAX_DEPORTES,
  ORDEN_DEPORTE,
  ORDEN_PERSONA,
  cuerpoAlta,
  destinoErrorServidor,
  edadEnAnios,
  esMenorDeEdad,
  fechaAltaNoAnteriorANacimiento,
  fechaHoy,
  mensajesDeporte,
  mensajesPersona,
  textoAnadirDeporte,
  textoEdad,
  textoErrorAlta,
  tituloFaltan,
} from './alta-alumno-vista';

interface ErrorResumen {
  id: string;
  etiqueta: string;
  mensaje: string;
}

/**
 * «Dar de alta un alumno» del panel nuevo (8b-3l, P1 de la decisión 285): una columna por bloques (Datos personales,
 * Contacto, Responsable legal, Deportes, Foto y permisos) con «obligatorio» u «opcional» en cada etiqueta, como Editar
 * evento; desde 1280 px, el resumen del alta fijo a la derecha (380 px) y, por debajo, al final. Cada deporte es un
 * sub-bloque («Taekwondo · amarillo», «Quitar») y «Añadir otro deporte (1 de 4)» dice el límite. La edad y la categoría
 * salen junto a la fecha de nacimiento y, con menos de 18 años, el responsable legal lo recomienda.
 *
 * Añadidos de la decisión 285: «Sede» (obligatoria con varias sedes; con una, no se pide), «fecha de alta ≥ fecha de
 * nacimiento» y «Dar de alta» siempre activo: con errores no envía, enseña «Faltan N datos» con enlaces a cada campo y
 * deja cada mensaje bajo su campo. Al cancelar con datos, «¿Salir sin dar de alta?».
 *
 * Las MISMAS reglas y el MISMO `POST /api/alumnos/crear` que `CrearAlumnoComponent` (validadores, reinicios al cambiar de
 * deporte o de tarifa, cuantía, licencia y competición, multipart con la foto opcional, aviso «Alumno creado con N
 * deporte(s)» y salto a la ficha), sin SweetAlert2. Va en su chunk detrás de `disenoNuevoGuard`.
 */
@Component({
  selector: 'app-alta-alumno',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, SearchableSelectDirective, AcentoEscuelaDirective, BotonOcupadoComponent],
  templateUrl: './alta-alumno.component.html',
  styleUrls: ['./alta-alumno.component.scss'],
})
export class AltaAlumnoComponent implements OnInit, OnDestroy {
  readonly catalogo = inject(CatalogoService);
  private readonly api = inject(AltaAlumnoApiService);
  private readonly permisos = inject(PermisosSesionService);
  private readonly router = inject(Router);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly fb = inject(FormBuilder);
  private readonly temaActual = inject(AcentoEscuelaActual);
  /** La lista flotante del buscador va al `body`: lleva el tema y el acento de la escuela (8b-C). */
  readonly variablesDesplegable = computed(() => variablesDelTema(this.temaActual.aplicado()));

  readonly rolesFamiliares: RolFamiliar[] = Object.values(RolFamiliar);
  readonly categorias = ['Infantil', 'Precadete', 'Cadete', 'Junior', 'Senior'];
  readonly etiquetasPersona = ETIQUETAS_PERSONA;
  readonly etiquetasDeporte = ETIQUETAS_DEPORTE;
  readonly maxDeportes = MAX_DEPORTES;
  readonly eur = eur;

  form!: FormGroup;
  /** Grados de `GET /grados` y códigos que admite la edad (`GET /grados/disponibles/{fecha}`), como hoy. */
  todosLosGrados: any[] = [];
  tiposGrado: string[] = [];

  /** Sedes activas de la escuela; «Sede» solo se pide si hay más de una (P-1). */
  sedes: SedeVisible[] = [];
  pedirSede = false;

  imagen: File | null = null;
  imagenPreview: string | null = null;
  arrastrando = false;

  enviando = false;
  /** Se pulsó «Dar de alta» con errores: se ven el resumen y los mensajes de todos los campos. */
  intentado = false;
  /** Error del servidor sin campo propio: va en el resumen. */
  errorGeneral: string | null = null;

  private readonly suscripciones = new Subscription();
  private destruido = false;

  get deportes(): FormArray<FormGroup> {
    return this.form.get('deportesInicial') as FormArray<FormGroup>;
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      nombre: ['', Validators.required],
      apellidos: ['', Validators.required],
      fechaNacimiento: ['', Validators.required],
      nif: ['', [Validators.pattern('^[0-9]{8}[A-Za-z]$')]],
      sede: [''],
      direccion: ['', Validators.required],
      telefono: ['', [Validators.required, Validators.pattern('^[0-9]+$'), Validators.maxLength(9)]],
      telefono2: ['', [Validators.pattern('^[0-9]+$'), Validators.maxLength(9)]],
      email: ['', [Validators.required, Validators.email]],
      responsableLegalNombre: ['', Validators.maxLength(180)],
      responsableLegalNif: ['', [Validators.pattern('^[0-9XYZxyz][0-9]{7}[A-Za-z]$'), Validators.maxLength(9)]],
      autorizacionWeb: [true, Validators.required],
      rolFamiliar: [{ value: RolFamiliar.NINGUNO, disabled: true }],
      grupoFamiliar: [{ value: '', disabled: true }],
      tieneDiscapacidad: [false],
      deportesInicial: this.fb.array([this.nuevoDeporte()], Validators.required),
    });

    this.suscripciones.add(
      this.form.get('fechaNacimiento')!.valueChanges.subscribe((valor: string) => {
        if (valor) {
          this.pedirGradosDisponibles(valor);
          this.actualizarCategoriasPorEdad();
        }
        // P-2: la fecha de alta de cada deporte se vuelve a comprobar con la nueva fecha de nacimiento.
        this.deportes.controls.forEach((d) => d.get('fechaAlta')?.updateValueAndValidity({ emitEvent: false }));
      })
    );
    this.suscripciones.add(
      this.api.grados().subscribe({
        next: (grados) => {
          this.todosLosGrados = Array.isArray(grados) ? grados : [];
          this.cdr.markForCheck();
        },
        error: () => this.avisos.error('No se pudieron cargar los grados'),
      })
    );
    this.suscripciones.add(
      this.permisos.obtener().subscribe({
        next: (p) => {
          this.sedes = (p?.sedes ?? []).filter((s) => s.activa !== false);
          this.pedirSede = !!p?.variasSedes && this.sedes.length > 1;
          const control = this.form.get('sede')!;
          control.setValidators(this.pedirSede ? Validators.required : null);
          control.updateValueAndValidity({ emitEvent: false });
          this.cdr.markForCheck();
        },
        // Sin permisos no se pide: el servidor decide la sede, como hoy.
        error: () => (this.pedirSede = false),
      })
    );
  }

  ngOnDestroy(): void {
    this.destruido = true;
    this.suscripciones.unsubscribe();
    this.soltarPreview();
  }

  // ------------------------------------------------------------------ deportes

  private nuevoDeporte(): FormGroup {
    const hoy = fechaHoy();
    return this.fb.group({
      deporte: ['', Validators.required],
      grado: [''],
      fechaGrado: [hoy],
      fechaAlta: [hoy, [Validators.required, fechaAltaNoAnteriorANacimiento()]],
      fechaAltaInicial: [''],
      tipoTarifa: ['', Validators.required],
      cuantiaTarifa: [null],
      rolFamiliar: [{ value: '', disabled: true }],
      grupoFamiliar: [{ value: '', disabled: true }],
      categoria: [''],
      competidor: [false],
      fechaAltaCompeticion: [''],
      fechaAltaCompetidorInicial: [''],
      peso: [null],
      fechaPeso: [hoy],
      tieneLicencia: [false],
      numeroLicencia: [null],
      fechaLicencia: [hoy],
    });
  }

  get textoAnadir(): string {
    return textoAnadirDeporte(this.deportes.length);
  }

  anadirDeporte(): void {
    if (this.deportes.length >= MAX_DEPORTES) {
      return;
    }
    this.deportes.push(this.nuevoDeporte());
    this.form.markAsDirty();
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`#alta-deporte-${this.deportes.length - 1}`)?.focus();
  }

  quitarDeporte(i: number): void {
    if (this.deportes.length <= 1) {
      this.avisos.info('Un alumno debe tener al menos 1 deporte');
      return;
    }
    this.deportes.removeAt(i);
    this.form.markAsDirty();
  }

  valorDeporte(i: number, campo: string): any {
    return this.deportes.at(i)?.get(campo)?.value;
  }

  /** «Taekwondo · amarillo»; sin deporte, «Deporte N». */
  cabeceraDeporte(i: number): string {
    const codigo = this.valorDeporte(i, 'deporte');
    if (!codigo) {
      return `Deporte ${i + 1}`;
    }
    const nombre = this.catalogo.etiquetaDisciplina(codigo);
    const grado = getGradoNombreParaDeporte(this.valorDeporte(i, 'grado'), codigo);
    return grado ? `${nombre} · ${grado.toLocaleLowerCase('es-ES')}` : nombre;
  }

  gradosDe(i: number): any[] {
    const deporte = this.valorDeporte(i, 'deporte');
    if (!deporte || !this.catalogo.tieneGrados(deporte)) {
      return [];
    }
    const deLaDisciplina = new Set<string>(this.catalogo.gradosSeleccionables(deporte).map((g) => g.codigo));
    let grados = this.todosLosGrados.filter((g) => deLaDisciplina.has(g.tipoGrado));
    if (this.valor('fechaNacimiento') && this.tiposGrado.length > 0) {
      grados = grados.filter((g) => this.tiposGrado.includes(g.tipoGrado));
    }
    return grados;
  }

  nombreGrado(grado: any, i: number): string {
    return getGradoNombreParaDeporte(grado?.tipoGrado, this.valorDeporte(i, 'deporte'));
  }

  tarifasDe(i: number): CatalogoTarifa[] {
    const deporte = this.valorDeporte(i, 'deporte');
    return deporte ? this.catalogo.tarifasDe(deporte) : [];
  }

  private tarifaDe(i: number): CatalogoTarifa | undefined {
    return this.catalogo.tarifa(this.valorDeporte(i, 'tipoTarifa'));
  }

  pideRol(i: number): boolean {
    return this.tarifaDe(i)?.requiereRolFamiliar === true;
  }

  pideGrupo(i: number): boolean {
    return this.tarifaDe(i)?.requiereGrupoFamiliar === true;
  }

  tieneGrados(i: number): boolean {
    return this.catalogo.tieneGrados(this.valorDeporte(i, 'deporte'));
  }

  pideLicencia(i: number): boolean {
    return this.catalogo.requiereLicencia(this.valorDeporte(i, 'deporte'));
  }

  permiteCompetir(i: number): boolean {
    return this.catalogo.permiteCompeticion(this.valorDeporte(i, 'deporte'));
  }

  /** Competidor se puede marcar: la disciplina compite y no pide licencia o ya la tiene (como hoy). */
  puedeCompetir(i: number): boolean {
    return this.permiteCompetir(i) && (!this.pideLicencia(i) || !!this.valorDeporte(i, 'tieneLicencia'));
  }

  /** «Precio de la tarifa: 30,00 €. Puedes cambiarlo.» */
  notaCuantia(i: number): string {
    const tarifa = this.tarifaDe(i);
    if (!tarifa) {
      return 'Se rellena con el precio de la tarifa. Puedes cambiarlo.';
    }
    const precio = cuantiaEstandar(tarifa, this.valorDeporte(i, 'rolFamiliar') || undefined);
    return precio === null || precio === undefined ? 'Puedes cambiarlo.' : `Precio de la tarifa: ${eur(Number(precio))}. Puedes cambiarlo.`;
  }

  /** Al cambiar de deporte: la tarifa que no vale, el grado y la fecha de grado, la licencia y la competición (hoy). */
  alCambiarDeporte(i: number): void {
    const d = this.deportes.at(i);
    const tarifa = d.get('tipoTarifa')!;
    if (tarifa.value && !this.tarifasDe(i).some((t) => t.codigo === tarifa.value)) {
      tarifa.setValue('');
    }
    const grado = d.get('grado')!;
    const fechaGrado = d.get('fechaGrado')!;
    if (this.tieneGrados(i)) {
      grado.setValidators(Validators.required);
      fechaGrado.setValidators(Validators.required);
    } else {
      grado.clearValidators();
      grado.setValue('');
      fechaGrado.clearValidators();
      fechaGrado.setValue('');
    }
    grado.updateValueAndValidity();
    fechaGrado.updateValueAndValidity();
    if (!this.pideLicencia(i)) {
      d.patchValue({ tieneLicencia: false, numeroLicencia: null, fechaLicencia: fechaHoy() }, { emitEvent: false });
    }
    if (!this.permiteCompetir(i)) {
      d.patchValue(
        { competidor: false, categoria: '', fechaAltaCompeticion: '', fechaAltaCompetidorInicial: '', peso: null },
        { emitEvent: false }
      );
    }
    this.limpiarErrorServidor(d.get('deporte'));
  }

  /** Al cambiar de tarifa: la cuantía de la tarifa y el rol o el grupo familiar según lo que pida (hoy). */
  alCambiarTarifa(i: number): void {
    const d = this.deportes.at(i);
    if (!d.get('tipoTarifa')!.value) {
      return;
    }
    const rol = d.get('rolFamiliar')!;
    const grupo = d.get('grupoFamiliar')!;
    d.get('cuantiaTarifa')!.setValue(cuantiaEstandar(this.tarifaDe(i), rol.value || undefined));
    if (this.pideRol(i)) {
      rol.setValidators(Validators.required);
      rol.enable();
      grupo.clearValidators();
      grupo.setValue('');
      grupo.disable();
    } else if (this.pideGrupo(i)) {
      grupo.setValidators(Validators.required);
      grupo.enable();
      rol.clearValidators();
      rol.setValue('');
      rol.disable();
    } else {
      rol.clearValidators();
      rol.setValue('');
      rol.disable();
      grupo.clearValidators();
      grupo.setValue('');
      grupo.disable();
    }
    rol.updateValueAndValidity();
    grupo.updateValueAndValidity();
  }

  /** Al cambiar el rol familiar: la cuantía de ese rol (hoy). */
  alCambiarRol(i: number): void {
    if (!this.pideRol(i)) {
      return;
    }
    const d = this.deportes.at(i);
    d.get('cuantiaTarifa')!.setValue(cuantiaEstandar(this.tarifaDe(i), d.get('rolFamiliar')!.value || undefined));
  }

  alternarLicencia(i: number): void {
    const d = this.deportes.at(i);
    const tiene = !d.get('tieneLicencia')!.value;
    d.get('tieneLicencia')!.setValue(tiene);
    d.markAsDirty();
    if (!tiene) {
      // Apagarla limpia competidor, categoría, fechas y peso (hoy).
      d.patchValue(
        { competidor: false, categoria: '', fechaAltaCompeticion: '', fechaAltaCompetidorInicial: '', peso: null },
        { emitEvent: false }
      );
    }
  }

  alternarCompetidor(i: number): void {
    const d = this.deportes.at(i);
    const compite = !d.get('competidor')!.value;
    d.get('competidor')!.setValue(compite);
    d.markAsDirty();
    const categoria = d.get('categoria')!;
    if (!compite) {
      d.patchValue({ categoria: '', fechaAltaCompeticion: '', fechaAltaCompetidorInicial: '' });
      return;
    }
    const deporte = d.get('deporte')!.value || this.catalogo.primeraDisciplinaActiva();
    const alta = d.get('fechaAlta')!.value || fechaHoy();
    const inicial = d.get('fechaAltaInicial')!.value || alta;
    const porEdad = calcularCategoriaPorEdad(this.valor('fechaNacimiento') || null, deporte);
    if (porEdad && (!categoria.value || !categoria.dirty)) {
      categoria.setValue(porEdad);
    }
    if (!d.get('fechaAltaCompeticion')!.value) {
      d.get('fechaAltaCompeticion')!.setValue(alta);
    }
    if (!d.get('fechaAltaCompetidorInicial')!.value) {
      d.get('fechaAltaCompetidorInicial')!.setValue(inicial);
    }
  }

  alternarDiscapacidad(): void {
    const c = this.form.get('tieneDiscapacidad')!;
    c.setValue(!c.value);
    c.markAsDirty();
  }

  private actualizarCategoriasPorEdad(): void {
    this.deportes.controls.forEach((d) => {
      const categoria = d.get('categoria')!;
      const deporte = d.get('deporte')!.value || this.catalogo.primeraDisciplinaActiva();
      if (d.get('competidor')!.value) {
        if (!categoria.value || !categoria.dirty) {
          categoria.setValue(calcularCategoriaPorEdad(this.valor('fechaNacimiento') || null, deporte), { emitEvent: false });
        }
      } else if (categoria.value) {
        categoria.setValue('', { emitEvent: false });
      }
    });
  }

  private pedirGradosDisponibles(fecha: string): void {
    this.suscripciones.add(
      this.api.gradosDisponibles(fecha).subscribe({
        next: (tipos) => {
          this.tiposGrado = Array.isArray(tipos) ? tipos : [];
          this.cdr.markForCheck();
        },
        error: () => this.avisos.error('No se pudieron cargar los grados disponibles.'),
      })
    );
  }

  // ------------------------------------------------------------------ edad y responsable

  valor(campo: string): any {
    return this.form.get(campo)?.value;
  }

  get edad(): number | null {
    return edadEnAnios(this.valor('fechaNacimiento'));
  }

  /** «9 años · categoría infantil», con la categoría del primer deporte (o de la primera disciplina). */
  get textoEdad(): string {
    const deporte = this.valorDeporte(0, 'deporte') || this.catalogo.primeraDisciplinaActiva();
    const categoria = this.edad === null ? '' : calcularCategoriaPorEdad(this.valor('fechaNacimiento'), deporte);
    return textoEdad(this.edad, categoria);
  }

  get esMenor(): boolean {
    return esMenorDeEdad(this.edad);
  }

  // ------------------------------------------------------------------ errores

  idPersona(campo: CampoPersona): string {
    return `alta-${campo}`;
  }

  idDeporte(campo: CampoDeporte, i: number): string {
    return campo === 'deporte' ? `alta-deporte-${i}` : `alta-${campo}-${i}`;
  }

  private visible(control: AbstractControl | null | undefined): boolean {
    return !!control && control.invalid && (control.touched || this.intentado);
  }

  erroresPersona(campo: CampoPersona): string[] {
    const control = this.form.get(campo);
    return this.visible(control) ? mensajesPersona(campo, control!.errors) : [];
  }

  erroresDeporte(campo: CampoDeporte, i: number): string[] {
    const control = this.deportes.at(i)?.get(campo);
    return this.visible(control) ? mensajesDeporte(campo, control!.errors) : [];
  }

  /** El `aria-describedby` de un campo: su ayuda (si la hay) y su error (si se ve). */
  describir(id: string, conError: boolean, ayuda = false): string | null {
    const ids = [ayuda ? `${id}-ayuda` : null, conError ? `${id}-error` : null].filter((v): v is string => !!v);
    return ids.length ? ids.join(' ') : null;
  }

  /** Todos los errores del formulario, en el orden de la pantalla (para «Faltan N datos»). */
  get errores(): ErrorResumen[] {
    const lista: ErrorResumen[] = [];
    if (this.errorGeneral) {
      lista.push({ id: 'alta-dar-de-alta', etiqueta: 'No se pudo dar de alta', mensaje: this.errorGeneral });
    }
    for (const campo of ORDEN_PERSONA) {
      const control = this.form.get(campo);
      if (control?.invalid) {
        for (const mensaje of mensajesPersona(campo, control.errors)) {
          lista.push({ id: this.idPersona(campo), etiqueta: ETIQUETAS_PERSONA[campo], mensaje });
        }
      }
    }
    this.deportes.controls.forEach((d, i) => {
      for (const campo of ORDEN_DEPORTE) {
        const control = d.get(campo);
        if (control?.invalid) {
          const prefijo = this.deportes.length > 1 || this.valorDeporte(i, 'deporte') ? `${this.cabeceraDeporte(i)}: ` : '';
          for (const mensaje of mensajesDeporte(campo, control.errors)) {
            lista.push({ id: this.idDeporte(campo, i), etiqueta: `${prefijo}${ETIQUETAS_DEPORTE[campo]}`, mensaje });
          }
        }
      }
    });
    return lista;
  }

  get tituloResumen(): string {
    return tituloFaltan(this.errores.length);
  }

  irACampo(evento: Event, id: string): void {
    evento.preventDefault();
    const campo = this.host.nativeElement.querySelector<HTMLElement>(`#${id}`);
    // El buscador de hoy oculta el <select> y pinta su campo al lado: el foco va a ese campo.
    const destino = campo && campo.tagName === 'SELECT' && campo.offsetParent === null
      ? campo.parentElement?.querySelector<HTMLElement>('input.admin-select-input') ?? campo
      : campo;
    destino?.focus();
    destino?.scrollIntoView?.({ block: 'center' });
  }

  private limpiarErrorServidor(control: AbstractControl | null | undefined): void {
    if (control?.errors?.['servidor']) {
      const { servidor, ...resto } = control.errors;
      control.setErrors(Object.keys(resto).length ? resto : null);
    }
  }

  /** Al escribir en un campo con el error del servidor (NIF duplicado, sede), el error se va. */
  alEditar(campo: CampoPersona): void {
    this.limpiarErrorServidor(this.form.get(campo));
    this.errorGeneral = null;
  }

  // ------------------------------------------------------------------ foto

  async elegirFoto(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const elegido = entrada?.files?.[0];
    if (elegido) {
      await this.usarFoto(elegido);
    }
    reiniciarSelectorArchivo(entrada);
  }

  alArrastrar(evento: DragEvent, encima: boolean): void {
    evento.preventDefault();
    this.arrastrando = encima;
  }

  async alSoltar(evento: DragEvent): Promise<void> {
    evento.preventDefault();
    this.arrastrando = false;
    const archivo = evento.dataTransfer?.files?.[0];
    if (archivo) {
      await this.usarFoto(archivo);
    }
  }

  /** La validación de hoy (`SUBIDA_IMAGEN`); si no vale, el aviso de error con el motivo. */
  private async usarFoto(archivo: File): Promise<void> {
    // Copia en memoria al elegirla: en Android leer el content:// al enviar falla (hoy).
    const preparado = await prepararFicheroParaSubida(archivo, SUBIDA_IMAGEN);
    if (!preparado.ok) {
      this.avisos.error(`No se pudo usar la imagen. ${preparado.motivo}`);
      this.cdr.markForCheck();
      return;
    }
    this.soltarPreview();
    this.imagen = preparado.file;
    this.imagenPreview = URL.createObjectURL(preparado.file);
    this.form.markAsDirty();
    this.cdr.markForCheck();
  }

  quitarFoto(): void {
    this.soltarPreview();
    this.imagen = null;
  }

  private soltarPreview(): void {
    if (this.imagenPreview) {
      URL.revokeObjectURL(this.imagenPreview);
      this.imagenPreview = null;
    }
  }

  // ------------------------------------------------------------------ resumen del alta

  get nombreCompleto(): string {
    return [this.valor('nombre'), this.valor('apellidos')].map((v) => (v ?? '').trim()).filter(Boolean).join(' ');
  }

  get inicialesAlumno(): string {
    return iniciales(this.valor('nombre'), this.valor('apellidos')) || '?';
  }

  get nombreSede(): string {
    const id = Number(this.valor('sede'));
    return this.sedes.find((s) => s.id === id)?.nombre ?? '';
  }

  /** «Mensual infantil · 30,00 €» del deporte i. */
  tarifaResumen(i: number): string {
    const tarifa = this.tarifaDe(i);
    if (!tarifa) {
      return '';
    }
    const cuantia = this.valorDeporte(i, 'cuantiaTarifa');
    const importe = cuantia === null || cuantia === undefined || cuantia === '' ? '' : eur(Number(cuantia));
    return importe ? `${tarifa.nombre} · ${importe}` : tarifa.nombre;
  }

  // ------------------------------------------------------------------ dar de alta y cancelar

  /** Con errores no envía: el resumen «Faltan N datos» y los mensajes bajo cada campo (decisión 285). */
  darDeAlta(): void {
    if (this.enviando) {
      return;
    }
    if (this.form.invalid) {
      this.intentado = true;
      this.form.markAllAsTouched();
      this.cdr.detectChanges();
      const resumen = this.host.nativeElement.querySelector<HTMLElement>('.alta__resumen-errores');
      resumen?.focus();
      resumen?.scrollIntoView?.({ block: 'start' });
      return;
    }
    this.errorGeneral = null;
    const cuerpo = cuerpoAlta(
      this.form.getRawValue(),
      {
        permiteCompeticion: (d) => this.catalogo.permiteCompeticion(d),
        requiereLicencia: (d) => this.catalogo.requiereLicencia(d),
        primeraDisciplinaActiva: () => this.catalogo.primeraDisciplinaActiva(),
        categoriaPorEdad: (f, d) => calcularCategoriaPorEdad(f, d),
        hoy: fechaHoy(),
      },
      this.pedirSede
    );
    this.enviando = true;
    this.api.crear(cuerpo, this.imagen).subscribe({
      next: (respuesta) => {
        this.enviando = false;
        const n = cuerpo['deportesInicial']?.length || 0;
        this.avisos.exito(`Alumno creado con ${n} deporte(s)`);
        this.form.markAsPristine();
        if (!this.destruido) {
          void this.router.navigate(respuesta?.id ? ['/alumnosEditar', respuesta.id] : ['/alumnosListar']);
        }
      },
      error: (error) => {
        this.enviando = false;
        this.mostrarErrorServidor(error);
      },
    });
  }

  /** El texto del servidor: bajo su campo (NIF, sede o deporte) o en el resumen, y en el aviso de error. */
  private mostrarErrorServidor(error: any): void {
    const texto = textoErrorAlta(error);
    const destino = destinoErrorServidor(
      error?.status,
      texto,
      this.deportes.controls.map((d) => ({
        codigo: d.get('deporte')!.value ?? '',
        nombre: d.get('deporte')!.value ? this.catalogo.etiquetaDisciplina(d.get('deporte')!.value) : '',
      }))
    );
    const control =
      destino?.tipo === 'persona'
        ? this.form.get(destino.campo)
        : destino?.tipo === 'deporte'
          ? this.deportes.at(destino.indice)?.get('deporte')
          : null;
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), servidor: texto });
      control.markAsTouched();
    } else {
      this.errorGeneral = texto;
    }
    this.intentado = true;
    this.avisos.error(texto);
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>('.alta__resumen-errores')?.focus();
  }

  /** «Cancelar»: con datos escritos, «¿Salir sin dar de alta?»; si no, al listado como hoy. */
  async cancelar(): Promise<void> {
    if (this.enviando) {
      return;
    }
    if (this.form.dirty || this.imagen) {
      const r = await this.confirmacion.confirmar({
        titulo: '¿Salir sin dar de alta?',
        mensaje: 'Se pierden los datos que has escrito.',
        accion: 'Salir sin guardar',
        cancelar: 'Seguir editando',
        destructivo: true,
        nombre: 'salir-sin-alta',
      });
      if (!r.confirmado) {
        return;
      }
    }
    if (!this.destruido) {
      void this.router.navigate(['/alumnosListar']);
    }
  }
}
