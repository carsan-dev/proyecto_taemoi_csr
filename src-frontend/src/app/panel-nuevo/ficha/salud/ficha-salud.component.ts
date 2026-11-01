import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import type { SaludAlumno } from '../../../interfaces/profesor';
import { ORDEN_DISCAPACIDAD, TIPOS_DISCAPACIDAD, TipoDiscapacidad } from '../../../interfaces/tipo-discapacidad';
import { AvisosService } from '../../avisos/avisos.service';
import { BloqueErrorComponent } from '../../carga/bloque-error.component';
import { mensajeDelServidor } from '../../mis-grupos/profesor-textos';
import { SelectorDiscapacidadComponent } from '../../salud/selector-discapacidad.component';
import { GuardarSalud, SaludAlumnoService } from '../../salud/salud-alumno.service';
import type { FichaAlumnoComponent } from '../ficha-alumno.component';

type FormularioSalud = Omit<GuardarSalud, 'descripcion' | 'impideDetalle' | 'alergias' | 'discapacidadOtra' | 'origen'> & {
  descripcion: string; impideDetalle: string; alergias: string; discapacidadOtra: string;
};

@Component({
  selector: 'pn-ficha-salud',
  standalone: true,
  imports: [FormsModule, BloqueErrorComponent, SelectorDiscapacidadComponent],
  templateUrl: './ficha-salud.component.html',
  styleUrls: ['./ficha-salud.component.scss'],
})
export class FichaSaludComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) ficha!: FichaAlumnoComponent;
  @Input({ required: true }) alumnoId = 0;
  @Input() anonimizado = false;
  @Input() soloLectura = false;
  private readonly api = inject(SaludAlumnoService);
  private readonly avisos = inject(AvisosService);
  private peticion: Subscription | null = null;
  private revision = 0;
  saludAbierta = false;
  cargando = false;
  guardando = false;
  editando = false;
  error: string | null = null;
  intentado = false;
  salud: SaludAlumno | null = null;
  formulario: FormularioSalud | null = null;
  readonly etiquetas = TIPOS_DISCAPACIDAD;
  readonly preguntas = [
    { campo: 'tieneDiscapacidad', texto: '¿Tiene discapacidad?' },
    { campo: 'lesionEnfermedad', texto: 'Lesión o enfermedad' },
    { campo: 'impideActividad', texto: '¿Le impide alguna actividad?' },
  ] as const;

  ngOnInit(): void { this.ficha.bloqueSalud = this; }
  ngOnChanges(cambios: SimpleChanges): void {
    if (cambios['alumnoId'] || (cambios['anonimizado'] && this.anonimizado)) this.descartarSalud();
  }
  ngOnDestroy(): void {
    this.descartarSalud();
    if (this.ficha.bloqueSalud === this) this.ficha.bloqueSalud = null;
  }
  get tiposLectura(): TipoDiscapacidad[] {
    return ORDEN_DISCAPACIDAD.filter(t => this.salud?.discapacidadTipos.includes(t));
  }
  get sinDatos(): boolean { return !!this.salud && !this.salud.registrado && !this.salud.tieneDiscapacidad; }
  get otraVacia(): boolean {
    const f = this.formulario;
    return !!f && f.tieneDiscapacidad === true && f.discapacidadTipos.includes('OTRA') && !f.discapacidadOtra?.trim();
  }
  respuesta(valor: boolean | null): string { return valor === null ? 'Sin indicar' : valor ? 'Sí' : 'No'; }

  conmutarSalud(): void {
    if (this.anonimizado || this.guardando || this.editando) return;
    if (this.saludAbierta) { this.descartarSalud(); return; }
    this.saludAbierta = true;
    this.pedirSalud();
  }
  pedirSalud(): void {
    if (!this.saludAbierta || this.anonimizado || !this.alumnoId) return;
    this.peticion?.unsubscribe();
    const revision = ++this.revision;
    const id = this.alumnoId;
    this.cargando = true;
    this.error = null;
    this.salud = null;
    this.peticion = this.api.obtener(id).subscribe({
      next: salud => {
        if (!this.vigente(id, revision) || salud.alumnoId !== id) return;
        this.salud = salud;
        this.cargando = false;
      },
      error: e => {
        if (!this.vigente(id, revision)) return;
        this.cargando = false;
        this.error = mensajeDelServidor(e, 'No se han podido leer los datos de salud.');
      },
    });
  }
  descartarSalud(): void {
    ++this.revision;
    this.peticion?.unsubscribe();
    this.peticion = null;
    this.saludAbierta = false;
    this.cargando = this.guardando = this.editando = this.intentado = false;
    this.salud = null;
    this.formulario = null;
    this.error = null;
  }
  editar(): void {
    if (!this.salud || this.soloLectura || this.anonimizado) return;
    const s = this.salud;
    this.formulario = {
      tieneDiscapacidad: s.tieneDiscapacidad, discapacidadTipos: [...s.discapacidadTipos], discapacidadOtra: s.discapacidadOtra ?? '',
      lesionEnfermedad: s.lesionEnfermedad, descripcion: s.descripcion ?? '',
      impideActividad: s.impideActividad, impideDetalle: s.impideDetalle ?? '', alergias: s.alergias ?? '',
    };
    this.editando = true;
    this.intentado = false;
    this.error = null;
  }
  descartarEdicion(): void {
    if (this.guardando) return;
    this.formulario = null;
    this.editando = this.intentado = false;
    this.error = null;
  }
  private texto(v: string | null | undefined): string | null { return v?.trim() || null; }
  /**
   * Texto ligado a una respuesta. Las fichas importadas de papel pueden traer texto sin un «Sí»: el bloque no lo enseña,
   * así que guardar otra cosa no debe borrarlo. Solo se borra al pasar de «Sí» a otra respuesta.
   */
  private textoLigado(respuesta: boolean | null, leida: boolean | null, escrito: string, leido: string | null): string | null {
    if (respuesta === true) return this.texto(escrito);
    return leida === true ? null : leido;
  }
  get cambios(): number {
    const f = this.formulario, s = this.salud;
    if (!this.editando || !f || !s) return 0;
    const iguales = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
    const tipos = (t: TipoDiscapacidad[]) => ORDEN_DISCAPACIDAD.filter(v => t.includes(v));
    return Number(f.tieneDiscapacidad !== s.tieneDiscapacidad)
      + Number(!iguales([tipos(f.discapacidadTipos), this.texto(f.discapacidadOtra)], [tipos(s.discapacidadTipos), this.texto(s.discapacidadOtra)]))
      + Number(!iguales([f.lesionEnfermedad, this.texto(f.descripcion)], [s.lesionEnfermedad, this.texto(s.descripcion)]))
      + Number(!iguales([f.impideActividad, this.texto(f.impideDetalle)], [s.impideActividad, this.texto(s.impideDetalle)]))
      + Number(this.texto(f.alergias) !== this.texto(s.alergias));
  }
  guardar(): void {
    const f = this.formulario, s = this.salud;
    if (!f || !s || this.guardando || this.soloLectura || this.anonimizado || !this.cambios) return;
    this.intentado = true;
    if (this.otraVacia) return;
    const cuerpo: GuardarSalud = {
      tieneDiscapacidad: f.tieneDiscapacidad,
      discapacidadTipos: f.tieneDiscapacidad === true ? [...f.discapacidadTipos] : [],
      discapacidadOtra: f.tieneDiscapacidad === true && f.discapacidadTipos.length > 0 ? this.texto(f.discapacidadOtra) : null,
      lesionEnfermedad: f.lesionEnfermedad,
      descripcion: this.textoLigado(f.lesionEnfermedad, s.lesionEnfermedad, f.descripcion, s.descripcion),
      impideActividad: f.impideActividad,
      impideDetalle: this.textoLigado(f.impideActividad, s.impideActividad, f.impideDetalle, s.impideDetalle),
      alergias: this.texto(f.alergias),
    };
    const id = this.alumnoId, revision = ++this.revision;
    this.guardando = true;
    this.error = null;
    this.peticion = this.api.guardar(id, cuerpo).subscribe({
      next: salud => {
        if (!this.vigente(id, revision) || salud.alumnoId !== id) return;
        this.salud = salud;
        this.guardando = false;
        this.descartarEdicion();
        this.avisos.exito('Datos de salud guardados.');
      },
      error: e => {
        if (!this.vigente(id, revision)) return;
        this.guardando = false;
        this.error = mensajeDelServidor(e, 'No se han podido guardar los datos de salud.');
        this.avisos.error(this.error);
      },
    });
  }
  private vigente(id: number, revision: number): boolean {
    return this.revision === revision && this.alumnoId === id && this.saludAbierta && !this.anonimizado;
  }
}
