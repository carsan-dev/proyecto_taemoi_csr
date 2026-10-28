import { NgTemplateOutlet } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, ElementRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { CatalogoService } from '../../../features/catalogo/catalogo.service';
import { AuthenticationService } from '../../../servicios/authentication/authentication.service';
import { DocumentoInforme } from '../../../servicios/generales/informe-pdf.service';
import { fechaApi } from '../../../utils/fecha-api';
import { AcentoEscuelaDirective } from '../../acento-escuela.directive';
import { AvisosService } from '../../avisos/avisos.service';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { BloqueVacioComponent } from '../../carga/bloque-vacio.component';
import { BotonOcupadoComponent } from '../../carga/boton-ocupado.component';
import { EsqueletoComponent } from '../../carga/esqueleto.component';
import { contextoErrorLocal } from '../../carga/lecturas-panel.service';
import { ConfirmacionService } from '../../dialogo/confirmacion.service';
import { PanelLateralComponent } from '../../panel-lateral/panel-lateral.component';
import { VisorPdfComponent } from '../../visor-pdf/visor-pdf.component';

const FIELDS = [
  { key: 'cabecera', label: 'Cabecera', max: 120 },
  { key: 'contacto', label: 'Contacto', max: 180 },
  { key: 'titulo', label: 'Título', max: 180 },
  { key: 'consentimiento', label: 'Consentimiento fotográfico', max: 1500 },
  { key: 'importes', label: 'Importes o información de pago', max: 3000 },
  { key: 'instrucciones', label: 'Instrucciones posteriores', max: 3000 },
] as const;
type TextField = typeof FIELDS[number]['key'];
type Draft = Record<TextField, string> & { normas: string[] };
interface Version {
  id: number;
  version: number;
  activa: boolean;
  creadaEn: unknown;
  contenido: Omit<Draft, 'instrucciones'>;
  instrucciones: string;
}
interface Templates { activa: Version | null; historial: Version[]; }
const STATUS = { loading: 'loading', ready: 'ready', error: 'error' } as const;
const emptyDraft = (): Draft => ({ cabecera: '', contacto: '', titulo: '', consentimiento: '', normas: [''], importes: '', instrucciones: '' });

@Component({
  selector: 'pn-plantillas-pdf',
  standalone: true,
  imports: [FormsModule, NgTemplateOutlet, AcentoEscuelaDirective, EsqueletoComponent, BloqueErrorComponent,
    BloqueVacioComponent, BotonOcupadoComponent, PanelLateralComponent, VisorPdfComponent],
  templateUrl: './plantillas-pdf.component.html',
  styleUrls: ['./plantillas-pdf.component.scss'],
})
export class PlantillasPdfComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly catalogo = inject(CatalogoService);
  private readonly auth = inject(AuthenticationService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly avisos = inject(AvisosService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly headerFields = FIELDS.slice(0, 3);
  readonly bodyFields = FIELDS.slice(3);
  readonly ayudasBloque: Record<string, string> = {
    consentimiento: 'Texto que acompaña a la casilla de autorización de imágenes.',
    importes: 'Precios y forma de pago que verá la familia.',
    instrucciones: 'Qué debe hacer la familia después de enviar la solicitud.',
  };
  disciplina = this.catalogo.primeraDisciplinaActiva();
  estado: typeof STATUS[keyof typeof STATUS] = STATUS.loading;
  activa: Version | null = null;
  historial: Version[] = [];
  borrador = emptyDraft();
  private base = emptyDraft();
  errores: Record<string, string> = {};
  validado = false;
  ocupado = false;
  publicando = false;
  previsualizando = false;
  versionesAbiertas = false;
  documentos: DocumentoInforme[] = [];
  focoRetorno: HTMLElement | null = null;
  private cambiandoDisciplina = false;

  get esAdmin(): boolean { return this.auth.tieneRolAdmin(); }
  get disciplinas() { return this.catalogo.disciplinasActivas(); }
  get nombreDisciplina(): string { return this.catalogo.etiquetaDisciplina(this.disciplina); }
  get siguienteVersion(): number { return (this.activa?.version ?? 0) + 1; }
  get cambios(): number {
    if (!this.esAdmin) return 0;
    return FIELDS.filter(f => this.borrador[f.key] !== this.base[f.key]).length
      + Number(JSON.stringify(this.borrador.normas) !== JSON.stringify(this.base.normas));
  }

  /** La barra inferior de publicar: con cambios, o siempre sin versión activa (si no, la versión 1 no se alcanzaría). */
  get hayBarraPublicar(): boolean {
    return this.esAdmin && this.estado === STATUS.ready && !!this.disciplina && (this.cambios > 0 || !this.activa);
  }
  get textoPendientes(): string {
    if (!this.activa) return this.cambios ? 'Cambios sin publicar' : 'Sin publicar';
    return `${this.cambios} ${this.cambios === 1 ? 'cambio' : 'cambios'} sin publicar`;
  }

  ngOnInit(): void { this.cargar(); }

  async cargar(): Promise<void> {
    if (!this.disciplina) { this.estado = STATUS.ready; return; }
    this.estado = STATUS.loading;
    try {
      // Panel-local requests keep legacy behavior and its initial bundle unchanged.
      const data = await firstValueFrom(this.http.get<Templates>(this.url(), { context: contextoErrorLocal() })
        .pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destroyRef.destroyed) return;
      this.activa = data.activa;
      this.historial = data.historial;
      this.descartar();
      this.estado = STATUS.ready;
    } catch (error) {
      if (this.destroyRef.destroyed) return;
      if (error instanceof HttpErrorResponse && error.status === 404) {
        this.activa = null;
        this.historial = [];
        this.descartar();
        this.estado = STATUS.ready;
      } else { this.estado = STATUS.error; }
    }
    this.cdr.markForCheck();
  }

  descartar(): void {
    const draft = emptyDraft();
    for (const f of FIELDS) {
      draft[f.key] = f.key === 'instrucciones' ? this.activa?.instrucciones ?? '' : this.activa?.contenido[f.key] ?? '';
    }
    draft.normas = [...(this.activa?.contenido.normas ?? [''])];
    this.borrador = draft;
    this.base = { ...draft, normas: [...draft.normas] };
    this.validado = false;
    this.errores = {};
  }

  actualizarErrores(): void {
    if (!this.validado) return;
    const errors: Record<string, string> = {};
    for (const f of FIELDS) errors[f.key] = this.errorTexto(this.borrador[f.key], f.max);
    errors['normas'] = this.borrador.normas.length === 0 ? 'Añade al menos una norma.'
      : this.borrador.normas.length > 30 ? 'No puedes añadir más de 30 normas.' : '';
    this.borrador.normas.forEach((n, i) => errors[`norma-${i}`] = this.errorTexto(n, 2000));
    this.errores = errors;
  }

  private errorTexto(value: string, max: number): string {
    return !value.trim() ? 'Escribe este texto.' : value.length > max ? `No superes los ${this.numero(max)} caracteres.` : '';
  }

  private validar(): boolean {
    this.validado = true;
    this.actualizarErrores();
    if (!Object.values(this.errores).some(Boolean)) return true;
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    return false;
  }

  agregarNorma(): void {
    if (!this.esAdmin || this.ocupado || this.borrador.normas.length >= 30) return;
    this.borrador.normas.push('');
    this.actualizarErrores();
    this.enfocarNorma(this.borrador.normas.length - 1);
  }

  moverNorma(index: number, offset: number): void {
    const destination = index + offset;
    if (!this.esAdmin || this.ocupado || destination < 0 || destination >= this.borrador.normas.length) return;
    [this.borrador.normas[index], this.borrador.normas[destination]] = [this.borrador.normas[destination], this.borrador.normas[index]];
    this.actualizarErrores();
    this.enfocarNorma(destination);
  }

  quitarNorma(index: number): void {
    if (!this.esAdmin || this.ocupado || this.borrador.normas.length <= 1) return;
    this.borrador.normas.splice(index, 1);
    this.actualizarErrores();
    this.enfocarNorma(Math.min(index, this.borrador.normas.length - 1));
  }

  private enfocarNorma(index: number): void {
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>(`#plantilla-norma-${index}`)?.focus();
  }

  async puedeSalir(): Promise<boolean> {
    if (this.ocupado) return false;
    if (!this.cambios) return true;
    const disparador = this.disparadorActivo();
    this.ocupado = true;
    try {
      const result = await this.confirmacion.confirmar({ titulo: '¿Descartar los cambios?',
        mensaje: `Tienes cambios sin publicar en ${this.nombreDisciplina}.`, cancelar: 'Seguir editando',
        accion: 'Descartar cambios', destructivo: true });
      if (!result.confirmado) this.devolverFoco(disparador);
      return !this.destroyRef.destroyed && result.confirmado;
    } finally { this.ocupado = false; this.cdr.markForCheck(); }
  }

  async cambiarDisciplina(code: string): Promise<void> {
    if (code === this.disciplina || this.estado === STATUS.loading || this.cambiandoDisciplina) return;
    this.cambiandoDisciplina = true;
    try {
      if (!(await this.puedeSalir()) || this.destroyRef.destroyed) return;
      this.disciplina = code;
      this.documentos = [];
      await this.cargar();
    } finally { this.cambiandoDisciplina = false; }
  }

  async teclaDisciplinas(event: KeyboardEvent): Promise<void> {
    const index = this.disciplinas.findIndex(d => d.codigo === this.disciplina);
    let next = index;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (index + 1) % this.disciplinas.length;
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (index - 1 + this.disciplinas.length) % this.disciplinas.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = this.disciplinas.length - 1;
    else return;
    event.preventDefault();
    await this.cambiarDisciplina(this.disciplinas[next].codigo);
    this.cdr.detectChanges();
    this.host.nativeElement.querySelector<HTMLElement>('[role="radio"][aria-checked="true"]')?.focus();
  }

  async previsualizar(button: EventTarget | null): Promise<void> {
    if (this.ocupado || this.estado !== STATUS.ready || (!this.esAdmin && !this.activa) || !this.validar()) return;
    this.ocupado = this.previsualizando = true;
    this.focoRetorno = button instanceof HTMLElement ? button : null;
    try {
      const blob = await firstValueFrom(this.http.post(`${this.url()}/preview`, this.payload(),
        { responseType: 'blob', context: contextoErrorLocal() }).pipe(takeUntilDestroyed(this.destroyRef)));
      if (!this.destroyRef.destroyed) this.documentos = [{ blob, nombreArchivo: `preinscripcion-${this.disciplina}-preview.pdf`, titulo: `Preinscripción · ${this.nombreDisciplina}` }];
    } catch (error) {
      if (!this.destroyRef.destroyed) this.avisos.error(await this.mensajeError(error, 'No se pudo generar la vista previa. Inténtalo de nuevo.'));
    } finally { this.ocupado = this.previsualizando = false; this.cdr.markForCheck(); }
  }

  async publicar(): Promise<void> {
    if (!this.esAdmin || this.ocupado || this.estado !== STATUS.ready || !this.validar()) return;
    const disparador = this.disparadorActivo();
    this.ocupado = true;
    const version = this.siguienteVersion;
    try {
      const result = await this.confirmacion.confirmar({ titulo: `¿Publicar la versión ${version} de ${this.nombreDisciplina}?`,
        mensaje: `Desde ahora, las preinscripciones de ${this.nombreDisciplina} usarán estos textos. Puedes volver a una versión anterior desde Versiones.`,
        cancelar: 'Seguir editando', accion: 'Publicar' });
      if (!result.confirmado || this.destroyRef.destroyed) { this.devolverFoco(disparador); return; }
      this.publicando = true;
      this.cdr.markForCheck();
      const published = await firstValueFrom(this.http.post<Version>(this.url(), this.payload(), { context: contextoErrorLocal() })
        .pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destroyRef.destroyed) return;
      this.avisos.exito(`Versión ${published.version} publicada.`);
      this.activa = published;
      this.descartar();
      await this.cargar();
    } catch (error) {
      if (!this.destroyRef.destroyed) this.avisos.error(await this.mensajeError(error, 'No se pudo publicar. Tus cambios siguen aquí.'));
    } finally { this.ocupado = this.publicando = false; this.cdr.markForCheck(); }
  }

  async restaurar(version: Version): Promise<void> {
    if (!this.esAdmin || this.ocupado || version.version === this.activa?.version) return;
    const disparador = this.disparadorActivo();
    this.ocupado = true;
    try {
      const result = await this.confirmacion.confirmar({ titulo: `¿Restaurar la versión ${version.version}?`,
        mensaje: `Se publicará como versión ${this.siguienteVersion} con los textos de la versión ${version.version}. El historial no cambia.${this.cambios ? ' Perderás los cambios sin publicar.' : ''}`,
        cancelar: 'Cancelar', accion: 'Restaurar' });
      if (!result.confirmado || this.destroyRef.destroyed) { this.devolverFoco(disparador); return; }
      const restored = await firstValueFrom(this.http.post<Version>(`${this.url()}/${version.version}/restaurar`, {}, { context: contextoErrorLocal() })
        .pipe(takeUntilDestroyed(this.destroyRef)));
      if (this.destroyRef.destroyed) return;
      this.avisos.exito(`Versión ${version.version} restaurada como versión ${restored.version}.`);
      this.activa = restored;
      this.descartar();
      this.versionesAbiertas = false;
      await this.cargar();
    } catch (error) {
      if (!this.destroyRef.destroyed) this.avisos.error(await this.mensajeError(error, 'No se pudo restaurar. Tus cambios siguen aquí.'));
    } finally { this.ocupado = false; this.cdr.markForCheck(); }
  }

  @HostListener('window:beforeunload', ['$event'])
  antesDeSalir(event: BeforeUnloadEvent): void {
    if (this.cambios || this.ocupado) { event.preventDefault(); event.returnValue = ''; }
  }

  numero(value: number): string { return new Intl.NumberFormat('es-ES', { useGrouping: true }).format(value); }
  fecha(value: unknown): string {
    const date = fechaApi(value);
    return date ? new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short', year: 'numeric' }).format(date).replace(/ de /g, ' ') : '';
  }
  /** Quien abre el diálogo: al cancelar, el foco vuelve a él cuando ya no está ocupado (deshabilitado no lo recibe). */
  private disparadorActivo(): HTMLElement | null {
    const activo = this.host.nativeElement.ownerDocument.activeElement;
    return activo instanceof HTMLElement ? activo : null;
  }
  private devolverFoco(disparador: HTMLElement | null): void {
    this.ocupado = false;
    if (this.destroyRef.destroyed) return;
    this.cdr.detectChanges();
    if (disparador?.isConnected) disparador.focus();
  }
  private payload(): Draft { return { ...this.borrador, normas: [...this.borrador.normas] }; }
  private url(): string { return `${environment.apiUrl}/preinscripciones/plantillas/${this.disciplina}`; }
  private async mensajeError(error: unknown, fallback: string): Promise<string> {
    if (!(error instanceof HttpErrorResponse)) return fallback;
    let body: unknown = error.error;
    if (body instanceof Blob) {
      try { body = JSON.parse(await body.text()); } catch { return fallback; }
    }
    if (typeof body === 'object' && body !== null) {
      const data = body as Record<string, unknown>;
      const message = data['mensaje'] ?? data['message'];
      if (typeof message === 'string' && message.trim()) return message;
    }
    return fallback;
  }
}
